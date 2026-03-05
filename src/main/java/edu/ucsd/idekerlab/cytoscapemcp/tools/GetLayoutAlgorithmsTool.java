package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.idekerlab.cytoscapemcp.McpSchema;

import org.cytoscape.view.layout.CyLayoutAlgorithm;
import org.cytoscape.view.layout.CyLayoutAlgorithmManager;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool that lists all available layout algorithms in Cytoscape with their names and display
 * names. Read-only; does not modify state.
 */
public class GetLayoutAlgorithmsTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetLayoutAlgorithmsTool.class);

    private static final String TOOL_NAME = "get_layout_algorithms";

    private static final String TOOL_DESCRIPTION =
            "List all available layout algorithms in Cytoscape with their names and display names."
                    + " Read-only; does not modify state.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record LayoutEntry(
            @JsonProperty("name") String name, @JsonProperty("displayName") String displayName) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record GetLayoutAlgorithmsResult(@JsonProperty("layouts") List<LayoutEntry> layouts) {}

    static final String INPUT_SCHEMA = McpSchema.toJson(McpSchema.InputSchema.builder().build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(GetLayoutAlgorithmsResult.class);

    private final CyLayoutAlgorithmManager layoutManager;

    public GetLayoutAlgorithmsTool(CyLayoutAlgorithmManager layoutManager) {
        this.layoutManager = layoutManager;
    }

    /** Returns the MCP SyncToolSpecification to register with the McpSyncServer. */
    public McpServerFeatures.SyncToolSpecification toSpec() {
        try {
            Tool toolDef =
                    Tool.builder()
                            .name(TOOL_NAME)
                            .description(TOOL_DESCRIPTION)
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
            Collection<CyLayoutAlgorithm> algorithms = layoutManager.getAllLayouts();
            List<LayoutEntry> entries = new ArrayList<>();

            for (CyLayoutAlgorithm algo : algorithms) {
                entries.add(new LayoutEntry(algo.getName(), algo.toString()));
            }

            return CallToolResult.builder()
                    .structuredContent(new GetLayoutAlgorithmsResult(entries))
                    .build();
        } catch (Exception e) {
            LOGGER.error("Error listing layout algorithms", e);
            return error("Failed to list layout algorithms: " + e.getMessage());
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
