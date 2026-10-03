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
import java.util.Map;

import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.apache.camel.dsl.jbang.core.commands.ai.AppFeatures;
import org.apache.camel.dsl.jbang.core.commands.ai.ToolGroup;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-24834: the AI panel loads the tool groups of the selected integration into the core set, and reads them again
 * only when the integration or its routes change.
 */
class AiPanelToolGroupsTest {

    static final AppFeatures SQL = new AppFeatures(
            List.of(new AppFeatures.DataSource("orders", "HikariCP")), List.of("sql"), true, false, List.of(),
            false, false, false, Map.of());
    static final AppFeatures BREAKERS = new AppFeatures(
            List.of(), List.of(), false, true, List.of("pay"), false, false, false, Map.of());
    static final AppFeatures OTEL = new AppFeatures(
            List.of(), List.of(), false, false, List.of(), true, false, false, Map.of());
    static final AppFeatures EVERYTHING = SQL.merge(BREAKERS).merge(new AppFeatures(
            List.of(), List.of(), false, false, List.of(), true, true, true, Map.of()));

    /** A selected integration whose pid, reload count and features a test changes, counting the status reads. */
    static final class FakeApp implements AiPanel.AppStatusSource {
        String pid = "100";
        int reloads;
        AppFeatures features = SQL;
        int reads;

        @Override
        public String selectedPid() {
            return pid;
        }

        @Override
        public int reloadCount() {
            return reloads;
        }

        @Override
        public AppFeatures features() {
            reads++;
            return features;
        }
    }

    static AiPanel panel(String mode, FakeApp app, boolean sqlWrites) {
        AiPanel panel = new AiPanel();
        panel.setToolRegistryForTesting(new TuiToolRegistry(null));
        panel.setToolModeForTesting(mode);
        panel.setAppStatusSourceForTesting(app);
        panel.setSqlWritesForTesting(sqlWrites);
        return panel;
    }

    static List<String> toolNames(AiPanel panel) {
        return panel.toolDefinitionsForTesting().stream().map(LlmClient.ToolDef::name).toList();
    }

    @Test
    void theSqlGroupAddsTheQueryToolOnly() {
        AiPanel panel = panel(AiPanel.TOOL_MODE_CORE, new FakeApp(), false);
        assertFalse(toolNames(panel).contains("tui_execute_sql"), "no group before the first question");

        panel.refreshToolGroupsForTesting();
        assertEquals(List.of(ToolGroup.SQL), panel.toolGroupsForTesting().groups());
        assertTrue(toolNames(panel).contains("tui_execute_sql"));
        assertFalse(toolNames(panel).contains("tui_update_row"), "writes are off");
        assertTrue(panel.systemPromptForTesting().contains("Read-only: tui_execute_sql runs SELECT only"));
        assertTrue(panel.describeToolModeForTesting().contains("groups: sql (from the selected integration)"),
                panel.describeToolModeForTesting());
        assertTrue(panel.describeToolModeForTesting().contains("SQL read-only"));

        AiPanel writes = panel(AiPanel.TOOL_MODE_CORE, new FakeApp(), true);
        writes.refreshToolGroupsForTesting();
        assertTrue(toolNames(writes).containsAll(List.of("tui_execute_sql", "tui_update_row")));
        assertFalse(writes.describeToolModeForTesting().contains("SQL read-only"));
    }

    @Test
    void anotherIntegrationGetsItsOwnGroups() {
        FakeApp app = new FakeApp();
        AiPanel panel = panel(AiPanel.TOOL_MODE_CORE, app, false);
        panel.refreshToolGroupsForTesting();
        assertEquals(List.of(ToolGroup.SQL), panel.toolGroupsForTesting().groups());

        app.pid = "200";
        app.features = OTEL;
        panel.refreshToolGroupsForTesting();
        assertEquals(2, app.reads);
        assertEquals(List.of(ToolGroup.TRACING), panel.toolGroupsForTesting().groups(), "no union across integrations");
        assertFalse(toolNames(panel).contains("tui_execute_sql"));
        assertTrue(panel.systemPromptForTesting().contains("tui_get_spans has the spans"));
    }

    @Test
    void aReloadAddsToTheGroups() {
        FakeApp app = new FakeApp();
        AiPanel panel = panel(AiPanel.TOOL_MODE_CORE, app, false);
        panel.refreshToolGroupsForTesting();

        app.reloads = 1;
        app.features = BREAKERS;
        panel.refreshToolGroupsForTesting();
        assertEquals(2, app.reads);
        assertEquals(List.of(ToolGroup.SQL, ToolGroup.RESILIENCE), panel.toolGroupsForTesting().groups(),
                "what it had before the reload still counts");
        assertTrue(panel.systemPromptForTesting().contains("Circuit breakers in routes pay: tui_get_table tab"));
    }

    @Test
    void theGroupsAreCachedOtherwise() {
        FakeApp app = new FakeApp();
        AiPanel panel = panel(AiPanel.TOOL_MODE_CORE, app, false);
        panel.refreshToolGroupsForTesting();
        String prompt = panel.systemPromptForTesting();
        List<LlmClient.ToolDef> tools = panel.toolDefinitionsForTesting();

        app.features = EVERYTHING;
        for (int i = 0; i < 3; i++) {
            panel.refreshToolGroupsForTesting();
        }
        assertEquals(1, app.reads, "same integration, no reload: the status is not read again");
        assertEquals(prompt, panel.systemPromptForTesting(), "the prompt stays byte-identical");
        assertEquals(tools, panel.toolDefinitionsForTesting());

        // turning SQL writes on changes the tools without reading the status
        panel.setSqlWritesForTesting(true);
        panel.refreshToolGroupsForTesting();
        assertEquals(1, app.reads);
        assertTrue(toolNames(panel).contains("tui_update_row"));
    }

    @Test
    void anIntegrationWithoutGroupsIsReadAgain() {
        // one that just started may not have written its status completely yet
        FakeApp app = new FakeApp();
        app.features = AppFeatures.none();
        AiPanel panel = panel(AiPanel.TOOL_MODE_CORE, app, false);
        panel.refreshToolGroupsForTesting();
        assertTrue(panel.toolGroupsForTesting().groups().isEmpty());
        assertTrue(panel.describeToolModeForTesting().contains("groups: none loaded"));

        app.features = SQL;
        panel.refreshToolGroupsForTesting();
        assertEquals(List.of(ToolGroup.SQL), panel.toolGroupsForTesting().groups());
    }

    @Test
    void theFullSetIsUnchanged() {
        FakeApp app = new FakeApp();
        app.features = EVERYTHING;
        AiPanel plain = panel(AiPanel.TOOL_MODE_FULL, new FakeApp(), false);
        AiPanel panel = panel(AiPanel.TOOL_MODE_FULL, app, false);
        String before = panel.systemPromptForTesting();
        panel.refreshToolGroupsForTesting();

        assertEquals(0, app.reads, "the full set does not need the status");
        assertEquals(before, panel.systemPromptForTesting());
        assertFalse(panel.systemPromptForTesting().contains("The selected integration"));
        assertEquals(toolNames(plain), toolNames(panel));
        assertTrue(toolNames(panel).contains("tui_update_row"));
        // and writing is not limited there
        String answer = panel.executeTuiToolForTesting("tui_execute_sql", query("DELETE FROM orders"));
        assertFalse(answer.contains("read-only"), answer);
    }

    @Test
    void theCoreSetRefusesSqlThatWrites() {
        AiPanel panel = panel(AiPanel.TOOL_MODE_CORE, new FakeApp(), false);
        panel.refreshToolGroupsForTesting();

        String answer = panel.executeTuiToolForTesting("tui_execute_sql", query("INSERT INTO orders VALUES (1)"));
        assertTrue(answer.startsWith("Error: read-only: "), answer);
        assertTrue(answer.contains("camel.tui.ai.sqlWrites=true"), answer);
        answer = panel.executeTuiToolForTesting("tui_update_row", new JsonObject());
        assertTrue(answer.startsWith("Error: read-only: tui_update_row"), answer);
        // a read goes on to the tool (which has no integration here)
        answer = panel.executeTuiToolForTesting("tui_execute_sql", query("SELECT * FROM orders"));
        assertFalse(answer.contains("read-only"), answer);

        AiPanel writes = panel(AiPanel.TOOL_MODE_CORE, new FakeApp(), true);
        writes.refreshToolGroupsForTesting();
        answer = writes.executeTuiToolForTesting("tui_execute_sql", query("INSERT INTO orders VALUES (1)"));
        assertFalse(answer.contains("read-only"), answer);
    }

    private static JsonObject query(String sql) {
        JsonObject args = new JsonObject();
        args.put("query", sql);
        return args;
    }
}
