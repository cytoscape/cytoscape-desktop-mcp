package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.List;

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
import org.cytoscape.view.layout.CyLayoutAlgorithm;
import org.cytoscape.view.layout.CyLayoutAlgorithmManager;
import org.cytoscape.view.model.CyNetworkView;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link ApplyLayoutTool} through its public interface ({@code toSpec()}) by registering
 * it on a real {@link io.modelcontextprotocol.server.McpSyncServer} backed by {@link
 * InMemoryTransport}. Cytoscape services are Mockito stubs.
 */
public class ApplyLayoutToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // --- JSON-RPC protocol messages ----------------------------------------

    private static final String INIT_REQUEST =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\","
                    + "\"params\":{\"protocolVersion\":\"2025-03-26\","
                    + "\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";

    private static final String INITIALIZED_NOTIFICATION =
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

    private static String toolCall(String algorithmName) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"apply_layout\","
                + "\"arguments\":{\"algorithm\":\""
                + algorithmName
                + "\"}}}";
    }

    // --- Mocks -------------------------------------------------------------

    @Mock private CyApplicationManager appManager;
    @Mock private CyLayoutAlgorithmManager layoutManager;
    @Mock private SynchronousTaskManager<?> syncTaskManager;
    @Mock private CyNetworkView view;

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
    // Success — fitContent and updateView called
    // -----------------------------------------------------------------------

    @Test
    public void success_callsFitContentAndUpdateView() throws Exception {
        CyLayoutAlgorithm algorithm = stubAlgorithm("force-directed", "Prefuse Force Directed");
        when(appManager.getCurrentNetworkView()).thenReturn(view);
        when(layoutManager.getLayout("force-directed")).thenReturn(algorithm);

        callTool("force-directed");

        verify(view).fitContent();
        verify(view).updateView();
    }

    // -----------------------------------------------------------------------
    // Success — response contains algorithm name and displayName
    // -----------------------------------------------------------------------

    @Test
    public void success_returnsAlgorithmNameAndDisplayName() throws Exception {
        CyLayoutAlgorithm algorithm =
                stubAlgorithm("force-directed", "Prefuse Force Directed Layout");
        when(appManager.getCurrentNetworkView()).thenReturn(view);
        when(layoutManager.getLayout("force-directed")).thenReturn(algorithm);

        String response = callTool("force-directed");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain algorithm name", response.contains("force-directed"));
        assertTrue(
                "Should contain displayName", response.contains("Prefuse Force Directed Layout"));
        assertTrue("Should contain success status", response.contains("success"));
    }

    // -----------------------------------------------------------------------
    // No current view
    // -----------------------------------------------------------------------

    @Test
    public void noCurrentView_returnsError() throws Exception {
        when(appManager.getCurrentNetworkView()).thenReturn(null);

        String response = callTool("force-directed");

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should mention no network view",
                response.contains("No network view is currently available"));
    }

    // -----------------------------------------------------------------------
    // Unknown algorithm
    // -----------------------------------------------------------------------

    @Test
    public void unknownAlgorithm_returnsError() throws Exception {
        when(appManager.getCurrentNetworkView()).thenReturn(view);
        when(layoutManager.getLayout("nonexistent-layout")).thenReturn(null);

        String response = callTool("nonexistent-layout");

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Should mention unknown algorithm", response.contains("Unknown layout algorithm"));
        assertTrue("Should include algorithm name", response.contains("nonexistent-layout"));
    }

    // -----------------------------------------------------------------------
    // Algorithm name is forwarded to layoutManager.getLayout
    // -----------------------------------------------------------------------

    @Test
    public void algorithmName_passedToLayoutManager() throws Exception {
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        CyLayoutAlgorithm algorithm = stubAlgorithm("circular", "Circular Layout");
        when(appManager.getCurrentNetworkView()).thenReturn(view);
        when(layoutManager.getLayout(nameCaptor.capture())).thenReturn(algorithm);

        callTool("circular");

        assertEquals("circular", nameCaptor.getValue());
    }

    // -----------------------------------------------------------------------
    // Layout execution failure
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void layoutExecutionFailure_returnsError() throws Exception {
        CyLayoutAlgorithm algorithm = stubAlgorithm("force-directed", "Prefuse Force Directed");
        when(appManager.getCurrentNetworkView()).thenReturn(view);
        when(layoutManager.getLayout("force-directed")).thenReturn(algorithm);
        org.mockito.Mockito.doThrow(new RuntimeException("layout timeout"))
                .when(syncTaskManager)
                .execute(any());

        String response = callTool("force-directed");

        assertTrue("Should be an error response", response.contains("\"isError\":true"));
        assertTrue("Should mention layout failed", response.contains("Layout failed"));
    }

    // -----------------------------------------------------------------------
    // Schema tests
    // -----------------------------------------------------------------------

    @Test
    public void inputSchema_algorithmIsRequired() throws Exception {
        JsonNode schema = MAPPER.readTree(ApplyLayoutTool.INPUT_SCHEMA);
        JsonNode required = schema.get("required");
        assertNotNull("required field must exist", required);
        assertTrue("required must be an array", required.isArray());
        boolean found = false;
        for (JsonNode el : required) {
            if ("algorithm".equals(el.asText())) {
                found = true;
                break;
            }
        }
        assertTrue("required must contain 'algorithm'", found);
    }

    @Test
    public void inputSchema_algorithmIsString() throws Exception {
        JsonNode schema = MAPPER.readTree(ApplyLayoutTool.INPUT_SCHEMA);
        assertEquals("string", schema.at("/properties/algorithm/type").asText());
    }

    @Test
    public void outputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(ApplyLayoutTool.OUTPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private CyLayoutAlgorithm stubAlgorithm(String name, String displayName) {
        CyLayoutAlgorithm algorithm = mock(CyLayoutAlgorithm.class);
        when(algorithm.getName()).thenReturn(name);
        when(algorithm.toString()).thenReturn(displayName);
        when(algorithm.createLayoutContext()).thenReturn(new Object());
        when(algorithm.createTaskIterator(eq(view), any(), any(), any()))
                .thenReturn(noOpTaskIterator);
        return algorithm;
    }

    private String callTool(String algorithmName) throws Exception {
        ApplyLayoutTool tool = new ApplyLayoutTool(appManager, layoutManager, syncTaskManager);
        transport = new InMemoryTransport();
        transport.startServer("cytoscape-mcp-test", "0.0.0-test", List.of(tool.toSpec()));

        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(toolCall(algorithmName));
        transport.await();

        return transport.getResponse();
    }
}
