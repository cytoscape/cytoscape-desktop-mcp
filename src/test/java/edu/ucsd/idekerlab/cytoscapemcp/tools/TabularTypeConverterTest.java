package edu.ucsd.idekerlab.cytoscapemcp.tools;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Unit tests for {@link TabularTypeConverter} — no Cytoscape mocks needed. */
public class TabularTypeConverterTest {

    private final TabularTypeConverter converter = new TabularTypeConverter();

    // -----------------------------------------------------------------------
    // inferType — basic cases
    // -----------------------------------------------------------------------

    @Test
    public void inferType_allIntegers_returnsInteger() {
        assertEquals(DataColumn.CyDataType.INTEGER, converter.inferType(List.of("1", "2", "3")));
    }

    @Test
    public void inferType_allLongs_returnsLong() {
        assertEquals(
                DataColumn.CyDataType.LONG,
                converter.inferType(List.of("3000000000", "4000000000")));
    }

    @Test
    public void inferType_allDoubles_returnsDouble() {
        assertEquals(DataColumn.CyDataType.DOUBLE, converter.inferType(List.of("1.5", "2.7")));
    }

    @Test
    public void inferType_allBooleans_returnsBoolean() {
        assertEquals(DataColumn.CyDataType.BOOLEAN, converter.inferType(List.of("true", "FALSE")));
    }

    @Test
    public void inferType_mixedNumericAndString_returnsString() {
        assertEquals(DataColumn.CyDataType.STRING, converter.inferType(List.of("42", "hello")));
    }

    @Test
    public void inferType_allBlank_returnsString() {
        assertEquals(DataColumn.CyDataType.STRING, converter.inferType(List.of("", "  ", "")));
    }

    @Test
    public void inferType_integerFitsRange_prefersIntegerOverLong() {
        assertEquals(DataColumn.CyDataType.INTEGER, converter.inferType(List.of("100")));
    }

    @Test
    public void inferType_mixedIntegerAndDouble_returnsDouble() {
        assertEquals(DataColumn.CyDataType.DOUBLE, converter.inferType(List.of("1", "2.5")));
    }

    @Test
    public void inferType_emptyList_returnsString() {
        assertEquals(DataColumn.CyDataType.STRING, converter.inferType(List.of()));
    }

    @Test
    public void inferType_blanksSkipped_nonBlankDrivesInference() {
        // Mix of blanks and integers — blanks are ignored; integers drive result
        assertEquals(DataColumn.CyDataType.INTEGER, converter.inferType(List.of("", "42", "  ")));
    }

    // -----------------------------------------------------------------------
    // coerceToColumnType
    // -----------------------------------------------------------------------

    @Test
    public void coerceToColumnType_integerType_parsesCorrectly() {
        assertEquals(Integer.valueOf(42), converter.coerceToColumnType("42", Integer.class));
    }

    @Test
    public void coerceToColumnType_longType_parsesCorrectly() {
        assertEquals(
                Long.valueOf(3000000000L), converter.coerceToColumnType("3000000000", Long.class));
    }

    @Test
    public void coerceToColumnType_doubleType_parsesCorrectly() {
        assertEquals(Double.valueOf(3.14), converter.coerceToColumnType("3.14", Double.class));
    }

    @Test
    public void coerceToColumnType_booleanType_parsesCorrectly() {
        assertEquals(Boolean.TRUE, converter.coerceToColumnType("true", Boolean.class));
        assertEquals(Boolean.FALSE, converter.coerceToColumnType("false", Boolean.class));
    }

    @Test
    public void coerceToColumnType_stringType_returnsRaw() {
        assertEquals("hello world", converter.coerceToColumnType("hello world", String.class));
    }

    @Test
    public void coerceToColumnType_parseFailure_returnsNull() {
        assertNull(converter.coerceToColumnType("N/A", Integer.class));
    }

    @Test
    public void coerceToColumnType_blankValue_returnsNull() {
        assertNull(converter.coerceToColumnType("  ", Double.class));
    }

    @Test
    public void coerceToColumnType_nullValue_returnsNull() {
        assertNull(converter.coerceToColumnType(null, Integer.class));
    }

    @Test
    public void coerceToColumnType_nanDouble_returnsNull() {
        assertNull(converter.coerceToColumnType("NaN", Double.class));
    }

    @Test
    public void coerceToColumnType_infiniteDouble_returnsNull() {
        assertNull(converter.coerceToColumnType("Infinity", Double.class));
    }

    @Test
    public void coerceToColumnType_trims_whitespace() {
        assertEquals(Integer.valueOf(7), converter.coerceToColumnType("  7  ", Integer.class));
    }
}
