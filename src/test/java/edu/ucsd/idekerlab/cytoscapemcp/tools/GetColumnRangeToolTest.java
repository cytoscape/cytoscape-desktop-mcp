package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.idekerlab.cytoscapemcp.fixture.InMemoryTransport;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link GetColumnRangeTool} through its public interface ({@code toSpec()}) by
 * registering it on a real {@link io.modelcontextprotocol.server.McpSyncServer} backed by {@link
 * InMemoryTransport}. Cytoscape services are Mockito stubs.
 *
 * <p>Row mocks are always pre-created before calling {@code when(table.getAllRows()).thenReturn()}.
 * Calling {@code rowWith()} inside the {@code List.of()} argument to {@code thenReturn()} causes an
 * {@code UnfinishedStubbingException} because Mockito sees a new {@code when()} stub opened while
 * the outer {@code when(getAllRows())} stub is still open.
 */
public class GetColumnRangeToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // --- JSON-RPC protocol messages ----------------------------------------

    private static final String INIT_REQUEST =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\","
                    + "\"params\":{\"protocolVersion\":\"2025-03-26\","
                    + "\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";

    private static final String INITIALIZED_NOTIFICATION =
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

    // --- Mocks -------------------------------------------------------------

    @Mock private CyApplicationManager appManager;
    @Mock private CyNetwork network;
    @Mock private CyTable nodeTable;
    @Mock private CyTable edgeTable;

    private GetColumnRangeTool tool;
    private InMemoryTransport transport;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        tool = new GetColumnRangeTool(appManager);
    }

    @After
    public void tearDown() {
        if (transport != null) {
            transport.close();
        }
    }

    // -----------------------------------------------------------------------
    // Error: no network loaded
    // -----------------------------------------------------------------------

    @Test
    public void noNetworkLoaded_returnsError() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(null);

        String response = callTool("{\"column_names\":[\"Degree\"],\"table\":\"node\"}");

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should contain descriptive error message",
                response.contains("No network is currently loaded in Cytoscape Desktop"));
    }

    // -----------------------------------------------------------------------
    // Error: missing column_names parameter
    // -----------------------------------------------------------------------

    @Test
    public void missingColumnNames_returnsError() throws Exception {
        String response = callTool("{\"table\":\"node\"}");

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should mention column_names parameter",
                response.contains("column_names parameter is required"));
    }

    // -----------------------------------------------------------------------
    // Error: empty column_names list
    // -----------------------------------------------------------------------

    @Test
    public void emptyColumnNames_returnsError() throws Exception {
        String response = callTool("{\"column_names\":[],\"table\":\"node\"}");

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should mention column_names parameter",
                response.contains("column_names parameter is required"));
    }

    // -----------------------------------------------------------------------
    // Error: missing table parameter
    // -----------------------------------------------------------------------

    @Test
    public void missingTableParam_returnsError() throws Exception {
        String response = callTool("{\"column_names\":[\"Degree\"]}");

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should mention table parameter", response.contains("table parameter is required"));
    }

    // -----------------------------------------------------------------------
    // Per-column error: column not found
    // -----------------------------------------------------------------------

    @Test
    public void columnNotFound_returnsPerColumnError() throws Exception {
        stubNetworkAndTables();
        when(nodeTable.getColumn("Missing")).thenReturn(null);
        when(nodeTable.getAllRows()).thenReturn(List.of());

        String response = callTool("{\"column_names\":[\"Missing\"],\"table\":\"node\"}");

        assertFalse("Should not be a tool-level error", response.contains("\"isError\":true"));
        JsonNode entry = parseStructuredContent(response).get("columns").get("Missing");
        assertNotNull("Missing column should have an entry", entry);
        assertTrue("Entry should have error field", entry.has("error"));
        assertTrue(
                "Error should mention column name",
                entry.get("error").asText().contains("Missing"));
        assertTrue(
                "Error should mention node table",
                entry.get("error").asText().contains("node table"));
    }

    // -----------------------------------------------------------------------
    // Per-column error: non-numeric column type
    // -----------------------------------------------------------------------

    @Test
    public void nonNumericColumn_returnsPerColumnError() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("GeneType", String.class, null);
        when(nodeTable.getColumn("GeneType")).thenReturn(col);
        when(nodeTable.getAllRows()).thenReturn(List.of());

        String response = callTool("{\"column_names\":[\"GeneType\"],\"table\":\"node\"}");

        assertFalse("Should not be a tool-level error", response.contains("\"isError\":true"));
        JsonNode entry = parseStructuredContent(response).get("columns").get("GeneType");
        assertNotNull(entry);
        assertTrue("Entry should have error field", entry.has("error"));
        assertTrue(
                "Error should mention non-numeric",
                entry.get("error").asText().contains("not numeric"));
        assertTrue(
                "Error should include actual type", entry.get("error").asText().contains("String"));
    }

    // -----------------------------------------------------------------------
    // Per-column error: all null values
    // -----------------------------------------------------------------------

    @Test
    public void allNullValues_returnsPerColumnError() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("Score", Double.class, null);
        when(nodeTable.getColumn("Score")).thenReturn(col);

        CyRow row1 = mock(CyRow.class);
        CyRow row2 = mock(CyRow.class);
        when(row1.get("Score", Double.class)).thenReturn(null);
        when(row2.get("Score", Double.class)).thenReturn(null);
        when(nodeTable.getAllRows()).thenReturn(List.of(row1, row2));

        String response = callTool("{\"column_names\":[\"Score\"],\"table\":\"node\"}");

        assertFalse("Should not be a tool-level error", response.contains("\"isError\":true"));
        JsonNode entry = parseStructuredContent(response).get("columns").get("Score");
        assertNotNull(entry);
        assertTrue("Entry should have error field", entry.has("error"));
        assertTrue(
                "Error should mention no non-null values",
                entry.get("error").asText().contains("no non-null values"));
    }

    // -----------------------------------------------------------------------
    // Success: single Integer column in node table
    // -----------------------------------------------------------------------

    @Test
    public void integerColumn_nodeTable_computesCorrectStats() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("Degree", Integer.class, null);
        when(nodeTable.getColumn("Degree")).thenReturn(col);

        // Values: 1, 4, 12, 23, 45 → min=1, max=45, mean=17.0, count=5
        CyRow r1 = rowWith("Degree", Integer.class, 1);
        CyRow r2 = rowWith("Degree", Integer.class, 4);
        CyRow r3 = rowWith("Degree", Integer.class, 12);
        CyRow r4 = rowWith("Degree", Integer.class, 23);
        CyRow r5 = rowWith("Degree", Integer.class, 45);
        when(nodeTable.getAllRows()).thenReturn(List.of(r1, r2, r3, r4, r5));

        String response = callTool("{\"column_names\":[\"Degree\"],\"table\":\"node\"}");

        assertFalse("Should not be an error", response.contains("\"isError\":true"));
        JsonNode entry = parseStructuredContent(response).get("columns").get("Degree");
        assertNotNull(entry);
        assertFalse("Entry should not have error field", entry.has("error"));
        assertEquals("type should be Integer", "Integer", entry.get("type").asText());
        assertEquals("min should be 1.0", 1.0, entry.get("min").asDouble(), 0.0001);
        assertEquals("max should be 45.0", 45.0, entry.get("max").asDouble(), 0.0001);
        assertEquals("mean should be 17.0", 17.0, entry.get("mean").asDouble(), 0.0001);
        assertEquals("count should be 5", 5, entry.get("count").asInt());
    }

    // -----------------------------------------------------------------------
    // Success: Double column with decimal values
    // -----------------------------------------------------------------------

    @Test
    public void doubleColumn_computesCorrectStats() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("BetweennessCentrality", Double.class, null);
        when(nodeTable.getColumn("BetweennessCentrality")).thenReturn(col);

        // Values: 0.0, 0.5, 1.0 → min=0.0, max=1.0, mean=0.5, count=3
        CyRow r1 = rowWith("BetweennessCentrality", Double.class, 0.0);
        CyRow r2 = rowWith("BetweennessCentrality", Double.class, 0.5);
        CyRow r3 = rowWith("BetweennessCentrality", Double.class, 1.0);
        when(nodeTable.getAllRows()).thenReturn(List.of(r1, r2, r3));

        String response =
                callTool("{\"column_names\":[\"BetweennessCentrality\"],\"table\":\"node\"}");

        assertFalse("Should not be an error", response.contains("\"isError\":true"));
        JsonNode entry =
                parseStructuredContent(response).get("columns").get("BetweennessCentrality");
        assertNotNull(entry);
        assertEquals("type should be Double", "Double", entry.get("type").asText());
        assertEquals("min should be 0.0", 0.0, entry.get("min").asDouble(), 0.0001);
        assertEquals("max should be 1.0", 1.0, entry.get("max").asDouble(), 0.0001);
        assertEquals("mean should be 0.5", 0.5, entry.get("mean").asDouble(), 0.0001);
        assertEquals("count should be 3", 3, entry.get("count").asInt());
    }

    // -----------------------------------------------------------------------
    // Success: Long column
    // -----------------------------------------------------------------------

    @Test
    public void longColumn_computesCorrectStats() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("NodeCount", Long.class, null);
        when(nodeTable.getColumn("NodeCount")).thenReturn(col);

        // Values: 100L, 200L, 300L → min=100, max=300, mean=200, count=3
        CyRow r1 = rowWith("NodeCount", Long.class, 100L);
        CyRow r2 = rowWith("NodeCount", Long.class, 200L);
        CyRow r3 = rowWith("NodeCount", Long.class, 300L);
        when(nodeTable.getAllRows()).thenReturn(List.of(r1, r2, r3));

        String response = callTool("{\"column_names\":[\"NodeCount\"],\"table\":\"node\"}");

        assertFalse("Should not be an error", response.contains("\"isError\":true"));
        JsonNode entry = parseStructuredContent(response).get("columns").get("NodeCount");
        assertNotNull(entry);
        assertEquals("type should be Long", "Long", entry.get("type").asText());
        assertEquals("min should be 100.0", 100.0, entry.get("min").asDouble(), 0.0001);
        assertEquals("max should be 300.0", 300.0, entry.get("max").asDouble(), 0.0001);
        assertEquals("mean should be 200.0", 200.0, entry.get("mean").asDouble(), 0.0001);
        assertEquals("count should be 3", 3, entry.get("count").asInt());
    }

    // -----------------------------------------------------------------------
    // Success: edge table routing
    // -----------------------------------------------------------------------

    @Test
    public void edgeTable_queriesEdgeTableNotNodeTable() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("weight", Double.class, null);
        when(edgeTable.getColumn("weight")).thenReturn(col);
        when(nodeTable.getColumn("weight")).thenReturn(null); // must not be used

        // Values: 0.2, 0.8 → min=0.2, max=0.8, mean=0.5, count=2
        CyRow r1 = rowWith("weight", Double.class, 0.2);
        CyRow r2 = rowWith("weight", Double.class, 0.8);
        when(edgeTable.getAllRows()).thenReturn(List.of(r1, r2));

        String response = callTool("{\"column_names\":[\"weight\"],\"table\":\"edge\"}");

        assertFalse("Should not be an error", response.contains("\"isError\":true"));
        JsonNode entry = parseStructuredContent(response).get("columns").get("weight");
        assertNotNull(entry);
        assertEquals("min should be 0.2", 0.2, entry.get("min").asDouble(), 0.0001);
        assertEquals("max should be 0.8", 0.8, entry.get("max").asDouble(), 0.0001);
    }

    // -----------------------------------------------------------------------
    // Success: single non-null row — min equals max equals mean
    // -----------------------------------------------------------------------

    @Test
    public void singleValue_minEqualsMaxEqualsMean() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("Score", Integer.class, null);
        when(nodeTable.getColumn("Score")).thenReturn(col);

        CyRow r1 = rowWith("Score", Integer.class, 7);
        when(nodeTable.getAllRows()).thenReturn(List.of(r1));

        String response = callTool("{\"column_names\":[\"Score\"],\"table\":\"node\"}");

        assertFalse("Should not be an error", response.contains("\"isError\":true"));
        JsonNode entry = parseStructuredContent(response).get("columns").get("Score");
        assertNotNull(entry);
        assertEquals("min should equal 7.0", 7.0, entry.get("min").asDouble(), 0.0001);
        assertEquals("max should equal 7.0", 7.0, entry.get("max").asDouble(), 0.0001);
        assertEquals("mean should equal 7.0", 7.0, entry.get("mean").asDouble(), 0.0001);
        assertEquals("count should be 1", 1, entry.get("count").asInt());
    }

    // -----------------------------------------------------------------------
    // Success: null rows are skipped
    // -----------------------------------------------------------------------

    @Test
    public void mixedNullAndNonNull_nullRowsSkipped() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("Score", Integer.class, null);
        when(nodeTable.getColumn("Score")).thenReturn(col);

        // null, 10, null, 20 → count=2, min=10, max=20, mean=15
        CyRow nullRow1 = mock(CyRow.class);
        CyRow nullRow2 = mock(CyRow.class);
        when(nullRow1.get("Score", Integer.class)).thenReturn(null);
        when(nullRow2.get("Score", Integer.class)).thenReturn(null);
        CyRow r2 = rowWith("Score", Integer.class, 10);
        CyRow r4 = rowWith("Score", Integer.class, 20);
        when(nodeTable.getAllRows()).thenReturn(List.of(nullRow1, r2, nullRow2, r4));

        String response = callTool("{\"column_names\":[\"Score\"],\"table\":\"node\"}");

        assertFalse("Should not be an error", response.contains("\"isError\":true"));
        JsonNode entry = parseStructuredContent(response).get("columns").get("Score");
        assertNotNull(entry);
        assertEquals("count should be 2 (nulls excluded)", 2, entry.get("count").asInt());
        assertEquals("min should be 10.0", 10.0, entry.get("min").asDouble(), 0.0001);
        assertEquals("max should be 20.0", 20.0, entry.get("max").asDouble(), 0.0001);
        assertEquals("mean should be 15.0", 15.0, entry.get("mean").asDouble(), 0.0001);
    }

    // -----------------------------------------------------------------------
    // Success: negative values
    // -----------------------------------------------------------------------

    @Test
    public void negativeValues_computedCorrectly() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("expression", Double.class, null);
        when(nodeTable.getColumn("expression")).thenReturn(col);

        // Values: -2.0, 0.0, 2.0 → min=-2.0, max=2.0, mean=0.0, count=3
        CyRow r1 = rowWith("expression", Double.class, -2.0);
        CyRow r2 = rowWith("expression", Double.class, 0.0);
        CyRow r3 = rowWith("expression", Double.class, 2.0);
        when(nodeTable.getAllRows()).thenReturn(List.of(r1, r2, r3));

        String response = callTool("{\"column_names\":[\"expression\"],\"table\":\"node\"}");

        assertFalse("Should not be an error", response.contains("\"isError\":true"));
        JsonNode entry = parseStructuredContent(response).get("columns").get("expression");
        assertNotNull(entry);
        assertEquals("min should be -2.0", -2.0, entry.get("min").asDouble(), 0.0001);
        assertEquals("max should be 2.0", 2.0, entry.get("max").asDouble(), 0.0001);
        assertEquals("mean should be 0.0", 0.0, entry.get("mean").asDouble(), 0.0001);
    }

    // -----------------------------------------------------------------------
    // Batch: multiple columns resolved in one call
    // -----------------------------------------------------------------------

    @Test
    public void batchColumns_allFound_returnsAllResults() throws Exception {
        stubNetworkAndTables();
        CyColumn degreeCol = mockColumn("Degree", Integer.class, null);
        CyColumn bcCol = mockColumn("BetweennessCentrality", Double.class, null);
        when(nodeTable.getColumn("Degree")).thenReturn(degreeCol);
        when(nodeTable.getColumn("BetweennessCentrality")).thenReturn(bcCol);

        // Rows have values for both columns
        CyRow r1 = mock(CyRow.class);
        CyRow r2 = mock(CyRow.class);
        when(r1.get("Degree", Integer.class)).thenReturn(5);
        when(r1.get("BetweennessCentrality", Double.class)).thenReturn(0.1);
        when(r2.get("Degree", Integer.class)).thenReturn(15);
        when(r2.get("BetweennessCentrality", Double.class)).thenReturn(0.9);
        when(nodeTable.getAllRows()).thenReturn(List.of(r1, r2));

        String response =
                callTool(
                        "{\"column_names\":[\"Degree\",\"BetweennessCentrality\"],"
                                + "\"table\":\"node\"}");

        assertFalse("Should not be a tool-level error", response.contains("\"isError\":true"));
        JsonNode columns = parseStructuredContent(response).get("columns");

        // Both columns present in the map
        assertNotNull("Degree entry should be present", columns.get("Degree"));
        assertNotNull(
                "BetweennessCentrality entry should be present",
                columns.get("BetweennessCentrality"));

        // Degree: min=5, max=15, mean=10
        JsonNode degree = columns.get("Degree");
        assertFalse("Degree should not have error", degree.has("error"));
        assertEquals(5.0, degree.get("min").asDouble(), 0.0001);
        assertEquals(15.0, degree.get("max").asDouble(), 0.0001);
        assertEquals(10.0, degree.get("mean").asDouble(), 0.0001);

        // BetweennessCentrality: min=0.1, max=0.9, mean=0.5
        JsonNode bc = columns.get("BetweennessCentrality");
        assertFalse("BC should not have error", bc.has("error"));
        assertEquals(0.1, bc.get("min").asDouble(), 0.0001);
        assertEquals(0.9, bc.get("max").asDouble(), 0.0001);
        assertEquals(0.5, bc.get("mean").asDouble(), 0.0001);
    }

    // -----------------------------------------------------------------------
    // Batch: one valid column, one missing — partial success, no tool-level error
    // -----------------------------------------------------------------------

    @Test
    public void batchColumns_mixedResults_partialSuccessWithErrors() throws Exception {
        stubNetworkAndTables();
        CyColumn degreeCol = mockColumn("Degree", Integer.class, null);
        when(nodeTable.getColumn("Degree")).thenReturn(degreeCol);
        when(nodeTable.getColumn("Missing")).thenReturn(null);

        CyRow r1 = rowWith("Degree", Integer.class, 5);
        CyRow r2 = rowWith("Degree", Integer.class, 15);
        when(nodeTable.getAllRows()).thenReturn(List.of(r1, r2));

        String response =
                callTool("{\"column_names\":[\"Degree\",\"Missing\"],\"table\":\"node\"}");

        assertFalse("Should not be a tool-level error", response.contains("\"isError\":true"));
        JsonNode columns = parseStructuredContent(response).get("columns");

        // Degree succeeds
        JsonNode degree = columns.get("Degree");
        assertNotNull(degree);
        assertFalse("Degree should not have error", degree.has("error"));
        assertEquals(5.0, degree.get("min").asDouble(), 0.0001);

        // Missing has per-column error, no stats fields
        JsonNode missing = columns.get("Missing");
        assertNotNull(missing);
        assertTrue("Missing should have error field", missing.has("error"));
        assertNull("Missing should not have min", missing.get("min"));
        assertNull("Missing should not have max", missing.get("max"));
    }

    // -----------------------------------------------------------------------
    // Schema: input schema has column_names as array and table enum
    // -----------------------------------------------------------------------

    @Test
    public void inputSchema_hasArrayColumnNamesAndTableEnum() throws Exception {
        JsonNode schema = MAPPER.readTree(GetColumnRangeTool.INPUT_SCHEMA);
        assertNotNull(schema);
        assertEquals("object", schema.get("type").asText());

        JsonNode required = schema.get("required");
        assertNotNull("required array should exist", required);
        assertTrue("required should be an array", required.isArray());
        assertEquals("should have 2 required fields", 2, required.size());

        List<String> requiredFields = List.of(required.get(0).asText(), required.get(1).asText());
        assertTrue("column_names should be required", requiredFields.contains("column_names"));
        assertTrue("table should be required", requiredFields.contains("table"));

        JsonNode props = schema.get("properties");
        assertNotNull(props);

        JsonNode colNamesSchema = props.get("column_names");
        assertNotNull("column_names property should exist", colNamesSchema);
        assertEquals(
                "column_names should be array type", "array", colNamesSchema.get("type").asText());
        assertNotNull("column_names should have items", colNamesSchema.get("items"));

        JsonNode tableEnum = props.get("table").get("enum");
        assertNotNull("table should have an enum", tableEnum);
        assertTrue("enum should contain node", tableEnum.toString().contains("node"));
        assertTrue("enum should contain edge", tableEnum.toString().contains("edge"));
    }

    // -----------------------------------------------------------------------
    // Schema: output schema is parseable JSON
    // -----------------------------------------------------------------------

    @Test
    public void outputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(GetColumnRangeTool.OUTPUT_SCHEMA);
        assertNotNull("Output schema should be non-null", schema);
        assertTrue("Output schema should be a JSON object", schema.isObject());
    }

    // -----------------------------------------------------------------------
    // Spec: tool name, title, and description
    // -----------------------------------------------------------------------

    @Test
    public void toolSpec_hasExpectedNameTitleAndDescription() {
        var spec = tool.toSpec();
        assertNotNull("Spec should not be null", spec);

        var toolDef = spec.tool();
        assertEquals("Tool name should be get_column_range", "get_column_range", toolDef.name());
        assertEquals(
                "Tool title should match", "Get Cytoscape Desktop Column Range", toolDef.title());

        String desc = toolDef.description();
        assertNotNull("Description should not be null", desc);
        assertFalse("Description should not be empty", desc.isBlank());
        assertTrue(
                "Description should mention continuous mapping",
                desc.contains("continuous mapping"));
        assertTrue("Description should state read-only", desc.contains("Read-only"));
        assertTrue("Description should mention batching", desc.contains("batch"));
        assertTrue("Description should contain examples", desc.contains("## Examples"));
        assertTrue("Description should contain at least 4 examples", desc.contains("Example 4"));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void stubNetworkAndTables() {
        when(appManager.getCurrentNetwork()).thenReturn(network);
        when(network.getDefaultNodeTable()).thenReturn(nodeTable);
        when(network.getDefaultEdgeTable()).thenReturn(edgeTable);
    }

    @SuppressWarnings("unchecked")
    private CyColumn mockColumn(String name, Class<?> type, Class<?> listElementType) {
        CyColumn col = mock(CyColumn.class);
        when(col.getName()).thenReturn(name);
        when(col.getType()).thenReturn((Class) type);
        when(col.getListElementType()).thenReturn((Class) listElementType);
        return col;
    }

    @SuppressWarnings("unchecked")
    private <T> CyRow rowWith(String col, Class<T> type, T value) {
        CyRow row = mock(CyRow.class);
        when(row.get(col, type)).thenReturn(value);
        return row;
    }

    private JsonNode parseStructuredContent(String fullResponse) throws Exception {
        String[] lines = fullResponse.split("\n");
        String toolResponseLine = lines[lines.length - 1];
        JsonNode root = MAPPER.readTree(toolResponseLine);
        return root.at("/result/structuredContent");
    }

    private String callTool(String arguments) throws Exception {
        String toolCall =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"get_column_range\","
                        + "\"arguments\":"
                        + arguments
                        + "}}";

        transport = new InMemoryTransport();
        transport.startServer("cytoscape-mcp-test", "0.0.0-test", List.of(tool.toSpec()));

        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(toolCall);
        transport.await();

        return transport.getResponse();
    }
}
