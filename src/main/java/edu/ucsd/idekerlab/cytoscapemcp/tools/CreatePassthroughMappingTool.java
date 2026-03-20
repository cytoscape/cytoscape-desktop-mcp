package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;
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
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyTable;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.model.VisualProperty;
import org.cytoscape.view.presentation.RenderingEngineManager;
import org.cytoscape.view.vizmap.VisualMappingFunctionFactory;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.view.vizmap.mappings.PassthroughMapping;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool that creates a passthrough visual mapping in the active Cytoscape Desktop visual style,
 * using a data column's value directly as the visual property value without transformation.
 * Replaces any existing mapping on the target property.
 */
public class CreatePassthroughMappingTool {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(CreatePassthroughMappingTool.class);

    static final String TOOL_NAME = "create_passthrough_mapping";
    static final String TOOL_TITLE = "Create Cytoscape Desktop Passthrough Mapping";

    private static final String TOOL_DESCRIPTION =
            "Create a passthrough visual mapping in the active Cytoscape Desktop visual style, using"
                    + " a data column's value directly as the visual property value without"
                    + " transformation. Use when a column already holds the exact value needed for a"
                    + " visual property — most commonly mapping a name or identifier column to a"
                    + " label property so each node or edge displays its data value as a visible"
                    + " label. State-mutating; replaces any existing mapping on the target property"
                    + " and immediately rerenders the current view.";

    private static final String TOOL_EXAMPLES =
            "\n\n## Examples\n\n"
                    + "Example 1 — Label nodes with the name column:\n"
                    + "{\"property_id\": \"NODE_LABEL\", \"column_name\": \"name\","
                    + " \"column_type\": \"String\"}\n\n"
                    + "Example 2 — Label edges with the interaction type:\n"
                    + "{\"property_id\": \"EDGE_LABEL\", \"column_name\": \"interaction\","
                    + " \"column_type\": \"String\"}\n\n"
                    + "Example 3 — Show node tooltip from description column:\n"
                    + "{\"property_id\": \"NODE_TOOLTIP\", \"column_name\": \"description\","
                    + " \"column_type\": \"String\"}\n\n"
                    + "Example 4 — \"Show node names\": this is a clear passthrough request. Use"
                    + " other available tools to confirm the column name (e.g. \"name\", \"label\","
                    + " \"id\") that exists in the network data before invoking — do not ask the"
                    + " user to type the column name if it can be discovered.";

    // -- Response record -------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record CreatePassthroughMappingResponse(
            @JsonPropertyDescription(
                            "Outcome. Always 'success' for non-error responses.\n\nExamples:"
                                    + " \"success\"")
                    @JsonProperty("status")
                    String status,
            @JsonPropertyDescription(
                            "Machine-readable ID of the visual property mapped.\n\nExamples:"
                                    + " \"NODE_LABEL\", \"EDGE_LABEL\"")
                    @JsonProperty("property_id")
                    String propertyId,
            @JsonPropertyDescription(
                            "Human-readable display name of the visual property.\n\nExamples:"
                                    + " \"Node Label\", \"Edge Label\"")
                    @JsonProperty("displayName")
                    String displayName,
            @JsonPropertyDescription("Always 'PassthroughMapping' for this tool.")
                    @JsonProperty("mapping_type")
                    String mappingType,
            @JsonPropertyDescription(
                            "Name of the data column whose values drive the mapping.\n\nExamples:"
                                    + " \"name\", \"interaction\"")
                    @JsonProperty("column")
                    String column) {}

    // -- Schemas ---------------------------------------------------------------

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String INPUT_SCHEMA =
            McpSchema.toJson(
                    McpSchema.InputSchema.builder()
                            .required("property_id", "column_name", "column_type")
                            .property(
                                    "property_id",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Required. Visual property ID (e.g. NODE_LABEL,"
                                                    + " EDGE_LABEL, NODE_TOOLTIP)."))
                            .property(
                                    "column_name",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Required. Name of the data column whose values will"
                                                    + " be used directly as the visual property"
                                                    + " value. The column must already exist in"
                                                    + " the node or edge table of the current"
                                                    + " network — use other available tools to"
                                                    + " confirm available columns before"
                                                    + " invoking."))
                            .property(
                                    "column_type",
                                    new McpSchema.InputProperty(
                                            "string",
                                            "Required. Java type of the data column. All five"
                                                    + " types are valid; the column value will be"
                                                    + " rendered as-is by Cytoscape.",
                                            List.of(
                                                    "Integer", "Long", "Double", "String",
                                                    "Boolean")))
                            .build());

    static final String OUTPUT_SCHEMA =
            McpSchema.toSchemaJson(CreatePassthroughMappingResponse.class);

    // -- Dependencies ----------------------------------------------------------

    private final CyApplicationManager appManager;
    private final VisualMappingManager vmmManager;
    private final RenderingEngineManager renderingEngineManager;
    private final VisualMappingFunctionFactory passthroughMappingFactory;
    private final VisualPropertyService vpService;

    public CreatePassthroughMappingTool(
            CyApplicationManager appManager,
            VisualMappingManager vmmManager,
            RenderingEngineManager renderingEngineManager,
            VisualMappingFunctionFactory passthroughMappingFactory,
            VisualPropertyService vpService) {
        this.appManager = appManager;
        this.vmmManager = vmmManager;
        this.renderingEngineManager = renderingEngineManager;
        this.passthroughMappingFactory = passthroughMappingFactory;
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

    // -- Handler ---------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        LOGGER.info("Tool call received: {}", TOOL_NAME);

        try {
            // GUARD: network and view
            if (appManager.getCurrentNetwork() == null) {
                return error(
                        "No network loaded in Cytoscape Desktop. Load a network first before"
                                + " creating a visual mapping.");
            }
            if (appManager.getCurrentNetworkView() == null) {
                return error(
                        "No network view is currently active. Create a view first before"
                                + " creating a visual mapping.");
            }

            VisualStyle style = vmmManager.getCurrentVisualStyle();
            VisualLexicon lexicon = renderingEngineManager.getDefaultVisualLexicon();

            Map<String, Object> args = request.arguments();
            if (args == null) args = Collections.emptyMap();

            String propertyId = String.valueOf(args.get("property_id"));
            String columnName = String.valueOf(args.get("column_name"));
            String columnTypeStr = String.valueOf(args.get("column_type"));

            // VALIDATION: property
            VisualProperty<?> vp = vpService.findPropertyById(lexicon, propertyId);
            if (vp == null) {
                return error(
                        "Unknown visual property ID '"
                                + propertyId
                                + "'. Use other available tools to retrieve valid property IDs.");
            }
            if (!vpService.isSupported(vp)) {
                return error("Property '" + propertyId + "' is not supported for MCP mapping.");
            }

            // VALIDATION: column type (all 5 types valid for passthrough)
            Class<?> columnTypeClass;
            try {
                columnTypeClass = vpService.resolveColumnType(columnTypeStr);
            } catch (IllegalArgumentException e) {
                return error(e.getMessage());
            }

            // VALIDATION: column exists in the appropriate table
            String tableName = vpService.getTableName(lexicon, vp);
            if (tableName != null) {
                CyNetwork network = appManager.getCurrentNetwork();
                CyTable table =
                        "node".equals(tableName)
                                ? network.getDefaultNodeTable()
                                : network.getDefaultEdgeTable();
                if (table.getColumn(columnName) == null) {
                    return error(
                            "Column '"
                                    + columnName
                                    + "' does not exist in the "
                                    + tableName
                                    + " table of the current network."
                                    + " Use other available tools to retrieve available"
                                    + " columns before creating a mapping.");
                }
            }

            // MUTATION: apply on Swing EDT
            final VisualProperty<?> finalVp = vp;
            final VisualStyle finalStyle = style;
            final Class<?> finalColType = columnTypeClass;
            final String finalColumnName = columnName;

            try {
                Runnable task =
                        () -> {
                            PassthroughMapping pm =
                                    (PassthroughMapping)
                                            passthroughMappingFactory.createVisualMappingFunction(
                                                    finalColumnName, finalColType, finalVp);
                            finalStyle.removeVisualMappingFunction(finalVp);
                            finalStyle.addVisualMappingFunction(pm);
                            CyNetworkView view = appManager.getCurrentNetworkView();
                            if (view != null) {
                                finalStyle.apply(view);
                                view.updateView();
                            }
                        };

                if (EventQueue.isDispatchThread()) {
                    task.run();
                } else {
                    SwingUtilities.invokeAndWait(task);
                }
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                LOGGER.error("Error applying passthrough mapping on EDT", cause);
                return error(
                        "Failed to apply mapping: "
                                + (cause.getMessage() != null
                                        ? cause.getMessage()
                                        : cause.getClass().getSimpleName()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return error("Interrupted while applying passthrough mapping.");
            }

            return CallToolResult.builder()
                    .structuredContent(
                            new CreatePassthroughMappingResponse(
                                    "success",
                                    vp.getIdString(),
                                    vp.getDisplayName(),
                                    "PassthroughMapping",
                                    columnName))
                    .build();

        } catch (Exception e) {
            LOGGER.error("Error creating passthrough mapping", e);
            return error("Failed to create passthrough mapping: " + e.getMessage());
        }
    }

    // -- Helpers ---------------------------------------------------------------

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}
