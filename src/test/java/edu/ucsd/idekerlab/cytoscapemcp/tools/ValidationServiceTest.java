package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

/**
 * Unit tests for {@link ValidationService} exercising {@code validateConditionalParams} and {@code
 * validatePresence} directly, without going through the MCP protocol stack.
 *
 * <p>These tests carry the specific error-message and chain-behaviour assertions that {@link
 * LoadNetworkViewToolTest} delegates to the mock. Each test constructs a minimal {@code args} map
 * and calls the service directly.
 */
public class ValidationServiceTest {

    private final ValidationService service = new ValidationService();

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Builds an args map with a ConditionalParameter wrapper for the given key. */
    private static Map<String, Object> wrap(String key, boolean waived, Object value) {
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("waived", waived);
        wrapper.put("parameter", value);
        Map<String, Object> args = new HashMap<>();
        args.put(key, wrapper);
        return args;
    }

    /** Returns the text content of a {@link CallToolResult} error (first content element). */
    private static String errorText(CallToolResult result) {
        assertNotNull("Expected a non-null error result", result);
        assertTrue("Expected isError=true", Boolean.TRUE.equals(result.isError()));
        io.modelcontextprotocol.spec.McpSchema.TextContent tc =
                (io.modelcontextprotocol.spec.McpSchema.TextContent) result.content().get(0);
        return tc.text();
    }

    // -----------------------------------------------------------------------
    // ndex source
    // -----------------------------------------------------------------------

    @Test
    public void ndex_networkIdPresent_returnsNull() {
        Map<String, Object> args = wrap("network_id", false, "some-uuid");
        assertNull(service.validateConditionalParams("ndex", args));
    }

    @Test
    public void ndex_networkIdAbsent_returnsError() {
        Map<String, Object> args = new HashMap<>(); // no network_id key
        CallToolResult result = service.validateConditionalParams("ndex", args);
        String msg = errorText(result);
        assertTrue("Should name network_id", msg.contains("network_id"));
        assertTrue("Should mention waived", msg.contains("waived"));
        assertTrue("Should refer to parameter description", msg.contains("parameter description"));
    }

    @Test
    public void ndex_networkIdWaivedTrue_returnsError() {
        Map<String, Object> args = wrap("network_id", true, null);
        CallToolResult result = service.validateConditionalParams("ndex", args);
        String msg = errorText(result);
        assertTrue("Should name network_id", msg.contains("network_id"));
        assertTrue(
                "Should say cannot be intentionally omitted",
                msg.contains("cannot be intentionally omitted"));
    }

    // -----------------------------------------------------------------------
    // network-file source
    // -----------------------------------------------------------------------

    @Test
    public void networkFile_filePathPresent_returnsNull() {
        Map<String, Object> args = wrap("file_path", false, "/path/to/file.sif");
        assertNull(service.validateConditionalParams("network-file", args));
    }

    @Test
    public void networkFile_filePathAbsent_returnsError() {
        Map<String, Object> args = new HashMap<>();
        CallToolResult result = service.validateConditionalParams("network-file", args);
        String msg = errorText(result);
        assertTrue("Should name file_path", msg.contains("file_path"));
        assertTrue("Should mention waived", msg.contains("waived"));
        assertTrue("Should refer to parameter description", msg.contains("parameter description"));
    }

    @Test
    public void networkFile_filePathWaivedTrue_returnsError() {
        Map<String, Object> args = wrap("file_path", true, null);
        CallToolResult result = service.validateConditionalParams("network-file", args);
        String msg = errorText(result);
        assertTrue("Should name file_path", msg.contains("file_path"));
        assertTrue(
                "Should say cannot be intentionally omitted",
                msg.contains("cannot be intentionally omitted"));
    }

    // -----------------------------------------------------------------------
    // tabular-file source — all required params present
    // -----------------------------------------------------------------------

    @Test
    public void tabular_allRequiredPresent_returnsNull() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", wrapperOf(false, "/data.csv"));
        args.put("source_column", wrapperOf(false, "Gene1"));
        args.put("target_column", wrapperOf(false, "Gene2"));
        args.put("use_header_row", wrapperOf(false, true));
        args.put("node_attributes_source_columns", wrapperOf(true, null)); // waived=true is valid
        args.put("node_attributes_target_columns", wrapperOf(true, null));
        assertNull(service.validateConditionalParams("tabular-file", args));
    }

    // -----------------------------------------------------------------------
    // tabular-file source — each param absent (chain stops at first missing)
    // -----------------------------------------------------------------------

    @Test
    public void tabular_filePathAbsent_returnsError() {
        Map<String, Object> args = new HashMap<>();
        // all others present; file_path absent — chain must stop here first
        args.put("source_column", wrapperOf(false, "Gene1"));
        args.put("target_column", wrapperOf(false, "Gene2"));
        args.put("use_header_row", wrapperOf(false, true));
        args.put("node_attributes_source_columns", wrapperOf(true, null));
        args.put("node_attributes_target_columns", wrapperOf(true, null));

        CallToolResult result = service.validateConditionalParams("tabular-file", args);
        String msg = errorText(result);
        assertTrue("Should name file_path", msg.contains("file_path"));
        assertTrue("Should mention waived", msg.contains("waived"));
        assertTrue("Should refer to parameter description", msg.contains("parameter description"));
    }

    @Test
    public void tabular_sourceColumnAbsent_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", wrapperOf(false, "/data.csv"));
        // source_column absent — chain stops here (after file_path passes)
        args.put("target_column", wrapperOf(false, "Gene2"));
        args.put("use_header_row", wrapperOf(false, true));
        args.put("node_attributes_source_columns", wrapperOf(true, null));
        args.put("node_attributes_target_columns", wrapperOf(true, null));

        CallToolResult result = service.validateConditionalParams("tabular-file", args);
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
        // target_column absent — chain stops here
        args.put("use_header_row", wrapperOf(false, true));
        args.put("node_attributes_source_columns", wrapperOf(true, null));
        args.put("node_attributes_target_columns", wrapperOf(true, null));

        CallToolResult result = service.validateConditionalParams("tabular-file", args);
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
        // use_header_row absent — chain stops here
        args.put("node_attributes_source_columns", wrapperOf(true, null));
        args.put("node_attributes_target_columns", wrapperOf(true, null));

        CallToolResult result = service.validateConditionalParams("tabular-file", args);
        String msg = errorText(result);
        assertTrue("Should name use_header_row", msg.contains("use_header_row"));
        assertTrue("Should mention waived", msg.contains("waived"));
        assertTrue("Should refer to parameter description", msg.contains("parameter description"));
    }

    @Test
    public void tabular_nodeAttrSourceColumnsAbsent_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", wrapperOf(false, "/data.csv"));
        args.put("source_column", wrapperOf(false, "Gene1"));
        args.put("target_column", wrapperOf(false, "Gene2"));
        args.put("use_header_row", wrapperOf(false, true));
        // node_attributes_source_columns absent — chain stops here
        args.put("node_attributes_target_columns", wrapperOf(true, null));

        CallToolResult result = service.validateConditionalParams("tabular-file", args);
        String msg = errorText(result);
        assertTrue(
                "Should name node_attributes_source_columns",
                msg.contains("node_attributes_source_columns"));
        assertTrue("Should mention waived", msg.contains("waived"));
        assertTrue("Should refer to parameter description", msg.contains("parameter description"));
    }

    @Test
    public void tabular_nodeAttrTargetColumnsAbsent_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", wrapperOf(false, "/data.csv"));
        args.put("source_column", wrapperOf(false, "Gene1"));
        args.put("target_column", wrapperOf(false, "Gene2"));
        args.put("use_header_row", wrapperOf(false, true));
        args.put("node_attributes_source_columns", wrapperOf(true, null));
        // node_attributes_target_columns absent — chain stops here

        CallToolResult result = service.validateConditionalParams("tabular-file", args);
        String msg = errorText(result);
        assertTrue(
                "Should name node_attributes_target_columns",
                msg.contains("node_attributes_target_columns"));
        assertTrue("Should mention waived", msg.contains("waived"));
        assertTrue("Should refer to parameter description", msg.contains("parameter description"));
    }

    // -----------------------------------------------------------------------
    // tabular-file source — waive behaviour
    // -----------------------------------------------------------------------

    @Test
    public void tabular_nodeAttrSourceWaivedTrue_returnsNull() {
        // node_attributes_source_columns is a waivable param — waived=true is valid
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", wrapperOf(false, "/data.csv"));
        args.put("source_column", wrapperOf(false, "Gene1"));
        args.put("target_column", wrapperOf(false, "Gene2"));
        args.put("use_header_row", wrapperOf(false, true));
        args.put("node_attributes_source_columns", wrapperOf(true, null)); // waived=true is valid
        args.put("node_attributes_target_columns", wrapperOf(true, null));
        assertNull(service.validateConditionalParams("tabular-file", args));
    }

    @Test
    public void tabular_filePathWaivedTrue_returnsError() {
        // file_path is cannotWaive — waived=true must be rejected
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", wrapperOf(true, null));
        args.put("source_column", wrapperOf(false, "Gene1"));
        args.put("target_column", wrapperOf(false, "Gene2"));
        args.put("use_header_row", wrapperOf(false, true));
        args.put("node_attributes_source_columns", wrapperOf(true, null));
        args.put("node_attributes_target_columns", wrapperOf(true, null));

        CallToolResult result = service.validateConditionalParams("tabular-file", args);
        String msg = errorText(result);
        assertTrue("Should name file_path", msg.contains("file_path"));
        assertTrue(
                "Should say cannot be intentionally omitted",
                msg.contains("cannot be intentionally omitted"));
    }

    // -----------------------------------------------------------------------
    // Unknown source
    // -----------------------------------------------------------------------

    @Test
    public void unknownSource_returnsNull() {
        assertNull(service.validateConditionalParams("unknown-source", new HashMap<>()));
    }

    // -----------------------------------------------------------------------
    // validatePresence directly
    // -----------------------------------------------------------------------

    @Test
    public void validatePresence_absent_returnsError() {
        Map<String, Object> args = new HashMap<>(); // param key not present
        CallToolResult result =
                service.validatePresence(args, "my_param", "test-source", "some purpose", true);
        String msg = errorText(result);
        assertTrue("Should name the param", msg.contains("my_param"));
        assertTrue("Should mention source", msg.contains("test-source"));
        assertTrue("Should mention waived", msg.contains("waived"));
        assertTrue("Should refer to parameter description", msg.contains("parameter description"));
    }

    @Test
    public void validatePresence_presentWaivedFalse_returnsNull() {
        Map<String, Object> args = wrap("my_param", false, "value");
        assertNull(service.validatePresence(args, "my_param", "test-source", "some purpose", true));
    }

    @Test
    public void validatePresence_cannotWaive_waivedTrue_returnsError() {
        Map<String, Object> args = wrap("my_param", true, null);
        CallToolResult result =
                service.validatePresence(args, "my_param", "test-source", "some purpose", true);
        String msg = errorText(result);
        assertTrue("Should name the param", msg.contains("my_param"));
        assertTrue(
                "Should say cannot be intentionally omitted",
                msg.contains("cannot be intentionally omitted"));
    }

    @Test
    public void validatePresence_canWaive_waivedTrue_returnsNull() {
        // cannotWaive=false — waived=true is acceptable; wrapper is present so no error
        Map<String, Object> args = wrap("my_param", true, null);
        assertNull(
                service.validatePresence(args, "my_param", "test-source", "some purpose", false));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static Map<String, Object> wrapperOf(boolean waived, Object value) {
        Map<String, Object> w = new HashMap<>();
        w.put("waived", waived);
        w.put("parameter", value);
        return w;
    }
}
