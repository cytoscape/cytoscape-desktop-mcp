package edu.ucsd.idekerlab.cytoscapemcp.tools;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * A tabular file column together with a data type inferred from sample values. Used as the element
 * type for node-attribute and edge-column parameters in load_network_view, and as the element type
 * in the get_file_columns response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataColumn(
        @JsonPropertyDescription("Column name exactly as it appears in the file header.")
                @JsonProperty("name")
                String name,
        @JsonPropertyDescription(
                        "Data type inferred from sample values in this column. "
                                + "Maps 1-to-1 onto Cytoscape table column types: "
                                + "string\u2192String, integer\u2192Integer (32-bit), long\u2192Long (64-bit), "
                                + "double\u2192Double (64-bit float), boolean\u2192Boolean. "
                                + "When passing this column to load_network_view, preserve this value so the "
                                + "Cytoscape table is created with the correct type instead of defaulting to string.")
                @JsonProperty("inferred_data_type")
                CyDataType inferredDataType) {

    /** The five Cytoscape column types, serialized as lower-case JSON strings. */
    public enum CyDataType {
        @JsonProperty("string")
        STRING,
        @JsonProperty("integer")
        INTEGER,
        @JsonProperty("long")
        LONG,
        @JsonProperty("double")
        DOUBLE,
        @JsonProperty("boolean")
        BOOLEAN;

        /** Deserializes from JSON string; unknown values silently map to STRING. */
        @JsonCreator
        public static CyDataType fromJson(String value) {
            if (value == null) return STRING;
            for (CyDataType t : values()) {
                if (t.name().equalsIgnoreCase(value)) return t;
            }
            return STRING;
        }

        /** Returns the corresponding Java Class for CyTable.createColumn(). */
        public Class<?> toClass() {
            return switch (this) {
                case INTEGER -> Integer.class;
                case LONG -> Long.class;
                case DOUBLE -> Double.class;
                case BOOLEAN -> Boolean.class;
                default -> String.class;
            };
        }
    }

    /**
     * Returns the Java Class to use for CyTable.createColumn(), defaulting to String.class if
     * inferredDataType is null (field absent from JSON input).
     */
    public Class<?> inferredTypeClass() {
        return inferredDataType == null ? String.class : inferredDataType.toClass();
    }
}
