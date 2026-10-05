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
import org.apache.camel.semantic.SemanticExpert.Instructions;
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

import static org.apache.camel.semantic.SemanticQuestionsBuilder.semanticQuestions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticExpertTest {
    private DefaultCamelContext context;
    private SemanticLanguage language;
    private FixedExpert security;
    private FixedExpert other;
    private DefaultExchange exchange;

    @BeforeEach
    void setup() throws Exception {
        context = new DefaultCamelContext();
        security = new FixedExpert();
        other = new FixedExpert();
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
        var registered = new FixedExpert() {
        };
        context.getRegistry().bind("security", registered);
        if (aliases) {
            context.getRegistry().bind("z-security", registered);
            context.getRegistry().bind("a-security", registered);
        }
        String name = aliases ? "a-security" : "security";
        SemanticQuestions.get(context).replace("test", Map.of("first",
                question(null, "Unsupported", Map.of(), SemanticQuestion.Type.BOOLEAN, 0.5)));
        assertThatThrownBy(() -> language.createExpression("ref:first"))
                .hasMessageContaining("evaluation 'first', expert '" + name + "'")
                .hasMessageContaining("Instructions are unsupported");
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
        for (String name : List.of("missing", FixedExpert.class.getName(), "#security")) {
            define(name, "security", 0.5);
            assertThatThrownBy(() -> language.createExpression("ref:first"))
                    .hasMessageContaining("first").hasMessageContaining(name);
        }
        assertThat(security.calls).isZero();
        assertThat(other.calls).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void legacyAdaptersReportUnknownRatherThanUniversalCapabilities(boolean automatic) {
        SemanticAdapter legacy = new SemanticAdapter() {
            @Override
            public void validate(SemanticQuestion question) {
            }

            @Override
            public SemanticResult evaluate(SemanticQuestion question, Object state) {
                return new SemanticResult(true, null, null, null, null);
            }
        };
        assertThat(legacy.capabilities().isKnown()).isFalse();
        assertThat(legacy.capabilities().getResultTypes()).isEmpty();
        context.getRegistry().bind("legacy", legacy);
        if (automatic) {
            context.getRegistry().unbind("security");
            context.getRegistry().unbind("other");
        }
        String reference = automatic ? null : "legacy";
        define(reference, "security", 0.5);
        assertThatThrownBy(() -> language.createExpression("ref:first"))
                .hasMessageContaining("first").hasMessageContaining("expert 'legacy'")
                .hasMessageContaining("instructions are required");
        SemanticQuestions.get(context).replace("test", Map.of("first",
                question(reference, "Is this valid?", Map.of(), SemanticQuestion.Type.BOOLEAN, 0.5)));
        assertThat(language.createExpression("ref:first").evaluate(exchange, Boolean.class)).isTrue();
    }

    @Test
    void fixedExpertRejectsInstructionsCriteriaAndChoiceBeforeInference() {
        List<SemanticQuestion> invalid = List.of(
                question("security", "Is it valid?", Map.of(), SemanticQuestion.Type.BOOLEAN, 0.5),
                question("security", null, Map.of("true", "valid"), SemanticQuestion.Type.BOOLEAN, 0.5),
                question("security", null, Map.of("billing", "invoices"), SemanticQuestion.Type.CHOICE, 0.5));
        for (SemanticQuestion question : invalid) {
            SemanticQuestions.get(context).replace("test", Map.of("first", question));
            assertThatThrownBy(() -> language.createExpression("ref:first"))
                    .hasMessageContaining("first").hasMessageContaining("security");
        }
        assertThat(security.calls).isZero();
    }

    @Test
    void mixedBatchGroupsByIdentityAndPublishesResultsInReferenceOrder() {
        context.getRegistry().bind("alias", security);
        define("security", "other", 0.5);
        SemanticQuestions.get(context).replace("alias", Map.of("third", question("alias", null, Map.of(),
                SemanticQuestion.Type.BOOLEAN, 0.5)));
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
        SemanticQuestions.get(context).replace("test", Map.of("first", new SemanticQuestion(
                SemanticQuestion.Type.BOOLEAN, null, null, null, null, 0.9, 0.05,
                SemanticQuestion.UncertaintyPolicy.FAIL, "security")));
        assertThatThrownBy(() -> expression.evaluate(exchange, Boolean.class)).hasMessageContaining("uncertain");
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
        SemanticQuestions.get(context).replace("test", Map.of("first", new SemanticQuestion(
                SemanticQuestion.Type.BOOLEAN, null, null, null, null, 0.9, 0.05,
                SemanticQuestion.UncertaintyPolicy.NON_MATCH, "security")));
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

        var replacement = new FixedExpert();
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
        var previous = SemanticQuestions.get(context).get("first");
        assertThatThrownBy(() -> define("missing", "other", 0.5)).hasMessageContaining("missing");
        assertThat(SemanticQuestions.get(context).get("first")).isSameAs(previous);
        define("other", "security", 0.5);
        assertThat(expression.evaluate(exchange, Boolean.class)).isFalse();
        assertThat(security.calls).isZero();
        assertThat(other.calls).isOne();
    }

    @Test
    void reloadValidatesAllInitializedReferencesAndLeavesUnusedDraftsAlone() {
        Map<String, SemanticQuestion> definitions = new LinkedHashMap<>();
        for (String name : List.of("first", "second", "third")) {
            definitions.put(name, question("security", null, Map.of(), SemanticQuestion.Type.BOOLEAN, 0.5));
        }
        definitions.put("draft", question(null, null, Map.of(), SemanticQuestion.Type.BOOLEAN, 0.5));
        SemanticQuestions questions = SemanticQuestions.get(context);
        questions.replace("test", definitions);
        var single = language.createExpression("ref:first");
        var batch = language.createExpression("refs:second,third");
        questions.replace("test", definitions);
        assertThatThrownBy(() -> language.createExpression("ref:draft"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exactly one eligible expert");
        // A failed expression must not make an unused draft mandatory on the next reload.
        questions.replace("test", definitions);
        for (String name : List.of("first", "second", "third")) {
            Map<String, SemanticQuestion> invalid = new LinkedHashMap<>(definitions);
            invalid.put(name, question("missing", null, Map.of(), SemanticQuestion.Type.BOOLEAN, 0.5));
            assertThatThrownBy(() -> questions.replace("test", invalid))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(name).hasMessageContaining("missing");
            assertThat(questions.get(name)).isSameAs(definitions.get(name));
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
                    semanticQuestions(this).question("injection").type("boolean").expert("{{selected.expert}}")
                            .threshold("{{injection.threshold}}").register();
                    from("direct:expert").setBody().language("semantic", "ref:injection");
                }
            });
        } else {
            PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("expert.xml",
                    """
                            <routes xmlns="http://camel.apache.org/schema/xml-io">
                              <semantic><question name="injection" type="boolean" expert="{{selected.expert}}"
                                                  threshold="{{injection.threshold}}"/></semantic>
                              <route><from uri="direct:expert"/><setBody><language language="semantic">ref:injection</language></setBody></route>
                            </routes>
                            """));
        }
        assertThat(SemanticQuestions.get(context).get("injection").getExpert()).isEqualTo("{{selected.expert}}");
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
                            semanticQuestions(this).question("invalid").type("boolean").expert("security")
                                    .threshold(threshold).register();
                        }
                    });
                } else {
                    PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("invalid.xml",
                            """
                                    <semantic xmlns="http://camel.apache.org/schema/semantic">
                                      <question name="invalid" type="boolean" expert="security" threshold="%s"/>
                                    </semantic>
                                    """.formatted(threshold)));
                }
            }).hasStackTraceContaining("semantic question 'invalid'").hasStackTraceContaining("expert 'security'");
            assertThat(security.calls).isZero();
        }
    }

    @Test
    void invalidXmlAttributesNameTheirExpert() {
        assertThatThrownBy(() -> PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("invalid.xml",
                """
                        <semantic xmlns="http://camel.apache.org/schema/semantic">
                          <question name="invalid" type="boolean" expert="security" thresholdd="0.9"/>
                        </semantic>
                        """)))
                .hasStackTraceContaining("semantic question 'invalid'").hasStackTraceContaining("expert 'security'")
                .hasStackTraceContaining("thresholdd");
    }

    @Test
    void validationDoesNotInitializeOrStartOwnedProviderResources() {
        ManagedExpert.initialized = 0;
        ManagedExpert.started = 0;
        language.setAdapter(ManagedExpert.class.getName());
        SemanticQuestions.get(context).replace("test", Map.of("first",
                question(null, "Unsupported", Map.of(), SemanticQuestion.Type.BOOLEAN, 0.5)));
        assertThatThrownBy(() -> language.createExpression("ref:first")).hasMessageContaining("Instructions are unsupported");
        assertThat(ManagedExpert.initialized).isZero();
        assertThat(ManagedExpert.started).isZero();
        define(null, null, 0.5);
        language.createExpression("ref:first");
        assertThat(ManagedExpert.initialized).isOne();
        assertThat(ManagedExpert.started).isOne();
    }

    @SemanticExpert(name = "managed", description = "Fixed detection", provider = "test", artifactId = "test",
                    inputTypes = InputType.TEXT, resultTypes = ResultType.BOOLEAN, instructions = Instructions.UNSUPPORTED,
                    callerDefinedCriteria = false)
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
        public void validate(SemanticQuestion question) {
        }

        @Override
        public SemanticResult evaluate(SemanticQuestion question, Object state) {
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

    public static class DirectService extends FixedExpert implements Service {
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
        define("label", "security", 0.8);
        assertThatThrownBy(() -> language.createExpression("ref:first"))
                .hasMessageContaining("first").hasMessageContaining("label").hasMessageContaining("probability support");
    }

    @Test
    void instructionDrivenAndFixedExpertsShareOneBatch() {
        context.getRegistry().bind("general", new SemanticLanguageTest.LabelAdapter());
        SemanticQuestions.get(context).replace("test", Map.of(
                "injection", question("security", null, Map.of(), SemanticQuestion.Type.BOOLEAN, 0.5),
                "department", question("general", "Which department?", Map.of("billing", "invoices"),
                        SemanticQuestion.Type.CHOICE, 0.5)));
        var result = language.createExpression("refs:injection,department").evaluate(exchange, Map.class);
        assertThat(result).containsEntry("injection", true).containsEntry("department", "billing");
        assertThat(security.batches).containsExactly(List.of("injection"));
    }

    private void define(String first, String second, double threshold) {
        SemanticQuestions.get(context).replace("test", Map.of(
                "first", question(first, null, Map.of(), SemanticQuestion.Type.BOOLEAN, threshold),
                "second", question(second, null, Map.of(), SemanticQuestion.Type.BOOLEAN, threshold)));
    }

    private static SemanticQuestion question(
            String expert, String instructions, Map<String, String> criteria,
            SemanticQuestion.Type type, double threshold) {
        return new SemanticQuestion(
                type, instructions, null, criteria, List.of(), threshold, 0,
                SemanticQuestion.UncertaintyPolicy.FAIL, expert);
    }

    @SemanticExpert(name = "fixed", description = "Prompt-injection detection", provider = "test", artifactId = "test",
                    inputTypes = InputType.TEXT, resultTypes = ResultType.BOOLEAN, instructions = Instructions.UNSUPPORTED,
                    callerDefinedCriteria = false, booleanProbability = true, trueMeaning = "Injection detected",
                    probabilityMeaning = "Probability of INJECTION, even when BENIGN wins")
    public static class FixedExpert implements SemanticAdapter {
        double probability = 0.9;
        boolean fail;
        int calls;
        final List<List<String>> batches = new ArrayList<>();
        final List<Object> states = new ArrayList<>();

        @Override
        public void validate(SemanticQuestion question) {
            capabilities().validate(question);
        }

        @Override
        public SemanticResult evaluate(SemanticQuestion question, Object state) {
            calls++;
            states.add(state);
            if (fail) {
                throw new IllegalStateException("provider unavailable");
            }
            return new SemanticResult(null, probability, null, null, Map.of("provider", "fixed"));
        }

        @Override
        public Map<String, SemanticResult> evaluateBatch(Map<String, SemanticQuestion> questions, Object state)
                throws Exception {
            batches.add(List.copyOf(questions.keySet()));
            return SemanticAdapter.super.evaluateBatch(questions, state);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof FixedExpert;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }
}
