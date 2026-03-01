# MCP Prompt Spec: Network Setup and Styling Wizard

> **Spec file**: `01-network-wizard-prompt.md`
> **Shared reference**: See `00-shared-reference.md` for SDK constants, enums, palettes, and patterns.

This is the primary entry-point prompt. It orchestrates the full workflow: select an existing network or load a new one (from NDEx or local file), analyze it, apply a layout, then style it (defaults + mappings). The default-styling and mapping-styling sub-workflows are embedded inline from specs `02` and `03`.

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
  "title": "Network Setup and Styling Wizard",
  "description": "Interactive wizard that guides you through selecting an existing network or loading a new one (from NDEx or local file) into Cytoscape, running network analysis, choosing a layout, and styling nodes and edges with defaults and data-driven mappings.",
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

STEP 1 — Ask for network source:

Say: "Let's load a network into Cytoscape. Where is your network?

1. NDEx (Network Data Exchange) — load by UUID
2. Local file — load from your filesystem

Supported file formats: SIF, GML, XGMML, CX, CX2, GraphML, SBML, BioPAX, CSV, TSV, Excel (.xlsx/.xls)"

Capture: $source_type ("ndex" or "file")

If user picks NDEx → go to STEP 1a-NDEx.
If user picks Local file → go to STEP 1a-File.

STEP 1a-NDEx — NDEx network loading:

Say: "Please provide the NDEx network UUID (you can find this in the NDEx URL or network details)."

Capture: $network_id

Call tool: load_cytoscape_network_view with { "source": "ndex", "network_id": $network_id }

If tool returns error containing "not found" → Say: "That network was not found on NDEx. Please verify the UUID and try again." → return to STEP 1a-NDEx.

If tool returns error containing "unreachable" or "connection" → Say: "I couldn't reach the NDEx server. Would you like to:
1. Try again
2. Switch to loading a local file instead"
Capture user choice and branch accordingly.

If tool returns error (other) → Say: "Failed to load from NDEx: {error}. Would you like to try a different UUID or switch to a local file?" → return to STEP 1.

If success → Say: "Network loaded from NDEx! Your network has {node_count} nodes and {edge_count} edges." → go to PHASE 2.

STEP 1a-File — Ask for file path:

Say: "Please provide the path to your network file on your local machine."

Capture: $file_path (the path provided by the user)

Determine format from file extension:
- If extension is .sif, .gml, .xgmml, .cx, .cx2, .graphml, .sbml, .owl, .biopax → go to STEP 1-LOAD
- If extension is .csv, .tsv, .txt, .xls, .xlsx → go to STEP 1b (column mapping)
- If extension is unrecognized → Say: "I don't recognize that file extension. Let me try loading it — Cytoscape may auto-detect the format." → go to STEP 1-LOAD

STEP 1b — Column mapping for tabular files:

Call tool: get_file_columns with { "file_path": $file_path }
Capture: $columns (array of column header names)

If tool returns error → Say: "I couldn't read the file headers. Error: {error}. Please check the file path and format." → return to STEP 1a-File.

Say: "I found the following columns in your file:
{numbered list of $columns}

Which column contains the **source (from) node**? (enter the number or column name)"

Capture: $source_column

Say: "Which column contains the **target (to) node**?"

Capture: $target_column

Say: "Which column contains the **interaction/relationship type**? (enter the number/name, or type 'skip' if there isn't one)"

Capture: $interaction_column (or null if skipped)

STEP 1-LOAD — Load the network from file:

For native formats:
  Call tool: load_cytoscape_network_view with { "source": "file", "file_path": $file_path }

For tabular formats:
  Call tool: load_cytoscape_network_view with {
    "source": "file",
    "file_path": $file_path,
    "source_column": $source_column,
    "target_column": $target_column,
    "interaction_column": $interaction_column   // omit if null
  }

If tool returns error → Say: "Failed to load the network: {error}. Would you like to try a different file?" → return to STEP 1a-File.

If success → Say: "Network loaded successfully! Your network has {node_count} nodes and {edge_count} edges."

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

| Step | Agent Says | User Responds | Variables Captured | Tool Called | On Error |
|------|-----------|---------------|-------------------|-------------|----------|
| 1 | "Where is your network? (1) NDEx, (2) Local file" | 1 or 2 | `$source_type` | — | — |
| 1a-NDEx | "Provide the NDEx UUID" | UUID string | `$network_id` | `load_cytoscape_network_view` | Not found → retry; unreachable → retry or switch to file |
| 1a-File | "Provide the path to your network file" | File path string | `$file_path` | — | — |
| 1a-File (branch) | — | — | `$format_type` (native or tabular) | — | Unrecognized ext: try native load |
| 1b.1 | — | — | `$columns` | `get_file_columns` | "Couldn't read headers" → retry |
| 1b.2 | "Which column is the source node?" | Column name/number | `$source_column` | — | — |
| 1b.3 | "Which column is the target node?" | Column name/number | `$target_column` | — | — |
| 1b.4 | "Which column is the interaction type?" | Column name/number or "skip" | `$interaction_column` | — | — |
| 1-LOAD | — | — | `$node_count`, `$edge_count` | `load_cytoscape_network_view` | "Failed to load" → retry |

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

```json
{
  "name": "load_cytoscape_network_view",
  "description": "Load a network into Cytoscape from NDEx (by UUID) or from a local file. Creates a new network collection and view, and sets it as the current network. For local tabular files (CSV, TSV, Excel), source/target column mapping is required.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "source": {
        "type": "string",
        "enum": ["ndex", "file"],
        "description": "Network source: 'ndex' to load from NDEx by UUID, 'file' to load from a local file path."
      },
      "network_id": {
        "type": "string",
        "description": "NDEx network UUID. Required when source='ndex'."
      },
      "file_path": {
        "type": "string",
        "description": "Absolute path to the network file on the local filesystem. Required when source='file'."
      },
      "source_column": {
        "type": "string",
        "description": "Column name for source (from) node. Required for tabular file formats (CSV, TSV, Excel)."
      },
      "target_column": {
        "type": "string",
        "description": "Column name for target (to) node. Required for tabular file formats (CSV, TSV, Excel)."
      },
      "interaction_column": {
        "type": "string",
        "description": "Column name for interaction/edge type. Optional; omit if not applicable."
      }
    },
    "required": ["source"]
  }
}
```

**Success response (NDEx):**
```json
{
  "content": [{ "type": "text", "text": "{\"status\":\"success\",\"network_suid\":12345,\"node_count\":150,\"edge_count\":340,\"network_name\":\"My NDEx Network\"}" }],
  "isError": false
}
```

**Success response (file):**
```json
{
  "content": [{ "type": "text", "text": "{\"status\":\"success\",\"network_suid\":12345,\"node_count\":150,\"edge_count\":340,\"network_name\":\"my_network.sif\"}" }],
  "isError": false
}
```

**Error response:**
```json
{
  "content": [{ "type": "text", "text": "Failed to load network: File not found at /path/to/file.sif" }],
  "isError": true
}
```

### 4.2 `get_file_columns`

```json
{
  "name": "get_file_columns",
  "description": "Read the column headers from a tabular file (CSV, TSV, or Excel) without importing the full data. Returns the column names and a few sample rows for context.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "file_path": {
        "type": "string",
        "description": "Absolute path to the tabular file."
      }
    },
    "required": ["file_path"]
  }
}
```

**Success response:**
```json
{
  "content": [{ "type": "text", "text": "{\"columns\":[\"Gene1\",\"Gene2\",\"Score\",\"InteractionType\"],\"sample_rows\":[[\"TP53\",\"MDM2\",\"0.95\",\"physical\"],[\"BRCA1\",\"RAD51\",\"0.87\",\"genetic\"]]}" }],
  "isError": false
}
```

### 4.3 `get_loaded_network_views`

```json
{
  "name": "get_loaded_network_views",
  "description": "Enumerate all network collections currently loaded in Cytoscape with their views, node counts, and edge counts. Read-only; does not modify state.",
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
  "description": "Set the specified network and view as the current (active) network and view in Cytoscape. Both network_suid and view_suid are required.",
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
  "description": "Create a visual view for a network that currently has no view. Sets the new view and its network as the current network and view.",
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
  "description": "Run NetworkAnalyzer on the current network to compute topological statistics. Adds columns like Degree, BetweennessCentrality, ClusteringCoefficient, etc. to the node and edge tables.",
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
  "description": "List all available layout algorithms in Cytoscape with their names and descriptions.",
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
  "description": "Apply a layout algorithm to the current network view using default parameters.",
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

### 5.1 `load_cytoscape_network_view` — Unified Loader

This tool dispatches on the `source` field. The NDEx path reuses the existing `LoadNetworkViewTool` logic. The file path uses `LoadNetworkFileTaskFactory` for native formats and the Cytoscape command API for tabular formats.

```java
public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
    String source = (String) request.arguments().get("source");

    if ("ndex".equals(source)) {
        return handleNdexImport(request);
    } else if ("file".equals(source)) {
        return handleFileImport(request);
    } else {
        return error("Invalid source: '" + source + "'. Must be 'ndex' or 'file'.");
    }
}
```

#### 5.1.1 NDEx Path

```java
private CallToolResult handleNdexImport(CallToolRequest request) {
    String networkId = (String) request.arguments().get("network_id");
    if (networkId == null || networkId.isBlank()) {
        return error("network_id is required when source='ndex'.");
    }

    // Reuse existing LoadNetworkViewTool logic:
    // 1. Fetch CX2 from NDEx via HTTP
    // 2. Create network via CyNetworkReaderManager (CX2 reader)
    // 3. Create view, set as current
    // ... (same as existing LoadNetworkViewTool implementation)

    CyNetwork network = appManager.getCurrentNetwork();
    return success(buildNetworkResult(network));
}
```

#### 5.1.2 File Path — Native Formats

```java
private CallToolResult handleFileImport(CallToolRequest request) {
    String filePath = (String) request.arguments().get("file_path");
    if (filePath == null || filePath.isBlank()) {
        return error("file_path is required when source='file'.");
    }

    File file = new File(filePath);
    if (!file.exists() || !file.isFile()) {
        return error("File not found: " + filePath);
    }

    // Determine if tabular
    String ext = getExtension(filePath).toLowerCase();
    boolean isTabular = Set.of("csv", "tsv", "txt", "xls", "xlsx").contains(ext);

    if (isTabular) {
        return handleTabularImport(request, file);
    }

    // Native format: use LoadNetworkFileTaskFactory
    TaskIterator taskIterator = loadNetworkFileTaskFactory.createTaskIterator(file);
    CountDownLatch latch = new CountDownLatch(1);

    TaskObserver observer = new TaskObserver() {
        CyNetwork loadedNetwork;

        @Override
        public void taskFinished(ObservableTask task) {
            loadedNetwork = task.getResults(CyNetwork.class);
            latch.countDown();
        }

        @Override
        public void allFinished(FinishStatus status) {
            latch.countDown();
        }
    };

    syncTaskManager.execute(taskIterator, observer);

    if (!latch.await(120, TimeUnit.SECONDS)) {
        return error("Network loading timed out after 120 seconds.");
    }

    CyNetwork network = appManager.getCurrentNetwork();
    return success(buildNetworkResult(network));
}
```

#### 5.1.3 File Path — Tabular Formats

```java
private CallToolResult handleTabularImport(CallToolRequest request, File file) {
    String sourceCol = (String) request.arguments().get("source_column");
    String targetCol = (String) request.arguments().get("target_column");
    String interactionCol = (String) request.arguments().get("interaction_column");

    if (sourceCol == null || targetCol == null) {
        return error("source_column and target_column are required for tabular files.");
    }

    // Use Cytoscape Command API for tabular imports
    Map<String, Object> args = new HashMap<>();
    args.put("file", file.getAbsolutePath());
    args.put("firstRowAsColumnNames", true);
    args.put("indexColumnSourceInteraction", sourceCol);
    args.put("indexColumnTargetInteraction", targetCol);
    if (interactionCol != null) {
        args.put("indexColumnTypeInteraction", interactionCol);
    }

    // Execute via CommandExecutorTaskFactory or TaskManager
    // ...

    CyNetwork network = appManager.getCurrentNetwork();
    return success(buildNetworkResult(network));
}

// Shared helper
private String buildNetworkResult(CyNetwork network) {
    int nodeCount = network.getNodeCount();
    int edgeCount = network.getEdgeCount();
    String name = network.getRow(network).get(CyNetwork.NAME, String.class);
    return String.format(
        "{\"status\":\"success\",\"network_suid\":%d,\"node_count\":%d,\"edge_count\":%d,\"network_name\":\"%s\"}",
        network.getSUID(), nodeCount, edgeCount, name
    );
}
```

### 5.2 `get_file_columns`

```java
public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
    String filePath = (String) request.arguments().get("file_path");
    File file = new File(filePath);
    String ext = getExtension(filePath).toLowerCase();

    List<String> columns;
    List<List<String>> sampleRows;

    if (Set.of("xls", "xlsx").contains(ext)) {
        // Use Apache POI to read header row
        try (Workbook wb = WorkbookFactory.create(file)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            columns = new ArrayList<>();
            for (Cell cell : header) {
                columns.add(cell.getStringCellValue());
            }
            // Read 2-3 sample rows
            sampleRows = readSampleRows(sheet, 1, 3);
        }
    } else {
        // CSV/TSV: detect delimiter from extension
        char delimiter = ext.equals("tsv") ? '\t' : ',';
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String headerLine = reader.readLine();
            columns = Arrays.asList(headerLine.split(String.valueOf(delimiter)));
            // Read 2-3 sample rows
            sampleRows = readSampleRows(reader, delimiter, 3);
        }
    }

    // Return as JSON
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode result = mapper.createObjectNode();
    result.set("columns", mapper.valueToTree(columns));
    result.set("sample_rows", mapper.valueToTree(sampleRows));
    return success(mapper.writeValueAsString(result));
}
```

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

### Phase 1 Edge Cases

### 6.5 File Not Found

- **Trigger**: User provides a file path that doesn't exist.
- **Tool behavior**: `load_cytoscape_network_view` returns `isError: true` with message.
- **Agent script**: "I couldn't find the file at that path. Please double-check and provide the correct path."

### 6.6 Unsupported File Format

- **Trigger**: File exists but Cytoscape cannot parse it.
- **Tool behavior**: `load_cytoscape_network_view` returns error from `LoadNetworkFileTaskFactory`.
- **Agent script**: "Cytoscape couldn't read this file. It may be in an unsupported format or corrupted. Supported formats include: SIF, GML, XGMML, CX, CX2, GraphML, SBML, BioPAX, CSV, TSV, Excel."

### 6.7 Tabular File Missing Required Columns

- **Trigger**: User selects source/target columns that don't exist in the file.
- **Tool behavior**: `load_cytoscape_network_view` returns error.
- **Agent script**: "The column '{name}' was not found in the file. Available columns are: {list}. Please try again."

### 6.8 NDEx Network Not Found

- **Trigger**: User provides an NDEx UUID that doesn't match any public network.
- **Tool behavior**: `load_cytoscape_network_view` returns `isError: true` with "not found" message.
- **Agent script**: "That network was not found on NDEx. Please verify the UUID and try again."
- **Flow**: Return to STEP 1a-NDEx.

### 6.9 NDEx Server Unreachable

- **Trigger**: NDEx server is down or network connection fails.
- **Tool behavior**: `load_cytoscape_network_view` returns `isError: true` with "unreachable" or connection error.
- **Agent script**: "I couldn't reach the NDEx server. Would you like to try again, or switch to loading a local file instead?"
- **Flow**: If retry → return to STEP 1a-NDEx. If switch → go to STEP 1a-File.

### 6.10 Empty Network

- **Trigger**: Loaded network (from file or NDEx) has 0 nodes.
- **Tool behavior**: `load_cytoscape_network_view` succeeds but `node_count` = 0.
- **Agent script**: "The network was loaded but contains 0 nodes. This might mean the file is empty or the format wasn't detected correctly. Would you like to try a different source?"

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

### 6.15 Tabular File with No Header Row

- **Trigger**: `get_file_columns` returns columns like "Column 1", "Column 2" (auto-generated).
- **Agent script**: Present the auto-generated names and note: "These appear to be auto-generated column names. If your file doesn't have a header row, the first row of data may have been used as headers. Please verify."

### 6.16 Relative File Paths

- **Trigger**: User provides a relative path like `./data/network.sif`.
- **Tool behavior**: The tool should resolve relative to the Cytoscape working directory, or return an error requesting an absolute path.
- **Agent script**: If error: "Please provide the full (absolute) path to the file, for example: /Users/you/data/network.sif"
