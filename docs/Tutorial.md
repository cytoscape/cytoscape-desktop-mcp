# Cytoscape MCP Server — Tutorial

## Introduction

This tutorial walks through the complete workflow of connecting Claude Desktop to Cytoscape Desktop via the MCP server and using it to load a biological network from NDEx as the current view.

**Prerequisites:**
- Cytoscape 3.10+ installed and running
- Cytoscape MCP Server app installed (see [User Manual](UserManual.md))
- MCP enabled Agent such as Claude or Codex installed.

---

## Step 1: Verify the MCP Server is Running

After Cytoscape starts, confirm the MCP server is active by opening a terminal and running:

```bash
curl http://localhost:9998/mcp
```

You should see an HTTP response (a 400 or 405 error is expected — it confirms the server is listening). A "connection refused" error means the server is not running.

If you see a "connection refused" error, check that the app is installed and that Cytoscape has fully started.

---

## Step 2: Connect Agent

Open (or create) your Agent MCP configuration file, this example uses Claude Code:

- **macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows:** `%APPDATA%\Claude\claude_desktop_config.json`

Add (or merge) the following entry for the MCP server published by Cytoscsape Desktop:

```json
{
  "mcpServers": {
    "cytoscape": {
      "url": "http://localhost:9998/mcp"
    }
  }
}
```

Save the file and restart Claude Desktop. You should see "cytoscape" listed in Claude's connected tools panel.
**Note:** Multiple agents can connect simultaneously, but Cytoscape Desktop is a single-user application with shared state — concurrent agents may issue conflicting commands that affect view state. Users are responsible for coordinating agent activity.

---

## Step 3: Find a Network UUID on NDEx

1. Go to [https://www.ndexbio.org](https://www.ndexbio.org).
2. Search for a network of interest (e.g. "STRING human").
3. Click on a result to open its detail page.
4. Copy the UUID from the URL — it looks like:
   ```
   https://www.ndexbio.org/viewer/networks/a7e43e3d-c7f8-11ec-8d17-005056ae23aa
   ```
   The UUID is `a7e43e3d-c7f8-11ec-8d17-005056ae23aa`.

---

## Step 4: Load the Network into Cytoscape Desktop via Agent

In Agent, type a prompt like:

> "Load the network with NDEx ID a7e43e3d-c7f8-11ec-8d17-005056ae23aa into Cytoscape."

Agent will call the `load_cytoscape_network_view` MCP tool published by Cytoscape Desktop. Within a few seconds the network should appear in Cytoscape Desktop as the active network view.

---

## Step 5: Verify in Cytoscape

Switch to Cytoscape Desktop. The loaded network should be visible in the **Network** panel and rendered in the main canvas. The network name typically matches what is stored in NDEx.

---

## Changing the NDEx Server (Optional)

If you are using a custom NDEx instance, update the base URL via **Edit > Preferences > Properties > cytoscapemcp** and set `mcp.ndexbaseurl` to your server's URL. Changes take effect immediately for subsequent tool calls.

---

## What's Next?

Additional MCP tools for Cytoscape (querying network data, applying visual styles, running layouts, etc.) can be added by extending the app. See the source code in `src/main/java/edu/ucsd/idekerlab/cytoscapemcp/tools/` for the tool implementation pattern.
