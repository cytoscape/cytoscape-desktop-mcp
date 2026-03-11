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

**Description:** Create a new instance of a network as a collection(includes the network as root and a view) on Cytoscape Desktop. The network instance will be created from one of multiple data sources: NDEx (by Network Id), a network formatted file, or a tabular formatted file with column mapping. Sets the new network collection instance and view as the current network view on desktop.  Use this whenever a new instance of a network is needed on the desktop. The same nettwork can be laoded as multiple collection instances and is allowed.  There may be other instances of the network loaded on the desktop but that is not of concern for this invocation. This tool will always create a new network collection instance regardless.  To use this tool most effectively, focus on determining the data source from which the new network instance shall be loaded first and foremost.

## Examples

Example 1 — Load NDEx network into cytoscape desktop:
{"source": "ndex", "network_id": "a7e43e3d-c7f8-11ec-8d17-005056ae23aa"}

Example 2 — Load network file into cytoscape desktop:
{"source": "network-file", "file_path": "/path/to/network.sif"}

Example 3 — Load tabular file into cytoscape desktop:
{"source": "tabular-file", "file_path": "/path/to/data.csv", "source_column": "Gene_A", "target_column": "Gene_B", "delimiter_char_code": 44, "use_header_row": true}"}

Example 4 — open network on cytoscape desktop:
{"source": "determine the source such as ndex, or local file(network or tabular) first, then figure out rest of related input params"}



**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "node_attributes_target_columns" : {
      "type" : "array",
      "description" : "Optional. Array of column names from sheet or file  to attach as properties on target nodes. Preview columns from the file(and sheet if applicable) to determine which columns are available.",
      "items" : {
        "type" : "string"
      }
    },
    "node_attributes_sheet_source_key_column" : {
      "type" : "string",
      "description" : "Optional. Column name in the node attributes sheet whose values match source-node IDs in the main network sheet. Used to join attributes onto source nodes.  Preview columns from the file and node attributes sheet to determine which columns are available. Required when node_attributes_sheet is provided."
    },
    "target_column" : {
      "type" : "string",
      "description" : "Optional. Column name for the target (to) node. Preview columns from the file(and sheet if applicable) to determine which is best for target node on a graph edge. Required when source='tabular-file'."
    },
    "source" : {
      "type" : "string",
      "description" : "Required. Determines which import path to use  and is the most important input parameter and must be determined first as the remaining input parameters depend on this value. Must be one of: 'ndex' (load from NDEx by Network ID as UUID), 'network-file' (load a native network format file such as SIF, GML, XGMML, CX, CX2, GraphML, SBML, BioPAX), 'tabular-file' (load a delimited or Excel file with column mapping).",
      "enum" : [ "ndex", "network-file", "tabular-file" ]
    },
    "node_attributes_sheet" : {
      "type" : "string",
      "description" : "Optional. Name of a second Excel sheet containing node attribute columns to join onto the nodes from main network sheet.  Inspect the source file to determine what sheets are available. Applicable for Excel tabular files."
    },
    "use_header_row" : {
      "type" : "boolean",
      "description" : "Optional. Whether the first row contains column headers. Preview columns from the file(and sheet if applicable) to determine if the first row has values which are applicable to be used as headers. If false, ordinal column names are generated. Required when source='tabular-file'."
    },
    "file_path" : {
      "type" : "string",
      "description" : "Optional. Absolute path to the file to import. Required when source='network-file' or source='tabular-file'. Ignored when source='ndex'."
    },
    "delimiter_char_code" : {
      "type" : "integer",
      "description" : "Optional. ASCII code of the column delimiter (e.g. 44=comma, 9=tab).  Use the file extension, or to be more thorough - inspect the source file to determine the delimiter char code. Required when source='tabular-file' and file is not Excel. Ignored for Excel files."
    },
    "source_column" : {
      "type" : "string",
      "description" : "Optional. Column name for the source (from) node. Preview columns from the file(and sheet if applicable) to determine which is best for source node on a graph edge. Required when source='tabular-file'."
    },
    "network_id" : {
      "type" : "string",
      "description" : "Optional. NDEx network Id expressed as UUID string (e.g. 'a7e43e3d-c7f8-11ec-8d17-005056ae23aa'). Required when source='ndex'. Ignored otherwise."
    },
    "node_attributes_source_columns" : {
      "type" : "array",
      "description" : "Optional. Array of column names from the sheet or file  to attach as properties on source nodes. Preview columns from the file(and sheet if applicable) to determine which columns are available.",
      "items" : {
        "type" : "string"
      }
    },
    "excel_sheet" : {
      "type" : "string",
      "description" : "Optional. Name of the Excel sheet containing the network edge data.  Inspect the source file to determine what sheets are available. Required when source='tabular-file' and file is Excel. Ignored for non-Excel files."
    },
    "interaction_column" : {
      "type" : "string",
      "description" : "Optional. Column name for the edge interaction type. Preview columns from the file(and sheet if applicable) to determine which is best for graph edge name. Applicable when source='tabular-file'."
    },
    "node_attributes_sheet_target_key_column" : {
      "type" : "string",
      "description" : "Optional. Column name in the node attributes sheet whose values match target-node IDs in the main network sheet. Used to join attributes onto target nodes.  Preview columns from the file and node attributes sheet to determine which columns are available. Required when node_attributes_sheet is provided."
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

**Description:** List all network collections currently loaded in Cytoscape Desktop with their views, node counts, and edge counts. Use this to discover currently available network SUID and view SUID identifiers present on the desktop. Read-only; does not modify state.

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

**Description:** Set a network and its view as the current (active) selection in Cytoscape Desktop. Use before applying styles, layouts, or analysis to a specific network.

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
      "type" : "integer",
      "description" : "Number of edges in the active network."
    },
    "network_name" : {
      "type" : "string",
      "description" : "Name of the now-active network."
    },
    "node_count" : {
      "type" : "integer",
      "description" : "Number of nodes in the active network."
    },
    "status" : {
      "type" : "string",
      "description" : "Result status, e.g. 'success'."
    }
  }
}
```

---

### `create_network_view`

**Title:** Create Cytoscape Desktop Network View

**Description:** Create a new visual view for a network or retrieve the existing view if at least one already exists. Idempotent, will always result in setting the network and the view as the current selection in Cytoscape Desktop. By default will return an existing view if one exists for the given network rather than creating another view on the network.

## Examples

Example 1 — Create a visual view for a network that has no view in Cytoscape desktop:
{"network_suid": 100}

Example 2 — This network has no view, generate one in Cytoscape desktop:
{"network_suid": 100}

Example 3 — Get existing view or create one if none exist for a network in Cytoscape desktop:
{"network_suid": 100}

Example 4 — Force create a new view in Cytoscape desktop even though one already exists:
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
      "type" : "integer",
      "description" : "Number of edges in the network."
    },
    "network_name" : {
      "type" : "string",
      "description" : "Name of the network as shown in the Cytoscape Desktop network panel."
    },
    "network_suid" : {
      "type" : "integer",
      "description" : "SUID of the network."
    },
    "node_count" : {
      "type" : "integer",
      "description" : "Number of nodes in the network."
    },
    "status" : {
      "type" : "string",
      "description" : "Result status, e.g. 'success' or 'exists'."
    },
    "view_suid" : {
      "type" : "integer",
      "description" : "SUID of the created or existing network view."
    }
  }
}
```

---

### `inspect_tabular_file`

**Title:** Inspect Cytoscape Desktop Import File

**Description:** Inspect a tabular data file to determine whether it is an Excel workbook or a plain-text delimited file. For use when importing network data into Cytoscape Desktop. For Excel files, enumerates sheet names; for text files, detects the delimiter character.

## Examples

Example 1 — What tabular format is a file encoded:
{"file_path": "/path/to/data.xlsx"}

Example 2 — Inspect a file to determine its format for Cytoscape desktop import:
{"file_path": "/path/to/data.csv"}

Example 3 — What sheets are in this Excel workbook:
{"file_path": "/path/to/workbook.xlsx"}

Example 4 — What delimiter is used in a file for importing into Cytoscape desktop:
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

**Description:** Retrieve column headers and first three rows from a tabular file. Use when importing network data into Cytoscape Desktop to preview columns before mapping. Supports both Excel workbooks and plain-text delimited files.

## Examples

Example 1 — Read column headers from a file for Cytoscape desktop import:
{"file_path": "/path/to/data.csv", "delimiter_char_code": 44, "use_header_row": true}

Example 2 — Preview columns from a file:
{"file_path": "/path/to/data.tsv", "delimiter_char_code": 9, "use_header_row": true}

Example 3 — Read columns from an Excel sheet for Cytoscape desktop import:
{"file_path": "/path/to/data.xlsx", "use_header_row": true, "excel_sheet": "Sheet1"}

Example 4 — Get column headers from a file:
Inspect the file first to determine input params as needed.
{"file_path": "/path/to/data.csv", "delimiter_char_code": 44, "use_header_row": true}



**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "delimiter_char_code" : {
      "type" : "integer",
      "description" : "Optional. ASCII code of the delimiter character (e.g. 44=comma, 9=tab, 124=pipe). Required for non-Excel files. Ignored for Excel."
    },
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
      "description" : "Optional. Name of the Excel sheet to read. Required when reading an Excel file. Ignored for text files."
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
      "description" : "Column header names from the file. Ordinal names ('Column 1', 'Column 2', ...) if use_header_row was false.",
      "type" : "array",
      "items" : {
        "type" : "string"
      }
    },
    "sample_rows" : {
      "description" : "Up to the first three data rows in the file, each as an array of string values aligned with columns. The first three rows are included in the response to help determine if column header row is included or not.",
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

**Description:** Compute topological statistics (degree, betweenness centrality, closeness centrality) for the current network. Adds computed values as new columns to the node and edge tables. Returns an error if no network is loaded and set to curent view on desktop.

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
      "description" : "Optional. Default is true. When true, treats the network as a directed graph with in-degree/out-degree metrics. When false, treats it as undirected, typical for most biological interaction networks."
    }
  },
  "required" : [ ]
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
      "description" : "Available layout algorithms.",
      "type" : "array",
      "items" : {
        "type" : "object",
        "properties" : {
          "displayName" : {
            "type" : "string",
            "description" : "Human-readable algorithm label."
          },
          "name" : {
            "type" : "string",
            "description" : "Internal algorithm name to pass to apply_layout."
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

Example 4 — Apply a layout to the current network view in Cytoscape desktop:
{"algorithm": "force-directed"}



**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "algorithm" : {
      "type" : "string",
      "description" : "Required. Internal layout algorithm name to apply. Each algorithm has a 'name' (machine identifier) and a 'displayName' (human-readable label). Supply the 'name' value, not 'displayName'. Determine the layout name by choosing from list of all layout algorithms available in Cytoscape Desktop ."
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
      "type" : "string",
      "description" : "Internal name of the layout algorithm that was applied."
    },
    "displayName" : {
      "type" : "string",
      "description" : "Human-readable display name of the layout algorithm."
    },
    "status" : {
      "type" : "string",
      "description" : "Result status, e.g. 'success'."
    }
  }
}
```

---

### `get_visual_style_defaults`

**Title:** Get Cytoscape Desktop Style Defaults

**Description:** Retrieves all default visual property values for the active Cytoscape Desktop visual style, including node properties, edge properties, available font families and styles, and visual property dependency locks. Use when you need to inspect current styling for the active network, discover all valid property identifiers and their allowed value formats, or audit the full style state before applying any visual changes. Read-only; does not modify state. Returns an error if no network is currently loaded or if the active network has no view, each with a descriptive message indicating the specific cause.

## Examples

Example 1 — Inspect the visual style defaults before applying style changes:
{}

Example 2 — What are the default node and edge colors in the active style:
{}

Example 3 — Discover available node shapes, label fonts, and edge line types with their current values:
{}

Example 4 — Retrieve the full style state to plan a network visualization update:
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
  "$defs" : {
    "VisualPropertyEntry" : {
      "type" : "object",
      "properties" : {
        "allowedValues" : {
          "description" : "Alphabetically sorted list of valid values for discrete types (NodeShape, ArrowShape, LineType, LabelBackgroundShape, EdgeStacking). Use one of these exact strings when setting a default for a discrete property. Absent for continuous numeric types and for Font properties — font-typed properties reference the top-level font_families and font_styles lists instead; compose the full font value as Family-Style-Size (e.g. Arial-Bold-14).",
          "type" : "array",
          "items" : {
            "type" : "string"
          }
        },
        "currentValue" : {
          "type" : "string",
          "description" : "Current default value formatted as a string. For colors this is hex (#RRGGBB); for shapes, line types, and arrows this is the display name (e.g. Ellipse, Solid, Arrow); for Font properties this is Family-Style-Size composed from a family in the top-level font_families list, a style from font_styles, and an integer point size. When used as input to the setter, provide the new desired value in this field using the same format.\n\nExamples: \"#89D0F5\", \"35.0\", \"Dialog-Plain-12\""
        },
        "displayName" : {
          "type" : "string",
          "description" : "Human-readable display name for this visual property.\n\nExamples: \"Node Fill Color\", \"Edge Width\", \"Node Label Font Face\""
        },
        "id" : {
          "type" : "string",
          "description" : "Machine-readable visual property identifier. Use this ID when setting a default value.\n\nExamples: \"NODE_FILL_COLOR\", \"EDGE_WIDTH\", \"NODE_LABEL_FONT_FACE\""
        },
        "maxValue" : {
          "type" : "string",
          "description" : "Maximum valid value. Present only for continuous numeric types (Double, Integer). Values above this maximum will be rejected.\n\nExamples: \"255.0\", \"1.7976931348623157E308\""
        },
        "minValue" : {
          "type" : "string",
          "description" : "Minimum valid value. Present only for continuous numeric types (Double, Integer). Values below this minimum will be rejected.\n\nExamples: \"0.0\", \"0\""
        },
        "valueType" : {
          "type" : "string",
          "description" : "Value type: Paint, Double, Integer, NodeShape, ArrowShape, LineType, LabelBackgroundShape, EdgeStacking, Font, String, or Boolean.\n\nExamples: \"Paint\", \"Double\", \"Font\""
        }
      }
    }
  },
  "type" : "object",
  "properties" : {
    "dependencies" : {
      "description" : "Visual property dependency (lock) relationships for this style. When a dependency is enabled, changing one property in the group automatically adjusts all others.",
      "type" : "array",
      "items" : {
        "type" : "object",
        "properties" : {
          "displayName" : {
            "type" : "string",
            "description" : "Human-readable dependency name.\n\nExamples: \"Lock node width and height\", \"Edge color to arrows\""
          },
          "enabled" : {
            "type" : "boolean",
            "description" : "Whether this dependency is currently enabled. When true, the linked properties move together."
          },
          "id" : {
            "type" : "string",
            "description" : "Machine-readable dependency identifier.\n\nExamples: \"nodeSizeLocked\", \"arrowColorMatchesEdge\""
          },
          "properties" : {
            "description" : "Sorted list of visual property IDs linked by this dependency (e.g. [NODE_HEIGHT, NODE_WIDTH]).",
            "type" : "array",
            "items" : {
              "type" : "string"
            }
          }
        }
      }
    },
    "edge_properties" : {
      "description" : "Edge visual property defaults — one entry per property whose value type has a plain-text representation (colors, numbers, shapes, line types, fonts, booleans). Font-typed entries reference the top-level font_families and font_styles lists for their allowed values.",
      "type" : "array",
      "items" : {
        "$ref" : "#/$defs/VisualPropertyEntry"
      }
    },
    "font_families" : {
      "description" : "Alphabetically sorted list of available font family names installed on the system. Font-typed visual properties in the node_properties and edge_properties arrays reference this shared list rather than carrying their own allowedValues. To compose a font value for updating a style default, select a family name from this list, a style from the font_styles list, and an integer point size, then join them as Family-Style-Size.\n\nExamples: \"Arial\", \"Courier New\", \"SansSerif\"",
      "type" : "array",
      "items" : {
        "type" : "string"
      }
    },
    "font_styles" : {
      "description" : "Valid font style tokens for composing Font property values. When updating a font default, select one of these tokens as the Style component in the Family-Style-Size format.\n\nExamples: \"Plain\", \"Bold\", \"Italic\", \"BoldItalic\"",
      "type" : "array",
      "items" : {
        "type" : "string"
      }
    },
    "node_properties" : {
      "description" : "Node visual property defaults — one entry per property whose value type has a plain-text representation (colors, numbers, shapes, line types, fonts, booleans). Font-typed entries reference the top-level font_families and font_styles lists for their allowed values.",
      "type" : "array",
      "items" : {
        "$ref" : "#/$defs/VisualPropertyEntry"
      }
    },
    "style_name" : {
      "type" : "string",
      "description" : "Name of the active visual style applied to the current network.\n\nExamples: \"default\", \"Marquee\", \"galFiltered Style\""
    }
  }
}
```

---

### `set_visual_default`

**Title:** Set Cytoscape Desktop Style Defaults

**Description:** Sets default visual property values and/or toggles visual property dependency locks in the active Cytoscape Desktop visual style for nodes and/or edges — such as fill color, size, shape, border style, edge width, line type, font, or arrow shape. Use when you want to change how network elements appear by default; retrieve the current style defaults first to discover all valid property identifiers, their allowed value formats, available font families and styles, and dependency lock IDs, then provide only the entries you want to update. Returns an error if no network is currently loaded, if a property identifier is not recognized, or if a value cannot be parsed or falls outside the valid range — each error message identifies the specific property and failure reason. State-mutating; modifies the active visual style defaults and immediately rerenders the current view if one exists.

## Examples

Example 1 — Change the default node fill color to orange and increase node size:
{"node_properties": [{"id": "NODE_FILL_COLOR", "currentValue": "#FF6600"}, {"id": "NODE_SIZE", "currentValue": "45.0"}]}

Example 2 — Set node shape to Rectangle and increase edge width:
{"node_properties": [{"id": "NODE_SHAPE", "currentValue": "Rectangle"}], "edge_properties": [{"id": "EDGE_WIDTH", "currentValue": "3.0"}]}

Example 3 — Style edges with dashed lines and arrow targets:
{"edge_properties": [{"id": "EDGE_LINE_TYPE", "currentValue": "Dash"}, {"id": "EDGE_TARGET_ARROW_SHAPE", "currentValue": "Arrow"}]}

Example 4 — Update the default node label font obtained from the current style state:
{"node_properties": [{"id": "NODE_LABEL_FONT_FACE", "currentValue": "SansSerif-Bold-14"}]}

Example 5 — Set the node label font to bold Arial at 16pt:
{"node_properties": [{"id": "NODE_LABEL_FONT_FACE", "currentValue": "Arial-Bold-16"}]}

Example 6 — Lock node width and height so NODE_SIZE controls both dimensions:
{"dependencies": [{"id": "nodeSizeLocked", "enabled": true}]}

Example 7 — Lock node size and set it to 50 in one call:
{"dependencies": [{"id": "nodeSizeLocked", "enabled": true}], "node_properties": [{"id": "NODE_SIZE", "currentValue": "50.0"}]}

**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "dependencies" : {
      "type" : "array",
      "description" : "Optional. List of dependency locks to toggle on/off. Each entry requires an 'id' field matching a dependency identifier from the style defaults response and an 'enabled' field (true/false) indicating whether the lock should be active. Retrieve the current style defaults first to discover available dependency IDs and their current enabled state.\n\nExamples: [{\"id\": \"nodeSizeLocked\", \"enabled\": true}], [{\"id\": \"arrowColorMatchesEdge\", \"enabled\": false}]"
    },
    "edge_properties" : {
      "type" : "array",
      "description" : "Optional. List of edge visual property updates. Each entry requires 'id' and 'currentValue' using the same format conventions as node properties — property identifiers, value types, allowed values, font families, and font styles are documented in the style defaults response.\n\nExamples: [{\"id\": \"EDGE_WIDTH\", \"currentValue\": \"3.0\"}], [{\"id\": \"EDGE_LINE_TYPE\", \"currentValue\": \"Dash\"}], [{\"id\": \"EDGE_LABEL_FONT_FACE\", \"currentValue\": \"Courier New-Italic-12\"}]"
    },
    "node_properties" : {
      "type" : "array",
      "description" : "Optional. List of node visual property updates. Each entry requires an 'id' field matching a property identifier from the style defaults response and a 'currentValue' field with the new default value as a string, formatted according to the property's value type and allowed values documented in that response. For font properties, compose the value as Family-Style-Size using a family from the font_families list and a style from the font_styles list in the style defaults response.\n\nExamples: [{\"id\": \"NODE_FILL_COLOR\", \"currentValue\": \"#FF6600\"}], [{\"id\": \"NODE_SHAPE\", \"currentValue\": \"Rectangle\"}], [{\"id\": \"NODE_LABEL_FONT_FACE\", \"currentValue\": \"Arial-Bold-14\"}]"
    }
  },
  "required" : [ ]
}
```

**Output Schema:**

```json
{
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "type" : "object",
  "properties" : {
    "note" : {
      "type" : "string",
      "description" : "Informational note present only when no network view is currently active. The style defaults were updated successfully but the visual change cannot be rendered until a view is created. Absent when a view is present."
    },
    "status" : {
      "type" : "string",
      "description" : "Outcome of the operation. Always 'success' for non-error responses.\n\nExamples: \"success\""
    },
    "style_name" : {
      "type" : "string",
      "description" : "Name of the active visual style that was modified.\n\nExamples: \"default\", \"Marquee\""
    },
    "updated_dependencies" : {
      "description" : "Dependencies that were toggled, each showing its new enabled state. Absent when no dependency toggles were requested.",
      "type" : "array",
      "items" : {
        "type" : "object",
        "properties" : {
          "displayName" : {
            "type" : "string",
            "description" : "Human-readable display name of the toggled dependency.\n\nExamples: \"Lock node width and height\", \"Edge color to arrows\""
          },
          "enabled" : {
            "type" : "boolean",
            "description" : "New enabled state after the toggle was applied."
          },
          "id" : {
            "type" : "string",
            "description" : "Machine-readable dependency identifier that was toggled.\n\nExamples: \"nodeSizeLocked\", \"arrowColorMatchesEdge\""
          }
        }
      }
    },
    "updated_properties" : {
      "description" : "Properties that were updated, each showing its new default value as confirmed by reading back from the style after mutation.",
      "type" : "array",
      "items" : {
        "type" : "object",
        "properties" : {
          "currentValue" : {
            "type" : "string",
            "description" : "New default value after update, formatted as a string. Colors use hex (#RRGGBB); shapes, line types, and arrows use their display name; fonts use Family-Style-Size format; numbers use decimal notation.\n\nExamples: \"#FF6600\", \"3.0\", \"Arial-Bold-14\""
          },
          "displayName" : {
            "type" : "string",
            "description" : "Human-readable display name of the updated property.\n\nExamples: \"Node Fill Color\", \"Edge Width\""
          },
          "id" : {
            "type" : "string",
            "description" : "Machine-readable visual property identifier that was updated.\n\nExamples: \"NODE_FILL_COLOR\", \"EDGE_WIDTH\", \"NODE_LABEL_FONT_FACE\""
          }
        }
      }
    }
  }
}
```

---

### `get_mappable_properties`

**Title:** List Cytoscape Desktop Mappable Properties

**Description:** List all visual properties that support data-driven mappings in the active Cytoscape Desktop visual style, grouped by node and edge categories with each property's mapping compatibility type and any currently applied mapping. Use when you need to discover which visual properties can be mapped to data columns, determine what kind of mapping (continuous, discrete, or passthrough) each property supports, or inspect existing mappings before creating or replacing them. Read-only; does not modify state. Returns an error if no network is currently loaded or if the active network has no view, each with a descriptive message indicating the specific cause.

## Examples

Example 1 — List all mappable properties to see what styling options are available for data-driven visualization:
{}

Example 2 — What properties currently have data-driven mappings applied to the active style:
{}

Example 3 — Which node properties support continuous gradient mapping for numeric data columns:
{}

Example 4 — Check if any existing mappings will be overwritten before creating a new one:
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
  "$defs" : {
    "MappablePropertyEntry" : {
      "type" : "object",
      "properties" : {
        "continuousSubType" : {
          "type" : "string",
          "description" : "Indicates what kind of continuous mapping this property supports. 'color-gradient' for color properties that interpolate between colors. 'continuous' for numeric properties that interpolate between numbers. 'discrete' for discrete-typed properties (shapes, line types) that use threshold-based switching in continuous mappings. Absent for properties that do not support continuous mapping at all (e.g. String, Boolean) — these properties only support discrete or passthrough mappings.\n\nExamples: \"color-gradient\", \"continuous\", \"discrete\""
        },
        "currentMapping" : {
          "type" : "object",
          "properties" : {
            "column" : {
              "type" : "string",
              "description" : "Name of the data column driving this mapping.\n\nExamples: \"Degree\", \"GeneType\", \"name\""
            },
            "summary" : {
              "type" : "string",
              "description" : "Human-readable summary of the mapping. For continuous mappings shows the column name and the value range of the first and last breakpoints. For discrete mappings shows the column name and entry count. For passthrough mappings shows the column name with a passthrough indicator.\n\nExamples: \"Degree → 10.0–50.0\", \"GeneType → 5 entries\", \"name (passthrough)\""
            },
            "type" : {
              "type" : "string",
              "description" : "Mapping type: ContinuousMapping (numeric interpolation or color gradient), DiscreteMapping (explicit value-to-value map), or PassthroughMapping (column value used directly).\n\nExamples: \"ContinuousMapping\", \"DiscreteMapping\", \"PassthroughMapping\""
            }
          },
          "description" : "Details of the mapping currently applied to this property in the active visual style. Absent when no mapping is applied — the property uses its default value instead."
        },
        "displayName" : {
          "type" : "string",
          "description" : "Human-readable display name for this visual property.\n\nExamples: \"Node Fill Color\", \"Node Size\", \"Edge Width\""
        },
        "id" : {
          "type" : "string",
          "description" : "Machine-readable visual property identifier. Use this ID when creating a mapping for this property.\n\nExamples: \"NODE_FILL_COLOR\", \"NODE_SIZE\", \"EDGE_WIDTH\""
        },
        "valueType" : {
          "type" : "string",
          "description" : "Value type: Paint, Double, Integer, NodeShape, ArrowShape, LineType, LabelBackgroundShape, EdgeStacking, Font, String, or Boolean.\n\nExamples: \"Paint\", \"Double\", \"NodeShape\""
        }
      }
    }
  },
  "type" : "object",
  "properties" : {
    "edge_properties" : {
      "description" : "Edge visual properties that support data-driven mappings — one entry per property whose value type has a plain-text representation (colors, numbers, shapes, line types, fonts, booleans). Each entry indicates the property's continuous mapping compatibility and any currently applied mapping.",
      "type" : "array",
      "items" : {
        "$ref" : "#/$defs/MappablePropertyEntry"
      }
    },
    "node_properties" : {
      "description" : "Node visual properties that support data-driven mappings — one entry per property whose value type has a plain-text representation (colors, numbers, shapes, line types, fonts, booleans). Each entry indicates the property's continuous mapping compatibility and any currently applied mapping.",
      "type" : "array",
      "items" : {
        "$ref" : "#/$defs/MappablePropertyEntry"
      }
    },
    "style_name" : {
      "type" : "string",
      "description" : "Name of the active visual style applied to the current network.\n\nExamples: \"default\", \"Marquee\", \"galFiltered Style\""
    }
  }
}
```

---



## Prompts

none

## Resources

none

## Resource Templates

none
