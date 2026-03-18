package edu.ucsd.idekerlab.cytoscapemcp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;

import edu.ucsd.idekerlab.cytoscapemcp.tools.DataColumn;

/**
 * Shared schema utilities for MCP tools.
 *
 * <ul>
 *   <li>{@link InputProperty} — one entry in an input-schema {@code properties} object.
 *   <li>{@link InputSchema} — full input schema object (type/required/properties), built via {@link
 *       InputSchema#builder()}.
 *   <li>{@link #toJson(Object)} — serialize a schema model instance to a JSON string.
 *   <li>{@link #toSchemaJson(Class)} — derive a JSON schema string from a Jackson-annotated class
 *       via victools.
 * </ul>
 *
 * <p><strong>Null vs Absent vs Empty rules for tool result json:</strong>
 *
 * <ol>
 *   <li><strong>Field not applicable to this response shape</strong> — pass {@code null} and
 *       annotate the record with {@code @JsonInclude(NON_NULL)} so the key is <em>absent</em> from
 *       the JSON. Absent key means "this concept does not apply here."
 *   <li><strong>List field that is applicable but has no items</strong> — pass {@code List.of()} so
 *       the key is present as {@code []}. An empty array means "I checked; nothing was found." A
 *       list field that was actually checked must never be {@code null}.
 * </ol>
 */
public class McpSchema {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpSchema() {}

    // -- Static helpers -------------------------------------------------------

    /** Serialize an object instance to a JSON string (used for {@code INPUT_SCHEMA}). */
    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Derive a JSON schema string FROM a Jackson-annotated class via victools (used for {@code
     * OUTPUT_SCHEMA}).
     */
    public static String toSchemaJson(Class<?> clazz) {
        SchemaGenerator generator =
                new SchemaGenerator(
                        new SchemaGeneratorConfigBuilder(
                                        SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                                .with(
                                        new JacksonModule(
                                                JacksonOption.FLATTENED_ENUMS_FROM_JSONPROPERTY))
                                .build());
        return generator.generateSchema(clazz).toPrettyString();
    }

    // -- InputProperty --------------------------------------------------------

    /**
     * Describes one property entry in an MCP tool input schema's {@code properties} object.
     * Supports nested {@code items} for array-typed properties and {@code enum} for allowed values.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InputProperty(
            @JsonProperty("type") String type,
            @JsonProperty("description") String description,
            @JsonProperty("items") InputProperty items,
            @JsonProperty("enum") List<String> allowedValues) {

        /** Convenience constructor for non-array, non-enum (leaf) properties. */
        public InputProperty(String type, String description) {
            this(type, description, null, null);
        }

        /** Convenience constructor for enum-constrained properties. */
        public InputProperty(String type, String description, List<String> allowedValues) {
            this(type, description, null, allowedValues);
        }
    }

    // -- InputSchema ----------------------------------------------------------

    /**
     * Represents the complete {@code inputSchema} JSON object required by the MCP {@code Tool}
     * builder. Build via {@link #builder()} and serialize with {@link McpSchema#toJson(Object)}.
     *
     * <p>Parameters declared via {@link Builder#dataColumn(String, String)} become {@code
     * array}-typed properties in the JSON schema whose {@code items} are derived automatically from
     * {@link DataColumn}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonSerialize(using = McpSchema.InputSchemaSerializer.class)
    public static class InputSchema {

        private final String type;
        private final List<String> required;
        private final Map<String, InputProperty> properties;
        private final Map<String, String> dataColumnDescriptions;

        /**
         * Jackson deserialization constructor — {@code dataColumnDescriptions} defaults to empty.
         */
        @JsonCreator
        public InputSchema(
                @JsonProperty("type") String type,
                @JsonProperty("required") List<String> required,
                @JsonProperty("properties") Map<String, InputProperty> properties) {
            this(type, required, properties, Map.of());
        }

        private InputSchema(
                String type,
                List<String> required,
                Map<String, InputProperty> properties,
                Map<String, String> dataColumnDescriptions) {
            this.type = type;
            this.required = required;
            this.properties = properties;
            this.dataColumnDescriptions = dataColumnDescriptions;
        }

        public String getType() {
            return type;
        }

        public List<String> getRequired() {
            return required;
        }

        public Map<String, InputProperty> getProperties() {
            return properties;
        }

        public Map<String, String> getDataColumnDescriptions() {
            return dataColumnDescriptions;
        }

        public static Builder builder() {
            return new Builder();
        }

        /** Fluent builder for {@link InputSchema}. */
        public static class Builder {

            private final String type = "object";
            private final List<String> required = new ArrayList<>();
            private final Map<String, InputProperty> properties = new LinkedHashMap<>();
            private final Map<String, String> dataColumnDescriptions = new LinkedHashMap<>();

            /** Mark one or more property keys as required. */
            public Builder required(String... keys) {
                Collections.addAll(required, keys);
                return this;
            }

            /** Add a single property entry. */
            public Builder property(String key, InputProperty prop) {
                properties.put(key, prop);
                return this;
            }

            /**
             * Declare a {@code DataColumn} array parameter.
             *
             * <p>The property is serialized as {@code {"type":"array","description":"...",
             * "items":<DataColumn schema>}}. The caller supplies only the per-parameter
             * description; the full item schema is derived automatically from {@link DataColumn}.
             */
            public Builder dataColumn(String name, String description) {
                dataColumnDescriptions.put(name, description);
                return this;
            }

            public InputSchema build() {
                return new InputSchema(
                        type,
                        List.copyOf(required),
                        Map.copyOf(properties),
                        Collections.unmodifiableMap(new LinkedHashMap<>(dataColumnDescriptions)));
            }
        }
    }

    // -- InputSchemaSerializer ------------------------------------------------

    /**
     * Custom Jackson serializer for {@link InputSchema} that merges {@code properties} entries
     * (serialized as {@link InputProperty} objects) with {@code dataColumnDescriptions} entries
     * (serialized as {@code array}-typed schema objects with {@link DataColumn} items).
     */
    public static class InputSchemaSerializer extends JsonSerializer<InputSchema> {

        private static final JsonNode DATA_COLUMN_ITEM_SCHEMA;

        static {
            try {
                DATA_COLUMN_ITEM_SCHEMA =
                        new ObjectMapper().readTree(McpSchema.toSchemaJson(DataColumn.class));
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        @Override
        public void serialize(InputSchema schema, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeStartObject();
            gen.writeStringField("type", schema.getType());

            if (schema.getRequired() != null) {
                gen.writeArrayFieldStart("required");
                for (String r : schema.getRequired()) {
                    gen.writeString(r);
                }
                gen.writeEndArray();
            }

            gen.writeObjectFieldStart("properties");

            // InputProperty entries (existing behavior)
            for (Map.Entry<String, InputProperty> e : schema.getProperties().entrySet()) {
                gen.writeObjectField(e.getKey(), e.getValue());
            }

            // DataColumn array entries
            for (Map.Entry<String, String> e : schema.getDataColumnDescriptions().entrySet()) {
                gen.writeObjectFieldStart(e.getKey());
                gen.writeStringField("type", "array");
                gen.writeStringField("description", e.getValue());
                gen.writeFieldName("items");
                gen.writeTree(DATA_COLUMN_ITEM_SCHEMA);
                gen.writeEndObject();
            }

            gen.writeEndObject(); // end properties
            gen.writeEndObject(); // end root
        }
    }
}
