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
 * CAMEL-25362: explain with AI (Shift+F8) on the Circuit Breaker tab, for a breaker that is open or whose calls fail.
 */
class CircuitBreakerAiPromptTest {

    private final List<String> asked = new ArrayList<>();
    private final List<String> notified = new ArrayList<>();
    private IntegrationInfo info;
    private MonitorContext ctx;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "circuit-breaker";
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
    void theQuestionHasTheStateCountersConfigurationSourceAndLastError() {
        RouteInfo route = new RouteInfo();
        route.routeId = "stock-check";
        route.source = "stock.camel.yaml:5";
        ProcessorInfo breaker = new ProcessorInfo();
        breaker.id = "supplier-breaker";
        breaker.source = "stock.camel.yaml:9";
        route.processors.add(breaker);
        info.routes.add(route);
        ErrorInfo older = error("stock-check", 1000, "java.net.SocketTimeoutException", "Read timed out");
        ErrorInfo newer = error("stock-check", 2000, "java.net.ConnectException", "Connection refused");
        ErrorInfo other = error("orders", 3000, "java.lang.IllegalStateException", "other route");
        info.errors = new ArrayList<>(List.of(older, newer, other));
        CircuitBreakerInfo cb = openBreaker();

        String question = CircuitBreakerAiPrompt.of(info, cb);

        assertThat(question)
                .startsWith("Explain the circuit breaker supplier-breaker in route stock-check of the running integration"
                            + " circuit-breaker: it is OPEN (resilience4j).\n")
                .contains("Its sliding window: 3 successful, 2 failed (1 of all calls timed out), failure rate 40%.\n")
                .contains("Since it started: 9 calls rejected while open, 11 answered by the fallback.\n")
                .contains("Its configuration: failureRateThreshold=50.0, minimumNumberOfCalls=5,"
                          + " waitDurationInOpenState=10000\n")
                .contains("It is at stock.camel.yaml:9.\n")
                .contains("The last error of the route: ConnectException: Connection refused\n")
                .endsWith("Make no changes to the files unless I agree.");
    }

    @Test
    void aClosedBreakerWithoutFailuresHasNothingToExplain() {
        CircuitBreakerInfo cb = new CircuitBreakerInfo();
        cb.state = "CLOSED";
        cb.successfulCalls = 10;
        assertThat(CircuitBreakerAiPrompt.hasTrouble(cb)).isFalse();
        cb.timedOutCalls = 1;
        assertThat(CircuitBreakerAiPrompt.hasTrouble(cb)).isTrue();
        cb.timedOutCalls = 0;
        cb.state = "HALF_OPEN";
        assertThat(CircuitBreakerAiPrompt.hasTrouble(cb)).isTrue();
    }

    @Test
    void theStatusKeepsTheConfigurationOfABreaker() throws Exception {
        JsonObject root = (JsonObject) Jsoner.deserialize("""
                {"context": {"name": "circuit-breaker"},
                 "resilience4j": {"circuitBreakers": [
                   {"id": "supplier-breaker", "routeId": "stock-check", "state": "OPEN", "failedCalls": 2,
                    "configuration": {"failureRateThreshold": 50.0, "waitDurationInOpenState": 10000}}]}}
                """);
        IntegrationInfo parsed = StatusParser.parseIntegration(ProcessHandle.current(), root);

        assertThat(parsed.circuitBreakers).singleElement()
                .satisfies(cb -> assertThat(cb.configuration).containsKeys("failureRateThreshold",
                        "waitDurationInOpenState"));
    }

    @Test
    void shiftF8AsksAboutTheSelectedBreakerWhenItHasTrouble() {
        CircuitBreakerInfo healthy = new CircuitBreakerInfo();
        healthy.routeId = "checkout";
        healthy.id = "payment-breaker";
        healthy.state = "CLOSED";
        healthy.successfulCalls = 450;
        info.circuitBreakers.add(healthy);
        info.circuitBreakers.add(openBreaker());
        CircuitBreakerTab tab = new CircuitBreakerTab(ctx, new MetricsCollector());
        tab.onTabSelected();

        // sorted by route: checkout first, closed and quiet
        assertThat(fKeyHints(tab)).isEmpty();
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));
        assertThat(asked).isEmpty();
        assertThat(notified).singleElement().asString().startsWith("This circuit breaker is closed");

        tab.tableState.select(1);
        assertThat(fKeyHints(tab)).contains("Shift+F8").contains("explain with AI");
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));
        assertThat(asked).singleElement().asString()
                .startsWith("Explain the circuit breaker supplier-breaker in route stock-check");
    }

    private static CircuitBreakerInfo openBreaker() {
        CircuitBreakerInfo cb = new CircuitBreakerInfo();
        cb.routeId = "stock-check";
        cb.id = "supplier-breaker";
        cb.component = "resilience4j";
        cb.state = "OPEN";
        cb.successfulCalls = 3;
        cb.failedCalls = 2;
        cb.timedOutCalls = 1;
        cb.notPermittedCalls = 9;
        cb.fallbackCalls = 11;
        cb.failureRate = 40;
        Map<String, Object> conf = new LinkedHashMap<>();
        conf.put("failureRateThreshold", 50.0);
        conf.put("minimumNumberOfCalls", 5);
        conf.put("waitDurationInOpenState", 10000);
        conf.put("timeoutDuration", null);
        cb.configuration = conf;
        return cb;
    }

    private static ErrorInfo error(String routeId, long timestamp, String type, String message) {
        ErrorInfo e = new ErrorInfo();
        e.routeId = routeId;
        e.timestamp = timestamp;
        e.exceptionType = type;
        e.exceptionMessage = message;
        return e;
    }

    private static String fKeyHints(MonitorTab tab) {
        List<Span> spans = new ArrayList<>();
        tab.renderFKeyHints(spans);
        StringBuilder sb = new StringBuilder();
        spans.forEach(s -> sb.append(s.content()));
        return sb.toString();
    }
}
