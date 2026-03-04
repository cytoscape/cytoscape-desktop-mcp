package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.List;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import edu.ucsd.idekerlab.cytoscapemcp.fixture.InMemoryTransport;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Exercises {@link InspectTabularFileTool} through a real {@link
 * io.modelcontextprotocol.server.McpSyncServer} backed by {@link InMemoryTransport}. Tests create
 * real temporary files — no mocks needed since this is a pure file-inspection tool.
 */
public class InspectTabularFileToolTest {

    // --- JSON-RPC protocol messages ----------------------------------------

    private static final String INIT_REQUEST =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\","
                    + "\"params\":{\"protocolVersion\":\"2025-03-26\","
                    + "\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";

    private static final String INITIALIZED_NOTIFICATION =
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

    private InspectTabularFileTool tool = new InspectTabularFileTool();
    private InMemoryTransport transport;

    @After
    public void tearDown() {
        if (transport != null) {
            transport.close();
        }
    }

    // -----------------------------------------------------------------------
    // Excel .xlsx — returns sheet names
    // -----------------------------------------------------------------------

    @Test
    public void excelXlsx_returnsSheetNames() throws Exception {
        File tempFile = File.createTempFile("test-inspect-", ".xlsx");
        tempFile.deleteOnExit();

        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("Interactions");
            wb.createSheet("NodeAttributes");
            wb.createSheet("Metadata");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                wb.write(fos);
            }
        }

        String response = callTool(toolCallJson(tempFile.getAbsolutePath()));

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should be Excel", response.contains("\\\"is_excel\\\":true"));
        assertTrue("Should contain Interactions", response.contains("Interactions"));
        assertTrue("Should contain NodeAttributes", response.contains("NodeAttributes"));
        assertTrue("Should contain Metadata", response.contains("Metadata"));
    }

    // -----------------------------------------------------------------------
    // Excel .xls — returns sheet names
    // -----------------------------------------------------------------------

    @Test
    public void excelXls_returnsSheetNames() throws Exception {
        File tempFile = File.createTempFile("test-inspect-", ".xls");
        tempFile.deleteOnExit();

        try (Workbook wb = new HSSFWorkbook()) {
            wb.createSheet("Sheet1");
            wb.createSheet("Sheet2");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                wb.write(fos);
            }
        }

        String response = callTool(toolCallJson(tempFile.getAbsolutePath()));

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should be Excel", response.contains("\\\"is_excel\\\":true"));
        assertTrue("Should contain Sheet1", response.contains("Sheet1"));
        assertTrue("Should contain Sheet2", response.contains("Sheet2"));
    }

    // -----------------------------------------------------------------------
    // CSV file — returns detected extension
    // -----------------------------------------------------------------------

    @Test
    public void csvFile_returnsDetectedExtension() throws Exception {
        File tempFile = File.createTempFile("test-inspect-", ".csv");
        tempFile.deleteOnExit();

        try (FileWriter fw = new FileWriter(tempFile)) {
            fw.write("col1,col2\nval1,val2\n");
        }

        String response = callTool(toolCallJson(tempFile.getAbsolutePath()));

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should not be Excel", response.contains("\\\"is_excel\\\":false"));
        assertTrue(
                "Should detect .csv extension",
                response.contains("\\\"detected_extension\\\":\\\".csv\\\""));
    }

    // -----------------------------------------------------------------------
    // TSV file — returns detected extension
    // -----------------------------------------------------------------------

    @Test
    public void tsvFile_returnsDetectedExtension() throws Exception {
        File tempFile = File.createTempFile("test-inspect-", ".tsv");
        tempFile.deleteOnExit();

        try (FileWriter fw = new FileWriter(tempFile)) {
            fw.write("col1\tcol2\nval1\tval2\n");
        }

        String response = callTool(toolCallJson(tempFile.getAbsolutePath()));

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should not be Excel", response.contains("\\\"is_excel\\\":false"));
        assertTrue(
                "Should detect .tsv extension",
                response.contains("\\\"detected_extension\\\":\\\".tsv\\\""));
    }

    // -----------------------------------------------------------------------
    // File not found — returns error
    // -----------------------------------------------------------------------

    @Test
    public void fileNotFound_returnsError() throws Exception {
        String response = callTool(toolCallJson("/nonexistent/file.xlsx"));

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue("Should mention File not found", response.contains("File not found"));
    }

    // -----------------------------------------------------------------------
    // Corrupt .xlsx file — returns detected extension (not Excel)
    // -----------------------------------------------------------------------

    @Test
    public void corruptFile_returnsDetectedExtension() throws Exception {
        File tempFile = File.createTempFile("test-inspect-", ".xlsx");
        tempFile.deleteOnExit();

        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(new byte[] {0x00, 0x01, 0x02, 0x03, 0x04, 0x05});
        }

        String response = callTool(toolCallJson(tempFile.getAbsolutePath()));

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should not be Excel", response.contains("\\\"is_excel\\\":false"));
        assertTrue(
                "Should detect .xlsx extension",
                response.contains("\\\"detected_extension\\\":\\\".xlsx\\\""));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String toolCallJson(String filePath) {
        // Escape backslashes for Windows paths in JSON
        String escapedPath = filePath.replace("\\", "\\\\");
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"inspect_tabular_file\","
                + "\"arguments\":{\"file_path\":\""
                + escapedPath
                + "\"}}}";
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
}
