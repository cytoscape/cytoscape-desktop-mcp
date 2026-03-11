package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.idekerlab.cytoscapemcp.McpSchema;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.model.VisualProperty;
import org.cytoscape.view.presentation.RenderingEngineManager;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool that sets default visual property values in the active Cytoscape Desktop visual style.
 * Accepts lists of node and/or edge property updates, applies them atomically on the Swing EDT,
 * then returns a lightweight confirmation containing only the properties that were updated.
 */
public class SetVisualDefaultTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetVisualDefaultTool.class);

    private static final String TOOL_NAME = "set_visual_default";

    private static final String TOOL_TITLE = "Set Cytoscape Desktop Style Defaults";

    private static final String TOOL_DESCRIPTION =
            "Sets default visual property values in the active Cytoscape Desktop visual style"
                    + " for nodes and/or edges — such as fill color, size, shape, border style,"
                    + " edge width, line type, font, or arrow shape. Use when you want to change"
                    + " how network elements appear by default; retrieve the current style defaults"
                    + " first to discover all valid property identifiers, their allowed value"
                    + " formats, and available font families and styles, then provide only the"
                    + " entries you want to update. For font properties, compose the value as"
                    + " Family-Style-Size using a family name and style from the style defaults"
                    + " (e.g. Arial-Bold-14). Returns an error if no network is currently loaded,"
                    + " if a property identifier is not recognized, or if a value cannot be parsed"
                    + " or falls outside the valid range — each error message identifies the"
                    + " specific property and failure reason. State-mutating; modifies the active"
                    + " visual style and immediately updates the current view if one exists.";

    private static final String TOOL_EXAMPLES =
            "\n\n## Examples\n\n"
                    + "Example 1 — Change the default node fill color to orange and increase node"
                    + " size:\n"
                    + "{\"node_properties\": [{\"id\": \"NODE_FILL_COLOR\","
                    + " \"currentValue\": \"#FF6600\"}, {\"id\": \"NODE_SIZE\","
                    + " \"currentValue\": \"45.0\"}]}\n\n"
                    + "Example 2 — Set node shape to Rectangle and increase edge width:\n"
                    + "{\"node_properties\": [{\"id\": \"NODE_SHAPE\","
                    + " \"currentValue\": \"Rectangle\"}], \"edge_properties\":"
                    + " [{\"id\": \"EDGE_WIDTH\", \"currentValue\": \"3.0\"}]}\n\n"
                    + "Example 3 — Style edges with dashed lines and arrow targets:\n"
                    + "{\"edge_properties\": [{\"id\": \"EDGE_LINE_TYPE\","
                    + " \"currentValue\": \"Dash\"}, {\"id\": \"EDGE_TARGET_ARROW_SHAPE\","
                    + " \"currentValue\": \"Arrow\"}]}\n\n"
                    + "Example 4 — Update the default node label font"
                    + " obtained from the current style state:\n"
                    + "{\"node_properties\": [{\"id\": \"NODE_LABEL_FONT_FACE\","
                    + " \"currentValue\": \"SansSerif-Bold-14\"}]}\n\n"
                    + "Example 5 — Set the node label font to bold Arial at 16pt:\n"
                    + "{\"node_properties\": [{\"id\": \"NODE_LABEL_FONT_FACE\","
                    + " \"currentValue\": \"Arial-Bold-16\"}]}";

    private static final String NOTE_NO_VIEW =
            "Style defaults updated but no network view is active; changes will appear when a"
                    + " view is created.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // -- Response wrapper (setter-specific) -----------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record SetVisualDefaultsResponse(
            @JsonPropertyDescription(
                            "Outcome of the operation. Always 'success' for non-error responses."
                                    + "\n\nExamples: \"success\"")
                    @JsonProperty("status")
                    String status,
            @JsonPropertyDescription(
                            "Name of the active visual style that was modified."
                                    + "\n\nExamples: \"default\", \"Marquee\"")
                    @JsonProperty("style_name")
                    String styleName,
            @JsonPropertyDescription(
                            "Properties that were updated, each showing its new default value as"
                                    + " confirmed by reading back from the style after mutation.")
                    @JsonProperty("updated_properties")
                    List<UpdatedPropertyEntry> updatedProperties,
            @JsonPropertyDescription(
                            "Informational note present only when no network view is currently"
                                    + " active. The style defaults were updated successfully but the"
                                    + " visual change cannot be rendered until a view is created."
                                    + " Absent when a view is present.")
                    @JsonProperty("note")
                    String note) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record UpdatedPropertyEntry(
            @JsonPropertyDescription(
                            "Machine-readable visual property identifier that was updated."
                                    + "\n\nExamples: \"NODE_FILL_COLOR\", \"EDGE_WIDTH\","
                                    + " \"NODE_LABEL_FONT_FACE\"")
                    @JsonProperty("id")
                    String id,
            @JsonPropertyDescription(
                            "Human-readable display name of the updated property."
                                    + "\n\nExamples: \"Node Fill Color\", \"Edge Width\"")
                    @JsonProperty("displayName")
                    String displayName,
            @JsonPropertyDescription(
                            "New default value after update, formatted as a string. Colors use"
                                    + " hex (#RRGGBB); shapes, line types, and arrows use their"
                                    + " display name; fonts use Family-Style-Size format; numbers"
                                    + " use decimal notation."
                                    + "\n\nExamples: \"#FF6600\", \"3.0\", \"Arial-Bold-14\"")
                    @JsonProperty("currentValue")
                    String currentValue) {}

    // -- Schemas --------------------------------------------------------------

    static final String INPUT_SCHEMA =
            McpSchema.toJson(
                    McpSchema.InputSchema.builder()
                            .property(
                                    "node_properties",
                                    new McpSchema.InputProperty(
                                            "array",
                                            "Optional. List of node visual property updates."
                                                    + " Each entry requires an 'id' field matching a"
                                                    + " property identifier from the style defaults"
                                                    + " response and a 'currentValue' field with the"
                                                    + " new default value as a string, formatted"
                                                    + " according to the property's value type and"
                                                    + " allowed values documented in that response."
                                                    + " For font properties, compose the value as"
                                                    + " Family-Style-Size using a family from the"
                                                    + " font_families list and a style from the"
                                                    + " font_styles list in the style defaults"
                                                    + " response."
                                                    + "\n\nExamples:"
                                                    + " [{\"id\": \"NODE_FILL_COLOR\","
                                                    + " \"currentValue\": \"#FF6600\"}],"
                                                    + " [{\"id\": \"NODE_SHAPE\","
                                                    + " \"currentValue\": \"Rectangle\"}],"
                                                    + " [{\"id\": \"NODE_LABEL_FONT_FACE\","
                                                    + " \"currentValue\": \"Arial-Bold-14\"}]"))
                            .property(
                                    "edge_properties",
                                    new McpSchema.InputProperty(
                                            "array",
                                            "Optional. List of edge visual property updates."
                                                    + " Each entry requires 'id' and 'currentValue'"
                                                    + " using the same format conventions as node"
                                                    + " properties — property identifiers, value"
                                                    + " types, allowed values, font families, and"
                                                    + " font styles are documented in the style"
                                                    + " defaults response."
                                                    + "\n\nExamples:"
                                                    + " [{\"id\": \"EDGE_WIDTH\","
                                                    + " \"currentValue\": \"3.0\"}],"
                                                    + " [{\"id\": \"EDGE_LINE_TYPE\","
                                                    + " \"currentValue\": \"Dash\"}],"
                                                    + " [{\"id\": \"EDGE_LABEL_FONT_FACE\","
                                                    + " \"currentValue\":"
                                                    + " \"Courier New-Italic-12\"}]"))
                            .build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(SetVisualDefaultsResponse.class);

    // -- Dependencies ---------------------------------------------------------

    private final CyApplicationManager appManager;
    private final VisualMappingManager vmmManager;
    private final RenderingEngineManager renderingEngineManager;
    private final VisualPropertyService vpService;

    public SetVisualDefaultTool(
            CyApplicationManager appManager,
            VisualMappingManager vmmManager,
            RenderingEngineManager renderingEngineManager,
            VisualPropertyService vpService) {
        this.appManager = appManager;
        this.vmmManager = vmmManager;
        this.renderingEngineManager = renderingEngineManager;
        this.vpService = vpService;
    }

    /** Returns the MCP SyncToolSpecification to register with the McpSyncServer. */
    public McpServerFeatures.SyncToolSpecification toSpec() {
        try {
            Tool toolDef =
                    Tool.builder()
                            .name(TOOL_NAME)
                            .title(TOOL_TITLE)
                            .description(TOOL_DESCRIPTION + TOOL_EXAMPLES)
                            .inputSchema(MAPPER.readValue(INPUT_SCHEMA, JsonSchema.class))
                            .outputSchema(
                                    MAPPER.readValue(
                                            OUTPUT_SCHEMA,
                                            new TypeReference<Map<String, Object>>() {}))
                            .build();
            return McpServerFeatures.SyncToolSpecification.builder()
                    .tool(toolDef)
                    .callHandler(this::handle)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build tool spec for " + TOOL_NAME, e);
        }
    }

    // -- Handler --------------------------------------------------------------

    private CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        LOGGER.info("Tool call received: {}", TOOL_NAME);

        try {
            // Error check: no network loaded
            if (appManager.getCurrentNetwork() == null) {
                return error(
                        "No network is currently loaded in Cytoscape Desktop. Load or create a"
                                + " network first before setting visual style defaults.");
            }

            // View is optional — setter succeeds even without a view, but cannot apply or
            // refresh. The note field informs the LLM of this condition.
            CyNetworkView view = appManager.getCurrentNetworkView();

            VisualStyle style = vmmManager.getCurrentVisualStyle();
            VisualLexicon lexicon = renderingEngineManager.getDefaultVisualLexicon();

            // Collect input updates from node_properties and edge_properties arrays
            Map<String, Object> args = request.arguments();
            if (args == null) args = Collections.emptyMap();

            List<Map<String, Object>> nodeInput = safeList(args.get("node_properties"));
            List<Map<String, Object>> edgeInput = safeList(args.get("edge_properties"));

            // Validate all entries before making any mutations (fail-fast)
            List<PropertyUpdate> updates = new ArrayList<>();
            try {
                for (Map<String, Object> entry : nodeInput) {
                    updates.add(resolveUpdate(lexicon, entry));
                }
                for (Map<String, Object> entry : edgeInput) {
                    updates.add(resolveUpdate(lexicon, entry));
                }
            } catch (Exception e) {
                return validationError(e.getMessage());
            }

            // Apply mutations on Swing EDT, then refresh view if available
            try {
                Runnable applyTask =
                        () -> {
                            for (PropertyUpdate u : updates) {
                                setDefault(style, u.vp(), u.parsedValue());
                            }
                            if (view != null) {
                                style.apply(view);
                                view.updateView();
                            }
                        };

                if (EventQueue.isDispatchThread()) {
                    applyTask.run();
                } else {
                    SwingUtilities.invokeAndWait(applyTask);
                }
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                LOGGER.error("Error applying visual style defaults on EDT", cause);
                return error(
                        "Failed to apply visual style defaults: "
                                + (cause.getMessage() != null
                                        ? cause.getMessage()
                                        : cause.getClass().getSimpleName()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return error("Interrupted while applying visual style defaults.");
            }

            // Build lightweight confirmation from the updates that were applied
            List<UpdatedPropertyEntry> confirmed = new ArrayList<>();
            for (PropertyUpdate u : updates) {
                Object newDefault = style.getDefaultValue(u.vp());
                confirmed.add(
                        new UpdatedPropertyEntry(
                                u.vp().getIdString(),
                                u.vp().getDisplayName(),
                                vpService.formatValue(newDefault)));
            }

            String note = view == null ? NOTE_NO_VIEW : null;

            return CallToolResult.builder()
                    .structuredContent(
                            new SetVisualDefaultsResponse(
                                    "success", style.getTitle(), confirmed, note))
                    .build();

        } catch (Exception e) {
            LOGGER.error("Error setting visual style defaults", e);
            return error("Failed to set visual style defaults: " + e.getMessage());
        }
    }

    // -- Private helpers ------------------------------------------------------

    /** Resolves and validates a single input entry ({id, currentValue}) into a typed update. */
    private PropertyUpdate resolveUpdate(VisualLexicon lexicon, Map<String, Object> entry)
            throws Exception {
        Object idRaw = entry.get("id");
        Object valueRaw = entry.get("currentValue");

        if (idRaw == null) {
            throw new IllegalArgumentException(
                    "Each property update entry must include an 'id' field.");
        }
        String id = String.valueOf(idRaw);

        if (valueRaw == null) {
            throw new IllegalArgumentException(
                    "Missing 'currentValue' for property '"
                            + id
                            + "'. Each entry must include both 'id' and 'currentValue'.");
        }

        VisualProperty<?> vp = vpService.findPropertyById(lexicon, id);
        if (vp == null) {
            throw new IllegalArgumentException(
                    "Unknown visual property ID '"
                            + id
                            + "'. Call get_visual_style_defaults to retrieve valid property IDs"
                            + " and their current values.");
        }

        Object parsed;
        try {
            parsed = vpService.parseValue(vp, valueRaw);
        } catch (Exception e) {
            String typeName = vpService.getTypeName(vp.getRange());
            throw new Exception(
                    "Invalid value '"
                            + valueRaw
                            + "' for property '"
                            + id
                            + "' (type: "
                            + typeName
                            + "): "
                            + e.getMessage());
        }

        // Range validation for continuous numeric types
        String minStr = vpService.getRangeMin(vp);
        if (minStr != null && parsed instanceof Number n) {
            double v = n.doubleValue();
            double min = Double.parseDouble(minStr);
            double max = Double.parseDouble(vpService.getRangeMax(vp));
            if (v < min || v > max) {
                throw new IllegalArgumentException(
                        "Value "
                                + v
                                + " is out of range for property '"
                                + id
                                + "'. Valid range: "
                                + min
                                + " to "
                                + max
                                + ".");
            }
        }

        return new PropertyUpdate(vp, parsed);
    }

    /** Type-safe helper to call {@code style.setDefaultValue} without unchecked cast warnings. */
    @SuppressWarnings("unchecked")
    private static <V> void setDefault(VisualStyle style, VisualProperty<V> vp, Object value) {
        style.setDefaultValue(vp, (V) value);
    }

    /** Safely casts and copies an input list-of-maps argument, returning an empty list on null. */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> safeList(Object raw) {
        if (raw instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return Collections.emptyList();
    }

    // -- Result helpers -------------------------------------------------------

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }

    private static CallToolResult validationError(String message) {
        ValidationError err = new ValidationError(message, "invalid_input");
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .structuredContent(err)
                .isError(true)
                .build();
    }

    // -- Inner records --------------------------------------------------------

    private record ValidationError(
            @JsonPropertyDescription(
                            "Description of the specific input that failed validation — includes"
                                    + " which property ID was invalid, what value was rejected, and"
                                    + " (for discrete types) the list of accepted values.")
                    @JsonProperty("error_message")
                    String error_message,
            @JsonPropertyDescription(
                            "Category of error. Always 'invalid_input' for validation errors —"
                                    + " indicates the request can be corrected and retried with a"
                                    + " valid value.")
                    @JsonProperty("error_type")
                    String error_type) {}

    private record PropertyUpdate(VisualProperty<?> vp, Object parsedValue) {}
}
