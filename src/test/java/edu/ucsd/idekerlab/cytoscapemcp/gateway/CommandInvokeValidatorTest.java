package edu.ucsd.idekerlab.cytoscapemcp.gateway;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.cytoscape.command.AvailableCommands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CommandInvokeValidator}. Uses NS/CMD constants with a Mockito-backed {@link
 * AvailableCommands} to isolate validation logic from any Cytoscape infrastructure.
 */
public class CommandInvokeValidatorTest {

    private static final String NS = "test";
    private static final String CMD = "cmd";

    @Mock private AvailableCommands availableCommands;
    private AutoCloseable mocks;
    private CommandInvokeValidator validator;

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        validator = new CommandInvokeValidator(availableCommands);
        // Default: no args unless overridden per test
        when(availableCommands.getArguments(NS, CMD)).thenReturn(Collections.emptyList());
    }

    // -- Helpers --------------------------------------------------------------

    private void stubSingleArg(String arg, boolean required, Class<?> type) {
        when(availableCommands.getArguments(NS, CMD)).thenReturn(List.of(arg));
        when(availableCommands.getArgRequired(NS, CMD, arg)).thenReturn(required);
        doReturn(type).when(availableCommands).getArgType(NS, CMD, arg);
    }

    private CommandInvokeValidator.Result.Ok assertOk(CommandInvokeValidator.Result result) {
        assertTrue(
                "Expected Ok but got: " + result,
                result instanceof CommandInvokeValidator.Result.Ok);
        return (CommandInvokeValidator.Result.Ok) result;
    }

    private CommandInvokeValidator.Result.Failure assertFailure(
            CommandInvokeValidator.Result result) {
        assertTrue(
                "Expected Failure but got: " + result,
                result instanceof CommandInvokeValidator.Result.Failure);
        return (CommandInvokeValidator.Result.Failure) result;
    }

    // -- Step 1: required param presence --------------------------------------

    @Test
    public void requiredParamMissing_returnsFailureWithParamName() {
        stubSingleArg("network", true, String.class);
        CommandInvokeValidator.Result.Failure f =
                assertFailure(validator.validate(NS, CMD, Map.of()));
        assertEquals("network", f.paramName());
        assertTrue(f.reason().contains("required parameter is missing"));
        assertTrue(f.reason().contains("string"));
    }

    @Test
    public void requiredParamPresent_doesNotFailStep1() {
        stubSingleArg("network", true, String.class);
        assertOk(validator.validate(NS, CMD, Map.of("network", "current")));
    }

    @Test
    public void optionalParamMissing_doesNotFailStep1() {
        stubSingleArg("nodeList", false, String.class);
        assertOk(validator.validate(NS, CMD, Map.of()));
    }

    @Test
    public void requiredParamMissing_failsOnFirstMissing_inArgNamesOrder() {
        when(availableCommands.getArguments(NS, CMD)).thenReturn(Arrays.asList("a", "b", "c"));
        when(availableCommands.getArgRequired(NS, CMD, "a")).thenReturn(false);
        when(availableCommands.getArgRequired(NS, CMD, "b")).thenReturn(true);
        when(availableCommands.getArgRequired(NS, CMD, "c")).thenReturn(true);
        doReturn(String.class).when(availableCommands).getArgType(NS, CMD, "b");
        CommandInvokeValidator.Result.Failure f =
                assertFailure(validator.validate(NS, CMD, Map.of()));
        assertEquals("b", f.paramName());
    }

    // -- Step 2: unknown params -----------------------------------------------

    @Test
    public void unknownParam_returnsFailureWithParamName() {
        stubSingleArg("network", false, String.class);
        CommandInvokeValidator.Result.Failure f =
                assertFailure(
                        validator.validate(NS, CMD, Map.of("network", "current", "bogus", "x")));
        assertEquals("bogus", f.paramName());
        assertTrue(f.reason().contains("unknown parameter"));
        assertTrue(f.reason().contains("network"));
    }

    @Test
    public void unknownParam_failsAlphabeticallyFirst() {
        stubSingleArg("network", false, String.class);
        CommandInvokeValidator.Result.Failure f =
                assertFailure(
                        validator.validate(
                                NS, CMD, Map.of("network", "current", "aaa", "x", "zzz", "y")));
        assertEquals("aaa", f.paramName());
    }

    // -- Step 3: type coercion — pass-throughs --------------------------------

    @Test
    public void stringParam_stringValue_passedThrough() {
        stubSingleArg("name", false, String.class);
        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("name", "hello")));
        assertEquals("hello", ok.coercedParams().get("name"));
    }

    @Test
    public void nullArgType_valuePassedThroughUnchanged() {
        when(availableCommands.getArguments(NS, CMD)).thenReturn(List.of("x"));
        when(availableCommands.getArgRequired(NS, CMD, "x")).thenReturn(false);
        when(availableCommands.getArgType(NS, CMD, "x")).thenReturn(null);
        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("x", "anything")));
        assertEquals("anything", ok.coercedParams().get("x"));
    }

    // -- Step 3: integer coercion ---------------------------------------------

    @Test
    public void integer_integerProvided_passedThrough() {
        stubSingleArg("count", false, Integer.class);
        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("count", 42)));
        assertEquals(Integer.valueOf(42), ok.coercedParams().get("count"));
    }

    @Test
    public void integer_integerProvided_longExpected_widenedToLong() {
        stubSingleArg("count", false, Long.class);
        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("count", 42)));
        assertEquals(Long.valueOf(42L), ok.coercedParams().get("count"));
    }

    @Test
    public void integer_longProvided_inRange_coercedToInt() {
        stubSingleArg("count", false, Integer.class);
        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("count", 100L)));
        assertEquals(Integer.valueOf(100), ok.coercedParams().get("count"));
    }

    @Test
    public void integer_longProvided_outOfRange_returnsFailure() {
        stubSingleArg("count", false, Integer.class);
        CommandInvokeValidator.Result.Failure f =
                assertFailure(validator.validate(NS, CMD, Map.of("count", Long.MAX_VALUE)));
        assertEquals("count", f.paramName());
        assertTrue(f.reason().contains("out of int range"));
    }

    @Test
    public void integer_stringProvided_parseable_coerced() {
        stubSingleArg("count", false, Integer.class);
        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("count", "7")));
        assertEquals(Integer.valueOf(7), ok.coercedParams().get("count"));
    }

    @Test
    public void integer_stringProvided_notParseable_returnsFailure() {
        stubSingleArg("count", false, Integer.class);
        CommandInvokeValidator.Result.Failure f =
                assertFailure(validator.validate(NS, CMD, Map.of("count", "abc")));
        assertEquals("count", f.paramName());
        assertTrue(f.reason().contains("expected integer"));
        assertTrue(f.reason().contains("abc"));
    }

    @Test
    public void integer_wrongType_returnsFailure() {
        stubSingleArg("count", false, Integer.class);
        CommandInvokeValidator.Result.Failure f =
                assertFailure(validator.validate(NS, CMD, Map.of("count", 3.14)));
        assertEquals("count", f.paramName());
        assertTrue(f.reason().contains("expected integer"));
    }

    // -- Step 3: number coercion ----------------------------------------------

    @Test
    public void number_doubleProvided_passedThrough() {
        stubSingleArg("score", false, Double.class);
        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("score", 1.5)));
        assertEquals(Double.valueOf(1.5), ok.coercedParams().get("score"));
    }

    @Test
    public void number_integerProvided_coercedToDouble() {
        stubSingleArg("score", false, Double.class);
        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("score", 3)));
        assertEquals(Double.valueOf(3.0), ok.coercedParams().get("score"));
    }

    @Test
    public void number_stringProvided_parseable_coercedToDouble() {
        stubSingleArg("score", false, Double.class);
        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("score", "2.5")));
        assertEquals(Double.valueOf(2.5), ok.coercedParams().get("score"));
    }

    @Test
    public void number_floatExpected_stringProvided_coercedToDouble() {
        // Spec: always Double.parseDouble for String inputs, even when expected is Float
        stubSingleArg("score", false, Float.class);
        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("score", "1.5")));
        assertEquals(Double.class, ok.coercedParams().get("score").getClass());
    }

    @Test
    public void number_stringProvided_notParseable_returnsFailure() {
        stubSingleArg("score", false, Double.class);
        CommandInvokeValidator.Result.Failure f =
                assertFailure(validator.validate(NS, CMD, Map.of("score", "notanumber")));
        assertEquals("score", f.paramName());
        assertTrue(f.reason().contains("expected number"));
        assertTrue(f.reason().contains("notanumber"));
    }

    @Test
    public void number_wrongType_returnsFailure() {
        stubSingleArg("score", false, Double.class);
        CommandInvokeValidator.Result.Failure f =
                assertFailure(validator.validate(NS, CMD, Map.of("score", true)));
        assertEquals("score", f.paramName());
        assertTrue(f.reason().contains("expected number"));
    }

    // -- Step 3: boolean coercion ---------------------------------------------

    @Test
    public void boolean_booleanProvided_passedThrough() {
        stubSingleArg("verbose", false, Boolean.class);
        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("verbose", Boolean.TRUE)));
        assertEquals(Boolean.TRUE, ok.coercedParams().get("verbose"));
    }

    @Test
    public void boolean_stringTrue_coerced() {
        stubSingleArg("verbose", false, Boolean.class);
        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("verbose", "true")));
        assertEquals(Boolean.TRUE, ok.coercedParams().get("verbose"));
    }

    @Test
    public void boolean_stringFalseWithWhitespace_coerced() {
        stubSingleArg("verbose", false, Boolean.class);
        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("verbose", " false ")));
        assertEquals(Boolean.FALSE, ok.coercedParams().get("verbose"));
    }

    @Test
    public void boolean_stringInvalid_returnsFailure() {
        stubSingleArg("verbose", false, Boolean.class);
        CommandInvokeValidator.Result.Failure f =
                assertFailure(validator.validate(NS, CMD, Map.of("verbose", "yes")));
        assertEquals("verbose", f.paramName());
        assertTrue(f.reason().contains("expected boolean"));
    }

    @Test
    public void boolean_wrongType_returnsFailure() {
        stubSingleArg("verbose", false, Boolean.class);
        CommandInvokeValidator.Result.Failure f =
                assertFailure(validator.validate(NS, CMD, Map.of("verbose", 1)));
        assertEquals("verbose", f.paramName());
        assertTrue(f.reason().contains("expected boolean"));
    }

    // -- Optional param handling ----------------------------------------------

    @Test
    public void optionalParamAbsent_notPresentInCoercedParams() {
        stubSingleArg("nodeList", false, String.class);
        CommandInvokeValidator.Result.Ok ok = assertOk(validator.validate(NS, CMD, Map.of()));
        assertFalse(ok.coercedParams().containsKey("nodeList"));
    }

    @Test
    public void requiredPresentOptionalAbsent_coercedParamsContainsOnlyRequired() {
        when(availableCommands.getArguments(NS, CMD))
                .thenReturn(Arrays.asList("network", "nodeList"));
        when(availableCommands.getArgRequired(NS, CMD, "network")).thenReturn(true);
        when(availableCommands.getArgRequired(NS, CMD, "nodeList")).thenReturn(false);
        doReturn(String.class).when(availableCommands).getArgType(NS, CMD, "network");
        doReturn(String.class).when(availableCommands).getArgType(NS, CMD, "nodeList");

        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("network", "current")));
        assertTrue(ok.coercedParams().containsKey("network"));
        assertFalse(ok.coercedParams().containsKey("nodeList"));
    }

    // -- toErrorMessage format ------------------------------------------------

    @Test
    public void failure_toErrorMessage_containsCommandKeyAndParamAndReason() {
        CommandInvokeValidator.Result.Failure f =
                new CommandInvokeValidator.Result.Failure("count", "expected integer");
        String msg = f.toErrorMessage("table import");
        assertTrue(msg.contains("table import"));
        assertTrue(msg.contains("count"));
        assertTrue(msg.contains("expected integer"));
    }

    // -- Null value pass-through ----------------------------------------------

    @Test
    public void nullValue_passedThroughWithoutCoercion() {
        when(availableCommands.getArguments(NS, CMD)).thenReturn(List.of("x"));
        when(availableCommands.getArgRequired(NS, CMD, "x")).thenReturn(false);
        doReturn(Integer.class).when(availableCommands).getArgType(NS, CMD, "x");
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("x", null);
        CommandInvokeValidator.Result.Ok ok = assertOk(validator.validate(NS, CMD, params));
        assertNull(ok.coercedParams().get("x"));
    }

    // -- Complex / unknown type pass-through ----------------------------------

    @Test
    public void complexType_listValue_passedThroughUnchanged() {
        // List.class maps to "string" category — value should pass through unchanged
        stubSingleArg("items", false, List.class);
        List<String> listVal = List.of("a", "b");
        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("items", listVal)));
        assertEquals(listVal, ok.coercedParams().get("items"));
    }

    // -- Multi-param happy path -----------------------------------------------

    @Test
    public void multipleParams_allValid_coercedMapContainsAll() {
        when(availableCommands.getArguments(NS, CMD))
                .thenReturn(Arrays.asList("name", "count", "verbose"));
        when(availableCommands.getArgRequired(NS, CMD, "name")).thenReturn(true);
        when(availableCommands.getArgRequired(NS, CMD, "count")).thenReturn(true);
        when(availableCommands.getArgRequired(NS, CMD, "verbose")).thenReturn(false);
        doReturn(String.class).when(availableCommands).getArgType(NS, CMD, "name");
        doReturn(Integer.class).when(availableCommands).getArgType(NS, CMD, "count");
        doReturn(Boolean.class).when(availableCommands).getArgType(NS, CMD, "verbose");
        CommandInvokeValidator.Result.Ok ok =
                assertOk(
                        validator.validate(
                                NS, CMD, Map.of("name", "foo", "count", "3", "verbose", "true")));
        assertEquals("foo", ok.coercedParams().get("name"));
        assertEquals(Integer.valueOf(3), ok.coercedParams().get("count"));
        assertEquals(Boolean.TRUE, ok.coercedParams().get("verbose"));
    }

    // -- Step 3: String coercion from non-String values -----------------------

    @Test
    public void string_integerValue_coercedToString() {
        stubSingleArg("network", false, String.class);
        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("network", 42)));
        assertEquals("42", ok.coercedParams().get("network"));
    }

    @Test
    public void string_longValue_coercedToString() {
        stubSingleArg("network", false, String.class);
        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("network", 19876L)));
        assertEquals("19876", ok.coercedParams().get("network"));
    }

    @Test
    public void string_doubleValue_coercedToString() {
        stubSingleArg("network", false, String.class);
        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("network", 3.14)));
        assertEquals("3.14", ok.coercedParams().get("network"));
    }

    @Test
    public void string_booleanValue_coercedToString() {
        stubSingleArg("network", false, String.class);
        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("network", true)));
        assertEquals("true", ok.coercedParams().get("network"));
    }

    // -- Step 3: integer coercion — additional valid paths --------------------

    @Test
    public void integer_stringToLong_coerced() {
        stubSingleArg("id", false, Long.class);
        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("id", "100")));
        assertEquals(Long.valueOf(100L), ok.coercedParams().get("id"));
    }

    @Test
    public void integer_longToLong_passThrough() {
        stubSingleArg("id", false, Long.class);
        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("id", 100L)));
        assertEquals(Long.valueOf(100L), ok.coercedParams().get("id"));
    }

    // -- Step 3: number coercion — additional valid paths ---------------------

    @Test
    public void number_longToDouble_widened() {
        stubSingleArg("score", false, Double.class);
        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("score", 100L)));
        assertEquals(Double.valueOf(100.0), ok.coercedParams().get("score"));
    }

    @Test
    public void number_integerToFloat_widened() {
        stubSingleArg("score", false, Float.class);
        CommandInvokeValidator.Result.Ok ok =
                assertOk(validator.validate(NS, CMD, Map.of("score", 3)));
        assertEquals(Float.valueOf(3.0f), ok.coercedParams().get("score"));
    }

    // -- Step 3: additional rejection cases -----------------------------------

    @Test
    public void integer_doubleToLong_returnsFailure() {
        stubSingleArg("id", false, Long.class);
        CommandInvokeValidator.Result.Failure f =
                assertFailure(validator.validate(NS, CMD, Map.of("id", 3.14)));
        assertEquals("id", f.paramName());
        assertTrue(f.reason().contains("expected integer"));
        assertTrue(f.reason().contains("Double"));
    }

    @Test
    public void boolean_integerValue_returnsFailure() {
        stubSingleArg("verbose", false, Boolean.class);
        CommandInvokeValidator.Result.Failure f =
                assertFailure(validator.validate(NS, CMD, Map.of("verbose", 1)));
        assertEquals("verbose", f.paramName());
        assertTrue(f.reason().contains("expected boolean"));
    }

    @Test
    public void number_booleanValue_returnsFailure() {
        stubSingleArg("score", false, Double.class);
        CommandInvokeValidator.Result.Failure f =
                assertFailure(validator.validate(NS, CMD, Map.of("score", true)));
        assertEquals("score", f.paramName());
        assertTrue(f.reason().contains("expected number"));
    }
}
