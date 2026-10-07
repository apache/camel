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

import org.apache.camel.Service;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.semantic.SemanticExpert.InputType;
import org.apache.camel.semantic.SemanticExpert.ResultType;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.apache.camel.semantic.SemanticEvaluationsBuilder.semanticEvaluations;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticExpertTest {
    private DefaultCamelContext context;
    private SemanticLanguage language;
    private FixedSemanticExpert security;
    private FixedSemanticExpert other;
    private DefaultExchange exchange;

    @BeforeEach
    void setup() throws Exception {
        context = new DefaultCamelContext();
        security = new FixedSemanticExpert();
        other = new FixedSemanticExpert();
        other.probability = 0.1;
        context.getRegistry().bind("security", security);
        context.getRegistry().bind("other", other);
        language = (SemanticLanguage) context.resolveLanguage("semantic");
        context.start();
        exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("untrusted text, passed unchanged");
    }

    @AfterEach
    void cleanup() throws Exception {
        context.close();
    }

    @Test
    void explicitDefaultAndSoleExpertSelection() {
        define("security", "other", 0.5);
        language.setDefaultExpert("missing-default");
        var expression = language.createExpression("ref:first");
        assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
        assertThatThrownBy(() -> define(null, null, 0.5)).hasMessageContaining("missing-default");
        language.setDefaultExpert("other");
        define(null, null, 0.5);
        assertThat(language.createExpression("ref:first").evaluate(exchange, Boolean.class)).isFalse();
        language.setDefaultExpert(null);
        assertThatThrownBy(() -> language.createExpression("ref:first"))
                .hasMessageContaining("first").hasMessageContaining("security").hasMessageContaining("other")
                .hasMessageContaining("Specify expert");
        context.getRegistry().unbind("other");
        context.getRegistry().bind("alias", security);
        assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void automaticExpertErrorsRetainAStableRegistryName(boolean aliases) {
        context.getRegistry().unbind("other");
        var registered = new FixedSemanticExpert() {
        };
        context.getRegistry().bind("security", registered);
        if (aliases) {
            context.getRegistry().bind("z-security", registered);
            context.getRegistry().bind("a-security", registered);
        }
        String name = aliases ? "a-security" : "security";
        assertThatThrownBy(() -> SemanticEvaluations.get(context).replace("test", Map.of("first",
                evaluation(null, "Unsupported", Map.of(), "boolean", 0.5))))
                .hasMessageContaining("evaluation 'first', expert '" + name + "'")
                .hasMessageContaining("Unknown parameter 'instructions'");
        define(null, null, 0.5);
        var expression = language.createExpression("ref:first");
        exchange.getMessage().setBody(Map.of("text", "input"));
        assertThatThrownBy(() -> expression.evaluate(exchange, Boolean.class))
                .hasMessageContaining("evaluations [first], expert '" + name + "'")
                .hasMessageContaining("accepts [TEXT]");
        assertThat(registered.calls).isZero();
    }

    @Test
    void unknownExplicitExpertNeverFallsBackToDefaultOrClassLoading() {
        language.setDefaultExpert("security");
        for (String name : List.of("missing", FixedSemanticExpert.class.getName(), "#security")) {
            assertThatThrownBy(() -> define(name, "security", 0.5))
                    .hasMessageContaining("first").hasMessageContaining(name);
        }
        assertThat(security.calls).isZero();
        assertThat(other.calls).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void adaptersMustDeclareTheirStaticContract(boolean automatic) {
        SemanticAdapter legacy = new SemanticAdapter() {
            @Override
            public void validate(SemanticEvaluation evaluation) {
            }

            @Override
            public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
                return new SemanticResult(true, null, null, null, null);
            }
        };
        assertThatThrownBy(() -> SemanticCapabilities.from(legacy.getClass())).hasMessageContaining("@SemanticExpert");
        context.getRegistry().bind("legacy", legacy);
        if (automatic) {
            context.getRegistry().unbind("security");
            context.getRegistry().unbind("other");
        }
        String reference = automatic ? null : "legacy";
        assertThatThrownBy(() -> SemanticEvaluations.get(context).replace("test",
                Map.of("first", evaluation(reference, null, Map.of(), "boolean", 0.5))))
                .hasMessageContaining("first").hasMessageContaining("expert 'legacy'")
                .hasMessageContaining("@SemanticExpert");
    }

    @Test
    void fixedExpertRejectsInstructionsCriteriaAndChoiceBeforeInference() {
        List<SemanticEvaluation> invalid = List.of(
                evaluation("security", "Is it valid?", Map.of(), "boolean", 0.5),
                evaluation("security", null, Map.of("true", "valid"), "boolean", 0.5),
                evaluation("security", null, Map.of("billing", "invoices"), "choice", 0.5));
        for (SemanticEvaluation evaluation : invalid) {
            assertThatThrownBy(() -> SemanticEvaluations.get(context).replace("test", Map.of("first", evaluation)))
                    .hasMessageContaining("first").hasMessageContaining("security");
        }
        assertThat(security.calls).isZero();
    }

    @Test
    void mixedBatchGroupsByIdentityAndPublishesResultsInReferenceOrder() {
        context.getRegistry().bind("alias", security);
        define("security", "other", 0.5);
        SemanticEvaluations.get(context).replace("alias", Map.of("third", evaluation("alias", null, Map.of(),
                "boolean", 0.5)));
        Map<?, ?> decisions = language.createExpression("refs:first,second,third").evaluate(exchange, Map.class);
        assertThat(decisions.keySet().toArray()).containsExactly("first", "second", "third");
        assertThat(decisions.get("first")).isEqualTo(true);
        assertThat(decisions.get("second")).isEqualTo(false);
        assertThat(decisions.get("third")).isEqualTo(true);
        assertThat(security.batches).containsExactly(List.of("first", "third"));
        assertThat(other.batches).containsExactly(List.of("second"));
        assertThat(security.states).containsOnly(exchange.getMessage().getBody());
        assertThat(exchange.getProperty(SemanticLanguage.RESULTS, Map.class)).hasSize(3);
    }

    @Test
    void anyGroupFailureClearsDiagnosticsAndDoesNotPublishPartialResults() {
        define("security", "other", 0.5);
        var expression = language.createExpression("refs:first,second");
        assertThat(expression.evaluate(exchange, Map.class)).hasSize(2);
        other.fail = true;
        exchange.setProperty(SemanticLanguage.RESULT, "stale");
        assertThatThrownBy(() -> expression.evaluate(exchange, Map.class)).hasMessageContaining("provider unavailable");
        assertThat(exchange.getProperty(SemanticLanguage.RESULTS)).isNull();
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
        other.fail = false;
        other.probability = Double.NaN;
        assertThatThrownBy(() -> expression.evaluate(exchange, Map.class)).isInstanceOf(RuntimeException.class);
        assertThat(exchange.getProperty(SemanticLanguage.RESULTS)).isNull();
    }

    @Test
    void fixedExpertUsesPositiveProbabilityWithThresholdAndUncertaintyPolicies() {
        define("security", "other", 0.9);
        var expression = language.createExpression("ref:first");
        assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
        security.probability = 0.899;
        assertThat(expression.evaluate(exchange, Boolean.class)).isFalse();
        SemanticEvaluations.get(context).replace("test", Map.of("first", new SemanticEvaluation(
                "boolean", "security", null, Map.of("threshold", 0.9, "uncertainty", 0.05, "uncertaintyPolicy", "fail"))));
        assertThatThrownBy(() -> expression.evaluate(exchange, Boolean.class)).hasMessageContaining("uncertain");
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
        SemanticEvaluations.get(context).replace("test", Map.of("first", new SemanticEvaluation(
                "boolean", "security", null, Map.of("threshold", 0.9, "uncertainty", 0.05, "uncertaintyPolicy", "non-match"))));
        assertThat(expression.evaluate(exchange, Boolean.class)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void rebindingNamedExpertRequiresNewExpressionOrDeclarationReplacement(boolean useDefault) {
        if (useDefault) {
            language.setDefaultExpert("security");
        }
        String expert = useDefault ? null : "security";
        define(expert, "other", 0.5);
        var expression = language.createExpression("ref:first");
        assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();

        var replacement = new FixedSemanticExpert();
        replacement.probability = 0.1;
        context.getRegistry().unbind("security");
        context.getRegistry().bind("security", replacement);
        assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
        assertThat(language.createExpression("ref:first").evaluate(exchange, Boolean.class)).isFalse();

        define(expert, "other", 0.5);
        assertThat(expression.evaluate(exchange, Boolean.class)).isFalse();
    }

    @Test
    void changedDeclarationsAreValidatedBeforePublicationAndReselectExpert() {
        define("security", "other", 0.5);
        var expression = language.createExpression("ref:first");
        var previous = SemanticEvaluations.get(context).get("first");
        assertThatThrownBy(() -> define("missing", "other", 0.5)).hasMessageContaining("missing");
        assertThat(SemanticEvaluations.get(context).get("first")).isSameAs(previous);
        define("other", "security", 0.5);
        assertThat(expression.evaluate(exchange, Boolean.class)).isFalse();
        assertThat(security.calls).isZero();
        assertThat(other.calls).isOne();
    }

    @Test
    void reloadValidatesBothInitializedReferencesAndUnusedDeclarations() {
        Map<String, SemanticEvaluation> definitions = new LinkedHashMap<>();
        for (String name : List.of("first", "second", "third")) {
            definitions.put(name, evaluation("security", null, Map.of(), "boolean", 0.5));
        }
        definitions.put("draft", evaluation("security", null, Map.of(), "boolean", 0.5));
        SemanticEvaluations evaluations = SemanticEvaluations.get(context);
        evaluations.replace("test", definitions);
        var single = language.createExpression("ref:first");
        var batch = language.createExpression("refs:second,third");
        evaluations.replace("test", definitions);
        for (String name : List.of("first", "second", "third", "draft")) {
            Map<String, SemanticEvaluation> invalid = new LinkedHashMap<>(definitions);
            invalid.put(name, evaluation("missing", null, Map.of(), "boolean", 0.5));
            assertThatThrownBy(() -> evaluations.replace("test", invalid))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(name).hasMessageContaining("missing");
            assertThat(evaluations.get(name)).isSameAs(definitions.get(name));
        }
        assertThat(single.evaluate(exchange, Boolean.class)).isTrue();
        assertThat(batch.evaluate(exchange, Map.class)).containsEntry("second", true).containsEntry("third", true);
    }

    @Test
    void allInputShapesAreCheckedBeforeAnyBatchInference() {
        define("security", "other", 0.5);
        var expression = language.createExpression("refs:first,second");
        exchange.getMessage().setBody(Map.of("text", "input"));
        assertThatThrownBy(() -> expression.evaluate(exchange, Map.class))
                .hasMessageContaining("evaluations [first]").hasMessageContaining("expert 'security'")
                .hasMessageContaining("accepts [TEXT]");
        assertThatThrownBy(() -> language.createExpression("ref:second").evaluate(exchange, Boolean.class))
                .hasMessageContaining("evaluations [second]").hasMessageContaining("expert 'other'");
        assertThat(security.calls).isZero();
        assertThat(other.calls).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = { "java", "xml" })
    void declarationsSupportExpertsWithoutInstructionsAndPreservePlaceholders(String dsl) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("selected.expert", "security");
        properties.setProperty("injection.threshold", "0.95");
        context.getPropertiesComponent().setInitialProperties(properties);
        if (dsl.equals("java")) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    semanticEvaluations(this).evaluation("injection").type("boolean").expert("{{selected.expert}}")
                            .threshold("{{injection.threshold}}").register();
                    from("direct:expert").setBody().language("semantic", "ref:injection");
                }
            });
        } else {
            PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("expert.xml",
                    """
                            <routes xmlns="http://camel.apache.org/schema/xml-io">
                              <semantic><evaluation name="injection" type="boolean" expert="{{selected.expert}}"
                                                  threshold="{{injection.threshold}}"/></semantic>
                              <route><from uri="direct:expert"/><setBody><language language="semantic">ref:injection</language></setBody></route>
                            </routes>
                            """));
        }
        assertThat(SemanticEvaluations.get(context).get("injection").getExpert()).isEqualTo("{{selected.expert}}");
        assertThat(security.calls).isZero();
        try (var template = context.createProducerTemplate()) {
            assertThat(template.requestBody("direct:expert", "text", Boolean.class)).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "java", "xml" })
    void invalidDeclarationsNameTheirExpert(String dsl) {
        for (String threshold : List.of("-0.1", "not-a-number")) {
            assertThatThrownBy(() -> {
                if (dsl.equals("java")) {
                    context.addRoutes(new RouteBuilder() {
                        @Override
                        public void configure() {
                            semanticEvaluations(this).evaluation("invalid").type("boolean").expert("security")
                                    .threshold(threshold).register();
                        }
                    });
                } else {
                    PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("invalid.xml",
                            """
                                    <semantic xmlns="http://camel.apache.org/schema/semantic">
                                      <evaluation name="invalid" type="boolean" expert="security" threshold="%s"/>
                                    </semantic>
                                    """.formatted(threshold)));
                }
            }).hasStackTraceContaining("'invalid'").hasStackTraceContaining("expert 'security'");
            assertThat(security.calls).isZero();
        }
    }

    @Test
    void invalidXmlAttributesNameTheirExpert() {
        assertThatThrownBy(() -> PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("invalid.xml",
                """
                        <semantic xmlns="http://camel.apache.org/schema/semantic">
                          <evaluation name="invalid" type="boolean" expert="security" thresholdd="0.9"/>
                        </semantic>
                        """)))
                .hasStackTraceContaining("'invalid'").hasStackTraceContaining("expert 'security'")
                .hasStackTraceContaining("thresholdd");
    }

    @Test
    void validationDoesNotInitializeOrStartOwnedProviderResources() {
        ManagedExpert.initialized = 0;
        ManagedExpert.started = 0;
        language.setAdapter(ManagedExpert.class.getName());
        assertThatThrownBy(() -> SemanticEvaluations.get(context).replace("test", Map.of("first",
                evaluation(null, "Unsupported", Map.of(), "boolean", 0.5))))
                .hasMessageContaining("Unknown parameter 'instructions'");
        assertThat(ManagedExpert.initialized).isZero();
        assertThat(ManagedExpert.started).isZero();
        define(null, null, 0.5);
        language.createExpression("ref:first");
        assertThat(ManagedExpert.initialized).isOne();
        assertThat(ManagedExpert.started).isOne();
    }

    @SemanticExpert(name = "fixed", description = "Fixed detection", provider = "test", artifactId = "test",
                    operations = @SemanticOperation(name = "boolean", description = "Detect injection",
                                                    inputTypes = InputType.TEXT, inputRequirements = "A text message",
                                                    resultType = ResultType.BOOLEAN,
                                                    resultMeaning = "True means injection detected"))
    public static class ManagedExpert extends ServiceSupport implements SemanticAdapter {
        static int initialized;
        static int started;

        @Override
        protected void doInit() {
            initialized++;
        }

        @Override
        protected void doStart() {
            started++;
        }

        @Override
        public void validate(SemanticEvaluation evaluation) {
        }

        @Override
        public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) {
            return new SemanticResult(true, null, null, null, null);
        }
    }

    @Test
    void directServiceImplementationIsInitializedBeforeStartup() {
        DirectService.events.clear();
        language.setAdapter(DirectService.class.getName());
        define(null, null, 0.5);
        language.createExpression("ref:first");
        language.createExpression("ref:second");
        assertThat(DirectService.events).containsExactly("init", "start");
    }

    public static class DirectService extends FixedSemanticExpert implements Service {
        static final List<String> events = new ArrayList<>();

        @Override
        public void init() {
            events.add("init");
        }

        @Override
        public void start() {
            events.add("start");
        }

        @Override
        public void stop() {
            events.add("stop");
        }
    }

    @Test
    void booleanOnlyExpertRejectsProbabilityPoliciesBeforeStarting() {
        context.getRegistry().bind("label", new ManagedExpert());
        assertThatThrownBy(() -> define("label", "security", 0.8))
                .hasMessageContaining("first").hasMessageContaining("label")
                .hasMessageContaining("Unknown parameter 'threshold'");
    }

    @Test
    void instructionDrivenAndFixedSemanticExpertsShareOneBatch() {
        context.getRegistry().bind("general", new SemanticLanguageTest.LabelAdapter());
        SemanticEvaluations.get(context).replace("test", Map.of(
                "injection", evaluation("security", null, Map.of(), "boolean", 0.5),
                "department", evaluation("general", "Which department?", Map.of("billing", "invoices"),
                        "choice", 0.5)));
        var result = language.createExpression("refs:injection,department").evaluate(exchange, Map.class);
        assertThat(result).containsEntry("injection", true).containsEntry("department", "billing");
        assertThat(security.batches).containsExactly(List.of("injection"));
    }

    private void define(String first, String second, double threshold) {
        SemanticEvaluations.get(context).replace("test", Map.of(
                "first", evaluation(first, null, Map.of(), "boolean", threshold),
                "second", evaluation(second, null, Map.of(), "boolean", threshold)));
    }

    private static SemanticEvaluation evaluation(
            String expert, String instructions, Map<String, String> criteria,
            String type, double threshold) {
        if ("boolean".equals(type) && instructions == null && criteria.isEmpty() && threshold == 0.5) {
            return new SemanticEvaluation("boolean", expert, null, Map.of());
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (instructions != null) {
            parameters.put("instructions", instructions);
        }
        if (!criteria.isEmpty()) {
            parameters.put("criteria", criteria);
        }
        if ("boolean".equals(type)) {
            parameters.put("threshold", threshold);
            parameters.put("uncertainty", 0.0);
            parameters.put("uncertaintyPolicy", "fail");
        }
        return new SemanticEvaluation(type, expert, null, parameters);
    }

}
