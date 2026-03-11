package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Represents the complete default-value state of the active Cytoscape visual style. Returned
 * directly by {@code get_visual_style_defaults}. Font-typed visual properties reference the
 * top-level {@code font_families} and {@code font_styles} lists rather than carrying their own
 * {@code allowedValues}, avoiding duplication and keeping the response compact.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VisualStyleDefaults(
        @JsonPropertyDescription(
                        "Name of the active visual style applied to the current network."
                                + "\n\nExamples: \"default\", \"Marquee\", \"galFiltered Style\"")
                @JsonProperty("style_name")
                String styleName,
        @JsonPropertyDescription(
                        "Alphabetically sorted list of available font family names installed on the"
                                + " system. Font-typed visual properties in the node_properties and"
                                + " edge_properties arrays reference this shared list rather than"
                                + " carrying their own allowedValues. To compose a font value for"
                                + " updating a style default, select a family name from this list, a"
                                + " style from the font_styles list, and an integer point size, then"
                                + " join them as Family-Style-Size."
                                + "\n\nExamples: \"Arial\", \"Courier New\", \"SansSerif\"")
                @JsonProperty("font_families")
                List<String> fontFamilies,
        @JsonPropertyDescription(
                        "Valid font style tokens for composing Font property values. When updating"
                                + " a font default, select one of these tokens as the Style component"
                                + " in the Family-Style-Size format."
                                + "\n\nExamples: \"Plain\", \"Bold\", \"Italic\", \"BoldItalic\"")
                @JsonProperty("font_styles")
                List<String> fontStyles,
        @JsonPropertyDescription(
                        "Node visual property defaults — one entry per property whose value type"
                                + " has a plain-text representation (colors, numbers, shapes, line"
                                + " types, fonts, booleans). Font-typed entries reference the"
                                + " top-level font_families and font_styles lists for their allowed"
                                + " values.")
                @JsonProperty("node_properties")
                List<VisualPropertyEntry> nodeProperties,
        @JsonPropertyDescription(
                        "Edge visual property defaults — one entry per property whose value type"
                                + " has a plain-text representation (colors, numbers, shapes, line"
                                + " types, fonts, booleans). Font-typed entries reference the"
                                + " top-level font_families and font_styles lists for their allowed"
                                + " values.")
                @JsonProperty("edge_properties")
                List<VisualPropertyEntry> edgeProperties,
        @JsonPropertyDescription(
                        "Visual property dependency (lock) relationships for this style. When a"
                                + " dependency is enabled, changing one property in the group"
                                + " automatically adjusts all others.")
                @JsonProperty("dependencies")
                List<DependencyEntry> dependencies) {}
