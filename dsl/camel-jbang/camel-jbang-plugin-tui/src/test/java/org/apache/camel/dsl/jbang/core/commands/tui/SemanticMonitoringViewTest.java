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
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.event.MouseEvent;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SemanticMonitoringViewTest {
    @Test
    void showsRealCountsDistributionsAndLatencyInFramesAtSupportedWidths() throws Exception {
        try (var runtime = new Runtime()) {
            var view = loaded(runtime);
            for (int[] size : List.of(new int[] { 144, 40 }, new int[] { 100, 30 }, new int[] { 88, 24 })) {
                String screen = render(view, size[0], size[1]);
                assertThat(screen).contains("Reported measurements", "Observed measurements", "ANSWERS", "FAILED")
                        .doesNotContain("BASE N", "NOW N", "epoch:", "typed_results");
            }
            String screen = render(view, 144, 40);
            assertThat(screen).contains("Completed: 12", "Answers: 10", "Failed: 2", "Mean duration: 2 ms",
                    "billing  7", "technical  3", "Source details", "Expert: decisions");
            assertThat(((Number) view.snapshot().getJsonObject("selected").getJsonObject("values").get("completed"))
                    .longValue()).isEqualTo(12);
            assertThat(runtime.requests).allSatisfy(r -> assertThat(r).containsEntry("action", "semantic-monitoring"));
        }
    }

    @Test
    void genericMeasurementDoesNotInventComparisonOrHistoryControls() throws Exception {
        try (var runtime = new Runtime()) {
            runtime.rows = List.of(new JsonObject(
                    Map.of("id", "queue", "source", "queue-monitor", "name", "Queue depth",
                            "meaning", "Messages waiting", "kind", "gauge", "unit", "messages", "values",
                            Map.of("value", 42))));
            var view = loaded(runtime);
            String screen = render(view, 144, 40);
            assertThat(screen).contains("Queue depth", "42", "Messages waiting", "Current values only")
                    .doesNotContain("Period", "Compare", "BASE N", "NOW N", "CHANGE", "Store errors", "History:");
            assertThat(view.setInput("monitoring.period", "1h")).isFalse();
            assertThat(view.setInput("monitoring.comparison", "fixed")).isFalse();
            assertThat(view.key(KeyEvent.ofChar('t'))).isFalse();
            assertThat(view.key(KeyEvent.ofChar('c'))).isFalse();
            assertThat(runtime.requests).singleElement().satisfies(request -> assertThat(request)
                    .containsOnlyKeys("action").containsEntry("action", "semantic-monitoring"));
        }
    }

    @Test
    void selectionSurvivesUpdatedLatestSnapshotsAndFiltersDoNotEvaluate() throws Exception {
        try (var runtime = new Runtime()) {
            JsonObject second = new JsonObject(runtime.rows.get(0));
            second.put("id", "other");
            second.put("dimensions", new JsonObject(Map.of("expert", "security", "operation", "injection")));
            runtime.rows = List.of(runtime.rows.get(0), second);
            var view = loaded(runtime);
            view.navigate(1);
            second.put("to", "2026-10-13T11:00:00Z");
            runtime.rows = List.of(second, runtime.rows.get(0));
            view.load();
            await().atMost(5, TimeUnit.SECONDS).until(() -> !view.ensureLoaded());
            assertThat(view.snapshot().getJsonObject("selected")).containsEntry("id", "other");
            assertThat(view.setInput("monitoring.expert", "decisions")).isTrue();
            assertThat(SemanticTab.objects(view.snapshot(), "rows")).hasSize(1);
            assertThat(view.snapshot().getJsonObject("selected")).containsEntry("id", "choices");
            view.setInput("monitoring.expert", "unknown");
            view.key(KeyEvent.ofChar('i'));
            assertThat(render(view, 144, 40)).contains("No matching measurements");
            assertThat(runtime.requests).allSatisfy(r -> assertThat(r).containsEntry("action", "semantic-monitoring"));
        }
    }

    @Test
    void switchesBetweenSuppliedExpertAndDefinitionAggregatesWithoutCombiningThem() throws Exception {
        try (var runtime = new Runtime()) {
            JsonObject expert = runtime.rows.get(0);
            JsonObject first = new JsonObject(expert);
            first.put("id", "screenPrompt");
            first.put("dimensions", Map.of("aggregation", "definition", "definition", "screenPrompt", "expert", "security"));
            first.put("values", Map.of("completed", 3, "answers", 2, "failed", 1, "meanDurationNanos", 6000000));
            JsonObject second = new JsonObject(expert);
            second.put("id", "screenContext");
            second.put("dimensions", Map.of("definition", "screenContext", "expert", "security"));
            second.put("values", Map.of("completed", 5, "answers", 5, "failed", 0, "meanDurationNanos", 2000000));
            runtime.rows = List.of(expert, first, second);
            var view = loaded(runtime);
            assertThat(view.snapshot()).containsEntry("aggregation", "expert");
            assertThat(SemanticTab.objects(view.snapshot(), "rows")).containsExactly(expert);
            view.key(KeyEvent.ofChar('g'));
            assertThat(view.snapshot()).containsEntry("aggregation", "definition");
            assertThat(SemanticTab.objects(view.snapshot(), "rows")).containsExactly(first, second);
            for (int width : List.of(144, 100, 88)) {
                assertThat(render(view, width, 40)).contains("Group (g): Definition", "DEFINITION", "screenPrompt",
                        "screenContext", "6 ms");
            }
            view.navigate(1);
            view.load();
            await().atMost(5, TimeUnit.SECONDS).until(() -> !view.ensureLoaded());
            assertThat(view.snapshot().getJsonObject("selected")).containsEntry("id", "screenContext");
            assertThat(view.mouse(MouseEvent.press(MouseButton.LEFT, 2, 1))).isTrue();
            assertThat(view.snapshot()).containsEntry("aggregation", "all");
            assertThat(SemanticTab.objects(view.snapshot(), "rows")).containsExactly(expert, first, second);
            assertThat(render(view, 144, 40)).contains("EXPERT / DEFINITION", "screenPrompt", "screenContext", "decisions");
            view.key(KeyEvent.ofChar('g'));
            assertThat(SemanticTab.objects(view.snapshot(), "rows")).containsExactly(expert);
            assertThat(runtime.requests).hasSize(2)
                    .allSatisfy(r -> assertThat(r).containsEntry("action", "semantic-monitoring"));
        }
    }

    @Test
    void missingDefinitionAggregatesAreNotReconstructedFromExpertTotals() throws Exception {
        try (var runtime = new Runtime()) {
            var view = loaded(runtime);
            assertThat(view.setInput("monitoring.aggregation", "definition")).isTrue();
            assertThat(SemanticTab.objects(view.snapshot(), "rows")).isEmpty();
            assertThat(render(view, 144, 40)).contains("No matching definition aggregates supplied");
            assertThat(view.setInput("monitoring.aggregation", "arbitrary")).isFalse();
            assertThat(runtime.requests).hasSize(1);
        }
    }

    @Test
    void changingIntegrationClearsSourceAndDimensionFiltersAndRecomputesAggregation() throws Exception {
        try (var runtime = new Runtime()) {
            var view = loaded(runtime);
            view.setInput("monitoring.source", "decisions-choice");
            await().atMost(5, TimeUnit.SECONDS).until(() -> !view.ensureLoaded());
            view.setInput("monitoring.expert", "decisions");
            view.setInput("monitoring.definition", "routeRequest");
            view.setInput("monitoring.availability", "available");
            view.setInput("monitoring.aggregation", "definition");
            IntegrationInfo second = new IntegrationInfo();
            second.pid = "second";
            runtime.data.set(List.of(second));
            runtime.selectedPid = "second";
            JsonObject measurement = new JsonObject(
                    Map.of("id", "queue-depth", "source", "queue-monitor",
                            "name", "Queue depth", "unit", "messages", "values", Map.of("count", 42)));
            runtime.rows = List.of(measurement);
            await().atMost(5, TimeUnit.SECONDS).until(() -> !view.ensureLoaded());
            assertThat(runtime.requests.get(runtime.requests.size() - 1)).doesNotContainKey("source");
            assertThat(view.snapshot()).containsEntry("filters", Map.of()).containsEntry("aggregation", "all");
            assertThat(SemanticTab.objects(view.snapshot(), "rows")).containsExactly(measurement);
            assertThat(render(view, 144, 40)).contains("Queue depth").doesNotContain("No matching");
        }
    }

    @Test
    void sourceChangesDuringAReadRequestTheNewSource() throws Exception {
        try (var runtime = new Runtime()) {
            var view = loaded(runtime);
            runtime.release = new CountDownLatch(1);
            view.load();
            view.setInput("monitoring.source", "decisions-choice");
            runtime.release.countDown();
            await().atMost(5, TimeUnit.SECONDS).until(() -> !view.ensureLoaded());
            assertThat(runtime.requests).hasSize(3);
            assertThat(runtime.requests.get(2)).containsEntry("source", "decisions-choice").doesNotContainKeys("history",
                    "since");
        }
    }

    @Test
    void pauseFreezesTheDisplayedResponseIncludingAnInFlightRefresh() throws Exception {
        try (var runtime = new Runtime()) {
            var view = loaded(runtime);
            runtime.release = new CountDownLatch(1);
            runtime.rows = List.of(new JsonObject(
                    Map.of("id", "new", "name", "New measurement", "source", "live",
                            "dimensions", Map.of("expert", "decisions"))));
            view.load();
            view.key(KeyEvent.ofChar(' '));
            runtime.release.countDown();
            await().atMost(5, TimeUnit.SECONDS).until(() -> runtime.requests.size() == 2);
            assertThat(view.snapshot().getJsonObject("selected")).containsEntry("id", "choices");
            assertThat(render(view, 144, 40)).contains("DISPLAY PAUSED");
            view.key(KeyEvent.ofChar(' '));
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(view.snapshot().getJsonObject("selected")).containsEntry("id", "new"));
        }
    }

    private static SemanticMonitoringView loaded(Runtime runtime) {
        var view = new SemanticMonitoringView(runtime);
        view.load();
        await().atMost(5, TimeUnit.SECONDS).until(() -> !view.ensureLoaded());
        return view;
    }

    private static String render(SemanticMonitoringView view, int width, int height) {
        Rect area = new Rect(0, 0, width, height);
        Buffer buffer = Buffer.empty(area);
        view.render(Frame.forTesting(buffer), area);
        return TuiTestHelper.bufferToString(buffer);
    }

    private static class Runtime extends MonitorContext implements AutoCloseable {
        final List<JsonObject> requests = new CopyOnWriteArrayList<>();
        volatile CountDownLatch release;
        volatile List<JsonObject> rows;

        Runtime() throws Exception {
            super(new AtomicReference<>(new ArrayList<>()), new AtomicReference<>(List.of()));
            IntegrationInfo integration = new IntegrationInfo();
            integration.pid = "first";
            data.set(List.of(integration));
            selectedPid = "first";
            rows = List.of((JsonObject) Jsoner.deserialize("""
                    {"id":"choices","source":"decisions-choice","name":"CHOICE result distribution",
                     "meaning":"Selected support departments","kind":"typed_results","unit":"assessments",
                     "from":"2026-10-13T10:00:00Z","to":"2026-10-13T10:15:00Z","availability":"available",
                     "denominator":10,"exclusions":2,"loss":0,"epoch":"not-for-the-summary",
                     "dimensions":{"expert":"decisions","operation":"choice","population":"Completed support requests"},
                     "values":{"completed":12,"answers":10,"failed":2,"notExecuted":0,"unavailable":0,
                       "durationCount":12,"meanDurationNanos":2000000,"minimumDurationNanos":1000000,
                       "maximumDurationNanos":3000000,"distribution":[{"label":"billing","count":7},
                         {"label":"technical","count":3}]}}
                    """));
        }

        @Override
        JsonObject executeIndependentAction(String pid, JsonObject request, long timeoutMs) {
            requests.add(request);
            try {
                if (release != null && !release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Test refresh was not released");
                }
                return new JsonObject(
                        Map.of("snapshots", rows, "lastSample", "2026-10-13T10:15:01Z",
                                "sourceErrors", 0, "storeErrors", 0, "dropped", 0, "recovery", "memory_only"));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        }

        @Override
        public void close() {
            if (release != null) {
                release.countDown();
            }
            backgroundExecutor.shutdownNow();
        }
    }
}
