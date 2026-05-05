package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
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
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.CyTableFactory;
import org.cytoscape.model.CyTableManager;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool that imports columns from a local tabular file (CSV, TSV, pipe-delimited, or Excel) into
 * Cytoscape Desktop node, edge, or network tables of the currently active network view, or creates
 * a new standalone private table in the current session.
 */
public class TableImportFileTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(TableImportFileTool.class);

    static final String TOOL_NAME = "table_import_file";

    private static final String TOOL_TITLE = "Import Table File into Cytoscape Desktop";

    private static final String TOOL_DESCRIPTION =
            "Import columns from a local tabular file into Cytoscape Desktop node, edge, or"
                    + " network tables of the currently active network view, or create a new"
                    + " standalone private table in the current session. Use when external"
                    + " annotation data — such as expression values, ontology terms, or"
                    + " experimental scores — needs to be attached to the active network or stored"
                    + " as an independent reference table. Supports CSV, TSV, pipe-delimited, and"
                    + " Excel (.xlsx/.xls) file formats; file format is detected from the file"
                    + " extension. Side-effects: adds or updates columns in the current network's"
                    + " node, edge, or network table, or creates and registers a new table with the"
                    + " Cytoscape table manager. Returns an error response when the file cannot be"
                    + " read, no active network is loaded, required parameters are absent or"
                    + " inconsistent, or the destination is invalid; the error message identifies"
                    + " the specific cause.";

    private static final String TOOL_EXAMPLES =
            "\n\n## Examples\n\n"
                    + "Example 1 — Import node expression data from a TSV into the current"
                    + " network's node table:\n"
                    + "{\"file_path\": \"/data/expr.tsv\","
                    + " \"excel_sheet\": {\"waived\": true},"
                    + " \"where_to_import\": \"current_network_view\","
                    + " \"table_type\": {\"waived\": false, \"parameter\": \"node\"},"
                    + " \"datafile_key_column_index\": {\"waived\": false, \"parameter\": 1},"
                    + " \"network_key_column_name\": {\"waived\": false, \"parameter\": \"shared name\"},"
                    + " \"new_table_name\": {\"waived\": true}}\n\n"
                    + "Example 2 — Attach edge scores from a CSV into the current network's edge table:\n"
                    + "{\"file_path\": \"/data/scores.csv\","
                    + " \"excel_sheet\": {\"waived\": true},"
                    + " \"where_to_import\": \"current_network_view\","
                    + " \"table_type\": {\"waived\": false, \"parameter\": \"edge\"},"
                    + " \"datafile_key_column_index\": {\"waived\": false, \"parameter\": 1},"
                    + " \"network_key_column_name\": {\"waived\": false, \"parameter\": \"shared name\"},"
                    + " \"new_table_name\": {\"waived\": true}}\n\n"
                    + "Example 3 — Create a standalone lookup table from an Excel workbook:\n"
                    + "{\"file_path\": \"/data/lookup.xlsx\","
                    + " \"excel_sheet\": {\"waived\": false, \"parameter\": \"Sheet1\"},"
                    + " \"where_to_import\": \"unassigned_table\","
                    + " \"new_table_name\": {\"waived\": false, \"parameter\": \"GeneAnnotations\"},"
                    + " \"table_type\": {\"waived\": true},"
                    + " \"datafile_key_column_index\": {\"waived\": true},"
                    + " \"network_key_column_name\": {\"waived\": true}}\n\n"
                    + "Example 4 — Import node attributes with explicit types and a comment character:\n"
                    + "{\"file_path\": \"/data/attrs.csv\","
                    + " \"excel_sheet\": {\"waived\": true},"
                    + " \"data_type_list\": {\"waived\": false, \"parameter\": \"s,d,d\"},"
                    + " \"comment_char\": {\"waived\": false, \"parameter\": \"#\"},"
                    + " \"where_to_import\": \"current_network_view\","
                    + " \"table_type\": {\"waived\": false, \"parameter\": \"node\"},"
                    + " \"datafile_key_column_index\": {\"waived\": false, \"parameter\": 1},"
                    + " \"network_key_column_name\": {\"waived\": false, \"parameter\": \"shared name\"},"
                    + " \"new_table_name\": {\"waived\": true}}\n\n";

    private static final Set<String> VALID_TYPE_CODES =
            Set.of("s", "i", "l", "d", "b", "sl", "il", "ll", "dl", "bl");

    private static final Set<String> LIST_TYPE_CODES = Set.of("sl", "il", "ll", "dl", "bl");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String INPUT_SCHEMA =
            McpSchema.toJson(
                    McpSchema.InputSchema.builder()
                            .required("file_path")
                            .property(
                                    "file_path",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Required. Absolute path to the tabular file on the"
                                                    + " local machine. Supports CSV, TSV,"
                                                    + " pipe-delimited, and Excel (.xlsx/.xls)"
                                                    + " formats. Use the file inspection tool if"
                                                    + " the format is uncertain before invoking.\n\n"
                                                    + "Examples: \"/data/expression.tsv\","
                                                    + " \"/home/user/annotations.csv\","
                                                    + " \"/tmp/lookup.xlsx\""))
                            .conditionalParam(
                                    "excel_sheet",
                                    "string",
                                    "Optional. Required when file_path is an Excel file (.xlsx or"
                                            + " .xls). Name of the sheet to import. Waive for"
                                            + " non-Excel files — delimiter is detected automatically."
                                            + " Use the file inspection tool to enumerate available"
                                            + " sheet names before setting. Confirm with the user"
                                            + " before setting or waiving.\n\n"
                                            + "Examples: \"Sheet1\", \"Node Attributes\", \"Data\"")
                            .property(
                                    "use_header_row",
                                    new McpSchema.InputProperty(
                                            "boolean",
                                            "Optional. When true (default), the first row in the"
                                                    + " file is treated as column headers and those"
                                                    + " values become the column names in Cytoscape."
                                                    + " When false, all rows are treated as data rows"
                                                    + " and columns are assigned generated names"
                                                    + " (Column_1, Column_2, etc.)."
                                                    + " Defaults to true.\n\n"
                                                    + "Examples: true, false"))
                            .conditionalParam(
                                    "comment_char",
                                    "string",
                                    "Optional. Single character; lines that begin with this"
                                            + " character are treated as comments and skipped before"
                                            + " column parsing. Waive if the file contains no"
                                            + " comment lines.\n\n"
                                            + "Examples: \"#\", \"!\", \";\"")
                            .conditionalParam(
                                    "decimal_separator",
                                    "string",
                                    "Optional. Single character used as the decimal point when"
                                            + " parsing numeric values in non-Excel files."
                                            + " Defaults to \".\". Cannot be waived — always"
                                            + " provide either the default or an alternative value."
                                            + " Has no effect on Excel files. Confirm the value"
                                            + " with the user.\n\n"
                                            + "Examples: \".\" (default, standard locale),"
                                            + " \",\" (European locale)")
                            .conditionalParam(
                                    "data_type_list",
                                    "string",
                                    "Optional. Ordered, comma-separated per-column type codes."
                                            + " Must use only the following codes — any other value"
                                            + " is invalid and will produce an error. Scalar codes:"
                                            + " s=String, i=Integer, l=Long, d=Double, b=Boolean."
                                            + " List-variant codes: sl=List of String, il=List of"
                                            + " Integer, ll=List of Long, dl=List of Double,"
                                            + " bl=List of Boolean. Count of codes must equal the"
                                            + " number of columns in the file. Waive to let the"
                                            + " tool infer types from sample values.\n\n"
                                            + "Examples: \"s,d,d\" (name, two doubles),"
                                            + " \"s,s,b\" (two strings, boolean),"
                                            + " \"s,il\" (name, integer list)")
                            .conditionalParam(
                                    "list_delimiter",
                                    "string",
                                    "Optional. Single character used to split values in list-typed"
                                            + " columns into individual elements. Defaults to \"|\"."
                                            + " Cannot be waived — always provide either the default"
                                            + " or an alternative value. Only meaningful when"
                                            + " data_type_list contains list-type codes (sl, il, ll,"
                                            + " dl, bl). Confirm the value with the user.\n\n"
                                            + "Examples: \"|\" (default), \",\", \";\"")
                            .property(
                                    "where_to_import",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Optional. Determines where the imported columns are"
                                                    + " placed. Must be exactly one of:"
                                                    + " \"current_network_view\" (default, adds"
                                                    + " columns to the currently active network's"
                                                    + " tables) or \"unassigned_table\" (creates a"
                                                    + " new standalone private table not connected to"
                                                    + " any network — if the final goal requires that"
                                                    + " table to be associated with a network,"
                                                    + " merging it with an existing table can be"
                                                    + " considered as a follow-on step). No other"
                                                    + " values are accepted. Resolve this value"
                                                    + " before setting the destination parameters"
                                                    + " that depend on it.",
                                            List.of("current_network_view", "unassigned_table")))
                            .conditionalParam(
                                    "table_type",
                                    "string",
                                    "Optional. Required when where_to_import is"
                                            + " \"current_network_view\". Must be exactly one of:"
                                            + " \"node\" (default node table), \"edge\" (default"
                                            + " edge table), or \"network\" (network-level"
                                            + " attributes table). No other values are accepted."
                                            + " Waive when where_to_import is \"unassigned_table\"."
                                            + " Confirm with the user before setting or waiving.")
                            .conditionalParam(
                                    "datafile_key_column_index",
                                    "integer",
                                    "Optional. Required when where_to_import is"
                                            + " \"current_network_view\". 1-based ordinal position"
                                            + " of the column in the data file that contains the key"
                                            + " values used to match rows to the active network"
                                            + " table. Column 1 is the leftmost column. Defaults to"
                                            + " 1. Cannot be waived — always provide either the"
                                            + " default or another value. Confirm the column position"
                                            + " with the user. Waive when where_to_import is"
                                            + " \"unassigned_table\".\n\n"
                                            + "Examples: 1 (leftmost/first column, default),"
                                            + " 2 (second column), 3")
                            .conditionalParam(
                                    "network_key_column_name",
                                    "string",
                                    "Optional. Required when where_to_import is"
                                            + " \"current_network_view\". Name of the column in the"
                                            + " active network's node, edge, or network table whose"
                                            + " values are compared against the data file key column"
                                            + " to match rows. The standard Cytoscape identifier"
                                            + " column name is \"shared name\". Use the loaded"
                                            + " networks tool to confirm available column names."
                                            + " Waive when where_to_import is \"unassigned_table\"."
                                            + " Confirm with the user before setting or waiving.\n\n"
                                            + "Examples: \"shared name\", \"GeneID\","
                                            + " \"Entrez ID\"")
                            .conditionalParam(
                                    "new_table_name",
                                    "string",
                                    "Optional. Required when where_to_import is"
                                            + " \"unassigned_table\". Name for the new standalone"
                                            + " table to create. Must be unique within the current"
                                            + " Cytoscape session; if a table with this name already"
                                            + " exists an error is returned. Waive when"
                                            + " where_to_import is \"current_network_view\"."
                                            + " Confirm with the user before setting or waiving.\n\n"
                                            + "Examples: \"ExpressionData\","
                                            + " \"GeneAnnotations\", \"LookupTable\"")
                            .build());

    /** App response model — Jackson annotations drive victools OUTPUT_SCHEMA generation. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TableImportFileCallResult(
            @JsonPropertyDescription(
                            "Number of file data rows successfully imported — either matched to an"
                                    + " existing row in a network table and updated, or added as a"
                                    + " new row in a standalone table.\n\n"
                                    + "Examples: 3, 150, 0")
                    @JsonProperty("rows_imported")
                    int rowsImported,
            @JsonPropertyDescription(
                            "Number of file data rows that had no matching key in the active"
                                    + " network table and were skipped. Absent when zero. Present"
                                    + " only for current_network_view imports; always absent for"
                                    + " unassigned_table imports. A nonzero value is a data quality"
                                    + " observation — not all file entries have a corresponding row"
                                    + " in the network table.\n\n"
                                    + "Examples: 1, 12 (absent when all rows matched)")
                    @JsonProperty("rows_unmatched")
                    Integer rowsUnmatched,
            @JsonPropertyDescription(
                            "Number of new columns created in the target table during this import."
                                    + " A column is counted as added only when it did not previously"
                                    + " exist in the table.\n\n"
                                    + "Examples: 3, 0, 7")
                    @JsonProperty("columns_added")
                    int columnsAdded,
            @JsonPropertyDescription(
                            "Number of existing columns in the target table that received new"
                                    + " values from the file. A column is counted as updated when"
                                    + " it was already present and at least one row value was"
                                    + " written to it.\n\n"
                                    + "Examples: 1, 0, 5")
                    @JsonProperty("columns_updated")
                    int columnsUpdated,
            @JsonPropertyDescription(
                            "Name of the target table. For current_network_view imports, this is"
                                    + " the name of the node, edge, or network table of the active"
                                    + " network. For unassigned_table imports, this is the name of"
                                    + " the newly created table.\n\n"
                                    + "Examples: \"default node\", \"default edge\","
                                    + " \"ExpressionData\"")
                    @JsonProperty("table_name")
                    String tableName,
            @JsonPropertyDescription(
                            "Unique session identifier (SUID) of the Cytoscape network modified."
                                    + " Absent for unassigned_table imports. For"
                                    + " current_network_view imports, this SUID can be used with"
                                    + " other tools to inspect or set the network as current.\n\n"
                                    + "Examples: 12345, 67890 (absent for unassigned_table imports)")
                    @JsonProperty("network_suid")
                    Long networkSuid) {}

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(TableImportFileCallResult.class);

    private final CyApplicationManager appManager;
    private final CyTableManager tableManager;
    private final CyTableFactory tableFactory;
    private final TabularTypeConverter typeConverter;
    private final ValidationService validationService;

    public TableImportFileTool(
            CyApplicationManager appManager,
            CyTableManager tableManager,
            CyTableFactory tableFactory,
            TabularTypeConverter typeConverter,
            ValidationService validationService) {
        this.appManager = appManager;
        this.tableManager = tableManager;
        this.tableFactory = tableFactory;
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
            return new McpServerFeatures.SyncToolSpecification(
                    toolDef, (exchange, request) -> handle(exchange, request));
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        // Validate inputs
        List<String> errors = new InputValidator(validationService).validate(args);
        if (!errors.isEmpty()) {
            return error(String.join("\n", errors));
        }

        // Parse common params
        String filePath =
                validationService.unwrapToolInputValue(args.get("file_path"), String.class);
        if (filePath == null || filePath.isBlank()) {
            return error("'file_path' is required and must not be blank.");
        }

        boolean useHeaderRow =
                Boolean.TRUE.equals(
                        validationService.unwrapToolInputValue(
                                args.get("use_header_row"), Boolean.class));
        // default true when absent
        if (args.get("use_header_row") == null) useHeaderRow = true;

        String commentChar =
                validationService.unwrapToolInputValue(args.get("comment_char"), String.class);
        String decimalSep =
                validationService.unwrapToolInputValue(args.get("decimal_separator"), String.class);
        if (decimalSep == null) decimalSep = ".";

        String dataTypeListRaw =
                validationService.unwrapToolInputValue(args.get("data_type_list"), String.class);
        String listDelimiter =
                validationService.unwrapToolInputValue(args.get("list_delimiter"), String.class);
        if (listDelimiter == null) listDelimiter = "|";

        String whereToImport =
                validationService.unwrapToolInputValue(args.get("where_to_import"), String.class);
        if (whereToImport == null) whereToImport = "current_network_view";

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            return error("File not found or not a regular file: '" + filePath + "'.");
        }

        // Detect file type and parse
        boolean isExcel =
                filePath.toLowerCase().endsWith(".xlsx") || filePath.toLowerCase().endsWith(".xls");

        List<String[]> rows;
        List<String> colNames;
        try {
            if (isExcel) {
                String excelSheet =
                        validationService.unwrapToolInputValue(
                                args.get("excel_sheet"), String.class);
                List<String[]> rawRows = parseExcelFile(file, excelSheet, useHeaderRow);
                if (useHeaderRow && !rawRows.isEmpty()) {
                    colNames = Arrays.asList(rawRows.get(0));
                    rows = rawRows.subList(1, rawRows.size());
                } else {
                    rows = rawRows;
                    colNames = generateColumnNames(rows.isEmpty() ? 0 : rows.get(0).length);
                }
            } else {
                int delim;
                try {
                    delim = validationService.detectDelimiter(file, commentChar);
                } catch (IOException e) {
                    return error("Failed to read file '" + filePath + "': " + e.getMessage());
                }
                if (delim == -1) {
                    return error(
                            "Cannot determine the column delimiter from the file content."
                                    + " The file must use a consistent tab, comma (,), pipe (|),"
                                    + " or semicolon (;) as the column separator across all rows.");
                }

                List<String[]> rawRows = parseTextFile(file, delim, commentChar, useHeaderRow);
                if (useHeaderRow && !rawRows.isEmpty()) {
                    colNames = Arrays.asList(rawRows.get(0));
                    rows = rawRows.subList(1, rawRows.size());
                } else {
                    rows = rawRows;
                    colNames = generateColumnNames(rows.isEmpty() ? 0 : rows.get(0).length);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse file: {}", filePath, e);
            return error("Failed to read file '" + filePath + "': " + e.getMessage());
        }

        if (rows.isEmpty()) {
            return error("No data rows found in file '" + filePath + "'.");
        }

        // Resolve column types from data_type_list or infer
        List<String> typeCodes = resolveTypeCodes(dataTypeListRaw, colNames.size(), rows);

        // Dispatch
        if ("unassigned_table".equals(whereToImport)) {
            String newTableName =
                    validationService.unwrapToolInputValue(
                            args.get("new_table_name"), String.class);
            return handleUnassignedTable(
                    newTableName, colNames, typeCodes, rows, listDelimiter, decimalSep);
        } else {
            String tableType =
                    validationService.unwrapToolInputValue(args.get("table_type"), String.class);
            Integer keyColIdx =
                    validationService.unwrapToolInputValue(
                            args.get("datafile_key_column_index"), Integer.class);
            if (keyColIdx == null) keyColIdx = 1;
            String networkKeyCol =
                    validationService.unwrapToolInputValue(
                            args.get("network_key_column_name"), String.class);
            return handleCurrentNetworkView(
                    tableType,
                    keyColIdx,
                    networkKeyCol,
                    colNames,
                    typeCodes,
                    rows,
                    listDelimiter,
                    decimalSep);
        }
    }

    private CallToolResult handleCurrentNetworkView(
            String tableType,
            int keyColIdx,
            String networkKeyCol,
            List<String> colNames,
            List<String> typeCodes,
            List<String[]> rows,
            String listDelimiter,
            String decimalSep) {

        CyNetwork network = appManager.getCurrentNetwork();
        if (network == null) {
            return error(
                    "No active network is currently loaded in Cytoscape Desktop. Load a"
                            + " network first, then invoke this tool.");
        }

        CyTable table = resolveNetworkTable(network, tableType);
        if (table == null) {
            return error(
                    "Unknown table_type '"
                            + tableType
                            + "'. Must be 'node', 'edge',"
                            + " or 'network'.");
        }

        // Add missing columns
        int[] addedUpdated = ensureColumns(table, colNames, typeCodes, keyColIdx);

        // Match rows and set values
        int rowsImported = 0;
        int rowsUnmatched = 0;
        int keyFileIdx = keyColIdx - 1; // 0-based

        for (String[] row : rows) {
            if (keyFileIdx >= row.length) {
                rowsUnmatched++;
                continue;
            }
            String keyValue = row[keyFileIdx];
            if (keyValue == null || keyValue.isBlank()) {
                rowsUnmatched++;
                continue;
            }

            // Match case-insensitively
            Collection<CyRow> matching = findMatchingRows(table, networkKeyCol, keyValue);
            if (matching.isEmpty()) {
                rowsUnmatched++;
                continue;
            }

            for (CyRow cyRow : matching) {
                applyRowValues(
                        cyRow, row, colNames, typeCodes, keyColIdx, listDelimiter, decimalSep);
            }
            rowsImported++;
        }

        return buildResult(
                rowsImported,
                rowsUnmatched == 0 ? null : rowsUnmatched,
                addedUpdated[0],
                addedUpdated[1],
                table.getTitle(),
                network.getSUID());
    }

    private CallToolResult handleUnassignedTable(
            String newTableName,
            List<String> colNames,
            List<String> typeCodes,
            List<String[]> rows,
            String listDelimiter,
            String decimalSep) {

        // Duplicate name check
        boolean nameExists =
                tableManager.getGlobalTables().stream()
                        .anyMatch(t -> newTableName.equalsIgnoreCase(t.getTitle()));
        if (nameExists) {
            return error(
                    "A table named '"
                            + newTableName
                            + "' already exists in this Cytoscape"
                            + " session. Choose a different name.");
        }

        CyTable table = tableFactory.createTable(newTableName, "suid", Long.class, true, true);

        // Create all columns
        for (int i = 0; i < colNames.size(); i++) {
            String colName = colNames.get(i);
            String code = i < typeCodes.size() ? typeCodes.get(i) : "s";
            createColumn(table, colName, code);
        }

        // Add rows
        long suid = 1L;
        for (String[] row : rows) {
            CyRow cyRow = table.getRow(suid++);
            for (int i = 0; i < colNames.size(); i++) {
                String colName = colNames.get(i);
                String code = i < typeCodes.size() ? typeCodes.get(i) : "s";
                String rawVal = i < row.length ? row[i] : null;
                setRowValue(cyRow, colName, code, rawVal, listDelimiter, decimalSep);
            }
        }

        tableManager.addTable(table);

        return buildResult(rows.size(), null, colNames.size(), 0, newTableName, null);
    }

    // -- Helpers ------------------------------------------------------------------

    private CyTable resolveNetworkTable(CyNetwork network, String tableType) {
        if ("edge".equals(tableType)) return network.getDefaultEdgeTable();
        if ("network".equals(tableType))
            return network.getTable(CyNetwork.class, CyNetwork.DEFAULT_ATTRS);
        if ("node".equals(tableType)) return network.getDefaultNodeTable();
        return null;
    }

    /** Ensures all non-key columns exist in the table. Returns [columnsAdded, columnsUpdated]. */
    private int[] ensureColumns(
            CyTable table, List<String> colNames, List<String> typeCodes, int keyColIdx) {
        int added = 0;
        int updated = 0;
        for (int i = 0; i < colNames.size(); i++) {
            if (i == keyColIdx - 1) continue; // skip key column
            String colName = colNames.get(i);
            String code = i < typeCodes.size() ? typeCodes.get(i) : "s";
            if (table.getColumn(colName) == null) {
                createColumn(table, colName, code);
                added++;
            } else {
                updated++;
            }
        }
        return new int[] {added, updated};
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void createColumn(CyTable table, String colName, String code) {
        try {
            if (LIST_TYPE_CODES.contains(code)) {
                Class<?> elemClass = typeConverter.resolveListElementClass(code);
                table.createListColumn(colName, elemClass, false);
            } else {
                Class<?> colClass = codeToClass(code);
                table.createColumn(colName, colClass, false);
            }
        } catch (Exception e) {
            LOGGER.debug(
                    "Could not create column '{}' (code={}): {}",
                    new Object[] {colName, code, e.getMessage()});
        }
    }

    private Collection<CyRow> findMatchingRows(
            CyTable table, String keyColumnName, String keyValue) {
        try {
            // Try exact match first
            Collection<CyRow> exact = table.getMatchingRows(keyColumnName, keyValue);
            if (!exact.isEmpty()) return exact;
            // Case-insensitive fallback: scan all rows
            List<CyRow> result = new ArrayList<>();
            for (CyRow row : table.getAllRows()) {
                Object val = row.getRaw(keyColumnName);
                if (val instanceof String && ((String) val).equalsIgnoreCase(keyValue)) {
                    result.add(row);
                }
            }
            return result;
        } catch (Exception e) {
            LOGGER.debug(
                    "getMatchingRows failed for column '{}': {}", keyColumnName, e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applyRowValues(
            CyRow cyRow,
            String[] fileRow,
            List<String> colNames,
            List<String> typeCodes,
            int keyColIdx,
            String listDelimiter,
            String decimalSep) {
        for (int i = 0; i < colNames.size(); i++) {
            if (i == keyColIdx - 1) continue; // skip key column
            if (i >= fileRow.length) continue;
            String colName = colNames.get(i);
            String code = i < typeCodes.size() ? typeCodes.get(i) : "s";
            setRowValue(cyRow, colName, code, fileRow[i], listDelimiter, decimalSep);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void setRowValue(
            CyRow cyRow,
            String colName,
            String code,
            String rawVal,
            String listDelimiter,
            String decimalSep) {
        if (rawVal == null || rawVal.isBlank()) return;
        try {
            if (LIST_TYPE_CODES.contains(code)) {
                Class elemClass = typeConverter.resolveListElementClass(code);
                List list = typeConverter.coerceToListValue(rawVal, listDelimiter, elemClass);
                if (!list.isEmpty()) cyRow.set(colName, list);
            } else {
                String adjusted =
                        "d".equals(code) && !".".equals(decimalSep)
                                ? rawVal.replace(decimalSep, ".")
                                : rawVal;
                Object val = typeConverter.coerceToColumnType(adjusted, codeToClass(code));
                if (val != null) cyRow.set(colName, val);
            }
        } catch (Exception e) {
            LOGGER.debug("Could not set value for column '{}': {}", colName, e.getMessage());
        }
    }

    private Class<?> codeToClass(String code) {
        return switch (code) {
            case "i" -> Integer.class;
            case "l" -> Long.class;
            case "d" -> Double.class;
            case "b" -> Boolean.class;
            default -> String.class;
        };
    }

    private List<String> resolveTypeCodes(
            String dataTypeListRaw, int columnCount, List<String[]> sampleRows) {
        if (dataTypeListRaw != null && !dataTypeListRaw.isBlank()) {
            return List.of(dataTypeListRaw.split(",", -1));
        }
        // Infer from sample rows (up to 5)
        List<String> codes = new ArrayList<>(columnCount);
        int sampleCount = Math.min(5, sampleRows.size());
        for (int col = 0; col < columnCount; col++) {
            List<String> samples = new ArrayList<>();
            for (int r = 0; r < sampleCount; r++) {
                String[] row = sampleRows.get(r);
                if (col < row.length) samples.add(row[col]);
            }
            DataColumn.CyDataType inferred = typeConverter.inferType(samples);
            codes.add(cyDataTypeToCode(inferred));
        }
        return codes;
    }

    private String cyDataTypeToCode(DataColumn.CyDataType t) {
        return switch (t) {
            case INTEGER -> "i";
            case LONG -> "l";
            case DOUBLE -> "d";
            case BOOLEAN -> "b";
            default -> "s";
        };
    }

    private List<String> generateColumnNames(int count) {
        List<String> names = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) names.add("Column_" + i);
        return names;
    }

    // -- File parsing (package-private for spy-ability in tests) ------------------

    List<String[]> parseTextFile(File file, int delimCode, String commentChar, boolean useHeaderRow)
            throws IOException {
        List<String[]> result = new ArrayList<>();
        String delim = String.valueOf((char) delimCode);
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                // Strip UTF-8 BOM from the first line if present
                if (firstLine) {
                    if (line.startsWith("﻿")) line = line.substring(1);
                    firstLine = false;
                }
                if (commentChar != null && line.startsWith(commentChar)) continue;
                result.add(line.split(delim, -1));
            }
        }
        return result;
    }

    List<String[]> parseExcelFile(File file, String sheetName, boolean useHeaderRow)
            throws Exception {
        List<String[]> result = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook wb = WorkbookFactory.create(file)) {
            Sheet sheet = sheetName != null ? wb.getSheet(sheetName) : wb.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException(
                        "Sheet '" + sheetName + "' not found in workbook.");
            }
            int maxCols = 0;
            for (Row row : sheet) {
                maxCols = Math.max(maxCols, row.getLastCellNum());
            }
            for (Row row : sheet) {
                String[] cells = new String[maxCols];
                for (int c = 0; c < maxCols; c++) {
                    Cell cell = row.getCell(c);
                    cells[c] = cell != null ? formatter.formatCellValue(cell) : "";
                }
                result.add(cells);
            }
        }
        return result;
    }

    // -- Result builders ----------------------------------------------------------

    private static CallToolResult buildResult(
            int rowsImported,
            Integer rowsUnmatched,
            int columnsAdded,
            int columnsUpdated,
            String tableName,
            Long networkSuid) {
        try {
            TableImportFileCallResult r =
                    new TableImportFileCallResult(
                            rowsImported,
                            rowsUnmatched,
                            columnsAdded,
                            columnsUpdated,
                            tableName,
                            networkSuid);
            return CallToolResult.builder().structuredContent(r).build();
        } catch (Exception e) {
            return error("Failed to serialize result: " + e.getMessage());
        }
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }

    // -- InputValidator -----------------------------------------------------------

    static class InputValidator {

        private final ValidationService vs;

        InputValidator(ValidationService vs) {
            this.vs = vs;
        }

        List<String> validate(Map<String, Object> args) {
            List<String> errors = new ArrayList<>();
            errors.addAll(validateGroupA(args));
            errors.addAll(validateGroupB(args));
            errors.addAll(validateGroupC(args));
            errors.addAll(validateGroupDE(args));
            errors.addAll(validateGroupH(args));
            return errors;
        }

        // Group A: file_path
        private List<String> validateGroupA(Map<String, Object> args) {
            String fp = vs.unwrapToolInputValue(args.get("file_path"), String.class);
            if (fp == null || fp.isBlank()) {
                return List.of("'file_path' is required and must not be blank.");
            }
            return List.of();
        }

        // Group B: excel_sheet required for Excel files
        private List<String> validateGroupB(Map<String, Object> args) {
            String fp = vs.unwrapToolInputValue(args.get("file_path"), String.class);
            boolean isExcel =
                    fp != null
                            && (fp.toLowerCase().endsWith(".xlsx")
                                    || fp.toLowerCase().endsWith(".xls"));

            if (isExcel && !isNonWaived(args.get("excel_sheet"))) {
                return List.of(
                        "File appears to be an Excel file. Provide 'excel_sheet'"
                                + " (waived=false, parameter=\"<sheet name>\").");
            }
            return List.of();
        }

        // Group C: reader options
        private List<String> validateGroupC(Map<String, Object> args) {
            List<String> errors = new ArrayList<>();

            // decimal_separator: non-waivable; if present must be single char
            Object decRaw = args.get("decimal_separator");
            if (decRaw != null && isWaived(decRaw)) {
                errors.add(
                        "'decimal_separator' cannot be waived — it has a default value of '.'"
                                + " and must always be confirmed. Provide {waived:false, parameter:\".\"}.");
            } else {
                String dec = vs.unwrapToolInputValue(decRaw, String.class);
                if (dec != null && dec.length() != 1) {
                    errors.add("'decimal_separator' must be a single character.");
                }
            }

            // list_delimiter: non-waivable; if present must be single char
            Object ldRaw = args.get("list_delimiter");
            if (ldRaw != null && isWaived(ldRaw)) {
                errors.add(
                        "'list_delimiter' cannot be waived — it has a default value of '|'"
                                + " and must always be confirmed. Provide {waived:false, parameter:\"|\"}.");
            } else {
                String ld = vs.unwrapToolInputValue(ldRaw, String.class);
                if (ld != null && ld.length() != 1) {
                    errors.add("'list_delimiter' must be a single character.");
                }
            }

            // comment_char: if non-waived, must be single char
            String cc = vs.unwrapToolInputValue(args.get("comment_char"), String.class);
            if (cc != null && cc.length() != 1) {
                errors.add("'comment_char' must be a single character.");
            }

            // data_type_list: if non-waived, validate codes
            String dtl = vs.unwrapToolInputValue(args.get("data_type_list"), String.class);
            if (dtl != null) {
                String[] codes = dtl.split(",", -1);
                for (String code : codes) {
                    if (!VALID_TYPE_CODES.contains(code.trim().toLowerCase())) {
                        errors.add(
                                "'data_type_list' contains invalid type code '"
                                        + code.trim()
                                        + "'. Valid codes: s, i, l, d, b, sl, il, ll, dl, bl.");
                        break;
                    }
                }
            }

            return errors;
        }

        // Group D+E: where_to_import and network params
        private List<String> validateGroupDE(Map<String, Object> args) {
            List<String> errors = new ArrayList<>();

            String where = vs.unwrapToolInputValue(args.get("where_to_import"), String.class);
            if (where == null) where = "current_network_view"; // default

            if (!"current_network_view".equals(where) && !"unassigned_table".equals(where)) {
                errors.add(
                        "'where_to_import' must be exactly one of: 'current_network_view',"
                                + " 'unassigned_table'.");
                return errors;
            }

            if ("current_network_view".equals(where)) {
                // table_type required and must be valid enum
                String tableType = vs.unwrapToolInputValue(args.get("table_type"), String.class);
                if (tableType == null || isWaived(args.get("table_type"))) {
                    errors.add(
                            "'table_type' is required when where_to_import is"
                                    + " 'current_network_view'. Provide 'node', 'edge', or 'network'.");
                } else if (!"node".equals(tableType)
                        && !"edge".equals(tableType)
                        && !"network".equals(tableType)) {
                    errors.add(
                            "'table_type' must be one of: 'node', 'edge', 'network'."
                                    + " Got: '"
                                    + tableType
                                    + "'.");
                }

                // datafile_key_column_index: non-waivable; must be >= 1
                Object kidxRaw = args.get("datafile_key_column_index");
                if (kidxRaw != null && isWaived(kidxRaw)) {
                    errors.add(
                            "'datafile_key_column_index' cannot be waived when"
                                    + " where_to_import is 'current_network_view'. It defaults to 1.");
                } else {
                    Integer kidx = vs.unwrapToolInputValue(kidxRaw, Integer.class);
                    if (kidx != null && kidx < 1) {
                        errors.add("'datafile_key_column_index' must be >= 1.");
                    }
                }

                // network_key_column_name: required (not waived)
                String netKey =
                        vs.unwrapToolInputValue(args.get("network_key_column_name"), String.class);
                if (netKey == null || isWaived(args.get("network_key_column_name"))) {
                    errors.add(
                            "'network_key_column_name' is required when where_to_import is"
                                    + " 'current_network_view'.");
                }
            }

            return errors;
        }

        // Group H: unassigned_table params
        private List<String> validateGroupH(Map<String, Object> args) {
            String where = vs.unwrapToolInputValue(args.get("where_to_import"), String.class);
            if (where == null) where = "current_network_view";

            if ("unassigned_table".equals(where)) {
                String name = vs.unwrapToolInputValue(args.get("new_table_name"), String.class);
                if (name == null || isWaived(args.get("new_table_name"))) {
                    return List.of(
                            "'new_table_name' is required when where_to_import is"
                                    + " 'unassigned_table'.");
                }
            }
            return List.of();
        }

        private boolean isNonWaived(Object rawArg) {
            if (rawArg == null) return false;
            if (rawArg instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) rawArg;
                if (m.containsKey("waived")) {
                    // Conditional wrapper: only present if not waived AND has a value
                    return !Boolean.TRUE.equals(m.get("waived")) && m.containsKey("parameter");
                }
            }
            return true; // plain value, not a conditional wrapper
        }

        private boolean isWaived(Object rawArg) {
            if (rawArg instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) rawArg;
                return Boolean.TRUE.equals(m.get("waived"));
            }
            return false;
        }
    }
}
