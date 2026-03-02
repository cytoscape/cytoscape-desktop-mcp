package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

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

    private final ObjectMapper mapper = new ObjectMapper();
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
        Map<String, Object> networkSuidProp =
                Map.of("type", "integer", "description", "SUID of the target network");
        Map<String, Object> viewSuidProp =
                Map.of("type", "integer", "description", "SUID of the target network view");

        Map<String, Object> properties =
                Map.of("network_suid", networkSuidProp, "view_suid", viewSuidProp);

        Tool toolDef =
                Tool.builder()
                        .name(TOOL_NAME)
                        .description(TOOL_DESCRIPTION)
                        .inputSchema(
                                new JsonSchema(
                                        "object",
                                        properties,
                                        List.of("network_suid", "view_suid"),
                                        null,
                                        null,
                                        null))
                        .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(toolDef)
                .callHandler(this::handle)
                .build();
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

            // Build success response.
            String networkName = network.getRow(network).get(CyNetwork.NAME, String.class);
            ObjectNode result = mapper.createObjectNode();
            result.put("status", "success");
            result.put("network_name", networkName);
            result.put("node_count", network.getNodeCount());
            result.put("edge_count", network.getEdgeCount());

            return success(mapper.writeValueAsString(result));
        } catch (Exception e) {
            LOGGER.error("Error setting current network view", e);
            return error("Failed to set current network view: " + e.getMessage());
        }
    }

    // -- Result helpers -------------------------------------------------------

    private static CallToolResult success(String message) {
        return CallToolResult.builder().content(List.of(new TextContent(message))).build();
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}
