package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.ucsd.idekerlab.cytoscapemcp.fixture.InMemoryTransport;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyRow;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewFactory;
import org.cytoscape.view.model.CyNetworkViewManager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link CreateNetworkViewTool} through its public interface ({@code toSpec()}) by
 * registering it on a real {@link io.modelcontextprotocol.server.McpSyncServer} backed by {@link
 * InMemoryTransport}. Cytoscape services are Mockito stubs.
 */
public class CreateNetworkViewToolTest {

    // --- JSON-RPC protocol messages ----------------------------------------

    private static final String INIT_REQUEST =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\","
                    + "\"params\":{\"protocolVersion\":\"2025-03-26\","
                    + "\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";

    private static final String INITIALIZED_NOTIFICATION =
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

    private static final String TOOL_CALL_CREATE =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                    + "\"params\":{\"name\":\"create_network_view\","
                    + "\"arguments\":{\"network_suid\":100}}}";

    private static final String TOOL_CALL_BAD_NETWORK =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                    + "\"params\":{\"name\":\"create_network_view\","
                    + "\"arguments\":{\"network_suid\":999}}}";

    // --- Mocks -------------------------------------------------------------

    @Mock private CyApplicationManager appManager;
    @Mock private CyNetworkManager networkManager;
    @Mock private CyNetworkViewManager viewManager;
    @Mock private CyNetworkViewFactory networkViewFactory;
    @Mock private CyNetwork network;
    @Mock private CyNetworkView existingView;
    @Mock private CyNetworkView newView;
    @Mock private CyRow networkRow;

    private CreateNetworkViewTool tool;
    private InMemoryTransport transport;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        tool =
                new CreateNetworkViewTool(
                        appManager, networkManager, viewManager, networkViewFactory);
    }

    @After
    public void tearDown() {
        if (transport != null) {
            transport.close();
        }
    }

    // -----------------------------------------------------------------------
    // Create new view — network exists, no existing views
    // -----------------------------------------------------------------------

    @Test
    public void noExistingView_createsNewView() throws Exception {
        stubNetwork(100L, "My Network", 10, 20);
        when(viewManager.getNetworkViews(network)).thenReturn(Collections.emptyList());
        when(networkViewFactory.createNetworkView(network)).thenReturn(newView);
        when(newView.getSUID()).thenReturn(300L);

        String response = callTool(TOOL_CALL_CREATE);

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should contain success status",
                response.contains("\\\"status\\\":\\\"success\\\""));
        assertTrue("Should contain view SUID", response.contains("\\\"view_suid\\\":300"));
        assertTrue("Should contain network SUID", response.contains("\\\"network_suid\\\":100"));
        assertTrue("Should contain network name", response.contains("My Network"));
        assertTrue("Should contain node count", response.contains("\\\"node_count\\\":10"));
        assertTrue("Should contain edge count", response.contains("\\\"edge_count\\\":20"));
    }

    // -----------------------------------------------------------------------
    // Return existing view — network exists, view already present
    // -----------------------------------------------------------------------

    @Test
    public void existingView_returnsExistingWithoutCreating() throws Exception {
        stubNetwork(100L, "My Network", 10, 20);
        when(viewManager.getNetworkViews(network))
                .thenReturn(Collections.singletonList(existingView));
        when(existingView.getSUID()).thenReturn(200L);

        String response = callTool(TOOL_CALL_CREATE);

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should contain success status",
                response.contains("\\\"status\\\":\\\"success\\\""));
        assertTrue("Should contain existing view SUID", response.contains("\\\"view_suid\\\":200"));
        verify(networkViewFactory, never()).createNetworkView(network);
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
    // Verify factory + viewManager calls on create
    // -----------------------------------------------------------------------

    @Test
    public void create_callsFactoryAndViewManager() throws Exception {
        stubNetwork(100L, "My Network", 10, 20);
        when(viewManager.getNetworkViews(network)).thenReturn(Collections.emptyList());
        when(networkViewFactory.createNetworkView(network)).thenReturn(newView);
        when(newView.getSUID()).thenReturn(300L);

        callTool(TOOL_CALL_CREATE);

        verify(networkViewFactory).createNetworkView(network);
        verify(viewManager).addNetworkView(newView);
        verify(appManager).setCurrentNetwork(network);
        verify(appManager).setCurrentNetworkView(newView);
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
