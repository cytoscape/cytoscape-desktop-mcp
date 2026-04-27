package edu.ucsd.idekerlab.cytoscapemcp.gateway;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Unit tests for {@link CommandService} using a real in-memory Lucene index. */
public class CommandServiceTest {

    private CommandService service;

    @Before
    public void setUp() throws Exception {
        service = new CommandService();
    }

    @After
    public void tearDown() {
        if (service != null) service.close();
    }

    @Test
    public void upsertAndGetByKey_returnsStoredCommand() throws Exception {
        Command cmd =
                new Command(
                        "network select",
                        "network",
                        "select",
                        "Select nodes in the network",
                        "Extended description",
                        "[{\"name\":\"nodeList\"}]",
                        "nodeList Select nodes",
                        "nodeList",
                        "{\"selected\":[]}",
                        true);
        service.upsert(cmd);

        Optional<Command> result = service.getByKey("network select");
        assertTrue(result.isPresent());
        Command got = result.get();
        assertEquals("network select", got.commandKey());
        assertEquals("network", got.namespace());
        assertEquals("select", got.commandName());
        assertEquals("Select nodes in the network", got.description());
        assertEquals("Extended description", got.longDescription());
        assertTrue(got.supportsJson());
    }

    @Test
    public void upsertTwice_secondReplacesFirst() throws Exception {
        Command first =
                new Command(
                        "network select",
                        "network",
                        "select",
                        "Original description",
                        null,
                        "[]",
                        "",
                        "",
                        null,
                        false);
        Command second =
                new Command(
                        "network select",
                        "network",
                        "select",
                        "Updated description",
                        null,
                        "[]",
                        "",
                        "",
                        null,
                        true);
        service.upsert(first);
        service.upsert(second);

        Optional<Command> result = service.getByKey("network select");
        assertTrue(result.isPresent());
        assertEquals("Updated description", result.get().description());
        assertTrue(result.get().supportsJson());
    }

    @Test
    public void getByKey_unknownKey_returnsEmpty() throws Exception {
        Optional<Command> result = service.getByKey("does not exist");
        assertFalse(result.isPresent());
    }

    @Test
    public void delete_removesFromIndex() throws Exception {
        Command cmd =
                new Command(
                        "layout force-directed",
                        "layout",
                        "force-directed",
                        "Apply force-directed layout",
                        null,
                        "[]",
                        "",
                        "",
                        null,
                        false);
        service.upsert(cmd);
        assertTrue(service.getAllCommandKeys().contains("layout force-directed"));

        service.delete("layout force-directed");
        assertFalse(service.getAllCommandKeys().contains("layout force-directed"));
        assertFalse(service.getByKey("layout force-directed").isPresent());
    }

    @Test
    public void getAllCommandKeys_returnsAllInsertedKeys() throws Exception {
        service.upsert(makeCmd("network select"));
        service.upsert(makeCmd("network deselect"));
        service.upsert(makeCmd("layout force-directed"));

        Set<String> keys = service.getAllCommandKeys();
        assertEquals(3, keys.size());
        assertTrue(keys.contains("network select"));
        assertTrue(keys.contains("network deselect"));
        assertTrue(keys.contains("layout force-directed"));
    }

    @Test
    public void search_matchingQuery_returnsResults() throws Exception {
        service.upsert(
                new Command(
                        "network select",
                        "network",
                        "select",
                        "Select nodes in the network",
                        null,
                        "[]",
                        "nodeList Select nodes in network",
                        "nodeList",
                        null,
                        false));
        service.upsert(
                new Command(
                        "layout force-directed",
                        "layout",
                        "force-directed",
                        "Apply force-directed layout algorithm",
                        null,
                        "[]",
                        "networkSUID Apply layout",
                        "networkSUID",
                        null,
                        false));

        SearchResults results = service.search("select nodes", 10);
        assertTrue(results.success());
        assertFalse(results.results().isEmpty());
        assertEquals("network select", results.results().get(0).commandKey());
    }

    @Test
    public void search_malformedQuery_returnsFailure() {
        SearchResults results = service.search("field:*bad[", 5);
        assertFalse(results.success());
        assertNotNull(results.failure());
        assertTrue(results.results().isEmpty());
    }

    @Test
    public void search_emptyIndex_returnsEmptyResults() {
        SearchResults results = service.search("anything", 10);
        assertTrue(results.success());
        assertTrue(results.results().isEmpty());
    }

    @Test
    public void search_resultRow_inputsFromArgNamesField() throws Exception {
        service.upsert(
                new Command(
                        "table export",
                        "table",
                        "export",
                        "Export table to file",
                        null,
                        "[]",
                        "filePath outputFormat",
                        "filePath|outputFormat",
                        null,
                        false));

        SearchResults results = service.search("namespace:table", 10);
        assertTrue(results.success());
        assertFalse(results.results().isEmpty());
        List<String> inputs = results.results().get(0).inputs();
        assertEquals(2, inputs.size());
        assertTrue(inputs.contains("filePath"));
        assertTrue(inputs.contains("outputFormat"));
    }

    @Test
    public void search_resultRow_outputsFromStoredJson() throws Exception {
        service.upsert(
                new Command(
                        "network select",
                        "network",
                        "select",
                        "Select nodes",
                        null,
                        "[]",
                        "",
                        "",
                        "{\"nodeList\":[],\"edgeList\":[]}",
                        true));

        SearchResults results = service.search("namespace:network", 10);
        assertTrue(results.success());
        assertFalse(results.results().isEmpty());
        List<String> outputs = results.results().get(0).outputs();
        assertTrue(outputs.contains("nodeList"));
        assertTrue(outputs.contains("edgeList"));
    }

    // -- Helpers --------------------------------------------------------------

    private static Command makeCmd(String commandKey) {
        String[] parts = commandKey.split(" ", 2);
        return new Command(
                commandKey,
                parts[0],
                parts[1],
                "Description of " + commandKey,
                null,
                "[]",
                "",
                "",
                null,
                false);
    }
}
