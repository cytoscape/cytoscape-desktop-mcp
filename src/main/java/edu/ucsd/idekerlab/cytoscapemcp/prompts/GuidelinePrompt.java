package edu.ucsd.idekerlab.cytoscapemcp.prompts;

import java.util.List;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * MCP prompt that communicates Cytoscape-wide guidelines to the agent.
 *
 * <p>Registered as the {@code cytoscape-guidelines} prompt. Agents that call {@code prompts/get}
 * with this name receive behavioral rules that apply globally to all Cytoscape tool interactions —
 * in particular, how to handle connectivity failures when Cytoscape Desktop is not running.
 */
public class GuidelinePrompt {

    private static final String PROMPT_NAME = "cytoscape-guidelines";
    private static final String PROMPT_TITLE = "Cytoscape Desktop Guidelines";
    private static final String PROMPT_DESCRIPTION =
            "General guidelines for using the Cytoscape Desktop MCP server";

    static final String GUIDELINE_TEXT =
            """
            This MCP server is hosted inside a running Cytoscape desktop application process \
            on the same machine as the agent. All tool calls use the MCP Streamable HTTP \
            transport bound to localhost — there is no remote MCP server.

            When any Cytoscape tool call fails, inspect the error before responding to the \
            user and apply exactly one of the three rules below:

            RULE 1 — TCP/HTTP connectivity failure
            If the error indicates a network-level problem reaching localhost (e.g. connection \
            refused, host unreachable, timeout, HTTP 503, or any socket-level failure):
              1. Do NOT show the raw technical error to the user.
              2. Do NOT retry the tool call.
              3. Show ONLY this message: 'Please make sure your Cytoscape desktop is running, \
            it is required for Cytoscape integration from this agent'

            RULE 2 — Formatted application error
            If the error response is a structured message describing real application feedback \
            (e.g. invalid parameters, business logic errors):
              1. Follow any next-step instructions contained in the error response.
              2. If no instructions are present, display the error message to the user \
            exactly as returned, without modification.

            RULE 3 — Unexpected server error
            If the error is an unformatted response (e.g. an HTTP 4xx/5xx with no structured \
            body, or a Java stack trace):
              1. Do NOT show the raw stack trace or HTTP status to the user.
              2. Show this message: 'An unexpected error occurred in the Cytoscape MCP server. \
            Check the MCP status indicator in the bottom toolbar of Cytoscape desktop to \
            confirm the server is operational.'
            """;

    private static final Prompt PROMPT =
            new Prompt(PROMPT_NAME, PROMPT_TITLE, PROMPT_DESCRIPTION, List.of());

    public McpServerFeatures.SyncPromptSpecification toSpec() {
        return new McpServerFeatures.SyncPromptSpecification(
                PROMPT,
                (exchange, request) ->
                        new GetPromptResult(
                                PROMPT_DESCRIPTION,
                                List.of(
                                        new PromptMessage(
                                                Role.ASSISTANT, new TextContent(GUIDELINE_TEXT)))));
    }
}
