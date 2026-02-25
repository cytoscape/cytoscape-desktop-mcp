# Connecting an Agent to the Cytoscape MCP Server

The Cytoscape MCP Server publishes its endpoint over the [MCP Streamable HTTP transport](https://modelcontextprotocol.io/specification/2025-03-26/basic/transports#streamable-http). Any MCP-compatible agent that supports this standard transport can connect to it.

**MCP URL** `http://localhost:9998/mcp` (Cytoscape must be running with this app installed at which point this url will be live)

**Note:** Multiple agents can connect simultaneously, but Cytoscape Desktop is a single-user application with shared state — concurrent agents may issue conflicting commands that affect view state. Users are responsible for coordinating agent activity.

If your agent is not listed below, consult its documentation for how to configure an MCP server for Streamable HTTP and specify the url as shown.

---

## Claude Desktop

1. Open Claude Desktop and go to **Settings > Connectors**.
2. Click **"Add custom connector"**.
3. Enter the server URL: `http://localhost:9998/mcp`
4. Save — the connector is active immediately, no restart needed.

To verify: click the **"+"** button in the chat input and select **"Connectors"** to confirm Cytoscape appears in the list.

---

## Claude Code

```bash
claude mcp add --transport http cytoscape-mcp http://localhost:9998/mcp
```

To verify it was added:

```bash
claude mcp list
```

---

## GitHub Copilot (VS Code)

**Option A — Command Palette (guided):**

1. Open the Command Palette: `Cmd+Shift+P` (macOS) or `Ctrl+Shift+P` (Windows/Linux).
2. Run **"MCP: Add Server"**.
3. Choose **HTTP** as the server type, enter `http://localhost:9998/mcp` as the URL, and name it `cytoscape-mcp`.
4. Choose **Workspace** scope (project-only) or **User** scope (all projects).

**Option B — VS Code CLI (one-liner):**

```bash
code --add-mcp '{"name":"cytoscape-mcp","type":"http","url":"http://localhost:9998/mcp"}'
```

To manage or remove servers later: `Cmd+Shift+P` → **"MCP: List Servers"**.

---

## GitHub Copilot CLI

Inside a Copilot CLI interactive session, run:

```
/mcp add
```

Fill in the form fields (Tab to navigate, Ctrl+S to save):

| Field | Value |
|-------|-------|
| Server Name | `cytoscape-mcp` |
| Server Type | SSE |
| URL | `http://localhost:9998/mcp` |
| HTTP Headers | *(leave blank)* |

To verify: run `/mcp show` inside the session to list configured servers.

---

## OpenAI Codex CLI

```bash
codex mcp add cytoscape-mcp --http-url http://localhost:9998/mcp
```

To verify: run `codex mcp list`, or type `/mcp` inside the interactive Codex TUI.
