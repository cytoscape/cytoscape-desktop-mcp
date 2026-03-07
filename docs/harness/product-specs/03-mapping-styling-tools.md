# Tool Spec: Mapping Styling Tools

## 4. MCP Tool Schemas

### 4.1 `get_mappable_properties`

```json
{
  "name": "get_mappable_properties",
  "title": "List Cytoscape Desktop Mappable Properties",
  "description": "List all visual properties that support data-driven mappings in Cytoscape Desktop, grouped by node and edge categories. Shows the property ID, display name, value type, and current mapping (if any).",
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
  "content": [{
    "type": "text",
    "text": "{\"style_name\":\"default\",\"node_properties\":[{\"id\":\"NODE_FILL_COLOR\",\"displayName\":\"Node Fill Color\",\"valueType\":\"Paint\",\"continuousSubType\":\"color-gradient\",\"currentMapping\":null},{\"id\":\"NODE_SIZE\",\"displayName\":\"Node Size\",\"valueType\":\"Double\",\"continuousSubType\":\"continuous\",\"currentMapping\":{\"type\":\"ContinuousMapping\",\"column\":\"Degree\",\"summary\":\"Degree → 10.0–50.0\"}},{\"id\":\"NODE_SHAPE\",\"displayName\":\"Node Shape\",\"valueType\":\"NodeShape\",\"continuousSubType\":\"discrete\",\"currentMapping\":null},{\"id\":\"NODE_LABEL\",\"displayName\":\"Node Label\",\"valueType\":\"String\",\"continuousSubType\":null,\"currentMapping\":{\"type\":\"PassthroughMapping\",\"column\":\"name\",\"summary\":\"name (passthrough)\"}}],\"edge_properties\":[{\"id\":\"EDGE_STROKE_UNSELECTED_PAINT\",\"displayName\":\"Edge Stroke Color (Unselected)\",\"valueType\":\"Paint\",\"continuousSubType\":\"color-gradient\",\"currentMapping\":null},{\"id\":\"EDGE_WIDTH\",\"displayName\":\"Edge Width\",\"valueType\":\"Double\",\"continuousSubType\":\"continuous\",\"currentMapping\":null}]}"
  }],
  "isError": false
}
```

### 4.2 `get_compatible_columns`

```json
{
  "name": "get_compatible_columns",
  "title": "Get Cytoscape Desktop Mapping Columns",
  "description": "List data columns compatible with a given visual property for mapping in Cytoscape Desktop. Filters columns by type compatibility (e.g., continuous mappings require numeric columns). Returns column names, types, and sample values.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "property_id": {
        "type": "string",
        "description": "Visual property ID string (e.g., 'NODE_FILL_COLOR')."
      }
    },
    "required": ["property_id"]
  }
}
```

**Success response:**
```json
{
  "content": [{
    "type": "text",
    "text": "{\"property_id\":\"NODE_FILL_COLOR\",\"table\":\"node\",\"columns\":[{\"name\":\"Degree\",\"type\":\"Integer\",\"sampleValues\":[1,4,12,23],\"supportsMapping\":{\"continuous\":true,\"discrete\":true,\"passthrough\":false}},{\"name\":\"BetweennessCentrality\",\"type\":\"Double\",\"sampleValues\":[0.0,0.15,0.87],\"supportsMapping\":{\"continuous\":true,\"discrete\":true,\"passthrough\":false}},{\"name\":\"GeneType\",\"type\":\"String\",\"sampleValues\":[\"kinase\",\"receptor\",\"TF\"],\"supportsMapping\":{\"continuous\":false,\"discrete\":true,\"passthrough\":false}}]}"
  }],
  "isError": false
}
```

### 4.3 `get_column_range`

```json
{
  "name": "get_column_range",
  "title": "Get Cytoscape Desktop Column Range",
  "description": "Get the min, max, and mean values for a numeric column in the node or edge table in Cytoscape Desktop. Used to determine the data range for continuous mappings.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "column_name": {
        "type": "string",
        "description": "Name of the data column."
      },
      "table": {
        "type": "string",
        "enum": ["node", "edge"],
        "description": "Whether to query the node table or edge table."
      }
    },
    "required": ["column_name", "table"]
  }
}
```

**Success response:**
```json
{
  "content": [{ "type": "text", "text": "{\"column_name\":\"Degree\",\"type\":\"Integer\",\"min\":1,\"max\":45,\"mean\":8.3,\"count\":150}" }],
  "isError": false
}
```

### 4.4 `get_column_distinct_values`

```json
{
  "name": "get_column_distinct_values",
  "title": "Get Cytoscape Desktop Column Values",
  "description": "Get the distinct values in a column along with their occurrence counts in Cytoscape Desktop. Used for discrete mapping setup.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "column_name": {
        "type": "string",
        "description": "Name of the data column."
      },
      "table": {
        "type": "string",
        "enum": ["node", "edge"],
        "description": "Whether to query the node table or edge table."
      }
    },
    "required": ["column_name", "table"]
  }
}
```

**Success response:**
```json
{
  "content": [{ "type": "text", "text": "{\"column_name\":\"GeneType\",\"type\":\"String\",\"distinct_count\":5,\"values\":[{\"value\":\"kinase\",\"count\":23},{\"value\":\"receptor\",\"count\":18},{\"value\":\"TF\",\"count\":45},{\"value\":\"phosphatase\",\"count\":12},{\"value\":\"other\",\"count\":52}]}" }],
  "isError": false
}
```

### 4.5 `create_continuous_mapping`

```json
{
  "name": "create_continuous_mapping",
  "title": "Create Cytoscape Desktop Continuous Mapping",
  "description": "Create a continuous visual mapping in Cytoscape Desktop from a numeric data column to a visual property. Supports numeric interpolation (sizes, widths, transparency), color gradients (fill colors, border colors), and threshold-based discrete assignment (shapes, line types). Breakpoints define how data values map to property values.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "property_id": {
        "type": "string",
        "description": "Visual property ID string (e.g., 'NODE_SIZE', 'NODE_FILL_COLOR')."
      },
      "column_name": {
        "type": "string",
        "description": "Name of the numeric data column."
      },
      "column_type": {
        "type": "string",
        "enum": ["Integer", "Long", "Double"],
        "description": "Java type of the data column."
      },
      "points": {
        "type": "array",
        "description": "Breakpoints defining the mapping function. Each point maps a data value to a property value. For values between breakpoints, the property value is interpolated (for numeric/color) or uses the nearest boundary (for discrete types).",
        "items": {
          "type": "object",
          "properties": {
            "value": {
              "type": "number",
              "description": "The data value at this breakpoint."
            },
            "lesser": {
              "description": "Property value for data values less than this breakpoint.",
              "oneOf": [{ "type": "string" }, { "type": "number" }]
            },
            "equal": {
              "description": "Property value for data values equal to this breakpoint.",
              "oneOf": [{ "type": "string" }, { "type": "number" }]
            },
            "greater": {
              "description": "Property value for data values greater than this breakpoint.",
              "oneOf": [{ "type": "string" }, { "type": "number" }]
            }
          },
          "required": ["value", "lesser", "equal", "greater"]
        },
        "minItems": 2
      }
    },
    "required": ["property_id", "column_name", "column_type", "points"]
  }
}
```

**Success response:**
```json
{
  "content": [{ "type": "text", "text": "{\"status\":\"success\",\"property_id\":\"NODE_SIZE\",\"displayName\":\"Node Size\",\"mapping_type\":\"ContinuousMapping\",\"column\":\"Degree\",\"points_count\":2}" }],
  "isError": false
}
```

### 4.6 `create_discrete_mapping`

```json
{
  "name": "create_discrete_mapping",
  "title": "Create Cytoscape Desktop Discrete Mapping",
  "description": "Create a discrete visual mapping in Cytoscape Desktop from a data column to a visual property. Each distinct column value is explicitly mapped to a specific property value.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "property_id": {
        "type": "string",
        "description": "Visual property ID string."
      },
      "column_name": {
        "type": "string",
        "description": "Name of the data column."
      },
      "column_type": {
        "type": "string",
        "enum": ["Integer", "Long", "Double", "String", "Boolean"],
        "description": "Java type of the data column."
      },
      "entries": {
        "type": "object",
        "description": "Map of column value (as string key) to property value. Property values follow the same format conventions as set_visual_default: hex for colors, display names for shapes, numbers for sizes.",
        "additionalProperties": {
          "oneOf": [{ "type": "string" }, { "type": "number" }, { "type": "boolean" }]
        }
      }
    },
    "required": ["property_id", "column_name", "column_type", "entries"]
  }
}
```

**Success response:**
```json
{
  "content": [{ "type": "text", "text": "{\"status\":\"success\",\"property_id\":\"NODE_FILL_COLOR\",\"displayName\":\"Node Fill Color\",\"mapping_type\":\"DiscreteMapping\",\"column\":\"GeneType\",\"entries_count\":5}" }],
  "isError": false
}
```

### 4.7 `create_passthrough_mapping`

```json
{
  "name": "create_passthrough_mapping",
  "title": "Create Cytoscape Desktop Passthrough Mapping",
  "description": "Create a passthrough visual mapping in Cytoscape Desktop that uses a column's value directly as the visual property value. Commonly used for labels (column value becomes the label text).",
  "inputSchema": {
    "type": "object",
    "properties": {
      "property_id": {
        "type": "string",
        "description": "Visual property ID string (typically NODE_LABEL or EDGE_LABEL)."
      },
      "column_name": {
        "type": "string",
        "description": "Name of the data column."
      },
      "column_type": {
        "type": "string",
        "enum": ["Integer", "Long", "Double", "String", "Boolean"],
        "description": "Java type of the data column."
      }
    },
    "required": ["property_id", "column_name", "column_type"]
  }
}
```

**Success response:**
```json
{
  "content": [{ "type": "text", "text": "{\"status\":\"success\",\"property_id\":\"NODE_LABEL\",\"displayName\":\"Node Label\",\"mapping_type\":\"PassthroughMapping\",\"column\":\"name\"}" }],
  "isError": false
}
```

### 4.8 `create_discrete_mapping_generated`

```json
{
  "name": "create_discrete_mapping_generated",
  "title": "Create Cytoscape Desktop Auto Mapping",
  "description": "Create a discrete mapping in Cytoscape Desktop where property values are auto-generated for all distinct column values. Useful when there are many categories. Supports Rainbow (evenly-spaced hues), Random, and Brewer palette generators for colors; shape cycling for shapes; and evenly-distributed numeric ranges for sizes.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "property_id": {
        "type": "string",
        "description": "Visual property ID string."
      },
      "column_name": {
        "type": "string",
        "description": "Name of the data column."
      },
      "column_type": {
        "type": "string",
        "enum": ["Integer", "Long", "Double", "String", "Boolean"],
        "description": "Java type of the data column."
      },
      "generator": {
        "type": "string",
        "enum": ["rainbow", "random", "brewer_sequential", "shape_cycle", "numeric_range"],
        "description": "Generator algorithm to use."
      },
      "generator_params": {
        "type": "object",
        "description": "Parameters for the generator. For 'numeric_range': { min, max }. For 'brewer_sequential': { hue: 'blue'|'red'|'green'|'purple' }. Others need no params.",
        "properties": {
          "min": { "type": "number", "description": "Minimum value for numeric_range generator." },
          "max": { "type": "number", "description": "Maximum value for numeric_range generator." },
          "hue": { "type": "string", "description": "Base hue for brewer_sequential generator." }
        }
      }
    },
    "required": ["property_id", "column_name", "column_type", "generator"]
  }
}
```

**Success response:**
```json
{
  "content": [{ "type": "text", "text": "{\"status\":\"success\",\"property_id\":\"NODE_FILL_COLOR\",\"displayName\":\"Node Fill Color\",\"mapping_type\":\"DiscreteMapping\",\"column\":\"GeneType\",\"generator\":\"rainbow\",\"entries_count\":25,\"sample_entries\":{\"kinase\":\"#FF0000\",\"receptor\":\"#FF9900\",\"TF\":\"#33FF00\",\"phosphatase\":\"#00FFCC\",\"other\":\"#0066FF\"}}" }],
  "isError": false
}
```

## 6. Edge Cases and Error Handling

### 6.1 No Network Loaded

- **Trigger**: User invokes mapping_styling with no network in Cytoscape.
- **Tool behavior**: All tools return error.
- **Agent script**: "No network is currently loaded. Please load a network first."

### 6.2 No Data Columns Available

- **Trigger**: Network has only internal columns (SUID, selected) and no user data.
- **Tool behavior**: `get_compatible_columns` returns empty list.
- **Agent script**: "There are no data columns available for mapping. You may need to import additional data or run network analysis first to generate columns like Degree and BetweennessCentrality."

### 6.3 Column Has All Null Values

- **Trigger**: Selected column exists but all rows are null.
- **Tool behavior**: `get_column_range` or `get_column_distinct_values` returns error.
- **Agent script**: "The column '{name}' has no non-null values. Please choose a different column."

### 6.4 Continuous Mapping on Non-Numeric Column

- **Trigger**: User selects continuous mapping for a String column.
- **Agent behavior**: The `get_compatible_columns` tool already filters this — continuous won't be listed as supported. If the user explicitly requests it, say: "Continuous mappings require a numeric column. '{column_name}' is a text column. You can use discrete or passthrough mapping instead."

### 6.5 Discrete Mapping with Very Many Values (>100)

- **Trigger**: Column has hundreds of distinct values (e.g., gene names).
- **Agent behavior**: Even with auto-generation, this many entries can be slow. Warn: "This column has {count} distinct values. Auto-generating {count} mappings may take a moment." Proceed with generator.

### 6.6 Overwriting Existing Mapping

- **Trigger**: User creates a mapping for a property that already has one.
- **Tool behavior**: The tool removes the existing mapping before adding the new one. This is intentional.
- **Agent script**: "Note: this will replace the existing mapping ({old_summary}). Proceeding..."

### 6.7 Incompatible Mapping Type for Property

- **Trigger**: User tries passthrough on a non-String property.
- **Agent behavior**: The mapping type selection in Step 3 only shows compatible options. If somehow attempted, the tool returns an error.

### 6.8 DiscreteMappingGenerator Not Available (OSGi)

- **Trigger**: Rainbow/Random generators from `vizmap-gui-impl` are not accessible.
- **Fallback**: The `create_discrete_mapping_generated` tool implements simple generators directly (HSB-based Rainbow, seeded Random, shade interpolation) without relying on `vizmap-gui-impl` OSGi services.

### 6.9 Thread Safety — Swing EDT

- **Issue**: `VisualStyle.addVisualMappingFunction()`, `removeVisualMappingFunction()`, and `apply()` may trigger view updates that must happen on the Swing Event Dispatch Thread.
- **Implementation**: Wrap all style mutation + view update calls in `SwingUtilities.invokeAndWait()`.

### 6.10 Continuous Mapping Breakpoint Order

- **Requirement**: Breakpoints in the `points` array must be in ascending order by `value`. The tool should sort them if needed, or return an error if duplicates exist.
- **Validation**: Check `points[i].value < points[i+1].value` for all i.

### 6.11 Color Parsing for Discrete Entries

- **Trigger**: User enters a color name like "red" instead of hex.
- **Handling**: The `parseValue` helper should support CSS named colors in addition to hex. If an unrecognized name is provided, return an error listing supported formats.

### 6.12 Edge vs. Node Table Ambiguity

- **Trigger**: A property is selected but the tool needs to know whether to query node or edge table.
- **Resolution**: The `get_mappable_properties` response tags each property as node or edge. The `get_compatible_columns` tool determines this from the property's position in the visual lexicon hierarchy (descendant of NODE vs EDGE root).
