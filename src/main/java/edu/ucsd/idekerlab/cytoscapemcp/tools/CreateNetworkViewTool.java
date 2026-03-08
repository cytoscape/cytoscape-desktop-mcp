package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
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
 * MCP tool that creates a visual view for a network. If a view already exists, it returns the
 * existing one instead of creating a duplicate. Sets the new/existing view and its network as the
 * current network and view.
 */
public class CreateNetworkViewTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateNetworkViewTool.class);

    private static final String TOOL_NAME = "create_network_view";

    private static final String TOOL_TITLE = "Create Cytoscape Desktop Network View";

    private static final String TOOL_EXAMPLES =
            "\n\n## Examples\n\n"
                    + "Example 1 — Create a visual view for a network that has no view in Cytoscape desktop:\n"
                    + "{\"network_suid\": 100}\n\n"
                    + "Example 2 — This network has no view, generate one in Cytoscape desktop:\n"
                    + "{\"network_suid\": 100}\n\n"
                    + "Example 3 — Get existing view or create one if none exist for a network in Cytoscape desktop:\n"
                    + "{\"network_suid\": 100}\n\n"
                    + "Example 4 — Force create a new view in Cytoscape desktop even though one already exists:\n"
                    + "{\"network_suid\": 100, \"create_if_exists\": true}";

    private static final String TOOL_DESCRIPTION =
            "Create a new visual view for a network or retrieve the existing view if at least one already"
                    + " exists. Idempotent, will always result in setting the network and the view as the current selection in Cytoscape"
                    + " Desktop. By default will return an existing view if one exists for the given network rather than creating another view on the network.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record CreateNetworkViewCallResult(
            @JsonPropertyDescription("Result status, e.g. 'success' or 'exists'.")
                    @JsonProperty("status")
                    String status,
            @JsonPropertyDescription("SUID of the network.") @JsonProperty("network_suid")
                    long networkSuid,
            @JsonPropertyDescription("SUID of the created or existing network view.")
                    @JsonProperty("view_suid")
                    long viewSuid,
            @JsonPropertyDescription(
                            "Name of the network as shown in the Cytoscape Desktop network panel.")
                    @JsonProperty("network_name")
                    String networkName,
            @JsonPropertyDescription("Number of nodes in the network.") @JsonProperty("node_count")
                    int nodeCount,
            @JsonPropertyDescription("Number of edges in the network.") @JsonProperty("edge_count")
                    int edgeCount) {}

    static final String INPUT_SCHEMA =
            McpSchema.toJson(
                    McpSchema.InputSchema.builder()
                            .required("network_suid")
                            .property(
                                    "network_suid",
                                    new McpSchema.InputProperty(
                                            "integer",
                                            "Required. SUID of the network in Cytoscape Desktop that needs a view."))
                            .property(
                                    "create_if_exists",
                                    new McpSchema.InputProperty(
                                            "boolean",
                                            "Optional. Default is false. When false and a view already exists,"
                                                    + " returns the existing current view (or first available) without creating a duplicate."
                                                    + " When true, always creates a new view even if views already exist."))
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
            long networkSuid = ((Number) request.arguments().get("network_suid")).longValue();

            // Look up the network.
            CyNetwork network = networkManager.getNetwork(networkSuid);
            if (network == null) {
                return error("Network with SUID " + networkSuid + " not found.");
            }

            Boolean createIfExists = (Boolean) request.arguments().get("create_if_exists");
            if (createIfExists == null) createIfExists = Boolean.FALSE;

            // Check for existing views.
            Collection<CyNetworkView> existingViews = viewManager.getNetworkViews(network);
            CyNetworkView view;

            if (!existingViews.isEmpty() && !createIfExists) {
                // Return existing: prefer current view if it belongs to this network.
                CyNetworkView currentView = appManager.getCurrentNetworkView();
                if (currentView != null && existingViews.contains(currentView)) {
                    view = currentView;
                } else {
                    view = existingViews.iterator().next();
                }
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
