package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.awt.Color;
import java.awt.Font;
import java.awt.Paint;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.cytoscape.view.model.ContinuousRange;
import org.cytoscape.view.model.DiscreteRange;
import org.cytoscape.view.model.Range;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.model.VisualProperty;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.view.presentation.property.values.ArrowShape;
import org.cytoscape.view.presentation.property.values.EdgeStacking;
import org.cytoscape.view.presentation.property.values.LabelBackgroundShape;
import org.cytoscape.view.presentation.property.values.LineType;
import org.cytoscape.view.presentation.property.values.NodeShape;
import org.cytoscape.view.presentation.property.values.VisualPropertyValue;

/** Service for visual property type conversion and discovery. */
public class VisualPropertyService {

    /** The four font style tokens accepted by {@code Font.decode} — a Java SE invariant. */
    public static final List<String> FONT_STYLES = List.of("Plain", "Bold", "Italic", "BoldItalic");

    /**
     * Intentionally limited to value types that have a plain-text representation suitable for MCP
     * tool input and output. This is an MCP-layer constraint, not a Cytoscape limitation.
     *
     * <p>Excluded types and why:
     *
     * <ul>
     *   <li>{@code CyCustomGraphics} — requires a graphic resource reference (URL/bundle); not
     *       representable as a simple string value.
     *   <li>{@code ObjectPosition} — complex multi-field serialized format (anchor, justification,
     *       X/Y offset) with no single-string representation.
     *   <li>{@code EdgeBend} — variable-length list of 2D control points; cannot be expressed as a
     *       simple scalar.
     *   <li>{@code Visualizable} — container node type used as a parent VP group; not directly
     *       settable.
     * </ul>
     */
    private final Set<Class<?>> MCP_ONLY_SUPPORTED_TYPES =
            Set.of(
                    Paint.class,
                    Color.class,
                    Double.class,
                    Integer.class,
                    NodeShape.class,
                    ArrowShape.class,
                    LineType.class,
                    LabelBackgroundShape.class,
                    EdgeStacking.class,
                    Font.class,
                    String.class,
                    Boolean.class);

    /** Finds a VisualProperty by its ID string in the given lexicon. Returns null if not found. */
    public VisualProperty<?> findPropertyById(VisualLexicon lexicon, String idString) {
        for (VisualProperty<?> vp : lexicon.getAllVisualProperties()) {
            if (vp.getIdString().equals(idString)) return vp;
        }
        return null;
    }

    /** Returns true if the visual property's value type is one MCP can support for styling. */
    public boolean isSupported(VisualProperty<?> vp) {
        Range<?> range = vp.getRange();
        if (range == null) return false;
        Class<?> type = range.getType();
        return MCP_ONLY_SUPPORTED_TYPES.stream().anyMatch(t -> t.isAssignableFrom(type));
    }

    /** Returns a human-readable type name for the given range (e.g. "Paint", "Double"). */
    public String getTypeName(Range<?> range) {
        if (range == null) return "Unknown";
        Class<?> type = range.getType();
        // Color VPs have range type Color.class (a Paint subtype); normalize all paint subtypes to
        // "Paint".
        if (Paint.class.isAssignableFrom(type)) return "Paint";
        return type.getSimpleName();
    }

    /**
     * Determines the continuous mapping sub-type for a VP.
     *
     * @return "color-gradient", "continuous", "discrete", or null if no continuous mapping is
     *     possible.
     */
    public String getContinuousSubType(VisualProperty<?> vp) {
        Range<?> range = vp.getRange();
        if (range == null) return null;
        Class<?> type = range.getType();

        if (range.isDiscrete()) {
            // DiscreteRange VPs (shapes, arrows, lines, fonts) use threshold-based mapping.
            return "discrete";
        }
        // Non-discrete (continuous) range.
        if (Paint.class.isAssignableFrom(type) || Color.class.isAssignableFrom(type)) {
            return "color-gradient";
        } else if (Number.class.isAssignableFrom(type)) {
            return "continuous";
        }
        return null;
    }

    /**
     * Formats a visual property value for display. Colors become hex strings, fonts become
     * "Family-Style-Size", enum types use their display name, everything else uses toString().
     */
    public String formatValue(Object value) {
        if (value instanceof Paint) {
            Color c = (Color) value;
            return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
        } else if (value instanceof Font) {
            Font f = (Font) value;
            String style =
                    f.isPlain()
                            ? "Plain"
                            : (f.isBold() && f.isItalic())
                                    ? "BoldItalic"
                                    : f.isBold() ? "Bold" : "Italic";
            return f.getFamily() + "-" + style + "-" + f.getSize();
        } else if (value instanceof VisualPropertyValue) {
            // NodeShape, ArrowShape, LineType — toString() returns the display name
            // (e.g. "Ellipse", "Arrow", "Solid").
            return ((VisualPropertyValue) value).getDisplayName();
        } else {
            return String.valueOf(value);
        }
    }

    /**
     * Returns an alphabetically sorted list of display-name strings for discrete-typed visual
     * properties (NodeShape, ArrowShape, LineType). Returns null for non-discrete properties and
     * for Font-typed properties (whose allowed families are in the top-level font_families field)
     * so the key is absent from JSON with @JsonInclude(NON_NULL).
     */
    public List<String> getAllowedValues(VisualProperty<?> vp) {
        Range<?> range = vp.getRange();
        if (range == null || !range.isDiscrete()) return null;
        // Font properties: allowed families are in the top-level font_families field
        if (range.getType() == Font.class) return null;
        DiscreteRange<?> discreteRange = (DiscreteRange<?>) range;
        return discreteRange.values().stream()
                .map(
                        v ->
                                v instanceof VisualPropertyValue
                                        ? ((VisualPropertyValue) v).getDisplayName()
                                        : String.valueOf(v))
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Extracts deduplicated, alphabetically sorted font family names from the lexicon's Font
     * discrete range. Uses NODE_LABEL_FONT_FACE as the canonical Font VP.
     */
    public List<String> getFontFamilies(VisualLexicon lexicon) {
        VisualProperty<?> fontVP = findPropertyById(lexicon, "NODE_LABEL_FONT_FACE");
        if (fontVP == null) return Collections.emptyList();
        Range<?> range = fontVP.getRange();
        if (range == null || !range.isDiscrete()) return Collections.emptyList();
        DiscreteRange<?> discreteRange = (DiscreteRange<?>) range;
        return discreteRange.values().stream()
                .filter(v -> v instanceof Font)
                .map(v -> ((Font) v).getFamily())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Returns the minimum value for a continuous numeric range, or null if the property is discrete
     * or non-numeric. Used to inform LLMs of valid value bounds.
     */
    public String getRangeMin(VisualProperty<?> vp) {
        Range<?> range = vp.getRange();
        if (range == null || range.isDiscrete() || !(range instanceof ContinuousRange)) return null;
        ContinuousRange<?> cr = (ContinuousRange<?>) range;
        if (!Number.class.isAssignableFrom(cr.getType())) return null;
        Object min = cr.getMin();
        return min != null ? String.valueOf(min) : null;
    }

    /**
     * Returns the maximum value for a continuous numeric range, or null if the property is discrete
     * or non-numeric. Used to inform LLMs of valid value bounds.
     */
    public String getRangeMax(VisualProperty<?> vp) {
        Range<?> range = vp.getRange();
        if (range == null || range.isDiscrete() || !(range instanceof ContinuousRange)) return null;
        ContinuousRange<?> cr = (ContinuousRange<?>) range;
        if (!Number.class.isAssignableFrom(cr.getType())) return null;
        Object max = cr.getMax();
        return max != null ? String.valueOf(max) : null;
    }

    /**
     * Parses a raw value (from MCP tool input) into the correct type for the given VP. Accepts hex
     * color strings, numeric values, shape/arrow/line display names, font strings, etc.
     *
     * @throws Exception if the value cannot be parsed (checked — callers must handle or declare).
     */
    public Object parseValue(VisualProperty<?> vp, Object rawValue) throws Exception {
        Range<?> range = vp.getRange();
        Class<?> type = range.getType();

        if (Paint.class.isAssignableFrom(type)) {
            return parseColor(String.valueOf(rawValue));
        } else if (type == Double.class) {
            if (rawValue instanceof Number) return ((Number) rawValue).doubleValue();
            return Double.parseDouble(String.valueOf(rawValue));
        } else if (type == Integer.class) {
            if (rawValue instanceof Number) return ((Number) rawValue).intValue();
            return Integer.parseInt(String.valueOf(rawValue));
        } else if (type == Font.class) {
            return Font.decode(String.valueOf(rawValue));
        } else if (type == String.class) {
            return String.valueOf(rawValue);
        } else if (type == Boolean.class) {
            return Boolean.valueOf(String.valueOf(rawValue));
        } else if (range instanceof DiscreteRange) {
            // NodeShape, ArrowShape, LineType — match by name.
            return parseDiscreteValue(vp, (DiscreteRange<?>) range, String.valueOf(rawValue));
        }
        throw new IllegalArgumentException("Unsupported property type: " + type.getSimpleName());
    }

    /**
     * Parses a node shape from a display name or serializable string (case-insensitive).
     *
     * @throws Exception if the name doesn't match any known shape (checked — callers must handle or
     *     declare).
     */
    public NodeShape parseNodeShape(String name) throws Exception {
        return parseNamedValue(BasicVisualLexicon.NODE_SHAPE, NodeShape.class, name, "node shape");
    }

    /**
     * Parses an arrow shape from a display name or serializable string (case-insensitive).
     *
     * @throws Exception if the name doesn't match any known arrow shape (checked — callers must
     *     handle or declare).
     */
    public ArrowShape parseArrowShape(String name) throws Exception {
        return parseNamedValue(
                BasicVisualLexicon.EDGE_TARGET_ARROW_SHAPE, ArrowShape.class, name, "arrow shape");
    }

    /**
     * Parses a line type from a display name or serializable string (case-insensitive).
     *
     * @throws Exception if the name doesn't match any known line type (checked — callers must
     *     handle or declare).
     */
    public LineType parseLineType(String name) throws Exception {
        return parseNamedValue(
                BasicVisualLexicon.EDGE_LINE_TYPE, LineType.class, name, "line type");
    }

    // ---- Private helpers ----

    private Color parseColor(String colorStr) throws Exception {
        if (!colorStr.startsWith("#")) colorStr = "#" + colorStr;
        try {
            return Color.decode(colorStr);
        } catch (NumberFormatException e) {
            throw new Exception(
                    "Invalid color: '" + colorStr + "'. Please use hex format like #FF0000.");
        }
    }

    /**
     * Parses a discrete-range value by trying: (1) VP.parseSerializableString, (2) case-insensitive
     * match on displayName, (3) case-insensitive match on serializableString.
     */
    private <T> Object parseDiscreteValue(VisualProperty<?> vp, DiscreteRange<T> range, String name)
            throws Exception {
        // Try the VP's built-in parser first (handles serializable strings + legacy aliases).
        // Note: some VPs (e.g. LineTypeVisualProperty) never return null — they fall back to a
        // default. So we verify the result matches the input before accepting it.
        @SuppressWarnings("unchecked")
        VisualProperty<T> typedVP = (VisualProperty<T>) vp;
        T parsed = typedVP.parseSerializableString(name);
        if (parsed != null && matchesName(parsed, name)) return parsed;

        // Fall back to case-insensitive match on display name.
        for (T value : range.values()) {
            if (value instanceof VisualPropertyValue) {
                VisualPropertyValue vpv = (VisualPropertyValue) value;
                if (vpv.getDisplayName().equalsIgnoreCase(name)
                        || vpv.getSerializableString().equalsIgnoreCase(name)) {
                    return value;
                }
            } else if (String.valueOf(value).equalsIgnoreCase(name)) {
                return value;
            }
        }

        String validNames =
                range.values().stream()
                        .map(
                                v ->
                                        v instanceof VisualPropertyValue
                                                ? ((VisualPropertyValue) v).getDisplayName()
                                                : String.valueOf(v))
                        .sorted()
                        .collect(Collectors.joining(", "));
        throw new Exception("Invalid value: '" + name + "'. Valid values: " + validNames);
    }

    /**
     * Generic standalone parser for discrete VP value types (NodeShape, ArrowShape, LineType). Uses
     * the given BasicVisualLexicon VP to access the DiscreteRange and parse methods.
     */
    @SuppressWarnings("unchecked")
    private <T> T parseNamedValue(VisualProperty<T> vp, Class<T> type, String name, String typeName)
            throws Exception {
        // Try the VP's built-in parser. Verify result matches input because some VPs
        // (e.g. LineTypeVisualProperty) fall back to a default instead of returning null.
        T parsed = vp.parseSerializableString(name);
        if (parsed != null && matchesName(parsed, name)) return parsed;

        // Fall back to case-insensitive match on display name.
        Range<T> range = vp.getRange();
        if (range instanceof DiscreteRange) {
            for (T value : ((DiscreteRange<T>) range).values()) {
                if (value instanceof VisualPropertyValue) {
                    VisualPropertyValue vpv = (VisualPropertyValue) value;
                    if (vpv.getDisplayName().equalsIgnoreCase(name)
                            || vpv.getSerializableString().equalsIgnoreCase(name)) {
                        return value;
                    }
                }
            }
        }

        // Build error with valid names.
        String validNames = "";
        if (range instanceof DiscreteRange) {
            validNames =
                    ((DiscreteRange<T>) range)
                            .values().stream()
                                    .map(
                                            v ->
                                                    v instanceof VisualPropertyValue
                                                            ? ((VisualPropertyValue) v)
                                                                    .getDisplayName()
                                                            : String.valueOf(v))
                                    .sorted()
                                    .collect(Collectors.joining(", "));
        }
        throw new Exception(
                "Invalid " + typeName + ": '" + name + "'. Valid values: " + validNames);
    }

    /**
     * Verifies that a parsed value actually corresponds to the given name. This guards against VPs
     * whose parseSerializableString() returns a default value instead of null for unknown inputs.
     */
    private <T> boolean matchesName(T value, String name) {
        if (value instanceof VisualPropertyValue) {
            VisualPropertyValue vpv = (VisualPropertyValue) value;
            return vpv.getDisplayName().equalsIgnoreCase(name)
                    || vpv.getSerializableString().equalsIgnoreCase(name);
        }
        return String.valueOf(value).equalsIgnoreCase(name);
    }
}
