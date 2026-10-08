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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticBatchTest {
    private DefaultCamelContext context;
    private SemanticLanguage language;
    private DefaultExchange exchange;
    private RecordingAdapter adapter;

    @BeforeEach
    void setup() throws Exception {
        context = new DefaultCamelContext();
        language = new SemanticLanguage();
        language.setCamelContext(context);
        language.setAdapter("adapter");
        adapter = new RecordingAdapter();
        context.getRegistry().bind("adapter", adapter);
        context.start();
        exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("original");
        SemanticEvaluations.get(context).replace("test", Map.of(
                "urgent", evaluation("boolean", null, 0.5, 0, "fail"),
                "department", evaluation("choice", null, 0.5, 0, "fail"),
                "priority", evaluation("score", null, 0.5, 0, "fail")));
    }

    @AfterEach
    void stop() throws Exception {
        context.stop();
    }

    private static SemanticEvaluation evaluation(
            String type, String state, double threshold,
            double uncertainty, String policy) {
        return new SemanticEvaluation(type, null, state, switch (type) {
            case "boolean" -> Map.of("instructions", "Classify", "threshold", threshold,
                    "uncertainty", uncertainty, "uncertaintyPolicy", policy);
            case "choice" -> Map.of("instructions", "Classify", "criteria", Map.of("billing", "Payments", "technical", "Bugs"));
            case "score" -> Map.of("instructions", "Classify", "criteria", List.of("low", "medium", "high"));
            default -> throw new IllegalArgumentException("Unknown fixture operation");
        });
    }

    @Test
    void existingAdapterEvaluatesMixedEvaluationsSequentiallyAndRetainsDetails() {
        Expression expression = language.createExpression("refs: urgent, department, priority ");
        assertThat(adapter.calls).isEmpty();
        assertThat(expression.evaluate(exchange, Map.class))
                .containsAllEntriesOf(Map.of("urgent", true, "department", "billing", "priority", 1.2));
        Map<String, Object> decisions = expression.evaluate(exchange, Map.class);
        assertThat(new ArrayList<>(decisions.keySet())).containsExactly("urgent", "department", "priority");
        assertThat(adapter.calls).containsExactly("boolean", "choice",
                "score", "boolean", "choice",
                "score");
        assertThat(adapter.states).containsOnly("original");
        assertThat(exchange.getMessage().getBody()).isEqualTo("original");
        Map<?, ?> details = exchange.getProperty(SemanticLanguage.RESULTS, Map.class);
        SemanticResult department = (SemanticResult) details.get("department");
        assertThat(department.getProbabilities()).containsEntry("billing", 0.9);
        assertThat(department.getConfidence()).isEqualTo(0.8);
        assertThat(department.getMetadata()).containsEntry("provider", "fixture");
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
        language.createExpression("ref:urgent").evaluate(exchange, Boolean.class);
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isInstanceOf(SemanticResult.class);
        assertThat(exchange.getProperty(SemanticLanguage.RESULTS)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "refs:", "refs: ", "refs:,urgent", "refs:urgent,", "refs:urgent,,priority",
            "refs:urgent,urgent", "refs:urgent, urgent", "refs:urgent,unknown" })
    void rejectsInvalidReferencesBeforeInference(String expression) {
        assertThatThrownBy(() -> language.createExpression(expression)).isInstanceOf(IllegalArgumentException.class);
        assertThat(adapter.calls).isEmpty();
    }

    @Test
    void singleReferenceStillAcceptsNamesContainingCommas() {
        SemanticEvaluations.get(context).replace("comma", Map.of("a,b", SemanticEvaluations.get(context).get("urgent")));
        assertThat(language.createExpression("ref:a,b").evaluate(exchange, Boolean.class)).isTrue();
        assertThatThrownBy(() -> language.createExpression("refs:a,b")).hasMessageContaining("Unknown");
    }

    @Test
    void batchesCannotBePredicatesEvenWithOneBooleanEvaluation() {
        assertThatThrownBy(() -> language.validatePredicate("refs:urgent")).hasMessageContaining("cannot be predicates");
        assertThatThrownBy(() -> language.createPredicate("refs:urgent")).hasMessageContaining("cannot be predicates");
        Expression expression = language.createExpression("refs:urgent");
        expression.evaluate(exchange, Object.class);
        assertThatThrownBy(() -> ((Predicate) expression).matches(exchange)).hasMessageContaining("cannot be predicates");
        assertThat(exchange.getProperty(SemanticLanguage.RESULTS)).isNull();
        assertThat(adapter.calls).hasSize(1);
    }

    @Test
    void sharedStateSelectorIsEvaluatedOnlyOnce() {
        AtomicInteger reads = new AtomicInteger();
        exchange.setIn(new DefaultMessage(context) {
            @Override
            public Object getHeader(String name) {
                if (name.equals("selected")) {
                    reads.incrementAndGet();
                    return "selected";
                }
                return super.getHeader(name);
            }
        });
        language.setDefaultState("${header.selected}");
        language.createExpression("refs:urgent,department,priority").evaluate(exchange, Map.class);
        assertThat(reads).hasValue(1);
        assertThat(adapter.states).containsExactly("selected", "selected", "selected");
    }

    @Test
    void resolvesSharedDefaultAndExplicitSelectorsBeforeComparison() {
        Properties properties = new Properties();
        properties.setProperty("selected", "${header.selected}");
        context.getPropertiesComponent().setInitialProperties(properties);
        language.setDefaultState("{{selected}}");
        SemanticEvaluations.get(context).replace("explicit", Map.of("other",
                evaluation("boolean", "${header.selected}", 0.5, 0,
                        "fail")));
        exchange.getMessage().setHeader("selected", Map.of("text", "invoice"));
        Expression expression = language.createExpression("refs:urgent,other");
        expression.evaluate(exchange, Map.class);
        assertThat(adapter.states).containsExactly(Map.of("text", "invoice"), Map.of("text", "invoice"));
        exchange.getMessage().removeHeader("selected");
        assertThatThrownBy(() -> expression.evaluate(exchange, Map.class)).hasMessageContaining("Missing selected state");
        assertThat(adapter.calls).hasSize(2);
        assertThat(exchange.getProperty(SemanticLanguage.RESULTS)).isNull();
    }

    @Test
    void batchUsesTheSameEmptyPlaceholderStateAsSingleEvaluations() {
        Properties properties = new Properties();
        properties.setProperty("selected", "");
        context.getPropertiesComponent().setInitialProperties(properties);
        language.setDefaultState("{{selected}}");
        assertThat(language.createExpression("refs:urgent,department").evaluate(exchange, Map.class))
                .containsEntry("urgent", true).containsEntry("department", "billing");
        assertThat(adapter.states).containsExactly("", "");
    }

    @Test
    void incompatibleSelectorsAndUnsupportedCapabilitiesFailBeforeInference() {
        SemanticEvaluations.get(context).replace("other", Map.of("other",
                evaluation("boolean", "${header.other}", 0.5, 0, "fail")));
        assertThatThrownBy(() -> language.createExpression("refs:urgent,other"))
                .hasMessageContaining("same effective state selector");
        context.getRegistry().unbind("adapter");
        context.getRegistry().bind("adapter", new RecordingAdapter() {
            @Override
            public void validate(SemanticEvaluation evaluation) {
                if ("score".equals(evaluation.getOperation())) {
                    throw new IllegalArgumentException("Unsupported score");
                }
            }
        });
        // Use a fresh language because adapter selection is cached for the context lifetime.
        SemanticLanguage other = new SemanticLanguage();
        other.setCamelContext(context);
        other.setAdapter("adapter");
        assertThatThrownBy(() -> other.createExpression("refs:urgent,priority")).hasMessageContaining("Unsupported score");
        assertThat(adapter.calls).isEmpty();
    }

    @Test
    void eachEvaluationKeepsItsDecisionPolicyAndFailureClearsAllDiagnostics() {
        SemanticEvaluation negative
                = evaluation("boolean", null, 0.95, 0, "fail");
        SemanticEvaluation uncertain
                = evaluation("boolean", null, 0.9, 0.05, "non-match");
        SemanticEvaluations.get(context).replace("policy", Map.of("negative", negative, "uncertain", uncertain));
        Expression expression = language.createExpression("refs:urgent,negative,uncertain");
        assertThat(expression.evaluate(exchange, Map.class)).containsEntry("urgent", true)
                .containsEntry("negative", false).containsEntry("uncertain", false);
        SemanticEvaluations.get(context).replace("policy", Map.of("negative", negative, "uncertain",
                evaluation("boolean", null, 0.9, 0.05, "fail")));
        exchange.setProperty(SemanticLanguage.RESULT, "stale");
        assertThatThrownBy(() -> expression.evaluate(exchange, Map.class)).hasMessageContaining("uncertain");
        assertThat(exchange.getProperty(SemanticLanguage.RESULTS)).isNull();
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "missing", "extra", "null", "invalid", "failure" })
    void rejectsIncompleteOrInvalidBatchResultsWithoutPublishingPartialDiagnostics(String failure) {
        context.getRegistry().unbind("adapter");
        context.getRegistry().bind("adapter", new RecordingAdapter() {
            @Override
            public Map<String, SemanticResult> evaluateBatch(Map<String, SemanticEvaluation> evaluations, Object state) {
                if (failure.equals("failure")) {
                    throw new IllegalStateException("provider failed");
                }
                Map<String, SemanticResult> results = new LinkedHashMap<>();
                results.put("urgent", result("boolean"));
                if (!failure.equals("missing")) {
                    results.put("department", failure.equals("null") ? null
                            : failure.equals("invalid") ? new SemanticResult("undeclared", null, null, null, null)
                            : result("choice"));
                }
                if (failure.equals("extra")) {
                    results.put("extra", result("boolean"));
                }
                return results;
            }
        });
        Expression expression = language.createExpression("refs:urgent,department");
        exchange.setProperty(SemanticLanguage.RESULT, "stale");
        exchange.setProperty(SemanticLanguage.RESULTS, "stale");
        assertThatThrownBy(() -> expression.evaluate(exchange, Map.class)).isInstanceOf(RuntimeException.class);
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
        assertThat(exchange.getProperty(SemanticLanguage.RESULTS)).isNull();
    }

    @Test
    void reloadDuringValidationOrEvaluationCannotMixEvaluationDefinitions() {
        SemanticEvaluation old = SemanticEvaluations.get(context).get("urgent");
        SemanticEvaluation updated
                = evaluation("boolean", null, 0.95, 0, "fail");
        SemanticEvaluations evaluations = SemanticEvaluations.get(context);
        evaluations.replace("test", Map.of("first", old, "second", old));
        AtomicInteger validations = new AtomicInteger();
        context.getRegistry().unbind("adapter");
        context.getRegistry().bind("adapter", new RecordingAdapter() {
            @Override
            public void validate(SemanticEvaluation evaluation) {
                if (validations.incrementAndGet() == 1) {
                    evaluations.replace("test", Map.of("first", updated, "second", updated));
                }
            }

            @Override
            public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) throws Exception {
                evaluations.replace("test", Map.of("first", old, "second", old));
                return super.evaluate(evaluation, state);
            }
        });
        Expression expression = language.createExpression("refs:first,second");
        assertThat(expression.evaluate(exchange, Map.class)).containsEntry("first", false).containsEntry("second", false);
        assertThat(expression.evaluate(exchange, Map.class)).containsEntry("first", true).containsEntry("second", true);
        evaluations.replace("test", Map.of("first", old));
        assertThatThrownBy(() -> expression.evaluate(exchange, Map.class))
                .hasMessageContaining("Unknown semantic evaluation: second");
        assertThat(exchange.getProperty(SemanticLanguage.RESULTS)).isNull();
        assertThatThrownBy(() -> evaluations.replace("test", Map.of("first", old, "second",
                evaluation("boolean", "${header.changed}", 0.5, 0, "fail"))))
                .hasMessageContaining("same effective state selector");
        assertThat(evaluations.get("first")).isSameAs(old);
        assertThatThrownBy(() -> evaluations.get("second")).hasMessageContaining("Unknown semantic evaluation");
        assertThatThrownBy(() -> expression.evaluate(exchange, Map.class)).hasMessageContaining("Unknown semantic evaluation");
    }

    @Test
    void reloadChecksBatchSelectorsAcrossResourcesAndRetainsThePreviousSnapshot() {
        SemanticEvaluations evaluations = SemanticEvaluations.get(context);
        SemanticEvaluation original = evaluations.get("urgent");
        evaluations.replace("other", Map.of("other", original));
        Expression expression = language.createExpression("refs:urgent,other");
        assertThatThrownBy(() -> evaluations.replace("other", Map.of("other",
                evaluation("boolean", "${header.changed}", 0.5, 0, "fail"))))
                .hasMessageContaining("same effective state selector");
        assertThat(adapter.calls).isEmpty();
        assertThat(evaluations.get("other")).isSameAs(original);
        assertThat(expression.evaluate(exchange, Map.class)).containsEntry("urgent", true).containsEntry("other", true);
    }

    @Test
    void interruptionStopsSequentialFallbackAndPreservesInterruptFlag() {
        AtomicInteger invocations = new AtomicInteger();
        context.getRegistry().unbind("adapter");
        context.getRegistry().bind("adapter", new RecordingAdapter() {
            @Override
            public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) throws Exception {
                invocations.incrementAndGet();
                throw new InterruptedException("interrupted");
            }
        });
        Expression expression = language.createExpression("refs:urgent,department");
        try {
            assertThatThrownBy(() -> expression.evaluate(exchange, Map.class)).hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(invocations).hasValue(1);
            assertThat(exchange.getProperty(SemanticLanguage.RESULTS)).isNull();
        } finally {
            Thread.interrupted();
        }
    }

    private static SemanticResult result(String type) {
        return switch (type) {
            case "boolean" -> new SemanticResult(null, 0.9, null, null, Map.of("provider", "fixture"));
            case "choice" -> new SemanticResult(
                    "billing", null, Map.of("billing", 0.9, "technical", 0.1), 0.8, Map.of("provider", "fixture"));
            case "score" -> new SemanticResult(1.2, null, null, 0.7, Map.of("provider", "fixture"));
            default -> throw new IllegalArgumentException("Unsupported fixture operation");
        };
    }

    private static class RecordingAdapter extends TestSemanticAdapter {
        final List<String> calls = new ArrayList<>();
        final List<Object> states = new ArrayList<>();

        @Override
        public void validate(SemanticEvaluation evaluation) {
        }

        @Override
        public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) throws Exception {
            calls.add(evaluation.getOperation());
            states.add(state);
            return applyPolicy(evaluation, result(evaluation.getOperation()));
        }
    }
}
