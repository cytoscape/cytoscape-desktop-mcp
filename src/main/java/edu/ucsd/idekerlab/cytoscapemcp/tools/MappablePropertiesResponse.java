package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Top-level response for the {@code get_mappable_properties} tool. Lists all visual properties that
 * support data-driven mappings, grouped by node and edge categories.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MappablePropertiesResponse(
        @JsonPropertyDescription(
                        "Name of the active visual style applied to the current network."
                                + "\n\nExamples: \"default\", \"Marquee\", \"galFiltered Style\"")
                @JsonProperty("style_name")
                String styleName,
        @JsonPropertyDescription(
                        "Node visual properties that support data-driven mappings — one entry per"
                                + " property whose value type has a plain-text representation"
                                + " (colors, numbers, shapes, line types, fonts, booleans). Each"
                                + " entry indicates the property's continuous mapping compatibility"
                                + " and any currently applied mapping.")
                @JsonProperty("node_properties")
                List<MappablePropertyEntry> nodeProperties,
        @JsonPropertyDescription(
                        "Edge visual properties that support data-driven mappings — one entry per"
                                + " property whose value type has a plain-text representation"
                                + " (colors, numbers, shapes, line types, fonts, booleans). Each"
                                + " entry indicates the property's continuous mapping compatibility"
                                + " and any currently applied mapping.")
                @JsonProperty("edge_properties")
                List<MappablePropertyEntry> edgeProperties) {}
