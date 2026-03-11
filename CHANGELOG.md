# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0-rc.2] - 2026-03-11

### Added
- 2 new tools added for reading and updating properties of network default style: `set_visual_default`, `get_visual_style_defaults`
- refer to [MCPManifest.md](./MCPManifest.md) for detailed descriptions of each MCP tool.
-  prompt activation examples
  - `> increse the network edge width by 1` 
  - `> change the network node color to green`
  - `> change node label to courier new`
  - `> what properties can I lock on network`
  - `> lock node width and height on network`
  


## [1.0.0-rc.1] - 2026-03-09

### Added
- Initial release of the Cytoscape MCP Server app
- Embedded MCP (Model Context Protocol) server running inside Cytoscape Desktop CyRest HTTP (Streamable HTTP transport) — no separate server process required
- 9 MCP tools initially added related to loading and analyzing a network on desktop: `get_loaded_network_views`, `set_current_network_view`, `create_network_view`, `load_cytoscape_network_view`, `inspect_tabular_file`, `get_file_columns`, `analyze_network`, `get_layout_algorithms`, `apply_layout`
- refer to [MCPManifest.md](./MCPManifest.md) for detailed descriptions of each MCP tool.
-  prompt activation examples
  - `> open a network using cytoscape desktop` 
  - `> analyze the network`
  - `> change the network layout`

