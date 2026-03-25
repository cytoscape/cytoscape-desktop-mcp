package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.awt.Color;
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
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * Exercises {@link CreateDiscreteMappingGeneratedTool} via InMemoryTransport. Cytoscape services
 * are Mockito stubs; {@link GeneratorService} is also mocked to decouple palette SDK from tests.
 */
public class CreateDiscreteMappingGeneratedToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
    @Mock private GeneratorService generatorService;
    @Mock private ValidationService validationService;
    @Mock private VisualStyle style;
    @Mock private CyNetwork network;
    @Mock private CyNetworkView networkView;
    @Mock private VisualLexicon lexicon;
    @Mock private CyTable nodeTable;
    @Mock private CyTable edgeTable;
    @Mock private DiscreteMapping mockMapping;

    private CreateDiscreteMappingGeneratedTool tool;
    private InMemoryTransport transport;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        doAnswer(
                        inv -> {
                            Object raw = inv.getArgument(0);
                            Class<?> type = inv.getArgument(1);
                            if (raw == null) return null;
                            if (raw instanceof java.util.Map<?, ?> map
                                    && map.containsKey("waived")) {
                                if (Boolean.TRUE.equals(map.get("waived"))) return null;
                                raw = map.get("parameter");
                            }
                            if (raw == null) return null;
                            return type.isInstance(raw) ? raw : null;
                        })
                .when(validationService)
                .unwrapToolInputValue(any(), any());
        tool = buildTool(validationService);
    }

    private CreateDiscreteMappingGeneratedTool buildTool(ValidationService vs) {
        return new CreateDiscreteMappingGeneratedTool(
                appManager,
                vmmManager,
                renderingEngineManager,
                discreteMappingFactory,
                new VisualPropertyService(),
                generatorService,
                vs);
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

        String response = callTool(buildArgs("NODE_FILL_COLOR", "GeneType", "String", "rainbow"));

        assertTrue(response.contains("\"isError\":true"));
        assertTrue(response.contains("No network"));
    }

    // -----------------------------------------------------------------------
    // Error: no view loaded
    // -----------------------------------------------------------------------

    @Test
    public void noViewLoaded_returnsError() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(network);
        when(appManager.getCurrentNetworkView()).thenReturn(null);

        String response = callTool(buildArgs("NODE_FILL_COLOR", "GeneType", "String", "rainbow"));

        assertTrue(response.contains("\"isError\":true"));
        assertTrue(response.toLowerCase().contains("view"));
    }

    // -----------------------------------------------------------------------
    // Error: unknown property_id
    // -----------------------------------------------------------------------

    @Test
    public void unknownPropertyId_returnsError() throws Exception {
        stubSuccessPath();

        String response = callTool(buildArgs("BOGUS_PROP", "GeneType", "String", "rainbow"));

        assertTrue(response.contains("\"isError\":true"));
        assertTrue(response.contains("Unknown"));
    }

    // -----------------------------------------------------------------------
    // Error: unknown generator name
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void unknownGenerator_returnsError() throws Exception {
        stubSuccessPath();
        // Stub column so the handler reaches generate() — error surfaces there for unknown names
        stubNodeTableWithStringColumn("GeneType", "kinase");

        String response =
                callTool(buildArgs("NODE_FILL_COLOR", "GeneType", "String", "sparkle_burst"));

        assertTrue(response.contains("\"isError\":true"));
        assertTrue(response.contains("Unknown generator") || response.contains("sparkle_burst"));
    }

    // -----------------------------------------------------------------------
    // Error: invalid column_type
    // -----------------------------------------------------------------------

    @Test
    public void invalidColumnType_returnsError() throws Exception {
        stubSuccessPath();

        String rawJson =
                "{\"property_id\":\"NODE_FILL_COLOR\",\"column_name\":\"GeneType\","
                        + "\"column_type\":\"Timestamp\",\"generator\":\"rainbow\"}";
        String response = callToolRaw(rawJson);

        assertTrue(response.contains("\"isError\":true"));
        assertTrue(response.contains("Unsupported column type"));
    }

    // -----------------------------------------------------------------------
    // Error: column not found in table
    // -----------------------------------------------------------------------

    @Test
    public void columnNotFound_returnsError() throws Exception {
        stubSuccessPath();
        when(network.getDefaultNodeTable()).thenReturn(nodeTable);
        when(nodeTable.getColumn("Missing")).thenReturn(null);

        String response = callTool(buildArgs("NODE_FILL_COLOR", "Missing", "String", "rainbow"));

        assertTrue(response.contains("\"isError\":true"));
        assertTrue(response.contains("Missing") || response.contains("not found"));
    }

    // -----------------------------------------------------------------------
    // Error: column has no non-null values
    // -----------------------------------------------------------------------

    @Test
    public void emptyColumn_returnsError() throws Exception {
        stubSuccessPath();
        CyColumn col = mockColumn("GeneType", String.class, null);
        when(network.getDefaultNodeTable()).thenReturn(nodeTable);
        when(nodeTable.getColumn("GeneType")).thenReturn(col);
        CyRow nullRow = mock(CyRow.class);
        when(nullRow.get("GeneType", String.class)).thenReturn(null);
        when(nodeTable.getAllRows()).thenReturn(List.of(nullRow));

        String response = callTool(buildArgs("NODE_FILL_COLOR", "GeneType", "String", "rainbow"));

        assertTrue(response.contains("\"isError\":true"));
        assertTrue(
                response.contains("no non-null values")
                        || response.contains("empty")
                        || response.contains("GeneType"));
    }

    // -----------------------------------------------------------------------
    // Error: rainbow on non-Paint VP (NODE_SIZE is Double)
    // -----------------------------------------------------------------------

    @Test
    public void rainbowOnNonPaintVP_returnsError() throws Exception {
        stubSuccessPath();

        String response = callTool(buildArgs("NODE_SIZE", "GeneType", "String", "rainbow"));

        assertTrue(response.contains("\"isError\":true"));
        assertTrue(
                response.contains("color")
                        || response.contains("Paint")
                        || response.contains("incompatible"));
    }

    // -----------------------------------------------------------------------
    // Error: shape_cycle on Paint VP (NODE_FILL_COLOR is Paint)
    // -----------------------------------------------------------------------

    @Test
    public void shapeCycleOnPaintVP_returnsError() throws Exception {
        stubSuccessPath();

        String response =
                callTool(buildArgs("NODE_FILL_COLOR", "GeneType", "String", "shape_cycle"));

        assertTrue(response.contains("\"isError\":true"));
        assertTrue(
                response.contains("shape_cycle")
                        || response.contains("incompatible")
                        || response.contains("discrete"));
    }

    // -----------------------------------------------------------------------
    // Error: numeric_range missing generator_params
    // -----------------------------------------------------------------------

    @Test
    public void numericRangeMissingParams_returnsError() throws Exception {
        stubSuccessPath();

        String rawJson =
                "{\"property_id\":\"NODE_SIZE\",\"column_name\":\"Degree\","
                        + "\"column_type\":\"Integer\",\"generator\":\"numeric_range\"}";
        String response = callToolRaw(rawJson);

        assertTrue(response.contains("\"isError\":true"));
        assertTrue(
                response.contains("min")
                        || response.contains("max")
                        || response.contains("generator_params"));
    }

    // -----------------------------------------------------------------------
    // Success: rainbow on NODE_FILL_COLOR / String column
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void rainbow_successOnColorVP() throws Exception {
        stubSuccessPath();
        stubNodeTableWithStringColumn("GeneType", "kinase", "receptor");
        stubFactory();
        doReturn(Map.of("kinase", Color.RED, "receptor", Color.BLUE))
                .when(generatorService)
                .generateRainbow(any());

        String response = callTool(buildArgs("NODE_FILL_COLOR", "GeneType", "String", "rainbow"));

        assertFalse("Should not be error", response.contains("\"isError\":true"));
        assertTrue(response.contains("\"status\":\"success\""));
        assertTrue(response.contains("\"entries_count\":2"));
        assertTrue(response.contains("\"mapping_type\":\"DiscreteMapping\""));
        assertTrue(response.contains("\"generator\":\"rainbow\""));
        verify(mockMapping).putAll(any(Map.class));
        verify(style).apply(networkView);
        verify(networkView).updateView();
    }

    // -----------------------------------------------------------------------
    // Success: random on NODE_FILL_COLOR / String column
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void random_successOnColorVP() throws Exception {
        stubSuccessPath();
        stubNodeTableWithStringColumn("Type", "alpha", "beta");
        stubFactory();
        doReturn(Map.of("alpha", Color.CYAN, "beta", Color.ORANGE))
                .when(generatorService)
                .generateRandom(any());

        String response = callTool(buildArgs("NODE_FILL_COLOR", "Type", "String", "random"));

        assertFalse(response.contains("\"isError\":true"));
        assertTrue(response.contains("\"status\":\"success\""));
        assertTrue(response.contains("\"entries_count\":2"));
        assertTrue(response.contains("\"generator\":\"random\""));
    }

    // -----------------------------------------------------------------------
    // Success: brewer_sequential on NODE_FILL_COLOR / String column
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void brewerSequential_successOnColorVP() throws Exception {
        stubSuccessPath();
        stubNodeTableWithStringColumn("tissue", "liver", "kidney", "lung");
        stubFactory();
        doReturn(
                        Map.of(
                                "liver",
                                new Color(0, 0, 200),
                                "kidney",
                                new Color(0, 0, 150),
                                "lung",
                                new Color(0, 0, 100)))
                .when(generatorService)
                .generateBrewerSequential(any(), any());

        String rawJson =
                "{\"property_id\":\"NODE_FILL_COLOR\",\"column_name\":\"tissue\","
                        + "\"column_type\":\"String\",\"generator\":\"brewer_sequential\","
                        + "\"generator_params\":{\"hue\":\"blue\"}}";
        String response = callToolRaw(rawJson);

        assertFalse(response.contains("\"isError\":true"));
        assertTrue(response.contains("\"entries_count\":3"));
        assertTrue(response.contains("\"generator\":\"brewer_sequential\""));
    }

    // -----------------------------------------------------------------------
    // Success: shape_cycle on NODE_SHAPE / String column
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void shapeCycle_successOnShapeVP() throws Exception {
        stubSuccessPath();
        stubNodeTableWithStringColumn("community", "c1", "c2", "c3");
        stubFactory();
        // Return actual NodeShape values - use Ellipse from BasicVisualLexicon
        org.cytoscape.view.presentation.property.values.NodeShape ellipse =
                org.cytoscape.view.presentation.property.NodeShapeVisualProperty.ELLIPSE;
        doReturn(Map.of("c1", ellipse, "c2", ellipse, "c3", ellipse))
                .when(generatorService)
                .generateShapeCycle(any(), any());

        String response = callTool(buildArgs("NODE_SHAPE", "community", "String", "shape_cycle"));

        assertFalse(response.contains("\"isError\":true"));
        assertTrue(response.contains("\"entries_count\":3"));
        assertTrue(response.contains("\"generator\":\"shape_cycle\""));
    }

    // -----------------------------------------------------------------------
    // Success: numeric_range on NODE_SIZE / Integer column
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void numericRange_successOnNumericVP() throws Exception {
        stubSuccessPath();
        CyColumn col = mockColumn("Degree", Integer.class, null);
        when(network.getDefaultNodeTable()).thenReturn(nodeTable);
        when(nodeTable.getColumn("Degree")).thenReturn(col);
        CyRow row1 = mock(CyRow.class);
        CyRow row2 = mock(CyRow.class);
        when(row1.get("Degree", Integer.class)).thenReturn(1);
        when(row2.get("Degree", Integer.class)).thenReturn(5);
        when(nodeTable.getAllRows()).thenReturn(List.of(row1, row2));
        stubFactory();
        doReturn(Map.of(1, 10.0, 5, 60.0))
                .when(generatorService)
                .generateNumericRange(any(), any(double.class), any(double.class), any());

        String rawJson =
                "{\"property_id\":\"NODE_SIZE\",\"column_name\":\"Degree\","
                        + "\"column_type\":\"Integer\",\"generator\":\"numeric_range\","
                        + "\"generator_params\":{\"min\":10,\"max\":60}}";
        String response = callToolRaw(rawJson);

        assertFalse(response.contains("\"isError\":true"));
        assertTrue(response.contains("\"entries_count\":2"));
        assertTrue(response.contains("\"generator\":\"numeric_range\""));
    }

    // -----------------------------------------------------------------------
    // Success: edge VP (EDGE_WIDTH) uses edge table
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void edgeVP_usesEdgeTable() throws Exception {
        stubSuccessPath();
        CyColumn col = mockColumn("weight", String.class, null);
        when(network.getDefaultEdgeTable()).thenReturn(edgeTable);
        when(edgeTable.getColumn("weight")).thenReturn(col);
        CyRow row = mock(CyRow.class);
        when(row.get("weight", String.class)).thenReturn("strong");
        when(edgeTable.getAllRows()).thenReturn(List.of(row));
        stubFactory();
        doReturn(Map.of("strong", 3.0))
                .when(generatorService)
                .generateNumericRange(any(), any(double.class), any(double.class), any());

        String rawJson =
                "{\"property_id\":\"EDGE_WIDTH\",\"column_name\":\"weight\","
                        + "\"column_type\":\"String\",\"generator\":\"numeric_range\","
                        + "\"generator_params\":{\"min\":1,\"max\":5}}";
        String response = callToolRaw(rawJson);

        assertFalse(response.contains("\"isError\":true"));
        assertTrue(response.contains("\"entries_count\":1"));
    }

    // -----------------------------------------------------------------------
    // Schema: inputSchema is valid JSON with expected structure
    // -----------------------------------------------------------------------

    @Test
    public void inputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(CreateDiscreteMappingGeneratedTool.INPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
        assertEquals("object", schema.get("type").asText());

        JsonNode props = schema.get("properties");
        assertNotNull(props);
        assertTrue("Should have property_id", props.has("property_id"));
        assertTrue("Should have column_name", props.has("column_name"));
        assertTrue("Should have column_type", props.has("column_type"));
        assertTrue("Should have generator", props.has("generator"));
        assertTrue("Should have generator_params", props.has("generator_params"));

        // generator is now a ConditionalParam (object with waived + parameter fields)
        JsonNode generatorNode = props.get("generator");
        assertEquals("object", generatorNode.get("type").asText());
        JsonNode generatorProps = generatorNode.get("properties");
        assertNotNull("generator should have nested properties", generatorProps);
        assertTrue("generator should have waived field", generatorProps.has("waived"));
        assertTrue("generator should have parameter field", generatorProps.has("parameter"));

        JsonNode colTypeEnum = props.get("column_type").get("enum");
        assertNotNull("column_type should have enum values", colTypeEnum);
        String colEnumStr = colTypeEnum.toString();
        assertTrue(colEnumStr.contains("Integer"));
        assertTrue(colEnumStr.contains("String"));
        assertTrue(colEnumStr.contains("Boolean"));
    }

    // -----------------------------------------------------------------------
    // Schema: outputSchema is valid JSON
    // -----------------------------------------------------------------------

    @Test
    public void outputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(CreateDiscreteMappingGeneratedTool.OUTPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
    }

    // -----------------------------------------------------------------------
    // Delegation: ValidationService error propagation
    // -----------------------------------------------------------------------

    @Test
    public void numericRangeMissingGeneratorParams_propagatesValidationError() throws Exception {
        stubSuccessPath();
        when(validationService.validateConditionalParams(anyString(), anyString(), any(), any()))
                .thenReturn(stubError("stub-error: generator_params missing for numeric_range"));

        String rawJson =
                "{\"property_id\":\"NODE_SIZE\",\"column_name\":\"Degree\","
                        + "\"column_type\":\"Integer\",\"generator\":\"numeric_range\"}";
        String response = callToolRaw(rawJson);

        assertTrue(response.contains("\"isError\":true"));
        assertTrue(response.contains("stub-error: generator_params missing for numeric_range"));
    }

    // -----------------------------------------------------------------------
    // Generator as ConditionalParam: 4 waived/explicit × 1-choice/multi-choice cases
    // -----------------------------------------------------------------------

    /**
     * Test 1: generator waived, only one compatible generator exists (NODE_SHAPE → shape_cycle).
     * The tool must auto-select shape_cycle and succeed.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void generator_waived_oneCompatible_autoSelectsAndSucceeds() throws Exception {
        stubSuccessPath();
        stubNodeTableWithStringColumn("community", "c1", "c2");
        stubFactory();
        org.cytoscape.view.presentation.property.values.NodeShape ellipse =
                org.cytoscape.view.presentation.property.NodeShapeVisualProperty.ELLIPSE;
        doReturn(Map.of("c1", ellipse, "c2", ellipse))
                .when(generatorService)
                .generateShapeCycle(any(), any());

        String rawJson =
                "{\"property_id\":\"NODE_SHAPE\",\"column_name\":\"community\","
                        + "\"column_type\":\"String\","
                        + "\"generator\":{\"waived\":true}}";
        String response = callToolRaw(rawJson);

        assertFalse("Expected no error", response.contains("\"isError\":true"));
        assertTrue(response.contains("\"generator\":\"shape_cycle\""));
    }

    /**
     * Test 2: generator waived, multiple compatible generators exist (NODE_FILL_COLOR → rainbow /
     * random / brewer_sequential). The tool must reject the waive and return an error.
     */
    @Test
    public void generator_waived_multipleCompatible_returnsError() throws Exception {
        stubSuccessPath();
        // Use real ValidationService so validateConditionalParams actually enforces cannotWaive
        tool = buildTool(new ValidationService());

        String rawJson =
                "{\"property_id\":\"NODE_FILL_COLOR\",\"column_name\":\"GeneType\","
                        + "\"column_type\":\"String\","
                        + "\"generator\":{\"waived\":true}}";
        String response = callToolRaw(rawJson);

        assertTrue("Expected error", response.contains("\"isError\":true"));
        assertTrue(response.contains("generator"));
    }

    /**
     * Test 3: generator explicitly provided (not waived), only one compatible option (NODE_SHAPE +
     * shape_cycle). The tool must accept the explicit value and succeed.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void generator_explicit_oneCompatible_succeeds() throws Exception {
        stubSuccessPath();
        stubNodeTableWithStringColumn("community", "c1", "c2");
        stubFactory();
        org.cytoscape.view.presentation.property.values.NodeShape ellipse =
                org.cytoscape.view.presentation.property.NodeShapeVisualProperty.ELLIPSE;
        doReturn(Map.of("c1", ellipse, "c2", ellipse))
                .when(generatorService)
                .generateShapeCycle(any(), any());

        String rawJson =
                "{\"property_id\":\"NODE_SHAPE\",\"column_name\":\"community\","
                        + "\"column_type\":\"String\","
                        + "\"generator\":{\"waived\":false,\"parameter\":\"shape_cycle\"}}";
        String response = callToolRaw(rawJson);

        assertFalse("Expected no error", response.contains("\"isError\":true"));
        assertTrue(response.contains("\"generator\":\"shape_cycle\""));
    }

    /**
     * Test 4: generator explicitly provided (not waived), multiple compatible options
     * (NODE_FILL_COLOR + rainbow). The tool must accept the explicit value and succeed.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void generator_explicit_multipleCompatible_succeeds() throws Exception {
        stubSuccessPath();
        stubNodeTableWithStringColumn("GeneType", "kinase", "receptor");
        stubFactory();
        doReturn(Map.of("kinase", java.awt.Color.RED, "receptor", java.awt.Color.BLUE))
                .when(generatorService)
                .generateRainbow(any());

        String rawJson =
                "{\"property_id\":\"NODE_FILL_COLOR\",\"column_name\":\"GeneType\","
                        + "\"column_type\":\"String\","
                        + "\"generator\":{\"waived\":false,\"parameter\":\"rainbow\"}}";
        String response = callToolRaw(rawJson);

        assertFalse("Expected no error", response.contains("\"isError\":true"));
        assertTrue(response.contains("\"generator\":\"rainbow\""));
    }

    private static CallToolResult stubError(String marker) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(marker)))
                .isError(true)
                .build();
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
        // Node descendants: all node VPs
        @SuppressWarnings("unchecked")
        Set<VisualProperty<?>> nodeDescendants =
                Set.of(
                        BasicVisualLexicon.NODE_FILL_COLOR,
                        BasicVisualLexicon.NODE_SIZE,
                        BasicVisualLexicon.NODE_SHAPE,
                        BasicVisualLexicon.NODE_LABEL);
        when(lexicon.getAllDescendants(BasicVisualLexicon.NODE)).thenReturn((Set) nodeDescendants);
        // Edge descendants: edge VPs
        @SuppressWarnings("unchecked")
        Set<VisualProperty<?>> edgeDescendants =
                Set.of(BasicVisualLexicon.EDGE_WIDTH, BasicVisualLexicon.EDGE_LINE_TYPE);
        when(lexicon.getAllDescendants(BasicVisualLexicon.EDGE)).thenReturn((Set) edgeDescendants);
    }

    @SuppressWarnings("unchecked")
    private void stubFactory() {
        doReturn(mockMapping)
                .when(discreteMappingFactory)
                .createVisualMappingFunction(any(), any(), any());
    }

    private void stubNodeTableWithStringColumn(String columnName, String... values)
            throws Exception {
        CyColumn col = mockColumn(columnName, String.class, null);
        when(network.getDefaultNodeTable()).thenReturn(nodeTable);
        when(nodeTable.getColumn(columnName)).thenReturn(col);
        List<CyRow> rows =
                java.util.Arrays.stream(values)
                        .map(
                                v -> {
                                    CyRow row = mock(CyRow.class);
                                    when(row.get(columnName, String.class)).thenReturn(v);
                                    return row;
                                })
                        .toList();
        when(nodeTable.getAllRows()).thenReturn(rows);
    }

    @SuppressWarnings("unchecked")
    private CyColumn mockColumn(String name, Class<?> type, Class<?> listElementType) {
        CyColumn col = mock(CyColumn.class);
        when(col.getName()).thenReturn(name);
        when(col.getType()).thenReturn((Class) type);
        when(col.getListElementType()).thenReturn((Class) listElementType);
        return col;
    }

    private static String buildArgs(
            String propertyId, String columnName, String columnType, String generator) {
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
                + "\"generator\":\""
                + generator
                + "\""
                + "}";
    }

    private String callTool(String arguments) throws Exception {
        return callToolRaw(arguments);
    }

    private String callToolRaw(String arguments) throws Exception {
        String toolCall =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"create_discrete_mapping_generated\","
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
