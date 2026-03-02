package edu.ucsd.idekerlab.cytoscapemcp.ui;

import java.awt.Font;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link McpStatusPanel}. McpStatusPanel extends JButton directly, so all button
 * properties are tested on the panel instance itself.
 *
 * <p>Color tests are omitted: status color is set asynchronously by {@link McpLivenessProbe} on a
 * background scheduler thread and cannot be asserted synchronously without introducing timing
 * dependencies. Probe behavior is covered by {@link McpConfigDialogTest} where a mock probe is
 * injectable.
 */
public class McpStatusPanelTest {

    private int testPort;
    private McpStatusPanel panel;

    @Before
    public void setUp() {
        testPort = 1234;
    }

    @After
    public void tearDown() {
        if (panel != null) {
            panel.removeNotify(); // shuts down the internal ScheduledExecutorService
        }
    }

    // --- Button text, font and tooltip ---

    @Test
    public void constructor_buttonTextIsMcp() {
        panel = new McpStatusPanel(testPort);
        assertEquals("Button text should be 'MCP'", "MCP", panel.getText());
    }

    @Test
    public void constructor_setsBoldFontOn10Point() {
        panel = new McpStatusPanel(testPort);
        Font font = panel.getFont();
        assertTrue("Font should be bold", (font.getStyle() & Font.BOLD) != 0);
        assertEquals("Font size should be 10pt", 10.0f, font.getSize2D(), 0.01f);
    }

    @Test
    public void constructor_setsTooltip() {
        panel = new McpStatusPanel(testPort);
        assertEquals("MCP Server Configuration", panel.getToolTipText());
    }

    @Test
    public void constructor_disablesFocusPainting() {
        panel = new McpStatusPanel(testPort);
        assertFalse("Focus painting should be disabled", panel.isFocusPainted());
    }

    // --- Dialog smoke test ---

    @Test
    public void buttonClick_noExceptionThrown() {
        panel = new McpStatusPanel(testPort);
        panel.doClick(); // should not throw
    }
}

