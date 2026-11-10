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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import org.apache.camel.dsl.jbang.core.commands.ai.About;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Overview ▸ About (CAMEL-26700): what an integration does, from its sources, and what has happened, from its timeline.
 */
class AboutViewTest {

    private static final long T0 = 1_800_000_000_000L;

    @TempDir
    Path dir;

    private static String render(AboutView about, IntegrationInfo info) {
        return render(about, info, 160, 34);
    }

    private static String render(AboutView about, IntegrationInfo info, int width, int height) {
        Rect area = new Rect(0, 0, width, height);
        Buffer buffer = Buffer.empty(area);
        about.render(Frame.forTesting(buffer), area, info);
        return TuiTestHelper.bufferToString(buffer);
    }

    @Test
    void thePurposeIsTheCommentAtTheTopOfTheFile() {
        assertThat(About.leadingComment("orders.camel.yaml", """
                # Each order id is accepted once.
                # An accepted order is enriched with its customer.

                - route:
                    id: orders
                """)).isEqualTo("Each order id is accepted once. An accepted order is enriched with its customer.");
        assertThat(About.leadingComment("Shop.java", """
                //DEPS org.apache.camel:camel-http
                import org.apache.camel.builder.RouteBuilder;

                /**
                 * A small shop: every order is reserved and paid.
                 *
                 * @author someone
                 */
                public class Shop extends RouteBuilder {
                """)).isEqualTo("A small shop: every order is reserved and paid.");
        assertThat(About.leadingComment("routes.xml", "<?xml version=\"1.0\"?>\n<!-- Copies the files. -->\n<routes/>"))
                .isEqualTo("Copies the files.");
        // a license is no description
        assertThat(About.leadingComment("a.yaml", "# Licensed to the Apache Software Foundation (ASF)\n- route:"))
                .isNull();
        assertThat(About.leadingComment("a.yaml", "- route:\n    id: a")).isNull();
    }

    @Test
    void aLongPurposeIsWrapped() {
        assertThat(AboutView.wrap("one two three four five six", 10, 2)).containsExactly("one two", "three fou…");
        assertThat(AboutView.wrap("short", 10, 2)).containsExactly("short");
    }

    @Test
    void aSparklineSumsItsSeconds() {
        assertThat(AboutView.spark(new long[] { 0, 0, 2, 2, 0, 8 }, 3)).isEqualTo(" ▄█");
    }

    @Test
    void zoomingInGoesToTheTrouble() {
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "1234";
        RouteInfo r = new RouteInfo();
        r.routeId = "orders";
        info.routes.add(r);
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        long end = T0 / 1000 + 299;
        // the outage was 4.5 minutes ago, for 15 seconds
        ctx.timeline.merge("1234", TimelineTabRenderTest.outage(end, 300, 0).json(), true);
        AboutView about = new AboutView(ctx);
        // 160 wide has room for 100 columns of 3 seconds next to the notes of the route
        assertThat(render(about, info)).contains("What happened ─ last 5m, a column is 3s ─ + zoom in, 4 for each");

        assertThat(about.handleKeyEvent(KeyEvent.ofChar('+'), info)).isTrue();
        // 2 minutes from the first second recorded, with the outage in it
        String to = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochSecond(end - 180));
        assertThat(render(about, info)).contains("What happened ─ 2m to " + to + ", a column is 2s")
                .contains("+ zoom in, - out, ← → move").contains(to).contains("30 handled");

        assertThat(about.handleKeyEvent(KeyEvent.ofChar('+'), info)).isTrue();
        assertThat(render(about, info)).contains("a column is 1s");
        assertThat(about.handleKeyEvent(KeyEvent.ofChar('-'), info)).isTrue();
        assertThat(about.handleKeyEvent(KeyEvent.ofChar('-'), info)).isTrue();
        assertThat(render(about, info)).contains("What happened ─ last 5m").contains("now");
        assertThat(about.handleKeyEvent(KeyEvent.ofChar('x'), info)).isFalse();
    }

    @Test
    void theStripsAreAsWideAsTheRoomAllows() {
        // a whole number of seconds in a column, at least 60 columns and at most 300
        assertThat(AboutView.stripColumns(300, 40)).isEqualTo(60);
        assertThat(AboutView.stripColumns(300, 94)).isEqualTo(75);
        assertThat(AboutView.stripColumns(300, 129)).isEqualTo(100);
        assertThat(AboutView.stripColumns(300, 160)).isEqualTo(150);
        assertThat(AboutView.stripColumns(300, 500)).isEqualTo(300);
        assertThat(AboutView.stripColumns(120, 129)).isEqualTo(120);
        assertThat(AboutView.stripColumns(60, 500)).isEqualTo(60);
    }

    @Test
    void theStripsTakeTheRoomTheNotesLeave() {
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "1234";
        RouteInfo r = new RouteInfo();
        r.routeId = "stock-check";
        info.routes.add(r);
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.timeline.merge("1234", TimelineTabRenderTest.outage(T0 / 1000 + 299, 300, 0).json(), true);

        // a 200 wide screen: the notes after a strip are short, so the strips get 150 columns of 2 seconds, not 100
        assertThat(render(new AboutView(ctx), info, 200, 40)).contains("last 5m, a column is 2s");
    }

    @Test
    void anIntegrationWithoutAStatusOfLateSaysSoInsteadOfRunning() {
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "1234";
        info.errorCount = 2;
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        info.statusWrittenAt = System.currentTimeMillis();
        assertThat(render(new AboutView(ctx), info)).contains("● running").contains("✗ 2 errors");

        // a timer-log held with kill -STOP: its last status 30 seconds ago, and no CPU since
        info.statusWrittenAt = System.currentTimeMillis() - 30_000;
        info.cpuPercent = 0;
        String rendered = render(new AboutView(ctx), info);
        assertThat(rendered).contains("✗ no status").doesNotContain("● running")
                .contains("(30s ago): idle at 0% CPU, as when a debugger holds it")
                // what is to watch now; its errors are of then
                .doesNotContain("2 errors");
    }

    @Test
    void watchCalmsDownWhenTheErrorsStopped() {
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "1234";
        info.errorCount = 13;
        info.errorKinds = List.of(
                new IntegrationInfo.ErrorKind(
                        "stock-check", "io.github.resilience4j.circuitbreaker.CallNotPermittedException", 10),
                new IntegrationInfo.ErrorKind("supplier", "java.net.ConnectException", 3));
        ErrorInfo last = new ErrorInfo();
        info.errors.add(last);
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));

        // the last error 20 seconds ago: still something to watch
        last.timestamp = System.currentTimeMillis() - 20_000;
        assertThat(render(new AboutView(ctx), info))
                .contains("✗ 13 errors: CallNotPermittedException ×10 · ConnectException ×3 · the last 20s ago");

        // 90 seconds ago: none in the last minute, the ones before for the record
        last.timestamp = System.currentTimeMillis() - 90_000;
        String rendered = render(new AboutView(ctx), info);
        assertThat(rendered).contains("no errors in the last minute · 13 errors before: CallNotPermittedException ×10")
                .contains("the last 1m30s ago").contains("9  show errors").doesNotContain("✗");

        // the integration says when the newest error happened, without the Errors tab loading them
        info.errors = List.of();
        info.lastErrorTimestamp = System.currentTimeMillis() - 20_000;
        assertThat(render(new AboutView(ctx), info)).contains("✗ 13 errors").contains("the last 20s ago");
    }

    @Test
    void aTallScreenGivesTheSectionsRoom() {
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "1234";
        for (String id : List.of("orders", "stock")) {
            RouteInfo r = new RouteInfo();
            r.routeId = id;
            info.routes.add(r);
        }
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        TimelineJson t = new TimelineJson(T0 / 1000 + 299, 300);
        for (int i = 0; i < 300; i++) {
            t.route("orders", "completed", i, 2);
            t.route("stock", "completed", i, 1);
        }
        ctx.timeline.merge("1234", t.json(), true);
        AboutView about = new AboutView(ctx);

        // a tall screen: the panels apart, a blank line between the routes, and the strips 2 rows high
        String tall = render(about, info, 198, 48);
        assertThat(tall).contains("╮ ╭");
        // the panels end where the others do, at the right edge
        assertThat(tall.lines().filter(l -> l.contains("╮ ╭")).findFirst().orElseThrow().stripTrailing()).hasSize(198);
        // (the strips are in the What happened panel; the Last 5m line has its own sparkline)
        List<String> strip = tall.lines().filter(l -> l.startsWith("│") && l.contains("██████████")).toList();
        assertThat(strip).hasSize(4);
        assertThat(strip.get(1)).contains("orders");
        assertThat(strip.get(0)).doesNotContain("orders");
        assertThat(tall.lines().filter(l -> l.contains("Next")).findFirst().orElseThrow()).startsWith("   Next");

        // a short one: as compact as before
        String compact = render(about, info, 198, 16);
        assertThat(compact).contains("╮╭");
        assertThat(compact.lines().filter(l -> l.startsWith("│") && l.contains("██████████")).count()).isEqualTo(2);
    }

    @Test
    void watchSaysTheErrorsThatHappenedAndTheirKinds() {
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "1234";
        info.errorCount = 13;
        info.errorKinds = List.of(
                new IntegrationInfo.ErrorKind(
                        "stock-check", "io.github.resilience4j.circuitbreaker.CallNotPermittedException", 10),
                new IntegrationInfo.ErrorKind("supplier", "java.net.ConnectException", 3));
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        assertThat(render(new AboutView(ctx), info))
                .contains("✗ 13 errors: CallNotPermittedException ×10 · ConnectException ×3    9  show errors");

        info.errorCount = 3;
        info.errorKinds = List.of(new IntegrationInfo.ErrorKind("supplier", "java.net.ConnectException", 3));
        assertThat(render(new AboutView(ctx), info)).contains("✗ 3 errors: ConnectException    9  show errors");
    }

    @Test
    void theTimeBeforeTheIntegrationStartedIsBlank() {
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "1234";
        RouteInfo r = new RouteInfo();
        r.routeId = "orders";
        info.routes.add(r);
        long end = T0 / 1000 + 299;
        // started 100 seconds ago, idle for its first 10
        info.uptime = (end - 99) * 1000;
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        TimelineJson t = new TimelineJson(end, 300);
        for (int i = 210; i < 300; i++) {
            t.route("orders", "completed", i, 1);
        }
        ctx.timeline.merge("1234", t.json(), true);
        String line = render(new AboutView(ctx), info, 198, 16).lines().filter(l -> l.contains("orders ")).findFirst()
                .orElseThrow();
        // 100 columns of 3 seconds: 66 blank before it started, then dots for the seconds it did nothing
        String strip = line.substring(line.indexOf("orders") + "orders".length()).stripLeading();
        assertThat(strip).startsWith("···").doesNotContain("· ·");
        assertThat(line.indexOf('·') - line.indexOf("orders")).isGreaterThan(60);
    }

    @Test
    void aRecoveryShowsWhenAndTheFailureItRecoveredFrom() {
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "1234";
        for (String id : List.of("orders", "stock")) {
            RouteInfo r = new RouteInfo();
            r.routeId = id;
            info.routes.add(r);
        }
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        TimelineJson t = new TimelineJson(T0 / 1000 + 299, 300);
        for (int i = 0; i < 300; i++) {
            t.route("orders", "completed", i, 1);
        }
        // redelivered in stock 2 minutes ago, kept under orders where it came in
        t.route("stock", "recovered", 179, 1).route("stock", "redeliveryAttempts", 179, 1);
        t.exchange("R1", "orders", 179, 40, "recovered", "Connection refused", null);
        ctx.timeline.merge("1234", t.json(), true);
        AboutView about = new AboutView(ctx);
        assertThat(render(about, info)).contains("1 recovered 2m ago · Connection refused");
    }

    @Test
    void theSecondsACircuitBreakerOfARouteWasOpen() {
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "1234";
        RouteInfo r = new RouteInfo();
        r.routeId = "orders";
        info.routes.add(r);
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        TimelineJson t = new TimelineJson(T0 / 1000 + 299, 300);
        long[] states = new long[300];
        for (int i = 0; i < 300; i++) {
            t.route("orders", "completed", i, 1);
            states[i] = i >= 230 && i < 240 ? 2 : 0;
        }
        ctx.timeline.merge("1234", t.breaker("orders", "cb1", states).json(), true);
        AboutView about = new AboutView(ctx);
        // the bolt is two columns wide: the screen has a blank cell after it
        String screen = render(about, info).replace("⚡ ", "⚡");
        assertThat(screen).contains("⚡ open 10s 1m ago").contains("▒");
    }

    @Test
    void theSendsOfARouteToAnEndpoint() {
        IntegrationInfo info = new IntegrationInfo();
        info.endpoints.add(endpoint("http://localhost:8765/ship?bridgeEndpoint=true", "out", "orders", 7));
        info.endpoints.add(endpoint("http://localhost:8765/ship", "out", "returns", 2));
        info.endpoints.add(endpoint("http://localhost:8765/ship", "in", "orders", 99));
        assertThat(AboutView.outHits(info, "http:localhost:8765/ship", "orders")).isEqualTo(7);
        assertThat(AboutView.outHits(info, "http://localhost:8765/ship/", "returns")).isEqualTo(2);
        assertThat(AboutView.outHits(info, "{{ship.url}}", "orders")).isNull();
    }

    private static EndpointInfo endpoint(String uri, String direction, String routeId, long hits) {
        EndpointInfo ep = new EndpointInfo();
        ep.uri = uri;
        ep.direction = direction;
        ep.routeId = routeId;
        ep.hits = hits;
        return ep;
    }

    /** The colours of the borders of In and Routes. */
    private static List<Color> borders(AboutView about, IntegrationInfo info) {
        Rect area = new Rect(0, 0, 160, 34);
        Buffer buffer = Buffer.empty(area);
        about.render(Frame.forTesting(buffer), area, info);
        return List.of(TuiTestHelper.borderColor(buffer, " In "), TuiTestHelper.borderColor(buffer, " Routes "));
    }

    @Test
    void theStripsAreInTheOrderOfTheRoutesACalledRouteAfterItsCaller() throws Exception {
        Files.writeString(dir.resolve("orders.camel.yaml"), """
                - route:
                    id: incoming
                    from:
                      uri: timer:orders
                      steps:
                        - wireTap: direct:audit
                - route:
                    id: audit
                    from:
                      uri: direct:audit
                      steps:
                        - log: audited
                """);
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "orders";
        info.directory = dir.toString();
        // the integration lists its routes by id: the called one first
        for (String id : List.of("audit", "incoming")) {
            RouteInfo r = new RouteInfo();
            r.routeId = id;
            r.state = "Started";
            info.routes.add(r);
        }
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.timeline.merge("1234", TimelineTabRenderTest.outage(T0 / 1000 + 59, 60, 0).json(), true);
        AboutView about = new AboutView(ctx);

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            List<String> strips = render(about, info).lines()
                    .dropWhile(l -> !l.contains("What happened"))
                    .filter(l -> l.contains("    incoming ") || l.contains("    audit ")).toList();
            assertThat(strips).hasSize(2);
            assertThat(strips.get(0)).contains("incoming");
            assertThat(strips.get(1)).contains("audit");
        });
    }

    @Test
    void aboutAnIntegrationWithWhatHasHappened() throws Exception {
        Files.writeString(dir.resolve("shop.camel.yaml"), """
                # Every order is checked against the stock and shipped.
                - route:
                    id: orders
                    from:
                      uri: timer:orders
                      parameters:
                        period: 500
                      steps:
                        - to: direct:stock
                        - to: http://localhost:8765/ship
                - route:
                    id: stock
                    from:
                      uri: direct:stock
                      steps:
                        - log: checked
                """);
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "shop";
        info.camelVersion = "4.24.0";
        info.directory = dir.toString();
        for (String id : List.of("orders", "stock")) {
            RouteInfo r = new RouteInfo();
            r.routeId = id;
            r.state = "Started";
            r.total = 120;
            info.routes.add(r);
        }
        info.endpoints.add(endpoint("http://localhost:8765/ship", "out", "orders", 118));
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.timeline.merge("1234", TimelineTabRenderTest.outage(T0 / 1000 + 59, 60, 0).json(), true);
        AboutView about = new AboutView(ctx);

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            String screen = render(about, info);
            assertThat(screen).contains("Every order is checked against the stock and shipped.")
                    .contains("from the comment at the top of shop.camel.yaml");
            assertThat(screen).contains(" In ").contains("timer:orders").contains(" Routes ").contains("orders")
                    .contains("↳ stock").contains(" Out ").contains("localhost:8765/ship").contains("118")
                    .contains("from orders");
            assertThat(screen).contains("Last 5m").contains("30 handled")
                    .contains("HttpOperationFailedException 503 ×30").contains("Watch").contains("Next");
            assertThat(screen).contains("What happened").contains("30 handled");
            // the sparklines of In (the route it starts) and Out (the route that sends), with the rate now
            assertThat(screen).contains("2.0/s").contains("from orders  ");
            assertThat(screen.lines().filter(l -> l.contains("from orders")).findFirst().orElseThrow())
                    .contains("2.0/s");
        });

        // the same for an agent (tui_get_table)
        JsonObject table = about.tableJson(info);
        assertThat(table.getString("view")).isEqualTo("About");
        assertThat(table.getString("purpose")).isEqualTo("Every order is checked against the stock and shipped.");
        JsonObject in = (JsonObject) ((List<?>) table.get("in")).get(0);
        assertThat(in.getString("routeId")).isEqualTo("orders");
        assertThat(in.getString("rate")).isEqualTo("2.0/s");
        assertThat((List<?>) table.get("routes")).hasSize(2);
        assertThat(((JsonObject) ((List<?>) table.get("out")).get(0)).getLong("sends")).isEqualTo(118);
        JsonObject happened = (JsonObject) table.get("whatHappened");
        JsonObject orders = ((List<?>) happened.get("routes")).stream().map(JsonObject.class::cast)
                .filter(r -> "orders".equals(r.getString("routeId"))).findFirst().orElseThrow();
        assertThat(orders.getString("worst")).isEqualTo("handled");
        assertThat(orders.getLong("worstCount")).isEqualTo(30);

        // the cursor goes down In, Routes and Out; Enter opens what it is on
        List<AboutView.Target> opened = new ArrayList<>();
        ctx.openFromAbout = opened::add;
        assertThat(about.open()).isFalse();
        about.moveCursor(1);
        render(about, info);
        assertThat(about.selected()).isEqualTo(new AboutView.Target("orders", null));
        // the panel the cursor is in has the focus border
        assertThat(borders(about, info)).containsExactly(Theme.accent(), Theme.muted().fg().orElseThrow());
        about.moveCursor(2);
        render(about, info);
        assertThat(about.selected()).isEqualTo(new AboutView.Target("stock", null));
        assertThat(borders(about, info)).containsExactly(Theme.muted().fg().orElseThrow(), Theme.accent());
        assertThat(about.selectionContext().items()).contains("in: timer:orders (period=500) → orders", "route: orders",
                "route: stock");
        assertThat(about.selectionContext().selectedIndex()).isEqualTo(2);
        about.moveCursor(5);
        render(about, info);
        assertThat(about.selected()).isEqualTo(new AboutView.Target("orders", "http:localhost:8765/ship"));
        assertThat(about.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER), info)).isTrue();
        assertThat(opened).containsExactly(new AboutView.Target("orders", "http:localhost:8765/ship"));

        // a click puts the cursor on an entry: a route, an endpoint by the line under it, an entry of In
        List<String> screen = render(about, info).lines().toList();
        assertThat(click(about, screen, "↳ stock")).isTrue();
        assertThat(about.selected()).isEqualTo(new AboutView.Target("stock", null));
        assertThat(click(about, screen, "timer:orders")).isTrue();
        assertThat(about.selectionContext().selectedIndex()).isZero();
        assertThat(click(about, screen, "from orders")).isTrue();
        assertThat(about.selected()).isEqualTo(new AboutView.Target("orders", "http:localhost:8765/ship"));
        // the head and What happened are no entries
        assertThat(click(about, screen, "Every order is checked")).isFalse();
        assertThat(click(about, screen, "What happened")).isFalse();
        assertThat(about.selected()).isEqualTo(new AboutView.Target("orders", "http:localhost:8765/ship"));
    }

    /** Clicks on the first line that has the text, on its second character. */
    private static boolean click(AboutView about, List<String> screen, String text) {
        for (int y = 0; y < screen.size(); y++) {
            int x = screen.get(y).indexOf(text);
            if (x >= 0) {
                return about.click(x + 1, y);
            }
        }
        throw new AssertionError(text + " is not on the screen");
    }
}
