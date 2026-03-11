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
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
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

    private static final String TOOL_TITLE = "Analyze Cytoscape Desktop Network";

    private static final String TOOL_EXAMPLES =
            "\n\n## Examples\n\n"
                    + "Example 1 — Compute network statistics of the current network in Cytoscape desktop:\n"
                    + "{\"directed\": false}\n\n"
                    + "Example 2 — Run network analysis treating edges as directed of current network in Cytoscape desktop:\n"
                    + "{\"directed\": true}\n\n"
                    + "Example 3 — Calculate centrality metrics for an undirected biological network in Cytoscape desktop:\n"
                    + "{\"directed\": false}";

    private static final String TOOL_DESCRIPTION =
            "Compute topological statistics (degree, betweenness centrality, closeness centrality)"
                    + " for the current network. Adds computed values as new columns to the node and"
                    + " edge tables. Returns an error if no network is loaded and set to curent view on desktop.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String INPUT_SCHEMA =
            McpSchema.toJson(
                    McpSchema.InputSchema.builder()
                            .property(
                                    "directed",
                                    new McpSchema.InputProperty(
                                            "boolean",
                                            "Optional. Default is true. When true, treats the network"
                                                    + " as a directed graph with in-degree/out-degree"
                                                    + " metrics. When false, treats it as undirected,"
                                                    + " typical for most biological interaction"
                                                    + " networks."))
                            .build());

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record NetworkStats(
            @JsonPropertyDescription("Number of nodes in the network.") @JsonProperty("node_count")
                    int nodeCount,
            @JsonPropertyDescription("Number of edges in the network.") @JsonProperty("edge_count")
                    int edgeCount,
            @JsonPropertyDescription("Average node degree.") @JsonProperty("avg_degree")
                    double avgDegree) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record AnalyzeNetworkResult(
            @JsonPropertyDescription("Result status, e.g. 'success'.") @JsonProperty("status")
                    String status,
            @JsonPropertyDescription(
                            "Names of node attribute columns added to the Cytoscape Desktop node"
                                    + " table by NetworkAnalyzer.")
                    @JsonProperty("columns_added")
                    List<String> columnsAdded,
            @JsonPropertyDescription("Summary statistics of the analyzed network.")
                    @JsonProperty("network_stats")
                    NetworkStats networkStats) {}

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
                            .title(TOOL_TITLE)
                            .description(TOOL_DESCRIPTION + TOOL_EXAMPLES)
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
