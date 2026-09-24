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
package org.apache.camel.semantic;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticRetryAndValidationTest extends CamelTestSupport {
    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicInteger entries = new AtomicInteger();
    private final List<Object> evaluatedStates = new CopyOnWriteArrayList<>();
    private volatile int failures = Integer.MAX_VALUE;
    private volatile double probability = 0.9;
    private volatile boolean malformedResult;
    private volatile Exception evaluationFailure;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.getRegistry().bind("evaluator", new SemanticAdapter() {
                    @Override
                    public void validate(SemanticQuestion question) {
                    }

                    @Override
                    public SemanticResult evaluate(SemanticQuestion question, Object state) throws Exception {
                        evaluatedStates.add(state);
                        if (evaluationFailure != null) {
                            throw evaluationFailure;
                        }
                        return malformedResult
                                ? new SemanticResult("unexpected category", null, null, null, null)
                                : new SemanticResult(null, probability, null, null, null);
                    }
                });
                SemanticLanguage language = (SemanticLanguage) context.resolveLanguage("semantic");
                language.setAdapter("evaluator");
                SemanticQuestions.get(context).replace("test", Map.of(
                        "retryable", question("Is another attempt worthwhile?", "${exchangeProperty.retryState}"),
                        "withinScope", question("Does the proposed action serve the approved task?", "${body}")));
                Predicate retryable = language.createPredicate("ref:retryable");

                errorHandler(defaultErrorHandler().maximumRedeliveries(0).logExhausted(false));
                onException(IOException.class)
                        .onExceptionOccurred(exchange -> {
                            Exception failure = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                            exchange.setProperty("retryState", Map.of(
                                    "request", exchange.getMessage().getBody(String.class),
                                    "failure", failure.getMessage(),
                                    "attempt", exchange.getMessage().getHeader(Exchange.REDELIVERY_COUNTER, Integer.class)));
                        })
                        .retryWhile(
                                exchange -> exchange.getMessage().getHeader(Exchange.REDELIVERY_COUNTER, 0, Integer.class) <= 3
                                        && retryable.matches(exchange))
                        .redeliveryDelay(0)
                        .handled(true)
                        .to("mock:escalated");

                from("direct:retry")
                        .process(exchange -> entries.incrementAndGet())
                        .process(exchange -> {
                            int attempt = attempts.incrementAndGet();
                            if (attempt <= failures) {
                                throw new IOException("Failure " + attempt);
                            }
                        })
                        .to("mock:completed");

                from("direct:action")
                        .to("direct:checkPermissions")
                        .validate().language("semantic", "ref:withinScope")
                        .to("mock:performed");
                from("direct:checkPermissions").process(exchange -> {
                    // Stand-in for the application's trusted authorization service, not a caller-supplied header.
                    if (!Boolean.TRUE.equals(exchange.getProperty("permissionGranted", Boolean.class))) {
                        throw new CamelAuthorizationException("Permission denied", exchange);
                    }
                });
            }
        };
    }

    private static SemanticQuestion question(String instructions, String state) {
        return new SemanticQuestion(
                SemanticQuestion.Type.BOOLEAN, instructions, state, null, null,
                0.8, 0.05, SemanticQuestion.UncertaintyPolicy.FAIL);
    }

    @Test
    void approvedRetryResumesAtFailedProcessorAndPreservesBody() throws Exception {
        failures = 1;
        getMockEndpoint("mock:completed").expectedBodiesReceived("operation");
        getMockEndpoint("mock:escalated").expectedMessageCount(0);

        Exchange exchange = template.request("direct:retry", e -> e.getMessage().setBody("operation"));

        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage().getBody()).isEqualTo("operation");
        assertThat(attempts).hasValue(2);
        assertThat(entries).hasValue(1);
        assertThat(evaluatedStates).containsExactly(retryState(1));
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void negativeRetryDecisionEscalatesWithoutAnotherAttempt() throws Exception {
        probability = 0.1;
        getMockEndpoint("mock:completed").expectedMessageCount(0);
        getMockEndpoint("mock:escalated").expectedBodiesReceived("operation");

        Exchange exchange = template.request("direct:retry", e -> e.getMessage().setBody("operation"));

        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class))
                .isInstanceOf(IOException.class).hasMessage("Failure 1");
        assertThat(attempts).hasValue(1);
        assertThat(evaluatedStates).containsExactly(retryState(1));
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void alwaysApprovedRetryStopsAtBudgetBeforeAnotherEvaluation() throws Exception {
        getMockEndpoint("mock:completed").expectedMessageCount(0);
        getMockEndpoint("mock:escalated").expectedBodiesReceived("operation");

        Exchange exchange = template.request("direct:retry", e -> e.getMessage().setBody("operation"));

        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class))
                .isInstanceOf(IOException.class).hasMessage("Failure 4");
        assertThat(attempts).hasValue(4);
        assertThat(entries).hasValue(1);
        assertThat(evaluatedStates).containsExactly(retryState(1), retryState(2), retryState(3));
        MockEndpoint.assertIsSatisfied(context);
    }

    @ParameterizedTest
    @ValueSource(strings = { "timeout", "malformed", "uncertain" })
    void retryEvaluationFailurePropagatesWithoutRetryOrNormalEscalation(String failure) throws Exception {
        failEvaluation(failure);
        getMockEndpoint("mock:completed").expectedMessageCount(0);
        getMockEndpoint("mock:escalated").expectedMessageCount(0);

        Exchange exchange = template.request("direct:retry", e -> e.getMessage().setBody("operation"));

        assertEvaluationFailure(exchange, failure);
        assertThat(exchange.getMessage().getBody()).isEqualTo("operation");
        assertThat(exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class)).isInstanceOf(IOException.class);
        assertThat(attempts).hasValue(1);
        assertThat(evaluatedStates).containsExactly(retryState(1));
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void approvedContextExecutesActionAndPreservesState() throws Exception {
        getMockEndpoint("mock:performed").expectedBodiesReceived(actionState());

        Exchange exchange = requestAction(true);

        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage().getBody()).isEqualTo(actionState());
        assertThat(evaluatedStates).containsExactly(actionState());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void deniedPermissionPreventsEvaluationAndAction() throws Exception {
        getMockEndpoint("mock:performed").expectedMessageCount(0);

        Exchange exchange = requestAction(false);

        assertThat(exchange.getException()).isInstanceOf(CamelAuthorizationException.class);
        assertThat(evaluatedStates).isEmpty();
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void negativeContextDecisionPreventsActionDespitePermission() throws Exception {
        probability = 0.1;
        getMockEndpoint("mock:performed").expectedMessageCount(0);

        Exchange exchange = requestAction(true);

        assertThat(exchange.getException()).isInstanceOf(ValidationException.class);
        assertThat(evaluatedStates).containsExactly(actionState());
        MockEndpoint.assertIsSatisfied(context);
    }

    @ParameterizedTest
    @ValueSource(strings = { "timeout", "malformed", "uncertain" })
    void contextEvaluationFailurePreventsActionDespitePermission(String failure) throws Exception {
        failEvaluation(failure);
        getMockEndpoint("mock:performed").expectedMessageCount(0);

        Exchange exchange = requestAction(true);

        assertEvaluationFailure(exchange, failure);
        assertThat(exchange.getMessage().getBody()).isEqualTo(actionState());
        assertThat(evaluatedStates).containsExactly(actionState());
        MockEndpoint.assertIsSatisfied(context);
    }

    private Exchange requestAction(boolean permissionGranted) {
        return template.request("direct:action", exchange -> {
            exchange.setProperty("permissionGranted", permissionGranted);
            exchange.getMessage().setBody(actionState());
        });
    }

    private static Map<String, Object> retryState(int attempt) {
        return Map.of("request", "operation", "failure", "Failure " + attempt, "attempt", attempt);
    }

    private static Map<String, String> actionState() {
        return Map.of("approvedTask", "Send the customer their order status", "proposedAction", "Email the order status");
    }

    private void failEvaluation(String failure) {
        switch (failure) {
            case "timeout" -> evaluationFailure = new TimeoutException("Evaluation timed out");
            case "malformed" -> malformedResult = true;
            case "uncertain" -> probability = 0.8;
            default -> throw new IllegalArgumentException(failure);
        }
    }

    private static void assertEvaluationFailure(Exchange exchange, String failure) {
        switch (failure) {
            case "timeout" -> assertThat(exchange.getException(TimeoutException.class)).hasMessage("Evaluation timed out");
            case "malformed" -> assertThat(exchange.getException(IllegalArgumentException.class))
                    .hasMessage("Semantic result does not support the question and its decision policy");
            case "uncertain" -> assertThat(exchange.getException(IllegalStateException.class))
                    .hasMessage("Semantic boolean decision is uncertain");
            default -> throw new IllegalArgumentException(failure);
        }
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
    }
}
