# Cytoscape MCP Server — Tutorial

## Introduction

This tutorial walks through the complete workflow of connecting Claude Desktop to Cytoscape Desktop via the MCP server and using it to load a biological network from NDEx as the current view.

**Prerequisites:**
- Cytoscape 3.10+ installed and running
- Cytoscape MCP Server app installed (see [User Manual](UserManual.md))
- MCP enabled Agent such as Claude or Codex installed.

---

## Step 1: Verify the MCP Server is Running

After Cytoscape starts, look at the **bottom-left corner** of the Cytoscape window. You should see a bold **MCP** button:

- **Green label** — the server started successfully and is ready.
- **Red label** — the server is not responding. Check that CyREST is active and the `mcp.http_port` setting in **Edit > Preferences > Properties > cytoscapemcp** matches Cytoscape's actual CyREST port.

You can also confirm via `curl`:

```bash
curl http://localhost:{rest.port}/mcp/health
```

Replace `{rest.port}` with the CyREST port shown in the Agent Configuration dialog. A `{"status":"ok"}` response confirms the server is ready. A "connection refused" error means Cytoscape is not running.

---

## Step 2: Connect Agent

Click the **MCP** button in the bottom-left corner of Cytoscape to open the Agent Configuration dialog. It shows the current MCP endpoint URL and step-by-step instructions for your most agents.

---

## Step 3: Use the tools from Agent

Invoking the MCP tools requires some prompt engineering to provide key words or phrases which will activate the LLM to choose usage of a tool. Check out [MCPManifest.md](https://github.com/cytoscape/cytoscape-desktop-mcp/blob/main/MCPManifest.md) which contains 3 to 4 examples of Prompt snippets on each tool's description as reference of how to trigger LLM activation. 

* an example of a simple prompt which will lead the LLM to reason over the available tools as a whole and orchestrate their usage as building blocks into a sequence to reach the requested end result:
```
 > open a network in cytoscape desktop 
```

---

## Step 4: Verify in Cytoscape

Switch to Cytoscape Desktop. The loaded network should be visible in the **Network** panel and rendered in the main canvas. 

---

## Changing the NDEx Server (Optional)

If you are using a custom NDEx instance, update the base URL via **Edit > Preferences > Properties > cytoscapemcp** and set `mcp.ndexbaseurl` to your server's URL. Changes take effect immediately for subsequent tool calls.

---

## What's Next?

Explore the other available tools — ask your agent to:
- List all networks loaded in Cytoscape desktop
- Change the active network view to a different network
- Apply a different layout in Cytoscaep desktop

