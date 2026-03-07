# Tool Spec: Network Setup and Styling Wizard Tools

## 4. MCP Tool Schemas

### 4.1 `load_cytoscape_network_view`

See `04-load-network-tools.md §4.1` for the full tool schema and response examples.

### 4.2 `get_file_columns`

See `04-load-network-tools.md §4.2` for the full tool schema and response examples.

### 4.2a `inspect_tabular_file`

See `04-load-network-tools.md §4.2a` for the full tool schema and response examples.

### 4.3 `get_loaded_network_views`

```json
{
  "name": "get_loaded_network_views",
  "title": "List Cytoscape Desktop Networks",
  "description": "Enumerate all network collections currently loaded in Cytoscape Desktop with their views, node counts, and edge counts. Read-only; does not modify state.",
  "inputSchema": {
    "type": "object",
    "properties": {},
    "required": []
  }
}
```

**Success response:**
```json
{
  "content": [{ "type": "text", "text": "{\"views\":[{\"collection_name\":\"My Collection\",\"network_name\":\"network1\",\"network_suid\":100,\"view_suid\":200,\"node_count\":50,\"edge_count\":120},{\"collection_name\":\"Another Collection\",\"network_name\":\"large_net\",\"network_suid\":300,\"view_suid\":null,\"node_count\":5000,\"edge_count\":15000}]}" }],
  "isError": false
}
```

### 4.4 `set_current_network_view`

```json
{
  "name": "set_current_network_view",
  "title": "Set Cytoscape Desktop Active Network",
  "description": "Set the specified network and view as the current (active) network and view in Cytoscape Desktop. Both network_suid and view_suid are required.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "network_suid": {
        "type": "integer",
        "description": "SUID of the network to set as current."
      },
      "view_suid": {
        "type": "integer",
        "description": "SUID of the network view to set as current."
      }
    },
    "required": ["network_suid", "view_suid"]
  }
}
```

**Success response:**
```json
{
  "content": [{ "type": "text", "text": "{\"status\":\"success\",\"network_name\":\"network1\",\"node_count\":50,\"edge_count\":120}" }],
  "isError": false
}
```

**Error response:**
```json
{
  "content": [{ "type": "text", "text": "Network view not found. The view may have been closed." }],
  "isError": true
}
```

### 4.5 `create_network_view`

```json
{
  "name": "create_network_view",
  "title": "Create Cytoscape Desktop Network View",
  "description": "Create a visual view for a network in Cytoscape Desktop that currently has no view. Sets the new view and its network as the current network and view.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "network_suid": {
        "type": "integer",
        "description": "SUID of the network to create a view for."
      }
    },
    "required": ["network_suid"]
  }
}
```

**Success response:**
```json
{
  "content": [{ "type": "text", "text": "{\"status\":\"success\",\"network_suid\":300,\"view_suid\":400,\"network_name\":\"large_net\",\"node_count\":5000,\"edge_count\":15000}" }],
  "isError": false
}
```

**Error response:**
```json
{
  "content": [{ "type": "text", "text": "Failed to create view: Network with SUID 300 not found." }],
  "isError": true
}
```

### 4.6 `analyze_network`

```json
{
  "name": "analyze_network",
  "title": "Analyze Cytoscape Desktop Network",
  "description": "Run NetworkAnalyzer on the current network in Cytoscape Desktop to compute topological statistics. Adds columns like Degree, BetweennessCentrality, ClusteringCoefficient, etc. to the node and edge tables.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "directed": {
        "type": "boolean",
        "description": "True to treat the network as directed, false for undirected."
      }
    },
    "required": ["directed"]
  }
}
```

**Success response:**
```json
{
  "content": [{ "type": "text", "text": "{\"status\":\"success\",\"columns_added\":[\"Degree\",\"BetweennessCentrality\",\"ClosenessCentrality\",\"ClusteringCoefficient\",\"AverageShortestPathLength\",\"Eccentricity\",\"Radiality\",\"Stress\",\"TopologicalCoefficient\",\"NeighborhoodConnectivity\"],\"network_stats\":{\"avg_degree\":4.2,\"density\":0.05,\"connected_components\":3}}" }],
  "isError": false
}
```

### 4.7 `get_layout_algorithms`

```json
{
  "name": "get_layout_algorithms",
  "title": "List Cytoscape Desktop Layouts",
  "description": "List all available layout algorithms in Cytoscape Desktop with their names and descriptions.",
  "inputSchema": {
    "type": "object",
    "properties": {},
    "required": []
  }
}
```

**Success response:**
```json
{
  "content": [{ "type": "text", "text": "{\"layouts\":[{\"name\":\"force-directed\",\"displayName\":\"Prefuse Force Directed Layout\",\"description\":\"Spring-embedded layout using the Prefuse toolkit\"},{\"name\":\"hierarchical\",\"displayName\":\"Hierarchical Layout\",\"description\":\"Arrange nodes in a hierarchy based on edge direction\"},{\"name\":\"circular\",\"displayName\":\"Circular Layout\",\"description\":\"Arrange nodes in a circle\"},{\"name\":\"grid\",\"displayName\":\"Grid Layout\",\"description\":\"Arrange nodes in a grid pattern\"},{\"name\":\"degree-circle\",\"displayName\":\"Degree Sorted Circle Layout\",\"description\":\"Circular layout sorted by node degree\"},{\"name\":\"kamada-kawai\",\"displayName\":\"Edge-weighted Spring Embedded Layout\",\"description\":\"Spring-embedded layout with edge weights\"}]}" }],
  "isError": false
}
```

### 4.8 `apply_layout`

```json
{
  "name": "apply_layout",
  "title": "Apply Cytoscape Desktop Layout",
  "description": "Apply a layout algorithm to the current network view in Cytoscape Desktop using default parameters.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "algorithm": {
        "type": "string",
        "description": "Layout algorithm name (as returned by get_layout_algorithms)."
      }
    },
    "required": ["algorithm"]
  }
}
```

**Success response:**
```json
{
  "content": [{ "type": "text", "text": "{\"status\":\"success\",\"algorithm\":\"force-directed\",\"displayName\":\"Prefuse Force Directed Layout\"}" }],
  "isError": false
}
```

## 6. Edge Cases and Error Handling

### Phase 0 Edge Cases

### 6.1 No Networks Loaded

- **Trigger**: `get_loaded_network_views` returns an empty list.
- **Tool behavior**: Returns `{ "views": [] }`.
- **Agent behavior**: Skip Phase 0 entirely and proceed to Phase 1 with a welcome message.

### 6.2 Selected View Deleted Between Listing and Selection

- **Trigger**: User selects an existing network/view, but `set_current_network_view` fails because the view was closed between the listing and selection.
- **Tool behavior**: `set_current_network_view` returns `isError: true` with "not found" message.
- **Agent script**: "That network view is no longer available. Let me refresh the list." → re-call `get_loaded_network_views` and re-present the list.

### 6.3 Network Without View (Create View Interaction)

- **Trigger**: User selects a network from the list that has `view_suid: null`.
- **Agent script**: "This network doesn't have a visual view yet. Would you like me to create one?"
  - If yes → call `create_network_view` with `{ "network_suid": $network_suid }`. On success → skip to Phase 2. On error → re-present Phase 0 list.
  - If no → "No problem. Would you like to pick a different network or load a new one?" → re-present Phase 0 list.

### 6.4 Pre-Existing Analysis Columns on Existing Network

- **Trigger**: User selected an existing network in Phase 0 that already has analysis columns (e.g., Degree, BetweennessCentrality).
- **Agent script**: "I notice this network already has analysis columns ({list}). Would you like to re-run analysis (this will overwrite existing values) or keep the existing analysis and skip to layout?"
- **Flow**: If user skips → go to Phase 3 (layout). If user re-runs → proceed with normal Phase 2.

Phase 1 edge cases are documented in `04-load-network-tools.md §6`.

### Phase 2+ Edge Cases

### 6.11 NetworkAnalyzer Not Available

- **Trigger**: The Analyzer app is disabled, not installed, or its command API is not registered.
- **Tool behavior**: `analyze_network` returns `isError: true` with "not available" message.
- **Agent script**: "Network analysis is not available — the NetworkAnalyzer app may not be installed. Skipping this step. You can still style the network using the existing data columns."
- **Flow**: Skip to Phase 3 (layout).

### 6.12 Layout Timeout / Failure

- **Trigger**: Layout algorithm fails or takes too long (very large networks).
- **Tool behavior**: `apply_layout` returns error.
- **Agent script**: "The layout didn't complete successfully. This can happen with very large networks. Would you like to try a simpler layout like Grid, or skip the layout step?"

### 6.13 No Network View After Load

- **Trigger**: Network loaded but no view was created (can happen with very large networks where Cytoscape skips view creation).
- **Tool behavior**: Tools that need a view return "No network view is currently available."
- **Agent script**: "It looks like Cytoscape didn't create a visual view for this network, possibly because it's very large. Would you like me to try creating one?" If yes → call `create_network_view`. If that fails → "Layout and styling require a view. You may need to adjust Cytoscape's view creation threshold (Edit > Preferences)."

### General Edge Cases

### 6.14 User Wants to Skip a Phase

- **Trigger**: User says "skip" at any step.
- **Agent behavior**: Acknowledge and move to the next phase. Record "(skipped)" in the final summary.
