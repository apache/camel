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
package org.apache.camel.component.typesafeai;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.apache.camel.builder.Builder.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class TypeSafeAiPropertiesTest extends TypeSafeAiTestSupport {
    private Main configuredMain() {
        Main main = new Main();
        main.addProperty("camel.component.typesafe-ai.api-key", "test-key");
        main.addProperty("camel.component.typesafe-ai.base-url", "http://127.0.0.1:" + server.getAddress().getPort());
        main.addProperty("camel.component.typesafe-ai.model", "jev-1.13.0");
        main.addProperty("camel.component.typesafe-ai.request-timeout", "2000");
        main.addProperty("camel.component.typesafe-ai.questions",
                Jsoner.serialize(Map.of("refund", noulQuestion("Refund requested?"))));
        main.addProperty("camel.component.typesafe-ai.state", "${header.selected}");
        main.addProperty("camel.component.typesafe-ai.result-property", "evaluation");
        main.addProperty("refund.threshold", "0.8");
        respond = request -> result(Map.of("refund", Map.of("type", "noul",
                "noul", switch (request.getString("state")) {
                    case "refund" -> 0.9;
                    case "boundary" -> 0.8;
                    default -> 0.1;
                })));
        return main;
    }

    @Test
    void configuresProducerAndLocalRoutingEntirelyThroughProperties() throws Exception {
        Main main = configuredMain();
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(noErrorHandler());
                from("direct:produce").to("typesafe-ai:configured");
                from("direct:choose").to("typesafe-ai:configured").choice()
                        .when().simple("${exchangeProperty.evaluation[answers][refund][noul]} >= '{{refund.threshold}}'")
                        .setHeader("branch", constant("refund")).otherwise().setHeader("branch", constant("other"));
                from("direct:filter").to("typesafe-ai:configured")
                        .filter().simple("${exchangeProperty.evaluation[answers][refund][noul]} >= '{{refund.threshold}}'")
                        .setHeader("admitted", constant(true));
                from("direct:validate").to("typesafe-ai:configured")
                        .validate().simple("${exchangeProperty.evaluation[answers][refund][noul]} >= '{{refund.threshold}}'");
            }
        });
        try {
            main.start();
            var producer = main.getCamelContext().createProducerTemplate();
            Exchange evaluated = producer.request("direct:produce", e -> {
                e.getMessage().setBody("PRIVATE BODY");
                e.getMessage().setHeader("selected", "refund");
            });
            assertThat(evaluated.getException()).isNull();
            assertThat(evaluated.getMessage().getBody()).isEqualTo("PRIVATE BODY");
            assertThat(evaluated.getProperty("evaluation", JsonObject.class).path("answers.refund.noul")).isNotNull();
            for (String route : new String[] { "direct:choose", "direct:filter", "direct:validate" }) {
                for (String selected : new String[] { "refund", "boundary" }) {
                    Exchange accepted = producer.request(route, e -> {
                        e.getMessage().setBody("PRIVATE BODY");
                        e.getMessage().setHeader("selected", selected);
                    });
                    assertThat(accepted.getException()).isNull();
                    assertThat(accepted.getMessage().getBody()).isEqualTo("PRIVATE BODY");
                    assertThat(accepted.getProperty("evaluation")).isInstanceOf(JsonObject.class);
                }
            }
            Exchange rejected = producer.request("direct:choose", e -> e.getMessage().setHeader("selected", "hello"));
            assertThat(rejected.getException()).isNull();
            assertThat(rejected.getProperty("evaluation", JsonObject.class)
                    .getJsonObject("answers").getJsonObject("refund").getDouble("noul")).isEqualTo(0.1);
            assertThat(rejected.getMessage().getHeader("branch")).isEqualTo("other");
            Exchange filtered = producer.request("direct:filter", e -> e.getMessage().setHeader("selected", "hello"));
            assertThat(filtered.getException()).isNull();
            assertThat(filtered.getMessage().getHeader("admitted")).isNull();
            Exchange invalid = producer.request("direct:validate", e -> e.getMessage().setHeader("selected", "hello"));
            assertThat(invalid.getException()).isNotNull();
            assertThat(requests).hasSize(10).allSatisfy(request -> assertThat(request.toJson()).doesNotContain("PRIVATE BODY"));
            assertThat(main.getCamelContext().getRegistry().findByType(Predicate.class)).isEmpty();
            producer.stop();
        } finally {
            main.stop();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "{PRIVATE", "[]", "{}", "{\"q\":{\"type\":\"unknown\"}}" })
    void namesInvalidQuestionsOptionWithoutEchoingItsValue(String questions) throws Exception {
        Main main = configuredMain();
        main.addProperty("camel.component.typesafe-ai.questions", questions);
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:invalid").to("typesafe-ai:invalid");
            }
        });
        try {
            assertThatThrownBy(main::start).rootCause()
                    .hasMessageStartingWith("Invalid questions option:").hasMessageNotContaining("PRIVATE");
            assertThat(requests).isEmpty();
        } finally {
            main.stop();
        }
    }

    @Test
    void configuresConcurrencyLimitThroughProperties() throws Exception {
        Main main = configuredMain();
        main.addProperty("camel.component.typesafe-ai.max-concurrent-requests", "1");
        main.addProperty("camel.component.typesafe-ai.request-timeout", "30000");
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                onException(RejectedExecutionException.class).handled(true).setHeader("overloaded", constant(true));
                from("direct:limited").to("typesafe-ai:limited");
            }
        });
        holdHeaders = true;
        try {
            main.start();
            try (var producer = main.getCamelContext().createProducerTemplate()) {
                var first = producer.asyncSend("direct:limited", e -> e.getMessage().setHeader("selected", "refund"));
                await().atMost(5, TimeUnit.SECONDS).until(() -> requests.size() == 1);
                var rejected = producer.asyncSend("direct:limited", e -> e.getMessage().setHeader("selected", "refund"))
                        .get(5, TimeUnit.SECONDS);
                assertThat(rejected.getException()).isNull();
                assertThat(rejected.getMessage().getHeader("overloaded")).isEqualTo(true);
                assertThat(rejected.getProperty("evaluation")).isNull();
                assertThat(requests).hasSize(1);
                release.countDown();
                assertThat(first.get(5, TimeUnit.SECONDS).getProperty("evaluation")).isInstanceOf(JsonObject.class);
                var next = producer.request("direct:limited", e -> e.getMessage().setHeader("selected", "refund"));
                assertThat(next.getException()).isNull();
                assertThat(next.getProperty("evaluation")).isInstanceOf(JsonObject.class);
                assertThat(requests).hasSize(2);
            }
        } finally {
            release.countDown();
            main.stop();
        }
    }

    @Test
    void convertsStreamStateWithBodyAsString() throws Exception {
        Main main = configuredMain();
        main.addProperty("camel.component.typesafe-ai.state", "${bodyAs(String)}");
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:stream").to("typesafe-ai:stream");
            }
        });
        try {
            main.start();
            try (var producer = main.getCamelContext().createProducerTemplate()) {
                Exchange result = producer.request("direct:stream", e -> e.getMessage()
                        .setBody(new ByteArrayInputStream("refund".getBytes(StandardCharsets.UTF_8))));
                assertThat(result.getException()).isNull();
                assertThat(result.getProperty("evaluation")).isInstanceOf(JsonObject.class);
                assertThat(requests).hasSize(1);
                assertThat(requests.peek().get("state")).isEqualTo("refund");
            }
        } finally {
            main.stop();
        }
    }

    @Test
    void producerAndPredicateReportTheSameNullStateError() throws Exception {
        TypeSafeAiComponent component = context.getComponent("typesafe-ai", TypeSafeAiComponent.class);
        component.getConfiguration().setQuestions("{\"q\":{\"type\":\"noul\"}}");
        TypeSafeAiEndpoint endpoint = context.getEndpoint("typesafe-ai:null-state", TypeSafeAiEndpoint.class);
        Predicate predicate = predicate("typesafe-ai:null-state", body(), "Refund?", 0.8);
        predicate.init(context);

        assertThatThrownBy(() -> new TypeSafeAiProducer(endpoint).process(new DefaultExchange(context)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("TypeSafe AI state must not be null");
        assertThatThrownBy(() -> predicate.matches(new DefaultExchange(context)))
                .hasCauseInstanceOf(IllegalArgumentException.class).hasRootCauseMessage("TypeSafe AI state must not be null");
        assertThat(requests).isEmpty();
    }
}
