# Agent Instructions for cytoscape-mcp

## Project Overview
Cytoscape Desktop app (OSGi bundle) that embeds an MCP (Model Context Protocol) server inside Cytoscape. AI clients such as Claude Desktop connect to the server over HTTP/SSE and invoke tools that control Cytoscape directly — loading networks, setting views, and querying the active session. Built with Java 11, Gradle, packaged as an OSGi bundle with embedded dependencies (MCP SDK, Jetty, Jackson).

## Build Commands
Use the Makefile targets — they wrap Gradle:

- `make build` — clean + compile + produce OSGi bundle JAR (`build/libs/cytoscape-mcp-<VERSION>.jar`)
- `make test` — clean + compile + run tests
- `make install` — publish JAR to local Maven repository

No formatter/linter is currently configured. The `-Xlint:deprecation` flag is active on all compile tasks to catch API deprecations early.

## Project Structure
```
src/main/java/edu/ucsd/idekerlab/cytoscapemcp/
├── CyActivator.java                   # OSGi bundle activator — starts/stops Jetty + MCP server
└── tools/
    └── LoadNetworkViewTool.java       # MCP tool: loads a network from NDEx and sets it as current view

src/main/resources/
└── cytoscapemcp.props                 # App properties (mcp.http_port, mcp.ndexbaseurl)

docs/
├── UserManual.md                      # Configuration reference and AI client connection guide
├── FAQ.md                             # Frequently asked questions
└── Tutorial.md                        # End-to-end tutorial: install, connect Claude Desktop, load network
```

**Important:** Keep `docs/Tutorial.md`, `docs/FAQ.md`, and `docs/UserManual.md` up to date whenever code or functionality changes (new properties, new tools, transport changes).

## Key Patterns

### OSGi Lifecycle
`CyActivator` extends `AbstractCyActivator`. Jetty and the MCP server are started inside `initializeApp()`, which is called after `AppsFinishedStartingEvent` to ensure all Cytoscape services are registered before being looked up. `shutDown()` stops both servers. A JVM shutdown hook registered in `start()` provides SIGINT/SIGTERM safety in case the OSGi shutdown is bypassed.

### MCP Server Architecture
- **Transport:** `HttpServletSseServerTransportProvider` from the MCP SDK core (`io.modelcontextprotocol.sdk:mcp:0.12.1`) — no Spring required
- **Servlet container:** Jetty 12.0.x (`jetty-server` + `jetty-ee10-servlet`) hosts the transport servlet
- **SSE endpoint:** `GET /mcp` — AI clients connect here
- **Message endpoint:** `POST /mcp/message` — tool call requests arrive here
- **Server type:** `McpSyncServer` (synchronous) — tool calls block until complete
- **Bundle version** is read from `bundleContext.getBundle().getVersion()` at startup and passed to `McpServer.sync().serverInfo()`

### App Properties (CyProperty)
- `PropsReader` inner class in `CyActivator` extends `AbstractConfigDirPropsReader` with `SavePolicy.CONFIG_DIR`
- Defaults in `src/main/resources/cytoscapemcp.props`
- Registered with `cyPropertyName=cytoscapemcp.props` — appears under "cytoscapemcp" in Edit > Preferences > Properties
- Pass `CyProperty<Properties>` references to tools (not raw values) so Preferences edits take effect without restart

**App Properties (cytoscapemcp group):**

| Key | Default | Description |
|-----|---------|-------------|
| `mcp.http_port` | `9998` | TCP port Jetty listens on (requires restart to change) |
| `mcp.ndexbaseurl` | `https://www.ndexbio.org` | NDEx server base URL (read at tool-call time, no restart needed) |

### Adding New MCP Tools
1. Create a class in `tools/` following the pattern in `LoadNetworkViewTool.java`
2. Expose a `toSpec()` method returning `McpServerFeatures.SyncToolSpecification` built via `SyncToolSpecification.builder().tool(...).callHandler(...).build()`
3. Define the tool with `Tool.builder().name().description().inputSchema(new JsonSchema(...)).build()`
4. Handler signature: `CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request)` — get args via `request.arguments()`
5. Register in `CyActivator.startMcpServer()` via `mcpServer.addTool(newTool.toSpec())`

### NDEx Network Loading
`LoadNetworkViewTool` loads networks from NDEx using `LoadNetworkURLTaskFactory` (obtained from the OSGi service registry) and `SynchronousTaskManager` to block until the load completes. The NDEx CX2 URL is `{mcp.ndexbaseurl}/v2/network/{uuid}/cx2`. After loading, the new network is identified by diffing the `CyNetworkManager.getNetworkSet()` before and after, then set as current via `CyApplicationManager`.

### Testing
- JUnit 4 + Mockito 4 — mock all Cytoscape services at the service boundary
- No `xvfb-run` required (no AWT/Desktop usage in this app)

## Dependencies
- **Cytoscape APIs 3.10.0** — all `compileOnly` (provided by OSGi runtime)
- **MCP SDK 0.12.1** (`io.modelcontextprotocol.sdk:mcp`) — embedded in bundle JAR
- **Jetty 12.0.18** (`jetty-server`, `jetty-ee10-servlet`) — embedded in bundle JAR
- **Jackson 2.17.2** — embedded in bundle JAR
- Non-provided deps are physically unpacked into the bundle JAR via the `embed` Gradle configuration

## CI
GitHub Actions (`.github/workflows/ci.yml`) runs `make test` on every push and PR to main using JDK 17 + Temurin + Gradle cache. No `xvfb-run` needed.
