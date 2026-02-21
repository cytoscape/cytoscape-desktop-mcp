package edu.ucsd.idekerlab.cytoscapemcp.tools;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.property.CyProperty;
import org.cytoscape.task.read.LoadNetworkURLTaskFactory;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.work.FinishStatus;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.SynchronousTaskManager;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP tool that loads a biological network from NDEx into Cytoscape Desktop and sets it as the
 * current active network view.
 */
public class LoadNetworkViewTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadNetworkViewTool.class);

    private static final String TOOL_NAME = "load_cytoscape_network_view";

    private static final String TOOL_DESCRIPTION =
            "Loads a biological network from NDEx (https://www.ndexbio.org) into Cytoscape Desktop"
                    + " and sets it as the current active network view.\n\n"
                    + "The `network-id` parameter is REQUIRED and must be provided by the user —"
                    + " it is the UUID of the network on NDEx"
                    + " (e.g. \"a7e43e3d-c7f8-11ec-8d17-005056ae23aa\").\n\n"
                    + "If the user has not provided a network ID, ask them for it before calling"
                    + " this tool. Network IDs can be found on the NDEx website by searching for a"
                    + " network and copying the UUID from the network's detail page URL.";

    private static final String NETWORK_ID_DESCRIPTION =
            "The UUID of the network on NDEx"
                    + " (e.g. \"a7e43e3d-c7f8-11ec-8d17-005056ae23aa\")."
                    + " This value is required and must be requested from the user."
                    + " It can typically be obtained from the NDEx website at"
                    + " https://www.ndexbio.org by searching for a network and copying the UUID"
                    + " from the network's detail page URL.";

    private final CyProperty<Properties> cyProperties;
    private final CyApplicationManager appManager;
    private final CyNetworkManager networkManager;
    private final CyNetworkViewManager viewManager;
    private final SynchronousTaskManager<?> syncTaskManager;
    private final LoadNetworkURLTaskFactory loadNetworkURLTaskFactory;

    public LoadNetworkViewTool(
            CyProperty<Properties> cyProperties,
            CyApplicationManager appManager,
            CyNetworkManager networkManager,
            CyNetworkViewManager viewManager,
            SynchronousTaskManager<?> syncTaskManager,
            LoadNetworkURLTaskFactory loadNetworkURLTaskFactory) {
        this.cyProperties = cyProperties;
        this.appManager = appManager;
        this.networkManager = networkManager;
        this.viewManager = viewManager;
        this.syncTaskManager = syncTaskManager;
        this.loadNetworkURLTaskFactory = loadNetworkURLTaskFactory;
    }

    /** Returns the MCP SyncToolSpecification to register with the McpSyncServer. */
    public McpServerFeatures.SyncToolSpecification toSpec() {
        Tool toolDef = Tool.builder()
                .name(TOOL_NAME)
                .description(TOOL_DESCRIPTION)
                .inputSchema(new JsonSchema(
                        "object",
                        Map.of("network-id", Map.of(
                                "type", "string",
                                "description", NETWORK_ID_DESCRIPTION)),
                        List.of("network-id"),
                        null, null, null))
                .build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(toolDef)
                .callHandler(this::handle)
                .build();
    }

    // -- Handler --------------------------------------------------------------

    private CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        String networkId = extractNetworkId(request);
        if (networkId == null) {
            return error(
                    "network-id is required. Please provide an NDEx network UUID"
                            + " (e.g. \"a7e43e3d-c7f8-11ec-8d17-005056ae23aa\").");
        }

        URL ndexUrl;
        try {
            ndexUrl = buildNdexUrl(networkId);
        } catch (MalformedURLException e) {
            return error("Invalid network-id or NDEx base URL. network-id: \"" + networkId + "\"");
        }

        LOGGER.info("Loading NDEx network {} from {}", networkId, ndexUrl);

        CyNetwork loadedNetwork;
        try {
            loadedNetwork = executeLoad(ndexUrl);
        } catch (Exception e) {
            LOGGER.error("Error loading NDEx network {}", networkId, e);
            return error(
                    "Failed to load network from NDEx. The network may not exist or the NDEx"
                            + " server may be unreachable. network-id: \""
                            + networkId + "\", error: " + e.getMessage());
        }

        if (loadedNetwork == null) {
            return error(
                    "Network with id \""
                            + networkId
                            + "\" was not found on NDEx or could not be loaded into Cytoscape.");
        }

        activateNetwork(loadedNetwork);

        String displayName = getDisplayName(loadedNetwork, networkId);
        LOGGER.info("Successfully loaded and activated network \"{}\"", displayName);
        return success("Successfully loaded network \"" + displayName + "\" from NDEx into"
                + " Cytoscape and set it as the current network view.");
    }

    // -- Steps ----------------------------------------------------------------

    private String extractNetworkId(CallToolRequest request) {
        Object value = request.arguments().get("network-id");
        if (!(value instanceof String)) {
            return null;
        }
        String id = ((String) value).trim();
        return id.isEmpty() ? null : id;
    }

    private URL buildNdexUrl(String networkId) throws MalformedURLException {
        String ndexBase = cyProperties
                .getProperties()
                .getProperty("mcp.ndexbaseurl", "https://www.ndexbio.org")
                .trim();
        return new URL(ndexBase + "/v2/network/" + networkId + "/cx2");
    }

    /**
     * Executes the network load task and returns the newly created {@link CyNetwork}.
     *
     * <p>Uses the two-arg {@code execute(TaskIterator, TaskObserver)} overload so that
     * {@link TaskObserver#allFinished} gives a definitive completion signal and
     * {@link TaskObserver#taskFinished} can capture the result directly from
     * {@link ObservableTask#getResults} — avoiding the race condition of diffing the
     * network set immediately after a one-arg {@code execute()} that may return before
     * the network is fully registered.
     *
     * <p>Falls back to a before/after {@link CyNetworkManager#getNetworkSet()} diff
     * if the load task does not implement {@link ObservableTask}.
     */
    private CyNetwork executeLoad(URL ndexUrl) {
        Set<CyNetwork> networksBefore = networkManager.getNetworkSet();
        TaskIterator tasks = loadNetworkURLTaskFactory.createTaskIterator(ndexUrl, null);

        AtomicReference<CyNetwork> observed = new AtomicReference<>();
        AtomicReference<FinishStatus> completionStatus = new AtomicReference<>();

        syncTaskManager.execute(tasks, new TaskObserver() {
            @Override
            public void taskFinished(ObservableTask task) {
                CyNetwork net = task.getResults(CyNetwork.class);
                if (net != null) {
                    observed.compareAndSet(null, net);
                }
            }

            @Override
            public void allFinished(FinishStatus finishStatus) {
                completionStatus.set(finishStatus);
            }
        });

        FinishStatus status = completionStatus.get();
        if (status != null && status.getType() == FinishStatus.Type.FAILED) {
            Exception cause = status.getException();
            throw new RuntimeException(
                    cause != null ? cause.getMessage() : "Network load task failed", cause);
        }

        // Prefer the network captured via ObservableTask; fall back to set diff.
        CyNetwork result = observed.get();
        if (result == null) {
            result = findNewNetwork(networksBefore);
        }
        return result;
    }

    private void activateNetwork(CyNetwork network) {
        appManager.setCurrentNetwork(network);
        Collection<CyNetworkView> views = viewManager.getNetworkViews(network);
        if (!views.isEmpty()) {
            appManager.setCurrentNetworkView(views.iterator().next());
        }
    }

    private String getDisplayName(CyNetwork network, String fallbackId) {
        String name = network.getRow(network).get(CyNetwork.NAME, String.class);
        return name != null ? name : fallbackId;
    }

    private CyNetwork findNewNetwork(Set<CyNetwork> before) {
        for (CyNetwork net : networkManager.getNetworkSet()) {
            if (!before.contains(net)) {
                return net;
            }
        }
        return null;
    }

    // -- Result helpers -------------------------------------------------------

    private static CallToolResult success(String message) {
        return new CallToolResult(List.of(new TextContent(message)), false);
    }

    private static CallToolResult error(String message) {
        return new CallToolResult(List.of(new TextContent(message)), true);
    }
}
