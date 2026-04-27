package edu.ucsd.idekerlab.cytoscapemcp.gateway;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResults(
        @JsonPropertyDescription("True when the search executed without error.") boolean success,
        @JsonPropertyDescription(
                        "When success is false, a human-readable description of the error. Null on"
                                + " success.")
                String failure,
        @JsonPropertyDescription(
                        "Ranked list of matching commands ordered by descending match score.")
                List<ResultRow> results) {}
