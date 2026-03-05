package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.idekerlab.cytoscapemcp.McpSchema;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.view.layout.CyLayoutAlgorithm;
import org.cytoscape.view.layout.CyLayoutAlgorithmManager;
import org.cytoscape.view.model.CyNetworkView;
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
 * MCP tool that applies a named layout algorithm to the current network view. After execution it
 * calls {@code view.fitContent()} and {@code view.updateView()} to ensure the canvas is refreshed.
 */
public class ApplyLayoutTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplyLayoutTool.class);

    private static final String TOOL_NAME = "apply_layout";

    private static final String TOOL_DESCRIPTION =
            "Apply a layout algorithm to the current network view using default parameters."
                    + " After layout, the view is fitted to content and refreshed.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ApplyLayoutResult(
            @JsonProperty("status") String status,
            @JsonProperty("algorithm") String algorithm,
            @JsonProperty("displayName") String displayName) {}

    static final String INPUT_SCHEMA =
            McpSchema.toJson(
                    McpSchema.InputSchema.builder()
                            .required("algorithm")
                            .property(
                                    "algorithm",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Layout algorithm name (as returned by"
                                                    + " get_layout_algorithms)."))
                            .build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(ApplyLayoutResult.class);

    private final CyApplicationManager appManager;
    private final CyLayoutAlgorithmManager layoutManager;
    private final SynchronousTaskManager<?> syncTaskManager;

    public ApplyLayoutTool(
            CyApplicationManager appManager,
            CyLayoutAlgorithmManager layoutManager,
            SynchronousTaskManager<?> syncTaskManager) {
        this.appManager = appManager;
        this.layoutManager = layoutManager;
        this.syncTaskManager = syncTaskManager;
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

        String algorithmName = (String) request.arguments().get("algorithm");
        if (algorithmName == null || algorithmName.isBlank()) {
            return error("algorithm parameter is required.");
        }

        CyNetworkView view = appManager.getCurrentNetworkView();
        if (view == null) {
            return error("No network view is currently available. Please load a network first.");
        }

        CyLayoutAlgorithm algorithm = layoutManager.getLayout(algorithmName);
        if (algorithm == null) {
            return error("Unknown layout algorithm: " + algorithmName);
        }

        try {
            TaskIterator taskIterator =
                    algorithm.createTaskIterator(
                            view,
                            algorithm.createLayoutContext(),
                            CyLayoutAlgorithm.ALL_NODE_VIEWS,
                            null);
            syncTaskManager.execute(taskIterator);
        } catch (Exception e) {
            LOGGER.error("Layout execution failed for algorithm: {}", algorithmName, e);
            return error("Layout failed: " + e.getMessage());
        }

        view.fitContent();
        view.updateView();

        return CallToolResult.builder()
                .structuredContent(
                        new ApplyLayoutResult("success", algorithmName, algorithm.toString()))
                .build();
    }

    // -- Result helpers -------------------------------------------------------

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}
