package edu.ucsd.idekerlab.cytoscapemcp.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Window;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * The "MCP" button shown in Cytoscape's status bar. Extends {@link JButton} directly so it sizes
 * naturally alongside other status bar buttons (e.g. "Show Tasks").
 *
 * <p>Status color is determined by a live {@code tools/list} probe against the MCP server endpoint
 * via {@link McpLivenessProbe}. A single-threaded {@link ScheduledExecutorService} runs the probe
 * off the EDT every 5 seconds (with a 1-second initial delay), then marshals the color update back
 * to the EDT via {@link SwingUtilities#invokeLater}.
 */
public class McpStatusPanel extends JButton {

    private static final Color COLOR_RUNNING = new Color(0, 140, 0);
    private static final Color COLOR_STOPPED = Color.RED;

    private final McpLivenessProbe probe;
    private final int port;
    private final ScheduledExecutorService scheduler;

    private McpConfigDialog dialog;

    public McpStatusPanel(int port) {
        super("MCP");
        this.port = port;
        this.probe = new McpLivenessProbe(port);

        setFont(getFont().deriveFont(Font.BOLD, 10f));
        setToolTipText("MCP Server Configuration");
        setFocusPainted(false);

        if (isAquaLAF()) {
            putClientProperty("JButton.buttonType", "gradient");
        } else {
            setBorderPainted(true);
            setBorder(
                    BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground"), 1));
            setContentAreaFilled(false);
            setOpaque(false);
        }

        addActionListener(e -> showDialog());

        scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "mcp-status-probe");
                            t.setDaemon(true);
                            return t;
                        });
        scheduler.scheduleWithFixedDelay(this::runProbe, 1, 5, TimeUnit.SECONDS);
    }

    @Override
    public void removeNotify() {
        scheduler.shutdownNow();
        super.removeNotify();
    }

    private void runProbe() {
        boolean alive = probe.isAlive();
        SwingUtilities.invokeLater(() -> setForeground(alive ? COLOR_RUNNING : COLOR_STOPPED));
    }

    private void showDialog() {
        if (dialog == null || !dialog.isDisplayable()) {
            Window ancestor = SwingUtilities.getWindowAncestor(this);
            JFrame parent = ancestor instanceof JFrame ? (JFrame) ancestor : null;
            dialog = new McpConfigDialog(parent, probe, port);
        }
        dialog.setVisible(true);
        dialog.toFront();
    }

    private static boolean isAquaLAF() {
        String lafClass = UIManager.getLookAndFeel().getClass().getName();
        return lafClass.contains("Aqua") || lafClass.contains("aqua");
    }
}
