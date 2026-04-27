package edu.ucsd.idekerlab.cytoscapemcp.gateway;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.cytoscape.command.AvailableCommands;

/**
 * Background scanner that reads all commands from {@link AvailableCommands} and upserts them into
 * {@link CommandService}.
 *
 * <p>{@link #scheduleScan()} is non-blocking and idempotent: if a scan is already running, it sets
 * a dirty flag so the active scan loops once more after finishing — no ETL event is silently
 * dropped even under rapid app install/uninstall bursts.
 */
public class CommandETLService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandETLService.class);

    private final AvailableCommands availableCommands;
    private final CommandService commandService;
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean scanInProgress = new AtomicBoolean(false);
    private final AtomicBoolean rescanRequested = new AtomicBoolean(false);
    private final ObjectMapper mapper = new ObjectMapper();

    public CommandETLService(AvailableCommands availableCommands, CommandService commandService) {
        this.availableCommands = availableCommands;
        this.commandService = commandService;
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
     * Blocks until no scan is running, or the timeout elapses. Package-private for testing.
     *
     * @return true if idle before timeout, false if timed out
     */
    boolean awaitIdle(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (scanInProgress.get()) {
            if (System.nanoTime() > deadline) return false;
            Thread.sleep(10);
        }
        return true;
    }

    // -- Private scan logic ---------------------------------------------------

    private void performScan() throws Exception {
        LOGGER.info("ETL scan started");
        Set<String> liveKeys = new HashSet<>();

        for (String namespace : availableCommands.getNamespaces()) {
            for (String commandName : availableCommands.getCommands(namespace)) {
                String commandKey = namespace + " " + commandName;
                liveKeys.add(commandKey);

                List<String> argNames = availableCommands.getArguments(namespace, commandName);
                String description = availableCommands.getDescription(namespace, commandName);
                String longDescription =
                        availableCommands.getLongDescription(namespace, commandName);
                boolean supportsJson = availableCommands.getSupportsJSON(namespace, commandName);
                String exampleJson =
                        supportsJson
                                ? availableCommands.getExampleJSON(namespace, commandName)
                                : null;

                String inputParamsJson = buildInputParamsJson(namespace, commandName, argNames);
                String inputParamsText = buildInputParamsText(namespace, commandName, argNames);
                String argNamesDelim = String.join("|", argNames);

                Command cmd =
                        new Command(
                                commandKey,
                                namespace,
                                commandName,
                                description,
                                longDescription,
                                inputParamsJson,
                                inputParamsText,
                                argNamesDelim,
                                exampleJson,
                                supportsJson);

                commandService.upsert(cmd);
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

    private String buildInputParamsJson(String ns, String cmd, List<String> argNames) {
        List<Map<String, Object>> params = new ArrayList<>();
        for (String arg : argNames) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", arg);
            Class<?> argType = availableCommands.getArgType(ns, cmd, arg);
            p.put("type", mapArgType(argType));
            p.put("required", availableCommands.getArgRequired(ns, cmd, arg));
            p.put("description", availableCommands.getArgDescription(ns, cmd, arg));
            p.put("tooltip", availableCommands.getArgTooltip(ns, cmd, arg));
            p.put("example", availableCommands.getArgExampleStringValue(ns, cmd, arg));
            params.add(p);
        }
        try {
            return mapper.writeValueAsString(params);
        } catch (Exception e) {
            return "[]";
        }
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
