package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.awt.Color;
import java.awt.Font;
import java.awt.Paint;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.cytoscape.view.model.ContinuousRange;
import org.cytoscape.view.model.DiscreteRange;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.model.VisualProperty;
import org.cytoscape.view.presentation.property.ArrowShapeVisualProperty;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.view.presentation.property.LineTypeVisualProperty;
import org.cytoscape.view.presentation.property.NodeShapeVisualProperty;
import org.cytoscape.view.presentation.property.values.ArrowShape;
import org.cytoscape.view.presentation.property.values.NodeShape;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class VisualPropertyServiceTest {

    private final VisualPropertyService service = new VisualPropertyService();

    // ---- findPropertyById ----

    @SuppressWarnings("unchecked")
    @Test
    public void findPropertyById_found_returnsVP() {
        VisualLexicon lexicon = mock(VisualLexicon.class);
        VisualProperty<Double> vp = mock(VisualProperty.class);
        when(vp.getIdString()).thenReturn("NODE_SIZE");
        when(lexicon.getAllVisualProperties()).thenReturn(Set.of(vp));

        assertSame(vp, service.findPropertyById(lexicon, "NODE_SIZE"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void findPropertyById_notFound_returnsNull() {
        VisualLexicon lexicon = mock(VisualLexicon.class);
        when(lexicon.getAllVisualProperties()).thenReturn(Set.of());

        assertNull(service.findPropertyById(lexicon, "NONEXISTENT"));
    }

    // ---- isSupported ----

    @SuppressWarnings("unchecked")
    @Test
    public void isSupported_paintRange_returnsTrue() {
        VisualProperty<Paint> vp = mock(VisualProperty.class);
        ContinuousRange<Paint> range = new ContinuousRange<>(Paint.class, null, null, true, true);
        when(vp.getRange()).thenReturn(range);

        assertTrue(service.isSupported(vp));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void isSupported_nodeShapeRange_returnsTrue() {
        VisualProperty<NodeShape> vp = mock(VisualProperty.class);
        DiscreteRange<NodeShape> range =
                new DiscreteRange<>(NodeShape.class, Set.of(NodeShapeVisualProperty.ELLIPSE));
        when(vp.getRange()).thenReturn(range);

        assertTrue(service.isSupported(vp));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void isSupported_nullRange_returnsFalse() {
        VisualProperty<?> vp = mock(VisualProperty.class);
        when(vp.getRange()).thenReturn(null);

        assertFalse(service.isSupported(vp));
    }

    // ---- getTypeName ----

    @Test
    public void getTypeName_paint_returnsPaint() {
        ContinuousRange<Paint> range = new ContinuousRange<>(Paint.class, null, null, true, true);
        assertEquals("Paint", service.getTypeName(range));
    }

    @Test
    public void getTypeName_color_returnsPaint() {
        // Color extends Paint, so isAssignableFrom(Color) should return "Paint"
        ContinuousRange<Color> range = new ContinuousRange<>(Color.class, null, null, true, true);
        assertEquals("Paint", service.getTypeName(range));
    }

    @Test
    public void getTypeName_double_returnsDouble() {
        ContinuousRange<Double> range =
                new ContinuousRange<>(Double.class, 0.0, 1000.0, true, true);
        assertEquals("Double", service.getTypeName(range));
    }

    @Test
    public void getTypeName_integer_returnsInteger() {
        ContinuousRange<Integer> range = new ContinuousRange<>(Integer.class, 0, 255, true, true);
        assertEquals("Integer", service.getTypeName(range));
    }

    @Test
    public void getTypeName_nodeShape_returnsNodeShape() {
        DiscreteRange<NodeShape> range = new DiscreteRange<>(NodeShape.class, new HashSet<>());
        assertEquals("NodeShape", service.getTypeName(range));
    }

    @Test
    public void getTypeName_null_returnsUnknown() {
        assertEquals("Unknown", service.getTypeName(null));
    }

    // ---- getContinuousSubType ----

    @SuppressWarnings("unchecked")
    @Test
    public void getContinuousSubType_paintContinuous_returnsColorGradient() {
        VisualProperty<Paint> vp = mock(VisualProperty.class);
        ContinuousRange<Paint> range = new ContinuousRange<>(Paint.class, null, null, true, true);
        when(vp.getRange()).thenReturn(range);

        assertEquals("color-gradient", service.getContinuousSubType(vp));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void getContinuousSubType_doubleContinuous_returnsContinuous() {
        VisualProperty<Double> vp = mock(VisualProperty.class);
        ContinuousRange<Double> range = new ContinuousRange<>(Double.class, 0.0, 100.0, true, true);
        when(vp.getRange()).thenReturn(range);

        assertEquals("continuous", service.getContinuousSubType(vp));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void getContinuousSubType_nodeShapeDiscrete_returnsDiscrete() {
        VisualProperty<NodeShape> vp = mock(VisualProperty.class);
        DiscreteRange<NodeShape> range =
                new DiscreteRange<>(NodeShape.class, Set.of(NodeShapeVisualProperty.ELLIPSE));
        when(vp.getRange()).thenReturn(range);

        assertEquals("discrete", service.getContinuousSubType(vp));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void getContinuousSubType_stringContinuous_returnsNull() {
        VisualProperty<String> vp = mock(VisualProperty.class);
        ContinuousRange<String> range = new ContinuousRange<>(String.class, null, null, true, true);
        when(vp.getRange()).thenReturn(range);

        assertNull(service.getContinuousSubType(vp));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void getContinuousSubType_nullRange_returnsNull() {
        VisualProperty<?> vp = mock(VisualProperty.class);
        when(vp.getRange()).thenReturn(null);

        assertNull(service.getContinuousSubType(vp));
    }

    // ---- formatValue ----

    @Test
    public void formatValue_color_returnsHex() {
        assertEquals("#FF0000", service.formatValue(new Color(255, 0, 0)));
        assertEquals("#00FF00", service.formatValue(new Color(0, 255, 0)));
        assertEquals("#0000FF", service.formatValue(new Color(0, 0, 255)));
        assertEquals("#000000", service.formatValue(Color.BLACK));
        assertEquals("#FFFFFF", service.formatValue(Color.WHITE));
    }

    @Test
    public void formatValue_font_returnsFamilyStyleSize() {
        assertEquals(
                "SansSerif-Plain-12", service.formatValue(new Font("SansSerif", Font.PLAIN, 12)));
        assertEquals(
                "SansSerif-Bold-14", service.formatValue(new Font("SansSerif", Font.BOLD, 14)));
        assertEquals("Serif-Italic-10", service.formatValue(new Font("Serif", Font.ITALIC, 10)));
        assertEquals(
                "Dialog-BoldItalic-18",
                service.formatValue(new Font("Dialog", Font.BOLD | Font.ITALIC, 18)));
    }

    @Test
    public void formatValue_nodeShape_returnsDisplayName() {
        assertEquals("Ellipse", service.formatValue(NodeShapeVisualProperty.ELLIPSE));
        assertEquals("Rectangle", service.formatValue(NodeShapeVisualProperty.RECTANGLE));
        assertEquals("Diamond", service.formatValue(NodeShapeVisualProperty.DIAMOND));
    }

    @Test
    public void formatValue_arrowShape_returnsDisplayName() {
        assertEquals("None", service.formatValue(ArrowShapeVisualProperty.NONE));
        assertEquals("Arrow", service.formatValue(ArrowShapeVisualProperty.ARROW));
        assertEquals("Delta", service.formatValue(ArrowShapeVisualProperty.DELTA));
    }

    @Test
    public void formatValue_lineType_returnsDisplayName() {
        assertEquals("Solid", service.formatValue(LineTypeVisualProperty.SOLID));
    }

    @Test
    public void formatValue_double_returnsString() {
        assertEquals("35.0", service.formatValue(35.0));
    }

    @Test
    public void formatValue_integer_returnsString() {
        assertEquals("255", service.formatValue(255));
    }

    @Test
    public void formatValue_string_returnsString() {
        assertEquals("hello", service.formatValue("hello"));
    }

    @Test
    public void formatValue_boolean_returnsString() {
        assertEquals("true", service.formatValue(true));
    }

    // ---- parseValue ----

    @Test
    public void parseValue_color_roundTrip() {
        VisualProperty<Paint> vp = BasicVisualLexicon.NODE_FILL_COLOR;
        Color parsed = (Color) service.parseValue(vp, "#FF6600");
        assertEquals("#FF6600", service.formatValue(parsed));
    }

    @Test
    public void parseValue_color_withoutHash() {
        VisualProperty<Paint> vp = BasicVisualLexicon.NODE_FILL_COLOR;
        Color parsed = (Color) service.parseValue(vp, "FF0000");
        assertEquals(255, parsed.getRed());
        assertEquals(0, parsed.getGreen());
        assertEquals(0, parsed.getBlue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseValue_color_invalid_throwsException() {
        service.parseValue(BasicVisualLexicon.NODE_FILL_COLOR, "notacolor");
    }

    @Test
    public void parseValue_double_fromNumber() {
        VisualProperty<Double> vp = BasicVisualLexicon.NODE_SIZE;
        assertEquals(35.0, (Double) service.parseValue(vp, 35.0), 0.001);
    }

    @Test
    public void parseValue_double_fromString() {
        VisualProperty<Double> vp = BasicVisualLexicon.NODE_SIZE;
        assertEquals(42.5, (Double) service.parseValue(vp, "42.5"), 0.001);
    }

    @Test
    public void parseValue_integer_fromNumber() {
        VisualProperty<Integer> vp = BasicVisualLexicon.NODE_TRANSPARENCY;
        assertEquals(Integer.valueOf(200), service.parseValue(vp, 200));
    }

    @Test
    public void parseValue_integer_fromString() {
        VisualProperty<Integer> vp = BasicVisualLexicon.NODE_TRANSPARENCY;
        assertEquals(Integer.valueOf(128), service.parseValue(vp, "128"));
    }

    @Test
    public void parseValue_nodeShape_bySerializableString() {
        NodeShape shape = (NodeShape) service.parseValue(BasicVisualLexicon.NODE_SHAPE, "ELLIPSE");
        assertSame(NodeShapeVisualProperty.ELLIPSE, shape);
    }

    @Test
    public void parseValue_nodeShape_byDisplayName() {
        NodeShape shape = (NodeShape) service.parseValue(BasicVisualLexicon.NODE_SHAPE, "Ellipse");
        assertSame(NodeShapeVisualProperty.ELLIPSE, shape);
    }

    @Test
    public void parseValue_nodeShape_caseInsensitive() {
        NodeShape shape = (NodeShape) service.parseValue(BasicVisualLexicon.NODE_SHAPE, "diamond");
        assertSame(NodeShapeVisualProperty.DIAMOND, shape);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseValue_nodeShape_invalid_throwsException() {
        service.parseValue(BasicVisualLexicon.NODE_SHAPE, "Pentagon");
    }

    @Test
    public void parseValue_string_returnsString() {
        VisualProperty<String> vp = BasicVisualLexicon.NODE_LABEL;
        assertEquals("test label", service.parseValue(vp, "test label"));
    }

    @Test
    public void parseValue_boolean_returnsBoolean() {
        VisualProperty<Boolean> vp = BasicVisualLexicon.NODE_VISIBLE;
        assertEquals(Boolean.TRUE, service.parseValue(vp, "true"));
        assertEquals(Boolean.FALSE, service.parseValue(vp, "false"));
    }

    // ---- parseNodeShape ----

    @Test
    public void parseNodeShape_bySerializableString() {
        assertSame(NodeShapeVisualProperty.ELLIPSE, service.parseNodeShape("ELLIPSE"));
        assertSame(NodeShapeVisualProperty.RECTANGLE, service.parseNodeShape("RECTANGLE"));
        assertSame(NodeShapeVisualProperty.DIAMOND, service.parseNodeShape("DIAMOND"));
    }

    @Test
    public void parseNodeShape_byDisplayName() {
        assertSame(NodeShapeVisualProperty.ELLIPSE, service.parseNodeShape("Ellipse"));
        assertSame(
                NodeShapeVisualProperty.ROUND_RECTANGLE, service.parseNodeShape("Round Rectangle"));
    }

    @Test
    public void parseNodeShape_caseInsensitive() {
        assertSame(NodeShapeVisualProperty.TRIANGLE, service.parseNodeShape("triangle"));
        assertSame(NodeShapeVisualProperty.HEXAGON, service.parseNodeShape("hexagon"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseNodeShape_invalid_throwsException() {
        service.parseNodeShape("Pentagon");
    }

    // ---- parseArrowShape ----

    @Test
    public void parseArrowShape_bySerializableString() {
        assertSame(ArrowShapeVisualProperty.NONE, service.parseArrowShape("NONE"));
        assertSame(ArrowShapeVisualProperty.ARROW, service.parseArrowShape("ARROW"));
        assertSame(ArrowShapeVisualProperty.DELTA, service.parseArrowShape("DELTA"));
    }

    @Test
    public void parseArrowShape_byDisplayName() {
        assertSame(ArrowShapeVisualProperty.NONE, service.parseArrowShape("None"));
        assertSame(ArrowShapeVisualProperty.ARROW, service.parseArrowShape("Arrow"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseArrowShape_invalid_throwsException() {
        service.parseArrowShape("Invalid");
    }

    // ---- parseLineType ----

    @Test
    public void parseLineType_bySerializableString() {
        assertSame(LineTypeVisualProperty.SOLID, service.parseLineType("SOLID"));
    }

    @Test
    public void parseLineType_byDisplayName() {
        assertSame(LineTypeVisualProperty.SOLID, service.parseLineType("Solid"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseLineType_invalid_throwsException() {
        service.parseLineType("Invalid");
    }

    // ---- Round-trip tests: formatValue(parseValue(vp, input)) ----

    @Test
    public void roundTrip_color() {
        Color parsed = (Color) service.parseValue(BasicVisualLexicon.NODE_FILL_COLOR, "#FF6600");
        assertEquals("#FF6600", service.formatValue(parsed));
    }

    @Test
    public void roundTrip_nodeShape() {
        NodeShape parsed = (NodeShape) service.parseValue(BasicVisualLexicon.NODE_SHAPE, "Diamond");
        assertEquals("Diamond", service.formatValue(parsed));
    }

    @Test
    public void roundTrip_arrowShape() {
        ArrowShape parsed =
                (ArrowShape)
                        service.parseValue(BasicVisualLexicon.EDGE_TARGET_ARROW_SHAPE, "Delta");
        assertEquals("Delta", service.formatValue(parsed));
    }

    @Test
    public void roundTrip_double() {
        Double parsed = (Double) service.parseValue(BasicVisualLexicon.NODE_SIZE, 42.5);
        assertEquals("42.5", service.formatValue(parsed));
    }
}
