package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stateless helper that infers {@link DataColumn.CyDataType} from sample string values and coerces
 * raw CSV/Excel string values to the Java type required by a {@code CyTable} column.
 *
 * <p>Instantiated once in {@code McpServerFactory} and injected into both {@link
 * GetFileColumnsTool} and {@link LoadNetworkViewTool}.
 */
public class TabularTypeConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TabularTypeConverter.class);

    /**
     * Infers the most specific {@link DataColumn.CyDataType} from a list of raw sample strings.
     *
     * <p>Priority: boolean &rarr; integer &rarr; long &rarr; double &rarr; string. Blank/empty
     * samples are skipped; if all samples are blank, returns STRING.
     */
    public DataColumn.CyDataType inferType(List<String> samples) {
        List<String> nonBlank = samples.stream().filter(s -> s != null && !s.isBlank()).toList();

        if (nonBlank.isEmpty()) return DataColumn.CyDataType.STRING;

        // Check boolean
        if (nonBlank.stream()
                .allMatch(s -> s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false"))) {
            return DataColumn.CyDataType.BOOLEAN;
        }

        // Check integer/long (try to parse all as Long first)
        boolean allLong = true;
        boolean allInteger = true;
        for (String s : nonBlank) {
            try {
                long l = Long.parseLong(s.trim());
                if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
                    allInteger = false;
                }
            } catch (NumberFormatException e) {
                allLong = false;
                allInteger = false;
                break;
            }
        }
        if (allInteger) return DataColumn.CyDataType.INTEGER;
        if (allLong) return DataColumn.CyDataType.LONG;

        // Check double (finite only)
        boolean allDouble = true;
        for (String s : nonBlank) {
            try {
                double d = Double.parseDouble(s.trim());
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    allDouble = false;
                    break;
                }
            } catch (NumberFormatException e) {
                allDouble = false;
                break;
            }
        }
        if (allDouble) return DataColumn.CyDataType.DOUBLE;

        return DataColumn.CyDataType.STRING;
    }

    /**
     * Resolves the Java list-element class from a list-type code (e.g. {@code "sl"} → {@code
     * String.class}, {@code "il"} → {@code Integer.class}). Returns {@code String.class} for
     * unknown codes.
     */
    public Class<?> resolveListElementClass(String code) {
        if (code == null) return String.class;
        return switch (code.toLowerCase()) {
            case "il" -> Integer.class;
            case "ll" -> Long.class;
            case "dl" -> Double.class;
            case "bl" -> Boolean.class;
            default -> String.class;
        };
    }

    /**
     * Splits {@code raw} on {@code listDelimiter} and coerces each element to {@code elementClass}
     * via {@link #coerceToColumnType}. Elements that fail coercion are silently skipped. Returns an
     * empty list when {@code raw} is null or blank.
     */
    public <T> List<T> coerceToListValue(String raw, String listDelimiter, Class<T> elementClass) {
        if (raw == null || raw.isBlank()) return List.of();
        String[] parts = raw.split(Pattern.quote(listDelimiter), -1);
        List<T> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            T val = coerceToColumnType(part.trim(), elementClass);
            if (val != null) result.add(val);
        }
        return result;
    }

    /**
     * Coerces a raw String value from a tabular file to the Java type required by a CyTable column.
     *
     * <p>Returns {@code null} (rather than throwing) if parsing fails, so a bad cell does not abort
     * the entire network load.
     *
     * @param raw raw cell value (may be null or blank)
     * @param targetType the Java class expected by the CyTable column
     * @param <T> the target type
     * @return coerced value, or {@code null} if {@code raw} is blank or cannot be parsed
     */
    @SuppressWarnings("unchecked")
    public <T> T coerceToColumnType(String raw, Class<T> targetType) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        try {
            if (targetType == Integer.class) {
                return (T) Integer.valueOf(Integer.parseInt(trimmed));
            } else if (targetType == Long.class) {
                return (T) Long.valueOf(Long.parseLong(trimmed));
            } else if (targetType == Double.class) {
                double d = Double.parseDouble(trimmed);
                if (Double.isNaN(d) || Double.isInfinite(d)) return null;
                return (T) Double.valueOf(d);
            } else if (targetType == Boolean.class) {
                return (T) Boolean.valueOf(Boolean.parseBoolean(trimmed));
            } else {
                return (T) raw;
            }
        } catch (NumberFormatException e) {
            LOGGER.debug(
                    "Could not coerce '{}' to {}: {}",
                    new Object[] {raw, targetType.getSimpleName(), e.getMessage()});
            return null;
        }
    }
}
