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
package org.apache.camel.semantic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.management.ObjectName;

import org.apache.camel.console.DevConsole;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.semantic.internal.SemanticMonitoringService;
import org.apache.camel.support.EventHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SemanticMonitoringTest {
    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void semanticMonitoringDoesNotEnableExchangeEvents(boolean withMetrics) throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.disableJMX();
            if (withMetrics) {
                context.getRegistry().bind("metrics", new SemanticResultMetrics(
                        "boolean", "Boolean results", Map.of(), SemanticExpert.ResultType.BOOLEAN, event -> true, 2));
            }
            context.start();
            assertThat(context.getCamelContextExtension().isEventNotificationApplicable()).isFalse();
            var supplied = snapshot("counter", Map.of("count", 1));
            assertThat(EventHelper.notifyAiEvent(context,
                    () -> new SemanticMonitoringEvent(supplied))).isTrue();
            assertThat((List<?>) SemanticMonitoringService.get(context).snapshot(null).get("snapshots"))
                    .anySatisfy(value -> assertThat(value).isEqualTo(supplied.toMap()));
        }
    }

    @Test
    void jmxFacadeIsReadOnlySharesCachedValuesAndUnregistersOnStop() throws Exception {
        var context = new DefaultCamelContext();
        var supplied = snapshot("custom-measurement", Map.of("count", 12));
        context.getRegistry().bind("customMetrics", (SemanticMetricSource) () -> List.of(supplied));
        context.start();
        try {
            var service = SemanticMonitoringService.get(context);
            var agent = context.getManagementStrategy().getManagementAgent();
            assertThat(agent).isNotNull();
            var server = agent.getMBeanServer();
            var pattern = new ObjectName(
                    agent.getMBeanObjectDomainName() + ":context="
                                         + ObjectName.quote(context.getManagementName()) + ",type=semantic,*");
            var names = server.queryNames(pattern, null);
            assertThat(names).hasSize(2);
            for (ObjectName name : names) {
                assertThat(server.getMBeanInfo(name).getOperations()).isEmpty();
                assertThat(server.getMBeanInfo(name).getAttributes()).allSatisfy(attribute -> {
                    assertThat(attribute.isReadable()).isTrue();
                    assertThat(attribute.isWritable()).isFalse();
                });
                var json = (JsonObject) Jsoner.deserialize((String) server.getAttribute(name, "Snapshot"));
                var expected = (JsonObject) Jsoner.deserialize(new JsonObject(service.snapshot(null)).toJson());
                assertThat(json.get("snapshots")).isEqualTo(expected.get("snapshots"));
            }
            context.stop();
            assertThat(server.queryNames(pattern, null)).isEmpty();
        } finally {
            context.close();
        }
    }

    @Test
    void failedProducerIsVisibleWithoutBreakingOtherSources() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.disableJMX();
            context.getRegistry().bind("badMetrics", (SemanticMetricSource) () -> {
                throw new IllegalStateException("private");
            });
            context.getRegistry().bind("goodMetrics",
                    (SemanticMetricSource) () -> List.of(snapshot("counter", Map.of("count", 3))));
            context.start();
            var service = SemanticMonitoringService.get(context);
            var state = service.snapshot(null);
            assertThat((List<?>) state.get("snapshots")).hasSize(1);
            assertThat((long) state.get("sourceErrors")).isGreaterThan(0);
            assertThat(new JsonObject(state).toJson()).contains("sample_failed", "lastFailure").doesNotContain("private");
        }
    }

    @Test
    void rejectsHistoryOptionsInsteadOfReturningMisleadingCurrentValues() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.disableJMX();
            context.start();
            var console = new SemanticMonitoringConsole();
            console.setCamelContext(context);
            try {
                for (var option : List.of(Map.of("history", true), Map.of("since", "2026-10-14T12:00:00Z"))) {
                    assertThat((JsonObject) console.call(DevConsole.MediaType.JSON, new LinkedHashMap<>(option)))
                            .containsEntry("error", "monitoring_query_invalid").doesNotContainKey("snapshots");
                }
            } finally {
                console.stop();
            }
        }
    }

    @Test
    void customMetricUsesTheSharedCacheWithJmxDisabledAndNoAuditRetention() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.disableJMX();
            var supplied = snapshot("counter", Map.of("count", 9));
            context.getRegistry().bind("expertMetrics", (SemanticMetricSource) () -> List.of(supplied));
            context.start();
            var service = SemanticMonitoringService.get(context);
            var console = new SemanticMonitoringConsole();
            console.setCamelContext(context);
            JsonObject response = (JsonObject) console.call(DevConsole.MediaType.JSON, Map.of());
            assertThat(response).containsEntry("snapshots", List.of(supplied.toMap()));
            assertThat(service.snapshot(null)).isEqualTo(response);
            assertThat(context.getManagementStrategy().getManagementAgent()).isNull();
            assertThat(response).doesNotContainKeys("audit", "stores", "recovery", "historySize");
            console.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "reference", "current", "comparison", "state", "examples" })
    void rejectsMonitoringReportsAndAuditExampleLinks(String field) {
        var fields = new LinkedHashMap<>(snapshot("counter", Map.of("count", 1)).toMap());
        fields.put(field, List.of("example"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SemanticMonitoringSnapshot(fields));
    }

    static SemanticMonitoringSnapshot snapshot(String kind, Map<String, Object> values) {
        return new SemanticMonitoringSnapshot(
                Map.of("id", "population", "source", "domain-expert",
                        "name", "Expert-owned measurement", "meaning", "Supplied meaning", "kind", kind, "unit", "requests",
                        "dimensions", Map.of("taskVersion", "v7"), "values", values));
    }

}
