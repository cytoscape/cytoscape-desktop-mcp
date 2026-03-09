package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.idekerlab.cytoscapemcp.McpSchema;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.model.VisualProperty;
import org.cytoscape.view.presentation.RenderingEngineManager;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualPropertyDependency;
import org.cytoscape.view.vizmap.VisualStyle;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool that reads the active visual style's default property values from Cytoscape Desktop and
 * returns them as grouped JSON. Read-only; does not modify state.
 */
public class GetVisualStyleDefaultsTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetVisualStyleDefaultsTool.class);

    private static final String TOOL_NAME = "get_visual_style_defaults";

    private static final String TOOL_TITLE = "Get Cytoscape Desktop Style Defaults";

    private static final String TOOL_EXAMPLES =
            "\n\n## Examples\n\n"
                    + "Example 1 — Inspect the visual style defaults for the current network view:\n"
                    + "{}\n\n"
                    + "Example 2 — What are the default node and edge colors in the active style:\n"
                    + "{}\n\n"
                    + "Example 3 — Show me the current visual property defaults in Cytoscape:\n"
                    + "{}";

    private static final String TOOL_DESCRIPTION =
            "Get the current default values for all node and edge visual properties in the active"
                    + " visual style on the current network view in Cytoscape Desktop. Use when you"
                    + " need to inspect visual property defaults before modifying them. Returns"
                    + " property IDs, display names, value types, current values, allowed values for"
                    + " discrete types (shapes, arrows, line types), valid range bounds for numeric"
                    + " types, and visual property dependency (lock) relationships. Operates on the"
                    + " current desktop state — the result reflects whichever network view is"
                    + " currently selected. Read-only; does not modify state.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // -- Response records -----------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record VisualPropertyEntry(
            @JsonPropertyDescription("Visual property ID string (e.g. NODE_FILL_COLOR).")
                    @JsonProperty("id")
                    String id,
            @JsonPropertyDescription("Human-readable display name.") @JsonProperty("displayName")
                    String displayName,
            @JsonPropertyDescription(
                            "Value type: Paint, Double, Integer, NodeShape, ArrowShape, LineType,"
                                    + " Font, String, or Boolean.")
                    @JsonProperty("valueType")
                    String valueType,
            @JsonPropertyDescription("Current default value formatted as a string.")
                    @JsonProperty("currentValue")
                    String currentValue,
            @JsonPropertyDescription(
                            "Alphabetically sorted valid values. Present only for discrete types"
                                    + " (NodeShape, ArrowShape, LineType).")
                    @JsonProperty("allowedValues")
                    List<String> allowedValues,
            @JsonPropertyDescription(
                            "Minimum valid value. Present only for continuous numeric types (Double,"
                                    + " Integer).")
                    @JsonProperty("minValue")
                    String minValue,
            @JsonPropertyDescription(
                            "Maximum valid value. Present only for continuous numeric types (Double,"
                                    + " Integer).")
                    @JsonProperty("maxValue")
                    String maxValue) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record DependencyEntry(
            @JsonPropertyDescription("Dependency ID string (e.g. 'nodeSizeLocked').")
                    @JsonProperty("id")
                    String id,
            @JsonPropertyDescription(
                            "Human-readable dependency name (e.g. 'Lock node width and height').")
                    @JsonProperty("displayName")
                    String displayName,
            @JsonPropertyDescription("Whether this dependency is currently enabled.")
                    @JsonProperty("enabled")
                    boolean enabled,
            @JsonPropertyDescription("Visual property IDs that are linked by this dependency.")
                    @JsonProperty("properties")
                    List<String> properties) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record GetVisualStyleDefaultsResult(
            @JsonPropertyDescription("Name of the active visual style.") @JsonProperty("style_name")
                    String styleName,
            @JsonPropertyDescription("Node visual property defaults.")
                    @JsonProperty("node_properties")
                    List<VisualPropertyEntry> nodeProperties,
            @JsonPropertyDescription("Edge visual property defaults.")
                    @JsonProperty("edge_properties")
                    List<VisualPropertyEntry> edgeProperties,
            @JsonPropertyDescription(
                            "Visual property dependencies (lock relationships). When enabled,"
                                    + " changing one property in the group affects all others.")
                    @JsonProperty("dependencies")
                    List<DependencyEntry> dependencies) {}

    // -- Schemas --------------------------------------------------------------

    static final String INPUT_SCHEMA = McpSchema.toJson(McpSchema.InputSchema.builder().build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(GetVisualStyleDefaultsResult.class);

    // -- Dependencies ---------------------------------------------------------

    private final CyApplicationManager appManager;
    private final VisualMappingManager vmmManager;
    private final RenderingEngineManager renderingEngineManager;
    private final VisualPropertyService vpService = new VisualPropertyService();

    public GetVisualStyleDefaultsTool(
            CyApplicationManager appManager,
            VisualMappingManager vmmManager,
            RenderingEngineManager renderingEngineManager) {
        this.appManager = appManager;
        this.vmmManager = vmmManager;
        this.renderingEngineManager = renderingEngineManager;
    }

    /** Returns the MCP SyncToolSpecification to register with the McpSyncServer. */
    public McpServerFeatures.SyncToolSpecification toSpec() {
        try {
            Tool toolDef =
                    Tool.builder()
                            .name(TOOL_NAME)
                            .title(TOOL_TITLE)
                            .description(TOOL_DESCRIPTION + TOOL_EXAMPLES)
                            .inputSchema(MAPPER.readValue(INPUT_SCHEMA, JsonSchema.class))
                            .outputSchema(
                                    MAPPER.readValue(
                                            OUTPUT_SCHEMA,
                                            new TypeReference<Map<String, Object>>() {}))
                            .build();
            return McpServerFeatures.SyncToolSpecification.builder()
                    .tool(toolDef)
                    .callHandler(this::handle)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build tool spec for " + TOOL_NAME, e);
        }
    }

    // -- Handler --------------------------------------------------------------

    private CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        LOGGER.info("Tool call received: {}", TOOL_NAME);

        try {
            // Error check 1: no network loaded
            if (appManager.getCurrentNetwork() == null) {
                return error(
                        "No network is currently loaded in Cytoscape Desktop. A network must be"
                                + " loaded and selected as the current network before visual style"
                                + " defaults can be retrieved.");
            }

            // Error check 2: no view
            if (appManager.getCurrentNetworkView() == null) {
                return error(
                        "The current network has no view in Cytoscape Desktop. A network view must"
                                + " exist before visual style defaults can be retrieved.");
            }

            VisualStyle style = vmmManager.getCurrentVisualStyle();
            VisualLexicon lexicon = renderingEngineManager.getDefaultVisualLexicon();

            // Collect node properties
            List<VisualPropertyEntry> nodeProps =
                    collectProperties(lexicon, style, BasicVisualLexicon.NODE);

            // Collect edge properties
            List<VisualPropertyEntry> edgeProps =
                    collectProperties(lexicon, style, BasicVisualLexicon.EDGE);

            // Collect dependencies
            Set<VisualPropertyDependency<?>> deps = style.getAllVisualPropertyDependencies();
            List<DependencyEntry> dependencies = new ArrayList<>();
            for (VisualPropertyDependency<?> dep : deps) {
                List<String> propIds =
                        dep.getVisualProperties().stream()
                                .map(VisualProperty::getIdString)
                                .sorted()
                                .toList();
                dependencies.add(
                        new DependencyEntry(
                                dep.getIdString(),
                                dep.getDisplayName(),
                                dep.isDependencyEnabled(),
                                propIds));
            }
            dependencies.sort(Comparator.comparing(DependencyEntry::id));

            return CallToolResult.builder()
                    .structuredContent(
                            new GetVisualStyleDefaultsResult(
                                    style.getTitle(), nodeProps, edgeProps, dependencies))
                    .build();
        } catch (Exception e) {
            LOGGER.error("Error retrieving visual style defaults", e);
            return error("Failed to retrieve visual style defaults: " + e.getMessage());
        }
    }

    // -- Private helpers ------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<VisualPropertyEntry> collectProperties(
            VisualLexicon lexicon, VisualStyle style, VisualProperty<?> root) {
        Set<VisualProperty<?>> descendants =
                (Set<VisualProperty<?>>) (Set<?>) lexicon.getAllDescendants(root);
        List<VisualPropertyEntry> entries = new ArrayList<>();

        for (VisualProperty<?> vp : descendants) {
            if (!vpService.isSupported(vp)) continue;

            Object defaultValue = style.getDefaultValue(vp);
            entries.add(
                    new VisualPropertyEntry(
                            vp.getIdString(),
                            vp.getDisplayName(),
                            vpService.getTypeName(vp.getRange()),
                            vpService.formatValue(defaultValue),
                            vpService.getAllowedValues(vp),
                            vpService.getRangeMin(vp),
                            vpService.getRangeMax(vp)));
        }

        entries.sort(Comparator.comparing(VisualPropertyEntry::id));
        return entries;
    }

    // -- Result helpers -------------------------------------------------------

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}
