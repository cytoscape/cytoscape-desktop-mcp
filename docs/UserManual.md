# Cytoscape MCP Server — User Manual

## Overview

The Cytoscape MCP Server app exposes [Cytoscape Desktop](https://cytoscape.org) as an MCP (Model Context Protocol) server, allowing AI clients such as Claude Desktop to control Cytoscape directly through natural language.

Once installed, the app starts an HTTP server inside Cytoscape that AI clients connect to over Server-Sent Events (SSE). Clients can discover and call tools that load networks, manipulate views, and query the active session.

---

## Installation

1. Download the latest `cytoscape-mcp-<version>.jar` from the [Releases](../../releases) page.
2. Open Cytoscape Desktop.
3. Navigate to **Apps > App Manager**.
4. Click **Install from File** and select the downloaded JAR.
5. Restart Cytoscape if prompted.

After startup the app logs:
```
Cytoscape MCP Server started on port 9998 (version X.Y.Z)
SSE endpoint:     http://localhost:9998/mcp
Message endpoint: http://localhost:9998/mcp/message
```

---

## Configuration

Properties are editable at runtime via **Edit > Preferences > Properties > cytoscapemcp**.

| Property | Default | Description |
|----------|---------|-------------|
| `mcp.http_port` | `9998` | TCP port the MCP HTTP server listens on |
| `mcp.ndexbaseurl` | `https://www.ndexbio.org` | Base URL of the NDEx server to load networks from |

Changes to `mcp.ndexbaseurl` take effect immediately (tool calls read it at invocation time). Port changes require a Cytoscape restart.

---

## Available MCP Tools

### `load_cytoscape_network_view`

Loads a biological network from [NDEx](https://www.ndexbio.org) into Cytoscape Desktop and sets it as the current active network view.

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `network-id` | string | Yes | UUID of the network on NDEx (e.g. `a7e43e3d-c7f8-11ec-8d17-005056ae23aa`) |

**Returns:** A confirmation message with the loaded network name, or an error description if the network was not found.

**Network IDs** can be found by browsing [NDEx](https://www.ndexbio.org), searching for the network of interest, and copying the UUID from the URL of the network's detail page.

---

## Connecting an AI Client

### Claude Desktop

Add the following to your Claude Desktop MCP configuration (typically `~/Library/Application Support/Claude/claude_desktop_config.json` on macOS):

```json
{
  "mcpServers": {
    "cytoscape": {
      "url": "http://localhost:9998/mcp"
    }
  }
}
```

Restart Claude Desktop. Cytoscape must already be running with the MCP app installed.

---

## Troubleshooting

**Port already in use:** If another process is on port 9998, change `mcp.http_port` in Preferences and restart Cytoscape.

**Tool call fails with "not found":** Verify the NDEx network UUID is correct and that the NDEx server at `mcp.ndexbaseurl` is reachable from your machine.

**App does not appear in Preferences:** Confirm the JAR was installed through App Manager and Cytoscape was restarted.
