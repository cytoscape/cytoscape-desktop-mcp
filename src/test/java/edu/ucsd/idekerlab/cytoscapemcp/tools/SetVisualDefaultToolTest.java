package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.awt.Color;
import java.awt.Font;
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
import org.cytoscape.model.CyNetwork;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.model.VisualLexiconNode;
import org.cytoscape.view.model.VisualProperty;
import org.cytoscape.view.presentation.RenderingEngineManager;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.view.presentation.property.NodeShapeVisualProperty;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualPropertyDependency;
import org.cytoscape.view.vizmap.VisualStyle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link SetVisualDefaultTool} through its public interface ({@code toSpec()}) by
 * registering it on a real {@link io.modelcontextprotocol.server.McpSyncServer} backed by {@link
 * InMemoryTransport}. Cytoscape services are Mockito stubs.
 */
public class SetVisualDefaultToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Real BasicVisualLexicon VisualProperty instances known to findPropertyById. Tests that pass a
     * valid property ID must have that property in this set (or add it explicitly).
     */
    @SuppressWarnings("unchecked")
    private static final Set<VisualProperty<?>> ALL_TEST_PROPS =
            Set.of(
                    BasicVisualLexicon.NODE_FILL_COLOR,
                    BasicVisualLexicon.NODE_SIZE,
                    BasicVisualLexicon.NODE_SHAPE,
                    BasicVisualLexicon.NODE_LABEL_FONT_FACE,
                    BasicVisualLexicon.NODE_TRANSPARENCY,
                    BasicVisualLexicon.EDGE_WIDTH,
                    BasicVisualLexicon.EDGE_LINE_TYPE,
                    BasicVisualLexicon.EDGE_TARGET_ARROW_SHAPE);

    // --- JSON-RPC protocol messages -----------------------------------------

    private static final String INIT_REQUEST =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\","
                    + "\"params\":{\"protocolVersion\":\"2025-03-26\","
                    + "\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";

    private static final String INITIALIZED_NOTIFICATION =
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

    // --- Mocks --------------------------------------------------------------

    @Mock private CyApplicationManager appManager;
    @Mock private VisualMappingManager vmmManager;
    @Mock private RenderingEngineManager renderingEngineManager;
    @Mock private VisualLexicon lexicon;
    @Mock private VisualStyle style;
    @Mock private CyNetwork network;
    @Mock private CyNetworkView networkView;

    private SetVisualDefaultTool tool;
    private InMemoryTransport transport;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        tool =
                new SetVisualDefaultTool(
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

        String response = callTool("{}");

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should contain descriptive error message",
                response.contains("No network is currently loaded in Cytoscape Desktop"));
    }

    // -----------------------------------------------------------------------
    // Success: no view — style is updated but cannot be applied
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void noViewLoaded_succeedsWithNote() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(network);
        when(appManager.getCurrentNetworkView()).thenReturn(null);
        when(vmmManager.getCurrentVisualStyle()).thenReturn(style);
        when(renderingEngineManager.getDefaultVisualLexicon()).thenReturn(lexicon);
        when(style.getTitle()).thenReturn("Test Style");
        when(lexicon.getAllVisualProperties()).thenReturn(ALL_TEST_PROPS);

        when(style.getDefaultValue(BasicVisualLexicon.NODE_FILL_COLOR))
                .thenReturn(new Color(255, 102, 0)); // #FF6600

        String response =
                callTool(
                        "{\"node_properties\": [{\"id\": \"NODE_FILL_COLOR\","
                                + " \"currentValue\": \"#FF6600\"}]}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain status success", response.contains("\"status\":\"success\""));
        // note should be present (no view)
        assertTrue(
                "Should contain no-view note",
                response.contains("no network view is active") || response.contains("no view"));
        // result should have the new color
        assertTrue("Should contain updated color in result", response.contains("#FF6600"));
        assertTrue(
                "Should contain updated_properties", response.contains("\"updated_properties\""));
        // setDefaultValue should have been invoked
        verify(style).setDefaultValue(eq(BasicVisualLexicon.NODE_FILL_COLOR), any());
        // style.apply should NOT have been called (no view)
        verify(style, never()).apply(any(CyNetworkView.class));
    }

    // -----------------------------------------------------------------------
    // Success: set two node properties (color + size)
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void setNodeColorAndSize_succeeds() throws Exception {
        stubSuccessPath();

        when(style.getDefaultValue(BasicVisualLexicon.NODE_FILL_COLOR))
                .thenReturn(new Color(255, 102, 0)); // #FF6600
        when(style.getDefaultValue(BasicVisualLexicon.NODE_SIZE)).thenReturn(45.0);

        String response =
                callTool(
                        "{\"node_properties\": [{\"id\": \"NODE_FILL_COLOR\","
                                + " \"currentValue\": \"#FF6600\"}, {\"id\": \"NODE_SIZE\","
                                + " \"currentValue\": \"45.0\"}]}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain status success", response.contains("\"status\":\"success\""));
        assertTrue("Should contain updated color", response.contains("#FF6600"));
        assertTrue("Should contain updated size", response.contains("45.0"));
        assertTrue(
                "Should contain updated_properties", response.contains("\"updated_properties\""));
        verify(style).setDefaultValue(eq(BasicVisualLexicon.NODE_FILL_COLOR), any());
        verify(style).setDefaultValue(eq(BasicVisualLexicon.NODE_SIZE), any());
        verify(style).apply(networkView);
        verify(networkView).updateView();
    }

    // -----------------------------------------------------------------------
    // Success: set edge width
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void setEdgeWidth_succeeds() throws Exception {
        stubSuccessPath();

        when(style.getDefaultValue(BasicVisualLexicon.EDGE_WIDTH)).thenReturn(3.0);

        String response =
                callTool(
                        "{\"edge_properties\": [{\"id\": \"EDGE_WIDTH\","
                                + " \"currentValue\": \"3.0\"}]}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain status success", response.contains("\"status\":\"success\""));
        assertTrue(
                "Should contain updated_properties", response.contains("\"updated_properties\""));
        assertTrue("Should contain updated width", response.contains("3.0"));
        verify(style).setDefaultValue(eq(BasicVisualLexicon.EDGE_WIDTH), any());
    }

    // -----------------------------------------------------------------------
    // Success: set node shape (discrete type)
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void setNodeShape_succeeds() throws Exception {
        stubSuccessPath();

        when(style.getDefaultValue(BasicVisualLexicon.NODE_SHAPE))
                .thenReturn(NodeShapeVisualProperty.RECTANGLE);

        String response =
                callTool(
                        "{\"node_properties\": [{\"id\": \"NODE_SHAPE\","
                                + " \"currentValue\": \"Rectangle\"}]}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain status success", response.contains("\"status\":\"success\""));
        assertTrue("Should contain Rectangle", response.contains("Rectangle"));
        verify(style).setDefaultValue(eq(BasicVisualLexicon.NODE_SHAPE), any());
    }

    // -----------------------------------------------------------------------
    // Success: set node label font
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void setFont_succeeds() throws Exception {
        stubSuccessPath();

        when(style.getDefaultValue(BasicVisualLexicon.NODE_LABEL_FONT_FACE))
                .thenReturn(new Font("SansSerif", Font.BOLD, 14));

        String response =
                callTool(
                        "{\"node_properties\": [{\"id\": \"NODE_LABEL_FONT_FACE\","
                                + " \"currentValue\": \"SansSerif-Bold-14\"}]}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain status success", response.contains("\"status\":\"success\""));
        assertTrue("Should contain font info", response.contains("SansSerif"));
        verify(style).setDefaultValue(eq(BasicVisualLexicon.NODE_LABEL_FONT_FACE), any());
    }

    // -----------------------------------------------------------------------
    // Success: set edge line type (discrete type)
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void setLineType_succeeds() throws Exception {
        stubSuccessPath();

        when(style.getDefaultValue(BasicVisualLexicon.EDGE_LINE_TYPE))
                .thenReturn(org.cytoscape.view.presentation.property.LineTypeVisualProperty.SOLID);

        String response =
                callTool(
                        "{\"edge_properties\": [{\"id\": \"EDGE_LINE_TYPE\","
                                + " \"currentValue\": \"Solid\"}]}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain status success", response.contains("\"status\":\"success\""));
        assertTrue(
                "Should contain updated_properties", response.contains("\"updated_properties\""));
        verify(style).setDefaultValue(eq(BasicVisualLexicon.EDGE_LINE_TYPE), any());
    }

    // -----------------------------------------------------------------------
    // Success: set edge target arrow shape (discrete type)
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void setArrowShape_succeeds() throws Exception {
        stubSuccessPath();

        when(style.getDefaultValue(BasicVisualLexicon.EDGE_TARGET_ARROW_SHAPE))
                .thenReturn(
                        org.cytoscape.view.presentation.property.ArrowShapeVisualProperty.ARROW);

        String response =
                callTool(
                        "{\"edge_properties\": [{\"id\": \"EDGE_TARGET_ARROW_SHAPE\","
                                + " \"currentValue\": \"Arrow\"}]}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain status success", response.contains("\"status\":\"success\""));
        assertTrue("Should contain Arrow in response", response.contains("Arrow"));
        verify(style).setDefaultValue(eq(BasicVisualLexicon.EDGE_TARGET_ARROW_SHAPE), any());
    }

    // -----------------------------------------------------------------------
    // Error: unknown property ID
    // -----------------------------------------------------------------------

    @Test
    public void invalidPropertyId_returnsError() throws Exception {
        stubSuccessPath();

        String response =
                callTool(
                        "{\"node_properties\": [{\"id\": \"TOTALLY_BOGUS_PROPERTY\","
                                + " \"currentValue\": \"foo\"}]}");

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue("Should describe unknown ID", response.contains("Unknown visual property ID"));
        assertTrue("Should include the bad ID", response.contains("TOTALLY_BOGUS_PROPERTY"));
    }

    // -----------------------------------------------------------------------
    // Error: invalid color string
    // -----------------------------------------------------------------------

    @Test
    public void invalidColorString_returnsError() throws Exception {
        stubSuccessPath();

        String response =
                callTool(
                        "{\"node_properties\": [{\"id\": \"NODE_FILL_COLOR\","
                                + " \"currentValue\": \"not-a-color\"}]}");

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should describe invalid color",
                response.contains("Invalid") || response.contains("invalid"));
    }

    // -----------------------------------------------------------------------
    // Error: invalid shape name
    // -----------------------------------------------------------------------

    @Test
    public void invalidShapeName_returnsError() throws Exception {
        stubSuccessPath();

        String response =
                callTool(
                        "{\"node_properties\": [{\"id\": \"NODE_SHAPE\","
                                + " \"currentValue\": \"NotAShape\"}]}");

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should describe invalid shape",
                response.contains("Invalid") || response.contains("invalid"));
    }

    // -----------------------------------------------------------------------
    // Error: out-of-range numeric value
    // -----------------------------------------------------------------------

    @Test
    public void outOfRangeNumeric_returnsError() throws Exception {
        stubSuccessPath();

        // NODE_TRANSPARENCY range is 0–255; 999 is out of range.
        String response =
                callTool(
                        "{\"node_properties\": [{\"id\": \"NODE_TRANSPARENCY\","
                                + " \"currentValue\": \"999\"}]}");

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue("Should describe out-of-range", response.contains("out of range"));
    }

    // -----------------------------------------------------------------------
    // Success: empty input — returns empty updated_properties (no mutations)
    // -----------------------------------------------------------------------

    @Test
    public void emptyInput_returnsEmptyUpdatedProperties() throws Exception {
        stubSuccessPath();

        String response = callTool("{}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain status success", response.contains("\"status\":\"success\""));
        assertTrue(
                "Should contain updated_properties", response.contains("\"updated_properties\""));
        assertTrue(
                "Should have empty updated_properties",
                response.contains("\"updated_properties\":[]"));
        // No setDefaultValue calls since no updates were provided
        verify(style, never()).setDefaultValue(any(VisualProperty.class), any());
    }

    // -----------------------------------------------------------------------
    // Schema tests
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    // Success: toggle dependency enable=true
    // -----------------------------------------------------------------------

    @Test
    public void toggleDependency_enableTrue_succeeds() throws Exception {
        stubSuccessPath();
        VisualPropertyDependency<Double> dep = createNodeSizeDep(false);
        when(style.getAllVisualPropertyDependencies()).thenReturn(Set.of(dep));

        String response =
                callTool(
                        "{\"dependencies\": [{\"id\": \"nodeSizeLocked\","
                                + " \"enabled\": true}]}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain status success", response.contains("\"status\":\"success\""));
        assertTrue(
                "Should contain updated_dependencies",
                response.contains("\"updated_dependencies\""));
        assertTrue("Should contain nodeSizeLocked", response.contains("nodeSizeLocked"));
        assertTrue("Should show enabled true", response.contains("\"enabled\":true"));
        assertTrue("Dependency should be enabled", dep.isDependencyEnabled());
    }

    // -----------------------------------------------------------------------
    // Success: toggle dependency enable=false
    // -----------------------------------------------------------------------

    @Test
    public void toggleDependency_enableFalse_succeeds() throws Exception {
        stubSuccessPath();
        VisualPropertyDependency<Double> dep = createNodeSizeDep(true);
        when(style.getAllVisualPropertyDependencies()).thenReturn(Set.of(dep));

        String response =
                callTool(
                        "{\"dependencies\": [{\"id\": \"nodeSizeLocked\","
                                + " \"enabled\": false}]}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain status success", response.contains("\"status\":\"success\""));
        assertTrue("Should show enabled false", response.contains("\"enabled\":false"));
        assertFalse("Dependency should be disabled", dep.isDependencyEnabled());
    }

    // -----------------------------------------------------------------------
    // Error: unknown dependency ID
    // -----------------------------------------------------------------------

    @Test
    public void unknownDependencyId_returnsError() throws Exception {
        stubSuccessPath();
        when(style.getAllVisualPropertyDependencies()).thenReturn(Set.of());

        String response =
                callTool("{\"dependencies\": [{\"id\": \"bogusLock\"," + " \"enabled\": true}]}");

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should describe unknown dependency", response.contains("Unknown dependency ID"));
        assertTrue("Should include the bad ID", response.contains("bogusLock"));
    }

    // -----------------------------------------------------------------------
    // Error: missing 'enabled' field
    // -----------------------------------------------------------------------

    @Test
    public void missingEnabledField_returnsError() throws Exception {
        stubSuccessPath();
        VisualPropertyDependency<Double> dep = createNodeSizeDep(false);
        when(style.getAllVisualPropertyDependencies()).thenReturn(Set.of(dep));

        String response = callTool("{\"dependencies\": [{\"id\": \"nodeSizeLocked\"}]}");

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue("Should describe missing enabled", response.contains("Missing 'enabled'"));
    }

    // -----------------------------------------------------------------------
    // Success: combined property update + dependency toggle
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void dependencyWithPropertyUpdate_succeeds() throws Exception {
        stubSuccessPath();
        VisualPropertyDependency<Double> dep = createNodeSizeDep(false);
        when(style.getAllVisualPropertyDependencies()).thenReturn(Set.of(dep));
        when(style.getDefaultValue(BasicVisualLexicon.NODE_SIZE)).thenReturn(50.0);

        String response =
                callTool(
                        "{\"dependencies\": [{\"id\": \"nodeSizeLocked\","
                                + " \"enabled\": true}], \"node_properties\":"
                                + " [{\"id\": \"NODE_SIZE\", \"currentValue\": \"50.0\"}]}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain status success", response.contains("\"status\":\"success\""));
        assertTrue(
                "Should contain updated_properties", response.contains("\"updated_properties\""));
        assertTrue(
                "Should contain updated_dependencies",
                response.contains("\"updated_dependencies\""));
        assertTrue("Should contain size value", response.contains("50.0"));
        assertTrue("Should contain nodeSizeLocked", response.contains("nodeSizeLocked"));
        verify(style).setDefaultValue(eq(BasicVisualLexicon.NODE_SIZE), any());
        assertTrue("Dependency should be enabled", dep.isDependencyEnabled());
    }

    // -----------------------------------------------------------------------
    // Success: empty dependencies array — omits from response
    // -----------------------------------------------------------------------

    @Test
    public void emptyDependenciesArray_omitsFromResponse() throws Exception {
        stubSuccessPath();
        when(style.getAllVisualPropertyDependencies()).thenReturn(Set.of());

        String response = callTool("{\"dependencies\": []}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain status success", response.contains("\"status\":\"success\""));
        assertFalse(
                "Should not contain updated_dependencies",
                response.contains("\"updated_dependencies\""));
    }

    // -----------------------------------------------------------------------
    // Schema tests
    // -----------------------------------------------------------------------

    @Test
    public void inputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(SetVisualDefaultTool.INPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
        assertEquals("object", schema.get("type").asText());
        JsonNode props = schema.get("properties");
        assertNotNull(props);
        assertTrue(props.has("node_properties"));
        assertTrue(props.has("edge_properties"));
        assertTrue(props.has("dependencies"));
        assertEquals("array", props.get("node_properties").get("type").asText());
        assertEquals("array", props.get("edge_properties").get("type").asText());
        assertEquals("array", props.get("dependencies").get("type").asText());
    }

    @Test
    public void outputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(SetVisualDefaultTool.OUTPUT_SCHEMA);
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
        when(style.getTitle()).thenReturn("Test Style");
        // Allow findPropertyById to find any property in our test set.
        when(lexicon.getAllVisualProperties()).thenReturn(ALL_TEST_PROPS);
        // Default: no dependencies (tests that need them override this)
        when(style.getAllVisualPropertyDependencies()).thenReturn(Set.of());
    }

    /**
     * Creates a real {@link VisualPropertyDependency} for nodeSizeLocked. VisualPropertyDependency
     * is final so it cannot be mocked — we build real VisualLexiconNode stubs for its constructor.
     */
    @SuppressWarnings("unchecked")
    private VisualPropertyDependency<Double> createNodeSizeDep(boolean enabled) {
        Set<VisualProperty<Double>> depProps = new HashSet<>();
        depProps.add(BasicVisualLexicon.NODE_WIDTH);
        depProps.add(BasicVisualLexicon.NODE_HEIGHT);

        VisualLexiconNode parentNode = new VisualLexiconNode(BasicVisualLexicon.NODE, null);
        VisualLexiconNode widthNode =
                new VisualLexiconNode(BasicVisualLexicon.NODE_WIDTH, parentNode);
        VisualLexiconNode heightNode =
                new VisualLexiconNode(BasicVisualLexicon.NODE_HEIGHT, parentNode);
        VisualLexicon depLexicon = org.mockito.Mockito.mock(VisualLexicon.class);
        when(depLexicon.getVisualLexiconNode(BasicVisualLexicon.NODE_WIDTH)).thenReturn(widthNode);
        when(depLexicon.getVisualLexiconNode(BasicVisualLexicon.NODE_HEIGHT))
                .thenReturn(heightNode);

        VisualPropertyDependency<Double> dep =
                new VisualPropertyDependency<>(
                        "nodeSizeLocked", "Lock node width and height", depProps, depLexicon);
        dep.setDependency(enabled);
        return dep;
    }

    private String callTool(String arguments) throws Exception {
        String toolCall =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"set_visual_default\","
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
