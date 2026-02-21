package edu.ucsd.idekerlab.cytoscapemcp.testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Reusable test fixture that wires an MCP server with in-memory streams.
 *
 * <p>Uses {@link PipedInputStream}/{@link PipedOutputStream} to feed messages to the server
 * one at a time with proper pacing, and a {@link ByteArrayOutputStream} to capture responses.
 *
 * <p>Usage:
 * <pre>
 *   InMemoryTransport transport = new InMemoryTransport();
 *   transport.startServer("name", "1.0", List.of(myTool.toSpec()));
 *   transport.send(initRequest);
 *   transport.send(initializedNotification);
 *   transport.send(toolCallRequest);
 *   transport.await();
 *   String response = transport.getResponse();
 * </pre>
 */
public class InMemoryTransport {

    private static final long SETTLE_MS = 200;

    private final PipedOutputStream feeder = new PipedOutputStream();
    private final PipedInputStream serverInput;
    private final ByteArrayOutputStream serverOutput = new ByteArrayOutputStream();
    private McpSyncServer server;

    public InMemoryTransport() throws IOException {
        serverInput = new PipedInputStream(feeder, 8192);
    }

    /**
     * Builds and starts the MCP server with the given tools pre-registered.
     * Tools are registered via the builder so no {@code tools/list_changed}
     * notification is emitted.
     */
    public void startServer(String name, String version, List<SyncToolSpecification> tools)
            throws InterruptedException {
        StdioServerTransportProvider transport = new StdioServerTransportProvider(
                new ObjectMapper(), serverInput, serverOutput);

        McpServer.SyncSpecification<?> builder = McpServer.sync(transport)
                .serverInfo(name, version)
                .capabilities(ServerCapabilities.builder().tools(true).build());

        for (SyncToolSpecification spec : tools) {
            builder = builder.tools(spec);
        }

        server = builder.build();

        // Give the server time to start its reader thread
        Thread.sleep(SETTLE_MS);
    }

    /**
     * Sends a single JSON-RPC message to the server and pauses to let
     * the server process it before the next message is sent.
     */
    public void send(String jsonRpcLine) throws IOException, InterruptedException {
        byte[] data = (jsonRpcLine + "\n").getBytes(StandardCharsets.UTF_8);
        feeder.write(data);
        feeder.flush();
        Thread.sleep(SETTLE_MS);
    }

    /**
     * Signals EOF to the server and waits for outbound processing to drain.
     */
    public void await() throws IOException, InterruptedException {
        feeder.close();
        Thread.sleep(SETTLE_MS);
    }

    /**
     * Returns everything the server wrote as UTF-8.
     * Each JSON-RPC response is a separate line.
     */
    public String getResponse() {
        return serverOutput.toString(StandardCharsets.UTF_8);
    }

    /** Shuts down the MCP server. */
    public void close() {
        if (server != null) {
            try { server.close(); } catch (Exception ignored) {}
        }
        try { feeder.close(); } catch (Exception ignored) {}
    }
}
