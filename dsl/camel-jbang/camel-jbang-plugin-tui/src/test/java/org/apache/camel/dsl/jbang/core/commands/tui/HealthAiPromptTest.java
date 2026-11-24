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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25362: fix with AI (Shift+F8) on the Health tab, for the checks that are not UP.
 */
class HealthAiPromptTest {

    private static final long NOW = 1_800_000_000_000L;

    private final List<String> asked = new ArrayList<>();
    private final List<String> notified = new ArrayList<>();
    private IntegrationInfo info;
    private MonitorContext ctx;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "orders-app";
        ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = "1234";
        ctx.askAiCallback = new MonitorContext.AskAi() {
            @Override
            public void fixProblem(Path file, int line, String problem, String lineText) {
                asked.add("fix: " + problem);
            }

            @Override
            public void ask(String question) {
                asked.add(question);
            }
        };
        ctx.notificationCallback = (message, error) -> notified.add(message);
    }

    @Test
    void theQuestionHasTheMessageDetailsRouteAndError() {
        RouteInfo orders = new RouteInfo();
        orders.routeId = "orders";
        orders.source = "orders.camel.yaml:3";
        info.routes.add(orders);
        HealthCheckInfo hc = consumerDown();

        String question = HealthAiPrompt.of(info, List.of(hc), NOW);

        assertThat(question)
                .startsWith("The health check consumers/consumer:orders of the running integration orders-app is DOWN.\n")
                .contains("It is a readiness check: while it is not UP, the integration is taken out of service.\n")
                .contains("Its message: Consumer failed polling 3 times route: orders (kafka://orders)\n")
                .contains("It has been DOWN for 2m0s, and failed 3 of 10 times it ran.\n")
                // the details that say where, not the bookkeeping the page already shows
                .contains("Its details: route.id=orders, endpoint.uri=kafka://orders, consumer.failure=connect\n")
                .doesNotContain("invocation.count").doesNotContain("failure.start.time").doesNotContain("check.kind")
                .contains("Its route orders is at orders.camel.yaml:3.\n")
                .contains("The error:\n```\norg.apache.kafka.common.KafkaException: Failed to construct kafka consumer\n")
                .contains("Find the cause first.").contains("camel_edit_file")
                .endsWith("say how to start or reach it, and make no changes to the files.");
    }

    @Test
    void severalChecksAreAskedAboutTogether() {
        HealthCheckInfo route = new HealthCheckInfo();
        route.group = "camel";
        route.name = "route:shipping";
        route.state = "DOWN";
        route.liveness = true;
        String question = HealthAiPrompt.of(info, List.of(consumerDown(), route), NOW);

        assertThat(question)
                .startsWith("These health checks of the running integration orders-app are not UP:\n")
                .contains("\nThe check consumers/consumer:orders is DOWN.\n")
                .contains("\nThe check camel/route:shipping is DOWN.\n"
                          + "It is a liveness check: while it is not UP, the integration is restarted.\n");
    }

    @Test
    void theRouteOfACheckFromItsDetailsOrName() {
        HealthCheckInfo hc = new HealthCheckInfo();
        hc.name = "route:shipping";
        assertThat(HealthAiPrompt.routeOf(hc)).isEqualTo("shipping");
        hc.details = Map.of("route.id", "orders");
        assertThat(HealthAiPrompt.routeOf(hc)).isEqualTo("orders");
        hc.details = Map.of();
        hc.name = "context";
        assertThat(HealthAiPrompt.routeOf(hc)).isNull();
    }

    @Test
    void aLongStackTraceIsShortenedToTheExceptionsAndTheirFirstFrames() {
        List<String> lines = new ArrayList<>();
        lines.add("java.lang.IllegalStateException: boom");
        for (int i = 0; i < 20; i++) {
            lines.add("\tat org.example.Foo.bar" + i + "(Foo.java:" + i + ")");
        }
        lines.add("Caused by: java.net.ConnectException: Connection refused");
        lines.add("\tat java.base/sun.nio.ch.Net.connect0(Native Method)");
        lines.add("\t... 20 more");

        String trace = HealthAiPrompt.trace(lines);

        assertThat(trace).isEqualTo("""
                java.lang.IllegalStateException: boom
                \tat org.example.Foo.bar0(Foo.java:0)
                \tat org.example.Foo.bar1(Foo.java:1)
                \tat org.example.Foo.bar2(Foo.java:2)
                \t...
                Caused by: java.net.ConnectException: Connection refused
                \tat java.base/sun.nio.ch.Net.connect0(Native Method)
                \t... 20 more
                """);
        assertThat(HealthAiPrompt.trace(List.of())).isNull();
    }

    @Test
    void theStatusKeepsTheStackTraceAndDetailsOfACheck() throws Exception {
        JsonObject root = (JsonObject) Jsoner.deserialize("""
                {"context": {"name": "orders-app"},
                 "healthChecks": {"ready": false, "live": true, "checks": [
                   {"id": "consumer:orders", "group": "consumers", "state": "DOWN", "readiness": true,
                    "message": "Consumer failed polling",
                    "stackTrace": ["java.net.ConnectException: Connection refused", "\\tat a.B.c(B.java:1)"],
                    "details": {"route.id": "orders", "endpoint.uri": "kafka://orders", "failure.count": 2}}]}}
                """);
        IntegrationInfo parsed = StatusParser.parseIntegration(ProcessHandle.current(), root);

        HealthCheckInfo hc = parsed.healthChecks.get(0);
        assertThat(hc.stackTrace).containsExactly("java.net.ConnectException: Connection refused", "\tat a.B.c(B.java:1)");
        assertThat(hc.details).containsEntry("route.id", "orders").containsEntry("endpoint.uri", "kafka://orders")
                .containsEntry("failure.count", "2");
        assertThat(hc.failures).isEqualTo(2);
    }

    @Test
    void shiftF8OnThePageAsksAboutTheChecksThatAreNotUp() {
        info.healthChecks.add(up("context"));
        info.healthChecks.add(consumerDown());
        HealthTab tab = new HealthTab(ctx);

        assertThat(fKeyHints(tab)).contains("Shift+F8").contains("fix with AI");
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));

        assertThat(asked).singleElement().asString()
                .startsWith("The health check consumers/consumer:orders of the running integration orders-app is DOWN.");
    }

    @Test
    void shiftF8InTheTableAsksAboutTheSelectedCheckOnlyWhenItIsNotUp() {
        info.healthChecks.add(up("context"));
        info.healthChecks.add(consumerDown());
        HealthTab tab = new HealthTab(ctx);
        tab.handleKeyEvent(KeyEvent.ofChar('t', KeyModifiers.NONE));
        // sorted by name: consumer:orders, context
        tab.tableState.select(1);

        assertThat(fKeyHints(tab)).doesNotContain("Shift+F8");
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));
        assertThat(asked).isEmpty();
        assertThat(notified).singleElement().asString().startsWith("The selected check is UP");

        tab.tableState.select(0);
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));
        assertThat(asked).singleElement().asString().contains("consumers/consumer:orders");
    }

    @Test
    void noHintWhenAllChecksAreUp() {
        info.healthChecks.add(up("context"));
        HealthTab tab = new HealthTab(ctx);

        assertThat(fKeyHints(tab)).isEmpty();
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));
        assertThat(asked).isEmpty();
        assertThat(notified).containsExactly("All checks are UP: ask the AI with F8");
    }

    private static HealthCheckInfo consumerDown() {
        HealthCheckInfo hc = new HealthCheckInfo();
        hc.group = "consumers";
        hc.name = "consumer:orders";
        hc.state = "DOWN";
        hc.readiness = true;
        hc.message = "Consumer failed polling 3 times route: orders (kafka://orders)";
        hc.since = NOW - 120_000;
        hc.failures = 3;
        hc.invocations = 10;
        Map<String, String> details = new LinkedHashMap<>();
        details.put("check.kind", "READINESS");
        details.put("invocation.count", "10");
        details.put("failure.start.time", "2026-11-17T10:00:00+01:00");
        details.put("route.id", "orders");
        details.put("endpoint.uri", "kafka://orders");
        details.put("consumer.failure", "connect");
        hc.details = details;
        hc.stackTrace = List.of("org.apache.kafka.common.KafkaException: Failed to construct kafka consumer",
                "\tat org.apache.kafka.clients.consumer.KafkaConsumer.<init>(KafkaConsumer.java:830)");
        return hc;
    }

    private static HealthCheckInfo up(String name) {
        HealthCheckInfo hc = new HealthCheckInfo();
        hc.group = "camel";
        hc.name = name;
        hc.state = "UP";
        hc.readiness = true;
        return hc;
    }

    private static String fKeyHints(MonitorTab tab) {
        List<Span> spans = new ArrayList<>();
        tab.renderFKeyHints(spans);
        StringBuilder sb = new StringBuilder();
        spans.forEach(s -> sb.append(s.content()));
        return sb.toString();
    }
}
