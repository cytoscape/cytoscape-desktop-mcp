package edu.ucsd.idekerlab.cytoscapemcp.gateway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cytoscape.command.AvailableCommands;

/**
 * Pre-invocation input validator for Cytoscape commands. Checks required parameter presence,
 * rejects unknown parameter names, and coerces typed values before any invocation attempt. Returns
 * a per-parameter {@link Result.Failure} on the first problem found.
 */
public class CommandInvokeValidator {

    public sealed interface Result permits Result.Ok, Result.Failure {

        record Ok(Map<String, Object> coercedParams) implements Result {}

        record Failure(String paramName, String reason) implements Result {
            public String toErrorMessage(String commandKey) {
                return "Command '"
                        + commandKey
                        + "' rejected: parameter '"
                        + paramName
                        + "' \u2014 "
                        + reason;
            }
        }
    }

    private record CoercionOutcome(boolean ok, Object value, String reason) {
        static CoercionOutcome success(Object value) {
            return new CoercionOutcome(true, value, null);
        }

        static CoercionOutcome failure(String reason) {
            return new CoercionOutcome(false, null, reason);
        }
    }

    private final AvailableCommands availableCommands;

    public CommandInvokeValidator(AvailableCommands availableCommands) {
        this.availableCommands = availableCommands;
    }

    public Result validate(String namespace, String commandName, Map<String, Object> inputParams) {
        List<String> argNames = availableCommands.getArguments(namespace, commandName);
        Set<String> knownArgs = new LinkedHashSet<>(argNames);

        // Step 1: required param presence (in argNames order, fail-fast on first missing)
        for (String arg : argNames) {
            if (availableCommands.getArgRequired(namespace, commandName, arg)
                    && !inputParams.containsKey(arg)) {
                Class<?> argType = availableCommands.getArgType(namespace, commandName, arg);
                String typeName = CommandETLService.mapArgType(argType);
                return new Result.Failure(
                        arg, "required parameter is missing (expected type: " + typeName + ")");
            }
        }

        // Step 2: unknown param names (sorted for determinism, fail-fast on first unknown)
        List<String> sortedInputKeys = new ArrayList<>(inputParams.keySet());
        Collections.sort(sortedInputKeys);
        for (String key : sortedInputKeys) {
            if (!knownArgs.contains(key)) {
                return new Result.Failure(
                        key,
                        "unknown parameter \u2014 valid parameters: "
                                + String.join(", ", argNames));
            }
        }

        // Step 3: type coercion (in argNames order)
        Map<String, Object> coercedParams = new LinkedHashMap<>();
        for (String arg : argNames) {
            if (!inputParams.containsKey(arg)) continue;
            Object value = inputParams.get(arg);
            Class<?> argType = availableCommands.getArgType(namespace, commandName, arg);
            if (argType == null) {
                coercedParams.put(arg, value);
                continue;
            }
            CoercionOutcome outcome = coerce(value, argType);
            if (!outcome.ok()) {
                return new Result.Failure(arg, outcome.reason());
            }
            coercedParams.put(arg, outcome.value());
        }

        return new Result.Ok(Collections.unmodifiableMap(coercedParams));
    }

    private CoercionOutcome coerce(Object value, Class<?> expectedType) {
        if (value == null) return CoercionOutcome.success(null);
        String category = CommandETLService.mapArgType(expectedType);
        return switch (category) {
            case "integer" -> coerceToInteger(value, expectedType);
            case "number" -> coerceToNumber(value, expectedType);
            case "boolean" -> coerceToBoolean(value);
            default -> coerceToString(value, expectedType);
        };
    }

    private CoercionOutcome coerceToString(Object value, Class<?> expectedType) {
        // Only stringify when the param explicitly declares String — complex Cytoscape types
        // (e.g. CyNetwork, List) also fall here via mapArgType() and must pass through as-is.
        if (expectedType == String.class && !(value instanceof String)) {
            return CoercionOutcome.success(value.toString());
        }
        return CoercionOutcome.success(value);
    }

    private CoercionOutcome coerceToInteger(Object value, Class<?> expectedType) {
        boolean expectsLong = expectedType == Long.class || expectedType == long.class;
        if (value instanceof Number n) {
            if (n instanceof Double || n instanceof Float) {
                return CoercionOutcome.failure(
                        "expected integer, got " + n.getClass().getSimpleName());
            }
            long lval = n.longValue();
            if (!expectsLong) {
                if (lval < Integer.MIN_VALUE || lval > Integer.MAX_VALUE)
                    return CoercionOutcome.failure(
                            "expected integer, value " + lval + " is out of int range");
                return CoercionOutcome.success((int) lval);
            }
            return CoercionOutcome.success(lval);
        }
        if (value instanceof String s) {
            try {
                long parsed = Long.parseLong(s.trim());
                if (!expectsLong) {
                    if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE)
                        return CoercionOutcome.failure(
                                "expected integer, value '" + s.trim() + "' is out of int range");
                    return CoercionOutcome.success((int) parsed);
                }
                return CoercionOutcome.success(parsed);
            } catch (NumberFormatException e) {
                return CoercionOutcome.failure("expected integer, cannot parse '" + s.trim() + "'");
            }
        }
        return CoercionOutcome.failure("expected integer, got " + value.getClass().getSimpleName());
    }

    private CoercionOutcome coerceToNumber(Object value, Class<?> expectedType) {
        if (value instanceof Number n) {
            boolean expectsFloat = expectedType == Float.class || expectedType == float.class;
            if (expectsFloat) return CoercionOutcome.success(n.floatValue());
            return CoercionOutcome.success(n.doubleValue());
        }
        if (value instanceof String s) {
            try {
                return CoercionOutcome.success(Double.parseDouble(s.trim()));
            } catch (NumberFormatException e) {
                return CoercionOutcome.failure("expected number, cannot parse '" + s.trim() + "'");
            }
        }
        return CoercionOutcome.failure("expected number, got " + value.getClass().getSimpleName());
    }

    private CoercionOutcome coerceToBoolean(Object value) {
        if (value instanceof Boolean) return CoercionOutcome.success(value);
        if (value instanceof String s) {
            String trimmed = s.trim();
            return trimmed.equalsIgnoreCase("true")
                    ? CoercionOutcome.success(Boolean.TRUE)
                    : trimmed.equalsIgnoreCase("false")
                            ? CoercionOutcome.success(Boolean.FALSE)
                            : CoercionOutcome.failure(
                                    "expected boolean ('true' or 'false'), got '" + trimmed + "'");
        }
        return CoercionOutcome.failure("expected boolean, got " + value.getClass().getSimpleName());
    }
}
