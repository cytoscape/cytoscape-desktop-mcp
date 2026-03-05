package edu.ucsd.idekerlab.cytoscapemcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;

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
                                .with(new JacksonModule())
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
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InputSchema {

        @JsonProperty("type")
        private final String type;

        @JsonProperty("required")
        private final List<String> required;

        @JsonProperty("properties")
        private final Map<String, InputProperty> properties;

        @JsonCreator
        public InputSchema(
                @JsonProperty("type") String type,
                @JsonProperty("required") List<String> required,
                @JsonProperty("properties") Map<String, InputProperty> properties) {
            this.type = type;
            this.required = required;
            this.properties = properties;
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

        public static Builder builder() {
            return new Builder();
        }

        /** Fluent builder for {@link InputSchema}. */
        public static class Builder {

            private final String type = "object";
            private final List<String> required = new ArrayList<>();
            private final Map<String, InputProperty> properties = new LinkedHashMap<>();

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

            public InputSchema build() {
                return new InputSchema(type, List.copyOf(required), Map.copyOf(properties));
            }
        }
    }
}
