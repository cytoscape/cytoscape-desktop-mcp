# Shared Reference: Cytoscape MCP Styling SDK

This document is the authoritative reference for all three MCP Prompt specs (`01-network-wizard-prompt`, `02-default-styling-prompt`, `03-mapping-styling-prompt`). It centralizes SDK constants, enum tables, palettes, type compatibility, and implementation patterns so individual specs can reference this file instead of duplicating information.

---

## Table of Contents

1. [BasicVisualLexicon Constants](#1-basicvisuallexicon-constants)
2. [Enum Value Tables](#2-enum-value-tables)
3. [Color Palettes for Continuous Gradient Mappings](#3-color-palettes-for-continuous-gradient-mappings)
4. [Type Compatibility Matrix](#4-type-compatibility-matrix)
5. [Continuous Sub-Editor Determination](#5-continuous-sub-editor-determination)
6. [Dynamic Property Discovery Patterns](#6-dynamic-property-discovery-patterns)
7. [OSGi Service Lookup Patterns](#7-osgi-service-lookup-patterns)
8. [Build Dependencies](#8-build-dependencies)
9. [MCP SDK Patterns](#9-mcp-sdk-patterns)

---

## 1. BasicVisualLexicon Constants

All visual properties are defined in `org.cytoscape.view.presentation.property.BasicVisualLexicon`. Properties are discovered dynamically via `VisualLexicon.getAllDescendants()`, not hardcoded. The table below lists the standard properties available in Cytoscape 3.10.x for reference; actual runtime availability may include additional properties from renderers or apps.

### 1.1 Node Visual Properties

| Constant | Display Name | Value Type | Description |
|----------|-------------|------------|-------------|
| `NODE_FILL_COLOR` | Node Fill Color | `Paint` | Interior fill color |
| `NODE_BORDER_PAINT` | Node Border Paint | `Paint` | Border stroke color |
| `NODE_BORDER_WIDTH` | Node Border Width | `Double` | Border stroke width (pixels) |
| `NODE_BORDER_LINE_TYPE` | Node Border Line Type | `LineType` | Border stroke pattern |
| `NODE_BORDER_TRANSPARENCY` | Node Border Transparency | `Integer` | Border opacity (0-255) |
| `NODE_TRANSPARENCY` | Node Transparency | `Integer` | Fill opacity (0-255) |
| `NODE_WIDTH` | Node Width | `Double` | Width in pixels |
| `NODE_HEIGHT` | Node Height | `Double` | Height in pixels |
| `NODE_SIZE` | Node Size | `Double` | Uniform size (sets both width and height) |
| `NODE_SHAPE` | Node Shape | `NodeShape` | Geometric shape |
| `NODE_LABEL` | Node Label | `String` | Text label displayed on node |
| `NODE_LABEL_COLOR` | Node Label Color | `Paint` | Label text color |
| `NODE_LABEL_FONT_SIZE` | Node Label Font Size | `Integer` | Label font size (points) |
| `NODE_LABEL_FONT_FACE` | Node Label Font Face | `Font` | Label font family and style |
| `NODE_LABEL_TRANSPARENCY` | Node Label Transparency | `Integer` | Label opacity (0-255) |
| `NODE_LABEL_WIDTH` | Node Label Width | `Double` | Maximum label width before wrapping |
| `NODE_SELECTED_PAINT` | Node Selected Paint | `Paint` | Fill color when selected |
| `NODE_TOOLTIP` | Node Tooltip | `String` | Hover tooltip text |
| `NODE_VISIBLE` | Node Visible | `Boolean` | Visibility toggle |
| `NODE_NESTED_NETWORK_IMAGE_VISIBLE` | Nested Network Image Visible | `Boolean` | Show nested network preview |

### 1.2 Edge Visual Properties

| Constant | Display Name | Value Type | Description |
|----------|-------------|------------|-------------|
| `EDGE_STROKE_UNSELECTED_PAINT` | Edge Stroke Color (Unselected) | `Paint` | Edge line color |
| `EDGE_STROKE_SELECTED_PAINT` | Edge Stroke Color (Selected) | `Paint` | Edge line color when selected |
| `EDGE_UNSELECTED_PAINT` | Edge Color (Unselected) | `Paint` | Overall edge color (legacy) |
| `EDGE_SELECTED_PAINT` | Edge Color (Selected) | `Paint` | Overall edge color when selected (legacy) |
| `EDGE_WIDTH` | Edge Width | `Double` | Line width in pixels |
| `EDGE_LINE_TYPE` | Edge Line Type | `LineType` | Line pattern (solid, dashed, etc.) |
| `EDGE_TRANSPARENCY` | Edge Transparency | `Integer` | Edge opacity (0-255) |
| `EDGE_SOURCE_ARROW_SHAPE` | Source Arrow Shape | `ArrowShape` | Arrow shape at source end |
| `EDGE_TARGET_ARROW_SHAPE` | Target Arrow Shape | `ArrowShape` | Arrow shape at target end |
| `EDGE_SOURCE_ARROW_SIZE` | Source Arrow Size | `Double` | Arrow size at source end |
| `EDGE_TARGET_ARROW_SIZE` | Target Arrow Size | `Double` | Arrow size at target end |
| `EDGE_SOURCE_ARROW_UNSELECTED_PAINT` | Source Arrow Color | `Paint` | Arrow color at source |
| `EDGE_TARGET_ARROW_UNSELECTED_PAINT` | Target Arrow Color | `Paint` | Arrow color at target |
| `EDGE_LABEL` | Edge Label | `String` | Text label displayed on edge |
| `EDGE_LABEL_COLOR` | Edge Label Color | `Paint` | Label text color |
| `EDGE_LABEL_FONT_SIZE` | Edge Label Font Size | `Integer` | Label font size (points) |
| `EDGE_LABEL_FONT_FACE` | Edge Label Font Face | `Font` | Label font family and style |
| `EDGE_LABEL_TRANSPARENCY` | Edge Label Transparency | `Integer` | Label opacity (0-255) |
| `EDGE_TOOLTIP` | Edge Tooltip | `String` | Hover tooltip text |
| `EDGE_VISIBLE` | Edge Visible | `Boolean` | Visibility toggle |
| `EDGE_CURVED` | Edge Curved | `Boolean` | Use curved edges |
| `EDGE_BEND` | Edge Bend | `Bend` | Custom bend points |

### 1.3 Network Visual Properties

| Constant | Display Name | Value Type | Description |
|----------|-------------|------------|-------------|
| `NETWORK_BACKGROUND_PAINT` | Network Background Paint | `Paint` | Canvas background color |
| `NETWORK_TITLE` | Network Title | `String` | Network view title |
| `NETWORK_WIDTH` | Network Width | `Double` | Canvas width |
| `NETWORK_HEIGHT` | Network Height | `Double` | Canvas height |

---

## 2. Enum Value Tables

These enums are the discrete domain values for shape/line-type/arrow visual properties.

### 2.1 NodeShape (`org.cytoscape.view.presentation.property.NodeShapeVisualProperty`)

| Value | Display Name |
|-------|-------------|
| `ELLIPSE` | Ellipse |
| `RECTANGLE` | Rectangle |
| `ROUND_RECTANGLE` | Round Rectangle |
| `TRIANGLE` | Triangle |
| `DIAMOND` | Diamond |
| `HEXAGON` | Hexagon |
| `OCTAGON` | Octagon |
| `PARALLELOGRAM` | Parallelogram |
| `VEE` | Vee |

### 2.2 ArrowShape (`org.cytoscape.view.presentation.property.ArrowShapeVisualProperty`)

| Value | Display Name |
|-------|-------------|
| `NONE` | None |
| `ARROW` | Arrow |
| `ARROW_SHORT` | Arrow Short |
| `DELTA` | Delta |
| `DELTA_SHORT_1` | Delta Short 1 |
| `DELTA_SHORT_2` | Delta Short 2 |
| `DIAMOND` | Diamond |
| `DIAMOND_SHORT_1` | Diamond Short 1 |
| `DIAMOND_SHORT_2` | Diamond Short 2 |
| `CIRCLE` | Circle |
| `HALF_TOP` | Half Top |
| `HALF_BOTTOM` | Half Bottom |
| `T` | T |
| `CROSS_OPEN_DELTA` | Cross Open Delta |
| `CROSS_DELTA` | Cross Delta |

### 2.3 LineType (`org.cytoscape.view.presentation.property.LineTypeVisualProperty`)

| Value | Display Name |
|-------|-------------|
| `SOLID` | Solid |
| `LONG_DASH` | Long Dash |
| `EQUAL_DASH` | Equal Dash |
| `DASH_DOT` | Dash Dot |
| `DOT` | Dots |
| `SEPARATE_ARROW` | Separate Arrow |
| `ZIGZAG` | Zigzag |
| `SINEWAVE` | Sine Wave |
| `CONTIGUOUS_ARROW` | Contiguous Arrow |
| `BACKWARD_SLASH` | Backward Slash |
| `FORWARD_SLASH` | Forward Slash |
| `PARALLEL_LINES` | Parallel Lines |
| `VERTICAL_SLASH` | Vertical Slash |

### 2.4 Font

Font values are `java.awt.Font` objects. When presented to the user, show as `"FontFamily-Style-Size"` (e.g., `"SansSerif-Plain-12"`). Accept user input in the same format or as separate fields (family, style: Plain/Bold/Italic/BoldItalic, size).

---

## 3. Color Palettes for Continuous Gradient Mappings

These are the built-in palette presets offered when creating continuous-color-gradient mappings. Each palette defines colors for the minimum, midpoint (optional), and maximum of the data range.

| Palette Name | Min Color | Mid Color | Max Color | Description |
|-------------|-----------|-----------|-----------|-------------|
| Blue to Red | `#0000FF` | — | `#FF0000` | Classic diverging, cold→hot |
| White to Blue | `#FFFFFF` | — | `#0000FF` | Sequential, light→dark blue |
| Blue-White-Red | `#0000FF` | `#FFFFFF` | `#FF0000` | Diverging with neutral midpoint |
| Yellow-Green-Blue (YlGnBu) | `#FFFFCC` | `#41B6C4` | `#0C2C84` | Sequential multi-hue |
| Green-Black-Red (GnBkRd) | `#00FF00` | `#000000` | `#FF0000` | Diverging, green→black→red |
| Purple to Orange | `#7B3294` | `#F7F7F7` | `#E66101` | Diverging, cool→warm |

### Custom Colors

The agent should also allow the user to specify arbitrary hex colors (e.g., `#FF6600`) or named CSS colors (e.g., `coral`, `steelblue`). The MCP tool accepts hex color strings; the server converts them to `java.awt.Color` via `Color.decode()`.

---

## 4. Type Compatibility Matrix

This matrix determines which CyColumn data types can drive mappings for which visual property value types.

### 4.1 Column Types

| CyColumn Type | Java Type | Suitable For |
|--------------|-----------|-------------|
| `Integer` | `java.lang.Integer` | Continuous, Discrete |
| `Long` | `java.lang.Long` | Continuous, Discrete |
| `Double` | `java.lang.Double` | Continuous |
| `String` | `java.lang.String` | Discrete, Passthrough |
| `Boolean` | `java.lang.Boolean` | Discrete |
| `List` | `java.util.List` | (not mappable) |

### 4.2 Mapping Type × Column Type Compatibility

| Mapping Type | Numeric Columns (Integer, Long, Double) | String Columns | Boolean Columns |
|-------------|----------------------------------------|----------------|-----------------|
| **Continuous** | Yes | No | No |
| **Discrete** | Yes | Yes | Yes |
| **Passthrough** | Yes (converts to string) | Yes | Yes (converts to string) |

### 4.3 Visual Property Value Type × Mapping Type Compatibility

| VP Value Type | Continuous | Discrete | Passthrough |
|--------------|-----------|----------|-------------|
| `Double` (size, width) | Yes (numeric interpolation) | Yes | No |
| `Integer` (transparency, font size) | Yes (numeric interpolation) | Yes | No |
| `Paint` (colors) | Yes (color gradient) | Yes | No |
| `NodeShape` | No (use continuous-discrete) | Yes | No |
| `ArrowShape` | No (use continuous-discrete) | Yes | No |
| `LineType` | No (use continuous-discrete) | Yes | No |
| `Font` | No | Yes | No |
| `String` (labels, tooltips) | No | Yes | Yes |
| `Boolean` | No | Yes | No |

---

## 5. Continuous Sub-Editor Determination

When the user selects a continuous mapping for a visual property, the system determines the sub-editor type by inspecting `VisualProperty.getRange()`:

```
VisualProperty vp = <selected property>;
Range<?> range = vp.getRange();

if (range instanceof ContinuousRange) {
    Class<?> type = range.getType();
    if (Paint.class.isAssignableFrom(type) || Color.class.isAssignableFrom(type)) {
        → CONTINUOUS-COLOR-GRADIENT
        // User picks a palette or custom colors, maps numeric data → color interpolation
    } else if (Number.class.isAssignableFrom(type)) {
        → CONTINUOUS-CONTINUOUS (numeric interpolation)
        // User provides min/max property values, maps numeric data → numeric property
    }
} else if (range instanceof DiscreteRange) {
    → CONTINUOUS-DISCRETE (threshold-based)
    // User defines numeric thresholds, assigns discrete values per region
    // Example: degree < 5 → Ellipse, degree 5-10 → Rectangle, degree > 10 → Diamond
}
```

### Decision Summary

| `VisualProperty.getRange()` | Range Type | Value Type | Sub-Editor |
|-----------------------------|-----------|------------|------------|
| `ContinuousRange<Double>` | Continuous | Numeric | Continuous-Continuous |
| `ContinuousRange<Integer>` | Continuous | Numeric | Continuous-Continuous |
| `ContinuousRange<Paint>` | Continuous | Color | Continuous-Color-Gradient |
| `DiscreteRange<NodeShape>` | Discrete | Enum | Continuous-Discrete |
| `DiscreteRange<ArrowShape>` | Discrete | Enum | Continuous-Discrete |
| `DiscreteRange<LineType>` | Discrete | Enum | Continuous-Discrete |
| `DiscreteRange<Font>` | Discrete | Object | Continuous-Discrete |

---

## 6. Dynamic Property Discovery Patterns

Properties are NEVER hardcoded. The MCP tools enumerate them at runtime from the Cytoscape SDK.

### 6.1 Enumerating All Visual Properties

```java
// Get the visual lexicon from the current render engine
RenderingEngineManager renderingEngineManager = getService(bc, RenderingEngineManager.class);
VisualLexicon lexicon = renderingEngineManager.getDefaultVisualLexicon();

// Get all node properties
Set<VisualProperty<?>> nodeProps = lexicon.getAllDescendants(BasicVisualLexicon.NODE);

// Get all edge properties
Set<VisualProperty<?>> edgeProps = lexicon.getAllDescendants(BasicVisualLexicon.EDGE);
```

### 6.2 Filtering to Supported Types

```java
private static final Set<Class<?>> SUPPORTED_TYPES = Set.of(
    Paint.class, Color.class,
    Double.class, Integer.class,
    NodeShape.class, ArrowShape.class, LineType.class,
    Font.class, String.class, Boolean.class
);

boolean isSupported(VisualProperty<?> vp) {
    Range<?> range = vp.getRange();
    if (range == null) return false;
    return SUPPORTED_TYPES.stream().anyMatch(t -> t.isAssignableFrom(range.getType()));
}
```

### 6.3 Getting Current Defaults

```java
VisualMappingManager vmm = getService(bc, VisualMappingManager.class);
VisualStyle style = vmm.getCurrentVisualStyle();

for (VisualProperty<?> vp : nodeProps) {
    Object defaultValue = style.getDefaultValue(vp);
    // Format for display: colors as hex, shapes as display name, etc.
}
```

### 6.4 Getting Current Mappings

```java
for (VisualMappingFunction<?, ?> mapping : style.getAllVisualMappingFunctions()) {
    String vpId = mapping.getVisualProperty().getIdString();
    String column = mapping.getMappingColumnName();
    String type = mapping.getClass().getSimpleName(); // ContinuousMapping, DiscreteMapping, PassthroughMapping
}
```

### 6.5 Enumerating Table Columns

```java
CyNetwork network = appManager.getCurrentNetwork();
CyTable nodeTable = network.getDefaultNodeTable();

for (CyColumn column : nodeTable.getColumns()) {
    String name = column.getName();
    Class<?> type = column.getType();
    // Skip internal columns like SUID, selected
    if (name.equals(CyNetwork.SUID) || name.equals("selected")) continue;
}
```

---

## 7. OSGi Service Lookup Patterns

### 7.1 Standard Service Lookup (in CyActivator.start)

```java
// Simple service lookup
CyApplicationManager appManager = getService(bundleContext, CyApplicationManager.class);
CyNetworkManager networkManager = getService(bundleContext, CyNetworkManager.class);
CyNetworkViewManager viewManager = getService(bundleContext, CyNetworkViewManager.class);

// Filtered service lookup (by property)
InputStreamTaskFactory readerFactory = getService(bundleContext,
    InputStreamTaskFactory.class, "(id=cytoscapeCxNetworkReaderFactory)");

// Visual mapping services
VisualMappingManager vmm = getService(bundleContext, VisualMappingManager.class);
VisualMappingFunctionFactory continuousFactory = getService(bundleContext,
    VisualMappingFunctionFactory.class, "(mapping.type=continuous)");
VisualMappingFunctionFactory discreteFactory = getService(bundleContext,
    VisualMappingFunctionFactory.class, "(mapping.type=discrete)");
VisualMappingFunctionFactory passthroughFactory = getService(bundleContext,
    VisualMappingFunctionFactory.class, "(mapping.type=passthrough)");

// Layout services
CyLayoutAlgorithmManager layoutManager = getService(bundleContext, CyLayoutAlgorithmManager.class);

// Rendering engine (for VisualLexicon)
RenderingEngineManager renderingEngineManager = getService(bundleContext, RenderingEngineManager.class);

// Task execution
SynchronousTaskManager<?> syncTaskManager = getService(bundleContext, SynchronousTaskManager.class);

// File loading
LoadNetworkFileTaskFactory loadFileFactory = getService(bundleContext, LoadNetworkFileTaskFactory.class);

// View creation (for networks loaded without a view, e.g., large networks)
CyNetworkViewFactory networkViewFactory = getService(bundleContext, CyNetworkViewFactory.class);
```

### 7.2 Registering MCP Prompts

```java
// In CyActivator.startMcpServer(), after server creation:
mcpServer.addPrompt(networkWizardPrompt.toSpec());
mcpServer.addPrompt(defaultStylingPrompt.toSpec());
mcpServer.addPrompt(mappingStylingPrompt.toSpec());

// Server capabilities must include prompts:
ServerCapabilities.builder()
    .tools(false)
    .prompts(false)  // false = list-changed notifications not supported
    .build()
```

### 7.3 Tool Registration Pattern (existing)

```java
// From LoadNetworkViewTool — pattern all tools follow:
public McpServerFeatures.SyncToolSpecification toSpec() {
    return McpServerFeatures.SyncToolSpecification.builder()
        .tool(new Tool(TOOL_NAME, TOOL_DESCRIPTION, TOOL_SCHEMA))
        .callHandler(this::handle)
        .build();
}
```

---

## 8. Build Dependencies

The following dependencies must be added to `build.gradle` (all `compileOnly`, as they are provided by the Cytoscape runtime):

```gradle
dependencies {
    // Existing deps...

    // Visual mapping and presentation (new for styling prompts)
    compileOnly "org.cytoscape:vizmap-api:3.10.0"
    compileOnly "org.cytoscape:vizmap-gui-api:3.10.0"
    compileOnly "org.cytoscape:presentation-api:3.10.0"

    // Layout (new for layout tools)
    compileOnly "org.cytoscape:layout-api:3.10.0"

    // Core task factories (new for file loading)
    compileOnly "org.cytoscape:core-task-api:3.10.0"
}
```

### Maven Coordinates Reference

| Artifact | Group | Purpose |
|----------|-------|---------|
| `vizmap-api` | `org.cytoscape` | `VisualMappingManager`, `VisualStyle`, `VisualMappingFunction*` |
| `vizmap-gui-api` | `org.cytoscape` | `DiscreteMappingGenerator` (if accessible) |
| `presentation-api` | `org.cytoscape` | `VisualLexicon`, `BasicVisualLexicon`, visual property types |
| `layout-api` | `org.cytoscape` | `CyLayoutAlgorithmManager`, `CyLayoutAlgorithm` |
| `core-task-api` | `org.cytoscape` | `LoadNetworkFileTaskFactory`, `NetworkAnalyzerTaskFactory` |

---

## 9. MCP SDK Patterns

### 9.1 Prompt Specification (Java MCP SDK 0.12.x)

```java
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;  // or PromptSpecification
import io.modelcontextprotocol.spec.McpSchema.*;

Prompt prompt = new Prompt(
    "prompt_name",                          // unique name
    "Human-Readable Title",                 // title (optional in spec, recommended)
    List.of(                                // arguments
        new PromptArgument("arg_name", "description", true)  // name, description, required
    )
);

PromptSpecification spec = new PromptSpecification(
    prompt,
    (exchange, request) -> {
        Map<String, String> args = request.arguments();
        String systemPrompt = "...";  // full system prompt text

        return new GetPromptResult(
            "Result description",
            List.of(
                new PromptMessage(Role.ASSISTANT, new TextContent(systemPrompt))
            )
        );
    }
);
```

### 9.2 Tool Specification (Java MCP SDK 0.12.x)

```java
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.*;

String inputSchema = """
    {
        "type": "object",
        "properties": {
            "param1": { "type": "string", "description": "..." },
            "param2": { "type": "number", "description": "..." }
        },
        "required": ["param1"]
    }
    """;

SyncToolSpecification spec = SyncToolSpecification.builder()
    .tool(new Tool("tool_name", "Tool description", inputSchema))
    .callHandler((exchange, request) -> {
        Map<String, Object> args = request.arguments();
        // ... tool logic ...
        return new CallToolResult(List.of(new TextContent("result")), false);
    })
    .build();
```

### 9.3 CallToolResult Shape

```json
{
    "content": [
        {
            "type": "text",
            "text": "Result message or JSON data"
        }
    ],
    "isError": false
}
```

### 9.4 PromptMessage Roles

| Role | Usage |
|------|-------|
| `Role.USER` | Injected as a user message in the conversation |
| `Role.ASSISTANT` | Injected as an assistant (system-like) message |

For prompt specs, the system prompt is typically injected as `Role.ASSISTANT` so it acts as instruction context for the LLM driving the conversation.

---

## Conventions Used Across All Specs

1. **Property identification**: Tools accept visual property IDs as `idString` (e.g., `"NODE_FILL_COLOR"`, `"EDGE_WIDTH"`). These match `VisualProperty.getIdString()`.

2. **Color format**: All color values are passed as hex strings `"#RRGGBB"` (e.g., `"#FF0000"` for red). Server converts via `Color.decode()`.

3. **Shape/enum format**: Passed as display name strings (e.g., `"Ellipse"`, `"Arrow"`, `"Solid"`). Server matches via case-insensitive lookup against the enum's `getDisplayName()`.

4. **Font format**: Passed as `"Family-Style-Size"` string (e.g., `"SansSerif-Plain-12"`). Server parses via `Font.decode()`.

5. **Error responses**: All tools return `CallToolResult` with `isError: true` and a human-readable error message. The agent should present the error to the user and suggest corrective action.

6. **Current network/view context**: Tools operate on `CyApplicationManager.getCurrentNetwork()` and `.getCurrentNetworkView()`. If no network is loaded, tools return an error: `"No network is currently loaded. Please load a network first."` **Exceptions**: `get_loaded_network_views` enumerates all loaded networks (does not require a current selection), and `load_cytoscape_network_view` creates a new network and sets it as current (does not require a pre-existing current network).
