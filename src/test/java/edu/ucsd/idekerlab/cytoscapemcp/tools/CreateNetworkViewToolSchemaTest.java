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

public class CreateNetworkViewToolSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void inputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(CreateNetworkViewTool.INPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
    }

    @Test
    public void inputSchema_typeIsObject() throws Exception {
        JsonNode schema = MAPPER.readTree(CreateNetworkViewTool.INPUT_SCHEMA);
        assertEquals("object", schema.get("type").asText());
    }

    @Test
    public void inputSchema_requiredContainsNetworkSuid() throws Exception {
        JsonNode required = MAPPER.readTree(CreateNetworkViewTool.INPUT_SCHEMA).get("required");
        assertNotNull(required);
        assertTrue(required.isArray());
        assertEquals(1, required.size());
        assertEquals("network_suid", required.get(0).asText());
    }

    @Test
    public void inputSchema_networkSuidIsInteger() throws Exception {
        JsonNode schema = MAPPER.readTree(CreateNetworkViewTool.INPUT_SCHEMA);
        assertEquals("integer", schema.at("/properties/network_suid/type").asText());
    }

    @Test
    public void inputSchema_networkSuidHasDescription() throws Exception {
        JsonNode schema = MAPPER.readTree(CreateNetworkViewTool.INPUT_SCHEMA);
        JsonNode desc = schema.at("/properties/network_suid/description");
        assertNotNull(desc);
        assertTrue(!desc.isMissingNode() && !desc.asText().isEmpty());
    }
}
