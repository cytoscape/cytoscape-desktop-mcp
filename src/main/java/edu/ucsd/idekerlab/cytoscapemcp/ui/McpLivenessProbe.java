package edu.ucsd.idekerlab.cytoscapemcp.ui;

import java.net.HttpURLConnection;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs a stateless liveness check against the MCP server's {@code GET /mcp/health} endpoint.
 *
 * <p>Returns {@code true} only if {@code GET http://localhost:{port}/mcp/health} responds with HTTP
 * 200. No session is created or torn down. All exceptions are swallowed and logged at DEBUG level
 * so the status-bar timer never throws.
 */
public class McpLivenessProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpLivenessProbe.class);

    private static final int TIMEOUT_MS = 3000;

    private final int port;

    public McpLivenessProbe(int port) {
        this.port = port;
    }

    /** Returns {@code true} if the MCP server's health endpoint responds with HTTP 200. */
    public boolean isAlive() {
        try {
            HttpURLConnection conn =
                    (HttpURLConnection)
                            new URL("http://localhost:" + port + "/mcp/health").openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            int status = conn.getResponseCode();
            conn.disconnect();
            return status == 200;
        } catch (Exception e) {
            LOGGER.warn("MCP liveness probe failed: {}", e.getMessage());
            return false;
        }
    }
}
