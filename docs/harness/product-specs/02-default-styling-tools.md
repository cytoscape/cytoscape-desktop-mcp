# Tool Spec: Default Styling Tools

## 4. MCP Tool Schemas

### 4.1 `get_visual_style_defaults`

```json
{
  "name": "get_visual_style_defaults",
  "title": "Get Cytoscape Desktop Style Defaults",
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
  "title": "Set Cytoscape Desktop Style Default",
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

## 5. Use Case: User Chooses a Different Style

### 5.1 Get available styles

**Trigger**: User asks "what styles do I have?", "list styles", "switch to a different style"

Call tool: `get_styles`

Optionally also call: `get_loaded_network_views` to see which view is current and its style.

Present results:
"Here are the visual styles available in Cytoscape Desktop:

| # | Style Name |
|---|------------|
| 1 | default |
| 2 | Marquee |
| 3 | Nested Network Style |

Your current view is using the 'default' style. Which style would you like to apply?
(enter the number or name)"

### 5.2 Switch to chosen style

Call tool: `switch_current_style` with `{"name": "<chosen_style>"}`

If success → "Switched the current view to the '<style_name>' style."
If error → Present error message and suggest alternatives.

### 5.3 Create a new named style

**Trigger**: User asks "create a new style called X" or "make a copy of this style as X"

Ask: "Which existing style would you like to base the new style on?"
- Present style list from `get_styles`
- User picks one → `{"name": "X", "create_from": "<source_style>"}`

If success → Retrieve style defaults for the newly active style, then inform user:
"Created style 'X' based on '<source_style>'. Here are its current defaults —
what would you like to change?"

Then proceed with property changes using the standard STEP 1–4 flow.

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
