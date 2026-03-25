package edu.ucsd.idekerlab.cytoscapemcp.tools;

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

import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link GetStylesTool} through its public interface ({@code toSpec()}) by registering it
 * on a real {@link io.modelcontextprotocol.server.McpSyncServer} backed by {@link
 * InMemoryTransport}. Cytoscape services are Mockito stubs.
 */
public class GetStylesToolTest {

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
                    + "\"params\":{\"name\":\"get_styles\","
                    + "\"arguments\":{}}}";

    // --- Mocks -------------------------------------------------------------

    @Mock private VisualMappingManager vmmManager;

    private GetStylesTool tool;
    private InMemoryTransport transport;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        tool = new GetStylesTool(vmmManager);
    }

    @After
    public void tearDown() {
        if (transport != null) {
            transport.close();
        }
    }

    // -----------------------------------------------------------------------
    // Empty styles
    // -----------------------------------------------------------------------

    @Test
    public void emptyStyles_returnsEmptyList() throws Exception {
        when(vmmManager.getAllVisualStyles()).thenReturn(Set.of());

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain empty styles array", response.contains("\\\"styles\\\":[]"));
    }

    // -----------------------------------------------------------------------
    // Multiple styles returned alphabetically
    // -----------------------------------------------------------------------

    @Test
    public void multipleStyles_returnedAlphabetically() throws Exception {
        VisualStyle style1 = mock(VisualStyle.class);
        when(style1.getTitle()).thenReturn("Marquee");
        VisualStyle style2 = mock(VisualStyle.class);
        when(style2.getTitle()).thenReturn("default");
        VisualStyle style3 = mock(VisualStyle.class);
        when(style3.getTitle()).thenReturn("Nested Network Style");

        Set<VisualStyle> styles = new HashSet<>();
        styles.add(style1);
        styles.add(style2);
        styles.add(style3);
        when(vmmManager.getAllVisualStyles()).thenReturn(styles);

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain Marquee", response.contains("Marquee"));
        assertTrue("Should contain default", response.contains("default"));
        assertTrue(
                "Should contain Nested Network Style", response.contains("Nested Network Style"));

        // Verify alphabetical order: Marquee < Nested Network Style < default
        int marqueeIdx = response.indexOf("Marquee");
        int nestedIdx = response.indexOf("Nested Network Style");
        int defaultIdx = response.indexOf("default");
        assertTrue("Marquee should come before Nested Network Style", marqueeIdx < nestedIdx);
        assertTrue("Nested Network Style should come before default", nestedIdx < defaultIdx);
    }

    // -----------------------------------------------------------------------
    // Single style
    // -----------------------------------------------------------------------

    @Test
    public void singleStyle_returnsSingleItem() throws Exception {
        VisualStyle style = mock(VisualStyle.class);
        when(style.getTitle()).thenReturn("default");
        when(vmmManager.getAllVisualStyles()).thenReturn(Set.of(style));

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain default style", response.contains("default"));
    }

    // -----------------------------------------------------------------------
    // Schema tests
    // -----------------------------------------------------------------------

    @Test
    public void inputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(GetStylesTool.INPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
    }

    @Test
    public void inputSchema_typeIsObject() throws Exception {
        JsonNode schema = MAPPER.readTree(GetStylesTool.INPUT_SCHEMA);
        assertEquals("object", schema.get("type").asText());
    }

    @Test
    public void inputSchema_requiredIsEmpty() throws Exception {
        JsonNode required = MAPPER.readTree(GetStylesTool.INPUT_SCHEMA).get("required");
        assertNotNull(required);
        assertTrue(required.isArray());
        assertEquals(0, required.size());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

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
