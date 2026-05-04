package edu.ucsd.idekerlab.cytoscapemcp.gateway;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.command.AvailableCommands;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.SavePolicy;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewFactory;

/**
 * Background scanner that reads all commands from {@link AvailableCommands} and upserts them into
 * {@link CommandService}.
 *
 * <p>{@link #scheduleScan()} is non-blocking and idempotent: if a scan is already running, it sets
 * a dirty flag so the active scan loops once more after finishing — no ETL event is silently
 * dropped even under rapid app install/uninstall bursts.
 *
 * <p>Before each scan body runs, a single scaffold {@link CyNetwork} (and view) is pre-registered
 * as the current network/view. This prevents {@code AvailableCommandsImpl.getArgs()} from creating
 * and destroying a scaffold for every command that is a {@code NetworkTaskFactory}-type, reducing
 * GUI events from ~1600 per scan down to ~6.
 */
public class CommandETLService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandETLService.class);

    /**
     * Cytoscape system namespaces whose commands are framework internals, not user-data operations.
     */
    private static final Set<String> SYSTEM_NAMESPACES = Set.of("cy", "command");

    /**
     * Name used by Cytoscape's {@code AvailableCommandsImpl} for its own scaffold network. Kept
     * identical so {@code CytoscapeDesktop.isCommandDocGenNetwork()} suppresses the starter-panel
     * hide side-effect that would otherwise fire on {@code NetworkAddedEvent}.
     */
    private static final String SCAFFOLD_NETWORK_NAME = "cy:command_documentation_generation";

    private final AvailableCommands availableCommands;
    private final CommandService commandService;
    private final CyNetworkFactory networkFactory;
    private final CyApplicationManager appMgr;
    private final CyNetworkManager networkMgr;
    private final CyNetworkViewFactory networkViewFactory;
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean scanInProgress = new AtomicBoolean(false);
    private final AtomicBoolean rescanRequested = new AtomicBoolean(false);

    public CommandETLService(
            AvailableCommands availableCommands,
            CommandService commandService,
            CyNetworkFactory networkFactory,
            CyApplicationManager appMgr,
            CyNetworkManager networkMgr,
            CyNetworkViewFactory networkViewFactory) {
        this.availableCommands = availableCommands;
        this.commandService = commandService;
        this.networkFactory = networkFactory;
        this.appMgr = appMgr;
        this.networkMgr = networkMgr;
        this.networkViewFactory = networkViewFactory;
    }

    /**
     * Non-blocking. Submits a scan if none is running; otherwise marks rescanRequested so the
     * running scan loops once more after it finishes.
     */
    public void scheduleScan() {
        if (!scanInProgress.compareAndSet(false, true)) {
            rescanRequested.set(true);
            return;
        }
        scanExecutor.submit(
                () -> {
                    try {
                        do {
                            rescanRequested.set(false);
                            performScan();
                        } while (rescanRequested.compareAndSet(true, false));
                    } catch (Exception e) {
                        LOGGER.error("ETL scan failed", e);
                    } finally {
                        scanInProgress.set(false);
                    }
                });
    }

    /** Stops the scan executor. Called from CyActivator.shutDown(). */
    public void shutdown() {
        scanExecutor.shutdownNow();
        LOGGER.info("CommandETLService executor shut down");
    }

    /**
     * Blocks until no scan is running, or the timeout elapses.
     *
     * @return true if idle before timeout, false if timed out
     */
    public boolean awaitIdle(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (scanInProgress.get()) {
            if (System.nanoTime() > deadline) return false;
            Thread.sleep(10);
        }
        return true;
    }

    // -- Private scan logic ---------------------------------------------------

    /**
     * Wraps {@link #doPerformScan()} with a pre-scan scaffold lifecycle. When no current network
     * exists, registers a single scaffold network+view before the scan starts so that all {@code
     * getArguments()} calls inside see a non-null current network and skip their own per-command
     * scaffold creation. Reduces GUI events from ~1600 per scan to ~6.
     */
    private void performScan() throws Exception {
        boolean scaffoldActive = false;
        CyNetwork scaffold = null;
        CyNetworkView scaffoldView = null;

        if (appMgr.getCurrentNetwork() == null) {
            scaffold = networkFactory.createNetwork(SavePolicy.DO_NOT_SAVE);
            scaffold.getRow(scaffold).set(CyNetwork.NAME, SCAFFOLD_NETWORK_NAME);
            networkMgr.addNetwork(scaffold, true);
            appMgr.setCurrentNetwork(scaffold);
            scaffoldActive = true;
        }
        if (scaffoldActive && appMgr.getCurrentNetworkView() == null) {
            scaffoldView = networkViewFactory.createNetworkView(scaffold);
            appMgr.setCurrentNetworkView(scaffoldView);
        }

        try {
            doPerformScan();
        } finally {
            if (scaffoldActive) {
                if (scaffoldView != null && appMgr.getCurrentNetworkView() == scaffoldView) {
                    appMgr.setCurrentNetworkView(null);
                }
                if (appMgr.getCurrentNetwork() == scaffold) {
                    appMgr.setCurrentNetwork(null);
                }
                networkMgr.destroyNetwork(scaffold);
            }
        }
    }

    private void doPerformScan() throws Exception {
        LOGGER.info("ETL scan started");
        Set<String> liveKeys = new HashSet<>();

        for (String namespace : availableCommands.getNamespaces()) {
            if (SYSTEM_NAMESPACES.contains(namespace)) continue;
            for (String commandName : availableCommands.getCommands(namespace)) {
                String commandKey = namespace + " " + commandName;
                try {
                    liveKeys.add(commandKey);

                    List<String> argNames = availableCommands.getArguments(namespace, commandName);
                    String description = availableCommands.getDescription(namespace, commandName);
                    String longDescription =
                            availableCommands.getLongDescription(namespace, commandName);
                    boolean supportsJson =
                            availableCommands.getSupportsJSON(namespace, commandName);
                    String exampleJson =
                            supportsJson
                                    ? availableCommands.getExampleJSON(namespace, commandName)
                                    : null;

                    String inputParamsText = buildInputParamsText(namespace, commandName, argNames);
                    String argNamesDelim = String.join("|", argNames);

                    Command cmd =
                            new Command(
                                    commandKey,
                                    namespace,
                                    commandName,
                                    description,
                                    longDescription,
                                    inputParamsText,
                                    argNamesDelim,
                                    exampleJson,
                                    supportsJson);

                    commandService.upsert(cmd);
                } catch (Exception e) {
                    LOGGER.warn(
                            "Skipping command {} — introspection error: {}",
                            commandKey,
                            e.getMessage());
                    LOGGER.debug("Introspection stack trace for {}", commandKey, e);
                }
            }
        }

        // Remove commands from uninstalled apps.
        Set<String> storedKeys = commandService.getAllCommandKeys();
        storedKeys.removeAll(liveKeys);
        for (String stale : storedKeys) {
            LOGGER.debug("Removing stale command: {}", stale);
            commandService.delete(stale);
        }

        LOGGER.info(
                "ETL scan complete: {} live commands, {} stale removed",
                liveKeys.size(),
                storedKeys.size());
    }

    private String buildInputParamsText(String ns, String cmd, List<String> argNames) {
        StringBuilder sb = new StringBuilder();
        for (String arg : argNames) {
            sb.append(arg).append(' ');
            String desc = availableCommands.getArgDescription(ns, cmd, arg);
            if (desc != null) sb.append(desc).append(' ');
        }
        return sb.toString().trim();
    }

    static String mapArgType(Class<?> type) {
        if (type == null) return "string";
        return switch (type.getSimpleName()) {
            case "Integer", "Long", "int", "long" -> "integer";
            case "Double", "Float", "double", "float" -> "number";
            case "Boolean", "boolean" -> "boolean";
            default -> "string";
        };
    }
}
