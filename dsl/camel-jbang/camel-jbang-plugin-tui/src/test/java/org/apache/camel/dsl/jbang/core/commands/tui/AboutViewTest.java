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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
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
        Rect area = new Rect(0, 0, 160, 30);
        Buffer buffer = Buffer.empty(area);
        about.render(Frame.forTesting(buffer), area, info);
        return TuiTestHelper.bufferToString(buffer);
    }

    @Test
    void thePurposeIsTheCommentAtTheTopOfTheFile() {
        assertThat(AboutView.leadingComment("orders.camel.yaml", """
                # Each order id is accepted once.
                # An accepted order is enriched with its customer.

                - route:
                    id: orders
                """)).isEqualTo("Each order id is accepted once. An accepted order is enriched with its customer.");
        assertThat(AboutView.leadingComment("Shop.java", """
                //DEPS org.apache.camel:camel-http
                import org.apache.camel.builder.RouteBuilder;

                /**
                 * A small shop: every order is reserved and paid.
                 *
                 * @author someone
                 */
                public class Shop extends RouteBuilder {
                """)).isEqualTo("A small shop: every order is reserved and paid.");
        assertThat(AboutView.leadingComment("routes.xml", "<?xml version=\"1.0\"?>\n<!-- Copies the files. -->\n<routes/>"))
                .isEqualTo("Copies the files.");
        // a license is no description
        assertThat(AboutView.leadingComment("a.yaml", "# Licensed to the Apache Software Foundation (ASF)\n- route:"))
                .isNull();
        assertThat(AboutView.leadingComment("a.yaml", "- route:\n    id: a")).isNull();
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
        MonitorContext ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.timeline.merge("1234", TimelineTabRenderTest.outage(T0 / 1000 + 59, 60, 0).json(), true);
        AboutView about = new AboutView(ctx);

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            String screen = render(about, info);
            assertThat(screen).contains("Every order is checked against the stock and shipped.")
                    .contains("from the comment at the top of shop.camel.yaml");
            assertThat(screen).contains(" In ").contains("timer:orders").contains(" Routes ").contains("orders")
                    .contains("↳ stock").contains(" Out ").contains("localhost:8765/ship").contains("from orders");
            assertThat(screen).contains("Last 5m").contains("30 handled")
                    .contains("HttpOperationFailedException 503 ×30").contains("Watch").contains("Next");
        });
    }
}
