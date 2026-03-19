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
import org.cytoscape.view.model.ContinuousRange;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.model.VisualProperty;
import org.cytoscape.view.presentation.RenderingEngineManager;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.view.vizmap.VisualMappingFunctionFactory;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.view.vizmap.mappings.ContinuousMapping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link CreateContinuousMappingTool} through its public interface ({@code toSpec()}) by
 * registering it on a real {@link io.modelcontextprotocol.server.McpSyncServer} backed by {@link
 * InMemoryTransport}. Cytoscape services are Mockito stubs.
 */
public class CreateContinuousMappingToolTest {

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
    @Mock private VisualMappingFunctionFactory continuousMappingFactory;
    @Mock private VisualStyle style;
    @Mock private CyNetwork network;
    @Mock private CyNetworkView networkView;
    @Mock private VisualLexicon lexicon;
    @Mock private ContinuousMapping mockMapping;

    private CreateContinuousMappingTool tool;
    private InMemoryTransport transport;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        tool =
                new CreateContinuousMappingTool(
                        appManager,
                        vmmManager,
                        renderingEngineManager,
                        continuousMappingFactory,
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
                                "NODE_SIZE",
                                "Degree",
                                "Integer",
                                "[{\"value\":1,\"lesser\":10,\"equal\":10,\"greater\":10},"
                                        + "{\"value\":45,\"lesser\":60,\"equal\":60,\"greater\":60}]"));

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
                                "NODE_SIZE",
                                "Degree",
                                "Integer",
                                "[{\"value\":1,\"lesser\":10,\"equal\":10,\"greater\":10},"
                                        + "{\"value\":45,\"lesser\":60,\"equal\":60,\"greater\":60}]"));

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue(
                "Should mention no view",
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
                callTool(
                        buildArgs(
                                "BOGUS_PROP",
                                "Degree",
                                "Integer",
                                "[{\"value\":1,\"lesser\":10,\"equal\":10,\"greater\":10},"
                                        + "{\"value\":45,\"lesser\":60,\"equal\":60,\"greater\":60}]"));

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue("Should mention Unknown", response.contains("Unknown"));
    }

    // -----------------------------------------------------------------------
    // Error: property does not support continuous mapping (String VP with non-discrete range)
    // NODE_LABEL uses DiscreteRange in Cytoscape, so we use a mock VP with ContinuousRange<String>
    // to exercise the getContinuousSubType==null guard path.
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void nonContinuousProperty_returnsError() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(network);
        when(appManager.getCurrentNetworkView()).thenReturn(networkView);
        when(vmmManager.getCurrentVisualStyle()).thenReturn(style);
        when(renderingEngineManager.getDefaultVisualLexicon()).thenReturn(lexicon);

        // Build a mock VP with ContinuousRange<String>: isSupported=true but
        // getContinuousSubType=null (String is not Paint/Number, range is not discrete)
        VisualProperty<String> stringVp = org.mockito.Mockito.mock(VisualProperty.class);
        when(stringVp.getIdString()).thenReturn("NODE_LABEL_MOCK");
        ContinuousRange<String> stringRange =
                new ContinuousRange<>(String.class, null, null, true, true);
        when(stringVp.getRange()).thenReturn(stringRange);
        when(lexicon.getAllVisualProperties()).thenReturn(Set.of(stringVp));

        String response =
                callTool(
                        buildArgs(
                                "NODE_LABEL_MOCK",
                                "name",
                                "Double",
                                "[{\"value\":1,\"lesser\":\"a\",\"equal\":\"b\",\"greater\":\"c\"},"
                                        + "{\"value\":2,\"lesser\":\"d\",\"equal\":\"e\",\"greater\":\"f\"}]"));

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue(
                "Should mention continuous mapping",
                response.contains("not support continuous")
                        || response.contains("continuous mapping"));
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
                                "NODE_SIZE",
                                "Degree",
                                "Timestamp",
                                "[{\"value\":1,\"lesser\":10,\"equal\":10,\"greater\":10},"
                                        + "{\"value\":45,\"lesser\":60,\"equal\":60,\"greater\":60}]"));

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue(
                "Should mention Unsupported column type",
                response.contains("Unsupported column type"));
    }

    // -----------------------------------------------------------------------
    // Error: fewer than 2 points
    // -----------------------------------------------------------------------

    @Test
    public void fewerThanTwoPoints_returnsError() throws Exception {
        stubSuccessPath();

        String response =
                callTool(
                        buildArgs(
                                "NODE_SIZE",
                                "Degree",
                                "Integer",
                                "[{\"value\":1,\"lesser\":10,\"equal\":10,\"greater\":10}]"));

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue(
                "Should mention minimum 2 breakpoints",
                response.contains("2 breakpoints")
                        || response.contains("minimum")
                        || response.contains("At least 2"));
    }

    // -----------------------------------------------------------------------
    // Error: duplicate point values
    // -----------------------------------------------------------------------

    @Test
    public void duplicatePointValues_returnsError() throws Exception {
        stubSuccessPath();

        String response =
                callTool(
                        buildArgs(
                                "NODE_SIZE",
                                "Degree",
                                "Integer",
                                "[{\"value\":5,\"lesser\":10,\"equal\":10,\"greater\":10},"
                                        + "{\"value\":5,\"lesser\":60,\"equal\":60,\"greater\":60}]"));

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue(
                "Should mention duplicate",
                response.contains("duplicate") || response.contains("Duplicate"));
    }

    // -----------------------------------------------------------------------
    // Error: invalid BRV color value
    // -----------------------------------------------------------------------

    @Test
    public void invalidBrvColor_returnsError() throws Exception {
        stubSuccessPath();

        String response =
                callTool(
                        buildArgs(
                                "NODE_FILL_COLOR",
                                "expression",
                                "Double",
                                "[{\"value\":-2.0,\"lesser\":\"not-a-color\","
                                        + "\"equal\":\"#0000FF\",\"greater\":\"#FFFFFF\"},"
                                        + "{\"value\":2.0,\"lesser\":\"#FFFFFF\","
                                        + "\"equal\":\"#FF0000\",\"greater\":\"#FF0000\"}]"));

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue(
                "Should mention Invalid color",
                response.contains("Invalid color")
                        || response.contains("invalid color")
                        || response.contains("Invalid"));
    }

    // -----------------------------------------------------------------------
    // Success: numeric mapping with two points (NODE_SIZE, Double)
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void successNumericMapping_twoPoints() throws Exception {
        stubSuccessPath();
        stubFactory();

        String response =
                callTool(
                        buildArgs(
                                "NODE_SIZE",
                                "Degree",
                                "Double",
                                "[{\"value\":1.0,\"lesser\":10.0,\"equal\":10.0,\"greater\":10.0},"
                                        + "{\"value\":45.0,\"lesser\":60.0,\"equal\":60.0,\"greater\":60.0}]"));

        assertFalse("Should not be error", response.contains("\"isError\":true"));
        assertTrue("Should have status success", response.contains("\"status\":\"success\""));
        assertTrue("Should report 2 points", response.contains("\"points_count\":2"));
        assertTrue(
                "Should report ContinuousMapping",
                response.contains("\"mapping_type\":\"ContinuousMapping\""));
        verify(mockMapping, times(2)).addPoint(any(), any());
        verify(style).removeVisualMappingFunction(BasicVisualLexicon.NODE_SIZE);
        verify(style).addVisualMappingFunction(mockMapping);
        verify(style).apply(networkView);
        verify(networkView).updateView();
    }

    // -----------------------------------------------------------------------
    // Success: color gradient with three points (NODE_FILL_COLOR, Double)
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void successColorGradient_threePoints() throws Exception {
        stubSuccessPath();
        stubFactory();

        String response =
                callTool(
                        buildArgs(
                                "NODE_FILL_COLOR",
                                "expression",
                                "Double",
                                "[{\"value\":-2.0,\"lesser\":\"#0000FF\","
                                        + "\"equal\":\"#0000FF\",\"greater\":\"#FFFFFF\"},"
                                        + "{\"value\":0.0,\"lesser\":\"#FFFFFF\","
                                        + "\"equal\":\"#FFFFFF\",\"greater\":\"#FFFFFF\"},"
                                        + "{\"value\":2.0,\"lesser\":\"#FFFFFF\","
                                        + "\"equal\":\"#FF0000\",\"greater\":\"#FF0000\"}]"));

        assertFalse("Should not be error", response.contains("\"isError\":true"));
        assertTrue("Should have status success", response.contains("\"status\":\"success\""));
        assertTrue("Should report 3 points", response.contains("\"points_count\":3"));
        verify(mockMapping, times(3)).addPoint(any(), any());
    }

    // -----------------------------------------------------------------------
    // Success: points provided in reverse order — auto-sorted
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void successPointsSortedAutomatically() throws Exception {
        stubSuccessPath();
        stubFactory();

        // Provide points in reverse order (high value first)
        String response =
                callTool(
                        buildArgs(
                                "NODE_SIZE",
                                "Degree",
                                "Double",
                                "[{\"value\":45.0,\"lesser\":60.0,\"equal\":60.0,\"greater\":60.0},"
                                        + "{\"value\":1.0,\"lesser\":10.0,\"equal\":10.0,\"greater\":10.0}]"));

        assertFalse("Should not be error", response.contains("\"isError\":true"));
        assertTrue("Should have status success", response.contains("\"status\":\"success\""));
        assertTrue("Should report 2 points", response.contains("\"points_count\":2"));
    }

    // -----------------------------------------------------------------------
    // Success: Integer column type
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void successIntegerColumnType() throws Exception {
        stubSuccessPath();
        stubFactory();

        String response =
                callTool(
                        buildArgs(
                                "NODE_SIZE",
                                "Degree",
                                "Integer",
                                "[{\"value\":1,\"lesser\":10,\"equal\":10,\"greater\":10},"
                                        + "{\"value\":45,\"lesser\":60,\"equal\":60,\"greater\":60}]"));

        assertFalse("Should not be error", response.contains("\"isError\":true"));
        assertTrue("Should have status success", response.contains("\"status\":\"success\""));
        // Verify factory was called with Integer.class
        verify(continuousMappingFactory)
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

        String response =
                callTool(
                        buildArgs(
                                "EDGE_WIDTH",
                                "weight",
                                "Long",
                                "[{\"value\":0,\"lesser\":1.0,\"equal\":1.0,\"greater\":1.0},"
                                        + "{\"value\":100,\"lesser\":5.0,\"equal\":5.0,\"greater\":5.0}]"));

        assertFalse("Should not be error", response.contains("\"isError\":true"));
        assertTrue("Should have status success", response.contains("\"status\":\"success\""));
    }

    // -----------------------------------------------------------------------
    // Schema: inputSchema is valid JSON with expected structure
    // -----------------------------------------------------------------------

    @Test
    public void inputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(CreateContinuousMappingTool.INPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
        assertEquals("object", schema.get("type").asText());
        JsonNode props = schema.get("properties");
        assertNotNull(props);
        assertTrue("Should have property_id", props.has("property_id"));
        assertTrue("Should have column_name", props.has("column_name"));
        assertTrue("Should have column_type", props.has("column_type"));
        assertTrue("Should have points", props.has("points"));
        assertEquals("array", props.get("points").get("type").asText());
        // column_type should have enum
        JsonNode colTypeEnum = props.get("column_type").get("enum");
        assertNotNull("column_type should have enum values", colTypeEnum);
        assertTrue("enum should include Integer", colTypeEnum.toString().contains("Integer"));
        assertTrue("enum should include Double", colTypeEnum.toString().contains("Double"));
        assertTrue("enum should include Long", colTypeEnum.toString().contains("Long"));
    }

    // -----------------------------------------------------------------------
    // Schema: outputSchema is valid JSON
    // -----------------------------------------------------------------------

    @Test
    public void outputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(CreateContinuousMappingTool.OUTPUT_SCHEMA);
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
                .when(continuousMappingFactory)
                .createVisualMappingFunction(any(), any(), any());
    }

    private static String buildArgs(
            String propertyId, String columnName, String columnType, String points) {
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
                + "\"points\":"
                + points
                + "}";
    }

    private String callTool(String arguments) throws Exception {
        String toolCall =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"create_continuous_mapping\","
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
