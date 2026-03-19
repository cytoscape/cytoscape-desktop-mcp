package edu.ucsd.idekerlab.cytoscapemcp.tools;

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
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.model.VisualProperty;
import org.cytoscape.view.presentation.RenderingEngineManager;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.view.vizmap.VisualMappingFunctionFactory;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.view.vizmap.mappings.DiscreteMapping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link CreateDiscreteMappingTool} through its public interface ({@code toSpec()}) by
 * registering it on a real {@link io.modelcontextprotocol.server.McpSyncServer} backed by {@link
 * InMemoryTransport}. Cytoscape services are Mockito stubs.
 */
public class CreateDiscreteMappingToolTest {

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
    @Mock private VisualMappingFunctionFactory discreteMappingFactory;
    @Mock private VisualStyle style;
    @Mock private CyNetwork network;
    @Mock private CyNetworkView networkView;
    @Mock private VisualLexicon lexicon;
    @Mock private DiscreteMapping mockMapping;

    private CreateDiscreteMappingTool tool;
    private InMemoryTransport transport;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        tool =
                new CreateDiscreteMappingTool(
                        appManager,
                        vmmManager,
                        renderingEngineManager,
                        discreteMappingFactory,
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

        String response =
                callTool(
                        buildArgs(
                                "NODE_FILL_COLOR",
                                "GeneType",
                                "String",
                                "{\"kinase\":\"#FF0000\",\"receptor\":\"#00AA00\"}"));

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

        String response =
                callTool(
                        buildArgs(
                                "NODE_FILL_COLOR",
                                "GeneType",
                                "String",
                                "{\"kinase\":\"#FF0000\",\"receptor\":\"#00AA00\"}"));

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

        String response =
                callTool(buildArgs("BOGUS_PROP", "GeneType", "String", "{\"kinase\":\"#FF0000\"}"));

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue("Should mention Unknown", response.contains("Unknown"));
    }

    // -----------------------------------------------------------------------
    // Error: invalid column type
    // -----------------------------------------------------------------------

    @Test
    public void invalidColumnType_returnsError() throws Exception {
        stubSuccessPath();

        String response =
                callTool(
                        buildArgs(
                                "NODE_FILL_COLOR",
                                "GeneType",
                                "Timestamp",
                                "{\"kinase\":\"#FF0000\"}"));

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue(
                "Should mention Unsupported column type",
                response.contains("Unsupported column type"));
    }

    // -----------------------------------------------------------------------
    // Error: entries is a JSON array (not object)
    // -----------------------------------------------------------------------

    @Test
    public void invalidEntriesType_returnsError() throws Exception {
        stubSuccessPath();

        String rawJson =
                "{"
                        + "\"property_id\":\"NODE_FILL_COLOR\","
                        + "\"column_name\":\"GeneType\","
                        + "\"column_type\":\"String\","
                        + "\"entries\":[\"#FF0000\",\"#00AA00\"]"
                        + "}";

        String response = callToolRaw(rawJson);

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue(
                "Should mention invalid entries",
                response.contains("invalid 'entries'") || response.contains("entries"));
    }

    // -----------------------------------------------------------------------
    // Error: invalid property value
    // -----------------------------------------------------------------------

    @Test
    public void invalidPropertyValue_returnsError() throws Exception {
        stubSuccessPath();

        String response =
                callTool(
                        buildArgs(
                                "NODE_FILL_COLOR",
                                "GeneType",
                                "String",
                                "{\"kinase\":\"not-a-color\"}"));

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue(
                "Should mention Invalid color",
                response.contains("Invalid color")
                        || response.contains("invalid color")
                        || response.contains("Invalid"));
    }

    // -----------------------------------------------------------------------
    // Error: invalid key for Integer column
    // -----------------------------------------------------------------------

    @Test
    public void invalidKeyForIntegerColumn_returnsError() throws Exception {
        stubSuccessPath();

        String response = callTool(buildArgs("NODE_SIZE", "category", "Integer", "{\"abc\":10.0}"));

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue(
                "Should mention cannot parse",
                response.contains("cannot be parsed")
                        || response.contains("cannot parse")
                        || response.contains("abc"));
    }

    // -----------------------------------------------------------------------
    // Success: String column, color entries (NODE_FILL_COLOR)
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void successStringColumn_colorEntries() throws Exception {
        stubSuccessPath();
        stubFactory();

        String response =
                callTool(
                        buildArgs(
                                "NODE_FILL_COLOR",
                                "GeneType",
                                "String",
                                "{\"kinase\":\"#FF0000\",\"receptor\":\"#00AA00\"}"));

        assertFalse("Should not be error", response.contains("\"isError\":true"));
        assertTrue("Should have status success", response.contains("\"status\":\"success\""));
        assertTrue("Should report 2 entries", response.contains("\"entries_count\":2"));
        assertTrue(
                "Should report DiscreteMapping",
                response.contains("\"mapping_type\":\"DiscreteMapping\""));
        verify(mockMapping).putAll(any(java.util.Map.class));
        verify(style).removeVisualMappingFunction(BasicVisualLexicon.NODE_FILL_COLOR);
        verify(style).addVisualMappingFunction(mockMapping);
        verify(style).apply(networkView);
        verify(networkView).updateView();
    }

    // -----------------------------------------------------------------------
    // Success: Integer column, size entries
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void successIntegerColumn_sizeEntries() throws Exception {
        stubSuccessPath();
        stubFactory();

        String response =
                callTool(buildArgs("NODE_SIZE", "category", "Integer", "{\"1\":10,\"45\":60}"));

        assertFalse("Should not be error", response.contains("\"isError\":true"));
        assertTrue("Should report 2 entries", response.contains("\"entries_count\":2"));
        verify(discreteMappingFactory)
                .createVisualMappingFunction(
                        any(String.class), any(Class.class), any(VisualProperty.class));
    }

    // -----------------------------------------------------------------------
    // Success: String column, shape entries (NODE_SHAPE)
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void successStringColumn_shapeEntries() throws Exception {
        stubSuccessPath();
        stubFactory();

        String response =
                callTool(
                        buildArgs(
                                "NODE_SHAPE",
                                "type",
                                "String",
                                "{\"kinase\":\"Ellipse\",\"TF\":\"Diamond\"}"));

        assertFalse("Should not be error", response.contains("\"isError\":true"));
        assertTrue("Should report 2 entries", response.contains("\"entries_count\":2"));
    }

    // -----------------------------------------------------------------------
    // Error: empty entries map
    // -----------------------------------------------------------------------

    @Test
    public void emptyEntries_returnsError() throws Exception {
        stubSuccessPath();

        String response = callTool(buildArgs("NODE_SIZE", "category", "Integer", "{}"));

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue(
                "Should mention at least 1 entry",
                response.contains("1 entry")
                        || response.contains("empty")
                        || response.contains("At least"));
    }

    // -----------------------------------------------------------------------
    // Error: too many entries (>1000)
    // -----------------------------------------------------------------------

    @Test
    public void tooManyEntries_returnsError() throws Exception {
        stubSuccessPath();

        // Build 1001 entries programmatically
        StringBuilder entries = new StringBuilder("{");
        for (int i = 0; i < 1001; i++) {
            if (i > 0) entries.append(",");
            entries.append("\"key").append(i).append("\":\"#FF0000\"");
        }
        entries.append("}");

        String response =
                callTool(buildArgs("NODE_FILL_COLOR", "category", "String", entries.toString()));

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue(
                "Should mention too many entries or 1000",
                response.contains("Too many entries") || response.contains("1000"));
    }

    // -----------------------------------------------------------------------
    // Success: Long column type
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void successLongColumnType() throws Exception {
        stubSuccessPath();
        stubFactory();

        String response =
                callTool(buildArgs("EDGE_WIDTH", "weight", "Long", "{\"0\":1.0,\"100\":5.0}"));

        assertFalse("Should not be error", response.contains("\"isError\":true"));
        assertTrue("Should report 2 entries", response.contains("\"entries_count\":2"));
    }

    // -----------------------------------------------------------------------
    // Success: Boolean column type
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void successBooleanColumnType() throws Exception {
        stubSuccessPath();
        stubFactory();

        String response =
                callTool(
                        buildArgs(
                                "NODE_FILL_COLOR",
                                "isHub",
                                "Boolean",
                                "{\"true\":\"#FF0000\",\"false\":\"#0000FF\"}"));

        assertFalse("Should not be error", response.contains("\"isError\":true"));
        assertTrue("Should report 2 entries", response.contains("\"entries_count\":2"));
    }

    // -----------------------------------------------------------------------
    // Schema: inputSchema is valid JSON with expected structure
    // -----------------------------------------------------------------------

    @Test
    public void inputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(CreateDiscreteMappingTool.INPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
        assertEquals("object", schema.get("type").asText());
        JsonNode props = schema.get("properties");
        assertNotNull(props);
        assertTrue("Should have property_id", props.has("property_id"));
        assertTrue("Should have column_name", props.has("column_name"));
        assertTrue("Should have column_type", props.has("column_type"));
        assertTrue("Should have entries", props.has("entries"));
        // column_type enum should have all 5 values including String and Boolean
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
    // Schema: outputSchema is valid JSON
    // -----------------------------------------------------------------------

    @Test
    public void outputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(CreateDiscreteMappingTool.OUTPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
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
                .when(discreteMappingFactory)
                .createVisualMappingFunction(any(), any(), any());
    }

    private static String buildArgs(
            String propertyId, String columnName, String columnType, String entries) {
        return "{"
                + "\"property_id\":\""
                + propertyId
                + "\","
                + "\"column_name\":\""
                + columnName
                + "\","
                + "\"column_type\":\""
                + columnType
                + "\","
                + "\"entries\":"
                + entries
                + "}";
    }

    private String callTool(String arguments) throws Exception {
        return callToolRaw(arguments);
    }

    private String callToolRaw(String arguments) throws Exception {
        String toolCall =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"create_discrete_mapping\","
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
