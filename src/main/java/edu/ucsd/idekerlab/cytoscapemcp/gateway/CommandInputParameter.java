package edu.ucsd.idekerlab.cytoscapemcp.gateway;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** One input parameter for a Cytoscape Desktop command. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommandInputParameter(
        @JsonPropertyDescription("The argument name as accepted by the Cytoscape command.")
                String name,
        @JsonPropertyDescription(
                        "JSON Schema type for this parameter: string, integer, number, or boolean.")
                String type,
        @JsonPropertyDescription(
                        "Human-readable description of the parameter. Null if not provided.")
                String description,
        @JsonPropertyDescription("One or more example values for this parameter.")
                List<String> examples,
        @JsonPropertyDescription("True if this parameter must be provided to invoke the command.")
                boolean required) {}
