package edu.ucsd.idekerlab.cytoscapemcp.gateway;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.command.AvailableCommands;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.SavePolicy;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CommandETLService} using a real in-memory Lucene {@link CommandService}.
 */
public class CommandETLServiceTest {

    @Mock private AvailableCommands availableCommands;
    @Mock private CyNetworkFactory networkFactory;
    @Mock private CyApplicationManager appMgr;
    @Mock private CyNetworkManager networkMgr;
    @Mock private CyNetworkViewFactory networkViewFactory;

    private CommandService commandService;
    private CommandETLService etlService;
    private AutoCloseable mocks;

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        commandService = new CommandService();

        // Default: simulate an existing current network/view so existing tests skip scaffold.
        when(appMgr.getCurrentNetwork()).thenReturn(mock(CyNetwork.class));
        when(appMgr.getCurrentNetworkView()).thenReturn(mock(CyNetworkView.class));

        etlService =
                new CommandETLService(
                        availableCommands,
                        commandService,
                        networkFactory,
                        appMgr,
                        networkMgr,
                        networkViewFactory);
    }

    @After
    public void tearDown() throws Exception {
        etlService.shutdown();
        commandService.close();
        mocks.close();
    }

    // -- performScan: upsert coverage -----------------------------------------

    @Test
    public void performScan_upsertsAllLiveCommands() throws Exception {
        stubMinimal("network", "select");
        stubMinimal("layout", "force-directed");
        when(availableCommands.getNamespaces()).thenReturn(List.of("network", "layout"));
        when(availableCommands.getCommands("network")).thenReturn(List.of("select"));
        when(availableCommands.getCommands("layout")).thenReturn(List.of("force-directed"));

        etlService.scheduleScan();
        assertTrue(etlService.awaitIdle(2, TimeUnit.SECONDS));

        Set<String> keys = commandService.getAllCommandKeys();
        assertTrue(keys.contains("network select"));
        assertTrue(keys.contains("layout force-directed"));
        assertEquals(2, keys.size());
    }

    @Test
    public void performScan_commandFieldsMappedCorrectly() throws Exception {
        when(availableCommands.getNamespaces()).thenReturn(List.of("network"));
        when(availableCommands.getCommands("network")).thenReturn(List.of("select"));
        when(availableCommands.getDescription("network", "select")).thenReturn("Select nodes");
        when(availableCommands.getLongDescription("network", "select")).thenReturn("Extended desc");
        when(availableCommands.getSupportsJSON("network", "select")).thenReturn(true);
        when(availableCommands.getExampleJSON("network", "select")).thenReturn("{\"nodeList\":[]}");
        when(availableCommands.getArguments("network", "select"))
                .thenReturn(List.of("nodeList", "network"));
        stubArg("network", "select", "nodeList", false, String.class, "Node list", "all");
        stubArg("network", "select", "network", false, String.class, "Target network", "current");

        etlService.scheduleScan();
        assertTrue(etlService.awaitIdle(2, TimeUnit.SECONDS));

        Optional<Command> result = commandService.getByKey("network select");
        assertTrue(result.isPresent());
        Command cmd = result.get();
        assertEquals("network", cmd.namespace());
        assertEquals("select", cmd.commandName());
        assertEquals("Select nodes", cmd.description());
        assertEquals("Extended desc", cmd.longDescription());
        assertTrue(cmd.supportsJson());
        assertEquals("{\"nodeList\":[]}", cmd.outputExampleJson());
        assertEquals("nodeList|network", cmd.argNamesDelimited());
        assertTrue(cmd.inputParamsText().contains("nodeList"));
        assertTrue(cmd.inputParamsText().contains("network"));
    }

    @Test
    public void performScan_supportsJsonFalse_skipsExampleJsonAndStoresNullOutput()
            throws Exception {
        when(availableCommands.getNamespaces()).thenReturn(List.of("layout"));
        when(availableCommands.getCommands("layout")).thenReturn(List.of("force-directed"));
        when(availableCommands.getDescription("layout", "force-directed")).thenReturn("Layout");
        when(availableCommands.getLongDescription("layout", "force-directed")).thenReturn(null);
        when(availableCommands.getSupportsJSON("layout", "force-directed")).thenReturn(false);
        when(availableCommands.getArguments("layout", "force-directed")).thenReturn(List.of());

        etlService.scheduleScan();
        assertTrue(etlService.awaitIdle(2, TimeUnit.SECONDS));

        verify(availableCommands, never()).getExampleJSON(anyString(), anyString());
        Optional<Command> cmd = commandService.getByKey("layout force-directed");
        assertTrue(cmd.isPresent());
        assertFalse(cmd.get().supportsJson());
        String outEx = cmd.get().outputExampleJson();
        assertTrue(outEx == null || outEx.isBlank());
    }

    @Test
    public void performScan_argNamesDelimited_pipeJoined() throws Exception {
        when(availableCommands.getNamespaces()).thenReturn(List.of("table"));
        when(availableCommands.getCommands("table")).thenReturn(List.of("export"));
        stubMinimal("table", "export");
        when(availableCommands.getArguments("table", "export"))
                .thenReturn(List.of("filePath", "outputFormat", "table"));
        stubArg("table", "export", "filePath", false, String.class, "File path", "/tmp/out");
        stubArg("table", "export", "outputFormat", false, String.class, "Format", "csv");
        stubArg("table", "export", "table", false, String.class, "Table name", "default");

        etlService.scheduleScan();
        assertTrue(etlService.awaitIdle(2, TimeUnit.SECONDS));

        Command cmd = commandService.getByKey("table export").orElseThrow();
        assertEquals("filePath|outputFormat|table", cmd.argNamesDelimited());
    }

    @Test
    public void performScan_emptyRegistry_indexRemainsEmpty() throws Exception {
        when(availableCommands.getNamespaces()).thenReturn(List.of());

        etlService.scheduleScan();
        assertTrue(etlService.awaitIdle(2, TimeUnit.SECONDS));

        assertTrue(commandService.getAllCommandKeys().isEmpty());
    }

    // -- performScan: stale-key pruning ---------------------------------------

    @Test
    public void performScan_removesStaleCommands() throws Exception {
        commandService.upsert(makeCmd("old app command", "old", "app command"));

        when(availableCommands.getNamespaces()).thenReturn(List.of("network"));
        when(availableCommands.getCommands("network")).thenReturn(List.of("select"));
        stubMinimal("network", "select");

        etlService.scheduleScan();
        assertTrue(etlService.awaitIdle(2, TimeUnit.SECONDS));

        assertFalse(commandService.getAllCommandKeys().contains("old app command"));
        assertTrue(commandService.getAllCommandKeys().contains("network select"));
    }

    @Test
    public void performScan_existingCommandUpdated_onRescan() throws Exception {
        when(availableCommands.getNamespaces()).thenReturn(List.of("network"));
        when(availableCommands.getCommands("network")).thenReturn(List.of("select"));
        when(availableCommands.getDescription("network", "select")).thenReturn("v1");
        when(availableCommands.getLongDescription("network", "select")).thenReturn(null);
        when(availableCommands.getSupportsJSON("network", "select")).thenReturn(false);
        when(availableCommands.getArguments("network", "select")).thenReturn(List.of());

        etlService.scheduleScan();
        assertTrue(etlService.awaitIdle(2, TimeUnit.SECONDS));
        assertEquals("v1", commandService.getByKey("network select").orElseThrow().description());

        when(availableCommands.getDescription("network", "select")).thenReturn("v2");
        etlService.scheduleScan();
        assertTrue(etlService.awaitIdle(2, TimeUnit.SECONDS));

        assertEquals("v2", commandService.getByKey("network select").orElseThrow().description());
        assertEquals(1, commandService.getAllCommandKeys().size());
    }

    // -- scheduleScan: dirty-flag / concurrency -------------------------------

    @Test
    public void scheduleScan_whileScanRunning_setsDirtyFlagAndRescans() throws Exception {
        CountDownLatch firstScanBlocked = new CountDownLatch(1);
        CountDownLatch releaseFirstScan = new CountDownLatch(1);
        AtomicInteger callCount = new AtomicInteger(0);

        when(availableCommands.getNamespaces())
                .thenAnswer(
                        inv -> {
                            if (callCount.incrementAndGet() == 1) {
                                firstScanBlocked.countDown();
                                releaseFirstScan.await();
                            }
                            return List.of();
                        });

        etlService.scheduleScan();
        assertTrue("first scan did not start in time", firstScanBlocked.await(2, TimeUnit.SECONDS));

        etlService.scheduleScan();

        releaseFirstScan.countDown();
        assertTrue(etlService.awaitIdle(2, TimeUnit.SECONDS));

        verify(availableCommands, times(2)).getNamespaces();
    }

    // -- scaffold lifecycle ---------------------------------------------------

    @Test
    public void performScan_noCurrentNetwork_scaffoldCreatedAndDestroyed() throws Exception {
        CyNetwork scaffold = mockScaffoldNetwork();
        CyNetworkView scaffoldView = mock(CyNetworkView.class);

        when(appMgr.getCurrentNetwork()).thenReturn(null);
        when(appMgr.getCurrentNetworkView()).thenReturn(null);
        when(networkFactory.createNetwork(SavePolicy.DO_NOT_SAVE)).thenReturn(scaffold);
        when(networkViewFactory.createNetworkView(scaffold)).thenReturn(scaffoldView);
        when(availableCommands.getNamespaces()).thenReturn(List.of());

        // After addNetwork fires, simulate appMgr reflecting scaffold as current.
        when(appMgr.getCurrentNetworkView()).thenReturn(null).thenReturn(scaffoldView);

        etlService.scheduleScan();
        assertTrue(etlService.awaitIdle(2, TimeUnit.SECONDS));

        verify(networkMgr).addNetwork(scaffold, true);
        verify(appMgr).setCurrentNetwork(scaffold);
        verify(networkViewFactory).createNetworkView(scaffold);
        verify(appMgr).setCurrentNetworkView(scaffoldView);
        verify(networkMgr).destroyNetwork(scaffold);
    }

    @Test
    public void performScan_existingCurrentNetwork_noScaffoldCreated() throws Exception {
        // Default setUp already stubs getCurrentNetwork() to non-null.
        when(availableCommands.getNamespaces()).thenReturn(List.of());

        etlService.scheduleScan();
        assertTrue(etlService.awaitIdle(2, TimeUnit.SECONDS));

        verify(networkMgr, never()).addNetwork(any(), anyBoolean());
        verify(networkFactory, never()).createNetwork(any(SavePolicy.class));
    }

    @Test
    public void performScan_scaffoldNetworkName_isCommandDocGen() throws Exception {
        CyNetwork scaffold = mockScaffoldNetwork();

        when(appMgr.getCurrentNetwork()).thenReturn(null);
        when(appMgr.getCurrentNetworkView()).thenReturn(mock(CyNetworkView.class));
        when(networkFactory.createNetwork(SavePolicy.DO_NOT_SAVE)).thenReturn(scaffold);
        when(availableCommands.getNamespaces()).thenReturn(List.of());

        etlService.scheduleScan();
        assertTrue(etlService.awaitIdle(2, TimeUnit.SECONDS));

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(scaffold.getRow(scaffold)).set(eq(CyNetwork.NAME), nameCaptor.capture());
        assertEquals("cy:command_documentation_generation", nameCaptor.getValue());
    }

    // -- mapArgType static helper ---------------------------------------------

    @Test
    public void mapArgType_mapsJavaTypesToJsonSchemaTypes() {
        assertEquals("integer", CommandETLService.mapArgType(Integer.class));
        assertEquals("integer", CommandETLService.mapArgType(Long.class));
        assertEquals("integer", CommandETLService.mapArgType(int.class));
        assertEquals("integer", CommandETLService.mapArgType(long.class));
        assertEquals("number", CommandETLService.mapArgType(Double.class));
        assertEquals("number", CommandETLService.mapArgType(Float.class));
        assertEquals("number", CommandETLService.mapArgType(double.class));
        assertEquals("number", CommandETLService.mapArgType(float.class));
        assertEquals("boolean", CommandETLService.mapArgType(Boolean.class));
        assertEquals("boolean", CommandETLService.mapArgType(boolean.class));
        assertEquals("string", CommandETLService.mapArgType(String.class));
        assertEquals("string", CommandETLService.mapArgType(List.class));
        assertEquals("string", CommandETLService.mapArgType(null));
    }

    // -- Helpers --------------------------------------------------------------

    /** Stub the minimum required AvailableCommands calls for one command with no arguments. */
    private void stubMinimal(String ns, String cmd) {
        when(availableCommands.getDescription(ns, cmd))
                .thenReturn("Description of " + ns + " " + cmd);
        when(availableCommands.getLongDescription(ns, cmd)).thenReturn(null);
        when(availableCommands.getSupportsJSON(ns, cmd)).thenReturn(false);
        when(availableCommands.getArguments(ns, cmd)).thenReturn(List.of());
    }

    /** Stub all AvailableCommands calls for a single argument on a command. */
    @SuppressWarnings("unchecked")
    private void stubArg(
            String ns,
            String cmd,
            String arg,
            boolean required,
            Class<?> type,
            String desc,
            String example) {
        when(availableCommands.getArgRequired(ns, cmd, arg)).thenReturn(required);
        doReturn(type).when(availableCommands).getArgType(ns, cmd, arg);
        when(availableCommands.getArgDescription(ns, cmd, arg)).thenReturn(desc);
        when(availableCommands.getArgTooltip(ns, cmd, arg)).thenReturn(null);
        when(availableCommands.getArgExampleStringValue(ns, cmd, arg)).thenReturn(example);
    }

    /**
     * Creates a mock CyNetwork wired up with the minimal row/table chain needed for {@code
     * scaffold.getRow(scaffold).set(CyNetwork.NAME, ...)} to be verifiable.
     */
    private CyNetwork mockScaffoldNetwork() {
        CyNetwork scaffold = mock(CyNetwork.class);
        CyRow row = mock(CyRow.class);
        when(scaffold.getRow(scaffold)).thenReturn(row);
        return scaffold;
    }

    private static Command makeCmd(String key, String ns, String name) {
        return new Command(key, ns, name, "desc", null, "", "", null, false);
    }
}
