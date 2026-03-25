package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.cytoscape.util.color.BrewerType;
import org.cytoscape.util.color.Palette;
import org.cytoscape.util.color.PaletteProvider;
import org.cytoscape.util.color.PaletteProviderManager;
import org.cytoscape.view.model.DiscreteRange;
import org.cytoscape.view.model.VisualProperty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GeneratorService} using mocked {@link PaletteProviderManager} to isolate
 * palette SDK calls from runtime Cytoscape state.
 */
public class GeneratorServiceTest {

    @Mock private PaletteProviderManager paletteProviderManager;
    @Mock private PaletteProvider rainbowProvider;
    @Mock private PaletteProvider randomProvider;
    @Mock private PaletteProvider sequentialProvider;
    @Mock private Palette mockPalette;

    private GeneratorService service;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new GeneratorService(paletteProviderManager);
    }

    // -----------------------------------------------------------------------
    // generateRainbow — correct key-to-color mapping
    // -----------------------------------------------------------------------

    @Test
    public void generateRainbow_mapsEachValueToAColor() {
        Color red = Color.RED;
        Color green = Color.GREEN;
        Color blue = Color.BLUE;
        when(paletteProviderManager.getPaletteProvider("Rainbow")).thenReturn(rainbowProvider);
        when(rainbowProvider.getPalette("rainbow", 3)).thenReturn(mockPalette);
        when(mockPalette.getColors(3)).thenReturn(new Color[] {red, green, blue});

        Set<String> input = new LinkedHashSet<>(List.of("kinase", "receptor", "TF"));
        Map<String, Color> result = service.generateRainbow(input);

        assertEquals("Should have 3 entries", 3, result.size());
        assertEquals("kinase should get first color", red, result.get("kinase"));
        assertEquals("receptor should get second color", green, result.get("receptor"));
        assertEquals("TF should get third color", blue, result.get("TF"));
    }

    // -----------------------------------------------------------------------
    // generateRainbow — single value
    // -----------------------------------------------------------------------

    @Test
    public void generateRainbow_singleValue_returnsOneEntry() {
        when(paletteProviderManager.getPaletteProvider("Rainbow")).thenReturn(rainbowProvider);
        when(rainbowProvider.getPalette("rainbow", 1)).thenReturn(mockPalette);
        when(mockPalette.getColors(1)).thenReturn(new Color[] {Color.RED});

        Map<String, Color> result = service.generateRainbow(Set.of("only"));

        assertEquals("Should have 1 entry", 1, result.size());
        assertEquals(Color.RED, result.get("only"));
    }

    // -----------------------------------------------------------------------
    // generateRandom — correct key-to-color mapping
    // -----------------------------------------------------------------------

    @Test
    public void generateRandom_mapsEachValueToAColor() {
        when(paletteProviderManager.getPaletteProvider("Random")).thenReturn(randomProvider);
        when(randomProvider.getPalette("random", 2)).thenReturn(mockPalette);
        when(mockPalette.getColors(2)).thenReturn(new Color[] {Color.CYAN, Color.MAGENTA});

        Set<String> input = new LinkedHashSet<>(List.of("A", "B"));
        Map<String, Color> result = service.generateRandom(input);

        assertEquals("Should have 2 entries", 2, result.size());
        assertEquals(Color.CYAN, result.get("A"));
        assertEquals(Color.MAGENTA, result.get("B"));
    }

    // -----------------------------------------------------------------------
    // generateBrewerSequential — hue match
    // -----------------------------------------------------------------------

    @Test
    public void generateBrewerSequential_withMatchingHue_usesMatchingPalette() {
        Palette bluesPalette = mock(Palette.class);
        when(bluesPalette.getColors(2)).thenReturn(new Color[] {Color.BLUE, new Color(0, 0, 128)});

        when(paletteProviderManager.getPaletteProviders(BrewerType.SEQUENTIAL, false))
                .thenReturn(List.of(sequentialProvider));
        when(sequentialProvider.listPaletteNames(BrewerType.SEQUENTIAL, false))
                .thenReturn(List.of("Blues", "Greens"));
        when(sequentialProvider.getPalette("Blues", 2)).thenReturn(bluesPalette);

        Set<String> input = new LinkedHashSet<>(List.of("low", "high"));
        Map<String, Color> result = service.generateBrewerSequential(input, "blue");

        assertEquals(2, result.size());
        assertNotNull(result.get("low"));
        assertNotNull(result.get("high"));
    }

    // -----------------------------------------------------------------------
    // generateBrewerSequential — no hue match falls back to first palette
    // -----------------------------------------------------------------------

    @Test
    public void generateBrewerSequential_noHueMatch_fallsBackToFirstPalette() {
        Palette fallbackPalette = mock(Palette.class);
        when(fallbackPalette.getColors(1)).thenReturn(new Color[] {Color.RED});

        when(paletteProviderManager.getPaletteProviders(BrewerType.SEQUENTIAL, false))
                .thenReturn(List.of(sequentialProvider));
        when(sequentialProvider.listPaletteNames(BrewerType.SEQUENTIAL, false))
                .thenReturn(List.of("Oranges", "Purples"));
        when(sequentialProvider.getPalette("Oranges", 1)).thenReturn(fallbackPalette);

        Map<String, Color> result = service.generateBrewerSequential(Set.of("x"), "blue");

        assertEquals("Should produce 1 entry via fallback", 1, result.size());
        assertEquals(Color.RED, result.get("x"));
    }

    // -----------------------------------------------------------------------
    // generateBrewerSequential — throws when no sequential providers
    // -----------------------------------------------------------------------

    @Test(expected = IllegalStateException.class)
    public void generateBrewerSequential_noProviders_throwsIllegalState() {
        when(paletteProviderManager.getPaletteProviders(BrewerType.SEQUENTIAL, false))
                .thenReturn(List.of());

        service.generateBrewerSequential(Set.of("x"), "blue");
    }

    // -----------------------------------------------------------------------
    // generateShapeCycle — wraps around when values exceed shapes
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void generateShapeCycle_wrapsAroundWhenValuesExceedShapes() {
        VisualProperty<String> mockVp = mock(VisualProperty.class);
        DiscreteRange<String> mockRange = mock(DiscreteRange.class);
        when(mockVp.getRange()).thenReturn(mockRange);
        // Only 2 shapes, 4 distinct values → wraps
        Set<String> shapes = new LinkedHashSet<>(List.of("ELLIPSE", "DIAMOND"));
        when(mockRange.values()).thenReturn(shapes);

        Set<String> input = new LinkedHashSet<>(List.of("A", "B", "C", "D"));
        Map<String, Object> result = service.generateShapeCycle(input, mockVp);

        assertEquals("Should have 4 entries", 4, result.size());
        assertEquals("A → ELLIPSE (index 0)", "ELLIPSE", result.get("A"));
        assertEquals("B → DIAMOND (index 1)", "DIAMOND", result.get("B"));
        assertEquals("C → ELLIPSE (wrap index 2%2=0)", "ELLIPSE", result.get("C"));
        assertEquals("D → DIAMOND (wrap index 3%2=1)", "DIAMOND", result.get("D"));
    }

    // -----------------------------------------------------------------------
    // generateShapeCycle — fewer values than shapes
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    public void generateShapeCycle_fewerValuesThanShapes_usesFirstN() {
        VisualProperty<String> mockVp = mock(VisualProperty.class);
        DiscreteRange<String> mockRange = mock(DiscreteRange.class);
        when(mockVp.getRange()).thenReturn(mockRange);
        Set<String> shapes =
                new LinkedHashSet<>(List.of("ELLIPSE", "DIAMOND", "RECTANGLE", "TRIANGLE"));
        when(mockRange.values()).thenReturn(shapes);

        Set<String> input = new LinkedHashSet<>(List.of("kinase"));
        Map<String, Object> result = service.generateShapeCycle(input, mockVp);

        assertEquals(1, result.size());
        assertEquals("ELLIPSE", result.get("kinase"));
    }

    // -----------------------------------------------------------------------
    // generateNumericRange — double VP type
    // -----------------------------------------------------------------------

    @Test
    public void generateNumericRange_doubleType_evenlySpaced() {
        Set<String> input = new LinkedHashSet<>(List.of("v1", "v2", "v3", "v4", "v5"));
        Map<String, Object> result = service.generateNumericRange(input, 10.0, 60.0, Double.class);

        assertEquals(5, result.size());
        assertEquals("First value should be min", 10.0, (Double) result.get("v1"), 0.001);
        assertEquals("Last value should be max", 60.0, (Double) result.get("v5"), 0.001);
        assertEquals("Third value should be midpoint", 35.0, (Double) result.get("v3"), 0.001);
    }

    // -----------------------------------------------------------------------
    // generateNumericRange — integer VP type casts to int
    // -----------------------------------------------------------------------

    @Test
    public void generateNumericRange_integerType_castToInt() {
        Set<String> input = new LinkedHashSet<>(List.of("a", "b"));
        Map<String, Object> result = service.generateNumericRange(input, 0, 10, Integer.class);

        assertEquals(2, result.size());
        assertTrue("First value should be Integer", result.get("a") instanceof Integer);
        assertEquals(0, result.get("a"));
        assertEquals(10, result.get("b"));
    }

    // -----------------------------------------------------------------------
    // generateNumericRange — single value equals min
    // -----------------------------------------------------------------------

    @Test
    public void generateNumericRange_singleValue_equalsMin() {
        Map<String, Object> result =
                service.generateNumericRange(Set.of("only"), 25.0, 75.0, Double.class);

        assertEquals(1, result.size());
        assertEquals(25.0, (Double) result.get("only"), 0.001);
    }
}
