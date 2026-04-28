package edu.ucsd.idekerlab.cytoscapemcp.gateway;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DesktopCommand(
        @JsonPropertyDescription("Fully qualified command key in 'namespace command' format.")
                String commandKey,
        @JsonPropertyDescription("Command namespace, e.g. 'network', 'layout', 'table'.")
                String namespace,
        @JsonPropertyDescription(
                        "Command name within the namespace, e.g. 'select', 'force-directed'.")
                String commandName,
        @JsonPropertyDescription("Short description of the command.") String description,
        @JsonPropertyDescription(
                        "Extended description if provided by the command's author. May be null.")
                String longDescription,
        @JsonPropertyDescription("True if this command produces a JSON result.")
                boolean supportsJson,
        @JsonPropertyDescription(
                        "Structured input parameter definitions, split into required and optional"
                                + " parameters.")
                CommandInputParameters inputSchema,
        @JsonPropertyDescription(
                        "Representative JSON example of the command's output data. Not a formal"
                                + " JSON Schema — use it to understand the output field names and"
                                + " value shapes. Null if supportsJson is false.")
                String outputExample) {}
