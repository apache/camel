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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.semantic.internal.SemanticMonitoringService;
import org.apache.camel.support.EventHelper;
import org.junit.jupiter.api.Test;

import static org.apache.camel.semantic.SemanticMonitoringTest.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SemanticMonitoringSamplingTest {
    @Test
    void latestReplacesPreviousValuesAndRetiredSeriesDisappear() throws Exception {
        var initial = snapshot("counter", Map.of("count", 1));
        var changed = snapshot("counter", Map.of("count", 2));
        var current = new AtomicReference<>(List.of(initial));
        try (var context = new DefaultCamelContext()) {
            context.disableJMX();
            context.getRegistry().bind("metrics", (SemanticMetricSource) current::get);
            context.start();
            var service = SemanticMonitoringService.get(context);
            assertThat(service.snapshot(null)).containsEntry("snapshots", List.of(initial.toMap()));
            current.set(List.of(changed));
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(service.snapshot("metrics"))
                    .containsEntry("snapshots", List.of(changed.toMap())));
            current.set(List.of());
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat((List<?>) service.snapshot(null).get("snapshots"))
                            .isEmpty());
            assertThat(service.snapshot(null)).containsEntry("sampleIntervalSeconds", 1)
                    .doesNotContainKeys("historySize", "historyFrom", "historyTo", "recovery", "stores");
        }
    }

    @Test
    void failedSamplingKeepsLastValuesWithExplicitFailureHealth() throws Exception {
        AtomicBoolean failed = new AtomicBoolean();
        var initial = snapshot("counter", Map.of("count", 1));
        try (var context = new DefaultCamelContext()) {
            context.disableJMX();
            context.getRegistry().bind("metrics", (SemanticMetricSource) () -> {
                if (failed.get()) {
                    throw new IllegalStateException("private provider failure");
                }
                return List.of(initial);
            });
            context.start();
            var service = SemanticMonitoringService.get(context);
            failed.set(true);
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat((long) service.snapshot(null).get("sourceErrors"))
                            .isGreaterThan(0));
            var response = service.snapshot(null);
            assertThat(response).containsEntry("snapshots", List.of(initial.toMap()));
            assertThat(response.toString()).contains("sample_failed", "lastSuccess", "lastFailure")
                    .doesNotContain("private provider failure");
        }
    }

    @Test
    void invalidSourceSampleDoesNotPartiallyReplaceLastValues() throws Exception {
        var initial = snapshot("counter", Map.of("count", 1));
        var current = new AtomicReference<>(List.of(initial));
        try (var context = new DefaultCamelContext()) {
            context.disableJMX();
            context.getRegistry().bind("metrics", (SemanticMetricSource) current::get);
            context.start();
            var service = SemanticMonitoringService.get(context);
            current.set(Arrays.asList(snapshot("counter", Map.of("count", 2)), null));
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat((long) service.snapshot(null).get("sourceErrors"))
                            .isGreaterThan(0));
            assertThat(service.snapshot(null)).containsEntry("snapshots", List.of(initial.toMap()));
        }
    }

    @Test
    void publishedMeasurementsAreBoundedAndNotRecoveredAfterRestart() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.disableJMX();
            context.start();
            var service = SemanticMonitoringService.get(context);
            for (int i = 0; i < 110; i++) {
                var fields = new LinkedHashMap<>(snapshot("counter", Map.of("count", i)).toMap());
                fields.put("id", "series-" + i);
                var value = new SemanticMonitoringSnapshot(fields);
                assertThat(EventHelper.notifyAiEvent(context, () -> new SemanticMonitoringEvent(value))).isTrue();
            }
            assertThat((List<?>) service.snapshot(null).get("snapshots")).hasSize(100);
            assertThat(service.snapshot(null)).containsEntry("evicted", 10L);
            service.stop();
            service.start();
            assertThat((List<?>) service.snapshot(null).get("snapshots")).isEmpty();
        }
    }
}
