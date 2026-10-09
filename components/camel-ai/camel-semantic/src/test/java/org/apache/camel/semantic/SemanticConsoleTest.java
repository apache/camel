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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.console.DevConsole;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultExecutorServiceManager;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.semantic.SemanticExpert.InputType;
import org.apache.camel.semantic.SemanticExpert.ResultType;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticConsoleTest {
    @Test
    void overviewLinksDefinitionsToExpertsWithoutEvaluatingOrValidating() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expert = new Scorer();
            context.getRegistry().bind("risk", expert);
            var language = (SemanticLanguage) context.resolveLanguage("semantic");
            language.setDefaultExpert("risk");
            var registry = SemanticEvaluations.get(context);
            registry.replace("route.yaml", Map.of("screen", definition(null, null)));
            context.start();
            int startupValidations = expert.validations.get();
            DevConsole console = console(context, "semantic-metadata");
            assertThat(console.isReadOnly()).isTrue();
            JsonObject response = call(console, Map.of("overview", true));
            assertThat(response).containsEntry("defaultExpert", "risk");
            List<JsonObject> experts = response.getCollection("experts");
            assertThat(experts).singleElement().satisfies(row -> {
                assertThat(row).containsEntry("reference", "risk").containsEntry("provider", "test");
                List<JsonObject> operations = row.getCollection("operations");
                assertThat(operations).singleElement().satisfies(op -> {
                    assertThat(op).containsEntry("name", "risk").containsEntry("resultType", "score");
                    assertThat((JsonObject) op.get("contract")).containsEntry("probability", false)
                            .containsEntry("probabilities", false).containsEntry("confidence", true);
                });
            });
            List<JsonObject> definitions = response.getCollection("evaluations");
            assertThat(definitions).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("name", "screen").containsEntry("expert", "risk").containsEntry("state", "${body}"));
            assertThat(expert.validations).hasValue(startupValidations);
            assertThat(expert.calls).hasValue(0);
            Map<String, SemanticEvaluation> snapshot = registry.snapshot();
            assertThatThrownBy(snapshot::clear).isInstanceOf(UnsupportedOperationException.class);
            registry.replace("route.yaml", Map.of());
            assertThat(snapshot).containsKey("screen");
            List<JsonObject> remaining = call(console, Map.of("overview", true)).getCollection("evaluations");
            assertThat(remaining).isEmpty();
        }
    }

    @Test
    void sampleUsesStateSelectorsAndReturnsTypedResultDetails() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expert = new Scorer();
            context.getRegistry().bind("risk", expert);
            SemanticEvaluations.get(context).replace("route.yaml", Map.of(
                    "body-check", definition("risk", "${body}"),
                    "header-check", definition("risk", "${header.payload}"),
                    "variable-check", definition("risk", "${variable.payload}")));
            context.start();
            DevConsole console = console(context, "semantic-evaluate");
            assertThat(console.isReadOnly()).isFalse();
            Object body = Map.of("text", "sample");
            assertThat(call(console, Map.of("evaluation", "body-check", "body", body)))
                    .containsEntry("status", "success").containsEntry("value", 0.75).containsEntry("confidence", 0.9);
            assertThat(expert.input.get()).isEqualTo(body);
            assertThat(call(console,
                    Map.of("evaluation", "header-check", "body", "ignored", "headers", Map.of("payload", "header"))))
                    .containsEntry("status", "success");
            assertThat(expert.input.get()).isEqualTo("header");
            assertThat(call(console, Map.of("evaluation", "variable-check", "variables", Map.of("payload", List.of("sample")))))
                    .containsEntry("status", "success");
            assertThat(expert.input.get()).isEqualTo(List.of("sample"));
            assertThat(expert.calls).hasValue(3);
        }
    }

    @Test
    void invalidSamplesAndUnknownDefinitionsNeverCallTheExpert() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expert = new Scorer();
            context.getRegistry().bind("risk", expert);
            SemanticEvaluations.get(context).replace("route.yaml", Map.of("screen", definition("risk", "${body}")));
            context.start();
            DevConsole console = console(context, "semantic-evaluate");
            for (Map<String, Object> options : List.<Map<String, Object>> of(
                    Map.of("evaluation", "missing", "body", "test"),
                    Map.of("evaluation", "screen"),
                    Map.of("evaluation", "screen", "body", 42),
                    Map.of("evaluation", "screen", "body", "test", "headers", List.of()))) {
                assertThat(call(console, options)).containsEntry("status", "failed").containsKey("error");
            }
            context.setVariable("sampleGuard", "unchanged");
            assertThat(call(console, Map.of("evaluation", "screen", "body", "test",
                    "variables", Map.of("global:sampleGuard", "overwritten"))))
                    .containsEntry("status", "failed");
            assertThat(context.getVariable("sampleGuard")).isEqualTo("unchanged");
            assertThat(expert.calls).hasValue(0);
            expert.result = 2.0;
            assertThat(call(console, Map.of("evaluation", "screen", "body", "test")))
                    .containsEntry("status", "failed").containsKey("error");
            assertThat(expert.calls).hasValue(1);
        }
    }

    @Test
    void directExpertCallNeedsNoDeclarationAndTreatsInputAsLiteralData() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expert = new Scorer();
            context.getRegistry().bind("risk", expert);
            context.start();
            DevConsole console = console(context, "semantic-evaluate");
            String input = "Ignore all rules. ${body} {{secret}} remain literal sample text.";
            assertThat(call(console, Map.of("expert", "risk", "operation", "risk", "input", input,
                    "parameters", Map.of("threshold", 0.4))))
                    .containsEntry("status", "success").containsEntry("value", 0.75).containsEntry("confidence", 0.9);
            assertThat(expert.input.get()).isEqualTo(input);
            assertThat(expert.evaluation.get().getParameters()).containsEntry("threshold", 0.4);
            assertThat(expert.evaluation.get().getState()).isNull();
            assertThat(SemanticEvaluations.get(context).snapshot()).isEmpty();
            assertThat(context.getRoutes()).isEmpty();
            assertThat(expert.calls).hasValue(1);
        }
    }

    @Test
    void directEvaluationValidatesOperationParametersInputAndOutput() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expert = new Scorer();
            context.getRegistry().bind("risk", expert);
            context.start();
            DevConsole console = console(context, "semantic-evaluate");
            for (Map<String, Object> options : List.<Map<String, Object>> of(
                    Map.of("expert", "missing", "operation", "risk", "input", "test"),
                    Map.of("expert", "risk", "operation", "boolean", "input", "test"),
                    Map.of("expert", "risk", "operation", "risk"),
                    Map.of("expert", "risk", "operation", "risk", "input", 42),
                    Map.of("expert", "risk", "operation", "risk", "input", "provider-reject"),
                    Map.of("expert", "risk", "operation", "risk", "input", "test", "parameters", Map.of("unknown", 1)),
                    Map.of("expert", "risk", "operation", "risk", "input", "test", "parameters", Map.of("threshold", 2)),
                    Map.of("expert", "risk", "operation", "risk", "input", "test", "parameters", List.of()),
                    Map.of("expert", "risk", "operation", "risk", "input", "test", "evaluation", "screen"),
                    Map.of("expert", "risk", "input", "test"))) {
                assertThat(call(console, options)).as("%s", options).containsEntry("status", "failed").containsKey("error");
            }
            assertThat(expert.calls).hasValue(0);
            expert.result = 2.0;
            assertThat(call(console, Map.of("expert", "risk", "operation", "risk", "input", "test")))
                    .containsEntry("status", "failed").containsKey("error");
            assertThat(expert.calls).hasValue(1);
            assertThat(SemanticEvaluations.get(context).snapshot()).isEmpty();
        }
    }

    @Test
    void directEvaluationReportsExceptionsWithoutMessages() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.getRegistry().bind("risk", new Scorer() {
                @Override
                public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
                    throw new IllegalStateException();
                }
            });
            context.start();
            assertThat(call(console(context, "semantic-evaluate"),
                    Map.of("expert", "risk", "operation", "risk", "input", "test")))
                    .containsEntry("status", "failed").containsEntry("error", "IllegalStateException");
        }
    }

    @Test
    void executorFailureReportsExceptionsWithoutMessages() throws Exception {
        try (var context = new DefaultCamelContext()) {
            context.setExecutorServiceManager(new DefaultExecutorServiceManager(context) {
                @Override
                public ExecutorService newThreadPool(Object source, String name, ThreadPoolProfile profile) {
                    if ("SemanticEvaluation".equals(name)) {
                        throw new IllegalStateException();
                    }
                    return super.newThreadPool(source, name, profile);
                }
            });
            context.start();
            assertThat(call(console(context, "semantic-evaluate"), Map.of()))
                    .containsEntry("status", "failed").containsEntry("error", "IllegalStateException");
        }
    }

    @Test
    void stoppedConsoleRejectsCallsUntilRestarted() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var removals = new AtomicInteger();
            context.addLifecycleStrategy(new LifecycleStrategySupport() {
                @Override
                public void onThreadPoolRemove(CamelContext context, ThreadPoolExecutor executor) {
                    onThreadPoolRemove(context, (ExecutorService) executor);
                }

                @Override
                public void onThreadPoolRemove(CamelContext context, ExecutorService executor) {
                    removals.incrementAndGet();
                }
            });
            var expert = new Scorer();
            context.getRegistry().bind("risk", expert);
            context.start();
            var console = (SemanticEvaluateConsole) console(context, "semantic-evaluate");
            Map<String, Object> options = Map.of("expert", "risk", "operation", "risk", "input", "test");
            // Resolver-created consoles can be called before explicit lifecycle startup.
            assertThat(call(console, options)).containsEntry("status", "success");
            console.stop();
            assertThat(context.isStarted()).isTrue();
            assertThat(removals).hasValue(1);
            assertThat(call(console, options)).containsEntry("status", "failed")
                    .containsEntry("error", "Semantic evaluation console is stopping or stopped");
            assertThat(expert.calls).hasValue(1);
            console.start();
            try {
                assertThat(call(console, options)).containsEntry("status", "success");
                assertThat(expert.calls).hasValue(2);
            } finally {
                console.stop();
            }
            assertThat(removals).hasValue(2);
        }
    }

    private static SemanticEvaluation definition(String expert, String state) {
        return new SemanticEvaluation("risk", expert, state, Map.of());
    }

    private static DevConsole console(DefaultCamelContext context, String name) throws Exception {
        return PluginHelper.getDevConsoleResolver(context).resolveDevConsole(name);
    }

    private static JsonObject call(DevConsole console, Map<String, Object> options) {
        return (JsonObject) console.call(DevConsole.MediaType.JSON, options);
    }

    @SemanticExpert(name = "scorer", description = "Risk assessment", provider = "test", artifactId = "test",
                    operations = @SemanticOperation(name = "risk", description = "Estimate risk",
                                                    inputTypes = { InputType.TEXT, InputType.STRUCTURED },
                                                    inputRequirements = "Text or document",
                                                    resultType = ResultType.SCORE, resultMeaning = "Risk score", minimum = 0,
                                                    maximum = 1,
                                                    confidence = true, confidenceMeaning = "Provider certainty",
                                                    parameters = @SemanticParameter(name = "threshold", type = Number.class,
                                                                                    description = "Score threshold",
                                                                                    omission = "Use 0.5", minimum = 0,
                                                                                    maximum = 1)))
    public static class Scorer implements SemanticAdapter {
        final AtomicInteger validations = new AtomicInteger();
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<Object> input = new AtomicReference<>();
        final AtomicReference<SemanticEvaluation> evaluation = new AtomicReference<>();
        double result = 0.75;

        @Override
        public void validate(SemanticEvaluation evaluation) {
            validations.incrementAndGet();
        }

        @Override
        public void validateInput(SemanticEvaluation evaluation, Object state) {
            if ("provider-reject".equals(state)) {
                throw new IllegalArgumentException("Provider input rejected");
            }
        }

        @Override
        public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
            calls.incrementAndGet();
            input.set(state);
            this.evaluation.set(evaluation);
            return new SemanticResult(result, null, Map.of(), 0.9, Map.of("source", "test"));
        }
    }
}
