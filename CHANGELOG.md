# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0-rc.1] - 2026-03-09

### Added
- Initial release of the Cytoscape MCP Server app
- Embedded MCP (Model Context Protocol) server running inside Cytoscape Desktop CyRest HTTP (Streamable HTTP transport) — no separate server process required
- 9 MCP tools initially added related to loading and analyzing a network on desktop: `get_loaded_network_views`, `set_current_network_view`, `create_network_view`, `load_cytoscape_network_view`, `inspect_tabular_file`, `get_file_columns`, `analyze_network`, `get_layout_algorithms`, `apply_layout`
- refer to [MCPManifest.md](./MCPManifest.md) for detailed descriptions of each MCP tool.

