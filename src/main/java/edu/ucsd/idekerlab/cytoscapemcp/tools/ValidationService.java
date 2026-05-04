package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.idekerlab.cytoscapemcp.McpSchema;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * Stateless service that validates {@code ConditionalParameter} wrapper presence and waive-intent,
 * and unwraps tool input values. Reusable across any MCP tool that declares conditional inputs.
 *
 * <p>Follows the same stateless, no-arg-constructor service pattern as {@link
 * VisualPropertyService} and {@link GeneratorService}.
 */
public class ValidationService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Declares one conditional parameter for dynamic validation.
     *
     * @param name parameter key in the args map
     * @param purpose human-readable description of what this parameter controls (used in error
     *     messages)
     * @param waiveable {@code true} if the user may intentionally omit this parameter; {@code
     *     false} if it is always required
     */
    public record ConditionalParam(String name, String purpose, boolean waiveable) {}

    // -- Arg conversion -------------------------------------------------------

    /**
     * Converts raw tool arguments into typed {@link McpSchema.ToolInputParam} entries.
     *
     * <p>Detection rule: if a value is a {@link Map} that contains both {@code "waived"} and {@code
     * "parameter"} keys, it is classified as a {@link McpSchema.ConditionalParameter}; otherwise it
     * is classified as a required parameter.
     *
     * @param args the full tool invocation arguments map
     * @return map of parameter name to typed {@link McpSchema.ToolInputParam}
     */
    Map<String, McpSchema.ToolInputParam> convertToolArgs(Map<String, Object> args) {
        Map<String, McpSchema.ToolInputParam> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) val;
                if (map.containsKey("waived") && map.containsKey("parameter")) {
                    boolean waived = Boolean.TRUE.equals(map.get("waived"));
                    Object innerVal = map.get("parameter");
                    result.put(
                            entry.getKey(),
                            new McpSchema.ToolInputParam(
                                    new McpSchema.ConditionalParameter(waived, innerVal), null));
                    continue;
                }
            }
            result.put(entry.getKey(), new McpSchema.ToolInputParam(null, val));
        }
        return result;
    }

    // -- Validation -----------------------------------------------------------

    /**
     * Validates that all {@link ConditionalParam} entries in {@code conditionals} have their {@code
     * ConditionalParameter} wrapper present in {@code args}, and that non-waiveable parameters have
     * not been submitted with {@code waived=true}.
     *
     * <p>A parameter is considered "present" when the LLM has supplied the wrapper object
     * (regardless of the {@code waived} flag). An absent wrapper means the LLM has not yet
     * confirmed the parameter's intent, which is an error.
     *
     * @param target the context identifier included in error messages (e.g. the value of the source
     *     parameter such as {@code "tabular-file"}, or any other caller-defined label)
     * @param args the full arguments map from the tool request
     * @param conditionals the list of conditional parameters to validate for this target
     * @return an error {@link CallToolResult} if validation fails; {@code null} when all checks
     *     pass
     */
    public CallToolResult validateConditionalParams(
            String dependentParamName,
            String dependentParamValue,
            Map<String, Object> args,
            List<ConditionalParam> conditionals) {
        for (ConditionalParam cp : conditionals) {
            CallToolResult r =
                    validatePresence(
                            args,
                            cp.name(),
                            dependentParamName,
                            dependentParamValue,
                            cp.purpose(),
                            !cp.waiveable());
            if (r != null) return r;
        }
        return null;
    }

    /**
     * Checks that the given parameter key has a {@code ConditionalParameter} wrapper present in
     * {@code args}. If absent, returns a descriptive error. If present but {@code waived=true} on a
     * cannot-waive param, returns a descriptive error. Otherwise returns {@code null} (valid).
     *
     * @param args the full arguments map from the tool request
     * @param paramName the parameter key to check
     * @param dependentParamName the name of the parameter whose value drives this conditional (used
     *     in error messages, e.g. {@code "source"})
     * @param dependentParamValue the value of that parameter (e.g. {@code "ndex"})
     * @param paramPurpose human-readable description of what the parameter controls
     * @param cannotWaive {@code true} if this parameter is always required and may never be
     *     intentionally omitted
     * @return an error {@link CallToolResult}, or {@code null} if the parameter is valid
     */
    public CallToolResult validatePresence(
            Map<String, Object> args,
            String paramName,
            String dependentParamName,
            String dependentParamValue,
            String paramPurpose,
            boolean cannotWaive) {
        Object raw = args.get(paramName);
        if (raw == null) {
            if (!cannotWaive) {
                return null; // waiveable params may be absent; treat as implicitly omitted
            }
            return error(
                    "Parameter '"
                            + paramName
                            + "' must be confirmed when "
                            + dependentParamName
                            + "="
                            + dependentParamValue
                            + ": it controls "
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
                                + "' cannot be intentionally omitted when "
                                + dependentParamName
                                + "="
                                + dependentParamValue
                                + ": it controls "
                                + paramPurpose
                                + " and is always required. Provide a value"
                                + " (waived=false, parameter=<value>). Refer to the '"
                                + paramName
                                + "' parameter description for complete details.");
            }
        }
        return null;
    }

    // -- Value unwrapping -----------------------------------------------------

    /**
     * Extracts and converts a tool input value to the expected type.
     *
     * <p>If the raw value is a {@code ConditionalParameter} wrapper (a {@link Map} with both {@code
     * "waived"} and {@code "parameter"} keys): returns {@code null} when {@code waived=true};
     * otherwise extracts the {@code "parameter"} value. If not a conditional wrapper, the value
     * itself is used directly (required parameter).
     *
     * <p>Type conversion rules:
     *
     * <ul>
     *   <li>{@code String.class}: trim, return {@code null} if empty
     *   <li>{@code Boolean.class}: direct cast or {@code null}
     *   <li>{@code Integer.class}: {@link Number#intValue()} or {@code
     *       Integer.valueOf(value.toString())}
     *   <li>Other types: Jackson {@link ObjectMapper#convertValue} fallback
     * </ul>
     *
     * @param rawArg the raw value from the args map (may be a conditional wrapper Map or a direct
     *     value)
     * @param expectedType the expected return type class
     * @return the unwrapped and converted value, or {@code null} if absent/waived/empty
     */
    @SuppressWarnings("unchecked")
    public <T> T unwrapToolInputValue(Object rawArg, Class<T> expectedType) {
        if (rawArg == null) return null;

        Object value;
        if (rawArg instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) rawArg;
            if (map.containsKey("waived") && map.containsKey("parameter")) {
                if (Boolean.TRUE.equals(map.get("waived"))) return null;
                value = map.get("parameter");
            } else {
                value = rawArg;
            }
        } else {
            value = rawArg;
        }

        if (value == null) return null;

        if (expectedType == String.class) {
            if (!(value instanceof String)) return null;
            String s = ((String) value).trim();
            return s.isEmpty() ? null : (T) s;
        }

        if (expectedType == Boolean.class) {
            return value instanceof Boolean ? (T) value : null;
        }

        if (expectedType == Integer.class) {
            if (value instanceof Number) return (T) Integer.valueOf(((Number) value).intValue());
            try {
                return (T) Integer.valueOf(value.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        try {
            return MAPPER.convertValue(value, expectedType);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Extracts a {@code List<DataColumn>} from a tool input value using the same
     * conditional-wrapper detection as {@link #unwrapToolInputValue}.
     *
     * <p>Returns an empty list when the value is {@code null}, the wrapper is absent, or {@code
     * waived=true}. Uses Jackson for deserialization to handle the generic type correctly (type
     * erasure prevents expressing {@code List<DataColumn>} via {@code Class<T>}).
     *
     * @param rawArg the raw value from the args map
     * @return the unwrapped {@link DataColumn} list, or an empty list if absent/waived/null
     */
    @SuppressWarnings("unchecked")
    public List<DataColumn> unwrapToolInputDataColumns(Object rawArg) {
        if (rawArg == null) return List.of();

        Object value;
        if (rawArg instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) rawArg;
            if (map.containsKey("waived") && map.containsKey("parameter")) {
                if (Boolean.TRUE.equals(map.get("waived"))) return List.of();
                value = map.get("parameter");
            } else {
                value = rawArg;
            }
        } else {
            value = rawArg;
        }

        if (value == null) return List.of();
        return MAPPER.convertValue(value, new TypeReference<List<DataColumn>>() {});
    }

    // -- Delimiter detection --------------------------------------------------

    /**
     * Detects the most likely column delimiter in a plain-text tabular file by finding the
     * candidate (tab=9, comma=44, pipe=124, semicolon=59) whose occurrence count is positive and
     * identical across the first five sampled non-blank, non-comment lines.
     *
     * @param file the file to sample
     * @param commentChar lines starting with this character are skipped; {@code null} to disable
     * @return the detected ASCII delimiter code, or {@code -1} if detection is ambiguous (no
     *     candidate has a consistent count, two candidates tie, or fewer than 2 usable lines)
     */
    public int detectDelimiter(File file, String commentChar) throws IOException {
        int[] candidates = {9, 44, 124, 59};
        List<String> lines = new ArrayList<>();
        try (BufferedReader r =
                new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = r.readLine()) != null && lines.size() < 5) {
                if (firstLine) {
                    if (line.startsWith("﻿")) line = line.substring(1);
                    firstLine = false;
                }
                if (line.isBlank()) continue;
                if (commentChar != null && line.startsWith(commentChar)) continue;
                lines.add(line);
            }
        }
        if (lines.size() < 2) return -1;

        int winner = -1;
        for (int delim : candidates) {
            char c = (char) delim;
            int[] counts = new int[lines.size()];
            for (int i = 0; i < lines.size(); i++) {
                for (char ch : lines.get(i).toCharArray()) {
                    if (ch == c) counts[i]++;
                }
            }
            if (counts[0] == 0) continue;
            boolean consistent = true;
            for (int i = 1; i < counts.length; i++) {
                if (counts[i] != counts[0]) {
                    consistent = false;
                    break;
                }
            }
            if (consistent) {
                if (winner != -1) return -1; // two candidates match — ambiguous
                winner = delim;
            }
        }
        return winner;
    }

    // -- Internal helpers -----------------------------------------------------

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}
