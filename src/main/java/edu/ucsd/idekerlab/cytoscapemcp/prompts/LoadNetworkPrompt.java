package edu.ucsd.idekerlab.cytoscapemcp.prompts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * MCP prompt that guides an agent through the interactive network-loading wizard.
 *
 * <p>Registered as the {@code load_network} prompt. Agents that call {@code prompts/get} with this
 * name receive the full Phase 1 conversation script: source selection (NDEx or local file), file
 * format branching, tabular column mapping, and the final load-tool call.
 *
 * <p>The system prompt text is loaded from the classpath resource {@code
 * /LoadNetworkPromptSystem.prompt} to avoid embedding a large string literal in Java source.
 */
public class LoadNetworkPrompt {

    private static final String PROMPT_NAME = "load_network";
    private static final String PROMPT_TITLE = "Load Network on Cytoscape Desktop";
    private static final String PROMPT_DESCRIPTION =
            "Interactive prompt that guides you through loading a network into Cytoscape Desktop"
                    + " from NDEx (by UUID) or a local file (native network format or tabular data"
                    + " with column mapping). Creates a new root network and view and sets it as"
                    + " the current network.";
    private static final String GET_PROMPT_RESULT_DESCRIPTION = "Load Network";

    static final String SYSTEM_PROMPT_TEXT = loadSystemPromptText();

    private static String loadSystemPromptText() {
        try (InputStream is =
                LoadNetworkPrompt.class.getResourceAsStream("/LoadNetworkPromptSystem.prompt")) {
            if (is == null) {
                throw new IllegalStateException(
                        "Classpath resource /LoadNetworkPromptSystem.prompt not found");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load /LoadNetworkPromptSystem.prompt from classpath", e);
        }
    }

    private static final Prompt PROMPT =
            new Prompt(PROMPT_NAME, PROMPT_TITLE, PROMPT_DESCRIPTION, List.of());

    public McpServerFeatures.SyncPromptSpecification toSpec() {
        return new McpServerFeatures.SyncPromptSpecification(
                PROMPT,
                (exchange, request) ->
                        new GetPromptResult(
                                GET_PROMPT_RESULT_DESCRIPTION,
                                List.of(
                                        new PromptMessage(
                                                Role.ASSISTANT,
                                                new TextContent(SYSTEM_PROMPT_TEXT)))));
    }
}
