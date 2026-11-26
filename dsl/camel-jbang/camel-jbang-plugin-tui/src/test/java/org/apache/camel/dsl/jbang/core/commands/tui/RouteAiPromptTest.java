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
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25362: Shift+F8 on the Routes tab: fix with AI a route whose exchanges fail, explain with AI where the time of
 * another goes.
 */
class RouteAiPromptTest {

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
    void theFailuresQuestionHasTheFailingStepsAndTheLastError() {
        RouteInfo route = failingRoute();
        info.routes.add(route);
        ErrorInfo error = new ErrorInfo();
        error.routeId = "orders";
        error.nodeId = "to-payment";
        error.timestamp = 5000;
        error.exceptionType = "org.apache.camel.http.base.HttpOperationFailedException";
        error.exceptionMessage = "HTTP operation failed with statusCode: 503";
        error.repeatCount = 4;
        info.errors = new ArrayList<>(List.of(error));

        String question = RouteAiPrompt.ofFailures(info, route);

        assertThat(question)
                .startsWith("Exchanges fail in route orders of the running integration orders-app: 4 of 120 failed,"
                            + " 2 failures handled by the error handler, 6 redeliveries; the last failure 12s ago.\n")
                .contains("It starts from timer://orders?period=500 and is at orders.camel.yaml:3.\n")
                .contains("  to-payment (to) to http://localhost:8080/pay at orders.camel.yaml:9: 4 failed, 2 handled\n")
                // a step without failures is not listed
                .doesNotContain("log-order")
                .contains("The last error of the route: HttpOperationFailedException: HTTP operation failed with"
                          + " statusCode: 503 (4 times) (at to-payment, orders.camel.yaml:9)\n")
                .endsWith("Dev mode reloads the file when it is saved.");
    }

    @Test
    void thePerformanceQuestionHasTheSlowestStepsFirst() {
        RouteInfo route = failingRoute();
        route.failed = 0;
        route.failuresHandled = 0;
        route.p95Time = 900;
        route.p99Time = 1500;

        String question = RouteAiPrompt.ofPerformance(info, route);

        assertThat(question)
                .startsWith("Explain where the time goes in route orders of the running integration orders-app:"
                            + " 120 exchanges, mean 450ms, max 2s, p95 900ms, p99 1s.\n")
                .contains("The slowest steps by mean time (the time of an EIP includes the steps inside it):\n"
                          + "  to-payment (to) to http://localhost:8080/pay at orders.camel.yaml:9: mean 400ms, max 2s,"
                          + " 120 calls\n"
                          + "  log-order (log) at orders.camel.yaml:7: mean 1ms, max 3ms, 120 calls\n")
                .endsWith("Make no changes to the files unless I agree.");
    }

    @Test
    void shiftF8FixesAFailingRouteAndExplainsAQuietOne() {
        info.routes.add(failingRoute());
        RoutesTab tab = new RoutesTab(ctx);

        assertThat(fKeyHints(tab)).contains("fix with AI");
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));
        assertThat(asked).singleElement().asString().startsWith("Exchanges fail in route orders");

        asked.clear();
        info.routes.get(0).failed = 0;
        info.routes.get(0).failuresHandled = 0;
        assertThat(fKeyHints(tab)).contains("explain with AI");
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));
        assertThat(asked).singleElement().asString().startsWith("Explain where the time goes in route orders");

        asked.clear();
        info.routes.get(0).total = 0;
        assertThat(fKeyHints(tab)).isEmpty();
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));
        assertThat(asked).isEmpty();
        assertThat(notified).containsExactly("No exchanges in this route yet: ask the AI with F8");
    }

    private static RouteInfo failingRoute() {
        RouteInfo route = new RouteInfo();
        route.routeId = "orders";
        route.from = "timer://orders?period=500";
        route.source = "orders.camel.yaml:3";
        route.state = "Started";
        route.total = 120;
        route.failed = 4;
        route.failuresHandled = 2;
        route.redeliveries = 6;
        route.sinceLastFailed = "12s";
        route.meanTime = 450;
        route.maxTime = 2000;
        ProcessorInfo log = new ProcessorInfo();
        log.id = "log-order";
        log.processor = "log";
        log.source = "orders.camel.yaml:7";
        log.total = 120;
        log.meanTime = 1;
        log.maxTime = 3;
        ProcessorInfo pay = new ProcessorInfo();
        pay.id = "to-payment";
        pay.processor = "to";
        pay.uri = "http://localhost:8080/pay";
        pay.source = "orders.camel.yaml:9";
        pay.total = 120;
        pay.failed = 4;
        pay.failuresHandled = 2;
        pay.meanTime = 400;
        pay.maxTime = 2000;
        route.processors.add(log);
        route.processors.add(pay);
        return route;
    }

    private static String fKeyHints(MonitorTab tab) {
        List<Span> spans = new ArrayList<>();
        tab.renderFKeyHints(spans);
        StringBuilder sb = new StringBuilder();
        spans.forEach(s -> sb.append(s.content()));
        return sb.toString();
    }
}
