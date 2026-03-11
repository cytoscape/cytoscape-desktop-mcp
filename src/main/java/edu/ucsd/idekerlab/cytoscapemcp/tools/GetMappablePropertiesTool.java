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
import org.cytoscape.view.vizmap.VisualStyle;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool that lists all visual properties supporting data-driven mappings in the active Cytoscape
 * Desktop visual style. For each property, reports its mapping compatibility type and any currently
 * applied mapping. Read-only; does not modify state.
 */
public class GetMappablePropertiesTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetMappablePropertiesTool.class);

    private static final String TOOL_NAME = "get_mappable_properties";

    private static final String TOOL_TITLE = "List Cytoscape Desktop Mappable Properties";

    private static final String TOOL_EXAMPLES =
            "\n\n## Examples\n\n"
                    + "Example 1 — List all mappable properties to see what styling options are"
                    + " available for data-driven visualization:\n"
                    + "{}\n\n"
                    + "Example 2 — What properties currently have data-driven mappings applied to"
                    + " the active style:\n"
                    + "{}\n\n"
                    + "Example 3 — Which node properties support continuous gradient mapping for"
                    + " numeric data columns:\n"
                    + "{}\n\n"
                    + "Example 4 — Check if any existing mappings will be overwritten before"
                    + " creating a new one:\n"
                    + "{}";

    private static final String TOOL_DESCRIPTION =
            "List all visual properties that support data-driven mappings in the active Cytoscape"
                    + " Desktop visual style, grouped by node and edge categories with each"
                    + " property's mapping compatibility type and any currently applied mapping. Use"
                    + " when you need to discover which visual properties can be mapped to data"
                    + " columns, determine what kind of mapping (continuous, discrete, or"
                    + " passthrough) each property supports, or inspect existing mappings before"
                    + " creating or replacing them. Read-only; does not modify state. Returns an"
                    + " error if no network is currently loaded or if the active network has no"
                    + " view, each with a descriptive message indicating the specific cause.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // -- Schemas --------------------------------------------------------------

    static final String INPUT_SCHEMA = McpSchema.toJson(McpSchema.InputSchema.builder().build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(MappablePropertiesResponse.class);

    // -- Dependencies ---------------------------------------------------------

    private final CyApplicationManager appManager;
    private final VisualMappingManager vmmManager;
    private final RenderingEngineManager renderingEngineManager;
    private final VisualPropertyService vpService;

    public GetMappablePropertiesTool(
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
                                + " loaded and selected as the current network before mappable"
                                + " properties can be listed.");
            }

            // Error check 2: no view
            if (appManager.getCurrentNetworkView() == null) {
                return error(
                        "The current network has no view in Cytoscape Desktop. A network view must"
                                + " exist before mappable properties can be listed.");
            }

            VisualStyle style = vmmManager.getCurrentVisualStyle();
            VisualLexicon lexicon = renderingEngineManager.getDefaultVisualLexicon();

            // Collect node properties
            List<MappablePropertyEntry> nodeProps =
                    collectProperties(lexicon, style, BasicVisualLexicon.NODE);

            // Collect edge properties
            List<MappablePropertyEntry> edgeProps =
                    collectProperties(lexicon, style, BasicVisualLexicon.EDGE);

            return CallToolResult.builder()
                    .structuredContent(
                            new MappablePropertiesResponse(style.getTitle(), nodeProps, edgeProps))
                    .build();
        } catch (Exception e) {
            LOGGER.error("Error retrieving mappable properties", e);
            return error("Failed to retrieve mappable properties: " + e.getMessage());
        }
    }

    // -- Private helpers ------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<MappablePropertyEntry> collectProperties(
            VisualLexicon lexicon, VisualStyle style, VisualProperty<?> root) {
        Set<VisualProperty<?>> descendants =
                (Set<VisualProperty<?>>) (Set<?>) lexicon.getAllDescendants(root);
        List<MappablePropertyEntry> entries = new ArrayList<>();

        for (VisualProperty<?> vp : descendants) {
            if (!vpService.isSupported(vp)) continue;

            MappingInfo mappingInfo = vpService.getMappingInfo(style.getVisualMappingFunction(vp));
            entries.add(
                    new MappablePropertyEntry(
                            vp.getIdString(),
                            vp.getDisplayName(),
                            vpService.getTypeName(vp.getRange()),
                            vpService.getContinuousSubType(vp),
                            mappingInfo));
        }

        entries.sort(Comparator.comparing(MappablePropertyEntry::id));
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
