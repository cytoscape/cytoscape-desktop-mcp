# Connecting an Agent to the Cytoscape MCP Server

**MCP URL:** `http://localhost:{mcp.http_port}/mcp`

Cytoscape must be running with this app installed. The port defaults to 9998 and can be changed under Edit > Preferences > Properties > cytoscapemcp.

Multiple agents can connect simultaneously. Cytoscape is a single-user application — concurrent agents may issue conflicting commands.

If your agent is not listed below, consult its documentation for configuring an MCP server with Streamable HTTP transport.

---

## Claude Desktop

Open Claude Desktop and go to **Settings > Connectors**. Click **Add custom connector**. Enter the URL and save — active immediately, no restart needed.

```
http://localhost:{mcp.http_port}/mcp
```

To verify: click the **+** button in the chat input and select **Connectors**.

---

## Claude Code

```
claude mcp add --transport http cytoscape-mcp http://localhost:{mcp.http_port}/mcp
```

To verify:

```
claude mcp list
```

---

## GitHub Copilot (VS Code)

**Option A — Command Palette:** Open Command Palette (Cmd+Shift+P / Ctrl+Shift+P), run **MCP: Add Server**, choose HTTP, enter the URL below, name it `cytoscape-mcp`.

```
http://localhost:{mcp.http_port}/mcp
```

**Option B — CLI:**

```
code --add-mcp '{"name":"cytoscape-mcp","type":"http","url":"http://localhost:{mcp.http_port}/mcp"}'
```

---

## GitHub Copilot CLI

Inside a Copilot CLI interactive session, run `/mcp add` and fill in the form (Tab to navigate, Ctrl+S to save):

**Server Name:** `cytoscape-mcp`
**Server Type:** SSE
**URL:** `http://localhost:{mcp.http_port}/mcp`
**HTTP Headers:** leave blank

To verify: run `/mcp show` inside the session.

---

## OpenAI Codex CLI

```
codex mcp add cytoscape-mcp --http-url http://localhost:{mcp.http_port}/mcp
```

To verify: run `codex mcp list` or type `/mcp` inside the Codex TUI.

---

## Diagnostics

To interrogate the MCP server directly, install [cli-mcp](https://lobehub.com/mcp/zueai-cli-mcp):

```
uvx cli-mcp direct list --url http://localhost:{mcp.http_port}/mcp
```

Install uv first if needed:

```
curl -LsSf https://astral.sh | sh
```

