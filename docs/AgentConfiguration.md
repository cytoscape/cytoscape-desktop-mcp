# Connecting an Agent to the Cytoscape MCP Server

**MCP URL:** `http://localhost:{rest.port}/mcp`

Cytoscape must be running with this app installed. The app will run an mcp server that runs under the existing Cyrest port.

Multiple agents can connect simultaneously. Cytoscape is a single-user application — concurrent agents may issue conflicting commands.

If your agent is not listed below, consult its documentation for configuring an MCP server with Streamable HTTP transport.

---

## Claude Desktop

**Prerequisite:** Go to **Settings > Extensions > Advanced** and enable
**Use Built-in Node.js for MCP**. This is required for the extension to function.

Download the **Cytoscape MCP** extension from the
[releases page](https://github.com/cytoscape/cytoscape-desktop-mcp/releases/)
(`cytoscape-mcp.mcpb`). In Claude Desktop go to **Settings > Extensions**, click
**Install Extension**, and select the downloaded `cytoscape-mcp.mcpb` file.

To verify: the **Cytoscape MCP** connector will appear in **Customize > Connectors**. 
This screen will display the CyREST port as a config defaulted to 1234, change that if you have changed the CyRest port on Desktop as
the mcp server is hosted on CyRest.

```
---

## Claude Code

```
claude mcp add --transport http cytoscape-mcp http://localhost:{rest.port}/mcp
```

To verify:

```
claude mcp list
```

---

## GitHub Copilot (VS Code)

**Option A — Command Palette:** Open Command Palette (Cmd+Shift+P / Ctrl+Shift+P), run **MCP: Add Server**, choose HTTP, enter the URL below, name it `cytoscape-mcp`.

```
http://localhost:{rest.port}/mcp
```

**Option B — CLI:**

```
code --add-mcp '{"name":"cytoscape-mcp","type":"http","url":"http://localhost:{rest.port}/mcp"}'
```

---

## GitHub Copilot CLI

```
copilot mcp add --transport http cytoscape http://localhost:1234/mcp
```

To verify: 
```
copilot mcp list
```

---

## OpenAI Codex CLI

```
codex mcp add cytoscape-mcp --http-url http://localhost:{rest.port}/mcp
```

To verify: run `codex mcp list` or type `/mcp` inside the Codex TUI.

---

## Diagnostics

To interrogate the MCP server direct, use browser and go to http://localhost:{rest.port}/mcp/manifest.
The MCP server exposes a `/mcp/manifest` endpoint to provide a lightweight, human-readable catalog of every tool, prompt, resource, and resource template registered   on the server, formatted as Markdown. 

