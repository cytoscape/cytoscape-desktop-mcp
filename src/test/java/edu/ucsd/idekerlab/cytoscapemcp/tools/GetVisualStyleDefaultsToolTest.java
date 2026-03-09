package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.awt.Color;
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
import org.cytoscape.model.CyNetwork;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.model.VisualLexiconNode;
import org.cytoscape.view.model.VisualProperty;
import org.cytoscape.view.presentation.RenderingEngineManager;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualPropertyDependency;
import org.cytoscape.view.vizmap.VisualStyle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link GetVisualStyleDefaultsTool} through its public interface ({@code toSpec()}) by
 * registering it on a real {@link io.modelcontextprotocol.server.McpSyncServer} backed by {@link
 * InMemoryTransport}. Cytoscape services are Mockito stubs.
 */
public class GetVisualStyleDefaultsToolTest {

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
                    + "\"params\":{\"name\":\"get_visual_style_defaults\","
                    + "\"arguments\":{}}}";

    // --- Mocks -------------------------------------------------------------

    @Mock private CyApplicationManager appManager;
    @Mock private VisualMappingManager vmmManager;
    @Mock private RenderingEngineManager renderingEngineManager;
    @Mock private VisualLexicon lexicon;
    @Mock private VisualStyle style;
    @Mock private CyNetwork network;
    @Mock private CyNetworkView networkView;

    private GetVisualStyleDefaultsTool tool;
    private InMemoryTransport transport;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        tool = new GetVisualStyleDefaultsTool(appManager, vmmManager, renderingEngineManager);
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
    // Success: style with defaults
    // -----------------------------------------------------------------------

    @Test
    public void styleWithDefaults_returnsFormattedValues() throws Exception {
        stubSuccessPath();

        // Lexicon returns NODE_FILL_COLOR and NODE_SIZE as descendants of NODE
        Set<VisualProperty<?>> nodeDescendants = new HashSet<>();
        nodeDescendants.add(BasicVisualLexicon.NODE_FILL_COLOR);
        nodeDescendants.add(BasicVisualLexicon.NODE_SIZE);
        stubDescendants(BasicVisualLexicon.NODE, nodeDescendants);
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());

        when(style.getDefaultValue(BasicVisualLexicon.NODE_FILL_COLOR))
                .thenReturn(new Color(137, 208, 245));
        when(style.getDefaultValue(BasicVisualLexicon.NODE_SIZE)).thenReturn(35.0);
        when(style.getAllVisualPropertyDependencies()).thenReturn(Collections.emptySet());

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain hex color", response.contains("#89D0F5"));
        assertTrue("Should contain size value", response.contains("35.0"));
        assertTrue("Should contain style_name", response.contains("style_name"));
        assertTrue("Should contain style title", response.contains("Test Style"));
    }

    // -----------------------------------------------------------------------
    // Discrete property includes allowedValues
    // -----------------------------------------------------------------------

    @Test
    public void discreteProperty_includesAllowedValues() throws Exception {
        stubSuccessPath();

        Set<VisualProperty<?>> nodeDescendants = new HashSet<>();
        nodeDescendants.add(BasicVisualLexicon.NODE_SHAPE);
        stubDescendants(BasicVisualLexicon.NODE, nodeDescendants);
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());

        when(style.getDefaultValue(BasicVisualLexicon.NODE_SHAPE))
                .thenReturn(
                        org.cytoscape.view.presentation.property.NodeShapeVisualProperty.ELLIPSE);
        when(style.getAllVisualPropertyDependencies()).thenReturn(Collections.emptySet());

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain allowedValues", response.contains("allowedValues"));
        assertTrue("Should contain Ellipse in allowed values", response.contains("Ellipse"));
    }

    // -----------------------------------------------------------------------
    // Continuous property omits allowedValues
    // -----------------------------------------------------------------------

    @Test
    public void continuousProperty_omitsAllowedValues() throws Exception {
        stubSuccessPath();

        Set<VisualProperty<?>> nodeDescendants = new HashSet<>();
        nodeDescendants.add(BasicVisualLexicon.NODE_FILL_COLOR);
        stubDescendants(BasicVisualLexicon.NODE, nodeDescendants);
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());

        when(style.getDefaultValue(BasicVisualLexicon.NODE_FILL_COLOR)).thenReturn(Color.RED);
        when(style.getAllVisualPropertyDependencies()).thenReturn(Collections.emptySet());

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertFalse(
                "Should not contain allowedValues for Paint", response.contains("allowedValues"));
    }

    // -----------------------------------------------------------------------
    // Numeric property includes range bounds
    // -----------------------------------------------------------------------

    @Test
    public void numericProperty_includesRangeBounds() throws Exception {
        stubSuccessPath();

        Set<VisualProperty<?>> nodeDescendants = new HashSet<>();
        nodeDescendants.add(BasicVisualLexicon.NODE_TRANSPARENCY);
        stubDescendants(BasicVisualLexicon.NODE, nodeDescendants);
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());

        when(style.getDefaultValue(BasicVisualLexicon.NODE_TRANSPARENCY)).thenReturn(255);
        when(style.getAllVisualPropertyDependencies()).thenReturn(Collections.emptySet());

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain minValue", response.contains("minValue"));
        assertTrue("Should contain maxValue", response.contains("maxValue"));
    }

    // -----------------------------------------------------------------------
    // Dependencies included
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void dependenciesIncluded() throws Exception {
        stubSuccessPath();
        stubDescendants(BasicVisualLexicon.NODE, Collections.emptySet());
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());

        Set<VisualProperty<Double>> depProps = new HashSet<>();
        depProps.add(BasicVisualLexicon.NODE_WIDTH);
        depProps.add(BasicVisualLexicon.NODE_HEIGHT);
        // VisualPropertyDependency is final — must construct a real instance.
        // Its constructor calls lexicon.getVisualLexiconNode(vp).getParent(),
        // so we build real VisualLexiconNode instances and stub a local lexicon.
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
        dep.setDependency(true);

        Set<VisualPropertyDependency<?>> deps = Set.of(dep);
        when(style.getAllVisualPropertyDependencies()).thenReturn(deps);

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain dependencies", response.contains("dependencies"));
        assertTrue("Should contain dependency id", response.contains("nodeSizeLocked"));
        assertTrue(
                "Should contain dependency display name",
                response.contains("Lock node width and height"));
        assertTrue("Should contain enabled flag", response.contains("\"enabled\":true"));
        assertTrue("Should contain properties list", response.contains("NODE_WIDTH"));
        assertTrue("Should contain properties list", response.contains("NODE_HEIGHT"));
    }

    // -----------------------------------------------------------------------
    // Empty lexicon returns empty lists
    // -----------------------------------------------------------------------

    @Test
    public void emptyLexicon_returnsEmptyLists() throws Exception {
        stubSuccessPath();
        stubDescendants(BasicVisualLexicon.NODE, Collections.emptySet());
        stubDescendants(BasicVisualLexicon.EDGE, Collections.emptySet());
        when(style.getAllVisualPropertyDependencies()).thenReturn(Collections.emptySet());

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should contain empty node_properties",
                response.contains("\\\"node_properties\\\":[]"));
        assertTrue(
                "Should contain empty edge_properties",
                response.contains("\\\"edge_properties\\\":[]"));
        assertTrue(
                "Should contain empty dependencies", response.contains("\\\"dependencies\\\":[]"));
    }

    // -----------------------------------------------------------------------
    // Schema tests
    // -----------------------------------------------------------------------

    @Test
    public void inputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(GetVisualStyleDefaultsTool.INPUT_SCHEMA);
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
