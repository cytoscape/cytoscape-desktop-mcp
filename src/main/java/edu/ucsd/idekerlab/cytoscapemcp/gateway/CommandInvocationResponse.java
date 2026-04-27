package edu.ucsd.idekerlab.cytoscapemcp.gateway;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommandInvocationResponse(
        @JsonPropertyDescription(
                        "True if the command was found, validated, and executed without error.")
                boolean success,
        @JsonPropertyDescription(
                        "When success is false, describes validation failures or execution errors."
                                + " Null on success.")
                String failure,
        @JsonPropertyDescription(
                        "JSON blob of the command's response data. Null on failure or if the"
                                + " command produces no output.")
                String result) {}
