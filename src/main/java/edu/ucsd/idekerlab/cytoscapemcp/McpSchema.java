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
 *   <li>{@link ConditionalParamSpec} — descriptor for a conditional parameter wrapped as a {@code
 *       ConditionalParameter&lt;T&gt;} object in the schema, with {@code waived} and {@code
 *       parameter} sub-fields.
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

    // -- ConditionalParamSpec -------------------------------------------------

    /**
     * Describes the LLM-facing shape of a conditional parameter wrapped as a {@code
     * ConditionalParameter<T>} object in the JSON schema. The tool handler expects the LLM to
     * supply the param as a JSON object with two fields:
     *
     * <ul>
     *   <li>{@code waived} (boolean) — {@code true} means the user explicitly confirmed this param
     *       should be omitted; {@code false} means a value is being provided via {@code parameter}.
     *   <li>{@code parameter} ({@code T}) — the actual value; ignored when {@code waived=true}.
     * </ul>
     *
     * @param paramType JSON primitive type for the {@code parameter} field: {@code "string"},
     *     {@code "integer"}, {@code "boolean"}, or {@code "array"}. Use {@code null} when {@code
     *     isDataColumnArray=true} — the serializer builds the array+items schema automatically.
     * @param description description on the wrapper object (the per-param instruction shown to the
     *     LLM).
     * @param isDataColumnArray when {@code true}, the {@code parameter} field is rendered as a
     *     {@code DataColumn} array; {@code paramType} is ignored.
     */
    public record ConditionalParamSpec(
            String paramType, String description, boolean isDataColumnArray) {}

    /**
     * Runtime model for a deserialized {@code ConditionalParameter} wrapper. Represents the JSON
     * object {@code {"waived": <boolean>, "parameter": <value>}}.
     *
     * @param waived true if the user explicitly confirmed this parameter should be omitted
     * @param value the inner parameter value; may be null when waived is true
     */
    public record ConditionalParameter(boolean waived, Object value) {}

    /**
     * Discriminated container for a single tool input argument after type detection. Exactly one of
     * the two fields is non-null.
     *
     * <p>If the raw argument is a Map with both {@code "waived"} and {@code "parameter"} keys, it
     * is deserialized into {@link ConditionalParameter} and stored in {@code conditionalParameter}.
     * Otherwise the raw value is stored in {@code requiredParameter}.
     *
     * @param conditionalParameter the deserialized conditional wrapper, or null
     * @param requiredParameter the raw value for non-conditional params, or null
     */
    public record ToolInputParam(
            ConditionalParameter conditionalParameter, Object requiredParameter) {

        /** True if this argument was detected as a conditional parameter wrapper. */
        public boolean isConditional() {
            return conditionalParameter != null;
        }
    }

    /**
     * Standard description for the {@code waived} sub-field of every {@code
     * ConditionalParameter<T>} wrapper. Instructs the LLM that setting this field to {@code true}
     * requires explicit user confirmation and must never be assumed or defaulted.
     */
    public static final String WAIVED_FIELD_DESC =
            "Imperative: set to true only after direct user confirmation that this parameter"
                    + " should be intentionally omitted. Set to false when providing a value in"
                    + " the parameter field. Never assume or default — this requires explicit"
                    + " user confirmation or unambiguous contextual evidence in the current"
                    + " interaction.";

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
        private final Map<String, ConditionalParamSpec> conditionalParamSpecs;

        /**
         * Jackson deserialization constructor — {@code dataColumnDescriptions} and {@code
         * conditionalParamSpecs} default to empty.
         */
        @JsonCreator
        public InputSchema(
                @JsonProperty("type") String type,
                @JsonProperty("required") List<String> required,
                @JsonProperty("properties") Map<String, InputProperty> properties) {
            this(type, required, properties, Map.of(), Map.of());
        }

        private InputSchema(
                String type,
                List<String> required,
                Map<String, InputProperty> properties,
                Map<String, String> dataColumnDescriptions,
                Map<String, ConditionalParamSpec> conditionalParamSpecs) {
            this.type = type;
            this.required = required;
            this.properties = properties;
            this.dataColumnDescriptions = dataColumnDescriptions;
            this.conditionalParamSpecs = conditionalParamSpecs;
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

        public Map<String, ConditionalParamSpec> getConditionalParamSpecs() {
            return conditionalParamSpecs;
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
            private final Map<String, ConditionalParamSpec> conditionalParamSpecs =
                    new LinkedHashMap<>();

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

            /**
             * Declare a conditional scalar parameter wrapped as a {@code ConditionalParameter<T>}
             * object. The LLM must supply either {@code {waived:true}} (explicit omission confirmed
             * by the user) or {@code {waived:false, parameter:<value>}} (a value is provided). The
             * handler validates that the wrapper is present before extracting the inner value.
             *
             * @param name parameter key in the schema
             * @param paramType JSON primitive type for the {@code parameter} field: {@code
             *     "string"}, {@code "integer"}, or {@code "boolean"}
             * @param description description on the wrapper object (the LLM-facing instruction for
             *     this parameter)
             */
            public Builder conditionalParam(String name, String paramType, String description) {
                conditionalParamSpecs.put(
                        name, new ConditionalParamSpec(paramType, description, false));
                return this;
            }

            /**
             * Declare a conditional {@code DataColumn} array parameter wrapped as a {@code
             * ConditionalParameter<DataColumn[]>} object. Same wrapper semantics as {@link
             * #conditionalParam} but the {@code parameter} field is rendered as a {@code
             * DataColumn} array in the schema.
             *
             * @param name parameter key in the schema
             * @param description description on the wrapper object
             */
            public Builder conditionalDataColumnParam(String name, String description) {
                conditionalParamSpecs.put(name, new ConditionalParamSpec(null, description, true));
                return this;
            }

            public InputSchema build() {
                return new InputSchema(
                        type,
                        List.copyOf(required),
                        Map.copyOf(properties),
                        Collections.unmodifiableMap(new LinkedHashMap<>(dataColumnDescriptions)),
                        Collections.unmodifiableMap(new LinkedHashMap<>(conditionalParamSpecs)));
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

            // ConditionalParameter<T> wrapper entries
            for (Map.Entry<String, ConditionalParamSpec> e :
                    schema.getConditionalParamSpecs().entrySet()) {
                ConditionalParamSpec spec = e.getValue();
                gen.writeObjectFieldStart(e.getKey());
                gen.writeStringField("type", "object");
                gen.writeStringField("description", spec.description());
                gen.writeObjectFieldStart("properties");

                // waived field
                gen.writeObjectFieldStart("waived");
                gen.writeStringField("type", "boolean");
                gen.writeStringField("description", WAIVED_FIELD_DESC);
                gen.writeEndObject();

                // parameter field
                gen.writeObjectFieldStart("parameter");
                if (spec.isDataColumnArray()) {
                    gen.writeStringField("type", "array");
                    gen.writeFieldName("items");
                    gen.writeTree(DATA_COLUMN_ITEM_SCHEMA);
                } else {
                    gen.writeStringField("type", spec.paramType());
                }
                gen.writeEndObject();

                gen.writeEndObject(); // end properties
                gen.writeEndObject(); // end param object
            }

            gen.writeEndObject(); // end properties
            gen.writeEndObject(); // end root
        }
    }
}
