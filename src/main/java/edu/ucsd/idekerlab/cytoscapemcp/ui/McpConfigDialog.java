package edu.ucsd.idekerlab.cytoscapemcp.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Non-modal dialog showing the real-time MCP server status and the contents of
 * AgentConfiguration.md rendered as HTML.
 *
 * <p>Size: 80% × 70% of the parent frame, centered on it. A {@link ScheduledExecutorService} polls
 * the server via a live {@link McpLivenessProbe} every 5 seconds while the dialog is open, updating
 * the status label on the EDT.
 */
public class McpConfigDialog extends JDialog {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpConfigDialog.class);

    private final McpLivenessProbe probe;
    private final int port;
    private final JLabel statusLabel;
    private ScheduledExecutorService scheduler;

    public McpConfigDialog(JFrame parent, McpLivenessProbe probe, int port) {
        super(parent, "MCP Server", false); // non-modal
        this.probe = probe;
        this.port = port;

        // Size: 80% × 70% of parent frame, or sensible fallback if no parent
        int w = parent != null ? (int) (parent.getWidth() * 0.80) : 800;
        int h = parent != null ? (int) (parent.getHeight() * 0.70) : 600;
        setSize(w, h);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        // --- NORTH: status label ---
        statusLabel = new JLabel();
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(6, 10, 4, 10));
        statusPanel.add(statusLabel);
        add(statusPanel, BorderLayout.NORTH);

        // --- CENTER: markdown content rendered as HTML ---
        JEditorPane editorPane = new JEditorPane("text/html", loadMarkdownAsHtml());
        editorPane.setEditable(false);
        editorPane.setCaretPosition(0);
        // Suppress hyperlink navigation — no browser integration needed
        editorPane.addHyperlinkListener(e -> {});
        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        add(scrollPane, BorderLayout.CENTER);

        // --- SOUTH: close button ---
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setBorder(BorderFactory.createEmptyBorder(4, 10, 6, 10));
        btnPanel.add(closeBtn);
        add(btnPanel, BorderLayout.SOUTH);

        addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowOpened(WindowEvent we) {
                        startProbing();
                    }

                    @Override
                    public void windowClosed(WindowEvent we) {
                        stopProbing();
                    }
                });
    }

    private void startProbing() {
        scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "mcp-dialog-probe");
                            t.setDaemon(true);
                            return t;
                        });
        // Run immediately then every 5 s
        scheduler.scheduleWithFixedDelay(this::runProbe, 0, 5, TimeUnit.SECONDS);
    }

    private void stopProbing() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void runProbe() {
        boolean alive = probe.isAlive();
        SwingUtilities.invokeLater(() -> updateStatus(alive));
    }

    private void updateStatus(boolean running) {
        if (running) {
            statusLabel.setText("● MCP server running at http://localhost:" + port + "/mcp");
            statusLabel.setForeground(new Color(0, 140, 0));
        } else {
            statusLabel.setText("● MCP server stopped");
            statusLabel.setForeground(Color.RED);
        }
    }

    private String loadMarkdownAsHtml() {
        try (InputStream is = McpConfigDialog.class.getResourceAsStream("/AgentConfiguration.md")) {
            if (is == null) {
                LOGGER.warn("AgentConfiguration.md not found as classpath resource");
                return "<html><body><p><i>AgentConfiguration.md not found in JAR.</i></p></body>"
                        + "</html>";
            }
            String md = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            md = md.replace("{mcp.http_port}", String.valueOf(port));
            Parser parser = Parser.builder().build();
            HtmlRenderer renderer = HtmlRenderer.builder().build();
            Node doc = parser.parse(md);
            String html = renderer.render(doc);
            return "<html><head><style>"
                    + "body,p,li,td,th,em,strong,a{"
                    + "  font-family:Arial,sans-serif;font-size:13px;}"
                    + "h1,h2,h3,h4{"
                    + "  font-family:Arial,sans-serif;font-size:13px;font-weight:bold;"
                    + "  margin-top:8px;margin-bottom:2px;}"
                    + "pre,code{"
                    + "  font-family:Monospaced,monospace;font-size:12px;"
                    + "  background:#f4f4f4;}"
                    + "pre{display:block;padding:6px;margin:4px 0;}"
                    + "hr{border:none;border-top:1px solid #ccc;margin:8px 0;}"
                    + "table{border-collapse:collapse;margin:4px 0;}"
                    + "td,th{border:1px solid #ccc;padding:4px 8px;}"
                    + "</style></head>"
                    + "<body style='margin:10px'>"
                    + html
                    + "</body></html>";
        } catch (Exception e) {
            LOGGER.warn("Could not load AgentConfiguration.md", e);
            return "<html><body><p>Error loading configuration documentation.</p></body></html>";
        }
    }
}
