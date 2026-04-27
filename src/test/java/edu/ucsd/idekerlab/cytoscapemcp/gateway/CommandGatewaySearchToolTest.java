package edu.ucsd.idekerlab.cytoscapemcp.gateway;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.idekerlab.cytoscapemcp.fixture.InMemoryTransport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Exercises {@link CommandGatewaySearchTool} end-to-end via {@link InMemoryTransport} backed by a
 * real Lucene index pre-populated with test commands.
 */
public class CommandGatewaySearchToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String INIT_REQUEST =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\","
                    + "\"params\":{\"protocolVersion\":\"2025-03-26\","
                    + "\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";

    private static final String INITIALIZED_NOTIFICATION =
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

    private static String searchCall(String query, int max) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"command_gateway_search\","
                + "\"arguments\":{\"query\":\""
                + query
                + "\",\"max\":"
                + max
                + "}}}";
    }

    private CommandService commandService;
    private InMemoryTransport transport;

    @Before
    public void setUp() throws Exception {
        commandService = new CommandService();
        // Pre-populate index with a few commands
        commandService.upsert(
                new Command(
                        "network select",
                        "network",
                        "select",
                        "Select nodes in the network",
                        null,
                        "[]",
                        "nodeList Select nodes in the network by attribute",
                        "nodeList",
                        "{\"nodeList\":[]}",
                        true));
        commandService.upsert(
                new Command(
                        "layout force-directed",
                        "layout",
                        "force-directed",
                        "Apply force-directed layout algorithm",
                        null,
                        "[]",
                        "networkSUID",
                        "networkSUID",
                        null,
                        false));
        commandService.upsert(
                new Command(
                        "table export",
                        "table",
                        "export",
                        "Export table data to a file",
                        null,
                        "[]",
                        "filePath outputFormat table export",
                        "filePath|outputFormat",
                        null,
                        false));

        transport = new InMemoryTransport();
        transport.startServer(
                "test", "1.0", List.of(new CommandGatewaySearchTool(commandService).toSpec()));
    }

    @After
    public void tearDown() {
        transport.close();
        if (commandService != null) commandService.close();
    }

    @Test
    public void search_matchingQuery_returnsRankedResults() throws Exception {
        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(searchCall("select nodes", 10));
        transport.await();

        JsonNode response = lastResponse();
        assertFalse(response.path("error").asBoolean(false));
        JsonNode content = response.at("/result/structuredContent");
        assertTrue(content.path("success").asBoolean());
        JsonNode results = content.path("results");
        assertFalse(results.isEmpty());
        assertEquals("network select", results.get(0).path("commandKey").asText());
    }

    @Test
    public void search_namespaceQuery_filtersCorrectly() throws Exception {
        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(searchCall("namespace:table", 10));
        transport.await();

        JsonNode response = lastResponse();
        JsonNode content = response.at("/result/structuredContent");
        assertTrue(content.path("success").asBoolean());
        JsonNode results = content.path("results");
        assertEquals(1, results.size());
        assertEquals("table export", results.get(0).path("commandKey").asText());
    }

    @Test
    public void search_missingQuery_returnsError() throws Exception {
        String call =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"command_gateway_search\","
                        + "\"arguments\":{\"max\":5}}}";
        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(call);
        transport.await();

        JsonNode response = lastResponse();
        assertTrue(response.at("/result/isError").asBoolean());
    }

    @Test
    public void search_malformedLuceneQuery_returnsSuccessFalse() throws Exception {
        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(searchCall("field:*bad[", 5));
        transport.await();

        JsonNode response = lastResponse();
        JsonNode content = response.at("/result/structuredContent");
        assertFalse(content.path("success").asBoolean());
        assertNotNull(content.path("failure").asText());
    }

    @Test
    public void search_noMatchingResults_returnsEmptyList() throws Exception {
        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(searchCall("zzznomatchxxx", 10));
        transport.await();

        JsonNode response = lastResponse();
        JsonNode content = response.at("/result/structuredContent");
        assertTrue(content.path("success").asBoolean());
        assertEquals(0, content.path("results").size());
    }

    @Test
    public void search_nullCommandService_returnsError() throws Exception {
        InMemoryTransport nullTransport = new InMemoryTransport();
        nullTransport.startServer(
                "test", "1.0", List.of(new CommandGatewaySearchTool(null).toSpec()));
        try {
            nullTransport.send(INIT_REQUEST);
            nullTransport.send(INITIALIZED_NOTIFICATION);
            nullTransport.send(searchCall("network", 5));
            nullTransport.await();
            JsonNode response = lastResponse(nullTransport.getResponse());
            assertTrue(response.at("/result/isError").asBoolean());
        } finally {
            nullTransport.close();
        }
    }

    // -- Helpers --------------------------------------------------------------

    private JsonNode lastResponse() throws Exception {
        return lastResponse(transport.getResponse());
    }

    private static JsonNode lastResponse(String allOutput) throws Exception {
        String[] lines = allOutput.split("\n");
        return MAPPER.readTree(lines[lines.length - 1]);
    }
}
