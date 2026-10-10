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

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shapes a local model wrote and could not fix from the message it got: braces without quotes, an option of the
 * redelivery policy next to it, and the text of a language under another key than expression.
 */
public class QuotingAndOptionPlacementHintTest {

    private static YamlValidator validator;

    @BeforeAll
    public static void setup() throws Exception {
        validator = new YamlValidator();
        validator.init();
    }

    @Test
    public void anUnquotedPropertyPlaceholder() {
        // the parser said "Expected a field name (Scalar value in YAML), got this instead: <MappingStartEvent...>"
        String yaml = """
                - route:
                    from:
                      uri: timer:welcome
                      parameters:
                        period: {{welcome.period}}
                      steps:
                        - log:
                            message: "hi"
                """;
        assertThat(lines(yaml)).singleElement().satisfies(m -> assertThat(m)
                .contains("line 5: {{welcome.period}} without quotes is read as a YAML map")
                .contains("write the line as period: \"{{welcome.period}}\"")
                .doesNotContain("MappingStartEvent"));
    }

    @Test
    public void unquotedBracesWhereTextIsExpected() {
        String yaml = """
                - rest:
                    path: /stock
                    get:
                      - path: {sku}
                        to:
                          uri: direct:one-sku
                - route:
                    from:
                      uri: direct:one-sku
                      steps:
                        - log:
                            message: "x"
                """;
        assertThat(lines(yaml)).singleElement().satisfies(m -> assertThat(m)
                .startsWith("Line 4: ")
                .contains("{sku} without quotes is a YAML map")
                .contains("quote it, path: \"{sku}\""));
    }

    @Test
    public void anOptionOfTheRedeliveryPolicyNextToIt() {
        String yaml = """
                - onException:
                    exception:
                      - java.lang.IllegalStateException
                    maximumRedeliveries: 0
                    handled:
                      constant: "true"
                    steps:
                      - to:
                          uri: direct:parked
                - errorHandler:
                    deadLetterChannel:
                      deadLetterUri: direct:parked
                      redeliveryPolicy:
                        maximumRedeliveries: 2
                      retryAttemptedLogLevel: WARN
                - route:
                    from:
                      uri: direct:parked
                      steps:
                        - log:
                            message: "x"
                """;
        List<String> lines = lines(yaml);
        assertThat(lines).hasSize(2);
        assertThat(lines).anySatisfy(m -> assertThat(m)
                .startsWith("Line 4: ")
                .contains("maximumRedeliveries is an option of the redelivery policy")
                .contains("redeliveryPolicy: {maximumRedeliveries: 0}"));
        // the closest name was level, and the quick fix renamed it to that
        assertThat(lines).anySatisfy(m -> assertThat(m)
                .startsWith("Line 15: ")
                .contains("redeliveryPolicy: {retryAttemptedLogLevel: WARN}")
                .doesNotContain("did you mean"));
    }

    @Test
    public void theTextOfALanguageUnderAnotherKey() {
        String yaml = """
                - route:
                    from:
                      uri: timer:x
                      steps:
                        - setBody:
                            expression:
                              groovy:
                                script: "body.find { it.sku == headers.sku }"
                        - setHeader:
                            name: sku
                            expression:
                              groovy:
                                code: 'exchange.getVariable("sku")'
                        - filter:
                            expression:
                              simple:
                                text: "${body} != null"
                            steps:
                              - log:
                                  message: "x"
                """;
        List<String> lines = lines(yaml);
        assertThat(lines).anySatisfy(m -> assertThat(m)
                .contains("the groovy text goes in expression:, write groovy: {expression:"
                          + " \"body.find { it.sku == headers.sku }\"}"));
        // a double quote in the text is escaped in the form to write
        assertThat(lines).anySatisfy(m -> assertThat(m)
                .contains("write groovy: {expression: \"exchange.getVariable(\\\"sku\\\")\"}"));
        assertThat(lines).anySatisfy(m -> assertThat(m)
                .contains("the simple text goes in expression:, write simple: {expression: \"${body} != null\"}"));
    }

    private static List<String> lines(String yaml) {
        try {
            return YamlValidator.describeAll(yaml, validator.validate(yaml));
        } catch (Exception e) {
            throw new AssertionError("Failed to validate:\n" + yaml, e);
        }
    }
}
