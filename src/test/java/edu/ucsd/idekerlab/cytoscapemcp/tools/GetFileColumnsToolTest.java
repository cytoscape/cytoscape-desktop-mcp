package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.io.File;
import java.net.URL;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.idekerlab.cytoscapemcp.fixture.InMemoryTransport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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

    private final GetFileColumnsTool tool = new GetFileColumnsTool();
    private InMemoryTransport transport;

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
        assertEquals("Gene1", columns.get(0).asText());
        assertEquals("Gene2", columns.get(1).asText());
        assertEquals("Score", columns.get(2).asText());

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
        assertEquals("Gene1", columns.get(0).asText());
        assertEquals("Score", columns.get(2).asText());
    }

    @Test
    public void pipeDelimited_returnsHeaders() throws Exception {
        String path = fixturePath("genes_pipe.txt");
        String response = callTool(buildArgs(path, 124, true, null));

        assertNoError(response);
        JsonNode result = extractResult(response);
        JsonNode columns = result.get("columns");
        assertEquals(3, columns.size());
        assertEquals("Gene1", columns.get(0).asText());
        assertEquals("Gene2", columns.get(1).asText());
    }

    @Test
    public void useHeaderRowFalse_returnsOrdinalNames() throws Exception {
        String path = fixturePath("genes_comma.csv");
        String response = callTool(buildArgs(path, 44, false, null));

        assertNoError(response);
        JsonNode result = extractResult(response);
        JsonNode columns = result.get("columns");
        assertEquals(3, columns.size());
        assertEquals("Column 1", columns.get(0).asText());
        assertEquals("Column 2", columns.get(1).asText());
        assertEquals("Column 3", columns.get(2).asText());

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
        assertEquals("Gene1", columns.get(0).asText());
        assertEquals("Gene2", columns.get(1).asText());
        assertEquals("Score", columns.get(2).asText());

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
        assertEquals("Source", columns.get(0).asText());
        assertEquals("Target", columns.get(1).asText());
    }

    @Test
    public void excelXlsx_useHeaderRowFalse_returnsOrdinalNames() throws Exception {
        String path = fixturePath("network_data.xlsx");
        String response = callTool(buildArgs(path, null, false, "Sheet1"));

        assertNoError(response);
        JsonNode result = extractResult(response);
        JsonNode columns = result.get("columns");
        assertEquals(3, columns.size());
        assertEquals("Column 1", columns.get(0).asText());
        assertEquals("Column 2", columns.get(1).asText());
        assertEquals("Column 3", columns.get(2).asText());

        // Row 0 (the header row) should appear in sample_rows
        JsonNode sampleRows = result.get("sample_rows");
        assertTrue("Row 0 should be in sample_rows", sampleRows.size() > 0);
        assertEquals("Gene1", sampleRows.get(0).get(0).asText());
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
        assertEquals("integer", props.at("/delimiter_char_code/type").asText());
        assertEquals("string", props.at("/excel_sheet/type").asText());
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
            sb.append(",\"delimiter_char_code\":").append(delimCode);
        }
        if (excelSheet != null) {
            sb.append(",\"excel_sheet\":\"").append(excelSheet).append("\"");
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
        String text = root.at("/result/content/0/text").asText();
        return MAPPER.readTree(text);
    }
}
