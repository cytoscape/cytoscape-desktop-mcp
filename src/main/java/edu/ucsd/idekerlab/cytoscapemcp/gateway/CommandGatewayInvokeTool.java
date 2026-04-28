package edu.ucsd.idekerlab.cytoscapemcp.gateway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.idekerlab.cytoscapemcp.McpSchema;

import org.cytoscape.command.AvailableCommands;
import org.cytoscape.command.CommandExecutorTaskFactory;
import org.cytoscape.work.FinishStatus;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.SynchronousTaskManager;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskObserver;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool that validates and executes a registered Cytoscape Desktop command. Validates required
 * parameters and rejects unknown parameter names before invoking. Execution is synchronous and
 * blocks until the command completes.
 *
 * <p><strong>Warning:</strong> This tool is state-mutating. Desktop networks, views, tables, and
 * styles may change as a result of invocation.
 */
public class CommandGatewayInvokeTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandGatewayInvokeTool.class);

    static final String TOOL_NAME = "command_gateway_invoke";
    static final String TOOL_TITLE = "Invoke Desktop Command";

    private static final String TOOL_DESCRIPTION =
            "Execute a registered Cytoscape Desktop command by name with a JSON input"
                    + " parameter set and return the command's response. Use this tool only after"
                    + " retrieving the command's full schema from the command retrieval tool to"
                    + " ensure parameters are correct. The tool validates the supplied input"
                    + " parameters against the command's schema: required parameters must be"
                    + " present, unknown parameter names are rejected — all before the command is"
                    + " sent to the desktop. On validation failure, success is false and the"
                    + " failure field lists the specific problems.\n\n"
                    + "WHEN TO USE: This is the execution step after search and schema retrieval."
                    + " Do not guess at parameter names or values — always retrieve the command's"
                    + " schema first. For commands that modify desktop state (layout, selection,"
                    + " style changes, imports) be aware that execution is immediate and"
                    + " irreversible unless the desktop provides an undo mechanism.\n\n"
                    + "WARNING: This tool is state-mutating. Desktop networks, views, tables,"
                    + " and styles may change as a result of invocation depending on the command.\n\n"
                    + "Returns a CommandInvocationResponse. On error, success is false and failure"
                    + " describes the reason; result is null.\n\n"
                    + "## Examples\n\n"
                    + "Example 1 — Select all nodes in the current network:\n"
                    + "{\"commandKey\": \"network select\","
                    + " \"inputParams\": {\"network\": \"current\", \"nodeList\": \"all\"}}\n\n"
                    + "Example 2 — Apply force-directed layout with default parameters:\n"
                    + "{\"commandKey\": \"layout force-directed\", \"inputParams\": {}}\n\n"
                    + "Example 3 — Export the current network as a SIF file:\n"
                    + "{\"commandKey\": \"network export\","
                    + " \"inputParams\": {\"options\": \"SIF\","
                    + " \"OutputFile\": \"/tmp/mynet.sif\"}}\n\n"
                    + "Example 4 — Close a named network:\n"
                    + "{\"commandKey\": \"network destroy\","
                    + " \"inputParams\": {\"network\": \"myNetwork\"}}";

    private static final String INPUT_SCHEMA =
            "{"
                    + "\"type\":\"object\","
                    + "\"required\":[\"commandKey\",\"inputParams\"],"
                    + "\"properties\":{"
                    + "\"commandKey\":{"
                    + "\"type\":\"string\","
                    + "\"description\":\"Required. Fully qualified command key in 'namespace"
                    + " command' format. Must match a key returned by the search or retrieval"
                    + " tools. Example values: 'network select', 'layout force-directed',"
                    + " 'table import file'.\""
                    + "},"
                    + "\"inputParams\":{"
                    + "\"type\":\"object\","
                    + "\"additionalProperties\":true,"
                    + "\"description\":\"Required. JSON object whose keys are the command's input"
                    + " parameter names and values are the parameter values. Required parameters"
                    + " per the command's input schema must be present. Unknown parameter names"
                    + " are rejected. Example: {}, {\\\"network\\\": \\\"current\\\"},"
                    + " {\\\"filePath\\\": \\\"/data/net.sif\\\",'firstRowAsColumnNames': true}.\""
                    + "}}}";

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(CommandInvocationResponse.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AvailableCommands availableCommands;
    private final SynchronousTaskManager<?> syncTaskManager;
    private final CommandExecutorTaskFactory commandExecutorTaskFactory;

    public CommandGatewayInvokeTool(
            AvailableCommands availableCommands,
            SynchronousTaskManager<?> syncTaskManager,
            CommandExecutorTaskFactory commandExecutorTaskFactory) {
        this.availableCommands = availableCommands;
        this.syncTaskManager = syncTaskManager;
        this.commandExecutorTaskFactory = commandExecutorTaskFactory;
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

        if (availableCommands == null || commandExecutorTaskFactory == null) {
            return error("Required Cytoscape services are not available.");
        }

        Map<String, Object> args = request.arguments();
        if (args == null) args = Map.of();

        // --- commandKey ---
        String commandKey = args.get("commandKey") instanceof String s ? s : null;
        if (commandKey == null || commandKey.isBlank()) {
            return error("Required parameter 'commandKey' is missing or blank.");
        }
        String[] parts = commandKey.split(" ", 2);
        if (parts.length != 2) {
            return error(
                    "Invalid commandKey '" + commandKey + "'. Expected 'namespace commandName'.");
        }
        String namespace = parts[0];
        String commandName = parts[1];

        // --- validate command exists ---
        if (!availableCommands.getNamespaces().contains(namespace)
                || !availableCommands.getCommands(namespace).contains(commandName)) {
            return error(
                    "Command not found: '"
                            + commandKey
                            + "'. Use command_gateway_search to find valid command keys.");
        }

        // --- inputParams ---
        Object rawParams = args.get("inputParams");
        Map<String, Object> inputParams;
        if (rawParams instanceof Map) {
            inputParams = (Map<String, Object>) rawParams;
        } else if (rawParams == null) {
            inputParams = Map.of();
        } else {
            return error("'inputParams' must be a JSON object.");
        }

        // --- required-param validation ---
        List<String> argNames = availableCommands.getArguments(namespace, commandName);
        List<String> missing = new ArrayList<>();
        for (String arg : argNames) {
            if (availableCommands.getArgRequired(namespace, commandName, arg)
                    && !inputParams.containsKey(arg)) {
                missing.add(arg);
            }
        }
        if (!missing.isEmpty()) {
            return error("Missing required parameters: " + String.join(", ", missing));
        }

        // --- unknown-param validation ---
        Set<String> knownArgs = new HashSet<>(argNames);
        List<String> unknown =
                inputParams.keySet().stream()
                        .filter(k -> !knownArgs.contains(k))
                        .sorted()
                        .collect(Collectors.toList());
        if (!unknown.isEmpty()) {
            return error(
                    "Unknown parameters: "
                            + String.join(", ", unknown)
                            + ". Valid parameters: "
                            + String.join(", ", argNames));
        }

        // --- execute ---
        StringBuilder resultJson = new StringBuilder();
        boolean[] succeeded = {true};
        String[] failMsg = {null};

        TaskObserver observer =
                new TaskObserver() {
                    @Override
                    public void taskFinished(ObservableTask t) {
                        Object res = t.getResults(String.class);
                        if (res != null) {
                            resultJson.setLength(0);
                            resultJson.append(res.toString());
                        }
                    }

                    @Override
                    public void allFinished(FinishStatus status) {
                        if (status.getType() != FinishStatus.Type.SUCCEEDED) {
                            succeeded[0] = false;
                            resultJson.setLength(0);
                            if (status.getType() == FinishStatus.Type.FAILED) {
                                Exception ex = status.getException();
                                if (ex != null) failMsg[0] = buildExceptionMessage(ex);
                            }
                        }
                    }
                };

        try {
            TaskIterator ti =
                    commandExecutorTaskFactory.createTaskIterator(
                            namespace, commandName, new HashMap<>(inputParams), observer);
            syncTaskManager.execute(ti, observer);
        } catch (Exception e) {
            return error("Command execution failed: " + e.getMessage());
        }

        if (!succeeded[0]) {
            String msg =
                    failMsg[0] != null
                            ? "Command '" + commandKey + "' execution failed: " + failMsg[0]
                            : "Command '"
                                    + commandKey
                                    + "' was rejected during input validation. "
                                    + "Use command_gateway_get to retrieve the full schema for"
                                    + " this command and verify that every required and optional"
                                    + " parameter name, type, and value matches the schema"
                                    + " exactly before retrying.";
            return error(msg);
        }

        String result = resultJson.length() > 0 ? resultJson.toString() : null;
        return CallToolResult.builder()
                .structuredContent(new CommandInvocationResponse(true, null, result))
                .build();
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }

    private static String buildExceptionMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Set<Throwable> seen = new HashSet<>();
        while (t != null && seen.add(t)) {
            if (sb.length() > 0) sb.append(" → caused by: ");
            sb.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
            t = t.getCause();
            if (sb.length() > 2000) {
                sb.append(" [truncated]");
                break;
            }
        }
        return sb.toString();
    }
}
