package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.idekerlab.cytoscapemcp.McpSchema;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.command.CommandExecutorTaskFactory;
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.work.SynchronousTaskManager;
import org.cytoscape.work.TaskIterator;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool that runs NetworkAnalyzer on the current network to compute topological statistics. Adds
 * columns to the node and edge tables and returns the list of newly added node columns.
 */
public class AnalyzeNetworkTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyzeNetworkTool.class);

    private static final String TOOL_NAME = "analyze_network";

    private static final String TOOL_DESCRIPTION =
            "Run NetworkAnalyzer on the current network to compute topological statistics"
                    + " (Degree, BetweennessCentrality, etc.). Adds columns to the node and edge"
                    + " tables. Returns the list of newly added node columns and basic network"
                    + " statistics.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String INPUT_SCHEMA =
            McpSchema.toJson(
                    McpSchema.InputSchema.builder()
                            .required("directed")
                            .property(
                                    "directed",
                                    new McpSchema.InputProperty(
                                            "boolean",
                                            "True to treat the network as directed, false for"
                                                    + " undirected."))
                            .build());

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record NetworkStats(
            @JsonProperty("node_count") int nodeCount,
            @JsonProperty("edge_count") int edgeCount,
            @JsonProperty("avg_degree") double avgDegree) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record AnalyzeNetworkResult(
            @JsonProperty("status") String status,
            @JsonProperty("columns_added") List<String> columnsAdded,
            @JsonProperty("network_stats") NetworkStats networkStats) {}

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(AnalyzeNetworkResult.class);

    private final CyApplicationManager appManager;
    private final SynchronousTaskManager<?> syncTaskManager;
    private final CommandExecutorTaskFactory commandExecutorTaskFactory;

    public AnalyzeNetworkTool(
            CyApplicationManager appManager,
            SynchronousTaskManager<?> syncTaskManager,
            CommandExecutorTaskFactory commandExecutorTaskFactory) {
        this.appManager = appManager;
        this.syncTaskManager = syncTaskManager;
        this.commandExecutorTaskFactory = commandExecutorTaskFactory;
    }

    /** Returns the MCP SyncToolSpecification to register with the McpSyncServer. */
    public McpServerFeatures.SyncToolSpecification toSpec() {
        try {
            Tool toolDef =
                    Tool.builder()
                            .name(TOOL_NAME)
                            .description(TOOL_DESCRIPTION)
                            .inputSchema(MAPPER.readValue(INPUT_SCHEMA, JsonSchema.class))
                            .outputSchema(
                                    MAPPER.readValue(
                                            OUTPUT_SCHEMA,
                                            new TypeReference<Map<String, Object>>() {}))
                            .build();
            return McpServerFeatures.SyncToolSpecification.builder()
                    .tool(toolDef)
                    .callHandler(this::handle)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build tool spec for " + TOOL_NAME, e);
        }
    }

    // -- Handler --------------------------------------------------------------

    private CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        LOGGER.info("Tool call received: {}", TOOL_NAME);

        Boolean directed = (Boolean) request.arguments().get("directed");
        if (directed == null) {
            directed = Boolean.FALSE;
        }

        CyNetwork network = appManager.getCurrentNetwork();
        if (network == null) {
            return error("No network is currently loaded. Please load a network first.");
        }

        // Snapshot node-table column names before analysis.
        Set<String> columnsBefore = getNodeColumnNames(network);

        try {
            if (commandExecutorTaskFactory == null) {
                throw new IllegalStateException("CommandExecutorTaskFactory is not available");
            }
            Map<String, Object> args = new HashMap<>();
            args.put("directed", directed);
            TaskIterator ti =
                    commandExecutorTaskFactory.createTaskIterator(
                            "analyzer", "analyze", args, null);
            syncTaskManager.execute(ti);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("too small")) {
                return error("Network analysis failed: " + msg);
            }
            if (msg.contains("not applicable")) {
                return error("Network analysis failed: " + msg);
            }
            return error(
                    "Network analysis is not available: The NetworkAnalyzer app does not appear"
                            + " to be installed or active. Skip this tool and proceed without"
                            + " network statistics. ("
                            + msg
                            + ")");
        }

        // Compute newly added node columns (alphabetically sorted).
        Set<String> columnsAfter = getNodeColumnNames(network);
        Set<String> newCols = new HashSet<>(columnsAfter);
        newCols.removeAll(columnsBefore);
        List<String> columnsAdded = new ArrayList<>(newCols);
        Collections.sort(columnsAdded);

        // Basic network stats derived from the CyNetwork object directly.
        int nodeCount = network.getNodeCount();
        int edgeCount = network.getEdgeCount();
        double avgDegree = nodeCount > 0 ? (2.0 * edgeCount) / nodeCount : 0.0;
        NetworkStats stats = new NetworkStats(nodeCount, edgeCount, avgDegree);

        return CallToolResult.builder()
                .structuredContent(new AnalyzeNetworkResult("success", columnsAdded, stats))
                .build();
    }

    // -- Helpers --------------------------------------------------------------

    private static Set<String> getNodeColumnNames(CyNetwork network) {
        Set<String> names = new HashSet<>();
        for (CyColumn col : network.getDefaultNodeTable().getColumns()) {
            names.add(col.getName());
        }
        return names;
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}
