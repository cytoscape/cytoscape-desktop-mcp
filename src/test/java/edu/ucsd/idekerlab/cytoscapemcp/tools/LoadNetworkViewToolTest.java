package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.ucsd.idekerlab.cytoscapemcp.fixture.InMemoryTransport;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.io.read.CyNetworkReader;
import org.cytoscape.io.read.InputStreamTaskFactory;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyRow;
import org.cytoscape.property.CyProperty;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.work.FinishStatus;
import org.cytoscape.work.Task;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskManager;
import org.cytoscape.work.TaskObserver;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link LoadNetworkViewTool} through its public interface ({@code toSpec()}) by
 * registering it on a real {@link io.modelcontextprotocol.server.McpSyncServer} backed by {@link
 * InMemoryTransport}. Cytoscape services are Mockito stubs.
 */
public class LoadNetworkViewToolTest {

    private static final String VALID_UUID = "a7e43e3d-c7f8-11ec-8d17-005056ae23aa";

    // --- JSON-RPC protocol messages ----------------------------------------

    private static final String INIT_REQUEST =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\","
                    + "\"params\":{\"protocolVersion\":\"2025-03-26\","
                    + "\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";

    private static final String INITIALIZED_NOTIFICATION =
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

    // --- Mocks -------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Mock
    private CyProperty<Properties> cyProperties;

    @Mock private CyApplicationManager appManager;
    @Mock private CyNetworkManager networkManager;
    @Mock private CyNetworkViewManager viewManager;
    @Mock private TaskManager<?, ?> taskManager;
    @Mock private InputStreamTaskFactory cxReaderFactory;
    @Mock private CyNetworkReader networkReader;
    @Mock private CyNetwork network;
    @Mock private CyNetworkView networkView;
    @Mock private CyRow networkRow;

    private Properties props;
    private LoadNetworkViewTool tool;
    private InMemoryTransport transport;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        props = new Properties();
        props.setProperty("mcp.ndexbaseurl", "https://www.ndexbio.org");
        when(cyProperties.getProperties()).thenReturn(props);

        tool =
                spy(
                        new LoadNetworkViewTool(
                                cyProperties,
                                appManager,
                                networkManager,
                                viewManager,
                                taskManager,
                                cxReaderFactory));

        // Prevent real HTTP connections — return an empty stream for any URL
        try {
            doReturn(new ByteArrayInputStream(new byte[0])).when(tool).openStream(any(URL.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @After
    public void tearDown() {
        if (transport != null) {
            transport.close();
        }
    }

    // -----------------------------------------------------------------------
    // Success — basic load
    // -----------------------------------------------------------------------

    @Test
    public void successfulLoad_returnsNetworkName() throws Exception {
        stubSuccessfulLoad("Human PPI");

        String response = callTool(VALID_UUID);

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Response should mention network name", response.contains("Human PPI"));
        assertTrue("Response should mention NDEx", response.contains("NDEx"));
        verify(networkManager).addNetwork(network);
        verify(viewManager).addNetworkView(networkView);
        verify(appManager).setCurrentNetwork(network);
        verify(appManager).setCurrentNetworkView(networkView);
    }

    @Test
    public void successfulLoad_noViews_setsNetworkOnly() throws Exception {
        stubSuccessfulLoad("Yeast Net");
        when(viewManager.getNetworkViews(network)).thenReturn(Collections.emptyList());

        String response = callTool(VALID_UUID);

        assertFalse(response.contains("\"isError\":true"));
        verify(appManager).setCurrentNetwork(network);
        verify(appManager, never()).setCurrentNetworkView(any(CyNetworkView.class));
    }

    @Test
    public void successfulLoad_nullName_fallsBackToUuid() throws Exception {
        stubSuccessfulLoad(null);

        String response = callTool(VALID_UUID);

        assertFalse(response.contains("\"isError\":true"));
        assertTrue("Should fall back to UUID", response.contains(VALID_UUID));
    }

    @Test
    public void customNdexBaseUrl_isHonoured() throws Exception {
        props.setProperty("mcp.ndexbaseurl", "https://internal.ndex.example.com");
        stubSuccessfulLoad("Internal Net");

        String response = callTool(VALID_UUID);

        assertFalse(response.contains("\"isError\":true"));
        assertTrue("Response should mention network name", response.contains("Internal Net"));
    }

    // -----------------------------------------------------------------------
    // Failure — input validation
    // -----------------------------------------------------------------------

    @Test
    public void missingNetworkId_returnsError() throws Exception {
        String response =
                callToolRaw(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                                + "\"params\":{\"name\":\"load_cytoscape_network_view\","
                                + "\"arguments\":{}}}");

        assertTrue("Should be an error", response.contains("\"isError\":true"));
        assertTrue("Should mention network-id", response.contains("network-id"));
        verify(networkManager, never()).addNetwork(any());
    }

    @Test
    public void blankNetworkId_returnsError() throws Exception {
        String response = callTool("   ");

        assertTrue(response.contains("\"isError\":true"));
        verify(networkManager, never()).addNetwork(any());
    }

    // -----------------------------------------------------------------------
    // Failure — runtime errors
    // -----------------------------------------------------------------------

    @Test
    public void taskFailsViaFinishStatus_returnsError() throws Exception {
        when(cxReaderFactory.createTaskIterator(any(InputStream.class), isNull()))
                .thenReturn(new TaskIterator((Task) networkReader));

        doAnswer(
                        invocation -> {
                            TaskObserver observer = invocation.getArgument(1);
                            observer.allFinished(
                                    FinishStatus.newFailed(
                                            null, new RuntimeException("NDEx unreachable")));
                            return null;
                        })
                .when(taskManager)
                .execute(any(TaskIterator.class), any(TaskObserver.class));

        String response = callTool(VALID_UUID);

        assertTrue(response.contains("\"isError\":true"));
        assertTrue("Should surface the error message", response.contains("NDEx unreachable"));
        verify(appManager, never()).setCurrentNetwork(any());
    }

    @Test
    public void noNetworksAfterLoad_returnsError() throws Exception {
        when(cxReaderFactory.createTaskIterator(any(InputStream.class), isNull()))
                .thenReturn(new TaskIterator((Task) networkReader));
        when(networkReader.getNetworks()).thenReturn(new CyNetwork[0]);

        doAnswer(
                        invocation -> {
                            TaskObserver observer = invocation.getArgument(1);
                            observer.allFinished(FinishStatus.getSucceeded());
                            return null;
                        })
                .when(taskManager)
                .execute(any(TaskIterator.class), any(TaskObserver.class));

        String response = callTool(VALID_UUID);

        assertTrue(response.contains("\"isError\":true"));
        assertTrue("Should mention the UUID", response.contains(VALID_UUID));
        verify(appManager, never()).setCurrentNetwork(any());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Stubs mocks for a successful network load via CyNetworkReaderManager. */
    private void stubSuccessfulLoad(String networkName) {
        when(cxReaderFactory.createTaskIterator(any(InputStream.class), isNull()))
                .thenReturn(new TaskIterator((Task) networkReader));
        when(networkReader.getNetworks()).thenReturn(new CyNetwork[] {network});
        when(networkReader.buildCyNetworkView(network)).thenReturn(networkView);

        when(network.getRow(network)).thenReturn(networkRow);
        when(networkRow.get(CyNetwork.NAME, String.class)).thenReturn(networkName);

        when(viewManager.getNetworkViews(network))
                .thenReturn(Collections.singletonList(networkView));

        doAnswer(
                        invocation -> {
                            TaskObserver observer = invocation.getArgument(1);
                            observer.allFinished(FinishStatus.getSucceeded());
                            return null;
                        })
                .when(taskManager)
                .execute(any(TaskIterator.class), any(TaskObserver.class));
    }

    private String buildToolCall(String networkId) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"load_cytoscape_network_view\","
                + "\"arguments\":{\"network-id\":\""
                + networkId
                + "\"}}}";
    }

    /**
     * Sends the MCP init handshake + a {@code tools/call} for load_cytoscape_network_view with the
     * given network-id, and returns the raw server output.
     */
    private String callTool(String networkId) throws Exception {
        return callToolRaw(buildToolCall(networkId));
    }

    /**
     * Sends the MCP init handshake + a raw JSON-RPC tool call request, and returns the raw server
     * output.
     */
    private String callToolRaw(String toolCallJson) throws Exception {
        transport = new InMemoryTransport();
        transport.startServer("cytoscape-mcp-test", "0.0.0-test", List.of(tool.toSpec()));

        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(toolCallJson);
        transport.await();

        return transport.getResponse();
    }
}
