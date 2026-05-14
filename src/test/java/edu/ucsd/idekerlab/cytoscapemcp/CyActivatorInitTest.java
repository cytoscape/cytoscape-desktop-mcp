package edu.ucsd.idekerlab.cytoscapemcp;

import java.util.Dictionary;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import org.cytoscape.command.AvailableCommands;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests verifying that CyActivator correctly initializes whether Cytoscape fires
 * AppsFinishedStartingEvent (fresh boot) or the desktop is already running (dynamic install).
 */
public class CyActivatorInitTest {

    /**
     * Subclass that overrides doInitializeApp() to avoid real OSGi/Swing dependencies while still
     * exercising the real guard logic in initializeApp().
     */
    static class TestableCyActivator extends CyActivator {
        final AtomicInteger realInitCount = new AtomicInteger(0);

        @Override
        void doInitializeApp() {
            realInitCount.incrementAndGet();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private BundleContext mockBundleContext(boolean availableCommandsPresent) {
        BundleContext bc = mock(BundleContext.class);
        // AbstractCyActivator.registerService calls bc.registerService internally — silence it.
        when(bc.registerService(anyString(), any(), any(Dictionary.class)))
                .thenReturn(mock(ServiceRegistration.class));
        when(bc.registerService(any(String[].class), any(), any(Dictionary.class)))
                .thenReturn(mock(ServiceRegistration.class));
        ServiceReference ref = availableCommandsPresent ? mock(ServiceReference.class) : null;
        when(bc.getServiceReference(AvailableCommands.class)).thenReturn(ref);
        return bc;
    }

    private TestableCyActivator activator;

    @Before
    public void setUp() {
        activator = new TestableCyActivator();
    }

    @Test
    public void start_freshBoot_doesNotInitializeBeforeEventFires() throws Exception {
        activator.start(mockBundleContext(false));
        assertEquals(
                "should not init until AppsFinishedStartingEvent fires",
                0,
                activator.realInitCount.get());
    }

    @Test
    public void start_dynamicInstall_initializesImmediately() throws Exception {
        activator.start(mockBundleContext(true));
        assertEquals(
                "should init directly when AvailableCommands is already in registry",
                1,
                activator.realInitCount.get());
    }

    @Test
    public void initializeApp_idempotent_multipleCallsRunRealInitOnce() {
        activator.initializeApp();
        activator.initializeApp();
        activator.initializeApp();
        assertEquals(
                "doInitializeApp should execute exactly once regardless of call count",
                1,
                activator.realInitCount.get());
    }

    @Test
    public void start_bothPathsTrigger_initializesExactlyOnce() throws Exception {
        // Simulate race: AvailableCommands present (probe triggers) AND listener fires too.
        activator.start(mockBundleContext(true)); // probe path
        activator.initializeApp(); // listener path (fires again)
        assertEquals(
                "real init should run only once even if both trigger paths fire",
                1,
                activator.realInitCount.get());
    }
}
