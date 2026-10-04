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

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppFeaturesTest {

    private static AppFeatures features(String json) throws Exception {
        return AppFeatures.fromStatus((JsonObject) Jsoner.deserialize(json.replace('\'', '"')));
    }

    @Test
    void aDatasourceIsSql() throws Exception {
        AppFeatures f = features("{'dataSources': {'dataSources': [{'name': 'orders', 'type': 'com.zaxxer.hikari"
                                 + ".HikariDataSource', 'poolType': 'HikariCP'}]}}");
        assertTrue(f.sql());
        assertEquals(List.of(new AppFeatures.DataSource("orders", "HikariCP")), f.dataSources());
        assertEquals("orders", f.signals().get("dataSources"));
        assertFalse(f.tracing());
        assertFalse(f.circuitBreaker());
    }

    @Test
    void anEmptyDataSourcesArrayIsNotSql() throws Exception {
        AppFeatures f = features("{'dataSources': {'dataSources': []}, 'sqlTrace': {}}");
        assertFalse(f.sql());
        assertTrue(f.signals().isEmpty());
        assertTrue(ToolGroups.groups(f).isEmpty());
    }

    @Test
    void sqlEndpointsAndTracedStatementsAreSql() throws Exception {
        AppFeatures f = features("{'endpoints': {'endpoints': [{'uri': 'jdbc://default'}, {'uri': 'timer://tick'}]},"
                                 + " 'routes': [{'routeId': 'r1', 'from': 'sql:select * from orders'}],"
                                 + " 'sqlTrace': {'statements': [{'query': 'select * from orders',"
                                 + " 'endpoint': 'sql-stored:proc'}]}}");
        assertTrue(f.sql());
        assertEquals(List.of("jdbc", "sql", "sql-stored"), f.sqlComponents());
        assertTrue(f.sqlTraced());
        assertTrue(f.dataSources().isEmpty());
    }

    @Test
    void aCircuitBreakerProcessorNamesItsRoute() throws Exception {
        AppFeatures f = features("{'routes': [{'routeId': 'pay', 'processors': [{'routeId': 'pay', 'id': 'cb1',"
                                 + " 'processor': 'circuitBreaker'}, {'processor': 'to', 'uri': 'http://x'}]},"
                                 + " {'routeId': 'audit', 'processors': [{'processor': 'log'}]}]}");
        assertTrue(f.circuitBreaker());
        assertEquals(List.of("pay"), f.circuitBreakerRoutes());
        assertEquals("circuitBreaker in pay", f.signals().get("routes.processors"));
    }

    @Test
    void aResilience4jSectionWithBreakersIsResilience() throws Exception {
        AppFeatures f = features("{'resilience4j': {'circuitBreakers': [{'routeId': 'pay', 'id': 'cb1',"
                                 + " 'state': 'OPEN'}]}, 'circuit-breaker': {'circuitBreakers': []}}");
        assertTrue(f.circuitBreaker());
        assertEquals(List.of("pay"), f.circuitBreakerRoutes());
        assertEquals("1 circuit breaker(s)", f.signals().get("resilience4j"));
        assertFalse(f.signals().containsKey("circuit-breaker"), "an empty section says nothing");
    }

    @Test
    void theOpenTelemetryConsoleIsTracing() throws Exception {
        AppFeatures f = features("{'devConsoles': ['context', 'route', 'opentelemetry']}");
        assertTrue(f.openTelemetry());
        assertTrue(f.tracing());
        assertFalse(f.micrometer());
        assertEquals("opentelemetry", f.signals().get("devConsoles"));
    }

    @Test
    void enabledMessageTracingIsTracing() throws Exception {
        assertTrue(features("{'trace': {'enabled': true}}").messageTracing());
        assertFalse(features("{'trace': {'enabled': false, 'standby': true}}").tracing(),
                "the trace console is always there, only enabled tracing counts");
    }

    @Test
    void micrometerIsTracing() throws Exception {
        assertTrue(features("{'micrometer': {'counters': []}}").micrometer());
        assertTrue(features("{'devConsoles': ['micrometer']}").micrometer());
    }

    @Test
    void anOldStatusWithoutConsolesOrProcessorsGivesFewerFeatures() throws Exception {
        // a status file of an older Camel: no devConsoles, routes without processors, odd types
        AppFeatures f = features("{'context': {'name': 'old'}, 'routes': [{'routeId': 'r1', 'from': 'timer:x'}],"
                                 + " 'dataSources': 'n/a', 'resilience4j': [], 'trace': 'off'}");
        assertEquals(AppFeatures.none().dataSources(), f.dataSources());
        assertFalse(f.sql());
        assertFalse(f.tracing());
        assertFalse(f.circuitBreaker());
        assertFalse(AppFeatures.fromStatus(null).sql());
    }

    @Test
    void mergeKeepsWhatEitherHad() throws Exception {
        AppFeatures before = features("{'dataSources': {'dataSources': [{'name': 'orders'}]},"
                                      + " 'devConsoles': ['opentelemetry']}");
        AppFeatures after = features("{'resilience4j': {'circuitBreakers': [{'routeId': 'pay'}]}}");
        AppFeatures merged = before.merge(after);
        assertTrue(merged.sql());
        assertTrue(merged.openTelemetry());
        assertTrue(merged.circuitBreaker());
        assertEquals(List.of("orders"), merged.dataSourceNames());
        assertEquals(merged, merged.merge(AppFeatures.none()));
    }
}
