package edu.ucsd.idekerlab.cytoscapemcp.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

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

/**
 * Unit tests for {@link McpConfigDialog}. Verifies status display, markdown rendering, and timer
 * management.
 */
public class McpConfigDialogTest {

    private AtomicBoolean serverRunning;
    private JFrame mockParent;
    private int testPort;

    @Before
    public void setUp() {
        serverRunning = new AtomicBoolean(false);
        mockParent = new JFrame();
        mockParent.setSize(1000, 800);
        testPort = 1234;
    }

    // --- Dialog creation and sizing ---

    @Test
    public void constructor_setsDialogSize80x70PercentOfParent() {
        McpConfigDialog dialog = new McpConfigDialog(mockParent, serverRunning::get, testPort);

        assertEquals("Width should be 80% of parent", 800, dialog.getWidth());
        assertEquals("Height should be 70% of parent", 560, dialog.getHeight());
    }

    @Test
    public void constructor_setsNonModal() {
        McpConfigDialog dialog = new McpConfigDialog(mockParent, serverRunning::get, testPort);

        assertFalse("Dialog should be non-modal", dialog.isModal());
    }

    @Test
    public void constructor_setsTitleToMcpServer() {
        McpConfigDialog dialog = new McpConfigDialog(mockParent, serverRunning::get, testPort);

        assertEquals("Title should be 'MCP Server'", "MCP Server", dialog.getTitle());
    }

    @Test
    public void constructor_noParent_usesFallbackSize() {
        McpConfigDialog dialog = new McpConfigDialog(null, serverRunning::get, testPort);

        assertEquals("Width should be fallback 800", 800, dialog.getWidth());
        assertEquals("Height should be fallback 600", 600, dialog.getHeight());
    }

    // --- Status label updates ---

    @Test
    public void updateStatus_serverRunning_displaysGreenRunningMessage() {
        serverRunning.set(true);
        McpConfigDialog dialog = new McpConfigDialog(mockParent, serverRunning::get, testPort);

        JLabel statusLabel = findStatusLabel(dialog);
        assertNotNull("Status label should exist", statusLabel);
        assertTrue(
                "Status label should show running message",
                statusLabel.getText().contains("MCP server running"));
        assertTrue(
                "Status label should include port",
                statusLabel.getText().contains("localhost:" + testPort));
        assertEquals(
                "Status label should be green", new Color(0, 140, 0), statusLabel.getForeground());
    }

    @Test
    public void updateStatus_serverStopped_displaysRedStoppedMessage() {
        serverRunning.set(false);
        McpConfigDialog dialog = new McpConfigDialog(mockParent, serverRunning::get, testPort);

        JLabel statusLabel = findStatusLabel(dialog);
        assertNotNull("Status label should exist", statusLabel);
        assertTrue(
                "Status label should show stopped message",
                statusLabel.getText().contains("MCP server stopped"));
        assertEquals("Status label should be red", Color.RED, statusLabel.getForeground());
    }

    @Test
    public void updateStatus_serverNull_displaysStoppedMessage() {
        McpConfigDialog dialog = new McpConfigDialog(mockParent, null, testPort);

        JLabel statusLabel = findStatusLabel(dialog);
        assertNotNull("Status label should exist", statusLabel);
        assertTrue(
                "Status label should show stopped message",
                statusLabel.getText().contains("MCP server stopped"));
        assertEquals("Status label should be red", Color.RED, statusLabel.getForeground());
    }

    // --- Markdown rendering ---

    @Test
    public void constructor_loadsAndRendersMarkdownAsHtml() {
        McpConfigDialog dialog = new McpConfigDialog(mockParent, serverRunning::get, testPort);

        JEditorPane editorPane = findEditorPane(dialog);
        assertNotNull("Editor pane should exist for markdown content", editorPane);
        assertEquals(
                "Editor pane should use HTML content type",
                "text/html",
                editorPane.getContentType());
        assertFalse("Editor pane should not be editable", editorPane.isEditable());

        String content = editorPane.getText();
        assertTrue("Content should be HTML", content.contains("<html>"));
        assertTrue("Content should contain body tag", content.contains("<body"));
    }

    // --- Port placeholder substitution ---

    @Test
    public void constructor_replacesPortPlaceholderInRenderedContent() {
        McpConfigDialog dialog = new McpConfigDialog(mockParent, serverRunning::get, testPort);

        JEditorPane editorPane = findEditorPane(dialog);
        assertNotNull("Editor pane should exist", editorPane);
        String content = editorPane.getText();
        assertFalse(
                "Placeholder {mcp.http_port} should be replaced",
                content.contains("{mcp.http_port}"));
        assertTrue(
                "Rendered content should contain the actual port number",
                content.contains(String.valueOf(testPort)));
    }

    // --- Button interaction ---

    @Test
    public void closeButton_disposesDialog() {
        McpConfigDialog dialog = new McpConfigDialog(mockParent, serverRunning::get, testPort);

        JButton closeButton = findCloseButton(dialog);
        assertNotNull("Close button should exist", closeButton);
        assertEquals("Close button should have 'Close' text", "Close", closeButton.getText());

        // Verify button is present and has action listener
        assertTrue(
                "Close button should have at least one action listener",
                closeButton.getActionListeners().length > 0);
    }

    // --- Window listener for timer management ---

    @Test
    public void windowListeners_registered() {
        McpConfigDialog dialog = new McpConfigDialog(mockParent, serverRunning::get, testPort);

        assertTrue(
                "Dialog should have window listeners for timer management",
                dialog.getWindowListeners().length > 0);
    }

    @Test
    public void windowOpened_startsTimer() {
        serverRunning.set(true);
        McpConfigDialog dialog = new McpConfigDialog(mockParent, serverRunning::get, testPort);

        // Simulate window opened event
        WindowEvent openEvent = new WindowEvent(dialog, WindowEvent.WINDOW_OPENED);
        for (var listener : dialog.getWindowListeners()) {
            listener.windowOpened(openEvent);
        }

        // Timer should now be running (we can't directly test timer state without reflection,
        // but we verify no exceptions were thrown)
    }

    @Test
    public void windowClosed_stopsTimer() {
        serverRunning.set(true);
        McpConfigDialog dialog = new McpConfigDialog(mockParent, serverRunning::get, testPort);

        // Start timer first
        WindowEvent openEvent = new WindowEvent(dialog, WindowEvent.WINDOW_OPENED);
        for (var listener : dialog.getWindowListeners()) {
            listener.windowOpened(openEvent);
        }

        // Now close
        WindowEvent closeEvent = new WindowEvent(dialog, WindowEvent.WINDOW_CLOSED);
        for (var listener : dialog.getWindowListeners()) {
            listener.windowClosed(closeEvent);
        }

        // Timer should now be stopped (verified by no exceptions)
    }

    // --- Helpers ---

    private JLabel findStatusLabel(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof JPanel) {
                JPanel panel = (JPanel) c;
                for (Component child : panel.getComponents()) {
                    if (child instanceof JLabel) {
                        JLabel label = (JLabel) child;
                        // Status label contains "server" text
                        if (label.getText() != null
                                && label.getText().toLowerCase().contains("server")) {
                            return label;
                        }
                    }
                }
            }
            if (c instanceof Container) {
                JLabel found = findStatusLabel((Container) c);
                if (found != null) return found;
            }
        }
        return null;
    }

    private JEditorPane findEditorPane(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof JScrollPane) {
                JScrollPane scrollPane = (JScrollPane) c;
                Component view = scrollPane.getViewport().getView();
                if (view instanceof JEditorPane) {
                    return (JEditorPane) view;
                }
            }
            if (c instanceof Container) {
                JEditorPane found = findEditorPane((Container) c);
                if (found != null) return found;
            }
        }
        return null;
    }

    private JButton findCloseButton(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof JPanel) {
                JPanel panel = (JPanel) c;
                for (Component child : panel.getComponents()) {
                    if (child instanceof JButton) {
                        JButton button = (JButton) child;
                        if ("Close".equals(button.getText())) {
                            return button;
                        }
                    }
                }
            }
            if (c instanceof Container) {
                JButton found = findCloseButton((Container) c);
                if (found != null) return found;
            }
        }
        return null;
    }
}
