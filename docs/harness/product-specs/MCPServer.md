# MCP Server Architecture

Architecture decision record for the MCP server embedded in the Cytoscape Desktop OSGi app.

## Why MCP is hosted on CyREST's existing HTTP server

- Avoids a second port — users and agents only need to know CyREST's port (default 1234)
- Leverages CyREST's Pax Web / Jetty infrastructure (already running, already configured)
- Simpler discovery: MCP endpoint at `http://localhost:{rest.port}/mcp`

## Transport: MCP 2025-03-26 Streamable HTTP only

The `/mcp` endpoint implements the **MCP 2025-03-26 Streamable HTTP transport** exclusively. The deprecated HTTP+SSE transport (which used separate `GET /sse` and `POST /messages` endpoints) is **not** supported.

Streamable HTTP uses a single `POST /mcp` endpoint:
- **Initialize** — returns `Content-Type: application/json` with the server's capabilities
- **Tool calls / notifications** — returns `Content-Type: text/event-stream`; each JSON-RPC message is written as an event line. This `text/event-stream` wire format within a single HTTP response is mandated by the MCP 2025-03-26 spec and is distinct from the deprecated SSE transport mechanism.
- **Session termination** — `DELETE /mcp` with `mcp-session-id` header

Clients must include `Accept: application/json, text/event-stream` on every `POST`.

## JAX-RS endpoint registration via publisher-5.3

`McpEndpoint` is a standard JAX-RS resource class annotated with `@Path("/mcp")`. It is registered as an OSGi service in `CyActivator`. publisher-5.3 (osgi-jax-rs-connector) discovers the `@Path` annotation on the service instance and hot-mounts it into CyREST's Jersey `RootApplication`.

**Critical constraint — no `@Context` injection:** `McpEndpoint` uses only `@HeaderParam` (String) and `InputStream` for all request data. It has zero `@Context` field or parameter injections. This is intentional:

When a JAX-RS singleton with `@Context HttpServletRequest` or `@Context HttpServletResponse` fields is registered, Jersey triggers HK2 proxy generation during `ServletContainer.init()`. In CyREST's publisher-5.3 environment this init runs on a background `ScheduledExecutorService` thread — if it throws a `ServletException`, the exception is silently swallowed by the thread's `UncaughtExceptionHandler` and the `isJerseyReady` flag never becomes `true`. The result is **all** CyREST endpoints returning 503 permanently.

Neither `diffusion` nor `cy-ndex-2` (the reference Cytoscape JAX-RS apps) use `@Context` injection. MCP streaming does not require it: `InputStream` carries the POST body, `@HeaderParam` carries `Accept` and `mcp-session-id`, and `StreamingOutput` handles the response stream.

## McpTransportProvider — why it's a port of the SDK transport

`McpTransportProvider` reimplements the same wire-protocol logic as the SDK's `HttpServletStreamableServerTransportProvider` using JAX-RS types instead of `jakarta.servlet`. Two reasons make the SDK class unusable here:

1. **Namespace mismatch.** The SDK transport extends `jakarta.servlet.http.HttpServlet` and uses `jakarta.servlet.AsyncContext`. CyREST runs on Jetty 9.4 / Pax Web 7 (Servlet 3.1, `javax.servlet` namespace). The `javax.servlet-api` bundle in Cytoscape's OSGi container is permanently in INSTALLED state — it never resolves, so `javax.servlet` is also unavailable to third-party bundles. Either namespace is a dead end.

2. **JAX-RS hand-off boundary.** By the time a request reaches `McpEndpoint`, it has already passed through Jersey's pipeline. The method parameters are a deserialized `InputStream` body and `@HeaderParam` strings — there is no `HttpServletRequest` or `AsyncContext` in scope. `McpEndpoint` cannot delegate to a class that requires them.

`McpTransportProvider` works around both constraints using only standard JAX-RS types: `InputStream` for the request body, `Response` for single JSON replies, and `StreamingOutput` (which runs on a Jetty worker thread and blocks until the MCP session completes) for streaming responses.

## sync vs async server choice

`McpServer.sync()` is used. Internally, `McpSyncServer` wraps `McpAsyncServer` — the `sync()` builder converts all sync tool/resource/prompt handlers into async ones by wrapping them in `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`. The actual server core is always async.

This is correct because:
- Tool handlers call Cytoscape APIs (Swing EDT, synchronous services like `CyNetworkManager`, `TaskManager`) which are blocking
- The `boundedElastic` offloading prevents any single blocking tool from starving the Jetty worker thread handling the HTTP request
- Using `async()` would require every tool handler to return `Mono<CallToolResult>` and manually manage scheduling — exactly what `sync()` does automatically

`async()` would only be justified if tool handlers were themselves non-blocking (e.g., calling async HTTP APIs returning `Mono`).

## SDK version pinning

v0.12.1 is pinned via `io.modelcontextprotocol.sdk:mcp-bom:0.12.1`. The latest releases are v1.0.0+.

Reasons to stay on v0.12.1:
- v1.0.0 restructures modules: `mcp` was split into `mcp-core`, `mcp-json-jackson2`, `mcp-json-jackson3`. BOM artifact name and coordinates change.
- v1.0.0 removes deprecated APIs (commit `4c1c3d8`).
- The `McpStreamableServerTransportProvider` interface is stable across versions (moved from `mcp/spec/` to `mcp-core/spec/` but API unchanged).

Upgrade to v1.0.0 is feasible as a separate task once the current implementation is validated.

## Request flow

```
HTTP POST /mcp
  -> McpEndpoint.handlePost(accept, sessionId, body)   [JAX-RS @Path("/mcp")]
    -> McpTransportProvider.handlePost(accept, sessionId, body)
      -> parses JSON-RPC, manages sessions
      -> sessionFactory.create(transport)               [set by McpServer.sync().build()]
        -> McpSyncServer -> McpAsyncServer
          -> tool handler (e.g. LoadNetworkViewTool)
            -> runs on Schedulers.boundedElastic()
          -> result written back via StreamingOutput / Response
```

## Key classes

| Class | Role |
|-------|------|
| `McpEndpoint` | JAX-RS `@Path("/mcp")` resource; `@POST`/`@DELETE`; zero `@Context` |
| `McpTransportProvider` | MCP Streamable HTTP wire protocol; uses JAX-RS `Response`/`StreamingOutput`/`InputStream` |
| `CyActivator` | OSGi activator; wires transport → server → tools; registers `McpEndpoint` as OSGi service |
