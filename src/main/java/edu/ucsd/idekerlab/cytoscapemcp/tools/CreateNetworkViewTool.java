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
import org.cytoscape.view.model.CyNetworkViewFactory;
import org.cytoscape.view.model.CyNetworkViewManager;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool that creates a visual view for a network that currently has no view. If a view already
 * exists, it returns the existing one instead of creating a duplicate. Sets the new/existing view
 * and its network as the current network and view.
 */
public class CreateNetworkViewTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateNetworkViewTool.class);

    private static final String TOOL_NAME = "create_network_view";

    private static final String TOOL_DESCRIPTION =
            "Create a visual view for a network that currently has no view. Sets the new view"
                    + " and its network as the current network and view.";

    private final ObjectMapper mapper = new ObjectMapper();
    private final CyApplicationManager appManager;
    private final CyNetworkManager networkManager;
    private final CyNetworkViewManager viewManager;
    private final CyNetworkViewFactory networkViewFactory;

    public CreateNetworkViewTool(
            CyApplicationManager appManager,
            CyNetworkManager networkManager,
            CyNetworkViewManager viewManager,
            CyNetworkViewFactory networkViewFactory) {
        this.appManager = appManager;
        this.networkManager = networkManager;
        this.viewManager = viewManager;
        this.networkViewFactory = networkViewFactory;
    }

    /** Returns the MCP SyncToolSpecification to register with the McpSyncServer. */
    public McpServerFeatures.SyncToolSpecification toSpec() {
        Map<String, Object> networkSuidProp =
                Map.of("type", "integer", "description", "SUID of the target network");

        Map<String, Object> properties = Map.of("network_suid", networkSuidProp);

        Tool toolDef =
                Tool.builder()
                        .name(TOOL_NAME)
                        .description(TOOL_DESCRIPTION)
                        .inputSchema(
                                new JsonSchema(
                                        "object",
                                        properties,
                                        List.of("network_suid"),
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

            // Look up the network.
            CyNetwork network = networkManager.getNetwork(networkSuid);
            if (network == null) {
                return error("Network with SUID " + networkSuid + " not found.");
            }

            // Check for existing views.
            Collection<CyNetworkView> existingViews = viewManager.getNetworkViews(network);
            CyNetworkView view;

            if (!existingViews.isEmpty()) {
                // Use existing view instead of creating a duplicate.
                view = existingViews.iterator().next();
                LOGGER.info(
                        "Network {} already has a view (SUID {}); returning existing",
                        networkSuid,
                        view.getSUID());
            } else {
                // Create a new view.
                view = networkViewFactory.createNetworkView(network);
                viewManager.addNetworkView(view);
                LOGGER.info(
                        "Created new view (SUID {}) for network {}", view.getSUID(), networkSuid);
            }

            // Set as current.
            appManager.setCurrentNetwork(network);
            appManager.setCurrentNetworkView(view);

            // Build success response.
            String networkName = network.getRow(network).get(CyNetwork.NAME, String.class);
            ObjectNode result = mapper.createObjectNode();
            result.put("status", "success");
            result.put("network_suid", networkSuid);
            result.put("view_suid", view.getSUID());
            result.put("network_name", networkName);
            result.put("node_count", network.getNodeCount());
            result.put("edge_count", network.getEdgeCount());

            return success(mapper.writeValueAsString(result));
        } catch (Exception e) {
            LOGGER.error("Error creating network view", e);
            return error("Failed to create network view: " + e.getMessage());
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
