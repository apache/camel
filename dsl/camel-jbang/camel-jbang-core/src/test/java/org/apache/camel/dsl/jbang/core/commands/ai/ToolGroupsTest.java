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
        ToolGroups.Selection s = ToolGroups.select(AppFeatures.none());
        assertTrue(s.groups().isEmpty());
        assertTrue(s.mcpTools().isEmpty());
        assertTrue(s.guidance().isEmpty());
        assertEquals("|", s.fingerprint());
    }

    @Test
    void eachGroupHasItsTools() {
        ToolGroups.Selection s = ToolGroups.select(everything());
        assertEquals(List.of(ToolGroup.SQL, ToolGroup.TRACING, ToolGroup.RESILIENCE), s.toolGroups());
        assertEquals(List.of("camel_runtime_sql", "camel_runtime_datasources", "camel_runtime_sql_trace"),
                s.groups().get(0).tools());
        assertEquals(List.of("camel_runtime_spans", "camel_runtime_trace", "camel_runtime_metrics"),
                s.groups().get(1).tools());
        assertEquals(List.of("camel_runtime_circuit_breakers"), s.groups().get(2).tools());
    }

    @Test
    void micrometerAloneLoadsTheTracingGroupWithTheMetrics() {
        AppFeatures micrometer = new AppFeatures(
                List.of(), List.of(), false, false, List.of(), false, false, true, Map.of());
        ToolGroups.Selection s = ToolGroups.select(micrometer);
        assertEquals(List.of(ToolGroup.TRACING), s.toolGroups());
        assertEquals(List.of("camel_runtime_trace", "camel_runtime_metrics"), s.groups().get(0).tools());
        assertTrue(s.groups().get(0).guidance().contains("camel_runtime_metrics has the Micrometer metrics"));
    }

    @Test
    void theNewerFeaturesWinWhenMerged() {
        AppFeatures before = new AppFeatures(
                List.of(new AppFeatures.DataSource("orders", null)), List.of(), false, false, List.of(), false, false,
                false, Map.of("dataSources", "orders"));
        AppFeatures after = new AppFeatures(
                List.of(new AppFeatures.DataSource("orders", "HikariCP")), List.of(), false, false, List.of(), false, false,
                false, Map.of("dataSources", "orders (HikariCP)"));
        AppFeatures merged = before.merge(after);
        assertEquals("HikariCP", merged.dataSources().get(0).poolType(), "the pool type known after the pool started");
        assertEquals("orders (HikariCP)", merged.signals().get("dataSources"));
    }

    @Test
    void tracingOffersOnlyWhatIsOn() {
        AppFeatures messageTracing = new AppFeatures(
                List.of(), List.of(), false, false, List.of(), false, true, false, Map.of());
        ToolGroups.Selection s = ToolGroups.select(messageTracing);
        assertEquals(List.of("camel_runtime_trace"), s.mcpTools());
        assertTrue(s.guidance().get(0).contains("message tracing is on"), s.guidance().get(0));
    }

    @Test
    void theGuidanceNamesWhatTheIntegrationHas() {
        List<String> guidance = ToolGroups.select(everything()).guidance();
        assertEquals(3, guidance.size());
        assertEquals("SQL: datasource(s) orders (HikariCP), audit, used by sql endpoints. Table names come from the"
                     + " SQL trace (camel_runtime_sql_trace); don't guess a schema.",
                guidance.get(0));
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
        String fp = ToolGroups.select(everything()).fingerprint();
        assertEquals("resilience,sql,tracing|audit,orders", fp);
        assertEquals(fp, ToolGroups.select(reordered).fingerprint());
        assertNotEquals(fp, ToolGroups.select(AppFeatures.none()).fingerprint());
    }

    static HttpEndpoints.Served stockApi(int port) {
        return new HttpEndpoints.Served(
                port, "/api", "stock-api.json", List.of(new HttpEndpoints.Endpoint(
                        "GET", "/api/stock/{sku}", null, "application/json", "stock", "getStock", "rest", false)),
                Map.of("rests", "1 service(s)"));
    }

    @Test
    void httpLoadsTheEndpointAndRequestTools() {
        // CAMEL-25307
        ToolGroups.Selection s = ToolGroups.select(AppFeatures.none().withHttp(stockApi(8080)));
        assertEquals(List.of(ToolGroup.HTTP), s.toolGroups());
        assertEquals(List.of("camel_runtime_http_endpoints", "camel_runtime_http_request"), s.mcpTools());
        assertEquals("HTTP: served on http://localhost:8080/api (contract stock-api.json); camel_runtime_http_request"
                     + " calls it, camel_runtime_http_endpoints lists the operations.",
                s.guidance().get(0));
        assertEquals("http||http://localhost:8080/api", s.fingerprint());
        assertEquals(s.fingerprint(), ToolGroups.select(AppFeatures.none().withHttp(stockApi(8080))).fingerprint());
        assertNotEquals(s.fingerprint(), ToolGroups.select(AppFeatures.none().withHttp(stockApi(9090))).fingerprint());
        assertTrue(ToolGroups.select(AppFeatures.none().withHttp(stockApi(0))).guidance().get(0)
                .contains("the port is not known yet"));
    }

    @Test
    void httpComesLastSoTheOtherGroupsKeepTheirPlace() {
        ToolGroups.Selection s = ToolGroups.select(everything().withHttp(stockApi(8080)));
        assertEquals(List.of(ToolGroup.SQL, ToolGroup.TRACING, ToolGroup.RESILIENCE, ToolGroup.HTTP), s.toolGroups());
        assertEquals("http,resilience,sql,tracing|audit,orders|http://localhost:8080/api", s.fingerprint());
    }
}
