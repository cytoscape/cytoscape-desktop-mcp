package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.awt.Color;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.cytoscape.event.CyEventHelper;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.model.VisualProperty;
import org.cytoscape.view.presentation.RenderingEngineManager;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.view.vizmap.VisualMappingFunction;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.view.vizmap.mappings.BoundaryRangeValues;
import org.cytoscape.view.vizmap.mappings.ContinuousMapping;
import org.cytoscape.view.vizmap.mappings.ContinuousMappingPoint;
import org.cytoscape.view.vizmap.mappings.DiscreteMapping;
import org.cytoscape.view.vizmap.mappings.PassthroughMapping;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link GetMappablePropertiesTool} through its public interface ({@code toSpec()}) by
 * registering it on a real {@link io.modelcontextprotocol.server.McpSyncServer} backed by {@link
 * InMemoryTransport}. Cytoscape services are Mockito stubs.
 */
public class GetMappablePropertiesToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // --- JSON-RPC protocol messages ----------------------------------------

    private static final String INIT_REQUEST =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\","
                    + "\"params\":{\"protocolVersion\":\"2025-03-26\","
                    + "\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";

    private static final String INITIALIZED_NOTIFICATION =
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

    private static final String TOOL_CALL =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                    + "\"params\":{\"name\":\"get_mappable_properties\","
                    + "\"arguments\":{}}}";

    // --- Mocks -------------------------------------------------------------

    @Mock private CyApplicationManager appManager;
    @Mock private VisualMappingManager vmmManager;
    @Mock private RenderingEngineManager renderingEngineManager;
    @Mock private VisualLexicon lexicon;
    @Mock private VisualStyle style;
    @Mock private CyNetwork network;
    @Mock private CyNetworkView networkView;

    private GetMappablePropertiesTool tool;
    private InMemoryTransport transport;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        tool =
                new GetMappablePropertiesTool(
                        appManager,
                        vmmManager,
                        renderingEngineManager,
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

        String response = callTool();

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

        String response = callTool();

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain descriptive error message", response.contains("no view"));
    }

    // -----------------------------------------------------------------------
    // Success: properties with no mappings
    // -----------------------------------------------------------------------

    @Test
    public void propertiesWithNoMappings_omitsCurrentMapping() throws Exception {
        stubSuccessPath();

        Set<VisualProperty<?>> nodeDescendants = new HashSet<>();
        nodeDescendants.add(BasicVisualLexicon.NODE_FILL_COLOR);
        nodeDescendants.add(BasicVisualLexicon.NODE_SIZE);
        stubDescendants(BasicVisualLexicon.NODE, nodeDescendants);
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());

        // No mappings applied
        when(style.getVisualMappingFunction(any())).thenReturn(null);

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain style_name", response.contains("style_name"));
        assertTrue("Should contain Test Style", response.contains("Test Style"));
        assertFalse(
                "Should not contain currentMapping when no mapping applied",
                response.contains("currentMapping"));
    }

    // -----------------------------------------------------------------------
    // Success: property with continuous mapping
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void propertyWithContinuousMapping_showsMappingInfo() throws Exception {
        stubSuccessPath();

        Set<VisualProperty<?>> nodeDescendants = new HashSet<>();
        nodeDescendants.add(BasicVisualLexicon.NODE_SIZE);
        stubDescendants(BasicVisualLexicon.NODE, nodeDescendants);
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());

        // Mock a ContinuousMapping on NODE_SIZE with real ContinuousMappingPoint instances
        // (ContinuousMappingPoint is final — cannot be mocked)
        ContinuousMapping<Integer, Double> cm = mock(ContinuousMapping.class);
        when(cm.getMappingColumnName()).thenReturn("Degree");
        when(cm.getMappingColumnType()).thenReturn(Integer.class);

        CyEventHelper eventHelper = mock(CyEventHelper.class);
        ContinuousMappingPoint<Integer, Double> point1 =
                new ContinuousMappingPoint<>(
                        1, new BoundaryRangeValues<>(10.0, 10.0, 10.0), cm, eventHelper);
        ContinuousMappingPoint<Integer, Double> point2 =
                new ContinuousMappingPoint<>(
                        45, new BoundaryRangeValues<>(50.0, 50.0, 50.0), cm, eventHelper);

        when(cm.getAllPoints()).thenReturn(List.of(point1, point2));

        when(style.getVisualMappingFunction(BasicVisualLexicon.NODE_SIZE))
                .thenReturn((VisualMappingFunction) cm);

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain ContinuousMapping type", response.contains("ContinuousMapping"));
        assertTrue("Should contain column name", response.contains("Degree"));
        // Summary should show the range: "Degree → 10.0–50.0"
        assertTrue("Should contain first breakpoint value", response.contains("10.0"));
        assertTrue("Should contain last breakpoint value", response.contains("50.0"));
    }

    // -----------------------------------------------------------------------
    // Success: property with discrete mapping
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void propertyWithDiscreteMapping_showsMappingInfo() throws Exception {
        stubSuccessPath();

        Set<VisualProperty<?>> nodeDescendants = new HashSet<>();
        nodeDescendants.add(BasicVisualLexicon.NODE_FILL_COLOR);
        stubDescendants(BasicVisualLexicon.NODE, nodeDescendants);
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());

        // Mock a DiscreteMapping on NODE_FILL_COLOR
        DiscreteMapping<String, Color> dm = mock(DiscreteMapping.class);
        when(dm.getMappingColumnName()).thenReturn("GeneType");
        when(dm.getMappingColumnType()).thenReturn(String.class);
        when(dm.getAll())
                .thenReturn(
                        Map.of(
                                "kinase", Color.RED,
                                "receptor", Color.BLUE,
                                "TF", Color.GREEN));

        when(style.getVisualMappingFunction(BasicVisualLexicon.NODE_FILL_COLOR))
                .thenReturn((VisualMappingFunction) dm);

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain DiscreteMapping type", response.contains("DiscreteMapping"));
        assertTrue("Should contain column name", response.contains("GeneType"));
        assertTrue("Should contain entry count", response.contains("3 entries"));
    }

    // -----------------------------------------------------------------------
    // Success: property with passthrough mapping
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void propertyWithPassthroughMapping_showsMappingInfo() throws Exception {
        stubSuccessPath();

        Set<VisualProperty<?>> nodeDescendants = new HashSet<>();
        nodeDescendants.add(BasicVisualLexicon.NODE_LABEL);
        stubDescendants(BasicVisualLexicon.NODE, nodeDescendants);
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());

        // Mock a PassthroughMapping on NODE_LABEL
        PassthroughMapping<String, String> pm = mock(PassthroughMapping.class);
        when(pm.getMappingColumnName()).thenReturn("name");
        when(pm.getMappingColumnType()).thenReturn(String.class);

        when(style.getVisualMappingFunction(BasicVisualLexicon.NODE_LABEL))
                .thenReturn((VisualMappingFunction) pm);

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should contain PassthroughMapping type", response.contains("PassthroughMapping"));
        assertTrue("Should contain column name", response.contains("name"));
        assertTrue("Should contain passthrough indicator", response.contains("(passthrough)"));
    }

    // -----------------------------------------------------------------------
    // ContinuousSubType values
    // -----------------------------------------------------------------------

    @Test
    public void continuousSubTypes_correctForPropertyTypes() throws Exception {
        stubSuccessPath();

        Set<VisualProperty<?>> nodeDescendants = new HashSet<>();
        nodeDescendants.add(BasicVisualLexicon.NODE_FILL_COLOR); // Paint → color-gradient
        nodeDescendants.add(BasicVisualLexicon.NODE_SIZE); // Double → continuous
        nodeDescendants.add(BasicVisualLexicon.NODE_SHAPE); // NodeShape → discrete
        nodeDescendants.add(BasicVisualLexicon.NODE_LABEL); // String → null (absent)
        stubDescendants(BasicVisualLexicon.NODE, nodeDescendants);
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());
        when(style.getVisualMappingFunction(any())).thenReturn(null);

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain color-gradient", response.contains("color-gradient"));
        assertTrue("Should contain continuous", response.contains("continuous"));
        assertTrue("Should contain discrete", response.contains("discrete"));
        // String properties should not have continuousSubType — it will be null and thus absent
        // We verify this indirectly: NODE_LABEL is present but has no continuousSubType
        assertTrue("Should contain NODE_LABEL property", response.contains("NODE_LABEL"));
    }

    // -----------------------------------------------------------------------
    // Continuous mapping with color gradient
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void continuousMappingWithColor_showsHexInSummary() throws Exception {
        stubSuccessPath();

        Set<VisualProperty<?>> nodeDescendants = new HashSet<>();
        nodeDescendants.add(BasicVisualLexicon.NODE_FILL_COLOR);
        stubDescendants(BasicVisualLexicon.NODE, nodeDescendants);
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());

        // Mock a ContinuousMapping with color values using real ContinuousMappingPoint instances
        ContinuousMapping<Double, Color> cm = mock(ContinuousMapping.class);
        when(cm.getMappingColumnName()).thenReturn("BetweennessCentrality");
        when(cm.getMappingColumnType()).thenReturn(Double.class);

        CyEventHelper eventHelper = mock(CyEventHelper.class);
        ContinuousMappingPoint<Double, Color> point1 =
                new ContinuousMappingPoint<>(
                        0.0,
                        new BoundaryRangeValues<>(Color.RED, Color.RED, Color.RED),
                        cm,
                        eventHelper);
        ContinuousMappingPoint<Double, Color> point2 =
                new ContinuousMappingPoint<>(
                        1.0,
                        new BoundaryRangeValues<>(Color.BLUE, Color.BLUE, Color.BLUE),
                        cm,
                        eventHelper);

        when(cm.getAllPoints()).thenReturn(List.of(point1, point2));

        when(style.getVisualMappingFunction(BasicVisualLexicon.NODE_FILL_COLOR))
                .thenReturn((VisualMappingFunction) cm);

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        // Color values should be formatted as hex in the summary
        assertTrue("Should contain red hex", response.contains("#FF0000"));
        assertTrue("Should contain blue hex", response.contains("#0000FF"));
    }

    // -----------------------------------------------------------------------
    // Empty lexicon returns empty lists
    // -----------------------------------------------------------------------

    @Test
    public void emptyLexicon_returnsEmptyLists() throws Exception {
        stubSuccessPath();
        stubDescendants(BasicVisualLexicon.NODE, Collections.emptySet());
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should contain empty node_properties",
                response.contains("\\\"node_properties\\\":[]"));
        assertTrue(
                "Should contain empty edge_properties",
                response.contains("\\\"edge_properties\\\":[]"));
    }

    // -----------------------------------------------------------------------
    // Schema tests
    // -----------------------------------------------------------------------

    @Test
    public void inputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(GetMappablePropertiesTool.INPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
        assertEquals("object", schema.get("type").asText());
        JsonNode required = schema.get("required");
        assertNotNull(required);
        assertTrue(required.isArray());
        assertEquals(0, required.size());
        JsonNode props = schema.get("properties");
        assertNotNull(props);
        assertTrue(props.isObject());
        assertEquals(0, props.size());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void stubSuccessPath() {
        when(appManager.getCurrentNetwork()).thenReturn(network);
        when(appManager.getCurrentNetworkView()).thenReturn(networkView);
        when(vmmManager.getCurrentVisualStyle()).thenReturn(style);
        when(renderingEngineManager.getDefaultVisualLexicon()).thenReturn(lexicon);
        when(style.getTitle()).thenReturn("Test Style");
    }

    @SuppressWarnings("unchecked")
    private void stubDescendants(VisualProperty<?> root, Set<VisualProperty<?>> descendants) {
        when(lexicon.getAllDescendants(root)).thenReturn((Set) descendants);
    }

    private String callTool() throws Exception {
        transport = new InMemoryTransport();
        transport.startServer("cytoscape-mcp-test", "0.0.0-test", List.of(tool.toSpec()));

        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(TOOL_CALL);
        transport.await();

        return transport.getResponse();
    }
}
