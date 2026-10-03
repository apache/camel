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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolGroupsTest {

    static AppFeatures everything() {
        return new AppFeatures(
                List.of(new AppFeatures.DataSource("orders", "HikariCP"), new AppFeatures.DataSource("audit", null)),
                List.of("sql"), true, true, List.of("pay", "ship"), true, true, true, Map.of());
    }

    @Test
    void nothingLoadsNoGroup() {
        ToolGroups.Selection s = ToolGroups.select(AppFeatures.none(), false);
        assertTrue(s.groups().isEmpty());
        assertTrue(s.mcpTools().isEmpty());
        assertTrue(s.guidance().isEmpty());
        assertEquals("||", s.fingerprint());
    }

    @Test
    void eachGroupHasItsTools() {
        ToolGroups.Selection s = ToolGroups.select(everything(), false);
        assertEquals(List.of(ToolGroup.SQL, ToolGroup.TRACING, ToolGroup.RESILIENCE), s.toolGroups());
        assertEquals(List.of("camel_runtime_sql_query", "camel_runtime_datasources", "camel_runtime_sql_trace"),
                s.groups().get(0).tools());
        assertEquals(List.of("camel_runtime_spans", "camel_runtime_trace", "camel_runtime_metrics"),
                s.groups().get(1).tools());
        assertEquals(List.of("camel_runtime_circuit_breakers"), s.groups().get(2).tools());
        assertTrue(s.sqlReadOnly());
        assertFalse(s.mcpTools().contains("camel_runtime_sql"), "read-only SQL has no tool that writes");
    }

    @Test
    void sqlWritesAddTheToolThatWrites() {
        ToolGroups.Selection s = ToolGroups.select(everything(), true);
        assertFalse(s.sqlReadOnly());
        assertTrue(s.mcpTools().containsAll(List.of("camel_runtime_sql_query", "camel_runtime_sql")));
        assertTrue(s.guidance().get(0).contains("camel_runtime_sql also writes"), s.guidance().get(0));
    }

    @Test
    void tracingOffersOnlyWhatIsOn() {
        AppFeatures messageTracing = new AppFeatures(
                List.of(), List.of(), false, false, List.of(), false, true, false, Map.of());
        ToolGroups.Selection s = ToolGroups.select(messageTracing, false);
        assertEquals(List.of("camel_runtime_trace"), s.mcpTools());
        assertTrue(s.guidance().get(0).contains("message tracing is on"), s.guidance().get(0));
    }

    @Test
    void theGuidanceNamesWhatTheIntegrationHas() {
        List<String> guidance = ToolGroups.select(everything(), false).guidance();
        assertEquals(3, guidance.size());
        assertTrue(guidance.get(0).startsWith("SQL: datasource(s) orders (HikariCP), audit, used by sql endpoints."),
                guidance.get(0));
        assertTrue(guidance.get(0).contains("Read-only: SELECT only"), guidance.get(0));
        assertTrue(guidance.get(0).contains("don't guess a schema"), guidance.get(0));
        assertTrue(guidance.get(1).contains("OpenTelemetry"), guidance.get(1));
        assertTrue(guidance.get(2).startsWith("Circuit breakers in routes pay, ship: camel_runtime_circuit_breakers"),
                guidance.get(2));
        for (String line : guidance) {
            assertFalse(line.contains("\n"), "one line per group: " + line);
        }
    }

    @Test
    void theFingerprintIsStable() {
        AppFeatures reordered = new AppFeatures(
                List.of(new AppFeatures.DataSource("audit", null), new AppFeatures.DataSource("orders", "HikariCP")),
                List.of("sql"), true, true, List.of("ship", "pay"), true, true, true, Map.of("x", "y"));
        String fp = ToolGroups.select(everything(), false).fingerprint();
        assertEquals("resilience,sql,tracing|audit,orders|ro", fp);
        assertEquals(fp, ToolGroups.select(reordered, false).fingerprint());
        assertNotEquals(fp, ToolGroups.select(everything(), true).fingerprint(), "the SQL mode changes the tools");
    }
}
