package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.idekerlab.cytoscapemcp.fixture.InMemoryTransport;

import org.cytoscape.view.layout.CyLayoutAlgorithm;
import org.cytoscape.view.layout.CyLayoutAlgorithmManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link GetLayoutAlgorithmsTool} through its public interface ({@code toSpec()}) by
 * registering it on a real {@link io.modelcontextprotocol.server.McpSyncServer} backed by {@link
 * InMemoryTransport}. Cytoscape services are Mockito stubs.
 */
public class GetLayoutAlgorithmsToolTest {

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
                    + "\"params\":{\"name\":\"get_layout_algorithms\",\"arguments\":{}}}";

    // --- Mocks -------------------------------------------------------------

    @Mock private CyLayoutAlgorithmManager layoutManager;

    private InMemoryTransport transport;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @After
    public void tearDown() {
        if (transport != null) {
            transport.close();
        }
    }

    // -----------------------------------------------------------------------
    // Multiple algorithms — all returned with correct name/displayName
    // -----------------------------------------------------------------------

    @Test
    public void multipleAlgorithms_returnsAll() throws Exception {
        CyLayoutAlgorithm algo1 = mockAlgorithm("force-directed", "Prefuse Force Directed Layout");
        CyLayoutAlgorithm algo2 = mockAlgorithm("circular", "Circular Layout");
        CyLayoutAlgorithm algo3 = mockAlgorithm("grid", "Grid Layout");
        when(layoutManager.getAllLayouts()).thenReturn(List.of(algo1, algo2, algo3));

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain force-directed", response.contains("force-directed"));
        assertTrue(
                "Should contain Prefuse Force Directed Layout",
                response.contains("Prefuse Force Directed Layout"));
        assertTrue("Should contain circular", response.contains("circular"));
        assertTrue("Should contain grid", response.contains("grid"));
    }

    // -----------------------------------------------------------------------
    // Empty algorithm list
    // -----------------------------------------------------------------------

    @Test
    public void emptyAlgorithmList_returnsEmptyArray() throws Exception {
        when(layoutManager.getAllLayouts()).thenReturn(Collections.emptyList());

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain empty layouts array", response.contains("\\\"layouts\\\":[]"));
    }

    // -----------------------------------------------------------------------
    // Schema tests
    // -----------------------------------------------------------------------

    @Test
    public void inputSchema_hasNoRequiredFields() throws Exception {
        JsonNode schema = MAPPER.readTree(GetLayoutAlgorithmsTool.INPUT_SCHEMA);
        JsonNode required = schema.get("required");
        // required may be absent or an empty array
        if (required != null) {
            assertTrue("required must be an array", required.isArray());
            assertEquals("required must be empty", 0, required.size());
        }
    }

    @Test
    public void outputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(GetLayoutAlgorithmsTool.OUTPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static CyLayoutAlgorithm mockAlgorithm(String name, String displayName) {
        CyLayoutAlgorithm algo = mock(CyLayoutAlgorithm.class);
        when(algo.getName()).thenReturn(name);
        when(algo.toString()).thenReturn(displayName);
        return algo;
    }

    private String callTool() throws Exception {
        GetLayoutAlgorithmsTool tool = new GetLayoutAlgorithmsTool(layoutManager);
        transport = new InMemoryTransport();
        transport.startServer("cytoscape-mcp-test", "0.0.0-test", List.of(tool.toSpec()));

        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(TOOL_CALL);
        transport.await();

        return transport.getResponse();
    }
}
