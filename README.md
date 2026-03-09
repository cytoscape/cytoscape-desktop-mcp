[gradle]: https://gradle.org/
[java]: https://www.oracle.com/java/index.html
[git]: https://git-scm.com/
[make]: https://www.gnu.org/software/make
[cytoscape]: https://cytoscape.org/
[mcp]: https://modelcontextprotocol.io/
[ndex]: https://www.ndexbio.org/

Cytoscape MCP Server
=======================================

An embedded [Model Context Protocol (MCP)][mcp] server for [Cytoscape Desktop][cytoscape], packaged as a Cytoscape App. AI clients such as Claude Desktop connect to Cytoscape over HTTP and invoke tools that control the desktop application directly — loading networks, setting active views, and more.

**NOTE:** This app is experimental. The interface and available tools are subject to change.

## Architecture
![Cytoscape MCP Desktop](docs/desktopmcp.png)

## How It Works

Once installed, the app publishes an MCP endpoint inside Cytoscape's existing CyREST HTTP server — AI clients connect to the MCP endpoint with Streamable HTTP transport and call MCP tools which drive activity on the Cytoscape desktop display. 

The app also adds two visual indicators to the Cytoscape Desktop UI:

### MCP toolbar button
a bold **MCP** button in the bottom-left status bar. The label is green when the MCP server is running and red when it is not. Clicking it opens the Agent Configuration dialog which displays the full MCP url and connection instructions for all supported agents.

### Task History entries
every MCP tool invocation is recorded in Cytoscape's Task History panel (**View > Show Task History**), so you can see exactly which tools an agent called and when.

```
AI Agent──► HTTP──► http://localhost:{rest.port}/mcp ──► Cytoscape Desktop
                                                               └── load network from NDEx or file
                                                               └── get loaded network views
                                                               └── set current network view
                                                               └── create network view
```

## Requirements

* [Cytoscape][cytoscape] 3.10 or above
* Internet connection (for loading networks from [NDEx][ndex])
* An MCP-compatible AI client that also supports the Streamable HTTP transport(not SSE which is [deprecated as of 02/2025](https://auth0.com/blog/mcp-streamable-http/)) (e.g. Claude Desktop)

## Try it! 

### Build from Source

Requirements:
* [Java][java] 17 with JDK
* [Git][git]
* [Make][make]

```bash
git clone https://github.com/idekerlab/cytoscape-mcp
cd cytoscape-mcp
make build
```

The JAR is produced at `build/libs/cytoscape-mcp-<VERSION>.jar`.

For a full list of build targets:
```bash
make help
```
### Cytoscape Desktop Installation

1. Get the mcp app jar:
    * Download the latest `cytoscape-mcp-<VERSION>.jar` from the [Releases](../../releases) page.
    * or [Build](#building-from-source) the jar 
2. Open Cytoscape Desktop.
3. Navigate to **Apps > App Manager > Install from File**.
4. Select the file path to the MCP App JAR and restart Cytoscape if prompted.

After startup, the MCP status can be viewed via the [MCP button](#mcp-toolbar-button) in the status bar.

### Connecting an Agent to Cytoscape Desktop MCP
See [docs/AgentConfiguration.md](docs/AgentConfiguration.md) for step-by-step setup instructions for Claude Desktop, Claude Code, GitHub Copilot (VS Code), GitHub Copilot CLI, and OpenAI Codex CLI.

### Cytoscape Desktop MCP Diagnostics
* Most agents will have a `/mcp` command or UI settings panel which will show status of connection to the MCP server and a list of tools currently published by this server, check to see if it is denoted as 'connected'.
* Check the MCP health endpoint  
  ```bash
  curl http://localhost:{rest.port}/mcp/health
  ```
  Replace `{rest.port}` with Cytoscape's CyREST port (shown in the Agent Configuration dialog). You should see `{"status":"ok","transport":"mcp-streamable-http"}`. A "connection refused" error means Cytoscape is not running or the port is wrong.
* Use external MCP introspection tools against the Desktop MCP server running at `http://localhost:{rest.port}/mcp` to validate or view current tools catalog - [modelcontextprotocol/inspector](https://github.com/modelcontextprotocol/inspector?tab=readme-ov-file#running-the-inspector)


## Cytoscape Desktop MCP Tool Catalog
The MCP server provides a human-readable catalog of every tool registered on the server formatted as Markdown with complete MCP Protocol JSON schema definitions for each tool's input and output. You can obtain the catalog through multiple options:
*  When the app is loaded in Desktop, at runtime the MCP server exposes `<CyRest Url>/mcp/manifest` endpoint which can be retrieved by browser or command line
  ```bash
  curl http://localhost:{rest.port}/mcp/manifest
  ```
* After any build locally, `make build` will generate the MCP manifest based on current code into a static file at `build/generated/manifest/MCPManifest.md` for same review.
* Static copy of the catalog is also stored in repo at [MCPManifest.md](./MCPManifest.md)

### Activate Cytoscape Desktop MCP tools from Agent prompts:
Invoking the tools requires some prompt engineering to provide key words or phrases which will activate the LLM to choose usage of a tool. Check out [MCPManifest.md](./MCPManifest.md) which contains 3 to 4 examples of Prompt snippets on each tool's description as reference of how to trigger LLM activation. 

* an example of a simple prompt which will lead the LLM to reason over the available tools as a whole and orchestrate their usage as building blocks into a sequence to reach the requested end result:
  ```
   > analyze a network in cytoscape desktop 
  ```


## Cytoscape Desktop MCP Configuration properties

Properties are editable at runtime via **Edit > Preferences > Properties > cytoscapemcp**:

| Property | Default | Description |
|----------|---------|-------------|
| `mcp.ndexbaseurl` | `https://www.ndexbio.org` | NDEx base URL (takes effect immediately) |

## Documentation

Full documentation is in the `docs/` directory:

- [Agent Configuration](docs/AgentConfiguration.md) — connecting Claude Desktop, Claude Code, GitHub Copilot, Codex CLI, and others
- [User Manual](docs/UserManual.md) — configuration reference and available tools
- [Tutorial](docs/Tutorial.md) — end-to-end walkthrough: install, connect, load a network
- [FAQ](docs/FAQ.md) — common questions and troubleshooting

## COPYRIGHT AND LICENSE

[Click here](LICENSE)

## Acknowledgements

* TODO: denote funding sources
