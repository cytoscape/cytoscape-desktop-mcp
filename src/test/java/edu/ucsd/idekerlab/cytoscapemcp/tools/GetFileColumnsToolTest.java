package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.io.File;
import java.net.URL;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.idekerlab.cytoscapemcp.fixture.InMemoryTransport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * Exercises {@link GetFileColumnsTool} through a real {@link
 * io.modelcontextprotocol.server.McpSyncServer} backed by {@link InMemoryTransport}. Tests load
 * fixture files from {@code src/test/resources/fixture/} — no temp files needed.
 */
public class GetFileColumnsToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String INIT_REQUEST =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\","
                    + "\"params\":{\"protocolVersion\":\"2025-03-26\","
                    + "\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";

    private static final String INITIALIZED_NOTIFICATION =
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

    @Mock private ValidationService validationService;

    private GetFileColumnsTool tool;
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

    private GetFileColumnsTool buildTool(ValidationService vs) {
        return new GetFileColumnsTool(new TabularTypeConverter(), vs);
    }

    @After
    public void tearDown() {
        if (transport != null) {
            transport.close();
        }
    }

    // -----------------------------------------------------------------------
    // Text files — delimiter variations
    // -----------------------------------------------------------------------

    @Test
    public void csvCommaDelimited_returnsHeaders() throws Exception {
        String path = fixturePath("genes_comma.csv");
        String response = callTool(buildArgs(path, 44, true, null));

        assertNoError(response);
        JsonNode result = extractResult(response);
        JsonNode columns = result.get("columns");
        assertEquals(3, columns.size());
        assertEquals("Gene1", columns.get(0).get("name").asText());
        assertEquals("Gene2", columns.get(1).get("name").asText());
        assertEquals("Score", columns.get(2).get("name").asText());

        JsonNode sampleRows = result.get("sample_rows");
        assertTrue("Should have sample rows", sampleRows.size() > 0);
        assertEquals("TP53", sampleRows.get(0).get(0).asText());
    }

    @Test
    public void tsvTabDelimited_returnsHeaders() throws Exception {
        String path = fixturePath("genes_tab.tsv");
        String response = callTool(buildArgs(path, 9, true, null));

        assertNoError(response);
        JsonNode result = extractResult(response);
        JsonNode columns = result.get("columns");
        assertEquals(3, columns.size());
        assertEquals("Gene1", columns.get(0).get("name").asText());
        assertEquals("Score", columns.get(2).get("name").asText());
    }

    @Test
    public void pipeDelimited_returnsHeaders() throws Exception {
        String path = fixturePath("genes_pipe.txt");
        String response = callTool(buildArgs(path, 124, true, null));

        assertNoError(response);
        JsonNode result = extractResult(response);
        JsonNode columns = result.get("columns");
        assertEquals(3, columns.size());
        assertEquals("Gene1", columns.get(0).get("name").asText());
        assertEquals("Gene2", columns.get(1).get("name").asText());
    }

    @Test
    public void useHeaderRowFalse_returnsOrdinalNames() throws Exception {
        String path = fixturePath("genes_comma.csv");
        String response = callTool(buildArgs(path, 44, false, null));

        assertNoError(response);
        JsonNode result = extractResult(response);
        JsonNode columns = result.get("columns");
        assertEquals(3, columns.size());
        assertEquals("Column 1", columns.get(0).get("name").asText());
        assertEquals("Column 2", columns.get(1).get("name").asText());
        assertEquals("Column 3", columns.get(2).get("name").asText());

        // First data row (which was the header line) should appear in sample_rows
        JsonNode sampleRows = result.get("sample_rows");
        assertTrue("First row should be in sample_rows", sampleRows.size() > 0);
        assertEquals("Gene1", sampleRows.get(0).get(0).asText());
    }

    // -----------------------------------------------------------------------
    // Excel fixtures
    // -----------------------------------------------------------------------

    @Test
    public void excelXlsx_returnsColumnsFromNamedSheet() throws Exception {
        String path = fixturePath("network_data.xlsx");
        String response = callTool(buildArgs(path, null, true, "Sheet1"));

        assertNoError(response);
        JsonNode result = extractResult(response);
        JsonNode columns = result.get("columns");
        assertEquals(3, columns.size());
        assertEquals("Gene1", columns.get(0).get("name").asText());
        assertEquals("Gene2", columns.get(1).get("name").asText());
        assertEquals("Score", columns.get(2).get("name").asText());

        JsonNode sampleRows = result.get("sample_rows");
        assertTrue("Should have sample rows", sampleRows.size() > 0);
        assertEquals("TP53", sampleRows.get(0).get(0).asText());
    }

    @Test
    public void excelMultiSheet_secondSheetReturnsItsColumns() throws Exception {
        String path = fixturePath("network_data.xlsx");
        String response = callTool(buildArgs(path, null, true, "Interactions"));

        assertNoError(response);
        JsonNode result = extractResult(response);
        JsonNode columns = result.get("columns");
        assertEquals(2, columns.size());
        assertEquals("Source", columns.get(0).get("name").asText());
        assertEquals("Target", columns.get(1).get("name").asText());
    }

    @Test
    public void excelXlsx_useHeaderRowFalse_returnsOrdinalNames() throws Exception {
        String path = fixturePath("network_data.xlsx");
        String response = callTool(buildArgs(path, null, false, "Sheet1"));

        assertNoError(response);
        JsonNode result = extractResult(response);
        JsonNode columns = result.get("columns");
        assertEquals(3, columns.size());
        assertEquals("Column 1", columns.get(0).get("name").asText());
        assertEquals("Column 2", columns.get(1).get("name").asText());
        assertEquals("Column 3", columns.get(2).get("name").asText());

        // Row 0 (the header row) should appear in sample_rows
        JsonNode sampleRows = result.get("sample_rows");
        assertTrue("Row 0 should be in sample_rows", sampleRows.size() > 0);
        assertEquals("Gene1", sampleRows.get(0).get(0).asText());
    }

    // -----------------------------------------------------------------------
    // Numeric coercion — integer vs string delimiter_char_code
    // -----------------------------------------------------------------------

    @Test
    public void delimiterCharCodeAsInteger_succeeds() throws Exception {
        String path = fixturePath("genes_comma.csv");
        String response = callTool(buildArgs(path, 44, true, null)); // integer 44
        assertNoError(response);
        assertEquals(3, extractResult(response).get("columns").size());
    }

    @Test
    public void delimiterCharCodeAsString_succeeds() throws Exception {
        String path = fixturePath("genes_comma.csv");
        String toolCall =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"get_file_columns\",\"arguments\":{"
                        + "\"file_path\":\""
                        + path.replace("\\", "\\\\")
                        + "\","
                        + "\"use_header_row\":true,"
                        + "\"delimiter_char_code\":{\"waived\":false,\"parameter\":\"44\"}}}}";
        String response = callTool(toolCall);
        assertNoError(response);
        assertEquals(3, extractResult(response).get("columns").size());
    }

    // -----------------------------------------------------------------------
    // Error cases
    // -----------------------------------------------------------------------

    @Test
    public void fileNotFound_returnsError() throws Exception {
        String response = callTool(buildArgs("/nonexistent/path/data.csv", 44, true, null));

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue("Should mention file not found", response.contains("File not found"));
    }

    @Test
    public void excelSheetNotFound_returnsError() throws Exception {
        String path = fixturePath("network_data.xlsx");
        String response = callTool(buildArgs(path, null, true, "NoSuchSheet"));

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue("Should mention sheet not found", response.contains("Sheet not found"));
    }

    // -----------------------------------------------------------------------
    // Schema tests
    // -----------------------------------------------------------------------

    @Test
    public void inputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(GetFileColumnsTool.INPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
    }

    @Test
    public void inputSchema_typeIsObject() throws Exception {
        JsonNode schema = MAPPER.readTree(GetFileColumnsTool.INPUT_SCHEMA);
        assertEquals("object", schema.get("type").asText());
    }

    @Test
    public void inputSchema_requiredContainsFilePathAndUseHeaderRow() throws Exception {
        JsonNode required = MAPPER.readTree(GetFileColumnsTool.INPUT_SCHEMA).get("required");
        assertNotNull(required);
        assertTrue(required.isArray());
        List<String> reqList = new java.util.ArrayList<>();
        required.forEach(n -> reqList.add(n.asText()));
        assertTrue(reqList.contains("file_path"));
        assertTrue(reqList.contains("use_header_row"));
    }

    @Test
    public void inputSchema_propertyTypes() throws Exception {
        JsonNode props = MAPPER.readTree(GetFileColumnsTool.INPUT_SCHEMA).get("properties");
        assertEquals("string", props.at("/file_path/type").asText());
        assertEquals("boolean", props.at("/use_header_row/type").asText());
        // ConditionalParameter wrappers appear as "object" at the top level
        assertEquals("object", props.at("/delimiter_char_code/type").asText());
        assertEquals("object", props.at("/excel_sheet/type").asText());
        // Inner parameter types are nested under /properties/parameter
        assertEquals(
                "integer", props.at("/delimiter_char_code/properties/parameter/type").asText());
        assertEquals("string", props.at("/excel_sheet/properties/parameter/type").asText());
    }

    @Test
    public void inputSchema_allPropertiesHaveDescriptions() throws Exception {
        JsonNode props = MAPPER.readTree(GetFileColumnsTool.INPUT_SCHEMA).get("properties");
        for (String propName :
                List.of("file_path", "use_header_row", "delimiter_char_code", "excel_sheet")) {
            JsonNode desc = props.at("/" + propName + "/description");
            assertFalse("Property " + propName + " should have description", desc.isMissingNode());
            assertFalse(
                    "Property " + propName + " description should not be empty",
                    desc.asText().isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // Output schema tests
    // -----------------------------------------------------------------------

    @Test
    public void outputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(GetFileColumnsTool.OUTPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
    }

    @Test
    public void outputSchema_hasColumnsProperty() throws Exception {
        JsonNode props = MAPPER.readTree(GetFileColumnsTool.OUTPUT_SCHEMA).get("properties");
        assertNotNull("properties node must exist", props);
        assertFalse("columns property must exist", props.at("/columns").isMissingNode());
    }

    @Test
    public void outputSchema_hasSampleRowsProperty() throws Exception {
        JsonNode props = MAPPER.readTree(GetFileColumnsTool.OUTPUT_SCHEMA).get("properties");
        assertNotNull("properties node must exist", props);
        assertFalse("sample_rows property must exist", props.at("/sample_rows").isMissingNode());
    }

    // -----------------------------------------------------------------------
    // Type inference integration tests
    // -----------------------------------------------------------------------

    @Test
    public void csvWithNumericScore_inferredTypeIsDouble() throws Exception {
        String path = fixturePath("genes_comma.csv");
        String response = callTool(buildArgs(path, 44, true, null));

        assertNoError(response);
        JsonNode columns = extractResult(response).get("columns");
        // Score column (index 2) has values like 0.95 — should be inferred as double
        JsonNode scoreCol = columns.get(2);
        assertEquals("Score", scoreCol.get("name").asText());
        assertEquals("double", scoreCol.get("inferred_data_type").asText());
    }

    @Test
    public void csvWithStringColumns_geneColumnsAreString() throws Exception {
        String path = fixturePath("genes_comma.csv");
        String response = callTool(buildArgs(path, 44, true, null));

        assertNoError(response);
        JsonNode columns = extractResult(response).get("columns");
        // Gene1 and Gene2 contain gene names (strings)
        assertEquals("string", columns.get(0).get("inferred_data_type").asText());
        assertEquals("string", columns.get(1).get("inferred_data_type").asText());
    }

    @Test
    public void columnsHaveInferredDataTypeField() throws Exception {
        String path = fixturePath("genes_comma.csv");
        String response = callTool(buildArgs(path, 44, true, null));

        assertNoError(response);
        JsonNode columns = extractResult(response).get("columns");
        for (int i = 0; i < columns.size(); i++) {
            assertFalse(
                    "Column " + i + " should have inferred_data_type",
                    columns.get(i).get("inferred_data_type").isMissingNode());
        }
    }

    // -----------------------------------------------------------------------
    // Delegation tests — ValidationService error propagation
    // -----------------------------------------------------------------------

    @Test
    public void csvFile_absentDelimiterCharCode_propagatesValidationError() throws Exception {
        when(validationService.validateConditionalParams(anyString(), anyString(), any(), any()))
                .thenReturn(stubError("stub-error: delimiter_char_code missing"));

        String path = fixturePath("genes_comma.csv");
        String toolCall =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"get_file_columns\",\"arguments\":{"
                        + "\"file_path\":\""
                        + path.replace("\\", "\\\\")
                        + "\","
                        + "\"use_header_row\":true}}}";
        String response = callTool(toolCall);

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue(
                "Should contain stub message",
                response.contains("stub-error: delimiter_char_code missing"));
    }

    @Test
    public void excelFile_absentExcelSheet_propagatesValidationError() throws Exception {
        when(validationService.validateConditionalParams(anyString(), anyString(), any(), any()))
                .thenReturn(stubError("stub-error: excel_sheet missing"));

        String path = fixturePath("network_data.xlsx");
        String toolCall =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"get_file_columns\",\"arguments\":{"
                        + "\"file_path\":\""
                        + path.replace("\\", "\\\\")
                        + "\","
                        + "\"use_header_row\":true}}}";
        String response = callTool(toolCall);

        assertTrue("Should be error", response.contains("\"isError\":true"));
        assertTrue(
                "Should contain stub message",
                response.contains("stub-error: excel_sheet missing"));
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

    private static String fixturePath(String filename) throws Exception {
        URL url = GetFileColumnsToolTest.class.getClassLoader().getResource("fixture/" + filename);
        assertNotNull("Fixture file not found on classpath: " + filename, url);
        return new File(url.toURI()).getAbsolutePath();
    }

    private static String buildArgs(
            String filePath, Integer delimCode, boolean useHeaderRow, String excelSheet) {
        StringBuilder sb = new StringBuilder();
        sb.append(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"get_file_columns\",\"arguments\":{");
        sb.append("\"file_path\":\"").append(filePath.replace("\\", "\\\\")).append("\"");
        sb.append(",\"use_header_row\":").append(useHeaderRow);
        if (delimCode != null) {
            sb.append(",\"delimiter_char_code\":{\"waived\":false,\"parameter\":")
                    .append(delimCode)
                    .append("}");
        }
        if (excelSheet != null) {
            sb.append(",\"excel_sheet\":{\"waived\":false,\"parameter\":\"")
                    .append(excelSheet)
                    .append("\"}");
        }
        sb.append("}}}");
        return sb.toString();
    }

    private String callTool(String toolCallMessage) throws Exception {
        transport = new InMemoryTransport();
        transport.startServer("cytoscape-mcp-test", "0.0.0-test", List.of(tool.toSpec()));
        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(toolCallMessage);
        transport.await();
        return transport.getResponse();
    }

    private static void assertNoError(String response) {
        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
    }

    private static JsonNode extractResult(String response) throws Exception {
        // getResponse() returns all server output lines (init response + tool call result)
        String[] lines = response.trim().split("\n");
        String lastLine = lines[lines.length - 1];
        JsonNode root = MAPPER.readTree(lastLine);
        // structuredContent holds the GetFileColumnsCallResult object directly
        return root.at("/result/structuredContent");
    }
}
