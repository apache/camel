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

class YamlSimpleValidationTest {

    private static CamelCatalog catalog;

    @BeforeAll
    static void loadCatalog() {
        catalog = new DefaultCamelCatalog();
    }

    @Test
    void validExpressionInSetBody() {
        String yaml = """
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          simple: "${body}"
                """;
        List<String> errors = SourceTab.doValidateYamlSimple(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void invalidExpressionUnclosedBrace() {
        String yaml = """
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          simple: "${body"
                """;
        List<String> errors = SourceTab.doValidateYamlSimple(yaml, catalog);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("Simple syntax error");
    }

    @Test
    void validPredicateInFilter() {
        String yaml = """
                - from:
                    uri: timer:tick
                    steps:
                      - filter:
                          simple: "${header.foo} == 'bar'"
                """;
        List<String> errors = SourceTab.doValidateYamlSimple(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void validExpandedForm() {
        String yaml = """
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          expression:
                            simple:
                              expression: "${header.name}"
                """;
        List<String> errors = SourceTab.doValidateYamlSimple(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void invalidExpandedForm() {
        String yaml = """
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          expression:
                            simple:
                              expression: "${body"
                """;
        List<String> errors = SourceTab.doValidateYamlSimple(yaml, catalog);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("Simple syntax error");
    }

    @Test
    void placeholderOnlySkipped() {
        String yaml = """
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          simple: "{{myPlaceholder}}"
                """;
        List<String> errors = SourceTab.doValidateYamlSimple(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void logMessageValidated() {
        String yaml = """
                - from:
                    uri: timer:tick
                    steps:
                      - log:
                          message: "${body"
                """;
        List<String> errors = SourceTab.doValidateYamlSimple(yaml, catalog);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("Simple syntax error");
    }

    @Test
    void validLogMessage() {
        String yaml = """
                - from:
                    uri: timer:tick
                    steps:
                      - log:
                          message: "Order: ${body}"
                """;
        List<String> errors = SourceTab.doValidateYamlSimple(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void listItemSimpleWithExpression() {
        String yaml = """
                - from:
                    uri: timer:tick
                    steps:
                      - choice:
                          when:
                            - simple:
                                expression: "${body} >X= 30"
                              steps:
                                - log: "big"
                """;
        List<String> errors = SourceTab.doValidateYamlSimple(yaml, catalog);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("Simple syntax error");
    }

    @Test
    void listItemInlineSimple() {
        String yaml = """
                - from:
                    uri: timer:tick
                    steps:
                      - choice:
                          when:
                            - simple: "${body} >= 30"
                              steps:
                                - log: "big"
                """;
        List<String> errors = SourceTab.doValidateYamlSimple(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void multipleErrors() {
        String yaml = """
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          simple: "${body"
                      - setHeader:
                          name: foo
                          simple: "${header.bar"
                """;
        List<String> errors = SourceTab.doValidateYamlSimple(yaml, catalog);
        assertThat(errors).hasSizeGreaterThanOrEqualTo(2);
    }
}
