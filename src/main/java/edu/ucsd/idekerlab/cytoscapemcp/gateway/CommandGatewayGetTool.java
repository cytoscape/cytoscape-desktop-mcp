package edu.ucsd.idekerlab.cytoscapemcp.gateway;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.idekerlab.cytoscapemcp.McpSchema;

import org.cytoscape.command.AvailableCommands;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool that retrieves the complete schema for one or more Cytoscape Desktop commands by key.
 * Reads live from {@link AvailableCommands} to guarantee freshness — does not depend on the Lucene
 * index and therefore works correctly even before the first ETL scan completes.
 */
public class CommandGatewayGetTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandGatewayGetTool.class);

    static final String TOOL_NAME = "command_gateway_get";
    static final String TOOL_TITLE = "Get Desktop Command Schemas";

    private static final String TOOL_DESCRIPTION =
            "Retrieve the complete schema definition for one or more Cytoscape Desktop"
                    + " commands by their fully qualified command key. Use this tool after"
                    + " identifying candidate command keys through the command search tool to obtain"
                    + " the precise input parameter definitions — names, types, required vs"
                    + " optional, descriptions, and example values — and the full output schema"
                    + " before invoking a command. Accepts up to 10 command keys per call for"
                    + " batching. This tool is read-only and does not modify desktop state.\n\n"
                    + "WHEN TO USE: Call this tool for every command you plan to invoke, to obtain"
                    + " the input schema needed to construct valid invocation parameters. If a"
                    + " command key returned by search has a high match score and the summary looks"
                    + " right, retrieve its full schema here before invoking. Batch multiple"
                    + " candidate keys in a single call when comparing alternatives.\n\n"
                    + "Returns a DesktopCommandsResponse. Command keys not found in the desktop"
                    + " are silently omitted from results. If no keys are found, success is"
                    + " false.\n\n"
                    + "## Examples\n\n"
                    + "Example 1 — Retrieve full schema for a single command found by search:\n"
                    + "{\"commandKeys\": [\"network select\"]}\n\n"
                    + "Example 2 — Batch-retrieve schemas for two layout candidates:\n"
                    + "{\"commandKeys\": [\"layout force-directed\", \"layout hierarchical\"]}\n\n"
                    + "Example 3 — Get parameter details for a table export command:\n"
                    + "{\"commandKeys\": [\"table export\"]}";

    // Raw JSON string for inputSchema — uses array type which InputSchema.builder doesn't support
    private static final String INPUT_SCHEMA =
            "{"
                    + "\"type\":\"object\","
                    + "\"required\":[\"commandKeys\"],"
                    + "\"properties\":{"
                    + "\"commandKeys\":{"
                    + "\"type\":\"array\","
                    + "\"items\":{\"type\":\"string\"},"
                    + "\"minItems\":1,"
                    + "\"maxItems\":10,"
                    + "\"description\":\"Required. One or more fully qualified command keys in"
                    + " 'namespace command' format as returned by the command search tool."
                    + " Maximum 10 keys per call; excess keys are ignored."
                    + " Example: [\\\"network select\\\"],"
                    + " [\\\"layout force-directed\\\", \\\"layout hierarchical\\\"].\""
                    + "}}}";

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(DesktopCommandsResponse.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AvailableCommands availableCommands;

    public CommandGatewayGetTool(AvailableCommands availableCommands) {
        this.availableCommands = availableCommands;
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

    @SuppressWarnings("unchecked")
    private CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        LOGGER.info("Tool call received: {}", TOOL_NAME);

        if (availableCommands == null) {
            return error("AvailableCommands service is not available.");
        }

        Map<String, Object> args = request.arguments();
        if (args == null) args = Map.of();

        Object keysRaw = args.get("commandKeys");
        if (!(keysRaw instanceof List<?>)) {
            return error("Required parameter 'commandKeys' is missing or not an array.");
        }
        List<String> commandKeys =
                ((List<?>) keysRaw)
                        .stream()
                                .filter(o -> o instanceof String)
                                .map(Object::toString)
                                .limit(10)
                                .collect(Collectors.toList());

        if (commandKeys.isEmpty()) {
            return error("Parameter 'commandKeys' must contain at least one valid string key.");
        }

        List<DesktopCommand> results = new ArrayList<>();
        for (String commandKey : commandKeys) {
            String[] parts = commandKey.split(" ", 2);
            if (parts.length != 2) continue;
            String namespace = parts[0];
            String commandName = parts[1];

            if (!availableCommands.getNamespaces().contains(namespace)) continue;
            if (!availableCommands.getCommands(namespace).contains(commandName)) continue;

            String description = availableCommands.getDescription(namespace, commandName);
            String longDescription = availableCommands.getLongDescription(namespace, commandName);
            boolean supportsJson = availableCommands.getSupportsJSON(namespace, commandName);
            String outputExample =
                    supportsJson ? availableCommands.getExampleJSON(namespace, commandName) : null;
            List<String> argNames = availableCommands.getArguments(namespace, commandName);
            String inputSchema = buildInputSchemaJson(namespace, commandName, argNames);

            results.add(
                    new DesktopCommand(
                            commandKey,
                            namespace,
                            commandName,
                            description,
                            longDescription,
                            supportsJson,
                            inputSchema,
                            outputExample));
        }

        if (results.isEmpty()) {
            return error("No matching commands found for the supplied keys.");
        }
        return CallToolResult.builder()
                .structuredContent(new DesktopCommandsResponse(true, null, results))
                .build();
    }

    // -- Helpers --------------------------------------------------------------

    private String buildInputSchemaJson(String ns, String cmd, List<String> argNames) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (String arg : argNames) {
            Class<?> argType = availableCommands.getArgType(ns, cmd, arg);
            String jsonType = CommandETLService.mapArgType(argType);
            String desc = availableCommands.getArgDescription(ns, cmd, arg);
            String example = availableCommands.getArgExampleStringValue(ns, cmd, arg);
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", jsonType);
            if (desc != null && !desc.isBlank()) prop.put("description", desc);
            if (example != null && !example.isBlank()) prop.put("examples", List.of(example));
            properties.put(arg, prop);
            if (availableCommands.getArgRequired(ns, cmd, arg)) required.add(arg);
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) schema.put("required", required);
        try {
            return MAPPER.writeValueAsString(schema);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}
