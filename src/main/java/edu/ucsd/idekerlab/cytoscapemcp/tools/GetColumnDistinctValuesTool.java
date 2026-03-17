package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool that enumerates distinct values in one or more node or edge table columns together with
 * their occurrence counts, sorted by frequency descending. Read-only; does not modify state.
 */
public class GetColumnDistinctValuesTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetColumnDistinctValuesTool.class);

    private static final String TOOL_NAME = "get_column_distinct_values";

    private static final String TOOL_TITLE = "Get Cytoscape Desktop Column Values";

    private static final String TOOL_EXAMPLES =
            "\n\n## Examples\n\n"
                    + "Example 1 — Enumerate distinct values in a single column to plan a discrete"
                    + " color mapping:\n"
                    + "{\"column_names\": [\"GeneType\"], \"table\": \"node\"}\n\n"
                    + "Example 2 — Batch: enumerate values for two columns in one call to plan"
                    + " discrete mappings for both node color and node shape without extra round"
                    + " trips:\n"
                    + "{\"column_names\": [\"GeneType\", \"community\"], \"table\": \"node\"}\n\n"
                    + "Example 3 — Batch: list distinct values for multiple edge columns to map"
                    + " each interaction type and confidence tier to a different visual style:\n"
                    + "{\"column_names\": [\"interaction\", \"tier\"], \"table\": \"edge\"}\n\n"
                    + "Example 4 — Batch: resolve all categorical columns needed for a full"
                    + " discrete visual style in a single round trip:\n"
                    + "{\"column_names\": [\"GeneType\", \"community\", \"isHub\"],"
                    + " \"table\": \"node\"}\n\n"
                    + "Example 5 — Limit returned values for a high-cardinality column:\n"
                    + "{\"column_names\": [\"name\"], \"table\": \"node\","
                    + " \"max_values\": 10}";

    private static final String TOOL_DESCRIPTION =
            "Enumerates distinct non-null values with occurrence counts for node or edge table columns in the"
                    + " active Cytoscape Desktop network. Use before creating discrete mappings to discover the full"
                    + " set of categorical values a column contains. Always batch multiple columns in a single call"
                    + " to avoid redundant round trips — see column_names for input details and the output schema"
                    + " for result and per-column error structure. Read-only; does not modify state.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // -- Schemas --------------------------------------------------------------

    static final String INPUT_SCHEMA =
            McpSchema.toJson(
                    McpSchema.InputSchema.builder()
                            .property(
                                    "column_names",
                                    new McpSchema.InputProperty(
                                            "array",
                                            "Required. One or more column names to enumerate"
                                                    + " distinct values for. Pass all columns you"
                                                    + " need in a single call — each is processed"
                                                    + " independently and its result (or"
                                                    + " per-column error) appears in the response"
                                                    + " map. Must be a non-empty list. Works with"
                                                    + " any non-list column type: String, Integer,"
                                                    + " Long, Double, or Boolean."
                                                    + "\n\nExamples: [\"GeneType\"],"
                                                    + " [\"GeneType\", \"community\","
                                                    + " \"interaction\"]",
                                            new McpSchema.InputProperty(
                                                    "string",
                                                    "Name of a column in the specified table."),
                                            null))
                            .property(
                                    "table",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Required. Which data table to query — node table or"
                                                    + " edge table. Applies to all columns in the"
                                                    + " list."
                                                    + "\n\nExamples: \"node\", \"edge\"",
                                            List.of("node", "edge")))
                            .property(
                                    "max_values",
                                    new McpSchema.InputProperty(
                                            "integer",
                                            "Optional. Maximum number of distinct values to"
                                                    + " return per column. Default 50. Values are"
                                                    + " sorted by count descending and clipped to"
                                                    + " this limit before returning. Compare"
                                                    + " values.size() to count to detect clipping."
                                                    + " Increase if you need the full value set for"
                                                    + " a high-cardinality column."
                                                    + "\n\nExamples: 50, 100, 200"))
                            .required("column_names", "table")
                            .build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(DistinctValuesResponse.class);

    // -- Inner response records -----------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record DistinctValuesEntry(
            @JsonPropertyDescription(
                            "Java type of the column data. Absent on error."
                                    + "\n\nExamples: \"String\", \"Integer\", \"Boolean\"")
                    @JsonProperty("type")
                    String type,
            @JsonPropertyDescription(
                            "Total number of distinct non-null values in the column, before any"
                                    + " max_values clipping. Compare this to values.size() to"
                                    + " detect whether results were clipped. Absent on error."
                                    + "\n\nExamples: 5, 3, 24")
                    @JsonProperty("count")
                    Integer count,
            @JsonPropertyDescription(
                            "List of distinct values with occurrence counts, sorted by count"
                                    + " descending. Absent on error.")
                    @JsonProperty("values")
                    List<ValueCount> values,
            @JsonPropertyDescription(
                            "Error message describing why this column could not be processed."
                                    + " Present only when an error occurred; all other fields"
                                    + " are absent.")
                    @JsonProperty("error")
                    String error) {

        static DistinctValuesEntry success(
                String type, int distinctCount, List<ValueCount> values) {
            return new DistinctValuesEntry(type, distinctCount, values, null);
        }

        static DistinctValuesEntry error(String message) {
            return new DistinctValuesEntry(null, null, null, message);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ValueCount(
            @JsonPropertyDescription(
                            "A distinct value in its natural JSON type: numbers as numbers,"
                                    + " strings as strings, booleans as booleans. Use this exact"
                                    + " value as the key when specifying discrete mapping entries."
                                    + "\n\nExamples: \"kinase\", 3, true, \"pp\"")
                    @JsonProperty("value")
                    Object value,
            @JsonPropertyDescription(
                            "Number of rows in the table that contain this exact value."
                                    + "\n\nExamples: 23, 8, 150")
                    @JsonProperty("count")
                    int count) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record DistinctValuesResponse(
            @JsonPropertyDescription(
                            "Map of column name to distinct-values result or per-column error. "
                                    + "Each key is a column name from the request.")
                    @JsonProperty("columns")
                    Map<String, DistinctValuesEntry> columns) {}

    // -- Dependencies ---------------------------------------------------------

    private final CyApplicationManager appManager;

    public GetColumnDistinctValuesTool(CyApplicationManager appManager) {
        this.appManager = appManager;
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
            List<String> columnNames = (List<String>) request.arguments().get("column_names");
            String tableName = (String) request.arguments().get("table");

            if (columnNames == null || columnNames.isEmpty()) {
                return error(
                        "The column_names parameter is required and must be a non-empty list of"
                                + " column names.");
            }
            if (tableName == null || tableName.isBlank()) {
                return error("The table parameter is required and must be 'node' or 'edge'.");
            }

            CyNetwork network = appManager.getCurrentNetwork();
            if (network == null) {
                return error(
                        "No network is currently loaded in Cytoscape Desktop. A network must be"
                                + " loaded and selected as the current network before distinct"
                                + " column values can be enumerated.");
            }

            CyTable table =
                    "node".equals(tableName)
                            ? network.getDefaultNodeTable()
                            : network.getDefaultEdgeTable();

            // Fetch rows once; reuse across all column computations.
            List<CyRow> rows = table.getAllRows();

            Object rawMax = request.arguments().get("max_values");
            int maxValues = 50;
            if (rawMax instanceof Number n) {
                maxValues = Math.max(1, n.intValue());
            }

            Map<String, DistinctValuesEntry> resultMap = new LinkedHashMap<>();
            for (String columnName : columnNames) {
                resultMap.put(
                        columnName, computeEntry(table, tableName, columnName, rows, maxValues));
            }

            return CallToolResult.builder()
                    .structuredContent(new DistinctValuesResponse(resultMap))
                    .build();

        } catch (Exception e) {
            LOGGER.error("Error enumerating distinct column values", e);
            return error("Failed to enumerate distinct column values: " + e.getMessage());
        }
    }

    private static DistinctValuesEntry computeEntry(
            CyTable table, String tableName, String columnName, List<CyRow> rows, int maxValues) {
        CyColumn col = table.getColumn(columnName);
        if (col == null) {
            return DistinctValuesEntry.error(
                    "Column '"
                            + columnName
                            + "' was not found in the "
                            + tableName
                            + " table. Use a compatible columns query to see available columns.");
        }

        if (col.getListElementType() != null) {
            return DistinctValuesEntry.error(
                    "Column '"
                            + columnName
                            + "' is a list-typed column and cannot be enumerated for distinct"
                            + " values. Only scalar columns (String, Integer, Long, Double,"
                            + " Boolean) are supported.");
        }

        Class<?> colType = col.getType();

        Map<Object, Integer> counts = new LinkedHashMap<>();
        for (CyRow row : rows) {
            Object val = row.get(columnName, colType);
            if (val != null) {
                counts.merge(val, 1, Integer::sum);
            }
        }

        if (counts.isEmpty()) {
            return DistinctValuesEntry.error(
                    "Column '"
                            + columnName
                            + "' has no non-null values. Please choose a different column.");
        }

        List<ValueCount> valueList = new ArrayList<>(counts.size());
        counts.entrySet().stream()
                .sorted(
                        Comparator.<Map.Entry<Object, Integer>>comparingInt(Map.Entry::getValue)
                                .reversed()
                                .thenComparing(e -> String.valueOf(e.getKey())))
                .forEach(e -> valueList.add(new ValueCount(e.getKey(), e.getValue())));

        List<ValueCount> clipped =
                maxValues < valueList.size() ? valueList.subList(0, maxValues) : valueList;
        return DistinctValuesEntry.success(colType.getSimpleName(), counts.size(), clipped);
    }

    // -- Result helpers -------------------------------------------------------

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}
