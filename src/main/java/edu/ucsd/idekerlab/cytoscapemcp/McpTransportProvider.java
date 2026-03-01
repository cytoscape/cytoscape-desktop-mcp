package edu.ucsd.idekerlab.cytoscapemcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import io.modelcontextprotocol.spec.McpStreamableServerTransport;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.ProtocolVersions;
import reactor.core.publisher.Mono;

/**
 * JAX-RS port of the MCP SDK's {@code HttpServletStreamableServerTransportProvider}.
 *
 * <h3>Why a port is required</h3>
 *
 * <p>The MCP SDK ships {@code HttpServletStreamableServerTransportProvider}, which
 * extends {@code jakarta.servlet.http.HttpServlet} and holds a
 * {@code jakarta.servlet.AsyncContext} per streaming response. That class cannot be used
 * here for two reasons:
 *
 * <ol>
 *   <li><b>Namespace mismatch.</b> CyREST runs on Pax Web / Jetty 9.4 (Servlet 3.1,
 *       {@code javax.servlet}). The SDK uses {@code jakarta.servlet} 6.1, which is a
 *       different namespace requiring Servlet 6.0+ / Jetty 12+. The two are binary
 *       incompatible.</li>
 *   <li><b>JAX-RS hand-off boundary.</b> The MCP HTTP endpoint in this app is
 *       {@link McpEndpoint} — a plain JAX-RS {@code @Path("/mcp")} resource class
 *       registered as an OSGi service via publisher-5.3. publisher-5.3 discovers the
 *       {@code @Path} annotation and mounts the class into Jersey. By the time a request
 *       reaches {@link McpEndpoint}, it has already passed through Jersey's request
 *       pipeline: the method parameters are a deserialized {@link InputStream} body and
 *       {@code @HeaderParam} strings. There is no {@code HttpServletRequest} or
 *       {@code AsyncContext} in scope — {@link McpEndpoint} cannot delegate to a class
 *       that requires them.</li>
 * </ol>
 *
 * <h3>What this class does instead</h3>
 *
 * <p>This class re-implements the same MCP wire-protocol logic as the SDK transport,
 * using only standard JAX-RS and Java IO types:
 * <ul>
 *   <li>Request body — {@link InputStream} passed from {@link McpEndpoint}</li>
 *   <li>Single JSON response — {@code Response.ok(json).type(application/json)}</li>
 *   <li>Streaming response — {@code Response.ok(StreamingOutput).type(text/event-stream)};
 *       {@link StreamingOutput#write(java.io.OutputStream)} runs on a Jetty worker thread
 *       and blocks until {@code session.responseStream(...).block()} completes, keeping
 *       the HTTP connection alive for the duration of the stream</li>
 * </ul>
 *
 * <h3>Transport</h3>
 *
 * <p>Implements the <b>MCP 2025-03-26 Streamable HTTP transport</b>
 * ({@link McpStreamableServerTransportProvider}) only. The deprecated HTTP+SSE transport
 * (separate {@code GET /sse} and {@code POST /messages} endpoints) is not supported.
 *
 * <p>This class is a pure internal delegate — it carries no JAX-RS {@code @Path}
 * annotation and is not registered as an OSGi service directly. {@link McpEndpoint}
 * receives request data and forwards it here.
 */
public class McpTransportProvider implements McpStreamableServerTransportProvider {

    private static final Logger logger = LoggerFactory.getLogger(McpTransportProvider.class);

    public static final String MESSAGE_EVENT_TYPE = "message";
    public static final String UTF_8 = "UTF-8";
    public static final String APPLICATION_JSON = "application/json";
    public static final String TEXT_EVENT_STREAM = "text/event-stream";
    private static final String FAILED_TO_SEND_ERROR_RESPONSE = "Failed to send error response: {}";

    private final ObjectMapper objectMapper;
    private final McpJsonMapper jsonMapper;
    private volatile McpStreamableServerSession.Factory sessionFactory;
    private final ConcurrentHashMap<String, McpStreamableServerSession> sessions =
            new ConcurrentHashMap<>();
    private volatile boolean isClosing = false;

    public McpTransportProvider() {
        this.objectMapper = new ObjectMapper();
        this.jsonMapper = new JacksonMcpJsonMapper(this.objectMapper);
        logger.info("McpTransportProvider instance created (no-arg constructor)");
    }

    public McpTransportProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.jsonMapper = new JacksonMcpJsonMapper(objectMapper);
        logger.info("McpTransportProvider instance created (ObjectMapper constructor)");
    }

    public boolean isRunning() {
        return !isClosing;
    }

    // -------------------------------------------------------------------------
    // McpStreamableServerTransportProvider
    // -------------------------------------------------------------------------

    @Override
    public List<String> protocolVersions() {
        return List.of(
                ProtocolVersions.MCP_2024_11_05,
                ProtocolVersions.MCP_2025_03_26,
                ProtocolVersions.MCP_2025_06_18);
    }

    @Override
    public void setSessionFactory(McpStreamableServerSession.Factory sessionFactory) {
        this.sessionFactory = sessionFactory;
        logger.info(
                "setSessionFactory called — MCP SDK wired to transport; sessionFactory={}",
                sessionFactory != null ? sessionFactory.getClass().getName() : "null");
    }

    @Override
    public Mono<Void> notifyClients(String method, Object params) {
        if (this.sessions.isEmpty()) {
            logger.debug("No active sessions to broadcast message to");
            return Mono.empty();
        }
        logger.debug("Attempting to broadcast message to {} active sessions", this.sessions.size());
        return Mono.fromRunnable(
                () ->
                        this.sessions.values().parallelStream()
                                .forEach(
                                        session -> {
                                            try {
                                                session.sendNotification(method, params).block();
                                            } catch (Exception e) {
                                                logger.error(
                                                        "Failed to send message to session {}: {}",
                                                        session.getId(),
                                                        e.getMessage());
                                            }
                                        }));
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.fromRunnable(
                () -> {
                    this.isClosing = true;
                    logger.debug(
                            "Initiating graceful shutdown with {} active sessions",
                            this.sessions.size());
                    this.sessions.values().parallelStream()
                            .forEach(
                                    session -> {
                                        try {
                                            session.closeGracefully().block();
                                        } catch (Exception e) {
                                            logger.error(
                                                    "Failed to close session {}: {}",
                                                    session.getId(),
                                                    e.getMessage());
                                        }
                                    });
                    this.sessions.clear();
                    logger.debug("Graceful shutdown completed");
                });
    }

    // -------------------------------------------------------------------------
    // HTTP handler methods — called by McpEndpoint (JAX-RS)
    // -------------------------------------------------------------------------

    /**
     * Handles {@code POST /mcp}. Returns a plain JSON response for initialize, or a
     * {@link StreamingOutput} HTTP streaming response for tool requests (blocks Jetty
     * thread until done).
     *
     * <p>Per MCP 2025-03-26, the streaming response uses {@code text/event-stream} wire
     * format. The client {@code Accept} header MUST include both {@code application/json}
     * and {@code text/event-stream}.
     */
    public Response handlePost(String accept, String sessionId, InputStream body) {
        logger.info("handlePost invoked — accept={} sessionId={}", accept, sessionId);
        if (this.isClosing) {
            return Response.status(503).entity("Server is shutting down").build();
        }

        List<String> badRequestErrors = new ArrayList<>();
        if (accept == null || !accept.contains(TEXT_EVENT_STREAM)) {
            badRequestErrors.add("text/event-stream required in Accept header");
        }
        if (accept == null || !accept.contains(APPLICATION_JSON)) {
            badRequestErrors.add("application/json required in Accept header");
        }

        McpTransportContext transportContext = McpTransportContext.EMPTY;

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(body, UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            McpSchema.JSONRPCMessage message =
                    McpSchema.deserializeJsonRpcMessage(jsonMapper, sb.toString());

            if (message instanceof McpSchema.JSONRPCRequest
                    && ((McpSchema.JSONRPCRequest) message)
                            .method()
                            .equals(McpSchema.METHOD_INITIALIZE)) {
                McpSchema.JSONRPCRequest jsonrpcRequest = (McpSchema.JSONRPCRequest) message;
                if (!badRequestErrors.isEmpty()) {
                    return errorResponse(400, badRequestErrors);
                }

                McpSchema.InitializeRequest initializeRequest =
                        objectMapper.convertValue(
                                jsonrpcRequest.params(),
                                new TypeReference<McpSchema.InitializeRequest>() {});
                McpStreamableServerSession.McpStreamableServerSessionInit init =
                        this.sessionFactory.startSession(initializeRequest);
                this.sessions.put(init.session().getId(), init.session());

                try {
                    McpSchema.InitializeResult initResult = init.initResult().block();
                    String jsonResponse =
                            objectMapper.writeValueAsString(
                                    new McpSchema.JSONRPCResponse(
                                            McpSchema.JSONRPC_VERSION,
                                            jsonrpcRequest.id(),
                                            initResult,
                                            null));
                    return Response.ok(jsonResponse)
                            .type(APPLICATION_JSON)
                            .header(HttpHeaders.MCP_SESSION_ID, init.session().getId())
                            .build();
                } catch (Exception e) {
                    logger.error("Failed to initialize session: {}", e.getMessage());
                    return Response.status(500)
                            .entity(
                                    errorJson(
                                            McpSchema.ErrorCodes.INTERNAL_ERROR,
                                            "Failed to initialize session: " + e.getMessage()))
                            .type(APPLICATION_JSON)
                            .build();
                }
            }

            if (sessionId == null || sessionId.isBlank()) {
                badRequestErrors.add("Session ID required in mcp-session-id header");
            }
            if (!badRequestErrors.isEmpty()) {
                return errorResponse(400, badRequestErrors);
            }

            McpStreamableServerSession session = this.sessions.get(sessionId);
            if (session == null) {
                return Response.status(404)
                        .entity(
                                errorJson(
                                        McpSchema.ErrorCodes.INTERNAL_ERROR,
                                        "Session not found: " + sessionId))
                        .type(APPLICATION_JSON)
                        .build();
            }

            final McpTransportContext tc = transportContext;
            final String sid = sessionId;

            if (message instanceof McpSchema.JSONRPCResponse) {
                session.accept((McpSchema.JSONRPCResponse) message)
                        .contextWrite(c -> c.put(McpTransportContext.KEY, tc))
                        .block();
                return Response.accepted().build();
            } else if (message instanceof McpSchema.JSONRPCNotification) {
                session.accept((McpSchema.JSONRPCNotification) message)
                        .contextWrite(c -> c.put(McpTransportContext.KEY, tc))
                        .block();
                return Response.accepted().build();
            } else if (message instanceof McpSchema.JSONRPCRequest) {
                McpSchema.JSONRPCRequest jsonrpcRequest = (McpSchema.JSONRPCRequest) message;
                // HTTP streaming: StreamingOutput.write() keeps the connection alive by blocking
                // the Jetty worker thread until session.responseStream().block() returns.
                // The text/event-stream wire format is required by MCP 2025-03-26 for
                // streaming responses; each JSON-RPC message is written as an event line.
                StreamingOutput stream =
                        (OutputStream output) -> {
                            PrintWriter writer =
                                    new PrintWriter(
                                            new OutputStreamWriter(output, UTF_8), true);
                            McpSessionTransport sessionTransport =
                                    new McpSessionTransport(sid, writer);
                            try {
                                session.responseStream(jsonrpcRequest, sessionTransport)
                                        .contextWrite(c -> c.put(McpTransportContext.KEY, tc))
                                        .block();
                            } catch (Exception e) {
                                logger.error(
                                        "Failed to handle request stream for session {}: {}",
                                        sid,
                                        e.getMessage());
                            }
                        };
                return Response.ok(stream)
                        .type(TEXT_EVENT_STREAM)
                        .header("Cache-Control", "no-cache")
                        .header("Connection", "keep-alive")
                        .header("Access-Control-Allow-Origin", "*")
                        .build();
            } else {
                return Response.status(500)
                        .entity(
                                errorJson(
                                        McpSchema.ErrorCodes.INVALID_REQUEST,
                                        "Unknown message type"))
                        .type(APPLICATION_JSON)
                        .build();
            }
        } catch (IllegalArgumentException | IOException e) {
            logger.error("Failed to deserialize message: {}", e.getMessage());
            return Response.status(400)
                    .entity(
                            errorJson(
                                    McpSchema.ErrorCodes.INVALID_REQUEST,
                                    "Invalid message format: " + e.getMessage()))
                    .type(APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            logger.error("Error handling message: {}", e.getMessage());
            return Response.status(500)
                    .entity(
                            errorJson(
                                    McpSchema.ErrorCodes.INTERNAL_ERROR,
                                    "Error processing message: " + e.getMessage()))
                    .type(APPLICATION_JSON)
                    .build();
        }
    }

    /** Handles {@code DELETE /mcp}. Terminates the named session. */
    public Response handleDelete(String sessionId) {
        logger.info("handleDelete invoked — sessionId={}", sessionId);
        if (this.isClosing) {
            return Response.status(503).entity("Server is shutting down").build();
        }

        if (sessionId == null) {
            return Response.status(400)
                    .entity(
                            errorJson(
                                    McpSchema.ErrorCodes.INVALID_REQUEST,
                                    "Session ID required in mcp-session-id header"))
                    .type(APPLICATION_JSON)
                    .build();
        }

        McpStreamableServerSession session = this.sessions.get(sessionId);
        if (session == null) {
            return Response.status(404).build();
        }

        McpTransportContext tc = McpTransportContext.EMPTY;
        try {
            session.delete()
                    .contextWrite(c -> c.put(McpTransportContext.KEY, tc))
                    .block();
            this.sessions.remove(sessionId);
            return Response.ok().build();
        } catch (Exception e) {
            logger.error("Failed to delete session {}: {}", sessionId, e.getMessage());
            return Response.status(500)
                    .entity(errorJson(McpSchema.ErrorCodes.INTERNAL_ERROR, e.getMessage()))
                    .type(APPLICATION_JSON)
                    .build();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Response errorResponse(int status, List<String> errors) {
        return Response.status(status)
                .entity(
                        errorJson(
                                McpSchema.ErrorCodes.INVALID_REQUEST,
                                String.join("; ", errors)))
                .type(APPLICATION_JSON)
                .build();
    }

    private String errorJson(int code, String message) {
        try {
            return objectMapper.writeValueAsString(
                    McpError.builder(code).message(message).build());
        } catch (Exception e) {
            logger.error(FAILED_TO_SEND_ERROR_RESPONSE, e.getMessage());
            return "{\"error\":\"" + message.replace("\"", "\\\"") + "\"}";
        }
    }

    private void sendEvent(PrintWriter writer, String eventType, String data, String id)
            throws IOException {
        if (id != null) {
            writer.write("id: " + id + "\n");
        }
        writer.write("event: " + eventType + "\n");
        writer.write("data: " + data + "\n\n");
        writer.flush();
        if (writer.checkError()) {
            throw new IOException("Client disconnected");
        }
    }

    // -------------------------------------------------------------------------
    // Inner class: per-session streaming transport (Streamable HTTP, MCP 2025-03-26)
    // -------------------------------------------------------------------------

    private class McpSessionTransport implements McpStreamableServerTransport {

        private final String sessionId;
        private final PrintWriter writer;
        private volatile boolean closed = false;
        private final ReentrantLock lock = new ReentrantLock();

        McpSessionTransport(String sessionId, PrintWriter writer) {
            this.sessionId = sessionId;
            this.writer = writer;
            logger.debug("Streamable session transport {} initialized", sessionId);
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            return sendMessage(message, null);
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message, String messageId) {
            return Mono.fromRunnable(
                    () -> {
                        if (this.closed) {
                            logger.debug(
                                    "Attempted to send message to closed session: {}",
                                    this.sessionId);
                            return;
                        }
                        lock.lock();
                        try {
                            if (this.closed) {
                                logger.debug(
                                        "Session {} was closed during message send attempt",
                                        this.sessionId);
                                return;
                            }
                            String jsonText = objectMapper.writeValueAsString(message);
                            McpTransportProvider.this.sendEvent(
                                    writer,
                                    MESSAGE_EVENT_TYPE,
                                    jsonText,
                                    messageId != null ? messageId : this.sessionId);
                            logger.debug(
                                    "Message sent to session {} with ID {}",
                                    this.sessionId,
                                    messageId);
                        } catch (Exception e) {
                            logger.error(
                                    "Failed to send message to session {}: {}",
                                    this.sessionId,
                                    e.getMessage());
                            McpTransportProvider.this.sessions.remove(this.sessionId);
                            this.closed = true;
                        } finally {
                            lock.unlock();
                        }
                    });
        }

        @Override
        public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
            return objectMapper.convertValue(
                    data, objectMapper.getTypeFactory().constructType(typeRef.getType()));
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.fromRunnable(this::close);
        }

        @Override
        public void close() {
            lock.lock();
            try {
                if (this.closed) {
                    logger.debug("Session transport {} already closed", this.sessionId);
                    return;
                }
                this.closed = true;
                // Flush signals end of HTTP stream. StreamingOutput.write() returns
                // naturally once session.responseStream().block() completes.
                writer.flush();
                logger.debug("Session transport {} closed", this.sessionId);
            } finally {
                lock.unlock();
            }
        }
    }
}
