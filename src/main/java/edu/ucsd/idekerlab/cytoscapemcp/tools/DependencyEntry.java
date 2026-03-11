package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Represents a visual property dependency (lock relationship) within a {@link VisualStyleDefaults}
 * response. When a dependency is enabled, changing one linked property automatically adjusts all
 * others in the group.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DependencyEntry(
        @JsonPropertyDescription(
                        "Machine-readable dependency identifier."
                                + "\n\nExamples: \"nodeSizeLocked\", \"arrowColorMatchesEdge\"")
                @JsonProperty("id")
                String id,
        @JsonPropertyDescription(
                        "Human-readable dependency name."
                                + "\n\nExamples: \"Lock node width and height\","
                                + " \"Edge color to arrows\"")
                @JsonProperty("displayName")
                String displayName,
        @JsonPropertyDescription(
                        "Whether this dependency is currently enabled. When true, the linked"
                                + " properties move together.")
                @JsonProperty("enabled")
                boolean enabled,
        @JsonPropertyDescription(
                        "Sorted list of visual property IDs linked by this dependency (e.g."
                                + " [NODE_HEIGHT, NODE_WIDTH]).")
                @JsonProperty("properties")
                List<String> properties) {}
