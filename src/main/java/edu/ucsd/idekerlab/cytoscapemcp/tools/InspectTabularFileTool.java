package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
 * Otherwise, returns the file extension so the agent knows to proceed with delimiter-based import.
 */
public class InspectTabularFileTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(InspectTabularFileTool.class);

    private static final String TOOL_NAME = "inspect_tabular_file";

    private static final String TOOL_DESCRIPTION =
            "Inspect a tabular data file to determine whether it is an Excel workbook"
                    + " (.xls/.xlsx). If Excel, returns the list of sheet names. If not Excel,"
                    + " returns the detected file extension (e.g. '.csv', '.tsv').";

    static final String INPUT_SCHEMA =
            """
            {
              "type": "object",
              "required": ["file_path"],
              "properties": {
                "file_path": {
                  "type": "string",
                  "description": "Absolute path to the tabular data file to inspect."
                }
              }
            }
            """;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Returns the MCP SyncToolSpecification to register with the McpSyncServer. */
    public McpServerFeatures.SyncToolSpecification toSpec() {
        try {
            Tool toolDef =
                    Tool.builder()
                            .name(TOOL_NAME)
                            .description(TOOL_DESCRIPTION)
                            .inputSchema(mapper.readValue(INPUT_SCHEMA, JsonSchema.class))
                            .build();
            return McpServerFeatures.SyncToolSpecification.builder()
                    .tool(toolDef)
                    .callHandler(this::handle)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse INPUT_SCHEMA for " + TOOL_NAME, e);
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

                ObjectNode result = mapper.createObjectNode();
                result.put("is_excel", true);
                ArrayNode sheetsArray = result.putArray("sheets");
                for (String name : sheetNames) {
                    sheetsArray.add(name);
                }

                return success(mapper.writeValueAsString(result));
            } catch (Exception poiException) {
                // Not a valid Excel file — return extension info.
                LOGGER.debug("File is not Excel ({}): {}", poiException.getMessage(), filePath);

                String fileName = file.getName();
                int dotIndex = fileName.lastIndexOf('.');
                String extension = dotIndex >= 0 ? fileName.substring(dotIndex) : "";

                ObjectNode result = mapper.createObjectNode();
                result.put("is_excel", false);
                result.put("detected_extension", extension);

                return success(mapper.writeValueAsString(result));
            }
        } catch (Exception e) {
            LOGGER.error("Error inspecting tabular file", e);
            return error("Failed to inspect tabular file: " + e.getMessage());
        }
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
}
