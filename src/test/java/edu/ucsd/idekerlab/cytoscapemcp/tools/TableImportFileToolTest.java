package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import edu.ucsd.idekerlab.cytoscapemcp.fixture.InMemoryTransport;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.CyTableFactory;
import org.cytoscape.model.CyTableManager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link TableImportFileTool} via both direct {@link TableImportFileTool.InputValidator}
 * unit tests and full round-trips through {@link InMemoryTransport}.
 */
public class TableImportFileToolTest {

    private static final String INIT_REQUEST =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\","
                    + "\"params\":{\"protocolVersion\":\"2025-03-26\","
                    + "\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";

    private static final String INITIALIZED_NOTIFICATION =
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

    // Fixture file path (resolved from classpath)
    private static String TSV_FIXTURE_PATH;
    // Programmatically generated Excel fixture
    private static File EXCEL_FIXTURE;

    @Mock private CyApplicationManager appManager;
    @Mock private CyTableManager tableManager;
    @Mock private CyTableFactory tableFactory;
    @Mock private CyNetwork mockNetwork;
    @Mock private CyTable mockTable;
    @Mock private CyRow mockRow;
    @Mock private ValidationService validationService;

    private TabularTypeConverter typeConverter = new TabularTypeConverter();
    private InMemoryTransport transport;

    // -----------------------------------------------------------------------
    // Class-level setup / teardown
    // -----------------------------------------------------------------------

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Resolve TSV fixture from classpath
        java.net.URL tsvResource =
                TableImportFileToolTest.class
                        .getClassLoader()
                        .getResource("fixture/node_attrs.tsv");
        assertNotNull("node_attrs.tsv fixture must be on test classpath", tsvResource);
        TSV_FIXTURE_PATH = tsvResource.getFile();

        // Generate node_attrs.xlsx programmatically (no binary committed)
        EXCEL_FIXTURE = Files.createTempFile("node_attrs_", ".xlsx").toFile();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Data");
            String[][] data = {
                {"gene_id", "expression", "pvalue"},
                {"BRCA1", "1.5", "0.01"},
                {"TP53", "-2.3", "0.001"},
                {"EGFR", "0.8", "0.05"},
            };
            for (int r = 0; r < data.length; r++) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r);
                for (int c = 0; c < data[r].length; c++) {
                    row.createCell(c).setCellValue(data[r][c]);
                }
            }
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(EXCEL_FIXTURE)) {
                wb.write(fos);
            }
        }
    }

    @AfterClass
    public static void tearDownClass() {
        if (EXCEL_FIXTURE != null) EXCEL_FIXTURE.delete();
    }

    // -----------------------------------------------------------------------
    // Per-test setup / teardown
    // -----------------------------------------------------------------------

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Standard passthrough for unwrapToolInputValue
        doAnswer(
                        inv -> {
                            Object raw = inv.getArgument(0);
                            Class<?> type = inv.getArgument(1);
                            if (raw == null) return null;
                            if (raw instanceof Map<?, ?> map && map.containsKey("waived")) {
                                if (Boolean.TRUE.equals(map.get("waived"))) return null;
                                raw = map.get("parameter");
                            }
                            if (raw == null) return null;
                            return type.isInstance(raw) ? raw : null;
                        })
                .when(validationService)
                .unwrapToolInputValue(any(), any());
        ValidationService realVsForDetect = new ValidationService();
        doAnswer(
                        inv ->
                                realVsForDetect.detectDelimiter(
                                        inv.getArgument(0, File.class),
                                        inv.getArgument(1, String.class)))
                .when(validationService)
                .detectDelimiter(any(), any());
    }

    @After
    public void tearDown() {
        if (transport != null) transport.close();
    }

    // -----------------------------------------------------------------------
    // InputValidator unit tests
    // -----------------------------------------------------------------------

    @Test
    public void validator_missingFilePath_returnsError() {
        Map<String, Object> args = new HashMap<>();
        List<String> errors =
                new TableImportFileTool.InputValidator(validationService).validate(args);
        assertTrue(containsMatch(errors, "file_path"));
    }

    @Test
    public void validator_blankFilePath_returnsError() {
        Map<String, Object> args = Map.of("file_path", "");
        List<String> errors =
                new TableImportFileTool.InputValidator(validationService).validate(args);
        assertTrue(containsMatch(errors, "file_path"));
    }

    @Test
    public void validator_excelFile_missingExcelSheet_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", "/data/test.xlsx");
        // excel_sheet absent (not provided at all)
        List<String> errors =
                new TableImportFileTool.InputValidator(validationService).validate(args);
        assertTrue(containsMatch(errors, "excel_sheet") || containsMatch(errors, "Excel"));
    }

    @Test
    public void validator_excelFile_excelSheetWaived_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", "/data/test.xlsx");
        args.put("excel_sheet", Map.of("waived", true));
        List<String> errors =
                new TableImportFileTool.InputValidator(validationService).validate(args);
        assertTrue(containsMatch(errors, "excel_sheet") || containsMatch(errors, "Excel"));
    }

    @Test
    public void validator_decimalSeparatorWaived_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", "/data/test.tsv");
        args.put("decimal_separator", Map.of("waived", true));
        List<String> errors =
                new TableImportFileTool.InputValidator(validationService).validate(args);
        assertTrue(containsMatch(errors, "decimal_separator"));
    }

    @Test
    public void validator_listDelimiterWaived_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", "/data/test.tsv");
        args.put("list_delimiter", Map.of("waived", true));
        List<String> errors =
                new TableImportFileTool.InputValidator(validationService).validate(args);
        assertTrue(containsMatch(errors, "list_delimiter"));
    }

    @Test
    public void validator_invalidDataTypeCode_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", "/data/test.tsv");
        args.put("data_type_list", Map.of("waived", false, "parameter", "s,x,d"));
        List<String> errors =
                new TableImportFileTool.InputValidator(validationService).validate(args);
        assertTrue(containsMatch(errors, "data_type_list") && containsMatch(errors, "x"));
    }

    @Test
    public void validator_invalidWhereToImport_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", "/data/test.tsv");
        args.put("where_to_import", "bad_value");
        List<String> errors =
                new TableImportFileTool.InputValidator(validationService).validate(args);
        assertTrue(containsMatch(errors, "where_to_import"));
    }

    @Test
    public void validator_currentNetworkView_tableTypeWaived_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", "/data/test.tsv");
        args.put("where_to_import", "current_network_view");
        args.put("table_type", Map.of("waived", true));
        args.put("datafile_key_column_index", Map.of("waived", false, "parameter", 1));
        args.put("network_key_column_name", Map.of("waived", false, "parameter", "shared name"));
        List<String> errors =
                new TableImportFileTool.InputValidator(validationService).validate(args);
        assertTrue(containsMatch(errors, "table_type"));
    }

    @Test
    public void validator_currentNetworkView_invalidTableType_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", "/data/test.tsv");
        args.put("where_to_import", "current_network_view");
        args.put("table_type", Map.of("waived", false, "parameter", "invalid"));
        args.put("datafile_key_column_index", Map.of("waived", false, "parameter", 1));
        args.put("network_key_column_name", Map.of("waived", false, "parameter", "shared name"));
        List<String> errors =
                new TableImportFileTool.InputValidator(validationService).validate(args);
        assertTrue(containsMatch(errors, "table_type"));
    }

    @Test
    public void validator_currentNetworkView_keyColIndexWaived_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", "/data/test.tsv");
        args.put("where_to_import", "current_network_view");
        args.put("table_type", Map.of("waived", false, "parameter", "node"));
        args.put("datafile_key_column_index", Map.of("waived", true));
        args.put("network_key_column_name", Map.of("waived", false, "parameter", "shared name"));
        List<String> errors =
                new TableImportFileTool.InputValidator(validationService).validate(args);
        assertTrue(containsMatch(errors, "datafile_key_column_index"));
    }

    @Test
    public void validator_currentNetworkView_networkKeyColNameWaived_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", "/data/test.tsv");
        args.put("where_to_import", "current_network_view");
        args.put("table_type", Map.of("waived", false, "parameter", "node"));
        args.put("datafile_key_column_index", Map.of("waived", false, "parameter", 1));
        args.put("network_key_column_name", Map.of("waived", true));
        List<String> errors =
                new TableImportFileTool.InputValidator(validationService).validate(args);
        assertTrue(containsMatch(errors, "network_key_column_name"));
    }

    @Test
    public void validator_unassignedTable_newTableNameWaived_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", "/data/test.xlsx");
        args.put("excel_sheet", Map.of("waived", false, "parameter", "Sheet1"));
        args.put("where_to_import", "unassigned_table");
        args.put("new_table_name", Map.of("waived", true));
        List<String> errors =
                new TableImportFileTool.InputValidator(validationService).validate(args);
        assertTrue(containsMatch(errors, "new_table_name"));
    }

    @Test
    public void validator_validCurrentNetworkViewArgs_noErrors() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", "/data/test.tsv");
        args.put("excel_sheet", Map.of("waived", true));
        args.put("where_to_import", "current_network_view");
        args.put("table_type", Map.of("waived", false, "parameter", "node"));
        args.put("datafile_key_column_index", Map.of("waived", false, "parameter", 1));
        args.put("network_key_column_name", Map.of("waived", false, "parameter", "shared name"));
        args.put("new_table_name", Map.of("waived", true));
        List<String> errors =
                new TableImportFileTool.InputValidator(validationService).validate(args);
        assertTrue("Expected no validation errors but got: " + errors, errors.isEmpty());
    }

    @Test
    public void validator_validUnassignedTableArgs_noErrors() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", "/data/lookup.xlsx");
        args.put("excel_sheet", Map.of("waived", false, "parameter", "Sheet1"));
        args.put("where_to_import", "unassigned_table");
        args.put("new_table_name", Map.of("waived", false, "parameter", "Annotations"));
        args.put("table_type", Map.of("waived", true));
        args.put("datafile_key_column_index", Map.of("waived", true));
        args.put("network_key_column_name", Map.of("waived", true));
        List<String> errors =
                new TableImportFileTool.InputValidator(validationService).validate(args);
        assertTrue("Expected no validation errors but got: " + errors, errors.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Handler round-trip: current_network_view — TSV import success
    // -----------------------------------------------------------------------

    @Test
    public void currentNetworkView_tsvImport_allRowsMatch_success() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(mockNetwork);
        when(mockNetwork.getSUID()).thenReturn(100L);
        when(mockNetwork.getDefaultNodeTable()).thenReturn(mockTable);
        when(mockTable.getTitle()).thenReturn("default node");
        // All columns are new (getColumn returns null → added)
        when(mockTable.getColumn(anyString())).thenReturn(null);
        // All 3 gene rows match
        when(mockTable.getMatchingRows(anyString(), anyString())).thenReturn(List.of(mockRow));

        String response = callToolRaw(buildCurrentNetworkViewCall(TSV_FIXTURE_PATH, "node"));

        assertFalse("Should not be isError", response.contains("\"isError\":true"));
        // rows_imported = 3 (BRCA1, TP53, EGFR all match)
        assertTrue("rows_imported should be 3", response.contains("\\\"rows_imported\\\":3"));
        // columns_added = 2 (expression + pvalue; gene_id is key column, skipped)
        assertTrue("columns_added should be 2", response.contains("\\\"columns_added\\\":2"));
        // network_suid present
        assertTrue("network_suid should be 100", response.contains("\\\"network_suid\\\":100"));
        // table_name present
        assertTrue(
                "table_name should be default node",
                response.contains("\\\"table_name\\\":\\\"default node\\\""));
        // rows_unmatched should be absent (null → omitted)
        assertFalse("rows_unmatched should be absent", response.contains("rows_unmatched"));
    }

    @Test
    public void currentNetworkView_tsvImport_oneRowUnmatched_rowsUnmatchedPresent()
            throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(mockNetwork);
        when(mockNetwork.getSUID()).thenReturn(100L);
        when(mockNetwork.getDefaultNodeTable()).thenReturn(mockTable);
        when(mockTable.getTitle()).thenReturn("default node");
        when(mockTable.getColumn(anyString())).thenReturn(null);
        // Only BRCA1 and TP53 match; EGFR returns empty (unmatched)
        when(mockTable.getMatchingRows(anyString(), eq("BRCA1"))).thenReturn(List.of(mockRow));
        when(mockTable.getMatchingRows(anyString(), eq("TP53"))).thenReturn(List.of(mockRow));
        when(mockTable.getMatchingRows(anyString(), eq("EGFR"))).thenReturn(List.of());
        // Case-insensitive fallback: getAllRows returns empty list (so EGFR stays unmatched)
        when(mockTable.getAllRows()).thenReturn(List.of());

        String response = callToolRaw(buildCurrentNetworkViewCall(TSV_FIXTURE_PATH, "node"));

        assertFalse(response.contains("\"isError\":true"));
        assertTrue(response.contains("\\\"rows_imported\\\":2"));
        assertTrue(response.contains("\\\"rows_unmatched\\\":1"));
    }

    @Test
    public void currentNetworkView_noActiveNetwork_isError() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(null);

        String response = callToolRaw(buildCurrentNetworkViewCall(TSV_FIXTURE_PATH, "node"));

        assertTrue("Should be isError", response.contains("\"isError\":true"));
        assertTrue("Should mention no active network", response.contains("No active network"));
    }

    @Test
    public void currentNetworkView_fileNotFound_isError() throws Exception {
        String response =
                callToolRaw(buildCurrentNetworkViewCall("/nonexistent/path/data.tsv", "node"));

        assertTrue("Should be isError", response.contains("\"isError\":true"));
        assertTrue("Should mention file not found", response.contains("not found"));
    }

    @Test
    public void currentNetworkView_edgeTable_success() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(mockNetwork);
        when(mockNetwork.getSUID()).thenReturn(200L);
        when(mockNetwork.getDefaultEdgeTable()).thenReturn(mockTable);
        when(mockTable.getTitle()).thenReturn("default edge");
        when(mockTable.getColumn(anyString())).thenReturn(null);
        when(mockTable.getMatchingRows(anyString(), anyString())).thenReturn(List.of(mockRow));

        String response = callToolRaw(buildCurrentNetworkViewCall(TSV_FIXTURE_PATH, "edge"));

        assertFalse(response.contains("\"isError\":true"));
        assertTrue(response.contains("\\\"table_name\\\":\\\"default edge\\\""));
    }

    @Test
    public void currentNetworkView_existingColumn_countedAsUpdated() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(mockNetwork);
        when(mockNetwork.getSUID()).thenReturn(100L);
        when(mockNetwork.getDefaultNodeTable()).thenReturn(mockTable);
        when(mockTable.getTitle()).thenReturn("default node");
        // "expression" already exists (returns non-null column mock), "pvalue" is new
        org.cytoscape.model.CyColumn mockColumn =
                org.mockito.Mockito.mock(org.cytoscape.model.CyColumn.class);
        when(mockTable.getColumn("expression")).thenReturn(mockColumn);
        when(mockTable.getColumn("pvalue")).thenReturn(null);
        when(mockTable.getMatchingRows(anyString(), anyString())).thenReturn(List.of(mockRow));

        String response = callToolRaw(buildCurrentNetworkViewCall(TSV_FIXTURE_PATH, "node"));

        assertFalse(response.contains("\"isError\":true"));
        assertTrue("columns_added should be 1", response.contains("\\\"columns_added\\\":1"));
        assertTrue("columns_updated should be 1", response.contains("\\\"columns_updated\\\":1"));
    }

    // -----------------------------------------------------------------------
    // Handler round-trip: unassigned_table
    // -----------------------------------------------------------------------

    @Test
    public void unassignedTable_tsvImport_success() throws Exception {
        when(tableManager.getGlobalTables()).thenReturn(Set.of());
        when(tableFactory.createTable(anyString(), anyString(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(mockTable);
        when(mockTable.getRow(anyLong())).thenReturn(mockRow);
        when(mockTable.getTitle()).thenReturn("ExpressionData");

        String response = callToolRaw(buildUnassignedTableCall(TSV_FIXTURE_PATH, "ExpressionData"));

        assertFalse("Should not be isError", response.contains("\"isError\":true"));
        assertTrue("rows_imported should be 3", response.contains("\\\"rows_imported\\\":3"));
        assertTrue(
                "table_name should be ExpressionData",
                response.contains("\\\"table_name\\\":\\\"ExpressionData\\\""));
        // network_suid should be absent
        assertFalse("network_suid should be absent", response.contains("network_suid"));
        // rows_unmatched should be absent
        assertFalse("rows_unmatched should be absent", response.contains("rows_unmatched"));
        verify(tableManager).addTable(mockTable);
    }

    @Test
    public void unassignedTable_duplicateTableName_isError() throws Exception {
        CyTable existingTable = org.mockito.Mockito.mock(CyTable.class);
        when(existingTable.getTitle()).thenReturn("ExpressionData");
        when(tableManager.getGlobalTables()).thenReturn(Set.of(existingTable));

        String response = callToolRaw(buildUnassignedTableCall(TSV_FIXTURE_PATH, "ExpressionData"));

        assertTrue("Should be isError", response.contains("\"isError\":true"));
        assertTrue(
                "Should mention duplicate table name",
                response.contains("ExpressionData") && response.contains("already exists"));
        verify(tableFactory, never())
                .createTable(anyString(), anyString(), any(), anyBoolean(), anyBoolean());
    }

    // -----------------------------------------------------------------------
    // Handler round-trip: Excel import
    // -----------------------------------------------------------------------

    @Test
    public void unassignedTable_excelImport_success() throws Exception {
        when(tableManager.getGlobalTables()).thenReturn(Set.of());
        when(tableFactory.createTable(anyString(), anyString(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(mockTable);
        when(mockTable.getRow(anyLong())).thenReturn(mockRow);
        when(mockTable.getTitle()).thenReturn("GeneAnnotations");

        String response =
                callToolRaw(
                        buildUnassignedTableExcelCall(
                                EXCEL_FIXTURE.getAbsolutePath(), "Data", "GeneAnnotations"));

        assertFalse("Should not be isError", response.contains("\"isError\":true"));
        assertTrue("rows_imported should be 3", response.contains("\\\"rows_imported\\\":3"));
        assertTrue(
                "table_name should be GeneAnnotations",
                response.contains("\\\"table_name\\\":\\\"GeneAnnotations\\\""));
    }

    @Test
    public void currentNetworkView_excelImport_success() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(mockNetwork);
        when(mockNetwork.getSUID()).thenReturn(300L);
        when(mockNetwork.getDefaultNodeTable()).thenReturn(mockTable);
        when(mockTable.getTitle()).thenReturn("default node");
        when(mockTable.getColumn(anyString())).thenReturn(null);
        when(mockTable.getMatchingRows(anyString(), anyString())).thenReturn(List.of(mockRow));

        String response =
                callToolRaw(
                        buildCurrentNetworkViewExcelCall(
                                EXCEL_FIXTURE.getAbsolutePath(), "Data", "node"));

        assertFalse("Should not be isError", response.contains("\"isError\":true"));
        assertTrue("rows_imported should be 3", response.contains("\\\"rows_imported\\\":3"));
    }

    // -----------------------------------------------------------------------
    // Handler round-trip: missing required params (validation errors)
    // -----------------------------------------------------------------------

    @Test
    public void missingFilePath_isError() throws Exception {
        String call =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"table_import_file\","
                        + "\"arguments\":{}}}";
        String response = callToolRaw(call);
        assertTrue("Should be isError", response.contains("\"isError\":true"));
        assertTrue(response.contains("file_path"));
    }

    @Test
    public void invalidWhereToImport_isError() throws Exception {
        String call =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"table_import_file\","
                        + "\"arguments\":{"
                        + "\"file_path\":\"/data/test.tsv\","
                        + "\"where_to_import\":\"invalid_value\"}}}";
        String response = callToolRaw(call);
        assertTrue(response.contains("\"isError\":true"));
        assertTrue(response.contains("where_to_import"));
    }

    // -----------------------------------------------------------------------
    // Additional coverage tests
    // -----------------------------------------------------------------------

    /** Covers the "network" branch in resolveNetworkTable (line 619). */
    @Test
    public void currentNetworkView_networkTableType_success() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(mockNetwork);
        when(mockNetwork.getSUID()).thenReturn(400L);
        when(mockNetwork.getTable(any(), any())).thenReturn(mockTable);
        when(mockTable.getTitle()).thenReturn("default network");
        when(mockTable.getColumn(anyString())).thenReturn(null);
        when(mockTable.getMatchingRows(anyString(), anyString())).thenReturn(List.of(mockRow));

        String response = callToolRaw(buildCurrentNetworkViewCall(TSV_FIXTURE_PATH, "network"));

        assertFalse(response.contains("\"isError\":true"));
        assertTrue(response.contains("\\\"table_name\\\":\\\"default network\\\""));
    }

    /** Covers case-insensitive key fallback scan (lines 669-676). */
    @Test
    public void currentNetworkView_caseInsensitiveFallback_rowImported() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(mockNetwork);
        when(mockNetwork.getSUID()).thenReturn(500L);
        when(mockNetwork.getDefaultNodeTable()).thenReturn(mockTable);
        when(mockTable.getTitle()).thenReturn("default node");
        when(mockTable.getColumn(anyString())).thenReturn(null);
        // Exact match returns empty for all rows
        when(mockTable.getMatchingRows(anyString(), any())).thenReturn(List.of());
        // Case-insensitive scan: getAllRows returns mockRow with lower-case key
        when(mockTable.getAllRows()).thenReturn(List.of(mockRow));
        // mockRow.getRaw("shared name") returns lower-case variant
        when(mockRow.getRaw("shared name")).thenReturn("brca1");

        String response = callToolRaw(buildCurrentNetworkViewCall(TSV_FIXTURE_PATH, "node"));

        assertFalse(response.contains("\"isError\":true"));
        // Only "BRCA1" can match "brca1" case-insensitively; TP53 and EGFR do not match
        assertTrue(response.contains("\\\"rows_imported\\\":1"));
        assertTrue(response.contains("\\\"rows_unmatched\\\":2"));
    }

    /** Covers useHeaderRow=false branch (generateColumnNames) by treating all rows as data rows. */
    @Test
    public void currentNetworkView_useHeaderRowFalse_generatedColumnNames() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(mockNetwork);
        when(mockNetwork.getSUID()).thenReturn(700L);
        when(mockNetwork.getDefaultNodeTable()).thenReturn(mockTable);
        when(mockTable.getTitle()).thenReturn("default node");
        when(mockTable.getColumn(anyString())).thenReturn(null);
        when(mockTable.getMatchingRows(anyString(), any())).thenReturn(List.of(mockRow));

        String call =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"table_import_file\","
                        + "\"arguments\":{"
                        + "\"file_path\":\""
                        + escapeJson(TSV_FIXTURE_PATH)
                        + "\","
                        + "\"excel_sheet\":{\"waived\":true},"
                        + "\"use_header_row\":false,"
                        + "\"where_to_import\":\"current_network_view\","
                        + "\"table_type\":{\"waived\":false,\"parameter\":\"node\"},"
                        + "\"datafile_key_column_index\":{\"waived\":false,\"parameter\":1},"
                        + "\"network_key_column_name\":{\"waived\":false,\"parameter\":\"shared name\"},"
                        + "\"new_table_name\":{\"waived\":true}"
                        + "}}}";
        String response = callToolRaw(call);

        assertFalse(response.contains("\"isError\":true"));
        assertTrue(response.contains("\\\"rows_imported\\\":4"));
        assertTrue(response.contains("\\\"columns_added\\\":2"));
    }

    /** Covers decimal-separator replacement branch: decimalSep != "." */
    @Test
    public void currentNetworkView_decimalSeparatorComma_success() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(mockNetwork);
        when(mockNetwork.getSUID()).thenReturn(600L);
        when(mockNetwork.getDefaultNodeTable()).thenReturn(mockTable);
        when(mockTable.getTitle()).thenReturn("default node");
        when(mockTable.getColumn(anyString())).thenReturn(null);
        when(mockTable.getMatchingRows(anyString(), anyString())).thenReturn(List.of(mockRow));

        String call =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"table_import_file\","
                        + "\"arguments\":{"
                        + "\"file_path\":\""
                        + escapeJson(TSV_FIXTURE_PATH)
                        + "\","
                        + "\"excel_sheet\":{\"waived\":true},"
                        + "\"decimal_separator\":{\"waived\":false,\"parameter\":\",\"},"
                        + "\"data_type_list\":{\"waived\":false,\"parameter\":\"s,d,d\"},"
                        + "\"where_to_import\":\"current_network_view\","
                        + "\"table_type\":{\"waived\":false,\"parameter\":\"node\"},"
                        + "\"datafile_key_column_index\":{\"waived\":false,\"parameter\":1},"
                        + "\"network_key_column_name\":{\"waived\":false,\"parameter\":\"shared name\"},"
                        + "\"new_table_name\":{\"waived\":true}"
                        + "}}}";
        String response = callToolRaw(call);

        assertFalse(response.contains("\"isError\":true"));
        assertTrue(response.contains("\\\"rows_imported\\\":3"));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String callToolRaw(String toolCallJson) throws Exception {
        transport = new InMemoryTransport();
        TableImportFileTool tool =
                new TableImportFileTool(
                        appManager, tableManager, tableFactory, typeConverter, validationService);
        transport.startServer("test", "0.0.0-test", List.of(tool.toSpec()));

        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(toolCallJson);
        transport.await();

        return transport.getResponse();
    }

    private String buildCurrentNetworkViewCall(String filePath, String tableType) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"table_import_file\","
                + "\"arguments\":{"
                + "\"file_path\":\""
                + escapeJson(filePath)
                + "\","
                + "\"excel_sheet\":{\"waived\":true},"
                + "\"where_to_import\":\"current_network_view\","
                + "\"table_type\":{\"waived\":false,\"parameter\":\""
                + tableType
                + "\"},"
                + "\"datafile_key_column_index\":{\"waived\":false,\"parameter\":1},"
                + "\"network_key_column_name\":{\"waived\":false,\"parameter\":\"shared name\"},"
                + "\"new_table_name\":{\"waived\":true}"
                + "}}}";
    }

    private String buildUnassignedTableCall(String filePath, String tableName) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"table_import_file\","
                + "\"arguments\":{"
                + "\"file_path\":\""
                + escapeJson(filePath)
                + "\","
                + "\"excel_sheet\":{\"waived\":true},"
                + "\"where_to_import\":\"unassigned_table\","
                + "\"new_table_name\":{\"waived\":false,\"parameter\":\""
                + tableName
                + "\"},"
                + "\"table_type\":{\"waived\":true},"
                + "\"datafile_key_column_index\":{\"waived\":true},"
                + "\"network_key_column_name\":{\"waived\":true}"
                + "}}}";
    }

    private String buildUnassignedTableExcelCall(
            String filePath, String sheetName, String tableName) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"table_import_file\","
                + "\"arguments\":{"
                + "\"file_path\":\""
                + escapeJson(filePath)
                + "\","
                + "\"excel_sheet\":{\"waived\":false,\"parameter\":\""
                + sheetName
                + "\"},"
                + "\"where_to_import\":\"unassigned_table\","
                + "\"new_table_name\":{\"waived\":false,\"parameter\":\""
                + tableName
                + "\"},"
                + "\"table_type\":{\"waived\":true},"
                + "\"datafile_key_column_index\":{\"waived\":true},"
                + "\"network_key_column_name\":{\"waived\":true}"
                + "}}}";
    }

    private String buildCurrentNetworkViewExcelCall(
            String filePath, String sheetName, String tableType) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"table_import_file\","
                + "\"arguments\":{"
                + "\"file_path\":\""
                + escapeJson(filePath)
                + "\","
                + "\"excel_sheet\":{\"waived\":false,\"parameter\":\""
                + sheetName
                + "\"},"
                + "\"where_to_import\":\"current_network_view\","
                + "\"table_type\":{\"waived\":false,\"parameter\":\""
                + tableType
                + "\"},"
                + "\"datafile_key_column_index\":{\"waived\":false,\"parameter\":1},"
                + "\"network_key_column_name\":{\"waived\":false,\"parameter\":\"shared name\"},"
                + "\"new_table_name\":{\"waived\":true}"
                + "}}}";
    }

    // -----------------------------------------------------------------------
    // detectDelimiter unit tests (ValidationService)
    // -----------------------------------------------------------------------

    /** TSV fixture (tab-separated) should be detected as tab (9). */
    @Test
    public void detectDelimiter_tsvFile_returnsTab() throws Exception {
        ValidationService vs = new ValidationService();
        int detected = vs.detectDelimiter(new File(TSV_FIXTURE_PATH), null);
        assertTrue("Expected tab (9), got " + detected, detected == 9);
    }

    /** A comma-separated temp file should be detected as comma (44). */
    @Test
    public void detectDelimiter_csvContent_returnsComma() throws Exception {
        File csv = Files.createTempFile("detect_", ".csv").toFile();
        try {
            Files.writeString(
                    csv.toPath(),
                    "Gene Name,Gene ID,Fold Change\nCSF3,ENSG00000108342,4.4\nTP53,ENSG00000141510,2.1\n");
            ValidationService vs = new ValidationService();
            int detected = vs.detectDelimiter(csv, null);
            assertTrue("Expected comma (44), got " + detected, detected == 44);
        } finally {
            csv.delete();
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static boolean containsMatch(List<String> errors, String substring) {
        return errors.stream().anyMatch(e -> e.toLowerCase().contains(substring.toLowerCase()));
    }
}
