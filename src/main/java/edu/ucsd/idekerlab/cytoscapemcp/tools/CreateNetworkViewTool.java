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
            "Create a visual view for a network in Cytoscape Desktop that currently has no view. Sets the new view"
                    + " and its network as the current network and view. If a view already exists, returns the existing one instead of creating a duplicate.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record CreateNetworkViewCallResult(
            @JsonProperty("status") String status,
            @JsonProperty("network_suid") long networkSuid,
            @JsonProperty("view_suid") long viewSuid,
            @JsonProperty("network_name") String networkName,
            @JsonProperty("node_count") int nodeCount,
            @JsonProperty("edge_count") int edgeCount) {}

    static final String INPUT_SCHEMA =
            McpSchema.toJson(
                    McpSchema.InputSchema.builder()
                            .required("network_suid")
                            .property(
                                    "network_suid",
                                    new McpSchema.InputProperty(
                                            "integer", "SUID of the target network."))
                            .build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(CreateNetworkViewCallResult.class);
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

            String networkName = network.getRow(network).get(CyNetwork.NAME, String.class);
            return CallToolResult.builder()
                    .structuredContent(
                            new CreateNetworkViewCallResult(
                                    "success",
                                    networkSuid,
                                    view.getSUID(),
                                    networkName,
                                    network.getNodeCount(),
                                    network.getEdgeCount()))
                    .build();
        } catch (Exception e) {
            LOGGER.error("Error creating network view", e);
            return error("Failed to create network view: " + e.getMessage());
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
