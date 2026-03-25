package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import edu.ucsd.idekerlab.cytoscapemcp.McpSchema;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

/**
 * Unit tests for {@link ValidationService} exercising {@code validateConditionalParams}, {@code
 * validatePresence}, {@code convertToolArgs}, {@code unwrapToolInputValue}, and {@code
 * unwrapToolInputDataColumns} directly, without going through the MCP protocol stack.
 */
public class ValidationServiceTest {

    private final ValidationService service = new ValidationService();

    // -----------------------------------------------------------------------
    // Static conditional lists (mirrors what each handler passes)
    // -----------------------------------------------------------------------

    private static final List<ValidationService.ConditionalParam> NDEX_CONDITIONALS =
            List.of(
                    new ValidationService.ConditionalParam(
                            "network_id",
                            "the NDEx network UUID required to load the network from NDEx",
                            false));

    private static final List<ValidationService.ConditionalParam> NETWORK_FILE_CONDITIONALS =
            List.of(
                    new ValidationService.ConditionalParam(
                            "file_path", "the absolute path to the network file to import", false));

    private static final List<ValidationService.ConditionalParam> TABULAR_CONDITIONALS =
            List.of(
                    new ValidationService.ConditionalParam(
                            "file_path", "the absolute path to the tabular file to import", false),
                    new ValidationService.ConditionalParam(
                            "source_column",
                            "the file column that provides source node names for each edge row",
                            false),
                    new ValidationService.ConditionalParam(
                            "target_column",
                            "the file column that provides target node names for each edge row",
                            false),
                    new ValidationService.ConditionalParam(
                            "use_header_row",
                            "whether the first row of the file is a header row",
                            false),
                    new ValidationService.ConditionalParam(
                            "node_attributes_source_columns",
                            "which file columns should be attached as attributes on source nodes",
                            true),
                    new ValidationService.ConditionalParam(
                            "node_attributes_target_columns",
                            "which file columns should be attached as attributes on target nodes",
                            true));

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Builds a wrapper Map: {@code {"waived": waived, "parameter": value}}. */
    private static Map<String, Object> wrapperOf(boolean waived, Object value) {
        Map<String, Object> w = new HashMap<>();
        w.put("waived", waived);
        w.put("parameter", value);
        return w;
    }

    /** Returns the text of the first content element from a {@link CallToolResult} error. */
    private static String errorText(CallToolResult result) {
        assertNotNull("Expected a non-null error result", result);
        assertTrue("Expected isError=true", Boolean.TRUE.equals(result.isError()));
        io.modelcontextprotocol.spec.McpSchema.TextContent tc =
                (io.modelcontextprotocol.spec.McpSchema.TextContent) result.content().get(0);
        return tc.text();
    }

    // -----------------------------------------------------------------------
    // validateConditionalParams — ndex source
    // -----------------------------------------------------------------------

    @Test
    public void ndex_networkIdPresent_returnsNull() {
        Map<String, Object> args = new HashMap<>();
        args.put("network_id", wrapperOf(false, "some-uuid"));
        assertNull(service.validateConditionalParams("source", "ndex", args, NDEX_CONDITIONALS));
    }

    @Test
    public void ndex_networkIdAbsent_returnsError() {
        Map<String, Object> args = new HashMap<>();
        CallToolResult result =
                service.validateConditionalParams("source", "ndex", args, NDEX_CONDITIONALS);
        String msg = errorText(result);
        assertTrue("Should name network_id", msg.contains("network_id"));
        assertTrue("Should mention waived", msg.contains("waived"));
        assertTrue("Should refer to parameter description", msg.contains("parameter description"));
        assertTrue("Should mention source=ndex", msg.contains("source=ndex"));
    }

    @Test
    public void ndex_networkIdWaivedTrue_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("network_id", wrapperOf(true, null));
        CallToolResult result =
                service.validateConditionalParams("source", "ndex", args, NDEX_CONDITIONALS);
        String msg = errorText(result);
        assertTrue("Should name network_id", msg.contains("network_id"));
        assertTrue(
                "Should say cannot be intentionally omitted",
                msg.contains("cannot be intentionally omitted"));
    }

    // -----------------------------------------------------------------------
    // validateConditionalParams — network-file source
    // -----------------------------------------------------------------------

    @Test
    public void networkFile_filePathPresent_returnsNull() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", wrapperOf(false, "/path/to/file.sif"));
        assertNull(
                service.validateConditionalParams(
                        "source", "network-file", args, NETWORK_FILE_CONDITIONALS));
    }

    @Test
    public void networkFile_filePathAbsent_returnsError() {
        Map<String, Object> args = new HashMap<>();
        CallToolResult result =
                service.validateConditionalParams(
                        "source", "network-file", args, NETWORK_FILE_CONDITIONALS);
        String msg = errorText(result);
        assertTrue("Should name file_path", msg.contains("file_path"));
        assertTrue("Should mention waived", msg.contains("waived"));
        assertTrue("Should refer to parameter description", msg.contains("parameter description"));
    }

    @Test
    public void networkFile_filePathWaivedTrue_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", wrapperOf(true, null));
        CallToolResult result =
                service.validateConditionalParams(
                        "source", "network-file", args, NETWORK_FILE_CONDITIONALS);
        String msg = errorText(result);
        assertTrue("Should name file_path", msg.contains("file_path"));
        assertTrue(
                "Should say cannot be intentionally omitted",
                msg.contains("cannot be intentionally omitted"));
    }

    // -----------------------------------------------------------------------
    // validateConditionalParams — tabular-file: all required present
    // -----------------------------------------------------------------------

    @Test
    public void tabular_allRequiredPresent_returnsNull() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", wrapperOf(false, "/data.csv"));
        args.put("source_column", wrapperOf(false, "Gene1"));
        args.put("target_column", wrapperOf(false, "Gene2"));
        args.put("use_header_row", wrapperOf(false, true));
        args.put("node_attributes_source_columns", wrapperOf(true, null));
        args.put("node_attributes_target_columns", wrapperOf(true, null));
        assertNull(
                service.validateConditionalParams(
                        "source", "tabular-file", args, TABULAR_CONDITIONALS));
    }

    // -----------------------------------------------------------------------
    // validateConditionalParams — tabular-file: each param absent (chain stops)
    // -----------------------------------------------------------------------

    @Test
    public void tabular_filePathAbsent_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("source_column", wrapperOf(false, "Gene1"));
        args.put("target_column", wrapperOf(false, "Gene2"));
        args.put("use_header_row", wrapperOf(false, true));
        args.put("node_attributes_source_columns", wrapperOf(true, null));
        args.put("node_attributes_target_columns", wrapperOf(true, null));

        CallToolResult result =
                service.validateConditionalParams(
                        "source", "tabular-file", args, TABULAR_CONDITIONALS);
        String msg = errorText(result);
        assertTrue("Should name file_path", msg.contains("file_path"));
        assertTrue("Should mention waived", msg.contains("waived"));
        assertTrue("Should refer to parameter description", msg.contains("parameter description"));
    }

    @Test
    public void tabular_sourceColumnAbsent_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", wrapperOf(false, "/data.csv"));
        args.put("target_column", wrapperOf(false, "Gene2"));
        args.put("use_header_row", wrapperOf(false, true));
        args.put("node_attributes_source_columns", wrapperOf(true, null));
        args.put("node_attributes_target_columns", wrapperOf(true, null));

        CallToolResult result =
                service.validateConditionalParams(
                        "source", "tabular-file", args, TABULAR_CONDITIONALS);
        String msg = errorText(result);
        assertTrue("Should name source_column", msg.contains("source_column"));
        assertTrue("Should mention waived", msg.contains("waived"));
        assertTrue("Should refer to parameter description", msg.contains("parameter description"));
    }

    @Test
    public void tabular_targetColumnAbsent_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", wrapperOf(false, "/data.csv"));
        args.put("source_column", wrapperOf(false, "Gene1"));
        args.put("use_header_row", wrapperOf(false, true));
        args.put("node_attributes_source_columns", wrapperOf(true, null));
        args.put("node_attributes_target_columns", wrapperOf(true, null));

        CallToolResult result =
                service.validateConditionalParams(
                        "source", "tabular-file", args, TABULAR_CONDITIONALS);
        String msg = errorText(result);
        assertTrue("Should name target_column", msg.contains("target_column"));
        assertTrue("Should mention waived", msg.contains("waived"));
        assertTrue("Should refer to parameter description", msg.contains("parameter description"));
    }

    @Test
    public void tabular_useHeaderRowAbsent_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", wrapperOf(false, "/data.csv"));
        args.put("source_column", wrapperOf(false, "Gene1"));
        args.put("target_column", wrapperOf(false, "Gene2"));
        args.put("node_attributes_source_columns", wrapperOf(true, null));
        args.put("node_attributes_target_columns", wrapperOf(true, null));

        CallToolResult result =
                service.validateConditionalParams(
                        "source", "tabular-file", args, TABULAR_CONDITIONALS);
        String msg = errorText(result);
        assertTrue("Should name use_header_row", msg.contains("use_header_row"));
        assertTrue("Should mention waived", msg.contains("waived"));
        assertTrue("Should refer to parameter description", msg.contains("parameter description"));
    }

    @Test
    public void tabular_nodeAttrSourceColumnsAbsent_returnsNull() {
        // waiveable params may be absent — absence is treated as implicitly omitted (no error)
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", wrapperOf(false, "/data.csv"));
        args.put("source_column", wrapperOf(false, "Gene1"));
        args.put("target_column", wrapperOf(false, "Gene2"));
        args.put("use_header_row", wrapperOf(false, true));
        args.put("node_attributes_target_columns", wrapperOf(true, null));

        assertNull(
                service.validateConditionalParams(
                        "source", "tabular-file", args, TABULAR_CONDITIONALS));
    }

    @Test
    public void tabular_nodeAttrTargetColumnsAbsent_returnsNull() {
        // waiveable params may be absent — absence is treated as implicitly omitted (no error)
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", wrapperOf(false, "/data.csv"));
        args.put("source_column", wrapperOf(false, "Gene1"));
        args.put("target_column", wrapperOf(false, "Gene2"));
        args.put("use_header_row", wrapperOf(false, true));
        args.put("node_attributes_source_columns", wrapperOf(true, null));

        assertNull(
                service.validateConditionalParams(
                        "source", "tabular-file", args, TABULAR_CONDITIONALS));
    }

    // -----------------------------------------------------------------------
    // validateConditionalParams — tabular-file: waive behaviour
    // -----------------------------------------------------------------------

    @Test
    public void tabular_nodeAttrSourceWaivedTrue_returnsNull() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", wrapperOf(false, "/data.csv"));
        args.put("source_column", wrapperOf(false, "Gene1"));
        args.put("target_column", wrapperOf(false, "Gene2"));
        args.put("use_header_row", wrapperOf(false, true));
        args.put("node_attributes_source_columns", wrapperOf(true, null)); // waived=true is valid
        args.put("node_attributes_target_columns", wrapperOf(true, null));
        assertNull(
                service.validateConditionalParams(
                        "source", "tabular-file", args, TABULAR_CONDITIONALS));
    }

    @Test
    public void tabular_filePathWaivedTrue_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", wrapperOf(true, null));
        args.put("source_column", wrapperOf(false, "Gene1"));
        args.put("target_column", wrapperOf(false, "Gene2"));
        args.put("use_header_row", wrapperOf(false, true));
        args.put("node_attributes_source_columns", wrapperOf(true, null));
        args.put("node_attributes_target_columns", wrapperOf(true, null));

        CallToolResult result =
                service.validateConditionalParams(
                        "source", "tabular-file", args, TABULAR_CONDITIONALS);
        String msg = errorText(result);
        assertTrue("Should name file_path", msg.contains("file_path"));
        assertTrue(
                "Should say cannot be intentionally omitted",
                msg.contains("cannot be intentionally omitted"));
    }

    // -----------------------------------------------------------------------
    // validateConditionalParams — empty conditionals list
    // -----------------------------------------------------------------------

    @Test
    public void emptyConditionalsList_returnsNull() {
        assertNull(
                service.validateConditionalParams(
                        "source", "any-value", new HashMap<>(), List.of()));
    }

    // -----------------------------------------------------------------------
    // validatePresence directly
    // -----------------------------------------------------------------------

    @Test
    public void validatePresence_absent_returnsError() {
        Map<String, Object> args = new HashMap<>();
        CallToolResult result =
                service.validatePresence(
                        args, "my_param", "source", "test-target", "some purpose", true);
        String msg = errorText(result);
        assertTrue("Should name the param", msg.contains("my_param"));
        assertTrue("Should mention dependent param context", msg.contains("source=test-target"));
        assertTrue("Should mention waived", msg.contains("waived"));
        assertTrue("Should refer to parameter description", msg.contains("parameter description"));
    }

    @Test
    public void validatePresence_presentWaivedFalse_returnsNull() {
        Map<String, Object> args = new HashMap<>();
        args.put("my_param", wrapperOf(false, "value"));
        assertNull(
                service.validatePresence(
                        args, "my_param", "source", "test-target", "some purpose", true));
    }

    @Test
    public void validatePresence_cannotWaive_waivedTrue_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("my_param", wrapperOf(true, null));
        CallToolResult result =
                service.validatePresence(
                        args, "my_param", "source", "test-target", "some purpose", true);
        String msg = errorText(result);
        assertTrue("Should name the param", msg.contains("my_param"));
        assertTrue(
                "Should say cannot be intentionally omitted",
                msg.contains("cannot be intentionally omitted"));
    }

    @Test
    public void validatePresence_canWaive_waivedTrue_returnsNull() {
        Map<String, Object> args = new HashMap<>();
        args.put("my_param", wrapperOf(true, null));
        assertNull(
                service.validatePresence(
                        args, "my_param", "source", "test-target", "some purpose", false));
    }

    // -----------------------------------------------------------------------
    // convertToolArgs
    // -----------------------------------------------------------------------

    @Test
    public void convertToolArgs_conditionalWrapper_detectedCorrectly() {
        Map<String, Object> args = new HashMap<>();
        args.put("network_id", wrapperOf(false, "some-uuid"));
        Map<String, McpSchema.ToolInputParam> result = service.convertToolArgs(args);

        assertTrue(result.get("network_id").isConditional());
        assertFalse(result.get("network_id").conditionalParameter().waived());
        assertEquals("some-uuid", result.get("network_id").conditionalParameter().value());
    }

    @Test
    public void convertToolArgs_waivedWrapper_detectedCorrectly() {
        Map<String, Object> args = new HashMap<>();
        args.put("my_param", wrapperOf(true, null));
        Map<String, McpSchema.ToolInputParam> result = service.convertToolArgs(args);

        assertTrue(result.get("my_param").isConditional());
        assertTrue(result.get("my_param").conditionalParameter().waived());
    }

    @Test
    public void convertToolArgs_nonMapValue_isRequiredParameter() {
        Map<String, Object> args = new HashMap<>();
        args.put("source", "ndex");
        Map<String, McpSchema.ToolInputParam> result = service.convertToolArgs(args);

        assertFalse(result.get("source").isConditional());
        assertEquals("ndex", result.get("source").requiredParameter());
    }

    @Test
    public void convertToolArgs_nullValue_isRequiredParameter() {
        Map<String, Object> args = new HashMap<>();
        args.put("missing", null);
        Map<String, McpSchema.ToolInputParam> result = service.convertToolArgs(args);

        assertFalse(result.get("missing").isConditional());
        assertNull(result.get("missing").requiredParameter());
    }

    @Test
    public void convertToolArgs_mapWithoutWaivedKey_isRequiredParameter() {
        Map<String, Object> args = new HashMap<>();
        Map<String, Object> notAWrapper = new HashMap<>();
        notAWrapper.put("someKey", "someValue");
        args.put("param", notAWrapper);
        Map<String, McpSchema.ToolInputParam> result = service.convertToolArgs(args);

        assertFalse(result.get("param").isConditional());
    }

    @Test
    public void convertToolArgs_mixedArgs_bothTypesDetected() {
        Map<String, Object> args = new HashMap<>();
        args.put("source", "ndex");
        args.put("network_id", wrapperOf(false, "uuid-123"));
        Map<String, McpSchema.ToolInputParam> result = service.convertToolArgs(args);

        assertFalse(result.get("source").isConditional());
        assertTrue(result.get("network_id").isConditional());
        assertEquals("uuid-123", result.get("network_id").conditionalParameter().value());
    }

    // -----------------------------------------------------------------------
    // unwrapToolInputValue
    // -----------------------------------------------------------------------

    @Test
    public void unwrap_conditionalString_waivedFalse_returnsString() {
        assertEquals(
                "hello", service.unwrapToolInputValue(wrapperOf(false, "hello"), String.class));
    }

    @Test
    public void unwrap_conditionalString_waivedTrue_returnsNull() {
        assertNull(service.unwrapToolInputValue(wrapperOf(true, null), String.class));
    }

    @Test
    public void unwrap_conditionalBoolean_returnsBoolean() {
        assertEquals(
                Boolean.TRUE, service.unwrapToolInputValue(wrapperOf(false, true), Boolean.class));
    }

    @Test
    public void unwrap_conditionalBoolean_waivedTrue_returnsNull() {
        assertNull(service.unwrapToolInputValue(wrapperOf(true, null), Boolean.class));
    }

    @Test
    public void unwrap_conditionalInteger_returnsInteger() {
        assertEquals(
                Integer.valueOf(42),
                service.unwrapToolInputValue(wrapperOf(false, 42), Integer.class));
    }

    @Test
    public void unwrap_conditionalInteger_fromString_returnsInteger() {
        assertEquals(
                Integer.valueOf(44),
                service.unwrapToolInputValue(wrapperOf(false, "44"), Integer.class));
    }

    @Test
    public void unwrap_nonConditionalString_returnsTrimmed() {
        assertEquals("hello", service.unwrapToolInputValue("  hello  ", String.class));
    }

    @Test
    public void unwrap_nonConditionalInteger_returnsInteger() {
        assertEquals(Integer.valueOf(9), service.unwrapToolInputValue(9, Integer.class));
    }

    @Test
    public void unwrap_nullInput_returnsNull() {
        assertNull(service.unwrapToolInputValue(null, String.class));
    }

    @Test
    public void unwrap_emptyString_returnsNull() {
        assertNull(service.unwrapToolInputValue(wrapperOf(false, "   "), String.class));
    }

    @Test
    public void unwrap_stringTrimming() {
        assertEquals(
                "trimmed",
                service.unwrapToolInputValue(wrapperOf(false, "  trimmed  "), String.class));
    }

    @Test
    public void unwrap_requiredStringDirect_returnsValue() {
        assertEquals("ndex", service.unwrapToolInputValue("ndex", String.class));
    }

    // -----------------------------------------------------------------------
    // unwrapToolInputDataColumns
    // -----------------------------------------------------------------------

    @Test
    public void unwrapDataColumns_waivedFalse_returnsList() {
        List<Map<String, Object>> columns =
                List.of(Map.of("name", "Score", "inferred_data_type", "double"));
        List<DataColumn> result = service.unwrapToolInputDataColumns(wrapperOf(false, columns));
        assertEquals(1, result.size());
        assertEquals("Score", result.get(0).name());
    }

    @Test
    public void unwrapDataColumns_waivedTrue_returnsEmptyList() {
        assertTrue(service.unwrapToolInputDataColumns(wrapperOf(true, null)).isEmpty());
    }

    @Test
    public void unwrapDataColumns_null_returnsEmptyList() {
        assertTrue(service.unwrapToolInputDataColumns(null).isEmpty());
    }
}
