package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;
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
 * MCP tool that enumerates all network collections currently loaded in Cytoscape with their views,
 * node counts, and edge counts. Read-only; does not modify state.
 */
public class GetLoadedNetworkViewsTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetLoadedNetworkViewsTool.class);

    private static final String TOOL_NAME = "get_loaded_network_views";

    private static final String TOOL_DESCRIPTION =
            "Enumerate all network collections currently loaded in Cytoscape with their views,"
                    + " node counts, and edge counts. Read-only; does not modify state.";

    static final String INPUT_SCHEMA =
            """
            {
              "type": "object",
              "required": [],
              "properties": {}
            }
            """;

    private final ObjectMapper mapper = new ObjectMapper();
    private final CyNetworkManager networkManager;
    private final CyNetworkViewManager viewManager;

    public GetLoadedNetworkViewsTool(
            CyNetworkManager networkManager, CyNetworkViewManager viewManager) {
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
                            .inputSchema(mapper.readValue(INPUT_SCHEMA, JsonSchema.class))
                            .build();
            return McpServerFeatures.SyncToolSpecification.builder()
                    .tool(toolDef)
                    .callHandler(this::handle)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse INPUT_SCHEMA for " + TOOL_NAME, e);
        }
    }

    // -- Handler --------------------------------------------------------------

    private CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        LOGGER.info("Tool call received: {}", TOOL_NAME);

        try {
            Set<CyNetwork> networks = networkManager.getNetworkSet();
            ArrayNode viewsArray = mapper.createArrayNode();

            for (CyNetwork network : networks) {
                if (!(network instanceof CySubNetwork)) {
                    continue;
                }
                CySubNetwork subNetwork = (CySubNetwork) network;
                CyRootNetwork rootNetwork = subNetwork.getRootNetwork();

                String collectionName =
                        rootNetwork.getRow(rootNetwork).get(CyNetwork.NAME, String.class);
                String networkName = network.getRow(network).get(CyNetwork.NAME, String.class);

                Collection<CyNetworkView> views = viewManager.getNetworkViews(network);
                CyNetworkView firstView = views.isEmpty() ? null : views.iterator().next();

                ObjectNode entry = mapper.createObjectNode();
                entry.put("collection_name", collectionName);
                entry.put("network_name", networkName);
                entry.put("network_suid", network.getSUID());
                if (firstView != null) {
                    entry.put("view_suid", firstView.getSUID());
                } else {
                    entry.putNull("view_suid");
                }
                entry.put("node_count", network.getNodeCount());
                entry.put("edge_count", network.getEdgeCount());

                viewsArray.add(entry);
            }

            ObjectNode result = mapper.createObjectNode();
            result.set("views", viewsArray);

            return success(mapper.writeValueAsString(result));
        } catch (Exception e) {
            LOGGER.error("Error enumerating network views", e);
            return error("Failed to enumerate network views: " + e.getMessage());
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
