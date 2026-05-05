package edu.ucsd.idekerlab.cytoscapemcp.gateway;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.codecs.lucene99.Lucene99Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lucene-backed DAO for indexed Cytoscape Desktop commands. Provides full-text search via Lucene
 * analyzed fields and structured retrieval via stored fields. No relational database — Lucene
 * stored fields serve both roles.
 *
 * <p>Thread safety: {@link #upsert} and {@link #delete} are {@code synchronized} to prevent
 * interleaved delete+add on the same key. {@link #search}, {@link #getByKey}, and {@link
 * #getAllCommandKeys} use {@link SearcherManager} which is independently thread-safe for concurrent
 * reads.
 */
public class CommandService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandService.class);

    private static final int MAX_COMMANDS = 10_000;

    private final IndexWriter luceneWriter;
    private final SearcherManager searcherManager;
    private final ObjectMapper mapper = new ObjectMapper();

    public CommandService() throws Exception {
        ByteBuffersDirectory dir = new ByteBuffersDirectory();
        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        config.setCodec(new Lucene99Codec());
        this.luceneWriter = new IndexWriter(dir, config);
        this.searcherManager = new SearcherManager(luceneWriter, null);
        LOGGER.info("CommandService initialized (Lucene ByteBuffersDirectory)");
    }

    /** Upsert: replaces any existing document for this key. Synchronized to prevent interleave. */
    public synchronized void upsert(Command cmd) throws Exception {
        luceneWriter.deleteDocuments(new Term("commandKey", cmd.commandKey()));
        luceneWriter.addDocument(toDocument(cmd));
        luceneWriter.commit();
        searcherManager.maybeRefresh();
    }

    /** Delete a command by key. Synchronized. */
    public synchronized void delete(String commandKey) throws Exception {
        luceneWriter.deleteDocuments(new Term("commandKey", commandKey));
        luceneWriter.commit();
        searcherManager.maybeRefresh();
    }

    /** Returns all stored command keys. Thread-safe via SearcherManager. */
    public Set<String> getAllCommandKeys() throws Exception {
        IndexSearcher searcher = searcherManager.acquire();
        try {
            TopDocs hits = searcher.search(new MatchAllDocsQuery(), MAX_COMMANDS);
            Set<String> keys = new HashSet<>();
            StoredFields storedFields = searcher.storedFields();
            for (ScoreDoc sd : hits.scoreDocs) {
                Document doc = storedFields.document(sd.doc);
                String key = doc.get("commandKey");
                if (key != null) keys.add(key);
            }
            return keys;
        } finally {
            searcherManager.release(searcher);
        }
    }

    /** Full-schema fetch by exact key. Thread-safe via SearcherManager. */
    public Optional<Command> getByKey(String commandKey) throws Exception {
        IndexSearcher searcher = searcherManager.acquire();
        try {
            TopDocs hits = searcher.search(new TermQuery(new Term("commandKey", commandKey)), 1);
            if (hits.scoreDocs.length == 0) return Optional.empty();
            Document doc = searcher.storedFields().document(hits.scoreDocs[0].doc);
            return Optional.of(fromDocument(doc));
        } finally {
            searcherManager.release(searcher);
        }
    }

    /**
     * Full-text Lucene search. Thread-safe via SearcherManager. Returns error response on failure.
     */
    public SearchResults search(String query, int max) {
        IndexSearcher searcher = null;
        try {
            MultiFieldQueryParser parser = buildParser();
            org.apache.lucene.search.Query q = parser.parse(query);
            searcher = searcherManager.acquire();
            TopDocs hits = searcher.search(q, max);
            List<ResultRow> rows = new ArrayList<>();
            StoredFields storedFields = searcher.storedFields();
            for (ScoreDoc sd : hits.scoreDocs) {
                Document doc = storedFields.document(sd.doc);
                rows.add(toResultRow(doc, sd.score));
            }
            return new SearchResults(true, null, rows);
        } catch (ParseException e) {
            return new SearchResults(false, "Malformed Lucene query: " + e.getMessage(), List.of());
        } catch (Exception e) {
            LOGGER.error("Search error", e);
            return new SearchResults(false, "Search error: " + e.getMessage(), List.of());
        } finally {
            if (searcher != null) {
                try {
                    searcherManager.release(searcher);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void close() {
        try {
            searcherManager.close();
        } catch (Exception ignored) {
        }
        try {
            luceneWriter.close();
        } catch (Exception ignored) {
        }
        LOGGER.info("CommandService closed");
    }

    // -- Document conversion --------------------------------------------------

    private Document toDocument(Command cmd) {
        Document doc = new Document();
        doc.add(new StringField("commandKey", cmd.commandKey(), Field.Store.YES));
        doc.add(new TextField("namespace", nvl(cmd.namespace()), Field.Store.YES));
        doc.add(new TextField("commandName", nvl(cmd.commandName()), Field.Store.YES));
        doc.add(new TextField("description", nvl(cmd.description()), Field.Store.YES));
        doc.add(new StoredField("longDescription", nvl(cmd.longDescription())));
        doc.add(new TextField("inputParams", nvl(cmd.inputParamsText()), Field.Store.YES));
        doc.add(new StoredField("argNames", nvl(cmd.argNamesDelimited())));
        doc.add(new TextField("outputSchema", nvl(cmd.outputExampleJson()), Field.Store.YES));
        doc.add(new StoredField("supportsJson", String.valueOf(cmd.supportsJson())));
        // Catch-all field searched by default (not stored)
        String all =
                String.join(
                        " ",
                        nvl(cmd.namespace()),
                        nvl(cmd.commandName()),
                        nvl(cmd.description()),
                        nvl(cmd.inputParamsText()),
                        nvl(cmd.outputExampleJson()));
        doc.add(new TextField("all", all, Field.Store.NO));
        return doc;
    }

    private Command fromDocument(Document doc) {
        return new Command(
                doc.get("commandKey"),
                doc.get("namespace"),
                doc.get("commandName"),
                doc.get("description"),
                emptyToNull(doc.get("longDescription")),
                doc.get("inputParams"),
                doc.get("argNames"),
                emptyToNull(doc.get("outputSchema")),
                Boolean.parseBoolean(doc.get("supportsJson")));
    }

    private ResultRow toResultRow(Document doc, float score) {
        String commandKey = doc.get("commandKey");
        String summary = doc.get("description");

        // Inputs: restore arg names from pipe-delimited stored field
        String rawArgNames = doc.get("argNames");
        List<String> inputs =
                (rawArgNames == null || rawArgNames.isBlank())
                        ? List.of()
                        : Arrays.asList(rawArgNames.split("\\|"));

        // Outputs: parse top-level keys from stored outputSchema JSON example
        String outputSchemaJson = doc.get("outputSchema");
        List<String> outputs = List.of();
        if (outputSchemaJson != null && !outputSchemaJson.isBlank()) {
            try {
                JsonNode node = mapper.readTree(outputSchemaJson);
                if (node.isObject()) {
                    List<String> keys = new ArrayList<>();
                    node.fieldNames().forEachRemaining(keys::add);
                    outputs = keys;
                }
            } catch (Exception ignored) {
            }
        }

        return new ResultRow(commandKey, score, summary, inputs, outputs);
    }

    // -- Query builder --------------------------------------------------------

    private static MultiFieldQueryParser buildParser() {
        String[] fields = {"all", "description", "inputParams", "outputSchema", "namespace"};
        Map<String, Float> boosts =
                Map.of(
                        "description", 2.0f,
                        "inputParams", 1.5f,
                        "outputSchema", 1.0f,
                        "namespace", 1.0f,
                        "all", 1.0f);
        MultiFieldQueryParser parser =
                new MultiFieldQueryParser(fields, new StandardAnalyzer(), boosts);
        parser.setDefaultOperator(QueryParser.Operator.OR);
        return parser;
    }

    // -- Helpers --------------------------------------------------------------

    private static String nvl(String s) {
        return s != null ? s : "";
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
