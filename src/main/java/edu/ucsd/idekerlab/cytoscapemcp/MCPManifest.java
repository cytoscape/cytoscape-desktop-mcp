package edu.ucsd.idekerlab.cytoscapemcp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.PromptArgument;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.ResourceTemplate;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Standalone generator that produces {@code MCPManifest.md} — a human-readable Markdown reference
 * describing every tool, prompt, resource, and resource template registered on the MCP server.
 *
 * <p>Usage: {@code MCPManifest [outputDir]}
 *
 * <ul>
 *   <li>{@code outputDir} — directory where {@code MCPManifest.md} is written; defaults to the
 *       current working directory if omitted.
 * </ul>
 */
public final class McpManifest {

    public static void main(String[] args) throws Exception {
        Path outputDir = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        Files.createDirectories(outputDir);

        // Build the server with null Cytoscape services — safe because toSpec() only
        // reads static schema constants; the service references are only used inside
        // handler lambdas that execute during actual tool invocations.
        McpTransportProvider transport = new McpTransportProvider();
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        McpSyncServer server =
                McpServerFactory.create(
                        transport,
                        mapper,
                        "standalone",
                        /* cyProperties= */ null,
                        /* appManager= */ null,
                        /* networkManager= */ null,
                        /* viewManager= */ null,
                        /* taskManager= */ null,
                        /* cxReaderFactory= */ null,
                        /* vmmManager= */ null,
                        /* renderingEngineManager= */ null,
                        /* continuousMappingFactory= */ null,
                        /* discreteMappingFactory= */ null,
                        /* passthroughMappingFactory= */ null,
                        /* layoutManager= */ null,
                        /* loadFileTaskFactory= */ null,
                        /* networkFactory= */ null,
                        /* networkViewFactory= */ null,
                        /* syncTaskManager= */ null);

        String toolsSection = renderTools(server.listTools(), mapper);
        String promptsSection = renderPrompts(server.listPrompts());
        String resourcesSection = renderResources(server.listResources(), mapper);
        String resourceTemplatesSection =
                renderResourceTemplates(server.listResourceTemplates(), mapper);

        String template = loadTemplate();
        String manifest =
                template.replace("{{TOOLS}}", toolsSection)
                        .replace("{{PROMPTS}}", promptsSection)
                        .replace("{{RESOURCES}}", resourcesSection)
                        .replace("{{RESOURCE_TEMPLATES}}", resourceTemplatesSection);

        Path outputFile = outputDir.resolve("MCPManifest.md");
        Files.writeString(outputFile, manifest, StandardCharsets.UTF_8);
        System.out.println("MCPManifest.md written to: " + outputFile.toAbsolutePath());
    }

    // -------------------------------------------------------------------------
    // Section renderers
    // -------------------------------------------------------------------------

    private static String renderTools(List<Tool> tools, ObjectMapper mapper) throws IOException {
        if (tools.isEmpty()) {
            return "*No tools registered.*\n";
        }
        StringBuilder sb = new StringBuilder();
        for (Tool tool : tools) {
            sb.append("### `").append(tool.name()).append("`\n\n");
            if (tool.description() != null && !tool.description().isBlank()) {
                sb.append("**Description:** ").append(tool.description()).append("\n\n");
            }
            if (tool.inputSchema() != null) {
                sb.append("**Input Schema:**\n\n```json\n")
                        .append(mapper.writeValueAsString(tool.inputSchema()))
                        .append("\n```\n\n");
            }
            if (tool.outputSchema() != null && !tool.outputSchema().isEmpty()) {
                sb.append("**Output Schema:**\n\n```json\n")
                        .append(mapper.writeValueAsString(tool.outputSchema()))
                        .append("\n```\n\n");
            }
            sb.append("---\n\n");
        }
        return sb.toString();
    }

    private static String renderPrompts(List<Prompt> prompts) {
        if (prompts.isEmpty()) {
            return "*No prompts registered.*\n";
        }
        StringBuilder sb = new StringBuilder();
        for (Prompt prompt : prompts) {
            sb.append("### `").append(prompt.name()).append("`\n\n");
            if (prompt.description() != null && !prompt.description().isBlank()) {
                sb.append("**Description:** ").append(prompt.description()).append("\n\n");
            }
            List<PromptArgument> args = prompt.arguments();
            if (args != null && !args.isEmpty()) {
                sb.append("**Arguments:**\n\n");
                sb.append("| Name | Description | Required |\n");
                sb.append("|------|-------------|----------|\n");
                for (PromptArgument arg : args) {
                    String desc = arg.description() != null ? arg.description() : "";
                    String req = Boolean.TRUE.equals(arg.required()) ? "Yes" : "No";
                    sb.append("| `")
                            .append(arg.name())
                            .append("` | ")
                            .append(desc)
                            .append(" | ")
                            .append(req)
                            .append(" |\n");
                }
                sb.append("\n");
            }
            sb.append("---\n\n");
        }
        return sb.toString();
    }

    private static String renderResources(List<Resource> resources, ObjectMapper mapper)
            throws IOException {
        if (resources.isEmpty()) {
            return "*No resources registered.*\n";
        }
        StringBuilder sb = new StringBuilder();
        for (Resource resource : resources) {
            sb.append("### `").append(resource.name()).append("`\n\n");
            sb.append("**URI:** `").append(resource.uri()).append("`\n\n");
            if (resource.description() != null && !resource.description().isBlank()) {
                sb.append("**Description:** ").append(resource.description()).append("\n\n");
            }
            if (resource.mimeType() != null) {
                sb.append("**MIME Type:** `").append(resource.mimeType()).append("`\n\n");
            }
            sb.append("---\n\n");
        }
        return sb.toString();
    }

    private static String renderResourceTemplates(
            List<ResourceTemplate> templates, ObjectMapper mapper) throws IOException {
        if (templates.isEmpty()) {
            return "*No resource templates registered.*\n";
        }
        StringBuilder sb = new StringBuilder();
        for (ResourceTemplate tmpl : templates) {
            sb.append("### `").append(tmpl.name()).append("`\n\n");
            sb.append("**URI Template:** `").append(tmpl.uriTemplate()).append("`\n\n");
            if (tmpl.description() != null && !tmpl.description().isBlank()) {
                sb.append("**Description:** ").append(tmpl.description()).append("\n\n");
            }
            if (tmpl.mimeType() != null) {
                sb.append("**MIME Type:** `").append(tmpl.mimeType()).append("`\n\n");
            }
            sb.append("---\n\n");
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Template loading
    // -------------------------------------------------------------------------

    private static String loadTemplate() throws IOException {
        try (InputStream is = McpManifest.class.getResourceAsStream("/manifest_template.md")) {
            if (is == null) {
                throw new IllegalStateException(
                        "manifest_template.md not found on classpath at /manifest_template.md");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
