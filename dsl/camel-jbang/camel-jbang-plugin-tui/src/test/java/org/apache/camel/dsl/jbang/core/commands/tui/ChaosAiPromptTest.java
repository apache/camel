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
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26702: explain with AI (Shift+F8) what a chaos rule did, on the Chaos screen and on an error a rule made on the
 * Errors tab, where it takes the place of fix with AI: the error is not a bug.
 */
class ChaosAiPromptTest {

    private final List<String> asked = new ArrayList<>();
    private IntegrationInfo info;
    private MonitorContext ctx;

    @BeforeEach
    void setUp() throws Exception {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "chaosdemo";
        info.chaos = ChaosInfo.parse((JsonObject) Jsoner.deserialize("""
                {"enabled": true,
                 "rules": [{"id": "r1", "version": 1, "state": "active",
                            "rule": "endpoint=http://localhost:8765/stock* error status=503 rate=0.2 expect=orders:handled<=0",
                            "matches": 120, "fired": {"send": 24}, "rate": 0.2, "observedRate": 0.2,
                            "effect": {"faulted": 22, "completed": 0, "recovered": 21, "handled": 1, "failed": 0,
                                       "inFlight": 0},
                            "expectation": {"route": "orders", "kind": "handled", "op": "<=", "count": 0,
                                            "value": 1, "outcome": "unmet"}}],
                 "report": {"verdict": "FAIL", "final": true, "seed": 42,
                            "routes": [{"route": "orders", "completed": 119, "failed": 0, "failedKeptByConsumer": 0,
                                        "handled": 1, "redeliveries": 25},
                                       {"route": "shipping", "completed": 118, "failed": 0, "failedKeptByConsumer": 0,
                                        "handled": 0, "redeliveries": 0}],
                            "recentFaults": [{"ago": 2000, "rule": "r1", "fault": "error status 503", "hook": "send",
                                              "route": "orders", "endpoint": "http://localhost:8765/stock",
                                              "exchangeId": "3F2A85F3D4213-000000000000104E"}]}}
                """));
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
    }

    @Test
    void theQuestionHasTheRuleItsEffectAndTheRoutesItHit() {
        String question = ChaosAiPrompt.of(info, info.chaos.rule("r1"));

        assertThat(question).startsWith("Explain what chaos rule r1 did to the running integration chaosdemo")
                .contains("endpoint=http://localhost:8765/stock* error status=503 rate=0.2")
                .contains("fired 24 times of 120 matches (rate 20% of 20%)")
                .contains("Effect on 22 exchanges: 21 recovered by redelivery, 1 handled by the error handler")
                .contains("Expectation: orders:handled<=0, unmet (1 handled)")
                .contains("error status 503 at send http://localhost:8765/stock in route orders")
                .contains("Route orders since chaos started: 119 completed, 0 failed, 1 handled by the error handler,"
                          + " 25 redeliveries")
                .contains("redelivery with a delay, an idempotent consumer, a circuit breaker or a timeout")
                .endsWith("make them with camel_edit_file only when I agree.");
        // the routes its faults were not in are left out
        assertThat(question).doesNotContain("Route shipping");
    }

    @Test
    void shiftF8OnTheChaosScreenExplainsTheSelectedRule() {
        ChaosTab tab = new ChaosTab(ctx);
        // the first rule is selected when the table is rendered
        TuiTestHelper.renderToString(tab, 160, 40);
        List<Span> hints = new ArrayList<>();
        tab.renderFKeyHints(hints);
        assertThat(text(hints)).contains("explain with AI");

        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));

        assertThat(asked).hasSize(1);
        assertThat(asked.get(0)).startsWith("Explain what chaos rule r1 did");
    }

    @Test
    void anErrorAChaosRuleMadeIsExplainedNotFixed() {
        ErrorInfo ei = new ErrorInfo();
        ei.exchangeId = "3F2A85F3D4213-000000000000104E";
        ei.routeId = "orders";
        ei.nodeId = "callStock";
        ei.handled = true;
        ei.exceptionType = "org.apache.camel.http.base.HttpOperationFailedException";
        ei.exceptionMessage = "HTTP operation failed with statusCode: 503";
        ei.timestamp = System.currentTimeMillis();
        ei.properties.put(ChaosMarks.PROPERTY, "r1");
        info.errors = new ArrayList<>(List.of(ei));
        ErrorsTab tab = new ErrorsTab(ctx);
        TuiTestHelper.renderToString(tab, 160, 40);
        List<Span> hints = new ArrayList<>();
        tab.renderFKeyHints(hints);
        assertThat(text(hints)).contains("explain with AI").doesNotContain("fix with AI");

        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));

        assertThat(asked).hasSize(1);
        assertThat(asked.get(0))
                .startsWith("This error of the running integration chaosdemo was injected on purpose by chaos rule r1")
                .contains("HttpOperationFailedException").contains("in route orders at callStock")
                .contains("(handled by the error handler)").contains("Do not treat it as a bug to fix");
    }

    private static String text(List<Span> spans) {
        StringBuilder sb = new StringBuilder();
        spans.forEach(s -> sb.append(s.content()));
        return sb.toString();
    }
}
