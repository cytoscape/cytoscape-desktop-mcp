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

The SDK version supports last 3 mcp protocol releases with 2025-11-25 being the top most.

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

Feature behaviour for tools are specified in:

```
docs/harness/product-specs/
```

## MCP Meta Descriptions 
General rule of thumb for all mcp tool development is to remember that the tool through it's input/output and error/success responses and meta documentation needs to provide all info possible so that llm's can reason proprely to compose dynamic sequence of the desktop mcp tools as building blocks to dynamically complete larger flows that a user asks for.  But to this end each tool is encapsulated unto itself, it does not depend on or ever directly reference any other tool through meta description or code.

Research Summary: Consensus on 
Tool Meta Best Practices                                                                                                                                                                │

│                                                                                                                                                                                                            │
│ Sources: https://modelcontextprotocol.io/docs/concepts/tools, https://arxiv.org/html/2602.14878v1, https://github.com/modelcontextprotocol/modelcontextprotocol/issues/1382,                               │
│ https://modelcontextprotocol.info/docs/tutorials/writing-effective-tools/, https://www.arsturn.com/blog/maximizing-your-mcp-experience-tips-for-effective-tool-descriptions                                │
│                                                                                                                                                                                                            │
│ Guidelines                                                                                                                                                                                                 │
│                                                                                                                                                                                                            │
│ ┌─────┬──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┬──────────────┬───────────────────┐  │
│ │  #  │                                                                          Guideline                                                                           │  Applies to  │      Source       │  │
│ ├─────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────────┼───────────────────┤  │
│ │ G1  │ Lead with an imperative verb — one concise sentence for what the tool does                                                                                   │ Tool         │ MCP Spec,         │  │
│ │     │                                                                                                                                                              │ description  │ SEP-1382          │  │
│ ├─────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────────┼───────────────────┤  │
│ │ G2  │ High-level purpose only in tool description — avoid parameter names, return-schema fields, implementation internals                                          │ Tool         │ SEP-1382          │  │
│ │     │                                                                                                                                                              │ description  │                   │  │
│ ├─────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────────┼───────────────────┤  │
│ │ G3  │ Include activation criteria — "when to use" hint for tool selection                                                                                          │ Tool         │ arxiv, Arsturn    │  │
│ │     │                                                                                                                                                              │ description  │                   │  │
│ ├─────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────────┼───────────────────┤  │
│ │ G4  │ State key side-effects — read-only vs. mutating, what gets created/changed                                                                                   │ Tool         │ arxiv             │  │
│ │     │                                                                                                                                                              │ description  │                   │  │
│ ├─────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────────┼───────────────────┤  │
│ │ G5  │ 2–3 sentences for tool description                                                                                                                           │ Tool         │ arxiv, SEP-1382   │  │
│ │     │                                                                                                                                                              │ description  │                   │  │
│ ├─────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────────┼───────────────────┤  │
│ │ G6  │ Consistent voice — imperative-declarative across all tools                                                                                                   │ Tool         │ Arsturn           │  │
│ │     │                                                                                                                                                              │ description  │                   │  │
│ ├─────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────────┼───────────────────┤  │
│ │ G7  │ No parameter names in tool description — those belong in schema descriptions                                                                                 │ Tool         │ SEP-1382          │  │
│ │     │                                                                                                                                                              │ description  │                   │  │
│ ├─────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────────┼───────────────────┤  │
│ │ P1  │ Every output field needs a @JsonPropertyDescription — LLMs use these to interpret results and reason about next steps                                        │ Output       │ SEP-1382, Writing │  │
│ │     │                                                                                                                                                              │ params       │  Effective Tools  │  │
│ ├─────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────────┼───────────────────┤  │
│ │ P2  │ Conditional dependencies must be explicit — "Required when X. Ignored when Y." pattern helps LLMs reason about which params to populate                      │ Input params │ Writing Effective │  │
│ │     │                                                                                                                                                              │              │  Tools            │  │
│ ├─────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────────┼───────────────────┤  │
│ │ P3  │ Consistent Required/Optional prefix — start each param description with "Required." or "Optional." for quick LLM scanning                                    │ Input params │ Arsturn           │  │
│ ├─────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────────┼───────────────────┤  │
│ │ P4  │ Avoid prompt-engineering in schema descriptions — descriptions like "this is the most important parameter" are subjective directives, not documentation      │ Input params │ SEP-1382          │  │
│ ├─────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────────┼───────────────────┤  │
│ │ P5  │ Fix typos — LLMs may misinterpret misspelled terms                                                                                                           │ All          │ General           │  │
│ ├─────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────────┼───────────────────┤  │
│ │     │ Conditionally-required params use "Optional." prefix — JSON Schema required only supports unconditional requirements. Params that are required only under    │              │                   │  │
│ │ P6  │ certain conditions (e.g. file_path required when source='network-file') must NOT be in .required() (which would reject all calls missing them). Instead, use │ Input params │ JSON Schema spec  │  │
│ │     │  description prefix "Optional." followed by the conditional: "Required when source='tabular-file'." This is the correct pattern — the .required() list and   │              │ + MCP convention  │  │
│ │     │ descriptions must agree.                                                                                                                                     │              │                   │  │
│ ├─────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──────────────┼───────────────────┤  │
│ │ P7  │ Description prefix must match .required() list — if a param is in .required(), its description must start with "Required." If not in .required(), it must    │ Input params │ Consistency audit │  │
│ │     │ start with "Optional." even if conditionally required.                                                                                                       │              │                   │  │
│ └─────┴──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┴──────────────┴───────────────────┘  │
│                                              


