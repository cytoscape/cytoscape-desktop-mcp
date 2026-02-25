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

## How It Works

Once installed, the app starts a Jetty HTTP server inside Cytoscape on startup. AI clients connect to the HTTP endpoint and call MCP tools that interact with Cytoscape's Java API. The server runs entirely within the Cytoscape process — no separate server process or sidecar is needed.

```
Claude Desktop ──HTTP──► http://localhost:9998/mcp ──► Cytoscape Desktop
                                                           └── load network from NDEx
                                                           └── set current network view
                                                           └── (more tools coming)
```

## Requirements

* [Cytoscape][cytoscape] 3.10 or above
* Internet connection (for loading networks from [NDEx][ndex])
* An MCP-compatible AI client (e.g. Claude Desktop)

## Installation

1. Download the latest `cytoscape-mcp-<VERSION>.jar` from the [Releases](../../releases) page.
2. Open Cytoscape Desktop.
3. Navigate to **Apps > App Manager > Install from File**.
4. Select the downloaded JAR and restart Cytoscape if prompted.

After startup, the MCP server endpoint:
```
Cytoscape MCP Server started on port 9998
MCP endpoint:     http://localhost:9998/mcp
```

## Connecting to an Agent

Only local/desktop agents are supported. The Cytoscape MCP server runs on `localhost` inside the Cytoscape process — the AI agent must be on the same machine. Multiple agents can connect simultaneously, but be aware that Cytoscape Desktop is a single-user application — concurrent agents may issue conflicting commands that affect shared view state.

If your agent is not listed below, check its documentation for how to configure an HTTP MCP server at `http://localhost:9998/mcp`.

### Claude Desktop

Add to your MCP configuration (`~/Library/Application Support/Claude/claude_desktop_config.json` on macOS):

```json
{
  "mcpServers": {
    "cytoscape": {
      "url": "http://localhost:9998/mcp"
    }
  }
}
```

Restart Claude Desktop to load the config.

### Claude Code

Add via the CLI:

```bash
claude mcp add --transport http cytoscape-mcp http://localhost:9998/mcp
```

Or add a `.mcp.json` file to your project root:

```json
{
  "mcpServers": {
    "cytoscape-mcp": {
      "type": "http",
      "url": "http://localhost:9998/mcp"
    }
  }
}
```

### GitHub Copilot

Add to `.vscode/mcp.json` in your workspace (note: VS Code uses `"servers"`, not `"mcpServers"`):

```json
{
  "servers": {
    "cytoscape-mcp": {
      "type": "http",
      "url": "http://localhost:9998/mcp"
    }
  }
}
```

### OpenAI Codex CLI

Add to `~/.codex/config.toml` (or `.codex/config.toml` in your project root):

```toml
[mcp_servers.cytoscape-mcp]
url = "http://localhost:9998/mcp"
```

## Available MCP Tools

| Tool | Description |
|------|-------------|
| `load_cytoscape_network_view` | Loads a network from [NDEx][ndex] by UUID and sets it as the current view in Cytoscape |


**Example prompt** — after connecting your agent, try this to load the Yeast ergosterol network:

> Load the NDEx network 63836e7b-ca44-11f0-a218-005056ae3c32 into Cytoscape


## Configuration

Properties are editable at runtime via **Edit > Preferences > Properties > cytoscapemcp**:

| Property | Default | Description |
|----------|---------|-------------|
| `mcp.http_port` | `9998` | TCP port the MCP server listens on (restart required) |
| `mcp.ndexbaseurl` | `https://www.ndexbio.org` | NDEx base URL (takes effect immediately) |

## Building from Source

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

## Documentation

Full documentation is in the `docs/` directory:

- [User Manual](docs/UserManual.md) — configuration reference and AI client setup
- [Tutorial](docs/Tutorial.md) — end-to-end walkthrough: install, connect, load a network
- [FAQ](docs/FAQ.md) — common questions and troubleshooting

## COPYRIGHT AND LICENSE

[Click here](LICENSE)

## Acknowledgements

* TODO: denote funding sources
