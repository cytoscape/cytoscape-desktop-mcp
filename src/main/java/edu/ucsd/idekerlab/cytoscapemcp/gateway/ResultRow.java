package edu.ucsd.idekerlab.cytoscapemcp.gateway;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record ResultRow(
        @JsonPropertyDescription(
                        "Fully qualified command key in 'namespace command' format, e.g. 'network"
                                + " select'.")
                String commandKey,
        @JsonPropertyDescription(
                        "Lucene relevance score; higher means a closer match to the query.")
                float score,
        @JsonPropertyDescription("One-sentence summary of what the command does.") String summary,
        @JsonPropertyDescription("Names of input parameters accepted by this command.")
                List<String> inputs,
        @JsonPropertyDescription(
                        "Top-level output field names from this command's output model. Empty if"
                                + " command does not return JSON.")
                List<String> outputs) {}
