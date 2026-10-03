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

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the size of the static prefix the AI panel sends with every request (system prompt plus tool schemas). A local
 * model pays for every token of it in prompt-processing time on every question, so a regression here is a latency
 * regression for everyone running Ollama. The budgets leave headroom over the measured values; if a change genuinely
 * needs more, raise the budget in the same commit and say why.
 */
class AiPanelPromptBudgetTest {

    /** Measured ~3.0k tokens for 19 core tools. */
    // raised from 3500 when file editing (tui_write_file and its guidance) joined the core set for local models,
    // and from 3800 when tui_catalog_doc gained the endpoint argument (validates a URI, the endpoint counterpart of
    // tui_eval_expression for simple)
    // and from 3900 when the authoring tools became the camel_* set shared with camel-jbang-mcp (CAMEL-24695):
    // their schemas carry the directory and name arguments a server without a selection needs, and
    // camel_error_diagnose joined the core set
    // raised to 5000 when camel_catalog_find joined the core set (CAMEL-24760): the budget guards against accidental
    // growth of the prefix, a 32k context leaves ample room
    static final int CORE_BUDGET_TOKENS = 5_000;
    /** Measured ~6.9k tokens for 47 tools. */
    // raised from 7500 with tui_write_file and tui_validate_source
    // and from 7900 with the shared camel_* set (camel_catalog_find, camel_run and camel_error_diagnose added)
    // and from 8500 when camel_catalog_doc gained the api kind (CAMEL-24708): its kind argument names the core
    // classes and script languages the API reference covers, which is what makes a model ask for them
    // raised with the core budget (CAMEL-24760)
    // raised from 9200 when camel_control gained the reload action (CAMEL-24861): main was ~9180 already; the
    // camel_run and camel_control descriptions were shortened in the same change
    // raised from 9300 for camel_edit_file (CAMEL-24909), the tool that changes a file without rewriting it: it
    // saves far more tokens per edit than its schema costs once
    // raised from 9450 for camel_project_overview and camel_save_project_summary (CAMEL-25143), measured ~9750:
    // full mode only (hosted models), and the panel's own /overview sends no tools at all
    static final int FULL_BUDGET_TOKENS = 9_850;
    /** Measured ~5.0k tokens for 25 tools: the core set (~4.7k) plus every tool group (CAMEL-24834). */
    // the SQL group adds tui_execute_sql (~190 tokens), each group one guidance line in the prompt (~140 for all
    // three); an integration rarely has all three, and the groups only load for the integration that needs them
    static final int CORE_WITH_GROUPS_BUDGET_TOKENS = 5_300;

    record Prefix(String mode, int tools, long promptChars, long toolChars) {

        int promptTokens() {
            return AiPanel.estimateTokens(promptChars);
        }

        int toolTokens() {
            return AiPanel.estimateTokens(toolChars);
        }

        int totalTokens() {
            return promptTokens() + toolTokens();
        }

        @Override
        public String toString() {
            return String.format("%-4s tools=%2d  system prompt ~%d tok  tool schemas ~%d tok  total ~%d tok",
                    mode, tools, promptTokens(), toolTokens(), totalTokens());
        }
    }

    /**
     * Serializes the tools the way {@code LlmClient.buildOpenAiStyleTools} sends them, so the count matches the wire.
     */
    static long wireChars(List<LlmClient.ToolDef> defs) {
        long chars = 0;
        for (LlmClient.ToolDef def : defs) {
            JsonObject function = new JsonObject();
            function.put("name", def.name());
            function.put("description", def.description());
            function.put("parameters", def.parameters());
            JsonObject tool = new JsonObject();
            tool.put("type", "function");
            tool.put("function", function);
            chars += tool.toJson().length();
        }
        return chars;
    }

    static Prefix measure(String mode) {
        AiPanel panel = new AiPanel();
        panel.setToolRegistryForTesting(new TuiToolRegistry(null));
        panel.setToolModeForTesting(mode);
        return measure(mode, panel);
    }

    static Prefix measure(String mode, AiPanel panel) {
        List<LlmClient.ToolDef> defs = panel.toolDefinitionsForTesting();
        return new Prefix(mode, defs.size(), panel.systemPromptForTesting().length(), wireChars(defs));
    }

    /** A core panel with every tool group loaded: datasources, OpenTelemetry, tracing, Micrometer, circuit breakers. */
    static AiPanel coreWithAllGroups(boolean sqlWrites) {
        AiPanelToolGroupsTest.FakeApp app = new AiPanelToolGroupsTest.FakeApp();
        app.features = AiPanelToolGroupsTest.EVERYTHING;
        AiPanel panel = AiPanelToolGroupsTest.panel(AiPanel.TOOL_MODE_CORE, app, sqlWrites);
        panel.refreshToolGroupsForTesting();
        return panel;
    }

    @Test
    void corePrefixStaysWithinBudget() {
        Prefix core = measure(AiPanel.TOOL_MODE_CORE);
        System.out.println("AI panel static prefix: " + core);

        assertTrue(core.totalTokens() <= CORE_BUDGET_TOKENS,
                "core prefix grew to ~" + core.totalTokens() + " tokens, budget " + CORE_BUDGET_TOKENS + ": " + core);
    }

    @Test
    void coreWithAllGroupsPrefixStaysWithinBudget() {
        Prefix groups = measure("core+groups", coreWithAllGroups(false));
        System.out.println("AI panel static prefix: " + groups);

        assertTrue(groups.totalTokens() <= CORE_WITH_GROUPS_BUDGET_TOKENS,
                "core prefix with all groups grew to ~" + groups.totalTokens() + " tokens, budget "
                                                                           + CORE_WITH_GROUPS_BUDGET_TOKENS + ": "
                                                                           + groups);
    }

    @Test
    void fullPrefixStaysWithinBudget() {
        Prefix full = measure(AiPanel.TOOL_MODE_FULL);
        System.out.println("AI panel static prefix: " + full);

        assertTrue(full.totalTokens() <= FULL_BUDGET_TOKENS,
                "full prefix grew to ~" + full.totalTokens() + " tokens, budget " + FULL_BUDGET_TOKENS + ": " + full);
    }

    @Test
    void systemPromptStaysShortAndFreeOfTheToolList() {
        AiPanel panel = new AiPanel();
        panel.setToolRegistryForTesting(new TuiToolRegistry(null));
        panel.setToolModeForTesting(AiPanel.TOOL_MODE_FULL);
        String prompt = panel.systemPromptForTesting();

        // the tool definitions already describe every tool; repeating them in prose doubles the cost
        // 450 before the file editing guidance (two bullets) was added
        // 530 before the tools were split into camel_* and tui_* in the introduction
        // 545 before the file-write and canonical YAML shape lines (CAMEL-24760)
        assertTrue(AiPanel.estimateTokens(prompt.length()) <= 620,
                "system prompt grew to ~" + AiPanel.estimateTokens(prompt.length()) + " tokens");
        assertTrue(!prompt.contains("- tui_get_table:"), "system prompt must not list the tools again");
    }

    @Test
    void thePromptOnlyMentionsTheToolsOfTheActiveSet() {
        // tui_set_log_level left the core set (CAMEL-24760): its prompt line goes with it, a local model must not be
        // told about a tool it cannot call
        AiPanel panel = new AiPanel();
        panel.setToolRegistryForTesting(new TuiToolRegistry(null));
        panel.setToolModeForTesting(AiPanel.TOOL_MODE_CORE);
        String core = panel.systemPromptForTesting();
        panel.setToolModeForTesting(AiPanel.TOOL_MODE_FULL);
        String full = panel.systemPromptForTesting();

        assertTrue(full.contains("tui_set_log_level is the app's root logger"), "the full set has the tool");
        assertTrue(!core.contains("tui_set_log_level"), "the core set has not");
        // what both sets get: the file write rule and the canonical YAML shape
        for (String prompt : List.of(core, full)) {
            assertTrue(prompt.contains("camel_write_file"), "write files with the tool");
        }

        // CAMEL-24834: the guidance of the tool groups only names tools the model is given, with SQL writes or not
        for (boolean sqlWrites : List.of(false, true)) {
            AiPanel groups = coreWithAllGroups(sqlWrites);
            String prompt = groups.systemPromptForTesting();
            int start = prompt.indexOf("The selected integration:");
            assertTrue(start > 0, "the guidance is appended at the end");
            Set<String> tools = groups.toolDefinitionsForTesting().stream().map(LlmClient.ToolDef::name)
                    .collect(Collectors.toSet());
            Matcher m = Pattern.compile("\\b(?:tui|camel)_[a-z_]+").matcher(prompt.substring(start));
            int named = 0;
            while (m.find()) {
                named++;
                assertTrue(tools.contains(m.group()), m.group() + " is named in the guidance but not in the set");
            }
            assertTrue(named >= 4, "the guidance names the tools to use");
            assertTrue(prompt.contains("tui_update_row") == sqlWrites, "tui_update_row only with SQL writes");
        }
    }
}
