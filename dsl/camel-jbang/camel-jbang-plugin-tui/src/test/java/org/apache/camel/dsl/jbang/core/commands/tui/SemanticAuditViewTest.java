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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SemanticAuditViewTest {
    private static final Instant START = Instant.parse("2026-10-14T12:00:00Z");

    @ParameterizedTest
    @CsvSource({ "1,600", "2,3600", "3,86400" })
    void liveTimePresetsAdvanceTheirLowerBoundOnEachRefresh(int index, long seconds) {
        try (var runtime = new Runtime()) {
            AtomicLong clock = new AtomicLong();
            AtomicReference<Instant> now = new AtomicReference<>(START);
            var view = new SemanticAuditView(runtime, clock::get, now::get);
            loaded(view);
            chooseTimePreset(view, index);
            loaded(view);
            assertThat(runtime.lastQuery()).containsEntry("since", START.minusSeconds(seconds).toString());

            now.set(START.plusSeconds(60));
            clock.addAndGet(1_000_000_000L);
            loaded(view);
            assertThat(runtime.queries()).hasSize(3);
            assertThat(runtime.lastQuery()).containsEntry("since", now.get().minusSeconds(seconds).toString());
        }
    }

    @Test
    void explicitIsoTimeFilterRemainsFixedAcrossLiveRefreshes() {
        try (var runtime = new Runtime()) {
            AtomicLong clock = new AtomicLong();
            AtomicReference<Instant> now = new AtomicReference<>(START);
            var view = new SemanticAuditView(runtime, clock::get, now::get);
            loaded(view);
            String since = START.minusSeconds(600).toString();
            String until = START.toString();
            assertThat(view.setFilter("since=" + since + " until=" + until
                                      + " definition=routeRequest operation=choice resultType=CHOICE resultValue=\"none of these\""))
                    .isTrue();
            loaded(view);
            now.set(START.plusSeconds(60));
            clock.addAndGet(1_000_000_000L);
            loaded(view);
            assertThat(runtime.queries()).hasSize(3);
            assertThat(runtime.lastQuery()).containsEntry("since", since).containsEntry("until", until)
                    .containsEntry("definition", "routeRequest").containsEntry("operation", "choice")
                    .containsEntry("resultType", "CHOICE").containsEntry("resultValue", "none of these");
            assertThat(render(view, 180)).contains("until=", "definition=routeRequest", "resultType=CHOICE");
            chooseTimePreset(view, 1);
            loaded(view);
            assertThat(runtime.lastQuery()).doesNotContainKey("until");
        }
    }

    @Test
    void invalidWindowOrResultFilterDoesNotReplaceTheCurrentQuery() {
        try (var runtime = new Runtime()) {
            var view = new SemanticAuditView(runtime);
            loaded(view);
            int count = runtime.queries().size();
            for (String filter : List.of("since=" + START + " until=" + START,
                    "resultValue=false", "resultType=BOOLEAN resultValue=FALSE", "resultType=SCORE scoreMin=NaN")) {
                assertThat(view.setFilter(filter)).isFalse();
            }
            assertThat(runtime.queries()).hasSize(count);
        }
    }

    @Test
    void olderPagesAndTheirExplicitRefreshKeepTheOriginalPresetBound() {
        try (var runtime = new Runtime()) {
            AtomicLong clock = new AtomicLong();
            AtomicReference<Instant> now = new AtomicReference<>(START);
            var view = new SemanticAuditView(runtime, clock::get, now::get);
            loaded(view);
            chooseTimePreset(view, 1);
            loaded(view);
            String since = runtime.lastQuery().getString("since");

            now.set(START.plusSeconds(60));
            view.key(KeyEvent.ofChar('n'));
            loaded(view);
            assertThat(runtime.lastQuery()).containsEntry("cursor", "older").containsEntry("since", since);
            now.set(START.plusSeconds(3600));
            clock.addAndGet(3_600_000_000_000L);
            assertThat(view.ensureLoaded()).isFalse();
            assertThat(runtime.queries()).hasSize(3);
            view.key(KeyEvent.ofChar('r'));
            loaded(view);
            assertThat(runtime.lastQuery()).containsEntry("cursor", "older").containsEntry("since", since);

            view.key(KeyEvent.ofChar('g'));
            loaded(view);
            assertThat(runtime.lastQuery()).doesNotContainKey("cursor")
                    .containsEntry("since", now.get().minusSeconds(600).toString());
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "direct", "route" })
    void missingExecutingRouteDistinguishesDirectCallsFromUnrecordedContext(String origin) {
        try (var runtime = new Runtime()) {
            if (origin != null) {
                runtime.record.put("origin", origin);
            }
            var view = new SemanticAuditView(runtime);
            loaded(view);
            String expected = "direct".equals(origin) ? "n/a" : "not recorded";
            for (int width : List.of(100, 180)) {
                assertThat(render(view, width)).contains("Executing route: " + expected);
            }
            assertThat(runtime.record).doesNotContainKeys("executingRouteId", "exchangeId");
        }
    }

    private static void loaded(SemanticAuditView view) {
        await().atMost(5, TimeUnit.SECONDS).until(() -> !view.ensureLoaded());
    }

    private static void chooseTimePreset(SemanticAuditView view, int index) {
        assertThat(view.key(KeyEvent.ofChar('t'))).isTrue();
        for (int i = 0; i < index; i++) {
            view.key(KeyEvent.ofKey(KeyCode.DOWN));
        }
        view.key(KeyEvent.ofKey(KeyCode.ENTER));
    }

    private static String render(SemanticAuditView view, int width) {
        Rect area = new Rect(0, 0, width, 45);
        Buffer buffer = Buffer.empty(area);
        view.render(Frame.forTesting(buffer), area);
        return TuiTestHelper.bufferToString(buffer);
    }

    private static class Runtime extends MonitorContext implements AutoCloseable {
        final List<JsonObject> requests = new CopyOnWriteArrayList<>();
        final JsonObject record = new JsonObject(
                Map.of("eventId", "evaluation", "category", "evaluation",
                        "expert", "classifier", "status", "success", "timestamp", START.toString(),
                        "result", new JsonObject(Map.of("value", true))));

        Runtime() {
            super(new AtomicReference<>(List.of()), new AtomicReference<>(List.of()));
            IntegrationInfo integration = new IntegrationInfo();
            integration.pid = "first";
            data.set(List.of(integration));
            selectedPid = "first";
        }

        @Override
        JsonObject executeIndependentAction(String pid, JsonObject request, long timeoutMs) {
            requests.add(request);
            return request.containsKey("eventId")
                    ? new JsonObject(Map.of("record", new JsonObject(record)))
                    : new JsonObject(Map.of("records", List.of(new JsonObject(record)), "nextCursor", "older"));
        }

        List<JsonObject> queries() {
            return requests.stream().filter(request -> !request.containsKey("eventId")).toList();
        }

        JsonObject lastQuery() {
            List<JsonObject> queries = queries();
            return queries.get(queries.size() - 1);
        }

        @Override
        public void close() {
            backgroundExecutor.shutdownNow();
        }
    }
}
