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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class JfrTabRenderTest {

    private MonitorContext ctx;
    private IntegrationInfo info;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "test-app";

        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of(info));
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        ctx = new MonitorContext(data, infraData);
        ctx.selectedPid = "1234";
    }

    @Test
    void renderNoSelectionShowsPrompt() {
        ctx.selectedPid = null;
        JfrTab tab = new JfrTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);
        assertThat(rendered).containsAnyOf("No integration selected", "Select an integration");
    }

    @Test
    void renderShowsBlockTitle() {
        JfrTab tab = new JfrTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);
        assertThat(rendered).contains("JFR");
    }

    @Test
    void renderShowsRegisteredAndRecordingState() {
        JfrTab tab = new JfrTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 120, 20);
        assertThat(rendered).contains("runtime events").contains("no active recording");
    }

    @Test
    void renderShowsStatusErrorFromIntegration() {
        TestMonitorContext errorContext = new TestMonitorContext(dataWith(info), errorResponse());
        errorContext.selectedPid = "1234";
        JfrTab tab = new JfrTab(errorContext, Runnable::run);

        tab.onTabSelected();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(TuiTestHelper.renderToString(tab, 120, 20))
                .contains("JFR runtime instrumentation is not available"));
    }

    @Test
    void renderFooterHints() {
        JfrTab tab = new JfrTab(ctx);
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);
        String footer = footerSpans.stream().map(Span::content).reduce("", String::concat);

        assertThat(footer).contains("Esc").contains("F5").contains("snapshot");
    }

    @Test
    void renderFooterShowsViewHint() {
        JfrTab tab = new JfrTab(ctx);
        List<Span> footerSpans = new ArrayList<>();
        tab.renderFooter(footerSpans);
        String footer = footerSpans.stream().map(Span::content).reduce("", String::concat);

        assertThat(footer).contains("Space").contains("view");
    }

    @Test
    void renderSnapshotDataShowsRoutesTable() {
        TestMonitorContext snapshotCtx = new TestMonitorContext(dataWith(info), statusResponse())
                .withSnapshot(snapshotResponse());
        snapshotCtx.selectedPid = "1234";
        JfrTab tab = new JfrTab(snapshotCtx, Runnable::run);

        tab.onTabSelected();
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F5, KeyModifiers.NONE));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            String rendered = TuiTestHelper.renderToString(tab, 140, 30);
            assertThat(rendered).contains("Routes");
        });
    }

    @Test
    void renderSnapshotDataShowsRoutesPanelTitle() {
        TestMonitorContext snapshotCtx = new TestMonitorContext(dataWith(info), statusResponse())
                .withSnapshot(snapshotResponse());
        snapshotCtx.selectedPid = "1234";
        JfrTab tab = new JfrTab(snapshotCtx, Runnable::run);

        tab.onTabSelected();
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F5, KeyModifiers.NONE));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            String rendered = TuiTestHelper.renderToString(tab, 140, 30);
            assertThat(rendered).contains("Routes");
        });
    }

    @Test
    void viewSwitchingChangesActiveView() {
        TestMonitorContext snapshotCtx = new TestMonitorContext(dataWith(info), statusResponse())
                .withSnapshot(snapshotResponse());
        snapshotCtx.selectedPid = "1234";
        JfrTab tab = new JfrTab(snapshotCtx, Runnable::run);

        tab.onTabSelected();
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F5, KeyModifiers.NONE));
        // Space cycles: Routes -> Processors -> Endpoints
        tab.handleKeyEvent(KeyEvent.ofChar(' '));
        tab.handleKeyEvent(KeyEvent.ofChar(' '));

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            String rendered = TuiTestHelper.renderToString(tab, 140, 30);
            assertThat(rendered).contains("Endpoints");
        });
    }

    @Test
    void promptToSnapshotWhenNoData() {
        JfrTab tab = new JfrTab(ctx);
        String rendered = TuiTestHelper.renderToString(tab, 140, 30);
        assertThat(rendered).contains("F5");
    }

    @Test
    void description() {
        JfrTab tab = new JfrTab(ctx);
        assertThat(tab.description()).isNotBlank();
    }

    private static AtomicReference<List<IntegrationInfo>> dataWith(IntegrationInfo info) {
        return new AtomicReference<>(List.of(info));
    }

    private static JsonObject errorResponse() {
        JsonObject response = new JsonObject();
        response.put("error", "JFR runtime instrumentation is not available");
        return response;
    }

    private static JsonObject snapshotResponse() {
        JsonObject response = new JsonObject();
        response.put("snapshot", true);
        response.put("eventCount", 150);

        JsonArray routes = new JsonArray();
        JsonObject route1 = new JsonObject();
        route1.put("routeId", "order-in");
        route1.put("total", 100L);
        route1.put("failed", 5L);
        route1.put("minMs", 1.2);
        route1.put("meanMs", 8.5);
        route1.put("maxMs", 250.3);
        routes.add(route1);
        JsonObject route2 = new JsonObject();
        route2.put("routeId", "notify");
        route2.put("total", 50L);
        route2.put("failed", 0L);
        route2.put("minMs", 2.0);
        route2.put("meanMs", 5.0);
        route2.put("maxMs", 45.0);
        routes.add(route2);
        response.put("routes", routes);

        JsonArray processors = new JsonArray();
        JsonObject proc1 = new JsonObject();
        proc1.put("processorId", "to1");
        proc1.put("processorType", "to");
        proc1.put("routeId", "order-in");
        proc1.put("total", 100L);
        proc1.put("failed", 3L);
        proc1.put("minMs", 0.5);
        proc1.put("meanMs", 5.2);
        proc1.put("maxMs", 200.1);
        processors.add(proc1);
        response.put("processors", processors);

        JsonArray endpoints = new JsonArray();
        JsonObject ep1 = new JsonObject();
        ep1.put("endpointUri", "kafka://orders");
        ep1.put("total", 100L);
        ep1.put("failed", 3L);
        ep1.put("minMs", 0.8);
        ep1.put("meanMs", 4.1);
        ep1.put("maxMs", 180.5);
        endpoints.add(ep1);
        response.put("endpoints", endpoints);

        response.put("failures", new JsonArray());
        response.put("redeliveries", new JsonArray());

        return response;
    }

    private static JsonObject statusResponse() {
        JsonObject response = new JsonObject();
        response.put("runtimeEvents", true);
        JsonArray recordings = new JsonArray();
        JsonObject rec = new JsonObject();
        rec.put("name", "default");
        rec.put("state", "RUNNING");
        recordings.add(rec);
        response.put("recordings", recordings);
        JsonObject events = new JsonObject();
        events.put("route", true);
        events.put("processor", true);
        events.put("exchange", true);
        events.put("send", true);
        events.put("failed", true);
        events.put("redelivery", true);
        response.put("events", events);
        return response;
    }

    private static final class TestMonitorContext extends MonitorContext {

        private final JsonObject response;
        private JsonObject snapshotResp;

        private TestMonitorContext(
                                   AtomicReference<List<IntegrationInfo>> data,
                                   JsonObject response) {
            super(data, new AtomicReference<>(List.of()));
            this.response = response;
        }

        TestMonitorContext withSnapshot(JsonObject snapshot) {
            this.snapshotResp = snapshot;
            return this;
        }

        @Override
        JsonObject executeAction(String pid, JsonObject request, long timeoutMs) {
            String command = request.getString("command");
            if ("snapshot".equals(command) && snapshotResp != null) {
                return snapshotResp;
            }
            return response;
        }
    }
}
