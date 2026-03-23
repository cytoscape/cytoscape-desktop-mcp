package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cytoscape.util.color.BrewerType;
import org.cytoscape.util.color.Palette;
import org.cytoscape.util.color.PaletteProvider;
import org.cytoscape.util.color.PaletteProviderManager;
import org.cytoscape.view.model.DiscreteRange;
import org.cytoscape.view.model.VisualProperty;

/**
 * Encapsulates all discrete-mapping auto-generation algorithms, following the same service pattern
 * as {@link VisualPropertyService}.
 *
 * <p>Color generators (rainbow, random, brewer_sequential) delegate to Cytoscape's {@link
 * PaletteProviderManager} — the same underlying SDK service that the built-in VizMapper
 * context-menu generators ({@code PaletteMappingWrapper}) use internally. Shape-cycle and
 * numeric-range generators are pure Java because no headless SDK equivalent exists (the built-in
 * numeric generators open {@code JOptionPane} dialogs).
 */
public class GeneratorService {

    private final PaletteProviderManager paletteProviderManager;

    public GeneratorService(PaletteProviderManager paletteProviderManager) {
        this.paletteProviderManager = paletteProviderManager;
    }

    // -- Color generators ------------------------------------------------------

    /**
     * Generates a rainbow color mapping using Cytoscape's Rainbow palette provider.
     *
     * @param <T> attribute value type
     * @param attributeSet ordered set of distinct column values
     * @return map from attribute value to {@link Color}
     */
    public <T> Map<T, Color> generateRainbow(Set<T> attributeSet) {
        int n = attributeSet.size();
        PaletteProvider provider = paletteProviderManager.getPaletteProvider("Rainbow");
        Color[] colors = provider.getPalette("rainbow", n).getColors(n);
        return zipToColors(attributeSet, colors);
    }

    /**
     * Generates a random color mapping using Cytoscape's Random palette provider.
     *
     * @param <T> attribute value type
     * @param attributeSet ordered set of distinct column values
     * @return map from attribute value to {@link Color}
     */
    public <T> Map<T, Color> generateRandom(Set<T> attributeSet) {
        int n = attributeSet.size();
        PaletteProvider provider = paletteProviderManager.getPaletteProvider("Random");
        Color[] colors = provider.getPalette("random", n).getColors(n);
        return zipToColors(attributeSet, colors);
    }

    /**
     * Generates a sequential (light-to-dark) color mapping using a ColorBrewer sequential palette.
     * Searches available sequential palettes for one whose name contains the requested hue keyword
     * (case-insensitive). Falls back to the first available sequential palette if no match is
     * found.
     *
     * @param <T> attribute value type
     * @param attributeSet ordered set of distinct column values
     * @param hue palette hue hint — e.g. "blue", "red", "green", "purple"
     * @return map from attribute value to {@link Color}
     * @throws IllegalStateException if no sequential palette providers are registered
     */
    public <T> Map<T, Color> generateBrewerSequential(Set<T> attributeSet, String hue) {
        int n = attributeSet.size();
        Palette palette = findSequentialPalette(hue, n);
        Color[] colors = palette.getColors(n);
        return zipToColors(attributeSet, colors);
    }

    // -- Shape generator -------------------------------------------------------

    /**
     * Generates a shape-cycle mapping by cycling over the discrete range values of the given visual
     * property. When the number of distinct attribute values exceeds the number of available
     * shapes, values wrap around.
     *
     * @param <T> attribute value type
     * @param attributeSet ordered set of distinct column values
     * @param vp visual property whose range is a {@link DiscreteRange}
     * @return map from attribute value to a shape object from the visual property's range
     */
    @SuppressWarnings("unchecked")
    public <T> Map<T, Object> generateShapeCycle(Set<T> attributeSet, VisualProperty<?> vp) {
        DiscreteRange<?> range = (DiscreteRange<?>) vp.getRange();
        List<Object> shapes = new ArrayList<>(range.values());
        Map<T, Object> result = new HashMap<>();
        int i = 0;
        for (T key : attributeSet) {
            result.put(key, shapes.get(i % shapes.size()));
            i++;
        }
        return result;
    }

    // -- Numeric range generator -----------------------------------------------

    /**
     * Generates a numeric-range mapping by evenly distributing values between {@code min} and
     * {@code max} inclusive. When n == 1 the single value equals {@code min}. The concrete type of
     * each value (Double vs Integer) is determined by {@code rangeType}.
     *
     * @param <T> attribute value type
     * @param attributeSet ordered set of distinct column values
     * @param min lower bound (inclusive)
     * @param max upper bound (inclusive)
     * @param rangeType the VP range type, used to determine whether to cast values to Integer or
     *     Double
     * @return map from attribute value to a Number
     */
    public <T> Map<T, Object> generateNumericRange(
            Set<T> attributeSet, double min, double max, Class<?> rangeType) {
        int n = attributeSet.size();
        boolean isInteger =
                Integer.class.isAssignableFrom(rangeType) || int.class.isAssignableFrom(rangeType);
        double step = (n > 1) ? (max - min) / (n - 1) : 0.0;
        Map<T, Object> result = new HashMap<>();
        int i = 0;
        for (T key : attributeSet) {
            double raw = min + i * step;
            // Use if/else to avoid ternary numeric widening (int→double promotion).
            if (isInteger) {
                result.put(key, (int) Math.round(raw));
            } else {
                result.put(key, raw);
            }
            i++;
        }
        return result;
    }

    // -- Helpers ---------------------------------------------------------------

    private <T> Map<T, Color> zipToColors(Set<T> attributeSet, Color[] colors) {
        Map<T, Color> result = new HashMap<>();
        int i = 0;
        for (T key : attributeSet) {
            result.put(key, colors[i < colors.length ? i : colors.length - 1]);
            i++;
        }
        return result;
    }

    /**
     * Finds a sequential palette matching the hue hint. Searches all registered sequential palette
     * providers for a palette whose name contains the hue string (case-insensitive). Falls back to
     * the first available sequential palette if no name match is found.
     */
    private Palette findSequentialPalette(String hue, int n) {
        List<PaletteProvider> providers =
                paletteProviderManager.getPaletteProviders(BrewerType.SEQUENTIAL, false);
        if (providers == null || providers.isEmpty()) {
            throw new IllegalStateException(
                    "No sequential palette providers registered in Cytoscape. Cannot apply"
                            + " brewer_sequential generator.");
        }
        String hueNorm = (hue != null) ? hue.toLowerCase() : "blue";
        // First pass: try to find a palette whose name contains the hue hint
        for (PaletteProvider provider : providers) {
            for (String name : provider.listPaletteNames(BrewerType.SEQUENTIAL, false)) {
                if (name.toLowerCase().contains(hueNorm)) {
                    return provider.getPalette(name, n);
                }
            }
        }
        // Fallback: return the first available sequential palette
        PaletteProvider firstProvider = providers.get(0);
        List<String> names = firstProvider.listPaletteNames(BrewerType.SEQUENTIAL, false);
        if (names.isEmpty()) {
            throw new IllegalStateException(
                    "Sequential palette provider '"
                            + firstProvider.getProviderName()
                            + "' has no palettes.");
        }
        return firstProvider.getPalette(names.get(0), n);
    }
}
