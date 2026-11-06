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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
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

/**
 * The Timeline screen ({@link TimelineTab}) of an integration in an outage of the stock service: for 15 seconds the
 * orders go to the dead letter channel with a 503, and shipping has nothing to ship.
 */
class TimelineTabRenderTest {

    private static final long T0 = 1_800_000_000_000L;

    private final AtomicLong now = new AtomicLong(T0 + 60_000);
    private MonitorContext ctx;
    private IntegrationInfo info;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "shop";
        ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = "1234";
        long orders = 0;
        long handled = 0;
        long shipping = 0;
        List<ErrorInfo> errors = new ArrayList<>();
        for (int s = 0; s < 60; s++) {
            boolean outage = s >= 25 && s < 40;
            orders += 2;
            if (outage) {
                for (int k = 0; k < 2; k++) {
                    handled++;
                    ErrorInfo e = new ErrorInfo();
                    e.routeId = "orders";
                    e.nodeId = "callStock";
                    e.exceptionType = "org.apache.camel.http.base.HttpOperationFailedException";
                    e.exceptionMessage = "HTTP operation failed invoking http://localhost:8765/stock with statusCode: 503";
                    e.handled = true;
                    e.repeatCount = handled;
                    e.repeatFirstTimestamp = T0 + 25_000;
                    e.timestamp = T0 + s * 1000L + k * 400;
                    errors.add(0, e);
                }
            } else {
                shipping += 2;
            }
            info.routes.clear();
            info.routes.add(route("orders", orders, handled));
            info.routes.add(route("shipping", shipping, 0));
            info.routes.add(route("dlq", handled, 0));
            ctx.timeline.record(info, T0 + s * 1000L);
        }
        // the error registry keeps the newest 3 of a kind, with the count of the kind
        ctx.timeline.recordErrors("1234", errors.subList(0, 3), T0 + 59_000);
    }

    private static RouteInfo route(String id, long total, long handled) {
        RouteInfo r = new RouteInfo();
        r.routeId = id;
        r.total = total;
        r.failuresHandled = handled;
        r.meanTime = 4;
        return r;
    }

    @Test
    void theOutageReadsFromLeftToRight() {
        TimelineTab tab = new TimelineTab(ctx, now::get);
        String screen = TuiTestHelper.renderToString(tab, 160, 40);
        System.out.println(screen);

        assertThat(screen).contains("Timeline ─ shop ─ last 60s")
                .contains("Errors/s").contains("HttpOperationFailedException 503")
                .contains(">> orders").contains("shipping").contains("dlq")
                .contains("-60s").contains("-30s").contains("now")
                .contains("orders ─ per second").contains("mean time").contains("in flight")
                .contains("30 handled by the error handler");
        // shipping had nothing to ship in the outage: 15 quiet seconds
        String shipping = screen.lines().filter(l -> l.contains("   shipping")).findFirst().orElseThrow();
        assertThat(shipping).contains("···············");
    }

    @Test
    void theTableForAnAiHasTheSeriesPerSecond() {
        TimelineTab tab = new TimelineTab(ctx, now::get);
        TuiTestHelper.renderToString(tab, 160, 40);
        JsonObject table = tab.getTableDataAsJson();

        assertThat(table.getString("tab")).isEqualTo("Timeline");
        JsonArray causes = (JsonArray) table.get("causes");
        assertThat(causes).hasSize(1);
        // 3 entries kept, and the 27 before them spread from the first failure of the kind
        assertThat(((JsonObject) causes.get(0)).getLong("total")).isEqualTo(30);
        JsonArray rows = (JsonArray) table.get("rows");
        JsonObject orders = (JsonObject) rows.get(0);
        assertThat(orders.getString("route")).isEqualTo("orders");
        assertThat(orders.getLong("handled")).isEqualTo(30);
        // the first second has no rise yet
        assertThat(orders.getLong("completed")).isEqualTo(118 - 30);
    }

    @Test
    void pauseKeepsTheMomentAndGoesBack() {
        TimelineTab tab = new TimelineTab(ctx, now::get);
        tab.handleKeyEvent(KeyEvent.ofChar(' '));
        assertThat(tab.isPaused()).isTrue();
        now.addAndGet(10_000);
        String screen = TuiTestHelper.renderToString(tab, 160, 40);
        assertThat(screen).contains("paused, 10s ago");
        List<Span> hints = new ArrayList<>();
        tab.renderFooter(hints);
        assertThat(text(hints)).contains("follow").contains("back and forth");

        assertThat(tab.handleEscape()).isTrue();
        assertThat(tab.isPaused()).isFalse();
    }

    @Test
    void theWindowGrowsToFiveMinutes() {
        TimelineTab tab = new TimelineTab(ctx, now::get);
        tab.handleKeyEvent(KeyEvent.ofChar('w'));
        tab.handleKeyEvent(KeyEvent.ofChar('w'));
        assertThat(tab.seconds()).isEqualTo(300);
        String screen = TuiTestHelper.renderToString(tab, 160, 40);
        assertThat(screen).contains("last 5m").contains("-5m").contains("s a column");
    }

    @Test
    void aQuietIntegrationIsACalmScreen() {
        IntegrationInfo quiet = new IntegrationInfo();
        quiet.pid = "77";
        quiet.name = "quiet";
        RouteInfo r = route("timer", 0, 0);
        quiet.routes.add(r);
        MonitorContext qctx = new MonitorContext(new AtomicReference<>(List.of(quiet)), new AtomicReference<>(List.of()));
        qctx.selectedPid = "77";
        for (int s = 0; s < 60; s++) {
            qctx.timeline.record(quiet, T0 + s * 1000L);
        }
        qctx.timeline.recordErrors("77", List.of(), T0 + 59_000);
        String screen = TuiTestHelper.renderToString(new TimelineTab(qctx, now::get), 160, 40);

        assertThat(screen).contains("Errors/s  none").contains("idle");
    }

    @Test
    void theScreenOpensOnTheRouteWithTheWorstTime() {
        TimelineTab tab = new TimelineTab(ctx, now::get);
        TuiTestHelper.renderToString(tab, 160, 40);
        tab.onTabSelected();
        String screen = TuiTestHelper.renderToString(tab, 160, 40);
        // orders had its exchanges handled by the dead letter channel
        assertThat(screen).contains(">> orders").contains("orders ─ per second");
    }

    @Test
    void shiftF8AsksTheAiForTheStoryOfCauseAndEffect() {
        List<String> asked = new ArrayList<>();
        ctx.askAiCallback = new MonitorContext.AskAi() {
            @Override
            public void fixProblem(Path file, int line, String problem, String lineText) {
            }

            @Override
            public void ask(String question) {
                asked.add(question);
            }
        };
        TimelineTab tab = new TimelineTab(ctx, now::get);
        TuiTestHelper.renderToString(tab, 160, 40);
        List<Span> hints = new ArrayList<>();
        tab.renderFKeyHints(hints);
        assertThat(text(hints)).contains("explain with AI");

        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));

        assertThat(asked).hasSize(1);
        assertThat(asked.get(0)).startsWith("Explain what happened in the last 60s of the running integration shop")
                .contains("error HttpOperationFailedException 503: 30 in 15 seconds, from 34s to 20s ago")
                .contains("orders: 88 completed, 0 recovered by redelivery, 30 handled by the error handler, 0 failed"
                          + " (handled or failed from 34s to 20s ago)")
                .contains("shipping: 88 completed").endsWith("Make no changes to the files unless I agree.");
    }

    private static String text(List<Span> spans) {
        StringBuilder sb = new StringBuilder();
        spans.forEach(s -> sb.append(s.content()));
        return sb.toString();
    }
}
