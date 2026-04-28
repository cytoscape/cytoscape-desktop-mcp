package edu.ucsd.idekerlab.cytoscapemcp.gateway;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.idekerlab.cytoscapemcp.fixture.InMemoryTransport;

import org.cytoscape.command.AvailableCommands;
import org.cytoscape.command.CommandExecutorTaskFactory;
import org.cytoscape.work.FinishStatus;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.SynchronousTaskManager;
import org.cytoscape.work.Task;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.TaskObserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link CommandGatewayInvokeTool} end-to-end via {@link InMemoryTransport} with mocked
 * Cytoscape services.
 */
public class CommandGatewayInvokeToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String INIT_REQUEST =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\","
                    + "\"params\":{\"protocolVersion\":\"2025-03-26\","
                    + "\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";

    private static final String INITIALIZED_NOTIFICATION =
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

    @Mock private AvailableCommands availableCommands;
    @Mock private SynchronousTaskManager<?> syncTaskManager;
    @Mock private CommandExecutorTaskFactory commandExecutorTaskFactory;
    @Mock private CommandInvokeValidator invokeValidator;
    private AutoCloseable mocks;
    private InMemoryTransport transport;

    /** No-op Task for the TaskIterator stub. */
    private static final Task NO_OP_TASK =
            new Task() {
                @Override
                public void run(TaskMonitor tm) {}

                @Override
                public void cancel() {}
            };

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);

        // Stub AvailableCommands for "network select"
        when(availableCommands.getNamespaces()).thenReturn(Arrays.asList("network"));
        when(availableCommands.getCommands("network")).thenReturn(Arrays.asList("select"));
        when(availableCommands.getArguments("network", "select"))
                .thenReturn(Arrays.asList("network", "nodeList"));
        when(availableCommands.getArgRequired("network", "select", "network")).thenReturn(false);
        when(availableCommands.getArgRequired("network", "select", "nodeList")).thenReturn(false);

        // Stub task factory to return a no-op iterator and call observer with success
        when(commandExecutorTaskFactory.createTaskIterator(
                        anyString(), anyString(), anyMap(), any(TaskObserver.class)))
                .thenAnswer(
                        inv -> {
                            TaskObserver observer = inv.getArgument(3);
                            // Simulate successful execution — call taskFinished then allFinished
                            ObservableTask obsTask =
                                    new ObservableTask() {
                                        @Override
                                        public void run(TaskMonitor tm) {}

                                        @Override
                                        public void cancel() {}

                                        @Override
                                        public <R> R getResults(Class<? extends R> type) {
                                            if (type == String.class)
                                                return type.cast("{\"selected\":[]}");
                                            return null;
                                        }

                                        @Override
                                        public List<Class<?>> getResultClasses() {
                                            return List.of(String.class);
                                        }
                                    };
                            observer.taskFinished(obsTask);
                            observer.allFinished(FinishStatus.getSucceeded());
                            return new TaskIterator(NO_OP_TASK);
                        });

        // Default: validator approves all calls with an empty coerced-params map
        when(invokeValidator.validate(anyString(), anyString(), anyMap()))
                .thenReturn(new CommandInvokeValidator.Result.Ok(Map.of()));

        transport = new InMemoryTransport();
        transport.startServer(
                "test",
                "1.0",
                List.of(
                        new CommandGatewayInvokeTool(
                                        availableCommands,
                                        syncTaskManager,
                                        commandExecutorTaskFactory,
                                        invokeValidator)
                                .toSpec()));
    }

    @After
    public void tearDown() throws Exception {
        transport.close();
        mocks.close();
    }

    @Test
    public void invoke_validCommand_returnsSuccess() throws Exception {
        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(invokeCall("network select", "{\"network\":\"current\"}"));
        transport.await();

        JsonNode response = lastResponse();
        JsonNode content = response.at("/result/structuredContent");
        assertTrue(content.path("success").asBoolean());
        assertEquals("{\"selected\":[]}", content.path("result").asText());
    }

    @Test
    public void invoke_missingCommandKey_returnsError() throws Exception {
        String call =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"command_gateway_invoke\","
                        + "\"arguments\":{\"inputParams\":{}}}}";
        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(call);
        transport.await();

        assertTrue(lastResponse().at("/result/isError").asBoolean());
    }

    @Test
    public void invoke_unknownCommandKey_returnsError() throws Exception {
        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(invokeCall("bogus command", "{}"));
        transport.await();

        JsonNode response = lastResponse();
        assertTrue(response.at("/result/isError").asBoolean());
    }

    @Test
    public void invoke_missingRequiredParam_returnsError() throws Exception {
        when(invokeValidator.validate(anyString(), anyString(), anyMap()))
                .thenReturn(
                        new CommandInvokeValidator.Result.Failure(
                                "network",
                                "required parameter is missing (expected type: string)"));

        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(invokeCall("network select", "{\"nodeList\":\"all\"}"));
        transport.await();

        JsonNode response = lastResponse();
        assertTrue(response.at("/result/isError").asBoolean());
        String msg = response.at("/result/content/0/text").asText();
        assertTrue(msg.contains("required parameter is missing"));
        assertTrue(msg.contains("network"));
    }

    @Test
    public void invoke_unknownParam_returnsError() throws Exception {
        when(invokeValidator.validate(anyString(), anyString(), anyMap()))
                .thenReturn(
                        new CommandInvokeValidator.Result.Failure(
                                "unknownParam",
                                "unknown parameter \u2014 valid parameters: network, nodeList"));

        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(invokeCall("network select", "{\"unknownParam\":\"value\"}"));
        transport.await();

        JsonNode response = lastResponse();
        assertTrue(response.at("/result/isError").asBoolean());
        String msg = response.at("/result/content/0/text").asText();
        assertTrue(msg.contains("unknown parameter"));
        assertTrue(msg.contains("unknownParam"));
    }

    @Test
    public void invoke_invalidCommandKeyFormat_returnsError() throws Exception {
        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(invokeCall("badformat", "{}"));
        transport.await();

        assertTrue(lastResponse().at("/result/isError").asBoolean());
    }

    @Test
    public void invoke_taskFinishFailed_returnsFailure() throws Exception {
        // Override factory to simulate task failure
        when(commandExecutorTaskFactory.createTaskIterator(
                        anyString(), anyString(), anyMap(), any(TaskObserver.class)))
                .thenAnswer(
                        inv -> {
                            TaskObserver observer = inv.getArgument(3);
                            observer.allFinished(
                                    FinishStatus.newFailed(
                                            NO_OP_TASK, new Exception("cmd failed")));
                            return new TaskIterator(NO_OP_TASK);
                        });

        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(invokeCall("network select", "{}"));
        transport.await();

        JsonNode response = lastResponse();
        assertTrue(response.at("/result/isError").asBoolean());
        String msg = response.at("/result/content/0/text").asText();
        assertTrue(msg.contains("cmd failed"));
    }

    @Test
    public void invoke_taskFinishCancelled_returnsValidationHint() throws Exception {
        when(commandExecutorTaskFactory.createTaskIterator(
                        anyString(), anyString(), anyMap(), any(TaskObserver.class)))
                .thenAnswer(
                        inv -> {
                            TaskObserver observer = inv.getArgument(3);
                            observer.allFinished(FinishStatus.newCancelled(NO_OP_TASK));
                            return new TaskIterator(NO_OP_TASK);
                        });

        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(invokeCall("network select", "{}"));
        transport.await();

        JsonNode response = lastResponse();
        assertTrue(response.at("/result/isError").asBoolean());
        String msg = response.at("/result/content/0/text").asText();
        assertTrue(msg.contains("validation"));
        assertTrue(msg.contains("command_gateway_get"));
    }

    @Test
    public void invoke_syncTaskManagerException_returnsErrorWithMessage() throws Exception {
        doAnswer(
                        inv -> {
                            TaskObserver observer = inv.getArgument(1);
                            observer.allFinished(
                                    FinishStatus.newFailed(
                                            NO_OP_TASK, new RuntimeException("disk full")));
                            return null;
                        })
                .when(syncTaskManager)
                .execute(any(TaskIterator.class), any(TaskObserver.class));

        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(invokeCall("network select", "{}"));
        transport.await();

        JsonNode response = lastResponse();
        assertTrue(response.at("/result/isError").asBoolean());
        String msg = response.at("/result/content/0/text").asText();
        assertTrue(msg.contains("disk full"));
    }

    @Test
    public void invoke_failedStatus_exceptionChain_includedInMessage() throws Exception {
        when(commandExecutorTaskFactory.createTaskIterator(
                        anyString(), anyString(), anyMap(), any(TaskObserver.class)))
                .thenAnswer(
                        inv -> {
                            TaskObserver observer = inv.getArgument(3);
                            observer.allFinished(
                                    FinishStatus.newFailed(
                                            NO_OP_TASK,
                                            new RuntimeException(
                                                    "outer", new IllegalStateException("inner"))));
                            return new TaskIterator(NO_OP_TASK);
                        });

        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(invokeCall("network select", "{}"));
        transport.await();

        String msg = lastResponse().at("/result/content/0/text").asText();
        assertTrue(msg.contains("outer"));
        assertTrue(msg.contains("inner"));
    }

    @Test
    public void invoke_emptyInputParams_succeeds() throws Exception {
        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(invokeCall("network select", "{}"));
        transport.await();

        JsonNode response = lastResponse();
        assertTrue(response.at("/result/structuredContent/success").asBoolean());
    }

    @Test
    public void invoke_validatorOk_coercedParamsForwardedToCreateTaskIterator() throws Exception {
        Map<String, Object> coerced = new LinkedHashMap<>();
        coerced.put("count", 5);
        when(invokeValidator.validate(anyString(), anyString(), anyMap()))
                .thenReturn(new CommandInvokeValidator.Result.Ok(coerced));

        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(invokeCall("network select", "{\"count\":\"5\"}"));
        transport.await();

        assertTrue(lastResponse().at("/result/structuredContent/success").asBoolean());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(commandExecutorTaskFactory)
                .createTaskIterator(
                        eq("network"), eq("select"), captor.capture(), any(TaskObserver.class));
        assertEquals(Integer.valueOf(5), captor.getValue().get("count"));
    }

    @Test
    public void invoke_validatorFailure_returnsErrorWithParamAndReason() throws Exception {
        when(invokeValidator.validate(anyString(), anyString(), anyMap()))
                .thenReturn(
                        new CommandInvokeValidator.Result.Failure(
                                "count", "expected integer, cannot parse 'abc'"));

        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(invokeCall("network select", "{\"count\":\"abc\"}"));
        transport.await();

        JsonNode response = lastResponse();
        assertTrue(response.at("/result/isError").asBoolean());
        String msg = response.at("/result/content/0/text").asText();
        assertTrue(msg.contains("count"));
        assertTrue(msg.contains("expected integer"));
    }

    @Test
    public void invoke_validatorOk_booleanCoercion_succeeds() throws Exception {
        Map<String, Object> coerced = new LinkedHashMap<>();
        coerced.put("verbose", Boolean.TRUE);
        when(invokeValidator.validate(anyString(), anyString(), anyMap()))
                .thenReturn(new CommandInvokeValidator.Result.Ok(coerced));

        transport.send(INIT_REQUEST);
        transport.send(INITIALIZED_NOTIFICATION);
        transport.send(invokeCall("network select", "{\"verbose\":\"true\"}"));
        transport.await();

        assertTrue(lastResponse().at("/result/structuredContent/success").asBoolean());
    }

    // -- Helpers --------------------------------------------------------------

    private static String invokeCall(String commandKey, String inputParamsJson) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"command_gateway_invoke\","
                + "\"arguments\":{\"commandKey\":\""
                + commandKey
                + "\",\"inputParams\":"
                + inputParamsJson
                + "}}}";
    }

    private JsonNode lastResponse() throws Exception {
        String[] lines = transport.getResponse().split("\n");
        return MAPPER.readTree(lines[lines.length - 1]);
    }
}
