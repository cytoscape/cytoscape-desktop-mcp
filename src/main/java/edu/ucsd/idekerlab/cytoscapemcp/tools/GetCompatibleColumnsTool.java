package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.model.VisualProperty;
import org.cytoscape.view.presentation.RenderingEngineManager;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool that lists data columns from the active network that are compatible with one or more
 * visual properties for data-driven mapping, with each column's mapping type support and sample
 * values.
 */
public class GetCompatibleColumnsTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetCompatibleColumnsTool.class);

    private static final String TOOL_NAME = "get_compatible_columns";

    private static final String TOOL_TITLE = "Get Cytoscape Desktop Mapping Columns";

    private static final String TOOL_EXAMPLES =
            "\n\n## Examples\n\n"
                    + "Example 1 — Check which data columns can drive a color mapping on nodes:\n"
                    + "{\"property_ids\": [\"NODE_FILL_COLOR\"]}\n\n"
                    + "Example 2 — Query compatible columns for multiple node properties at once to"
                    + " plan a complete node styling:\n"
                    + "{\"property_ids\": [\"NODE_FILL_COLOR\", \"NODE_SIZE\", \"NODE_LABEL\"]}\n\n"
                    + "Example 3 — Find columns compatible with edge width and edge color for edge"
                    + " styling:\n"
                    + "{\"property_ids\": [\"EDGE_WIDTH\","
                    + " \"EDGE_STROKE_UNSELECTED_PAINT\"]}\n\n"
                    + "Example 4 — Batch query for data columns related to speparate node and edge properties in one call:\n"
                    + "{\"property_ids\": [\"NODE_FILL_COLOR\", \"EDGE_WIDTH\"]}";

    private static final String TOOL_DESCRIPTION =
            "List data columns from the active network that are compatible with one or more visual"
                    + " properties for data-driven mapping in the active Cytoscape Desktop visual"
                    + " style, with each column's mapping type support (continuous, discrete,"
                    + " passthrough) and sample values. Use when you need to determine which data"
                    + " columns can drive a mapping for specific visual properties, or to check"
                    + " whether a column supports continuous interpolation, discrete value-to-value"
                    + " mapping, or passthrough before creating a mapping. Accepts one or more"
                    + " property identifiers to batch-query multiple properties in a single call —"
                    + " useful when planning mappings for several visual properties at once."
                    + " Read-only; does not modify state. Returns an error if no network is"
                    + " currently loaded, if the active network has no view, or if any requested"
                    + " property identifier is not recognized or not applicable to nodes or edges,"
                    + " each with a descriptive message indicating the specific cause.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // -- Schemas --------------------------------------------------------------

    static final String INPUT_SCHEMA =
            McpSchema.toJson(
                    McpSchema.InputSchema.builder()
                            .property(
                                    "property_ids",
                                    new McpSchema.InputProperty(
                                            "array",
                                            "Required. One or more visual property identifiers to query"
                                                    + " compatible data columns for. Use the property IDs"
                                                    + " returned by the mappable properties listing (e.g."
                                                    + " NODE_FILL_COLOR, NODE_SIZE, EDGE_WIDTH). Accepts a"
                                                    + " single property for focused queries or multiple"
                                                    + " properties to batch-retrieve compatible columns for"
                                                    + " several visual properties in one call."
                                                    + "\n\nExamples: [\"NODE_FILL_COLOR\"],"
                                                    + " [\"NODE_SIZE\", \"NODE_LABEL\", \"NODE_SHAPE\"]",
                                            new McpSchema.InputProperty("string", null),
                                            null))
                            .required("property_ids")
                            .build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(CompatibleColumnsResult.class);

    // -- Inner response records -----------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record CompatibleColumnsResult(
            @JsonPropertyDescription(
                            "Map from visual property ID to its compatible columns and metadata."
                                    + " Each key is a property ID string as provided in the request (e.g."
                                    + " NODE_FILL_COLOR, EDGE_WIDTH). Each value contains the property's"
                                    + " display name, value type, which table it reads from, and the list"
                                    + " of compatible data columns with their mapping support flags.")
                    @JsonProperty("properties")
                    Map<String, PropertyColumns> properties) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record PropertyColumns(
            @JsonPropertyDescription(
                            "Human-readable display name of the visual property."
                                    + "\n\nExamples: \"Node Fill Color\", \"Edge Width\", \"Node Label\"")
                    @JsonProperty("displayName")
                    String displayName,
            @JsonPropertyDescription(
                            "Value type of the visual property, indicating what kind of values it"
                                    + " accepts."
                                    + "\n\nExamples: \"Paint\", \"Double\", \"String\", \"NodeShape\"")
                    @JsonProperty("valueType")
                    String valueType,
            @JsonPropertyDescription(
                            "Which data table this property reads column values from: node or edge."
                                    + "\n\nExamples: \"node\", \"edge\"")
                    @JsonProperty("table")
                    String table,
            @JsonPropertyDescription(
                            "List of data columns from the table that are compatible with this visual"
                                    + " property for mapping. Excludes internal columns (SUID, selected)"
                                    + " and list-typed columns. Empty list when the table has no compatible"
                                    + " columns.")
                    @JsonProperty("columns")
                    List<ColumnCompatibility> columns) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ColumnCompatibility(
            @JsonPropertyDescription(
                            "Column name as it appears in the data table."
                                    + "\n\nExamples: \"Degree\", \"GeneType\", \"name\","
                                    + " \"BetweennessCentrality\"")
                    @JsonProperty("name")
                    String name,
            @JsonPropertyDescription(
                            "Data type of the column."
                                    + "\n\nExamples: \"String\", \"Integer\", \"Double\", \"Long\","
                                    + " \"Boolean\"")
                    @JsonProperty("type")
                    String type,
            @JsonPropertyDescription(
                            "Up to 5 unique non-null sample values from this column, serialized in"
                                    + " their natural JSON type (numbers as numbers, strings as strings).")
                    @JsonProperty("sampleValues")
                    List<Object> sampleValues,
            @JsonPropertyDescription(
                            "Which mapping types this column supports for the queried visual property."
                                    + " Continuous requires numeric column data; discrete is available for"
                                    + " all column types; passthrough is available only when the visual"
                                    + " property accepts String values.")
                    @JsonProperty("supportsMapping")
                    MappingSupport supportsMapping) {}

    private record MappingSupport(
            @JsonPropertyDescription(
                            "True if this column supports continuous mapping (numeric interpolation"
                                    + " or color gradient). Only numeric columns (Integer, Long, Double)"
                                    + " support continuous mapping."
                                    + "\n\nExamples: true, false")
                    @JsonProperty("continuous")
                    boolean continuous,
            @JsonPropertyDescription(
                            "True if this column supports discrete mapping (explicit value-to-value"
                                    + " map). All non-list columns support discrete mapping."
                                    + "\n\nExamples: true")
                    @JsonProperty("discrete")
                    boolean discrete,
            @JsonPropertyDescription(
                            "True if this column supports passthrough mapping (column value used"
                                    + " directly as the visual property value). Available only when the"
                                    + " visual property type is String (e.g. labels, tooltips)."
                                    + "\n\nExamples: true, false")
                    @JsonProperty("passthrough")
                    boolean passthrough) {}

    // -- Dependencies ---------------------------------------------------------

    private final CyApplicationManager appManager;
    private final RenderingEngineManager renderingEngineManager;
    private final VisualPropertyService vpService;

    public GetCompatibleColumnsTool(
            CyApplicationManager appManager,
            RenderingEngineManager renderingEngineManager,
            VisualPropertyService vpService) {
        this.appManager = appManager;
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

    @SuppressWarnings("unchecked")
    private CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        LOGGER.info("Tool call received: {}", TOOL_NAME);

        try {
            // Parse property_ids from request arguments
            Object rawIds = request.arguments().get("property_ids");
            List<String> propertyIds;
            if (rawIds instanceof List<?>) {
                propertyIds = ((List<?>) rawIds).stream().map(String::valueOf).toList();
            } else {
                propertyIds = List.of();
            }
            if (propertyIds.isEmpty()) {
                return error(
                        "The property_ids parameter is required and must contain at least one"
                                + " visual property identifier.");
            }

            // Error check: no network loaded
            CyNetwork network = appManager.getCurrentNetwork();
            if (network == null) {
                return error(
                        "No network is currently loaded in Cytoscape Desktop. A network must be"
                                + " loaded and selected as the current network before compatible"
                                + " columns can be queried.");
            }

            // Error check: no view
            if (appManager.getCurrentNetworkView() == null) {
                return error(
                        "The current network has no view in Cytoscape Desktop. A network view"
                                + " must exist before compatible columns can be queried.");
            }

            VisualLexicon lexicon = renderingEngineManager.getDefaultVisualLexicon();

            // Pre-compute node and edge descendant sets once
            Set<VisualProperty<?>> nodeDescendants =
                    (Set<VisualProperty<?>>)
                            (Set<?>)
                                    lexicon.getAllDescendants(
                                            org.cytoscape.view.presentation.property
                                                    .BasicVisualLexicon.NODE);
            Set<VisualProperty<?>> edgeDescendants =
                    (Set<VisualProperty<?>>)
                            (Set<?>)
                                    lexicon.getAllDescendants(
                                            org.cytoscape.view.presentation.property
                                                    .BasicVisualLexicon.EDGE);

            // Process each property ID
            List<String> errors = new ArrayList<>();
            Map<String, PropertyColumns> resultMap = new LinkedHashMap<>();

            for (String propertyId : propertyIds) {
                // Resolve VP
                VisualProperty<?> vp = vpService.findPropertyById(lexicon, propertyId);
                if (vp == null) {
                    errors.add("Unknown property: " + propertyId);
                    continue;
                }

                // Determine table
                String tableName = vpService.getTableName(lexicon, vp);
                if (tableName == null) {
                    errors.add("Not a node or edge property: " + propertyId);
                    continue;
                }

                CyTable table =
                        "node".equals(tableName)
                                ? network.getDefaultNodeTable()
                                : network.getDefaultEdgeTable();

                // Determine passthrough eligibility based on VP type
                boolean passthroughEligible = "String".equals(vpService.getTypeName(vp.getRange()));

                // Enumerate compatible columns
                List<ColumnCompatibility> columns = new ArrayList<>();
                for (CyColumn col : table.getColumns()) {
                    // Skip internal columns
                    if ("SUID".equals(col.getName()) || "selected".equals(col.getName())) {
                        continue;
                    }
                    // Skip list-typed columns
                    if (col.getListElementType() != null) {
                        continue;
                    }

                    boolean continuous = Number.class.isAssignableFrom(col.getType());
                    boolean discrete = true;
                    boolean passthrough = passthroughEligible;

                    // Collect up to 5 unique non-null sample values
                    List<Object> sampleValues = collectSampleValues(table, col, 5);

                    columns.add(
                            new ColumnCompatibility(
                                    col.getName(),
                                    col.getType().getSimpleName(),
                                    sampleValues,
                                    new MappingSupport(continuous, discrete, passthrough)));
                }

                // Sort columns alphabetically by name
                columns.sort(Comparator.comparing(ColumnCompatibility::name));

                resultMap.put(
                        propertyId,
                        new PropertyColumns(
                                vp.getDisplayName(),
                                vpService.getTypeName(vp.getRange()),
                                tableName,
                                columns));
            }

            // If any errors were collected, return a single error response
            if (!errors.isEmpty()) {
                return error(String.join("; ", errors));
            }

            return CallToolResult.builder()
                    .structuredContent(new CompatibleColumnsResult(resultMap))
                    .build();
        } catch (Exception e) {
            LOGGER.error("Error retrieving compatible columns", e);
            return error("Failed to retrieve compatible columns: " + e.getMessage());
        }
    }

    // -- Private helpers ------------------------------------------------------

    private List<Object> collectSampleValues(CyTable table, CyColumn col, int maxSamples) {
        Set<Object> seen = new LinkedHashSet<>();
        for (CyRow row : table.getAllRows()) {
            Object val = row.get(col.getName(), col.getType());
            if (val != null && seen.add(val)) {
                if (seen.size() >= maxSamples) break;
            }
        }
        return new ArrayList<>(seen);
    }

    // -- Result helpers -------------------------------------------------------

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}
