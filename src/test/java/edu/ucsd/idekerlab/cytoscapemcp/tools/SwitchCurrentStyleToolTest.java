package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import edu.ucsd.idekerlab.cytoscapemcp.fixture.InMemoryTransport;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.view.vizmap.VisualStyleFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link SwitchCurrentStyleTool} through its public interface ({@code toSpec()}) by
 * registering it on a real {@link io.modelcontextprotocol.server.McpSyncServer} backed by {@link
 * InMemoryTransport}. Cytoscape services are Mockito stubs.
 */
public class SwitchCurrentStyleToolTest {

    // --- JSON-RPC protocol messages ----------------------------------------

    private static final String INIT_REQUEST =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\","
                    + "\"params\":{\"protocolVersion\":\"2025-03-26\","
                    + "\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";

    private static final String INITIALIZED_NOTIFICATION =
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

    // --- Mocks -------------------------------------------------------------

    @Mock private CyApplicationManager appManager;
    @Mock private VisualMappingManager vmmManager;
    @Mock private VisualStyleFactory visualStyleFactory;
    @Mock private CyNetworkView currentView;
    @Mock private CyNetwork currentNetwork;

    private SwitchCurrentStyleTool tool;
    private InMemoryTransport transport;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        tool = new SwitchCurrentStyleTool(appManager, vmmManager, visualStyleFactory);

        // Default: a current view exists.
        when(appManager.getCurrentNetworkView()).thenReturn(currentView);
        when(currentView.getModel()).thenReturn(currentNetwork);
    }

    @After
    public void tearDown() {
        if (transport != null) {
            transport.close();
        }
    }

    // -----------------------------------------------------------------------
    // No current view
    // -----------------------------------------------------------------------

    @Test
    public void noCurrentView_returnsError() throws Exception {
        when(appManager.getCurrentNetworkView()).thenReturn(null);

        String response = callTool("{\"name\": \"Marquee\"}");

        assertTrue("Should contain error about no view", response.contains("No network view"));
        assertTrue("Status should be false", response.contains("\\\"status\\\":false"));
    }

    // -----------------------------------------------------------------------
    // Switch to existing style
    // -----------------------------------------------------------------------

    @Test
    public void switchToExistingStyle_succeeds() throws Exception {
        VisualStyle style = mock(VisualStyle.class);
        when(style.getTitle()).thenReturn("Marquee");
        when(vmmManager.getAllVisualStyles()).thenReturn(Set.of(style));

        String response = callTool("{\"name\": \"Marquee\"}");

        assertTrue("Status should be true", response.contains("\\\"status\\\":true"));
        assertFalse("Should not contain error_msg", response.contains("error_msg"));
    }

    // -----------------------------------------------------------------------
    // Style not found, no create_from
    // -----------------------------------------------------------------------

    @Test
    public void styleNotFound_noCreateFrom_returnsError() throws Exception {
        when(vmmManager.getAllVisualStyles()).thenReturn(Set.of());

        String response = callTool("{\"name\": \"NonExistent\"}");

        assertTrue("Status should be false", response.contains("\\\"status\\\":false"));
        assertTrue(
                "Should mention style not found",
                response.contains("was not found among registered styles"));
    }

    // -----------------------------------------------------------------------
    // Create new style — clones the style currently applied to the view
    // -----------------------------------------------------------------------

    @Test
    public void createNewStyle_clonesCurrentViewStyle_succeeds() throws Exception {
        VisualStyle sourceStyle = mock(VisualStyle.class);

        VisualStyle newStyle = mock(VisualStyle.class);
        when(visualStyleFactory.createVisualStyle(sourceStyle)).thenReturn(newStyle);
        when(vmmManager.getVisualStyle(currentView)).thenReturn(sourceStyle);

        when(vmmManager.getAllVisualStyles()).thenReturn(Set.of());

        String response = callTool("{\"name\": \"My Style\", \"create\": true}");

        assertTrue("Status should be true", response.contains("\\\"status\\\":true"));
        verify(newStyle).setTitle("My Style");
        verify(vmmManager).addVisualStyle(newStyle);
    }

    // -----------------------------------------------------------------------
    // Create style when name already exists → error
    // -----------------------------------------------------------------------

    @Test
    public void createStyle_nameAlreadyExists_returnsError() throws Exception {
        VisualStyle existing = mock(VisualStyle.class);
        when(existing.getTitle()).thenReturn("My Style");

        when(vmmManager.getAllVisualStyles()).thenReturn(Set.of(existing));

        String response = callTool("{\"name\": \"My Style\", \"create\": true}");

        assertTrue("Status should be false", response.contains("\\\"status\\\":false"));
        assertTrue(
                "Should mention duplicate",
                response.contains("a style with that name already exists"));
        verify(visualStyleFactory, never()).createVisualStyle(any(VisualStyle.class));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String callTool(String arguments) throws Exception {
        String toolCall =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"switch_current_style\","
                        + "\"arguments\":"
                        + arguments
                        + "}}";

        transport = new InMemoryTransport();
        transport.startServer("cytoscape-mcp-test", "0.0.0-test", List.of(tool.toSpec()));

        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(toolCall);
        transport.await();

        return transport.getResponse();
    }
}
