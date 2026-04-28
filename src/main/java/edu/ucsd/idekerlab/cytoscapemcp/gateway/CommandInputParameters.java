package edu.ucsd.idekerlab.cytoscapemcp.gateway;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Structured input parameter definitions for a Cytoscape Desktop command. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommandInputParameters(
        @JsonPropertyDescription("Parameters that must be provided to invoke the command.")
                List<CommandInputParameter> required,
        @JsonPropertyDescription("Parameters that may optionally be provided to the command.")
                List<CommandInputParameter> optional) {}
