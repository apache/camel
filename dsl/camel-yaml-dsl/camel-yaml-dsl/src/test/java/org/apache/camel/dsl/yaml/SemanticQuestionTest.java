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
package org.apache.camel.dsl.yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.common.exception.YamlDeserializationException;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.semantic.SemanticAdapter;
import org.apache.camel.semantic.SemanticExpert;
import org.apache.camel.semantic.SemanticOperation;
import org.apache.camel.semantic.SemanticParameter;
import org.apache.camel.semantic.SemanticQuestion;
import org.apache.camel.semantic.SemanticQuestions;
import org.apache.camel.semantic.SemanticResult;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.RouteWatcherReloadStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.apache.camel.semantic.SemanticQuestionsBuilder.semanticQuestions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticQuestionTest extends YamlTestSupport {
    @TempDir
    Path directory;
    private final AtomicInteger calls = new AtomicInteger();
    private Object selected;

    @Override
    public void doSetup() {
        context.getRegistry().bind("classifier", new Classifier());
        ((SemanticLanguage) context.resolveLanguage("semantic")).setAdapter("classifier");
    }

    @SemanticExpert(name = "classifier", description = "Test classifier", provider = "test", artifactId = "test", operations = {
            @SemanticOperation(name = "choice", description = "Choose", inputTypes = SemanticExpert.InputType.TEXT,
                               inputRequirements = "Text", resultType = SemanticExpert.ResultType.CHOICE,
                               resultMeaning = "Department",
                               parameters = {
                                       @SemanticParameter(name = "instructions", description = "Question", required = true,
                                                          minSize = 1),
                                       @SemanticParameter(name = "criteria", description = "Labels", type = Map.class,
                                                          itemType = String.class,
                                                          required = true, minSize = 2) }),
            @SemanticOperation(name = "boolean", description = "Decide", inputTypes = SemanticExpert.InputType.TEXT,
                               inputRequirements = "Text", resultType = SemanticExpert.ResultType.BOOLEAN,
                               resultMeaning = "Decision",
                               parameters = {
                                       @SemanticParameter(name = "instructions", description = "Question", required = true,
                                                          minSize = 1),
                                       @SemanticParameter(name = "threshold", description = "Threshold", type = Number.class,
                                                          minimum = 0,
                                                          maximum = 1, omission = "0.5"),
                                       @SemanticParameter(name = "uncertainty", description = "Uncertainty",
                                                          type = Number.class, minimum = 0,
                                                          maximum = 1, omission = "0"),
                                       @SemanticParameter(name = "uncertaintyPolicy", description = "Policy",
                                                          values = { "fail", "non-match" },
                                                          omission = "fail") }),
            @SemanticOperation(name = "score", description = "Score", inputTypes = SemanticExpert.InputType.TEXT,
                               inputRequirements = "Text", resultType = SemanticExpert.ResultType.SCORE,
                               resultMeaning = "Score",
                               parameters = {
                                       @SemanticParameter(name = "instructions", description = "Question", required = true,
                                                          minSize = 1),
                                       @SemanticParameter(name = "criteria", description = "Levels", type = List.class,
                                                          itemType = String.class,
                                                          required = true, minSize = 2) }) })
    private class Classifier implements SemanticAdapter {
        public void validate(SemanticQuestion question) {
        }

        public SemanticResult evaluate(SemanticQuestion question, Object state) {
            calls.incrementAndGet();
            selected = state;
            return new SemanticResult(
                    state.toString().contains("invoice") ? "billing" : "technical", null, null, null, null);
        }
    }

    private static String declarations(String state) {
        return """
                - semantic:
                    question:
                      department:
                        type: choice
                        state: %s
                        instructions: Which department?
                        criteria:
                          billing: Invoices and refunds
                          technical: Bugs and outages
                """.formatted(state);
    }

    @SemanticExpert(name = "security", description = "Documentation example expert", provider = "test", artifactId = "test",
                    operations = {
                            @SemanticOperation(name = "injection", description = "Detect injection",
                                               inputTypes = SemanticExpert.InputType.TEXT,
                                               inputRequirements = "Text", resultType = SemanticExpert.ResultType.BOOLEAN,
                                               resultMeaning = "Injection detected"),
                            @SemanticOperation(name = "classify", description = "Find labels",
                                               inputTypes = SemanticExpert.InputType.TEXT,
                                               inputRequirements = "Text",
                                               resultType = SemanticExpert.ResultType.CLASSIFICATION,
                                               resultMeaning = "Content labels", labels = "privacy") })
    public static class SecurityExpert implements SemanticAdapter {
        public void validate(SemanticQuestion question) {
        }

        public SemanticResult evaluate(SemanticQuestion question, Object state) {
            return new SemanticResult(
                    question.getOperation().equals("injection")
                            ? state.toString().contains("injection") : Set.of("privacy"),
                    null, null, null, null);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void reloadPreparesExpertsAcrossTheResourceSetBeforeValidatingDeclarations(boolean separateResource) throws Exception {
        context.start();
        String beans = """
                - beans:
                    - name: newSecurity
                      type: org.apache.camel.dsl.yaml.SemanticQuestionTest$SecurityExpert
                """;
        String declarations = """
                - semantic:
                    expert: newSecurity
                    evaluation:
                      injection:
                        operation: injection
                """;
        if (separateResource) {
            loadRoutes(ResourceHelper.fromString("evaluations.yaml", declarations),
                    ResourceHelper.fromString("experts.yaml", beans));
        } else {
            loadRoutes(ResourceHelper.fromString("evaluations.yaml", declarations + beans));
        }
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("injection");
        assertThat(context.resolveLanguage("simple").createPredicate("${semantic('injection')}").matches(exchange)).isTrue();
    }

    @Test
    void documentationExpertExampleExecutesBooleanChoiceAndClassification() throws Exception {
        context.getRegistry().bind("security", new SecurityExpert());
        context.getRegistry().bind("decisions", new Classifier());
        String docs
                = Files.readString(Path.of("../../../components/camel-ai/camel-semantic/src/main/docs/semantic-language.adoc"));
        String declarations = docs.substring(docs.indexOf("- semantic:"));
        declarations = declarations.substring(0, declarations.indexOf("----"));
        String route = docs.substring(docs.indexOf("- route:"));
        route = route.substring(0, route.indexOf("----"));
        for (String target : List.of("security-review", "billing", "technical", "sales", "manual-review")) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:" + target).to("mock:" + target);
                }
            });
        }
        loadRoutes(declarations + route);
        context.start();
        var billing = context.getEndpoint("mock:billing", MockEndpoint.class);
        var review = context.getEndpoint("mock:security-review", MockEndpoint.class);
        billing.expectedBodiesReceived("invoice");
        review.expectedBodiesReceived("injection");
        try (var template = context.createProducerTemplate()) {
            template.sendBody("direct:incoming", "invoice");
            template.sendBody("direct:incoming", "injection");
        }
        MockEndpoint.assertIsSatisfied(context);
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("invoice");
        assertThat(context.resolveLanguage("simple").createExpression("${semantic('categories')}")
                .evaluate(exchange, Object.class)).isEqualTo(Set.of("privacy"));
    }

    @Test
    void javaDeclarationsCoexistWithUnchangedYamlDeclarations() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                semanticQuestions(this).question("javaDepartment").type("choice").state("${header.selected}")
                        .instructions("Which department?")
                        .criterion("billing", "Invoices and refunds").criterion("technical", "Bugs and outages").register();
            }
        });
        loadRoutes(declarations("${header.selected}") + route());
        context.start();
        SemanticQuestion java = SemanticQuestions.get(context).get("javaDepartment");
        SemanticQuestion yaml = SemanticQuestions.get(context).get("department");
        assertThat(java).usingRecursiveComparison().isEqualTo(yaml);
        assertThat(calls).hasValue(0);
        try (var template = context.createProducerTemplate()) {
            var exchange = template.request("direct:tickets", e -> {
                e.getMessage().setBody("original");
                e.getMessage().setHeader("selected", "invoice");
            });
            assertThat(exchange.getException()).isNull();
            assertThat(context.resolveLanguage("semantic").createExpression("refs:javaDepartment,department")
                    .evaluate(exchange, Map.class)).containsEntry("javaDepartment", "billing")
                    .containsEntry("department", "billing");
        }
        assertThat(selected).isEqualTo("invoice");
    }

    @Test
    void ordinaryResourceDoesNotCreateSemanticQuestionState() throws Exception {
        loadRoutes("""
                - from:
                    uri: direct:ordinary
                    steps:
                      - to: mock:ordinary
                """);
        assertThat(context.getCamelContextExtension().getContextPlugin(SemanticQuestions.class)).isNull();
    }

    private static String route() {
        return """
                - route:
                    id: tickets
                    from:
                      uri: direct:tickets
                      steps:
                        - setProperty:
                            name: department
                            expression:
                              language:
                                language: semantic
                                expression: ref:department
                        - choice:
                            when:
                              - expression:
                                  simple:
                                    expression: "${exchangeProperty.department} == 'billing'"
                                steps:
                                  - to: mock:billing
                              - expression:
                                  simple:
                                    expression: "${exchangeProperty.department} == 'technical'"
                                steps:
                                  - to: mock:technical
                            otherwise:
                              steps:
                                - to: mock:other
                """;
    }

    @Test
    void batchExpressionReusesNamedQuestionsAndResults() throws Exception {
        loadRoutes(declarations("${body}") + declarations("${body}").replace("department:", "second:") + """
                - route:
                    from:
                      uri: direct:batch
                      steps:
                        - setProperty:
                            name: decision
                            expression:
                              language:
                                language: semantic
                                expression: refs:department,second
                        - setHeader:
                            name: selectedDepartment
                            expression:
                              simple:
                                expression: "${exchangeProperty.decision[department]}"
                        - to: mock:batch
                """);
        context.start();
        MockEndpoint mock = context.getEndpoint("mock:batch", MockEndpoint.class);
        mock.expectedBodiesReceived("invoice");
        mock.expectedHeaderReceived("selectedDepartment", "billing");
        try (var template = context.createProducerTemplate()) {
            template.sendBody("direct:batch", "invoice");
        }
        mock.assertIsSatisfied();
        assertThat(calls).hasValue(2);
        assertThat(mock.getExchanges().get(0).getProperty(SemanticLanguage.RESULTS, Map.class))
                .containsKeys("department", "second");
    }

    @Test
    void declarationAfterRouteEvaluatesOnceAndPreservesMessage() throws Exception {
        loadRoutes(route() + declarations("${header.selected}"));
        assertThat(calls).hasValue(0);
        context.start();
        var billing = context.getEndpoint("mock:billing", MockEndpoint.class);
        var technical = context.getEndpoint("mock:technical", MockEndpoint.class);
        billing.expectedBodiesReceived("original");
        technical.expectedBodiesReceived("original");
        try (var template = context.createProducerTemplate()) {
            template.sendBodyAndHeader("direct:tickets", "original", "selected", "invoice");
            template.sendBodyAndHeader("direct:tickets", "original", "selected", "outage");
        }
        billing.assertIsSatisfied();
        technical.assertIsSatisfied();
        assertThat(calls).hasValue(2);
        assertThat(selected).isEqualTo("outage");
    }

    @Test
    void declarationsInAnotherResourceResolveBeforeTraffic() throws Exception {
        loadRoutes(ResourceHelper.fromString("routes.yaml", route()),
                ResourceHelper.fromString("questions.yaml", declarations("${body}")));
        context.start();
        try (var template = context.createProducerTemplate()) {
            template.sendBody("direct:tickets", "invoice");
        }
        assertThat(calls).hasValue(1);
    }

    @Test
    void resourceReloadUpdatesStateAndRemovesObsoleteQuestions() throws Exception {
        loadRoutes(ResourceHelper.fromString("questions.yaml", declarations("${body}")),
                ResourceHelper.fromString("routes.yaml", route()));
        context.start();
        PluginHelper.getRoutesLoader(context)
                .updateRoutes(ResourceHelper.fromString("questions.yaml", declarations("${header.updated}")));
        try (var template = context.createProducerTemplate()) {
            template.sendBodyAndHeader("direct:tickets", "original", "updated", "invoice");
        }
        assertThat(selected).isEqualTo("invoice");
        PluginHelper.getRoutesLoader(context).updateRoutes(ResourceHelper.fromString("questions.yaml", "[]"));
        assertThatThrownBy(() -> SemanticQuestions.get(context).get("department")).hasMessageContaining("Unknown");
    }

    @ParameterizedTest
    @MethodSource("malformedTopLevelResources")
    void malformedTopLevelEntriesUseNormalLoaderErrorsAndKeepQuestions(String yaml) throws Exception {
        loadRoutes(ResourceHelper.fromString("questions.yaml", declarations("${body}")));
        context.start();
        SemanticQuestion original = SemanticQuestions.get(context).get("department");
        assertThatThrownBy(() -> PluginHelper.getRoutesLoader(context)
                .updateRoutes(ResourceHelper.fromString("questions.yaml", yaml)))
                .isInstanceOf(YamlDeserializationException.class)
                .hasMessageContaining("Unable to find constructor for node");
        assertThat(SemanticQuestions.get(context).get("department")).isSameAs(original);
    }

    static Stream<String> malformedTopLevelResources() {
        return Stream.of("- invalid\n", "- []\n")
                .flatMap(entry -> Stream.of(entry, declarations("${header.updated}") + entry));
    }

    @Test
    void watcherDropsDeletedQuestionOnlyResourcesBeforeLoadingRenamedFiles() throws Exception {
        Path original = directory.resolve("questions.yaml");
        Files.writeString(original, declarations("${body}"));
        Resource source = ResourceHelper.resolveResource(context, original.toUri().toString());
        loadRoutes(source);
        context.start();
        TestWatcher watcher = new TestWatcher();
        watcher.setCamelContext(context);
        Path renamed = Files.move(original, directory.resolve("q.yaml"));
        watcher.reload(source);
        assertThatThrownBy(() -> SemanticQuestions.get(context).get("department"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown");
        Resource replacement = ResourceHelper.resolveResource(context, renamed.toUri().toString());
        watcher.reload(replacement);
        assertThat(watcher.getLastError()).isNull();
        assertThat(SemanticQuestions.get(context).get("department").getState()).isEqualTo("${body}");
        Files.delete(renamed);
        watcher.reload(replacement);
        assertThatThrownBy(() -> SemanticQuestions.get(context).get("department"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown");
    }

    private static class TestWatcher extends RouteWatcherReloadStrategy {
        void reload(Resource resource) {
            onRouteReload(List.of(resource), false);
        }
    }

    @Test
    void duplicatesAndInvalidDefinitionsAreRejected() {
        assertThatThrownBy(() -> loadRoutesNoValidate(declarations("${body}") + declarations("${body}")))
                .hasStackTraceContaining("Duplicate semantic question");
        assertThatThrownBy(() -> loadRoutesNoValidate(declarations("${body}").replace("instructions:", "typo:")))
                .hasStackTraceContaining("Unknown property");
        assertThatThrownBy(() -> loadRoutesNoValidate(declarations("${body}")
                .replace("instructions:", "uncertainty-policy: fail\n        instructions:")))
                .hasStackTraceContaining("Unknown property");
    }

    @Test
    void schemaAcceptsBooleanAndScoreDefinitionsAndRejectsWrongCriteria() throws Exception {
        loadRoutes("""
                - semantic:
                    question:
                      actionable:
                        type: boolean
                        instructions: Is the request actionable?
                        threshold: 0.8
                        uncertainty: 0.1
                        uncertaintyPolicy: fail
                      urgency:
                        type: score
                        instructions: How urgent?
                        criteria: [Routine, Urgent]
                """);
        assertThat(SemanticQuestions.get(context).get("urgency").getLevels()).containsExactly("Routine", "Urgent");
        context.start();
        assertThatThrownBy(() -> loadRoutesNoValidate(declarations("${body}").replace("type: choice", "type: score")))
                .hasStackTraceContaining("criteria").hasStackTraceContaining("List");
    }

    @Test
    void numericPlaceholdersResolveBeforeValidation() throws Exception {
        loadRoutesNoValidate("""
                - semantic:
                    question:
                      urgent:
                        type: boolean
                        instructions: Urgent?
                        threshold: "{{threshold:0.8}}"
                        uncertainty: "{{uncertainty:0.1}}"
                """);
        assertThat(SemanticQuestions.get(context).get("urgent").getThreshold()).isEqualTo(0.8);
        assertThat(SemanticQuestions.get(context).get("urgent").getUncertainty()).isEqualTo(0.1);
    }

    @ParameterizedTest
    @ValueSource(strings = { "threshold", "uncertainty" })
    void invalidNumericValuesIdentifyQuestionFieldAndLocation(String field) {
        String yaml = """
                - semantic:
                    question:
                      spam:
                        type: boolean
                        instructions: Is this spam?
                        %s: abc
                """.formatted(field);
        assertThatThrownBy(() -> loadRoutesNoValidate(yaml))
                .hasMessageContaining("route-0.yaml")
                .isInstanceOfSatisfying(YamlDeserializationException.class, error -> {
                    assertThat(error).hasMessageContaining(
                            "Invalid numeric value for '" + field + "' in semantic question 'spam'");
                    assertThat(error.getProblemMark()).hasValueSatisfying(mark -> {
                        assertThat(mark.getLine()).isEqualTo(5);
                        assertThat(mark.getColumn()).isEqualTo(8 + field.length() + 2);
                    });
                });
    }

    @ParameterizedTest
    @ValueSource(strings = { "type", "uncertaintyPolicy" })
    void invalidContractValuesFailAtStartupWithoutInference(String field) throws Exception {
        String yaml = """
                - semantic:
                    question:
                      spam:
                        type: boolean
                        instructions: Is this spam?
                """;
        yaml = field.equals("type")
                ? yaml.replace("type: boolean", "type: unsupported")
                : yaml + "        uncertaintyPolicy: unsupported\n";
        loadRoutesNoValidate(yaml);
        assertThatThrownBy(context::start).hasStackTraceContaining("spam")
                .hasStackTraceContaining("classifier")
                .hasStackTraceContaining(field.equals("type") ? "operation" : "uncertaintyPolicy");
        assertThat(calls).hasValue(0);
    }

    @ParameterizedTest
    @ValueSource(strings = { "non-match", "NON_MATCH", "nonMatch" })
    void enumParsingFollowsYamlDslConventions(String policy) throws Exception {
        loadRoutesNoValidate("""
                - semantic:
                    question:
                      spam:
                        type: BoOlEaN
                        instructions: Is this spam?
                        uncertaintyPolicy: %s
                """.formatted(policy));
        SemanticQuestion question = SemanticQuestions.get(context).get("spam");
        assertThat(question.getType()).isEqualTo(SemanticQuestion.Type.BOOLEAN);
        assertThat(question.getUncertaintyPolicy()).isEqualTo(SemanticQuestion.UncertaintyPolicy.NON_MATCH);
    }

    @ParameterizedTest
    @MethodSource("invalidStructures")
    void invalidStructuresIdentifySourceAndOffendingNode(String yaml, String message, int line, int column) {
        assertThatThrownBy(() -> loadRoutesNoValidate(yaml))
                .hasMessageContaining("route-0.yaml")
                .isInstanceOfSatisfying(YamlDeserializationException.class, error -> {
                    assertThat(error).hasMessageContaining(message);
                    assertThat(error.getProblemMark()).hasValueSatisfying(mark -> {
                        assertThat(mark.getLine()).isEqualTo(line);
                        assertThat(mark.getColumn()).isEqualTo(column);
                    });
                });
    }

    static Stream<Arguments> invalidStructures() {
        String declaration = declarations("${body}");
        String question = """
                - semantic:
                    question:
                      q: {type: boolean, instructions: Is it valid?}
                """;
        return Stream.of(
                Arguments.of(declaration.replace("instructions:", "typo:"),
                        "Unknown property 'typo' in semantic question 'department'", 5, 14),
                Arguments.of(declaration.replace("        type: choice\n", ""),
                        "Specify exactly one operation or type: department", 3, 8),
                Arguments.of(declaration.replace("type: choice", "type: choice\n        type: choice"),
                        "Duplicate key 'type' in semantic question 'department'", 4, 8),
                Arguments.of("- semantic: {other: {}}", "Unknown property 'other' in semantic declaration", 0, 20),
                Arguments.of(question + question, "Duplicate semantic question: q", 4, 4),
                Arguments.of(question + "      q: {type: boolean, instructions: Is it valid?}\n",
                        "Duplicate key 'q' in semantic evaluations", 3, 6));
    }

}
