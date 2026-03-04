package edu.ucsd.idekerlab.cytoscapemcp;

import java.io.IOException;
import java.io.InputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JAX-RS resource that mounts MCP at {@code /mcp} inside CyREST's Jersey/Pax-Web container.
 *
 * <p>Registered as an OSGi service in {@link CyActivator}. The publisher-5.3 connector discovers
 * the {@code @Path("/mcp")} annotation and adds this resource to Jersey.
 *
 * <p><strong>No {@code @Context} injection</strong> — all request data comes in as
 * {@code @HeaderParam} strings and {@code InputStream}. This avoids the HK2 proxy-generation
 * failure that caused Jersey's {@code ServletContainer.init()} to swallow an exception and leave
 * {@code isJerseyReady = false} permanently (503 for ALL CyREST endpoints).
 *
 * <p>All protocol logic is delegated to {@link McpTransportProvider}.
 */
@Path("/mcp")
public class McpEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(McpEndpoint.class);

    private final McpTransportProvider transportProvider;

    public McpEndpoint(McpTransportProvider transportProvider) {
        this.transportProvider = transportProvider;
        logger.info("McpEndpoint created (JAX-RS resource registered at /mcp)");
    }

    /**
     * Handles MCP POST requests (initialize or streaming tool calls).
     *
     * <p>The {@code Accept} header must include both {@code application/json} and {@code
     * text/event-stream}. For initialize, returns a plain JSON response. For subsequent tool calls,
     * returns a {@code text/event-stream} {@link javax.ws.rs.core.StreamingOutput} that blocks the
     * Jetty worker thread while sending streaming events.
     */
    @POST
    @Consumes(MediaType.WILDCARD)
    @Produces({MediaType.APPLICATION_JSON, "text/event-stream"})
    public Response handlePost(
            @HeaderParam("accept") String accept,
            @HeaderParam("mcp-session-id") String sessionId,
            InputStream body) {
        logger.debug("McpEndpoint.handlePost — accept={} sessionId={}", accept, sessionId);
        return transportProvider.handlePost(accept, sessionId, body);
    }

    /** Handles MCP DELETE requests — terminates the named session. */
    @DELETE
    public Response handleDelete(@HeaderParam("mcp-session-id") String sessionId) {
        logger.debug("McpEndpoint.handleDelete — sessionId={}", sessionId);
        return transportProvider.handleDelete(sessionId);
    }

    /**
     * Stateless health check — no session required.
     *
     * <p>Returns {@code 200 {"status":"ok","transport":"mcp-streamable-http"}} while the server is
     * running, or {@code 503} while it is shutting down.
     */
    @GET
    @Path("health")
    @Produces(MediaType.APPLICATION_JSON)
    public Response handleHealth() {
        return transportProvider.handleHealth();
    }

    /**
     * Returns the pre-generated API manifest as Markdown.
     *
     * <p>The file {@code MCPManifest.md} is generated during the Gradle build by {@link
     * MCPManifest#main} and bundled as a classpath resource at {@code /MCPManifest.md}. This
     * endpoint serves it as-is with content-type {@code text/markdown}.
     */
    @GET
    @Path("manifest")
    @Produces("text/markdown")
    public Response handleManifest() {
        try (InputStream is = McpEndpoint.class.getResourceAsStream("/MCPManifest.md")) {
            if (is == null) {
                logger.warn("MCPManifest.md not found on classpath");
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("MCPManifest.md not found on classpath")
                        .build();
            }
            byte[] bytes = is.readAllBytes();
            return Response.ok(bytes).type("text/markdown").build();
        } catch (IOException e) {
            logger.error("Failed to read MCPManifest.md", e);
            return Response.serverError()
                    .entity("Failed to read manifest: " + e.getMessage())
                    .build();
        }
    }
}
