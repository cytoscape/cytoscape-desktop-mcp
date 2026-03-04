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

### `load_cytoscape_network_view`

Loads a network into Cytoscape Desktop from NDEx (by UUID), a network-format file, or a tabular data file, and sets it as the current active network view.

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `network-id` | string | Yes | NDEx UUID, file path, or URL of the network to load |

**Returns:** Confirmation with the loaded network name, or an error description.

---

### `get_loaded_network_views`

Returns all networks and views currently loaded in Cytoscape.

**Returns:** A list of network names, network SUIDs, and their associated view SUIDs.

---

### `set_current_network_view`

Sets the active network view in Cytoscape by network or view ID.

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `network-suid` | string | No | SUID of the network to activate |
| `view-suid` | string | No | SUID of the specific view to activate |

---

### `create_network_view`

Creates a new view for an existing network that currently has no view.

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `network-suid` | string | Yes | SUID of the network to create a view for |

---

## Connecting an AI Agent

See **[AgentConfiguration.md](AgentConfiguration.md)** for step-by-step setup instructions covering Claude Desktop, Claude Code, GitHub Copilot (VS Code), GitHub Copilot CLI, and OpenAI Codex CLI.

---

## Troubleshooting

**MCP button is red:** The MCP server had a startup failure or sever runtime failure. Run `curl http://localhost:{rest.port}/mcp/health` to confirm.

**App does not appear in Preferences:** Confirm the JAR was installed through App Manager and Cytoscape was restarted.
