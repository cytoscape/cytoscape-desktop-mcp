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

public class LoadNetworkViewToolSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void inputSchema_isValidJson() throws Exception {
        JsonNode schema = MAPPER.readTree(LoadNetworkViewTool.INPUT_SCHEMA);
        assertNotNull(schema);
        assertTrue(schema.isObject());
    }

    @Test
    public void inputSchema_typeIsObject() throws Exception {
        JsonNode schema = MAPPER.readTree(LoadNetworkViewTool.INPUT_SCHEMA);
        assertEquals("object", schema.get("type").asText());
    }

    @Test
    public void inputSchema_requiredContainsSource() throws Exception {
        JsonNode required = MAPPER.readTree(LoadNetworkViewTool.INPUT_SCHEMA).get("required");
        assertNotNull(required);
        assertTrue(required.isArray());
        assertEquals(1, required.size());
        assertEquals("source", required.get(0).asText());
    }

    @Test
    public void inputSchema_sourcePropertyHasThreeEnumValues() throws Exception {
        JsonNode sourceEnum =
                MAPPER.readTree(LoadNetworkViewTool.INPUT_SCHEMA).at("/properties/source/enum");
        assertFalse(sourceEnum.isMissingNode());
        assertTrue(sourceEnum.isArray());
        assertEquals(3, sourceEnum.size());
    }

    @Test
    public void inputSchema_allExpectedPropertiesPresent() throws Exception {
        JsonNode props = MAPPER.readTree(LoadNetworkViewTool.INPUT_SCHEMA).get("properties");
        assertNotNull(props);
        for (String key :
                new String[] {
                    "source",
                    "network_id",
                    "file_path",
                    "source_column",
                    "target_column",
                    "interaction_column",
                    "delimiter_char_code",
                    "use_header_row",
                    "excel_sheet",
                    "node_attributes_sheet",
                    "node_attributes_key_column",
                    "node_attributes_source_columns",
                    "node_attributes_target_columns"
                }) {
            assertFalse("Missing property: " + key, props.path(key).isMissingNode());
        }
    }

    @Test
    public void inputSchema_arrayPropertiesHaveStringItems() throws Exception {
        JsonNode schema = MAPPER.readTree(LoadNetworkViewTool.INPUT_SCHEMA);
        assertEquals(
                "string",
                schema.at("/properties/node_attributes_source_columns/items/type").asText());
        assertEquals(
                "string",
                schema.at("/properties/node_attributes_target_columns/items/type").asText());
    }

    @Test
    public void inputSchema_delimiterCharCodeIsInteger() throws Exception {
        JsonNode schema = MAPPER.readTree(LoadNetworkViewTool.INPUT_SCHEMA);
        assertEquals("integer", schema.at("/properties/delimiter_char_code/type").asText());
    }

    @Test
    public void inputSchema_useHeaderRowIsBoolean() throws Exception {
        JsonNode schema = MAPPER.readTree(LoadNetworkViewTool.INPUT_SCHEMA);
        assertEquals("boolean", schema.at("/properties/use_header_row/type").asText());
    }
}
