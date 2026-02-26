package edu.ucsd.idekerlab.cytoscapemcp.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

import org.eclipse.jetty.server.Server;

/**
 * The "MCP" button shown in Cytoscape's status bar. Extends {@link JButton} directly so it sizes
 * naturally alongside other status bar buttons (e.g. "Show Tasks").
 */
public class McpStatusPanel extends JButton {

    private static final Color COLOR_RUNNING = new Color(0, 140, 0);
    private static final Color COLOR_STOPPED = Color.RED;

    private final Server jettyServer;
    private final int port;
    private final Timer statusTimer;

    private McpConfigDialog dialog;

    public McpStatusPanel(Server jettyServer, int port) {
        super("MCP");
        this.jettyServer = jettyServer;
        this.port = port;

        setFont(getFont().deriveFont(Font.BOLD, 10f));
        setToolTipText("MCP Server Configuration");
        setFocusPainted(false);

        // Apply native grey border on macOS Aqua (matches TaskStatusBar / other status bar buttons).
        // On other LAFs use a 1px grey line border.
        if (isAquaLAF()) {
            putClientProperty("JButton.buttonType", "gradient");
        } else {
            setBorderPainted(true);
            setBorder(BorderFactory.createLineBorder(
                    UIManager.getColor("Separator.foreground"), 1));
            setContentAreaFilled(false);
            setOpaque(false);
        }

        addActionListener(e -> showDialog());

        // Set initial color immediately, then poll every 2 s.
        updateButtonColor();
        statusTimer = new Timer(2000, e -> updateButtonColor());
        statusTimer.start();
    }

    @Override
    public void removeNotify() {
        statusTimer.stop();
        super.removeNotify();
    }

    private void updateButtonColor() {
        boolean running = jettyServer != null && jettyServer.isRunning();
        setForeground(running ? COLOR_RUNNING : COLOR_STOPPED);
    }

    private void showDialog() {
        if (dialog == null || !dialog.isDisplayable()) {
            Window ancestor = SwingUtilities.getWindowAncestor(this);
            JFrame parent = ancestor instanceof JFrame ? (JFrame) ancestor : null;
            dialog = new McpConfigDialog(parent, jettyServer, port);
        }
        dialog.setVisible(true);
        dialog.toFront();
    }

    private static boolean isAquaLAF() {
        String lafClass = UIManager.getLookAndFeel().getClass().getName();
        return lafClass.contains("Aqua") || lafClass.contains("aqua");
    }
}
