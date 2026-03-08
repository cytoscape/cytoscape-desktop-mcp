# Tool Spec: Load Network Tools

## 4. MCP Tool Schemas

### 4.1 `load_cytoscape_network_view`

```json
{
  "name": "load_cytoscape_network_view",
  "title": "Load Cytoscape Desktop Network",
  "description": "Load a network into Cytoscape Desktop from NDEx (by UUID), a native network format file, or a tabular data file with column mapping. Creates a new network collection and view, and sets it as the current network.",
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
      "node_attributes_sheet_source_key_column": {
        "type": "string",
        "description": "Column name in the node attributes sheet that contains the node ID of the source node for joining. Used with node_attributes_sheet (Excel only). For non-Excel files, source_column is used as the key automatically."
      },
      "node_attributes_sheet_target_key_column": {
        "type": "string",
        "description": "Column name in the node attributes sheet that contains the node ID of the target node for joining. Used with node_attributes_sheet (Excel only). For non-Excel files, target_column is used as the key automatically."
      },
      "node_attributes_source_columns": {
        "type": "array",
        "items": { "type": "string" },
        "description": "Column names to map as properties to the source node. Optional; applicable when source='tabular-file' and node attribute import is desired. For Excel, requires node_attributes_sheet_source_key_column. For non-Excel, source_column is used as the key. Omit if empty."
      },
      "node_attributes_target_columns": {
        "type": "array",
        "items": { "type": "string" },
        "description": "Column names to map as properties to the target node. Optional; applicable when source='tabular-file' and node attribute import is desired. For Excel, requires node_attributes_sheet_target_key_column. For non-Excel, target_column is used as the key. Omit if empty."
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
  "title": "Read Cytoscape Desktop File Columns",
  "description": "Read column headers and sample rows from a tabular data file, for use when importing network data into Cytoscape Desktop, using explicit format parameters. The caller must first use inspect_tabular_file to determine Excel vs text format. Returns column names and a few sample rows. Read-only; does not import data.",
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
  "title": "Inspect Cytoscape Desktop Import File",
  "description": "Inspect a tabular data file, for use when importing network data into Cytoscape Desktop, to determine if it is Excel format and, if so, list all sheet names. Used before get_file_columns to route the agent to the correct sub-flow (Excel sheet selection vs delimiter selection). Read-only; does not import data.",
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
  "content": [{ "type": "text", "text": "{\"is_excel\":false,\"detected_delimiter_char_code\":44}" }],
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
