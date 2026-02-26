# Agent Instructions for cytoscape-mcp

## Project Overview
Cytoscape Desktop app (OSGi bundle) that embeds an MCP server inside Cytoscape. See **[README.md](README.md)** and **[docs/](docs/)** for usage, installation, and agent connection instructions.

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
├── ui/
│   ├── McpStatusPanel.java            # MCP toolbar button (extends JButton)
│   └── McpConfigDialog.java           # Agent configuration dialog (renders AgentConfiguration.md)
└── tools/
    └── LoadNetworkViewTool.java       # MCP tool: loads a network from NDEx and sets it as current view

src/main/resources/
├── cytoscapemcp.props                 # App properties (mcp.http_port, mcp.ndexbaseurl)
└── docs/AgentConfiguration.md        # Embedded — rendered inside McpConfigDialog

docs/
├── AgentConfiguration.md             # Source for agent connection instructions (also embedded in JAR)
├── UserManual.md                      # Configuration reference and available tools
├── FAQ.md                             # Frequently asked questions
└── Tutorial.md                        # End-to-end tutorial: install, connect, load a network
```

**Important:** Keep `docs/` up to date whenever code or functionality changes (new properties, new tools, transport changes). `docs/AgentConfiguration.md` is also copied into `src/main/resources/docs/` so it can be rendered inside the app at runtime — keep both in sync.

## MCP Toolbar Button — Positioning

`McpStatusPanel` extends `JButton` directly (not `JPanel`) to match the natural sizing of the other status bar buttons.

Cytoscape exposes `CySwingApplication.getStatusToolBar()` which returns the inner `JToolBar` at index 2 of the status bar's parent `JPanel`. That parent uses a `GroupLayout` with four children: `JobStatusBar`, `TaskStatusBar`, the `JToolBar`, and `MemStatusPanel`.

`CyActivator.injectIntoStatusBar()` rebuilds the parent's `GroupLayout` to insert the MCP button at position 0 (leftmost). The critical constraint is that `TaskStatusBar` must keep its `Short.MAX_VALUE` maximum width — it contains both the "Show Tasks" button and the task-title label internally, and constraining it to `PREFERRED_SIZE` crushes the title label (regression). The rebuilt layout is:

```
[MCP (pref)] [JobStatusBar (pref)] [TaskStatusBar (expands, Short.MAX_VALUE)] [JToolBar (pref)] [MemStatusPanel (pref)]
```

Do **not** call `toolbar.add(mcpPanel, 0)` — this places the button inside the expanding `JToolBar` region, which lands it far right. The parent `GroupLayout` must be rebuilt directly.

## Key Patterns

### OSGi Lifecycle
`CyActivator` extends `AbstractCyActivator`. Jetty and the MCP server are started inside `initializeApp()`, which is called after `AppsFinishedStartingEvent` to ensure all Cytoscape services are registered before being looked up. `shutDown()` stops both servers. A JVM shutdown hook registered in `start()` provides SIGINT/SIGTERM safety in case the OSGi shutdown is bypassed.

### MCP Server Architecture
- **Transport:** `HttpServletStreamableServerTransportProvider` from the MCP SDK core (`io.modelcontextprotocol.sdk:mcp:0.12.1`) — no Spring required
- **Servlet container:** Jetty 12.0.x (`jetty-server` + `jetty-ee10-servlet`) hosts the transport servlet
- **MCP endpoint:** `POST/GET /mcp` — unified endpoint for all client communication
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
`LoadNetworkViewTool` loads networks from NDEx using the CX-specific `InputStreamTaskFactory` (OSGi ID `cytoscapeCxNetworkReaderFactory`, from `io-api`) and `SynchronousTaskManager` to block until the read completes. The NDEx URL is `{mcp.ndexbaseurl}/v2/network/{uuid}` (no format suffix — Cytoscape auto-detects CX or CX2).

**Important:** Do NOT use `CyNetworkReaderManager.getReader()` for CX streams — it selects the wrong reader (SIF/table parser instead of CX parser). Instead, use `InputStreamTaskFactory` obtained via `getService(bundleContext, InputStreamTaskFactory.class, "(id=cytoscapeCxNetworkReaderFactory)")`. This is the same approach used by CyNDEx-2 (`CxTaskFactoryManager`).

The tool downloads the CX stream directly from NDEx via `URL.openStream()`, then uses `cxReaderFactory.createTaskIterator(cxStream, null)` to get the CX reader. The reader is executed via `SynchronousTaskManager` (which provides a proper `TaskMonitor` with UI progress). After the reader finishes, networks are manually registered via `CyNetworkManager.addNetwork()` (which creates a new collection), views are built via `CyNetworkReader.buildCyNetworkView()` and registered via `CyNetworkViewManager.addNetworkView()`. The root network (collection) name is set to match the loaded sub-network name via `CySubNetwork.getRootNetwork()`, then the network is activated as current via `CyApplicationManager`.

**Tool use examples** are embedded in the tool description string (not the MCP `Tool` schema, which has no `inputExamples` field) to improve LLM accuracy when invoking the tool.

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
