package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.idekerlab.cytoscapemcp.fixture.InMemoryTransport;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.command.CommandExecutorTaskFactory;
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyTable;
import org.cytoscape.work.SynchronousTaskManager;
import org.cytoscape.work.Task;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskMonitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link AnalyzeNetworkTool} through its public interface ({@code toSpec()}) by
 * registering it on a real {@link io.modelcontextprotocol.server.McpSyncServer} backed by {@link
 * InMemoryTransport}. Cytoscape services are Mockito stubs.
 */
public class AnalyzeNetworkToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // --- JSON-RPC protocol messages ----------------------------------------

    private static final String INIT_REQUEST =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\","
                    + "\"params\":{\"protocolVersion\":\"2025-03-26\","
                    + "\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";

    private static final String INITIALIZED_NOTIFICATION =
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

    private static String toolCall(boolean directed) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"analyze_network\","
                + "\"arguments\":{\"directed\":"
                + directed
                + "}}}";
    }

    // --- Mocks -------------------------------------------------------------

    @Mock private CyApplicationManager appManager;
    @Mock private SynchronousTaskManager<?> syncTaskManager;
    @Mock private CommandExecutorTaskFactory commandExecutorTaskFactory;
    @Mock private CyNetwork network;
    @Mock private CyTable nodeTable;

    /** Real (non-mockable) TaskIterator wrapping a no-op task. */
    private final TaskIterator noOpTaskIterator =
            new TaskIterator(
                    new Task() {
                        @Override
                        public void run(TaskMonitor taskMonitor) {}

                        @Override
                        public void cancel() {}
                    });

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
    // No current network
    // -----------------------------------------------------------------------

    @Test
    public void noCurrentNetwork_returnsError() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(null);
        AnalyzeNetworkTool tool =
                new AnalyzeNetworkTool(appManager, syncTaskManager, commandExecutorTaskFactory);

        String response = callTool(tool, false);

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should mention loading a network",
                response.contains("Please load a network first"));
    }

    // -----------------------------------------------------------------------
    // Analyzer not available — null factory
    // -----------------------------------------------------------------------

    @Test
    public void analyzerNotAvailable_nullFactory_returnsError() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(network);
        stubEmptyNodeTable();

        AnalyzeNetworkTool tool = new AnalyzeNetworkTool(appManager, syncTaskManager, null);

        String response = callTool(tool, false);

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue("Should tell agent to skip this tool", response.contains("Skip this tool"));
    }

    // -----------------------------------------------------------------------
    // Analyzer not available — unknown command
    // -----------------------------------------------------------------------

    @Test
    public void analyzerNotAvailable_unknownCommand_returnsError() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(network);
        stubEmptyNodeTable();
        when(commandExecutorTaskFactory.createTaskIterator(
                        eq("analyzer"), eq("analyze"), any(), any()))
                .thenThrow(new RuntimeException("unknown command: analyzer analyze"));
        AnalyzeNetworkTool tool =
                new AnalyzeNetworkTool(appManager, syncTaskManager, commandExecutorTaskFactory);

        String response = callTool(tool, false);

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue("Should tell agent to skip this tool", response.contains("Skip this tool"));
    }

    // -----------------------------------------------------------------------
    // Network too small
    // -----------------------------------------------------------------------

    @Test
    public void networkTooSmall_returnsError() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(network);
        stubEmptyNodeTable();
        when(commandExecutorTaskFactory.createTaskIterator(
                        eq("analyzer"), eq("analyze"), any(), any()))
                .thenReturn(noOpTaskIterator);
        doThrow(new IllegalArgumentException("Network too small: 4 node minimum."))
                .when(syncTaskManager)
                .execute(any());
        AnalyzeNetworkTool tool =
                new AnalyzeNetworkTool(appManager, syncTaskManager, commandExecutorTaskFactory);

        String response = callTool(tool, false);

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue("Should mention 'too small'", response.contains("too small"));
        assertFalse("Should NOT tell agent to skip this tool", response.contains("Skip this tool"));
    }

    // -----------------------------------------------------------------------
    // Directed not applicable
    // -----------------------------------------------------------------------

    @Test
    public void directedNotApplicable_returnsError() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(network);
        stubEmptyNodeTable();
        when(commandExecutorTaskFactory.createTaskIterator(
                        eq("analyzer"), eq("analyze"), any(), any()))
                .thenReturn(noOpTaskIterator);
        doThrow(
                        new NullPointerException(
                                "Analyze as direct graph is not applicable. Try to analzye as"
                                        + " undirected graph"))
                .when(syncTaskManager)
                .execute(any());
        AnalyzeNetworkTool tool =
                new AnalyzeNetworkTool(appManager, syncTaskManager, commandExecutorTaskFactory);

        String response = callTool(tool, true);

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue("Should mention 'not applicable'", response.contains("not applicable"));
        assertFalse("Should NOT tell agent to skip this tool", response.contains("Skip this tool"));
    }

    // -----------------------------------------------------------------------
    // Directed=true is forwarded to command args
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    public void directedTrue_passedToCommandArgs() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(network);
        stubEmptyNodeTable();
        ArgumentCaptor<Map<String, Object>> argsCaptor = ArgumentCaptor.forClass(Map.class);
        when(commandExecutorTaskFactory.createTaskIterator(
                        eq("analyzer"), eq("analyze"), argsCaptor.capture(), any()))
                .thenReturn(noOpTaskIterator);
        when(network.getNodeCount()).thenReturn(10);
        when(network.getEdgeCount()).thenReturn(5);
        AnalyzeNetworkTool tool =
                new AnalyzeNetworkTool(appManager, syncTaskManager, commandExecutorTaskFactory);

        callTool(tool, true);

        Map<String, Object> capturedArgs = argsCaptor.getValue();
        assertEquals(Boolean.TRUE, capturedArgs.get("directed"));
    }

    // -----------------------------------------------------------------------
    // Column diff — new columns returned
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    public void columnDiff_returnsNewColumns() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(network);

        // Before: just "name"
        CyColumn nameCol = mockColumn("name");
        // After: "name", "Degree", "BetweennessCentrality"
        CyColumn degreeCol = mockColumn("Degree");
        CyColumn betweennessCol = mockColumn("BetweennessCentrality");

        when(network.getDefaultNodeTable()).thenReturn(nodeTable);
        when(nodeTable.getColumns())
                .thenReturn(
                        (Collection) List.of(nameCol),
                        (Collection) List.of(nameCol, degreeCol, betweennessCol));

        when(commandExecutorTaskFactory.createTaskIterator(
                        eq("analyzer"), eq("analyze"), any(), any()))
                .thenReturn(noOpTaskIterator);
        when(network.getNodeCount()).thenReturn(10);
        when(network.getEdgeCount()).thenReturn(15);
        AnalyzeNetworkTool tool =
                new AnalyzeNetworkTool(appManager, syncTaskManager, commandExecutorTaskFactory);

        String response = callTool(tool, false);

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should contain BetweennessCentrality", response.contains("BetweennessCentrality"));
        assertTrue("Should contain Degree", response.contains("Degree"));
    }

    // -----------------------------------------------------------------------
    // Column diff — nothing added
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Test
    public void columnDiff_nothingAdded_returnsEmptyList() throws Exception {
        when(appManager.getCurrentNetwork()).thenReturn(network);

        CyColumn nameCol = mockColumn("name");
        when(network.getDefaultNodeTable()).thenReturn(nodeTable);
        when(nodeTable.getColumns())
                .thenReturn((Collection) List.of(nameCol), (Collection) List.of(nameCol));

        when(commandExecutorTaskFactory.createTaskIterator(
                        eq("analyzer"), eq("analyze"), any(), any()))
                .thenReturn(noOpTaskIterator);
        when(network.getNodeCount()).thenReturn(10);
        when(network.getEdgeCount()).thenReturn(5);
        AnalyzeNetworkTool tool =
                new AnalyzeNetworkTool(appManager, syncTaskManager, commandExecutorTaskFactory);

        String response = callTool(tool, false);

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should contain empty columns_added array",
                response.contains("\\\"columns_added\\\":[]"));
    }

    // -----------------------------------------------------------------------
    // Schema tests
    // -----------------------------------------------------------------------

    @Test
    public void inputSchema_requiredContainsDirected() throws Exception {
        JsonNode schema = MAPPER.readTree(AnalyzeNetworkTool.INPUT_SCHEMA);
        JsonNode required = schema.get("required");
        assertNotNull("required field must exist", required);
        assertTrue("required must be an array", required.isArray());
        boolean found = false;
        for (JsonNode el : required) {
            if ("directed".equals(el.asText())) {
                found = true;
                break;
            }
        }
        assertTrue("required must contain 'directed'", found);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void stubEmptyNodeTable() {
        when(network.getDefaultNodeTable()).thenReturn(nodeTable);
        when(nodeTable.getColumns()).thenReturn(List.of());
    }

    private static CyColumn mockColumn(String name) {
        CyColumn col = mock(CyColumn.class);
        when(col.getName()).thenReturn(name);
        return col;
    }

    private String callTool(AnalyzeNetworkTool tool, boolean directed) throws Exception {
        transport = new InMemoryTransport();
        transport.startServer("cytoscape-mcp-test", "0.0.0-test", List.of(tool.toSpec()));

        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(toolCall(directed));
        transport.await();

        return transport.getResponse();
    }
}
