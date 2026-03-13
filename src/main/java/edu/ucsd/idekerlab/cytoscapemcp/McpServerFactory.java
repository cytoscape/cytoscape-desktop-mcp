package edu.ucsd.idekerlab.cytoscapemcp;

import java.util.Properties;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.ucsd.idekerlab.cytoscapemcp.tools.AnalyzeNetworkTool;
import edu.ucsd.idekerlab.cytoscapemcp.tools.ApplyLayoutTool;
import edu.ucsd.idekerlab.cytoscapemcp.tools.CreateNetworkViewTool;
import edu.ucsd.idekerlab.cytoscapemcp.tools.GetCompatibleColumnsTool;
import edu.ucsd.idekerlab.cytoscapemcp.tools.GetFileColumnsTool;
import edu.ucsd.idekerlab.cytoscapemcp.tools.GetLayoutAlgorithmsTool;
import edu.ucsd.idekerlab.cytoscapemcp.tools.GetLoadedNetworkViewsTool;
import edu.ucsd.idekerlab.cytoscapemcp.tools.GetMappablePropertiesTool;
import edu.ucsd.idekerlab.cytoscapemcp.tools.GetStylesTool;
import edu.ucsd.idekerlab.cytoscapemcp.tools.GetVisualStyleDefaultsTool;
import edu.ucsd.idekerlab.cytoscapemcp.tools.InspectTabularFileTool;
import edu.ucsd.idekerlab.cytoscapemcp.tools.LoadNetworkViewTool;
import edu.ucsd.idekerlab.cytoscapemcp.tools.SetCurrentNetworkViewTool;
import edu.ucsd.idekerlab.cytoscapemcp.tools.SetVisualDefaultTool;
import edu.ucsd.idekerlab.cytoscapemcp.tools.SwitchCurrentStyleTool;
import edu.ucsd.idekerlab.cytoscapemcp.tools.VisualPropertyService;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.command.CommandExecutorTaskFactory;
import org.cytoscape.io.read.CyNetworkReaderManager;
import org.cytoscape.io.read.InputStreamTaskFactory;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.property.CyProperty;
import org.cytoscape.view.layout.CyLayoutAlgorithmManager;
import org.cytoscape.view.model.CyNetworkViewFactory;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.presentation.RenderingEngineManager;
import org.cytoscape.view.vizmap.VisualMappingFunctionFactory;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyleFactory;
import org.cytoscape.work.SynchronousTaskManager;
import org.cytoscape.work.TaskManager;

import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.jackson2.DefaultJsonSchemaValidator;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;

/**
 * Static factory that creates and fully registers an {@link McpSyncServer} instance.
 *
 * <p>Centralises MCP server construction so that both {@link CyActivator} (OSGi runtime) and {@link
 * McpManifest} (standalone Gradle task) use identical tool/prompt registrations. Cytoscape service
 * parameters are nullable — they are stored in tool instances but only accessed inside handler
 * lambdas at call time, not during schema/spec construction via {@code toSpec()}.
 */
public final class McpServerFactory {

    /**
     * Creates and returns a fully-registered {@link McpSyncServer}.
     *
     * @param transportProvider the transport layer; required for server construction
     * @param objectMapper Jackson mapper used for JSON serialization
     * @param serverVersion version string embedded in the MCP server-info response
     * @param cyProperties app properties (nullable — used only by handler logic)
     * @param appManager Cytoscape application manager (nullable)
     * @param networkManager Cytoscape network manager (nullable)
     * @param viewManager Cytoscape network-view manager (nullable)
     * @param taskManager Cytoscape task manager (nullable)
     * @param cxReaderFactory CX network reader factory (nullable)
     * @param vmmManager visual mapping manager (nullable)
     * @param renderingEngineManager rendering engine manager (nullable)
     * @param continuousMappingFactory continuous visual mapping factory (nullable)
     * @param discreteMappingFactory discrete visual mapping factory (nullable)
     * @param passthroughMappingFactory passthrough visual mapping factory (nullable)
     * @param layoutManager Cytoscape layout algorithm manager (nullable)
     * @param networkReaderManager Cytoscape network reader manager for format auto-detection
     *     (nullable)
     * @param networkFactory Cytoscape network factory for creating networks from tabular data
     *     (nullable)
     * @param networkViewFactory Cytoscape network-view factory (nullable)
     * @param syncTaskManager synchronous task manager (nullable)
     * @param commandExecutorTaskFactory command executor for invoking registered app commands
     *     (nullable)
     * @param visualStyleFactory factory for creating new visual styles (nullable)
     */
    public static McpSyncServer create(
            McpTransportProvider transportProvider,
            ObjectMapper objectMapper,
            String serverVersion,
            CyProperty<Properties> cyProperties,
            CyApplicationManager appManager,
            CyNetworkManager networkManager,
            CyNetworkViewManager viewManager,
            TaskManager<?, ?> taskManager,
            InputStreamTaskFactory cxReaderFactory,
            VisualMappingManager vmmManager,
            RenderingEngineManager renderingEngineManager,
            VisualMappingFunctionFactory continuousMappingFactory,
            VisualMappingFunctionFactory discreteMappingFactory,
            VisualMappingFunctionFactory passthroughMappingFactory,
            CyLayoutAlgorithmManager layoutManager,
            CyNetworkReaderManager networkReaderManager,
            CyNetworkFactory networkFactory,
            CyNetworkViewFactory networkViewFactory,
            SynchronousTaskManager<?> syncTaskManager,
            CommandExecutorTaskFactory commandExecutorTaskFactory,
            VisualStyleFactory visualStyleFactory) {

        // Explicitly supply jsonMapper and jsonSchemaValidator to bypass McpJsonDefaults,
        // which uses ServiceLoader with the Thread context classloader — that classloader
        // cannot find META-INF/services inside our bundle in OSGi/Karaf/Felix.
        McpSyncServer server =
                McpServer.sync(transportProvider)
                        .serverInfo("cytoscape-mcp", serverVersion)
                        .capabilities(
                                ServerCapabilities.builder().tools(false).prompts(false).build())
                        .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                        .jsonSchemaValidator(new DefaultJsonSchemaValidator(objectMapper))
                        .build();

        // Register tools.
        server.addTool(
                new LoadNetworkViewTool(
                                cyProperties,
                                appManager,
                                networkManager,
                                viewManager,
                                taskManager,
                                cxReaderFactory,
                                networkReaderManager,
                                networkFactory,
                                networkViewFactory,
                                layoutManager)
                        .toSpec());
        server.addTool(
                new GetLoadedNetworkViewsTool(appManager, networkManager, viewManager, vmmManager)
                        .toSpec());
        server.addTool(
                new SetCurrentNetworkViewTool(appManager, networkManager, viewManager).toSpec());
        server.addTool(
                new CreateNetworkViewTool(
                                appManager, networkManager, viewManager, networkViewFactory)
                        .toSpec());
        server.addTool(new InspectTabularFileTool().toSpec());
        server.addTool(new GetFileColumnsTool().toSpec());
        server.addTool(
                new AnalyzeNetworkTool(appManager, syncTaskManager, commandExecutorTaskFactory)
                        .toSpec());
        server.addTool(new GetLayoutAlgorithmsTool(layoutManager).toSpec());
        server.addTool(new ApplyLayoutTool(appManager, layoutManager, syncTaskManager).toSpec());
        VisualPropertyService vpService = new VisualPropertyService();
        server.addTool(
                new GetVisualStyleDefaultsTool(
                                appManager, vmmManager, renderingEngineManager, vpService)
                        .toSpec());
        server.addTool(
                new SetVisualDefaultTool(appManager, vmmManager, renderingEngineManager, vpService)
                        .toSpec());
        server.addTool(
                new GetMappablePropertiesTool(
                                appManager, vmmManager, renderingEngineManager, vpService)
                        .toSpec());
        server.addTool(
                new GetCompatibleColumnsTool(appManager, renderingEngineManager, vpService)
                        .toSpec());
        server.addTool(new GetStylesTool(vmmManager).toSpec());
        server.addTool(
                new SwitchCurrentStyleTool(appManager, vmmManager, visualStyleFactory).toSpec());
        return server;
    }
}
