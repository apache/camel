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

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.language.typesafeai.TypeSafeAiLanguage;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.apache.camel.builder.Builder.body;
import static org.apache.camel.builder.Builder.header;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeSafeAiLanguageTest extends TypeSafeAiTestSupport {
    private Predicate predicate(double threshold) {
        return predicate("typesafe-ai:semantic", body(), "Is a refund requested?", threshold);
    }

    private Exchange exchange(Object body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);
        return exchange;
    }

    @ParameterizedTest
    @CsvSource({ "0,0,true", "0.7999,0.8,false", "0.8,0.8,true", "0.8001,0.8,true", "1,1,true", "0.9999,1,false" })
    void appliesInclusiveThresholdAndPreservesBody(double probability, double threshold, boolean expected) {
        respond = request -> noulResponse(probability);
        Object original = Map.of("text", "Please refund", "id", "123");
        Exchange exchange = exchange(original);
        Predicate predicate = predicate(threshold);
        predicate.init(context);
        assertThat(predicate.matches(exchange)).isEqualTo(expected);
        assertThat(exchange.getMessage().getBody()).isSameAs(original);
        JsonObject result = exchange.getProperty(TypeSafeAiLanguage.RESULT, JsonObject.class);
        assertThat(((Number) result.path("answers.predicate.noul")).doubleValue()).isEqualTo(probability);
        assertThat(result.get("model")).isEqualTo("jev-1.13.0");
        assertThat(requests).hasSize(1);
        assertThat(requests.peek().get("state")).isEqualTo(original);
    }

    @ParameterizedTest
    @CsvSource({ "0.374,false", "0.375,false", "0.5,false", "0.625,false", "0.626,true" })
    void nonMatchUncertaintyBandIncludesBothBoundaries(double probability, boolean expected) {
        respond = request -> noulResponse(probability);
        Predicate predicate = context.resolveLanguage("typesafe-ai").createPredicate("Refund?",
                new Object[] { "typesafe-ai:semantic", 0.5, 0.125, TypeSafeAiLanguage.UncertaintyPolicy.NonMatch });
        predicate.init(context);
        assertThat(predicate.matches(exchange("Refund?"))).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(doubles = { 0.375, 0.5, 0.625 })
    void uncertainFailureRetainsEvaluation(double probability) {
        respond = request -> noulResponse(probability);
        Predicate predicate = context.resolveLanguage("typesafe-ai").createPredicate("Refund?",
                new Object[] { "typesafe-ai:semantic", 0.5, 0.125, TypeSafeAiLanguage.UncertaintyPolicy.Fail });
        predicate.init(context);
        Exchange exchange = exchange("Refund?");
        assertThatThrownBy(() -> predicate.matches(exchange)).hasCauseInstanceOf(TypeSafeAiUncertainResultException.class);
        assertThat(exchange.getProperty(TypeSafeAiLanguage.RESULT)).isInstanceOf(JsonObject.class);
        assertThat(exchange.getMessage().getBody()).isEqualTo("Refund?");
    }

    @Test
    void selectsStateExplicitly() {
        Predicate predicate = predicate("typesafe-ai:semantic", header("selected"), "Refund?", 0.8);
        predicate.init(context);
        Exchange exchange = exchange("PRIVATE BODY");
        exchange.getMessage().setHeader("selected", "Refund the payment");
        exchange.getMessage().setHeader("private", "PRIVATE HEADER");
        assertThat(predicate.matches(exchange)).isTrue();
        assertThat(requests.peek().path("state")).isEqualTo("Refund the payment");
        assertThat(requests.peek().path("questions.predicate.instructions")).isEqualTo("Refund?");
        assertThat(requests.peek().toJson()).doesNotContain("PRIVATE");
        assertThat(exchange.getMessage().getBody()).isEqualTo("PRIVATE BODY");
    }

    @Test
    void reevaluatesChangedStateAndClearsStaleResultOnFailure() {
        respond = request -> noulResponse("refund".equals(request.get("state")) ? 0.9 : 0.1);
        Predicate predicate = predicate(0.8);
        predicate.init(context);
        Exchange exchange = exchange("refund");
        assertThat(predicate.matches(exchange)).isTrue();
        Object first = exchange.getProperty(TypeSafeAiLanguage.RESULT);
        exchange.getMessage().setBody("hello");
        assertThat(predicate.matches(exchange)).isFalse();
        assertThat(exchange.getProperty(TypeSafeAiLanguage.RESULT)).isNotSameAs(first);
        status = 500;
        assertThatThrownBy(() -> predicate.matches(exchange)).hasCauseInstanceOf(TypeSafeAiHttpException.class);
        assertThat(exchange.getProperty(TypeSafeAiLanguage.RESULT)).isNull();
        assertThat(requests).hasSize(3);
    }

    @Test
    void sharesPredicateConcurrentlyWithoutLeakingResults() throws Exception {
        respond = request -> noulResponse(request.get("state").toString().startsWith("yes") ? 0.9 : 0.1);
        Predicate predicate = predicate("typesafe-ai:semantic", body(), "Refund?", 0.8);
        predicate.init(context);
        ExecutorService callers = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Exchange>> calls = IntStream.range(0, 12).mapToObj(i -> (Callable<Exchange>) () -> {
                boolean yes = i % 2 == 0;
                Exchange exchange = exchange((yes ? "yes" : "no") + i);
                assertThat(predicate.matches(exchange)).isEqualTo(yes);
                JsonObject response = exchange.getProperty(TypeSafeAiLanguage.RESULT, JsonObject.class);
                assertThat(((Number) response.path("answers.predicate.noul")).doubleValue()).isEqualTo(yes ? 0.9 : 0.1);
                return exchange;
            }).toList();
            for (Future<Exchange> done : callers.invokeAll(calls, 10, TimeUnit.SECONDS)) {
                assertThat(done.get()).isNotNull();
            }
        } finally {
            callers.shutdownNow();
        }
        assertThat(requests).hasSize(12).allSatisfy(request -> assertThat(request.getJsonObject("questions")
                .getJsonObject("predicate").get("instructions"))
                .isEqualTo("Refund?"));
    }

    @Test
    void worksInChoiceFilterValidateAndRefWithNormalNonMatchSemantics() throws Exception {
        respond = request -> noulResponse("refund".equals(request.get("state")) ? 0.9 : 0.1);
        Predicate predicate = predicate(0.8);
        predicate.init(context);
        context.getRegistry().bind("refund", predicate);
        AtomicInteger admitted = new AtomicInteger();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(noErrorHandler());
                from("direct:choice").choice().when(predicate).setHeader("branch", constant("refund"))
                        .otherwise().setHeader("branch", constant("other"));
                from("direct:filter").filter().ref("refund").process(e -> admitted.incrementAndGet());
                from("direct:validate").validate(predicate).setHeader("validated", constant(true));
            }
        });
        Exchange yes = template.request("direct:choice", e -> e.getMessage().setBody("refund"));
        Exchange no = template.request("direct:choice", e -> e.getMessage().setBody("hello"));
        assertThat(yes.getMessage().getHeader("branch")).isEqualTo("refund");
        assertThat(no.getMessage().getHeader("branch")).isEqualTo("other");
        template.sendBody("direct:filter", "refund");
        template.sendBody("direct:filter", "hello");
        assertThat(admitted).hasValue(1);
        Exchange valid = template.request("direct:validate", e -> e.getMessage().setBody("refund"));
        Exchange invalid = template.request("direct:validate", e -> e.getMessage().setBody("hello"));
        assertThat(valid.getException()).isNull();
        assertThat(invalid.getException()).isNotNull();
        assertThat(valid.getMessage().getBody()).isEqualTo("refund");
        assertThat(requests).hasSize(6);
        status = 500;
        Exchange failed = template.request("direct:choice", e -> e.getMessage().setBody("refund"));
        assertThat(failed.getException()).isNotNull();
        assertThat(failed.getMessage().getHeader("branch")).isNull();
    }

    @Test
    void compositionAndChoiceRetainShortCircuitSemantics() throws Exception {
        Predicate composed = PredicateBuilder.and(header("evaluate").isEqualTo(true), predicate(0.8));
        Exchange exchange = exchange("refund");
        composed.init(context);
        assertThat(composed.matches(exchange)).isFalse();
        assertThat(requests).isEmpty();
        exchange.getMessage().setHeader("evaluate", true);
        assertThat(composed.matches(exchange)).isTrue();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:first").choice().when(predicate(0.8)).setHeader("branch", constant("first"))
                        .when(predicate(0.7)).setHeader("branch", constant("second"));
            }
        });
        Exchange first = template.request("direct:first", e -> e.getMessage().setBody("refund"));
        assertThat(first.getMessage().getHeader("branch")).isEqualTo("first");
        assertThat(requests).hasSize(2);
    }

    @Test
    void loopChecksUpdatedStateOnEveryIteration() throws Exception {
        respond = request -> noulResponse("again".equals(request.get("state")) ? 0.9 : 0.1);
        AtomicInteger iterations = new AtomicInteger();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:loop").loopDoWhile(predicate(0.8)).process(e -> {
                    iterations.incrementAndGet();
                    e.getMessage().setBody("done");
                }).end();
            }
        });
        assertThat(template.requestBody("direct:loop", "again", String.class)).isEqualTo("done");
        assertThat(iterations).hasValue(1);
        assertThat(requests).extracting(r -> r.get("state")).containsExactly("again", "done");
    }

    @Test
    void predicateOnlyEndpointParticipatesInContextLifecycle() throws Exception {
        Predicate predicate = predicate(0.8);
        predicate.init(context);
        assertThat(predicate.matches(exchange("refund"))).isTrue();
        TypeSafeAiEndpoint endpoint = context.getEndpoint("typesafe-ai:semantic", TypeSafeAiEndpoint.class);
        endpoint.stop();
        assertThat(endpoint.isStopped()).isTrue();
        assertThatThrownBy(() -> predicate.matches(exchange("refund")))
                .hasCauseInstanceOf(IllegalStateException.class).hasRootCauseMessage("TypeSafe AI endpoint is not started");
        assertThatThrownBy(() -> new TypeSafeAiProducer(endpoint).process(exchange(request("refund"))))
                .isInstanceOf(IllegalStateException.class).hasMessage("TypeSafe AI endpoint is not started");
        assertThat(endpoint.isStopped()).isTrue();
        assertThat(requests).hasSize(1);
        endpoint.start();
        assertThat(predicate.matches(exchange("refund"))).isTrue();
        assertThat(requests).hasSize(2);
        context.stop();
        assertThat(endpoint.isStopped()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(doubles = { -0.01, 1.01, Double.NaN, Double.POSITIVE_INFINITY })
    void rejectsInvalidThresholds(double threshold) {
        assertThatThrownBy(() -> predicate(threshold)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expressionReturnsBooleanAndPreservesBody() {
        var expression = context.resolveLanguage("typesafe-ai").createExpression("Refund?");
        expression.init(context);
        Exchange exchange = exchange("Refund");
        assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
        assertThat(exchange.getMessage().getBody()).isEqualTo("Refund");
        assertThat(exchange.getProperty(TypeSafeAiLanguage.RESULT)).isInstanceOf(JsonObject.class);
    }

    @Test
    void validatesQuestionsWithoutAnEndpointOrCredentials() {
        TypeSafeAiLanguage language = new TypeSafeAiLanguage();
        assertThat(language.validatePredicate("Refund?")).isTrue();
        for (String question : new String[] { null, "", "  " }) {
            assertThatThrownBy(() -> language.createPredicate(question))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("TypeSafe AI question");
        }
        assertThat(requests).isEmpty();
    }

    @Test
    void endpointOptionsNeverAppearInTheExpressionDescription() {
        Predicate predicate = predicate("typesafe-ai:refund?apiKey=PRIVATE", body(), "PRIVATE QUESTION", 0.8);
        assertThat(predicate.toString()).contains("typesafe-ai:refund", "0.8").doesNotContain("PRIVATE", "apiKey", "?");
    }
}
