package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * Stateless service that validates {@code ConditionalParameter} wrapper presence and waive-intent
 * for a given {@code source} value. Reusable across any MCP tool that declares conditional inputs.
 *
 * <p>Follows the same stateless, no-arg-constructor service pattern as {@link
 * VisualPropertyService} and {@link GeneratorService}.
 */
public class ValidationService {

    /**
     * Validates that all {@code ConditionalParameter} wrappers required for the given {@code
     * source} are present in {@code args}, and that truly-required parameters have not been
     * submitted with {@code waived=true}.
     *
     * <p>A parameter is considered "present" when the LLM has supplied the wrapper object
     * (regardless of the {@code waived} flag). An absent wrapper means the LLM has not yet
     * confirmed the parameter's intent, which is an error.
     *
     * @param source the chosen source type (e.g. {@code "ndex"}, {@code "network-file"}, {@code
     *     "tabular-file"})
     * @param args the full arguments map from the tool request
     * @return an error {@link CallToolResult} if validation fails; {@code null} when all checks
     *     pass
     */
    public CallToolResult validateConditionalParams(String source, Map<String, Object> args) {
        switch (source) {
            case "ndex":
                return validatePresence(
                        args,
                        "network_id",
                        source,
                        "the NDEx network UUID required to load the network from NDEx",
                        true);
            case "network-file":
                return validatePresence(
                        args,
                        "file_path",
                        source,
                        "the absolute path to the network file to import",
                        true);
            case "tabular-file":
                CallToolResult r;
                r =
                        validatePresence(
                                args,
                                "file_path",
                                source,
                                "the absolute path to the tabular file to import",
                                true);
                if (r != null) return r;
                r =
                        validatePresence(
                                args,
                                "source_column",
                                source,
                                "the file column that provides source node names for each edge row",
                                true);
                if (r != null) return r;
                r =
                        validatePresence(
                                args,
                                "target_column",
                                source,
                                "the file column that provides target node names for each edge row",
                                true);
                if (r != null) return r;
                r =
                        validatePresence(
                                args,
                                "use_header_row",
                                source,
                                "whether the first row of the file is a header row",
                                true);
                if (r != null) return r;
                r =
                        validatePresence(
                                args,
                                "node_attributes_source_columns",
                                source,
                                "which file columns should be attached as attributes on source nodes",
                                false);
                if (r != null) return r;
                r =
                        validatePresence(
                                args,
                                "node_attributes_target_columns",
                                source,
                                "which file columns should be attached as attributes on target nodes",
                                false);
                return r;
            default:
                return null;
        }
    }

    /**
     * Checks that the given parameter key has a {@code ConditionalParameter} wrapper present in
     * {@code args}. If absent, returns a descriptive error. If present but {@code waived=true} on a
     * cannot-waive param, returns a descriptive error. Otherwise returns {@code null} (valid).
     *
     * @param args the full arguments map from the tool request
     * @param paramName the parameter key to check
     * @param source the chosen source type (included in error messages for context)
     * @param paramPurpose human-readable description of what the parameter controls
     * @param cannotWaive {@code true} if this parameter is always required for the source type and
     *     may never be intentionally omitted
     * @return an error {@link CallToolResult}, or {@code null} if the parameter is valid
     */
    public CallToolResult validatePresence(
            Map<String, Object> args,
            String paramName,
            String source,
            String paramPurpose,
            boolean cannotWaive) {
        Object raw = args.get(paramName);
        if (raw == null) {
            return error(
                    "Parameter '"
                            + paramName
                            + "' must be confirmed before invoking with source='"
                            + source
                            + "': it controls "
                            + paramPurpose
                            + ". Provide a value (waived=false, parameter=<value>) or explicitly"
                            + " confirm it should be omitted (waived=true). Refer to the '"
                            + paramName
                            + "' parameter description for complete details on how to use it.");
        }
        if (cannotWaive && raw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> wrapper = (Map<String, Object>) raw;
            Object waivedVal = wrapper.get("waived");
            if (Boolean.TRUE.equals(waivedVal)) {
                return error(
                        "Parameter '"
                                + paramName
                                + "' cannot be intentionally omitted when source='"
                                + source
                                + "': it controls "
                                + paramPurpose
                                + " and is always required for this source type. Provide a value"
                                + " (waived=false, parameter=<value>). Refer to the '"
                                + paramName
                                + "' parameter description for complete details.");
            }
        }
        return null;
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}
