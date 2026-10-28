/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.apache.camel.dsl.jbang.core.commands.ai.AppFeatures;
import org.apache.camel.dsl.jbang.core.commands.tui.AiPanel.AppStatusSource;

/**
 * What the AI panel sends a model besides the conversation: the system prompt and the tools. A local provider gets the
 * core set ({@link TuiTool.Flag#CORE}) and the tool groups of the selected integration, such as the SQL tools when it
 * has a database (CAMEL-24834); a hosted provider gets every tool; {@code /tools} pins core or full. Both stay the same
 * from question to question, so a local model's prompt cache keeps working: the selected integration travels in the
 * question ({@link #contextualize(McpFacade, String)}), and the tool groups are read again only when another
 * integration is selected or the selected one reloaded.
 */
final class AiToolSet {

    // auto | core | full, see useCoreTools(); loaded from camel.tui.ai.tools, null means auto
    private volatile String toolMode;
    private TuiToolRegistry toolRegistry;
    // CAMEL-24834: the tool groups (SQL, tracing, resilience) of the selected integration that the core set gets, see
    // refreshToolGroups()
    private volatile AppStatusSource appStatusSource;
    private volatile String toolGroupsPid;
    private volatile int toolGroupsReloads = -1;
    private volatile AppFeatures toolGroupsFeatures = AppFeatures.none();
    private volatile TuiToolGroups.Selection toolGroups = TuiToolGroups.Selection.none();
    private boolean mcpServerActive;
    private int mcpServerPort;

    /** The configured mode, {@code auto}, {@code core} or {@code full}; null until it is loaded. */
    String mode() {
        return toolMode;
    }

    void setMode(String mode) {
        this.toolMode = mode;
    }

    /** The tools the panel runs; null until the MCP facade is wired. */
    TuiToolRegistry registry() {
        return toolRegistry;
    }

    void setRegistry(TuiToolRegistry registry) {
        this.toolRegistry = registry;
    }

    void setMcpInfo(boolean active, int port) {
        this.mcpServerActive = active;
        this.mcpServerPort = port;
    }

    /** Where the tool groups are read from; by default the selected integration of the MCP facade. */
    void setAppStatusSource(AppStatusSource source) {
        this.appStatusSource = source;
    }

    TuiToolGroups.Selection groups() {
        return toolGroups;
    }

    /**
     * The preamble an ACP agent gets ahead of its first prompt: the panel's system prompt plus the rules that only
     * matter for an agent with file tools of its own. Not part of the prompt budget, which covers the LLM path.
     */
    String acpPreamble(LlmClient client) {
        String prompt = systemPrompt(client);
        return (prompt.endsWith("\n") ? prompt : prompt + "\n")
               + "- You run inside the Camel TUI: edit the integration's source files only with camel_edit_file or camel_write_file, "
               + "never with your own file tools, so the user sees the diff and confirms it, or watches the edit "
               + "being typed in the Source editor (/write live)\n";
    }

    /**
     * The static prefix sent with every request. It deliberately contains nothing that changes between turns (the
     * selected integration travels in the user message instead) so a local model's prompt cache can reuse it, and it
     * does not repeat the tool list because the tool definitions already carry every description.
     */
    String systemPrompt(LlmClient client) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an Apache Camel assistant running inside the Camel TUI terminal console. ");
        sb.append("You help users understand and troubleshoot their running Camel integrations.\n\n");

        sb.append("You have camel_* tools for the integration and tui_* tools for the TUI; the tool definitions ");
        sb.append("describe each one. ");
        sb.append("All tui_get_* tools fetch data directly from any tab without changing what the user sees.\n\n");
        sb.append("Guidelines:\n");
        sb.append("- NEVER call tui_navigate just to read data; the tui_get_* tools read any tab without navigating\n");
        sb.append("- Prefer tui_get_table over tui_get_screen for structured data; ");
        sb.append("call tui_get_options only when unsure which tab has it\n");
        sb.append("- tui_get_state tells which integration and tab is selected; tui_get_processor_detail explains ");
        sb.append("a route's steps; tui_get_status has data no tab shows (context, runtime, health, properties)\n");
        sb.append("- Your own tool calls are in the AI log (tui_get_ai_log); the MCP log only has external clients\n");
        sb.append("- Be concise and actionable; when something looks wrong, explain it and suggest fixes\n");
        sb.append("- camel_control stops/starts routes and integrations gracefully; its reset-stats action clears ");
        sb.append("statistics without touching the routes\n");
        sb.append("- tui_infra lists infra services (brokers, databases) and their logs\n");
        sb.append("- Never restart, stop or kill an integration or infra service unless the user explicitly ");
        sb.append("asked for that\n");
        sb.append("- If a tool call returns an error, do not repeat it with the same arguments; ");
        sb.append("say what failed and what to try\n");
        sb.append("- To feed a route that consumes from a broker (MQTT, Kafka, JMS), tui_send_message can publish ");
        sb.append("to the broker with the route's own component and options\n");
        sb.append("- To edit: camel_get_files, then camel_edit_file (several places at once with edits), camel_write_file ");
        sb.append("for a new file; the user confirms, never ");
        sb.append(
                "retry a rejected write. Invalid YAML/properties is refused with errors: fix them (camel_catalog_doc has the ");
        sb.append("option names)\n");
        sb.append("- Write files only with these tools; never paste file contents in the answer\n");
        sb.append("- YAML DSL shape: a step is `- log: {message: ...}`, `- to: {uri: ...}`, an expression goes under ");
        sb.append("expression: (`- setBody: {expression: {simple: ...}}`); the shorthand forms are deprecated\n");
        sb.append("- 'log at WARN' in a route is the log step's loggingLevel in the source");
        sb.append(useCoreTools(client) ? "\n" : "; tui_set_log_level is the app's root logger, only when asked\n");
        sb.append("- Simple: functions inside ${...}, operators between them: ${header.a} == 'b', ");
        sb.append("${body} ?: 'none'; camel_eval_expression checks one, camel_catalog_doc simple lists them\n");
        if (!useCoreTools(client)) {
            sb.append("- Use tui_locate + tui_draw_shape to visually highlight problems on screen for the user\n");
        }
        if (mcpServerActive) {
            sb.append("\nThe TUI MCP server is available at http://localhost:")
                    .append(mcpServerPort).append("/mcp for external AI agents.");
        }
        // last, so the prefix above stays cached when the groups change
        if (useCoreTools(client) && !toolGroups.guidance().isEmpty()) {
            sb.append(mcpServerActive ? "\n" : "").append("\nThe selected integration:\n");
            toolGroups.guidance().forEach(line -> sb.append("- ").append(line).append('\n'));
        }
        return sb.toString();
    }

    /**
     * Loads the tool groups of the selected integration for the core set (CAMEL-24834): the SQL tools when it has a
     * database, the guidance for its tracing and circuit breakers. Read again only when another integration is selected
     * or the selected one reloaded (the groups then only grow: what it had before still counts), so the tools and the
     * prompt stay the same from question to question and a local model's prompt cache keeps working. While an
     * integration has no groups yet its status is read again, since one that just started may not have written it
     * completely. Full mode also uses these features to discover semantic audit history.
     */
    void refreshToolGroups(McpFacade facade) {
        AppStatusSource source = appStatusSource != null ? appStatusSource : facadeStatusSource(facade);
        String pid = source != null ? source.selectedPid() : null;
        int reloads = source != null ? source.reloadCount() : 0;
        boolean samePid = Objects.equals(pid, toolGroupsPid);
        if (samePid && reloads == toolGroupsReloads && !toolGroups.groups().isEmpty()) {
            return;
        }
        AppFeatures read = pid != null ? source.features() : AppFeatures.none();
        toolGroupsFeatures = samePid ? toolGroupsFeatures.merge(read) : read;
        toolGroupsPid = pid;
        toolGroupsReloads = reloads;
        toolGroups = TuiToolGroups.select(toolGroupsFeatures);
    }

    private static AppStatusSource facadeStatusSource(McpFacade facade) {
        if (facade == null) {
            return null;
        }
        return new AppStatusSource() {
            @Override
            public String selectedPid() {
                return facade.getSelectedPid();
            }

            @Override
            public int reloadCount() {
                return facade.getSelectedReloadCount();
            }

            @Override
            public AppFeatures features() {
                return AppFeatures.fromStatus(facade.readSelectedStatus());
            }
        };
    }

    /**
     * Prefixes the question with the integration the user is looking at. This used to live in the system prompt, but
     * there it invalidated the model's cached prompt prefix every time the selection changed.
     */
    static String contextualize(McpFacade facade, String question) {
        String selectedName = facade != null ? facade.getSelectedIntegrationName() : null;
        String selectedPid = facade != null ? facade.getSelectedPid() : null;
        if (selectedName != null && selectedPid != null) {
            return "[Monitoring " + selectedName + " (PID " + selectedPid + ")]\n" + question;
        }
        return question;
    }

    /**
     * Whether only the {@link TuiToolRegistry#CORE_TOOLS} are sent: always in {@code core} mode, never in {@code full}
     * mode, and for local providers in {@code auto} mode.
     */
    boolean useCoreTools(LlmClient client) {
        String mode = toolMode == null ? AiPanel.TOOL_MODE_AUTO : toolMode;
        if (AiPanel.TOOL_MODE_CORE.equals(mode)) {
            return true;
        }
        if (AiPanel.TOOL_MODE_FULL.equals(mode)) {
            return false;
        }
        return client != null && client.isLocalEndpoint();
    }

    String describe(LlmClient client) {
        if (toolRegistry == null) {
            return "no tools available";
        }
        int total = toolRegistry.getToolDefinitions().size();
        int active = useCoreTools(client)
                ? toolRegistry.getCoreToolDefinitions(toolGroups.tools()).size()
                : toolRegistry.getAvailableToolDefinitions(toolGroups.tools()).size();
        String mode = toolMode == null ? AiPanel.TOOL_MODE_AUTO : toolMode;
        String detail = AiPanel.TOOL_MODE_AUTO.equals(mode)
                ? (useCoreTools(client) ? " (local provider)" : " (hosted provider)") : "";
        String groups = "";
        if (useCoreTools(client)) {
            groups = toolGroups.groups().isEmpty()
                    ? "; groups: none loaded"
                    : "; groups: " + toolGroups.groupIds() + " (from the selected integration)";
        }
        return (useCoreTools(client) ? "core" : "full") + " (" + active + " of " + total + " tools), mode " + mode + detail
               + groups;
    }

    List<LlmClient.ToolDef> definitions(LlmClient client) {
        if (toolRegistry == null) {
            return List.of();
        }
        List<LlmClient.ToolDef> defs = new ArrayList<>();
        List<TuiToolRegistry.ToolDef> source = useCoreTools(client)
                ? toolRegistry.getCoreToolDefinitions(toolGroups.tools())
                : toolRegistry.getAvailableToolDefinitions(toolGroups.tools());
        for (TuiToolRegistry.ToolDef td : source) {
            defs.add(new LlmClient.ToolDef(td.name(), td.description(), td.inputSchema()));
        }
        return defs;
    }

    long schemaChars(LlmClient client) {
        long chars = 0;
        for (LlmClient.ToolDef def : definitions(client)) {
            chars += 40 + def.name().length() + (def.description() != null ? def.description().length() : 0)
                     + (def.parameters() != null ? def.parameters().toJson().length() : 0);
        }
        return chars;
    }
}
