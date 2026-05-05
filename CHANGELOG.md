# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-05-25

First GA release.

-  Added TableImportFile mcp tool, [pull/4](https://github.com/cytoscape/cytoscape-desktop-mcp/pull/4)

## [1.0.0-rc.4] - 2026-04-25

-  Add 3 new tools for using desktop commands from llm: CommandGatewayGetTool, CommandGatewayInvokeTool, CommandGatewaySearchTool, [pull/3](https://github.com/cytoscape/cytoscape-desktop-mcp/pull/3)

## [1.0.0-rc.3] - 2026-03-25

### Added
- Claude Desktop extension bundle (`cytoscape-mcp.mcpb`) added to enable stdio bridge configuration to Cytoscape's MCP HTTP server, use `make build_claude_mcpb`
- 10 new tools added for visual style mapping and data column interrogation: `get_mappable_properties`, `get_compatible_columns`, `get_styles`, `switch_current_style`, `get_column_range`, `get_column_distinct_values`, `create_continuous_mapping`, `create_discrete_mapping`, `create_discrete_mapping_generated`, `create_passthrough_mapping`
- refer to [MCPManifest.md](./MCPManifest.md) for detailed descriptions of each MCP tool.

### Fixed / Changed
- `load_cytoscape_network_view` refactored from optional parameters to a ConditionalParameter model for improved LLM input guidance and more reliable parameter resolution
- Fixed conditional parameter processing on `load_cytoscape_network_view` to correctly handle waived vs. provided states
- Added inferred data types on tabular file column inspection and enforced a maximum-size cap on column value retrievals
- Revised tool descriptions across the manifest to remove direct tool-name self-references for cleaner LLM context
- Discrete mapping generator color input made conditional; node size is now locked when a size-based style mapping is applied
- Fixed stdio bridge timeout in the Claude Desktop extension
- Updated to mcp sdk 1.1.0, updated McpTransportProvider to follow the latest from 1.1.0.

- prompt activation examples
  - discrete mapping
    - `> map edge shape to interaction`
    - `> map weight to node size`
    - `> generate green colors on edges based on discrete values of confidence`
  - continous mapping
    - `> set color gradient on nodes from blue to red based on eccentricity`
  - pass through mapping
    - `> set node label to gene1`
  - manage styles
    - `> switch network style`
    - `> switch network style to default`  


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

