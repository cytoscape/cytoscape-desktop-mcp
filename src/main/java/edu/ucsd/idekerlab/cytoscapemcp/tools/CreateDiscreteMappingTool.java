package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.idekerlab.cytoscapemcp.McpSchema;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.model.VisualProperty;
import org.cytoscape.view.presentation.RenderingEngineManager;
import org.cytoscape.view.vizmap.VisualMappingFunctionFactory;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.view.vizmap.mappings.DiscreteMapping;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool that creates a discrete data-driven visual mapping in the active Cytoscape Desktop
 * visual style, assigning an explicit visual property value to each distinct data column value.
 * Replaces any existing mapping on the target property.
 */
public class CreateDiscreteMappingTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateDiscreteMappingTool.class);

    static final String TOOL_NAME = "create_discrete_mapping";
    static final String TOOL_TITLE = "Create Cytoscape Desktop Discrete Mapping";

    private static final String TOOL_DESCRIPTION =
            "Create a discrete data-driven visual mapping in the active Cytoscape Desktop visual"
                    + " style, assigning a specific visual property value to each distinct data"
                    + " column value. Best suited for columns with a small number of distinct values"
                    + " (tens, not hundreds) — use this tool when the user can meaningfully specify"
                    + " a visual property value for each distinct data value (e.g. three gene types"
                    + " → three colors, two interaction types → two line styles); for columns with"
                    + " many distinct values, consider auto-generated discrete mapping options"
                    + " instead. State-mutating; replaces any existing mapping on the target"
                    + " property and immediately rerenders the current view."
                    + "\n\nIMPORTANT — before invoking this tool, you MUST retrieve the complete"
                    + " set of distinct column values from the network using other available tools."
                    + " Never ask the user to enumerate column values — they exist in the network"
                    + " data and must be fetched by the agent. Only ask the user to specify the"
                    + " visual property value (color, shape, line style, etc.) to assign to each"
                    + " distinct value you have already retrieved.";

    private static final String TOOL_EXAMPLES =
            "\n\n## Examples\n\n"
                    + "Example 1 — Map gene type to node fill color:\n"
                    + "{\"property_id\": \"NODE_FILL_COLOR\", \"column_name\": \"GeneType\","
                    + " \"column_type\": \"String\","
                    + " \"entries\": {\"kinase\": \"#FF0000\", \"receptor\": \"#00AA00\","
                    + " \"TF\": \"#0000FF\"}}\n\n"
                    + "Example 2 — Map node class to shape:\n"
                    + "{\"property_id\": \"NODE_SHAPE\", \"column_name\": \"class\","
                    + " \"column_type\": \"String\","
                    + " \"entries\": {\"gene\": \"Ellipse\", \"protein\": \"Diamond\","
                    + " \"drug\": \"Round Rectangle\"}}\n\n"
                    + "Example 3 — Map interaction type to edge line style:\n"
                    + "{\"property_id\": \"EDGE_LINE_TYPE\", \"column_name\": \"interaction\","
                    + " \"column_type\": \"String\","
                    + " \"entries\": {\"activates\": \"Solid\", \"inhibits\": \"Dash\"}}\n\n"
                    + "Example 4 — \"Color each node based on data\": this is ambiguous — do not"
                    + " assume discrete. Before invoking, confirm with the user that they want a"
                    + " discrete mapping (an explicit color assigned to each distinct value) rather"
                    + " than a continuous gradient. Only if the user confirms discrete intent, ask"
                    + " which categorical column to use and which color to assign to each distinct"
                    + " value.";

    // -- Response record -------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record CreateDiscreteMappingResponse(
            @JsonPropertyDescription(
                            "Outcome. Always 'success' for non-error responses.\n\nExamples:"
                                    + " \"success\"")
                    @JsonProperty("status")
                    String status,
            @JsonPropertyDescription(
                            "Machine-readable ID of the visual property mapped.\n\nExamples:"
                                    + " \"NODE_FILL_COLOR\", \"NODE_SHAPE\"")
                    @JsonProperty("property_id")
                    String propertyId,
            @JsonPropertyDescription(
                            "Human-readable display name of the visual property.\n\nExamples:"
                                    + " \"Node Fill Color\", \"Node Shape\"")
                    @JsonProperty("displayName")
                    String displayName,
            @JsonPropertyDescription("Always 'DiscreteMapping' for this tool.")
                    @JsonProperty("mapping_type")
                    String mappingType,
            @JsonPropertyDescription(
                            "Name of the data column driving the mapping.\n\nExamples: \"GeneType\","
                                    + " \"class\"")
                    @JsonProperty("column")
                    String column,
            @JsonPropertyDescription("Number of entries added to the discrete mapping.")
                    @JsonProperty("entries_count")
                    int entriesCount) {}

    // -- Schemas ---------------------------------------------------------------

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String INPUT_SCHEMA =
            McpSchema.toJson(
                    McpSchema.InputSchema.builder()
                            .required("property_id", "column_name", "column_type", "entries")
                            .property(
                                    "property_id",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Required. Visual property ID (e.g. NODE_FILL_COLOR,"
                                                    + " NODE_SHAPE, EDGE_LINE_TYPE) from"
                                                    + " get_mappable_properties."))
                            .property(
                                    "column_name",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Required. Name of the data column driving the"
                                                    + " mapping, from get_compatible_columns."))
                            .property(
                                    "column_type",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Required. Java type of the data column. All five"
                                                    + " types are valid for discrete mapping.",
                                            List.of(
                                                    "Integer", "Long", "Double", "String",
                                                    "Boolean")))
                            .property(
                                    "entries",
                                    new McpSchema.InputProperty(
                                            "object",
                                            "Required. Map of column value (as string key) to"
                                                    + " visual property value allowed for the visual"
                                                    + " style property id specified by property_id."
                                                    + " Minimum 1 entry;"
                                                    + " maximum 1000 entries — the tool returns an"
                                                    + " error if either limit is violated. This tool"
                                                    + " is designed for columns with a small number"
                                                    + " of distinct values (typically tens); if the"
                                                    + " column has hundreds of distinct values,"
                                                    + " consider auto-generated discrete mapping"
                                                    + " options instead. Keys are the column's"
                                                    + " distinct values expressed as strings (e.g."
                                                    + " \"23\" for Integer 23, \"true\" for"
                                                    + " Boolean). Values: hex for colors"
                                                    + " (#RRGGBB), display names for shapes"
                                                    + " (Ellipse, Diamond), display names for line"
                                                    + " types (Solid, Dash), numbers for numeric"
                                                    + " properties."))
                            .build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(CreateDiscreteMappingResponse.class);

    // -- Dependencies ----------------------------------------------------------

    private final CyApplicationManager appManager;
    private final VisualMappingManager vmmManager;
    private final RenderingEngineManager renderingEngineManager;
    private final VisualMappingFunctionFactory discreteMappingFactory;
    private final VisualPropertyService vpService;

    public CreateDiscreteMappingTool(
            CyApplicationManager appManager,
            VisualMappingManager vmmManager,
            RenderingEngineManager renderingEngineManager,
            VisualMappingFunctionFactory discreteMappingFactory,
            VisualPropertyService vpService) {
        this.appManager = appManager;
        this.vmmManager = vmmManager;
        this.renderingEngineManager = renderingEngineManager;
        this.discreteMappingFactory = discreteMappingFactory;
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

    // -- Handler ---------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        LOGGER.info("Tool call received: {}", TOOL_NAME);

        try {
            // GUARD: network and view
            if (appManager.getCurrentNetwork() == null) {
                return error(
                        "No network loaded in Cytoscape Desktop. Load a network first before"
                                + " creating a visual mapping.");
            }
            if (appManager.getCurrentNetworkView() == null) {
                return error(
                        "No network view is currently active. Create a view first before"
                                + " creating a visual mapping.");
            }

            VisualStyle style = vmmManager.getCurrentVisualStyle();
            VisualLexicon lexicon = renderingEngineManager.getDefaultVisualLexicon();

            Map<String, Object> args = request.arguments();
            if (args == null) args = Collections.emptyMap();

            String propertyId = String.valueOf(args.get("property_id"));
            String columnName = String.valueOf(args.get("column_name"));
            String columnTypeStr = String.valueOf(args.get("column_type"));

            // VALIDATION: property
            VisualProperty<?> vp = vpService.findPropertyById(lexicon, propertyId);
            if (vp == null) {
                return error(
                        "Unknown visual property ID '"
                                + propertyId
                                + "'. Call get_mappable_properties to retrieve valid property IDs.");
            }
            if (!vpService.isSupported(vp)) {
                return error("Property '" + propertyId + "' is not supported for MCP mapping.");
            }

            // VALIDATION: column type (all 5 types valid for discrete)
            Class<?> columnTypeClass;
            try {
                columnTypeClass = vpService.resolveColumnType(columnTypeStr);
            } catch (IllegalArgumentException e) {
                return error(e.getMessage());
            }

            // VALIDATION: entries object
            Object rawEntries = args.get("entries");
            if (!(rawEntries instanceof Map)) {
                return error(
                        "Missing or invalid 'entries' parameter. Expected an object mapping column"
                                + " values to property values.");
            }
            Map<String, Object> entriesMap = (Map<String, Object>) rawEntries;
            if (entriesMap.isEmpty()) {
                return error("At least 1 entry is required in the 'entries' map.");
            }
            if (entriesMap.size() > 1000) {
                return error(
                        "Too many entries ("
                                + entriesMap.size()
                                + "). Maximum 1000. Use create_discrete_mapping_generated for"
                                + " large value sets.");
            }

            // Parse all entries (fail fast before any mutation)
            Map<Object, Object> parsedEntries = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : entriesMap.entrySet()) {
                String strKey = entry.getKey();
                Object rawVal = entry.getValue();

                Object typedKey;
                try {
                    typedKey = vpService.parseColumnKey(strKey, columnTypeClass);
                } catch (IllegalArgumentException e) {
                    return error(e.getMessage());
                }

                Object parsedVal;
                try {
                    parsedVal = vpService.parseValue(vp, rawVal);
                } catch (Exception e) {
                    return error("Entry '" + strKey + "': " + e.getMessage());
                }

                parsedEntries.put(typedKey, parsedVal);
            }

            // MUTATION: apply on Swing EDT
            final VisualProperty<?> finalVp = vp;
            final VisualStyle finalStyle = style;
            final Map<Object, Object> finalEntries = parsedEntries;
            final Class<?> finalColType = columnTypeClass;
            final String finalColumnName = columnName;

            try {
                Runnable task =
                        () -> {
                            DiscreteMapping dm =
                                    (DiscreteMapping)
                                            discreteMappingFactory.createVisualMappingFunction(
                                                    finalColumnName, finalColType, finalVp);
                            dm.putAll(finalEntries);
                            finalStyle.removeVisualMappingFunction(finalVp);
                            finalStyle.addVisualMappingFunction(dm);
                            CyNetworkView view = appManager.getCurrentNetworkView();
                            if (view != null) {
                                finalStyle.apply(view);
                                view.updateView();
                            }
                        };

                if (EventQueue.isDispatchThread()) {
                    task.run();
                } else {
                    SwingUtilities.invokeAndWait(task);
                }
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                LOGGER.error("Error applying discrete mapping on EDT", cause);
                return error(
                        "Failed to apply mapping: "
                                + (cause.getMessage() != null
                                        ? cause.getMessage()
                                        : cause.getClass().getSimpleName()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return error("Interrupted while applying discrete mapping.");
            }

            return CallToolResult.builder()
                    .structuredContent(
                            new CreateDiscreteMappingResponse(
                                    "success",
                                    vp.getIdString(),
                                    vp.getDisplayName(),
                                    "DiscreteMapping",
                                    columnName,
                                    parsedEntries.size()))
                    .build();

        } catch (Exception e) {
            LOGGER.error("Error creating discrete mapping", e);
            return error("Failed to create discrete mapping: " + e.getMessage());
        }
    }

    // -- Helpers ---------------------------------------------------------------

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}
