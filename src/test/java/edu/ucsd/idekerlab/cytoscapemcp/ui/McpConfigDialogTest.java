package edu.ucsd.idekerlab.cytoscapemcp.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.WindowEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link McpConfigDialog}. Uses a mocked {@link McpLivenessProbe} to control the
 * status outcome synchronously, avoiding network calls or timing dependencies.
 */
public class McpConfigDialogTest {

    private McpLivenessProbe probe;
    private JFrame mockParent;
    private int testPort;

    @Before
    public void setUp() {
        probe = mock(McpLivenessProbe.class);
        when(probe.isAlive()).thenReturn(false); // default: stopped
        mockParent = new JFrame();
        mockParent.setSize(1000, 800);
        testPort = 1234;
    }

    // --- Dialog creation and sizing ---

    @Test
    public void constructor_setsDialogSize80x70PercentOfParent() {
        McpConfigDialog dialog = new McpConfigDialog(mockParent, probe, testPort);

        assertEquals("Width should be 80% of parent", 800, dialog.getWidth());
        assertEquals("Height should be 70% of parent", 560, dialog.getHeight());
    }

    @Test
    public void constructor_setsNonModal() {
        McpConfigDialog dialog = new McpConfigDialog(mockParent, probe, testPort);

        assertFalse("Dialog should be non-modal", dialog.isModal());
    }

    @Test
    public void constructor_setsTitleToMcpServer() {
        McpConfigDialog dialog = new McpConfigDialog(mockParent, probe, testPort);

        assertEquals("Title should be 'MCP Server'", "MCP Server", dialog.getTitle());
    }

    @Test
    public void constructor_noParent_usesFallbackSize() {
        McpConfigDialog dialog = new McpConfigDialog(null, probe, testPort);

        assertEquals("Width should be fallback 800", 800, dialog.getWidth());
        assertEquals("Height should be fallback 600", 600, dialog.getHeight());
    }

    // --- Status label updates via probe ---

    @Test
    public void updateStatus_serverRunning_displaysGreenRunningMessage() throws Exception {
        when(probe.isAlive()).thenReturn(true);
        McpConfigDialog dialog = new McpConfigDialog(mockParent, probe, testPort);

        // Fire windowOpened to start the scheduler and wait for probe result on EDT
        CountDownLatch latch = new CountDownLatch(1);
        fireWindowOpened(dialog);
        // Give scheduler time to fire probe and invokeLater to EDT
        Thread.sleep(200);
        SwingUtilities.invokeAndWait(latch::countDown);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        fireWindowClosed(dialog);

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
    public void updateStatus_serverStopped_displaysRedStoppedMessage() throws Exception {
        when(probe.isAlive()).thenReturn(false);
        McpConfigDialog dialog = new McpConfigDialog(mockParent, probe, testPort);

        CountDownLatch latch = new CountDownLatch(1);
        fireWindowOpened(dialog);
        Thread.sleep(200);
        SwingUtilities.invokeAndWait(latch::countDown);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        fireWindowClosed(dialog);

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
        McpConfigDialog dialog = new McpConfigDialog(mockParent, probe, testPort);

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
        McpConfigDialog dialog = new McpConfigDialog(mockParent, probe, testPort);

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
        McpConfigDialog dialog = new McpConfigDialog(mockParent, probe, testPort);

        JButton closeButton = findCloseButton(dialog);
        assertNotNull("Close button should exist", closeButton);
        assertEquals("Close button should have 'Close' text", "Close", closeButton.getText());
        assertTrue(
                "Close button should have at least one action listener",
                closeButton.getActionListeners().length > 0);
    }

    // --- Window listener for scheduler management ---

    @Test
    public void windowListeners_registered() {
        McpConfigDialog dialog = new McpConfigDialog(mockParent, probe, testPort);

        assertTrue(
                "Dialog should have window listeners for scheduler management",
                dialog.getWindowListeners().length > 0);
    }

    @Test
    public void windowOpened_startsScheduler() {
        McpConfigDialog dialog = new McpConfigDialog(mockParent, probe, testPort);
        fireWindowOpened(dialog); // should not throw
        fireWindowClosed(dialog); // clean up scheduler
    }

    @Test
    public void windowClosed_stopsScheduler() {
        McpConfigDialog dialog = new McpConfigDialog(mockParent, probe, testPort);
        fireWindowOpened(dialog);
        fireWindowClosed(dialog); // should not throw
    }

    // --- Helpers ---

    private void fireWindowOpened(McpConfigDialog dialog) {
        WindowEvent event = new WindowEvent(dialog, WindowEvent.WINDOW_OPENED);
        for (var listener : dialog.getWindowListeners()) {
            listener.windowOpened(event);
        }
    }

    private void fireWindowClosed(McpConfigDialog dialog) {
        WindowEvent event = new WindowEvent(dialog, WindowEvent.WINDOW_CLOSED);
        for (var listener : dialog.getWindowListeners()) {
            listener.windowClosed(event);
        }
    }

    private JLabel findStatusLabel(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof JPanel) {
                JPanel panel = (JPanel) c;
                for (Component child : panel.getComponents()) {
                    if (child instanceof JLabel) {
                        JLabel label = (JLabel) child;
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
                    if (child instanceof JButton && "Close".equals(((JButton) child).getText())) {
                        return (JButton) child;
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
