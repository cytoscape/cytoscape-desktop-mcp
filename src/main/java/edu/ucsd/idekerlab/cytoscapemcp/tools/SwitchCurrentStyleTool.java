package edu.ucsd.idekerlab.cytoscapemcp.tools;

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
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.view.vizmap.VisualStyleFactory;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool that switches the current network view to use an existing visual style or creates a new
 * named style cloned from current view style. State-mutating.
 */
public class SwitchCurrentStyleTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(SwitchCurrentStyleTool.class);

    private static final String TOOL_NAME = "switch_current_style";

    private static final String TOOL_TITLE = "Switch Cytoscape Desktop Style";

    private static final String TOOL_EXAMPLES =
            "\n\n## Examples\n\n"
                    + "Example 1 — Switch the current view to an existing style:\n"
                    + "{\"name\": \"Marquee\"}\n\n"
                    + "Example 2 — Choose a different style for the current network:\n"
                    + "{\"name\": \"Directed\"}\n\n"
                    + "Example 3 — Create a new style cloned from the current style:\n"
                    + "{\"name\": \"My Analysis Style\", \"create\": true}";

    private static final String TOOL_DESCRIPTION =
            "Switches the current network view to use an existing visual style or creates a new"
                    + " named style and applies it. Use when the user wants to change which style is"
                    + " applied to current network view. User can choose style from list of existing "
                    + " styles or they can choose to create a new style which gets automatically created with initial state cloned from style on current "
                    + " network views and in either case the chosen or new style is applied to curent network view."
                    + " Returns an error"
                    + " with a descriptive message if the style name is not found when create=false or "
                    + " a style by the new name already exists when create=true, or no network view is"
                    + " currently active — each error message is a well-formed sentence explaining the"
                    + " issue so the next step can be determined.";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record SwitchStyleResponse(
            @JsonPropertyDescription(
                            "Whether the style switch was successful. When true, the current"
                                    + " network view is now using the style specified by the request."
                                    + " When false, the error_msg field explains what went wrong."
                                    + "\n\nExamples: true, false")
                    @JsonProperty("status")
                    boolean status,
            @JsonPropertyDescription(
                            "Descriptive error message present only when status is false. Explains"
                                    + " the specific reason the operation failed in a well-formed"
                                    + " sentence — such as the style not being found, a style by the"
                                    + " new name already being registered, or no network view being"
                                    + " active. Absent when the operation succeeds."
                                    + "\n\nExamples: \"Style 'NonExistent' was not found among"
                                    + " registered styles.\", \"Cannot create style 'My Style' because"
                                    + " a style with that name already exists.\", \"No network view is"
                                    + " currently active in Cytoscape Desktop.\"")
                    @JsonProperty("error_msg")
                    String errorMsg) {}

    static final String INPUT_SCHEMA =
            McpSchema.toJson(
                    McpSchema.InputSchema.builder()
                            .required("name")
                            .property(
                                    "name",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Required. Name of the visual style to switch to or"
                                                    + " create. If a style with this name already"
                                                    + " exists in Cytoscape Desktop, the current"
                                                    + " network view is switched to use it. If no"
                                                    + " style by this name exists, a new style is"
                                                    + " created only when 'create' is true —"
                                                    + " otherwise an error is returned indicating"
                                                    + " the style was not found."
                                                    + "\n\nExamples: \"Marquee\", \"My Custom Style\","
                                                    + " \"Publication Ready\""))
                            .property(
                                    "create",
                                    new McpSchema.InputProperty(
                                            "boolean",
                                            "Optional. Default false. When true, triggers creation"
                                                    + " of a new style by cloning all default"
                                                    + " property values and mappings from the style"
                                                    + " currently applied to the active network"
                                                    + " view. If 'name' specifies a style that"
                                                    + " already exists in Cytoscape Desktop, an"
                                                    + " error is returned indicating a duplicate"
                                                    + " style cannot be created."
                                                    + "\n\nExamples: true, false"))
                            .build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(SwitchStyleResponse.class);

    private final CyApplicationManager appManager;
    private final VisualMappingManager vmmManager;
    private final VisualStyleFactory visualStyleFactory;

    public SwitchCurrentStyleTool(
            CyApplicationManager appManager,
            VisualMappingManager vmmManager,
            VisualStyleFactory visualStyleFactory) {
        this.appManager = appManager;
        this.vmmManager = vmmManager;
        this.visualStyleFactory = visualStyleFactory;
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

    @SuppressWarnings("unchecked")
    private CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        LOGGER.info("Tool call received: {}", TOOL_NAME);

        try {
            // 1. Check current network view exists.
            CyNetworkView view = appManager.getCurrentNetworkView();
            if (view == null) {
                return result(
                        false,
                        "No network view is currently active in Cytoscape Desktop."
                                + " Load a network and ensure it has a view first.");
            }

            // 2. Read parameters.
            Map<String, Object> args = (Map<String, Object>) request.arguments();
            String name = (String) args.get("name");
            boolean create = Boolean.TRUE.equals(args.get("create"));

            // 3. Search for existing style by name.
            VisualStyle existingStyle = findStyleByName(name);

            // 4. Existing style found + create=true → duplicate error.
            if (existingStyle != null && create) {
                return result(
                        false,
                        "Cannot create style '"
                                + name
                                + "' because a style with that name already exists"
                                + " in Cytoscape Desktop.");
            }

            // 5. Existing style found + create=false → switch to it.
            if (existingStyle != null) {
                applyStyle(existingStyle, view);
                return result(true, null);
            }

            // 6. Name not found + create=false → error.
            if (!create) {
                return result(
                        false,
                        "Style '"
                                + name
                                + "' was not found among registered styles"
                                + " in Cytoscape Desktop. To create a new style, set create"
                                + " to true.");
            }

            // 7. Name not found + create=true → clone current style and apply.
            VisualStyle sourceStyle = vmmManager.getVisualStyle(view);
            VisualStyle newStyle = visualStyleFactory.createVisualStyle(sourceStyle);
            newStyle.setTitle(name);
            vmmManager.addVisualStyle(newStyle);
            applyStyle(newStyle, view);
            return result(true, null);

        } catch (Exception e) {
            LOGGER.error("Error switching visual style", e);
            return error("Failed to switch visual style: " + e.getMessage());
        }
    }

    // -- Helpers --------------------------------------------------------------

    private VisualStyle findStyleByName(String name) {
        for (VisualStyle vs : vmmManager.getAllVisualStyles()) {
            if (vs.getTitle().equals(name)) {
                return vs;
            }
        }
        return null;
    }

    private void applyStyle(VisualStyle style, CyNetworkView view)
            throws InterruptedException, java.lang.reflect.InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> vmmManager.setVisualStyle(style, view));
        vmmManager.setCurrentVisualStyle(style);
    }

    private CallToolResult result(boolean status, String errorMsg) {
        return CallToolResult.builder()
                .structuredContent(new SwitchStyleResponse(status, errorMsg))
                .build();
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}
