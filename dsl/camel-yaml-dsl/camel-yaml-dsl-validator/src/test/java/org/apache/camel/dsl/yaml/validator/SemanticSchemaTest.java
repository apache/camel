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
