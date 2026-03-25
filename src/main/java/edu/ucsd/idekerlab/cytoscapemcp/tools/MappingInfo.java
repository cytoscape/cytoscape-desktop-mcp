package edu.ucsd.idekerlab.cytoscapemcp.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Describes a visual mapping currently applied to a visual property in the active style. Returned
 * as a nested object within {@link MappablePropertyEntry}. Reusable by any tool that needs to
 * report mapping state.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MappingInfo(
        @JsonPropertyDescription(
                        "Mapping type: ContinuousMapping (numeric interpolation or color gradient),"
                                + " DiscreteMapping (explicit value-to-value map), or"
                                + " PassthroughMapping (column value used directly)."
                                + "\n\nExamples: \"ContinuousMapping\", \"DiscreteMapping\","
                                + " \"PassthroughMapping\"")
                @JsonProperty("type")
                String type,
        @JsonPropertyDescription(
                        "Name of the data column driving this mapping."
                                + "\n\nExamples: \"Degree\", \"GeneType\", \"name\"")
                @JsonProperty("column")
                String column,
        @JsonPropertyDescription(
                        "Human-readable summary of the mapping. For continuous mappings shows the"
                                + " column name and the value range of the first and last breakpoints."
                                + " For discrete mappings shows the column name and entry count. For"
                                + " passthrough mappings shows the column name with a passthrough"
                                + " indicator."
                                + "\n\nExamples: \"Degree \u2192 10.0\u201350.0\","
                                + " \"GeneType \u2192 5 entries\", \"name (passthrough)\"")
                @JsonProperty("summary")
                String summary) {}
