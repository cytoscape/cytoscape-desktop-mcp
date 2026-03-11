package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                    + "Example 1 — Inspect the visual style defaults before applying style changes:\n"
                    + "{}\n\n"
                    + "Example 2 — What are the default node and edge colors in the active style:\n"
                    + "{}\n\n"
                    + "Example 3 — Discover available node shapes, label fonts, and edge line types"
                    + " with their current values:\n"
                    + "{}\n\n"
                    + "Example 4 — Retrieve the full style state to plan a network visualization"
                    + " update:\n"
                    + "{}";

    private static final String TOOL_DESCRIPTION =
            "Retrieves all default visual property values for the active Cytoscape Desktop visual"
                    + " style, including node properties, edge properties, available font families"
                    + " and styles, and visual property dependency locks. Use when you need to"
                    + " inspect current styling for the active network, discover all valid property"
                    + " identifiers and their allowed value formats, or audit the full style state"
                    + " before applying any visual changes. Read-only; does not modify state."
                    + " Returns an error if no network is currently loaded or if the active network"
                    + " has no view, each with a descriptive message indicating the specific cause.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Response model classes are package-level: VisualPropertyEntry, DependencyEntry,
    // VisualStyleDefaults — shared with SetVisualDefaultTool.

    // -- Schemas --------------------------------------------------------------

    static final String INPUT_SCHEMA = McpSchema.toJson(McpSchema.InputSchema.builder().build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(VisualStyleDefaults.class);

    // -- Dependencies ---------------------------------------------------------

    private final CyApplicationManager appManager;
    private final VisualMappingManager vmmManager;
    private final RenderingEngineManager renderingEngineManager;
    private final VisualPropertyService vpService;

    public GetVisualStyleDefaultsTool(
            CyApplicationManager appManager,
            VisualMappingManager vmmManager,
            RenderingEngineManager renderingEngineManager,
            VisualPropertyService vpService) {
        this.appManager = appManager;
        this.vmmManager = vmmManager;
        this.renderingEngineManager = renderingEngineManager;
        this.vpService = vpService;
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

            // Collect font families (shared top-level field, avoids duplicating in each VP)
            List<String> fontFamilies = vpService.getFontFamilies(lexicon);

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
                            new VisualStyleDefaults(
                                    style.getTitle(),
                                    fontFamilies,
                                    VisualPropertyService.FONT_STYLES,
                                    nodeProps,
                                    edgeProps,
                                    dependencies))
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
