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

import com.networknt.schema.Error;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24702: options that take an expression (handled, continued, retryWhile, the aggregate and throttle correlation
 * expressions) are commonly written as a plain value. The DSL does not accept that, so the validator must say what to
 * write instead of "boolean found, object expected".
 */
public class YamlValidatorExpressionHintTest {

    private static YamlValidator classic;
    private static YamlValidator canonical;

    @BeforeAll
    public static void setup() throws Exception {
        classic = new YamlValidator();
        classic.init();
        canonical = new YamlValidator(true);
        canonical.init();
    }

    @Test
    public void testHandledAsPlainValue() {
        String yaml = """
                - onException:
                    exception:
                      - java.lang.Exception
                    handled: true
                    steps:
                      - log:
                          message: "Error: ${exception.message}"
                """;
        assertHint(yaml, "handled: {constant: {expression: \"true\"}}", "handled: {simple: {expression: \"...\"}}");
    }

    @Test
    public void testContinuedAsPlainValue() {
        String yaml = """
                - onException:
                    exception:
                      - java.lang.Exception
                    continued: "true"
                    steps:
                      - log:
                          message: "Error: ${exception.message}"
                """;
        assertHint(yaml, "continued: {constant: {expression: \"true\"}}");
    }

    @Test
    public void testAggregateCompletionSizeAsPlainValue() {
        String yaml = """
                - from:
                    uri: timer:tick
                    steps:
                      - aggregate:
                          correlationExpression:
                            constant:
                              expression: "a"
                          completionSizeExpression: 10
                          aggregationStrategy: myStrategy
                          steps:
                            - log:
                                message: "${body}"
                """;
        assertHint(yaml, "completionSizeExpression: {constant: {expression: \"10\"}}");
    }

    @Test
    public void testExpressionFormIsAccepted() {
        // explicit form, so that it is valid in canonical mode too
        String yaml = """
                - onException:
                    exception:
                      - java.lang.Exception
                    handled:
                      constant:
                        expression: "true"
                    continued:
                      simple:
                        expression: "${header.retry} == null"
                    steps:
                      - log:
                          message: "Error: ${exception.message}"
                """;
        for (YamlValidator validator : List.of(classic, canonical)) {
            assertThat(validate(validator, yaml)).isEmpty();
        }
    }

    private void assertHint(String yaml, String... expectedInMessage) {
        for (YamlValidator validator : List.of(classic, canonical)) {
            String mode = validator.isCanonical() ? "canonical" : "classic";
            List<Error> errors = validate(validator, yaml);
            assertThat(errors).as("%s mode must report the plain value:\n%s", mode, yaml).isNotEmpty();
            for (String expected : expectedInMessage) {
                assertThat(errors)
                        .as("%s mode must show the expression form '%s'", mode, expected)
                        .anyMatch(e -> e.getMessage().contains(expected));
            }
            // one hint, not the two or three type errors the schema composition produces
            assertThat(errors.stream().filter(e -> e.getMessage().contains("an expression expected")).count())
                    .as("%s mode must report the hint once", mode)
                    .isEqualTo(1);
            assertThat(errors).noneMatch(e -> e.getMessage().contains("object expected"));
        }
    }

    private List<Error> validate(YamlValidator validator, String yaml) {
        try {
            return validator.validate(yaml);
        } catch (Exception e) {
            throw new AssertionError("Failed to validate:\n" + yaml, e);
        }
    }
}
