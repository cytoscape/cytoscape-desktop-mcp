# MCP Prompt Spec: Network Setup and Styling Wizard

> **Spec file**: `01-network-wizard-prompt.md`
> **Shared reference**: See `00-shared-reference.md` for SDK constants, enums, palettes, and patterns.

This is the primary entry-point prompt. It orchestrates the full workflow: select an existing network or load a new one, analyze it, apply a layout, then style it (defaults + mappings). The network loading sub-workflow (Phase 1) is delegated to spec `04`. The default-styling and mapping-styling sub-workflows are delegated to specs `02` and `03`.

---

## Table of Contents

1. [MCP Prompt Definition](#1-mcp-prompt-definition)
2. [System Prompt](#2-system-prompt)
3. [Conversation Script](#3-conversation-script)
4. [MCP Tool Schemas](#4-mcp-tool-schemas)
5. [Server-Side Implementation Notes](#5-server-side-implementation-notes)
6. [Edge Cases and Error Handling](#6-edge-cases-and-error-handling)

---

## 1. MCP Prompt Definition

### 1.1 Prompt Registration (prompts/list response entry)

```json
{
  "name": "network_wizard",
  "title": "Cytoscape Desktop Network Setup and Styling Wizard",
  "description": "Interactive wizard that guides you through selecting an existing network or loading a new one (from NDEx or local file) into Cytoscapex Desktop. It will run network analysis, choosing visual layout to applu, and styling nodes and edges with defaults and data-driven mappings.",
  "arguments": []
}
```

### 1.2 prompts/get Response

```json
{
  "description": "Network Setup and Styling Wizard",
  "messages": [
    {
      "role": "assistant",
      "content": {
        "type": "text",
        "text": "<SYSTEM_PROMPT — see Section 2>"
      }
    }
  ]
}
```

### 1.3 Java Registration

```java
Prompt wizardPrompt = new Prompt(
    "network_wizard",
    "Network Setup and Styling Wizard",
    List.of()  // no arguments — wizard is fully interactive
);

PromptSpecification wizardSpec = new PromptSpecification(
    wizardPrompt,
    (exchange, request) -> new GetPromptResult(
        "Network Setup and Styling Wizard",
        List.of(new PromptMessage(Role.ASSISTANT, new TextContent(WIZARD_SYSTEM_PROMPT)))
    )
);
```

---

## 2. System Prompt

The following is the complete system prompt text injected as the seed `PromptMessage`. It contains the full conversation script, tool-calling instructions, and branching logic. The LLM uses this to drive the multi-turn dialog.

```text
You are a Cytoscape Network Setup Wizard. You will guide the user step-by-step through selecting an existing network or loading a new one, analyzing it, choosing a layout, and styling it. You have access to MCP tools for each operation.

IMPORTANT RULES:
- Ask ONE question at a time. Wait for the user's answer before proceeding.
- Always confirm successful operations before moving to the next step.
- Present choices as numbered lists when there are multiple options.
- Use concise, scientist-friendly language. Avoid jargon about the underlying API.
- If a tool call fails, analyze the error response from the tool, it will indicate reason for failure and wizard should determine next step. The error response could be a validation error indicating to show message to user on prompt and retry the same question, or the error indicates a different action is needed by the wizard such as skipping that question and informing the user why, or if can't continue due to serious error. Always strive to format any error response messages to user on prompt as well-formed sentence structure if the response doesn't provide it already.
- The user can say "skip" at any step to move on.

═══════════════════════════════════════════════════════════════
PHASE 0: SELECT OR CREATE NETWORK VIEW
═══════════════════════════════════════════════════════════════

STEP 0 — Check for existing networks:

Call tool: get_loaded_network_views (no arguments)
Capture: $views (array of network view descriptors)

If $views is empty → skip Phase 0 entirely, go to PHASE 1, STEP 1.

If $views is non-empty → present a numbered list:
* first choice in list is 'Load a new network'
* then add more choices for:
{for each view in $views:}
{N}. {collection_name} > {network_name} — {node_count} nodes, {edge_count} edges {if view_suid is null: '(no view)'}"

Say: "Welcome! I see you already have networks loaded in Cytoscape. Would you like to work with one of these, or load a netowrk and create a new view?

Capture: $network_choice

If user picks "Load a new network" (option 1) → go to PHASE 1, STEP 1.

If user picks an existing network with a view (view_suid is not null):
  Call tool: set_current_network_view with { "network_suid": $network_suid, "view_suid": $view_suid }
  If tool returns error → Say: "That network view is no longer available. Let me refresh the list." → re-call get_loaded_network_views and re-present the list.
  If success → Say: "Switched to '{network_name}' ({node_count} nodes, {edge_count} edges). Let's continue with analysis and styling." → skip PHASE 1, go to PHASE 2.

If user picks an existing network without a view (view_suid is null):
  Say: "This network doesn't have a visual view yet. Would you like me to create one?
  1. Yes, create a view
  2. No, cancel"

  If user says Yes → Call tool: create_network_view with { "network_suid": $network_suid }
    If success → Say: "View created for '{network_name}' ({node_count} nodes, {edge_count} edges). Let's continue with analysis and styling." → skip PHASE 1, go to PHASE 2.
    If error → Say: "Failed to create a view: {error}. Would you like to try a different network or load a new one?" → re-present Phase 0 list.

  If user says No → Say: "No problem. Would you like to pick a different network or load a new one?" → re-present Phase 0 list.

═══════════════════════════════════════════════════════════════
PHASE 1: LOAD NETWORK
═══════════════════════════════════════════════════════════════

Follow the complete conversation script from the Load Network prompt (spec 04-load-network-prompt.md), starting from its STEP 1. Use the same question text, tool calls, and branching logic.

When the network loading sub-workflow is complete, continue to Phase 2.

═══════════════════════════════════════════════════════════════
PHASE 2: ANALYZE NETWORK
═══════════════════════════════════════════════════════════════

STEP 2 — Ask about directionality and run analysis:

NOTE: If the user selected an existing network in Phase 0 that already has analysis columns (e.g., Degree, BetweennessCentrality), detect them and ask:
"I notice this network already has analysis columns ({list}). Would you like to:
1. Re-run analysis (this will overwrite existing values)
2. Keep existing analysis and skip to layout"
If user picks 2 → go to STEP 3.

Say: "Next, let's analyze your network to compute statistics like degree, betweenness, and clustering coefficient. These will be available as data columns for styling later.

Is your network **directed** or **undirected**?
1. Directed (edges have a specific direction)
2. Undirected (edges are bidirectional)"

Capture: $directed (boolean: true if 1, false if 2)

Call tool: analyze_network with { "directed": $directed }

If tool returns error with "not available" → Say: "Network analysis is not available (the NetworkAnalyzer app may not be installed). Skipping this step — you can still style the network using existing data columns." → go to STEP 3.

If tool returns error (other) → Say: "Analysis failed: {error}. We'll skip this and proceed with layout." → go to STEP 3.

If success → Say: "Analysis complete! New columns added to your node table: {list of new columns, e.g., Degree, BetweennessCentrality, ClusteringCoefficient, etc.}. These are now available for data-driven styling."

═══════════════════════════════════════════════════════════════
PHASE 3: CHOOSE LAYOUT
═══════════════════════════════════════════════════════════════

STEP 3 — Pick a layout algorithm:

Call tool: get_layout_algorithms
Capture: $layouts (array of { name, displayName, description })

Say: "Now let's arrange the nodes. Here are the available layout algorithms:

{numbered list: displayName — description}

Which layout would you like to apply? (enter the number or name)"

Capture: $layout_name

Call tool: apply_layout with { "algorithm": $layout_name }

If tool returns error → Say: "Layout failed: {error}. Would you like to try a different one?" → return to layout selection.

If success → Say: "Layout applied! Your network is now arranged using {displayName}."

═══════════════════════════════════════════════════════════════
PHASE 4: DEFAULT STYLING
═══════════════════════════════════════════════════════════════

Follow the complete conversation script from the Default Styling prompt (spec 02-default-styling-prompt.md), starting from its STEP 1. Use the same question text, tool calls, and branching logic.

When the default styling sub-workflow is complete, continue to Phase 5.

═══════════════════════════════════════════════════════════════
PHASE 5: MAPPING STYLING
═══════════════════════════════════════════════════════════════

Follow the complete conversation script from the Mapping Styling prompt (spec 03-mapping-styling-prompt.md), starting from its STEP 1. Use the same question text, tool calls, and branching logic.

When the mapping styling sub-workflow is complete, continue to Phase 6.

═══════════════════════════════════════════════════════════════
PHASE 6: WRAP-UP
═══════════════════════════════════════════════════════════════

STEP 6 — Summary:

Say: "Your network is all set! Here's a summary of what we did:

- **Network**: {network_name}, {node_count} nodes, {edge_count} edges — source: {one of: 'NDEx ({network_id})', 'local file ({file name})', 'existing network'}
- **Analysis**: {directed/undirected} {if skipped: '(skipped)'} {if re-used existing: '(kept existing)'}
- **Layout**: {layout displayName}
- **Default styles changed**: {count} properties
- **Mappings created**: {count} mappings

You can re-run the styling wizard anytime using the 'default_styling' or 'mapping_styling' prompts. Happy exploring!"
```

---

## 3. Conversation Script

This section provides a structured reference of every step for implementation and testing. The system prompt above contains the LLM-facing instructions; this section is the developer-facing specification.

### Phase 0: Select or Create Network View

| Step | Agent Says | User Responds | Variables Captured | Tool Called | On Error |
|------|-----------|---------------|-------------------|-------------|----------|
| 0.1 | — | — | `$views` | `get_loaded_network_views` | — |
| 0.2 (empty) | — | — | — | — | Skip to Phase 1 |
| 0.2 (non-empty) | "Work with existing or load new?" | Choice number | `$network_choice` | — | — |
| 0.3 (existing, has view) | — | — | `$network_suid`, `$view_suid` | `set_current_network_view` | "No longer available" → re-list |
| 0.3 (existing, no view) | "Create a view?" | Yes / No | — | — | — |
| 0.4 (create view) | — | — | `$view_suid` | `create_network_view` | "Failed to create" → re-list |
| 0 → Phase 1 | User picks "Load a new network" | — | — | — | — |
| 0 → Phase 2 | Existing network selected | — | `$network_name`, `$node_count`, `$edge_count` | — | — |

### Phase 1: Load Network

Delegated to `04-load-network-prompt.md` conversation script.

### Phase 2: Analyze Network

| Step | Agent Says | User Responds | Variables Captured | Tool Called | On Error |
|------|-----------|---------------|-------------------|-------------|----------|
| 2.1 | "Is your network directed or undirected?" | 1 or 2 | `$directed` | — | — |
| 2.2 | — | — | `$new_columns` | `analyze_network` | "Not available" → skip; Other → skip |

### Phase 3: Choose Layout

| Step | Agent Says | User Responds | Variables Captured | Tool Called | On Error |
|------|-----------|---------------|-------------------|-------------|----------|
| 3.1 | — | — | `$layouts` | `get_layout_algorithms` | Present error, offer to skip |
| 3.2 | "Which layout?" | Layout name/number | `$layout_name` | `apply_layout` | "Layout failed" → retry |

### Phase 4: Default Styling

Delegated to `02-default-styling-prompt.md` conversation script.

### Phase 5: Mapping Styling

Delegated to `03-mapping-styling-prompt.md` conversation script.

### Phase 6: Wrap-Up

| Step | Agent Says | User Responds | Variables Captured | Tool Called |
|------|-----------|---------------|-------------------|-------------|
| 6 | Summary of all operations (includes network source: NDEx / local file / existing) | — | — | — |

---

## 4. MCP Tool Schemas

### 4.1 `load_cytoscape_network_view`

See `04-load-network-prompt.md §4.1` for the full tool schema and response examples.

### 4.2 `get_file_columns`

See `04-load-network-prompt.md §4.2` for the full tool schema and response examples.

### 4.2a `inspect_tabular_file`

See `04-load-network-prompt.md §4.2a` for the full tool schema and response examples.

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

---

## 5. Server-Side Implementation Notes

Phase 1 load network file implementation is documented in `04-load-network-prompt.md §5`.

### 5.3 `get_loaded_network_views`

```java
public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
    Set<CyNetwork> networks = networkManager.getNetworkSet();

    ArrayNode viewsArray = mapper.createArrayNode();
    for (CyNetwork network : networks) {
        // Only include subnetworks (skip root networks)
        if (!(network instanceof CySubNetwork)) continue;

        CySubNetwork subNet = (CySubNetwork) network;
        CyRootNetwork rootNet = subNet.getRootNetwork();

        String collectionName = rootNet.getRow(rootNet).get(CyNetwork.NAME, String.class);
        String networkName = network.getRow(network).get(CyNetwork.NAME, String.class);

        Collection<CyNetworkView> views = viewManager.getNetworkViews(network);
        CyNetworkView firstView = views.isEmpty() ? null : views.iterator().next();

        ObjectNode entry = mapper.createObjectNode();
        entry.put("collection_name", collectionName);
        entry.put("network_name", networkName);
        entry.put("network_suid", network.getSUID());
        if (firstView != null) {
            entry.put("view_suid", firstView.getSUID());
        } else {
            entry.putNull("view_suid");
        }
        entry.put("node_count", network.getNodeCount());
        entry.put("edge_count", network.getEdgeCount());
        viewsArray.add(entry);
    }

    ObjectNode result = mapper.createObjectNode();
    result.set("views", viewsArray);
    return success(mapper.writeValueAsString(result));
}
```

### 5.4 `set_current_network_view`

```java
public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
    long networkSuid = ((Number) request.arguments().get("network_suid")).longValue();
    long viewSuid = ((Number) request.arguments().get("view_suid")).longValue();

    CyNetwork network = networkManager.getNetwork(networkSuid);
    if (network == null) {
        return error("Network with SUID " + networkSuid + " not found.");
    }

    // Find the matching view
    Collection<CyNetworkView> views = viewManager.getNetworkViews(network);
    CyNetworkView targetView = null;
    for (CyNetworkView v : views) {
        if (v.getSUID() == viewSuid) {
            targetView = v;
            break;
        }
    }

    if (targetView == null) {
        return error("Network view not found. The view may have been closed.");
    }

    appManager.setCurrentNetwork(network);
    appManager.setCurrentNetworkView(targetView);

    String name = network.getRow(network).get(CyNetwork.NAME, String.class);
    return success(String.format(
        "{\"status\":\"success\",\"network_name\":\"%s\",\"node_count\":%d,\"edge_count\":%d}",
        name, network.getNodeCount(), network.getEdgeCount()
    ));
}
```

### 5.5 `create_network_view`

```java
public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
    long networkSuid = ((Number) request.arguments().get("network_suid")).longValue();

    CyNetwork network = networkManager.getNetwork(networkSuid);
    if (network == null) {
        return error("Network with SUID " + networkSuid + " not found.");
    }

    // Check if a view already exists
    Collection<CyNetworkView> existingViews = viewManager.getNetworkViews(network);
    if (!existingViews.isEmpty()) {
        CyNetworkView existingView = existingViews.iterator().next();
        appManager.setCurrentNetwork(network);
        appManager.setCurrentNetworkView(existingView);
        String name = network.getRow(network).get(CyNetwork.NAME, String.class);
        return success(String.format(
            "{\"status\":\"success\",\"network_suid\":%d,\"view_suid\":%d,\"network_name\":\"%s\",\"node_count\":%d,\"edge_count\":%d}",
            network.getSUID(), existingView.getSUID(), name, network.getNodeCount(), network.getEdgeCount()
        ));
    }

    // Create a new view
    CyNetworkView newView = networkViewFactory.createNetworkView(network);
    viewManager.addNetworkView(newView);

    appManager.setCurrentNetwork(network);
    appManager.setCurrentNetworkView(newView);

    String name = network.getRow(network).get(CyNetwork.NAME, String.class);
    return success(String.format(
        "{\"status\":\"success\",\"network_suid\":%d,\"view_suid\":%d,\"network_name\":\"%s\",\"node_count\":%d,\"edge_count\":%d}",
        network.getSUID(), newView.getSUID(), name, network.getNodeCount(), network.getEdgeCount()
    ));
}
```

### 5.6 `analyze_network`

```java
public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
    boolean directed = (boolean) request.arguments().get("directed");
    CyNetwork network = appManager.getCurrentNetwork();

    if (network == null) {
        return error("No network is currently loaded. Please load a network first.");
    }

    // NetworkAnalyzer is a separate app — its task factory may not be available
    // Look it up via command API: "analyzer analyze" command
    // Or via direct OSGi service lookup if the Analyzer app exposes a TaskFactory

    try {
        // Approach: Use Cytoscape command API
        // "analyzer analyze directed=true/false"
        Map<String, Object> args = Map.of("directed", directed);
        // Execute via CommandExecutorTaskFactory...

        // Capture columns added (compare table columns before/after)
        Set<String> columnsBefore = getColumnNames(network.getDefaultNodeTable());
        // ... execute analysis ...
        Set<String> columnsAfter = getColumnNames(network.getDefaultNodeTable());
        List<String> newColumns = columnsAfter.stream()
            .filter(c -> !columnsBefore.contains(c))
            .sorted()
            .collect(Collectors.toList());

        return success(buildAnalysisResult(newColumns, network));
    } catch (Exception e) {
        return error("Network analysis is not available: " + e.getMessage());
    }
}
```

### 5.7 `get_layout_algorithms`

```java
public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
    Collection<CyLayoutAlgorithm> algorithms = layoutManager.getAllLayouts();

    ArrayNode layoutsArray = mapper.createArrayNode();
    for (CyLayoutAlgorithm algo : algorithms) {
        ObjectNode entry = mapper.createObjectNode();
        entry.put("name", algo.getName());
        entry.put("displayName", algo.toString());  // or algo.getName() with human formatting
        // Description may not be available via API — use name as fallback
        layoutsArray.add(entry);
    }

    ObjectNode result = mapper.createObjectNode();
    result.set("layouts", layoutsArray);
    return success(mapper.writeValueAsString(result));
}
```

### 5.8 `apply_layout`

```java
public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
    String algorithmName = (String) request.arguments().get("algorithm");

    CyNetworkView view = appManager.getCurrentNetworkView();
    if (view == null) {
        return error("No network view is currently available.");
    }

    CyLayoutAlgorithm algorithm = layoutManager.getLayout(algorithmName);
    if (algorithm == null) {
        return error("Unknown layout algorithm: " + algorithmName);
    }

    // Apply with default parameters
    TaskIterator taskIterator = algorithm.createTaskIterator(
        view,
        algorithm.createLayoutContext(),
        CyLayoutAlgorithm.ALL_NODE_VIEWS,
        null  // no attribute for layout
    );

    syncTaskManager.execute(taskIterator);

    // Fit view to content after layout
    view.fitContent();
    view.updateView();

    return success(String.format(
        "{\"status\":\"success\",\"algorithm\":\"%s\",\"displayName\":\"%s\"}",
        algorithmName, algorithm.toString()
    ));
}
```

---

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

Phase 1 edge cases are documented in `04-load-network-prompt.md §6`.

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
