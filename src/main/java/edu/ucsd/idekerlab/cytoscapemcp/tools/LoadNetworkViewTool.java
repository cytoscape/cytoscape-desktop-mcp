package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.io.read.CyNetworkReader;
import org.cytoscape.io.read.InputStreamTaskFactory;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.property.CyProperty;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.FinishStatus;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskManager;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.TaskObserver;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

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

    private static final String TOOL_EXAMPLES =
            "\n\n## Examples\n\n"
                    + "Example 1 — Load a network by UUID (most common):\n"
                    + "{\"network-id\": \"a7e43e3d-c7f8-11ec-8d17-005056ae23aa\"}\n\n"
                    + "Example 2 — Load a different network (minimal):\n"
                    + "{\"network-id\": \"f3b72e5a-2d8c-4f1b-9e6a-8c7d5f4e3b2a\"}";

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
    private final TaskManager<?, ?> taskManager;
    private final InputStreamTaskFactory cxReaderFactory;

    public LoadNetworkViewTool(
            CyProperty<Properties> cyProperties,
            CyApplicationManager appManager,
            CyNetworkManager networkManager,
            CyNetworkViewManager viewManager,
            TaskManager<?, ?> taskManager,
            InputStreamTaskFactory cxReaderFactory) {
        this.cyProperties = cyProperties;
        this.appManager = appManager;
        this.networkManager = networkManager;
        this.viewManager = viewManager;
        this.taskManager = taskManager;
        this.cxReaderFactory = cxReaderFactory;
    }

    /** Returns the MCP SyncToolSpecification to register with the McpSyncServer. */
    public McpServerFeatures.SyncToolSpecification toSpec() {
        Tool toolDef =
                Tool.builder()
                        .name(TOOL_NAME)
                        .description(TOOL_DESCRIPTION + TOOL_EXAMPLES)
                        .inputSchema(
                                new JsonSchema(
                                        "object",
                                        Map.of(
                                                "network-id",
                                                Map.of(
                                                        "type",
                                                        "string",
                                                        "description",
                                                        NETWORK_ID_DESCRIPTION)),
                                        List.of("network-id"),
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
        LOGGER.info("Tool call received: {} params={}", TOOL_NAME, request.arguments());
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
            loadedNetwork = executeLoad(ndexUrl, networkId);
        } catch (Exception e) {
            LOGGER.error("Error loading NDEx network {}", networkId, e);
            return error(
                    "Failed to load network from NDEx. The network may not exist or the NDEx"
                            + " server may be unreachable. network-id: \""
                            + networkId
                            + "\", error: "
                            + e.getMessage());
        }

        if (loadedNetwork == null) {
            return error(
                    "Network with id \""
                            + networkId
                            + "\" was not found on NDEx or could not be loaded into Cytoscape.");
        }

        setCollectionName(loadedNetwork);
        activateNetwork(loadedNetwork);

        String displayName = getDisplayName(loadedNetwork, networkId);
        LOGGER.info("Successfully loaded and activated network \"{}\"", displayName);
        return success(
                "Successfully loaded network \""
                        + displayName
                        + "\" from NDEx into"
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
        String ndexBase =
                cyProperties
                        .getProperties()
                        .getProperty("mcp.ndexbaseurl", "https://www.ndexbio.org")
                        .trim();
        return new URL(ndexBase + "/v2/network/" + networkId);
    }

    /**
     * Downloads the network CX stream from NDEx, parses it with the CX-specific network reader, and
     * registers the resulting network and view with Cytoscape.
     *
     * <p>Uses {@link InputStreamTaskFactory} (OSGi ID {@code cytoscapeCxNetworkReaderFactory}) to
     * obtain the CX reader. This ensures the correct reader is used (the generic {@code
     * CyNetworkReaderManager} selects the wrong reader for CX streams).
     *
     * <p>After the reader finishes, networks are manually registered via {@link
     * CyNetworkManager#addNetwork} and views are built via {@link
     * CyNetworkReader#buildCyNetworkView} then registered via {@link
     * CyNetworkViewManager#addNetworkView}.
     */
    private CyNetwork executeLoad(URL ndexUrl, String networkId) throws IOException {
        InputStream cxStream = openStream(ndexUrl);

        TaskIterator ti = cxReaderFactory.createTaskIterator(cxStream, null);
        CyNetworkReader reader = (CyNetworkReader) ti.next();

        LoadNetworkTask wrapper = new LoadNetworkTask(reader, networkId);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<FinishStatus> completionStatus = new AtomicReference<>();

        taskManager.execute(
                new TaskIterator(wrapper),
                new TaskObserver() {
                    @Override
                    public void taskFinished(ObservableTask task) {
                        // Not needed — we access the reader directly after completion
                    }

                    @Override
                    public void allFinished(FinishStatus finishStatus) {
                        completionStatus.set(finishStatus);
                        latch.countDown();
                    }
                });

        try {
            if (!latch.await(120, TimeUnit.SECONDS)) {
                throw new RuntimeException("Network load timed out after 120 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Network load interrupted", e);
        }

        FinishStatus status = completionStatus.get();
        if (status != null && status.getType() == FinishStatus.Type.FAILED) {
            Exception cause = status.getException();
            throw new RuntimeException(
                    cause != null ? cause.getMessage() : "Network load task failed", cause);
        }

        CyNetwork[] networks = reader.getNetworks();
        if (networks == null || networks.length == 0) {
            return null;
        }

        CyNetwork loaded = networks[0];

        // Register the network with Cytoscape (creates the collection)
        networkManager.addNetwork(loaded);

        // Build and register the view (buildCyNetworkView does NOT auto-register)
        CyNetworkView view = reader.buildCyNetworkView(loaded);
        if (view != null) {
            viewManager.addNetworkView(view);
        }

        return loaded;
    }

    /** Downloads the CX stream from the given URL. Package-private for test overriding. */
    InputStream openStream(URL url) throws IOException {
        return url.openStream();
    }

    /**
     * Sets the root network (collection) name to match the loaded sub-network name, so the Network
     * panel displays the proper name instead of a UUID.
     */
    private void setCollectionName(CyNetwork network) {
        if (network instanceof CySubNetwork) {
            CyRootNetwork root = ((CySubNetwork) network).getRootNetwork();
            String subName = network.getRow(network).get(CyNetwork.NAME, String.class);
            if (subName != null) {
                root.getRow(root).set(CyNetwork.NAME, subName);
            }
        }
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

    // -- Inner task class ---------------------------------------------------

    /**
     * Wraps the CX network reader as an {@link AbstractTask} so that execution via {@link
     * TaskManager} causes a "Load NDEx Network" entry to appear in Cytoscape's Task History panel.
     * The title and status messages set on the {@link TaskMonitor} are captured by Cytoscape's
     * TaskHistoryWindow.
     */
    private static final class LoadNetworkTask extends AbstractTask {
        private final CyNetworkReader reader;
        private final String networkId;

        LoadNetworkTask(CyNetworkReader reader, String networkId) {
            this.reader = reader;
            this.networkId = networkId;
        }

        @Override
        public void run(TaskMonitor monitor) throws Exception {
            monitor.setTitle("[MCP Tool Invocation] Load Network View");
            monitor.setStatusMessage("Downloading NDEx network " + networkId + " from NDEx...");
            monitor.setProgress(-1); // indeterminate while downloading
            reader.run(monitor);
            monitor.setProgress(1.0);
            monitor.setStatusMessage("Network downloaded.");
        }
    }
}
