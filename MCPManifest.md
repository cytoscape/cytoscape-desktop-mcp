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

**Description:** Load a network into Cytoscape Desktop from one of multiple sources such as NDEx (by Network Id), a network formatted file, or a tabular formatted file with column mapping. Creates a new network root as a new collection and view, and sets it as the current network on Cytoscape Desktop.

## Examples

Example 1 — Load NDEx network into cytoscape desktop:
{"source": "ndex", "network_id": "a7e43e3d-c7f8-11ec-8d17-005056ae23aa"}

Example 2 — Load network file into cytoscape desktop:
{"source": "network-file", "file_path": "/path/to/network.sif"}

Example 3 — Load tabular file into cytoscape desktop:
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
    "network_id" : {
      "type" : "string",
      "description" : "The UUID of the network on NDEx (e.g. \"a7e43e3d-c7f8-11ec-8d17-005056ae23aa\"). Required when source='ndex'."
    },
    "source_column" : {
      "type" : "string",
      "description" : "Column name for the source node. Required when source='tabular-file'."
    },
    "delimiter_char_code" : {
      "type" : "integer",
      "description" : "ASCII character code of the column delimiter (e.g. 44 for comma, 9 for tab). Required for non-Excel tabular files."
    },
    "file_path" : {
      "type" : "string",
      "description" : "Absolute path to the file to import. Required when source='network-file' or 'tabular-file'."
    },
    "use_header_row" : {
      "type" : "boolean",
      "description" : "Whether the first row of the file contains column headers. Required when source='tabular-file'."
    },
    "node_attributes_sheet" : {
      "type" : "string",
      "description" : "Name of a second Excel sheet that contains additional node attribute columns to join onto the network nodes. Optional for Excel tabular files."
    },
    "source" : {
      "type" : "string",
      "description" : "Required. \nThe location and type of network data file to load. \nMust be one of: \n'ndex' (load by UUID from NDEx - ndexbio.org), \n'network-file' (load a local filepath which is encoded in network format already such as SIF, GML, XGMML, CX, CX2, GraphML, SBML, BioPAX), \n'tabular-file' (load a local filepath which is encoded as CSV, TSV, or Excel which will have column mappings).",
      "enum" : [ "ndex", "network-file", "tabular-file" ]
    },
    "target_column" : {
      "type" : "string",
      "description" : "Column name for the target node. Required when source='tabular-file'."
    },
    "node_attributes_sheet_source_key_column" : {
      "type" : "string",
      "description" : "Column name in the node attributes sheet whose values match source-node IDs in the main network sheet. Used to join attributes onto source nodes. Required when node_attributes_sheet is set."
    },
    "node_attributes_target_columns" : {
      "type" : "array",
      "description" : "Optional. Array of column names from the node attributes sheet (or main sheet) to attach as properties on target nodes.",
      "items" : {
        "type" : "string"
      }
    },
    "node_attributes_sheet_target_key_column" : {
      "type" : "string",
      "description" : "Column name in the node attributes sheet whose values match target-node IDs in the main network sheet. Used to join attributes onto target nodes. Required when node_attributes_sheet is set."
    },
    "interaction_column" : {
      "type" : "string",
      "description" : "Column name for the edge interaction type. Optional for source='tabular-file'."
    },
    "excel_sheet" : {
      "type" : "string",
      "description" : "Name of the Excel sheet containing the network data. Required for Excel tabular files."
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

## Examples

Example 1 — List networks currently open in Cytoscape desktop:
{}

Example 2 — What networks are loaded in Cytoscape desktop:
{}

Example 3 — Show me the network SUIDs available in Cytoscape desktop:
{}

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

## Examples

Example 1 — Switch to a specific network view in Cytoscape desktop:
{"network_suid": 100, "view_suid": 200}

Example 2 — Make a different network the active one in Cytoscape desktop:
{"network_suid": 300, "view_suid": 400}

Example 3 — Focus Cytoscape desktop on a particular network before applying styles:
{"network_suid": 100, "view_suid": 200}

**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "network_suid" : {
      "type" : "integer",
      "description" : "Required. SUID of the target network in Cytoscape Desktop."
    },
    "view_suid" : {
      "type" : "integer",
      "description" : "Required. SUID of the target network view in Cytoscape Desktop."
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

**Description:** Create or retrieve existing view for the provided network in Cytoscape Desktop. Sets the provided network and view as the current network and view on Desktop. If a view already exists for the network, returns the existing one instead of creating another view in the same network collection by default. Change the default behavior by setting create_if_exists to true to always create a new view even in the network collection even when one or more views already exists in the network collection.

## Examples

Example 1 — Create a visual view for a network that has no view in Cytoscape desktop:
{"network_suid": 100}

Example 2 — This network has no view, generate one in Cytoscape desktop:
{"network_suid": 100}

Example 3 — Force create a new view in Cytoscape desktop even though one already exists:
{"network_suid": 100, "create_if_exists": true}

**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "network_suid" : {
      "type" : "integer",
      "description" : "Required. SUID of the network in Cytoscape Desktop that needs a view."
    },
    "create_if_exists" : {
      "type" : "boolean",
      "description" : "Optional. Default is false. When false and a view already exists, returns the existing current view (or first available) without creating a duplicate. When true, always creates a new view even if views already exist."
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

**Description:** Inspect a tabular data file to determine whether it is an Excel workbook (.xls/.xlsx) or a plain-text delimited file such as csv or tsv. For use when importing network data into Cytoscape Desktop. If Excel, returns the list of sheet names contained in the workbook. If not Excel, returns the detected delimiter.

## Examples

Example 1 — What tabular format is data file encoded in importing into Cytoscape desktop:
{"file_path": "/path/to/data.xlsx"}

Example 2 — Inspect a tabular file to determine its format for Cytoscape desktop import:
{"file_path": "/path/to/data.csv"}

Example 3 — What sheets are in this Excel workbook:
{"file_path": "/path/to/workbook.xlsx"}

Example 4 — What delimiter is used in a data file for importing into Cytoscape desktop:
{"file_path": "/path/to/data.csv"}



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
    "detected_delimiter_char_code" : {
      "type" : "integer",
      "description" : "ASCII code of the detected delimiter character (e.g. 44=comma, 9=tab, 124=pipe, 59=semicolon, 32=space). Present only when is_excel is false."
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

**Description:** Retrieve column headers and up to three sample data rows from a tabular file. Use when importing network data from a tabular file into Cytoscape Desktop. For Excel files supply excel_sheet; for text files supply delimiter_char_code. Returns a 'columns' array of header strings and a 'sample_rows' list of value arrays.

## Examples

Example 1 — Read column headers from a CSV file for Cytoscape desktop import:
{"file_path": "/path/to/data.csv", "delimiter_char_code": 44, "use_header_row": true}

Example 2 — Preview columns from a tab-separated file:
{"file_path": "/path/to/data.tsv", "delimiter_char_code": 9, "use_header_row": true}

Example 3 — Read columns from an Excel sheet for Cytoscape desktop import:
{"file_path": "/path/to/data.xlsx", "use_header_row": true, "excel_sheet": "Sheet1"}

**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "delimiter_char_code" : {
      "type" : "integer",
      "description" : "ASCII code of the delimiter character (e.g. 44=comma, 9=tab, 124=pipe). Required for non-Excel files. Ignored for Excel."
    },
    "excel_sheet" : {
      "type" : "string",
      "description" : "Name of the Excel sheet to read. Required when reading an Excel file. Ignored for text files."
    },
    "file_path" : {
      "type" : "string",
      "description" : "Required. Absolute path to the tabular file."
    },
    "use_header_row" : {
      "type" : "boolean",
      "description" : "Required. If true, the first row is treated as column headers and those strings appear in 'columns'. If false, ordinal names are generated ('Column 1', 'Column 2', ...) and those ordinal names appear in 'columns' instead."
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

## Examples

Example 1 — Compute network statistics of the current network in Cytoscape desktop:
{"directed": false}

Example 2 — Run network analysis treating edges as directed of current network in Cytoscape desktop:
{"directed": true}

Example 3 — Calculate centrality metrics for an undirected biological network in Cytoscape desktop:
{"directed": false}

**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "directed" : {
      "type" : "boolean",
      "description" : "Optional. Default is True. When True, treats the network as a directed graph;  When False, treats the network as undirected (typical for most biological interaction networks). Affects which centrality metrics NetworkAnalyzer computes — directed mode adds in-degree/out-degree metrics."
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

## Examples

Example 1 — What layout algorithms are available in Cytoscape desktop:
{}

Example 2 — List the layout options available from Cytoscape desktop that I can apply to a network:
{}

Example 3 — Show me what layouts Cytoscape desktop supports:
{}

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

## Examples

Example 1 — Apply a force-directed layout to the current network view in Cytoscape desktop:
{"algorithm": "force-directed"}

Example 2 — Arrange nodes in a circle on the current network view in Cytoscape desktop:
{"algorithm": "circular"}

Example 3 — Apply a hierarchical layout to the current network in Cytoscape desktop:
{"algorithm": "hierarchical"}

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
