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
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import org.apache.camel.dsl.jbang.core.commands.ai.About;
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
        Rect area = new Rect(0, 0, 160, 34);
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
        assertThat(render(about, info)).contains("What happened ─ last 5m, a column is 5s ─ + zoom in, 4 for each");

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

        // the cursor goes down In, Routes and Out; Enter opens what it is on
        List<AboutView.Target> opened = new ArrayList<>();
        ctx.openFromAbout = opened::add;
        assertThat(about.open()).isFalse();
        about.moveCursor(1);
        render(about, info);
        assertThat(about.selected()).isEqualTo(new AboutView.Target("orders", null));
        about.moveCursor(2);
        render(about, info);
        assertThat(about.selected()).isEqualTo(new AboutView.Target("stock", null));
        about.moveCursor(5);
        render(about, info);
        assertThat(about.selected()).isEqualTo(new AboutView.Target("orders", "http:localhost:8765/ship"));
        assertThat(about.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER), info)).isTrue();
        assertThat(opened).containsExactly(new AboutView.Target("orders", "http:localhost:8765/ship"));
    }
}
