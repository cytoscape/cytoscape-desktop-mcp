package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool that reads column headers and up to three sample rows from a tabular file so the agent
 * can present column choices to the user for source/target/interaction mapping.
 *
 * <p>Supports two modes:
 *
 * <ul>
 *   <li><b>Excel</b> — {@code excel_sheet} is provided; opens the workbook via Apache POI and reads
 *       the named sheet.
 *   <li><b>Text (CSV/TSV/custom)</b> — {@code excel_sheet} is absent; opens the file as plain text
 *       and splits lines by {@code delimiter_char_code} (ASCII integer, defaults to comma).
 * </ul>
 */
public class GetFileColumnsTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetFileColumnsTool.class);

    private static final String TOOL_NAME = "get_file_columns";

    private static final String TOOL_TITLE = "Read Cytoscape Desktop File Columns";

    private static final String TOOL_EXAMPLES =
            "\n\n## Examples\n\n"
                    + "Example 1 — Read column headers from a file for Cytoscape desktop import:\n"
                    + "{\"file_path\": \"/path/to/data.csv\", \"delimiter_char_code\": 44, \"use_header_row\": true}\n\n"
                    + "Example 2 — Preview columns from a file:\n"
                    + "{\"file_path\": \"/path/to/data.tsv\", \"delimiter_char_code\": 9, \"use_header_row\": true}\n\n"
                    + "Example 3 — Read columns from an Excel sheet for Cytoscape desktop import:\n"
                    + "{\"file_path\": \"/path/to/data.xlsx\", \"use_header_row\": true, \"excel_sheet\": \"Sheet1\"}\n\n"
                    + "Example 4 — Get column headers from a file:\n"
                    + "Inspect the file first to determine input params as needed.\n"
                    + "{\"file_path\": \"/path/to/data.csv\", \"delimiter_char_code\": 44, \"use_header_row\": true}\n\n";

    private static final String TOOL_DESCRIPTION =
            "Retrieve column headers and first three rows from a tabular file. Use when importing"
                    + " network data into Cytoscape Desktop to preview columns before mapping."
                    + " Supports both Excel workbooks and plain-text delimited files.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** App response model — Jackson annotations also drive victools OUTPUT_SCHEMA generation. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record GetFileColumnsCallResult(
            @JsonPropertyDescription(
                            "Column header names from the file. Ordinal names ('Column 1',"
                                    + " 'Column 2', ...) if use_header_row was false.")
                    @JsonProperty("columns")
                    List<String> columns,
            @JsonPropertyDescription(
                            "Up to the first three data rows in the file, each as an array of string values"
                                    + " aligned with columns. The first three rows are included in the response to help determine if column header row is included or not.")
                    @JsonProperty("sample_rows")
                    List<List<String>> sampleRows) {}

    static final String INPUT_SCHEMA =
            McpSchema.toJson(
                    McpSchema.InputSchema.builder()
                            .required("file_path", "use_header_row")
                            .property(
                                    "file_path",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Required. Absolute path to the tabular file."))
                            .property(
                                    "delimiter_char_code",
                                    new McpSchema.InputProperty(
                                            "integer",
                                            "Optional. ASCII code of the delimiter character"
                                                    + " (e.g. 44=comma, 9=tab, 124=pipe)."
                                                    + " Required for non-Excel files. Ignored for"
                                                    + " Excel."))
                            .property(
                                    "use_header_row",
                                    new McpSchema.InputProperty(
                                            "boolean",
                                            "Required. If true, the first row is treated as column headers and those strings"
                                                    + " appear in 'columns'. If false, ordinal names are generated"
                                                    + " ('Column 1', 'Column 2', ...) and those ordinal names"
                                                    + " appear in 'columns' instead."))
                            .property(
                                    "excel_sheet",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Optional. Name of the Excel sheet to read."
                                                    + " Required when reading an Excel file."
                                                    + " Ignored for text files."))
                            .build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(GetFileColumnsCallResult.class);

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
            @SuppressWarnings("unchecked")
            Map<String, Object> args = (Map<String, Object>) request.arguments();

            String filePath = (String) args.get("file_path");
            if (filePath == null || filePath.isBlank()) {
                return error("file_path is required");
            }

            Object useHeaderObj = args.get("use_header_row");
            if (useHeaderObj == null) {
                return error("use_header_row is required");
            }
            boolean useHeaderRow = Boolean.TRUE.equals(useHeaderObj);

            String excelSheet = (String) args.get("excel_sheet");
            Number delimCode = (Number) args.get("delimiter_char_code");

            File file = new File(filePath);
            if (!file.exists()) {
                return error("File not found: " + filePath);
            }

            if (excelSheet != null) {
                return handleExcel(file, excelSheet, useHeaderRow);
            } else {
                char delimiter = delimCode != null ? (char) delimCode.intValue() : ',';
                return handleText(file, delimiter, useHeaderRow);
            }
        } catch (Exception e) {
            LOGGER.error("Error reading file columns", e);
            return error("Failed to read columns: " + e.getMessage());
        }
    }

    // -- Excel path -----------------------------------------------------------

    private CallToolResult handleExcel(File file, String sheetName, boolean useHeaderRow)
            throws Exception {
        try (Workbook wb = WorkbookFactory.create(file)) {
            Sheet sheet = wb.getSheet(sheetName);
            if (sheet == null) {
                return error("Sheet not found: " + sheetName);
            }

            DataFormatter fmt = new DataFormatter();
            int firstRowIndex = sheet.getFirstRowNum();
            Row firstRow = sheet.getRow(firstRowIndex);
            if (firstRow == null) {
                return buildResult(List.of(), List.of());
            }

            int colCount = firstRow.getLastCellNum();
            List<String> columns;
            int sampleStart;

            if (useHeaderRow) {
                columns = readRowCells(firstRow, colCount, fmt);
                sampleStart = firstRowIndex + 1;
            } else {
                columns = ordinalNames(colCount);
                sampleStart = firstRowIndex;
            }

            List<List<String>> sampleRows = new ArrayList<>();
            for (int r = sampleStart; r < sampleStart + 3; r++) {
                Row row = sheet.getRow(r);
                if (row == null) break;
                sampleRows.add(readRowCells(row, colCount, fmt));
            }

            return buildResult(columns, sampleRows);
        }
    }

    private static List<String> readRowCells(Row row, int colCount, DataFormatter fmt) {
        List<String> cells = new ArrayList<>(colCount);
        for (int c = 0; c < colCount; c++) {
            Cell cell = row.getCell(c);
            cells.add(cell != null ? fmt.formatCellValue(cell) : "");
        }
        return cells;
    }

    // -- Text path ------------------------------------------------------------

    private CallToolResult handleText(File file, char delimiter, boolean useHeaderRow)
            throws Exception {
        String delimPattern = Pattern.quote(String.valueOf(delimiter));

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String firstLine = reader.readLine();
            if (firstLine == null) {
                return buildResult(List.of(), List.of());
            }

            String[] firstParts = firstLine.split(delimPattern, -1);
            List<String> columns;
            List<List<String>> sampleRows = new ArrayList<>();

            if (useHeaderRow) {
                columns = trimAll(firstParts);
            } else {
                columns = ordinalNames(firstParts.length);
                sampleRows.add(trimAll(firstParts));
            }

            int maxSample = useHeaderRow ? 3 : 2;
            String line;
            while (sampleRows.size() < maxSample && (line = reader.readLine()) != null) {
                sampleRows.add(trimAll(line.split(delimPattern, -1)));
            }

            return buildResult(columns, sampleRows);
        }
    }

    // -- Shared helpers -------------------------------------------------------

    private static List<String> ordinalNames(int count) {
        List<String> names = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            names.add("Column " + i);
        }
        return names;
    }

    private static List<String> trimAll(String[] parts) {
        List<String> list = new ArrayList<>(parts.length);
        for (String p : parts) {
            list.add(p.trim());
        }
        return list;
    }

    private CallToolResult buildResult(List<String> columns, List<List<String>> sampleRows) {
        return CallToolResult.builder()
                .structuredContent(new GetFileColumnsCallResult(columns, sampleRows))
                .build();
    }

    // -- Result helpers -------------------------------------------------------

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}
