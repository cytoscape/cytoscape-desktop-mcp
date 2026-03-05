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
- **Health check** — `GET /mcp/health`; stateless, no session required (see below)

Clients must include `Accept: application/json, text/event-stream` on every `POST`.

## Health endpoint

`GET /mcp/health` is a stateless endpoint that requires no `mcp-session-id` and creates no session state. It returns:

- `200 {"status":"ok","transport":"mcp-streamable-http"}` — server is running
- `503` — server is in the process of shutting down

This is the endpoint used by `McpLivenessProbe` to drive the green/red toolbar button in the Cytoscape status bar. Using a dedicated health endpoint avoids the session churn that would result from polling via a real `initialize` / `tools/list` / `DELETE` cycle every 5 seconds.

`GET /mcp/manifest` returns a complete Markdown catalog of every tool, prompt, resource, and resource template registered on the server, including full JSON schema definitions for each artifact's input and output. The file is generated at build time by `MCPManifest.main()` (a `generateManifest` Gradle task that runs before `jar`) and bundled into the application JAR as the classpath resource `/MCPManifest.md`. After running `make build` the generated file is also readable locally at `build/generated/manifest/MCPManifest.md`.

## JAX-RS endpoint registration via publisher-5.3

`McpEndpoint` is a standard JAX-RS resource class annotated with `@Path("/mcp")`. It is registered as an OSGi service in `CyActivator`. publisher-5.3 (osgi-jax-rs-connector) discovers the `@Path` annotation on the service instance and hot-mounts it into CyREST's Jersey `RootApplication`.

**Critical constraint — no `@Context` injection:** `McpEndpoint` uses only `@HeaderParam` (String) and `InputStream` for all request data. It has zero `@Context` field or parameter injections. This is intentional:

When a JAX-RS singleton with `@Context HttpServletRequest` or `@Context HttpServletResponse` fields is registered, Jersey triggers HK2 proxy generation during `ServletContainer.init()`. In CyREST's publisher-5.3 environment this init runs on a background `ScheduledExecutorService` thread — if it throws a `ServletException`, the exception is silently swallowed by the thread's `UncaughtExceptionHandler` and the `isJerseyReady` flag never becomes `true`. The result is **all** CyREST endpoints returning 503 permanently.

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

`io.modelcontextprotocol.sdk:mcp-bom:1.0.0` is used, with the two required split modules:

```
io.modelcontextprotocol.sdk:mcp-core
io.modelcontextprotocol.sdk:mcp-json-jackson2
```

`mcp-json-jackson3` is not used (Cytoscape ships Jackson 2.x).

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

## Tools package

All MCP tools live in:

```
src/main/java/edu/ucsd/idekerlab/cytoscapemcp/tools/
```

Each class in this package implements one MCP tool. Tools are registered individually in `CyActivator` via `mcpServer.addTool(tool.toSpec())`. Adding a new tool means adding a single class to this package and one `addTool` call in `CyActivator` — no other wiring is required.

## Product specs

Feature behaviour and agent conversation flows are specified in:

```
docs/harness/product-specs/
```

Specs define the canonical `Say:` / `Ask:` / `Capture:` / `Call tool:` directives that drive agent behaviour. Tool schemas in Section 4 of each spec are the source of truth for the corresponding tool implementations in the `tools` package.

## Guideline prompt

`GuidelinePrompt` (in `prompts/`) is registered as the `cytoscape-guidelines` MCP prompt. It is fetched by the agent during session initialisation via `prompts/list` + `prompts/get` and injected into the agent's context before any tool calls are made.

### Why a cross-cutting prompt rather than per-tool descriptions

The guidelines express **server-wide behavioural policy** — how the agent should handle any failure, regardless of which tool was called. Embedding this in individual tool `description` fields would:
- Repeat the same ~300-character policy block in every tool schema
- Require updating every tool if the policy changes
- Bloat the model's tool-selection context with irrelevant error-handling prose

A named server prompt is fetched once and applies globally, which is the correct MCP idiom for cross-cutting rules.

### Three-rule error handling model

The prompt defines three mutually exclusive rules the agent applies on any tool failure:

| Rule | Trigger | Agent action |
|------|---------|-------------|
| **RULE 1** — Connectivity failure | Connection refused, host unreachable, timeout, HTTP 503, socket error | Suppress raw error; do not retry; show fixed user message: _"Please make sure your Cytoscape desktop is running..."_ |
| **RULE 2** — Formatted application error | Structured error body with business logic feedback | Follow embedded next-step instructions if present; otherwise display the error message verbatim |
| **RULE 3** — Unexpected server error | Unformatted HTTP 4xx/5xx, Java stack trace | Suppress raw error; show fixed user message directing user to check the MCP status toolbar |

### Why suppress raw errors for Rules 1 and 3

Connectivity errors and stack traces are meaningless to a non-technical user. The fixed messages are actionable: Rule 1 tells the user exactly what to do; Rule 3 directs them to the visual status indicator that shows whether the server is operational.

### Client support caveat

The MCP spec does not require clients to call `prompts/list` / `prompts/get`. Claude Desktop and Cursor both do. A minimal or custom MCP client that skips prompts will not receive these guidelines. For such clients, the fallback is the raw tool error text, which is harmless but less user-friendly.

## Key classes

| Class | Role |
|-------|------|
| `CyActivator` | OSGi activator — wires transport → server → tools; registers `McpEndpoint` as OSGi service |
| `McpEndpoint` | JAX-RS `@Path("/mcp")` resource; `@POST`, `@DELETE`, `@GET /health`; zero `@Context` |
| `McpTransportProvider` | MCP Streamable HTTP wire protocol — session lifecycle, JAX-RS `Response`/`StreamingOutput`/`InputStream` |
| `McpStatusPanel` | Swing toolbar button — polls `McpLivenessProbe` every 5 s, shows green/red server status |
| `McpLivenessProbe` | Stateless health checker — single `GET /mcp/health`, no session created or torn down |
| `GuidelinePrompt` | MCP `cytoscape-guidelines` prompt — cross-cutting error-handling rules injected into agent context at session start |
