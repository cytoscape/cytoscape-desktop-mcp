package edu.ucsd.idekerlab.cytoscapemcp.tools;

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
 * MCP tool that computes min, max, mean, and count statistics for one or more numeric columns in
 * the node or edge table of the active Cytoscape network. Read-only; does not modify state.
 */
public class GetColumnRangeTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetColumnRangeTool.class);

    private static final String TOOL_NAME = "get_column_range";

    private static final String TOOL_TITLE = "Get Cytoscape Desktop Column Range";

    private static final String TOOL_EXAMPLES =
            "\n\n## Examples\n\n"
                    + "Example 1 — Get the range of a single degree column to plan continuous"
                    + " mapping breakpoints for node size:\n"
                    + "{\"column_names\": [\"Degree\"], \"table\": \"node\"}\n\n"
                    + "Example 2 — Batch: get ranges for three node columns in one call to plan"
                    + " multiple continuous mappings without extra round trips:\n"
                    + "{\"column_names\": [\"Degree\", \"BetweennessCentrality\", \"expression\"],"
                    + " \"table\": \"node\"}\n\n"
                    + "Example 3 — Batch: check ranges for two edge columns before creating"
                    + " continuous mappings for edge width and edge opacity:\n"
                    + "{\"column_names\": [\"weight\", \"confidence\"], \"table\": \"edge\"}\n\n"
                    + "Example 4 — Batch: inspect all numeric node columns needed to configure a"
                    + " full visual style in a single round trip:\n"
                    + "{\"column_names\": [\"Degree\", \"ClusterCoefficient\", \"Score\"],"
                    + " \"table\": \"node\"}";

    private static final String TOOL_DESCRIPTION =
            "Computes min, max, mean, and non-null count for numeric node or edge table columns in the active"
                    + " Cytoscape Desktop network. Use before setting up continuous mapping breakpoints to understand"
                    + " a column's data range. Always batch multiple columns in a single call to avoid redundant"
                    + " round trips — see column_names for input details and the output schema for result and"
                    + " per-column error structure. Read-only; does not modify state.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // -- Schemas --------------------------------------------------------------

    static final String INPUT_SCHEMA =
            McpSchema.toJson(
                    McpSchema.InputSchema.builder()
                            .property(
                                    "column_names",
                                    new McpSchema.InputProperty(
                                            "array",
                                            "Required. One or more column names to compute range"
                                                    + " statistics for. Pass all columns you need"
                                                    + " in a single call — each is processed"
                                                    + " independently and its result (or"
                                                    + " per-column error) appears in the response"
                                                    + " map. Must be a non-empty list. Each column"
                                                    + " must be a numeric type (Integer, Long, or"
                                                    + " Double)."
                                                    + "\n\nExamples: [\"Degree\"],"
                                                    + " [\"Degree\", \"BetweennessCentrality\","
                                                    + " \"expression\"]",
                                            new McpSchema.InputProperty(
                                                    "string",
                                                    "Name of a numeric column in the specified"
                                                            + " table."),
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
                            .required("column_names", "table")
                            .build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(ColumnRangeResponse.class);

    // -- Inner response record ------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ColumnRangeEntry(
            @JsonPropertyDescription(
                            "Java type of the column data. Absent on error."
                                    + "\n\nExamples: \"Integer\", \"Long\", \"Double\"")
                    @JsonProperty("type")
                    String type,
            @JsonPropertyDescription(
                            "Minimum non-null value as a double. Absent on error."
                                    + "\n\nExamples: 1.0, 0.0, -2.5")
                    @JsonProperty("min")
                    Double min,
            @JsonPropertyDescription(
                            "Maximum non-null value as a double. Absent on error."
                                    + "\n\nExamples: 45.0, 1.0, 12.7")
                    @JsonProperty("max")
                    Double max,
            @JsonPropertyDescription(
                            "Arithmetic mean of all non-null values. Absent on error."
                                    + "\n\nExamples: 8.3, 0.42, 5.0")
                    @JsonProperty("mean")
                    Double mean,
            @JsonPropertyDescription(
                            "Number of non-null rows that contributed to the statistics."
                                    + " Absent on error."
                                    + "\n\nExamples: 150, 42, 1200")
                    @JsonProperty("count")
                    Integer count,
            @JsonPropertyDescription(
                            "Error message describing why this column could not be processed."
                                    + " Present only when an error occurred; all other fields"
                                    + " are absent.")
                    @JsonProperty("error")
                    String error) {

        static ColumnRangeEntry success(
                String type, double min, double max, double mean, int count) {
            return new ColumnRangeEntry(type, min, max, mean, count, null);
        }

        static ColumnRangeEntry error(String message) {
            return new ColumnRangeEntry(null, null, null, null, null, message);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ColumnRangeResponse(
            @JsonPropertyDescription(
                            "Map of column name to range statistics or per-column error. "
                                    + "Each key is a column name from the request.")
                    @JsonProperty("columns")
                    Map<String, ColumnRangeEntry> columns) {}

    // -- Dependencies ---------------------------------------------------------

    private final CyApplicationManager appManager;

    public GetColumnRangeTool(CyApplicationManager appManager) {
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
                                + " loaded and selected as the current network before column"
                                + " statistics can be computed.");
            }

            CyTable table =
                    "node".equals(tableName)
                            ? network.getDefaultNodeTable()
                            : network.getDefaultEdgeTable();

            // Fetch rows once; reuse across all column computations.
            List<CyRow> rows = table.getAllRows();

            Map<String, ColumnRangeEntry> resultMap = new LinkedHashMap<>();
            for (String columnName : columnNames) {
                resultMap.put(columnName, computeEntry(table, tableName, columnName, rows));
            }

            return CallToolResult.builder()
                    .structuredContent(new ColumnRangeResponse(resultMap))
                    .build();

        } catch (Exception e) {
            LOGGER.error("Error computing column range", e);
            return error("Failed to compute column range: " + e.getMessage());
        }
    }

    private static ColumnRangeEntry computeEntry(
            CyTable table, String tableName, String columnName, List<CyRow> rows) {
        CyColumn col = table.getColumn(columnName);
        if (col == null) {
            return ColumnRangeEntry.error(
                    "Column '"
                            + columnName
                            + "' was not found in the "
                            + tableName
                            + " table. Use a compatible columns query to see available columns.");
        }

        Class<?> colType = col.getType();
        if (!Number.class.isAssignableFrom(colType)) {
            return ColumnRangeEntry.error(
                    "Column '"
                            + columnName
                            + "' has type '"
                            + colType.getSimpleName()
                            + "', which is not numeric. Only Integer, Long, and Double columns"
                            + " support range statistics. Use the distinct values query for"
                            + " non-numeric columns.");
        }

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double sum = 0.0;
        int count = 0;

        for (CyRow row : rows) {
            Object val = row.get(columnName, colType);
            if (val != null) {
                double d = ((Number) val).doubleValue();
                if (d < min) min = d;
                if (d > max) max = d;
                sum += d;
                count++;
            }
        }

        if (count == 0) {
            return ColumnRangeEntry.error(
                    "Column '"
                            + columnName
                            + "' has no non-null values. Please choose a different column.");
        }

        return ColumnRangeEntry.success(colType.getSimpleName(), min, max, sum / count, count);
    }

    // -- Result helpers -------------------------------------------------------

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}
