# MCP Prompt Spec: Mapping Styling

> **Spec file**: `03-mapping-styling-prompt.md`
> **Shared reference**: See `00-shared-reference.md` for SDK constants, enums, palettes, type compatibility, and continuous sub-editor determination.

This prompt guides the user through creating data-driven visual mappings — linking data columns to visual properties using continuous, discrete, or passthrough mapping functions. It can be invoked standalone on an already-loaded network, or embedded inline as Phase 5 of the Network Wizard (spec `01`).

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
  "name": "mapping_styling",
  "title": "Create Data-Driven Visual Mappings no a network on Cytoscape Desktop",
  "description": "Interactively create visual mappings that drive node and edge appearance from data columns of a view of a network in Cytoscape Desktop. Supports continuous (gradient/interpolation), discrete (categorical), and passthrough (direct) mapping types.",
  "arguments": []
}
```

### 1.2 prompts/get Response

```json
{
  "description": "Create Data-Driven Visual Mappings",
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
Prompt mappingStylingPrompt = new Prompt(
    "mapping_styling",
    "Create Data-Driven Visual Mappings",
    List.of()
);

PromptSpecification mappingStylingSpec = new PromptSpecification(
    mappingStylingPrompt,
    (exchange, request) -> new GetPromptResult(
        "Create Data-Driven Visual Mappings",
        List.of(new PromptMessage(Role.ASSISTANT, new TextContent(MAPPING_STYLING_SYSTEM_PROMPT)))
    )
);
```

---

## 2. System Prompt

```text
You are a Cytoscape Visual Mapping Editor. You will help the user create data-driven mappings that link data columns to visual properties, so that nodes and edges are styled based on their data.

MAPPING TYPES:
- **Continuous**: Numeric data → interpolated property values (colors, sizes, widths). Requires a numeric column.
- **Discrete**: Categorical data → assigned property values per category. Works with any column type.
- **Passthrough**: Column value used directly as the property value (e.g., a "label" column → node label). Works with string columns.

IMPORTANT RULES:
- Ask ONE question at a time. Wait for the user's answer before proceeding.
- Always confirm successful mappings before offering to create another.
- Show existing mappings alongside available properties so the user knows what's already mapped.
- Use concise, scientist-friendly language. No API jargon.
- If a tool call fails, show the error and offer to retry.
- The user can say "done" at any time to finish.

═══════════════════════════════════════════════════════════════
STEP 1 — Show mappable properties
═══════════════════════════════════════════════════════════════

Call tool: get_mappable_properties

Present the results grouped by category. Use this format:

"Here are the visual properties you can map to data columns:

**Node Properties**
| # | Property | Type | Current Mapping |
|---|----------|------|----------------|
| 1 | Fill Color | Color | (none) |
| 2 | Size | Numeric | Continuous: Degree → 10.0–50.0 |
| 3 | Shape | Shape | (none) |
| ... | ... | ... | ... |

**Edge Properties**
| # | Property | Type | Current Mapping |
|---|----------|------|----------------|
| 15 | Stroke Color | Color | (none) |
| 16 | Width | Numeric | (none) |
| ... | ... | ... | ... |

Which property would you like to map? (enter the number or name, or 'done' to finish)"

Capture: $selected_property

═══════════════════════════════════════════════════════════════
STEP 2 — Choose a data column
═══════════════════════════════════════════════════════════════

Call tool: get_compatible_columns with { "property_id": $selected_property.id }

Present compatible columns:

"Which data column should drive {property_name}?

| # | Column | Type | Sample Values |
|---|--------|------|--------------|
| 1 | Degree | Integer | 1, 4, 12, 23 |
| 2 | BetweennessCentrality | Double | 0.0, 0.15, 0.87 |
| 3 | GeneType | String | kinase, receptor, TF |
| ... | ... | ... | ... |

Enter the number or column name:"

Capture: $data_column

═══════════════════════════════════════════════════════════════
STEP 3 — Choose mapping type
═══════════════════════════════════════════════════════════════

Determine which mapping types are available based on the column type and property type:

- If column is numeric (Integer, Long, Double) AND property supports continuous:
    Offer: Continuous, Discrete, Passthrough (if String property)
- If column is String or Boolean:
    Offer: Discrete, Passthrough (if property is String-typed)
- If only one type is valid, skip the question and state it.

Say: "How should the data map to {property_name}?

1. **Continuous** — Smoothly interpolate values across the data range (e.g., low→small, high→large)
2. **Discrete** — Assign a specific value for each distinct category
3. **Passthrough** — Use the column value directly as the property value

Enter 1, 2, or 3:"

(Only show options that are valid for the column/property combination.)

Capture: $mapping_type

If $mapping_type is Continuous → go to STEP 4a
If $mapping_type is Discrete → go to STEP 4b
If $mapping_type is Passthrough → go to STEP 4c

═══════════════════════════════════════════════════════════════
STEP 4a — CONTINUOUS MAPPING
═══════════════════════════════════════════════════════════════

First, determine the continuous sub-editor based on the property's value type (see 00-shared-reference.md Section 5):

--- SUB-TYPE 4a-i: CONTINUOUS-CONTINUOUS (numeric properties: size, width, transparency) ---

Call tool: get_column_range with { "column_name": $data_column, "table": "node" or "edge" }
Capture: $min_data, $max_data, $mean_data

Say: "The data in '{column_name}' ranges from {min_data} to {max_data} (mean: {mean_data}).

What should the {property_name} value be at the MINIMUM of the data range ({min_data})?
(e.g., for node size: 10.0)"

Capture: $min_property_value

Say: "What should the {property_name} value be at the MAXIMUM of the data range ({max_data})?
(e.g., for node size: 80.0)"

Capture: $max_property_value

Say: "Would you like to add a midpoint breakpoint? This lets you control the value at a specific data point (e.g., at the mean).
1. No, just use min→max (linear)
2. Yes, add a midpoint"

Capture: $use_midpoint

If $use_midpoint is Yes:
    Say: "What data value for the midpoint? (default: {mean_data})"
    Capture: $mid_data (default: $mean_data)
    Say: "What {property_name} value at the midpoint?"
    Capture: $mid_property_value

Build breakpoints:
    points = [
        { "value": $min_data, "lesser": $min_property_value, "equal": $min_property_value, "greater": $min_property_value },
        // if midpoint: { "value": $mid_data, "lesser": $mid_property_value, "equal": $mid_property_value, "greater": $mid_property_value },
        { "value": $max_data, "lesser": $max_property_value, "equal": $max_property_value, "greater": $max_property_value }
    ]

Call tool: create_continuous_mapping with {
    "property_id": $selected_property.id,
    "column_name": $data_column,
    "column_type": $column_type,
    "points": points
}

→ go to STEP 5

--- SUB-TYPE 4a-ii: CONTINUOUS-COLOR-GRADIENT (Paint/Color properties) ---

Call tool: get_column_range with { "column_name": $data_column, "table": "node" or "edge" }
Capture: $min_data, $max_data, $mean_data

Say: "The data in '{column_name}' ranges from {min_data} to {max_data}.

Choose a color palette for the gradient:
1. Blue → Red (cold to hot)
2. White → Blue (light to dark)
3. Blue → White → Red (diverging, neutral midpoint)
4. Yellow-Green-Blue (sequential)
5. Green → Black → Red (diverging)
6. Purple → Orange (diverging)
7. Custom (enter your own colors)

Enter the number:"

Capture: $palette_choice

If $palette_choice is Custom:
    Say: "Enter the color for the MINIMUM value ({min_data}) as a hex code (e.g., #0000FF):"
    Capture: $min_color
    Say: "Enter the color for the MAXIMUM value ({max_data}):"
    Capture: $max_color
    Say: "Would you like a midpoint color?
    1. No, just min→max
    2. Yes, add a midpoint color"
    If yes:
        Say: "Enter the midpoint data value (default: {mean_data}):"
        Capture: $mid_data
        Say: "Enter the midpoint color:"
        Capture: $mid_color

If $palette_choice is a preset (1-6):
    Look up colors from the palette table (see 00-shared-reference.md Section 3):
    - Palette 1 (Blue→Red): min=#0000FF, max=#FF0000
    - Palette 2 (White→Blue): min=#FFFFFF, max=#0000FF
    - Palette 3 (Blue→White→Red): min=#0000FF, mid=#FFFFFF, max=#FF0000, mid_data=$mean_data
    - Palette 4 (YlGnBu): min=#FFFFCC, mid=#41B6C4, max=#0C2C84, mid_data=$mean_data
    - Palette 5 (GnBkRd): min=#00FF00, mid=#000000, max=#FF0000, mid_data=$mean_data
    - Palette 6 (Purple→Orange): min=#7B3294, mid=#F7F7F7, max=#E66101, mid_data=$mean_data

Build breakpoints:
    points = [
        { "value": $min_data, "lesser": $min_color, "equal": $min_color, "greater": $min_color },
        // if midpoint: { "value": $mid_data, "lesser": $mid_color, "equal": $mid_color, "greater": $mid_color },
        { "value": $max_data, "lesser": $max_color, "equal": $max_color, "greater": $max_color }
    ]

Call tool: create_continuous_mapping with {
    "property_id": $selected_property.id,
    "column_name": $data_column,
    "column_type": $column_type,
    "points": points
}

→ go to STEP 5

--- SUB-TYPE 4a-iii: CONTINUOUS-DISCRETE (enum properties: shape, arrow, line type) ---

Call tool: get_column_range with { "column_name": $data_column, "table": "node" or "edge" }
Capture: $min_data, $max_data

Say: "Since {property_name} is a categorical property (like shape), we'll define thresholds in the data and assign a value to each region.

How many threshold regions would you like? (e.g., 2 creates: below threshold, at/above threshold; 3 creates: low, medium, high)"

Capture: $num_regions

For each boundary between regions (num_regions - 1 thresholds):
    Say: "What data value separates region {i} from region {i+1}?"
    Capture: $threshold_i

For each region:
    Say: "What {property_name} should be used for region {i} ({range description})?
    {list of available enum values — same format as default styling}"
    Capture: $region_value_i

Build breakpoints from thresholds and region values:
    For each threshold:
        point = {
            "value": $threshold_i,
            "lesser": $region_below_value,
            "equal": $region_above_value,
            "greater": $region_above_value
        }

Call tool: create_continuous_mapping with {
    "property_id": $selected_property.id,
    "column_name": $data_column,
    "column_type": $column_type,
    "points": points
}

→ go to STEP 5

═══════════════════════════════════════════════════════════════
STEP 4b — DISCRETE MAPPING
═══════════════════════════════════════════════════════════════

Call tool: get_column_distinct_values with { "column_name": $data_column, "table": "node" or "edge" }
Capture: $distinct_values, $value_count

--- IF $value_count ≤ 10: Manual assignment ---

Say: "The column '{column_name}' has {value_count} distinct values:

| # | Value | Assign {property_name} |
|---|-------|----------------------|
| 1 | kinase | ? |
| 2 | receptor | ? |
| 3 | TF | ? |
| ... | ... | ? |

For each value, what {property_name} should it get?"

For each distinct value:
    Prompt for the property value using the same type-specific prompt format as in the default styling spec (Step 2 of 02-default-styling-prompt.md):
    - Color → hex or preset
    - Shape → enum list
    - Number → numeric input
    - etc.

    Capture: $mapping_pair (column_value → property_value)

Build mapping entries from all pairs.

Call tool: create_discrete_mapping with {
    "property_id": $selected_property.id,
    "column_name": $data_column,
    "column_type": $column_type,
    "entries": { $column_value_1: $property_value_1, $column_value_2: $property_value_2, ... }
}

→ go to STEP 5

--- IF $value_count > 10: Generator-assisted ---

Say: "The column '{column_name}' has {value_count} distinct values — that's a lot to assign manually.

Would you like to:
1. **Auto-generate** — Automatically assign values using a generator
2. **Manual** — Assign each one individually (this will take a while)

Enter 1 or 2:"

If Manual → proceed with manual assignment as above.

If Auto-generate:

    Determine generator options based on property type:

    For Color properties:
        Say: "Choose a color generation method:
        1. **Rainbow** — Evenly spaced hues around the color wheel
        2. **Random** — Random colors for each value
        3. **Brewer Sequential** — Shades of a single hue (good for ordered categories)

        Enter 1, 2, or 3:"

    For Shape properties:
        Say: "I'll cycle through available shapes for each value."
        (No choice needed — cycle through NodeShape enum values.)

    For Numeric properties (size, width, transparency):
        Say: "Enter the range to spread values across.
        Minimum value:"
        Capture: $gen_min
        "Maximum value:"
        Capture: $gen_max
        (Values are evenly distributed across the range.)

    Capture: $generator_type

    Call tool: create_discrete_mapping_generated with {
        "property_id": $selected_property.id,
        "column_name": $data_column,
        "column_type": $column_type,
        "generator": $generator_type,
        "generator_params": { ... }  // min/max for numeric, palette name for color
    }

→ go to STEP 5

═══════════════════════════════════════════════════════════════
STEP 4c — PASSTHROUGH MAPPING
═══════════════════════════════════════════════════════════════

Say: "Setting up a passthrough mapping: the value in column '{column_name}' will be used directly as the {property_name} for each node/edge."

Call tool: create_passthrough_mapping with {
    "property_id": $selected_property.id,
    "column_name": $data_column,
    "column_type": $column_type
}

If success → Say: "Passthrough mapping created: {column_name} → {property_name}."

→ go to STEP 5

═══════════════════════════════════════════════════════════════
STEP 5 — Confirm and loop
═══════════════════════════════════════════════════════════════

If tool returned success:
    Say: "Mapping created: {column_name} → {property_name} ({mapping_type}).
    Would you like to create another mapping? (enter a property number/name, or 'done' to finish)"

If tool returned error:
    Say: "Mapping failed: {error}. Would you like to try again with different settings?"
    If yes → return to the appropriate step.

If user says "done" → Say: "Mapping styling complete! You created {count} mappings." → END
Otherwise → Capture new $selected_property → go to STEP 2.
```

---

## 3. Conversation Script

### Step-by-Step Reference

| Step | Agent Says | User Responds | Variables Captured | Tool Called | Next |
|------|-----------|---------------|-------------------|-------------|------|
| 1 | Mappable property table | Property # or "done" | `$selected_property` | `get_mappable_properties` | 2 or END |
| 2 | Compatible columns table | Column # or name | `$data_column` | `get_compatible_columns` | 3 |
| 3 | "Continuous, discrete, or passthrough?" | 1, 2, or 3 | `$mapping_type` | — | 4a/4b/4c |
| 4a-i | Continuous-continuous: min/max/midpoint | Numeric values | `$min_prop`, `$max_prop`, `$mid_prop` | `get_column_range`, `create_continuous_mapping` | 5 |
| 4a-ii | Continuous-gradient: palette or custom | Palette # or colors | `$palette`, `$colors` | `get_column_range`, `create_continuous_mapping` | 5 |
| 4a-iii | Continuous-discrete: thresholds + enum values | Threshold values, enum picks | `$thresholds`, `$region_values` | `get_column_range`, `create_continuous_mapping` | 5 |
| 4b (≤10) | Discrete manual: value-by-value assignment | Property values | `$entries` | `get_column_distinct_values`, `create_discrete_mapping` | 5 |
| 4b (>10) | Discrete generated: choose generator | Generator type | `$generator` | `get_column_distinct_values`, `create_discrete_mapping_generated` | 5 |
| 4c | Passthrough: confirm | — | — | `create_passthrough_mapping` | 5 |
| 5 | "Created! Another or done?" | Property or "done" | — | — | 2 or END |

### Branching Diagram

```
START
  │
  ▼
STEP 1: get_mappable_properties → show table
  │
  ├── "done" → END
  │
  ▼
STEP 2: get_compatible_columns → pick column
  │
  ▼
STEP 3: pick mapping type
  │
  ├── Continuous ──────────────────────────────┐
  │   │                                        │
  │   ├── Numeric VP (size, width, alpha)      │
  │   │   → STEP 4a-i: min/max/midpoint       │
  │   │                                        │
  │   ├── Color VP (fill, border, stroke)      │
  │   │   → STEP 4a-ii: palette/custom        │
  │   │                                        │
  │   └── Enum VP (shape, arrow, line type)    │
  │       → STEP 4a-iii: thresholds+regions    │
  │                                            │
  ├── Discrete ────────────────────────────────┤
  │   │                                        │
  │   ├── ≤10 distinct values                  │
  │   │   → STEP 4b manual assignment          │
  │   │                                        │
  │   └── >10 distinct values                  │
  │       → STEP 4b auto-generate or manual    │
  │                                            │
  └── Passthrough ─────────────────────────────┤
      → STEP 4c (just set it)                  │
                                               │
                                               ▼
                                         STEP 5: confirm
                                               │
                                               ├── "done" → END
                                               └── another → STEP 2
```

---

## 4. MCP Tool Schemas

### 4.1 `get_mappable_properties`

```json
{
  "name": "get_mappable_properties",
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

---

## 5. Server-Side Implementation Notes

### 5.1 `get_mappable_properties`

```java
public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
    CyNetwork network = appManager.getCurrentNetwork();
    if (network == null) return error("No network is currently loaded.");

    VisualStyle style = vmmManager.getCurrentVisualStyle();
    VisualLexicon lexicon = renderingEngineManager.getDefaultVisualLexicon();

    // Build a lookup of current mappings
    Map<String, VisualMappingFunction<?, ?>> currentMappings = new HashMap<>();
    for (VisualMappingFunction<?, ?> fn : style.getAllVisualMappingFunctions()) {
        currentMappings.put(fn.getVisualProperty().getIdString(), fn);
    }

    // Enumerate node properties
    List<Map<String, Object>> nodeProps = buildPropertyList(
        lexicon.getAllDescendants(BasicVisualLexicon.NODE), currentMappings);
    List<Map<String, Object>> edgeProps = buildPropertyList(
        lexicon.getAllDescendants(BasicVisualLexicon.EDGE), currentMappings);

    ObjectNode result = mapper.createObjectNode();
    result.put("style_name", style.getTitle());
    result.set("node_properties", mapper.valueToTree(nodeProps));
    result.set("edge_properties", mapper.valueToTree(edgeProps));
    return success(mapper.writeValueAsString(result));
}

private List<Map<String, Object>> buildPropertyList(
        Set<VisualProperty<?>> vps, Map<String, VisualMappingFunction<?, ?>> mappings) {

    List<Map<String, Object>> list = new ArrayList<>();
    for (VisualProperty<?> vp : vps) {
        if (!isSupported(vp)) continue;

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", vp.getIdString());
        entry.put("displayName", vp.getDisplayName());
        entry.put("valueType", getTypeName(vp.getRange()));
        entry.put("continuousSubType", getContinuousSubType(vp));

        VisualMappingFunction<?, ?> mapping = mappings.get(vp.getIdString());
        if (mapping != null) {
            entry.put("currentMapping", Map.of(
                "type", mapping.getClass().getSimpleName(),
                "column", mapping.getMappingColumnName(),
                "summary", summarizeMapping(mapping)
            ));
        } else {
            entry.put("currentMapping", null);
        }

        list.add(entry);
    }
    return list;
}

// Determine continuous sub-type: "continuous", "color-gradient", "discrete", or null
private String getContinuousSubType(VisualProperty<?> vp) {
    Range<?> range = vp.getRange();
    if (range instanceof ContinuousRange) {
        Class<?> type = range.getType();
        if (Paint.class.isAssignableFrom(type)) return "color-gradient";
        if (Number.class.isAssignableFrom(type)) return "continuous";
    } else if (range instanceof DiscreteRange) {
        return "discrete";
    }
    return null;
}
```

### 5.2 `get_compatible_columns`

```java
public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
    String propertyId = (String) request.arguments().get("property_id");

    CyNetwork network = appManager.getCurrentNetwork();
    if (network == null) return error("No network is currently loaded.");

    VisualLexicon lexicon = renderingEngineManager.getDefaultVisualLexicon();
    VisualProperty<?> vp = findPropertyById(lexicon, propertyId);
    if (vp == null) return error("Unknown visual property: " + propertyId);

    // Determine if this is a node or edge property
    boolean isNode = lexicon.getAllDescendants(BasicVisualLexicon.NODE).contains(vp);
    CyTable table = isNode
        ? network.getDefaultNodeTable()
        : network.getDefaultEdgeTable();

    List<Map<String, Object>> columns = new ArrayList<>();
    for (CyColumn col : table.getColumns()) {
        String name = col.getName();
        // Skip internal columns
        if (name.equals(CyNetwork.SUID) || name.equals("selected")) continue;
        if (col.getType() == List.class) continue;  // List columns not mappable

        Class<?> colType = col.getType();
        boolean supportsContinuous = Number.class.isAssignableFrom(colType)
            && getContinuousSubType(vp) != null;
        boolean supportsDiscrete = true;  // all column types support discrete
        boolean supportsPassthrough = vp.getRange().getType() == String.class;

        // Get sample values
        List<Object> samples = table.getAllRows().stream()
            .map(row -> row.get(name, colType))
            .filter(Objects::nonNull)
            .distinct()
            .limit(4)
            .collect(Collectors.toList());

        columns.add(Map.of(
            "name", name,
            "type", colType.getSimpleName(),
            "sampleValues", samples,
            "supportsMapping", Map.of(
                "continuous", supportsContinuous,
                "discrete", supportsDiscrete,
                "passthrough", supportsPassthrough
            )
        ));
    }

    ObjectNode result = mapper.createObjectNode();
    result.put("property_id", propertyId);
    result.put("table", isNode ? "node" : "edge");
    result.set("columns", mapper.valueToTree(columns));
    return success(mapper.writeValueAsString(result));
}
```

### 5.3 `get_column_range`

```java
public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
    String columnName = (String) request.arguments().get("column_name");
    String tableType = (String) request.arguments().get("table");

    CyNetwork network = appManager.getCurrentNetwork();
    if (network == null) return error("No network is currently loaded.");

    CyTable table = "node".equals(tableType)
        ? network.getDefaultNodeTable()
        : network.getDefaultEdgeTable();

    CyColumn column = table.getColumn(columnName);
    if (column == null) return error("Column not found: " + columnName);

    Class<?> type = column.getType();
    if (!Number.class.isAssignableFrom(type)) {
        return error("Column '" + columnName + "' is not numeric (type: " + type.getSimpleName() + ").");
    }

    // Compute stats
    double min = Double.MAX_VALUE, max = Double.MIN_VALUE, sum = 0;
    int count = 0;
    for (CyRow row : table.getAllRows()) {
        Number val = (Number) row.get(columnName, type);
        if (val == null) continue;
        double d = val.doubleValue();
        min = Math.min(min, d);
        max = Math.max(max, d);
        sum += d;
        count++;
    }

    if (count == 0) return error("Column '" + columnName + "' has no non-null values.");

    double mean = sum / count;

    ObjectNode result = mapper.createObjectNode();
    result.put("column_name", columnName);
    result.put("type", type.getSimpleName());
    result.put("min", min);
    result.put("max", max);
    result.put("mean", Math.round(mean * 100.0) / 100.0);
    result.put("count", count);
    return success(mapper.writeValueAsString(result));
}
```

### 5.4 `get_column_distinct_values`

```java
public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
    String columnName = (String) request.arguments().get("column_name");
    String tableType = (String) request.arguments().get("table");

    CyNetwork network = appManager.getCurrentNetwork();
    if (network == null) return error("No network is currently loaded.");

    CyTable table = "node".equals(tableType)
        ? network.getDefaultNodeTable()
        : network.getDefaultEdgeTable();

    CyColumn column = table.getColumn(columnName);
    if (column == null) return error("Column not found: " + columnName);

    // Count occurrences of each value
    Map<Object, Integer> valueCounts = new LinkedHashMap<>();
    for (CyRow row : table.getAllRows()) {
        Object val = row.get(columnName, column.getType());
        if (val == null) continue;
        valueCounts.merge(val, 1, Integer::sum);
    }

    // Sort by count descending
    List<Map<String, Object>> values = valueCounts.entrySet().stream()
        .sorted(Map.Entry.<Object, Integer>comparingByValue().reversed())
        .map(e -> Map.<String, Object>of("value", e.getKey(), "count", e.getValue()))
        .collect(Collectors.toList());

    ObjectNode result = mapper.createObjectNode();
    result.put("column_name", columnName);
    result.put("type", column.getType().getSimpleName());
    result.put("distinct_count", values.size());
    result.set("values", mapper.valueToTree(values));
    return success(mapper.writeValueAsString(result));
}
```

### 5.5 `create_continuous_mapping`

```java
public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
    String propertyId = (String) request.arguments().get("property_id");
    String columnName = (String) request.arguments().get("column_name");
    String columnType = (String) request.arguments().get("column_type");
    List<Map<String, Object>> points = (List<Map<String, Object>>) request.arguments().get("points");

    CyNetwork network = appManager.getCurrentNetwork();
    if (network == null) return error("No network is currently loaded.");

    VisualStyle style = vmmManager.getCurrentVisualStyle();
    VisualLexicon lexicon = renderingEngineManager.getDefaultVisualLexicon();
    VisualProperty<?> vp = findPropertyById(lexicon, propertyId);
    if (vp == null) return error("Unknown visual property: " + propertyId);

    Class<?> colClass = resolveColumnType(columnType);

    // Create continuous mapping
    @SuppressWarnings("unchecked")
    ContinuousMapping<Number, ?> mapping = (ContinuousMapping<Number, ?>)
        continuousMappingFactory.createVisualMappingFunction(
            columnName, colClass, vp);

    // Add breakpoints
    for (int i = 0; i < points.size(); i++) {
        Map<String, Object> point = points.get(i);
        Number dataValue = ((Number) point.get("value"));
        Object lesser = parseValue(vp, point.get("lesser"));
        Object equal = parseValue(vp, point.get("equal"));
        Object greater = parseValue(vp, point.get("greater"));

        @SuppressWarnings("unchecked")
        BoundaryRangeValues brv = new BoundaryRangeValues(lesser, equal, greater);
        mapping.addPoint(dataValue, brv);
    }

    // Remove existing mapping for this property (if any) and add new one
    SwingUtilities.invokeAndWait(() -> {
        VisualMappingFunction<?, ?> existing = style.getVisualMappingFunction(vp);
        if (existing != null) style.removeVisualMappingFunction(vp);
        style.addVisualMappingFunction(mapping);
        CyNetworkView view = appManager.getCurrentNetworkView();
        if (view != null) {
            style.apply(view);
            view.updateView();
        }
    });

    return success(String.format(
        "{\"status\":\"success\",\"property_id\":\"%s\",\"displayName\":\"%s\",\"mapping_type\":\"ContinuousMapping\",\"column\":\"%s\",\"points_count\":%d}",
        propertyId, vp.getDisplayName(), columnName, points.size()
    ));
}
```

### 5.6 `create_discrete_mapping`

```java
public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
    String propertyId = (String) request.arguments().get("property_id");
    String columnName = (String) request.arguments().get("column_name");
    String columnType = (String) request.arguments().get("column_type");
    Map<String, Object> entries = (Map<String, Object>) request.arguments().get("entries");

    CyNetwork network = appManager.getCurrentNetwork();
    if (network == null) return error("No network is currently loaded.");

    VisualStyle style = vmmManager.getCurrentVisualStyle();
    VisualLexicon lexicon = renderingEngineManager.getDefaultVisualLexicon();
    VisualProperty<?> vp = findPropertyById(lexicon, propertyId);
    if (vp == null) return error("Unknown visual property: " + propertyId);

    Class<?> colClass = resolveColumnType(columnType);

    @SuppressWarnings("unchecked")
    DiscreteMapping<Object, ?> mapping = (DiscreteMapping<Object, ?>)
        discreteMappingFactory.createVisualMappingFunction(
            columnName, colClass, vp);

    // Add entries
    Map<Object, Object> parsedEntries = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : entries.entrySet()) {
        Object key = convertColumnValue(entry.getKey(), colClass);
        Object value = parseValue(vp, entry.getValue());
        parsedEntries.put(key, value);
    }
    mapping.putAll(parsedEntries);

    // Apply
    SwingUtilities.invokeAndWait(() -> {
        VisualMappingFunction<?, ?> existing = style.getVisualMappingFunction(vp);
        if (existing != null) style.removeVisualMappingFunction(vp);
        style.addVisualMappingFunction(mapping);
        CyNetworkView view = appManager.getCurrentNetworkView();
        if (view != null) {
            style.apply(view);
            view.updateView();
        }
    });

    return success(String.format(
        "{\"status\":\"success\",\"property_id\":\"%s\",\"displayName\":\"%s\",\"mapping_type\":\"DiscreteMapping\",\"column\":\"%s\",\"entries_count\":%d}",
        propertyId, vp.getDisplayName(), columnName, entries.size()
    ));
}
```

### 5.7 `create_passthrough_mapping`

```java
public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
    String propertyId = (String) request.arguments().get("property_id");
    String columnName = (String) request.arguments().get("column_name");
    String columnType = (String) request.arguments().get("column_type");

    CyNetwork network = appManager.getCurrentNetwork();
    if (network == null) return error("No network is currently loaded.");

    VisualStyle style = vmmManager.getCurrentVisualStyle();
    VisualLexicon lexicon = renderingEngineManager.getDefaultVisualLexicon();
    VisualProperty<?> vp = findPropertyById(lexicon, propertyId);
    if (vp == null) return error("Unknown visual property: " + propertyId);

    Class<?> colClass = resolveColumnType(columnType);

    PassthroughMapping<?, ?> mapping = (PassthroughMapping<?, ?>)
        passthroughMappingFactory.createVisualMappingFunction(
            columnName, colClass, vp);

    SwingUtilities.invokeAndWait(() -> {
        VisualMappingFunction<?, ?> existing = style.getVisualMappingFunction(vp);
        if (existing != null) style.removeVisualMappingFunction(vp);
        style.addVisualMappingFunction(mapping);
        CyNetworkView view = appManager.getCurrentNetworkView();
        if (view != null) {
            style.apply(view);
            view.updateView();
        }
    });

    return success(String.format(
        "{\"status\":\"success\",\"property_id\":\"%s\",\"displayName\":\"%s\",\"mapping_type\":\"PassthroughMapping\",\"column\":\"%s\"}",
        propertyId, vp.getDisplayName(), columnName
    ));
}
```

### 5.8 `create_discrete_mapping_generated`

```java
public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
    String propertyId = (String) request.arguments().get("property_id");
    String columnName = (String) request.arguments().get("column_name");
    String columnType = (String) request.arguments().get("column_type");
    String generator = (String) request.arguments().get("generator");
    Map<String, Object> genParams = (Map<String, Object>) request.arguments().getOrDefault("generator_params", Map.of());

    CyNetwork network = appManager.getCurrentNetwork();
    if (network == null) return error("No network is currently loaded.");

    VisualStyle style = vmmManager.getCurrentVisualStyle();
    VisualLexicon lexicon = renderingEngineManager.getDefaultVisualLexicon();
    VisualProperty<?> vp = findPropertyById(lexicon, propertyId);
    if (vp == null) return error("Unknown visual property: " + propertyId);

    // Get distinct values
    boolean isNode = lexicon.getAllDescendants(BasicVisualLexicon.NODE).contains(vp);
    CyTable table = isNode
        ? network.getDefaultNodeTable()
        : network.getDefaultEdgeTable();
    CyColumn column = table.getColumn(columnName);
    List<Object> distinctValues = table.getAllRows().stream()
        .map(row -> row.get(columnName, column.getType()))
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());

    // Generate values
    Map<Object, Object> entries = new LinkedHashMap<>();
    switch (generator) {
        case "rainbow":
            for (int i = 0; i < distinctValues.size(); i++) {
                float hue = (float) i / distinctValues.size();
                Color c = Color.getHSBColor(hue, 0.8f, 0.9f);
                entries.put(distinctValues.get(i), c);
            }
            break;
        case "random":
            Random rng = new Random(42);  // fixed seed for reproducibility
            for (Object val : distinctValues) {
                entries.put(val, new Color(rng.nextInt(256), rng.nextInt(256), rng.nextInt(256)));
            }
            break;
        case "brewer_sequential":
            String hue = (String) genParams.getOrDefault("hue", "blue");
            // Generate shades of the base hue
            for (int i = 0; i < distinctValues.size(); i++) {
                float lightness = 0.3f + 0.6f * ((float) i / Math.max(distinctValues.size() - 1, 1));
                Color c = generateBrewerShade(hue, lightness);
                entries.put(distinctValues.get(i), c);
            }
            break;
        case "shape_cycle":
            List<NodeShape> shapes = new ArrayList<>(NodeShapeVisualProperty.getValues());
            for (int i = 0; i < distinctValues.size(); i++) {
                entries.put(distinctValues.get(i), shapes.get(i % shapes.size()));
            }
            break;
        case "numeric_range":
            double min = ((Number) genParams.get("min")).doubleValue();
            double max = ((Number) genParams.get("max")).doubleValue();
            for (int i = 0; i < distinctValues.size(); i++) {
                double val = min + (max - min) * ((double) i / Math.max(distinctValues.size() - 1, 1));
                entries.put(distinctValues.get(i), val);
            }
            break;
        default:
            return error("Unknown generator: " + generator);
    }

    // Create the discrete mapping with generated entries
    Class<?> colClass = resolveColumnType(columnType);
    @SuppressWarnings("unchecked")
    DiscreteMapping<Object, ?> mapping = (DiscreteMapping<Object, ?>)
        discreteMappingFactory.createVisualMappingFunction(columnName, colClass, vp);
    mapping.putAll(entries);

    SwingUtilities.invokeAndWait(() -> {
        VisualMappingFunction<?, ?> existing = style.getVisualMappingFunction(vp);
        if (existing != null) style.removeVisualMappingFunction(vp);
        style.addVisualMappingFunction(mapping);
        CyNetworkView view = appManager.getCurrentNetworkView();
        if (view != null) {
            style.apply(view);
            view.updateView();
        }
    });

    // Build sample entries for response
    Map<String, String> sampleEntries = new LinkedHashMap<>();
    entries.entrySet().stream().limit(5).forEach(e ->
        sampleEntries.put(String.valueOf(e.getKey()), formatValue(e.getValue())));

    return success(String.format(
        "{\"status\":\"success\",\"property_id\":\"%s\",\"displayName\":\"%s\",\"mapping_type\":\"DiscreteMapping\",\"column\":\"%s\",\"generator\":\"%s\",\"entries_count\":%d,\"sample_entries\":%s}",
        propertyId, vp.getDisplayName(), columnName, generator, entries.size(),
        mapper.writeValueAsString(sampleEntries)
    ));
}
```

### 5.9 Helper: Column Type Resolution

```java
private Class<?> resolveColumnType(String typeName) {
    switch (typeName) {
        case "Integer": return Integer.class;
        case "Long": return Long.class;
        case "Double": return Double.class;
        case "String": return String.class;
        case "Boolean": return Boolean.class;
        default: throw new IllegalArgumentException("Unknown column type: " + typeName);
    }
}

private Object convertColumnValue(String stringValue, Class<?> targetType) {
    if (targetType == Integer.class) return Integer.parseInt(stringValue);
    if (targetType == Long.class) return Long.parseLong(stringValue);
    if (targetType == Double.class) return Double.parseDouble(stringValue);
    if (targetType == Boolean.class) return Boolean.parseBoolean(stringValue);
    return stringValue;  // String type
}
```

---

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
- **Implementation note**: This is why the pseudocode above uses `Color.getHSBColor()` and `java.util.Random` directly rather than delegating to `DiscreteMappingGenerator` services.

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
