package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.Collection;
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
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool that sets the specified network and view as the current (active) network and view in
 * Cytoscape. Requires both {@code network_suid} and {@code view_suid}.
 */
public class SetCurrentNetworkViewTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetCurrentNetworkViewTool.class);

    private static final String TOOL_NAME = "set_current_network_view";

    private static final String TOOL_DESCRIPTION =
            "Set the specified network and view as the current (active) network and view"
                    + " in Cytoscape. Both network_suid and view_suid are required.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record SetCurrentNetworkViewCallResult(
            @JsonProperty("status") String status,
            @JsonProperty("network_name") String networkName,
            @JsonProperty("node_count") int nodeCount,
            @JsonProperty("edge_count") int edgeCount) {}

    static final String INPUT_SCHEMA =
            McpSchema.toJson(
                    McpSchema.InputSchema.builder()
                            .required("network_suid", "view_suid")
                            .property(
                                    "network_suid",
                                    new McpSchema.InputProperty(
                                            "integer", "SUID of the target network."))
                            .property(
                                    "view_suid",
                                    new McpSchema.InputProperty(
                                            "integer", "SUID of the target network view."))
                            .build());

    static final String OUTPUT_SCHEMA =
            McpSchema.toSchemaJson(SetCurrentNetworkViewCallResult.class);
    private final CyApplicationManager appManager;
    private final CyNetworkManager networkManager;
    private final CyNetworkViewManager viewManager;

    public SetCurrentNetworkViewTool(
            CyApplicationManager appManager,
            CyNetworkManager networkManager,
            CyNetworkViewManager viewManager) {
        this.appManager = appManager;
        this.networkManager = networkManager;
        this.viewManager = viewManager;
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

        try {
            long networkSuid = ((Number) request.arguments().get("network_suid")).longValue();
            long viewSuid = ((Number) request.arguments().get("view_suid")).longValue();

            // Look up the network.
            CyNetwork network = networkManager.getNetwork(networkSuid);
            if (network == null) {
                return error("Network with SUID " + networkSuid + " not found.");
            }

            // Look up the view among the network's views.
            Collection<CyNetworkView> views = viewManager.getNetworkViews(network);
            CyNetworkView targetView = null;
            for (CyNetworkView view : views) {
                if (view.getSUID() == viewSuid) {
                    targetView = view;
                    break;
                }
            }
            if (targetView == null) {
                return error("Network view not found. The view may have been closed.");
            }

            // Set as current.
            appManager.setCurrentNetwork(network);
            appManager.setCurrentNetworkView(targetView);

            String networkName = network.getRow(network).get(CyNetwork.NAME, String.class);
            return CallToolResult.builder()
                    .structuredContent(
                            new SetCurrentNetworkViewCallResult(
                                    "success",
                                    networkName,
                                    network.getNodeCount(),
                                    network.getEdgeCount()))
                    .build();
        } catch (Exception e) {
            LOGGER.error("Error setting current network view", e);
            return error("Failed to set current network view: " + e.getMessage());
        }
    }

    // -- Result helpers -------------------------------------------------------

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}
