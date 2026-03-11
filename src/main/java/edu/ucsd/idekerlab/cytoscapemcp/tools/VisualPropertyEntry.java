package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Represents one visual property in a {@link VisualStyleDefaults} response. Shared by {@code
 * get_visual_style_defaults} (read) and {@code set_visual_default} (write).
 *
 * <p>When used as input to the setter, only {@code id} and {@code currentValue} are read; all other
 * fields are ignored.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VisualPropertyEntry(
        @JsonPropertyDescription(
                        "Machine-readable visual property identifier. Use this ID when setting a"
                                + " default value."
                                + "\n\nExamples: \"NODE_FILL_COLOR\", \"EDGE_WIDTH\","
                                + " \"NODE_LABEL_FONT_FACE\"")
                @JsonProperty("id")
                String id,
        @JsonPropertyDescription(
                        "Human-readable display name for this visual property."
                                + "\n\nExamples: \"Node Fill Color\", \"Edge Width\","
                                + " \"Node Label Font Face\"")
                @JsonProperty("displayName")
                String displayName,
        @JsonPropertyDescription(
                        "Value type: Paint, Double, Integer, NodeShape, ArrowShape, LineType,"
                                + " LabelBackgroundShape, EdgeStacking, Font, String, or Boolean."
                                + "\n\nExamples: \"Paint\", \"Double\", \"Font\"")
                @JsonProperty("valueType")
                String valueType,
        @JsonPropertyDescription(
                        "Current default value formatted as a string. For colors this is hex"
                                + " (#RRGGBB); for shapes, line types, and arrows this is the"
                                + " display name (e.g. Ellipse, Solid, Arrow); for Font properties"
                                + " this is Family-Style-Size composed from a family in the"
                                + " top-level font_families list, a style from font_styles, and an"
                                + " integer point size. When used as input to the setter, provide"
                                + " the new desired value in this field using the same format."
                                + "\n\nExamples: \"#89D0F5\", \"35.0\", \"Dialog-Plain-12\"")
                @JsonProperty("currentValue")
                String currentValue,
        @JsonPropertyDescription(
                        "Alphabetically sorted list of valid values for discrete types"
                                + " (NodeShape, ArrowShape, LineType, LabelBackgroundShape,"
                                + " EdgeStacking). Use one of these exact strings when setting a"
                                + " default for a discrete property. Absent for continuous numeric"
                                + " types and for Font properties — font-typed properties reference"
                                + " the top-level font_families and font_styles lists instead;"
                                + " compose the full font value as Family-Style-Size"
                                + " (e.g. Arial-Bold-14).")
                @JsonProperty("allowedValues")
                List<String> allowedValues,
        @JsonPropertyDescription(
                        "Minimum valid value. Present only for continuous numeric types (Double,"
                                + " Integer). Values below this minimum will be rejected."
                                + "\n\nExamples: \"0.0\", \"0\"")
                @JsonProperty("minValue")
                String minValue,
        @JsonPropertyDescription(
                        "Maximum valid value. Present only for continuous numeric types (Double,"
                                + " Integer). Values above this maximum will be rejected."
                                + "\n\nExamples: \"255.0\", \"1.7976931348623157E308\"")
                @JsonProperty("maxValue")
                String maxValue) {}
