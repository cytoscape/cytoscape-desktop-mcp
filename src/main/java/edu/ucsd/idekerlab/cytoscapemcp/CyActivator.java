package edu.ucsd.idekerlab.cytoscapemcp;

import java.awt.Component;
import java.awt.Container;
import java.util.Properties;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JToolBar;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.idekerlab.cytoscapemcp.tools.LoadNetworkViewTool;
import edu.ucsd.idekerlab.cytoscapemcp.ui.McpStatusPanel;

import org.cytoscape.app.event.AppsFinishedStartingEvent;
import org.cytoscape.app.event.AppsFinishedStartingListener;
import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.io.read.InputStreamTaskFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.property.AbstractConfigDirPropsReader;
import org.cytoscape.property.CyProperty;
import org.cytoscape.service.util.AbstractCyActivator;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.work.TaskManager;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;

public class CyActivator extends AbstractCyActivator {

    private static final Logger LOGGER = LoggerFactory.getLogger(CyActivator.class);

    private volatile org.eclipse.jetty.server.Server jettyServer;
    private volatile McpSyncServer mcpServer;
    private volatile BundleContext bundleContext;

    /**
     * Reads app properties from cytoscapemcp.props bundled in the JAR, then merges any user
     * overrides from the Cytoscape config directory. Properties are visible and editable via Edit >
     * Preferences > Properties under the "cytoscapemcp" group.
     */
    private static class PropsReader extends AbstractConfigDirPropsReader {
        PropsReader(String name, String fileName) {
            super(name, fileName, CyProperty.SavePolicy.CONFIG_DIR);
        }
    }

    @Override
    public void start(BundleContext bc) throws Exception {
        this.bundleContext = bc;
        LOGGER.info("Registering AppsFinishedStartingListener");

        // Defer MCP server init until all Cytoscape apps have finished starting.
        // This ensures the OSGi service registry is fully populated before we
        // look up services like LoadNetworkURLTaskFactory.
        AppsFinishedStartingListener listener =
                new AppsFinishedStartingListener() {
                    @Override
                    public void handleEvent(AppsFinishedStartingEvent event) {
                        LOGGER.info("AppsFinishedStartingEvent received — initializing MCP server");
                        try {
                            initializeApp();
                        } catch (Exception e) {
                            LOGGER.error("Failed to start Cytoscape MCP Server", e);
                        }
                    }
                };
        registerService(bc, listener, AppsFinishedStartingListener.class, new Properties());
    }

    @Override
    public void shutDown() {
        stopServers();
        super.shutDown();
    }

    private void initializeApp() {
        // Register and expose app properties so users can edit them via
        // Edit > Preferences > Properties > "cytoscapemcp" in Cytoscape.
        PropsReader propsReader = new PropsReader("cytoscapemcp", "cytoscapemcp.props");
        Properties propsServiceProps = new Properties();
        propsServiceProps.setProperty("cyPropertyName", "cytoscapemcp.props");
        registerAllServices(bundleContext, propsReader, propsServiceProps);

        @SuppressWarnings("unchecked")
        CyProperty<Properties> cyProperties =
                getService(bundleContext, CyProperty.class, "(cyPropertyName=cytoscapemcp.props)");

        int port;
        try {
            port =
                    Integer.parseInt(
                            cyProperties
                                    .getProperties()
                                    .getProperty("mcp.http_port", "9998")
                                    .trim());
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid mcp.http_port value; defaulting to 9998", e);
            port = 9998;
        }

        // Retrieve Cytoscape services needed by MCP tools.
        CyApplicationManager appManager = getService(bundleContext, CyApplicationManager.class);
        CyNetworkManager networkManager = getService(bundleContext, CyNetworkManager.class);
        CyNetworkViewManager viewManager = getService(bundleContext, CyNetworkViewManager.class);
        CySwingApplication cySwingApp = getService(bundleContext, CySwingApplication.class);

        @SuppressWarnings("unchecked")
        TaskManager<?, ?> taskManager = getService(bundleContext, TaskManager.class);

        InputStreamTaskFactory cxReaderFactory =
                getService(
                        bundleContext,
                        InputStreamTaskFactory.class,
                        "(id=cytoscapeCxNetworkReaderFactory)");

        startMcpServer(
                port,
                cyProperties,
                appManager,
                networkManager,
                viewManager,
                taskManager,
                cxReaderFactory);

        // Add the MCP toolbar button to the status bar on the Swing EDT, after the
        // Jetty server has successfully started and jettyServer field is populated.
        final int finalPort = port;
        final CySwingApplication finalSwingApp = cySwingApp;
        SwingUtilities.invokeLater(
                () -> {
                    JToolBar toolbar = finalSwingApp.getStatusToolBar();
                    if (toolbar != null) {
                        McpStatusPanel mcpPanel = new McpStatusPanel(jettyServer, finalPort);
                        boolean injected = injectIntoStatusBar(toolbar, mcpPanel);
                        if (!injected) {
                            // Fallback: prepend to statusToolBar directly
                            toolbar.add(mcpPanel, 0);
                            toolbar.revalidate();
                            toolbar.repaint();
                        }
                        LOGGER.info("MCP toolbar button added to status bar (injected={})", injected);
                    } else {
                        LOGGER.warn(
                                "MCP toolbar button: could not locate status toolbar in Cytoscape"
                                        + " UI");
                    }
                });
    }

    private void startMcpServer(
            int port,
            CyProperty<Properties> cyProperties,
            CyApplicationManager appManager,
            CyNetworkManager networkManager,
            CyNetworkViewManager viewManager,
            TaskManager<?, ?> taskManager,
            InputStreamTaskFactory cxReaderFactory) {

        // Streamable HTTP transport provider — part of the MCP SDK core (no Spring required).
        HttpServletStreamableServerTransportProvider transportProvider =
                HttpServletStreamableServerTransportProvider.builder()
                        .mcpEndpoint("/mcp")
                        .objectMapper(new ObjectMapper())
                        .build();

        // Jetty as the servlet container hosting the MCP HTTP servlet.
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setName("mcp-jetty");
        jettyServer = new org.eclipse.jetty.server.Server(threadPool);

        ServerConnector connector = new ServerConnector(jettyServer);
        connector.setPort(port);
        jettyServer.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(new ServletHolder(transportProvider), "/*");
        jettyServer.setHandler(context);

        try {
            jettyServer.start();
        } catch (Exception e) {
            LOGGER.error("Failed to start Jetty for MCP server on port {}", port, e);
            return;
        }

        // Build the MCP server. Version is read from the OSGi bundle manifest
        // at runtime so it stays in sync with the Gradle build version automatically.
        String bundleVersion = bundleContext.getBundle().getVersion().toString();
        mcpServer =
                McpServer.sync(transportProvider)
                        .serverInfo("cytoscape-mcp", bundleVersion)
                        .capabilities(ServerCapabilities.builder().tools(false).build())
                        .build();

        // Register MCP tools.
        LoadNetworkViewTool loadTool =
                new LoadNetworkViewTool(
                        cyProperties,
                        appManager,
                        networkManager,
                        viewManager,
                        taskManager,
                        cxReaderFactory);
        mcpServer.addTool(loadTool.toSpec());

        LOGGER.info(
                "Cytoscape MCP Server started — http://localhost:{}/mcp (version {})",
                port,
                bundleVersion);
    }

    /**
     * Injects {@code mcpPanel} into Cytoscape's status bar by rebuilding the parent JPanel's
     * GroupLayout. Based on {@code CytoscapeDesktop.setupStatusPanel()}, the original layout is:
     * <pre>[jobStatusPanel] [taskStatusPanel] [statusToolBar] [memStatusPanel]</pre>
     * We place McpStatusPanel first so the result is:
     * <pre>[MCP] [jobStatusPanel] [taskStatusPanel(expands)] [statusToolBar(expands)] [memStatusPanel]</pre>
     * taskStatusPanel keeps {@code Short.MAX_VALUE} so the task title label can expand freely.
     *
     * @return true if injection succeeded; false if the structure was unexpected (caller should
     *         fall back to appending to statusToolBar).
     */
    private boolean injectIntoStatusBar(JToolBar statusToolBar, McpStatusPanel mcpPanel) {
        Container parent = statusToolBar.getParent();
        if (parent == null) {
            LOGGER.warn("statusToolBar has no parent container");
            return false;
        }
        if (!(parent.getLayout() instanceof GroupLayout)) {
            LOGGER.warn("Parent layout is not GroupLayout ({}); cannot inject",
                    parent.getLayout().getClass().getSimpleName());
            return false;
        }

        Component[] comps = parent.getComponents();
        LOGGER.info("Status bar parent has {} components:", comps.length);
        int toolbarIdx = -1;
        for (int i = 0; i < comps.length; i++) {
            LOGGER.info("  [" + i + "]: " + comps[i].getClass().getSimpleName()
                    + " pref=" + comps[i].getPreferredSize());
            if (comps[i] == statusToolBar) toolbarIdx = i;
        }

        if (toolbarIdx < 1) {
            LOGGER.warn("statusToolBar found at unexpected index {}; cannot inject", toolbarIdx);
            return false;
        }

        // Add our button to the parent container before setting up the new layout.
        parent.add(mcpPanel);

        // Rebuild the GroupLayout with MCP first, preserving all original size constraints.
        // Original constraints from CytoscapeDesktop.setupStatusPanel():
        //   jobStatusPanel  : PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE
        //   taskStatusPanel : DEFAULT_SIZE,   DEFAULT_SIZE, Short.MAX_VALUE  ← must keep MAX to avoid clipping title
        //   statusToolBar   : DEFAULT_SIZE,   DEFAULT_SIZE, Short.MAX_VALUE
        //   memStatusPanel  : PREFERRED_SIZE, DEFAULT_SIZE, PREFERRED_SIZE
        GroupLayout layout = new GroupLayout(parent);
        layout.setAutoCreateContainerGaps(false);
        layout.setAutoCreateGaps(false);
        parent.setLayout(layout);

        boolean isWin = UIManager.getLookAndFeel().getClass().getName().contains("Windows");
        int vGap = isWin ? 5 : 0;

        // comps[0..toolbarIdx-2] = fixed components before taskPanel (e.g. jobStatusPanel)
        // comps[toolbarIdx-1]    = taskStatusPanel
        // comps[toolbarIdx]      = statusToolBar  (= the JToolBar we were given)
        // comps[toolbarIdx+1..]  = fixed components after toolBar (e.g. memStatusPanel)
        Component taskPanel = comps[toolbarIdx - 1];

        GroupLayout.SequentialGroup hGroup = layout.createSequentialGroup().addContainerGap();
        GroupLayout.ParallelGroup vGroup = layout.createParallelGroup(Alignment.CENTER, true);

        // MCP first — fixed (preferred) width.
        hGroup.addComponent(mcpPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
                GroupLayout.PREFERRED_SIZE);
        hGroup.addPreferredGap(ComponentPlacement.RELATED);
        vGroup.addComponent(mcpPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE,
                Short.MAX_VALUE);

        // Fixed components before taskPanel (e.g. jobStatusPanel).
        for (int i = 0; i < toolbarIdx - 1; i++) {
            hGroup.addComponent(comps[i], GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
                    GroupLayout.PREFERRED_SIZE);
            hGroup.addPreferredGap(ComponentPlacement.RELATED);
            vGroup.addComponent(comps[i], GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE,
                    Short.MAX_VALUE);
        }

        // taskStatusPanel: Short.MAX_VALUE so the internal task title label can expand freely.
        hGroup.addComponent(taskPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE,
                Short.MAX_VALUE);
        hGroup.addPreferredGap(ComponentPlacement.UNRELATED);
        vGroup.addComponent(taskPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE,
                Short.MAX_VALUE);

        // statusToolBar: Short.MAX_VALUE (originally expanding, currently empty).
        hGroup.addComponent(statusToolBar, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE,
                Short.MAX_VALUE);
        vGroup.addComponent(statusToolBar, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
                GroupLayout.PREFERRED_SIZE);

        // Fixed components after statusToolBar (e.g. memStatusPanel).
        for (int i = toolbarIdx + 1; i < comps.length; i++) {
            hGroup.addPreferredGap(ComponentPlacement.UNRELATED);
            hGroup.addComponent(comps[i], GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
                    GroupLayout.PREFERRED_SIZE);
            vGroup.addComponent(comps[i], GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE,
                    Short.MAX_VALUE);
        }

        hGroup.addContainerGap();
        layout.setHorizontalGroup(hGroup);
        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGap(vGap)
                .addGroup(vGroup)
                .addGap(vGap));

        parent.revalidate();
        parent.repaint();
        LOGGER.info("MCP panel injected into status bar GroupLayout at position 0 (toolbarIdx={})",
                toolbarIdx);
        return true;
    }

    private void stopServers() {
        if (mcpServer != null) {
            try {
                mcpServer.close();
                LOGGER.info("MCP server stopped");
            } catch (Exception e) {
                LOGGER.warn("Error stopping MCP server", e);
            }
        }
        if (jettyServer != null) {
            try {
                jettyServer.stop();
                LOGGER.info("Jetty server stopped");
            } catch (Exception e) {
                LOGGER.warn("Error stopping Jetty server", e);
            }
        }
    }
}
