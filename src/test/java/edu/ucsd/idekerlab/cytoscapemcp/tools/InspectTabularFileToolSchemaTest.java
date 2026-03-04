package edu.ucsd.idekerlab.cytoscapemcp.tools;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class InspectTabularFileToolSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void inputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(InspectTabularFileTool.INPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
    }

    @Test
    public void inputSchema_typeIsObject() throws Exception {
        JsonNode schema = MAPPER.readTree(InspectTabularFileTool.INPUT_SCHEMA);
        assertEquals("object", schema.get("type").asText());
    }

    @Test
    public void inputSchema_requiredContainsFilePath() throws Exception {
        JsonNode required = MAPPER.readTree(InspectTabularFileTool.INPUT_SCHEMA).get("required");
        assertNotNull(required);
        assertTrue(required.isArray());
        assertEquals(1, required.size());
        assertEquals("file_path", required.get(0).asText());
    }

    @Test
    public void inputSchema_filePathIsStringProperty() throws Exception {
        JsonNode schema = MAPPER.readTree(InspectTabularFileTool.INPUT_SCHEMA);
        assertEquals("string", schema.at("/properties/file_path/type").asText());
    }

    @Test
    public void inputSchema_filePathHasDescription() throws Exception {
        JsonNode schema = MAPPER.readTree(InspectTabularFileTool.INPUT_SCHEMA);
        JsonNode desc = schema.at("/properties/file_path/description");
        assertFalse(desc.isMissingNode());
        assertFalse(desc.asText().isEmpty());
    }
}
