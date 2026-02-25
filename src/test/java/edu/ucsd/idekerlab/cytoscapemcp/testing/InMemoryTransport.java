package edu.ucsd.idekerlab.cytoscapemcp.testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Reusable test fixture that wires an MCP server with in-memory streams.
 *
 * <p>Uses {@link PipedInputStream}/{@link PipedOutputStream} to feed messages to the server,
 * and a {@link LineCountingOutputStream} to capture responses with deterministic
 * synchronization (no {@code Thread.sleep}).
 *
 * <p>Usage:
 * <pre>
 *   InMemoryTransport transport = new InMemoryTransport();
 *   transport.startServer("name", "1.0", List.of(myTool.toSpec()));
 *   transport.send(initRequest);
 *   transport.send(initializedNotification);
 *   transport.send(toolCallRequest);
 *   transport.await();        // blocks until 2 response lines written (init + tool call)
 *   String response = transport.getResponse();
 * </pre>
 */
public class InMemoryTransport {

    private static final long RESPONSE_TIMEOUT_MS = 10_000;

    private final PipedOutputStream feeder = new PipedOutputStream();
    private final PipedInputStream serverInput;
    private final LineCountingOutputStream serverOutput = new LineCountingOutputStream();
    private McpSyncServer server;

    public InMemoryTransport() throws IOException {
        serverInput = new PipedInputStream(feeder, 8192);
    }

    /**
     * Builds and starts the MCP server with the given tools pre-registered.
     * Tools are registered via the builder so no {@code tools/list_changed}
     * notification is emitted.
     *
     * <p>The server's reader thread is started by {@code build()}. Messages written
     * to the feeder are buffered in the pipe and read when the thread is ready —
     * no startup sleep needed.
     */
    public void startServer(String name, String version, List<SyncToolSpecification> tools) {
        StdioServerTransportProvider transport = new StdioServerTransportProvider(
                new ObjectMapper(), serverInput, serverOutput);

        McpServer.SyncSpecification<?> builder = McpServer.sync(transport)
                .serverInfo(name, version)
                .capabilities(ServerCapabilities.builder().tools(true).build());

        for (SyncToolSpecification spec : tools) {
            builder = builder.tools(spec);
        }

        server = builder.build();
    }

    /**
     * Sends a single JSON-RPC message to the server. Non-blocking — the message
     * is written to the pipe buffer and processed by the server's reader thread.
     */
    public void send(String jsonRpcLine) throws IOException {
        byte[] data = (jsonRpcLine + "\n").getBytes(StandardCharsets.UTF_8);
        feeder.write(data);
        feeder.flush();
    }

    /**
     * Blocks until the server has written the expected number of response lines.
     *
     * <p>In the MCP protocol, each JSON-RPC request produces one response line;
     * notifications produce none. The standard init + tool-call flow produces
     * 2 response lines (init response + tool call result).
     */
    public void await() throws InterruptedException {
        await(2);
    }

    /**
     * Blocks until the server has written at least {@code expectedResponses}
     * newline-terminated lines to its output stream.
     *
     * @throws RuntimeException if the timeout is reached before the expected count
     */
    public void await(int expectedResponses) throws InterruptedException {
        serverOutput.awaitLineCount(expectedResponses, RESPONSE_TIMEOUT_MS);
    }

    /**
     * Returns everything the server wrote as UTF-8.
     * Each JSON-RPC response is a separate line.
     */
    public String getResponse() {
        return serverOutput.toString(StandardCharsets.UTF_8);
    }

    /** Shuts down the MCP server and closes the input pipe. */
    public void close() {
        if (server != null) {
            try { server.close(); } catch (Exception ignored) {}
        }
        try { feeder.close(); } catch (Exception ignored) {}
    }

    /**
     * OutputStream that counts newline-terminated lines and supports blocking
     * until a target line count is reached via {@code wait/notify}.
     */
    private static class LineCountingOutputStream extends OutputStream {
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        private final Object lock = new Object();
        private int lineCount = 0;

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            if (b == '\n') {
                incrementLineCount();
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            for (int i = off; i < off + len; i++) {
                if (b[i] == '\n') {
                    incrementLineCount();
                }
            }
        }

        private void incrementLineCount() {
            synchronized (lock) {
                lineCount++;
                lock.notifyAll();
            }
        }

        void awaitLineCount(int expected, long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            synchronized (lock) {
                while (lineCount < expected) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        throw new RuntimeException(
                                "Timed out waiting for " + expected
                                + " response lines, got " + lineCount);
                    }
                    lock.wait(remaining);
                }
            }
        }

        String toString(Charset charset) {
            synchronized (lock) {
                return delegate.toString(charset);
            }
        }
    }
}
