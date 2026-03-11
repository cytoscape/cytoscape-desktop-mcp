package edu.ucsd.idekerlab.cytoscapemcp.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Represents one visual property that supports data-driven mapping, within a {@link
 * MappablePropertiesResponse}. Includes the property's mapping compatibility type and any currently
 * applied mapping.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MappablePropertyEntry(
        @JsonPropertyDescription(
                        "Machine-readable visual property identifier. Use this ID when creating a"
                                + " mapping for this property."
                                + "\n\nExamples: \"NODE_FILL_COLOR\", \"NODE_SIZE\","
                                + " \"EDGE_WIDTH\"")
                @JsonProperty("id")
                String id,
        @JsonPropertyDescription(
                        "Human-readable display name for this visual property."
                                + "\n\nExamples: \"Node Fill Color\", \"Node Size\","
                                + " \"Edge Width\"")
                @JsonProperty("displayName")
                String displayName,
        @JsonPropertyDescription(
                        "Value type: Paint, Double, Integer, NodeShape, ArrowShape, LineType,"
                                + " LabelBackgroundShape, EdgeStacking, Font, String, or Boolean."
                                + "\n\nExamples: \"Paint\", \"Double\", \"NodeShape\"")
                @JsonProperty("valueType")
                String valueType,
        @JsonPropertyDescription(
                        "Indicates what kind of continuous mapping this property supports."
                                + " 'color-gradient' for color properties that interpolate between"
                                + " colors. 'continuous' for numeric properties that interpolate"
                                + " between numbers. 'discrete' for discrete-typed properties"
                                + " (shapes, line types) that use threshold-based switching in"
                                + " continuous mappings. Absent for properties that do not support"
                                + " continuous mapping at all (e.g. String, Boolean) — these"
                                + " properties only support discrete or passthrough mappings."
                                + "\n\nExamples: \"color-gradient\", \"continuous\", \"discrete\"")
                @JsonProperty("continuousSubType")
                String continuousSubType,
        @JsonPropertyDescription(
                        "Details of the mapping currently applied to this property in the active"
                                + " visual style. Absent when no mapping is applied — the property"
                                + " uses its default value instead.")
                @JsonProperty("currentMapping")
                MappingInfo currentMapping) {}
