package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Paint;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.DiscreteRange;
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
 * MCP tool that creates a discrete visual mapping in the active Cytoscape Desktop visual style with
 * auto-generated property values for every distinct column value. Suitable for columns with large
 * cardinality where specifying each entry manually is impractical.
 */
public class CreateDiscreteMappingGeneratedTool {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(CreateDiscreteMappingGeneratedTool.class);

    static final String TOOL_NAME = "create_discrete_mapping_generated";
    static final String TOOL_TITLE = "Create Cytoscape Desktop Auto Mapping";

    private static final String TOOL_DESCRIPTION =
            "Create a discrete data-driven visual mapping in the active Cytoscape Desktop visual"
                    + " style where property values are auto-generated for every distinct column"
                    + " value — no manual entry needed. Prefer this tool over the manual discrete"
                    + " mapping tool when a column has more than a dozen or so distinct values,"
                    + " or whenever you want the user to simply choose a generator style rather"
                    + " than enumerate discrete mapping entries themselves. The caller must select from given generator"
                    + " algorithms that determine how visual property values are produced across the"
                    + " full set of discrete values; refer to the generator input parameter for the"
                    + " available algorithm choices and their compatibility requirements. State-mutating;"
                    + " creates or replaces a discrete mapping on the active visual style and"
                    + " immediately rerenders the current view.";

    private static final String TOOL_EXAMPLES =
            "\n\n## Examples\n\n"
                    + "Example 1 — \"Automatically color the nodes by discrete values in GeneType column\":\n"
                    + "{\"property_id\": \"NODE_FILL_COLOR\", \"column_name\": \"GeneType\","
                    + " \"column_type\": \"String\", \"generator\": \"rainbow\"}\n\n"
                    + "Example 2 — \"Auto-generate a shape mapping for the discrete community column values\":\n"
                    + "{\"property_id\": \"NODE_SHAPE\", \"column_name\": \"community\","
                    + " \"column_type\": \"Integer\", \"generator\": \"shape_cycle\"}\n\n"
                    + "Example 3 — \"Generate a blue color gradient for the discrete values in tissue column\":\n"
                    + "{\"property_id\": \"NODE_FILL_COLOR\", \"column_name\": \"tissue\","
                    + " \"column_type\": \"String\", \"generator\": \"brewer_sequential\","
                    + " \"generator_params\": {\"hue\": \"blue\"}}\n\n"
                    + "Example 4 — \"Create a discrete size mapping based on the Degree column"
                    + " automatically\":\n"
                    + "{\"property_id\": \"NODE_SIZE\", \"column_name\": \"Degree\","
                    + " \"column_type\": \"Integer\", \"generator\": \"numeric_range\","
                    + " \"generator_params\": {\"min\": 10, \"max\": 60}}";

    // -- Response record -------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record CreateDiscreteMappingGeneratedResponse(
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
                                    + " \"community\"")
                    @JsonProperty("column")
                    String column,
            @JsonPropertyDescription(
                            "Generator algorithm that was applied.\n\nExamples: \"rainbow\","
                                    + " \"shape_cycle\"")
                    @JsonProperty("generator")
                    String generator,
            @JsonPropertyDescription(
                            "Total number of distinct column values mapped (one mapping entry per"
                                    + " value).")
                    @JsonProperty("entries_count")
                    int entriesCount,
            @JsonPropertyDescription(
                            "Sample of the first up to five generated mapping entries as"
                                    + " {columnValue → formattedPropertyValue} pairs. Colors are"
                                    + " hex strings; shapes and line types are display names.")
                    @JsonProperty("sample_entries")
                    Map<String, String> sampleEntries) {}

    // -- Schemas ---------------------------------------------------------------

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String INPUT_SCHEMA =
            McpSchema.toJson(
                    McpSchema.InputSchema.builder()
                            .required("property_id", "column_name", "column_type", "generator")
                            .property(
                                    "property_id",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Required. Visual property ID (e.g. NODE_FILL_COLOR,"
                                                    + " NODE_SHAPE, EDGE_WIDTH) from"
                                                    + " get_mappable_properties."))
                            .property(
                                    "column_name",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Required. Name of the data column to drive the"
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
                                    "generator",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Required. Auto-generation algorithm to apply across all distinct column values."
                                                    + " rainbow and random produce colors from Cytoscape's palette providers"
                                                    + " (Paint properties only). brewer_sequential produces a light-to-dark"
                                                    + " single-hue gradient (Paint only); supply an optional hue hint via"
                                                    + " generator_params. shape_cycle steps through the visual property's own"
                                                    + " discrete value set (e.g. node shapes), wrapping as needed."
                                                    + " numeric_range spreads values evenly between a min and max (numeric"
                                                    + " properties only); min and max are required via generator_params.",
                                            List.of(
                                                    "rainbow",
                                                    "random",
                                                    "brewer_sequential",
                                                    "shape_cycle",
                                                    "numeric_range")))
                            .property(
                                    "generator_params",
                                    new McpSchema.InputProperty(
                                            "object",
                                            "Optional. Generator-specific parameters. For"
                                                    + " numeric_range: required keys are 'min'"
                                                    + " (number) and 'max' (number). For"
                                                    + " brewer_sequential: optional key 'hue'"
                                                    + " (string) selects the palette color family"
                                                    + " — e.g. 'blue', 'red', 'green', 'purple';"
                                                    + " defaults to 'blue'. Ignored for rainbow,"
                                                    + " random, and shape_cycle."))
                            .build());

    static final String OUTPUT_SCHEMA =
            McpSchema.toSchemaJson(CreateDiscreteMappingGeneratedResponse.class);

    // -- Dependencies ----------------------------------------------------------

    private final CyApplicationManager appManager;
    private final VisualMappingManager vmmManager;
    private final RenderingEngineManager renderingEngineManager;
    private final VisualMappingFunctionFactory discreteMappingFactory;
    private final VisualPropertyService vpService;
    private final GeneratorService generatorService;

    public CreateDiscreteMappingGeneratedTool(
            CyApplicationManager appManager,
            VisualMappingManager vmmManager,
            RenderingEngineManager renderingEngineManager,
            VisualMappingFunctionFactory discreteMappingFactory,
            VisualPropertyService vpService,
            GeneratorService generatorService) {
        this.appManager = appManager;
        this.vmmManager = vmmManager;
        this.renderingEngineManager = renderingEngineManager;
        this.discreteMappingFactory = discreteMappingFactory;
        this.vpService = vpService;
        this.generatorService = generatorService;
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

    @SuppressWarnings({"unchecked", "rawtypes"})
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

            CyNetwork network = appManager.getCurrentNetwork();
            VisualStyle style = vmmManager.getCurrentVisualStyle();
            VisualLexicon lexicon = renderingEngineManager.getDefaultVisualLexicon();

            Map<String, Object> args = request.arguments();
            if (args == null) args = Collections.emptyMap();

            String propertyId = String.valueOf(args.get("property_id"));
            String columnName = String.valueOf(args.get("column_name"));
            String columnTypeStr = String.valueOf(args.get("column_type"));
            String generator = String.valueOf(args.get("generator"));

            // VALIDATION: visual property
            VisualProperty<?> vp = vpService.findPropertyById(lexicon, propertyId);
            if (vp == null) {
                return error(
                        "Unknown visual property ID '"
                                + propertyId
                                + "'. Call get_mappable_properties to retrieve valid property"
                                + " IDs.");
            }
            if (!vpService.isSupported(vp)) {
                return error("Property '" + propertyId + "' is not supported for MCP mapping.");
            }

            // VALIDATION: column type
            Class<?> columnTypeClass;
            try {
                columnTypeClass = vpService.resolveColumnType(columnTypeStr);
            } catch (IllegalArgumentException e) {
                return error(e.getMessage());
            }

            // VALIDATION: generator name
            List<String> validGenerators =
                    List.of(
                            "rainbow",
                            "random",
                            "brewer_sequential",
                            "shape_cycle",
                            "numeric_range");
            if (!validGenerators.contains(generator)) {
                return error(
                        "Unknown generator '"
                                + generator
                                + "'. Valid values: "
                                + String.join(", ", validGenerators)
                                + ".");
            }

            // VALIDATION: generator ↔ VP type compatibility (before any table lookups)
            String compatError = checkCompatibility(generator, vp);
            if (compatError != null) {
                return error(compatError);
            }

            // Parse generator_params
            Map<String, Object> generatorParams = Collections.emptyMap();
            Object rawParams = args.get("generator_params");
            if (rawParams instanceof Map) {
                generatorParams = (Map<String, Object>) rawParams;
            }

            // VALIDATION: numeric_range requires min and max (before table lookups)
            if ("numeric_range".equals(generator)) {
                if (!generatorParams.containsKey("min") || !generatorParams.containsKey("max")) {
                    return error(
                            "Generator 'numeric_range' requires 'min' and 'max' in"
                                    + " generator_params.");
                }
            }

            // VALIDATION: table and column
            String tableName = vpService.getTableName(lexicon, vp);
            if (tableName == null) {
                return error(
                        "Property '"
                                + propertyId
                                + "' is a network-level property and cannot be driven by a column"
                                + " mapping.");
            }
            CyTable table =
                    "edge".equals(tableName)
                            ? network.getDefaultEdgeTable()
                            : network.getDefaultNodeTable();

            CyColumn col = table.getColumn(columnName);
            if (col == null) {
                return error(
                        "Column '"
                                + columnName
                                + "' was not found in the "
                                + tableName
                                + " table. Use a compatible columns query to see available"
                                + " columns.");
            }
            if (col.getListElementType() != null) {
                return error(
                        "Column '"
                                + columnName
                                + "' is a list-typed column. Only scalar columns (String,"
                                + " Integer, Long, Double, Boolean) are supported.");
            }

            // Enumerate distinct values, sorted by count descending then by string value
            Map<Object, Integer> counts = new LinkedHashMap<>();
            for (CyRow row : table.getAllRows()) {
                Object val = row.get(columnName, col.getType());
                if (val != null) {
                    counts.merge(val, 1, Integer::sum);
                }
            }
            if (counts.isEmpty()) {
                return error(
                        "Column '"
                                + columnName
                                + "' has no non-null values. Please choose a different column.");
            }

            // Build ordered set of distinct values (sort by count desc, then string for stability)
            Set<Object> distinctValues = new LinkedHashSet<>();
            counts.entrySet().stream()
                    .sorted(
                            Comparator.<Map.Entry<Object, Integer>>comparingInt(Map.Entry::getValue)
                                    .reversed()
                                    .thenComparing(e -> String.valueOf(e.getKey())))
                    .forEach(e -> distinctValues.add(e.getKey()));

            // Generate property values
            Map<Object, Object> generatedEntries;
            try {
                generatedEntries = runGenerator(generator, distinctValues, vp, generatorParams);
            } catch (Exception e) {
                return error("Generator failed: " + e.getMessage());
            }

            // Build typed column key → VP value map
            Map<Object, Object> parsedEntries = new LinkedHashMap<>();
            for (Object distinctVal : distinctValues) {
                Object typedKey;
                try {
                    typedKey =
                            vpService.parseColumnKey(String.valueOf(distinctVal), columnTypeClass);
                } catch (IllegalArgumentException e) {
                    return error(
                            "Column key conversion failed for value '"
                                    + distinctVal
                                    + "': "
                                    + e.getMessage());
                }
                Object genVal = generatedEntries.get(distinctVal);
                if (genVal != null) {
                    parsedEntries.put(typedKey, genVal);
                }
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
                LOGGER.error("Error applying generated discrete mapping on EDT", cause);
                return error(
                        "Failed to apply mapping: "
                                + (cause.getMessage() != null
                                        ? cause.getMessage()
                                        : cause.getClass().getSimpleName()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return error("Interrupted while applying generated discrete mapping.");
            }

            // Build sample entries (first ≤5 entries formatted as strings)
            Map<String, String> sampleEntries = new LinkedHashMap<>();
            int sampleLimit = 5;
            int sampleCount = 0;
            for (Map.Entry<Object, Object> entry : parsedEntries.entrySet()) {
                if (sampleCount >= sampleLimit) break;
                sampleEntries.put(
                        String.valueOf(entry.getKey()), vpService.formatValue(entry.getValue()));
                sampleCount++;
            }

            return CallToolResult.builder()
                    .structuredContent(
                            new CreateDiscreteMappingGeneratedResponse(
                                    "success",
                                    vp.getIdString(),
                                    vp.getDisplayName(),
                                    "DiscreteMapping",
                                    columnName,
                                    generator,
                                    parsedEntries.size(),
                                    sampleEntries))
                    .build();

        } catch (Exception e) {
            LOGGER.error("Error creating generated discrete mapping", e);
            return error("Failed to create generated discrete mapping: " + e.getMessage());
        }
    }

    // -- Generator dispatch ----------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<Object, Object> runGenerator(
            String generator,
            Set<Object> distinctValues,
            VisualProperty<?> vp,
            Map<String, Object> params) {
        switch (generator) {
            case "rainbow":
                return (Map<Object, Object>)
                        (Map<?, ?>) generatorService.generateRainbow(distinctValues);
            case "random":
                return (Map<Object, Object>)
                        (Map<?, ?>) generatorService.generateRandom(distinctValues);
            case "brewer_sequential":
                {
                    String hue =
                            params.containsKey("hue") ? String.valueOf(params.get("hue")) : "blue";
                    return (Map<Object, Object>)
                            (Map<?, ?>)
                                    generatorService.generateBrewerSequential(distinctValues, hue);
                }
            case "shape_cycle":
                return (Map<Object, Object>)
                        (Map<?, ?>) generatorService.generateShapeCycle(distinctValues, vp);
            case "numeric_range":
                {
                    double min = toDouble(params.get("min"));
                    double max = toDouble(params.get("max"));
                    return (Map<Object, Object>)
                            (Map<?, ?>)
                                    generatorService.generateNumericRange(
                                            distinctValues, min, max, vp.getRange().getType());
                }
            default:
                throw new IllegalArgumentException("Unknown generator: " + generator);
        }
    }

    // -- Compatibility check ---------------------------------------------------

    private static String checkCompatibility(String generator, VisualProperty<?> vp) {
        Class<?> vpType = vp.getRange().getType();
        switch (generator) {
            case "rainbow":
            case "random":
            case "brewer_sequential":
                if (!Paint.class.isAssignableFrom(vpType)
                        && !Color.class.isAssignableFrom(vpType)) {
                    return "Generator '"
                            + generator
                            + "' produces colors and requires a Paint visual property (e.g."
                            + " NODE_FILL_COLOR, EDGE_STROKE_UNSELECTED_PAINT). Property '"
                            + vp.getIdString()
                            + "' has type "
                            + vpType.getSimpleName()
                            + ".";
                }
                return null;
            case "shape_cycle":
                if (!(vp.getRange() instanceof DiscreteRange)
                        || Paint.class.isAssignableFrom(vpType)
                        || java.awt.Font.class.isAssignableFrom(vpType)) {
                    return "Generator 'shape_cycle' requires a discrete non-Paint, non-Font visual"
                            + " property (e.g. NODE_SHAPE, EDGE_LINE_TYPE). Property '"
                            + vp.getIdString()
                            + "' is not compatible.";
                }
                return null;
            case "numeric_range":
                if (vp.getRange() instanceof DiscreteRange
                        || !Number.class.isAssignableFrom(vpType)) {
                    return "Generator 'numeric_range' requires a numeric non-discrete visual"
                            + " property (e.g. NODE_SIZE, EDGE_WIDTH). Property '"
                            + vp.getIdString()
                            + "' is not compatible.";
                }
                return null;
            default:
                return null;
        }
    }

    // -- Helpers ---------------------------------------------------------------

    private static double toDouble(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return Double.parseDouble(String.valueOf(val));
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}
