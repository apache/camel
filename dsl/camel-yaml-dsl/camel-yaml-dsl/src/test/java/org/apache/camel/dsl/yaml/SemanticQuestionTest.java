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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.common.exception.YamlDeserializationException;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.semantic.SemanticAdapter;
import org.apache.camel.semantic.SemanticQuestion;
import org.apache.camel.semantic.SemanticQuestions;
import org.apache.camel.semantic.SemanticResult;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.RouteWatcherReloadStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticQuestionTest extends YamlTestSupport {
    @TempDir
    Path directory;
    private final AtomicInteger calls = new AtomicInteger();
    private Object selected;

    @Override
    public void doSetup() {
        context.getRegistry().bind("classifier", new SemanticAdapter() {
            public void validate(SemanticQuestion question) {
            }

            public SemanticResult evaluate(SemanticQuestion question, Object state) {
                calls.incrementAndGet();
                selected = state;
                return new SemanticResult(
                        state.toString().contains("invoice") ? "billing" : "technical", null, null, null, null);
            }
        });
        ((SemanticLanguage) context.resolveLanguage("semantic")).setAdapter("classifier");
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
        assertThatThrownBy(() -> loadRoutesNoValidate(declarations("${body}").replace("type: choice", "type: unsupported")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class).hasStackTraceContaining("UNSUPPORTED");
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
        assertThatThrownBy(() -> loadRoutesNoValidate(declarations("${body}").replace("type: choice", "type: score")))
                .hasStackTraceContaining("Node type map is invalid, expected array");
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
                .hasRootCauseInstanceOf(NumberFormatException.class)
                .cause().isInstanceOfSatisfying(YamlDeserializationException.class, error -> {
                    assertThat(error).hasMessageContaining(
                            "Invalid numeric value for '" + field + "' in semantic question 'spam': abc");
                    assertThat(error.getProblemMark()).hasValueSatisfying(mark -> {
                        assertThat(mark.getLine()).isEqualTo(5);
                        assertThat(mark.getColumn()).isEqualTo(8 + field.length() + 2);
                    });
                });
    }

    @ParameterizedTest
    @MethodSource("invalidEnums")
    void invalidEnumValuesIdentifyQuestionFieldAndLocation(String field, String value, int line) {
        String yaml = """
                - semantic:
                    question:
                      spam:
                        type: boolean
                        instructions: Is this spam?
                """;
        yaml = field.equals("type")
                ? yaml.replace("type: boolean", "type: " + value)
                : yaml + "        uncertaintyPolicy: " + value + "\n";
        String source = yaml;
        assertThatThrownBy(() -> loadRoutesNoValidate(source))
                .hasMessageContaining("route-0.yaml")
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .cause().isInstanceOfSatisfying(YamlDeserializationException.class, error -> {
                    assertThat(error).hasMessageContaining("Invalid value for '" + field + "' in semantic question 'spam'");
                    assertThat(error.getProblemMark()).hasValueSatisfying(mark -> {
                        assertThat(mark.getLine()).isEqualTo(line);
                        assertThat(mark.getColumn()).isEqualTo(8 + field.length() + 2);
                    });
                });
    }

    static Stream<Arguments> invalidEnums() {
        return Stream.of(
                Arguments.of("type", "unsupported", 3),
                Arguments.of("type", "''", 3),
                Arguments.of("uncertaintyPolicy", "unsupported", 5),
                Arguments.of("uncertaintyPolicy", "''", 5),
                Arguments.of("uncertaintyPolicy", "null", 5));
    }

    @ParameterizedTest
    @MethodSource("invalidStructures")
    void invalidStructuresIdentifySourceAndOffendingNode(String yaml, String message, int line, int column) {
        assertThatThrownBy(() -> loadRoutesNoValidate(yaml))
                .hasMessageContaining("route-0.yaml")
                .cause().isInstanceOfSatisfying(YamlDeserializationException.class, error -> {
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
                        "Semantic question type is required: department", 3, 8),
                Arguments.of(declaration.replace("type: choice", "type: choice\n        type: choice"),
                        "Duplicate key 'type' in semantic question 'department'", 4, 8),
                Arguments.of("- semantic: {other: {}}", "Semantic declaration requires only question", 0, 12),
                Arguments.of(question + question, "Duplicate semantic question: q", 4, 4),
                Arguments.of(question + "      q: {type: boolean, instructions: Is it valid?}\n",
                        "Duplicate key 'q' in semantic questions", 3, 6),
                Arguments.of(question.replace("Is it valid?", "''"),
                        "Invalid semantic question 'q': Question instructions must not be blank", 2, 9));
    }

}
