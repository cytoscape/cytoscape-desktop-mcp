package edu.ucsd.idekerlab.cytoscapemcp.ui;

import java.awt.Color;
import java.awt.Font;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link McpStatusPanel}. McpStatusPanel extends JButton directly, so all button
 * properties are tested on the panel instance itself.
 */
public class McpStatusPanelTest {

    private AtomicBoolean serverRunning;
    private int testPort;
    private McpStatusPanel panel;

    @Before
    public void setUp() {
        serverRunning = new AtomicBoolean(false);
        testPort = 1234;
    }

    @After
    public void tearDown() {
        if (panel != null) {
            panel.removeNotify(); // stops the internal status timer
        }
    }

    // --- Button text, font and tooltip ---

    @Test
    public void constructor_buttonTextIsMcp() {
        panel = new McpStatusPanel(serverRunning::get, testPort);
        assertEquals("Button text should be 'MCP'", "MCP", panel.getText());
    }

    @Test
    public void constructor_setsBoldFontOn10Point() {
        panel = new McpStatusPanel(serverRunning::get, testPort);
        Font font = panel.getFont();
        assertTrue("Font should be bold", (font.getStyle() & Font.BOLD) != 0);
        assertEquals("Font size should be 10pt", 10.0f, font.getSize2D(), 0.01f);
    }

    @Test
    public void constructor_setsTooltip() {
        panel = new McpStatusPanel(serverRunning::get, testPort);
        assertEquals("MCP Server Configuration", panel.getToolTipText());
    }

    @Test
    public void constructor_disablesFocusPainting() {
        panel = new McpStatusPanel(serverRunning::get, testPort);
        assertFalse("Focus painting should be disabled", panel.isFocusPainted());
    }

    // --- Status color ---

    @Test
    public void constructor_serverRunning_foregroundIsGreen() {
        serverRunning.set(true);
        panel = new McpStatusPanel(serverRunning::get, testPort);
        Color fg = panel.getForeground();
        assertEquals("Red channel should be 0 when running", 0, fg.getRed());
        assertEquals("Green channel should be 140 when running", 140, fg.getGreen());
    }

    @Test
    public void constructor_serverStopped_foregroundIsRed() {
        serverRunning.set(false);
        panel = new McpStatusPanel(serverRunning::get, testPort);
        assertEquals("Foreground should be red when stopped", Color.RED, panel.getForeground());
    }

    @Test
    public void constructor_nullSupplier_foregroundIsRed() {
        panel = new McpStatusPanel(null, testPort);
        assertEquals(
                "Foreground should be red when supplier is null", Color.RED, panel.getForeground());
    }

    // --- Dialog smoke test ---

    @Test
    public void buttonClick_noExceptionThrown() {
        serverRunning.set(true);
        panel = new McpStatusPanel(serverRunning::get, testPort);
        panel.doClick(); // should not throw
    }
}
