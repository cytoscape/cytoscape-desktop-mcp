# Cytoscape MCP Server — FAQ

### What is the MCP Server app?

It turns Cytoscape Desktop into an MCP (Model Context Protocol) server. AI clients (such as Claude Desktop) can connect to it and call tools that control Cytoscape Desktop — for example, loading networks from NDEx and setting them as the active view.

---

### What port does the server use?

The MCP server runs inside Cytoscape's existing **CyREST** HTTP server — no separate port is opened. The MCP endpoint is at:
```
http://localhost:{rest.port}/mcp
```
where `{rest.port}` is Cytoscape's CyREST port (default varies by Cytoscape version). The current endpoint URL is shown in the Agent Configuration dialog — click the **MCP** button in the status bar.

---

### Can I use a private or internal NDEx instance?

Yes. Change the `mcp.ndexbaseurl` property via **Edit > Preferences > Properties > cytoscapemcp** to point to your internal NDEx server. This change takes effect immediately without restarting.

---

### Does the server require internet access?

Only when the `load_cytoscape_network_view` tool is called and chose a network from NDEx. If you are using a local NDEx instance, no internet access is required.

---

### How do I verify the server is running?

[MCP Diagnostics](https://github.com/cytoscape/cytoscape-desktop-mcp?tab=readme-ov-file#cytoscape-desktop-mcp-diagnostics)

Also, on the desktop, there is an [MCP toolbar](https://github.com/cytoscape/cytoscape-desktop-mcp?tab=readme-ov-file#mcp-toolbar-button) which provides real time status of MCP Server.

---

### How do I connect an Agent to Desktop MCP server?

Refer To [Connecting an Agent To Desktop MCP](https://github.com/cytoscape/cytoscape-desktop-mcp?tab=readme-ov-file#connecting-an-agent-to-cytoscape-desktop-mcp)

### What happens when Cytoscape shuts down?

The MCP server stops cleanly. The OSGi framework calls the app's `shutDown()` callback, which deregisters the MCP endpoint before the JVM exits.

---

### Can I run multiple Agents against the Cytoscape MCP concurrently?

It is not encouraged or recommended to do so. The Streamable HTTP transport supports multiple concurrent agent connections, each with its own session. However, Cytoscape Desktop is a single-user application with shared state — concurrent agents may issue conflicting commands (e.g., both changing the active view). Users are responsible for coordinating agent activity to avoid conflicts.
