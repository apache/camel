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
 * CAMEL-25362: fix with AI (Shift+F8) on the Kafka tab, for a consumer with an error or that does not run.
 */
class KafkaAiPromptTest {

    private final List<String> asked = new ArrayList<>();
    private final List<String> notified = new ArrayList<>();
    private IntegrationInfo info;
    private MonitorContext ctx;

    @BeforeEach
    void setUp() {
        Theme.resetForTesting();
        info = new IntegrationInfo();
        info.pid = "1234";
        info.name = "kafka-orders";
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
    void theQuestionHasTheStateErrorEndpointGroupSourceAndLastErrorOfTheRoute() {
        RouteInfo route = new RouteInfo();
        route.routeId = "orders";
        route.source = "orders.camel.yaml:3";
        info.routes.add(route);
        ErrorInfo error = new ErrorInfo();
        error.routeId = "orders";
        error.timestamp = 1000;
        error.exceptionType = "org.apache.kafka.common.errors.SerializationException";
        error.exceptionMessage = "Error deserializing key/value";
        info.errors = new ArrayList<>(List.of(error));

        String question = KafkaAiPrompt.of(info, failing());

        assertThat(question)
                .startsWith("Fix the Kafka consumer of route orders in the running integration kafka-orders: it is"
                            + " stopped.\n")
                .contains("Its last error: Error deserializing key/value for partition orders-0 at offset 42\n")
                .contains("Its endpoint: kafka:orders?brokers=localhost:9092\n")
                .contains("Its consumer group: order-service, member consumer-1, generation 3.\n")
                .contains("It last read topic orders, partition 0, offset 41.\n")
                .contains("Its route is at orders.camel.yaml:3.\n")
                .contains("The last error of the route: SerializationException: Error deserializing key/value\n")
                .endsWith("say how to fix that, and make no changes to the files.");
    }

    @Test
    void aRunningOrPausedConsumerWithoutErrorsHasNothingToFix() {
        KafkaConsumerInfo ki = new KafkaConsumerInfo();
        ki.state = "Running";
        assertThat(KafkaAiPrompt.hasTrouble(ki)).isFalse();
        ki.state = "PAUSED";
        assertThat(KafkaAiPrompt.hasTrouble(ki)).isFalse();
        ki.lastError = "Broker may not be available";
        assertThat(KafkaAiPrompt.hasTrouble(ki)).isTrue();
        ki.lastError = null;
        ki.state = "Stopped";
        assertThat(KafkaAiPrompt.hasTrouble(ki)).isTrue();
    }

    @Test
    void aConsumerThatReadNothingSaysSo() {
        KafkaConsumerInfo ki = failing();
        ki.lastTopic = null;

        assertThat(KafkaAiPrompt.of(info, ki)).contains("It has not read a record yet.\n");
    }

    @Test
    void shiftF8AsksAboutTheSelectedConsumerWhenItHasTrouble() {
        KafkaConsumerInfo healthy = new KafkaConsumerInfo();
        healthy.routeId = "billing";
        healthy.state = "Running";
        info.kafkaConsumers.add(healthy);
        info.kafkaConsumers.add(failing());
        KafkaTab tab = new KafkaTab(ctx);
        tab.onTabSelected();

        // sorted by route: billing first, running without errors
        assertThat(fKeyHints(tab)).isEmpty();
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));
        assertThat(asked).isEmpty();
        assertThat(notified).singleElement().asString().startsWith("This Kafka consumer is running without errors");

        tab.tableState.select(1);
        assertThat(fKeyHints(tab)).contains("Shift+F8").contains("fix with AI");
        tab.handleKeyEvent(KeyEvent.ofKey(KeyCode.F8, KeyModifiers.SHIFT));
        assertThat(asked).singleElement().asString().startsWith("Fix the Kafka consumer of route orders");
    }

    private static KafkaConsumerInfo failing() {
        KafkaConsumerInfo ki = new KafkaConsumerInfo();
        ki.routeId = "orders";
        ki.uri = "kafka:orders?brokers=localhost:9092";
        ki.state = "Stopped";
        ki.lastError = "Error deserializing key/value for partition orders-0 at offset 42";
        ki.groupId = "order-service";
        ki.memberId = "consumer-1";
        ki.generationId = 3;
        ki.lastTopic = "orders";
        ki.lastPartition = 0;
        ki.lastOffset = 41;
        return ki;
    }

    private static String fKeyHints(MonitorTab tab) {
        List<Span> spans = new ArrayList<>();
        tab.renderFKeyHints(spans);
        StringBuilder sb = new StringBuilder();
        spans.forEach(s -> sb.append(s.content()));
        return sb.toString();
    }
}
