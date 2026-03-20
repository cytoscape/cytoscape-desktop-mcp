package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.ArrayList;
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
 * Exercises {@link GetColumnDistinctValuesTool} through its public interface ({@code toSpec()}) by
 * registering it on a real {@link io.modelcontextprotocol.server.McpSyncServer} backed by {@link
 * InMemoryTransport}. Cytoscape services are Mockito stubs.
 *
 * <p>Row mocks are always pre-created before calling {@code when(table.getAllRows()).thenReturn()}.
 * Calling {@code rowWith()} inside the {@code List.of()} argument to {@code thenReturn()} causes an
 * {@code UnfinishedStubbingException} because Mockito sees a new {@code when()} stub opened while
 * the outer {@code when(getAllRows())} stub is still open.
 */
public class GetColumnDistinctValuesToolTest {

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

    private GetColumnDistinctValuesTool tool;
    private InMemoryTransport transport;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        tool = new GetColumnDistinctValuesTool(appManager);
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

        String response = callTool("{\"column_names\":[\"GeneType\"],\"table\":\"node\"}");

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
        String response = callTool("{\"column_names\":[\"GeneType\"]}");

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
    // Per-column error: list-typed column rejected
    // -----------------------------------------------------------------------

    @Test
    public void listTypeColumn_returnsPerColumnError() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("Tags", List.class, String.class);
        when(nodeTable.getColumn("Tags")).thenReturn(col);
        when(nodeTable.getAllRows()).thenReturn(List.of());

        String response = callTool("{\"column_names\":[\"Tags\"],\"table\":\"node\"}");

        assertFalse("Should not be a tool-level error", response.contains("\"isError\":true"));
        JsonNode entry = parseStructuredContent(response).get("columns").get("Tags");
        assertNotNull(entry);
        assertTrue("Entry should have error field", entry.has("error"));
        assertTrue(
                "Error should mention list-typed column",
                entry.get("error").asText().contains("list-typed column"));
    }

    // -----------------------------------------------------------------------
    // Per-column error: all null values
    // -----------------------------------------------------------------------

    @Test
    public void allNullValues_returnsPerColumnError() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("GeneType", String.class, null);
        when(nodeTable.getColumn("GeneType")).thenReturn(col);

        CyRow row1 = mock(CyRow.class);
        CyRow row2 = mock(CyRow.class);
        when(row1.get("GeneType", String.class)).thenReturn(null);
        when(row2.get("GeneType", String.class)).thenReturn(null);
        when(nodeTable.getAllRows()).thenReturn(List.of(row1, row2));

        String response = callTool("{\"column_names\":[\"GeneType\"],\"table\":\"node\"}");

        assertFalse("Should not be a tool-level error", response.contains("\"isError\":true"));
        JsonNode entry = parseStructuredContent(response).get("columns").get("GeneType");
        assertNotNull(entry);
        assertTrue("Entry should have error field", entry.has("error"));
        assertTrue(
                "Error should mention no non-null values",
                entry.get("error").asText().contains("no non-null values"));
    }

    // -----------------------------------------------------------------------
    // Success: single String column returns distinct values and counts
    // -----------------------------------------------------------------------

    @Test
    public void stringColumn_returnsDistinctValuesAndCounts() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("GeneType", String.class, null);
        when(nodeTable.getColumn("GeneType")).thenReturn(col);

        // kinase×3, receptor×2, TF×1
        CyRow r1 = rowWith("GeneType", String.class, "kinase");
        CyRow r2 = rowWith("GeneType", String.class, "receptor");
        CyRow r3 = rowWith("GeneType", String.class, "kinase");
        CyRow r4 = rowWith("GeneType", String.class, "TF");
        CyRow r5 = rowWith("GeneType", String.class, "kinase");
        CyRow r6 = rowWith("GeneType", String.class, "receptor");
        when(nodeTable.getAllRows()).thenReturn(List.of(r1, r2, r3, r4, r5, r6));

        String response = callTool("{\"column_names\":[\"GeneType\"],\"table\":\"node\"}");

        assertFalse("Should not be an error", response.contains("\"isError\":true"));
        JsonNode entry = parseStructuredContent(response).get("columns").get("GeneType");
        assertNotNull(entry);
        assertFalse("Entry should not have error field", entry.has("error"));

        assertEquals("type should be String", "String", entry.get("type").asText());
        assertEquals("count should be 3", 3, entry.get("count").asInt());

        JsonNode values = entry.get("values");
        assertNotNull("values should be present", values);
        assertEquals("values array should have 3 entries", 3, values.size());

        assertEquals("First value should be kinase", "kinase", values.get(0).get("value").asText());
        assertEquals("kinase count should be 3", 3, values.get(0).get("count").asInt());
        assertEquals(
                "Second value should be receptor", "receptor", values.get(1).get("value").asText());
        assertEquals("receptor count should be 2", 2, values.get(1).get("count").asInt());
        assertEquals("Third value should be TF", "TF", values.get(2).get("value").asText());
        assertEquals("TF count should be 1", 1, values.get(2).get("count").asInt());
    }

    // -----------------------------------------------------------------------
    // Success: Integer column — values serialized as JSON numbers
    // -----------------------------------------------------------------------

    @Test
    public void integerColumn_valuesSerializedAsNumbers() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("Cluster", Integer.class, null);
        when(nodeTable.getColumn("Cluster")).thenReturn(col);

        // Cluster 1×3, Cluster 2×2
        CyRow r1 = rowWith("Cluster", Integer.class, 1);
        CyRow r2 = rowWith("Cluster", Integer.class, 2);
        CyRow r3 = rowWith("Cluster", Integer.class, 1);
        CyRow r4 = rowWith("Cluster", Integer.class, 1);
        CyRow r5 = rowWith("Cluster", Integer.class, 2);
        when(nodeTable.getAllRows()).thenReturn(List.of(r1, r2, r3, r4, r5));

        String response = callTool("{\"column_names\":[\"Cluster\"],\"table\":\"node\"}");

        assertFalse("Should not be an error", response.contains("\"isError\":true"));
        JsonNode entry = parseStructuredContent(response).get("columns").get("Cluster");
        assertNotNull(entry);

        assertEquals("type should be Integer", "Integer", entry.get("type").asText());
        assertEquals("count should be 2", 2, entry.get("count").asInt());

        JsonNode values = entry.get("values");
        assertEquals("values array should have 2 entries", 2, values.size());

        JsonNode first = values.get(0);
        assertTrue("value should be a number, not a string", first.get("value").isNumber());
        assertEquals("first value should be 1", 1, first.get("value").asInt());
        assertEquals("first count should be 3", 3, first.get("count").asInt());
    }

    // -----------------------------------------------------------------------
    // Success: Boolean column — values serialized as JSON booleans
    // -----------------------------------------------------------------------

    @Test
    public void booleanColumn_valuesSerializedAsBooleans() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("isHub", Boolean.class, null);
        when(nodeTable.getColumn("isHub")).thenReturn(col);

        // false×4, true×2
        CyRow r1 = rowWith("isHub", Boolean.class, Boolean.FALSE);
        CyRow r2 = rowWith("isHub", Boolean.class, Boolean.TRUE);
        CyRow r3 = rowWith("isHub", Boolean.class, Boolean.FALSE);
        CyRow r4 = rowWith("isHub", Boolean.class, Boolean.FALSE);
        CyRow r5 = rowWith("isHub", Boolean.class, Boolean.FALSE);
        CyRow r6 = rowWith("isHub", Boolean.class, Boolean.TRUE);
        when(nodeTable.getAllRows()).thenReturn(List.of(r1, r2, r3, r4, r5, r6));

        String response = callTool("{\"column_names\":[\"isHub\"],\"table\":\"node\"}");

        assertFalse("Should not be an error", response.contains("\"isError\":true"));
        JsonNode entry = parseStructuredContent(response).get("columns").get("isHub");
        assertNotNull(entry);

        assertEquals("type should be Boolean", "Boolean", entry.get("type").asText());
        assertEquals("count should be 2", 2, entry.get("count").asInt());

        JsonNode values = entry.get("values");
        assertEquals("values array should have 2 entries", 2, values.size());

        JsonNode first = values.get(0);
        assertTrue("value should be a boolean node", first.get("value").isBoolean());
        assertFalse("first value should be false", first.get("value").asBoolean());
        assertEquals("false count should be 4", 4, first.get("count").asInt());
    }

    // -----------------------------------------------------------------------
    // Success: edge table routing
    // -----------------------------------------------------------------------

    @Test
    public void edgeTable_queriesEdgeTableNotNodeTable() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("interaction", String.class, null);
        when(edgeTable.getColumn("interaction")).thenReturn(col);
        when(nodeTable.getColumn("interaction")).thenReturn(null); // must not be used

        CyRow r1 = rowWith("interaction", String.class, "pp");
        CyRow r2 = rowWith("interaction", String.class, "pd");
        CyRow r3 = rowWith("interaction", String.class, "pp");
        when(edgeTable.getAllRows()).thenReturn(List.of(r1, r2, r3));

        String response = callTool("{\"column_names\":[\"interaction\"],\"table\":\"edge\"}");

        assertFalse("Should not be an error", response.contains("\"isError\":true"));
        JsonNode entry = parseStructuredContent(response).get("columns").get("interaction");
        assertNotNull(entry);

        assertEquals("count should be 2", 2, entry.get("count").asInt());
        assertEquals(
                "First value should be pp (count 2)",
                "pp",
                entry.get("values").get(0).get("value").asText());
    }

    // -----------------------------------------------------------------------
    // Sorting: values sorted by count descending
    // -----------------------------------------------------------------------

    @Test
    public void values_sortedByCountDescending() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("GeneType", String.class, null);
        when(nodeTable.getColumn("GeneType")).thenReturn(col);

        // TF×5, receptor×3, kinase×1 — inserted in non-descending order
        CyRow r1 = rowWith("GeneType", String.class, "kinase");
        CyRow r2 = rowWith("GeneType", String.class, "receptor");
        CyRow r3 = rowWith("GeneType", String.class, "TF");
        CyRow r4 = rowWith("GeneType", String.class, "TF");
        CyRow r5 = rowWith("GeneType", String.class, "receptor");
        CyRow r6 = rowWith("GeneType", String.class, "TF");
        CyRow r7 = rowWith("GeneType", String.class, "TF");
        CyRow r8 = rowWith("GeneType", String.class, "receptor");
        CyRow r9 = rowWith("GeneType", String.class, "TF");
        when(nodeTable.getAllRows()).thenReturn(List.of(r1, r2, r3, r4, r5, r6, r7, r8, r9));

        String response = callTool("{\"column_names\":[\"GeneType\"],\"table\":\"node\"}");

        assertFalse("Should not be an error", response.contains("\"isError\":true"));
        JsonNode values =
                parseStructuredContent(response).get("columns").get("GeneType").get("values");
        assertEquals("3 distinct values expected", 3, values.size());
        assertEquals("TF should be first (count 5)", "TF", values.get(0).get("value").asText());
        assertEquals(
                "receptor should be second (count 3)",
                "receptor",
                values.get(1).get("value").asText());
        assertEquals(
                "kinase should be third (count 1)", "kinase", values.get(2).get("value").asText());
    }

    // -----------------------------------------------------------------------
    // Tie-breaking: equal counts sorted alphabetically
    // -----------------------------------------------------------------------

    @Test
    public void tiedCounts_tieBrokenAlphabeticallyByValue() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("Category", String.class, null);
        when(nodeTable.getColumn("Category")).thenReturn(col);

        // All three values have count=2; alphabetical order: Alpha, Beta, Gamma
        CyRow r1 = rowWith("Category", String.class, "Gamma");
        CyRow r2 = rowWith("Category", String.class, "Alpha");
        CyRow r3 = rowWith("Category", String.class, "Beta");
        CyRow r4 = rowWith("Category", String.class, "Gamma");
        CyRow r5 = rowWith("Category", String.class, "Alpha");
        CyRow r6 = rowWith("Category", String.class, "Beta");
        when(nodeTable.getAllRows()).thenReturn(List.of(r1, r2, r3, r4, r5, r6));

        String response = callTool("{\"column_names\":[\"Category\"],\"table\":\"node\"}");

        assertFalse("Should not be an error", response.contains("\"isError\":true"));
        JsonNode values =
                parseStructuredContent(response).get("columns").get("Category").get("values");
        assertEquals("3 distinct values expected", 3, values.size());
        assertEquals(
                "Alpha should be first (tied count, alphabetical)",
                "Alpha",
                values.get(0).get("value").asText());
        assertEquals("Beta should be second", "Beta", values.get(1).get("value").asText());
        assertEquals("Gamma should be third", "Gamma", values.get(2).get("value").asText());
    }

    // -----------------------------------------------------------------------
    // Null rows are excluded from counts
    // -----------------------------------------------------------------------

    @Test
    public void nullRows_excludedFromCounts() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("GeneType", String.class, null);
        when(nodeTable.getColumn("GeneType")).thenReturn(col);

        // kinase×2, null×2 → distinct_count=1
        CyRow nullRow1 = mock(CyRow.class);
        CyRow nullRow2 = mock(CyRow.class);
        when(nullRow1.get("GeneType", String.class)).thenReturn(null);
        when(nullRow2.get("GeneType", String.class)).thenReturn(null);
        CyRow kinase1 = rowWith("GeneType", String.class, "kinase");
        CyRow kinase2 = rowWith("GeneType", String.class, "kinase");
        when(nodeTable.getAllRows()).thenReturn(List.of(kinase1, nullRow1, kinase2, nullRow2));

        String response = callTool("{\"column_names\":[\"GeneType\"],\"table\":\"node\"}");

        assertFalse("Should not be an error", response.contains("\"isError\":true"));
        JsonNode entry = parseStructuredContent(response).get("columns").get("GeneType");
        assertNotNull(entry);

        assertEquals("count should be 1 (nulls excluded)", 1, entry.get("count").asInt());
        assertEquals("values list should have 1 entry", 1, entry.get("values").size());
        assertEquals(
                "kinase count should be 2", 2, entry.get("values").get(0).get("count").asInt());
    }

    // -----------------------------------------------------------------------
    // distinct_count matches values list size
    // -----------------------------------------------------------------------

    @Test
    public void count_reflectsTotalDistinct_valuesListSizeEqualsCountWhenUnclipped()
            throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("GeneType", String.class, null);
        when(nodeTable.getColumn("GeneType")).thenReturn(col);

        CyRow r1 = rowWith("GeneType", String.class, "A");
        CyRow r2 = rowWith("GeneType", String.class, "B");
        CyRow r3 = rowWith("GeneType", String.class, "C");
        CyRow r4 = rowWith("GeneType", String.class, "A");
        when(nodeTable.getAllRows()).thenReturn(List.of(r1, r2, r3, r4));

        String response = callTool("{\"column_names\":[\"GeneType\"],\"table\":\"node\"}");

        assertFalse("Should not be an error", response.contains("\"isError\":true"));
        JsonNode entry = parseStructuredContent(response).get("columns").get("GeneType");
        assertNotNull(entry);

        int count = entry.get("count").asInt();
        int valuesSize = entry.get("values").size();
        assertEquals("count should equal values array size when not clipped", count, valuesSize);
    }

    // -----------------------------------------------------------------------
    // Batch: multiple columns resolved in one call
    // -----------------------------------------------------------------------

    @Test
    public void batchColumns_allFound_returnsAllResults() throws Exception {
        stubNetworkAndTables();
        CyColumn geneTypeCol = mockColumn("GeneType", String.class, null);
        CyColumn communityCol = mockColumn("community", Integer.class, null);
        when(nodeTable.getColumn("GeneType")).thenReturn(geneTypeCol);
        when(nodeTable.getColumn("community")).thenReturn(communityCol);

        // Rows have values for both columns
        CyRow r1 = mock(CyRow.class);
        CyRow r2 = mock(CyRow.class);
        CyRow r3 = mock(CyRow.class);
        when(r1.get("GeneType", String.class)).thenReturn("kinase");
        when(r1.get("community", Integer.class)).thenReturn(1);
        when(r2.get("GeneType", String.class)).thenReturn("receptor");
        when(r2.get("community", Integer.class)).thenReturn(2);
        when(r3.get("GeneType", String.class)).thenReturn("kinase");
        when(r3.get("community", Integer.class)).thenReturn(1);
        when(nodeTable.getAllRows()).thenReturn(List.of(r1, r2, r3));

        String response =
                callTool("{\"column_names\":[\"GeneType\",\"community\"]," + "\"table\":\"node\"}");

        assertFalse("Should not be a tool-level error", response.contains("\"isError\":true"));
        JsonNode columns = parseStructuredContent(response).get("columns");

        // Both columns present
        assertNotNull("GeneType entry should be present", columns.get("GeneType"));
        assertNotNull("community entry should be present", columns.get("community"));

        // GeneType: 2 distinct values
        JsonNode geneType = columns.get("GeneType");
        assertFalse("GeneType should not have error", geneType.has("error"));
        assertEquals("GeneType count should be 2", 2, geneType.get("count").asInt());
        assertEquals(
                "kinase should be first (count 2)",
                "kinase",
                geneType.get("values").get(0).get("value").asText());

        // community: 2 distinct values
        JsonNode community = columns.get("community");
        assertFalse("community should not have error", community.has("error"));
        assertEquals("community count should be 2", 2, community.get("count").asInt());
    }

    // -----------------------------------------------------------------------
    // Batch: one valid column, one missing — partial success
    // -----------------------------------------------------------------------

    @Test
    public void batchColumns_mixedResults_partialSuccessWithErrors() throws Exception {
        stubNetworkAndTables();
        CyColumn geneTypeCol = mockColumn("GeneType", String.class, null);
        when(nodeTable.getColumn("GeneType")).thenReturn(geneTypeCol);
        when(nodeTable.getColumn("Missing")).thenReturn(null);

        CyRow r1 = rowWith("GeneType", String.class, "kinase");
        CyRow r2 = rowWith("GeneType", String.class, "receptor");
        when(nodeTable.getAllRows()).thenReturn(List.of(r1, r2));

        String response =
                callTool("{\"column_names\":[\"GeneType\",\"Missing\"],\"table\":\"node\"}");

        assertFalse("Should not be a tool-level error", response.contains("\"isError\":true"));
        JsonNode columns = parseStructuredContent(response).get("columns");

        // GeneType succeeds
        JsonNode geneType = columns.get("GeneType");
        assertNotNull(geneType);
        assertFalse("GeneType should not have error", geneType.has("error"));
        assertEquals(2, geneType.get("count").asInt());

        // Missing has per-column error, no data fields
        JsonNode missing = columns.get("Missing");
        assertNotNull(missing);
        assertTrue("Missing should have error field", missing.has("error"));
        assertNull("Missing should not have count", missing.get("count"));
        assertNull("Missing should not have values", missing.get("values"));
    }

    // -----------------------------------------------------------------------
    // max_values: clips values list but count reflects true total
    // -----------------------------------------------------------------------

    @Test
    public void maxValues_clipsValuesListButCountReflectsTotal() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("Gene", String.class, null);
        when(nodeTable.getColumn("Gene")).thenReturn(col);

        // Create 60 distinct String values; value "v00" appears 61 times, v01-v59 appear once each
        List<CyRow> rows = new ArrayList<>();
        for (int i = 0; i < 61; i++) {
            rows.add(rowWith("Gene", String.class, "v00"));
        }
        for (int i = 1; i < 60; i++) {
            rows.add(rowWith("Gene", String.class, String.format("v%02d", i)));
        }
        when(nodeTable.getAllRows()).thenReturn(rows);

        String response =
                callTool("{\"column_names\":[\"Gene\"],\"table\":\"node\",\"max_values\":10}");

        assertFalse("Should not be an error", response.contains("\"isError\":true"));
        JsonNode entry = parseStructuredContent(response).get("columns").get("Gene");
        assertNotNull(entry);

        assertEquals("values should be clipped to 10", 10, entry.get("values").size());
        assertEquals("count should reflect true total of 60", 60, entry.get("count").asInt());
        assertEquals(
                "top entry should be v00 (most frequent)",
                "v00",
                entry.get("values").get(0).get("value").asText());
    }

    @Test
    public void maxValues_defaultOf50_appliedWhenParamAbsent() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("Gene", String.class, null);
        when(nodeTable.getColumn("Gene")).thenReturn(col);

        // Create 51 distinct values; "v00" appears 52 times, v01-v50 appear once each
        List<CyRow> rows = new ArrayList<>();
        for (int i = 0; i < 52; i++) {
            rows.add(rowWith("Gene", String.class, "v00"));
        }
        for (int i = 1; i <= 50; i++) {
            rows.add(rowWith("Gene", String.class, String.format("v%02d", i)));
        }
        when(nodeTable.getAllRows()).thenReturn(rows);

        // No max_values param — default of 50 should apply
        String response = callTool("{\"column_names\":[\"Gene\"],\"table\":\"node\"}");

        assertFalse("Should not be an error", response.contains("\"isError\":true"));
        JsonNode entry = parseStructuredContent(response).get("columns").get("Gene");
        assertNotNull(entry);

        assertEquals("values should be clipped to default 50", 50, entry.get("values").size());
        assertEquals("count should reflect true total of 51", 51, entry.get("count").asInt());
    }

    @Test
    public void maxValues_noClipping_whenCountBelowLimit() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("Status", String.class, null);
        when(nodeTable.getColumn("Status")).thenReturn(col);

        CyRow r1 = rowWith("Status", String.class, "active");
        CyRow r2 = rowWith("Status", String.class, "inactive");
        CyRow r3 = rowWith("Status", String.class, "pending");
        when(nodeTable.getAllRows()).thenReturn(List.of(r1, r2, r3));

        String response = callTool("{\"column_names\":[\"Status\"],\"table\":\"node\"}");

        assertFalse("Should not be an error", response.contains("\"isError\":true"));
        JsonNode entry = parseStructuredContent(response).get("columns").get("Status");
        assertNotNull(entry);

        int count = entry.get("count").asInt();
        int valuesSize = entry.get("values").size();
        assertEquals("3 distinct values, all returned", 3, valuesSize);
        assertEquals("count equals values size when no clipping", count, valuesSize);
    }

    // -----------------------------------------------------------------------
    // Numeric coercion — integer vs string max_values
    // -----------------------------------------------------------------------

    @Test
    public void maxValuesAsNumber_limitsResults() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("GeneType", String.class, null);
        when(nodeTable.getColumn("GeneType")).thenReturn(col);
        CyRow r1 = rowWith("GeneType", String.class, "kinase");
        when(nodeTable.getAllRows()).thenReturn(List.of(r1));
        // max_values as JSON integer
        String response =
                callTool("{\"column_names\":[\"GeneType\"],\"table\":\"node\",\"max_values\":5}");
        assertFalse("Should not be an error", response.contains("\"isError\":true"));
    }

    @Test
    public void maxValuesAsString_limitsResults() throws Exception {
        stubNetworkAndTables();
        CyColumn col = mockColumn("GeneType", String.class, null);
        when(nodeTable.getColumn("GeneType")).thenReturn(col);
        CyRow r1 = rowWith("GeneType", String.class, "kinase");
        when(nodeTable.getAllRows()).thenReturn(List.of(r1));
        // max_values as JSON string
        String response =
                callTool(
                        "{\"column_names\":[\"GeneType\"],\"table\":\"node\",\"max_values\":\"5\"}");
        assertFalse("Should not be an error", response.contains("\"isError\":true"));
    }

    // -----------------------------------------------------------------------
    // Schema: input schema has optional max_values integer param
    // -----------------------------------------------------------------------

    @Test
    public void inputSchema_hasOptionalMaxValuesIntegerParam() throws Exception {
        JsonNode schema = MAPPER.readTree(GetColumnDistinctValuesTool.INPUT_SCHEMA);
        assertNotNull(schema);

        JsonNode props = schema.get("properties");
        assertNotNull("properties should exist", props);
        assertNotNull("max_values property should exist", props.get("max_values"));
        assertEquals(
                "max_values should be integer type",
                "integer",
                props.get("max_values").get("type").asText());

        JsonNode required = schema.get("required");
        boolean maxValuesRequired = false;
        if (required != null) {
            for (JsonNode req : required) {
                if ("max_values".equals(req.asText())) {
                    maxValuesRequired = true;
                    break;
                }
            }
        }
        assertFalse("max_values should NOT be required", maxValuesRequired);
    }

    // -----------------------------------------------------------------------
    // Schema: input schema has column_names as array and table enum
    // -----------------------------------------------------------------------

    @Test
    public void inputSchema_hasArrayColumnNamesAndTableEnum() throws Exception {
        JsonNode schema = MAPPER.readTree(GetColumnDistinctValuesTool.INPUT_SCHEMA);
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
        JsonNode schema = MAPPER.readTree(GetColumnDistinctValuesTool.OUTPUT_SCHEMA);
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
        assertEquals(
                "Tool name should be get_column_distinct_values",
                "get_column_distinct_values",
                toolDef.name());
        assertEquals(
                "Tool title should match", "Get Cytoscape Desktop Column Values", toolDef.title());

        String desc = toolDef.description();
        assertNotNull("Description should not be null", desc);
        assertFalse("Description should not be empty", desc.isBlank());
        assertTrue(
                "Description should mention discrete mapping", desc.contains("discrete mapping"));
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
                        + "\"params\":{\"name\":\"get_column_distinct_values\","
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
