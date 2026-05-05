package edu.ucsd.idekerlab.cytoscapemcp.gateway;

/** Lucene binding record — one entry per registered Cytoscape Desktop command. */
public record Command(
        String commandKey,
        String namespace,
        String commandName,
        String description,
        String longDescription,
        String inputParamsText,
        String argNamesDelimited,
        String outputExampleJson,
        boolean supportsJson) {}
