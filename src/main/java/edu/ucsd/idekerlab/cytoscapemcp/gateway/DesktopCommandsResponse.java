package edu.ucsd.idekerlab.cytoscapemcp.gateway;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DesktopCommandsResponse(
        @JsonPropertyDescription("True when at least one command was found and returned.")
                boolean success,
        @JsonPropertyDescription("When success is false, a human-readable error. Null on success.")
                String failure,
        @JsonPropertyDescription("One entry per command key that was found in the desktop.")
                List<DesktopCommand> results) {}
