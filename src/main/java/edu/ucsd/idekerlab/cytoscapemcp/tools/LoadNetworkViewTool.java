package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.idekerlab.cytoscapemcp.McpSchema;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.io.read.CyNetworkReader;
import org.cytoscape.io.read.CyNetworkReaderManager;
import org.cytoscape.io.read.InputStreamTaskFactory;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.property.CyProperty;
import org.cytoscape.view.layout.CyLayoutAlgorithm;
import org.cytoscape.view.layout.CyLayoutAlgorithmManager;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewFactory;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.FinishStatus;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskManager;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.TaskObserver;
import org.cytoscape.work.util.ListSingleSelection;

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

    private static final String TOOL_TITLE = "Load Cytoscape Desktop Network";

    private static final String TOOL_DESCRIPTION =
            "Load a network into Cytoscape Desktop from one of multiple sources such as NDEx (by Network Id), a network formatted file,"
                    + " or a tabular formatted file with column mapping. Creates a new network root as a new collection"
                    + " and view, and sets it as the current network on Cytoscape Desktop.";

    private static final String TOOL_EXAMPLES =
            "\n\n## Examples\n\n"
                    + "Example 1 — Load NDEx network into cytoscape desktop:\n"
                    + "{\"source\": \"ndex\", \"network_id\":"
                    + " \"a7e43e3d-c7f8-11ec-8d17-005056ae23aa\"}\n\n"
                    + "Example 2 — Load network file into cytoscape desktop:\n"
                    + "{\"source\": \"network-file\", \"file_path\":"
                    + " \"/path/to/network.sif\"}\n\n"
                    + "Example 3 — Load tabular file into cytoscape desktop:\n"
                    + "{\"source\": \"tabular-file\", \"file_path\":"
                    + " \"/path/to/data.csv\", \"source_column\": \"Gene_A\","
                    + " \"target_column\": \"Gene_B\", \"delimiter_char_code\": 44,"
                    + " \"use_header_row\": true}";

    static final String INPUT_SCHEMA =
            McpSchema.toJson(
                    McpSchema.InputSchema.builder()
                            .required("source")
                            .property(
                                    "source",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Required. Determines which import path to use — the"
                                                    + " remaining parameters depend on this value."
                                                    + " Must be one of: 'ndex' (load from NDEx by"
                                                    + " Network ID as UUID), 'network-file' (load a native network"
                                                    + " format file such as SIF, GML, XGMML, CX,"
                                                    + " CX2, GraphML, SBML, BioPAX),"
                                                    + " 'tabular-file' (load a delimited or Excel"
                                                    + " file with column mapping).",
                                            List.of("ndex", "network-file", "tabular-file")))
                            .property(
                                    "network_id",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Optional. NDEx network Id as UUID (e.g."
                                                    + " 'a7e43e3d-c7f8-11ec-8d17-005056ae23aa')."
                                                    + " Required when source='ndex'. Ignored"
                                                    + " otherwise."))
                            .property(
                                    "file_path",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Optional. Absolute path to the file to import."
                                                    + " Required when source='network-file' or"
                                                    + " source='tabular-file'. Ignored when"
                                                    + " source='ndex'."))
                            .property(
                                    "source_column",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Optional. Column name for the source (from) node."
                                                    + " Required when source='tabular-file'."))
                            .property(
                                    "target_column",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Optional. Column name for the target (to) node."
                                                    + " Required when source='tabular-file'."))
                            .property(
                                    "interaction_column",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Optional. Column name for the edge interaction type."
                                                    + " Applicable when source='tabular-file'."))
                            .property(
                                    "delimiter_char_code",
                                    new McpSchema.InputProperty(
                                            "integer",
                                            "Optional. ASCII code of the column delimiter (e.g."
                                                    + " 44=comma, 9=tab). Required when"
                                                    + " source='tabular-file' and file is not Excel."
                                                    + " Ignored for Excel files."))
                            .property(
                                    "use_header_row",
                                    new McpSchema.InputProperty(
                                            "boolean",
                                            "Optional. Whether the first row contains column"
                                                    + " headers. If false, ordinal column names are"
                                                    + " generated. Required when"
                                                    + " source='tabular-file'."))
                            .property(
                                    "excel_sheet",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Optional. Name of the Excel sheet containing the"
                                                    + " network edge data. Required when"
                                                    + " source='tabular-file' and file is Excel."
                                                    + " Ignored for non-Excel files."))
                            .property(
                                    "node_attributes_sheet",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Optional. Name of a second Excel sheet containing"
                                                    + " node attribute columns to join onto the"
                                                    + " network nodes. Applicable for Excel tabular"
                                                    + " files."))
                            .property(
                                    "node_attributes_sheet_target_key_column",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Optional. Column name in the node attributes sheet"
                                                    + " whose values match target-node IDs in the"
                                                    + " network sheet. Used to join attributes onto"
                                                    + " target nodes. Required when"
                                                    + " node_attributes_sheet is provided."))
                            .property(
                                    "node_attributes_sheet_source_key_column",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Optional. Column name in the node attributes sheet"
                                                    + " whose values match source-node IDs in the"
                                                    + " network sheet. Used to join attributes onto"
                                                    + " source nodes. Required when"
                                                    + " node_attributes_sheet is provided."))
                            .property(
                                    "node_attributes_source_columns",
                                    new McpSchema.InputProperty(
                                            "array",
                                            "Optional. Array of column names from the node_attributes_sheet"
                                                    + " (or main sheet) to attach as properties on"
                                                    + " source nodes.",
                                            new McpSchema.InputProperty("string", null),
                                            null))
                            .property(
                                    "node_attributes_target_columns",
                                    new McpSchema.InputProperty(
                                            "array",
                                            "Optional. Array of column names from the node_attributes_sheet"
                                                    + " (or main sheet) to attach as properties on"
                                                    + " target nodes.",
                                            new McpSchema.InputProperty("string", null),
                                            null))
                            .build());

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record LoadNetworkViewCallResult(
            @JsonPropertyDescription("Result status, e.g. 'success'.") @JsonProperty("status")
                    String status,
            @JsonPropertyDescription("Unique SUID of the loaded network in Cytoscape Desktop.")
                    @JsonProperty("network_suid")
                    long networkSuid,
            @JsonPropertyDescription("Number of nodes in the loaded network.")
                    @JsonProperty("node_count")
                    int nodeCount,
            @JsonPropertyDescription("Number of edges in the loaded network.")
                    @JsonProperty("edge_count")
                    int edgeCount,
            @JsonPropertyDescription(
                            "Name of the loaded network as shown in the Cytoscape Desktop Network"
                                    + " panel.")
                    @JsonProperty("network_name")
                    String networkName,
            @JsonPropertyDescription(
                            "True if node attributes were successfully joined onto the network."
                                    + " Present only when node_attributes_* parameters were"
                                    + " supplied.")
                    @JsonProperty("node_attributes_imported")
                    Boolean nodeAttributesImported,
            @JsonPropertyDescription(
                            "Non-fatal warning message, e.g. when the network loaded but some"
                                    + " attributes could not be mapped. Present only when a"
                                    + " warning occurred.")
                    @JsonProperty("warning")
                    String warning) {}

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(LoadNetworkViewCallResult.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CyProperty<Properties> cyProperties;
    private final CyApplicationManager appManager;
    private final CyNetworkManager networkManager;
    private final CyNetworkViewManager viewManager;
    private final TaskManager<?, ?> taskManager;
    private final InputStreamTaskFactory cxReaderFactory;
    private final CyNetworkReaderManager networkReaderManager;
    private final CyNetworkFactory networkFactory;
    private final CyNetworkViewFactory networkViewFactory;
    private final CyLayoutAlgorithmManager layoutAlgorithmManager;

    public LoadNetworkViewTool(
            CyProperty<Properties> cyProperties,
            CyApplicationManager appManager,
            CyNetworkManager networkManager,
            CyNetworkViewManager viewManager,
            TaskManager<?, ?> taskManager,
            InputStreamTaskFactory cxReaderFactory,
            CyNetworkReaderManager networkReaderManager,
            CyNetworkFactory networkFactory,
            CyNetworkViewFactory networkViewFactory,
            CyLayoutAlgorithmManager layoutAlgorithmManager) {
        this.cyProperties = cyProperties;
        this.appManager = appManager;
        this.networkManager = networkManager;
        this.viewManager = viewManager;
        this.taskManager = taskManager;
        this.cxReaderFactory = cxReaderFactory;
        this.networkReaderManager = networkReaderManager;
        this.networkFactory = networkFactory;
        this.networkViewFactory = networkViewFactory;
        this.layoutAlgorithmManager = layoutAlgorithmManager;
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
        LOGGER.info("Tool call received: {} params={}", TOOL_NAME, request.arguments());
        String source = extractString(request, "source");
        if (source == null) {
            return error(
                    "'source' is required. Must be 'ndex', 'network-file',"
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
                return error(
                        "Invalid source: '"
                                + source
                                + "'. Must be 'ndex',"
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
            return error(
                    "'file_path' is required when source='network-file'."
                            + " Provide the absolute path to a network file"
                            + " (.sif, .gml, .xgmml, .cx, .cx2, .graphml, .sbml, .owl, .biopax).");
        }

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            return error("File not found: " + filePath);
        }

        LOGGER.info("Loading network from file: {}", filePath);

        CyNetwork loadedNetwork;
        try {
            loadedNetwork = executeFileLoadViaReader(file);
        } catch (Exception e) {
            LOGGER.error("Error loading network from file {}", filePath, e);
            return error(
                    "Failed to load network from file: " + filePath + ", error: " + e.getMessage());
        }

        if (loadedNetwork == null) {
            LOGGER.warn("No network was created after loading file: {}", filePath);
            return error("No network was created after loading file: " + filePath);
        }

        // If the reader produced a generic/empty name, fall back to the source filename.
        String existingName = loadedNetwork.getRow(loadedNetwork).get(CyNetwork.NAME, String.class);
        if (existingName == null
                || existingName.isBlank()
                || existingName.equalsIgnoreCase("network")) {
            String baseName = baseNameWithoutExtension(file);
            loadedNetwork.getRow(loadedNetwork).set(CyNetwork.NAME, baseName);
        }

        setCollectionName(loadedNetwork);
        activateNetwork(loadedNetwork);
        return buildSuccessResponse(loadedNetwork, file.getName());
    }

    /**
     * Loads a network file by obtaining a format-specific {@link CyNetworkReader} from {@link
     * CyNetworkReaderManager}, executing it via the task manager, and returning the first loaded
     * network.
     *
     * <p>The reader is wrapped in {@link LoadNetworkTask} — an {@link AbstractTask} with no
     * {@code @Tunable} fields — so the task manager never scans the reader's own tunables. Without
     * this wrapper the GUI task manager detects the reader's {@code targetNetworkCollection}
     * tunable and shows a modal "Import Network" dialog. The wrapper also ensures the reader's
     * {@code parent} field stays {@code null}, which tells every built-in reader to create a new
     * independent root network rather than appending to the currently-active collection.
     */
    private CyNetwork executeFileLoadViaReader(File file) throws IOException {
        CyNetworkReader reader = networkReaderManager.getReader(file.toURI(), file.getName());
        if (reader == null) {
            throw new IOException(
                    "No compatible reader found for file: "
                            + file.getName()
                            + ". Supported formats: SIF, XGMML, GraphML, GML, CX, CX2, SBML,"
                            + " BioPAX.");
        }

        LOGGER.info(
                "Loading file {} using reader: {}",
                file.getName(),
                reader.getClass().getSimpleName());

        // Clear any @Tunable CyNetwork field on the reader so it creates a new independent root
        // network. The reader constructor may have captured the currently-active network as the
        // default parent collection; nulling it here forces "create new collection" behavior.
        forceNewCollectionOnReader(reader);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<FinishStatus> completionStatus = new AtomicReference<>();

        taskManager.execute(
                new TaskIterator(new LoadNetworkTask(reader, file.getName())),
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

        CyNetwork[] networks = reader.getNetworks();
        if (networks == null || networks.length == 0) {
            return null;
        }

        CyNetwork loaded = networks[0];
        networkManager.addNetwork(loaded);

        CyNetworkView view = reader.buildCyNetworkView(loaded);
        if (view != null) {
            viewManager.addNetworkView(view);
        }

        return loaded;
    }

    private CallToolResult handleTabularImport(CallToolRequest request) {
        // -- Parameter extraction & validation --------------------------------
        String filePath = extractString(request, "file_path");
        if (filePath == null) {
            return error(
                    "'file_path' is required when source='tabular-file'."
                            + " Provide the absolute path to a CSV, TSV, or Excel file.");
        }

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            return error("File not found: " + filePath);
        }

        String sourceCol = extractString(request, "source_column");
        String targetCol = extractString(request, "target_column");
        if (sourceCol == null || targetCol == null) {
            return error(
                    "'source_column' and 'target_column' are required when"
                            + " source='tabular-file'.");
        }

        Boolean useHeaderRow = extractBoolean(request, "use_header_row");
        if (useHeaderRow == null) {
            return error("'use_header_row' is required when source='tabular-file'.");
        }

        String excelSheet = extractString(request, "excel_sheet");
        Integer delimiterCharCode = extractInteger(request, "delimiter_char_code");
        if (excelSheet == null && delimiterCharCode == null) {
            return error(
                    "'delimiter_char_code' is required for non-Excel tabular files."
                            + " Provide the ASCII code of the delimiter (e.g. 44 for comma, 9 for"
                            + " tab).");
        }

        String interactionCol = extractString(request, "interaction_column");
        String nodeAttrSheet = extractString(request, "node_attributes_sheet");
        String nodeAttrSheetSourceKeyCol =
                extractString(request, "node_attributes_sheet_source_key_column");
        String nodeAttrSheetTargetKeyCol =
                extractString(request, "node_attributes_sheet_target_key_column");

        @SuppressWarnings("unchecked")
        List<String> nodeAttrSourceCols =
                (List<String>) request.arguments().get("node_attributes_source_columns");
        @SuppressWarnings("unchecked")
        List<String> nodeAttrTargetCols =
                (List<String>) request.arguments().get("node_attributes_target_columns");
        if (nodeAttrSourceCols == null) nodeAttrSourceCols = Collections.emptyList();
        if (nodeAttrTargetCols == null) nodeAttrTargetCols = Collections.emptyList();

        LOGGER.info(
                "Tabular import: file={} source={} target={} excelSheet={} delimiter={}",
                new Object[] {filePath, sourceCol, targetCol, excelSheet, delimiterCharCode});

        // -- Parse primary edge data ------------------------------------------
        List<Map<String, String>> rows;
        try {
            rows = parseTabularRows(file, delimiterCharCode, useHeaderRow, excelSheet);
        } catch (Exception e) {
            return error("Failed to read tabular file: " + e.getMessage());
        }

        if (rows.isEmpty()) {
            return error("No data rows found in file: " + filePath);
        }

        // Determine which columns are node-attribute columns (to exclude from edge attrs)
        Set<String> nodeAttrColSet = new java.util.HashSet<>();
        nodeAttrColSet.addAll(nodeAttrSourceCols);
        nodeAttrColSet.addAll(nodeAttrTargetCols);

        // -- Build CyNetwork from rows -----------------------------------------
        // CyNetworkFactory.createNetwork() always produces a new root network (collection).
        // Setting the name before registration and calling setCollectionName() ensures both the
        // sub-network and the collection header in the Network panel show the source filename.
        CyNetwork network = networkFactory.createNetwork();
        network.getRow(network).set(CyNetwork.NAME, baseNameWithoutExtension(file));

        Map<String, CyNode> nodeMap = new LinkedHashMap<>();
        CyTable edgeTable = network.getDefaultEdgeTable();

        final String finalSourceCol = sourceCol;
        final String finalTargetCol = targetCol;
        final String finalInteractionCol = interactionCol;

        for (Map<String, String> row : rows) {
            String srcName = row.get(finalSourceCol);
            String tgtName = row.get(finalTargetCol);
            if (srcName == null || tgtName == null) {
                continue; // skip rows missing required columns
            }

            CyNode srcNode =
                    nodeMap.computeIfAbsent(
                            srcName,
                            k -> {
                                CyNode n = network.addNode();
                                network.getRow(n).set(CyNetwork.NAME, k);
                                return n;
                            });
            CyNode tgtNode =
                    nodeMap.computeIfAbsent(
                            tgtName,
                            k -> {
                                CyNode n = network.addNode();
                                network.getRow(n).set(CyNetwork.NAME, k);
                                return n;
                            });

            CyEdge edge = network.addEdge(srcNode, tgtNode, true);
            CyRow edgeRow = network.getRow(edge);

            String interaction =
                    (finalInteractionCol != null) ? row.get(finalInteractionCol) : null;
            if (interaction != null) {
                edgeRow.set(CyEdge.INTERACTION, interaction);
                edgeRow.set(CyNetwork.NAME, srcName + " (" + interaction + ") " + tgtName);
            } else {
                edgeRow.set(CyNetwork.NAME, srcName + " () " + tgtName);
            }

            // Add remaining columns as edge attributes
            for (Map.Entry<String, String> entry : row.entrySet()) {
                String col = entry.getKey();
                if (col.equals(finalSourceCol)
                        || col.equals(finalTargetCol)
                        || col.equals(finalInteractionCol)
                        // Skip node attribute columns if non-excel or is excel and attribs are on
                        // same sheet)
                        || (nodeAttrColSet.contains(col)
                                && (excelSheet == null || excelSheet.equals(nodeAttrSheet)))) {
                    continue;
                }
                createColumnIfAbsent(edgeTable, col);
                edgeRow.set(col, entry.getValue());
            }
        }

        // -- Register network and create view ----------------------------------
        networkManager.addNetwork(network);
        setCollectionName(network);

        CyNetworkView view = networkViewFactory.createNetworkView(network);
        viewManager.addNetworkView(view);
        applyDefaultLayout(view);
        appManager.setCurrentNetwork(network);
        appManager.setCurrentNetworkView(view);

        // -- Secondary node attribute import ----------------------------------
        Boolean nodeAttrsImported = null;
        String warning = null;

        boolean shouldImportNodeAttrs =
                nodeAttrSheet != null
                        || !nodeAttrSourceCols.isEmpty()
                        || !nodeAttrTargetCols.isEmpty();
        if (shouldImportNodeAttrs) {
            NodeAttrImportResult result =
                    importNodeAttributes(
                            network,
                            file,
                            rows,
                            nodeAttrSheet,
                            delimiterCharCode,
                            useHeaderRow,
                            nodeAttrSheetSourceKeyCol,
                            nodeAttrSheetTargetKeyCol,
                            sourceCol,
                            targetCol,
                            nodeAttrSourceCols,
                            nodeAttrTargetCols);
            nodeAttrsImported = result.imported();
            warning = result.warning();
        }

        return buildTabularSuccessResponse(network, file.getName(), nodeAttrsImported, warning);
    }

    // -- Tabular parsing helpers -----------------------------------------------

    /** Holds the outcome of a secondary node-attribute import pass. */
    private record NodeAttrImportResult(Boolean imported, String warning) {}

    /**
     * Imports node attributes into the node table of an already-built network. For Excel files (
     * {@code nodeAttrSheet != null}), attribute rows are read from the specified sheet and keyed by
     * the per-node-type key columns ({@code nodeAttrSheetSourceKeyCol} / {@code
     * nodeAttrSheetTargetKeyCol}). For non-Excel files ({@code nodeAttrSheet == null}), the primary
     * edge rows are reused and keyed by the existing source/target column values, which already
     * correspond to node names in the network.
     *
     * <p>Source and target attribute imports run independently — either or both may be provided.
     */
    private NodeAttrImportResult importNodeAttributes(
            CyNetwork network,
            File file,
            List<Map<String, String>> rows,
            String nodeAttrSheet,
            Integer delimiterCharCode,
            boolean useHeaderRow,
            String nodeAttrSheetSourceKeyCol,
            String nodeAttrSheetTargetKeyCol,
            String sourceCol,
            String targetCol,
            List<String> nodeAttrSourceCols,
            List<String> nodeAttrTargetCols) {

        if (nodeAttrSourceCols.isEmpty() && nodeAttrTargetCols.isEmpty()) {
            return new NodeAttrImportResult(null, null);
        }

        // Build node name → CyNode lookup from the network
        Map<String, CyNode> nameToNode = new LinkedHashMap<>();
        for (CyNode n : network.getNodeList()) {
            String name = network.getRow(n).get(CyNetwork.NAME, String.class);
            if (name != null) nameToNode.put(name, n);
        }

        // Load attribute rows from separate Excel sheet or reuse primary rows for non-Excel
        List<Map<String, String>> attrRows;
        try {
            if (nodeAttrSheet != null) {
                attrRows = parseTabularRows(file, delimiterCharCode, useHeaderRow, nodeAttrSheet);
            } else {
                attrRows = rows;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to read node attribute data: {}", e.getMessage());
            attrRows = Collections.emptyList();
        }

        CyTable nodeTable = network.getDefaultNodeTable();
        int matchCount = 0;

        // For Excel: key cols are from the node attributes sheet; for non-Excel: node identity is
        // the source/target column value used during edge construction
        String srcKeyCol = (nodeAttrSheet != null) ? nodeAttrSheetSourceKeyCol : sourceCol;
        String tgtKeyCol = (nodeAttrSheet != null) ? nodeAttrSheetTargetKeyCol : targetCol;

        // Source node attribute import
        if (!nodeAttrSourceCols.isEmpty() && srcKeyCol != null) {
            for (Map<String, String> attrRow : attrRows) {
                String keyVal = attrRow.get(srcKeyCol);
                if (keyVal == null) continue;
                CyNode matchedNode = nameToNode.get(keyVal);
                if (matchedNode == null) continue;
                matchCount++;
                CyRow nodeRow = network.getRow(matchedNode);
                for (String col : nodeAttrSourceCols) {
                    String val = attrRow.get(col);
                    if (val != null) {
                        createColumnIfAbsent(nodeTable, col);
                        nodeRow.set(col, val);
                    }
                }
            }
        }

        // Target node attribute import
        if (!nodeAttrTargetCols.isEmpty() && tgtKeyCol != null) {
            for (Map<String, String> attrRow : attrRows) {
                String keyVal = attrRow.get(tgtKeyCol);
                if (keyVal == null) continue;
                CyNode matchedNode = nameToNode.get(keyVal);
                if (matchedNode == null) continue;
                matchCount++;
                CyRow nodeRow = network.getRow(matchedNode);
                for (String col : nodeAttrTargetCols) {
                    String val = attrRow.get(col);
                    if (val != null) {
                        createColumnIfAbsent(nodeTable, col);
                        nodeRow.set(col, val);
                    }
                }
            }
        }

        if (matchCount == 0 && !attrRows.isEmpty()) {
            return new NodeAttrImportResult(
                    false, "No matching node IDs found between key column and network node names.");
        }
        return new NodeAttrImportResult(matchCount > 0, null);
    }

    /**
     * Reads all data rows from a tabular file into a list of column-name → value maps. For Excel
     * files, specify {@code excelSheet}; for text files, specify {@code delimiterCharCode}.
     *
     * @param file the file to read
     * @param delimiterCharCode ASCII code of the delimiter (ignored for Excel)
     * @param useHeaderRow if true, first row is used as column names; if false, ordinal names are
     *     generated ("Column 1", "Column 2", ...)
     * @param excelSheet name of the Excel sheet to read; null for text files
     */
    private List<Map<String, String>> parseTabularRows(
            File file, Integer delimiterCharCode, boolean useHeaderRow, String excelSheet)
            throws IOException {
        if (excelSheet != null) {
            return parseExcelRows(file, useHeaderRow, excelSheet);
        } else {
            char delimiter = delimiterCharCode != null ? (char) delimiterCharCode.intValue() : ',';
            return parseTextRows(file, delimiter, useHeaderRow);
        }
    }

    private List<Map<String, String>> parseExcelRows(
            File file, boolean useHeaderRow, String sheetName) throws IOException {
        try (Workbook wb = WorkbookFactory.create(file)) {
            Sheet sheet = wb.getSheet(sheetName);
            if (sheet == null) {
                throw new IOException("Sheet '" + sheetName + "' not found in workbook.");
            }

            List<String> headers = new ArrayList<>();
            List<Map<String, String>> result = new ArrayList<>();

            int startDataRow = 0;
            if (useHeaderRow && sheet.getPhysicalNumberOfRows() > 0) {
                Row headerRow = sheet.getRow(0);
                if (headerRow != null) {
                    for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                        Cell cell = headerRow.getCell(c);
                        headers.add(cell != null ? cellStringValue(cell) : "Column " + (c + 1));
                    }
                }
                startDataRow = 1;
            }

            for (int r = startDataRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                // If no header row, generate ordinal names from first data row's cell count
                if (headers.isEmpty()) {
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        headers.add("Column " + (c + 1));
                    }
                }

                Map<String, String> rowMap = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.getCell(c);
                    rowMap.put(headers.get(c), cell != null ? cellStringValue(cell) : "");
                }
                result.add(rowMap);
            }

            return result;
        }
    }

    private List<Map<String, String>> parseTextRows(File file, char delimiter, boolean useHeaderRow)
            throws IOException {
        List<String> headers = new ArrayList<>();
        List<Map<String, String>> result = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String firstLine = reader.readLine();
            if (firstLine == null) return result;

            String[] firstParts = splitLine(firstLine, delimiter);

            if (useHeaderRow) {
                for (String h : firstParts) headers.add(h.trim());
            } else {
                for (int i = 1; i <= firstParts.length; i++) {
                    headers.add("Column " + i);
                }
                // First line is data
                Map<String, String> rowMap = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    rowMap.put(headers.get(i), i < firstParts.length ? firstParts[i].trim() : "");
                }
                result.add(rowMap);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = splitLine(line, delimiter);
                Map<String, String> rowMap = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    rowMap.put(headers.get(i), i < parts.length ? parts[i].trim() : "");
                }
                result.add(rowMap);
            }
        }

        return result;
    }

    private String[] splitLine(String line, char delimiter) {
        return line.split(java.util.regex.Pattern.quote(String.valueOf(delimiter)), -1);
    }

    private String cellStringValue(Cell cell) {
        if (cell == null) return "";
        CellType type = cell.getCellType();
        if (type == CellType.NUMERIC) {
            double d = cell.getNumericCellValue();
            // Return integer representation if the value has no fractional part
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }
        if (type == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }
        if (type == CellType.FORMULA) {
            try {
                return String.valueOf(cell.getNumericCellValue());
            } catch (Exception e) {
                return cell.getStringCellValue();
            }
        }
        return cell.getStringCellValue();
    }

    /**
     * Creates a {@code String}-typed column in {@code table} with the given name if it does not
     * already exist. Silently skips reserved columns (SUID, shared name, selected).
     */
    private void createColumnIfAbsent(CyTable table, String name) {
        if (table.getColumn(name) == null) {
            try {
                table.createColumn(name, String.class, false);
            } catch (Exception e) {
                LOGGER.debug("Could not create column '{}': {}", name, e.getMessage());
            }
        }
    }

    private String extractString(CallToolRequest request, String key) {
        Object value = request.arguments().get(key);
        if (!(value instanceof String)) {
            return null;
        }
        String s = ((String) value).trim();
        return s.isEmpty() ? null : s;
    }

    private Boolean extractBoolean(CallToolRequest request, String key) {
        Object value = request.arguments().get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return null;
    }

    private Integer extractInteger(CallToolRequest request, String key) {
        Object value = request.arguments().get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
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
     * Returns the filename without its last extension (e.g. {@code "my_net.sif"} → {@code
     * "my_net"}, {@code "data.csv"} → {@code "data"}). If the filename has no extension the full
     * name is returned.
     */
    private static String baseNameWithoutExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
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
        String networkName = getDisplayName(network, fallbackName);
        return CallToolResult.builder()
                .structuredContent(
                        new LoadNetworkViewCallResult(
                                "success",
                                network.getSUID(),
                                network.getNodeCount(),
                                network.getEdgeCount(),
                                networkName,
                                null,
                                null))
                .build();
    }

    private CallToolResult buildTabularSuccessResponse(
            CyNetwork network,
            String fallbackName,
            Boolean nodeAttributesImported,
            String warning) {
        String networkName = getDisplayName(network, fallbackName);
        return CallToolResult.builder()
                .structuredContent(
                        new LoadNetworkViewCallResult(
                                "success",
                                network.getSUID(),
                                network.getNodeCount(),
                                network.getEdgeCount(),
                                networkName,
                                nodeAttributesImported,
                                warning))
                .build();
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }

    // -- Inner task class and helpers ----------------------------------------

    /**
     * Applies the Cytoscape default (preferred) layout to a newly created network view. Mirrors the
     * pattern used by {@code SIFNetworkReader.buildCyNetworkView()}: the layout task is run
     * synchronously on the current thread. After layout, the view is fitted and refreshed so nodes
     * are visible immediately.
     *
     * <p>Without this call, all nodes in a programmatically constructed network (e.g. tabular
     * import) sit at the default (0, 0) coordinate and render as a single node on screen.
     */
    private void applyDefaultLayout(CyNetworkView view) {
        if (layoutAlgorithmManager == null) return;
        CyLayoutAlgorithm layout = layoutAlgorithmManager.getDefaultLayout();
        if (layout == null) {
            LOGGER.warn("No default layout algorithm available; skipping layout");
            return;
        }
        try {
            String attribute = layoutAlgorithmManager.getLayoutAttribute(layout, view);
            TaskIterator itr =
                    layout.createTaskIterator(
                            view,
                            layout.getDefaultLayoutContext(),
                            CyLayoutAlgorithm.ALL_NODE_VIEWS,
                            attribute);
            TaskMonitor noOp =
                    new TaskMonitor() {
                        @Override
                        public void setTitle(String t) {}

                        @Override
                        public void setProgress(double p) {}

                        @Override
                        public void setStatusMessage(String m) {}

                        @Override
                        public void showMessage(TaskMonitor.Level l, String m) {}
                    };
            while (itr.hasNext()) {
                itr.next().run(noOp);
            }
            view.fitContent();
            view.updateView();
        } catch (Exception e) {
            LOGGER.warn(
                    "Could not apply default layout to view {}: {}",
                    view.getSUID(),
                    e.getMessage());
        }
    }

    /**
     * Forces the reader to load into a new, independent network collection.
     *
     * <p>{@link AbstractCyNetworkReader#init()} runs at reader-construction time (inside {@link
     * CyNetworkReaderManager#getReader}) and pre-selects the currently-active collection in its
     * {@code rootNetworkList} field. Without resetting that selection here, every file load joins
     * the active collection instead of creating its own.
     *
     * <p>The fix: find the {@code rootNetworkList} field (a {@code ListSingleSelection<String>}) in
     * the reader's class hierarchy, then select the "create new collection" entry — identified as
     * the first possible value that starts with {@code "--"}.
     */
    private void forceNewCollectionOnReader(CyNetworkReader reader) {
        for (Class<?> c = reader.getClass(); c != null; c = c.getSuperclass()) {
            Field f;
            try {
                f = c.getDeclaredField("rootNetworkList");
            } catch (NoSuchFieldException e) {
                continue;
            }
            try {
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                ListSingleSelection<String> lss = (ListSingleSelection<String>) f.get(reader);
                if (lss == null) return;
                String createNew = null;
                for (String v : lss.getPossibleValues()) {
                    if (v != null && v.startsWith("--")) {
                        createNew = v;
                        break;
                    }
                }
                if (createNew != null) {
                    lss.setSelectedValue(createNew);
                    LOGGER.debug(
                            "Forced new collection selection on {}",
                            reader.getClass().getSimpleName());
                }
                return;
            } catch (Exception e) {
                LOGGER.warn(
                        "Could not reset rootNetworkList on {}: {}",
                        reader.getClass().getSimpleName(),
                        e.getMessage());
                return;
            }
        }
        LOGGER.debug(
                "No rootNetworkList found on {}; skipping collection reset",
                reader.getClass().getSimpleName());
    }

    /**
     * Wraps a {@link CyNetworkReader} in a plain {@link AbstractTask} so the task manager never
     * scans the reader's own {@code @Tunable} fields. Without this wrapper the GUI task manager
     * detects tunables on the reader (e.g. {@code targetNetworkCollection}) and shows modal
     * dialogs. By running the reader inside this wrapper's {@link #run} method the reader's
     * internals are invisible to the tunable system.
     */
    private static final class LoadNetworkTask extends AbstractTask {
        private final CyNetworkReader reader;
        private final String source;

        LoadNetworkTask(CyNetworkReader reader, String source) {
            this.reader = reader;
            this.source = source;
        }

        @Override
        public void run(TaskMonitor monitor) throws Exception {
            monitor.setTitle("[MCP Tool Invocation] Load Network View");
            monitor.setStatusMessage("Loading network: " + source + "...");
            monitor.setProgress(-1); // indeterminate while loading
            reader.run(monitor);
            monitor.setProgress(1.0);
            monitor.setStatusMessage("Network loaded.");
        }
    }
}
