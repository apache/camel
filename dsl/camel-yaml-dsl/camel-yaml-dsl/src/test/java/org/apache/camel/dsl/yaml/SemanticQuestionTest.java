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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.semantic.SemanticAdapter;
import org.apache.camel.semantic.SemanticQuestion;
import org.apache.camel.semantic.SemanticQuestions;
import org.apache.camel.semantic.SemanticResult;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticQuestionTest extends YamlTestSupport {
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
        ((SemanticLanguage) context.resolveLanguage("semantic")).setAdapter("#bean:classifier");
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

    private static String route() {
        return """
                - route:
                    id: tickets
                    from:
                      uri: direct:tickets
                      steps:
                        - choice:
                            selector:
                              language:
                                language: semantic
                                expression: ref:department
                            when:
                              - value: billing
                                steps:
                                  - to: mock:billing
                              - value: technical
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
    void duplicatesAndInvalidDefinitionsAreRejected() {
        assertThatThrownBy(() -> loadRoutesNoValidate(declarations("${body}") + declarations("${body}")))
                .hasStackTraceContaining("Duplicate semantic question");
        assertThatThrownBy(() -> loadRoutesNoValidate(declarations("${body}").replace("instructions:", "typo:")))
                .hasStackTraceContaining("Unknown property");
        assertThatThrownBy(() -> loadRoutes(declarations("${body}").replace("type: choice", "type: unsupported")))
                .isInstanceOf(Exception.class);
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
        assertThatThrownBy(() -> loadRoutes(declarations("${body}").replace("type: choice", "type: score")))
                .isInstanceOf(Exception.class);
    }
}
