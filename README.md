[gradle]: https://gradle.org/
[java]: https://www.oracle.com/java/index.html
[git]: https://git-scm.com/
[make]: https://www.gnu.org/software/make
[cytoscape]: https://cytoscape.org/
[mcp]: https://modelcontextprotocol.io/
[ndex]: https://www.ndexbio.org/

Cytoscape MCP Server
=======================================

An embedded [Model Context Protocol (MCP)][mcp] server for [Cytoscape Desktop][cytoscape], packaged as a Cytoscape App. AI clients such as Claude Desktop connect to Cytoscape over HTTP/SSE and invoke tools that control the desktop application directly — loading networks, setting active views, and more.

**NOTE:** This app is experimental. The interface and available tools are subject to change.

## How It Works

Once installed, the app starts a Jetty HTTP server inside Cytoscape on startup. AI clients connect to the SSE endpoint and call MCP tools that interact with Cytoscape's Java API. The server runs entirely within the Cytoscape process — no separate server process or sidecar is needed.

```
Claude Desktop ──SSE──► http://localhost:9998/mcp ──► Cytoscape Desktop
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

After startup, the MCP server logs its endpoints:
```
Cytoscape MCP Server started on port 9998
SSE endpoint:     http://localhost:9998/mcp
Message endpoint: http://localhost:9998/mcp/message
```

## Connecting Claude Desktop

Add the following to your Claude Desktop MCP configuration (`~/Library/Application Support/Claude/claude_desktop_config.json` on macOS):

```json
{
  "mcpServers": {
    "cytoscape": {
      "url": "http://localhost:9998/mcp"
    }
  }
}
```

Restart Claude Desktop. Cytoscape must be running with the MCP app installed.

## Available Tools

| Tool | Description |
|------|-------------|
| `load_cytoscape_network_view` | Loads a network from [NDEx][ndex] by UUID and sets it as the current view in Cytoscape |

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
