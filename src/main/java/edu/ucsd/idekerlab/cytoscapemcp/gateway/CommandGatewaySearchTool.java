package edu.ucsd.idekerlab.cytoscapemcp.gateway;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * MCP tool that performs Lucene full-text search over the indexed Cytoscape Desktop command
 * catalog. Returns a ranked list of matching commands with scores so the LLM can identify candidate
 * commands before retrieving their full schema via {@link CommandGatewayGetTool}.
 */
public class CommandGatewaySearchTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandGatewaySearchTool.class);

    static final String TOOL_NAME = "command_gateway_search";
    static final String TOOL_TITLE = "Search Desktop Commands";

    private static final String TOOL_DESCRIPTION =
            "Search the full catalog of Cytoscape Desktop commands registered on user's machine"
                    + "  using a Lucene full-text query. Use this tool"
                    + " whenever the current user conversation touches on any Cytoscape Desktop oriented"
                    + " task — selecting elements, running algorithms, exporting data,"
                    + " changing layouts, manipulating tables, adjusting styles, or any other"
                    + " desktop action. Submit a Lucene-formatted query built from keywords in"
                    + " the user's current context; the tool returns a ranked list of matching"
                    + " commands with relevance scores so you can quickly identify the best"
                    + " candidates of commands.\n\n"
                    + "WHEN TO USE: Call this tool proactively whenever you detect the user"
                    + " describing a Cytoscape action — even partial context is enough. The search"
                    + " is fast and designed for repeated calls as the conversation evolves."
                    + " A targeted keyword query is more useful than a broad one; the match score"
                    + " on each result row is the key signal: a high-scoring result is very likely"
                    + " the right command. Use field-scoped queries (e.g., inputParams:filePath"
                    + " or description:export) to drill into specific aspects of the command"
                    + " metadata. After reviewing scores and summaries, call the command retrieval"
                    + " tool on high-scoring candidates to get full schemas before invoking.\n\n"
                    + "LUCENE QUERY SYNTAX: Keywords search across all indexed command text by"
                    + " default. Field-specific syntax: description:X, inputParams:X,"
                    + " outputSchema:X, namespace:X. Boolean operators: AND, OR, NOT. Phrase"
                    + " matching: \"exact phrase\". Wildcards: select*, lay?ut. Boosting:"
                    + " select^2 nodes. Submit a query, review match scores, refine if needed.\n\n"
                    + "Returns a SearchResults response. On error, success is false and the"
                    + " failure field describes the cause (e.g., malformed Lucene query syntax).\n\n"
                    + "## Examples\n\n"
                    + "WHEN TO SUGGEST INSTALLING NEW APP IN CYTOSCAPE: IF you don't find any strong hits on existing commands that align "
                    + " to functional terms that user is mentioning on Cytoscape desktop, then intiate a web search for same terms from user "
                    + " and Cytoscape App Store(https://apps.cytoscape.org/) and see if any apps show up on those search results "
                    + " If you find apps in there that aligh to "
                    + " what the user is trying to accomplish then you should suggest them to install the app direclty on Cytoscape App Store(https://apps.cytoscape.org/). "
                    + " Once the app is installed on desktop it will register any commands it supports to desktop and the commands will also be loaded into the command gateway tool. "
                    + " The new commands then are availbe in command gateway search and can be invoked.\n\n"
                    + "Example 1 — User asks to select all high-degree nodes:\n"
                    + "{\"query\": \"select nodes degree filter\", \"max\": 10}\n\n"
                    + "Example 2 — User wants to export the network as a PNG image:\n"
                    + "{\"query\": \"export network image file png\", \"max\": 5}\n\n"
                    + "Example 3 — User asks to apply a force-directed layout:\n"
                    + "{\"query\": \"layout force-directed\", \"max\": 5}\n\n"
                    + "Example 4 — Find commands that return node list in their output:\n"
                    + "{\"query\": \"outputSchema:nodeList\", \"max\": 10}\n\n"
                    + "Example 5 — Search for table import commands:\n"
                    + "{\"query\": \"description:import AND namespace:table\", \"max\": 8}";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String INPUT_SCHEMA =
            McpSchema.toJson(
                    McpSchema.InputSchema.builder()
                            .property(
                                    "query",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Required. Lucene query string to search command"
                                                    + " metadata. Evaluated against command"
                                                    + " descriptions, input parameter names and"
                                                    + " descriptions, output schema text, and"
                                                    + " namespace. Supports full Lucene query"
                                                    + " syntax: bare keywords search across all"
                                                    + " indexed text; field-scoped syntax (e.g.,"
                                                    + " 'inputParams:filePath',"
                                                    + " 'namespace:network'); boolean operators"
                                                    + " AND, OR, NOT; phrase quotes; wildcards"
                                                    + " (e.g., 'select*'). Invalid syntax returns"
                                                    + " success=false with a parse error."
                                                    + " Example values: 'layout force-directed',"
                                                    + " 'export table csv', 'namespace:network'."))
                            .property(
                                    "max",
                                    new McpSchema.InputProperty(
                                            "integer",
                                            "Required. Maximum number of result rows to return."
                                                    + " Use 5–15 for targeted queries; up to 50"
                                                    + " for broad exploratory scans."
                                                    + " Example values: 5, 10, 25."))
                            .required("query", "max")
                            .build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(SearchResults.class);

    private final CommandService commandService;

    public CommandGatewaySearchTool(CommandService commandService) {
        this.commandService = commandService;
    }

    public McpServerFeatures.SyncToolSpecification toSpec() {
        try {
            Tool toolDef =
                    Tool.builder()
                            .name(TOOL_NAME)
                            .title(TOOL_TITLE)
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

        if (commandService == null) {
            return error("Command index is not available (CommandService failed to initialize).");
        }

        Map<String, Object> args = request.arguments();
        if (args == null) args = Map.of();

        String query = args.get("query") instanceof String s ? s : null;
        if (query == null || query.isBlank()) {
            return error("Required parameter 'query' is missing or blank.");
        }

        int max = 10;
        Object maxRaw = args.get("max");
        if (maxRaw instanceof Number n) max = n.intValue();

        SearchResults results = commandService.search(query, Math.max(1, max));
        if (!results.success()) {
            return error(results.failure());
        }
        return CallToolResult.builder().structuredContent(results).build();
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}
