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
import org.cytoscape.io.read.AbstractCyNetworkReader;
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
            "Create a new instance of a network as a collection(includes the network as root and a view) on Cytoscape Desktop. The network instance will be created from one of multiple data sources: NDEx (by Network Id), a network formatted file,"
                    + " or a tabular formatted file with column mapping. Sets the new network collection instance and view as the current network view on desktop. "
                    + " Use this whenever a new instance of a network is needed on the desktop. The same nettwork can be laoded as multiple collection instances and is allowed. "
                    + " There may be other instances of the network loaded on the desktop but that is not of concern for this invocation. This tool will always create a new network collection instance regardless. "
                    + " To use this tool most effectively, focus on determining the input source parameter first from which the new network instance shall be loaded foremost. "
                    + " Then follow the specific requirements for the chosen source type via the rest of conditional input parameters."
                    + " It is VERY important to identify each conditional input parameter related to a chosen source type before executing the tool."
                    + " The more details provided via the additional conditional input parameters which are related to a chosen source type, the better the resulting network instance will be for the context. ";

    private static final String TOOL_EXAMPLES =
            "\n\n## Examples\n\n"
                    + "Example 1 — Load NDEx network into cytoscape desktop:\n"
                    + "{\"source\": \"ndex\", \"network_id\":"
                    + " {\"waived\": false, \"parameter\":"
                    + " \"a7e43e3d-c7f8-11ec-8d17-005056ae23aa\"}}\n\n"
                    + "Example 2 — Load network file into cytoscape desktop:\n"
                    + "{\"source\": \"network-file\", \"file_path\":"
                    + " {\"waived\": false, \"parameter\": \"/path/to/network.sif\"}}\n\n"
                    + "Example 3 — Load tabular file into cytoscape desktop - an example that will trigger asking for the conditional input parameters related to tabular file type :\n"
                    + "{\"source\": \"tabular-file\","
                    + " \"file_path\": {\"waived\": false, \"parameter\": \"/path/to/data.csv\"},"
                    + " \"source_column\": {\"waived\": false, \"parameter\": \"Gene_A\"},"
                    + " \"target_column\": {\"waived\": false, \"parameter\": \"Gene_B\"},"
                    + " \"delimiter_char_code\": {\"waived\": false, \"parameter\": 44},"
                    + " \"use_header_row\": {\"waived\": false, \"parameter\": true},"
                    + " \"interaction_column\": {\"waived\": true, \"parameter\": null},"
                    + " \"node_attributes_source_columns\": {\"waived\": false, \"parameter\":"
                    + " [{\"name\": \"Score\", \"inferred_data_type\": \"double\"}]},"
                    + " \"node_attributes_target_columns\": {\"waived\": false, \"parameter\":"
                    + " [{\"name\": \"Score\", \"inferred_data_type\": \"double\"}]},"
                    + " \"edge_columns\": {\"waived\": true, \"parameter\": null}}\n\n"
                    + "Example 4 — open network on cytoscape desktop:\n"
                    + "{\"source\": \"determine the source such as ndex, or local file(network or tabular) first, then figure out rest of related input params\"}\n\n";

    static final String INPUT_SCHEMA =
            McpSchema.toJson(
                    McpSchema.InputSchema.builder()
                            .required("source")
                            .property(
                                    "source",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Required. Determines which import path to use  and is the most important input parameter and must be determined first as the"
                                                    + " remaining input parameters depend on this value."
                                                    + " Must be one of: 'ndex' (load from NDEx by"
                                                    + " Network ID as UUID), 'network-file' (load a native network"
                                                    + " format file such as SIF, GML, XGMML, CX,"
                                                    + " CX2, GraphML, SBML, BioPAX),"
                                                    + " 'tabular-file' (load a delimited or Excel"
                                                    + " file with column mapping).",
                                            List.of("ndex", "network-file", "tabular-file")))
                            .conditionalParam(
                                    "network_id",
                                    "string",
                                    "Conditional on source='ndex'. NDEx network Id expressed as UUID string (e.g."
                                            + " 'a7e43e3d-c7f8-11ec-8d17-005056ae23aa')."
                                            + " Required when source='ndex'. Ignored otherwise."
                                            + " Confirm with the user before setting or waiving.")
                            .conditionalParam(
                                    "file_path",
                                    "string",
                                    "Conditional on source='network-file' or source='tabular-file'."
                                            + " Absolute path to the file to import."
                                            + " Required when source='network-file' or source='tabular-file'."
                                            + " Ignored when source='ndex'."
                                            + " Confirm the path with the user before setting or waiving.")
                            .conditionalParam(
                                    "source_column",
                                    "string",
                                    "Conditional on source='tabular-file'. Column name for the source (from) node."
                                            + " Preview columns from the file (and sheet if applicable)"
                                            + " to determine which is best for source node on a graph edge."
                                            + " Required when source='tabular-file'."
                                            + " Confirm the column choice with the user before setting or waiving.")
                            .conditionalParam(
                                    "target_column",
                                    "string",
                                    "Conditional on source='tabular-file'. Column name for the target (to) node."
                                            + " Preview columns from the file (and sheet if applicable)"
                                            + " to determine which is best for target node on a graph edge."
                                            + " Required when source='tabular-file'."
                                            + " Confirm the column choice with the user before setting or waiving.")
                            .conditionalParam(
                                    "interaction_column",
                                    "string",
                                    "Conditional on source='tabular-file'. Column name for the edge interaction type."
                                            + " Preview columns from the file (and sheet if applicable)"
                                            + " to determine which is best for graph edge name."
                                            + " Applicable when source='tabular-file'."
                                            + " Confirm with the user whether an interaction column should be mapped or waived.")
                            .conditionalParam(
                                    "delimiter_char_code",
                                    "integer",
                                    "Conditional on source='tabular-file' with a non-Excel file (i.e. excel_sheet not set)."
                                            + " ASCII code of the column delimiter (e.g."
                                            + " 44=comma, 9=tab)."
                                            + " Use the file extension, or inspect the source file to determine the delimiter."
                                            + " Required when source='tabular-file' and file is not Excel."
                                            + " Ignored for Excel files (use excel_sheet instead)."
                                            + " Confirm with the user by inspecting the file before setting or waiving.")
                            .conditionalParam(
                                    "use_header_row",
                                    "boolean",
                                    "Conditional on source='tabular-file'. Whether the first row contains column headers."
                                            + " Preview columns from the file (and sheet if applicable)"
                                            + " to determine if the first row has values suitable as headers."
                                            + " If false, ordinal column names are generated."
                                            + " Required when source='tabular-file'."
                                            + " Confirm with the user by inspecting the file before setting or waiving.")
                            .conditionalParam(
                                    "excel_sheet",
                                    "string",
                                    "Conditional on source='tabular-file' with an Excel file"
                                            + " (mutually exclusive with delimiter_char_code — set one or the other, not both)."
                                            + " Name of the Excel sheet containing the network edge data."
                                            + " Inspect the source file to determine what sheets are available."
                                            + " Required when source='tabular-file' and file is Excel."
                                            + " Ignored for non-Excel files (use delimiter_char_code instead)."
                                            + " Confirm with the user which sheet to use before setting or waiving.")
                            .conditionalParam(
                                    "node_attributes_sheet",
                                    "string",
                                    "Conditional on source='tabular-file' and excel_sheet being set."
                                            + " Name of the Excel sheet containing node attribute"
                                            + " columns to join onto the nodes from the main network sheet."
                                            + " Inspect the source file to determine what sheets are available."
                                            + " Applicable for Excel tabular files only."
                                            + " Confirm with the user whether a separate node attributes sheet should be used before setting or waiving.")
                            .conditionalParam(
                                    "node_attributes_sheet_source_key_column",
                                    "string",
                                    "Conditional on node_attributes_sheet being provided."
                                            + " Column name in the node attributes sheet whose values match"
                                            + " source-node IDs in the main network sheet."
                                            + " Used to join attributes onto source nodes."
                                            + " Preview columns from the node attributes sheet to determine which columns are available."
                                            + " Required when node_attributes_sheet is provided."
                                            + " Confirm the key column with the user before setting or waiving.")
                            .conditionalParam(
                                    "node_attributes_sheet_target_key_column",
                                    "string",
                                    "Conditional on node_attributes_sheet being provided."
                                            + " Column name in the node attributes sheet whose values match"
                                            + " target-node IDs in the main network sheet."
                                            + " Used to join attributes onto target nodes."
                                            + " Preview columns from the node attributes sheet to determine which columns are available."
                                            + " Required when node_attributes_sheet is provided."
                                            + " Confirm the key column with the user before setting or waiving.")
                            .conditionalDataColumnParam(
                                    "node_attributes_source_columns",
                                    "Conditional on source='tabular-file'. "
                                            + " Preview columns from the file (and sheet if applicable)"
                                            + " ask the user"
                                            + " which file columns should be attached as attributes on source"
                                            + " nodes or none is allowed. Populate with the user's confirmed selections or set as empty list if none chosen."
                                            + " Each entry is a DataColumn object (name + inferred_data_type)."
                                            + " Inspect the file's columns using other tooling available to retrieve column entries,"
                                            + " then pass the user's confirmed selections here."
                                            + " Waive only when the user has explicitly declined all source"
                                            + " node attribute mapping.")
                            .conditionalDataColumnParam(
                                    "node_attributes_target_columns",
                                    "Conditional on source='tabular-file'. "
                                            + " Preview columns from the file (and sheet if applicable)"
                                            + " ask the user"
                                            + " which file columns should be attached as attributes on target"
                                            + " nodes or none is allowed. Populate with the user's confirmed selections or set as empty list if none chosen."
                                            + " Each entry is a DataColumn object (name + inferred_data_type)."
                                            + " Inspect the file's columns using other tooling available to retrieve column entries,"
                                            + " then pass the user's confirmed selections here."
                                            + " Waive only when the user has explicitly declined all target"
                                            + " node attribute mapping.")
                            .conditionalDataColumnParam(
                                    "edge_columns",
                                    "Conditional on source='tabular-file'. "
                                            + " Populate with remaining file columns the user"
                                            + " has NOT already explicitly confirmed to be mapped to source nodes,"
                                            + " target nodes, or edge interaction or node attributes. This provides ability to specify more correct data types.")
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
    private final TabularTypeConverter typeConverter;
    private final ValidationService validationService;

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
            CyLayoutAlgorithmManager layoutAlgorithmManager,
            TabularTypeConverter typeConverter,
            ValidationService validationService) {
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
        this.typeConverter = typeConverter;
        this.validationService = validationService;
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
        try {
            Map<String, Object> args = request.arguments();
            String source =
                    validationService.unwrapToolInputValue(args.get("source"), String.class);
            if (source == null) {
                return error(
                        "'source' is required. Must be 'ndex', 'network-file',"
                                + " or 'tabular-file'.");
            }

            switch (source) {
                case "ndex":
                    return handleNdexImport(args);
                case "network-file":
                    return handleNetworkFileImport(args);
                case "tabular-file":
                    return handleTabularImport(args);
                default:
                    return error(
                            "Invalid source: '"
                                    + source
                                    + "'. Must be 'ndex',"
                                    + " 'network-file', or 'tabular-file'.");
            }
        } catch (Exception e) {
            LOGGER.error("Unexpected error in {}", TOOL_NAME, e);
            return error("Unexpected error: " + e.getMessage());
        }
    }

    // -- Source handlers -------------------------------------------------------

    private CallToolResult handleNdexImport(Map<String, Object> args) {
        CallToolResult err =
                validationService.validateConditionalParams(
                        "source",
                        "ndex",
                        args,
                        List.of(
                                new ValidationService.ConditionalParam(
                                        "network_id",
                                        "the NDEx network UUID required to load the network from"
                                                + " NDEx",
                                        false)));
        if (err != null) return err;

        String networkId =
                validationService.unwrapToolInputValue(args.get("network_id"), String.class);

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

    private CallToolResult handleNetworkFileImport(Map<String, Object> args) {
        CallToolResult err =
                validationService.validateConditionalParams(
                        "source",
                        "network-file",
                        args,
                        List.of(
                                new ValidationService.ConditionalParam(
                                        "file_path",
                                        "the absolute path to the network file to import",
                                        false)));
        if (err != null) return err;

        String filePath =
                validationService.unwrapToolInputValue(args.get("file_path"), String.class);

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

    private CallToolResult handleTabularImport(Map<String, Object> args) {
        // -- Phase 1: common required + sub-type discriminator ----------------
        CallToolResult err =
                validationService.validateConditionalParams(
                        "source",
                        "tabular-file",
                        args,
                        List.of(
                                new ValidationService.ConditionalParam(
                                        "file_path",
                                        "the absolute path to the tabular file to import",
                                        false),
                                new ValidationService.ConditionalParam(
                                        "source_column",
                                        "the file column that provides source node names for each"
                                                + " edge row",
                                        false),
                                new ValidationService.ConditionalParam(
                                        "target_column",
                                        "the file column that provides target node names for each"
                                                + " edge row",
                                        false),
                                new ValidationService.ConditionalParam(
                                        "use_header_row",
                                        "whether the first row of the file is a header row",
                                        false),
                                new ValidationService.ConditionalParam(
                                        "excel_sheet",
                                        "the Excel sheet containing the network edge data",
                                        true)));
        if (err != null) return err;

        String excelSheet =
                validationService.unwrapToolInputValue(args.get("excel_sheet"), String.class);
        boolean isExcel = excelSheet != null;

        // -- Phase 1b: node attribute sheet (Excel only; LLM must confirm intent)
        String nodeAttrSheet = null;
        if (isExcel) {
            err =
                    validationService.validateConditionalParams(
                            "excel_sheet",
                            excelSheet,
                            args,
                            List.of(
                                    new ValidationService.ConditionalParam(
                                            "node_attributes_sheet",
                                            "the Excel sheet for node attribute columns",
                                            false)));
            if (err != null) return err;
            nodeAttrSheet =
                    validationService.unwrapToolInputValue(
                            args.get("node_attributes_sheet"), String.class);
        }
        boolean hasNodeAttrSheet = nodeAttrSheet != null;

        // -- Phase 2: common optional + conditionally required node attr columns
        boolean nodeAttrColsRequired = !isExcel || hasNodeAttrSheet;
        err =
                validationService.validateConditionalParams(
                        "source",
                        "tabular-file",
                        args,
                        List.of(
                                new ValidationService.ConditionalParam(
                                        "interaction_column",
                                        "the column name for the edge interaction type",
                                        true),
                                new ValidationService.ConditionalParam(
                                        "edge_columns",
                                        "type overrides for remaining edge columns",
                                        true),
                                new ValidationService.ConditionalParam(
                                        "node_attributes_source_columns",
                                        "which file columns should be attached as attributes on"
                                                + " source nodes",
                                        !nodeAttrColsRequired),
                                new ValidationService.ConditionalParam(
                                        "node_attributes_target_columns",
                                        "which file columns should be attached as attributes on"
                                                + " target nodes",
                                        !nodeAttrColsRequired)));
        if (err != null) return err;

        // -- Phase 3a: key columns (only when node_attributes_sheet declared) -
        if (hasNodeAttrSheet) {
            err =
                    validationService.validateConditionalParams(
                            "node_attributes_sheet",
                            nodeAttrSheet,
                            args,
                            List.of(
                                    new ValidationService.ConditionalParam(
                                            "node_attributes_sheet_source_key_column",
                                            "the key column in node attributes sheet for source"
                                                    + " nodes",
                                            false),
                                    new ValidationService.ConditionalParam(
                                            "node_attributes_sheet_target_key_column",
                                            "the key column in node attributes sheet for target"
                                                    + " nodes",
                                            false)));
            if (err != null) return err;
        }

        // -- Phase 3b: delimiter required for non-Excel files -----------------
        if (!isExcel) {
            err =
                    validationService.validateConditionalParams(
                            "source",
                            "tabular-file",
                            args,
                            List.of(
                                    new ValidationService.ConditionalParam(
                                            "delimiter_char_code",
                                            "the ASCII code of the column delimiter",
                                            false)));
            if (err != null) return err;
        }

        // -- Extract validated values -----------------------------------------
        String filePath =
                validationService.unwrapToolInputValue(args.get("file_path"), String.class);

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            return error("File not found: " + filePath);
        }

        String sourceCol =
                validationService.unwrapToolInputValue(args.get("source_column"), String.class);
        String targetCol =
                validationService.unwrapToolInputValue(args.get("target_column"), String.class);
        Boolean useHeaderRow =
                validationService.unwrapToolInputValue(args.get("use_header_row"), Boolean.class);

        Integer delimiterCharCode =
                validationService.unwrapToolInputValue(
                        args.get("delimiter_char_code"), Integer.class);

        String interactionCol =
                validationService.unwrapToolInputValue(
                        args.get("interaction_column"), String.class);
        String nodeAttrSheetSourceKeyCol =
                validationService.unwrapToolInputValue(
                        args.get("node_attributes_sheet_source_key_column"), String.class);
        String nodeAttrSheetTargetKeyCol =
                validationService.unwrapToolInputValue(
                        args.get("node_attributes_sheet_target_key_column"), String.class);

        List<DataColumn> nodeAttrSourceCols =
                validationService.unwrapToolInputDataColumns(
                        args.get("node_attributes_source_columns"));
        List<DataColumn> nodeAttrTargetCols =
                validationService.unwrapToolInputDataColumns(
                        args.get("node_attributes_target_columns"));
        List<DataColumn> edgeCols =
                validationService.unwrapToolInputDataColumns(args.get("edge_columns"));

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

        // Build name→DataColumn lookup maps for typed column creation
        Map<String, DataColumn> edgeColMap = toColMap(edgeCols);
        Map<String, DataColumn> srcColMap = toColMap(nodeAttrSourceCols);
        Map<String, DataColumn> tgtColMap = toColMap(nodeAttrTargetCols);

        // Determine which columns are node-attribute columns (to exclude from edge attrs)
        Set<String> nodeAttrColSet = new java.util.HashSet<>();
        for (DataColumn dc : nodeAttrSourceCols)
            if (dc.name() != null) nodeAttrColSet.add(dc.name());
        for (DataColumn dc : nodeAttrTargetCols)
            if (dc.name() != null) nodeAttrColSet.add(dc.name());

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

            // Add remaining columns as edge attributes (with inferred types if available)
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
                Class<?> colType =
                        edgeColMap.containsKey(col)
                                ? edgeColMap.get(col).inferredTypeClass()
                                : String.class;
                createColumnIfAbsent(edgeTable, col, colType);
                Object colVal = typeConverter.coerceToColumnType(entry.getValue(), colType);
                if (colVal != null) edgeRow.set(col, colVal);
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
                            nodeAttrTargetCols,
                            srcColMap,
                            tgtColMap);
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
            List<DataColumn> nodeAttrSourceCols,
            List<DataColumn> nodeAttrTargetCols,
            Map<String, DataColumn> srcColMap,
            Map<String, DataColumn> tgtColMap) {

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
                for (DataColumn dc : nodeAttrSourceCols) {
                    String val = attrRow.get(dc.name());
                    if (val != null) {
                        DataColumn lookup = srcColMap.getOrDefault(dc.name(), dc);
                        Class<?> colType = lookup.inferredTypeClass();
                        createColumnIfAbsent(nodeTable, dc.name(), colType);
                        Object converted = typeConverter.coerceToColumnType(val, colType);
                        if (converted != null) nodeRow.set(dc.name(), converted);
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
                for (DataColumn dc : nodeAttrTargetCols) {
                    String val = attrRow.get(dc.name());
                    if (val != null) {
                        DataColumn lookup = tgtColMap.getOrDefault(dc.name(), dc);
                        Class<?> colType = lookup.inferredTypeClass();
                        createColumnIfAbsent(nodeTable, dc.name(), colType);
                        Object converted = typeConverter.coerceToColumnType(val, colType);
                        if (converted != null) nodeRow.set(dc.name(), converted);
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
     * Creates a typed column in {@code table} with the given name if it does not already exist.
     * Silently skips if the column already exists or creation fails.
     */
    private void createColumnIfAbsent(CyTable table, String name, Class<?> type) {
        if (table.getColumn(name) == null) {
            try {
                table.createColumn(name, type, false);
            } catch (Exception e) {
                LOGGER.debug("Could not create column '{}': {}", name, e.getMessage());
            }
        }
    }

    /** Builds a name → {@link DataColumn} lookup map from the given list. */
    private Map<String, DataColumn> toColMap(List<DataColumn> cols) {
        if (cols == null || cols.isEmpty()) return Map.of();
        Map<String, DataColumn> map = new LinkedHashMap<>();
        for (DataColumn dc : cols) {
            if (dc != null && dc.name() != null) map.put(dc.name(), dc);
        }
        return map;
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
