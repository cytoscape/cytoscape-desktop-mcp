package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
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
import org.cytoscape.view.vizmap.mappings.BoundaryRangeValues;
import org.cytoscape.view.vizmap.mappings.ContinuousMapping;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool that creates a continuous data-driven visual mapping in the active Cytoscape Desktop
 * visual style, linking a numeric data column to a visual property through user-defined
 * breakpoints. Replaces any existing mapping on the target property.
 */
public class CreateContinuousMappingTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateContinuousMappingTool.class);

    static final String TOOL_NAME = "create_continuous_mapping";
    static final String TOOL_TITLE = "Create Cytoscape Desktop Continuous Mapping";

    private static final String TOOL_DESCRIPTION =
            "Create a continuous data-driven visual mapping in the active Cytoscape Desktop visual"
                    + " style, linking a numeric data column to a visual property through"
                    + " user-defined breakpoints. Use when you want node or edge appearance to vary"
                    + " continuously with data — such as mapping expression values to a color"
                    + " gradient, degree centrality to node size, or interaction score to edge"
                    + " width. State-mutating; replaces any existing mapping on the target property"
                    + " and immediately rerenders the current view.";

    private static final String TOOL_EXAMPLES =
            "\n\n## Examples\n\n"
                    + "Example 1 — Map node degree to size (small degree = 10px, large degree ="
                    + " 60px):\n"
                    + "{\"property_id\": \"NODE_SIZE\", \"column_name\": \"Degree\","
                    + " \"column_type\": \"Integer\","
                    + " \"points\": [{\"value\": 1, \"lesser\": 10, \"equal\": 10,"
                    + " \"greater\": 10},"
                    + " {\"value\": 45, \"lesser\": 60, \"equal\": 60, \"greater\": 60}]}\n\n"
                    + "Example 2 — Change color gradient of nodes from blue to red based on expression:\n"
                    + "{\"property_id\": \"NODE_FILL_COLOR\", \"column_name\": \"expression\","
                    + " \"column_type\": \"Double\","
                    + " \"points\": [{\"value\": -2.0, \"lesser\": \"#0000FF\","
                    + " \"equal\": \"#0000FF\", \"greater\": \"#FFFFFF\"},"
                    + " {\"value\": 0.0, \"lesser\": \"#FFFFFF\", \"equal\": \"#FFFFFF\","
                    + " \"greater\": \"#FFFFFF\"},"
                    + " {\"value\": 2.0, \"lesser\": \"#FFFFFF\", \"equal\": \"#FF0000\","
                    + " \"greater\": \"#FF0000\"}]}\n\n"
                    + "Example 3 — Map betweenness centrality to edge width with three"
                    + " breakpoints:\n"
                    + "{\"property_id\": \"EDGE_WIDTH\", \"column_name\":"
                    + " \"BetweennessCentrality\", \"column_type\": \"Double\","
                    + " \"points\": [{\"value\": 0.0, \"lesser\": 1.0, \"equal\": 1.0,"
                    + " \"greater\": 1.0},"
                    + " {\"value\": 0.5, \"lesser\": 3.0, \"equal\": 3.0, \"greater\": 3.0},"
                    + " {\"value\": 1.0, \"lesser\": 8.0, \"equal\": 8.0,"
                    + " \"greater\": 8.0}]}\n\n"
                    + "Example 4 — \"Change node color based on centrality\": before invoking,"
                    + " confirm with the user that they want continuous gradient interpolation —"
                    + " color varying smoothly with the numeric data — rather than discrete color"
                    + " assignment per value. If confirmed continuous, ask which centrality column"
                    + " to use and for at least two breakpoints (a low value with its color and a"
                    + " high value with its color).";

    // -- Response record -------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record CreateContinuousMappingResponse(
            @JsonPropertyDescription(
                            "Outcome. Always 'success' for non-error responses.\n\nExamples:"
                                    + " \"success\"")
                    @JsonProperty("status")
                    String status,
            @JsonPropertyDescription(
                            "Machine-readable ID of the visual property mapped.\n\nExamples:"
                                    + " \"NODE_SIZE\", \"NODE_FILL_COLOR\"")
                    @JsonProperty("property_id")
                    String propertyId,
            @JsonPropertyDescription(
                            "Human-readable display name of the visual property.\n\nExamples:"
                                    + " \"Node Size\", \"Node Fill Color\"")
                    @JsonProperty("displayName")
                    String displayName,
            @JsonPropertyDescription("Always 'ContinuousMapping' for this tool.")
                    @JsonProperty("mapping_type")
                    String mappingType,
            @JsonPropertyDescription(
                            "Name of the data column driving the mapping.\n\nExamples: \"Degree\","
                                    + " \"expression\"")
                    @JsonProperty("column")
                    String column,
            @JsonPropertyDescription("Number of breakpoints added to the mapping.")
                    @JsonProperty("points_count")
                    int pointsCount) {}

    // -- Schemas ---------------------------------------------------------------

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String INPUT_SCHEMA =
            McpSchema.toJson(
                    McpSchema.InputSchema.builder()
                            .required("property_id", "column_name", "column_type", "points")
                            .property(
                                    "property_id",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Required. Visual property ID (e.g. NODE_FILL_COLOR,"
                                                    + " NODE_SIZE, EDGE_WIDTH). Retrieve the"
                                                    + " available style properties in the active"
                                                    + " style using other tooling available."))
                            .property(
                                    "column_name",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Required. Name of the numeric data column driving the"
                                                    + " mapping. Query numeric network columns"
                                                    + " compatible with continuous mapping for the"
                                                    + " chosen property using other tooling available."))
                            .property(
                                    "column_type",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Required. Java type of the data column.",
                                            List.of("Integer", "Long", "Double")))
                            .property(
                                    "points",
                                    new McpSchema.InputProperty(
                                            "array",
                                            "Required. Minimum 2 breakpoints. Defines the"
                                                    + " piecewise continuous mapping function: each"
                                                    + " breakpoint anchors the visual property at a"
                                                    + " specific data value, and Cytoscape"
                                                    + " interpolates between adjacent breakpoints —"
                                                    + " so at least one lower and one upper anchor is"
                                                    + " needed to form a valid range. Breakpoints must"
                                                    + " be in ascending order by value; the tool sorts"
                                                    + " them automatically but rejects duplicates. Each"
                                                    + " entry has: value (number — the data value at"
                                                    + " this breakpoint anchor); lesser (property value"
                                                    + " applied for data values strictly below this"
                                                    + " breakpoint); equal (property value applied for"
                                                    + " data values exactly at this breakpoint); greater"
                                                    + " (property value applied for data values strictly"
                                                    + " above this breakpoint). For color-gradient"
                                                    + " properties (Paint), use hex strings (#RRGGBB)."
                                                    + " For discrete-typed properties (NodeShape,"
                                                    + " LineType), use display names. For numeric"
                                                    + " properties (Double, Integer), use numbers.",
                                            new McpSchema.InputProperty(
                                                    "object",
                                                    "A breakpoint entry with fields: value (number),"
                                                            + " lesser (property value below this"
                                                            + " point), equal (property value at this"
                                                            + " point), greater (property value above"
                                                            + " this point)."),
                                            null))
                            .build());

    static final String OUTPUT_SCHEMA =
            McpSchema.toSchemaJson(CreateContinuousMappingResponse.class);

    // -- Dependencies ----------------------------------------------------------

    private final CyApplicationManager appManager;
    private final VisualMappingManager vmmManager;
    private final RenderingEngineManager renderingEngineManager;
    private final VisualMappingFunctionFactory continuousMappingFactory;
    private final VisualPropertyService vpService;

    public CreateContinuousMappingTool(
            CyApplicationManager appManager,
            VisualMappingManager vmmManager,
            RenderingEngineManager renderingEngineManager,
            VisualMappingFunctionFactory continuousMappingFactory,
            VisualPropertyService vpService) {
        this.appManager = appManager;
        this.vmmManager = vmmManager;
        this.renderingEngineManager = renderingEngineManager;
        this.continuousMappingFactory = continuousMappingFactory;
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
            if (vpService.getContinuousSubType(vp) == null) {
                return error(
                        "Property '"
                                + propertyId
                                + "' does not support continuous mapping. Only numeric (Double,"
                                + " Integer) and color (Paint) properties support continuous"
                                + " mapping.");
            }

            // VALIDATION: column type
            Class<?> columnTypeClass;
            try {
                columnTypeClass = vpService.resolveColumnType(columnTypeStr);
            } catch (IllegalArgumentException e) {
                return error(e.getMessage());
            }

            // VALIDATION: points array
            Object rawPoints = args.get("points");
            if (!(rawPoints instanceof List<?>)) {
                return error(
                        "Missing or invalid 'points' parameter. Expected an array of breakpoints.");
            }
            List<Map<String, Object>> pointsList = (List<Map<String, Object>>) rawPoints;
            if (pointsList.size() < 2) {
                return error(
                        "At least 2 breakpoints are required to define a continuous mapping."
                                + " Provide at least one lower anchor and one upper anchor.");
            }

            List<ParsedPoint> parsedPoints = new ArrayList<>();
            for (int i = 0; i < pointsList.size(); i++) {
                Map<String, Object> entry = pointsList.get(i);
                Object valueRaw = entry.get("value");
                Object lesserRaw = entry.get("lesser");
                Object equalRaw = entry.get("equal");
                Object greaterRaw = entry.get("greater");

                if (!(valueRaw instanceof Number)) {
                    return error("Breakpoint " + i + ": 'value' must be a number.");
                }
                double value = ((Number) valueRaw).doubleValue();

                Object lesser, equal, greater;
                try {
                    lesser = vpService.parseValue(vp, lesserRaw);
                    equal = vpService.parseValue(vp, equalRaw);
                    greater = vpService.parseValue(vp, greaterRaw);
                } catch (Exception e) {
                    return error("Breakpoint " + i + ": " + e.getMessage());
                }

                parsedPoints.add(new ParsedPoint(value, lesser, equal, greater));
            }

            // Sort ascending by data value
            parsedPoints.sort((a, b) -> Double.compare(a.value(), b.value()));

            // Reject duplicate values
            for (int i = 0; i < parsedPoints.size() - 1; i++) {
                if (parsedPoints.get(i).value() == parsedPoints.get(i + 1).value()) {
                    return error(
                            "Duplicate breakpoint values are not allowed. Found duplicate value: "
                                    + parsedPoints.get(i).value());
                }
            }

            // MUTATION: apply on Swing EDT
            final VisualProperty<?> finalVp = vp;
            final VisualStyle finalStyle = style;
            final List<ParsedPoint> finalPoints = parsedPoints;
            final Class<?> finalColType = columnTypeClass;
            final String finalColumnName = columnName;

            try {
                Runnable task =
                        () -> {
                            ContinuousMapping cm =
                                    (ContinuousMapping)
                                            continuousMappingFactory.createVisualMappingFunction(
                                                    finalColumnName, finalColType, finalVp);
                            for (ParsedPoint p : finalPoints) {
                                BoundaryRangeValues brv =
                                        new BoundaryRangeValues(p.lesser(), p.equal(), p.greater());
                                Object convertedValue =
                                        vpService.convertColumnValue(p.value(), finalColType);
                                cm.addPoint(convertedValue, brv);
                            }
                            finalStyle.removeVisualMappingFunction(finalVp);
                            finalStyle.addVisualMappingFunction(cm);
                            vpService.enableNodeSizeLockIfNeeded(finalVp, finalStyle);
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
                LOGGER.error("Error applying continuous mapping on EDT", cause);
                return error(
                        "Failed to apply mapping: "
                                + (cause.getMessage() != null
                                        ? cause.getMessage()
                                        : cause.getClass().getSimpleName()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return error("Interrupted while applying continuous mapping.");
            }

            return CallToolResult.builder()
                    .structuredContent(
                            new CreateContinuousMappingResponse(
                                    "success",
                                    vp.getIdString(),
                                    vp.getDisplayName(),
                                    "ContinuousMapping",
                                    columnName,
                                    parsedPoints.size()))
                    .build();

        } catch (Exception e) {
            LOGGER.error("Error creating continuous mapping", e);
            return error("Failed to create continuous mapping: " + e.getMessage());
        }
    }

    // -- Helpers ---------------------------------------------------------------

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }

    /** Internal DTO used during validation → mutation handoff. */
    private record ParsedPoint(double value, Object lesser, Object equal, Object greater) {}
}
