package edu.ucsd.idekerlab.cytoscapemcp.prompts;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

public class LoadNetworkPromptTest {

    private LoadNetworkPrompt prompt;

    @Before
    public void setUp() {
        prompt = new LoadNetworkPrompt();
    }

    // -------------------------------------------------------------------------
    // toSpec() structure
    // -------------------------------------------------------------------------

    @Test
    public void toSpec_isNotNull() {
        assertNotNull(prompt.toSpec());
    }

    @Test
    public void toSpec_promptName_isLoadNetwork() {
        McpServerFeatures.SyncPromptSpecification spec = prompt.toSpec();
        assertEquals("load_network", spec.prompt().name());
    }

    @Test
    public void toSpec_promptTitle_isNotBlank() {
        McpServerFeatures.SyncPromptSpecification spec = prompt.toSpec();
        assertFalse("Prompt title must not be blank", spec.prompt().title().isBlank());
    }

    @Test
    public void toSpec_promptDescription_isNotBlank() {
        McpServerFeatures.SyncPromptSpecification spec = prompt.toSpec();
        assertFalse("Prompt description must not be blank", spec.prompt().description().isBlank());
    }

    @Test
    public void toSpec_promptArguments_isEmpty() {
        McpServerFeatures.SyncPromptSpecification spec = prompt.toSpec();
        assertNotNull(spec.prompt().arguments());
        assertEquals(0, spec.prompt().arguments().size());
    }

    @Test
    public void toSpec_promptHandler_isNotNull() {
        assertNotNull(prompt.toSpec().promptHandler());
    }

    // -------------------------------------------------------------------------
    // GetPromptResult structure
    // -------------------------------------------------------------------------

    @Test
    public void handler_result_isNotNull() {
        GetPromptResult result = prompt.toSpec().promptHandler().apply(null, null);
        assertNotNull(result);
    }

    @Test
    public void handler_result_description_isNotBlank() {
        GetPromptResult result = prompt.toSpec().promptHandler().apply(null, null);
        assertFalse(
                "GetPromptResult description must not be blank", result.description().isBlank());
    }

    @Test
    public void handler_result_containsOneMessage() {
        GetPromptResult result = prompt.toSpec().promptHandler().apply(null, null);
        assertNotNull(result.messages());
        assertEquals(1, result.messages().size());
    }

    @Test
    public void handler_result_messageRole_isAssistant() {
        GetPromptResult result = prompt.toSpec().promptHandler().apply(null, null);
        PromptMessage message = result.messages().get(0);
        assertEquals(Role.ASSISTANT, message.role());
    }

    @Test
    public void handler_result_messageContent_isTextContent() {
        GetPromptResult result = prompt.toSpec().promptHandler().apply(null, null);
        PromptMessage message = result.messages().get(0);
        assertNotNull(message.content());
        assertEquals(TextContent.class, message.content().getClass());
    }

    @Test
    public void handler_result_messageText_isNotBlank() {
        GetPromptResult result = prompt.toSpec().promptHandler().apply(null, null);
        String text = ((TextContent) result.messages().get(0).content()).text();
        assertFalse("System prompt text must not be blank", text.isBlank());
    }

    // -------------------------------------------------------------------------
    // System prompt text content
    // -------------------------------------------------------------------------

    @Test
    public void systemPromptText_containsStep1() {
        assertTrue("Must contain STEP 1", LoadNetworkPrompt.SYSTEM_PROMPT_TEXT.contains("STEP 1"));
    }

    @Test
    public void systemPromptText_containsStep1a_NDEx() {
        assertTrue(
                "Must contain STEP 1a-NDEx",
                LoadNetworkPrompt.SYSTEM_PROMPT_TEXT.contains("STEP 1a-NDEx"));
    }

    @Test
    public void systemPromptText_containsStep1a_File() {
        assertTrue(
                "Must contain STEP 1a-File",
                LoadNetworkPrompt.SYSTEM_PROMPT_TEXT.contains("STEP 1a-File"));
    }

    @Test
    public void systemPromptText_containsStep1b() {
        assertTrue(
                "Must contain STEP 1b", LoadNetworkPrompt.SYSTEM_PROMPT_TEXT.contains("STEP 1b"));
    }

    @Test
    public void systemPromptText_containsStep1_LOAD() {
        assertTrue(
                "Must contain STEP 1-LOAD",
                LoadNetworkPrompt.SYSTEM_PROMPT_TEXT.contains("STEP 1-LOAD"));
    }
}
