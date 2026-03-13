package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.idekerlab.cytoscapemcp.McpSchema;

import org.cytoscape.view.vizmap.VisualMappingManager;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool that enumerates all visual style names registered in Cytoscape Desktop. Read-only; does
 * not modify state.
 */
public class GetStylesTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetStylesTool.class);

    private static final String TOOL_NAME = "get_styles";

    private static final String TOOL_TITLE = "List Cytoscape Desktop Styles";

    private static final String TOOL_EXAMPLES =
            "\n\n## Examples\n\n"
                    + "Example 1 — What styles are available?:\n"
                    + "{}\n\n"
                    + "Example 2 — List all visual styles so I can pick one:\n"
                    + "{}\n\n"
                    + "Example 3 — Show me the style names in Cytoscape:\n"
                    + "{}";

    private static final String TOOL_DESCRIPTION =
            "Retrieves the names of all visual styles currently registered in Cytoscape Desktop."
                    + " Use when you need to discover available styles before switching a view to a"
                    + " different style, or to present style choices to the user. To determine which"
                    + " style is currently active on a specific view, retrieve the list of loaded"
                    + " network views which includes each view's applied style name. Read-only; does"
                    + " not modify state.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private record GetStylesResponse(
            @JsonPropertyDescription(
                            "Alphabetically sorted list of all visual style names registered in"
                                    + " Cytoscape Desktop. Each name can be provided to the style"
                                    + " switching tool to apply that style to the current network view,"
                                    + " or used as a clone source when creating a new style."
                                    + "\n\nExamples: [\"default\", \"Marquee\", \"Nested Network"
                                    + " Style\"]")
                    @JsonProperty("styles")
                    List<String> styles) {}

    static final String INPUT_SCHEMA = McpSchema.toJson(McpSchema.InputSchema.builder().build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(GetStylesResponse.class);

    private final VisualMappingManager vmmManager;

    public GetStylesTool(VisualMappingManager vmmManager) {
        this.vmmManager = vmmManager;
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
            List<String> styleNames =
                    vmmManager.getAllVisualStyles().stream()
                            .map(vs -> vs.getTitle())
                            .sorted()
                            .toList();

            return CallToolResult.builder()
                    .structuredContent(new GetStylesResponse(styleNames))
                    .build();
        } catch (Exception e) {
            LOGGER.error("Error enumerating visual styles", e);
            return error("Failed to enumerate visual styles: " + e.getMessage());
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
