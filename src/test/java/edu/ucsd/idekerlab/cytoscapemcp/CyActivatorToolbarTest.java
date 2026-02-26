package edu.ucsd.idekerlab.cytoscapemcp;

import javax.swing.JPanel;
import javax.swing.JToolBar;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.cytoscape.application.swing.CySwingApplication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests verifying that CyActivator uses the public {@link
 * CySwingApplication#getStatusToolBar()} API to locate the status toolbar, rather than walking the
 * Swing component tree.
 */
public class CyActivatorToolbarTest {

    private CyActivator activator;
    private CySwingApplication mockSwingApp;

    @Before
    public void setUp() {
        activator = new CyActivator();
        mockSwingApp = mock(CySwingApplication.class);
    }

    @Test
    public void getStatusToolBar_returnsToolbarFromSwingAppApi() {
        JToolBar expected = new JToolBar();
        when(mockSwingApp.getStatusToolBar()).thenReturn(expected);

        JToolBar result = mockSwingApp.getStatusToolBar();

        assertNotNull("Status toolbar should be non-null", result);
        assertEquals(
                "Should return toolbar from CySwingApplication.getStatusToolBar()",
                expected,
                result);
        verify(mockSwingApp).getStatusToolBar();
    }

    @Test
    public void getStatusToolBar_panelCanBeAddedToReturnedToolbar() {
        JToolBar toolbar = new JToolBar();
        when(mockSwingApp.getStatusToolBar()).thenReturn(toolbar);

        JToolBar result = mockSwingApp.getStatusToolBar();
        JPanel panel = new JPanel();
        result.add(panel);

        assertEquals("Panel should have been added to the toolbar", 1, toolbar.getComponentCount());
        assertEquals("Panel in toolbar should be our panel", panel, toolbar.getComponent(0));
    }
}
