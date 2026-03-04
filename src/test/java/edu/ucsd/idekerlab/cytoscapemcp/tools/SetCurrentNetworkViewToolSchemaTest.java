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

public class SetCurrentNetworkViewToolSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void inputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(SetCurrentNetworkViewTool.INPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
    }

    @Test
    public void inputSchema_typeIsObject() throws Exception {
        JsonNode schema = MAPPER.readTree(SetCurrentNetworkViewTool.INPUT_SCHEMA);
        assertEquals("object", schema.get("type").asText());
    }

    @Test
    public void inputSchema_requiredContainsBothSuids() throws Exception {
        JsonNode required = MAPPER.readTree(SetCurrentNetworkViewTool.INPUT_SCHEMA).get("required");
        assertNotNull(required);
        assertTrue(required.isArray());
        assertEquals(2, required.size());
        String r0 = required.get(0).asText();
        String r1 = required.get(1).asText();
        assertTrue(
                (r0.equals("network_suid") && r1.equals("view_suid"))
                        || (r0.equals("view_suid") && r1.equals("network_suid")));
    }

    @Test
    public void inputSchema_networkSuidIsInteger() throws Exception {
        JsonNode schema = MAPPER.readTree(SetCurrentNetworkViewTool.INPUT_SCHEMA);
        assertEquals("integer", schema.at("/properties/network_suid/type").asText());
    }

    @Test
    public void inputSchema_viewSuidIsInteger() throws Exception {
        JsonNode schema = MAPPER.readTree(SetCurrentNetworkViewTool.INPUT_SCHEMA);
        assertEquals("integer", schema.at("/properties/view_suid/type").asText());
    }
}
