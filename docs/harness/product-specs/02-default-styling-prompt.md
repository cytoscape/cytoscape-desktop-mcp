# MCP Prompt Spec: Default Styling

> **Spec file**: `02-default-styling-prompt.md`
> **Shared reference**: See `00-shared-reference.md` for SDK constants, enums, palettes, and patterns.

This prompt guides the user through changing the default visual property values of the current visual style. It can be invoked standalone on an already-loaded network, or embedded inline as Phase 4 of the Network Wizard (spec `01`).

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
  "name": "default_styling",
  "title": "Change Network Default Visual Style Properties on Cytoscape Desktop",
  "description": "Interactively change default values of node and edge visual properties (colors, sizes, shapes, fonts, etc.) for the current network's visual style on Cytoscape Desktop.",
  "arguments": []
}
```

### 1.2 prompts/get Response

```json
{
  "description": "Change Default Visual Style Properties",
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
Prompt defaultStylingPrompt = new Prompt(
    "default_styling",
    "Change Default Visual Style Properties",
    List.of()
);

PromptSpecification defaultStylingSpec = new PromptSpecification(
    defaultStylingPrompt,
    (exchange, request) -> new GetPromptResult(
        "Change Default Visual Style Properties",
        List.of(new PromptMessage(Role.ASSISTANT, new TextContent(DEFAULT_STYLING_SYSTEM_PROMPT)))
    )
);
```

---

## 2. System Prompt

```text
You are a Cytoscape Visual Style Editor. You will help the user change the default appearance of nodes and edges in their current visual style. Default values apply to ALL nodes/edges that don't have a data-driven mapping overriding them.

IMPORTANT RULES:
- Ask ONE question at a time. Wait for the user's answer before proceeding.
- Always confirm successful changes before offering the next one.
- Present property lists grouped by category (Node properties first, then Edge properties).
- Show current values alongside property names so the user knows what they're changing.
- Use concise, scientist-friendly language. Avoid API jargon.
- If a tool call fails, show the error and offer to retry.
- The user can say "done" at any time to finish.

═══════════════════════════════════════════════════════════════
STEP 1 — Show current defaults
═══════════════════════════════════════════════════════════════

Call tool: get_visual_style_defaults

Present the results to the user grouped by category. Use this format:

"Here are the current default visual properties for your style '{style_name}':

**Node Properties**
| # | Property | Current Value |
|---|----------|--------------|
| 1 | Fill Color | #89D0F5 |
| 2 | Border Color | #333333 |
| 3 | Border Width | 2.0 |
| ... | ... | ... |

**Edge Properties**
| # | Property | Current Value |
|---|----------|--------------|
| 20 | Stroke Color | #666666 |
| 21 | Width | 2.0 |
| ... | ... | ... |

Which property would you like to change? (enter the number, name, or type 'done' to finish)"

Capture: $selected_property (the property the user wants to change)

Note: Properties with valueType NodeShape, ArrowShape, or LineType include an "allowedValues"
array in the tool response listing all valid choices. Use this data when constructing the
STEP 2 prompt for discrete-typed properties.

═══════════════════════════════════════════════════════════════
STEP 2 — Prompt for new value (type-specific)
═══════════════════════════════════════════════════════════════

Based on the value type of $selected_property, ask for the new value using the appropriate sub-prompt:

--- IF value type is Paint/Color ---

Say: "The current {property_name} is {current_hex_color}.

You can enter a new color as:
- A hex code (e.g., #FF6600)
- A common color name (e.g., red, steelblue, coral)

Enter your choice:"

Capture: $new_color

--- IF value type is Double (size, width) ---

Say: "The current {property_name} is {current_value}.

Enter a new numeric value (e.g., 35.0 for node size, 3.0 for border width):"

Capture: $new_number

--- IF value type is Integer (transparency, font size) ---

Say: "The current {property_name} is {current_value}.

Enter a new integer value{context}:"

Where {context} is:
- For transparency properties: " (0 = fully transparent, 255 = fully opaque)"
- For font size: " (in points, e.g., 12)"

Capture: $new_integer

--- IF value type is NodeShape ---

Say: "The current {property_name} is {current_shape}.

Available shapes:
{numbered list from $selected_property.allowedValues}

Enter the number or name:"

Capture: $new_shape

--- IF value type is ArrowShape ---

Say: "The current {property_name} is {current_shape}.

Available arrow shapes:
{numbered list from $selected_property.allowedValues}

Enter the number or name (use 'None' to remove the arrow):"

Capture: $new_arrow

--- IF value type is LineType ---

Say: "The current {property_name} is {current_line_type}.

Available line types:
{numbered list from $selected_property.allowedValues}

Enter the number or name:"

Capture: $new_line_type

--- IF value type is Font ---

Say: "The current {property_name} is {current_font}.

Enter a new font in the format: Family-Style-Size
- Family: SansSerif, Serif, Monospaced, Dialog, Arial, etc.
- Style: Plain, Bold, Italic, BoldItalic
- Size: number in points

Examples: SansSerif-Bold-14, Arial-Plain-12, Serif-Italic-10"

Capture: $new_font

--- IF value type is String ---

Say: "The current {property_name} is '{current_value}'.

Enter the new text value:"

Capture: $new_string

--- IF value type is Boolean ---

Say: "The current {property_name} is {current_value}.

Enter: true or false"

Capture: $new_boolean

═══════════════════════════════════════════════════════════════
STEP 3 — Apply the change
═══════════════════════════════════════════════════════════════

Call tool: set_visual_default with {
  "property_id": $selected_property.idString,
  "value": $new_value  // formatted appropriately for the type
}

If tool returns error → Say: "Couldn't set {property_name}: {error}. Would you like to try a different value?"

If success → Say: "{property_name} changed from {old_value} to {new_value}."

═══════════════════════════════════════════════════════════════
STEP 4 — Loop or finish
═══════════════════════════════════════════════════════════════

Say: "Would you like to change another property? (enter a property number/name, or 'done' to finish)"

If user says "done" → Say: "Default styling complete! You changed {count} properties." → END
Otherwise → Capture new $selected_property → go to STEP 2.
```

---

## 3. Conversation Script

### Step-by-Step Reference

| Step | Agent Says | User Responds | Variables Captured | Tool Called | On Error |
|------|-----------|---------------|-------------------|-------------|----------|
| 1 | Present property table with current values | — | `$defaults` (full list) | `get_visual_style_defaults` | "Couldn't retrieve style" → error msg |
| 1b | "Which property to change?" | Property # or name or "done" | `$selected_property` | — | — |
| 2 | Type-specific value prompt (color/number/shape/etc.) | New value | `$new_value` | — | — |
| 3 | — | — | — | `set_visual_default` | "Couldn't set" → retry |
| 3b | "Changed {prop} from {old} to {new}" | — | `$changes_count++` | — | — |
| 4 | "Change another or done?" | Property or "done" | — | — | Loop to Step 2 or END |

### Branching Logic

```
START → STEP 1 (get defaults) → STEP 1b (pick property)
                                    ↓ "done" → END
                                    ↓ property selected
                                 STEP 2 (type-specific prompt)
                                    ↓
                                 STEP 3 (apply change)
                                    ↓ error → retry STEP 2
                                    ↓ success
                                 STEP 4 (loop?) → STEP 2 or END
```

---

## 4. MCP Tool Schemas

### 4.1 `get_visual_style_defaults`

```json
{
  "name": "get_visual_style_defaults",
  "description": "Get the current default values for all node and edge visual properties in the active visual style in Cytoscape Desktop. Returns properties grouped by category with their IDs, display names, value types, and current default values. Discrete-typed properties (NodeShape, ArrowShape, LineType) include an 'allowedValues' array listing all valid choices.",
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
    "text": "{\"style_name\":\"default\",\"node_properties\":[{\"id\":\"NODE_FILL_COLOR\",\"displayName\":\"Node Fill Color\",\"valueType\":\"Paint\",\"currentValue\":\"#89D0F5\"},{\"id\":\"NODE_BORDER_PAINT\",\"displayName\":\"Node Border Paint\",\"valueType\":\"Paint\",\"currentValue\":\"#333333\"},{\"id\":\"NODE_BORDER_WIDTH\",\"displayName\":\"Node Border Width\",\"valueType\":\"Double\",\"currentValue\":2.0},{\"id\":\"NODE_SIZE\",\"displayName\":\"Node Size\",\"valueType\":\"Double\",\"currentValue\":35.0},{\"id\":\"NODE_SHAPE\",\"displayName\":\"Node Shape\",\"valueType\":\"NodeShape\",\"currentValue\":\"Ellipse\",\"allowedValues\":[\"Diamond\",\"Ellipse\",\"Hexagon\",\"Octagon\",\"Parallelogram\",\"Rectangle\",\"Round Rectangle\",\"Triangle\",\"Vee\"]},{\"id\":\"NODE_TRANSPARENCY\",\"displayName\":\"Node Transparency\",\"valueType\":\"Integer\",\"currentValue\":255},{\"id\":\"NODE_LABEL_COLOR\",\"displayName\":\"Node Label Color\",\"valueType\":\"Paint\",\"currentValue\":\"#000000\"},{\"id\":\"NODE_LABEL_FONT_SIZE\",\"displayName\":\"Node Label Font Size\",\"valueType\":\"Integer\",\"currentValue\":12},{\"id\":\"NODE_LABEL_FONT_FACE\",\"displayName\":\"Node Label Font Face\",\"valueType\":\"Font\",\"currentValue\":\"SansSerif-Plain-12\"}],\"edge_properties\":[{\"id\":\"EDGE_STROKE_UNSELECTED_PAINT\",\"displayName\":\"Edge Stroke Color (Unselected)\",\"valueType\":\"Paint\",\"currentValue\":\"#666666\"},{\"id\":\"EDGE_WIDTH\",\"displayName\":\"Edge Width\",\"valueType\":\"Double\",\"currentValue\":2.0},{\"id\":\"EDGE_LINE_TYPE\",\"displayName\":\"Edge Line Type\",\"valueType\":\"LineType\",\"currentValue\":\"Solid\",\"allowedValues\":[\"Backward Slash\",\"Contiguous Arrow\",\"Dash Dot\",\"Dots\",\"Equal Dash\",\"Forward Slash\",\"Long Dash\",\"Parallel Lines\",\"Separate Arrow\",\"Sine Wave\",\"Solid\",\"Vertical Slash\",\"Zigzag\"]},{\"id\":\"EDGE_TRANSPARENCY\",\"displayName\":\"Edge Transparency\",\"valueType\":\"Integer\",\"currentValue\":255},{\"id\":\"EDGE_TARGET_ARROW_SHAPE\",\"displayName\":\"Target Arrow Shape\",\"valueType\":\"ArrowShape\",\"currentValue\":\"None\",\"allowedValues\":[\"Arrow\",\"Arrow Short\",\"Circle\",\"Cross Delta\",\"Cross Open Delta\",\"Delta\",\"Delta Short 1\",\"Delta Short 2\",\"Diamond\",\"Diamond Short 1\",\"Diamond Short 2\",\"Half Bottom\",\"Half Top\",\"None\",\"T\"]},{\"id\":\"EDGE_SOURCE_ARROW_SHAPE\",\"displayName\":\"Source Arrow Shape\",\"valueType\":\"ArrowShape\",\"currentValue\":\"None\",\"allowedValues\":[\"Arrow\",\"Arrow Short\",\"Circle\",\"Cross Delta\",\"Cross Open Delta\",\"Delta\",\"Delta Short 1\",\"Delta Short 2\",\"Diamond\",\"Diamond Short 1\",\"Diamond Short 2\",\"Half Bottom\",\"Half Top\",\"None\",\"T\"]}]}"
  }],
  "isError": false
}
```

**Error response:**
```json
{
  "content": [{ "type": "text", "text": "No network is currently loaded. Please load a network first." }],
  "isError": true
}
```

### 4.2 `set_visual_default`

```json
{
  "name": "set_visual_default",
  "description": "Set the default value of a single visual property in the active visual style in Cytoscape Desktop. The value format depends on the property type: hex color string for Paint properties, numeric for Double/Integer, display name string for shapes and line types, 'Family-Style-Size' for fonts.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "property_id": {
        "type": "string",
        "description": "Visual property ID string (e.g., 'NODE_FILL_COLOR', 'EDGE_WIDTH'). Must match a valid VisualProperty.getIdString()."
      },
      "value": {
        "description": "New default value. Type depends on the property: string for colors ('#FF0000'), shapes ('Ellipse'), line types ('Solid'), fonts ('SansSerif-Bold-14'); number for sizes/widths/transparency; boolean for visibility flags.",
        "oneOf": [
          { "type": "string" },
          { "type": "number" },
          { "type": "boolean" }
        ]
      }
    },
    "required": ["property_id", "value"]
  }
}
```

**Success response:**
```json
{
  "content": [{ "type": "text", "text": "{\"status\":\"success\",\"property_id\":\"NODE_FILL_COLOR\",\"displayName\":\"Node Fill Color\",\"old_value\":\"#89D0F5\",\"new_value\":\"#FF6600\"}" }],
  "isError": false
}
```

**Error response:**
```json
{
  "content": [{ "type": "text", "text": "Invalid value for NODE_SHAPE: 'Pentagon' is not a recognized node shape. Valid shapes: Diamond, Ellipse, Hexagon, Octagon, Parallelogram, Rectangle, Round Rectangle, Triangle, Vee" }],
  "isError": true
}
```

---

## 5. Server-Side Implementation Notes

### 5.1 `get_visual_style_defaults`

```java
public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
    CyNetwork network = appManager.getCurrentNetwork();
    if (network == null) {
        return error("No network is currently loaded. Please load a network first.");
    }

    VisualStyle style = vmmManager.getCurrentVisualStyle();
    VisualLexicon lexicon = renderingEngineManager.getDefaultVisualLexicon();

    // Enumerate node properties
    Set<VisualProperty<?>> nodeVPs = lexicon.getAllDescendants(BasicVisualLexicon.NODE);
    List<Map<String, Object>> nodeProps = new ArrayList<>();
    for (VisualProperty<?> vp : nodeVPs) {
        if (!isSupported(vp)) continue;
        Object defaultVal = style.getDefaultValue(vp);
        Map<String, Object> propMap = new LinkedHashMap<>();
        propMap.put("id", vp.getIdString());
        propMap.put("displayName", vp.getDisplayName());
        propMap.put("valueType", getTypeName(vp.getRange()));
        propMap.put("currentValue", formatValue(defaultVal));
        List<String> allowed = getAllowedValues(vp);
        if (allowed != null) propMap.put("allowedValues", allowed);
        nodeProps.add(propMap);
    }

    // Enumerate edge properties (same pattern)
    Set<VisualProperty<?>> edgeVPs = lexicon.getAllDescendants(BasicVisualLexicon.EDGE);
    List<Map<String, Object>> edgeProps = new ArrayList<>();
    for (VisualProperty<?> vp : edgeVPs) {
        if (!isSupported(vp)) continue;
        Object defaultVal = style.getDefaultValue(vp);
        Map<String, Object> propMap = new LinkedHashMap<>();
        propMap.put("id", vp.getIdString());
        propMap.put("displayName", vp.getDisplayName());
        propMap.put("valueType", getTypeName(vp.getRange()));
        propMap.put("currentValue", formatValue(defaultVal));
        List<String> allowed = getAllowedValues(vp);
        if (allowed != null) propMap.put("allowedValues", allowed);
        edgeProps.add(propMap);
    }

    // Build JSON response
    ObjectNode result = mapper.createObjectNode();
    result.put("style_name", style.getTitle());
    result.set("node_properties", mapper.valueToTree(nodeProps));
    result.set("edge_properties", mapper.valueToTree(edgeProps));
    return success(mapper.writeValueAsString(result));
}

// Helper: format values for display
private String formatValue(Object value) {
    if (value instanceof Paint || value instanceof Color) {
        Color c = (Color) value;
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    } else if (value instanceof Font) {
        Font f = (Font) value;
        String style = f.isPlain() ? "Plain" : f.isBold() && f.isItalic() ? "BoldItalic" :
                       f.isBold() ? "Bold" : "Italic";
        return f.getFamily() + "-" + style + "-" + f.getSize();
    } else if (value instanceof NodeShape || value instanceof ArrowShape || value instanceof LineType) {
        // These implement getDisplayName() or similar
        return value.toString();
    } else {
        return String.valueOf(value);
    }
}

// Helper: type name from Range
private String getTypeName(Range<?> range) {
    Class<?> type = range.getType();
    if (Paint.class.isAssignableFrom(type)) return "Paint";
    if (type == Double.class) return "Double";
    if (type == Integer.class) return "Integer";
    if (type == NodeShape.class) return "NodeShape";
    if (type == ArrowShape.class) return "ArrowShape";
    if (type == LineType.class) return "LineType";
    if (type == Font.class) return "Font";
    if (type == String.class) return "String";
    if (type == Boolean.class) return "Boolean";
    return type.getSimpleName();
}

// Helper: get allowed values for discrete-typed properties (NodeShape, ArrowShape, LineType)
// Note: This will be implemented as a public method on VisualPropertyService when Task 12 is built.
private List<String> getAllowedValues(VisualProperty<?> vp) {
    Range<?> range = vp.getRange();
    if (!(range instanceof DiscreteRange<?>)) return null;
    DiscreteRange<?> discrete = (DiscreteRange<?>) range;
    return discrete.values().stream()
        .map(v -> v instanceof VisualPropertyValue
            ? ((VisualPropertyValue) v).getDisplayName()
            : String.valueOf(v))
        .sorted()
        .collect(Collectors.toList());
}
```

### 5.2 `set_visual_default`

```java
public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
    String propertyId = (String) request.arguments().get("property_id");
    Object rawValue = request.arguments().get("value");

    CyNetwork network = appManager.getCurrentNetwork();
    if (network == null) {
        return error("No network is currently loaded. Please load a network first.");
    }

    VisualStyle style = vmmManager.getCurrentVisualStyle();
    VisualLexicon lexicon = renderingEngineManager.getDefaultVisualLexicon();

    // Find the visual property by ID
    VisualProperty<?> vp = findPropertyById(lexicon, propertyId);
    if (vp == null) {
        return error("Unknown visual property: " + propertyId);
    }

    // Parse the value according to the property's type
    Object parsedValue;
    try {
        parsedValue = parseValue(vp, rawValue);
    } catch (IllegalArgumentException e) {
        return error(e.getMessage());
    }

    // Capture old value for reporting
    Object oldValue = style.getDefaultValue(vp);

    // Set the new default — must run on EDT for thread safety
    SwingUtilities.invokeAndWait(() -> {
        @SuppressWarnings("unchecked")
        VisualProperty<Object> typedVP = (VisualProperty<Object>) vp;
        style.setDefaultValue(typedVP, parsedValue);
    });

    // Apply to current view
    CyNetworkView view = appManager.getCurrentNetworkView();
    if (view != null) {
        style.apply(view);
        view.updateView();
    }

    return success(String.format(
        "{\"status\":\"success\",\"property_id\":\"%s\",\"displayName\":\"%s\",\"old_value\":\"%s\",\"new_value\":\"%s\"}",
        propertyId, vp.getDisplayName(), formatValue(oldValue), formatValue(parsedValue)
    ));
}

// Helper: find property by ID string across all node/edge/network properties
private VisualProperty<?> findPropertyById(VisualLexicon lexicon, String idString) {
    for (VisualProperty<?> vp : lexicon.getAllVisualProperties()) {
        if (vp.getIdString().equals(idString)) return vp;
    }
    return null;
}

// Helper: parse a raw value into the correct type for a visual property
private Object parseValue(VisualProperty<?> vp, Object rawValue) {
    Range<?> range = vp.getRange();
    Class<?> type = range.getType();

    if (Paint.class.isAssignableFrom(type)) {
        // Accept hex color strings
        String colorStr = String.valueOf(rawValue);
        if (!colorStr.startsWith("#")) colorStr = "#" + colorStr;
        try {
            return Color.decode(colorStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid color: '" + rawValue + "'. Please use hex format like #FF0000.");
        }
    } else if (type == Double.class) {
        return ((Number) rawValue).doubleValue();
    } else if (type == Integer.class) {
        return ((Number) rawValue).intValue();
    } else if (type == NodeShape.class) {
        return parseNodeShape(String.valueOf(rawValue));
    } else if (type == ArrowShape.class) {
        return parseArrowShape(String.valueOf(rawValue));
    } else if (type == LineType.class) {
        return parseLineType(String.valueOf(rawValue));
    } else if (type == Font.class) {
        return Font.decode(String.valueOf(rawValue));
    } else if (type == String.class) {
        return String.valueOf(rawValue);
    } else if (type == Boolean.class) {
        return Boolean.valueOf(String.valueOf(rawValue));
    }

    throw new IllegalArgumentException("Unsupported property type: " + type.getSimpleName());
}

// Helper: parse node shape from display name (case-insensitive)
private NodeShape parseNodeShape(String name) {
    for (NodeShape shape : NodeShapeVisualProperty.getValues()) {
        if (shape.getDisplayName().equalsIgnoreCase(name)) return shape;
    }
    throw new IllegalArgumentException(
        "Invalid value for NODE_SHAPE: '" + name + "' is not a recognized node shape. " +
        "Valid shapes: " + NodeShapeVisualProperty.getValues().stream()
            .map(NodeShape::getDisplayName).collect(Collectors.joining(", ")));
}

// Similar helpers: parseArrowShape(), parseLineType() — same pattern
```

---

## 6. Edge Cases and Error Handling

### 6.1 No Network Loaded

- **Trigger**: User invokes default_styling with no network in Cytoscape.
- **Tool behavior**: `get_visual_style_defaults` returns error.
- **Agent script**: "No network is currently loaded. Please load a network first using the Network Wizard or by importing a file in Cytoscape."

### 6.2 Invalid Color String

- **Trigger**: User enters a color that can't be parsed (e.g., "salmon" if not mapped to CSS, or "xyz").
- **Tool behavior**: `set_visual_default` returns error with guidance.
- **Agent script**: "I couldn't parse that color. Please use a hex code like #FF6600, or one of the preset color names."

### 6.3 Out-of-Range Numeric Value

- **Trigger**: User enters a negative width or transparency > 255.
- **Tool behavior**: `set_visual_default` returns error.
- **Agent script**: For transparency: "Transparency must be between 0 (fully transparent) and 255 (fully opaque). Please enter a value in that range." For sizes: "Width/size must be a positive number."

### 6.4 Invalid Shape/Enum Name

- **Trigger**: User enters a shape name that doesn't match any enum value.
- **Tool behavior**: `set_visual_default` returns error listing valid options. The valid-values list in the error message is generated dynamically by `VisualPropertyService.parseDiscreteValue()` at runtime (alphabetically sorted), not hardcoded.
- **Agent script**: Present the error message from the tool, which includes the list of valid values.

### 6.5 No Network View

- **Trigger**: Style can be changed but view won't update.
- **Tool behavior**: `set_visual_default` succeeds (style is modified) but notes that view update was skipped.
- **Agent script**: "The default has been changed in the style, but there's no network view to display the change."

### 6.6 Large Number of Properties

- **Trigger**: Runtime lexicon contains many properties (e.g., renderer adds custom ones).
- **Agent behavior**: The property table may be long. Group by category and number sequentially. If more than ~30 properties per category, consider summarizing the less-common ones.

### 6.7 User Enters Property by ID Instead of Display Name

- **Agent behavior**: Accept both. Match against the numbered list first, then display names (case-insensitive), then ID strings as fallback.

### 6.8 Thread Safety — Swing EDT

- **Issue**: `VisualStyle.setDefaultValue()` may trigger view updates that must happen on the Swing Event Dispatch Thread.
- **Implementation**: Wrap the `setDefaultValue` + `style.apply()` calls in `SwingUtilities.invokeAndWait()` to ensure EDT execution. The MCP tool handler runs on a non-EDT thread (Jetty/CyREST request thread), so this dispatch is required.
