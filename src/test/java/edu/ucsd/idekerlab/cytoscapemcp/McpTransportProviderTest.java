package edu.ucsd.idekerlab.cytoscapemcp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.modelcontextprotocol.server.transport.ServerTransportSecurityException;
import io.modelcontextprotocol.server.transport.ServerTransportSecurityValidator;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import io.modelcontextprotocol.spec.McpStreamableServerTransport;
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

    @Test
    public void post_unknownSessionId_errorBodyIsCleanJson_noStackTrace() throws Exception {
        wireFactory();
        String toolCallJson =
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}";
        Response r = provider.handlePost(ACCEPT_BOTH, "unknown-session", body(toolCallJson));

        String responseBody = r.getEntity().toString();
        com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(responseBody);

        // Must contain the standard error fields
        assertTrue("body must contain 'code'", node.has("code"));
        assertTrue("body must contain 'message'", node.has("message"));

        // Must NOT look like a serialized Java exception
        assertFalse("body must not contain 'stackTrace'", node.has("stackTrace"));
        assertFalse("body must not contain 'cause'", node.has("cause"));
        assertFalse("body must not contain 'suppressed'", node.has("suppressed"));
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
    // notifyClient
    // -------------------------------------------------------------------------

    @Test
    public void notifyClient_noSessions_returnsEmpty() {
        provider.notifyClient("unknown", "notifications/message", null).block();
    }

    @Test
    public void notifyClient_knownSession_delegatesToSendNotification() {
        wireFactory();
        provider.handlePost(ACCEPT_BOTH, null, body(initializeJson(1)));

        when(session.sendNotification(any(), any())).thenReturn(Mono.empty());

        provider.notifyClient("test-session-id", "notifications/message", null).block();

        verify(session).sendNotification("notifications/message", null);
    }

    // -------------------------------------------------------------------------
    // Security validator
    // -------------------------------------------------------------------------

    @Test
    public void post_securityValidationFailure_returnsValidatorStatusCode() {
        ServerTransportSecurityValidator reject =
                headers -> {
                    throw new ServerTransportSecurityException(403, "Forbidden");
                };
        provider.setSecurityValidator(reject);

        Response r = provider.handlePost(ACCEPT_BOTH, null, body(initializeJson(1)));
        assertEquals(403, r.getStatus());
    }

    @Test
    public void delete_securityValidationFailure_returnsValidatorStatusCode() {
        ServerTransportSecurityValidator reject =
                headers -> {
                    throw new ServerTransportSecurityException(403, "Forbidden");
                };
        provider.setSecurityValidator(reject);

        Response r = provider.handleDelete("some-id");
        assertEquals(403, r.getStatus());
    }

    // -------------------------------------------------------------------------
    // Error code alignment
    // -------------------------------------------------------------------------

    @Test
    public void post_badAccept_errorBodyUsesMethodNotFoundCode() throws Exception {
        Response r = provider.handlePost(ACCEPT_JSON_ONLY, null, body(initializeJson(1)));
        assertEquals(400, r.getStatus());

        com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(r.getEntity().toString());
        assertEquals(McpSchema.ErrorCodes.METHOD_NOT_FOUND, node.get("code").asInt());
    }

    @Test
    public void delete_nullSessionId_errorBodyUsesMethodNotFoundCode() throws Exception {
        Response r = provider.handleDelete(null);
        assertEquals(400, r.getStatus());

        com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(r.getEntity().toString());
        assertEquals(McpSchema.ErrorCodes.METHOD_NOT_FOUND, node.get("code").asInt());
    }

    // -------------------------------------------------------------------------
    // notifyClients with active sessions
    // -------------------------------------------------------------------------

    @Test
    public void notifyClients_withActiveSession_callsSendNotification() {
        wireFactory();
        provider.handlePost(ACCEPT_BOTH, null, body(initializeJson(1)));

        when(session.sendNotification(any(), any())).thenReturn(Mono.empty());

        provider.notifyClients("notifications/message", null).block();

        verify(session).sendNotification("notifications/message", null);
    }

    // -------------------------------------------------------------------------
    // Keep-alive lifecycle
    // -------------------------------------------------------------------------

    @Test
    public void startKeepAlive_thenCloseGracefully_completesWithoutError() {
        provider.startKeepAlive(Duration.ofSeconds(30));
        provider.closeGracefully().block();
        assertFalse(provider.isRunning());
    }

    // -------------------------------------------------------------------------
    // SSE event id regression (Issue #6)
    // -------------------------------------------------------------------------

    @Test
    public void post_streamingOutput_sseEventHasNoIdLineWhenMessageIdIsNull() throws Exception {
        wireFactory();
        provider.handlePost(ACCEPT_BOTH, null, body(initializeJson(1)));

        ArgumentCaptor<McpStreamableServerTransport> transportCaptor =
                ArgumentCaptor.forClass(McpStreamableServerTransport.class);
        when(session.responseStream(any(), transportCaptor.capture()))
                .thenAnswer(
                        inv -> {
                            McpSchema.JSONRPCResponse msg =
                                    new McpSchema.JSONRPCResponse(
                                            McpSchema.JSONRPC_VERSION, 42, null, null);
                            return transportCaptor.getValue().sendMessage(msg, null);
                        });

        String toolCallJson =
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}";
        Response r = provider.handlePost(ACCEPT_BOTH, "test-session-id", body(toolCallJson));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ((StreamingOutput) r.getEntity()).write(baos);
        String sseOutput = baos.toString(StandardCharsets.UTF_8);

        assertFalse(
                "SSE event must not contain id: line when messageId is null",
                sseOutput.contains("\nid: ") || sseOutput.startsWith("id: "));
        assertTrue(
                "SSE event must contain event: message line", sseOutput.contains("event: message"));
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
