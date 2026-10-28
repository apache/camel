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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticLanguageTest {
    DefaultCamelContext context;
    SemanticLanguage language;

    @BeforeEach
    void setup() throws Exception {
        CountingAdapter.constructed.set(0);
        CountingAdapter.started.set(0);
        CountingAdapter.stopped.set(0);
        context = new DefaultCamelContext();
        language = new SemanticLanguage();
        language.setCamelContext(context);
        context.getRegistry().bind("classifier", new CountingAdapter());
        context.getRegistry().bind("semantic", language);
        context.init();
        evaluations(evaluation("boolean", null, 0.5, 0, "fail"));
    }

    @AfterEach
    void cleanup() throws Exception {
        context.stop();
    }

    static SemanticEvaluation evaluation(
            String type, String state, double threshold, double uncertainty,
            String policy) {
        return new SemanticEvaluation(type, "classifier", state, switch (type) {
            case "boolean" -> Map.of("instructions", "Classify this message", "threshold", threshold,
                    "uncertainty", uncertainty, "uncertaintyPolicy", policy);
            case "choice" -> Map.of("instructions", "Classify this message", "criteria",
                    Map.of("billing", "Payment", "technical", "Problem"));
            case "score" -> Map.of("instructions", "Classify this message", "criteria", List.of("low", "medium", "high"));
            default -> throw new IllegalArgumentException("Unknown fixture operation");
        });
    }

    private void evaluations(SemanticEvaluation evaluation) {
        SemanticEvaluations.get(context).replace("test", Map.of("q", evaluation));
    }

    @Test
    void warmEvaluationDoesNotAcquireTheLanguageMonitor() throws Exception {
        context.start();
        Expression expression = language.createExpression("ref:q");
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("content");
        assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
        var executor = Executors.newSingleThreadExecutor();
        try {
            synchronized (language) {
                assertThat(executor.submit(() -> expression.evaluate(exchange, Boolean.class)).get(5, TimeUnit.SECONDS))
                        .isTrue();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void explicitBeanRetainsIdentityAndLifecycleOwner() throws Exception {
        CountingAdapter bean = new CountingAdapter();
        context.getRegistry().bind("classifier", bean);
        language.createExpression("ref:q");
        assertThat(context.getRegistry().lookupByName("classifier")).isSameAs(bean);
        assertThat(CountingAdapter.started).hasValue(0);
        context.stop();
        assertThat(CountingAdapter.stopped).hasValue(0);
    }

    @Test
    void evaluationStateOverridesDefaultAndMissingStateNeverFallsBack() {
        language.setDefaultState("${header.fallback}");
        evaluations(
                evaluation("boolean", "${header.selected}", 0.5, 0, "fail"));
        Expression expression = language.createExpression("ref:q");
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("body");
        exchange.getMessage().setHeader("fallback", "fallback");
        exchange.getMessage().setHeader("selected", "${body}");
        expression.evaluate(exchange, Boolean.class);
        CountingAdapter adapter
                = context.getRegistry().lookupByNameAndType("classifier", CountingAdapter.class);
        assertThat(adapter.state).isEqualTo("${body}");
        exchange.getMessage().removeHeader("selected");
        assertThatThrownBy(() -> expression.evaluate(exchange, Object.class)).hasMessageContaining("Missing selected state");
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
        evaluations(evaluation("boolean", null, 0.5, 0, "fail"));
        expression.evaluate(exchange, Boolean.class);
        assertThat(adapter.state).isEqualTo("fallback");
    }

    @Test
    void invalidSelectorsAndUnknownReferencesFailBeforeEvaluation() {
        assertThatThrownBy(() -> language.createExpression("q")).hasMessageContaining("ref:name");
        assertThatThrownBy(() -> language.createExpression("ref:unknown")).hasMessageContaining("Unknown");
        evaluations(
                evaluation("boolean", "${invalidFunction}", 0.5, 0, "fail"));
        assertThatThrownBy(() -> language.createExpression("ref:q")).hasMessageContaining("Unknown function: invalidFunction");
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void placeholderResolvingToEmptyStateRetainsSingleEvaluationBehavior(boolean evaluationState) {
        Properties properties = new Properties();
        properties.setProperty("selected", "");
        context.getPropertiesComponent().setInitialProperties(properties);
        if (evaluationState) {
            evaluations(evaluation("boolean", "{{selected}}", 0.5, 0,
                    "fail"));
        } else {
            language.setDefaultState("{{selected}}");
        }
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("original");
        assertThat(language.createExpression("ref:q").evaluate(exchange, Boolean.class)).isTrue();
        CountingAdapter adapter
                = context.getRegistry().lookupByNameAndType("classifier", CountingAdapter.class);
        assertThat(adapter.state).isEqualTo("");
        assertThat(exchange.getMessage().getBody()).isEqualTo("original");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " " })
    void literalBlankSelectorsStillFailBeforeEvaluation(String selector) {
        language.setDefaultState(selector);
        assertThatThrownBy(() -> language.createExpression("ref:q"))
                .isExactlyInstanceOf(IllegalArgumentException.class).hasMessageContaining("must not be blank");
    }

    @Test
    void byteStateRequiresExplicitConversionAndPreservesBody() {
        Expression expression = language.createExpression("ref:q");
        var exchange = new DefaultExchange(context);
        byte[] body = "invoice".getBytes(StandardCharsets.UTF_8);
        exchange.getMessage().setBody(body);
        assertThatThrownBy(() -> expression.evaluate(exchange, Object.class))
                .hasCauseInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported state type");
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
        evaluations(evaluation("boolean", "${bodyAs(String)}", 0.5, 0,
                "fail"));
        assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
        CountingAdapter adapter
                = context.getRegistry().lookupByNameAndType("classifier", CountingAdapter.class);
        assertThat(adapter.state).isEqualTo("invoice");
        assertThat(exchange.getMessage().getBody()).isSameAs(body);
    }

    @Test
    void probabilityPolicyAndFailuresNeverBecomeImplicitNonMatches() {
        Predicate predicate = language.createPredicate("ref:q");
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("content");
        CountingAdapter adapter
                = context.getRegistry().lookupByNameAndType("classifier", CountingAdapter.class);
        adapter.answer = new SemanticResult(null, 0.5, null, null, null);
        assertThat(predicate.matches(exchange)).isTrue();
        evaluations(evaluation("boolean", null, 0.5, 0.1, "fail"));
        assertThatThrownBy(() -> predicate.matches(exchange)).hasMessageContaining("uncertain");
        evaluations(evaluation("boolean", null, 0.5, 0.1, "non-match"));
        assertThat(predicate.matches(exchange)).isFalse();
        adapter.failure = new IllegalStateException("provider failed");
        assertThatThrownBy(() -> predicate.matches(exchange)).isExactlyInstanceOf(RuntimeCamelException.class)
                .hasCause(adapter.failure).hasMessageContaining("provider failed");
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
    }

    @Test
    void initializationChecksReplacementsBeforeRegisteringItsValidator() {
        AtomicInteger validations = new AtomicInteger();
        context.getRegistry().unbind("classifier");
        context.getRegistry().bind("classifier", new CountingAdapter() {
            @Override
            public void validate(SemanticEvaluation evaluation) {
                if (validations.incrementAndGet() == 1) {
                    evaluations(evaluation("choice", null, 0.5, 0, "fail"));
                }
            }
        });
        assertThatThrownBy(() -> language.createPredicate("ref:q"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("boolean");
        // A failed initialization must not register a predicate constraint.
        evaluations(evaluation("choice", null, 0.5, 0, "fail"));
        assertThat(language.createExpression("ref:q")).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "${header.broken", "{{missing.selector}}" })
    void reloadRejectsInvalidStateSelectorsBeforePublication(String selector) throws Exception {
        context.start();
        Expression expression = language.createExpression("ref:q");
        SemanticEvaluation previous = SemanticEvaluations.get(context).get("q");
        assertThatThrownBy(() -> evaluations(
                evaluation("boolean", selector, 0.5, 0, "fail")))
                .isInstanceOf(RuntimeException.class);
        assertThat(SemanticEvaluations.get(context).get("q")).isSameAs(previous);
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("content");
        assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
    }

    @Test
    void reloadReplacesDefinitionsAndRemovedReferencesFail() {
        Expression expression = language.createExpression("ref:q");
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("content");
        evaluations(
                evaluation("boolean", "${header.updated}", 0.5, 0, "fail"));
        exchange.getMessage().setHeader("updated", "new state");
        expression.evaluate(exchange, Boolean.class);
        CountingAdapter adapter
                = context.getRegistry().lookupByNameAndType("classifier", CountingAdapter.class);
        assertThat(adapter.state).isEqualTo("new state");
        assertThatThrownBy(
                () -> SemanticEvaluations.get(context).replace("other", Map.of("q", SemanticEvaluations.get(context).get("q"))))
                .hasMessageContaining("Duplicate");
        SemanticEvaluations.get(context).replace("test", Map.of());
        assertThatThrownBy(() -> expression.evaluate(exchange, Object.class)).hasMessageContaining("Unknown");
    }

    @Test
    void predicateReloadRejectsIncompatibleTypesBeforePublication() {
        Predicate predicate = language.createPredicate("ref:q");
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("content");
        assertThat(predicate.matches(exchange)).isTrue();
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNotNull();
        // Registering an expression for the same name must not replace the predicate's validation.
        language.createExpression("ref:q");
        SemanticEvaluation previous = SemanticEvaluations.get(context).get("q");
        assertThatThrownBy(() -> evaluations(
                evaluation("choice", null, 0.5, 0, "fail")))
                .isExactlyInstanceOf(IllegalArgumentException.class).hasMessageContaining("boolean");
        assertThat(SemanticEvaluations.get(context).get("q")).isSameAs(previous);
        assertThat(predicate.matches(exchange)).isTrue();
        exchange.setProperty(SemanticLanguage.RESULT, "old");
        SemanticEvaluations.get(context).replace("test", Map.of());
        assertThatThrownBy(() -> predicate.matches(exchange))
                .isExactlyInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown");
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
    }

    @Test
    void labelOnlyProviderWorksWithoutInventedProbabilitiesAndRejectsUnsupportedKinds() {
        context.getRegistry().unbind("classifier");
        context.getRegistry().bind("classifier", new LabelAdapter());
        evaluations(evaluation("choice", null, 0.5, 0, "fail"));
        Expression expression = language.createExpression("ref:q");
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("invoice");
        assertThat(expression.evaluate(exchange, String.class)).isEqualTo("billing");
        SemanticResult result = exchange.getProperty(SemanticLanguage.RESULT, SemanticResult.class);
        assertThat(result.getProbability()).isNull();
        assertThat(result.getProbabilities()).isEmpty();
        assertThat(result.getConfidence()).isNull();
        assertThatThrownBy(() -> language.createPredicate("ref:q")).hasMessageContaining("boolean");
        assertThatThrownBy(() -> evaluations(evaluation("score", null, 0.5, 0,
                "fail"))).hasMessageContaining("only choice");
        assertThat(expression.evaluate(exchange, String.class)).isEqualTo("billing");
    }

    public static class CountingAdapter extends TestSemanticAdapter {
        static final AtomicInteger constructed = new AtomicInteger();
        static final AtomicInteger started = new AtomicInteger();
        static final AtomicInteger stopped = new AtomicInteger();
        volatile Object state;
        volatile SemanticResult answer = new SemanticResult(null, 0.9, null, null, Map.of("provider", "test"));
        volatile RuntimeException failure;

        public CountingAdapter() {
            constructed.incrementAndGet();
        }

        @Override
        public void validate(SemanticEvaluation evaluation) {
        }

        @Override
        public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
            this.state = state;
            if (failure != null) {
                throw failure;
            }
            return applyPolicy(evaluation, answer);
        }

        @Override
        protected void doStart() {
            started.incrementAndGet();
        }

        @Override
        protected void doStop() {
            stopped.incrementAndGet();
        }
    }

    public static class LabelAdapter extends TestSemanticAdapter {
        @Override
        public void validate(SemanticEvaluation evaluation) {
            if (!"choice".equals(evaluation.getOperation())) {
                throw new IllegalArgumentException("Supports only choice");
            }
        }

        @Override
        public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
            return new SemanticResult("billing", null, null, null, Map.of("provider", "fixed-classifier"));
        }
    }
}
