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
            assertThat(validator.validate(booleanEvaluation(value)))
                    .as("decision policy %s in canonical mode %s", value, canonical).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void scalarConversionsInDifferentEvaluationsSelectTheirOwnSchema(boolean canonical) throws Exception {
        var validator = new YamlValidator(canonical);
        String evaluations = booleanEvaluation("\"{{limit:0.1}}\"") + """
                      topic:
                        type: choice
                        instructions: Select the topic
                        criteria: {billing: 123, support: 456}
                      urgency:
                        type: score
                        instructions: Assess urgency
                        criteria: [1, 2, 3]
                """;
        assertThat(validator.validate(evaluations)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void placeholdersDoNotHideInvalidEvaluationFields(boolean canonical) throws Exception {
        var validator = new YamlValidator(canonical);
        String evaluation = booleanEvaluation("\"{{limit:0.1}}\"");
        for (String invalid : new String[] {
                evaluation.replace("threshold:", "thresholdd:"),
                evaluation.replace("threshold: \"{{limit:0.1}}\"", "threshold: not-a-number"),
                evaluation.replace("threshold: \"{{limit:0.1}}\"", "threshold: {value: 0.1}") }) {
            assertThat(validator.validate(invalid)).as("invalid evaluation: %s", invalid).isNotEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void expertContractsOwnOperationNamesAndRequiredParameters(boolean canonical) throws Exception {
        var validator = new YamlValidator(canonical);
        String yaml = """
                - semantic:
                    expert: content
                    state: "${body}"
                    evaluation:
                      safety:
                        operation: classify
                        parameters:
                          policy:
                            labels: [privacy, unsafe]
                            limit: !number "{{limit:2}}"
                            enabled: true
                      department:
                        type: choice
                        instructions: Department?
                        criteria: {billing: Invoices, support: Questions}
                      score:
                        operation: rank
                        criteria: [Low, High]
                """;
        assertThat(validator.validate(yaml)).isEmpty();
        assertThat(validator.validate(yaml.replace("parameters:", "paramters:"))).isNotEmpty();
        assertThat(validator.validate(yaml.replace("parameters:", "parameters: []\n            ignored:")))
                .isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void semanticRequiresExactlyOneDeclarationBlock(boolean canonical) throws Exception {
        var validator = new YamlValidator(canonical);
        assertThat(validator.validate("- semantic: {expert: content}")).isNotEmpty();
        assertThat(validator.validate("- semantic: {question: {}}")).isNotEmpty();
        assertThat(validator.validate("- semantic: {question: {}, evaluation: {}}")).isNotEmpty();
        assertThat(validator.validate("- semantic: {evaluation: {q: {operation: detect, uncertaintyPolicy: expert-policy}}}"))
                .isEmpty();
    }

    private static String booleanEvaluation(String value) {
        return """
                - semantic:
                    evaluation:
                      actionable:
                        type: boolean
                        instructions: Is this actionable?
                        threshold: %s
                        uncertainty: %s
                """.formatted(value, value);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void evaluationSyntaxIsClosedAndExpertConstraintsAreValidatedAtRuntime(boolean canonical) throws Exception {
        YamlValidator validator = new YamlValidator(canonical);
        String choice = """
                - semantic:
                    evaluation:
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
                choice.replace("instructions:", "typo:"),
                choice.replace("instructions:", "uncertainty-policy: fail\n        instructions:") }) {
            assertThat(validator.validate(invalid)).isNotEmpty().allSatisfy(
                    error -> assertThat(error.getInstanceLocation().toString()).startsWith("/0/semantic/evaluation/topic"));
        }
        assertThat(validator.validate("""
                - semantic:
                    evaluation:
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
