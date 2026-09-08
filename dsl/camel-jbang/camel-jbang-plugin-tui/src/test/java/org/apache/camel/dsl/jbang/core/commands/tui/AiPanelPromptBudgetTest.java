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
    static final int CORE_BUDGET_TOKENS = 3_500;
    /** Measured ~6.9k tokens for 47 tools. */
    static final int FULL_BUDGET_TOKENS = 7_500;

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
        List<LlmClient.ToolDef> defs = panel.toolDefinitionsForTesting();
        return new Prefix(mode, defs.size(), panel.systemPromptForTesting().length(), wireChars(defs));
    }

    @Test
    void corePrefixStaysWithinBudget() {
        Prefix core = measure(AiPanel.TOOL_MODE_CORE);
        System.out.println("AI panel static prefix: " + core);

        assertTrue(core.totalTokens() <= CORE_BUDGET_TOKENS,
                "core prefix grew to ~" + core.totalTokens() + " tokens, budget " + CORE_BUDGET_TOKENS + ": " + core);
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
        assertTrue(AiPanel.estimateTokens(prompt.length()) <= 450,
                "system prompt grew to ~" + AiPanel.estimateTokens(prompt.length()) + " tokens");
        assertTrue(!prompt.contains("- tui_get_table:"), "system prompt must not list the tools again");
    }
}
