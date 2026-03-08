package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.ArrayList;
import java.util.Collection;
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

    private static final String TOOL_TITLE = "List Cytoscape Desktop Networks";

    private static final String TOOL_EXAMPLES =
            "\n\n## Examples\n\n"
                    + "Example 1 — List networks currently open in Cytoscape desktop:\n"
                    + "{}\n\n"
                    + "Example 2 — What networks are loaded in Cytoscape desktop:\n"
                    + "{}\n\n"
                    + "Example 3 — Show me the network SUIDs available in Cytoscape desktop:\n"
                    + "{}";

    private static final String TOOL_DESCRIPTION =
            "List all network collections currently loaded in Cytoscape Desktop with their views,"
                    + " node counts, and edge counts. Call this first to discover network and view"
                    + " identifiers required by other tools. Read-only; does not modify state.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record NetworkViewEntry(
            @JsonPropertyDescription("Name of the root network collection.")
                    @JsonProperty("collection_name")
                    String collectionName,
            @JsonPropertyDescription("Name of the sub-network within the collection.")
                    @JsonProperty("network_name")
                    String networkName,
            @JsonPropertyDescription("Unique SUID of the network in Cytoscape Desktop.")
                    @JsonProperty("network_suid")
                    long networkSuid,
            @JsonPropertyDescription(
                            "Unique SUID of the network view. Absent if the network has no view.")
                    @JsonProperty("view_suid")
                    Long viewSuid,
            @JsonPropertyDescription("Number of nodes in the network.") @JsonProperty("node_count")
                    int nodeCount,
            @JsonPropertyDescription("Number of edges in the network.") @JsonProperty("edge_count")
                    int edgeCount) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record GetLoadedNetworkViewsCallResult(
            @JsonProperty("views") List<NetworkViewEntry> views) {}

    static final String INPUT_SCHEMA = McpSchema.toJson(McpSchema.InputSchema.builder().build());

    static final String OUTPUT_SCHEMA =
            McpSchema.toSchemaJson(GetLoadedNetworkViewsCallResult.class);
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

        try {
            Set<CyNetwork> networks = networkManager.getNetworkSet();
            List<NetworkViewEntry> entries = new ArrayList<>();

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
                Long viewSuid = views.isEmpty() ? null : views.iterator().next().getSUID();

                entries.add(
                        new NetworkViewEntry(
                                collectionName,
                                networkName,
                                network.getSUID(),
                                viewSuid,
                                network.getNodeCount(),
                                network.getEdgeCount()));
            }

            return CallToolResult.builder()
                    .structuredContent(new GetLoadedNetworkViewsCallResult(entries))
                    .build();
        } catch (Exception e) {
            LOGGER.error("Error enumerating network views", e);
            return error("Failed to enumerate network views: " + e.getMessage());
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
