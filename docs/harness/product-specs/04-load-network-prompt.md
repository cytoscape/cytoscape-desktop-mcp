# MCP Prompt Spec: Load Network

> **Spec file**: `04-load-network-prompt.md`
> **Shared reference**: See `00-shared-reference.md` for SDK constants, enums, palettes, and patterns.

This prompt guides the user through loading a network into Cytoscape from NDEx (by UUID) or a local file (native network format or tabular data with column mapping). It can be invoked standalone, or embedded inline as Phase 1 of the Network Wizard (spec `01`).

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
  "name": "load_network",
  "title": "Load Network",
  "description": "Interactive prompt that guides you through loading a network into Cytoscape from NDEx (by UUID) or a local file (native network format or tabular data with column mapping). Creates a new network collection and view and sets it as the current network.",
  "arguments": []
}
```

### 1.2 prompts/get Response

```json
{
  "description": "Load Network",
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
Prompt loadNetworkPrompt = new Prompt(
    "load_network",
    "Load Network",
    List.of()  // no arguments — prompt is fully interactive
);

PromptSpecification loadNetworkSpec = new PromptSpecification(
    loadNetworkPrompt,
    (exchange, request) -> new GetPromptResult(
        "Load Network",
        List.of(new PromptMessage(Role.ASSISTANT, new TextContent(LOAD_NETWORK_SYSTEM_PROMPT)))
    )
);
```

---

## 2. System Prompt

The following is the complete system prompt text injected as the seed `PromptMessage`. It contains the full conversation script, tool-calling instructions, and branching logic for network loading.

```text
You are a Cytoscape Network Loading assistant. You will guide the user step-by-step through loading a network into Cytoscape from NDEx or a local file. You have access to MCP tools for each operation.

IMPORTANT RULES:
- Ask ONE question at a time. Wait for the user's answer before proceeding.
- Always confirm successful operations before moving to the next step.
- Present choices as numbered lists when there are multiple options.
- Use concise, scientist-friendly language. Avoid jargon about the underlying API.
- If a tool call fails, analyze the error response from the tool, it will indicate reason for failure and assistant should determine next step. The error response could be a validation error indicating to show message to user on prompt and retry the same question, or the error indicates a different action is needed such as skipping that question and informing the user why, or if can't continue due to serious error. Always strive to format any error response messages to user on prompt as well-formed sentence structure if the response doesn't provide it already.
- The user can say "skip" at any step to move on.

═══════════════════════════════════════════════════════════════
STEP 1 — Ask for network source:
═══════════════════════════════════════════════════════════════

Say: "Let's load a network into Cytoscape. Where is your network?

1. NDEx (Network Data Exchange) — load by UUID
2. Local file — load from your filesystem

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

If success → Say: "Network loaded from NDEx! Your network has {node_count} nodes and {edge_count} edges." → network loading is complete.

STEP 1a-File — Ask for file path:

Say: "Is this a raw tabular data file with delimeters or is it expresed as a network formatted file that captures source and target nodes and relationships(edges)?"

Ask: '1 - Tabular, delimited data', '2 - Network formatted data' 

Capture: $file_format_type as integer 1 or 2

Say: "Please provide the path to your network file on your local machine."

Capture: $file_path (the path provided by the user)

- If $file_format_type=2 (network) → go to STEP 1-LOAD
- If $file_format_type=1 (tabular) → go to STEP 1b (column mapping)

STEP 1b — Column mapping for tabular files:

Say: "Next step is to identify the delimiter, node, and edge columns from the tabular data file"

Mcp performs an inspection on $file_path, attempts to open it with poi sdk to determine if it is an excel formatted file. 

Capture: $is_excel // true = yes, false = no

if $is_excel=true, go to STep 1b1 
if $is_excel=false, go to Step 1b2

Step 1b1 - excel tabular config

Mcp reads file to get list of all sheets in the excel data file.

Capture: $sheets (array of excel sheet names present)

if $sheets is empty, tell user invalid file.

if $sheets has only one then:
    Capture: $excel_sheet = $sheets[0]
else:
    Need to ask the user to choose from multiple sheets avaialbe.
    Say: The following sheets were present: {numbered list of $sheets}
    Ask: "Which sheet should be used for source/target network data?"
    Capture: $excel_sheet = name of sheet for number user selected.
endif    

Go to Step 1b3

Step 1b2 - non excel tabular config
Mcp should parse out the extension of data file.
Capture: $detected_extension

Ask: "Choose which delimiter character is used for separation of columnar data:"

Say: {1 - comma, 2 - tab, 3 - space, 4 - Other} // list should denote highlight of the option as pre-selected based on the $detected_extension

If user chooses 4 - Other, then ask them to type in the character or the ascii code expressed as integral value.

Capture: $delimiter_char_code // this should be converted in all cases to ascii integer code.

go to Step 1b3

Step 1b3- Column mapping

Ask: "Does first row of data contain header of column names? If no, then default ordinal names will be created like 'Column 1', 'Column 2', etc."

Say: {"1 - Yes, 2 - No"}

Capture: $use_header_row // boolean for 1 - yes or 2 - no

Call tool: get_file_columns with { "file_path": $file_path, "delimiter_char_code": $delimiter_char_code, "use_header_row", $use_header_row , "excel_sheet" : $excel_sheet}

Capture: $columns (array of column header names)

If tool returns error → Say: "Can't read the file headers. Error: {error}. Please check the file path and format." → return to STEP 1a-File.

Say: "The following columns were detected in your file:
{numbered list of $columns}

Ask: "Which column contains the **source (from) node**? (enter the number or column name)"

Capture: $source_column

Ask: "Which column contains the **target (to) node**?"

Capture: $target_column

Ask: "Which column contains the **interaction/relationship type**? (enter the number for column or type 'skip' if there isn't one)"

Capture: $interaction_column (or null if skipped)

Ask: "Do you want to map properties for Nodes from the file columns at this time? You can always do this later as well. By default columns get mapped as edge attributes "
if user answers yes: 
  if $is_excel
    if $sheets has only one then:
      Capture: $node_attributes_sheet = $sheets[0] 
    else:
      Need to ask the user to choose from multiple sheets avaialbe.
      Say: "The following sheets were present: {numbered list of $sheets and a Skip option}"
      Ask: "Which sheet should be used for Node properties?"
      Capture: $node_attributes_sheet = name of sheet user chose or null if user chose Skip
    endif 
    Mcp uses poi sdk to get list of columns on $node_attributes_sheet:
    Capture: $node_attribs_columns (array of column names from poi)
  else
    Mcp sets the list of columns to all columns avaialble on current data file
    Capture: $node_attribs_columns = $columns 
  endif  

  Say: The following columns are present: {numbered list of $node_attribs_columns and Skip as an option}

  Ask: "Which column contains the key for node ID? (enter the number for column name or type 'Skip' to not import node attributes at this time)"
  Capture: $node_attributes_key_column (or null if skipped)
  if User chose 'Skip':
    go to STEP 1-LOAD 
  endif

  Ask: "Which columns do you want mapped as properties to the Source Node( {$source_column}). Enter number of each column separated by a comma. Leave blank for none."
  Capture: $node_attributes_source_columns (or null if skipped)

  Ask: "Which columns do you want mapped as properties to the Target Node( {$target_column}). Enter number of each column separated by a comma. Leave blank for none."
  Capture: $node_attributes_target_columns (or null if skipped)

  Say: "Any remaining columns not mapped for Node properties will be an edge property"
endif

go to STEP 1-LOAD.

STEP 1-LOAD — Load the network from file:

If user chose type=network format:
  Call tool: load_cytoscape_network_view with { "source": "network-file", "file_path": $file_path }

If user chose type=tabular format:
  Call tool: load_cytoscape_network_view with {
    "source": "tabular-file",
    "file_path": $file_path,
    "delimiter_char_code": $delimiter_char_code,  // null for Excel
    "use_header_row": $use_header_row,
    "excel_sheet": $excel_sheet,                  // null for non-Excel
    "source_column": $source_column,
    "target_column": $target_column,
    "interaction_column": $interaction_column,     // omit if null
    "node_attributes_sheet": $node_attributes_sheet,         // omit if null
    "node_attributes_key_column": $node_attributes_key_column,  // omit if null
    "node_attributes_source_columns" $node_attributes_source_columns // omit if empty
    "node_attributes_target_columns" $node_attributes_target_columns // omit if empty
  }
  ** note the load_cytoscape_network_view will check for presence of node_attributes_key_column, if present and node_attributes_sheet is null, it will load node properties based on columns from file otherwise if "node_attributes_sheet" is also non-null, it will use poi to load node properties from columns on that excel sheet. 
  
  it will use the app sdk to ensure the node table is updated from these attribute column parameters, whether that is accomplished directly by specifying them at load network sdk call or through a secondary call to load data table sdk call.
endif

If tool returns error → Say: "Failed to load the network: {error}. Would you like to try a different file?" → return to STEP 1a-File.

If success → Say: "Network loaded successfully! Your network has {node_count} nodes and {edge_count} edges." → network loading is complete.
```

---

## 3. Conversation Script

This section provides a structured reference of every step for implementation and testing. The system prompt above contains the LLM-facing instructions; this section is the developer-facing specification.

### Phase 1: Load Network

| Step | Agent Says | User Responds | Variables Captured | Tool Called | On Error |
|------|-----------|---------------|-------------------|-------------|----------|
| 1 | "Where is your network? (1) NDEx, (2) Local file" | 1 or 2 | `$source_type` | — | — |
| 1a-NDEx | "Provide the NDEx UUID" | UUID string | `$network_id` | `load_cytoscape_network_view` | Not found → retry; unreachable → retry or switch to file |
| 1a-File | "Is this tabular data or network formatted?" | 1 or 2 | `$file_format_type` | — | — |
| 1a-File.2 | "Provide the path to your network file" | File path string | `$file_path` | — | — |
| 1b | _(inspect file)_ | — | `$is_excel`, `$sheets`, `$detected_extension` | `inspect_tabular_file` | File unreadable → return to 1a-File |
| 1b1 (Excel) | "Which sheet for network data?" (auto-selects if single sheet) | Sheet number | `$excel_sheet` | — | — |
| 1b2 (non-Excel) | "Choose delimiter: comma, tab, space, other" | Choice | `$delimiter_char_code` | — | Invalid input → re-ask |
| 1b3 | "Does first row contain column headers?" | Yes or No | `$use_header_row` | — | — |
| 1b3.2 | _(read columns)_ | — | `$columns` | `get_file_columns` | "Can't read headers" → return to 1a-File |
| 1b3.3 | "Which column is the source node?" | Column name/number | `$source_column` | — | — |
| 1b3.4 | "Which column is the target node?" | Column name/number | `$target_column` | — | — |
| 1b3.5 | "Which column is the interaction type? (or skip)" | Column name/number or "skip" | `$interaction_column` | — | — |
| 1b3.6 | "Map node properties from file columns?" | Yes or No | — | — | — |
| 1b3.7 (Excel) | "Which sheet for node properties?" (auto-selects if single sheet) | Sheet number or "skip" | `$node_attributes_sheet` | — | — |
| 1b3.8 | _(get node attr columns: Excel via `get_file_columns`; non-Excel uses `$columns`)_ | — | `$node_attribs_columns` | `get_file_columns` (Excel) | — |
| 1b3.9 | "Which column is the node ID key? (or skip)" | Column name/number or "skip" | `$node_attributes_key_column` | — | — |
| 1b3.10 | "Which columns for source node properties?" | Column numbers (comma-sep) | `$node_attributes_source_columns` | — | — |
| 1b3.11 | "Which columns for target node properties?" | Column numbers (comma-sep) | `$node_attributes_target_columns` | — | — |
| 1-LOAD | — | — | `$node_count`, `$edge_count` | `load_cytoscape_network_view` | "Failed to load" → retry |

---

## 4. MCP Tool Schemas

### 4.1 `load_cytoscape_network_view`

```json
{
  "name": "load_cytoscape_network_view",
  "description": "Load a network into Cytoscape from NDEx (by UUID), a native network format file, or a tabular data file with column mapping. Creates a new network collection and view, and sets it as the current network.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "source": {
        "type": "string",
        "enum": ["ndex", "network-file", "tabular-file"],
        "description": "Network source: 'ndex' to load from NDEx by UUID, 'network-file' to load a native network format file, 'tabular-file' to load a delimited/Excel file with column mapping."
      },
      "network_id": {
        "type": "string",
        "description": "NDEx network UUID. Required when source='ndex'."
      },
      "file_path": {
        "type": "string",
        "description": "Absolute path to the network file. Required when source='network-file' or source='tabular-file'."
      },
      "source_column": {
        "type": "string",
        "description": "Column name for source (from) node. Required when source='tabular-file'."
      },
      "target_column": {
        "type": "string",
        "description": "Column name for target (to) node. Required when source='tabular-file'."
      },
      "interaction_column": {
        "type": "string",
        "description": "Column name for interaction/edge type. Optional; only applicable when source='tabular-file'."
      },
      "delimiter_char_code": {
        "type": "integer",
        "description": "ASCII code of the delimiter character (e.g., 44=comma, 9=tab, 124=pipe). Required when source='tabular-file' and file is non-Excel. Null/omit for Excel files."
      },
      "use_header_row": {
        "type": "boolean",
        "description": "If true, first row is treated as column headers. If false, ordinal names are generated (Column 1, Column 2, ...). Required when source='tabular-file'."
      },
      "excel_sheet": {
        "type": "string",
        "description": "Name of the Excel sheet containing network edge data (source/target columns). Required when source='tabular-file' and file is Excel. Null/omit for non-Excel."
      },
      "node_attributes_sheet": {
        "type": "string",
        "description": "Name of an Excel sheet containing node attributes to merge into the node table. Optional; only for Excel files when source='tabular-file'. Omit if not applicable."
      },
      "node_attributes_key_column": {
        "type": "string",
        "description": "Column name containing the node ID key for joining node attributes. When provided without node_attributes_sheet, node properties are loaded from the same file's columns. When provided with node_attributes_sheet, node properties are loaded from that Excel sheet. Omit if no node attribute mapping is needed."
      },
      "node_attributes_source_columns": {
        "type": "array",
        "items": { "type": "string" },
        "description": "Column names to map as properties to the source node. Optional; only applicable when source='tabular-file' and node_attributes_key_column is provided. Omit if empty."
      },
      "node_attributes_target_columns": {
        "type": "array",
        "items": { "type": "string" },
        "description": "Column names to map as properties to the target node. Optional; only applicable when source='tabular-file' and node_attributes_key_column is provided. Omit if empty."
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

**Success response (network file):**
```json
{
  "content": [{ "type": "text", "text": "{\"status\":\"success\",\"network_suid\":12345,\"node_count\":150,\"edge_count\":340,\"network_name\":\"my_network.sif\"}" }],
  "isError": false
}
```

**Success response (tabular):**
```json
{
  "content": [{ "type": "text", "text": "{\"status\":\"success\",\"network_suid\":12345,\"node_count\":150,\"edge_count\":340,\"network_name\":\"interactions.xlsx\",\"node_attributes_imported\":true}" }],
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
  "description": "Read column headers and sample rows from a tabular data file using explicit format parameters. The caller must first use inspect_tabular_file to determine Excel vs text format. Returns column names and a few sample rows. Read-only; does not import data.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "file_path": {
        "type": "string",
        "description": "Absolute path to the tabular file."
      },
      "delimiter_char_code": {
        "type": "integer",
        "description": "ASCII code of the delimiter character (e.g., 44=comma, 9=tab). Required for non-Excel text files. Ignored for Excel."
      },
      "use_header_row": {
        "type": "boolean",
        "description": "If true, first row is treated as column headers. If false, ordinal names are generated (Column 1, Column 2, ...). Default: true."
      },
      "excel_sheet": {
        "type": "string",
        "description": "Name of the Excel sheet to read columns from. Required for Excel files. Ignored for text files."
      }
    },
    "required": ["file_path", "use_header_row"]
  }
}
```

> **Note:** The agent may call this tool multiple times for Excel files — once for the network data sheet (Step 1b3) and optionally again for the node-attributes sheet (after Step 1b3.5, if user opts in to node attribute mapping) to enumerate its columns for the key-column prompt.

**Success response:**
```json
{
  "content": [{ "type": "text", "text": "{\"columns\":[\"Gene1\",\"Gene2\",\"Score\",\"InteractionType\"],\"sample_rows\":[[\"TP53\",\"MDM2\",\"0.95\",\"physical\"],[\"BRCA1\",\"RAD51\",\"0.87\",\"genetic\"]]}" }],
  "isError": false
}
```

### 4.2a `inspect_tabular_file`

```json
{
  "name": "inspect_tabular_file",
  "description": "Inspect a tabular data file to determine if it is Excel format and, if so, list all sheet names. Used before get_file_columns to route the agent to the correct sub-flow (Excel sheet selection vs delimiter selection). Read-only; does not import data.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "file_path": {
        "type": "string",
        "description": "Absolute path to the tabular data file."
      }
    },
    "required": ["file_path"]
  }
}
```

**Success response (Excel):**
```json
{
  "content": [{ "type": "text", "text": "{\"is_excel\":true,\"sheets\":[\"Interactions\",\"NodeAttributes\",\"Metadata\"]}" }],
  "isError": false
}
```

**Success response (non-Excel):**
```json
{
  "content": [{ "type": "text", "text": "{\"is_excel\":false,\"detected_extension\":\".csv\"}" }],
  "isError": false
}
```

**Error response:**
```json
{
  "content": [{ "type": "text", "text": "File not found: /path/to/file.xlsx" }],
  "isError": true
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
    } else if ("network-file".equals(source)) {
        return handleNetworkFileImport(request);
    } else if ("tabular-file".equals(source)) {
        return handleTabularImport(request);
    } else {
        return error("Invalid source: '" + source + "'. Must be 'ndex', 'network-file', or 'tabular-file'.");
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

#### 5.1.2 Network File Format Path (`source="network-file"`)

```java
private CallToolResult handleNetworkFileImport(CallToolRequest request) {
    String filePath = (String) request.arguments().get("file_path");
    if (filePath == null || filePath.isBlank()) {
        return error("file_path is required when source='network-file'.");
    }

    File file = new File(filePath);
    if (!file.exists() || !file.isFile()) {
        return error("File not found: " + filePath);
    }

    // Native format: use LoadNetworkFileTaskFactory
    // Handles .sif, .gml, .xgmml, .cx, .cx2, .graphml, .sbml, .owl, .biopax
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

#### 5.1.3 Tabular File Path (`source="tabular-file"`)

```java
private CallToolResult handleTabularImport(CallToolRequest request) {
    String filePath = (String) request.arguments().get("file_path");
    if (filePath == null || filePath.isBlank()) {
        return error("file_path is required when source='tabular-file'.");
    }

    File file = new File(filePath);
    if (!file.exists() || !file.isFile()) {
        return error("File not found: " + filePath);
    }

    String sourceCol = (String) request.arguments().get("source_column");
    String targetCol = (String) request.arguments().get("target_column");
    String interactionCol = (String) request.arguments().get("interaction_column");
    Integer delimiterCharCode = (Integer) request.arguments().get("delimiter_char_code");
    Boolean useHeaderRow = (Boolean) request.arguments().get("use_header_row");
    String excelSheet = (String) request.arguments().get("excel_sheet");

    if (sourceCol == null || targetCol == null) {
        return error("source_column and target_column are required when source='tabular-file'.");
    }
    if (useHeaderRow == null) {
        return error("use_header_row is required when source='tabular-file'.");
    }

    // Use Cytoscape Command API for tabular imports
    Map<String, Object> args = new HashMap<>();
    args.put("file", file.getAbsolutePath());
    args.put("firstRowAsColumnNames", useHeaderRow);
    args.put("indexColumnSourceInteraction", sourceCol);
    args.put("indexColumnTargetInteraction", targetCol);
    if (interactionCol != null) {
        args.put("indexColumnTypeInteraction", interactionCol);
    }
    if (delimiterCharCode != null) {
        args.put("delimiter", String.valueOf((char) delimiterCharCode.intValue()));
    }
    if (excelSheet != null) {
        args.put("excelSheet", excelSheet);
    }

    // Execute via CommandExecutorTaskFactory or TaskManager
    // ...

    CyNetwork network = appManager.getCurrentNetwork();

    // Secondary node attribute import (Excel or non-Excel)
    String nodeAttrSheet = (String) request.arguments().get("node_attributes_sheet");
    String nodeAttrKeyCol = (String) request.arguments().get("node_attributes_key_column");
    List<String> nodeAttrSourceCols = (List<String>) request.arguments().get("node_attributes_source_columns");
    List<String> nodeAttrTargetCols = (List<String>) request.arguments().get("node_attributes_target_columns");
    boolean nodeAttrsImported = false;
    if (nodeAttrKeyCol != null) {
        if (nodeAttrSheet != null) {
            // Excel: load node properties from the specified Excel sheet
            importNodeAttributes(file, nodeAttrSheet, nodeAttrKeyCol,
                    nodeAttrSourceCols, nodeAttrTargetCols, network);
        } else {
            // Non-Excel: load node properties from columns in the same file
            importNodeAttributesFromFile(file, nodeAttrKeyCol,
                    nodeAttrSourceCols, nodeAttrTargetCols, network);
        }
        nodeAttrsImported = true;
    }

    return success(buildNetworkResult(network, nodeAttrsImported));
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
    if (!file.exists() || !file.isFile()) {
        return error("File not found: " + filePath);
    }

    String excelSheet = (String) request.arguments().get("excel_sheet");
    Integer delimiterCharCode = (Integer) request.arguments().get("delimiter_char_code");
    Boolean useHeaderRow = (Boolean) request.arguments().getOrDefault("use_header_row", true);

    List<String> columns;
    List<List<String>> sampleRows;

    if (excelSheet != null) {
        // Excel path: excel_sheet presence indicates Excel format
        try (Workbook wb = WorkbookFactory.create(file)) {
            Sheet sheet = wb.getSheet(excelSheet);
            if (sheet == null) {
                return error("Sheet '" + excelSheet + "' not found in workbook.");
            }

            if (useHeaderRow) {
                Row header = sheet.getRow(0);
                columns = new ArrayList<>();
                for (Cell cell : header) {
                    columns.add(cell.getStringCellValue());
                }
                sampleRows = readSampleRows(sheet, 1, 3);
            } else {
                // Generate ordinal column names
                Row firstRow = sheet.getRow(0);
                int colCount = firstRow != null ? firstRow.getPhysicalNumberOfCells() : 0;
                columns = new ArrayList<>();
                for (int i = 1; i <= colCount; i++) {
                    columns.add("Column " + i);
                }
                // First row is data, include it in sample rows
                sampleRows = readSampleRows(sheet, 0, 3);
            }
        }
    } else {
        // Non-Excel text file: use delimiter_char_code from caller
        char delimiter = delimiterCharCode != null ? (char) delimiterCharCode.intValue() : ',';
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String firstLine = reader.readLine();
            String[] parts = firstLine.split(String.valueOf(delimiter));

            if (useHeaderRow) {
                columns = Arrays.asList(parts);
                sampleRows = readSampleRows(reader, delimiter, 3);
            } else {
                // Generate ordinal column names
                columns = new ArrayList<>();
                for (int i = 1; i <= parts.length; i++) {
                    columns.add("Column " + i);
                }
                // First line is data, include it as first sample row
                sampleRows = new ArrayList<>();
                sampleRows.add(Arrays.asList(parts));
                sampleRows.addAll(readSampleRows(reader, delimiter, 2));
            }
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

### 5.2a `inspect_tabular_file`

```java
public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
    String filePath = (String) request.arguments().get("file_path");
    File file = new File(filePath);
    if (!file.exists() || !file.isFile()) {
        return error("File not found: " + filePath);
    }

    // Try opening as Excel via POI
    try (Workbook wb = WorkbookFactory.create(file)) {
        List<String> sheets = new ArrayList<>();
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            sheets.add(wb.getSheetName(i));
        }
        ObjectNode result = mapper.createObjectNode();
        result.put("is_excel", true);
        result.set("sheets", mapper.valueToTree(sheets));
        return success(mapper.writeValueAsString(result));
    } catch (InvalidFormatException | IOException e) {
        // Not Excel — fall back to text file
        String ext = getExtension(filePath).toLowerCase();
        ObjectNode result = mapper.createObjectNode();
        result.put("is_excel", false);
        result.put("detected_extension", "." + ext);
        return success(mapper.writeValueAsString(result));
    }
}
```

---

## 6. Edge Cases and Error Handling

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

### 6.7a Excel File Detection Failure

- **Trigger**: `inspect_tabular_file` cannot open the file (corrupt or unreadable).
- **Tool behavior**: Returns `isError: true` with message.
- **Agent script**: "I couldn't read this file. It may be corrupted or in an unexpected format. Please check the file and try again."

### 6.7b Excel File with No Sheets

- **Trigger**: `inspect_tabular_file` returns `is_excel=true` but `sheets` is empty.
- **Agent script**: "This Excel file appears to have no sheets. Please check the file in Excel and try again."

### 6.7c Invalid Excel Sheet Selection

- **Trigger**: User enters a sheet number that is out of range.
- **Agent script**: "That sheet number is not in the list. Please choose from the sheets shown above."

### 6.7d Node Attributes Key Column Mismatch

- **Trigger**: `load_cytoscape_network_view` secondary import (Excel sheet or same-file columns) finds 0 matching node IDs between the key column and the network.
- **Tool behavior**: Returns success for the primary network load but includes a warning: `"node_attributes_imported": false, "warning": "No matching node IDs found"`.
- **Agent script**: "The network was loaded, but I couldn't match any node attributes — the key column values didn't match the node IDs in the network. You can try importing attributes manually via File -> Import -> Table from File."

### 6.7e Invalid Delimiter Input

- **Trigger**: User selects "Other" delimiter but provides an invalid ASCII code (e.g., negative, non-printable, or out of range).
- **Agent script**: "That doesn't appear to be a valid delimiter. Please enter a single character (like |) or its ASCII code as a number (e.g., 124 for pipe)."

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

### 6.15 Tabular File with No Header Row

- **Trigger**: User selects "No" for header row in Step 1b3.
- **Tool behavior**: `get_file_columns` generates ordinal column names ("Column 1", "Column 2", etc.) and includes the first row in sample data.
- **Agent script**: Present the ordinal column names and sample rows. "Since there's no header row, I've assigned ordinal names. Please select which column number contains the source and target nodes."

### 6.16 Relative File Paths

- **Trigger**: User provides a relative path like `./data/network.sif`.
- **Tool behavior**: The tool should resolve relative to the Cytoscape working directory, or return an error requesting an absolute path.
- **Agent script**: If error: "Please provide the full (absolute) path to the file, for example: /Users/you/data/network.sif"
