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

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.event.MouseEvent;
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
        for (String id : List.of("orders", "shipping", "dlq")) {
            RouteInfo r = new RouteInfo();
            r.routeId = id;
            info.routes.add(r);
        }
        ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = "1234";
        ctx.timeline.merge("1234", outage(T0 / 1000 + 59, 60, 0).json(), true);
    }

    /** The seconds of the shop: the outage from second 25 to 39 of the minute that ends at end. */
    static TimelineJson outage(long end, int seconds, int offset) {
        TimelineJson t = new TimelineJson(end, seconds);
        long[] errors = new long[seconds];
        for (int i = 0; i < seconds; i++) {
            int s = i + offset;
            boolean outage = s >= 25 && s < 40;
            t.route("orders", "meanMillis", i, outage ? 300 : 4).route("orders", "p95Millis", i, outage ? 800 : 6)
                    .route("orders", "maxMillis", i, outage ? 950 : 9);
            if (outage) {
                t.route("orders", "handled", i, 2).route("dlq", "completed", i, 2)
                        .route("orders", "redeliveryAttempts", i, 6);
                errors[i] = 2;
                t.exchange("H" + s, "orders", i, 900, "handled",
                        "HTTP operation failed invoking http://localhost:8765/stock with statusCode: 503",
                        s == 30 ? "r1" : null, "http://localhost:8765/stock@850");
            } else {
                t.route("orders", "completed", i, 2).route("shipping", "completed", i, 2);
                t.exchange("C" + s, "orders", i, 12, "completed", null, null, "http://localhost:8765/stock@8");
            }
        }
        return t.error("orders", "org.apache.camel.http.base.HttpOperationFailedException", 503, null, errors);
    }

    private static RouteInfo route(String id, long total, long handled) {
        RouteInfo r = new RouteInfo();
        r.routeId = id;
        r.total = total;
        r.failuresHandled = handled;
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
                .contains("p95 800ms max 950ms").contains("   calls").contains("http://localhost:8765/stock max 850ms")
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
        assertThat(((JsonObject) causes.get(0)).getLong("total")).isEqualTo(30);
        JsonArray rows = (JsonArray) table.get("rows");
        JsonObject orders = (JsonObject) rows.get(0);
        assertThat(orders.getString("route")).isEqualTo("orders");
        assertThat(orders.getLong("handled")).isEqualTo(30);
        assertThat(orders.getLong("completed")).isEqualTo(90);
        assertThat((JsonArray) ((JsonObject) orders.get("perSecond")).get("p95Millis")).hasSize(60);
    }

    @Test
    void pauseKeepsTheMomentAndGoesBack() {
        TimelineTab tab = new TimelineTab(ctx, now::get);
        tab.handleKeyEvent(KeyEvent.ofChar(' '));
        assertThat(tab.isPaused()).isTrue();
        // 10 seconds go by, which the next refreshes read
        now.addAndGet(10_000);
        ctx.timeline.merge("1234", outage(T0 / 1000 + 69, 10, 60).json(), false);
        String screen = TuiTestHelper.renderToString(tab, 160, 40);
        assertThat(screen).contains("paused, 10s ago").contains("▲ 10s ago");
        List<Span> hints = new ArrayList<>();
        tab.renderFooter(hints);
        assertThat(text(hints)).contains("follow").contains("back and forth").contains("exchanges");

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
        qctx.timeline.merge("77", new TimelineJson(T0 / 1000 + 59, 60).route("timer", "completed", 0, 0).json(), true);
        String screen = TuiTestHelper.renderToString(new TimelineTab(qctx, now::get), 160, 40);

        assertThat(screen).contains("Errors/s  none").contains("idle");
    }

    @Test
    void aQuietMinuteOpensOnTheWindowWithTheLastExchange() {
        IntegrationInfo batch = new IntegrationInfo();
        batch.pid = "88";
        batch.name = "batch";
        batch.routes.add(route("files", 0, 0));
        MonitorContext bctx = new MonitorContext(new AtomicReference<>(List.of(batch)), new AtomicReference<>(List.of()));
        bctx.selectedPid = "88";
        // the files were picked up at startup, 3 minutes ago
        bctx.timeline.merge("88", new TimelineJson(T0 / 1000 + 59, 300).route("files", "completed", 60, 3)
                .route("files", "meanMillis", 60, 40).route("files", "maxMillis", 60, 90).json(), true);
        TimelineTab tab = new TimelineTab(bctx, now::get);

        tab.onTabSelected();

        assertThat(tab.seconds()).isEqualTo(300);
        String screen = TuiTestHelper.renderToString(tab, 160, 40);
        assertThat(screen).contains("last 5m").contains("avg 40ms");
        tab.handleKeyEvent(KeyEvent.ofChar('w'));
        assertThat(TuiTestHelper.renderToString(tab, 160, 40)).contains("the last exchange 3m59s ago");
    }

    @Test
    void theCallsAreShownDecoded() {
        assertThat(TimelineExchanges.uri("file://parked?fileName=%24%7Bheader.CamelFileName%7D"))
                .isEqualTo("file://parked?fileName=${header.CamelFileName}");
        assertThat(TimelineExchanges.uri("http://stock?q=a+b")).isEqualTo("http://stock?q=a+b");
        assertThat(TimelineExchanges.uri("http://stock")).isEqualTo("http://stock");
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
                .contains("orders: 90 completed, 0 recovered by redelivery, 30 handled by the error handler, 0 failed"
                          + " (handled or failed from 34s to 20s ago)")
                .contains("shipping: 90 completed").endsWith("Make no changes to the files unless I agree.");
    }

    @Test
    void aRecoveredExchangeShowsTheFailureItWasRedeliveredFor() {
        TimelineJson t = new TimelineJson(T0 / 1000 + 59, 60);
        t.route("orders", "recovered", 59, 1).route("orders", "redeliveryAttempts", 59, 1);
        t.exchange("R59", "orders", 59, 40, "recovered", "Connection refused", null, "http://localhost:8765/stock@20");
        ctx.timeline.merge("1234", t.json(), true);
        TimelineTab tab = new TimelineTab(ctx, now::get);
        TuiTestHelper.renderToString(tab, 160, 40);
        tab.handleKeyEvent(KeyEvent.ofChar(' '));
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));
        assertThat(TuiTestHelper.renderToString(tab, 160, 40)).contains("R59").contains("recovered")
                .contains("(the failed, handled and recovered, and the slowest)");
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));
        assertThat(TuiTestHelper.renderToString(tab, 160, 40)).contains("recovered after: Connection refused");
    }

    @Test
    void theSecondsACircuitBreakerWasOpen() {
        long[] states = new long[60];
        for (int i = 40; i < 52; i++) {
            states[i] = i < 50 ? 2 : 1;
        }
        TimelineJson t = TimelineTabRenderTest.outage(T0 / 1000 + 59, 60, 0).breaker("orders", "cb1", states);
        ctx.timeline.merge("1234", t.json(), true);
        TimelineTab tab = new TimelineTab(ctx, now::get);
        // the bolt is two columns wide: the screen has a blank cell after it
        String screen = TuiTestHelper.renderToString(tab, 160, 40).replace("⚡ ", "⚡");
        assertThat(screen).contains("▒ circuit breaker open").contains("⚡ open 10s");
        String orders = screen.lines().filter(l -> l.contains("orders") && l.contains("⚡")).findFirst().orElseThrow();
        assertThat(orders.chars().filter(c -> c == '▒').count()).isGreaterThanOrEqualTo(12);
        assertThat(screen.lines().filter(l -> l.contains("shipping")).findFirst().orElseThrow()).doesNotContain("▒");

        // the same for an agent (tui_get_table)
        JsonObject row = ((JsonArray) tab.getTableDataAsJson().get("rows")).stream().map(JsonObject.class::cast)
                .filter(r -> "orders".equals(r.getString("route"))).findFirst().orElseThrow();
        assertThat(row.getLong("circuitBreakerOpenSeconds")).isEqualTo(10);
        JsonArray recorded = (JsonArray) ((JsonObject) row.get("perSecond")).get("circuitBreaker");
        assertThat(recorded.stream().filter(v -> ((Number) v).longValue() == 2).count()).isEqualTo(10);
    }

    @Test
    void theCursorHidesTheLabelOfTheAxisItIsOn() {
        TimelineTab tab = new TimelineTab(ctx, now::get);
        TuiTestHelper.renderToString(tab, 160, 40);
        // paused, the cursor is on the last second, where the axis says now
        tab.handleKeyEvent(KeyEvent.ofChar(' '));
        String axis = TuiTestHelper.renderToString(tab, 160, 40).lines().filter(l -> l.contains("-60s")).findFirst()
                .orElseThrow();
        // no piece of a label next to the cursor; its time is at the right
        assertThat(axis).doesNotContainPattern("[^ ▲]▲").contains("▲ now");
        // the cursor on the start of the axis
        for (int i = 0; i < 59; i++) {
            tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.LEFT));
        }
        axis = TuiTestHelper.renderToString(tab, 160, 40).lines().filter(l -> l.contains("-30s")).findFirst().orElseThrow();
        assertThat(axis).doesNotContain("-60s").doesNotContainPattern("▲[^ ▲]");
    }

    @Test
    void enterShowsTheExchangesOfTheSecondAtTheCursorAndOneInDetail() {
        TimelineTab tab = new TimelineTab(ctx, now::get);
        TuiTestHelper.renderToString(tab, 160, 40);
        tab.handleKeyEvent(KeyEvent.ofChar(' '));
        // from the last second (59) back to second 30, in the outage
        for (int i = 0; i < 29; i++) {
            tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.LEFT));
        }
        assertThat(tab.cursorFrom()).isEqualTo(T0 / 1000 + 30);
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));
        // the dice is two columns wide: the screen has a blank cell after it
        String list = TuiTestHelper.renderToString(tab, 160, 40).replace(TuiIcons.TAB_CHAOS + " ", TuiIcons.TAB_CHAOS);
        assertThat(list).contains("Exchanges at").contains("4 done, 1 kept").contains("H30")
                .contains("handled").contains("1 call").contains(TuiIcons.TAB_CHAOS + " r1");

        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));
        String detail = TuiTestHelper.renderToString(tab, 160, 40);
        assertThat(detail).contains("H30").contains("statusCode: 503").contains("850ms")
                .contains("faulted by chaos rule r1");
        JsonObject table = tab.getTableDataAsJson();
        assertThat((JsonArray) table.get("exchanges")).hasSize(1);

        // Esc: the list, then the route again, then follow now
        assertThat(tab.handleEscape()).isTrue();
        assertThat(TuiTestHelper.renderToString(tab, 160, 40)).contains("Exchanges at");
        assertThat(tab.handleEscape()).isTrue();
        assertThat(TuiTestHelper.renderToString(tab, 160, 40)).contains("orders ─ per second");
        assertThat(tab.isPaused()).isTrue();
        assertThat(tab.handleEscape()).isTrue();
        assertThat(tab.isPaused()).isFalse();
    }

    private static String text(List<Span> spans) {
        StringBuilder sb = new StringBuilder();
        spans.forEach(s -> sb.append(s.content()));
        return sb.toString();
    }

    @Test
    void aTallScreenHasRoomAroundThePanelsAndTallerStrips() {
        TimelineTab tab = new TimelineTab(ctx, now::get);
        List<String> lines = TuiTestHelper.renderToString(tab, 198, 48).lines().toList();

        // a line between the panels
        int bottom = indexOf(lines, "╰");
        assertThat(lines.get(bottom + 1)).isBlank();
        assertThat(lines.get(bottom + 2)).contains("orders ─ per second");
        // a strip is 2 rows: the blocks of a busy second stand on the row with the name
        int orders = indexOf(lines, ">> orders");
        assertThat(lines.get(orders - 1)).contains("████");
        // and a blank line between the routes
        int shipping = indexOf(lines, "   shipping");
        assertThat(shipping).isEqualTo(orders + 3);
        assertThat(lines.get(orders + 1).replace("│", "")).isBlank();
        // the charts grow: the scale of the errors per second is 6 rows from top to bottom
        int top = indexOf(lines, "2 ┤");
        assertThat(lines.get(top + 5)).contains("0 ┤");
    }

    @Test
    void aSmallScreenStaysCompact() {
        TimelineTab tab = new TimelineTab(ctx, now::get);
        List<String> lines = TuiTestHelper.renderToString(tab, 160, 30).lines().toList();

        int bottom = indexOf(lines, "╰");
        assertThat(lines.get(bottom + 1)).contains("orders ─ per second");
        int orders = indexOf(lines, ">> orders");
        assertThat(lines.get(orders + 1)).contains("   shipping");
    }

    @Test
    void theRoomGoesToTheSpaceAroundThenTheRoutesThenTheCharts() {
        assertThat(TimelineTab.room(30, 3, true)).isEqualTo(TimelineTab.Room.COMPACT);
        assertThat(TimelineTab.room(48, 3, true)).isEqualTo(new TimelineTab.Room(true, 6, 6, 2, true));
        // the routes need the rows: one row each, no blank line between them
        TimelineTab.Room many = TimelineTab.room(48, 10, true);
        assertThat(many.roomy()).isTrue();
        assertThat(many.strip()).isEqualTo(1);
        assertThat(many.spaced()).isFalse();
        // no error: the detail has the rows the errors would have
        assertThat(TimelineTab.room(48, 3, false).causeRows()).isEqualTo(4);
    }

    @Test
    void aClickOnTheStripOfARouteSelectsIt() {
        TimelineTab tab = new TimelineTab(ctx, now::get);
        Rect area = new Rect(0, 0, 198, 48);
        List<String> lines = TuiTestHelper.renderToString(tab, 198, 48).lines().toList();
        int shipping = indexOf(lines, "   shipping");

        // the blank line between orders and shipping is no route
        assertThat(tab.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, 40, shipping - 2), area)).isFalse();
        // the upper row of the strip of shipping is shipping
        assertThat(tab.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, 40, shipping - 1), area)).isTrue();
        assertThat(tab.getTableDataAsJson().getInteger("selectedIndex")).isEqualTo(1);
        assertThat(tab.handleMouseEvent(MouseEvent.press(MouseButton.LEFT, 40, shipping + 3), area)).isTrue();
        assertThat(tab.getTableDataAsJson().getInteger("selectedIndex")).isEqualTo(2);
    }

    @Test
    void thePanelTheKeysGoToHasTheFocusBorder() {
        TimelineTab tab = new TimelineTab(ctx, now::get);
        Buffer screen = TuiTestHelper.renderToBuffer(tab, 160, 40);
        // ↑↓ select a route: the timeline has the focus
        assertThat(TuiTestHelper.borderColor(screen, "Timeline ─ shop")).isEqualTo(Theme.accent());
        assertThat(TuiTestHelper.borderColor(screen, "orders ─ per second")).isNotEqualTo(Theme.accent());

        // Enter: ↑↓ go over the exchanges of the second
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER));
        screen = TuiTestHelper.renderToBuffer(tab, 160, 40);
        assertThat(TuiTestHelper.borderColor(screen, "Timeline ─ shop")).isNotEqualTo(Theme.accent());
        assertThat(TuiTestHelper.borderColor(screen, "Exchanges at")).isEqualTo(Theme.accent());
    }

    private static int indexOf(List<String> lines, String text) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(text)) {
                return i;
            }
        }
        throw new AssertionError("no line with " + text);
    }

    @Test
    void aMillisecondOfJitterIsNoFullBar() {
        // a fast route: 0 and 1 ms seconds are noise at the bottom, not spikes
        assertThat(TimelineTab.meanScale(1, 0)).isEqualTo(10);
        assertThat(TimelineTab.spark(new long[] { 0, 1, 0, 1 }, TimelineTab.meanScale(1, 0)))
                .doesNotContain("█");
        // a slow second still stands out against a steady time
        assertThat(TimelineTab.meanScale(400, 50)).isEqualTo(400);
        assertThat(TimelineTab.meanScale(60, 50)).isEqualTo(100);
    }
}
