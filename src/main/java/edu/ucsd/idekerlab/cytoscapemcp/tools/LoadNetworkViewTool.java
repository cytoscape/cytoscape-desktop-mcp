package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.io.read.CyNetworkReader;
import org.cytoscape.io.read.InputStreamTaskFactory;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.property.CyProperty;
import org.cytoscape.task.read.LoadNetworkFileTaskFactory;
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
 * MCP tool that loads a network into Cytoscape from NDEx (by UUID), a native network format file,
 * or a tabular data file with column mapping. Creates a new network collection and view, and sets
 * it as the current network.
 */
public class LoadNetworkViewTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadNetworkViewTool.class);

    private static final String TOOL_NAME = "load_cytoscape_network_view";

    private static final String TOOL_DESCRIPTION =
            "Load a network into Cytoscape from NDEx (by UUID), a native network format file,"
                    + " or a tabular data file with column mapping. Creates a new network collection"
                    + " and view, and sets it as the current network.";

    private static final String TOOL_EXAMPLES =
            "\n\n## Examples\n\n"
                    + "Example 1 — Load from NDEx:\n"
                    + "{\"source\": \"ndex\", \"network_id\":"
                    + " \"a7e43e3d-c7f8-11ec-8d17-005056ae23aa\"}\n\n"
                    + "Example 2 — Load a native format file:\n"
                    + "{\"source\": \"network-file\", \"file_path\":"
                    + " \"/path/to/network.sif\"}\n\n"
                    + "Example 3 — Load tabular data:\n"
                    + "{\"source\": \"tabular-file\", \"file_path\":"
                    + " \"/path/to/data.csv\", \"source_column\": \"Gene_A\","
                    + " \"target_column\": \"Gene_B\", \"delimiter_char_code\": 44,"
                    + " \"use_header_row\": true}";

    private final ObjectMapper mapper = new ObjectMapper();

    private final CyProperty<Properties> cyProperties;
    private final CyApplicationManager appManager;
    private final CyNetworkManager networkManager;
    private final CyNetworkViewManager viewManager;
    private final TaskManager<?, ?> taskManager;
    private final InputStreamTaskFactory cxReaderFactory;
    private final LoadNetworkFileTaskFactory loadFileTaskFactory;

    public LoadNetworkViewTool(
            CyProperty<Properties> cyProperties,
            CyApplicationManager appManager,
            CyNetworkManager networkManager,
            CyNetworkViewManager viewManager,
            TaskManager<?, ?> taskManager,
            InputStreamTaskFactory cxReaderFactory,
            LoadNetworkFileTaskFactory loadFileTaskFactory) {
        this.cyProperties = cyProperties;
        this.appManager = appManager;
        this.networkManager = networkManager;
        this.viewManager = viewManager;
        this.taskManager = taskManager;
        this.cxReaderFactory = cxReaderFactory;
        this.loadFileTaskFactory = loadFileTaskFactory;
    }

    /** Returns the MCP SyncToolSpecification to register with the McpSyncServer. */
    public McpServerFeatures.SyncToolSpecification toSpec() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("source", Map.of(
                "type", "string",
                "description", "The data source type. Must be one of: 'ndex', 'network-file',"
                        + " 'tabular-file'.",
                "enum", List.of("ndex", "network-file", "tabular-file")));

        properties.put("network_id", Map.of(
                "type", "string",
                "description", "The UUID of the network on NDEx"
                        + " (e.g. \"a7e43e3d-c7f8-11ec-8d17-005056ae23aa\")."
                        + " Required when source='ndex'."));

        properties.put("file_path", Map.of(
                "type", "string",
                "description", "Absolute path to the file to import."
                        + " Required when source='network-file' or 'tabular-file'."));

        properties.put("source_column", Map.of(
                "type", "string",
                "description", "Column name for the source node."
                        + " Required when source='tabular-file'."));

        properties.put("target_column", Map.of(
                "type", "string",
                "description", "Column name for the target node."
                        + " Required when source='tabular-file'."));

        properties.put("interaction_column", Map.of(
                "type", "string",
                "description", "Column name for the edge interaction type."
                        + " Optional for source='tabular-file'."));

        properties.put("delimiter_char_code", Map.of(
                "type", "integer",
                "description", "ASCII character code of the column delimiter"
                        + " (e.g. 44 for comma, 9 for tab)."
                        + " Required for non-Excel tabular files."));

        properties.put("use_header_row", Map.of(
                "type", "boolean",
                "description", "Whether the first row of the file contains column headers."
                        + " Required when source='tabular-file'."));

        properties.put("excel_sheet", Map.of(
                "type", "string",
                "description", "Name of the Excel sheet containing the network data."
                        + " Required for Excel tabular files."));

        properties.put("node_attributes_sheet", Map.of(
                "type", "string",
                "description", "Name of an Excel sheet containing node attribute data."
                        + " Optional for Excel tabular files."));

        properties.put("node_attributes_key_column", Map.of(
                "type", "string",
                "description", "Column name in the node attributes sheet that contains the node"
                        + " ID for joining. Used with node_attributes_sheet."));

        Map<String, Object> stringArrayItems = Map.of("type", "string");

        Map<String, Object> sourceColsProp = new LinkedHashMap<>();
        sourceColsProp.put("type", "array");
        sourceColsProp.put("items", stringArrayItems);
        sourceColsProp.put("description", "Array of column names from the node attributes sheet"
                + " to map as source node properties.");
        properties.put("node_attributes_source_columns", sourceColsProp);

        Map<String, Object> targetColsProp = new LinkedHashMap<>();
        targetColsProp.put("type", "array");
        targetColsProp.put("items", stringArrayItems);
        targetColsProp.put("description", "Array of column names from the node attributes sheet"
                + " to map as target node properties.");
        properties.put("node_attributes_target_columns", targetColsProp);

        Tool toolDef =
                Tool.builder()
                        .name(TOOL_NAME)
                        .description(TOOL_DESCRIPTION + TOOL_EXAMPLES)
                        .inputSchema(
                                new JsonSchema(
                                        "object",
                                        properties,
                                        List.of("source"),
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
        String source = extractString(request, "source");
        if (source == null) {
            return error("'source' is required. Must be 'ndex', 'network-file',"
                    + " or 'tabular-file'.");
        }
        switch (source) {
            case "ndex":
                return handleNdexImport(request);
            case "network-file":
                return handleNetworkFileImport(request);
            case "tabular-file":
                return handleTabularImport(request);
            default:
                return error("Invalid source: '" + source + "'. Must be 'ndex',"
                        + " 'network-file', or 'tabular-file'.");
        }
    }

    // -- Source handlers -------------------------------------------------------

    private CallToolResult handleNdexImport(CallToolRequest request) {
        String networkId = extractString(request, "network_id");
        if (networkId == null) {
            return error(
                    "'network_id' is required when source='ndex'. Please provide an NDEx"
                            + " network UUID (e.g. \"a7e43e3d-c7f8-11ec-8d17-005056ae23aa\").");
        }

        URL ndexUrl;
        try {
            ndexUrl = buildNdexUrl(networkId);
        } catch (MalformedURLException e) {
            return error("Invalid network_id or NDEx base URL. network_id: \"" + networkId + "\"");
        }

        LOGGER.info("Loading NDEx network {} from {}", networkId, ndexUrl);

        CyNetwork loadedNetwork;
        try {
            loadedNetwork = executeLoad(ndexUrl, networkId);
        } catch (Exception e) {
            LOGGER.error("Error loading NDEx network {}", networkId, e);
            return error(
                    "Failed to load network from NDEx. The network may not exist or the NDEx"
                            + " server may be unreachable. network_id: \""
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

        return buildSuccessResponse(loadedNetwork, networkId);
    }

    private CallToolResult handleNetworkFileImport(CallToolRequest request) {
        String filePath = extractString(request, "file_path");
        if (filePath == null) {
            return error("'file_path' is required when source='network-file'."
                    + " Provide the absolute path to a network file"
                    + " (.sif, .gml, .xgmml, .cx, .cx2, .graphml, .sbml, .owl, .biopax).");
        }

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            return error("File not found: " + filePath);
        }

        LOGGER.info("Loading network from file: {}", filePath);

        try {
            executeFileLoad(file);
        } catch (Exception e) {
            LOGGER.error("Error loading network from file {}", filePath, e);
            return error("Failed to load network from file: " + filePath
                    + ", error: " + e.getMessage());
        }

        CyNetwork loadedNetwork = appManager.getCurrentNetwork();
        if (loadedNetwork == null) {
            return error("No network was created after loading file: " + filePath);
        }

        return buildSuccessResponse(loadedNetwork, file.getName());
    }

    private void executeFileLoad(File file) {
        TaskIterator ti = loadFileTaskFactory.createTaskIterator(file);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<FinishStatus> completionStatus = new AtomicReference<>();

        taskManager.execute(
                ti,
                new TaskObserver() {
                    @Override
                    public void taskFinished(ObservableTask task) {}

                    @Override
                    public void allFinished(FinishStatus finishStatus) {
                        completionStatus.set(finishStatus);
                        latch.countDown();
                    }
                });

        try {
            if (!latch.await(120, TimeUnit.SECONDS)) {
                throw new RuntimeException("Network file load timed out after 120 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Network file load interrupted", e);
        }

        FinishStatus status = completionStatus.get();
        if (status != null && status.getType() == FinishStatus.Type.FAILED) {
            Exception cause = status.getException();
            throw new RuntimeException(
                    cause != null ? cause.getMessage() : "Network file load task failed", cause);
        }
    }

    private CallToolResult handleTabularImport(CallToolRequest request) {
        return error("source='tabular-file' is not yet implemented. (Coming in Task 9)");
    }

    // -- Steps ----------------------------------------------------------------

    private String extractString(CallToolRequest request, String key) {
        Object value = request.arguments().get(key);
        if (!(value instanceof String)) {
            return null;
        }
        String s = ((String) value).trim();
        return s.isEmpty() ? null : s;
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

    private CallToolResult buildSuccessResponse(CyNetwork network, String fallbackName) {
        try {
            String networkName = getDisplayName(network, fallbackName);
            ObjectNode result = mapper.createObjectNode();
            result.put("status", "success");
            result.put("network_suid", network.getSUID());
            result.put("node_count", network.getNodeCount());
            result.put("edge_count", network.getEdgeCount());
            result.put("network_name", networkName);
            return success(mapper.writeValueAsString(result));
        } catch (Exception e) {
            return success("Network loaded successfully.");
        }
    }

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
