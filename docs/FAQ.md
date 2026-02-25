# Cytoscape MCP Server — FAQ

### What is the MCP Server app?

It turns Cytoscape Desktop into an MCP (Model Context Protocol) server. AI clients (such as Claude Desktop) can connect to it and call tools that control Cytoscape — for example, loading networks from NDEx and setting them as the active view.

---

### How do I find a network's NDEx UUID?

Go to [https://www.ndexbio.org](https://www.ndexbio.org), search for the network, open its detail page, and copy the UUID from the URL. Example URL:
```
https://www.ndexbio.org/viewer/networks/a7e43e3d-c7f8-11ec-8d17-005056ae23aa
```
The UUID is `a7e43e3d-c7f8-11ec-8d17-005056ae23aa`.

---

### What port does the server use?

Default is **9998**. You can change it via **Edit > Preferences > Properties > cytoscapemcp** (`mcp.http_port`). A Cytoscape restart is required for port changes to take effect.

---

### Can I use a private or internal NDEx instance?

Yes. Change the `mcp.ndexbaseurl` property via **Edit > Preferences > Properties > cytoscapemcp** to point to your internal NDEx server. This change takes effect immediately without restarting.

---

### Does the server require internet access?

Only when the `load_cytoscape_network_view` tool is called — it fetches the network from NDEx. If you are using a local NDEx instance, no internet access is required.

---

### How do I verify the server is running?

Run the following from a terminal while Cytoscape is open:
```bash
curl http://localhost:9998/mcp
```
You should see an HTTP response (a 400 or 405 error is expected — it confirms the server is listening). A "connection refused" error means the server is not running.

---

### Why does the tool fail with "network not found"?

- The UUID may be incorrect — double-check it on the NDEx website.
- The network may be private on NDEx and require authentication (not currently supported).
- The NDEx server may be temporarily unavailable.
- Your `mcp.ndexbaseurl` property may point to the wrong server.

---

### What happens when Cytoscape shuts down?

The MCP server stops cleanly. The OSGi framework calls the app's `shutDown()` callback, which stops Jetty and the MCP server before the JVM exits.

---

### Can I run multiple Agents against the Cytoscape MCP concurrently?

Yes. The Streamable HTTP transport supports multiple concurrent agent connections, each with its own session. However, Cytoscape Desktop is a single-user application with shared state — concurrent agents may issue conflicting commands (e.g., both changing the active view). Users are responsible for coordinating agent activity to avoid conflicts.
