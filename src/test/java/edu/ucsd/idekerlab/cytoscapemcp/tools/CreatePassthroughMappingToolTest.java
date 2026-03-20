package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.Collections;
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
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyTable;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.model.VisualProperty;
import org.cytoscape.view.presentation.RenderingEngineManager;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.view.vizmap.VisualMappingFunctionFactory;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.view.vizmap.mappings.PassthroughMapping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link CreatePassthroughMappingTool} through its public interface ({@code toSpec()}) by
 * registering it on a real {@link io.modelcontextprotocol.server.McpSyncServer} backed by {@link
 * InMemoryTransport}. Cytoscape services are Mockito stubs.
 */
public class CreatePassthroughMappingToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Real VP instances known to findPropertyById for these tests. */
    @SuppressWarnings("unchecked")
    private static final Set<VisualProperty<?>> ALL_TEST_PROPS =
            Set.of(
                    BasicVisualLexicon.NODE_FILL_COLOR,
                    BasicVisualLexicon.NODE_SIZE,
                    BasicVisualLexicon.NODE_SHAPE,
                    BasicVisualLexicon.NODE_LABEL,
                    BasicVisualLexicon.EDGE_WIDTH,
                    BasicVisualLexicon.EDGE_LINE_TYPE);

    private static final String INIT_REQUEST =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\","
                    + "\"params\":{\"protocolVersion\":\"2025-03-26\","
                    + "\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";

    private static final String INITIALIZED_NOTIFICATION =
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

    // -- Mocks -----------------------------------------------------------------

    @Mock private CyApplicationManager appManager;
    @Mock private VisualMappingManager vmmManager;
    @Mock private RenderingEngineManager renderingEngineManager;
    @Mock private VisualMappingFunctionFactory passthroughMappingFactory;
    @Mock private VisualStyle style;
    @Mock private CyNetwork network;
    @Mock private CyNetworkView networkView;
    @Mock private VisualLexicon lexicon;
    @Mock private PassthroughMapping mockMapping;
    @Mock private CyTable nodeTable;

    private CreatePassthroughMappingTool tool;
    private InMemoryTransport transport;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        tool =
                new CreatePassthroughMappingTool(
                        appManager,
                        vmmManager,
                        renderingEngineManager,
                        passthroughMappingFactory,
                        new VisualPropertyService());
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

        String response = callTool(buildArgs("NODE_LABEL", "name", "String"));

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue("Should mention No network", response.contains("No network"));
    }

    // -----------------------------------------------------------------------
    // Error: no view loaded
    // -----------------------------------------------------------------------

    @Test
    public void noViewLoaded_returnsError() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(network);
        when(appManager.getCurrentNetworkView()).thenReturn(null);

        String response = callTool(buildArgs("NODE_LABEL", "name", "String"));

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue(
                "Should mention view",
                response.contains("No network view")
                        || response.contains("no view")
                        || response.contains("view"));
    }

    // -----------------------------------------------------------------------
    // Error: unknown property ID
    // -----------------------------------------------------------------------

    @Test
    public void unknownPropertyId_returnsError() throws Exception {
        stubSuccessPath();

        String response = callTool(buildArgs("BOGUS_PROP", "name", "String"));

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue("Should mention Unknown", response.contains("Unknown"));
    }

    // -----------------------------------------------------------------------
    // Error: invalid column type
    // -----------------------------------------------------------------------

    @Test
    public void invalidColumnType_returnsError() throws Exception {
        stubSuccessPath();

        String response = callTool(buildArgs("NODE_LABEL", "name", "Timestamp"));

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue(
                "Should mention Unsupported column type",
                response.contains("Unsupported column type"));
    }

    // -----------------------------------------------------------------------
    // Error: column does not exist in the network table
    // -----------------------------------------------------------------------

    @Test
    public void columnNotFound_returnsError() throws Exception {
        stubSuccessPath();
        // Stub getAllDescendants so getTableName resolves NODE_LABEL → "node"
        when(lexicon.getAllDescendants(BasicVisualLexicon.NODE))
                .thenReturn(Set.of(BasicVisualLexicon.NODE_LABEL));
        when(lexicon.getAllDescendants(BasicVisualLexicon.EDGE)).thenReturn(Collections.emptySet());
        when(network.getDefaultNodeTable()).thenReturn(nodeTable);
        when(nodeTable.getColumn("ghost_column")).thenReturn(null);

        String response = callTool(buildArgs("NODE_LABEL", "ghost_column", "String"));

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue("Should mention column name", response.contains("ghost_column"));
        assertTrue(
                "Should mention node table",
                response.contains("node table") || response.contains("node"));
    }

    // -----------------------------------------------------------------------
    // Success: String column, NODE_LABEL
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void successStringColumn_nodeLabel() throws Exception {
        stubSuccessPath();
        stubFactory();

        String response = callTool(buildArgs("NODE_LABEL", "name", "String"));

        assertFalse("Should not be error", response.contains("\"isError\":true"));
        assertTrue("Should have status success", response.contains("\"status\":\"success\""));
        assertTrue(
                "Should report PassthroughMapping",
                response.contains("\"mapping_type\":\"PassthroughMapping\""));
        verify(passthroughMappingFactory)
                .createVisualMappingFunction(any(), any(), any(VisualProperty.class));
        verify(style).removeVisualMappingFunction(BasicVisualLexicon.NODE_LABEL);
        verify(style).addVisualMappingFunction(mockMapping);
        verify(style).apply(networkView);
        verify(networkView).updateView();
    }

    // -----------------------------------------------------------------------
    // Success: Integer column type
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void successIntegerColumnType() throws Exception {
        stubSuccessPath();
        stubFactory();

        String response = callTool(buildArgs("NODE_LABEL", "score", "Integer"));

        assertFalse("Should not be error", response.contains("\"isError\":true"));
        verify(passthroughMappingFactory)
                .createVisualMappingFunction(
                        any(String.class), any(Class.class), any(VisualProperty.class));
    }

    // -----------------------------------------------------------------------
    // Success: Long column type
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void successLongColumnType() throws Exception {
        stubSuccessPath();
        stubFactory();

        String response = callTool(buildArgs("EDGE_WIDTH", "weight", "Long"));

        assertFalse("Should not be error", response.contains("\"isError\":true"));
    }

    // -----------------------------------------------------------------------
    // Success: Double column type
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void successDoubleColumnType() throws Exception {
        stubSuccessPath();
        stubFactory();

        String response = callTool(buildArgs("NODE_SIZE", "expression", "Double"));

        assertFalse("Should not be error", response.contains("\"isError\":true"));
    }

    // -----------------------------------------------------------------------
    // Success: Boolean column type
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void successBooleanColumnType() throws Exception {
        stubSuccessPath();
        stubFactory();

        String response = callTool(buildArgs("NODE_LABEL", "isHub", "Boolean"));

        assertFalse("Should not be error", response.contains("\"isError\":true"));
    }

    // -----------------------------------------------------------------------
    // Schema: inputSchema is valid JSON with expected structure
    // -----------------------------------------------------------------------

    @Test
    public void inputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(CreatePassthroughMappingTool.INPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
        assertEquals("object", schema.get("type").asText());
        JsonNode props = schema.get("properties");
        assertNotNull(props);
        assertTrue("Should have property_id", props.has("property_id"));
        assertTrue("Should have column_name", props.has("column_name"));
        assertTrue("Should have column_type", props.has("column_type"));
        assertFalse("Should NOT have entries", props.has("entries"));
        assertFalse("Should NOT have points", props.has("points"));
        // column_type enum should have all 5 values
        JsonNode colTypeEnum = props.get("column_type").get("enum");
        assertNotNull("column_type should have enum values", colTypeEnum);
        String enumStr = colTypeEnum.toString();
        assertTrue("enum should include Integer", enumStr.contains("Integer"));
        assertTrue("enum should include Long", enumStr.contains("Long"));
        assertTrue("enum should include Double", enumStr.contains("Double"));
        assertTrue("enum should include String", enumStr.contains("String"));
        assertTrue("enum should include Boolean", enumStr.contains("Boolean"));
    }

    // -----------------------------------------------------------------------
    // Schema: outputSchema is valid JSON with expected structure
    // -----------------------------------------------------------------------

    @Test
    public void outputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(CreatePassthroughMappingTool.OUTPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
        String schemaStr = schema.toString();
        assertTrue("Should have mapping_type", schemaStr.contains("mapping_type"));
        assertFalse("Should NOT have entries_count", schemaStr.contains("entries_count"));
        assertFalse("Should NOT have points_count", schemaStr.contains("points_count"));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void stubSuccessPath() {
        when(appManager.getCurrentNetwork()).thenReturn(network);
        when(appManager.getCurrentNetworkView()).thenReturn(networkView);
        when(vmmManager.getCurrentVisualStyle()).thenReturn(style);
        when(renderingEngineManager.getDefaultVisualLexicon()).thenReturn(lexicon);
        when(lexicon.getAllVisualProperties()).thenReturn(ALL_TEST_PROPS);
    }

    @SuppressWarnings("unchecked")
    private void stubFactory() {
        doReturn(mockMapping)
                .when(passthroughMappingFactory)
                .createVisualMappingFunction(any(), any(), any());
    }

    private static String buildArgs(String propertyId, String columnName, String columnType) {
        return "{"
                + "\"property_id\":\""
                + propertyId
                + "\","
                + "\"column_name\":\""
                + columnName
                + "\","
                + "\"column_type\":\""
                + columnType
                + "\"}";
    }

    private String callTool(String arguments) throws Exception {
        String toolCall =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"create_passthrough_mapping\","
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
