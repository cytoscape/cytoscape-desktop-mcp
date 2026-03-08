# Cytoscape MCP Server — API Manifest

> Auto-generated schema reference for all registered MCP artifacts.
> Served at: `GET /v1/mcp/manifest`

This document describes every tool, prompt, resource, and resource template exposed by the
Cytoscape MCP Server. It is regenerated automatically as part of the Gradle build and bundled
into the application JAR.

---

## Tools

### `load_cytoscape_network_view`

**Title:** Load Cytoscape Desktop Network

**Description:** Load a network into Cytoscape Desktop from NDEx (by UUID), a native network format file, or a tabular data file with column mapping. Creates a new network collection and view, and sets it as the current network.

## Examples

Example 1 — Load from NDEx:
{"source": "ndex", "network_id": "a7e43e3d-c7f8-11ec-8d17-005056ae23aa"}

Example 2 — Load a native format file:
{"source": "network-file", "file_path": "/path/to/network.sif"}

Example 3 — Load tabular data:
{"source": "tabular-file", "file_path": "/path/to/data.csv", "source_column": "Gene_A", "target_column": "Gene_B", "delimiter_char_code": 44, "use_header_row": true}

**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "node_attributes_source_columns" : {
      "type" : "array",
      "description" : "Optional. Array of column names from the node attributes sheet (or main sheet) to attach as properties on source nodes.",
      "items" : {
        "type" : "string"
      }
    },
    "excel_sheet" : {
      "type" : "string",
      "description" : "Name of the Excel sheet containing the network data. Required for Excel tabular files."
    },
    "interaction_column" : {
      "type" : "string",
      "description" : "Column name for the edge interaction type. Optional for source='tabular-file'."
    },
    "node_attributes_sheet_target_key_column" : {
      "type" : "string",
      "description" : "Column name in the node attributes sheet whose values match target-node IDs in the main network sheet. Used to join attributes onto target nodes. Required when node_attributes_sheet is set."
    },
    "node_attributes_target_columns" : {
      "type" : "array",
      "description" : "Optional. Array of column names from the node attributes sheet (or main sheet) to attach as properties on target nodes.",
      "items" : {
        "type" : "string"
      }
    },
    "node_attributes_sheet_source_key_column" : {
      "type" : "string",
      "description" : "Column name in the node attributes sheet whose values match source-node IDs in the main network sheet. Used to join attributes onto source nodes. Required when node_attributes_sheet is set."
    },
    "target_column" : {
      "type" : "string",
      "description" : "Column name for the target node. Required when source='tabular-file'."
    },
    "source" : {
      "type" : "string",
      "description" : "Required. The data source type. Must be one of: 'ndex' (load by UUID from NDEx), 'network-file' (load a native network format file such as .sif, .xgmml, or .cx), 'tabular-file' (load a CSV or Excel file with column mapping).",
      "enum" : [ "ndex", "network-file", "tabular-file" ]
    },
    "node_attributes_sheet" : {
      "type" : "string",
      "description" : "Name of a second Excel sheet that contains additional node attribute columns to join onto the network nodes. Optional for Excel tabular files."
    },
    "use_header_row" : {
      "type" : "boolean",
      "description" : "Whether the first row of the file contains column headers. Required when source='tabular-file'."
    },
    "file_path" : {
      "type" : "string",
      "description" : "Absolute path to the file to import. Required when source='network-file' or 'tabular-file'."
    },
    "delimiter_char_code" : {
      "type" : "integer",
      "description" : "ASCII character code of the column delimiter (e.g. 44 for comma, 9 for tab). Required for non-Excel tabular files."
    },
    "source_column" : {
      "type" : "string",
      "description" : "Column name for the source node. Required when source='tabular-file'."
    },
    "network_id" : {
      "type" : "string",
      "description" : "The UUID of the network on NDEx (e.g. \"a7e43e3d-c7f8-11ec-8d17-005056ae23aa\"). Required when source='ndex'."
    }
  },
  "required" : [ "source" ]
}
```

**Output Schema:**

```json
{
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "type" : "object",
  "properties" : {
    "edge_count" : {
      "type" : "integer",
      "description" : "Number of edges in the loaded network."
    },
    "network_name" : {
      "type" : "string",
      "description" : "Name of the loaded network as shown in the Cytoscape Desktop Network panel."
    },
    "network_suid" : {
      "type" : "integer",
      "description" : "Unique SUID of the loaded network in Cytoscape Desktop."
    },
    "node_attributes_imported" : {
      "type" : "boolean",
      "description" : "True if node attributes were successfully joined onto the network. Present only when node_attributes_* parameters were supplied."
    },
    "node_count" : {
      "type" : "integer",
      "description" : "Number of nodes in the loaded network."
    },
    "status" : {
      "type" : "string",
      "description" : "Result status, e.g. 'success'."
    },
    "warning" : {
      "type" : "string",
      "description" : "Non-fatal warning message, e.g. when the network loaded but some attributes could not be mapped. Present only when a warning occurred."
    }
  }
}
```

---

### `get_loaded_network_views`

**Title:** List Cytoscape Desktop Networks

**Description:** Enumerate all network collections currently loaded in Cytoscape Desktop with their views, node counts, and edge counts. Call this first to discover network_suid and view_suid values required by other tools. Read-only; does not modify state. If a network has no view, view_suid is absent from that entry.

**Input Schema:**

```json
{
  "type" : "object",
  "properties" : { },
  "required" : [ ]
}
```

**Output Schema:**

```json
{
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "type" : "object",
  "properties" : {
    "views" : {
      "type" : "array",
      "items" : {
        "type" : "object",
        "properties" : {
          "collection_name" : {
            "type" : "string",
            "description" : "Name of the root network collection."
          },
          "edge_count" : {
            "type" : "integer",
            "description" : "Number of edges in the network."
          },
          "network_name" : {
            "type" : "string",
            "description" : "Name of the sub-network within the collection."
          },
          "network_suid" : {
            "type" : "integer",
            "description" : "Unique SUID of the network in Cytoscape Desktop."
          },
          "node_count" : {
            "type" : "integer",
            "description" : "Number of nodes in the network."
          },
          "view_suid" : {
            "type" : "integer",
            "description" : "Unique SUID of the network view. Absent if the network has no view."
          }
        }
      }
    }
  }
}
```

---

### `set_current_network_view`

**Title:** Set Cytoscape Desktop Active Network

**Description:** Set the specified network and view as the current (active) network and view in Cytoscape Desktop. Both network_suid and view_suid are required. Useful before applying styles, layouts, or analysis to a specific network.

**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "view_suid" : {
      "type" : "integer",
      "description" : "Required. SUID of the target network view in Cytoscape Desktop."
    },
    "network_suid" : {
      "type" : "integer",
      "description" : "Required. SUID of the target network in Cytoscape Desktop."
    }
  },
  "required" : [ "network_suid", "view_suid" ]
}
```

**Output Schema:**

```json
{
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "type" : "object",
  "properties" : {
    "edge_count" : {
      "type" : "integer"
    },
    "network_name" : {
      "type" : "string"
    },
    "node_count" : {
      "type" : "integer"
    },
    "status" : {
      "type" : "string"
    }
  }
}
```

---

### `create_network_view`

**Title:** Create Cytoscape Desktop Network View

**Description:** Create a visual view for a network in Cytoscape Desktop that currently has no view. Sets the new view and its network as the current network and view. If a view already exists for the network, returns the existing one instead of creating a duplicate.

**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "network_suid" : {
      "type" : "integer",
      "description" : "Required. SUID of the network in Cytoscape Desktop that needs a view."
    }
  },
  "required" : [ "network_suid" ]
}
```

**Output Schema:**

```json
{
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "type" : "object",
  "properties" : {
    "edge_count" : {
      "type" : "integer"
    },
    "network_name" : {
      "type" : "string"
    },
    "network_suid" : {
      "type" : "integer"
    },
    "node_count" : {
      "type" : "integer"
    },
    "status" : {
      "type" : "string"
    },
    "view_suid" : {
      "type" : "integer"
    }
  }
}
```

---

### `inspect_tabular_file`

**Title:** Inspect Cytoscape Desktop Import File

**Description:** Inspect a tabular data file to determine whether it is an Excel workbook (.xls/.xlsx) or a plain-text delimited file, for use when importing network data into Cytoscape Desktop. If Excel, returns the list of sheet names contained in the workbook. If not Excel, returns the detected file extension (e.g. '.csv', '.tsv').

**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "file_path" : {
      "type" : "string",
      "description" : "Required. Absolute path to the tabular data file to inspect."
    }
  },
  "required" : [ "file_path" ]
}
```

**Output Schema:**

```json
{
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "type" : "object",
  "properties" : {
    "detected_extension" : {
      "type" : "string",
      "description" : "Detected file extension (e.g. '.csv', '.tsv'). Present only when is_excel is false."
    },
    "is_excel" : {
      "type" : "boolean",
      "description" : "True if the file is an Excel workbook; false if plain-text."
    },
    "sheets" : {
      "description" : "Sheet names in the workbook. Present only when is_excel is true.",
      "type" : "array",
      "items" : {
        "type" : "string"
      }
    }
  }
}
```

---

### `get_file_columns`

**Title:** Read Cytoscape Desktop File Columns

**Description:** Read column headers and up to three sample rows from a tabular file, for use when importing network data into Cytoscape Desktop. For Excel files supply excel_sheet; for text files supply delimiter_char_code. Returns a 'columns' array of header strings and a 'sample_rows' list of value arrays.

**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "use_header_row" : {
      "type" : "boolean",
      "description" : "Required. If true, the first row is treated as column headers and those strings appear in 'columns'. If false, ordinal names are generated ('Column 1', 'Column 2', ...) and those ordinal names appear in 'columns' instead."
    },
    "file_path" : {
      "type" : "string",
      "description" : "Required. Absolute path to the tabular file."
    },
    "excel_sheet" : {
      "type" : "string",
      "description" : "Name of the Excel sheet to read. Required when reading an Excel file. Ignored for text files."
    },
    "delimiter_char_code" : {
      "type" : "integer",
      "description" : "ASCII code of the delimiter character (e.g. 44=comma, 9=tab, 124=pipe). Required for non-Excel files. Ignored for Excel."
    }
  },
  "required" : [ "file_path", "use_header_row" ]
}
```

**Output Schema:**

```json
{
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "type" : "object",
  "properties" : {
    "columns" : {
      "type" : "array",
      "items" : {
        "type" : "string"
      }
    },
    "sample_rows" : {
      "type" : "array",
      "items" : {
        "type" : "array",
        "items" : {
          "type" : "string"
        }
      }
    }
  }
}
```

---

### `analyze_network`

**Title:** Analyze Cytoscape Desktop Network

**Description:** Run NetworkAnalyzer on the current network in Cytoscape Desktop to compute topological statistics such as Degree, BetweennessCentrality, and ClosenessCentrality. Adds the computed values as new columns directly to the node and edge tables in Cytoscape Desktop. Returns the names of newly added node columns and basic network statistics.

**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "directed" : {
      "type" : "boolean",
      "description" : "Required. True to treat the network as a directed graph; false for undirected (typical for most biological interaction networks). Affects which centrality metrics NetworkAnalyzer computes — directed mode adds in-degree/out-degree metrics."
    }
  },
  "required" : [ "directed" ]
}
```

**Output Schema:**

```json
{
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "type" : "object",
  "properties" : {
    "columns_added" : {
      "description" : "Names of node attribute columns added to the Cytoscape Desktop node table by NetworkAnalyzer.",
      "type" : "array",
      "items" : {
        "type" : "string"
      }
    },
    "network_stats" : {
      "type" : "object",
      "properties" : {
        "avg_degree" : {
          "type" : "number",
          "description" : "Average node degree."
        },
        "edge_count" : {
          "type" : "integer",
          "description" : "Number of edges in the network."
        },
        "node_count" : {
          "type" : "integer",
          "description" : "Number of nodes in the network."
        }
      },
      "description" : "Summary statistics of the analyzed network."
    },
    "status" : {
      "type" : "string",
      "description" : "Result status, e.g. 'success'."
    }
  }
}
```

---

### `get_layout_algorithms`

**Title:** List Cytoscape Desktop Layouts

**Description:** List all layout algorithms available in Cytoscape Desktop with their internal names and human-readable display names. Read-only; does not modify state.

**Input Schema:**

```json
{
  "type" : "object",
  "properties" : { },
  "required" : [ ]
}
```

**Output Schema:**

```json
{
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "type" : "object",
  "properties" : {
    "layouts" : {
      "type" : "array",
      "items" : {
        "type" : "object",
        "properties" : {
          "displayName" : {
            "type" : "string"
          },
          "name" : {
            "type" : "string"
          }
        }
      }
    }
  }
}
```

---

### `apply_layout`

**Title:** Apply Cytoscape Desktop Layout

**Description:** Apply a layout algorithm to the current network view in Cytoscape Desktop using default parameters. After the layout runs, the view is fitted to content and refreshed.

**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "algorithm" : {
      "type" : "string",
      "description" : "Required. Internal layout algorithm name to apply. Each algorithm has a 'name' (machine identifier) and a 'displayName' (human-readable label). Supply the 'name' value, not 'displayName'."
    }
  },
  "required" : [ "algorithm" ]
}
```

**Output Schema:**

```json
{
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "type" : "object",
  "properties" : {
    "algorithm" : {
      "type" : "string"
    },
    "displayName" : {
      "type" : "string"
    },
    "status" : {
      "type" : "string"
    }
  }
}
```

---



## Prompts

{{PROMPTS}}

## Resources

{{RESOURCES}}

## Resource Templates

{{RESOURCE_TEMPLATES}}
