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

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dsl.yaml.common.YamlDeserializationContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.semantic.SemanticExpert.InputType;
import org.apache.camel.semantic.SemanticExpert.ResultType;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;

import static org.apache.camel.semantic.SemanticEvaluationsBuilder.semanticEvaluations;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticContractTest {
    @ParameterizedTest
    @ValueSource(strings = { "java", "yaml", "xml" })
    void equivalentDeclarationsPreserveNestedParametersAndStructuredState(String dsl) throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expert = new ContentExpert();
            context.getRegistry().bind("content", expert);
            declarations(context, dsl);
            context.start();
            var evaluation = SemanticEvaluations.get(context).get("categories");
            assertThat(evaluation.getExpert()).isEqualTo("content");
            assertThat(evaluation.getState()).isEqualTo("${body}");
            assertThat(evaluation.getParameters()).containsOnlyKeys("policy", "limit");
            assertThat(evaluation.getParameters().get("limit")).isInstanceOf(Number.class);
            assertThat(evaluation.getParameters().get("policy")).isInstanceOfSatisfying(Map.class, policy -> {
                assertThat(policy.get("allowed-tags")).isEqualTo(List.of("privacy", "unsafe"));
                assertThat(policy.get("enabled")).isEqualTo(true);
                assertThat(policy.get("cutoff")).isInstanceOf(Number.class);
            });
            assertThat(new BigDecimal(((Map<?, ?>) evaluation.getParameters().get("policy")).get("cutoff").toString()))
                    .isEqualByComparingTo("0.7");
            var exchange = new DefaultExchange(context);
            Map<String, Object> state = Map.of("prompt", "request", "response", "answer", "documents", List.of("evidence"));
            exchange.getMessage().setBody(state);
            var expression = context.resolveLanguage("simple").createExpression("${semantic('categories')}");
            assertThat(expression.evaluate(exchange, Object.class)).isEqualTo(Set.of("privacy"));
            assertThat(expert.state).isSameAs(state);
            assertThat(exchange.getMessage().getBody()).isSameAs(state);
            assertThat(expert.calls).isOne();
            assertThat(expression.evaluate(exchange, Object.class)).isEqualTo(Set.of("privacy"));
            assertThat(expert.calls).isEqualTo(2);
            var detail = exchange.getProperty(SemanticLanguage.RESULT, SemanticResult.class);
            assertThat(detail.getConfidence()).isNull();
            assertThat(detail.getProbabilities()).containsEntry("privacy", 0.8);
            assertThat(context.resolveLanguage("simple").createPredicate("${semantic('categories')} contains 'privacy'")
                    .matches(exchange)).isTrue();
            assertThat(expert.calls).isEqualTo(3);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "${semantic()}", "${semantic(name)}", "${semantic('')}", "${semantic(' ')}",
            "${semantic('first','second')}" })
    void simpleFunctionRejectsInvalidArguments(String expression) throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.start();
            assertThatThrownBy(() -> context.resolveLanguage("simple").createExpression(expression))
                    .hasMessageContaining("Semantic requires one quoted evaluation name");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "safety", "classify", "rank" })
    void simplePredicatesRejectNonBooleanBeforeInference(String operation) throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expert = new ContentExpert();
            context.getRegistry().bind("content", expert);
            SemanticEvaluations.get(context).replace("test",
                    Map.of("q", new SemanticEvaluation(operation, "content", null, Map.of())));
            context.start();
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody(Map.of("response", "answer"));
            for (String syntax : List.of("${semantic('q')}", "${!semantic('q')}", "!${semantic('q')}")) {
                assertThatThrownBy(() -> context.resolveLanguage("simple").createPredicate(syntax).matches(exchange))
                        .as(syntax).hasMessageContaining("requires a boolean");
            }
            assertThat(expert.calls).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void cachedNestedSelectorsValidateTheCompleteReplacement(boolean transitive) throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("content", new ContentExpert());
            var registry = SemanticEvaluations.get(context);
            Map<String, SemanticEvaluation> declarations = new LinkedHashMap<>();
            declarations.put("outer", new SemanticEvaluation("detect", "content", "${semantic('inner')} ready", Map.of()));
            declarations.put("inner", new SemanticEvaluation(
                    "detect", "content",
                    transitive ? "${semantic('leaf')} ready" : null, Map.of()));
            if (transitive) {
                declarations.put("leaf", new SemanticEvaluation("detect", "content", null, Map.of()));
            }
            registry.replace("test", declarations);
            context.start();
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("content");
            var expression = context.resolveLanguage("semantic").createExpression("ref:outer");
            assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
            Map<String, SemanticEvaluation> replacement = new LinkedHashMap<>(declarations);
            String removed = transitive ? "leaf" : "inner";
            replacement.remove(removed);
            assertThatThrownBy(() -> registry.replace("test", replacement))
                    .hasMessageContaining("Unknown semantic evaluation: " + removed);
            assertThat(registry.get(removed)).isSameAs(declarations.get(removed));
            assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
        }
    }

    @Test
    void candidateDeclarationsRemainPrivateUntilValidationCompletes() throws Exception {
        var callers = Executors.newSingleThreadExecutor();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var context = new DefaultCamelContext()) {
            var original = new SemanticEvaluation("detect", "content", null, Map.of());
            var candidate = new SemanticEvaluation("detect", "content", "${header.candidate}", Map.of());
            var registry = SemanticEvaluations.get(context);
            context.getRegistry().bind("content", new ContentExpert() {
                @Override
                public void validate(SemanticEvaluation evaluation) {
                    if (evaluation == candidate) {
                        assertThat(registry.get("q")).as("callback reads published evaluation").isSameAs(original);
                        assertThat(registry.get(List.of("q"))).containsEntry("q", original);
                        entered.countDown();
                        try {
                            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(interrupted);
                        }
                    }
                }
            });
            registry.replace("test", Map.of("q", original));
            context.start();
            var replacement = callers.submit(() -> registry.replace("test", Map.of("q", candidate)));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(registry.get("q")).isSameAs(original);
            } finally {
                release.countDown();
            }
            replacement.get(10, TimeUnit.SECONDS);
            assertThat(registry.get("q")).isSameAs(candidate);
        } finally {
            release.countDown();
            callers.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void expertCallbacksDoNotInheritValidationScope(boolean fail) throws Exception {
        try (var context = new DefaultCamelContext()) {
            var registry = SemanticEvaluations.get(context);
            var original = new SemanticEvaluation("detect", "content", null, Map.of());
            var candidate = new SemanticEvaluation("detect", "content", "${semantic('added')} ready", Map.of());
            var nested = new SemanticEvaluation("detect", "content", null, Map.of("threshold", 0.9));
            var expert = new ContentExpert() {
                boolean checked;

                @Override
                public void validate(SemanticEvaluation evaluation) {
                    if (evaluation == candidate) {
                        checked = true;
                        var exchange = new DefaultExchange(context);
                        exchange.getMessage().setBody("published state");
                        var language = context.resolveLanguage("semantic");
                        assertThat(language.createExpression("ref:nested").evaluate(exchange, Boolean.class)).isTrue();
                        // Reentrant validation must restore the outer replacement's candidate snapshot.
                        registry.validate();
                        if (fail) {
                            throw new IllegalArgumentException("Reject replacement");
                        }
                    }
                }
            };
            context.getRegistry().bind("content", expert);
            registry.replace("test", Map.of("q", original, "nested", original));
            context.start();
            var current = context.resolveLanguage("semantic").createExpression("ref:q");
            Map<String, SemanticEvaluation> replacement = Map.of("q", candidate, "nested", nested, "added", original);
            if (fail) {
                assertThatThrownBy(() -> registry.replace("test", replacement)).hasMessageContaining("Reject replacement");
                assertThat(registry.get("q")).isSameAs(original);
                assertThatThrownBy(() -> registry.get("added")).hasMessageContaining("Unknown semantic evaluation");
            } else {
                registry.replace("test", replacement);
                assertThat(registry.get("q")).isSameAs(candidate);
            }
            assertThat(expert.checked).isTrue();
            // A later successful replacement and runtime compilation must not retain either validation scope.
            registry.replace("test", Map.of("q", original, "nested", nested));
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("published state");
            assertThat(context.resolveLanguage("semantic").createExpression("ref:nested")
                    .evaluate(exchange, Boolean.class)).isFalse();
            assertThat(current.evaluate(exchange, Boolean.class)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void latePredicatesValidatePendingReplacement(boolean compatible) throws Exception {
        try (var context = new DefaultCamelContext()) {
            var registry = SemanticEvaluations.get(context);
            var original = new SemanticEvaluation("detect", "content", null, Map.of());
            var candidate = new SemanticEvaluation("detect", "content", "${header.candidate}", Map.of());
            var expert = new ContentExpert() {
                int candidateValidations;
                boolean attempted;
                Predicate latePredicate;

                @Override
                public void validate(SemanticEvaluation evaluation) {
                    // First validate the declaration; then enter the already-copied expression validators.
                    if (evaluation == candidate && ++candidateValidations == 2) {
                        attempted = true;
                        assertThat(registry.get("target")).isSameAs(original);
                        latePredicate = context.resolveLanguage("semantic").createPredicate("ref:target");
                    }
                }
            };
            context.getRegistry().bind("content", expert);
            registry.replace("test", Map.of("outer", original, "target", original));
            context.start();
            var language = context.resolveLanguage("semantic");
            var outer = language.createExpression("ref:outer");
            var target = compatible
                    ? new SemanticEvaluation("detect", "content", null, Map.of("threshold", 0.9))
                    : new SemanticEvaluation("rank", "content", null, Map.of());
            Map<String, SemanticEvaluation> replacement = Map.of("outer", candidate, "target", target);
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("published state");
            exchange.getMessage().setHeader("candidate", "replacement state");
            if (compatible) {
                registry.replace("test", replacement);
                assertThat(registry.get("target")).isSameAs(target);
                assertThat(expert.latePredicate.matches(exchange)).isFalse();
            } else {
                assertThatThrownBy(() -> registry.replace("test", replacement))
                        .hasMessageContaining("Semantic predicate requires a boolean evaluation: target");
                assertThat(registry.get("outer")).isSameAs(original);
                assertThat(registry.get("target")).isSameAs(original);
                assertThat(expert.latePredicate).isNull();
            }
            assertThat(expert.attempted).isTrue();
            assertThat(outer.evaluate(exchange, Boolean.class)).isTrue();
            // Both success and failure must clear the pending replacement before later registrations.
            registry.replace("test", Map.of("outer", original, "target", original));
            assertThat(language.createPredicate("ref:target").matches(exchange)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "${semantic('inner')} ready",
            "${semantic('inner')} ? 'yes' : 'no'" })
    void nestedSimpleFunctionsValidateCandidateReferences(String selector) throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("content", new ContentExpert());
            var registry = SemanticEvaluations.get(context);
            var inner = new SemanticEvaluation("detect", "content", null, Map.of());
            registry.replace("test", Map.of("inner", inner));
            context.start();
            var outer = new SemanticEvaluation("detect", "content", selector, Map.of());
            registry.replace("test", Map.of("inner", inner, "outer", outer));
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("content");
            var expression = context.resolveLanguage("semantic").createExpression("ref:outer");
            assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
            assertThatThrownBy(() -> registry.replace("test", Map.of("outer", outer)))
                    .hasMessageContaining("Unknown semantic evaluation: inner");
            assertThat(registry.get("inner")).isSameAs(inner);
            assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "${not(${semantic('inner')})} ready | false ready | true ready",
            "${iif(${semantic('inner')},yes,no)} | yes | no" })
    void lazyNestedSimpleFunctionsReadPublishedReplacements(String selector, String before, String after) throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expert = new ContentExpert();
            context.getRegistry().bind("content", expert);
            var registry = SemanticEvaluations.get(context);
            var inner = new SemanticEvaluation("detect", "content", null, Map.of());
            var outer = new SemanticEvaluation("detect", "content", selector, Map.of());
            registry.replace("test", Map.of("inner", inner, "outer", outer));
            context.start();
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("content");
            var expression = context.resolveLanguage("semantic").createExpression("ref:outer");
            assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
            assertThat(expert.state).isEqualTo(before);
            registry.replace("test", Map.of("inner",
                    new SemanticEvaluation("detect", "content", null, Map.of("threshold", 0.9)), "outer", outer));
            assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
            assertThat(expert.state).isEqualTo(after);
            // Simple compiles these composite functions lazily, when they are evaluated.
            registry.replace("test", Map.of("outer", outer));
            assertThatThrownBy(() -> expression.evaluate(exchange, Boolean.class))
                    .hasMessageContaining("Unknown semantic evaluation: inner");
        }
    }

    @Test
    void invalidProgrammaticReplacementRetainsUnusedDeclarations() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("content", new ContentExpert());
            var registry = SemanticEvaluations.get(context);
            var previous = new SemanticEvaluation("detect", "content", null, Map.of());
            registry.replace("test", Map.of("q", previous));
            context.start();
            assertThatThrownBy(() -> registry.replace("test", Map.of("q",
                    new SemanticEvaluation("detect", "content", null, Map.of("threshold", "wrong")))))
                    .hasMessageContaining("q").hasMessageContaining("content").hasMessageContaining("threshold");
            assertThat(registry.get("q")).isSameAs(previous);
            assertThat(previous.getParameters().get("threshold")).isNull();
            assertThat(previous.getParameters().get("uncertainty")).isNull();
            assertThat(previous.getParameters().get("uncertaintyPolicy")).isNull();
        }
    }

    @Test
    void classificationMembershipAndScalarSwitchWorkInRoutes() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("content", new ContentExpert());
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    semanticEvaluations(this).expert("content")
                            .evaluation("categories").operation("classify").end()
                            .evaluation("safety").operation("safety").register();
                    from("direct:check").choice()
                            .when(simple("${semantic('categories')} contains 'privacy'"))
                            .setHeader("review", constant(true)).end()
                            .doSwitch(simple("${semantic('safety')}"))
                            .doCase("safe", "direct:deliver").otherwise("direct:review");
                    from("direct:deliver").setHeader("verdict", constant("safe"));
                    from("direct:review").setHeader("verdict", constant("unsafe"));
                }
            });
            context.start();
            try (var template = context.createProducerTemplate()) {
                Map<String, Object> body = Map.of("prompt", "request", "response", "answer");
                var exchange = template.request("direct:check", e -> e.getMessage().setBody(body));
                assertThat(exchange.getException()).isNull();
                assertThat(exchange.getMessage().getBody()).isSameAs(body);
                assertThat(exchange.getMessage().getHeader("review")).isEqualTo(true);
                assertThat(exchange.getMessage().getHeader("verdict")).isEqualTo("safe");
            }
        }
    }

    @Test
    void providerPolicyIsAppliedOnceAndOmissionIsPreserved() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expert = new ContentExpert();
            context.getRegistry().bind("content", expert);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    semanticEvaluations(this).expert("content")
                            .evaluation("injection").operation("detect").parameter("threshold", 0.9).end()
                            .evaluation("defaultPolicy").operation("detect").register();
                }
            });
            context.start();
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("content");
            assertThat(context.resolveLanguage("simple").createPredicate("${semantic('injection')}").matches(exchange))
                    .isFalse();
            assertThat(exchange.getProperty(SemanticLanguage.RESULT, SemanticResult.class).getProbability()).isEqualTo(0.8);
            assertThat(expert.parameters).containsEntry("threshold", 0.9);
            assertThat(context.resolveLanguage("simple").createPredicate("${!semantic('injection')}").matches(exchange))
                    .isTrue();
            assertThat(context.resolveLanguage("simple").createPredicate("!${semantic('injection')}").matches(exchange))
                    .isTrue();
            expert.verdict = true;
            assertThat(context.resolveLanguage("simple").createPredicate("${semantic('injection')}").matches(exchange))
                    .isTrue();
            expert.verdict = false;
            assertThat(context.resolveLanguage("semantic").createPredicate("ref:defaultPolicy").matches(exchange)).isFalse();
            expert.verdict = null;
            assertThat(context.resolveLanguage("semantic").createPredicate("ref:defaultPolicy").matches(exchange)).isTrue();
            assertThat(expert.parameters).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "failure", "malformed", "missingInput" })
    void failuresNeverBecomeDecisionsAndClearBothResultProperties(String failure) throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expert = new ContentExpert();
            context.getRegistry().bind("content", expert);
            SemanticEvaluations.get(context).replace("test",
                    Map.of("categories", new SemanticEvaluation("classify", "content", null, Map.of())));
            context.start();
            var expression = context.resolveLanguage("simple").createExpression("${semantic('categories')}");
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody(Map.of("response", "answer"));
            expression.evaluate(exchange, Object.class);
            exchange.setProperty(SemanticLanguage.RESULTS, "old");
            expert.failure = failure.equals("failure");
            expert.malformed = failure.equals("malformed");
            if (failure.equals("missingInput")) {
                exchange.getMessage().setBody(Map.of("prompt", "private-content"));
            }
            assertThatThrownBy(() -> expression.evaluate(exchange, Object.class)).hasMessageNotContaining("private-content");
            assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
            assertThat(exchange.getProperty(SemanticLanguage.RESULTS)).isNull();
            assertThat(expert.calls).isEqualTo(failure.equals("missingInput") ? 1 : 2);
        }
    }

    @Test
    void instanceCannotReplaceItsStaticContract() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("content", new ContentExpert() {
                public SemanticCapabilities capabilities() {
                    throw new AssertionError("Runtime must read the class contract directly");
                }
            });
            SemanticEvaluations.get(context).replace("test",
                    Map.of("detect", new SemanticEvaluation("detect", "content", null, Map.of())));
            context.start();
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("content");
            assertThat(context.resolveLanguage("semantic").createPredicate("ref:detect").matches(exchange)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void nestedSelectorsDoNotActivateOwnedProvidersDuringValidation(boolean started) throws Exception {
        try (var context = new DefaultCamelContext()) {
            SemanticExpertTest.ManagedExpert.initialized = 0;
            SemanticExpertTest.ManagedExpert.started = 0;
            var language = (SemanticLanguage) context.resolveLanguage("semantic");
            language.setAdapter(SemanticExpertTest.ManagedExpert.class.getName());
            context.getRegistry().bind("content", new ContentExpert());
            if (started) {
                context.start();
            }
            SemanticEvaluations.get(context).replace("test", Map.of(
                    "inner", new SemanticEvaluation("boolean", null, null, Map.of()),
                    "outer", new SemanticEvaluation("detect", "content", "${semantic('inner')} ready", Map.of())));
            context.start();
            assertThat(SemanticExpertTest.ManagedExpert.initialized).isZero();
            assertThat(SemanticExpertTest.ManagedExpert.started).isZero();
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody("content");
            assertThat(language.createExpression("ref:outer").evaluate(exchange, Boolean.class)).isTrue();
            assertThat(SemanticExpertTest.ManagedExpert.initialized).isOne();
            assertThat(SemanticExpertTest.ManagedExpert.started).isOne();
        }
    }

    @Test
    void unusedInvalidDeclarationFailsAtStartupWithoutInference() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expert = new ContentExpert();
            context.getRegistry().bind("content", expert);
            SemanticEvaluations.get(context).replace("test", Map.of("bad", new SemanticEvaluation(
                    "detect", "content", null,
                    Map.of("threshold", "private-value"))));
            assertThatThrownBy(context::start).hasMessageContaining("bad").hasMessageContaining("content")
                    .hasMessageContaining("threshold").hasMessageNotContaining("private-value");
            assertThat(expert.calls).isZero();
        }
    }

    @Test
    void nestedStateEvaluationCannotLeaveResultsWhenOuterEvaluationFails() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("content", new ContentExpert());
            SemanticEvaluations.get(context).replace("test", Map.of(
                    "inner", new SemanticEvaluation("safety", "content", null, Map.of()),
                    "outer", new SemanticEvaluation("classify", "content", "${semantic('inner')}", Map.of())));
            context.start();
            var exchange = new DefaultExchange(context);
            exchange.getMessage().setBody(Map.of("response", "answer"));
            assertThatThrownBy(() -> context.resolveLanguage("semantic").createExpression("ref:outer")
                    .evaluate(exchange, Object.class)).hasMessageContaining("outer");
            assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
            assertThat(exchange.getProperty(SemanticLanguage.RESULTS)).isNull();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "java", "yaml", "xml" })
    void numericParametersPreservePrecisionAndRejectFractionalIntegers(String dsl) throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("content", new ContentExpert());
            numericDeclaration(context, dsl, "9007199254740993");
            context.start();
            Object value = SemanticEvaluations.get(context).get("numeric").getParameters().get("limit");
            assertThat(new BigDecimal(value.toString())).isEqualByComparingTo("9007199254740993");
        }
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("content", new ContentExpert());
            numericDeclaration(context, dsl, "2.0000000000000000001");
            assertThatThrownBy(context::start).hasMessageContaining("limit").hasMessageContaining("integer");
        }
    }

    private static void numericDeclaration(DefaultCamelContext context, String dsl, String value) throws Exception {
        if (dsl.equals("java")) {
            SemanticEvaluations.get(context).replace("test", Map.of("numeric",
                    new SemanticEvaluation("classify", "content", null, Map.of("limit", new BigDecimal(value)))));
        } else if (dsl.equals("yaml")) {
            yamlDeclaration(context, """
                    - semantic:
                        expert: content
                        evaluation:
                          numeric:
                            operation: classify
                            parameters:
                              limit: %s
                    """.formatted(value));
        } else {
            PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("numeric.xml", """
                    <semantic expert="content">
                      <evaluation name="numeric" operation="classify">
                        <parameters><parameter name="limit"><number>%s</number></parameter></parameters>
                      </evaluation>
                    </semantic>
                    """.formatted(value)));
        }
    }

    private static void yamlDeclaration(DefaultCamelContext context, String yaml) throws Exception {
        var settings = LoadSettings.builder().build();
        try (var dc = new YamlDeserializationContext(settings)) {
            dc.setCamelContext(context);
            dc.setResource(ResourceHelper.fromString("contract.yaml", yaml));
            dc.start();
            dc.preParse(new Compose(settings).composeString(yaml).orElseThrow());
        }
    }

    private static void declarations(DefaultCamelContext context, String dsl) throws Exception {
        if (dsl.equals("java")) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    semanticEvaluations(this).expert("content").state("${body}")
                            .evaluation("categories").operation("classify")
                            .parameter("limit", Double.valueOf(getContext().resolvePropertyPlaceholders("{{limit:2}}")))
                            .parameter("policy",
                                    Map.of("allowed-tags", List.of("{{label:privacy}}", "unsafe"), "enabled", true, "cutoff",
                                            Double.valueOf(getContext().resolvePropertyPlaceholders("{{cutoff:0.7}}"))))
                            .register();
                }
            });
        } else if (dsl.equals("yaml")) {
            String yaml = """
                    - semantic:
                        expert: content
                        state: "${body}"
                        evaluation:
                          categories:
                            operation: classify
                            parameters:
                              limit: !number "{{limit:2}}"
                              policy:
                                allowed-tags: ["{{label:privacy}}", unsafe]
                                enabled: true
                                cutoff: !number "{{cutoff:0.7}}"
                    """;
            yamlDeclaration(context, yaml);
        } else {
            PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("contract.xml",
                    """
                            <semantic expert="content" state="${body}">
                              <evaluation name="categories" operation="classify">
                                <parameters>
                                  <parameter name="limit"><number>{{limit:2}}</number></parameter>
                                  <parameter name="policy"><map>
                                    <entry key="allowed-tags"><list><string>{{label:privacy}}</string><string>unsafe</string></list></entry>
                                    <entry key="enabled"><boolean>true</boolean></entry>
                                    <entry key="cutoff"><number>{{cutoff:0.7}}</number></entry>
                                  </map></parameter>
                                </parameters>
                              </evaluation>
                            </semantic>
                            """));
        }
    }

    @SemanticExpert(name = "content", description = "Content fixture", provider = "test", artifactId = "test", operations = {
            @SemanticOperation(name = "classify", description = "Content classification", inputTypes = InputType.STRUCTURED,
                               inputRequirements = "A map containing the assistant response",
                               resultType = ResultType.CLASSIFICATION,
                               resultMeaning = "Detected categories", labels = { "privacy", "unsafe" }, probabilities = true,
                               probabilityMeaning = "Independent label probabilities", parameters = {
                                       @SemanticParameter(name = "limit", description = "Maximum labels", type = Number.class,
                                                          integer = true, minimum = 1, omission = "All labels"),
                                       @SemanticParameter(name = "policy", description = "Provider policy", type = Map.class,
                                                          omission = "Service defaults") }),
            @SemanticOperation(name = "safety", description = "Response safety", inputTypes = InputType.STRUCTURED,
                               inputRequirements = "A map containing the assistant response", resultType = ResultType.CHOICE,
                               resultMeaning = "Response safety verdict", labels = { "safe", "unsafe" }),
            @SemanticOperation(name = "rank", description = "Response score", inputTypes = InputType.STRUCTURED,
                               inputRequirements = "A map containing the assistant response", resultType = ResultType.SCORE,
                               resultMeaning = "Response quality", minimum = 0, maximum = 1),
            @SemanticOperation(name = "detect", description = "Injection detection", inputTypes = InputType.TEXT,
                               inputRequirements = "Text", resultType = ResultType.BOOLEAN,
                               resultMeaning = "True means injection detected",
                               probability = true, probabilityMeaning = "Probability of injection", parameters = {
                                       @SemanticParameter(name = "threshold", description = "Requested threshold",
                                                          type = Number.class,
                                                          minimum = 0, maximum = 1, omission = "Use provider threshold 0.5") })
    })
    static class ContentExpert implements SemanticAdapter {
        int calls;
        Object state;
        Map<String, Object> parameters;
        Boolean verdict;
        boolean failure;
        boolean malformed;

        @Override
        public void validate(SemanticEvaluation evaluation) {
        }

        @Override
        public void validateInput(SemanticEvaluation evaluation, Object selected) {
            if (!evaluation.getOperation().equals("detect")
                    && (!(selected instanceof Map<?, ?> map) || !map.containsKey("response"))) {
                throw new IllegalArgumentException("Selected state requires a response");
            }
        }

        @Override
        public SemanticResult evaluate(SemanticEvaluation evaluation, Object selected) {
            calls++;
            state = selected;
            parameters = evaluation.getParameters();
            if (failure) {
                throw new IllegalStateException("Expert unavailable");
            }
            if (evaluation.getOperation().equals("detect")) {
                double threshold = ((Number) parameters.getOrDefault("threshold", 0.5)).doubleValue();
                return new SemanticResult(verdict != null ? verdict : 0.8 >= threshold, 0.8, null, null, null);
            }
            if (evaluation.getOperation().equals("rank")) {
                return new SemanticResult(0.7, null, null, null, null);
            }
            if (evaluation.getOperation().equals("safety")) {
                return new SemanticResult("safe", null, null, null, null);
            }
            return new SemanticResult(
                    malformed ? Set.of("unknown") : Set.of("privacy"), null, Map.of("privacy", 0.8), null, null);
        }
    }
}
