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

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyRow;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link SetCurrentNetworkViewTool} through its public interface ({@code toSpec()}) by
 * registering it on a real {@link io.modelcontextprotocol.server.McpSyncServer} backed by {@link
 * InMemoryTransport}. Cytoscape services are Mockito stubs.
 */
public class SetCurrentNetworkViewToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // --- JSON-RPC protocol messages ----------------------------------------

    private static final String INIT_REQUEST =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\","
                    + "\"params\":{\"protocolVersion\":\"2025-03-26\","
                    + "\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";

    private static final String INITIALIZED_NOTIFICATION =
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

    private static final String TOOL_CALL_SUCCESS =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                    + "\"params\":{\"name\":\"set_current_network_view\","
                    + "\"arguments\":{\"network_suid\":100,\"view_suid\":200}}}";

    private static final String TOOL_CALL_BAD_NETWORK =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                    + "\"params\":{\"name\":\"set_current_network_view\","
                    + "\"arguments\":{\"network_suid\":999,\"view_suid\":200}}}";

    private static final String TOOL_CALL_BAD_VIEW =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                    + "\"params\":{\"name\":\"set_current_network_view\","
                    + "\"arguments\":{\"network_suid\":100,\"view_suid\":999}}}";

    // --- Mocks -------------------------------------------------------------

    @Mock private CyApplicationManager appManager;
    @Mock private CyNetworkManager networkManager;
    @Mock private CyNetworkViewManager viewManager;
    @Mock private CyNetwork network;
    @Mock private CyNetworkView networkView;
    @Mock private CyRow networkRow;

    private SetCurrentNetworkViewTool tool;
    private InMemoryTransport transport;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        tool = new SetCurrentNetworkViewTool(appManager, networkManager, viewManager);
    }

    @After
    public void tearDown() {
        if (transport != null) {
            transport.close();
        }
    }

    // -----------------------------------------------------------------------
    // Success — valid network + view SUIDs
    // -----------------------------------------------------------------------

    @Test
    public void validNetworkAndView_returnsSuccess() throws Exception {
        stubNetwork(100L, "My Network", 10, 20);
        stubView(200L);
        when(viewManager.getNetworkViews(network))
                .thenReturn(Collections.singletonList(networkView));

        String response = callTool(TOOL_CALL_SUCCESS);

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should contain success status",
                response.contains("\\\"status\\\":\\\"success\\\""));
        assertTrue("Should contain network name", response.contains("My Network"));
        assertTrue("Should contain node count", response.contains("\\\"node_count\\\":10"));
        assertTrue("Should contain edge count", response.contains("\\\"edge_count\\\":20"));
    }

    // -----------------------------------------------------------------------
    // Network not found
    // -----------------------------------------------------------------------

    @Test
    public void networkNotFound_returnsError() throws Exception {
        when(networkManager.getNetwork(999L)).thenReturn(null);

        String response = callTool(TOOL_CALL_BAD_NETWORK);

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue("Should mention SUID", response.contains("999"));
        assertTrue("Should mention not found", response.contains("not found"));
    }

    // -----------------------------------------------------------------------
    // View not found
    // -----------------------------------------------------------------------

    @Test
    public void viewNotFound_returnsError() throws Exception {
        stubNetwork(100L, "My Network", 10, 20);
        // Return a view with a different SUID than what we're looking for (999)
        when(viewManager.getNetworkViews(network))
                .thenReturn(Collections.singletonList(networkView));
        when(networkView.getSUID()).thenReturn(200L);

        String response = callTool(TOOL_CALL_BAD_VIEW);

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue("Should mention view not found", response.contains("view"));
    }

    // -----------------------------------------------------------------------
    // Verify appManager calls on success
    // -----------------------------------------------------------------------

    @Test
    public void success_callsSetCurrentNetworkAndView() throws Exception {
        stubNetwork(100L, "My Network", 10, 20);
        stubView(200L);
        when(viewManager.getNetworkViews(network))
                .thenReturn(Collections.singletonList(networkView));

        callTool(TOOL_CALL_SUCCESS);

        verify(appManager).setCurrentNetwork(network);
        verify(appManager).setCurrentNetworkView(networkView);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void stubNetwork(long suid, String name, int nodeCount, int edgeCount) {
        when(networkManager.getNetwork(suid)).thenReturn(network);
        when(network.getRow(network)).thenReturn(networkRow);
        when(networkRow.get(CyNetwork.NAME, String.class)).thenReturn(name);
        when(network.getNodeCount()).thenReturn(nodeCount);
        when(network.getEdgeCount()).thenReturn(edgeCount);
    }

    private void stubView(long suid) {
        when(networkView.getSUID()).thenReturn(suid);
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

    // -----------------------------------------------------------------------
    // Schema tests
    // -----------------------------------------------------------------------

    @Test
    public void inputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(SetCurrentNetworkViewTool.INPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
    }

    @Test
    public void inputSchema_typeIsObject() throws Exception {
        JsonNode schema = MAPPER.readTree(SetCurrentNetworkViewTool.INPUT_SCHEMA);
        assertEquals("object", schema.get("type").asText());
    }

    @Test
    public void inputSchema_requiredContainsBothSuids() throws Exception {
        JsonNode required = MAPPER.readTree(SetCurrentNetworkViewTool.INPUT_SCHEMA).get("required");
        assertNotNull(required);
        assertTrue(required.isArray());
        assertEquals(2, required.size());
        String r0 = required.get(0).asText();
        String r1 = required.get(1).asText();
        assertTrue(
                (r0.equals("network_suid") && r1.equals("view_suid"))
                        || (r0.equals("view_suid") && r1.equals("network_suid")));
    }

    @Test
    public void inputSchema_networkSuidIsInteger() throws Exception {
        JsonNode schema = MAPPER.readTree(SetCurrentNetworkViewTool.INPUT_SCHEMA);
        assertEquals("integer", schema.at("/properties/network_suid/type").asText());
    }

    @Test
    public void inputSchema_viewSuidIsInteger() throws Exception {
        JsonNode schema = MAPPER.readTree(SetCurrentNetworkViewTool.INPUT_SCHEMA);
        assertEquals("integer", schema.at("/properties/view_suid/type").asText());
    }
}
