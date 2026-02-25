# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-02-20

### Added
- Initial release of the Cytoscape MCP Server app
- Embedded MCP (Model Context Protocol) server running inside Cytoscape Desktop over HTTP (Streamable HTTP transport) — no separate server process required
- Jetty 12.0.x servlet container hosting the MCP Streamable HTTP transport (unified `/mcp` endpoint) on configurable port (default: 9998)
- MCP tool `load_cytoscape_network_view` — loads a biological network from [NDEx](https://www.ndexbio.org) by UUID and sets it as the current active network view in Cytoscape
- App properties (`cytoscapemcp` group in Edit > Preferences > Properties):
  - `mcp.http_port` — configurable HTTP port for the MCP server (default: 9998)
  - `mcp.ndexbaseurl` — configurable NDEx base URL (default: `https://www.ndexbio.org`), read at tool-call time so changes take effect without restart
- Clean OSGi lifecycle: `CyActivator.shutDown()` stops both Jetty and the MCP server; a JVM shutdown hook provides SIGINT/SIGTERM safety
- Gradle build replacing Maven — produces a self-contained OSGi bundle JAR with MCP SDK, Jetty, and Jackson embedded
- Built against Cytoscape API 3.10.0, Java 11 target, MCP SDK 0.12.1
