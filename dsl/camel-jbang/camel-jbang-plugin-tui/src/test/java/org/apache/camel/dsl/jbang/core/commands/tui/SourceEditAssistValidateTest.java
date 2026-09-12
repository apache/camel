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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceEditAssistValidateTest {

    private static SourceEditAssist assist() {
        AtomicReference<List<IntegrationInfo>> data = new AtomicReference<>(List.of());
        AtomicReference<List<InfraInfo>> infraData = new AtomicReference<>(List.of());
        return new SourceEditAssist(new MonitorContext(data, infraData));
    }

    @Test
    void validRouteHasNoErrors() {
        List<String> errors = assist().validateSource("timer-log.camel.yaml", """
                - route:
                    id: timer-log
                    from:
                      uri: timer:tick
                      steps:
                        - log:
                            message: "${body}"
                            loggingLevel: WARN
                """);

        assertTrue(errors.isEmpty(), String.valueOf(errors));
    }

    @Test
    void misspelledOptionIsReported() {
        List<String> errors = assist().validateSource("timer-log.camel.yaml", """
                - route:
                    id: timer-log
                    from:
                      uri: timer:tick
                      steps:
                        - log:
                            message: "${body}"
                            logLevel: WARN
                """);

        assertFalse(errors.isEmpty(), "logLevel is not an option of the log EIP");
        assertTrue(errors.stream().anyMatch(e -> e.contains("logLevel")), String.valueOf(errors));
    }

    @Test
    void brokenYamlIsReported() {
        List<String> errors = assist().validateCamelYaml("- route:\n  from: [unclosed\n");

        assertFalse(errors.isEmpty());
        assertTrue(assist().validateCamelYaml("").isEmpty());
    }

    @Test
    void otherFileTypesAreNotValidated() {
        assertTrue(assist().validateSource("README.md", "# whatever").isEmpty());
        assertTrue(SourceEditAssist.isValidatableFile("application.properties"));
        assertTrue(SourceEditAssist.isValidatableFile("routes.YAML"));
        assertFalse(SourceEditAssist.isValidatableFile("Foo.java"));
    }

    @Test
    void placeholderAsOperandIsValidated() {
        // CAMEL-24692: the catalog can validate these now, so they must no longer be skipped
        assertFalse(SourceEditAssist.hasPlaceholderAsLogicalOperand("{{hot.threshold}}"));
        assertFalse(SourceEditAssist.hasPlaceholderAsLogicalOperand("${body} >= {{hot.threshold}}"));
        assertFalse(SourceEditAssist.hasPlaceholderAsLogicalOperand("{{a}} == {{b}}"));
        assertFalse(SourceEditAssist.hasPlaceholderAsLogicalOperand("${body} >= {{t}} && ${body} < {{u}}"));
        assertFalse(SourceEditAssist.hasPlaceholderAsLogicalOperand("${body} == 'abc'"));
        assertFalse(SourceEditAssist.hasPlaceholderAsLogicalOperand(null));
    }

    @Test
    void placeholderAsLogicalOperandIsSkipped() {
        // a placeholder can expand to an entire predicate which the catalog cannot know
        assertTrue(SourceEditAssist.hasPlaceholderAsLogicalOperand("{{a}} && {{b}}"));
        assertTrue(SourceEditAssist.hasPlaceholderAsLogicalOperand("${body} > 1 && {{flag}}"));
        assertTrue(SourceEditAssist.hasPlaceholderAsLogicalOperand("{{flag}} || ${body} > 1"));
        // a logical operator inside a quoted literal is not an operator
        assertFalse(SourceEditAssist.hasPlaceholderAsLogicalOperand("${body} == '{{a}} && {{b}}'"));
    }

    @Test
    void placeholderPredicateIsNotReportedAsError() {
        List<String> errors = assist().validateSource("placeholder.camel.yaml", """
                - route:
                    id: placeholder
                    from:
                      uri: timer:tick
                      steps:
                        - choice:
                            when:
                              - simple: "${body} >= {{hot.threshold}}"
                                steps:
                                  - log:
                                      message: "hot"
                              - simple: "{{enabled}} && ${body} > 1"
                                steps:
                                  - log:
                                      message: "on"
                """);

        assertTrue(errors.stream().noneMatch(e -> e.contains("Simple syntax error")), String.valueOf(errors));
    }
}
