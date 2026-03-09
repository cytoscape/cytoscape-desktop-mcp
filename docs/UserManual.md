# Cytoscape MCP Server — User Manual

## Overview

The Cytoscape MCP Server app exposes [Cytoscape Desktop](https://cytoscape.org) as an MCP (Model Context Protocol) server, allowing AI clients such as Claude Desktop to control Cytoscape directly through natural language.

Once installed, the app registers an MCP endpoint inside Cytoscape's existing CyREST HTTP server. No separate server process or port is started. AI clients connect to the MCP Streamable HTTP endpoint and call tools that load networks, manipulate views, and query the active session.

---

## Installation

1. Download the latest `cytoscape-mcp-<version>.jar` from the [Releases](../../releases) page.
2. Open Cytoscape Desktop.
3. Navigate to **Apps > App Manager**.
4. Click **Install from File** and select the downloaded JAR.
5. Restart Cytoscape if prompted.

After startup the MCP endpoint is available at `http://localhost:{rest.port}/mcp` where `{rest.port}` is Cytoscape's CyREST port. The current endpoint URL is shown in the Agent Configuration dialog (click the **MCP** button in the status bar).

---

## Desktop UI

The app adds two visual indicators to the Cytoscape Desktop interface:

### MCP Toolbar Button

A bold **MCP** button appears in the bottom-left status bar next to the other status buttons. The label color reflects the current server state:

- **Green** — MCP server is running and ready to accept connections.
- **Red** — MCP server is not responding. Verify that Cytoscape is running and CyREST is active.

Click the button to open the **Agent Configuration** dialog, which shows the current endpoint URL and step-by-step connection instructions for all supported agents.

### Task History

Every MCP tool invocation is recorded as an entry in Cytoscape's Task History. To view it, go to **View > Show Task History**. Each entry shows the tool name and the time it was called, giving you a full audit trail of agent activity.

---

## Configuration

Properties are editable at runtime via **Edit > Preferences > Properties > cytoscapemcp**.

| Property | Default | Description |
|----------|---------|-------------|
| `mcp.ndexbaseurl` | `https://www.ndexbio.org` | Base URL of the NDEx server to load networks from |

Changes to `mcp.ndexbaseurl` take effect immediately (tool calls read it at invocation time).

---

## Available MCP Tools
Check out [MCPManifest.md](https://github.com/cytoscape/cytoscape-desktop-mcp/blob/main/MCPManifest.md). It provides a catalog of all MCP Tools currently available. Each tool also contains 3 to 4 examples of Prompt snippets on each tool's description as reference of how to trigger LLM activation of the tool. 

---

## Connecting an AI Agent

Click the **MCP** button in the bottom-left corner of Cytoscape to open the Agent Configuration dialog. It shows the current MCP endpoint URL and step-by-step instructions for your most agents.

---

## Troubleshooting

Follow [Desktop MCP diagnostic steps and tools](https://github.com/cytoscape/cytoscape-desktop-mcp?tab=readme-ov-file#cytoscape-desktop-mcp-diagnostics).
