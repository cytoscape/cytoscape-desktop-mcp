package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.io.ByteArrayInputStream;
import java.io.File;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.idekerlab.cytoscapemcp.fixture.InMemoryTransport;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.io.read.CyNetworkReader;
import org.cytoscape.io.read.InputStreamTaskFactory;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyRow;
import org.cytoscape.property.CyProperty;
import org.cytoscape.task.read.LoadNetworkFileTaskFactory;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.work.FinishStatus;
import org.cytoscape.work.Task;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskManager;
import org.cytoscape.work.TaskObserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
    @Mock private LoadNetworkFileTaskFactory loadFileTaskFactory;
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
                                cxReaderFactory,
                                loadFileTaskFactory));

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
    // Success — basic load (NDEx)
    // -----------------------------------------------------------------------

    @Test
    public void successfulLoad_returnsJsonWithNetworkName() throws Exception {
        stubSuccessfulLoad("Human PPI");

        String response = callTool(VALID_UUID);

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Response should contain status success",
                response.contains("\\\"status\\\":\\\"success\\\""));
        assertTrue(
                "Response should contain network_name",
                response.contains("\\\"network_name\\\":\\\"Human PPI\\\""));
        assertTrue(
                "Response should contain network_suid",
                response.contains("\\\"network_suid\\\":100"));
        assertTrue(
                "Response should contain node_count", response.contains("\\\"node_count\\\":50"));
        assertTrue(
                "Response should contain edge_count", response.contains("\\\"edge_count\\\":75"));
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
        assertTrue(
                "Should fall back to UUID in network_name",
                response.contains("\\\"network_name\\\":\\\"" + VALID_UUID + "\\\""));
    }

    @Test
    public void customNdexBaseUrl_isHonoured() throws Exception {
        props.setProperty("mcp.ndexbaseurl", "https://internal.ndex.example.com");
        stubSuccessfulLoad("Internal Net");

        String response = callTool(VALID_UUID);

        assertFalse(response.contains("\"isError\":true"));
        assertTrue(
                "Response should contain network name",
                response.contains("\\\"network_name\\\":\\\"Internal Net\\\""));
    }

    // -----------------------------------------------------------------------
    // Failure — input validation
    // -----------------------------------------------------------------------

    @Test
    public void missingSource_returnsError() throws Exception {
        String response =
                callToolRaw(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                                + "\"params\":{\"name\":\"load_cytoscape_network_view\","
                                + "\"arguments\":{}}}");

        assertTrue("Should be an error", response.contains("\"isError\":true"));
        assertTrue("Should mention source", response.contains("source"));
        verify(networkManager, never()).addNetwork(any());
    }

    @Test
    public void invalidSource_returnsError() throws Exception {
        String response =
                callToolRaw(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                                + "\"params\":{\"name\":\"load_cytoscape_network_view\","
                                + "\"arguments\":{\"source\":\"ftp\"}}}");

        assertTrue("Should be an error", response.contains("\"isError\":true"));
        assertTrue("Should mention invalid source", response.contains("ftp"));
        assertTrue("Should list valid sources", response.contains("ndex"));
        verify(networkManager, never()).addNetwork(any());
    }

    @Test
    public void missingNetworkId_returnsError() throws Exception {
        String response =
                callToolRaw(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                                + "\"params\":{\"name\":\"load_cytoscape_network_view\","
                                + "\"arguments\":{\"source\":\"ndex\"}}}");

        assertTrue("Should be an error", response.contains("\"isError\":true"));
        assertTrue("Should mention network_id", response.contains("network_id"));
        verify(networkManager, never()).addNetwork(any());
    }

    @Test
    public void blankNetworkId_returnsError() throws Exception {
        String response =
                callToolRaw(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                                + "\"params\":{\"name\":\"load_cytoscape_network_view\","
                                + "\"arguments\":{\"source\":\"ndex\","
                                + "\"network_id\":\"   \"}}}");

        assertTrue(response.contains("\"isError\":true"));
        verify(networkManager, never()).addNetwork(any());
    }

    // -----------------------------------------------------------------------
    // Success — network-file source
    // -----------------------------------------------------------------------

    @Test
    public void networkFileLoad_success() throws Exception {
        File tempFile = stubSuccessfulFileLoad("Yeast SIF");

        String response =
                callToolRaw(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                                + "\"params\":{\"name\":\"load_cytoscape_network_view\","
                                + "\"arguments\":{\"source\":\"network-file\","
                                + "\"file_path\":\""
                                + tempFile.getAbsolutePath()
                                + "\"}}}");

        assertFalse("Should not be an error response", response.contains("\"isError\":true"));
        assertTrue(
                "Response should contain status success",
                response.contains("\\\"status\\\":\\\"success\\\""));
        assertTrue(
                "Response should contain network_name",
                response.contains("\\\"network_name\\\":\\\"Yeast SIF\\\""));
        assertTrue(
                "Response should contain network_suid",
                response.contains("\\\"network_suid\\\":200"));
        assertTrue(
                "Response should contain node_count", response.contains("\\\"node_count\\\":30"));
        assertTrue(
                "Response should contain edge_count", response.contains("\\\"edge_count\\\":45"));
    }

    @Test
    public void networkFileLoad_missingFilePath() throws Exception {
        String response =
                callToolRaw(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                                + "\"params\":{\"name\":\"load_cytoscape_network_view\","
                                + "\"arguments\":{\"source\":\"network-file\"}}}");

        assertTrue("Should be an error", response.contains("\"isError\":true"));
        assertTrue("Should mention file_path", response.contains("file_path"));
    }

    @Test
    public void networkFileLoad_fileNotFound() throws Exception {
        String response =
                callToolRaw(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                                + "\"params\":{\"name\":\"load_cytoscape_network_view\","
                                + "\"arguments\":{\"source\":\"network-file\","
                                + "\"file_path\":\"/nonexistent/path/test.sif\"}}}");

        assertTrue("Should be an error", response.contains("\"isError\":true"));
        assertTrue("Should mention File not found", response.contains("File not found"));
    }

    @Test
    public void networkFileLoad_taskFails() throws Exception {
        File tempFile = File.createTempFile("test-network", ".sif");
        tempFile.deleteOnExit();

        when(loadFileTaskFactory.createTaskIterator(any(File.class)))
                .thenReturn(new TaskIterator((Task) networkReader));

        doAnswer(
                        invocation -> {
                            TaskObserver observer = invocation.getArgument(1);
                            observer.allFinished(
                                    FinishStatus.newFailed(
                                            null, new RuntimeException("Corrupt SIF file")));
                            return null;
                        })
                .when(taskManager)
                .execute(any(TaskIterator.class), any(TaskObserver.class));

        String response =
                callToolRaw(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                                + "\"params\":{\"name\":\"load_cytoscape_network_view\","
                                + "\"arguments\":{\"source\":\"network-file\","
                                + "\"file_path\":\""
                                + tempFile.getAbsolutePath()
                                + "\"}}}");

        assertTrue("Should be an error", response.contains("\"isError\":true"));
        assertTrue("Should surface the error message", response.contains("Corrupt SIF file"));
    }

    @Test
    public void networkFileLoad_noNetworkAfterLoad() throws Exception {
        File tempFile = File.createTempFile("test-network", ".sif");
        tempFile.deleteOnExit();

        when(loadFileTaskFactory.createTaskIterator(any(File.class)))
                .thenReturn(new TaskIterator((Task) networkReader));
        when(appManager.getCurrentNetwork()).thenReturn(null);

        doAnswer(
                        invocation -> {
                            TaskObserver observer = invocation.getArgument(1);
                            observer.allFinished(FinishStatus.getSucceeded());
                            return null;
                        })
                .when(taskManager)
                .execute(any(TaskIterator.class), any(TaskObserver.class));

        String response =
                callToolRaw(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                                + "\"params\":{\"name\":\"load_cytoscape_network_view\","
                                + "\"arguments\":{\"source\":\"network-file\","
                                + "\"file_path\":\""
                                + tempFile.getAbsolutePath()
                                + "\"}}}");

        assertTrue("Should be an error", response.contains("\"isError\":true"));
        assertTrue("Should mention No network", response.contains("No network"));
    }

    // -----------------------------------------------------------------------
    // Failure — stub handlers for unimplemented sources
    // -----------------------------------------------------------------------

    @Test
    public void tabularFileSource_returnsNotImplemented() throws Exception {
        String response =
                callToolRaw(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                                + "\"params\":{\"name\":\"load_cytoscape_network_view\","
                                + "\"arguments\":{\"source\":\"tabular-file\","
                                + "\"file_path\":\"/tmp/test.csv\","
                                + "\"source_column\":\"A\","
                                + "\"target_column\":\"B\","
                                + "\"delimiter_char_code\":44,"
                                + "\"use_header_row\":true}}}");

        assertTrue("Should be an error", response.contains("\"isError\":true"));
        assertTrue("Should mention not yet implemented", response.contains("not yet implemented"));
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

    /**
     * Stubs mocks for a successful network file load via LoadNetworkFileTaskFactory. Creates a real
     * temp file so that file.exists() passes. Returns the temp file for embedding in the JSON-RPC
     * call.
     */
    private File stubSuccessfulFileLoad(String networkName) throws Exception {
        File tempFile = File.createTempFile("test-network", ".sif");
        tempFile.deleteOnExit();

        when(loadFileTaskFactory.createTaskIterator(any(File.class)))
                .thenReturn(new TaskIterator((Task) networkReader));
        when(appManager.getCurrentNetwork()).thenReturn(network);

        when(network.getRow(network)).thenReturn(networkRow);
        when(network.getSUID()).thenReturn(200L);
        when(network.getNodeCount()).thenReturn(30);
        when(network.getEdgeCount()).thenReturn(45);
        when(networkRow.get(CyNetwork.NAME, String.class)).thenReturn(networkName);

        doAnswer(
                        invocation -> {
                            TaskObserver observer = invocation.getArgument(1);
                            observer.allFinished(FinishStatus.getSucceeded());
                            return null;
                        })
                .when(taskManager)
                .execute(any(TaskIterator.class), any(TaskObserver.class));

        return tempFile;
    }

    /** Stubs mocks for a successful network load via CyNetworkReaderManager. */
    private void stubSuccessfulLoad(String networkName) {
        when(cxReaderFactory.createTaskIterator(any(InputStream.class), isNull()))
                .thenReturn(new TaskIterator((Task) networkReader));
        when(networkReader.getNetworks()).thenReturn(new CyNetwork[] {network});
        when(networkReader.buildCyNetworkView(network)).thenReturn(networkView);

        when(network.getRow(network)).thenReturn(networkRow);
        when(network.getSUID()).thenReturn(100L);
        when(network.getNodeCount()).thenReturn(50);
        when(network.getEdgeCount()).thenReturn(75);
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
                + "\"arguments\":{\"source\":\"ndex\",\"network_id\":\""
                + networkId
                + "\"}}}";
    }

    /**
     * Sends the MCP init handshake + a {@code tools/call} for load_cytoscape_network_view with the
     * given network_id, and returns the raw server output.
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

    // -----------------------------------------------------------------------
    // Schema tests
    // -----------------------------------------------------------------------

    @Test
    public void inputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(LoadNetworkViewTool.INPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
    }

    @Test
    public void inputSchema_typeIsObject() throws Exception {
        JsonNode schema = MAPPER.readTree(LoadNetworkViewTool.INPUT_SCHEMA);
        assertEquals("object", schema.get("type").asText());
    }

    @Test
    public void inputSchema_requiredContainsSource() throws Exception {
        JsonNode required = MAPPER.readTree(LoadNetworkViewTool.INPUT_SCHEMA).get("required");
        assertNotNull(required);
        assertTrue(required.isArray());
        assertEquals(1, required.size());
        assertEquals("source", required.get(0).asText());
    }

    @Test
    public void inputSchema_sourcePropertyHasThreeEnumValues() throws Exception {
        JsonNode sourceEnum =
                MAPPER.readTree(LoadNetworkViewTool.INPUT_SCHEMA).at("/properties/source/enum");
        assertFalse(sourceEnum.isMissingNode());
        assertTrue(sourceEnum.isArray());
        assertEquals(3, sourceEnum.size());
    }

    @Test
    public void inputSchema_allExpectedPropertiesPresent() throws Exception {
        JsonNode props = MAPPER.readTree(LoadNetworkViewTool.INPUT_SCHEMA).get("properties");
        assertNotNull(props);
        for (String key :
                new String[] {
                    "source",
                    "network_id",
                    "file_path",
                    "source_column",
                    "target_column",
                    "interaction_column",
                    "delimiter_char_code",
                    "use_header_row",
                    "excel_sheet",
                    "node_attributes_sheet",
                    "node_attributes_key_column",
                    "node_attributes_source_columns",
                    "node_attributes_target_columns"
                }) {
            assertFalse("Missing property: " + key, props.path(key).isMissingNode());
        }
    }

    @Test
    public void inputSchema_arrayPropertiesHaveStringItems() throws Exception {
        JsonNode schema = MAPPER.readTree(LoadNetworkViewTool.INPUT_SCHEMA);
        assertEquals(
                "string",
                schema.at("/properties/node_attributes_source_columns/items/type").asText());
        assertEquals(
                "string",
                schema.at("/properties/node_attributes_target_columns/items/type").asText());
    }

    @Test
    public void inputSchema_delimiterCharCodeIsInteger() throws Exception {
        JsonNode schema = MAPPER.readTree(LoadNetworkViewTool.INPUT_SCHEMA);
        assertEquals("integer", schema.at("/properties/delimiter_char_code/type").asText());
    }

    @Test
    public void inputSchema_useHeaderRowIsBoolean() throws Exception {
        JsonNode schema = MAPPER.readTree(LoadNetworkViewTool.INPUT_SCHEMA);
        assertEquals("boolean", schema.at("/properties/use_header_row/type").asText());
    }
}
