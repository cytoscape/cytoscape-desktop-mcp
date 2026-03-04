package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.Collections;
import java.util.LinkedHashSet;
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

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.subnetwork.CyRootNetwork;
import org.cytoscape.model.subnetwork.CySubNetwork;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link GetLoadedNetworkViewsTool} through its public interface ({@code toSpec()}) by
 * registering it on a real {@link io.modelcontextprotocol.server.McpSyncServer} backed by {@link
 * InMemoryTransport}. Cytoscape services are Mockito stubs.
 */
public class GetLoadedNetworkViewsToolTest {

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
                    + "\"params\":{\"name\":\"get_loaded_network_views\","
                    + "\"arguments\":{}}}";

    // --- Mocks -------------------------------------------------------------

    @Mock private CyNetworkManager networkManager;
    @Mock private CyNetworkViewManager viewManager;

    // Network 1
    @Mock private CySubNetwork subNetwork1;
    @Mock private CyRootNetwork rootNetwork1;
    @Mock private CyRow networkRow1;
    @Mock private CyRow rootRow1;
    @Mock private CyNetworkView networkView1;

    // Network 2
    @Mock private CySubNetwork subNetwork2;
    @Mock private CyRootNetwork rootNetwork2;
    @Mock private CyRow networkRow2;
    @Mock private CyRow rootRow2;

    // Non-subnetwork (should be skipped)
    @Mock private CyNetwork plainNetwork;

    private GetLoadedNetworkViewsTool tool;
    private InMemoryTransport transport;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        tool = new GetLoadedNetworkViewsTool(networkManager, viewManager);
    }

    @After
    public void tearDown() {
        if (transport != null) {
            transport.close();
        }
    }

    // -----------------------------------------------------------------------
    // Empty network set
    // -----------------------------------------------------------------------

    @Test
    public void emptyNetworkSet_returnsEmptyViews() throws Exception {
        when(networkManager.getNetworkSet()).thenReturn(Collections.emptySet());

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain empty views array", response.contains("\\\"views\\\":[]"));
    }

    // -----------------------------------------------------------------------
    // Single network with view
    // -----------------------------------------------------------------------

    @Test
    public void singleNetworkWithView_returnsAllFields() throws Exception {
        stubNetwork1("My Collection", "My Network", 100L, 200L, 10, 20);
        when(viewManager.getNetworkViews(subNetwork1))
                .thenReturn(Collections.singletonList(networkView1));
        when(networkView1.getSUID()).thenReturn(300L);

        Set<CyNetwork> networks = new LinkedHashSet<>();
        networks.add(subNetwork1);
        when(networkManager.getNetworkSet()).thenReturn(networks);

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain collection name", response.contains("My Collection"));
        assertTrue("Should contain network name", response.contains("My Network"));
        assertTrue("Should contain network SUID", response.contains("100"));
        assertTrue("Should contain view SUID", response.contains("300"));
        assertTrue("Should contain node count", response.contains("\\\"node_count\\\":10"));
        assertTrue("Should contain edge count", response.contains("\\\"edge_count\\\":20"));
    }

    // -----------------------------------------------------------------------
    // Single network without view
    // -----------------------------------------------------------------------

    @Test
    public void singleNetworkWithoutView_returnsNullViewSuid() throws Exception {
        stubNetwork1("Collection A", "Network A", 100L, null, 5, 3);
        when(viewManager.getNetworkViews(subNetwork1)).thenReturn(Collections.emptyList());

        Set<CyNetwork> networks = new LinkedHashSet<>();
        networks.add(subNetwork1);
        when(networkManager.getNetworkSet()).thenReturn(networks);

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain null view_suid", response.contains("\\\"view_suid\\\":null"));
        assertTrue("Should contain network name", response.contains("Network A"));
    }

    // -----------------------------------------------------------------------
    // Multiple networks
    // -----------------------------------------------------------------------

    @Test
    public void multipleNetworks_returnsCorrectCount() throws Exception {
        stubNetwork1("Collection 1", "Network 1", 100L, 200L, 10, 20);
        when(viewManager.getNetworkViews(subNetwork1))
                .thenReturn(Collections.singletonList(networkView1));
        when(networkView1.getSUID()).thenReturn(200L);

        stubNetwork2("Collection 2", "Network 2", 400L, null, 30, 40);
        when(viewManager.getNetworkViews(subNetwork2)).thenReturn(Collections.emptyList());

        Set<CyNetwork> networks = new LinkedHashSet<>();
        networks.add(subNetwork1);
        networks.add(subNetwork2);
        when(networkManager.getNetworkSet()).thenReturn(networks);

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain Network 1", response.contains("Network 1"));
        assertTrue("Should contain Network 2", response.contains("Network 2"));
        assertTrue("Should contain Collection 1", response.contains("Collection 1"));
        assertTrue("Should contain Collection 2", response.contains("Collection 2"));
    }

    // -----------------------------------------------------------------------
    // Skips non-CySubNetwork instances
    // -----------------------------------------------------------------------

    @Test
    public void skipsNonSubNetworks() throws Exception {
        stubNetwork1("Collection X", "SubNet X", 100L, 200L, 5, 3);
        when(viewManager.getNetworkViews(subNetwork1))
                .thenReturn(Collections.singletonList(networkView1));
        when(networkView1.getSUID()).thenReturn(200L);

        Set<CyNetwork> networks = new LinkedHashSet<>();
        networks.add(plainNetwork); // should be skipped
        networks.add(subNetwork1);
        when(networkManager.getNetworkSet()).thenReturn(networks);

        String response = callTool();

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue("Should contain SubNet X", response.contains("SubNet X"));
        // Only one entry should be present (the plain network was skipped)
        // Count occurrences of "network_suid" — should be exactly 1
        int count = countOccurrences(response, "network_suid");
        assertTrue("Should have exactly 1 network entry, found " + count, count == 1);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void stubNetwork1(
            String collectionName,
            String networkName,
            long networkSuid,
            Long viewSuid,
            int nodeCount,
            int edgeCount) {
        when(subNetwork1.getRootNetwork()).thenReturn(rootNetwork1);
        when(rootNetwork1.getRow(rootNetwork1)).thenReturn(rootRow1);
        when(rootRow1.get(CyNetwork.NAME, String.class)).thenReturn(collectionName);
        when(subNetwork1.getRow(subNetwork1)).thenReturn(networkRow1);
        when(networkRow1.get(CyNetwork.NAME, String.class)).thenReturn(networkName);
        when(subNetwork1.getSUID()).thenReturn(networkSuid);
        when(subNetwork1.getNodeCount()).thenReturn(nodeCount);
        when(subNetwork1.getEdgeCount()).thenReturn(edgeCount);
    }

    private void stubNetwork2(
            String collectionName,
            String networkName,
            long networkSuid,
            Long viewSuid,
            int nodeCount,
            int edgeCount) {
        when(subNetwork2.getRootNetwork()).thenReturn(rootNetwork2);
        when(rootNetwork2.getRow(rootNetwork2)).thenReturn(rootRow2);
        when(rootRow2.get(CyNetwork.NAME, String.class)).thenReturn(collectionName);
        when(subNetwork2.getRow(subNetwork2)).thenReturn(networkRow2);
        when(networkRow2.get(CyNetwork.NAME, String.class)).thenReturn(networkName);
        when(subNetwork2.getSUID()).thenReturn(networkSuid);
        when(subNetwork2.getNodeCount()).thenReturn(nodeCount);
        when(subNetwork2.getEdgeCount()).thenReturn(edgeCount);
    }

    private String callTool() throws Exception {
        transport = new InMemoryTransport();
        transport.startServer("cytoscape-mcp-test", "0.0.0-test", List.of(tool.toSpec()));

        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(TOOL_CALL);
        transport.await();

        return transport.getResponse();
    }

    private static int countOccurrences(String text, String substring) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(substring, idx)) != -1) {
            count++;
            idx += substring.length();
        }
        return count;
    }

    // -----------------------------------------------------------------------
    // Schema tests
    // -----------------------------------------------------------------------

    @Test
    public void inputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(GetLoadedNetworkViewsTool.INPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
    }

    @Test
    public void inputSchema_typeIsObject() throws Exception {
        JsonNode schema = MAPPER.readTree(GetLoadedNetworkViewsTool.INPUT_SCHEMA);
        assertEquals("object", schema.get("type").asText());
    }

    @Test
    public void inputSchema_requiredIsEmpty() throws Exception {
        JsonNode required = MAPPER.readTree(GetLoadedNetworkViewsTool.INPUT_SCHEMA).get("required");
        assertNotNull(required);
        assertTrue(required.isArray());
        assertEquals(0, required.size());
    }

    @Test
    public void inputSchema_propertiesIsEmptyObject() throws Exception {
        JsonNode schema = MAPPER.readTree(GetLoadedNetworkViewsTool.INPUT_SCHEMA);
        JsonNode props = schema.get("properties");
        assertNotNull(props);
        assertTrue(props.isObject());
        assertEquals(0, props.size());
    }
}
