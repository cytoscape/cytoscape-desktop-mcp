package edu.ucsd.idekerlab.cytoscapemcp.gateway;

import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.idekerlab.cytoscapemcp.fixture.InMemoryTransport;

import org.cytoscape.command.AvailableCommands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link CommandGatewayGetTool} end-to-end via {@link InMemoryTransport} with mocked
 * {@link AvailableCommands}.
 */
public class CommandGatewayGetToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String INIT_REQUEST =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\","
                    + "\"params\":{\"protocolVersion\":\"2025-03-26\","
                    + "\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";

    private static final String INITIALIZED_NOTIFICATION =
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

    @Mock private AvailableCommands availableCommands;
    private AutoCloseable mocks;
    private InMemoryTransport transport;

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);

        // Stub namespace/command existence checks
        when(availableCommands.getNamespaces()).thenReturn(Arrays.asList("network", "layout"));
        when(availableCommands.getCommands("network")).thenReturn(Arrays.asList("select"));
        when(availableCommands.getCommands("layout")).thenReturn(Arrays.asList("force-directed"));

        // Stub metadata for "network select"
        when(availableCommands.getDescription("network", "select"))
                .thenReturn("Select nodes in the network");
        when(availableCommands.getLongDescription("network", "select")).thenReturn(null);
        when(availableCommands.getSupportsJSON("network", "select")).thenReturn(true);
        when(availableCommands.getExampleJSON("network", "select")).thenReturn("{\"nodeList\":[]}");
        when(availableCommands.getArguments("network", "select"))
                .thenReturn(Arrays.asList("network", "nodeList"));
        doReturn(String.class).when(availableCommands).getArgType("network", "select", "network");
        doReturn(String.class).when(availableCommands).getArgType("network", "select", "nodeList");
        when(availableCommands.getArgRequired("network", "select", "network")).thenReturn(false);
        when(availableCommands.getArgRequired("network", "select", "nodeList")).thenReturn(false);
        when(availableCommands.getArgDescription("network", "select", "network"))
                .thenReturn("Target network");
        when(availableCommands.getArgDescription("network", "select", "nodeList"))
                .thenReturn("Comma-separated node names or 'all'");
        when(availableCommands.getArgExampleStringValue("network", "select", "network"))
                .thenReturn("current");
        when(availableCommands.getArgExampleStringValue("network", "select", "nodeList"))
                .thenReturn("all");

        // Stub metadata for "layout force-directed"
        when(availableCommands.getDescription("layout", "force-directed"))
                .thenReturn("Apply force-directed layout");
        when(availableCommands.getLongDescription("layout", "force-directed")).thenReturn(null);
        when(availableCommands.getSupportsJSON("layout", "force-directed")).thenReturn(false);
        when(availableCommands.getArguments("layout", "force-directed"))
                .thenReturn(Arrays.asList("networkSUID"));
        doReturn(Long.class)
                .when(availableCommands)
                .getArgType("layout", "force-directed", "networkSUID");
        when(availableCommands.getArgRequired("layout", "force-directed", "networkSUID"))
                .thenReturn(false);
        when(availableCommands.getArgDescription("layout", "force-directed", "networkSUID"))
                .thenReturn("Network SUID");
        when(availableCommands.getArgExampleStringValue("layout", "force-directed", "networkSUID"))
                .thenReturn("123");

        transport = new InMemoryTransport();
        transport.startServer(
                "test", "1.0", List.of(new CommandGatewayGetTool(availableCommands).toSpec()));
    }

    @After
    public void tearDown() throws Exception {
        transport.close();
        mocks.close();
    }

    @Test
    public void getCommand_singleKey_returnsFullSchema() throws Exception {
        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(getCall("[\"network select\"]"));
        transport.await();

        JsonNode response = lastResponse();
        JsonNode content = response.at("/result/structuredContent");
        assertTrue(content.path("success").asBoolean());

        JsonNode results = content.path("results");
        assertEquals(1, results.size());
        JsonNode cmd = results.get(0);
        assertEquals("network select", cmd.path("commandKey").asText());
        assertEquals("network", cmd.path("namespace").asText());
        assertEquals("select", cmd.path("commandName").asText());
        assertEquals("Select nodes in the network", cmd.path("description").asText());
        assertTrue(cmd.path("supportsJson").asBoolean());

        // inputSchema should be valid JSON with properties
        String inputSchema = cmd.path("inputSchema").asText();
        JsonNode schema = MAPPER.readTree(inputSchema);
        assertTrue(schema.has("properties"));
        assertTrue(schema.path("properties").has("network"));
        assertTrue(schema.path("properties").has("nodeList"));

        // outputExample present since supportsJson=true
        assertFalse(cmd.path("outputExample").asText().isEmpty());
    }

    @Test
    public void getCommand_batchKeys_returnsMultipleResults() throws Exception {
        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(getCall("[\"network select\",\"layout force-directed\"]"));
        transport.await();

        JsonNode response = lastResponse();
        JsonNode content = response.at("/result/structuredContent");
        assertTrue(content.path("success").asBoolean());
        assertEquals(2, content.path("results").size());
    }

    @Test
    public void getCommand_unknownKey_omittedSilently() throws Exception {
        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(getCall("[\"nonexistent command\"]"));
        transport.await();

        JsonNode response = lastResponse();
        JsonNode content = response.at("/result/structuredContent");
        assertFalse(content.path("success").asBoolean());
        assertEquals(0, content.path("results").size());
    }

    @Test
    public void getCommand_missingCommandKeys_returnsError() throws Exception {
        String call =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"command_gateway_get\","
                        + "\"arguments\":{}}}";
        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(call);
        transport.await();

        JsonNode response = lastResponse();
        assertTrue(response.at("/result/isError").asBoolean());
    }

    @Test
    public void getCommand_supportsJsonFalse_outputExampleNull() throws Exception {
        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(getCall("[\"layout force-directed\"]"));
        transport.await();

        JsonNode response = lastResponse();
        JsonNode content = response.at("/result/structuredContent");
        assertTrue(content.path("success").asBoolean());
        JsonNode cmd = content.path("results").get(0);
        assertFalse(cmd.path("supportsJson").asBoolean());
        assertTrue(cmd.path("outputExample").isMissingNode() || cmd.path("outputExample").isNull());
    }

    @Test
    public void getCommand_noMatchingKeys_returnsTextError() throws Exception {
        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(getCall("[\"nonexistent command\"]"));
        transport.await();

        JsonNode response = lastResponse();
        assertTrue(response.at("/result/isError").asBoolean());
        String msg = response.at("/result/content/0/text").asText();
        assertTrue(msg.contains("No matching commands found"));
    }

    @Test
    public void getCommand_keysExceedingTen_onlyFirstTenProcessed() throws Exception {
        // Build array of 11 valid-format keys (most won't exist and will be silently skipped)
        StringBuilder keys = new StringBuilder("[");
        keys.append("\"network select\"");
        for (int i = 1; i <= 10; i++) {
            keys.append(",\"ns cmd").append(i).append("\"");
        }
        keys.append("]");

        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(getCall(keys.toString()));
        transport.await();

        // Should complete without error (limit enforced silently)
        JsonNode response = lastResponse();
        assertFalse(response.has("error"));
    }

    // -- Helpers --------------------------------------------------------------

    private static String getCall(String keysJson) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"command_gateway_get\","
                + "\"arguments\":{\"commandKeys\":"
                + keysJson
                + "}}}";
    }

    private JsonNode lastResponse() throws Exception {
        String[] lines = transport.getResponse().split("\n");
        return MAPPER.readTree(lines[lines.length - 1]);
    }
}
