package edu.ucsd.idekerlab.cytoscapemcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import io.modelcontextprotocol.spec.McpStreamableServerTransport;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.ProtocolVersions;
import reactor.core.publisher.Mono;

/**
 * JAX-RS resource that implements the MCP Streamable HTTP transport for Cytoscape.
 *
 * <p>Registered as an OSGi service so CyREST's AutomationAppTracker discovers the {@code @Path}
 * annotation and mounts the endpoint under CyREST's existing HTTP port. The {@code @Api} annotation
 * is intentionally absent so this endpoint does not appear in CyREST's Swagger docs.
 *
 * <p>This is a {@code javax.servlet} port of the MCP SDK's {@code
 * HttpServletStreamableServerTransportProvider} — the wire-protocol logic is identical; only the
 * servlet namespace changes (jakarta → javax) to match the Pax Web / Jersey 2.x container that
 * CyREST runs.
 */
@Path("/mcp")
public class McpJaxRsTransportProvider implements McpStreamableServerTransportProvider {

    private static final Logger logger = LoggerFactory.getLogger(McpJaxRsTransportProvider.class);

    public static final String MESSAGE_EVENT_TYPE = "message";
    public static final String UTF_8 = "UTF-8";
    public static final String APPLICATION_JSON = "application/json";
    public static final String TEXT_EVENT_STREAM = "text/event-stream";
    private static final String FAILED_TO_SEND_ERROR_RESPONSE = "Failed to send error response: {}";
    private static final String ACCEPT = "Accept";

    private final ObjectMapper objectMapper;
    private volatile McpStreamableServerSession.Factory sessionFactory;
    private final ConcurrentHashMap<String, McpStreamableServerSession> sessions =
            new ConcurrentHashMap<>();
    private volatile boolean isClosing = false;

    public McpJaxRsTransportProvider() {
        this.objectMapper = new ObjectMapper();
    }

    public McpJaxRsTransportProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isRunning() {
        return !isClosing;
    }

    // -------------------------------------------------------------------------
    // McpStreamableServerTransportProvider
    // -------------------------------------------------------------------------

    @Override
    public List<String> protocolVersions() {
        return List.of(ProtocolVersions.MCP_2024_11_05, ProtocolVersions.MCP_2025_03_26);
    }

    @Override
    public void setSessionFactory(McpStreamableServerSession.Factory sessionFactory) {
        this.sessionFactory = sessionFactory;
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
    // JAX-RS HTTP methods (inject javax.servlet types via @Context)
    // -------------------------------------------------------------------------

    @GET
    public void handleGet(
            @Context HttpServletRequest request, @Context HttpServletResponse response)
            throws IOException {
        if (this.isClosing) {
            response.sendError(
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Server is shutting down");
            return;
        }

        List<String> badRequestErrors = new ArrayList<>();
        String accept = request.getHeader(ACCEPT);
        if (accept == null || !accept.contains(TEXT_EVENT_STREAM)) {
            badRequestErrors.add("text/event-stream required in Accept header");
        }
        String sessionId = request.getHeader(HttpHeaders.MCP_SESSION_ID);
        if (sessionId == null || sessionId.isBlank()) {
            badRequestErrors.add("Session ID required in mcp-session-id header");
        }
        if (!badRequestErrors.isEmpty()) {
            responseError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST)
                            .message(String.join("; ", badRequestErrors))
                            .build());
            return;
        }

        McpStreamableServerSession session = this.sessions.get(sessionId);
        if (session == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        logger.debug("Handling GET request for session: {}", sessionId);

        try {
            response.setContentType(TEXT_EVENT_STREAM);
            response.setCharacterEncoding(UTF_8);
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");
            response.setHeader("Access-Control-Allow-Origin", "*");

            AsyncContext asyncContext = request.startAsync();
            asyncContext.setTimeout(0);

            McpJaxRsSessionTransport sessionTransport =
                    new McpJaxRsSessionTransport(sessionId, asyncContext, response.getWriter());

            String lastEventId = request.getHeader(HttpHeaders.LAST_EVENT_ID);
            if (lastEventId != null) {
                // Replay missed events for reconnecting client
                final McpTransportContext ctx = McpTransportContext.EMPTY;
                try {
                    session.replay(lastEventId)
                            .contextWrite(c -> c.put(McpTransportContext.KEY, ctx))
                            .toIterable()
                            .forEach(
                                    message -> {
                                        try {
                                            sessionTransport
                                                    .sendMessage(message)
                                                    .contextWrite(
                                                            c ->
                                                                    c.put(
                                                                            McpTransportContext.KEY,
                                                                            ctx))
                                                    .block();
                                        } catch (Exception e) {
                                            logger.error(
                                                    "Failed to replay message: {}", e.getMessage());
                                            asyncContext.complete();
                                        }
                                    });
                } catch (Exception e) {
                    logger.error("Failed to replay messages: {}", e.getMessage());
                    asyncContext.complete();
                }
            } else {
                // Open a new listening SSE stream
                McpStreamableServerSession.McpStreamableServerSessionStream listeningStream =
                        session.listeningStream(sessionTransport);

                asyncContext.addListener(
                        new AsyncListener() {
                            @Override
                            public void onComplete(AsyncEvent event) throws IOException {
                                logger.debug("SSE connection completed for session: {}", sessionId);
                                listeningStream.close();
                            }

                            @Override
                            public void onTimeout(AsyncEvent event) throws IOException {
                                logger.debug("SSE connection timed out for session: {}", sessionId);
                                listeningStream.close();
                            }

                            @Override
                            public void onError(AsyncEvent event) throws IOException {
                                logger.debug("SSE connection error for session: {}", sessionId);
                                listeningStream.close();
                            }

                            @Override
                            public void onStartAsync(AsyncEvent event) throws IOException {
                                // no-op
                            }
                        });
            }
        } catch (Exception e) {
            logger.error(
                    "Failed to handle GET request for session {}: {}", sessionId, e.getMessage());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    public void handlePost(
            @Context HttpServletRequest request, @Context HttpServletResponse response)
            throws IOException {
        if (this.isClosing) {
            response.sendError(
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Server is shutting down");
            return;
        }

        List<String> badRequestErrors = new ArrayList<>();
        String accept = request.getHeader(ACCEPT);
        if (accept == null || !accept.contains(TEXT_EVENT_STREAM)) {
            badRequestErrors.add("text/event-stream required in Accept header");
        }
        if (accept == null || !accept.contains(APPLICATION_JSON)) {
            badRequestErrors.add("application/json required in Accept header");
        }

        McpTransportContext transportContext = McpTransportContext.EMPTY;

        try {
            BufferedReader reader = request.getReader();
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }

            McpSchema.JSONRPCMessage message =
                    McpSchema.deserializeJsonRpcMessage(objectMapper, body.toString());

            if (message instanceof McpSchema.JSONRPCRequest
                    && ((McpSchema.JSONRPCRequest) message)
                            .method()
                            .equals(McpSchema.METHOD_INITIALIZE)) {
                McpSchema.JSONRPCRequest jsonrpcRequest = (McpSchema.JSONRPCRequest) message;
                // Initialization: validate headers then create a new session
                if (!badRequestErrors.isEmpty()) {
                    responseError(
                            response,
                            HttpServletResponse.SC_BAD_REQUEST,
                            McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST)
                                    .message(String.join("; ", badRequestErrors))
                                    .build());
                    return;
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
                    response.setContentType(APPLICATION_JSON);
                    response.setCharacterEncoding(UTF_8);
                    response.setHeader(HttpHeaders.MCP_SESSION_ID, init.session().getId());
                    response.setStatus(HttpServletResponse.SC_OK);
                    String jsonResponse =
                            objectMapper.writeValueAsString(
                                    new McpSchema.JSONRPCResponse(
                                            McpSchema.JSONRPC_VERSION,
                                            jsonrpcRequest.id(),
                                            initResult,
                                            null));
                    PrintWriter writer = response.getWriter();
                    writer.write(jsonResponse);
                    writer.flush();
                } catch (Exception e) {
                    logger.error("Failed to initialize session: {}", e.getMessage());
                    responseError(
                            response,
                            HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                            McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
                                    .message("Failed to initialize session: " + e.getMessage())
                                    .build());
                }
                return;
            }

            String sessionId = request.getHeader(HttpHeaders.MCP_SESSION_ID);
            if (sessionId == null || sessionId.isBlank()) {
                badRequestErrors.add("Session ID required in mcp-session-id header");
            }
            if (!badRequestErrors.isEmpty()) {
                responseError(
                        response,
                        HttpServletResponse.SC_BAD_REQUEST,
                        McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST)
                                .message(String.join("; ", badRequestErrors))
                                .build());
                return;
            }

            McpStreamableServerSession session = this.sessions.get(sessionId);
            if (session == null) {
                responseError(
                        response,
                        HttpServletResponse.SC_NOT_FOUND,
                        McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
                                .message("Session not found: " + sessionId)
                                .build());
                return;
            }

            final McpTransportContext tc = transportContext;
            if (message instanceof McpSchema.JSONRPCResponse) {
                McpSchema.JSONRPCResponse jsonrpcResponse = (McpSchema.JSONRPCResponse) message;
                session.accept(jsonrpcResponse)
                        .contextWrite(c -> c.put(McpTransportContext.KEY, tc))
                        .block();
                response.setStatus(HttpServletResponse.SC_ACCEPTED);
            } else if (message instanceof McpSchema.JSONRPCNotification) {
                McpSchema.JSONRPCNotification jsonrpcNotification =
                        (McpSchema.JSONRPCNotification) message;
                session.accept(jsonrpcNotification)
                        .contextWrite(c -> c.put(McpTransportContext.KEY, tc))
                        .block();
                response.setStatus(HttpServletResponse.SC_ACCEPTED);
            } else if (message instanceof McpSchema.JSONRPCRequest) {
                McpSchema.JSONRPCRequest jsonrpcRequest = (McpSchema.JSONRPCRequest) message;
                // Streaming response via SSE
                response.setContentType(TEXT_EVENT_STREAM);
                response.setCharacterEncoding(UTF_8);
                response.setHeader("Cache-Control", "no-cache");
                response.setHeader("Connection", "keep-alive");
                response.setHeader("Access-Control-Allow-Origin", "*");

                AsyncContext asyncContext = request.startAsync();
                asyncContext.setTimeout(0);

                McpJaxRsSessionTransport sessionTransport =
                        new McpJaxRsSessionTransport(sessionId, asyncContext, response.getWriter());
                try {
                    session.responseStream(jsonrpcRequest, sessionTransport)
                            .contextWrite(c -> c.put(McpTransportContext.KEY, tc))
                            .block();
                } catch (Exception e) {
                    logger.error("Failed to handle request stream: {}", e.getMessage());
                    asyncContext.complete();
                }
            } else {
                responseError(
                        response,
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST)
                                .message("Unknown message type")
                                .build());
            }
        } catch (IllegalArgumentException | IOException e) {
            logger.error("Failed to deserialize message: {}", e.getMessage());
            responseError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST)
                            .message("Invalid message format: " + e.getMessage())
                            .build());
        } catch (Exception e) {
            logger.error("Error handling message: {}", e.getMessage());
            try {
                responseError(
                        response,
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
                                .message("Error processing message: " + e.getMessage())
                                .build());
            } catch (IOException ex) {
                logger.error(FAILED_TO_SEND_ERROR_RESPONSE, ex.getMessage());
                response.sendError(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing message");
            }
        }
    }

    @DELETE
    public void handleDelete(
            @Context HttpServletRequest request, @Context HttpServletResponse response)
            throws IOException {
        if (this.isClosing) {
            response.sendError(
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Server is shutting down");
            return;
        }

        String sessionId = request.getHeader(HttpHeaders.MCP_SESSION_ID);
        if (sessionId == null) {
            responseError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    McpError.builder(McpSchema.ErrorCodes.INVALID_REQUEST)
                            .message("Session ID required in mcp-session-id header")
                            .build());
            return;
        }

        McpStreamableServerSession session = this.sessions.get(sessionId);
        if (session == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        McpTransportContext transportContext = McpTransportContext.EMPTY;
        try {
            session.delete()
                    .contextWrite(c -> c.put(McpTransportContext.KEY, transportContext))
                    .block();
            this.sessions.remove(sessionId);
            response.setStatus(HttpServletResponse.SC_OK);
        } catch (Exception e) {
            logger.error("Failed to delete session {}: {}", sessionId, e.getMessage());
            try {
                responseError(
                        response,
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
                                .message(e.getMessage())
                                .build());
            } catch (IOException ex) {
                logger.error(FAILED_TO_SEND_ERROR_RESPONSE, ex.getMessage());
                response.sendError(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error deleting session");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void responseError(HttpServletResponse response, int httpCode, McpError mcpError)
            throws IOException {
        response.setContentType(APPLICATION_JSON);
        response.setCharacterEncoding(UTF_8);
        response.setStatus(httpCode);
        String jsonError = objectMapper.writeValueAsString(mcpError);
        PrintWriter writer = response.getWriter();
        writer.write(jsonError);
        writer.flush();
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
    // Inner class: per-session SSE transport
    // -------------------------------------------------------------------------

    private class McpJaxRsSessionTransport implements McpStreamableServerTransport {

        private final String sessionId;
        private final AsyncContext asyncContext;
        private final PrintWriter writer;
        private volatile boolean closed = false;
        private final ReentrantLock lock = new ReentrantLock();

        McpJaxRsSessionTransport(String sessionId, AsyncContext asyncContext, PrintWriter writer) {
            this.sessionId = sessionId;
            this.asyncContext = asyncContext;
            this.writer = writer;
            logger.debug("Streamable session transport {} initialized with SSE writer", sessionId);
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
                            McpJaxRsTransportProvider.this.sendEvent(
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
                            McpJaxRsTransportProvider.this.sessions.remove(this.sessionId);
                            this.asyncContext.complete();
                        } finally {
                            lock.unlock();
                        }
                    });
        }

        @Override
        public <T> T unmarshalFrom(Object data, TypeReference<T> typeRef) {
            return objectMapper.convertValue(data, typeRef);
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
                this.asyncContext.complete();
                logger.debug("Successfully completed async context for session {}", sessionId);
            } catch (Exception e) {
                logger.warn(
                        "Failed to complete async context for session {}: {}",
                        sessionId,
                        e.getMessage());
            } finally {
                lock.unlock();
            }
        }
    }
}
