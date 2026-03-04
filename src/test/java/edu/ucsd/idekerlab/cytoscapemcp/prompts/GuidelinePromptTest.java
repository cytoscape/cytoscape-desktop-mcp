package edu.ucsd.idekerlab.cytoscapemcp.prompts;

import org.junit.Before;
import org.junit.Test;

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

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

public class GuidelinePromptTest {

    private GuidelinePrompt prompt;

    @Before
    public void setUp() {
        prompt = new GuidelinePrompt();
    }

    // -------------------------------------------------------------------------
    // toSpec() structure
    // -------------------------------------------------------------------------

    @Test
    public void toSpec_isNotNull() {
        assertNotNull(prompt.toSpec());
    }

    @Test
    public void toSpec_promptName_isCytoscapeGuidelines() {
        McpServerFeatures.SyncPromptSpecification spec = prompt.toSpec();
        assertEquals("cytoscape-guidelines", spec.prompt().name());
    }

    @Test
    public void toSpec_promptDescription_isNotEmpty() {
        McpServerFeatures.SyncPromptSpecification spec = prompt.toSpec();
        assertFalse(spec.prompt().description().isBlank());
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
    public void handler_result_containsOneMessage() {
        GetPromptResult result = prompt.toSpec().promptHandler().apply(null, null);
        assertNotNull(result.messages());
        assertEquals(1, result.messages().size());
    }

    @Test
    public void handler_result_messageRole_isUser() {
        GetPromptResult result = prompt.toSpec().promptHandler().apply(null, null);
        PromptMessage message = result.messages().get(0);
        assertEquals(Role.USER, message.role());
    }

    @Test
    public void handler_result_messageContent_isTextContent() {
        GetPromptResult result = prompt.toSpec().promptHandler().apply(null, null);
        PromptMessage message = result.messages().get(0);
        assertNotNull(message.content());
        assertEquals(TextContent.class, message.content().getClass());
    }

    @Test
    public void handler_result_messageText_isNotEmpty() {
        GetPromptResult result = prompt.toSpec().promptHandler().apply(null, null);
        String text = ((TextContent) result.messages().get(0).content()).text();
        assertFalse("Guideline text must not be blank", text.isBlank());
    }

    // -------------------------------------------------------------------------
    // Guideline text content
    // -------------------------------------------------------------------------

    @Test
    public void guidelineText_containsRule1_connectivityFailure() {
        assertTrue(
                "RULE 1 section must be present",
                GuidelinePrompt.GUIDELINE_TEXT.contains("RULE 1"));
    }

    @Test
    public void guidelineText_containsRule2_formattedApplicationError() {
        assertTrue(
                "RULE 2 section must be present",
                GuidelinePrompt.GUIDELINE_TEXT.contains("RULE 2"));
    }

    @Test
    public void guidelineText_containsRule3_unexpectedServerError() {
        assertTrue(
                "RULE 3 section must be present",
                GuidelinePrompt.GUIDELINE_TEXT.contains("RULE 3"));
    }

    @Test
    public void guidelineText_containsConnectivityUserMessage() {
        assertTrue(
                "User-facing connectivity error message must be present",
                GuidelinePrompt.GUIDELINE_TEXT.contains(
                        "Please make sure your Cytoscape desktop is running"));
    }

    @Test
    public void guidelineText_containsUnexpectedErrorUserMessage() {
        assertTrue(
                "User-facing unexpected error message must be present",
                GuidelinePrompt.GUIDELINE_TEXT.contains(
                        "An unexpected error occurred in the Cytoscape MCP server"));
    }

    @Test
    public void guidelineText_mentionsLocalhost() {
        assertTrue(
                "Guideline must mention localhost transport constraint",
                GuidelinePrompt.GUIDELINE_TEXT.contains("localhost"));
    }
}
