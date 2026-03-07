package edu.ucsd.idekerlab.cytoscapemcp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import io.modelcontextprotocol.spec.ProtocolVersions;
import reactor.core.publisher.Mono;

public class McpTransportProviderTest {

    private static final String ACCEPT_BOTH = "application/json, text/event-stream";
    private static final String ACCEPT_JSON_ONLY = "application/json";
    private static final String ACCEPT_STREAM_ONLY = "text/event-stream";

    private McpTransportProvider provider;
    private ObjectMapper mapper;

    // Mocks for the session factory / session
    private McpStreamableServerSession.Factory factory;
    private McpStreamableServerSession session;

    @Before
    public void setUp() {
        mapper = new ObjectMapper();
        provider = new McpTransportProvider(mapper);

        factory = mock(McpStreamableServerSession.Factory.class);
        session = mock(McpStreamableServerSession.class);
        when(session.getId()).thenReturn("test-session-id");
    }

    // -------------------------------------------------------------------------
    // protocolVersions / isRunning
    // -------------------------------------------------------------------------

    @Test
    public void protocolVersions_containsBothSupportedVersions() {
        assertTrue(provider.protocolVersions().contains(ProtocolVersions.MCP_2024_11_05));
        assertTrue(provider.protocolVersions().contains(ProtocolVersions.MCP_2025_03_26));
        assertTrue(provider.protocolVersions().contains(ProtocolVersions.MCP_2025_06_18));
        assertTrue(provider.protocolVersions().contains(ProtocolVersions.MCP_2025_11_25));
    }

    @Test
    public void isRunning_trueBeforeClose() {
        assertTrue(provider.isRunning());
    }

    @Test
    public void isRunning_falseAfterCloseGracefully() {
        provider.closeGracefully().block();
        assertFalse(provider.isRunning());
    }

    // -------------------------------------------------------------------------
    // Accept header validation
    // -------------------------------------------------------------------------

    @Test
    public void post_nullAccept_returns400() {
        Response r = provider.handlePost(null, null, body("{}"));
        assertEquals(400, r.getStatus());
    }

    @Test
    public void post_acceptMissingTextEventStream_returns400() {
        Response r = provider.handlePost(ACCEPT_JSON_ONLY, null, body("{}"));
        assertEquals(400, r.getStatus());
    }

    @Test
    public void post_acceptMissingApplicationJson_returns400() {
        Response r = provider.handlePost(ACCEPT_STREAM_ONLY, null, body("{}"));
        assertEquals(400, r.getStatus());
    }

    // -------------------------------------------------------------------------
    // Bad / malformed request bodies
    // -------------------------------------------------------------------------

    @Test
    public void post_malformedJson_returns400() {
        Response r = provider.handlePost(ACCEPT_BOTH, null, body("not-json"));
        assertEquals(400, r.getStatus());
    }

    @Test
    public void post_emptyBody_returns400() {
        Response r = provider.handlePost(ACCEPT_BOTH, null, body(""));
        assertEquals(400, r.getStatus());
    }

    // -------------------------------------------------------------------------
    // Shutdown guard
    // -------------------------------------------------------------------------

    @Test
    public void post_whileClosing_returns503() {
        provider.closeGracefully().block();
        Response r = provider.handlePost(ACCEPT_BOTH, null, body(initializeJson(1)));
        assertEquals(503, r.getStatus());
    }

    @Test
    public void delete_whileClosing_returns503() {
        provider.closeGracefully().block();
        Response r = provider.handleDelete("any-session-id");
        assertEquals(503, r.getStatus());
    }

    // -------------------------------------------------------------------------
    // Initialize happy path
    // -------------------------------------------------------------------------

    @Test
    public void post_initialize_returns200() {
        wireFactory();
        Response r = provider.handlePost(ACCEPT_BOTH, null, body(initializeJson(1)));
        assertEquals(200, r.getStatus());
    }

    @Test
    public void post_initialize_responsIsApplicationJson() {
        wireFactory();
        Response r = provider.handlePost(ACCEPT_BOTH, null, body(initializeJson(1)));
        assertTrue(r.getMediaType().toString().contains("application/json"));
    }

    @Test
    public void post_initialize_sessionIdReturnedInHeader() {
        wireFactory();
        Response r = provider.handlePost(ACCEPT_BOTH, null, body(initializeJson(1)));
        assertEquals("test-session-id", r.getHeaderString("mcp-session-id"));
    }

    @Test
    public void post_initialize_responseBodyContainsServerInfo() throws Exception {
        wireFactory();
        Response r = provider.handlePost(ACCEPT_BOTH, null, body(initializeJson(1)));
        String body = r.getEntity().toString();
        assertTrue("body should contain protocolVersion", body.contains("protocolVersion"));
        assertTrue("body should contain serverInfo", body.contains("serverInfo"));
    }

    // -------------------------------------------------------------------------
    // Non-initialize requests — session lookup
    // -------------------------------------------------------------------------

    @Test
    public void post_toolCall_nullSessionId_returns400() {
        wireFactory();
        String toolCallJson =
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}";
        Response r = provider.handlePost(ACCEPT_BOTH, null, body(toolCallJson));
        assertEquals(400, r.getStatus());
    }

    @Test
    public void post_toolCall_unknownSessionId_returns404() {
        wireFactory();
        String toolCallJson =
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}";
        Response r = provider.handlePost(ACCEPT_BOTH, "unknown-session", body(toolCallJson));
        assertEquals(404, r.getStatus());
    }

    @Test
    public void post_notification_unknownSessionId_returns404() {
        wireFactory();
        String notifJson =
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}";
        Response r = provider.handlePost(ACCEPT_BOTH, "ghost-session", body(notifJson));
        assertEquals(404, r.getStatus());
    }

    // -------------------------------------------------------------------------
    // Notification / Response sent to known session
    // -------------------------------------------------------------------------

    @Test
    public void post_notification_toKnownSession_returns202() {
        wireFactory();
        // Initialize to register the session
        provider.handlePost(ACCEPT_BOTH, null, body(initializeJson(1)));

        when(session.accept(any(McpSchema.JSONRPCNotification.class))).thenReturn(Mono.empty());

        String notifJson =
                "{\"jsonrpc\":\"2.0\","
                        + "\"method\":\"notifications/initialized\","
                        + "\"params\":{}}";
        Response r = provider.handlePost(ACCEPT_BOTH, "test-session-id", body(notifJson));
        assertEquals(202, r.getStatus());
    }

    @Test
    public void post_jsonrpcResponse_toKnownSession_returns202() {
        wireFactory();
        provider.handlePost(ACCEPT_BOTH, null, body(initializeJson(1)));

        when(session.accept(any(McpSchema.JSONRPCResponse.class))).thenReturn(Mono.empty());

        String responseJson = "{\"jsonrpc\":\"2.0\",\"id\":\"msg-1\",\"result\":{}}";
        Response r = provider.handlePost(ACCEPT_BOTH, "test-session-id", body(responseJson));
        assertEquals(202, r.getStatus());
    }

    // -------------------------------------------------------------------------
    // Streaming tool call
    // -------------------------------------------------------------------------

    @Test
    public void post_jsonrpcRequest_toKnownSession_returnsStreamingOutput() {
        wireFactory();
        provider.handlePost(ACCEPT_BOTH, null, body(initializeJson(1)));

        when(session.responseStream(any(), any())).thenReturn(Mono.empty());

        String toolCallJson =
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}";
        Response r = provider.handlePost(ACCEPT_BOTH, "test-session-id", body(toolCallJson));

        assertEquals(200, r.getStatus());
        assertTrue(
                "content type should be text/event-stream",
                r.getMediaType().toString().contains("text/event-stream"));
        assertNotNull("entity should be StreamingOutput", r.getEntity());
        assertTrue(r.getEntity() instanceof StreamingOutput);
    }

    @Test
    public void post_jsonrpcRequest_streamingOutput_invokesResponseStream() throws Exception {
        wireFactory();
        provider.handlePost(ACCEPT_BOTH, null, body(initializeJson(1)));

        when(session.responseStream(any(), any())).thenReturn(Mono.empty());

        String toolCallJson =
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}";
        Response r = provider.handlePost(ACCEPT_BOTH, "test-session-id", body(toolCallJson));

        // Execute the StreamingOutput to verify it calls session.responseStream()
        StreamingOutput so = (StreamingOutput) r.getEntity();
        so.write(new ByteArrayOutputStream());

        verify(session).responseStream(any(), any());
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------

    @Test
    public void delete_nullSessionId_returns400() {
        Response r = provider.handleDelete(null);
        assertEquals(400, r.getStatus());
    }

    @Test
    public void delete_unknownSessionId_returns404() {
        Response r = provider.handleDelete("does-not-exist");
        assertEquals(404, r.getStatus());
    }

    @Test
    public void delete_knownSession_returns200() {
        wireFactory();
        provider.handlePost(ACCEPT_BOTH, null, body(initializeJson(1)));

        when(session.delete()).thenReturn(Mono.empty());

        Response r = provider.handleDelete("test-session-id");
        assertEquals(200, r.getStatus());
    }

    @Test
    public void delete_knownSession_removesSessionFromMap() {
        wireFactory();
        provider.handlePost(ACCEPT_BOTH, null, body(initializeJson(1)));

        when(session.delete()).thenReturn(Mono.empty());
        provider.handleDelete("test-session-id");

        // Second delete should 404 now that the session is gone
        Response r2 = provider.handleDelete("test-session-id");
        assertEquals(404, r2.getStatus());
    }

    // -------------------------------------------------------------------------
    // notifyClients
    // -------------------------------------------------------------------------

    @Test
    public void notifyClients_withNoSessions_completesWithoutError() {
        // Should complete without throwing
        provider.notifyClients("notifications/message", null).block();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private InputStream body(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    private String initializeJson(int id) {
        return "{\"jsonrpc\":\"2.0\",\"id\":"
                + id
                + ",\"method\":\"initialize\","
                + "\"params\":{"
                + "\"protocolVersion\":\"2025-03-26\","
                + "\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}"
                + "}}";
    }

    /** Wires a mock session factory that returns a fixed session + InitializeResult. */
    private void wireFactory() {
        McpSchema.InitializeResult initResult =
                new McpSchema.InitializeResult(
                        "2025-03-26",
                        new McpSchema.ServerCapabilities(null, null, null, null, null, null),
                        new McpSchema.Implementation("test-server", "1.0"),
                        null);

        McpStreamableServerSession.McpStreamableServerSessionInit init =
                new McpStreamableServerSession.McpStreamableServerSessionInit(
                        session, Mono.just(initResult));

        when(factory.startSession(any())).thenReturn(init);
        provider.setSessionFactory(factory);
    }
}
