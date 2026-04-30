# Plan: Desktop Command Gateway Tools (Spec 05)

## Problem Statement

Cytoscape Desktop exposes several hundred commands registered by core and installed apps. LLMs must be able to **discover, inspect, and invoke** these commands without receiving a full command catalog up front (which would flood context with thousands of tokens). The solution provides three progressive MCP tools backed by an in-memory H2 database and a Lucene index:

1. **CommandGatewaySearchTool** — Lucene full-text search over indexed command metadata; returns ranked results with match scores.
2. **CommandGatewayGetTool** — Full schema retrieval for specific commands by key.
3. **CommandGatewayInvokeTool** — Validated execution of a command with JSON input/output.

Backing services:
- **`CommandService`** — DAO pattern managing H2 (structured data) + Lucene (full-text search).
- **`CommandETLService`** — Background scanner that uses `AvailableCommands` SDK API to populate `CommandService` and keep it fresh.
- **OSGi `BundleListener`** — Push detection of app install/uninstall events to trigger ETL rescans.

---

## Architecture Overview

```
                 ┌──────────────────────────────────────────────────────┐
                 │  Cytoscape Desktop                                    │
                 │  AvailableCommands (org.cytoscape.command)            │
                 │  CommandExecutorTaskFactory                           │
                 └───────────────────┬──────────────────────────────────┘
                                     │ OSGi service refs
            ┌────────────────────────▼───────────────────────────┐
            │  CommandETLService (scanner/ETL)                    │
            │  - scheduleScan()  async, single-thread pool        │
            │  - triggered: startup + OSGi BundleListener         │
            └────────────────────────┬───────────────────────────┘
                                     │ upsert / delete
            ┌────────────────────────▼───────────────────────────┐
            │  CommandService (DAO)                               │
            │  - H2 in-memory JDBC  (structured store / CRUD)     │
            │  - Lucene ByteBuffersDirectory  (FTS index)         │
            │  - search(query, max) → SearchResults               │
            │  - upsert(Command) / delete(key)                    │
            │  - getAllCommandKeys() → Set<String>                 │
            └────────────────────────┬───────────────────────────┘
                                     │
        ┌───────────────────┬────────┴──────────────┐
        │                   │                        │
 ┌──────▼──────┐   ┌────────▼────────┐   ┌──────────▼──────────┐
 │ SearchTool  │   │   GetTool       │   │   InvokeTool        │
 │ (search)    │   │ (AvailableCmd)  │   │ (cmd execution)     │
 └─────────────┘   └─────────────────┘   └─────────────────────┘
```

---

## New Package

All new classes live in:
```
edu.ucsd.idekerlab.cytoscapemcp.gateway
```

`build.gradle` BND `Private-Package` instruction must include this package.

---

## Part 1: Data Model Records

### `Command` record (H2 binding)

```java
package edu.ucsd.idekerlab.cytoscapemcp.gateway;

/**
 * Persistent command record — maps to a row in the COMMANDS H2 table
 * and to a Lucene Document.
 */
public record Command(
    String commandKey,        // PK: "namespace commandName", e.g. "network select"
    String namespace,         // e.g. "network"
    String commandName,       // e.g. "select"
    String description,       // short description from AvailableCommands.getDescription()
    String longDescription,   // from getLongDescription(); may be null
    String inputParamsJson,   // JSON array: [{name,type,required,description,tooltip,exampleValue}]
    String inputParamsText,   // flat concatenated text from all arg names+descriptions for Lucene
    String outputExampleJson,  // representative example JSON from getExampleJSON(); may be null
    boolean supportsJson      // from getSupportsJSON()
) {}
```

### `SearchResults` record (Tool #1 response)

```java
public record SearchResults(
    @JsonPropertyDescription("True when the search executed without error.")
    boolean success,

    @JsonPropertyDescription("When success is false, a human-readable description of the error. Null on success.")
    String failure,

    @JsonPropertyDescription("Ranked list of matching commands ordered by descending match score.")
    List<ResultRow> results
) {}
```

### `ResultRow` record

```java
public record ResultRow(
    @JsonPropertyDescription("Fully qualified command key in 'namespace command' format, e.g. 'network select'.")
    String commandKey,

    @JsonPropertyDescription("Lucene relevance score; higher means a closer match to the query.")
    float score,

    @JsonPropertyDescription("One-sentence summary of what the command does.")
    String summary,

    @JsonPropertyDescription("Names of input parameters accepted by this command.")
    List<String> inputs,

    @JsonPropertyDescription("Top-level output field names from this command's output model. Empty if command does not return JSON.")
    List<String> outputs
) {}
```

### `DesktopCommandsResponse` record (Tool #2 response)

```java
public record DesktopCommandsResponse(
    @JsonPropertyDescription("True when at least one command was found and returned.")
    boolean success,

    @JsonPropertyDescription("When success is false, a human-readable error. Null on success.")
    String failure,

    @JsonPropertyDescription("One entry per command key that was found in the desktop.")
    List<DesktopCommand> results
) {}
```

### `DesktopCommand` record

```java
public record DesktopCommand(
    @JsonPropertyDescription("Fully qualified command key in 'namespace command' format.")
    String commandKey,

    @JsonPropertyDescription("Command namespace, e.g. 'network', 'layout', 'table'.")
    String namespace,

    @JsonPropertyDescription("Command name within the namespace, e.g. 'select', 'force-directed'.")
    String commandName,

    @JsonPropertyDescription("Short description of the command.")
    String description,

    @JsonPropertyDescription("Extended description if provided by the command's author. May be null.")
    String longDescription,

    @JsonPropertyDescription("True if this command produces a JSON result.")
    boolean supportsJson,

    @JsonPropertyDescription("JSON Schema string for the input parameters. Use when invoking this command.")
    String inputSchema,

    @JsonPropertyDescription("Representative JSON example of the command's output data. Not a formal JSON Schema — use it to understand the output field names and value shapes. Null if supportsJson is false.")
    String outputExample
) {}
```

### `CommandInvocationResponse` record (Tool #3 response)

```java
public record CommandInvocationResponse(
    @JsonPropertyDescription("True if the command was found, validated, and executed without error.")
    boolean success,

    @JsonPropertyDescription("When success is false, describes validation failures or execution errors. Null on success.")
    String failure,

    @JsonPropertyDescription("JSON blob of the command's response data. Null on failure or if the command produces no output.")
    String result
) {}
```

---

## Part 2: H2 Database Schema

```sql
CREATE TABLE IF NOT EXISTS COMMANDS (
    COMMAND_KEY       VARCHAR(512)  PRIMARY KEY,  -- "namespace commandName"
    NAMESPACE         VARCHAR(128)  NOT NULL,
    COMMAND_NAME      VARCHAR(256)  NOT NULL,
    DESCRIPTION       VARCHAR(2000),
    LONG_DESCRIPTION  CLOB,
    INPUT_PARAMS_JSON CLOB,   -- structured JSON array for schema delivery
    INPUT_PARAMS_TEXT VARCHAR(8000),  -- flat text for Lucene indexing
    OUTPUT_SCHEMA_JSON CLOB,  -- example JSON from getExampleJSON()
    SUPPORTS_JSON     BOOLEAN  DEFAULT FALSE,
    UPDATED_AT        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

H2 is used for:
- Durable structured storage (survives Lucene index rebuild).
- `SELECT COMMAND_KEY FROM COMMANDS` — stale-key detection in ETL.
- `SELECT * FROM COMMANDS WHERE COMMAND_KEY = ?` — full-schema fetch for Tool #2.

Lucene is used for:
- Full-text scored search (Tool #1).
- Field-specific queries against description, inputParams, namespace.

H2 dialect: `CREATE TABLE IF NOT EXISTS` with `MERGE INTO ... KEY(COMMAND_KEY)` for upsert.

---

## Part 3: Lucene Index Schema

Each command is a Lucene `Document` with the following `Field`s:

| Lucene field name | Field type | Stored | Analyzed | Purpose |
|---|---|---|---|---|
| `commandKey` | `StringField` | yes | no | Lookup + delete by key (no tokenisation) |
| `namespace` | `TextField` | yes | yes | `namespace:network` style queries |
| `description` | `TextField` | yes | yes | `description:"select nodes"` queries |
| `inputParams` | `TextField` | yes | yes | `inputParams:filePath` queries |
| `outputSchema` | `TextField` | yes | yes | `outputSchema:nodeList` queries |
| `all` | `TextField` | no | yes | Default field — concatenation of all above text |

**QueryParser configuration:**
```java
String[] searchFields = {"all", "description", "inputParams", "outputSchema", "namespace"};
Map<String, Float> boosts = Map.of(
    "description", 2.0f,
    "inputParams", 1.5f,
    "outputSchema", 1.0f,
    "namespace",   1.0f,
    "all",         1.0f);
MultiFieldQueryParser parser = new MultiFieldQueryParser(
    searchFields, new StandardAnalyzer(), boosts);
parser.setDefaultOperator(QueryParser.Operator.OR);
```

**In-memory index:** `ByteBuffersDirectory` (Lucene 9+, replaces deprecated `RAMDirectory`).

After every upsert, `IndexWriter.commit()` is called and `DirectoryReader.openIfChanged()` refreshes the searcher (near-real-time).

---

## Part 4: CommandService (DAO)

### Thread-Safety Design

- **Write operations** (`upsert`, `delete`) are guarded by a `ReentrantReadWriteLock` write lock to serialize concurrent ETL writes.
- **Read operations** (`getAllCommandKeys`, `getByKey`) acquire the read lock, allowing concurrent tool reads.
- **`search()`** uses Lucene's `SearcherManager` which is independently thread-safe; no RW lock required for searches.
- H2 connection: single `Connection` per `CommandService` instance; only accessed under the write or read lock, so no concurrent access can occur.

```java
package edu.ucsd.idekerlab.cytoscapemcp.gateway;

public class CommandService {

    private final Connection h2;
    private final IndexWriter luceneWriter;
    private final SearcherManager searcherManager; // thread-safe reader pool
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public CommandService() {
        // open H2: "jdbc:h2:mem:commands;DB_CLOSE_DELAY=-1"
        // run CREATE TABLE IF NOT EXISTS COMMANDS ...
        // open Lucene ByteBuffersDirectory + IndexWriter(StandardAnalyzer)
        // this.searcherManager = new SearcherManager(luceneWriter, null)
    }

    /** Upsert a command into H2 and Lucene (write-locked). */
    public void upsert(Command cmd) {
        lock.writeLock().lock();
        try {
            // H2: MERGE INTO COMMANDS KEY(COMMAND_KEY) VALUES (...)
            // Lucene: writer.deleteDocuments(new Term("commandKey", key))
            //         writer.addDocument(toDocument(cmd))
            //         writer.commit()
            // searcherManager.maybeRefresh()
        } finally { lock.writeLock().unlock(); }
    }

    /** Delete a command (write-locked). */
    public void delete(String commandKey) {
        lock.writeLock().lock();
        try {
            // H2: DELETE FROM COMMANDS WHERE COMMAND_KEY = ?
            // Lucene: writer.deleteDocuments + commit
            // searcherManager.maybeRefresh()
        } finally { lock.writeLock().unlock(); }
    }

    /** Return all stored command keys (read-locked). */
    public Set<String> getAllCommandKeys() {
        lock.readLock().lock();
        try { /* SELECT COMMAND_KEY FROM COMMANDS */ }
        finally { lock.readLock().unlock(); }
    }

    /** Full-schema fetch from H2 (read-locked). */
    public Optional<Command> getByKey(String commandKey) {
        lock.readLock().lock();
        try { /* SELECT * FROM COMMANDS WHERE COMMAND_KEY = ? */ }
        finally { lock.readLock().unlock(); }
    }

    /**
     * Full-text search via Lucene SearcherManager (no RW lock needed —
     * SearcherManager is independently thread-safe for concurrent reads).
     */
    public SearchResults search(String query, int max) {
        // 1. MultiFieldQueryParser.parse(query)
        // 2. IndexSearcher searcher = searcherManager.acquire()
        // 3. TopDocs = searcher.search(q, max)
        // 4. For each ScoreDoc: load Document, build ResultRow
        //    (commandKey, score, summary from description field,
        //     inputs = split stored inputParams field on separator token,
        //     outputs = top-level key names parsed from outputSchema field JSON if non-null)
        // 5. searcherManager.release(searcher) in finally
        // On parse error: return SearchResults(false, "Malformed query: …", emptyList)
    }

    public void close() {
        lock.writeLock().lock();
        try { /* searcherManager.close(), luceneWriter.close(), h2.close() */ }
        finally { lock.writeLock().unlock(); }
    }
}
```

---

## Part 5: CommandETLService

### scheduleScan() — low-level design

```java
package edu.ucsd.idekerlab.cytoscapemcp.gateway;

public class CommandETLService {

    private final AvailableCommands availableCommands;
    private final CommandService commandService;
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean scanInProgress = new AtomicBoolean(false);
    private final AtomicBoolean rescanRequested = new AtomicBoolean(false);

    public CommandETLService(AvailableCommands availableCommands,
                             CommandService commandService) { ... }

    /**
     * Non-blocking. If a scan is already running, marks a rescan-requested flag
     * so the running scan loops again once it finishes. This prevents missed events
     * when a bundle lifecycle change arrives during an active scan.
     */
    public void scheduleScan() {
        if (!scanInProgress.compareAndSet(false, true)) {
            rescanRequested.set(true); // running scan will pick this up
            return;
        }
        scanExecutor.submit(() -> {
            try {
                do {
                    rescanRequested.set(false);
                    performScan();
                } while (rescanRequested.compareAndSet(true, false));
            } catch (Exception e) {
                // log but do not crash the thread
            } finally {
                scanInProgress.set(false);
            }
        });
    }

    private void performScan() {
        Set<String> liveKeys = new HashSet<>();

        for (String namespace : availableCommands.getNamespaces()) {
            for (String commandName : availableCommands.getCommands(namespace)) {
                String commandKey = namespace + " " + commandName;
                liveKeys.add(commandKey);

                // --- collect argument metadata ---
                List<String> argNames = availableCommands.getArguments(namespace, commandName);
                String description       = availableCommands.getDescription(namespace, commandName);
                String longDescription   = availableCommands.getLongDescription(namespace, commandName);
                boolean supportsJson     = availableCommands.getSupportsJSON(namespace, commandName);
                String exampleJson       = supportsJson
                    ? availableCommands.getExampleJSON(namespace, commandName)
                    : null;

                // --- build inputParamsJson (JSON array of arg descriptors) ---
                // For each argName in argNames:
                //   {
                //     "name":         argName,
                //     "type":         availableCommands.getArgType(ns, cmd, argName).getSimpleName(),
                //     "required":     availableCommands.getArgRequired(ns, cmd, argName),
                //     "description":  availableCommands.getArgDescription(ns, cmd, argName),
                //     "tooltip":      availableCommands.getArgTooltip(ns, cmd, argName),
                //     "example":      availableCommands.getArgExampleStringValue(ns, cmd, argName)
                //   }
                String inputParamsJson = buildInputParamsJson(namespace, commandName, argNames);

                // --- build inputParamsText for Lucene ---
                // Concatenate: argName + " " + argDescription + " " for each arg.
                String inputParamsText = buildInputParamsText(namespace, commandName, argNames);

                Command cmd = new Command(
                    commandKey, namespace, commandName,
                    description, longDescription,
                    inputParamsJson, inputParamsText,
                    exampleJson, supportsJson);

                commandService.upsert(cmd);
            }
        }

        // Remove stale commands (apps that were uninstalled)
        Set<String> storedKeys = commandService.getAllCommandKeys();
        storedKeys.removeAll(liveKeys);
        for (String stale : storedKeys) {
            commandService.delete(stale);
        }
    }
}
```

### Key design choices

- **Single-thread executor**: `Executors.newSingleThreadExecutor()` — at most one scan thread ever runs.
- **`AtomicBoolean scanInProgress`**: Fast guard so `scheduleScan()` is O(1) non-blocking.
- **`AtomicBoolean rescanRequested` (dirty flag)**: If `scheduleScan()` is called while a scan is running, it sets this flag. The running scan will loop and re-execute once after finishing, ensuring no event is ever lost even under rapid app installs.
- **Idempotent**: Each call to `upsert()` uses H2 `MERGE INTO ... KEY(...)` and Lucene delete-then-add, so repeated scans are safe.
- **Stale removal**: Any command key in H2 but no longer reported by `AvailableCommands` is deleted. Safe because scans are fully serialized (single thread).

---

## Part 6: Push Detection — OSGi BundleListener

Research finding: The Cytoscape public SDK (`org.cytoscape.app.event`) exposes only `AppsFinishedStartingEvent` for batch startup completion. There is **no** `AppInstalledEvent` or `AppRemovedEvent` in the public API.

**Solution: OSGi `BundleListener`**

When a user installs or uninstalls an app through Cytoscape's App Manager, the app's OSGi bundle transitions through `STARTED` / `STOPPING` / `STOPPED` lifecycle states. The `BundleContext.addBundleListener()` API fires synchronously for these transitions.

```java
// In CyActivator.start() (or McpServerFactory init):
bundleContext.addBundleListener(event -> {
    int type = event.getType();
    // STARTED: app bundle activated, commands registered
    // STOPPING: app bundle deactivating, commands about to be unregistered
    // STOPPED: complete, for safety
    if (type == BundleEvent.STARTED
            || type == BundleEvent.STOPPING
            || type == BundleEvent.STOPPED) {
        commandETLService.scheduleScan();
    }
});
```

This is a **push** solution. The `BundleListener` callback is very lightweight (just calls `scheduleScan()` which returns immediately if a scan is already running). The actual scan is off-thread.

**Why this is sufficient without a poll fallback:**
- App installations and removals go through the OSGi bundle lifecycle without exception.
- `STARTED` fires after a bundle's `start()` method returns, which is when commands are registered.
- `STOPPED` fires after `stop()`, which is when commands are unregistered.

There is no need for a poll fallback given this OSGi push mechanism.

---

## Part 7: Tool #1 — CommandGatewaySearchTool

### Class definition

```java
package edu.ucsd.idekerlab.cytoscapemcp.gateway;

public class CommandGatewaySearchTool {

    static final String TOOL_NAME  = "command_gateway_search";
    static final String TOOL_TITLE = "Search Desktop Commands";

    static final String TOOL_DESCRIPTION = """
        Search the full catalog of Cytoscape Desktop commands registered by core \
        and all installed apps using a Lucene full-text query. Use this tool \
        whenever the current user conversation touches on any Cytoscape-oriented \
        operation — selecting elements, running algorithms, exporting data, \
        changing layouts, manipulating tables, adjusting styles, or any other \
        desktop action. Submit a Lucene-formatted query built from keywords in \
        the user's current context; the tool returns a ranked list of matching \
        commands with relevance scores so you can quickly identify the best \
        candidate commands.

        WHEN TO USE: Call this tool proactively whenever you detect the user \
        describing a Cytoscape action — even partial context is enough. The search \
        is fast and designed for repeated calls as the conversation evolves. \
        A targeted keyword query is more useful than a broad one; the match score \
        on each result row is the key signal: a high-scoring result is very likely \
        the right command. Use field-scoped queries (e.g., inputParams:filePath \
        or description:export) to drill into specific aspects of the command \
        metadata. After reviewing scores and summaries, call the command retrieval \
        tool on high-scoring candidates to get full schemas before invoking.

        LUCENE QUERY SYNTAX: Keywords search across all indexed command text by \
        default. Field-specific syntax: description:X, inputParams:X, \
        outputSchema:X, namespace:X. Boolean operators: AND, OR, NOT. Phrase \
        matching: "exact phrase". Wildcards: select*, lay?ut. Boosting: \
        select^2 nodes. Submit a query, review match scores, refine if needed.

        Returns a SearchResults response. On error, success is false and the \
        failure field describes the cause (e.g., malformed Lucene query syntax).\
        """;

    static final String TOOL_EXAMPLES = """
        Example 1 — User asks to select all high-degree nodes in the network:
        {"query": "select nodes degree filter", "max": 10}

        Example 2 — User wants to export the current network as a PNG image:
        {"query": "export network image file png", "max": 5}

        Example 3 — User asks to apply a force-directed layout:
        {"query": "layout force-directed", "max": 5}

        Example 4 — User wants to filter edges by a weight attribute:
        {"query": "inputParams:weight filter edge threshold", "max": 10}

        Example 5 — Broad discovery across all network commands:
        {"query": "namespace:network", "max": 20}

        Example 6 — Find commands that return node list in their output:
        {"query": "outputSchema:nodeList", "max": 10}

        Example 7 — Search for table import commands:
        {"query": "description:import AND namespace:table", "max": 8}
        """;

    // INPUT_SCHEMA — two required properties: query and max
    static final String INPUT_SCHEMA = McpSchema.InputSchema.builder()
        .property("query",
            McpSchema.InputSchema.stringProp(
                "Required. Lucene query string to search command metadata. "
                + "Evaluated against command descriptions, input parameter names and descriptions, "
                + "output schema text, and namespace. Supports full Lucene query syntax: "
                + "bare keywords search across all indexed text "
                + "(e.g., 'select nodes filtered'); "
                + "field-scoped syntax restricts to one metadata aspect "
                + "(e.g., 'inputParams:filePath', 'description:export', 'namespace:network'); "
                + "boolean operators AND, OR, NOT; phrase quotes "
                + "(e.g., '\"network layout\"'); "
                + "wildcards (e.g., 'select*'). "
                + "Must be valid Lucene query syntax — invalid syntax returns "
                + "success=false with a parse error in the failure field. "
                + "Example values: 'layout force-directed', "
                + "'description:select AND inputParams:nodeList', "
                + "'export table csv', 'namespace:network'."))
        .property("max",
            McpSchema.InputSchema.intProp(
                "Required. Maximum number of result rows to return. "
                + "Results beyond this cap are silently clipped. "
                + "Use 5–15 for targeted queries; up to 50 for broad exploratory scans. "
                + "Example values: 5, 10, 25."))
        .required("query", "max")
        .build();

    // OUTPUT_SCHEMA derived from SearchResults record via McpSchema.toSchemaJson()
    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(SearchResults.class);

    private final CommandService commandService;

    public CommandGatewaySearchTool(CommandService commandService) {
        this.commandService = commandService;
    }

    public McpServerFeatures.SyncToolSpecification toSpec() {
        return Tool.builder()
            .name(TOOL_NAME)
            .title(TOOL_TITLE)
            .description(TOOL_DESCRIPTION + "\n\n" + TOOL_EXAMPLES)
            .inputSchema(INPUT_SCHEMA)
            .outputSchema(OUTPUT_SCHEMA)
            .build();
    }

    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest req) {
        int max = req.param("max", Integer.class, 10);
        String query = req.param("query", String.class, null);
        if (query == null || query.isBlank()) {
            var err = new SearchResults(false, "Required parameter 'query' is missing or blank.", List.of());
            return CallToolResult.builder().structuredContent(err).isError(true).build();
        }
        SearchResults results = commandService.search(query, Math.max(1, max));
        return CallToolResult.builder().structuredContent(results).build();
    }
}
```

---

## Part 8: Tool #2 — CommandGatewayGetTool

This tool fetches **full schema** from `AvailableCommands` directly (bypassing H2/Lucene) to ensure the freshest, most complete data for invocation planning.

**Timing note**: `AvailableCommands` reflects the live desktop state at call time. If a command was just registered moments ago (new app installed) and the ETL scan hasn't run yet, the Get tool will still return a result because it reads live from the OSGi service — it does not depend on the H2/Lucene index. This means the Get and Invoke tools work correctly even before the first ETL scan completes.

### inputSchema derivation

For each command, `inputSchema` is a JSON Schema string built from `AvailableCommands`:

```json
{
  "type": "object",
  "properties": {
    "network": {
      "type": "string",
      "description": "Name or SUID of the target network",
      "examples": ["current"]
    },
    "nodeList": {
      "type": "string",
      "description": "Comma-separated list of node identifiers or 'all'",
      "examples": ["all"]
    }
  },
  "required": ["network"]
}
```

Each property is built from:
- `name` → property key
- `getArgType(ns,cmd,arg).getSimpleName()` → mapped to JSON Schema type (String→"string", Integer/Long→"integer", Boolean→"boolean", Double→"number", default→"string")
- `getArgDescription(ns,cmd,arg)` → "description"
- `getArgExampleStringValue(ns,cmd,arg)` → "examples": [...]
- `getArgRequired(ns,cmd,arg)` → added to "required" array if true

### outputExample (command output)

`outputExample` = `getExampleJSON(ns, cmd)` — a representative JSON example provided by the command author. This is **not** a JSON Schema; it shows typical output field names and value shapes. If the command does not support JSON (`supportsJson = false`), this is null.

### MCP meta description

```java
static final String TOOL_NAME  = "command_gateway_get";
static final String TOOL_TITLE = "Get Desktop Command Schemas";

static final String TOOL_DESCRIPTION = """
    Retrieve the complete schema definition for one or more Cytoscape Desktop \
    commands by their fully qualified command key. Use this tool after \
    identifying candidate command keys through the command search tool to obtain \
    the precise input parameter definitions — names, types, required vs optional, \
    descriptions, and example values — and the full output schema before \
    invoking a command. Accepts up to 10 command keys per call for batching. \
    This tool is read-only and does not modify desktop state.

    WHEN TO USE: Call this tool for every command you plan to invoke, to obtain \
    the input schema needed to construct valid invocation parameters. If a \
    command key returned by search has a high match score and the summary looks \
    right, retrieve its full schema here before invoking. Batch multiple \
    candidate keys in a single call when comparing alternatives.

    Returns a DesktopCommandsResponse. Command keys not found in the desktop \
    are silently omitted from results. If no keys are found, success is false.\
    """;

static final String TOOL_EXAMPLES = """
    Example 1 — Retrieve full schema for a single command found by search:
    {"commandKeys": ["network select"]}

    Example 2 — Batch-retrieve schemas for two layout candidates:
    {"commandKeys": ["layout force-directed", "layout hierarchical"]}

    Example 3 — Get parameter details for a table export command:
    {"commandKeys": ["table export"]}

    Example 4 — Retrieve three commands at once after a broad search:
    {"commandKeys": ["network select", "network deselect", "network hide"]}
    """;
```

**Input schema:**
```json
{
  "type": "object",
  "properties": {
    "commandKeys": {
      "type": "array",
      "items": { "type": "string" },
      "minItems": 1,
      "maxItems": 10,
      "description": "Required. One or more fully qualified command keys in 'namespace command' format as returned by the command search tool. Maximum of 10 keys per call; excess keys are ignored. Example: [\"network select\"], [\"layout force-directed\", \"layout hierarchical\"]."
    }
  },
  "required": ["commandKeys"]
}
```

**Output schema:** derived from `DesktopCommandsResponse` via `McpSchema.toSchemaJson()`.

---

## Part 9: Tool #3 — CommandGatewayInvokeTool

### Validation

Before invocation, the tool performs the following checks:
1. Parse `inputParams` as a JSON object (fail fast on parse error).
2. Load the command's input schema from `AvailableCommands` (same logic as Tool #2).
3. For each required parameter: verify the key exists in `inputParams`.
4. For each supplied parameter: verify the key is a known argument name.
5. (Optional type check) Attempt coercion of each value to `getArgType()` — report mismatch.

A lightweight validation is preferred over embedding an external JSON-schema-validator library. The required-fields check and unknown-param warnings cover the most common LLM errors. If the project later adds a JSON Schema validator dependency, it can replace this inline logic.

### Command invocation

Follows the CyREST pattern exactly:

```java
// Parse namespace and commandName from commandKey
String[] parts = commandKey.split(" ", 2);
String namespace   = parts[0];
String commandName = parts[1];

// Convert inputParams JSON to Map<String, Object>
Map<String, Object> argsMap = objectMapper.readValue(inputParams, Map.class);

// Accumulate result via observer
// Result contract: if taskFinished() is called multiple times (multi-task iterator),
// the LAST non-null String result is used (commands that produce multiple tasks
// typically have the meaningful output in the final task).
StringBuilder resultJson = new StringBuilder();
TaskObserver observer = new TaskObserver() {
    @Override public void taskFinished(ObservableTask t) {
        Object res = t.getResults(String.class);
        if (res != null) {
            resultJson.setLength(0); // reset — keep last non-null result
            resultJson.append(res.toString());
        }
    }
    @Override public void allFinished(FinishStatus status) {
        // FinishStatus.SUCCEEDED / CANCELLED / FAILED can be checked here
        // if allFinished is not SUCCEEDED, override the result with failure
    }
};

syncTaskManager.execute(
    commandExecutorTaskFactory.createTaskIterator(namespace, commandName, argsMap, observer),
    observer);

String result = resultJson.length() > 0 ? resultJson.toString() : null;
return new CommandInvocationResponse(true, null, result);
```

### MCP meta description

```java
static final String TOOL_NAME  = "command_gateway_invoke";
static final String TOOL_TITLE = "Invoke Desktop Command";

static final String TOOL_DESCRIPTION = """
    Execute a registered Cytoscape Desktop command by name with a JSON input \
    parameter set and return the command's response. Use this tool only after \
    retrieving the command's full schema from the command retrieval tool to \
    ensure parameters are correct. The tool validates the supplied input \
    parameters against the command's input schema: required parameters must be \
    present, unknown parameter names are rejected, and type mismatches are \
    reported — all before the command is sent to the desktop. On validation \
    failure, success is false and the failure field lists the specific problems.

    WHEN TO USE: This is the execution step after search and schema retrieval. \
    Do not guess at parameter names or values — always retrieve the command's \
    schema first. For commands that modify desktop state (layout, selection, \
    style changes, imports) be aware that execution is immediate and \
    irreversible unless the desktop provides an undo mechanism.

    WARNING: This tool is state-mutating. Desktop networks, views, tables, \
    and styles may change as a result of invocation depending on the command.

    Returns a CommandInvocationResponse. On error, success is false and failure \
    describes the reason; result is null.\
    """;

static final String TOOL_EXAMPLES = """
    Example 1 — Select nodes with degree > 5 in the current network:
    {"commandKey": "network select", "inputParams": "{\"network\": \"current\", \"nodeList\": \"attribute:Degree > 5\"}"}

    Example 2 — Apply force-directed layout with default parameters:
    {"commandKey": "layout force-directed", "inputParams": "{}"}

    Example 3 — Export the current network as a SIF file:
    {"commandKey": "network export", "inputParams": "{\"options\": \"SIF\", \"OutputFile\": \"/tmp/mynet.sif\"}"}

    Example 4 — Close a named network:
    {"commandKey": "network destroy", "inputParams": "{\"network\": \"myNetwork\"}"}
    """;
```

**Input schema:**
```json
{
  "type": "object",
  "properties": {
    "commandKey": {
      "type": "string",
      "description": "Required. Fully qualified command key in 'namespace command' format. Must match a key returned by the search or retrieval tools. Example values: 'network select', 'layout force-directed', 'table import file'."
    },
    "inputParams": {
      "type": "string",
      "description": "Required. JSON string whose keys are the command's input parameter names and values are the parameter values. Must be valid JSON. Required parameters per the command's input schema must be present. String values must be quoted, numbers unquoted, booleans as true/false. Example values: '{}', '{\"network\": \"current\"}', '{\"filePath\": \"/data/net.sif\", \"firstRowAsColumnNames\": true}'."
    }
  },
  "required": ["commandKey", "inputParams"]
}
```

**Output schema:** derived from `CommandInvocationResponse` via `McpSchema.toSchemaJson()`.

---

## Part 10: CyActivator / McpServerFactory Changes

### CyActivator.java

```java
// New service retrieval (add alongside existing retrievals):
AvailableCommands availableCommands =
    getService(bundleContext, AvailableCommands.class);

// Instantiate gateway services:
CommandService commandService = new CommandService();
CommandETLService commandETLService = new CommandETLService(availableCommands, commandService);

// Pass to McpServerFactory (add as new parameters):
McpSyncServer mcpServer = McpServerFactory.create(
    ...,                       // existing params
    availableCommands,
    commandService);

// Register OSGi BundleListener for push-based ETL trigger:
bundleContext.addBundleListener(event -> {
    int type = event.getType();
    if (type == BundleEvent.STARTED || type == BundleEvent.STOPPED) {
        commandETLService.scheduleScan();
    }
});

// Register the AppsFinishedStartingListener (already exists) to also trigger scan:
// (modify existing listener to add commandETLService.scheduleScan() call)
registerService(bundleContext, (AppsFinishedStartingListener) e -> {
    commandETLService.scheduleScan();
    // ... existing startup logic
}, AppsFinishedStartingListener.class, new Properties());
```

### McpServerFactory.java

Add parameters `AvailableCommands availableCommands` and `CommandService commandService` to `create(...)`. Inside the method:

```java
// Gateway tools
CommandService commandService_local = commandService; // alias for clarity
server.addTool(new CommandGatewaySearchTool(commandService_local).toSpec());
server.addTool(new CommandGatewayGetTool(availableCommands).toSpec());
server.addTool(new CommandGatewayInvokeTool(availableCommands, syncTaskManager,
                                             commandExecutorTaskFactory).toSpec());
```

---

## Part 11: build.gradle Changes

```groovy
// Add to configurations.embed block:
embed 'com.h2database:h2:2.2.224'
embed 'org.apache.lucene:lucene-core:9.11.1'
embed 'org.apache.lucene:lucene-queryparser:9.11.1'
embed 'org.apache.lucene:lucene-analysis-common:9.11.1'

// Update BND Private-Package:
// from: edu.ucsd.idekerlab.cytoscapemcp,edu.ucsd.idekerlab.cytoscapemcp.tools,...
// to:   add edu.ucsd.idekerlab.cytoscapemcp.gateway
```

---

## Part 12: New Files Summary

| File | Description |
|---|---|
| `gateway/Command.java` | H2 binding record |
| `gateway/CommandService.java` | DAO: H2 + Lucene management |
| `gateway/CommandETLService.java` | Scanner: AvailableCommands → CommandService |
| `gateway/SearchResults.java` | Search response record |
| `gateway/ResultRow.java` | Per-result record |
| `gateway/DesktopCommandsResponse.java` | Get response record |
| `gateway/DesktopCommand.java` | Per-command schema record |
| `gateway/CommandInvocationResponse.java` | Invoke response record |
| `gateway/CommandGatewaySearchTool.java` | MCP Tool #1 |
| `gateway/CommandGatewayGetTool.java` | MCP Tool #2 |
| `gateway/CommandGatewayInvokeTool.java` | MCP Tool #3 |
| `docs/harness/product-specs/05-command-gateway-tools.md` | Product spec |

---

## Todos

See SQL tracking table.
