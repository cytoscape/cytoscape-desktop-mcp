package edu.ucsd.idekerlab.cytoscapemcp.tools;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GetLoadedNetworkViewsToolSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void inputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(GetLoadedNetworkViewsTool.INPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
    }

    @Test
    public void inputSchema_typeIsObject() throws Exception {
        JsonNode schema = MAPPER.readTree(GetLoadedNetworkViewsTool.INPUT_SCHEMA);
        assertEquals("object", schema.get("type").asText());
    }

    @Test
    public void inputSchema_requiredIsEmpty() throws Exception {
        JsonNode required = MAPPER.readTree(GetLoadedNetworkViewsTool.INPUT_SCHEMA).get("required");
        assertNotNull(required);
        assertTrue(required.isArray());
        assertEquals(0, required.size());
    }

    @Test
    public void inputSchema_propertiesIsEmptyObject() throws Exception {
        JsonNode schema = MAPPER.readTree(GetLoadedNetworkViewsTool.INPUT_SCHEMA);
        JsonNode props = schema.get("properties");
        assertNotNull(props);
        assertTrue(props.isObject());
        assertEquals(0, props.size());
    }
}
