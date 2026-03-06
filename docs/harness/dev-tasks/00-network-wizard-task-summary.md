# Network Wizard — DFS Task Breakdown

## Context

The product specs (01 through 04, plus 00 shared reference) define a **Network Setup and Styling Wizard** delivered as MCP prompts and tools. The wizard orchestrates 6 phases: select/create network view (Phase 0), load network (Phase 1, delegated to 04 spec), analyze (Phase 2), layout (Phase 3), default styling (Phase 4, delegated to 02 spec), mapping styling (Phase 5, delegated to 03 spec), and wrap-up (Phase 6).

First review these docs to understand the network wizard feature spec, they are all located in `docs/harness/product-specs/` and keep in mind the [app developer cookbook](https://wikiold.cytoscape.org/Cytoscape_3/AppDeveloper/Cytoscape_3_App_Cookbook), it will be a good referecne when you are trying to understand how to accomplish any app ui sdk concerns. 

This document captures the bottoms-up DFS traversal of the spec hierarchy (01 → phases → sub-steps → leaf tools, with cross-references into 02 and 03 specs) and produces an ordered list of implementation tasks.

---

## DFS Traversal Summary

```
01-network-wizard-prompt
├── Phase 0: Select/Create Network View
│   ├── get_loaded_network_views tool
│   ├── set_current_network_view tool
│   └── create_network_view tool
├── Phase 1: Load Network → delegates to 04-load-network-prompt
│   ├── load_cytoscape_network_view (refactor existing, add source param)
│   │   ├── NDEx path (already exists — adapt to new schema)
│   │   ├── Native file format path (new — LoadNetworkFileTaskFactory)
│   │   └── Tabular file path (new — CSV/TSV/Excel with column mapping)
│   ├── inspect_tabular_file tool (detect Excel vs text, list Excel sheets)
│   └── get_file_columns tool (read headers from tabular files)
├── Phase 2: Analyze Network
│   └── analyze_network tool
├── Phase 3: Choose Layout
│   ├── get_layout_algorithms tool
│   └── apply_layout tool
├── Phase 4: Default Styling → delegates to 02-default-styling-prompt
│   ├── get_visual_style_defaults tool
│   │   └── requires: VisualPropertyService (injected instance)
│   └── set_visual_default tool
│       └── requires: VisualPropertyService
├── Phase 5: Mapping Styling → delegates to 03-mapping-styling-prompt
│   ├── get_mappable_properties tool
│   │   └── requires: VisualPropertyService
│   ├── get_compatible_columns tool
│   ├── get_column_range tool
│   ├── get_column_distinct_values tool
│   ├── create_continuous_mapping tool
│   │   └── requires: VisualPropertyService (parseValue)
│   ├── create_discrete_mapping tool
│   │   └── requires: VisualPropertyService (parseValue)
│   ├── create_passthrough_mapping tool
│   └── create_discrete_mapping_generated tool
└── Phase 6: Wrap-up (prompt text only)
    └── network_wizard MCP prompt registration
        ├── requires: default_styling MCP prompt
        └── requires: mapping_styling MCP prompt
```

---

## Ordered Task List

Each task is an independent, committable unit with unit tests. Tasks are ordered by dependency — task 1 has no dependencies; later tasks build on earlier ones. Very important aspect to remember - during task you are allowed to make all file edits and at end of each task pause after all file edits are done and ask the user to review and when they approve then ask user to approve a git stage command for the files changed in the ask and then ask for user to approve a git commit command with a succint/short message of "#CYTOSCAPE-13162: Network Wizard  Task: <task num> - <task title>"

**Live Agent Test Milestones**: Four milestone gates (M1–M4) are inserted at phase boundaries. Each milestone requires deploying the JAR into live Cytoscape and running an agent test script via MCP. A milestone **MUST** pass before any subsequent dev task begins — this catches OSGi wiring failures, EDT deadlocks, service lookup misses, and classloading errors that unit tests cannot surface.

| # | Task Name | Scope | Spec Source |
|---|-----------|-------|-------------|
| 1 | **Add build dependencies for styling, layout, and file loading** | Add `vizmap-api`, `presentation-api`, `layout-api` to `build.gradle` as `compileOnly` (and `testImplementation`). Add Apache POI (`poi` + `poi-ooxml`) as `embed` dependency for Excel file reading. Add corresponding OSGi service lookups in `CyActivator` for: `VisualMappingManager`, `RenderingEngineManager`, `VisualMappingFunctionFactory` (×3: continuous, discrete, passthrough), `CyLayoutAlgorithmManager`, `CyNetworkViewFactory`, `LoadNetworkFileTaskFactory`. Pass these services through to where tools will be constructed. No new tool classes yet — just the wiring foundation. | 00 §8, 00 §7 |
| 2 | **Create VisualPropertyService (instance-based service)** | Implement `VisualPropertyService` as a non-final, instance-based service class (not a static utility) following the same constructor-injection pattern used by `LoadNetworkViewTool`. All methods are instance methods: `findPropertyById(lexicon, id)`, `isSupported(vp)`, `getTypeName(range)`, `getContinuousSubType(vp)`, `formatValue(object)` (Paint→hex, Font→family-style-size, enums→displayName, others→toString), `parseValue(vp, rawValue)` (hex→Color, string→NodeShape/ArrowShape/LineType/Font, number→Double/Integer, string→String, boolean→Boolean), `parseNodeShape`, `parseArrowShape`, `parseLineType`. Instantiated once in `CyActivator.startMcpServer()` and injected into downstream tool constructors, enabling mockability in tool tests. Unit tests for each type conversion round-trip. | 00 §1-2, 00 §6, 02 §5.1-5.2, 03 §5.1 |
| 3 | **Implement `get_loaded_network_views` tool** | `TOOL_TITLE = "List Cytoscape Desktop Networks"`. New tool class that enumerates all `CySubNetwork` instances from `CyNetworkManager`, finds their views from `CyNetworkViewManager`, and returns JSON array with collection_name, network_name, network_suid, view_suid (nullable), node_count, edge_count. Register in `CyActivator`. Use `McpSchema.InputSchema` builder for `INPUT_SCHEMA`, a private static inner Jackson-annotated record for the response model and `OUTPUT_SCHEMA` (via `McpSchema.toSchemaJson()`), and return `CallToolResult.builder().structuredContent(result).build()`. Tests: mock manager with multiple networks (with/without views), verify JSON structure. | 01 §4.3, 01 §5.3 |
| 4 | **Implement `set_current_network_view` tool** | `TOOL_TITLE = "Set Cytoscape Desktop Active Network"`. New tool class that takes network_suid + view_suid, validates both exist, calls `appManager.setCurrentNetwork()` + `setCurrentNetworkView()`. Returns network name/counts on success. Use `McpSchema.InputSchema` builder for `INPUT_SCHEMA`, a private static inner Jackson-annotated record for the response model and `OUTPUT_SCHEMA` (via `McpSchema.toSchemaJson()`), and return `CallToolResult.builder().structuredContent(result).build()`. Tests: success path, network not found, view not found. | 01 §4.4, 01 §5.4 |
| 5 | **Implement `create_network_view` tool** | `TOOL_TITLE = "Create Cytoscape Desktop Network View"`. New tool class that takes network_suid, checks if view already exists (returns existing if so), otherwise creates via `CyNetworkViewFactory.createNetworkView()`, registers via `viewManager.addNetworkView()`, and sets as current. Use `McpSchema.InputSchema` builder for `INPUT_SCHEMA`, a private static inner Jackson-annotated record for the response model and `OUTPUT_SCHEMA` (via `McpSchema.toSchemaJson()`), and return `CallToolResult.builder().structuredContent(result).build()`. Tests: create new view, return existing view, network not found. | 01 §4.5, 01 §5.5 |

### 🚩 MILESTONE 1 — "Bundle Starts + Phase 0 Tools Live" (after Task 5)

> **Gate rule**: This milestone MUST pass before any Task 6+ work begins.

**Prerequisite tasks**: Tasks 1–5 complete, all unit tests passing.

**Deploy & connect**:
1. `make build && make install` — copy JAR to Cytoscape apps/
2. Restart Cytoscape Desktop (or use Apps → App Manager → Reinstall)
3. Verify MCP status button turns green in bottom toolbar
4. Connect agent to `http://localhost:9998/mcp`

**Agent test script**:
1. Ask agent: *"List all loaded networks"* → agent calls `get_loaded_network_views` → returns JSON array (may be empty if no session networks)
2. Load a network manually in Cytoscape (File → Import → Network from NDEx, pick any small network)
3. Ask agent: *"List all loaded networks again"* → should now return 1+ entry with `collection_name`, `network_name`, `network_suid`, `view_suid`, `node_count`, `edge_count`
4. Ask agent: *"Set the current network view to [suid from step 3]"* → agent calls `set_current_network_view` → returns success with network name and counts
5. If a network without a view exists, ask: *"Create a view for network [suid]"* → agent calls `create_network_view` → Cytoscape shows the new view panel

**Pass criteria**:
- Bundle resolves and starts (green MCP button)
- All 4 tools appear in `tools/list` response (`load_cytoscape_network_view` + 3 new)
- `get_loaded_network_views` returns correctly structured JSON
- `set_current_network_view` switches the active panel in Cytoscape
- `create_network_view` produces a visible network view in Cytoscape

**Runtime risks caught**: OSGi service lookup failures for new deps (`vizmap-api`, `presentation-api`, `layout-api`), `CyNetworkViewFactory` wiring, `CyApplicationManager` interaction.

---

| 6 | **Refactor `load_cytoscape_network_view` — add `source` dispatch and NDEx adapter** | `TOOL_TITLE = "Load Cytoscape Desktop Network"`. Modify `LoadNetworkViewTool` input schema: add `source` (enum: ndex/network-file/tabular-file), rename `network-id` to `network_id`, add `file_path`/`source_column`/`target_column`/`interaction_column`/`node_attributes_source_columns`/`node_attributes_target_columns` fields. Add `source` dispatch in handler. Adapt existing NDEx loading to the `source=ndex` branch (logic stays same, just gated on source). Update tool description. Return JSON response `{status, network_suid, node_count, edge_count, network_name}` instead of plain text. Use `McpSchema.InputSchema` builder for `INPUT_SCHEMA`, a private static inner Jackson-annotated record for the response model and `OUTPUT_SCHEMA` (via `McpSchema.toSchemaJson()`), and return `CallToolResult.builder().structuredContent(result).build()`. Update existing tests for new schema. | 04 §4.1, 04 §5.1, 04 §5.1.1 |
| 7 | **`load_cytoscape_network_view` — network file format support** | Add dispatch to `handleFileImport()` when `source=network-file` — the agent sets this based on the user's explicit STEP 1a choice of "Network formatted data"; no extension-based auto-detection or unrecognized-extension fallback is needed. Loads via `LoadNetworkFileTaskFactory` for formats Cytoscape parses natively (.sif, .gml, .xgmml, .cx, .cx2, .graphml, .sbml, .owl, .biopax). File existence validation, timeout handling (120s), error responses. Tests: mock `LoadNetworkFileTaskFactory`, verify file loading flow, file-not-found error. | 04 §5.1.2 |
| 7b | **Implement `inspect_tabular_file` tool** | `TOOL_TITLE = "Inspect Cytoscape Desktop Import File"`. New tool class that inspects a tabular data file to determine if it is Excel format and, if so, enumerate its sheets. This tool bridges the gap between the user selecting "Tabular data" (STEP 1a) and the agent knowing which sub-flow to follow (Step 1b1 Excel vs Step 1b2 non-Excel). Input schema: `{ "file_path": string }`. Behavior: attempts to open the file with Apache POI (`WorkbookFactory.create(file)`) — if POI succeeds, it is Excel: iterate all sheets via `workbook.getNumberOfSheets()` / `workbook.getSheetName(i)` and return them. If POI throws `InvalidFormatException` or `IOException`, treat as non-Excel: parse the file extension from the path. Returns JSON: `{ "is_excel": boolean, "sheets": [string] (present only when is_excel=true), "detected_extension": string (present only when is_excel=false, e.g. ".csv", ".tsv", ".txt") }`. The agent uses `is_excel` to route to Step 1b1 (Excel sheet selection) or Step 1b2 (delimiter selection), and uses `sheets` to present the sheet list without a second round-trip. File-not-found and unreadable-file errors return `isError=true` with a human-readable message. Use `McpSchema.InputSchema` builder for `INPUT_SCHEMA`, a private static inner Jackson-annotated record for the response model and `OUTPUT_SCHEMA` (via `McpSchema.toSchemaJson()`), and return `CallToolResult.builder().structuredContent(result).build()`. Tests: valid `.xlsx` with 3 sheets (verify sheet names + order), valid `.xls` legacy format, non-Excel `.csv` (verify `detected_extension=".csv"`), non-Excel `.tsv`, file-not-found error, corrupt/empty file error. | 04 §2, 04 §3 |
| 8 | **Implement `get_file_columns` tool** | `TOOL_TITLE = "Read Cytoscape Desktop File Columns"`. New tool class that reads column headers from tabular data files. Depends on Task 7b — the agent calls `inspect_tabular_file` first to determine Excel vs non-Excel and (for Excel) to get the sheet list; those results feed into this tool's parameters. Input schema: `{ "file_path": string, "delimiter_char_code": integer (nullable), "use_header_row": boolean, "excel_sheet": string (nullable) }`. For Excel (.xls/.xlsx) via Apache POI (`WorkbookFactory.create(file)`): open workbook, read columns from the sheet named by `excel_sheet`; `delimiter_char_code` is ignored for Excel. For non-Excel text files: split lines using `delimiter_char_code` (ASCII integer) — the agent confirms the delimiter with the user in STEP 1b2 before calling this tool; no extension-based auto-detection in the tool itself. `use_header_row=false`: generate ordinal column names "Column 1", "Column 2", etc. instead of reading the first row as headers. Note: the agent calls this tool twice for Excel when a node-attributes sheet is selected — first for the network data sheet (Step 1b3), then again with the node-attributes sheet name (after Step 1b3.5, if user opts in to node attribute mapping) to enumerate its columns for the key-column prompt. Use `McpSchema.InputSchema` builder for `INPUT_SCHEMA`, a private static inner Jackson-annotated record for the response model and `OUTPUT_SCHEMA` (via `McpSchema.toSchemaJson()`), and return `CallToolResult.builder().structuredContent(result).build()`. Tests: CSV comma (code 44), TSV tab (code 9), custom/pipe (code 124), Excel with explicit `excel_sheet` param, multi-sheet Excel second call with different sheet name, `use_header_row=false` ordinal naming. | 04 §4.2, 04 §5.2 |
| 9 | **`load_cytoscape_network_view` — tabular file import with column mapping** | Add dispatch to `handleTabularImport()` when `source=tabular-file`. Extend the tool input schema (also update Task 6 schema for these new fields): `delimiter_char_code` (integer, required for non-Excel text files), `use_header_row` (boolean), `excel_sheet` (string, nullable — which Excel sheet holds the source/target network edge data), `node_attributes_sheet` (string, nullable), `node_attributes_key_column` (string, nullable), `node_attributes_source_columns` (array of strings, nullable — columns to map as source node properties), `node_attributes_target_columns` (array of strings, nullable — columns to map as target node properties). Primary network import: uses Cytoscape's command API (`CommandExecutorTaskFactory`) or equivalent task factory, passing `delimiter_char_code`, `use_header_row`, `excel_sheet`, and source/target/interaction column mapping. Secondary node attribute import: when `node_attributes_key_column` is non-null, import node properties. If `node_attributes_sheet` is also non-null (Excel path where user selected a node attribute sheet after STEP 1b3.5), use Apache POI to read that sheet and import into the node table joining on the key column. If `node_attributes_sheet` is null (non-Excel path), load node properties from the same file's columns. In both cases, `node_attributes_source_columns` and `node_attributes_target_columns` specify which columns map to source/target node properties respectively; remaining unmapped columns become edge properties. Skip secondary import entirely if `node_attributes_key_column` is null. Tests: non-Excel with delimiter + `use_header_row`; Excel with `excel_sheet`; non-Excel node-attribute import (same file, key column + source/target column lists); Excel node-attribute import from separate sheet; verify secondary import triggered when `node_attributes_key_column` is non-null; verify secondary import skipped when `node_attributes_key_column` is null; verify node table merge uses correct key column; verify source/target column routing. | 04 §5.1.3 |
| 10 | **Implement `analyze_network` tool** | `TOOL_TITLE = "Analyze Cytoscape Desktop Network"`. New tool class that runs NetworkAnalyzer on the current network. Takes `directed` boolean. Compares node table columns before/after to report newly added columns. Uses Cytoscape command API (`analyzer analyze`). Handles "analyzer not available" gracefully. Use `McpSchema.InputSchema` builder for `INPUT_SCHEMA`, a private static inner Jackson-annotated record for the response model and `OUTPUT_SCHEMA` (via `McpSchema.toSchemaJson()`), and return `CallToolResult.builder().structuredContent(result).build()`. Tests: mock command execution, verify directed param, verify column diff detection. | 01 §4.6, 01 §5.6 |
| 11 | **Implement `get_layout_algorithms` and `apply_layout` tools** | `TOOL_TITLE = "List Cytoscape Desktop Layouts"` and `"Apply Cytoscape Desktop Layout"` respectively. Two tool classes. `get_layout_algorithms`: enumerate `CyLayoutAlgorithmManager.getAllLayouts()`, return JSON array of {name, displayName}. `apply_layout`: look up algorithm by name, create task iterator with default context, execute via `SynchronousTaskManager`, call `view.fitContent()` + `view.updateView()`. Register both in CyActivator. Use `McpSchema.InputSchema` builder for `INPUT_SCHEMA`, a private static inner Jackson-annotated record for the response model and `OUTPUT_SCHEMA` (via `McpSchema.toSchemaJson()`), and return `CallToolResult.builder().structuredContent(result).build()`. Tests: mock layout manager, verify algorithm listing; mock layout execution, verify fitContent called. | 01 §4.7-4.8, 01 §5.7-5.8 |
| 12 | **Implement `load_network` MCP prompt** | New prompt class. Constructs the system prompt text from 04 spec §2 (the full conversation script: STEP 1, STEP 1a-NDEx, STEP 1a-File, STEP 1b, Step 1b1, Step 1b2, Step 1b3, STEP 1-LOAD). Registers as `SyncPromptSpecification` with `name="load_network"`, `title="Load Network on Cytoscape Desktop"`, `description="Interactive prompt that guides you..."`, no arguments. System prompt text stored in classpath resource `LoadNetworkPromptSystem.prompt`. Tests: verify `name`, `title`, `description` fields; verify system prompt text includes all Phase 1 steps (STEP 1 through STEP 1-LOAD); verify `GetPromptResult` structure; verify message role is `ASSISTANT`. | 04 §1, 04 §2 |

### 🚩 MILESTONE 2 — "Load + Analyze + Layout Pipeline" (after Task 12)

> **Gate rule**: This milestone MUST pass before any Task 13+ work begins.

**Prerequisite tasks**: Tasks 1–12 complete, M1 passed, all unit tests passing.

**Deploy & connect**: Same as M1.

**Agent test script**:

Verify `load_network` appears in `prompts/list` before running scenarios.

---

**Scenario A — NDEx**

1. Type: *"i want to load a network into cytoscape"*
2. > *"Let's load a network into Cytoscape. Where is your network? 1. NDEx (Network Data Exchange) — load by UUID  2. Local file — load from your filesystem"*  
   → type `1`
3. > *"Please provide the NDEx network UUID (you can find this in the NDEx URL or network details)."*  
   → enter `a420ade1-56e6-11ef-bca1-005056ae23aa`
4. > *"Network loaded from NDEx! Your network has N nodes and M edges."*  
   ✓ Network appears in Cytoscape

---

**Scenario B — Network-formatted file (.sif)**

1. Type: *"i want to load a network into cytoscape"*
2. > *"Let's load a network into Cytoscape. Where is your network? 1. NDEx (Network Data Exchange) — load by UUID  2. Local file — load from your filesystem"*  
   → type `2`
3. > *"Is this a raw tabular data file with delimiters or is it expressed as a network formatted file that captures source and target nodes and relationships (edges)?  1 - Tabular, delimited data  2 - Network formatted data"*  
   → type `2`
4. > *"Please provide the path to your network file on your local machine."*  
   → for test purpose only, agent uses its local file search tools to resolve the absolute path of this project relative path - `docs/harnes/dev-tasks/test_export.sif`, then enter the resolved absolute path on prompt
5. > *"Network loaded…X nodes and Y edges."*  
   ✓ Network appears in Cytoscape

---

**Scenario C — CSV tabular file (test_export.csv)**

1. Type: *"i want to load a network into cytoscape"*
2. > *"Let's load a network into Cytoscape. Where is your network? 1. NDEx (Network Data Exchange) — load by UUID  2. Local file — load from your filesystem"*  
   → type `2`
3. > *"Is this a raw tabular data file with delimiters or is it expressed as a network formatted file…?  1 - Tabular, delimited data  2 - Network formatted data"*  
   → type `1`
4. > *"Please provide the path to your network file on your local machine."*  
   → for test purpose only, agent uses its local file search tools to resolve the absolute path of this project relative path - `docs/harness/dev-tasks/test_export.csv`, then enter the resolved absolute path on prompt
5. > *"Next step is to identify the delimiter, node, and edge columns from the tabular data file."*  
   > *"Choose which delimiter character is used for separation of columnar data:  1 - comma [pre-selected]  2 - tab  3 - space  4 - Other"*  
   → type `1`
6. > *"Does first row of data contain header of column names? If no, then default ordinal names will be created like 'Column 1', 'Column 2', etc.  1 - Yes  2 - No"*  
   → type `1`
7. > *"The following columns were detected in your file:  1. Gene1  2. interaction  3. Gene2  4. speed  5. style  6. weight  7. color  8. confidence"*  
   > *"Which column contains the source (from) node?"*  
   → enter `Gene1`
8. > *"Which column contains the target (to) node?"*  
   → enter `Gene2`
9. > *"Which column contains the interaction/relationship type? (enter the number for column or type 'skip' if there isn't one)"*  
   → type `interaction`
10. > *"Do you want to map properties for Nodes from the file columns at this time?"*  
    → type `no`
11. > *"Network loaded…X nodes and Y edges."*  
    ✓ Network appears in Cytoscape

---

**Scenario D — Excel tabular file (test_export.xlsx)**

1. Type: *"i want to load a network into cytoscape"*
2. > *"Let's load a network into Cytoscape. Where is your network? 1. NDEx (Network Data Exchange) — load by UUID  2. Local file — load from your filesystem"*  
   → type `2`
3. > *"Is this a raw tabular data file with delimiters or is it expressed as a network formatted file…?  1 - Tabular, delimited data  2 - Network formatted data"*  
   → type `1`
4. > *"Please provide the path to your network file on your local machine."*  
   → for test purpose only, agent uses its local file search tools to resolve the absolute path of this project relative path - `docs/harness/dev-tasks/test_export.xlsx`, then enter the resolved absolute path on prompt
5. > *"The following sheets were present:  1. Sheet1"*  
   > *"Which sheet should be used for source/target network data?"*  
   → type `1`
6. > *"Does first row of data contain header of column names?  1 - Yes  2 - No"*  
   → type `1`
7. > *"The following columns were detected in your file:  1. Gene1  2. interaction  3. Gene2  4. speed  5. style  6. weight  7. color  8. confidence"*  
   > *"Which column contains the source (from) node?"*  
   → enter `Gene1`
8. > *"Which column contains the target (to) node?"*  
   → enter `Gene2`
9. > *"Which column contains the interaction/relationship type? (enter the number for column or type 'skip' if there isn't one)"*  
   → type `interaction`
10. > *"Do you want to map properties for Nodes from the file columns at this time?"*  
    → type `no`
11. > *"Network loaded…X nodes and Y edges."*  
    ✓ Network appears in Cytoscape

---

**Scenario E — CSV tabular file with node property mapping (test_export.csv)**

1. Type: *"i want to load a network into cytoscape"*
2. > *"Let's load a network into Cytoscape. Where is your network? 1. NDEx (Network Data Exchange) — load by UUID  2. Local file — load from your filesystem"*  
   → type `2`
3. > *"Is this a raw tabular data file with delimiters or is it expressed as a network formatted file…?  1 - Tabular, delimited data  2 - Network formatted data"*  
   → type `1`
4. > *"Please provide the path to your network file on your local machine."*  
   → for test purpose only, agent uses its local file search tools to resolve the absolute path of this project relative path - `docs/harness/dev-tasks/test_export.csv`, then enter the resolved absolute path on prompt
5. > *"Choose which delimiter character is used for separation of columnar data:  1 - comma [pre-selected]  2 - tab  3 - space  4 - Other"*  
   → type `1`
6. > *"Does first row of data contain header of column names? If no, then default ordinal names will be created like 'Column 1', 'Column 2', etc.  1 - Yes  2 - No"*  
   → type `1`
7. > *"The following columns were detected in your file:  1. Gene1  2. interaction  3. Gene2  4. speed  5. style  6. weight  7. color  8. confidence"*  
   > *"Which column contains the source (from) node?"*  
   → enter `Gene1`
8. > *"Which column contains the target (to) node?"*  
   → enter `Gene2`
9. > *"Which column contains the interaction/relationship type? (enter the number for column or type 'skip' if there isn't one)"*  
   → type `interaction`
10. > *"Do you want to map properties for Nodes from the file columns at this time?"*  
    → type `yes`
11. > *"Which column should be mapped as a property for the source node (Gene1)? Available: speed, style, weight, color, confidence  (or type 'skip')"*  
    → type `weight`
12. > *"Which column should be mapped as a property for the target node (Gene2)? Available: speed, style, weight, color, confidence  (or type 'skip')"*  
    → type `speed`
13. > *"Network loaded…X nodes and Y edges."*  
    ✓ Network appears in Cytoscape  
    ✓ Node table contains a `weight` column on source nodes and a `speed` column on target nodes

---

**Scenario F — Multi-sheet Excel with node attribute mapping from separate sheet (test_export_multi.xlsx)**

1. Type: *"i want to load a network into cytoscape"*
2. > *"Let's load a network into Cytoscape. Where is your network? 1. NDEx (Network Data Exchange) — load by UUID  2. Local file — load from your filesystem"*  
   → type `2`
3. > *"Is this a raw tabular data file with delimiters or is it expressed as a network formatted file…?  1 - Tabular, delimited data  2 - Network formatted data"*  
   → type `1`
4. > *"Please provide the path to your network file on your local machine."*  
   → for test purpose only, agent uses its local file search tools to resolve the absolute path of this project relative path - `docs/harness/dev-tasks/test_export_multi.xlsx`, then enter the resolved absolute path on prompt
5. > *"The following sheets were present:  1. Summary  2. Interactions  3. NodeProps"*  
   > *"Which sheet should be used for source/target network data?"*  
   → type `2`
6. > *"Does first row of data contain header of column names?  1 - Yes  2 - No"*  
   → type `1`
7. > *"The following columns were detected in your file:  1. Gene1  2. interaction  3. Gene2  4. speed  5. style  6. weight  7. color  8. confidence"*  
   > *"Which column contains the source (from) node?"*  
   → enter `Gene1`
8. > *"Which column contains the target (to) node?"*  
   → enter `Gene2`
9. > *"Which column contains the interaction/relationship type? (enter the number for column or type 'skip' if there isn't one)"*  
   → type `interaction`
10. > *"Do you want to map properties for Nodes from the file columns at this time?"*  
    → type `yes`
11. > *"Would you like to load node attributes from a separate sheet in this file?  1 - Yes, choose another sheet  2 - No, use remaining columns from the current sheet"*  
    → type `1`
12. > *"Which sheet contains the node attribute data?  1. Summary  2. Interactions  3. NodeProps"*  
    → type `3`
13. > *"The following columns were detected in sheet NodeProps:  1. direction  2. origin"*  
    > *"Select the columns to import as node attributes (comma-separated numbers, or 'all', or 'skip')"*  
    → type `all`
14. > *"Network loaded…X nodes and Y edges."*  
    ✓ Network appears in Cytoscape  
    ✓ Node table contains a `direction` column (decimal values 0.00–360.00) and an `origin` column (Latin phrase values)

---

**Scenario G — Analyze network**

1. Ensure a network is loaded with a view (run any of Scenarios A–F first if needed).
2. Type: *"Analyze the current network"*
3. > *"Running network analysis…"* (or similar) → agent calls `analyze_network`
4. > Response lists newly computed columns added to the node table, e.g.:  
   *"Analysis complete. The following columns were added: Degree, BetweennessCentrality, ClosenessCentrality, …"*  
   ✓ Node table in Cytoscape now contains `Degree` (and other analyzer columns)

---

**Scenario H — Apply layout**

1. Ensure a network is loaded with a view (run any of Scenarios A–F first if needed).
2. Type: *"Apply the force-directed layout"*
3. > Agent calls `get_layout_algorithms` to enumerate available algorithms, selects the force-directed algorithm, then calls `apply_layout`
4. > Response confirms layout was applied, e.g.:  
   *"Layout applied. The network view has been updated with the force-directed layout."*  
   ✓ Nodes visibly rearrange in the Cytoscape canvas  
   ✓ View fits to content (network fills the viewport)

---

**Pass criteria**:
- `load_network` prompt appears in `prompts/list`
- **Scenario A**: prompt presents numbered source menu → asks for UUID → responds with "Network loaded from NDEx! N nodes M edges" confirmation
- **Scenario B**: prompt presents format menu → asks for file path → responds with load confirmation
- **Scenario C**: prompt presents format menu → asks for file path → presents delimiter menu with comma pre-selected → asks header row question → lists columns `[Gene1, interaction, Gene2, speed, style, weight, color, confidence]` → asks for source, target, interaction, and node-property choices → responds with load confirmation
- **Scenario D**: prompt presents format menu → asks for file path → presents sheet selection listing `Sheet1` → asks header row question → lists columns `[Gene1, interaction, Gene2, speed, style, weight, color, confidence]` → asks for source, target, interaction, and node-property choices → responds with load confirmation
- **Scenario E**: same as C through column listing → user chooses to map node properties → maps `weight` to source node (Gene1) and `speed` to target node (Gene2) → responds with load confirmation → node table contains `weight` and `speed` columns
- **Scenario F**: prompt presents format menu → asks for file path → presents sheet selection listing `Summary`, `Interactions`, `NodeProps` → user selects sheet 2 (Interactions) → column listing shows `[Gene1, interaction, Gene2, speed, style, weight, color, confidence]` → source/target/interaction mapped → user opts to map node attributes from a separate sheet → selects sheet 3 (NodeProps) → selects all columns (`direction`, `origin`) → responds with load confirmation → node table contains `direction` and `origin` columns
- **Scenario G**: agent calls `analyze_network` → response enumerates newly added columns by name → open Cytoscape node table and verify `Degree` column (and other analyzer columns) now exist with numeric values populated for every node
- **Scenario H**: agent calls `get_layout_algorithms` then `apply_layout` with a force-directed algorithm name → response confirms layout applied → verify in Cytoscape that node positions have changed from their previous layout and the view is fitted so all nodes are visible in the viewport

**Runtime risks caught**: `LoadNetworkFileTaskFactory` OSGi lookup, `CommandExecutorTaskFactory` for analyzer, `CyLayoutAlgorithmManager` service resolution, `SynchronousTaskManager` thread safety during layout execution.

---

| 13 | **Implement `get_visual_style_defaults` tool** | `TOOL_TITLE = "Get Cytoscape Desktop Style Defaults"`. New tool class using `VisualPropertyService`. Gets current `VisualStyle` from `VisualMappingManager`, enumerates node/edge VPs from `VisualLexicon.getAllDescendants()`, reads default values via `style.getDefaultValue()`, returns grouped JSON. For discrete-typed properties (NodeShape, ArrowShape, LineType), include an `allowedValues` array populated via new `VisualPropertyService.getAllowedValues(vp)` method (reads `DiscreteRange.values()`, maps to display names, sorts alphabetically). Paint/Double/Integer/Font/String/Boolean properties omit `allowedValues`. Use `McpSchema.InputSchema` builder for `INPUT_SCHEMA`, a private static inner Jackson-annotated record for the response model and `OUTPUT_SCHEMA` (via `McpSchema.toSchemaJson()`), and return `CallToolResult.builder().structuredContent(result).build()`. Tests: mock style with known defaults, verify JSON structure and value formatting; verify `allowedValues` present and alphabetically sorted for discrete types (NodeShape, ArrowShape, LineType); verify `allowedValues` absent for continuous types (Paint, Double, Integer). | 02 §4.1, 02 §5.1 |
| 14 | **Implement `set_visual_default` tool** | `TOOL_TITLE = "Set Cytoscape Desktop Style Default"`. New tool class using `VisualPropertyService`. Takes property_id + value, finds VP by id, parses value by type, captures old value, sets new default via `style.setDefaultValue()` on EDT (`SwingUtilities.invokeAndWait`), applies to current view. Use `McpSchema.InputSchema` builder for `INPUT_SCHEMA`, a private static inner Jackson-annotated record for the response model and `OUTPUT_SCHEMA` (via `McpSchema.toSchemaJson()`), and return `CallToolResult.builder().structuredContent(result).build()`. Tests: mock style, verify each type (color, shape, double, integer, font, line type, arrow shape), verify error for invalid values. | 02 §4.2, 02 §5.2 |
| 15 | **Implement `get_mappable_properties` tool** | `TOOL_TITLE = "List Cytoscape Desktop Mappable Properties"`. New tool class using `VisualPropertyService`. Like `get_visual_style_defaults` but also includes `continuousSubType` (color-gradient / continuous / discrete / null) and `currentMapping` info (type, column, summary) for each VP. Use `McpSchema.InputSchema` builder for `INPUT_SCHEMA`, a private static inner Jackson-annotated record for the response model and `OUTPUT_SCHEMA` (via `McpSchema.toSchemaJson()`), and return `CallToolResult.builder().structuredContent(result).build()`. Tests: mock style with existing mappings, verify currentMapping serialization. | 03 §4.1, 03 §5.1 |
| 16 | **Implement `get_compatible_columns` tool** | `TOOL_TITLE = "Get Cytoscape Desktop Mapping Columns"`. New tool class. Takes property_id, determines if node/edge property from lexicon hierarchy, enumerates table columns (excluding SUID, selected, List types), computes `supportsMapping` (continuous/discrete/passthrough) per column based on column type × VP type compatibility matrix (00 §4). Returns JSON with sample values. Use `McpSchema.InputSchema` builder for `INPUT_SCHEMA`, a private static inner Jackson-annotated record for the response model and `OUTPUT_SCHEMA` (via `McpSchema.toSchemaJson()`), and return `CallToolResult.builder().structuredContent(result).build()`. Tests: mock table with various column types, verify compatibility flags. | 03 §4.2, 03 §5.2 |
| 17 | **Implement `get_column_range` and `get_column_distinct_values` tools** | `TOOL_TITLE = "Get Cytoscape Desktop Column Range"` and `"Get Cytoscape Desktop Column Values"` respectively. Two tool classes. `get_column_range`: takes column_name + table (node/edge), computes min/max/mean/count for numeric columns. `get_column_distinct_values`: takes column_name + table, counts occurrences of each distinct value, sorts by count descending. Use `McpSchema.InputSchema` builder for `INPUT_SCHEMA`, a private static inner Jackson-annotated record for the response model and `OUTPUT_SCHEMA` (via `McpSchema.toSchemaJson()`), and return `CallToolResult.builder().structuredContent(result).build()`. Tests: mock table rows, verify statistics; verify distinct counting and sort order. | 03 §4.3-4.4, 03 §5.3-5.4 |

### 🚩 MILESTONE 3 — "Visual Style Read/Write + Column Inspection" (after Task 17)

> **Gate rule**: This milestone MUST pass before any Task 18+ work begins.

**Prerequisite tasks**: Tasks 1–17 complete, M1+M2 passed, all unit tests passing. Ensure a network is loaded with a view before testing.

**Deploy & connect**: Same as M1.

**Agent test script**:
1. Ask agent: *"Show me the current visual style defaults"* → agent calls `get_visual_style_defaults` → returns grouped JSON with node/edge properties, each showing `id`, `displayName`, `typeName`, `currentDefault` (formatted: colors as hex, shapes as names, fonts as "family-style-size")
2. Ask agent: *"Change the default node fill color to #FF5733"* → agent calls `set_visual_default` with `property_id=NODE_FILL_COLOR, value=#FF5733` → all nodes in canvas turn orange-red, response shows old→new value
3. Ask agent: *"Change the default node shape to DIAMOND"* → agent calls `set_visual_default` → nodes change shape visually
4. Ask agent: *"What properties can I map to data?"* → agent calls `get_mappable_properties` → returns properties with `continuousSubType` and any existing `currentMapping` info
5. Ask agent: *"What columns are compatible with NODE_FILL_COLOR?"* → agent calls `get_compatible_columns` → returns columns with `supportsMapping` flags (continuous/discrete/passthrough)
6. If a numeric column exists (e.g., Degree from M2 analyzer): *"Show me the range of the Degree column"* → agent calls `get_column_range` → returns min/max/mean/count
7. For a string column: *"Show me the distinct values of the 'name' column"* → agent calls `get_column_distinct_values` → returns value-count pairs sorted by frequency

**Pass criteria**:
- `get_visual_style_defaults` returns all supported node/edge VPs with correctly formatted values
- `set_visual_default` changes are immediately visible in the Cytoscape canvas (no EDT deadlock)
- `get_mappable_properties` includes `continuousSubType` classification for each VP
- `get_compatible_columns` correctly filters out SUID/selected/List columns and computes compatibility flags
- `get_column_range` and `get_column_distinct_values` return correct statistics from live table data

**Runtime risks caught**: `VisualMappingManager` / `RenderingEngineManager` service wiring, `VisualLexicon.getAllDescendants()` traversal, EDT `invokeAndWait` from MCP request thread (deadlock potential), `CyTable` row iteration performance.

---

| 18 | **Implement `create_continuous_mapping` tool** | `TOOL_TITLE = "Create Cytoscape Desktop Continuous Mapping"`. New tool class using `VisualPropertyService`. Takes property_id, column_name, column_type, points (breakpoints array). Creates `ContinuousMapping` via factory, adds `BoundaryRangeValues` for each point, removes existing mapping, adds new one, applies to view on EDT. Use `McpSchema.InputSchema` builder for `INPUT_SCHEMA`, a private static inner Jackson-annotated record for the response model and `OUTPUT_SCHEMA` (via `McpSchema.toSchemaJson()`), and return `CallToolResult.builder().structuredContent(result).build()`. Tests: mock factory, verify breakpoint creation for numeric and color types, verify point ordering validation. | 03 §4.5, 03 §5.5 |
| 19 | **Implement `create_discrete_mapping` tool** | `TOOL_TITLE = "Create Cytoscape Desktop Discrete Mapping"`. New tool class using `VisualPropertyService`. Takes property_id, column_name, column_type, entries (map of value→property_value). Creates `DiscreteMapping` via factory, converts column keys via `resolveColumnType`/`convertColumnValue`, parses property values, calls `mapping.putAll()`, applies on EDT. Use `McpSchema.InputSchema` builder for `INPUT_SCHEMA`, a private static inner Jackson-annotated record for the response model and `OUTPUT_SCHEMA` (via `McpSchema.toSchemaJson()`), and return `CallToolResult.builder().structuredContent(result).build()`. Tests: mock factory, verify entry mapping for string and integer column types. | 03 §4.6, 03 §5.6 |
| 20 | **Implement `create_passthrough_mapping` tool** | `TOOL_TITLE = "Create Cytoscape Desktop Passthrough Mapping"`. New tool class. Takes property_id, column_name, column_type. Creates `PassthroughMapping` via factory, removes existing, adds new, applies on EDT. Simpler than continuous/discrete — no entries to configure. Use `McpSchema.InputSchema` builder for `INPUT_SCHEMA`, a private static inner Jackson-annotated record for the response model and `OUTPUT_SCHEMA` (via `McpSchema.toSchemaJson()`), and return `CallToolResult.builder().structuredContent(result).build()`. Tests: mock factory, verify mapping creation and application. | 03 §4.7, 03 §5.7 |
| 21 | **Implement `create_discrete_mapping_generated` tool** | `TOOL_TITLE = "Create Cytoscape Desktop Auto Mapping"`. New tool class. Takes property_id, column_name, column_type, generator (rainbow/random/brewer_sequential/shape_cycle/numeric_range), generator_params. Gets distinct values from table, generates property values per algorithm, creates discrete mapping with generated entries. Use `McpSchema.InputSchema` builder for `INPUT_SCHEMA`, a private static inner Jackson-annotated record for the response model and `OUTPUT_SCHEMA` (via `McpSchema.toSchemaJson()`), and return `CallToolResult.builder().structuredContent(result).build()`. Tests: verify each generator algorithm (rainbow produces evenly-spaced hues, random is seeded, numeric_range interpolates, shape_cycle cycles). | 03 §4.8, 03 §5.8 |
| 22 | **Implement `default_styling` MCP prompt** | New prompt class. Constructs the system prompt text from 02 spec §2 (the full conversation script). Registers as `SyncPromptSpecification` with name="default_styling", title="Change Default Visual Style Properties", no arguments. Tests: verify prompt name/title, verify system prompt text includes all steps (STEP 1-4), verify GetPromptResult structure. | 02 §1, 02 §2 |
| 23 | **Implement `mapping_styling` MCP prompt** | New prompt class. Constructs the system prompt text from 03 spec §2 (the full conversation script including all sub-types 4a-i through 4c). Registers as `SyncPromptSpecification` with name="mapping_styling", title="Create Data-Driven Visual Mappings", no arguments. Tests: verify prompt name/title, verify system prompt includes all mapping type steps. | 03 §1, 03 §2 |
| 24 | **Implement `network_wizard` MCP prompt and final CyActivator integration** | New prompt class. Constructs the full orchestrator system prompt from 01 spec §2 (Phases 0-6, delegating Phase 1 to 04 spec, Phase 4/5 to sub-specs). Registers as `SyncPromptSpecification` with name="network_wizard". Update `CyActivator.startMcpServer()` to register all 4 prompts via `mcpServer.addPrompt()`. Update `ServerCapabilities` to include `prompts(false)`. Verify all tools registered. Tests: verify prompt content includes all phases, verify CyActivator registers correct tool/prompt count. | 01 §1, 01 §2, 00 §7.2, 00 §9.1 |

### 🚩 MILESTONE 4 — "Full Wizard: Mappings + Prompts" (after Task 24)

> **Gate rule**: All previous milestones (M1–M3) must have passed. This is the final acceptance gate.

**Prerequisite tasks**: Tasks 1–24 complete, M1+M2+M3 passed, all unit tests passing. Ensure a network with analyzed columns is loaded.

**Deploy & connect**: Same as M1.

**Agent test script — Mappings**:
1. Ask agent: *"Create a continuous mapping from Degree to NODE_SIZE, small values get size 20, large values get size 80"* → agent calls `create_continuous_mapping` with breakpoints → nodes resize proportionally in canvas
2. Ask agent: *"Create a discrete mapping from some string column to NODE_FILL_COLOR, assign specific colors"* → agent calls `create_discrete_mapping` → nodes recolor by category
3. Ask agent: *"Create a passthrough mapping from 'name' column to NODE_LABEL"* → agent calls `create_passthrough_mapping` → node labels appear showing names
4. Ask agent: *"Auto-generate a rainbow color mapping from the 'name' column to NODE_FILL_COLOR"* → agent calls `create_discrete_mapping_generated` with `generator=rainbow` → nodes get evenly-spaced hues

**Agent test script — Prompts**:
5. List available prompts via MCP `prompts/list` → should return 4 prompts: `network_wizard`, `default_styling`, `mapping_styling`, `load_network`
6. Select the `network_wizard` prompt → agent receives the full orchestrator system prompt → begin wizard conversation → agent walks through Phase 0–6 using all tools
7. During wizard Phase 4, verify agent delegates to `default_styling` sub-prompt behavior
8. During wizard Phase 5, verify agent delegates to `mapping_styling` sub-prompt behavior

**Pass criteria**:
- All 3 mapping factory types resolve (`ContinuousMappingFactory`, `DiscreteMappingFactory`, `PassthroughMappingFactory`)
- Continuous mapping produces visible size/color gradients
- Discrete mapping assigns per-category values correctly
- Generated discrete mapping (rainbow) produces visually distinct hues
- All 4 prompts appear in `prompts/list`
- `network_wizard` prompt orchestrates the full 7-phase flow
- Sub-prompt delegation works (wizard hands off to `default_styling` and `mapping_styling`)

**Runtime risks caught**: `VisualMappingFunctionFactory` OSGi filter-based lookups (×3 factories with different `(mapping.type=...)` filters), `BoundaryRangeValues` construction for continuous mappings, `ServerCapabilities` prompt support flag, `mcpServer.addPrompt()` registration, full end-to-end MCP protocol flow with prompts.

---

## Notes

- **Existing code**: `LoadNetworkViewTool.java` already implements NDEx loading. Task 6 refactors its schema and response format; tasks 7+9 add file loading paths.
- **Build deps already present**: `core-task-api` and `io-api` are already in build.gradle. Need to add: `vizmap-api`, `presentation-api`, `layout-api`.
- **Apache POI for Excel**: Task 1 adds `poi` + `poi-ooxml` as `embed` dependencies; Task 7b uses POI for Excel detection/sheet listing; Task 8 uses POI for Excel column reading.
- **Thread safety**: All tools that mutate `VisualStyle` or update views must wrap calls in `SwingUtilities.invokeAndWait()` (per 00 spec, 02 §6.8, 03 §6.9).
- **Error pattern**: All tools return `CallToolResult(isError=true)` with human-readable messages per 00 §5.

