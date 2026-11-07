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
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A real error as a chaos rule ({@link ChaosFromError}): {@code !} on the Errors tab opens the Chaos screen with a rule
 * that makes the same failure again.
 */
class ChaosFromErrorTest {

    private IntegrationInfo info;
    private MonitorContext ctx;
    private final List<String> opened = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "chaosdemo";
        info.chaos = ChaosInfo.parse((JsonObject) Jsoner.deserialize("""
                {"enabled": true, "rules": [], "report": {"verdict": "PASS", "final": true, "seed": 42}}
                """));
        ctx = new MonitorContext(new AtomicReference<>(List.of(info)), new AtomicReference<>(List.of()));
        ctx.selectedPid = "1234";
        ctx.chaosRule = opened::add;
    }

    private static ErrorInfo error(String nodeId, String type, String message) {
        ErrorInfo ei = new ErrorInfo();
        ei.exchangeId = "ID-1";
        ei.routeId = "orders";
        ei.nodeId = nodeId;
        ei.exceptionType = type;
        ei.exceptionMessage = message;
        ei.timestamp = System.currentTimeMillis();
        return ei;
    }

    @Test
    void theSameExceptionAtTheSameStep() {
        assertThat(ChaosFromError.rule(error("callStock", "java.net.ConnectException", "Connection refused")))
                .isEqualTo("step=callStock error exception=java.net.ConnectException message=\"Connection refused\" until=10");
        // the message on one line, without the quotes of the rule, and not too long
        String rule = ChaosFromError.rule(error("callStock", "java.lang.IllegalStateException",
                "the \"stock\" is\n  gone " + "x".repeat(200)));
        assertThat(rule)
                .startsWith("step=callStock error exception=java.lang.IllegalStateException message=\"the 'stock' is gone x")
                .endsWith("...\" until=10");
        // without a message
        assertThat(ChaosFromError.rule(error("callStock", "java.io.IOException", null)))
                .isEqualTo("step=callStock error exception=java.io.IOException until=10");
    }

    @Test
    void anHttpFailureIsTheSameStatusAtTheSameEndpoint() {
        ErrorInfo http = error("callStock", "org.apache.camel.http.base.HttpOperationFailedException",
                "HTTP operation failed invoking http://localhost:8765/stock with statusCode: 503");
        assertThat(ChaosFromError.rule(http))
                .isEqualTo("endpoint=http://localhost:8765/stock in=orders error status=503 until=10");
        // the endpoint of the error when it has one
        http.endpointUri = "https://inventory/stock?bridgeEndpoint=true";
        assertThat(ChaosFromError.rule(http)).isEqualTo("endpoint=https://inventory/stock in=orders error status=503 until=10");
    }

    @Test
    void withoutAStepTheEndpointOrTheRoute() {
        ErrorInfo ei = error(null, "java.io.IOException", "boom");
        ei.endpointUri = "kafka:orders?brokers=localhost:9092";
        assertThat(ChaosFromError.rule(ei))
                .isEqualTo("endpoint=kafka:orders error exception=java.io.IOException message=\"boom\" until=10");
        ei.endpointUri = null;
        assertThat(ChaosFromError.rule(ei))
                .isEqualTo("route=orders error exception=java.io.IOException message=\"boom\" until=10");
        ei.routeId = null;
        assertThat(ChaosFromError.rule(ei)).isNull();
        assertThat(ChaosFromError.rule(error("callStock", null, "boom"))).isNull();
    }

    @Test
    void theErrorsTabOpensTheChaosScreenWithTheRule() {
        info.errors = new ArrayList<>(List.of(error("callStock", "java.net.ConnectException", "Connection refused")));
        ErrorsTab tab = new ErrorsTab(ctx);
        TuiTestHelper.renderToString(tab, 160, 40);
        List<Span> footer = new ArrayList<>();
        tab.renderFooter(footer);
        assertThat(footer.stream().map(Span::content).reduce("", String::concat)).contains("chaos rule of it");

        tab.handleKeyEvent(KeyEvent.ofChar('!', KeyModifiers.NONE));
        assertThat(opened).containsExactly(
                "step=callStock error exception=java.net.ConnectException message=\"Connection refused\" until=10");
    }

    @Test
    void notForAnErrorMadeByChaosNorWhenChaosIsOff() {
        ErrorInfo made = error("callStock", "java.net.ConnectException", "Connection refused");
        made.properties.put(ChaosMarks.PROPERTY, "r1");
        info.errors = new ArrayList<>(List.of(made));
        ErrorsTab tab = new ErrorsTab(ctx);
        TuiTestHelper.renderToString(tab, 160, 40);
        tab.handleKeyEvent(KeyEvent.ofChar('!', KeyModifiers.NONE));
        assertThat(opened).isEmpty();

        made.properties.clear();
        info.chaos.enabled = false;
        tab.handleKeyEvent(KeyEvent.ofChar('!', KeyModifiers.NONE));
        assertThat(opened).isEmpty();
        assertThat(tab.chaosRuleOfSelectedError()).isNull();
    }
}
