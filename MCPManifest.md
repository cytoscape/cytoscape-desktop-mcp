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

**Description:** Create a new instance of a network as a collection(includes the network as root and a view) on Cytoscape Desktop. The network instance will be created from one of multiple data sources: NDEx (by Network Id), a network formatted file, or a tabular formatted file with column mapping. Sets the new network collection instance and view as the current network view on desktop.  Use this whenever a new instance of a network is needed on the desktop. The same nettwork can be laoded as multiple collection instances and is allowed.  There may be other instances of the network loaded on the desktop but that is not of concern for this invocation. This tool will always create a new network collection instance regardless.  To use this tool most effectively, focus on determining the input source parameter first from which the new network instance shall be loaded foremost.  Then follow the specific requirements for the chosen source type via the rest of conditional input parameters. It is VERY important to identify each conditional input parameter related to a chosen source type before executing the tool. The more details provided via the additional conditional input parameters which are related to a chosen source type, the better the resulting network instance will be for the context. 

## Examples

Example 1 — Load NDEx network into cytoscape desktop:
{"source": "ndex", "network_id": {"waived": false, "parameter": "a7e43e3d-c7f8-11ec-8d17-005056ae23aa"}}

Example 2 — Load network file into cytoscape desktop:
{"source": "network-file", "file_path": {"waived": false, "parameter": "/path/to/network.sif"}}

Example 3 — Load tabular file into cytoscape desktop - an example that will trigger asking for the conditional input parameters related to tabular file type :
{"source": "tabular-file", "file_path": {"waived": false, "parameter": "/path/to/data.csv"}, "source_column": {"waived": false, "parameter": "Gene_A"}, "target_column": {"waived": false, "parameter": "Gene_B"}, "delimiter_char_code": {"waived": false, "parameter": 44}, "use_header_row": {"waived": false, "parameter": true}, "interaction_column": {"waived": true, "parameter": null}, "node_attributes_source_columns": {"waived": false, "parameter": [{"name": "Score", "inferred_data_type": "double"}]}, "node_attributes_target_columns": {"waived": false, "parameter": [{"name": "Score", "inferred_data_type": "double"}]}, "edge_columns": {"waived": true, "parameter": null}}

Example 4 — open network on cytoscape desktop:
{"source": "determine the source such as ndex, or local file(network or tabular) first, then figure out rest of related input params"}



**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "source" : {
      "type" : "string",
      "description" : "Required. Determines which import path to use  and is the most important input parameter and must be determined first as the remaining input parameters depend on this value. Must be one of: 'ndex' (load from NDEx by Network ID as UUID), 'network-file' (load a native network format file such as SIF, GML, XGMML, CX, CX2, GraphML, SBML, BioPAX), 'tabular-file' (load a delimited or Excel file with column mapping).",
      "enum" : [ "ndex", "network-file", "tabular-file" ]
    },
    "network_id" : {
      "type" : "object",
      "description" : "Conditional on source='ndex'. NDEx network Id expressed as UUID string (e.g. 'a7e43e3d-c7f8-11ec-8d17-005056ae23aa'). Required when source='ndex'. Ignored otherwise. Confirm with the user before setting or waiving.",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "string"
        }
      }
    },
    "file_path" : {
      "type" : "object",
      "description" : "Conditional on source='network-file' or source='tabular-file'. Absolute path to the file to import. Required when source='network-file' or source='tabular-file'. Ignored when source='ndex'. Confirm the path with the user before setting or waiving.",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "string"
        }
      }
    },
    "source_column" : {
      "type" : "object",
      "description" : "Conditional on source='tabular-file'. Column name for the source (from) node. Preview columns from the file (and sheet if applicable) to determine which is best for source node on a graph edge. Required when source='tabular-file'. Confirm the column choice with the user before setting or waiving.",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "string"
        }
      }
    },
    "target_column" : {
      "type" : "object",
      "description" : "Conditional on source='tabular-file'. Column name for the target (to) node. Preview columns from the file (and sheet if applicable) to determine which is best for target node on a graph edge. Required when source='tabular-file'. Confirm the column choice with the user before setting or waiving.",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "string"
        }
      }
    },
    "interaction_column" : {
      "type" : "object",
      "description" : "Conditional on source='tabular-file'. Column name for the edge interaction type. Preview columns from the file (and sheet if applicable) to determine which is best for graph edge name. Applicable when source='tabular-file'. Confirm with the user whether an interaction column should be mapped or waived.",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "string"
        }
      }
    },
    "delimiter_char_code" : {
      "type" : "object",
      "description" : "Conditional on source='tabular-file' with a non-Excel file (i.e. excel_sheet not set). ASCII code of the column delimiter (e.g. 44=comma, 9=tab). Use the file extension, or inspect the source file to determine the delimiter. Required when source='tabular-file' and file is not Excel. Ignored for Excel files (use excel_sheet instead). Confirm with the user by inspecting the file before setting or waiving.",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "integer"
        }
      }
    },
    "use_header_row" : {
      "type" : "object",
      "description" : "Conditional on source='tabular-file'. Whether the first row contains column headers. Preview columns from the file (and sheet if applicable) to determine if the first row has values suitable as headers. If false, ordinal column names are generated. Required when source='tabular-file'. Confirm with the user by inspecting the file before setting or waiving.",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "boolean"
        }
      }
    },
    "excel_sheet" : {
      "type" : "object",
      "description" : "Conditional on source='tabular-file' with an Excel file (mutually exclusive with delimiter_char_code — set one or the other, not both). Name of the Excel sheet containing the network edge data. Inspect the source file to determine what sheets are available. Required when source='tabular-file' and file is Excel. Ignored for non-Excel files (use delimiter_char_code instead). Confirm with the user which sheet to use before setting or waiving.",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "string"
        }
      }
    },
    "node_attributes_sheet" : {
      "type" : "object",
      "description" : "Conditional on source='tabular-file' and excel_sheet being set. Name of the Excel sheet containing node attribute columns to join onto the nodes from the main network sheet. Inspect the source file to determine what sheets are available. Applicable for Excel tabular files only. Confirm with the user whether a separate node attributes sheet should be used before setting or waiving.",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "string"
        }
      }
    },
    "node_attributes_sheet_source_key_column" : {
      "type" : "object",
      "description" : "Conditional on node_attributes_sheet being provided. Column name in the node attributes sheet whose values match source-node IDs in the main network sheet. Used to join attributes onto source nodes. Preview columns from the node attributes sheet to determine which columns are available. Required when node_attributes_sheet is provided. Confirm the key column with the user before setting or waiving.",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "string"
        }
      }
    },
    "node_attributes_sheet_target_key_column" : {
      "type" : "object",
      "description" : "Conditional on node_attributes_sheet being provided. Column name in the node attributes sheet whose values match target-node IDs in the main network sheet. Used to join attributes onto target nodes. Preview columns from the node attributes sheet to determine which columns are available. Required when node_attributes_sheet is provided. Confirm the key column with the user before setting or waiving.",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "string"
        }
      }
    },
    "node_attributes_source_columns" : {
      "type" : "object",
      "description" : "Conditional on source='tabular-file'.  Preview columns from the file (and sheet if applicable) ask the user which file columns should be attached as attributes on source nodes or none is allowed. Populate with the user's confirmed selections or set as empty list if none chosen. Each entry is a DataColumn object (name + inferred_data_type). Inspect the file's columns using other tooling available to retrieve column entries, then pass the user's confirmed selections here. Waive only when the user has explicitly declined all source node attribute mapping.",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "array",
          "items" : {
            "$schema" : "https://json-schema.org/draft/2020-12/schema",
            "type" : "object",
            "properties" : {
              "inferred_data_type" : {
                "type" : "string",
                "enum" : [ "string", "integer", "long", "double", "boolean" ],
                "description" : "Data type inferred from sample values in this column. Maps 1-to-1 onto Cytoscape table column types: string→String, integer→Integer (32-bit), long→Long (64-bit), double→Double (64-bit float), boolean→Boolean. Preserve this value when passing the column to a network import — ensures the correct Cytoscape table column type instead of defaulting to string."
              },
              "name" : {
                "type" : "string",
                "description" : "Column name exactly as it appears in the file header."
              }
            }
          }
        }
      }
    },
    "node_attributes_target_columns" : {
      "type" : "object",
      "description" : "Conditional on source='tabular-file'.  Preview columns from the file (and sheet if applicable) ask the user which file columns should be attached as attributes on target nodes or none is allowed. Populate with the user's confirmed selections or set as empty list if none chosen. Each entry is a DataColumn object (name + inferred_data_type). Inspect the file's columns using other tooling available to retrieve column entries, then pass the user's confirmed selections here. Waive only when the user has explicitly declined all target node attribute mapping.",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "array",
          "items" : {
            "$schema" : "https://json-schema.org/draft/2020-12/schema",
            "type" : "object",
            "properties" : {
              "inferred_data_type" : {
                "type" : "string",
                "enum" : [ "string", "integer", "long", "double", "boolean" ],
                "description" : "Data type inferred from sample values in this column. Maps 1-to-1 onto Cytoscape table column types: string→String, integer→Integer (32-bit), long→Long (64-bit), double→Double (64-bit float), boolean→Boolean. Preserve this value when passing the column to a network import — ensures the correct Cytoscape table column type instead of defaulting to string."
              },
              "name" : {
                "type" : "string",
                "description" : "Column name exactly as it appears in the file header."
              }
            }
          }
        }
      }
    },
    "edge_columns" : {
      "type" : "object",
      "description" : "Conditional on source='tabular-file'.  Populate with remaining file columns the user has NOT already explicitly confirmed to be mapped to source nodes, target nodes, or edge interaction or node attributes. This provides ability to specify more correct data types.",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "array",
          "items" : {
            "$schema" : "https://json-schema.org/draft/2020-12/schema",
            "type" : "object",
            "properties" : {
              "inferred_data_type" : {
                "type" : "string",
                "enum" : [ "string", "integer", "long", "double", "boolean" ],
                "description" : "Data type inferred from sample values in this column. Maps 1-to-1 onto Cytoscape table column types: string→String, integer→Integer (32-bit), long→Long (64-bit), double→Double (64-bit float), boolean→Boolean. Preserve this value when passing the column to a network import — ensures the correct Cytoscape table column type instead of defaulting to string."
              },
              "name" : {
                "type" : "string",
                "description" : "Column name exactly as it appears in the file header."
              }
            }
          }
        }
      }
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

**Description:** List all network collections currently loaded in Cytoscape Desktop with their views, node counts, edge counts, active view indicator, and applied visual style name. Use this to discover available network and view identifiers, determine which view is currently active, and see what visual style each view is using. Read-only; does not modify state.

## Examples

Example 1 — List networks currently open in Cytoscape desktop:
{}

Example 2 — What networks are loaded in Cytoscape desktop:
{}

Example 3 — Show me the network SUIDs available in Cytoscape desktop:
{}

Example 4 — Check which network view is currently active and what style it uses:
{}

Example 5 — Discover what style is applied to each loaded network:
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
          "is_current" : {
            "type" : "boolean",
            "description" : "Whether this network view is the currently active (selected) view in Cytoscape Desktop. Exactly one entry in the list will have this set to true. Use to identify which network the user is currently working on.\n\nExamples: true, false"
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
          "style_name" : {
            "type" : "string",
            "description" : "Name of the visual style applied to this network view. Absent when the network has no view. Use to determine the active styling context for each network — this name corresponds to the style names returned by the style listing tool and can be used with the style switching tool.\n\nExamples: \"default\", \"Marquee\", \"Directed\""
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
      "type" : "object",
      "description" : "Discretionary. Controls whether a new view is created when the network already has views. Set to true (waived=false, parameter=true) to always create a new view even when views exist. Set to false (waived=false, parameter=false) to return the existing current view (or first available) without creating a duplicate. Waive to accept the default behaviour (false). Confirm with the user before setting or waiving.\n\nExamples: {\"waived\": false, \"parameter\": true}, {\"waived\": false, \"parameter\": false}, {\"waived\": true}",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "boolean"
        }
      }
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

**Description:** Retrieve column headers and first three rows from a tabular file. Use when loading tabular network data into Cytoscape Desktop in order to preview columns and advise about node and edge attribute mapping potential. Supports both Excel workbooks and plain-text delimited files.

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
    "file_path" : {
      "type" : "string",
      "description" : "Required. Absolute path to the tabular file."
    },
    "use_header_row" : {
      "type" : "boolean",
      "description" : "Required. If true, the first row is treated as column headers and those strings appear in 'columns'. If false, ordinal names are generated ('Column 1', 'Column 2', ...) and those ordinal names appear in 'columns' instead."
    },
    "delimiter_char_code" : {
      "type" : "object",
      "description" : "Conditional on file type derived from file_path. Required when file_path is a non-Excel file (CSV, TSV, pipe-delimited, etc.). ASCII code of the column delimiter (44=comma, 9=tab, 124=pipe). Waive when file_path is an Excel file (.xlsx/.xls) — use excel_sheet instead. Inspect the file extension to determine which applies. Confirm with the user before setting or waiving.",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "integer"
        }
      }
    },
    "excel_sheet" : {
      "type" : "object",
      "description" : "Conditional on file type derived from file_path. Required when file_path is an Excel file (.xlsx/.xls). Name of the Excel sheet to read. Waive for non-Excel files — use delimiter_char_code instead. Inspect the file extension and available sheets to determine the correct one. Confirm the sheet name with the user before setting or waiving.",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "string"
        }
      }
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
      "description" : "Columns found in the file, each with its name and an inferred data type. This helps advise any potential node or edge attribute mapping efforts when loading a network from this file. The inferred types ensure Cytoscape creates table columns correctly instead of defaulting everything to string.",
      "type" : "array",
      "items" : {
        "type" : "object",
        "properties" : {
          "inferred_data_type" : {
            "type" : "string",
            "enum" : [ "string", "integer", "long", "double", "boolean" ],
            "description" : "Data type inferred from sample values in this column. Maps 1-to-1 onto Cytoscape table column types: string→String, integer→Integer (32-bit), long→Long (64-bit), double→Double (64-bit float), boolean→Boolean. Preserve this value when passing the column to a network import — ensures the correct Cytoscape table column type instead of defaulting to string."
          },
          "name" : {
            "type" : "string",
            "description" : "Column name exactly as it appears in the file header."
          }
        }
      }
    },
    "sample_rows" : {
      "description" : "Up to the first three data rows in the file, each as an array of string values aligned with columns. The first three rows are included in the response to help determine if column header row is present and infer the data type of the column from the sample values.",
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
      "type" : "object",
      "description" : "Required. Confirm whether the network should be analyzed as directed or undirected — the algorithm produces fundamentally different metrics for each mode. Set to true (waived=false, parameter=true) to treat the network as directed, computing in-degree and out-degree. Set to false (waived=false, parameter=false) to treat it as undirected, computing degree for all nodes. This parameter cannot be waived: ask the user whether their network is directed or undirected before invoking.\n\nExamples: {\"waived\": false, \"parameter\": true}, {\"waived\": false, \"parameter\": false}",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "boolean"
        }
      }
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
            "description" : "Internal algorithm identifier used to apply a layout to the active network view."
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

**Description:** Retrieves all default visual property values for the active Cytoscape Desktop visual style. Use when you need to inspect current styling for the active network, discover all valid property identifiers and their allowed value formats, or audit the full style state before applying any visual changes. See the output schema for the complete result structure including node/edge properties, font options, and dependency locks. Read-only; does not modify state.

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

**Description:** Sets default visual property values and/or toggles visual property dependency locks in the active Cytoscape Desktop visual style for nodes and/or edges — such as fill color, size, shape, border style, edge width, line type, font, or arrow shape. Use when you want to change how network elements appear by default; retrieve the current style defaults first to discover all valid property identifiers, their allowed value formats, available font families and styles, and dependency lock IDs, then provide only the entries you want to update. The prior retrieval of style defaults includes the active style name — mention this name as confirmation to the user before invoking this tool so they know which style is being modified. The only way a style can be edited is through this tool which requires the style be set on current view. State-mutating; modifies the active visual style defaults and immediately rerenders the current view.

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
    "node_properties" : {
      "type" : "object",
      "description" : "Discretionary. List of node visual property updates. Each entry requires an 'id' field matching a property identifier from the style defaults response and a 'currentValue' field with the new default value as a string, formatted according to the property's value type and allowed values documented in that response. For font properties, compose the value as Family-Style-Size using a family from the font_families list and a style from the font_styles list in the style defaults response. Waive if no node visual defaults need updating. Confirm with the user which node properties to update before setting or waiving.\n\nExamples: [{\"id\": \"NODE_FILL_COLOR\", \"currentValue\": \"#FF6600\"}], [{\"id\": \"NODE_SHAPE\", \"currentValue\": \"Rectangle\"}], [{\"id\": \"NODE_LABEL_FONT_FACE\", \"currentValue\": \"Arial-Bold-14\"}]",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "array"
        }
      }
    },
    "edge_properties" : {
      "type" : "object",
      "description" : "Discretionary. List of edge visual property updates. Each entry requires 'id' and 'currentValue' using the same format conventions as node properties — property identifiers, value types, allowed values, font families, and font styles are documented in the style defaults response. Waive if no edge visual defaults need updating. Confirm with the user which edge properties to update before setting or waiving.\n\nExamples: [{\"id\": \"EDGE_WIDTH\", \"currentValue\": \"3.0\"}], [{\"id\": \"EDGE_LINE_TYPE\", \"currentValue\": \"Dash\"}], [{\"id\": \"EDGE_LABEL_FONT_FACE\", \"currentValue\": \"Courier New-Italic-12\"}]",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "array"
        }
      }
    },
    "dependencies" : {
      "type" : "object",
      "description" : "Discretionary. List of dependency locks to toggle on/off. Each entry requires an 'id' field matching a dependency identifier from the style defaults response and an 'enabled' field (true/false) indicating whether the lock should be active. Retrieve the current style defaults first to discover available dependency IDs and their current enabled state. Waive if no dependency locks need changing. Confirm with the user before setting or waiving.\n\nExamples: [{\"id\": \"nodeSizeLocked\", \"enabled\": true}], [{\"id\": \"arrowColorMatchesEdge\", \"enabled\": false}]",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "array"
        }
      }
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

**Description:** List all visual properties that support data-driven mappings in the active Cytoscape Desktop visual style, grouped by node and edge categories with each property's mapping compatibility type and any currently applied mapping. Use when you need to discover which visual properties can be mapped to data columns, determine what kind of mapping (continuous, discrete, or passthrough) each property supports, or inspect existing mappings before creating or replacing them. Read-only; does not modify state.

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

### `get_compatible_columns`

**Title:** Get Cytoscape Desktop Mapping Columns

**Description:** List data columns from the active Cytoscape Desktop network that are compatible with one or more visual properties for data-driven mapping, with each column's supported mapping types (continuous, discrete, passthrough) and sample values. Use when planning which data columns can drive a mapping for specific visual properties, or to verify mapping type support before creating a mapping. Always batch multiple property IDs in a single call — see property_ids for input details and the output schema for result structure. Read-only; does not modify state.

## Examples

Example 1 — Check which data columns can drive a color mapping on nodes:
{"property_ids": ["NODE_FILL_COLOR"]}

Example 2 — Query compatible columns for multiple node properties at once to plan a complete node styling:
{"property_ids": ["NODE_FILL_COLOR", "NODE_SIZE", "NODE_LABEL"]}

Example 3 — Find columns compatible with edge width and edge color for edge styling:
{"property_ids": ["EDGE_WIDTH", "EDGE_STROKE_UNSELECTED_PAINT"]}

Example 4 — Batch query for data columns related to speparate node and edge properties in one call:
{"property_ids": ["NODE_FILL_COLOR", "EDGE_WIDTH"]}

**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "property_ids" : {
      "type" : "array",
      "description" : "Required. One or more visual property identifiers to query compatible data columns for. Use the property IDs returned by the mappable properties listing (e.g. NODE_FILL_COLOR, NODE_SIZE, EDGE_WIDTH). Accepts a single property for focused queries or multiple properties to batch-retrieve compatible columns for several visual properties in one call.\n\nExamples: [\"NODE_FILL_COLOR\"], [\"NODE_SIZE\", \"NODE_LABEL\", \"NODE_SHAPE\"]",
      "items" : {
        "type" : "string"
      }
    }
  },
  "required" : [ "property_ids" ]
}
```

**Output Schema:**

```json
{
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "type" : "object",
  "properties" : {
    "properties" : {
      "type" : "object",
      "description" : "Map from visual property ID to its compatible columns and metadata. Each key is a property ID string as provided in the request (e.g. NODE_FILL_COLOR, EDGE_WIDTH). Each value contains the property's display name, value type, which table it reads from, and the list of compatible data columns with their mapping support flags."
    }
  }
}
```

---

### `get_column_range`

**Title:** Get Cytoscape Desktop Column Range

**Description:** Computes min, max, mean, and non-null count for numeric node or edge table columns in the active Cytoscape Desktop network. Use before setting up continuous mapping breakpoints to understand a column's data range. Always batch multiple columns in a single call to avoid redundant round trips — see column_names for input details and the output schema for result and per-column error structure. Read-only; does not modify state.

## Examples

Example 1 — Get the range of a single degree column to plan continuous mapping breakpoints for node size:
{"column_names": ["Degree"], "table": "node"}

Example 2 — Batch: get ranges for three node columns in one call to plan multiple continuous mappings without extra round trips:
{"column_names": ["Degree", "BetweennessCentrality", "expression"], "table": "node"}

Example 3 — Batch: check ranges for two edge columns before creating continuous mappings for edge width and edge opacity:
{"column_names": ["weight", "confidence"], "table": "edge"}

Example 4 — Batch: inspect all numeric node columns needed to configure a full visual style in a single round trip:
{"column_names": ["Degree", "ClusterCoefficient", "Score"], "table": "node"}

**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "column_names" : {
      "type" : "array",
      "description" : "Required. One or more column names to compute range statistics for. Pass all columns you need in a single call — each is processed independently and its result (or per-column error) appears in the response map. Must be a non-empty list. Each column must be a numeric type (Integer, Long, or Double).\n\nExamples: [\"Degree\"], [\"Degree\", \"BetweennessCentrality\", \"expression\"]",
      "items" : {
        "type" : "string",
        "description" : "Name of a numeric column in the specified table."
      }
    },
    "table" : {
      "type" : "string",
      "description" : "Required. Which data table to query — node table or edge table. Applies to all columns in the list.\n\nExamples: \"node\", \"edge\"",
      "enum" : [ "node", "edge" ]
    }
  },
  "required" : [ "column_names", "table" ]
}
```

**Output Schema:**

```json
{
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "type" : "object",
  "properties" : {
    "columns" : {
      "type" : "object",
      "description" : "Map of column name to range statistics or per-column error. Each key is a column name from the request."
    }
  }
}
```

---

### `get_column_distinct_values`

**Title:** Get Cytoscape Desktop Column Values

**Description:** Enumerates distinct non-null values with occurrence counts for node or edge table columns in the active Cytoscape Desktop network. Use before creating discrete mappings to discover the full set of categorical values a column contains. Always batch multiple columns in a single call to avoid redundant round trips — see column_names for input details and the output schema for result and per-column error structure. Read-only; does not modify state.

## Examples

Example 1 — Enumerate distinct values in a single column to plan a discrete color mapping:
{"column_names": ["GeneType"], "table": "node"}

Example 2 — Batch: enumerate values for two columns in one call to plan discrete mappings for both node color and node shape without extra round trips:
{"column_names": ["GeneType", "community"], "table": "node"}

Example 3 — Batch: list distinct values for multiple edge columns to map each interaction type and confidence tier to a different visual style:
{"column_names": ["interaction", "tier"], "table": "edge"}

Example 4 — Batch: resolve all categorical columns needed for a full discrete visual style in a single round trip:
{"column_names": ["GeneType", "community", "isHub"], "table": "node"}

Example 5 — Limit returned values for a high-cardinality column:
{"column_names": ["name"], "table": "node", "max_values": 10}

**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "column_names" : {
      "type" : "array",
      "description" : "Required. One or more column names to enumerate distinct values for. Pass all columns you need in a single call — each is processed independently and its result (or per-column error) appears in the response map. Must be a non-empty list. Works with any non-list column type: String, Integer, Long, Double, or Boolean.\n\nExamples: [\"GeneType\"], [\"GeneType\", \"community\", \"interaction\"]",
      "items" : {
        "type" : "string",
        "description" : "Name of a column in the specified table."
      }
    },
    "table" : {
      "type" : "string",
      "description" : "Required. Which data table to query — node table or edge table. Applies to all columns in the list.\n\nExamples: \"node\", \"edge\"",
      "enum" : [ "node", "edge" ]
    },
    "max_values" : {
      "type" : "object",
      "description" : "Discretionary. Maximum number of distinct values to return per column. Values are sorted by count descending and clipped to this limit before returning. Compare values.size() to count to detect clipping. Waive to accept the default limit of 50. Increase if you need the full value set for a high-cardinality column. Confirm with the user before setting or waiving.\n\nExamples: {\"waived\": false, \"parameter\": 50}, {\"waived\": false, \"parameter\": 100}, {\"waived\": true}",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "integer"
        }
      }
    }
  },
  "required" : [ "column_names", "table" ]
}
```

**Output Schema:**

```json
{
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "type" : "object",
  "properties" : {
    "columns" : {
      "type" : "object",
      "description" : "Map of column name to distinct-values result or per-column error. Each key is a column name from the request."
    }
  }
}
```

---

### `create_continuous_mapping`

**Title:** Create Cytoscape Desktop Continuous Mapping

**Description:** Create a continuous data-driven visual mapping in the active Cytoscape Desktop visual style, linking a numeric data column to a visual property through user-defined breakpoints. Use when you want node or edge appearance to vary continuously with data — such as mapping expression values to a color gradient, degree centrality to node size, or interaction score to edge width. State-mutating; replaces any existing mapping on the target property and immediately rerenders the current view.

## Examples

Example 1 — Map node degree to size (small degree = 10px, large degree = 60px):
{"property_id": "NODE_SIZE", "column_name": "Degree", "column_type": "Integer", "points": [{"value": 1, "lesser": 10, "equal": 10, "greater": 10}, {"value": 45, "lesser": 60, "equal": 60, "greater": 60}]}

Example 2 — Change color gradient of nodes from blue to red based on expression:
{"property_id": "NODE_FILL_COLOR", "column_name": "expression", "column_type": "Double", "points": [{"value": -2.0, "lesser": "#0000FF", "equal": "#0000FF", "greater": "#FFFFFF"}, {"value": 0.0, "lesser": "#FFFFFF", "equal": "#FFFFFF", "greater": "#FFFFFF"}, {"value": 2.0, "lesser": "#FFFFFF", "equal": "#FF0000", "greater": "#FF0000"}]}

Example 3 — Map betweenness centrality to edge width with three breakpoints:
{"property_id": "EDGE_WIDTH", "column_name": "BetweennessCentrality", "column_type": "Double", "points": [{"value": 0.0, "lesser": 1.0, "equal": 1.0, "greater": 1.0}, {"value": 0.5, "lesser": 3.0, "equal": 3.0, "greater": 3.0}, {"value": 1.0, "lesser": 8.0, "equal": 8.0, "greater": 8.0}]}

Example 4 — "Change node color based on centrality": before invoking, confirm with the user that they want continuous gradient interpolation — color varying smoothly with the numeric data — rather than discrete color assignment per value. If confirmed continuous, ask which centrality column to use and for at least two breakpoints (a low value with its color and a high value with its color).

**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "points" : {
      "type" : "array",
      "description" : "Required. Minimum 2 breakpoints. Defines the piecewise continuous mapping function: each breakpoint anchors the visual property at a specific data value, and Cytoscape interpolates between adjacent breakpoints — so at least one lower and one upper anchor is needed to form a valid range. Breakpoints must be in ascending order by value; the tool sorts them automatically but rejects duplicates. Each entry has: value (number — the data value at this breakpoint anchor); lesser (property value applied for data values strictly below this breakpoint); equal (property value applied for data values exactly at this breakpoint); greater (property value applied for data values strictly above this breakpoint). For color-gradient properties (Paint), use hex strings (#RRGGBB). For discrete-typed properties (NodeShape, LineType), use display names. For numeric properties (Double, Integer), use numbers.",
      "items" : {
        "type" : "object",
        "description" : "A breakpoint entry with fields: value (number), lesser (property value below this point), equal (property value at this point), greater (property value above this point)."
      }
    },
    "property_id" : {
      "type" : "string",
      "description" : "Required. Visual property ID (e.g. NODE_FILL_COLOR, NODE_SIZE, EDGE_WIDTH). Retrieve the available style properties in the active style using other tooling available."
    },
    "column_name" : {
      "type" : "string",
      "description" : "Required. Name of the numeric data column driving the mapping. Query numeric network columns compatible with continuous mapping for the chosen property using other tooling available."
    },
    "column_type" : {
      "type" : "string",
      "description" : "Required. Java type of the data column.",
      "enum" : [ "Integer", "Long", "Double" ]
    }
  },
  "required" : [ "property_id", "column_name", "column_type", "points" ]
}
```

**Output Schema:**

```json
{
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "type" : "object",
  "properties" : {
    "column" : {
      "type" : "string",
      "description" : "Name of the data column driving the mapping.\n\nExamples: \"Degree\", \"expression\""
    },
    "displayName" : {
      "type" : "string",
      "description" : "Human-readable display name of the visual property.\n\nExamples: \"Node Size\", \"Node Fill Color\""
    },
    "mapping_type" : {
      "type" : "string",
      "description" : "Always 'ContinuousMapping' for this tool."
    },
    "points_count" : {
      "type" : "integer",
      "description" : "Number of breakpoints added to the mapping."
    },
    "property_id" : {
      "type" : "string",
      "description" : "Machine-readable ID of the visual property mapped.\n\nExamples: \"NODE_SIZE\", \"NODE_FILL_COLOR\""
    },
    "status" : {
      "type" : "string",
      "description" : "Outcome. Always 'success' for non-error responses.\n\nExamples: \"success\""
    }
  }
}
```

---

### `create_discrete_mapping`

**Title:** Create Cytoscape Desktop Discrete Mapping

**Description:** Create a discrete data-driven visual mapping in the active Cytoscape Desktop visual style, assigning a specific visual property value to each distinct data column value. Best suited for columns with a small number of distinct values (tens, not hundreds) — use this tool when the user can meaningfully specify a visual property value for each distinct data value (e.g. three gene types → three colors, two interaction types → two line styles); for columns with many distinct values, consider auto-generated discrete mapping options instead. State-mutating; replaces any existing mapping on the target property and immediately rerenders the current view.

IMPORTANT — before invoking this tool, you MUST retrieve the complete set of distinct column values from the network using other available tools. Never ask the user to enumerate column values — they exist in the network data and must be fetched by the agent. Only ask the user to specify the visual property value (color, shape, line style, etc.) to assign to each distinct value you have already retrieved.

## Examples

Example 1 — Map gene type to node fill color:
{"property_id": "NODE_FILL_COLOR", "column_name": "GeneType", "column_type": "String", "entries": {"kinase": "#FF0000", "receptor": "#00AA00", "TF": "#0000FF"}}

Example 2 — Map node class to shape:
{"property_id": "NODE_SHAPE", "column_name": "class", "column_type": "String", "entries": {"gene": "Ellipse", "protein": "Diamond", "drug": "Round Rectangle"}}

Example 3 — Map interaction type to edge line style:
{"property_id": "EDGE_LINE_TYPE", "column_name": "interaction", "column_type": "String", "entries": {"activates": "Solid", "inhibits": "Dash"}}

Example 4 — "Color each node based on data": this is ambiguous — do not assume discrete. Before invoking, confirm with the user that they want a discrete mapping (an explicit color assigned to each distinct value) rather than a continuous gradient. Only if the user confirms discrete intent, ask which categorical column to use and which color to assign to each distinct value.

**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "entries" : {
      "type" : "object",
      "description" : "Required. Map of column value (as string key) to visual property value allowed for the visual style property id specified by property_id. Minimum 1 entry; maximum 1000 entries — the tool returns an error if either limit is violated. This tool is designed for columns with a small number of distinct values (typically tens); if the column has hundreds of distinct values, consider auto-generated discrete mapping options instead. Keys are the column's distinct values expressed as strings (e.g. \"23\" for Integer 23, \"true\" for Boolean). Values: hex for colors (#RRGGBB), display names for shapes (Ellipse, Diamond), display names for line types (Solid, Dash), numbers for numeric properties."
    },
    "property_id" : {
      "type" : "string",
      "description" : "Required. Visual property ID (e.g. NODE_FILL_COLOR, NODE_SHAPE, EDGE_LINE_TYPE). Retrieve the available style properties in the active style using other tooling available."
    },
    "column_name" : {
      "type" : "string",
      "description" : "Required. Name of the data column driving the mapping. Query compatible network columns for the chosen property using other tooling available."
    },
    "column_type" : {
      "type" : "string",
      "description" : "Required. Java type of the data column. All five types are valid for discrete mapping.",
      "enum" : [ "Integer", "Long", "Double", "String", "Boolean" ]
    }
  },
  "required" : [ "property_id", "column_name", "column_type", "entries" ]
}
```

**Output Schema:**

```json
{
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "type" : "object",
  "properties" : {
    "column" : {
      "type" : "string",
      "description" : "Name of the data column driving the mapping.\n\nExamples: \"GeneType\", \"class\""
    },
    "displayName" : {
      "type" : "string",
      "description" : "Human-readable display name of the visual property.\n\nExamples: \"Node Fill Color\", \"Node Shape\""
    },
    "entries_count" : {
      "type" : "integer",
      "description" : "Number of entries added to the discrete mapping."
    },
    "mapping_type" : {
      "type" : "string",
      "description" : "Always 'DiscreteMapping' for this tool."
    },
    "property_id" : {
      "type" : "string",
      "description" : "Machine-readable ID of the visual property mapped.\n\nExamples: \"NODE_FILL_COLOR\", \"NODE_SHAPE\""
    },
    "status" : {
      "type" : "string",
      "description" : "Outcome. Always 'success' for non-error responses.\n\nExamples: \"success\""
    }
  }
}
```

---

### `create_discrete_mapping_generated`

**Title:** Create Cytoscape Desktop Auto Mapping

**Description:** Create a discrete data-driven visual mapping in the active Cytoscape Desktop visual style where property values are auto-generated for every distinct column value — no manual entry needed. Prefer this tool over the manual discrete mapping tool when a column has more than a dozen or so distinct values, or whenever you want the user to simply choose a generator style rather than enumerate discrete mapping entries themselves. The caller must select from given generator algorithms that determine how visual property values are produced across the full set of discrete values; refer to the generator input parameter for the available algorithm choices and their compatibility requirements. State-mutating; creates or replaces a discrete mapping on the active visual style and immediately rerenders the current view.

## Examples

Example 1 — "Automatically color the nodes by discrete values in GeneType column":
{"property_id": "NODE_FILL_COLOR", "column_name": "GeneType", "column_type": "String", "generator": "rainbow"}

Example 2 — "Auto-generate a shape mapping for the discrete community column values":
{"property_id": "NODE_SHAPE", "column_name": "community", "column_type": "Integer", "generator": "shape_cycle"}

Example 3 — "Generate a blue color gradient for the discrete values in tissue column":
{"property_id": "NODE_FILL_COLOR", "column_name": "tissue", "column_type": "String", "generator": "brewer_sequential", "generator_params": {"hue": "blue"}}

Example 4 — "Create a discrete size mapping based on the Degree column automatically":
{"property_id": "NODE_SIZE", "column_name": "Degree", "column_type": "Integer", "generator": "numeric_range", "generator_params": {"min": 10, "max": 60}}

**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "property_id" : {
      "type" : "string",
      "description" : "Required. Visual property ID (e.g. NODE_FILL_COLOR, NODE_SHAPE, EDGE_WIDTH). Retrieve the available style properties in the active style using other tooling available."
    },
    "column_type" : {
      "type" : "string",
      "description" : "Required. Java type of the data column. All five types are valid for discrete mapping.",
      "enum" : [ "Integer", "Long", "Double", "String", "Boolean" ]
    },
    "column_name" : {
      "type" : "string",
      "description" : "Required. Name of the data column to drive the mapping. Query compatible network columns for the chosen property using other tooling available."
    },
    "generator" : {
      "type" : "object",
      "description" : "Conditional on property_id. Required when the property supports multiple generators (Paint properties such as NODE_FILL_COLOR, EDGE_STROKE_UNSELECTED_PAINT) — choose from rainbow, random, or brewer_sequential. May be waived when only one generator is compatible: shape_cycle is auto-selected for discrete shape/line-type properties (NODE_SHAPE, EDGE_LINE_TYPE, etc.); numeric_range is auto-selected for numeric properties (NODE_SIZE, EDGE_WIDTH, etc.) — waive and the tool selects it. Confirm with the user before setting or waiving.",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "string"
        }
      }
    },
    "generator_params" : {
      "type" : "object",
      "description" : "Conditional on generator. Required when generator='numeric_range' — supply an object with keys 'min' (number) and 'max' (number) defining the value range spread. Provide with key 'hue' (string, e.g. 'blue', 'red', 'green', 'purple') when generator='brewer_sequential' to select the palette family; waive to use the default ('blue'). Waive when generator is rainbow, random, or shape_cycle — params are not used by those algorithms. Confirm with the user before setting or waiving.",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "object"
        }
      }
    }
  },
  "required" : [ "property_id", "column_name", "column_type" ]
}
```

**Output Schema:**

```json
{
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "type" : "object",
  "properties" : {
    "column" : {
      "type" : "string",
      "description" : "Name of the data column driving the mapping.\n\nExamples: \"GeneType\", \"community\""
    },
    "displayName" : {
      "type" : "string",
      "description" : "Human-readable display name of the visual property.\n\nExamples: \"Node Fill Color\", \"Node Shape\""
    },
    "entries_count" : {
      "type" : "integer",
      "description" : "Total number of distinct column values mapped (one mapping entry per value)."
    },
    "generator" : {
      "type" : "string",
      "description" : "Generator algorithm that was applied.\n\nExamples: \"rainbow\", \"shape_cycle\""
    },
    "mapping_type" : {
      "type" : "string",
      "description" : "Always 'DiscreteMapping' for this tool."
    },
    "property_id" : {
      "type" : "string",
      "description" : "Machine-readable ID of the visual property mapped.\n\nExamples: \"NODE_FILL_COLOR\", \"NODE_SHAPE\""
    },
    "sample_entries" : {
      "type" : "object",
      "description" : "Sample of the first up to five generated mapping entries as {columnValue → formattedPropertyValue} pairs. Colors are hex strings; shapes and line types are display names."
    },
    "status" : {
      "type" : "string",
      "description" : "Outcome. Always 'success' for non-error responses.\n\nExamples: \"success\""
    }
  }
}
```

---

### `create_passthrough_mapping`

**Title:** Create Cytoscape Desktop Passthrough Mapping

**Description:** Create a passthrough visual mapping in the active Cytoscape Desktop visual style, using a data column's value directly as the visual property value without transformation. Use when a column already holds the exact value needed for a visual property — most commonly mapping a name or identifier column to a label property so each node or edge displays its data value as a visible label. State-mutating; replaces any existing mapping on the target property and immediately rerenders the current view.

## Examples

Example 1 — Label nodes with the name column:
{"property_id": "NODE_LABEL", "column_name": "name", "column_type": "String"}

Example 2 — Label edges with the interaction type:
{"property_id": "EDGE_LABEL", "column_name": "interaction", "column_type": "String"}

Example 3 — Show node tooltip from description column:
{"property_id": "NODE_TOOLTIP", "column_name": "description", "column_type": "String"}

Example 4 — "Show node names": this is a clear passthrough request. Use other available tools to confirm the column name (e.g. "name", "label", "id") that exists in the network data before invoking — do not ask the user to type the column name if it can be discovered.

**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "property_id" : {
      "type" : "string",
      "description" : "Required. Visual property ID (e.g. NODE_LABEL, EDGE_LABEL, NODE_TOOLTIP)."
    },
    "column_type" : {
      "type" : "string",
      "description" : "Required. Java type of the data column. All five types are valid; the column value will be rendered as-is by Cytoscape.",
      "enum" : [ "Integer", "Long", "Double", "String", "Boolean" ]
    },
    "column_name" : {
      "type" : "string",
      "description" : "Required. Name of the data column whose values will be used directly as the visual property value. The column must already exist in the node or edge table of the current network — use other available tools to confirm available columns before invoking."
    }
  },
  "required" : [ "property_id", "column_name", "column_type" ]
}
```

**Output Schema:**

```json
{
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "type" : "object",
  "properties" : {
    "column" : {
      "type" : "string",
      "description" : "Name of the data column whose values drive the mapping.\n\nExamples: \"name\", \"interaction\""
    },
    "displayName" : {
      "type" : "string",
      "description" : "Human-readable display name of the visual property.\n\nExamples: \"Node Label\", \"Edge Label\""
    },
    "mapping_type" : {
      "type" : "string",
      "description" : "Always 'PassthroughMapping' for this tool."
    },
    "property_id" : {
      "type" : "string",
      "description" : "Machine-readable ID of the visual property mapped.\n\nExamples: \"NODE_LABEL\", \"EDGE_LABEL\""
    },
    "status" : {
      "type" : "string",
      "description" : "Outcome. Always 'success' for non-error responses.\n\nExamples: \"success\""
    }
  }
}
```

---

### `get_styles`

**Title:** List Cytoscape Desktop Styles

**Description:** Retrieves the names of all visual styles currently registered in Cytoscape Desktop. Use when you need to discover available styles before switching a view to a different style, or to present style choices to the user. To determine which style is currently active on a specific view, retrieve the list of loaded network views which includes each view's applied style name. Read-only; does not modify state.

## Examples

Example 1 — What styles are available?:
{}

Example 2 — List all visual styles so I can pick one:
{}

Example 3 — Show me the style names in Cytoscape:
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
    "styles" : {
      "description" : "Alphabetically sorted list of all visual style names registered in Cytoscape Desktop. Each name can be provided to the style switching tool to apply that style to the current network view, or used as a clone source when creating a new style.\n\nExamples: [\"default\", \"Marquee\", \"Nested Network Style\"]",
      "type" : "array",
      "items" : {
        "type" : "string"
      }
    }
  }
}
```

---

### `switch_current_style`

**Title:** Switch Cytoscape Desktop Style

**Description:** Switches the current network view to use an existing visual style, or creates a new named style cloned from the current style and applies it. Use when the user wants to change which style is applied to the current network view. Returns an error if the style name is not found when create=false, a style by the new name already exists when create=true, or no network view is currently active.

## Examples

Example 1 — Switch the current view to an existing style:
{"name": "Marquee"}

Example 2 — Choose a different style for the current network:
{"name": "Directed"}

Example 3 — Create a new style cloned from the current style:
{"name": "My Analysis Style", "create": true}

**Input Schema:**

```json
{
  "type" : "object",
  "properties" : {
    "name" : {
      "type" : "string",
      "description" : "Required. Name of the visual style to switch to or create. If a style with this name already exists in Cytoscape Desktop, the current network view is switched to use it. If no style by this name exists, a new style is created only when 'create' is true — otherwise an error is returned indicating the style was not found.\n\nExamples: \"Marquee\", \"My Custom Style\", \"Publication Ready\""
    },
    "create" : {
      "type" : "object",
      "description" : "Required. Confirm whether to create a new style or switch to an existing one — the semantics of 'name' differ completely between the two modes. Set to false (waived=false, parameter=false) to switch the current network view to an existing style matching 'name' — an error is returned if the style is not found. Set to true (waived=false, parameter=true) to create a new style by cloning default property values and mappings from the currently applied style, then apply it — an error is returned if a style named 'name' already exists. This parameter cannot be waived: confirm the user's intent (find vs create) before invoking.\n\nExamples: {\"waived\": false, \"parameter\": true}, {\"waived\": false, \"parameter\": false}",
      "properties" : {
        "waived" : {
          "type" : "boolean",
          "description" : "Imperative: set to true only after direct user confirmation that this parameter should be intentionally omitted. Set to false when providing a value in the parameter field. Never assume or default — this requires explicit user confirmation or unambiguous contextual evidence in the current interaction."
        },
        "parameter" : {
          "type" : "boolean"
        }
      }
    }
  },
  "required" : [ "name" ]
}
```

**Output Schema:**

```json
{
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "type" : "object",
  "properties" : {
    "error_msg" : {
      "type" : "string",
      "description" : "Descriptive error message present only when status is false. Explains the specific reason the operation failed in a well-formed sentence — such as the style not being found, a style by the new name already being registered, or no network view being active. Absent when the operation succeeds.\n\nExamples: \"Style 'NonExistent' was not found among registered styles.\", \"Cannot create style 'My Style' because a style with that name already exists.\", \"No network view is currently active in Cytoscape Desktop.\""
    },
    "status" : {
      "type" : "boolean",
      "description" : "Whether the style switch was successful. When true, the current network view is now using the style specified by the request. When false, the error_msg field explains what went wrong.\n\nExamples: true, false"
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
