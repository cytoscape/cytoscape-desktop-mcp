package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
 * MCP tool that inspects a tabular data file to determine if it is an Excel workbook. If so,
 * enumerates the sheet names so the agent can prompt the user to select which sheet to import.
 * Otherwise, returns the detected delimiter character code so the agent knows to proceed with
 * delimiter-based import.
 */
public class InspectTabularFileTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(InspectTabularFileTool.class);

    private static final String TOOL_NAME = "inspect_tabular_file";

    private static final String TOOL_TITLE = "Inspect Cytoscape Desktop Import File";

    private static final String TOOL_EXAMPLES =
            "\n\n## Examples\n\n"
                    + "Example 1 — What tabular format is a file encoded:\n"
                    + "{\"file_path\": \"/path/to/data.xlsx\"}\n\n"
                    + "Example 2 — Inspect a file to determine its format for Cytoscape desktop import:\n"
                    + "{\"file_path\": \"/path/to/data.csv\"}\n\n"
                    + "Example 3 — What sheets are in this Excel workbook:\n"
                    + "{\"file_path\": \"/path/to/workbook.xlsx\"}\n\n"
                    + "Example 4 — What delimiter is used in a file for importing into Cytoscape desktop:\n"
                    + "{\"file_path\": \"/path/to/data.csv\"}\n\n";

    private static final String TOOL_DESCRIPTION =
            "Inspect a tabular data file to determine whether it is an Excel workbook or a"
                    + " plain-text delimited file. For use when importing network data into Cytoscape"
                    + " Desktop. For Excel files, enumerates sheet names; for text files, detects the"
                    + " delimiter character.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record InspectTabularFileCallResult(
            @JsonPropertyDescription("True if the file is an Excel workbook; false if plain-text.")
                    @JsonProperty("is_excel")
                    boolean isExcel,
            @JsonPropertyDescription(
                            "Sheet names in the workbook. Present only when is_excel is true.")
                    @JsonProperty("sheets")
                    List<String> sheets,
            @JsonPropertyDescription(
                            "ASCII code of the detected delimiter character"
                                    + " (e.g. 44=comma, 9=tab, 124=pipe, 59=semicolon, 32=space)."
                                    + " Present only when is_excel is false.")
                    @JsonProperty("detected_delimiter_char_code")
                    Integer detectedDelimiterCharCode) {}

    static final String INPUT_SCHEMA =
            McpSchema.toJson(
                    McpSchema.InputSchema.builder()
                            .required("file_path")
                            .property(
                                    "file_path",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Required. Absolute path to the tabular data file to inspect."))
                            .build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(InspectTabularFileCallResult.class);

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

            File file = new File(filePath);
            if (!file.exists()) {
                return error("File not found: " + filePath);
            }

            // Try opening as an Excel workbook via POI.
            try (Workbook workbook = WorkbookFactory.create(file)) {
                int sheetCount = workbook.getNumberOfSheets();
                List<String> sheetNames = new ArrayList<>(sheetCount);
                for (int i = 0; i < sheetCount; i++) {
                    sheetNames.add(workbook.getSheetName(i));
                }
                return CallToolResult.builder()
                        .structuredContent(new InspectTabularFileCallResult(true, sheetNames, null))
                        .build();
            } catch (Exception poiException) {
                // Not a valid Excel file — detect delimiter.
                LOGGER.debug("File is not Excel ({}): {}", poiException.getMessage(), filePath);

                int delimiterCharCode = detectDelimiter(file);

                return CallToolResult.builder()
                        .structuredContent(
                                new InspectTabularFileCallResult(false, null, delimiterCharCode))
                        .build();
            }
        } catch (Exception e) {
            LOGGER.error("Error inspecting tabular file", e);
            return error("Failed to inspect tabular file: " + e.getMessage());
        }
    }

    // -- Delimiter detection --------------------------------------------------

    /**
     * Reads the first line of the file and counts occurrences of common delimiters. Returns the
     * ASCII code of the most frequently occurring one, preferring tab if any tabs are present.
     * Falls back to comma (44) if the file is empty or no delimiters are found.
     */
    private static int detectDelimiter(File file) {
        String firstLine;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            firstLine = reader.readLine();
        } catch (Exception e) {
            return 44; // fallback to comma
        }

        if (firstLine == null || firstLine.isEmpty()) {
            return 44; // fallback to comma
        }

        int tabs = 0, commas = 0, pipes = 0, semicolons = 0, spaces = 0;
        for (int i = 0; i < firstLine.length(); i++) {
            switch (firstLine.charAt(i)) {
                case '\t' -> tabs++;
                case ',' -> commas++;
                case '|' -> pipes++;
                case ';' -> semicolons++;
                case ' ' -> spaces++;
            }
        }

        // Prefer tab if any are present — tabs are unambiguous delimiters
        if (tabs > 0) {
            return 9;
        }

        // Find the delimiter with the highest count
        int maxCount = commas;
        int bestDelimiter = 44; // comma

        if (pipes > maxCount) {
            maxCount = pipes;
            bestDelimiter = 124;
        }
        if (semicolons > maxCount) {
            maxCount = semicolons;
            bestDelimiter = 59;
        }
        if (spaces > maxCount) {
            maxCount = spaces;
            bestDelimiter = 32;
        }

        return bestDelimiter; // defaults to comma (44) if all counts are 0
    }

    // -- Result helpers -------------------------------------------------------

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}
