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
package org.apache.camel.dsl.yaml.validator;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticSchemaTest {
    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void numericDecisionPoliciesAcceptRuntimeScalarForms(boolean canonical) throws Exception {
        var validator = new YamlValidator(canonical);
        for (String value : new String[] { "0.1", "\"0.1\"", "\"{{limit}}\"", "\"{{limit:0.1}}\"" }) {
            assertThat(validator.validate(booleanQuestion(value)))
                    .as("decision policy %s in canonical mode %s", value, canonical).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void scalarConversionsInDifferentQuestionsSelectTheirOwnSchema(boolean canonical) throws Exception {
        var validator = new YamlValidator(canonical);
        String questions = booleanQuestion("\"{{limit:0.1}}\"") + """
                      topic:
                        type: choice
                        instructions: Select the topic
                        criteria: {billing: 123, support: 456}
                      urgency:
                        type: score
                        instructions: Assess urgency
                        criteria: [1, 2, 3]
                """;
        assertThat(validator.validate(questions)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void placeholdersDoNotHideInvalidQuestionFields(boolean canonical) throws Exception {
        var validator = new YamlValidator(canonical);
        String question = booleanQuestion("\"{{limit:0.1}}\"");
        for (String invalid : new String[] {
                question.replace("threshold:", "thresholdd:"),
                question.replace("type: boolean", "type: choice\n        criteria: {billing: Payments}"),
                question.replace("type: boolean", "type: score\n        criteria: [Low, High]"),
                question.replace("threshold: \"{{limit:0.1}}\"", "threshold: not-a-number"),
                question.replace("threshold: \"{{limit:0.1}}\"", "threshold: {value: 0.1}"),
                question.replace("type: boolean", "type: unknown") }) {
            assertThat(validator.validate(invalid)).as("invalid question: %s", invalid).isNotEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void scalarConversionsDoNotHideMissingRequiredFields(boolean canonical) throws Exception {
        var validator = new YamlValidator(canonical);
        for (String fields : new String[] {
                "instructions: 123",
                "instructions: Assess this\n        threshold: \"{{limit}}\"",
                "type: choice\n        instructions: 123",
                "type: score\n        instructions: 123" }) {
            String invalid = """
                    - semantic:
                        question:
                          invalid:
                            %s
                    """.formatted(fields);
            String valid = booleanQuestion("\"{{limit:0.1}}\"");
            for (String yaml : new String[] { invalid, valid + invalid, invalid + valid }) {
                assertThat(validator.validate(yaml)).as("missing required field: %s", yaml).isNotEmpty();
            }
        }
    }

    private static String booleanQuestion(String value) {
        return """
                - semantic:
                    question:
                      actionable:
                        type: boolean
                        instructions: Is this actionable?
                        threshold: %s
                        uncertainty: %s
                """.formatted(value, value);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void questionTypesHaveDistinctClosedShapes(boolean canonical) throws Exception {
        YamlValidator validator = new YamlValidator(canonical);
        String choice = """
                - semantic:
                    question:
                      topic:
                        type: choice
                        instructions: Select the topic
                        criteria:
                          customer-service: Customer service
                          billing: Payments
                """;
        assertThat(validator.validate(choice)).isEmpty();
        assertThat(validator.validate(choice.replace("type: choice", "type: boolean")))
                .isEmpty(); // criterion key constraints are checked by the runtime
        for (String invalid : new String[] {
                choice.replace("type: choice", "type: score"),
                choice.replace("instructions:", "threshold: 0.5\n        instructions:"),
                choice.replace("instructions:", "typo:"),
                choice.replace("instructions:", "uncertainty-policy: fail\n        instructions:") }) {
            assertThat(validator.validate(invalid)).isNotEmpty().allSatisfy(
                    error -> assertThat(error.getInstanceLocation().toString()).startsWith("/0/semantic/question/topic"));
        }
        assertThat(validator.validate("""
                - semantic:
                    question:
                      actionable:
                        type: boolean
                        instructions: Is this actionable?
                        uncertaintyPolicy: non-match
                      urgency:
                        type: score
                        instructions: Assess urgency
                        criteria: [Routine, Urgent]
                """)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void storedSemanticDecisionUsesOrdinaryChoicePredicates(boolean canonical) throws Exception {
        YamlValidator validator = new YamlValidator(canonical);
        String route = """
                - route:
                    from:
                      uri: direct:start
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
                                  - to:
                                      uri: mock:billing
                """;
        assertThat(validator.validate(route)).isEmpty();
        assertThat(validator.validate(route.replace("""
                              - expression:
                                  simple:
                                    expression: "${exchangeProperty.department} == 'billing'"
                """, "              - value: billing\n"))).isNotEmpty();
    }
}
