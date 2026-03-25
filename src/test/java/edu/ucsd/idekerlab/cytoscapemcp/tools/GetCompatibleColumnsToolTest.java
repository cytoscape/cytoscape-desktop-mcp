package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.model.VisualProperty;
import org.cytoscape.view.presentation.RenderingEngineManager;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link GetCompatibleColumnsTool} through its public interface ({@code toSpec()}) by
 * registering it on a real {@link io.modelcontextprotocol.server.McpSyncServer} backed by {@link
 * InMemoryTransport}. Cytoscape services are Mockito stubs.
 */
public class GetCompatibleColumnsToolTest {

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
    @Mock private RenderingEngineManager renderingEngineManager;
    @Mock private VisualLexicon lexicon;
    @Mock private CyNetwork network;
    @Mock private CyNetworkView networkView;
    @Mock private CyTable nodeTable;
    @Mock private CyTable edgeTable;

    private GetCompatibleColumnsTool tool;
    private InMemoryTransport transport;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        tool =
                new GetCompatibleColumnsTool(
                        appManager, renderingEngineManager, new VisualPropertyService());
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

        String response = callTool("{\"property_ids\":[\"NODE_FILL_COLOR\"]}");

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should contain descriptive error message",
                response.contains("No network is currently loaded in Cytoscape Desktop"));
    }

    // -----------------------------------------------------------------------
    // Error: no view loaded
    // -----------------------------------------------------------------------

    @Test
    public void noViewLoaded_returnsError() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(network);
        when(appManager.getCurrentNetworkView()).thenReturn(null);

        String response = callTool("{\"property_ids\":[\"NODE_FILL_COLOR\"]}");

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain descriptive error message", response.contains("no view"));
    }

    // -----------------------------------------------------------------------
    // Error: missing property_ids (empty array)
    // -----------------------------------------------------------------------

    @Test
    public void missingPropertyIds_returnsError() throws Exception {
        String response = callTool("{\"property_ids\":[]}");

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should contain descriptive error message",
                response.contains("property_ids parameter is required"));
    }

    // -----------------------------------------------------------------------
    // Error: unknown property ID
    // -----------------------------------------------------------------------

    @Test
    public void unknownPropertyId_returnsError() throws Exception {
        stubSuccessPath();
        stubDescendants(BasicVisualLexicon.NODE, Collections.emptySet());
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());

        String response = callTool("{\"property_ids\":[\"FAKE_PROPERTY\"]}");

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should contain unknown property message",
                response.contains("Unknown property: FAKE_PROPERTY"));
    }

    // -----------------------------------------------------------------------
    // Error: network-level property (not node or edge descendant)
    // -----------------------------------------------------------------------

    @Test
    public void networkLevelProperty_returnsError() throws Exception {
        stubSuccessPath();
        // NETWORK is not a descendant of NODE or EDGE
        stubDescendants(BasicVisualLexicon.NODE, Collections.emptySet());
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());
        // Still need the VP to be findable in the lexicon
        when(lexicon.getAllVisualProperties())
                .thenReturn(Set.of(BasicVisualLexicon.NETWORK_BACKGROUND_PAINT));

        String response = callTool("{\"property_ids\":[\"NETWORK_BACKGROUND_PAINT\"]}");

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should contain not-applicable message",
                response.contains("Not a node or edge property"));
    }

    // -----------------------------------------------------------------------
    // Success: single node property returns node table
    // -----------------------------------------------------------------------

    @Test
    public void singleNodeProperty_returnsNodeTable() throws Exception {
        stubSuccessPath();
        Set<VisualProperty<?>> nodeDescendants = new HashSet<>();
        nodeDescendants.add(BasicVisualLexicon.NODE_FILL_COLOR);
        stubDescendants(BasicVisualLexicon.NODE, nodeDescendants);
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());
        stubTable(nodeTable, List.of());

        String response = callTool("{\"property_ids\":[\"NODE_FILL_COLOR\"]}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain table: node", response.contains("\"table\":\"node\""));
        assertTrue("Should contain NODE_FILL_COLOR key", response.contains("NODE_FILL_COLOR"));
    }

    // -----------------------------------------------------------------------
    // Success: single edge property returns edge table
    // -----------------------------------------------------------------------

    @Test
    public void singleEdgeProperty_returnsEdgeTable() throws Exception {
        stubSuccessPath();
        stubDescendants(BasicVisualLexicon.NODE, Collections.emptySet());
        Set<VisualProperty<?>> edgeDescendants = new HashSet<>();
        edgeDescendants.add(BasicVisualLexicon.EDGE_WIDTH);
        stubDescendants(BasicVisualLexicon.EDGE, edgeDescendants);
        stubTable(edgeTable, List.of());

        String response = callTool("{\"property_ids\":[\"EDGE_WIDTH\"]}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain table: edge", response.contains("\"table\":\"edge\""));
        assertTrue("Should contain EDGE_WIDTH key", response.contains("EDGE_WIDTH"));
    }

    // -----------------------------------------------------------------------
    // Success: batch node properties returns map with all entries
    // -----------------------------------------------------------------------

    @Test
    public void batchNodeProperties_returnsMapWithAllEntries() throws Exception {
        stubSuccessPath();
        Set<VisualProperty<?>> nodeDescendants = new HashSet<>();
        nodeDescendants.add(BasicVisualLexicon.NODE_FILL_COLOR);
        nodeDescendants.add(BasicVisualLexicon.NODE_SIZE);
        stubDescendants(BasicVisualLexicon.NODE, nodeDescendants);
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());
        stubTable(nodeTable, List.of());

        String response = callTool("{\"property_ids\":[\"NODE_FILL_COLOR\",\"NODE_SIZE\"]}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain NODE_FILL_COLOR key", response.contains("NODE_FILL_COLOR"));
        assertTrue("Should contain NODE_SIZE key", response.contains("NODE_SIZE"));
    }

    // -----------------------------------------------------------------------
    // Success: batch mixed node and edge returns correct tables
    // -----------------------------------------------------------------------

    @Test
    public void batchMixedNodeAndEdge_returnsCorrectTables() throws Exception {
        stubSuccessPath();
        Set<VisualProperty<?>> nodeDescendants = new HashSet<>();
        nodeDescendants.add(BasicVisualLexicon.NODE_FILL_COLOR);
        stubDescendants(BasicVisualLexicon.NODE, nodeDescendants);
        Set<VisualProperty<?>> edgeDescendants = new HashSet<>();
        edgeDescendants.add(BasicVisualLexicon.EDGE_WIDTH);
        stubDescendants(BasicVisualLexicon.EDGE, edgeDescendants);
        stubTable(nodeTable, List.of());
        stubTable(edgeTable, List.of());

        String response = callTool("{\"property_ids\":[\"NODE_FILL_COLOR\",\"EDGE_WIDTH\"]}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        // Both properties should be present
        assertTrue("Should contain NODE_FILL_COLOR", response.contains("NODE_FILL_COLOR"));
        assertTrue("Should contain EDGE_WIDTH", response.contains("EDGE_WIDTH"));
        // Verify table assignment via JSON parsing
        // Response is the second line (index 1) of the full output
        String[] lines = transport.getResponse().split("\n");
        String toolResponseLine = lines[lines.length - 1];
        JsonNode root = MAPPER.readTree(toolResponseLine);
        JsonNode structured = root.at("/result/structuredContent");
        assertEquals("node", structured.at("/properties/NODE_FILL_COLOR/table").asText());
        assertEquals("edge", structured.at("/properties/EDGE_WIDTH/table").asText());
    }

    // -----------------------------------------------------------------------
    // Numeric column supports continuous
    // -----------------------------------------------------------------------

    @Test
    public void numericColumn_supportsContinuous() throws Exception {
        stubSuccessPath();
        Set<VisualProperty<?>> nodeDescendants = new HashSet<>();
        nodeDescendants.add(BasicVisualLexicon.NODE_FILL_COLOR);
        stubDescendants(BasicVisualLexicon.NODE, nodeDescendants);
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());

        CyColumn intCol = mockColumn("Degree", Integer.class, null);
        CyRow row = mock(CyRow.class);
        when(row.get("Degree", Integer.class)).thenReturn(5);
        stubTable(nodeTable, List.of(intCol), List.of(row));

        String response = callTool("{\"property_ids\":[\"NODE_FILL_COLOR\"]}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        // Parse structured response
        JsonNode structured = parseStructuredContent(response);
        JsonNode columns = structured.at("/properties/NODE_FILL_COLOR/columns");
        assertNotNull(columns);
        // Find the Degree column
        JsonNode degreeCol = findColumnByName(columns, "Degree");
        assertNotNull("Should contain Degree column", degreeCol);
        assertTrue(
                "Integer column should support continuous",
                degreeCol.at("/supportsMapping/continuous").asBoolean());
        assertTrue(
                "Integer column should support discrete",
                degreeCol.at("/supportsMapping/discrete").asBoolean());
    }

    // -----------------------------------------------------------------------
    // String column does not support continuous
    // -----------------------------------------------------------------------

    @Test
    public void stringColumn_doesNotSupportContinuous() throws Exception {
        stubSuccessPath();
        Set<VisualProperty<?>> nodeDescendants = new HashSet<>();
        nodeDescendants.add(BasicVisualLexicon.NODE_FILL_COLOR);
        stubDescendants(BasicVisualLexicon.NODE, nodeDescendants);
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());

        CyColumn strCol = mockColumn("GeneType", String.class, null);
        CyRow row = mock(CyRow.class);
        when(row.get("GeneType", String.class)).thenReturn("kinase");
        stubTable(nodeTable, List.of(strCol), List.of(row));

        String response = callTool("{\"property_ids\":[\"NODE_FILL_COLOR\"]}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        JsonNode structured = parseStructuredContent(response);
        JsonNode columns = structured.at("/properties/NODE_FILL_COLOR/columns");
        JsonNode geneTypeCol = findColumnByName(columns, "GeneType");
        assertNotNull("Should contain GeneType column", geneTypeCol);
        assertFalse(
                "String column should not support continuous",
                geneTypeCol.at("/supportsMapping/continuous").asBoolean());
        assertTrue(
                "String column should support discrete",
                geneTypeCol.at("/supportsMapping/discrete").asBoolean());
    }

    // -----------------------------------------------------------------------
    // String VP enables passthrough
    // -----------------------------------------------------------------------

    @Test
    public void stringVP_enablesPassthrough() throws Exception {
        stubSuccessPath();
        Set<VisualProperty<?>> nodeDescendants = new HashSet<>();
        nodeDescendants.add(BasicVisualLexicon.NODE_LABEL);
        stubDescendants(BasicVisualLexicon.NODE, nodeDescendants);
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());

        CyColumn strCol = mockColumn("name", String.class, null);
        CyRow row = mock(CyRow.class);
        when(row.get("name", String.class)).thenReturn("TP53");
        stubTable(nodeTable, List.of(strCol), List.of(row));

        String response = callTool("{\"property_ids\":[\"NODE_LABEL\"]}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        JsonNode structured = parseStructuredContent(response);
        JsonNode columns = structured.at("/properties/NODE_LABEL/columns");
        JsonNode nameCol = findColumnByName(columns, "name");
        assertNotNull("Should contain name column", nameCol);
        assertTrue(
                "String VP should enable passthrough",
                nameCol.at("/supportsMapping/passthrough").asBoolean());
    }

    // -----------------------------------------------------------------------
    // Non-string VP disables passthrough
    // -----------------------------------------------------------------------

    @Test
    public void nonStringVP_disablesPassthrough() throws Exception {
        stubSuccessPath();
        Set<VisualProperty<?>> nodeDescendants = new HashSet<>();
        nodeDescendants.add(BasicVisualLexicon.NODE_FILL_COLOR);
        stubDescendants(BasicVisualLexicon.NODE, nodeDescendants);
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());

        CyColumn strCol = mockColumn("GeneType", String.class, null);
        CyRow row = mock(CyRow.class);
        when(row.get("GeneType", String.class)).thenReturn("kinase");
        stubTable(nodeTable, List.of(strCol), List.of(row));

        String response = callTool("{\"property_ids\":[\"NODE_FILL_COLOR\"]}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        JsonNode structured = parseStructuredContent(response);
        JsonNode columns = structured.at("/properties/NODE_FILL_COLOR/columns");
        JsonNode geneTypeCol = findColumnByName(columns, "GeneType");
        assertNotNull("Should contain GeneType column", geneTypeCol);
        assertFalse(
                "Paint VP should disable passthrough",
                geneTypeCol.at("/supportsMapping/passthrough").asBoolean());
    }

    // -----------------------------------------------------------------------
    // Excluded columns not in response
    // -----------------------------------------------------------------------

    @Test
    public void excludedColumns_notInResponse() throws Exception {
        stubSuccessPath();
        Set<VisualProperty<?>> nodeDescendants = new HashSet<>();
        nodeDescendants.add(BasicVisualLexicon.NODE_FILL_COLOR);
        stubDescendants(BasicVisualLexicon.NODE, nodeDescendants);
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());

        CyColumn suidCol = mockColumn("SUID", Long.class, null);
        CyColumn selectedCol = mockColumn("selected", Boolean.class, null);
        CyColumn nameCol = mockColumn("name", String.class, null);
        CyRow row = mock(CyRow.class);
        when(row.get("name", String.class)).thenReturn("TP53");
        stubTable(nodeTable, List.of(suidCol, selectedCol, nameCol), List.of(row));

        String response = callTool("{\"property_ids\":[\"NODE_FILL_COLOR\"]}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        JsonNode structured = parseStructuredContent(response);
        JsonNode columns = structured.at("/properties/NODE_FILL_COLOR/columns");
        assertNotNull(columns);
        assertEquals("Should only have name column", 1, columns.size());
        assertEquals("name", columns.get(0).get("name").asText());
    }

    // -----------------------------------------------------------------------
    // List-type column excluded
    // -----------------------------------------------------------------------

    @Test
    public void listTypeColumn_excluded() throws Exception {
        stubSuccessPath();
        Set<VisualProperty<?>> nodeDescendants = new HashSet<>();
        nodeDescendants.add(BasicVisualLexicon.NODE_FILL_COLOR);
        stubDescendants(BasicVisualLexicon.NODE, nodeDescendants);
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());

        CyColumn listCol = mockColumn("Tags", List.class, String.class);
        CyColumn nameCol = mockColumn("name", String.class, null);
        CyRow row = mock(CyRow.class);
        when(row.get("name", String.class)).thenReturn("TP53");
        stubTable(nodeTable, List.of(listCol, nameCol), List.of(row));

        String response = callTool("{\"property_ids\":[\"NODE_FILL_COLOR\"]}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        JsonNode structured = parseStructuredContent(response);
        JsonNode columns = structured.at("/properties/NODE_FILL_COLOR/columns");
        assertEquals("Should only have name column (list excluded)", 1, columns.size());
        assertEquals("name", columns.get(0).get("name").asText());
    }

    // -----------------------------------------------------------------------
    // Empty table returns empty columns list
    // -----------------------------------------------------------------------

    @Test
    public void emptyTable_returnsEmptyColumnsList() throws Exception {
        stubSuccessPath();
        Set<VisualProperty<?>> nodeDescendants = new HashSet<>();
        nodeDescendants.add(BasicVisualLexicon.NODE_FILL_COLOR);
        stubDescendants(BasicVisualLexicon.NODE, nodeDescendants);
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());

        // Only SUID and selected — no user columns
        CyColumn suidCol = mockColumn("SUID", Long.class, null);
        CyColumn selectedCol = mockColumn("selected", Boolean.class, null);
        stubTable(nodeTable, List.of(suidCol, selectedCol));

        String response = callTool("{\"property_ids\":[\"NODE_FILL_COLOR\"]}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        JsonNode structured = parseStructuredContent(response);
        JsonNode columns = structured.at("/properties/NODE_FILL_COLOR/columns");
        assertNotNull(columns);
        assertTrue("Columns array should be empty", columns.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Input schema has required property_ids
    // -----------------------------------------------------------------------

    @Test
    public void inputSchema_hasRequiredPropertyIds() throws Exception {
        JsonNode schema = MAPPER.readTree(GetCompatibleColumnsTool.INPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
        assertEquals("object", schema.get("type").asText());

        JsonNode required = schema.get("required");
        assertNotNull(required);
        assertTrue(required.isArray());
        assertEquals(1, required.size());
        assertEquals("property_ids", required.get(0).asText());

        JsonNode props = schema.get("properties");
        assertNotNull(props);
        JsonNode propertyIds = props.get("property_ids");
        assertNotNull(propertyIds);
        assertEquals("array", propertyIds.get("type").asText());

        JsonNode items = propertyIds.get("items");
        assertNotNull(items);
        assertEquals("string", items.get("type").asText());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void stubSuccessPath() {
        when(appManager.getCurrentNetwork()).thenReturn(network);
        when(appManager.getCurrentNetworkView()).thenReturn(networkView);
        when(renderingEngineManager.getDefaultVisualLexicon()).thenReturn(lexicon);
        when(network.getDefaultNodeTable()).thenReturn(nodeTable);
        when(network.getDefaultEdgeTable()).thenReturn(edgeTable);
        // Default: include BasicVisualLexicon VPs in getAllVisualProperties
        Set<VisualProperty<?>> allVPs = new HashSet<>();
        allVPs.add(BasicVisualLexicon.NODE_FILL_COLOR);
        allVPs.add(BasicVisualLexicon.NODE_SIZE);
        allVPs.add(BasicVisualLexicon.NODE_LABEL);
        allVPs.add(BasicVisualLexicon.NODE_SHAPE);
        allVPs.add(BasicVisualLexicon.EDGE_WIDTH);
        allVPs.add(BasicVisualLexicon.EDGE_STROKE_UNSELECTED_PAINT);
        allVPs.add(BasicVisualLexicon.NETWORK_BACKGROUND_PAINT);
        when(lexicon.getAllVisualProperties()).thenReturn(allVPs);
    }

    @SuppressWarnings("unchecked")
    private void stubDescendants(VisualProperty<?> root, Set<VisualProperty<?>> descendants) {
        when(lexicon.getAllDescendants(root)).thenReturn((Set) descendants);
    }

    private void stubTable(CyTable table, List<CyColumn> columns) {
        stubTable(table, columns, List.of());
    }

    private void stubTable(CyTable table, List<CyColumn> columns, List<CyRow> rows) {
        // getColumns() returns Collection<CyColumn>
        when(table.getColumns()).thenReturn(new ArrayList<>(columns));
        when(table.getAllRows()).thenReturn(rows);
    }

    private CyColumn mockColumn(String name, Class<?> type, Class<?> listElementType) {
        CyColumn col = mock(CyColumn.class);
        when(col.getName()).thenReturn(name);
        when(col.getType()).thenReturn((Class) type);
        when(col.getListElementType()).thenReturn((Class) listElementType);
        return col;
    }

    private JsonNode parseStructuredContent(String fullResponse) throws Exception {
        String[] lines = fullResponse.split("\n");
        String toolResponseLine = lines[lines.length - 1];
        JsonNode root = MAPPER.readTree(toolResponseLine);
        return root.at("/result/structuredContent");
    }

    private JsonNode findColumnByName(JsonNode columns, String name) {
        for (JsonNode col : columns) {
            if (name.equals(col.get("name").asText())) {
                return col;
            }
        }
        return null;
    }

    private String callTool(String arguments) throws Exception {
        String toolCall =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"get_compatible_columns\","
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
