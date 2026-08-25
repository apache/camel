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

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YamlEndpointValidationTest {

    private static CamelCatalog catalog;

    @BeforeAll
    static void loadCatalog() {
        catalog = new DefaultCamelCatalog();
    }

    @Test
    void validExpandedFormNoErrors() {
        String yaml = """
                - from:
                    uri: timer:tick
                    parameters:
                      period: 1000
                      fixedRate: true
                  steps:
                    - log: "${body}"
                """;
        List<String> errors = SourceTab.doValidateYamlEndpoints(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void expandedFormUnknownOption() {
        String yaml = """
                - from:
                    uri: timer:tick
                    parameters:
                      period: 1000
                      badOption: xyz
                  steps:
                    - log: "${body}"
                """;
        List<String> errors = SourceTab.doValidateYamlEndpoints(yaml, catalog);
        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).contains("timer:");
        assertThat(errors.get(0)).containsIgnoringCase("unknown");
    }

    @Test
    void expandedFormInvalidBoolean() {
        String yaml = """
                - from:
                    uri: timer:tick
                    parameters:
                      fixedRate: notABoolean
                  steps:
                    - log: "${body}"
                """;
        List<String> errors = SourceTab.doValidateYamlEndpoints(yaml, catalog);
        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).contains("timer:");
    }

    @Test
    void inlineUriNoErrors() {
        String yaml = """
                - from: timer:tick?period=1000
                  steps:
                    - to: log:myLogger
                """;
        List<String> errors = SourceTab.doValidateYamlEndpoints(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void inlineUriUnknownOption() {
        String yaml = """
                - from: timer:tick?badOption=xyz
                  steps:
                    - log: "${body}"
                """;
        List<String> errors = SourceTab.doValidateYamlEndpoints(yaml, catalog);
        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).contains("timer:");
        assertThat(errors.get(0)).containsIgnoringCase("unknown");
    }

    @Test
    void expandedUriWithQueryParamsNoErrors() {
        String yaml = """
                - from:
                    uri: timer:tick?period=1000
                  steps:
                    - log: "${body}"
                """;
        List<String> errors = SourceTab.doValidateYamlEndpoints(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void expandedUriWithQueryParamsAndParametersBlock() {
        String yaml = """
                - from:
                    uri: timer:tick?period=1000
                    parameters:
                      fixedRate: true
                  steps:
                    - log: "${body}"
                """;
        List<String> errors = SourceTab.doValidateYamlEndpoints(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void expandedUriWithQueryParamsAndBadParameter() {
        String yaml = """
                - from:
                    uri: timer:tick?period=1000
                    parameters:
                      badOption: xyz
                  steps:
                    - log: "${body}"
                """;
        List<String> errors = SourceTab.doValidateYamlEndpoints(yaml, catalog);
        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).contains("timer:");
    }

    @Test
    void multipleEndpointsValidatedIndependently() {
        String yaml = """
                - from:
                    uri: timer:tick
                    parameters:
                      period: 1000
                  steps:
                    - to:
                        uri: log:myLogger
                        parameters:
                          badOption: xyz
                """;
        List<String> errors = SourceTab.doValidateYamlEndpoints(yaml, catalog);
        assertThat(errors).isNotEmpty();
        assertThat(errors).allSatisfy(e -> assertThat(e).contains("log:"));
        assertThat(errors).noneSatisfy(e -> assertThat(e).contains("timer:"));
    }

    @Test
    void placeholderUriSkipped() {
        String yaml = """
                - from: "{{myUri}}"
                  steps:
                    - log: "${body}"
                """;
        List<String> errors = SourceTab.doValidateYamlEndpoints(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void placeholderValueSkipped() {
        String yaml = """
                - from:
                    uri: timer:tick
                    parameters:
                      period: "{{myPeriod}}"
                  steps:
                    - log: "${body}"
                """;
        List<String> errors = SourceTab.doValidateYamlEndpoints(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void quotedParameterValues() {
        String yaml = """
                - from:
                    uri: timer:tick
                    parameters:
                      period: "1000"
                      fixedRate: 'true'
                  steps:
                    - log: "${body}"
                """;
        List<String> errors = SourceTab.doValidateYamlEndpoints(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void inlineToWithDash() {
        String yaml = """
                - from: timer:tick?period=1000
                  steps:
                    - to: seda:myQueue?badOption=xyz
                """;
        List<String> errors = SourceTab.doValidateYamlEndpoints(yaml, catalog);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("seda:");
    }

    @Test
    void validRouteNoEndpointErrors() {
        String yaml = """
                - from:
                    uri: timer:tick
                    parameters:
                      period: 5000
                      repeatCount: 1
                  steps:
                    - setBody:
                        simple: "Hello World"
                    - to:
                        uri: seda:result
                """;
        List<String> errors = SourceTab.doValidateYamlEndpoints(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void schemeOnlyUriWithParameters() {
        String yaml = """
                - route:
                    id: timer-log
                    from:
                      uri: timer
                      parameters:
                        timerName: tick
                        period: 1000
                        bridgeErrorHandler2: 123
                      steps:
                        - log:
                            message: "${body}"
                """;
        List<String> errors = SourceTab.doValidateYamlEndpoints(yaml, catalog);
        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(e -> e.contains("timer:") && e.contains("bridgeErrorHandler2"));
    }

    @Test
    void schemeOnlyUriValidOptions() {
        String yaml = """
                - route:
                    id: timer-log
                    from:
                      uri: timer
                      parameters:
                        timerName: tick
                        period: 1000
                        fixedRate: true
                      steps:
                        - log:
                            message: "${body}"
                """;
        List<String> errors = SourceTab.doValidateYamlEndpoints(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void commentsIgnored() {
        String yaml = """
                # from: timer:tick?badOption=xyz
                - from:
                    uri: timer:tick
                    parameters:
                      period: 1000
                  steps:
                    - log: "${body}"
                """;
        List<String> errors = SourceTab.doValidateYamlEndpoints(yaml, catalog);
        assertThat(errors).isEmpty();
    }
}
