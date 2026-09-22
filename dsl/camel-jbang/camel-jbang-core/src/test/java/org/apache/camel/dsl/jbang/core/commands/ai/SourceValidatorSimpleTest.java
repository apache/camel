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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceValidatorSimpleTest {

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
        List<String> errors = SourceValidator.validateYamlSimple(yaml, catalog);
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
        List<String> errors = SourceValidator.validateYamlSimple(yaml, catalog);
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
        List<String> errors = SourceValidator.validateYamlSimple(yaml, catalog);
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
        List<String> errors = SourceValidator.validateYamlSimple(yaml, catalog);
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
        List<String> errors = SourceValidator.validateYamlSimple(yaml, catalog);
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
        List<String> errors = SourceValidator.validateYamlSimple(yaml, catalog);
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
        List<String> errors = SourceValidator.validateYamlSimple(yaml, catalog);
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
        List<String> errors = SourceValidator.validateYamlSimple(yaml, catalog);
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
        List<String> errors = SourceValidator.validateYamlSimple(yaml, catalog);
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
        List<String> errors = SourceValidator.validateYamlSimple(yaml, catalog);
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
        List<String> errors = SourceValidator.validateYamlSimple(yaml, catalog);
        assertThat(errors).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void aPredicateUnderTheExpressionWrapperIsCheckedAsAPredicate() {
        List<String> msgs = SourceValidator.validateYamlSimple("""
                - from:
                    uri: timer:tick
                    steps:
                      - choice:
                          when:
                            - expression:
                                simple: "body contains 'critical'"
                              steps:
                                - log: "a"
                """, catalog);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("Unexpected token body").contains("did you mean ${body} contains 'critical'?");
    }

    @Test
    void sizeInsideAnAggregateNamesTheAggregatedSizeProperty() {
        List<String> msgs = SourceValidator.validateYamlSimple("""
                - from:
                    uri: timer:tick
                    steps:
                      - aggregate:
                          constant: "1"
                          completionSize: 3
                          steps:
                            - log:
                                message: "Batch complete! Collected ${size} messages"
                """, catalog);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("Unknown function: size").contains("${exchangeProperty.CamelAggregatedSize}");

        // outside an aggregate the parser's own suggestion is all there is
        msgs = SourceValidator.validateYamlSimple("""
                - from:
                    uri: timer:tick
                    steps:
                      - log:
                          message: "Collected ${size} messages"
                """, catalog);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("Unknown function: size").doesNotContain("CamelAggregatedSize");
    }

    @Test
    void functionOfALanguageOrComponentNotOnTheClasspathIsNotReported() {
        List<String> msgs = SourceValidator.validateYamlSimple("""
                - from:
                    uri: direct:start
                    steps:
                      - setHeader:
                          name: city
                          simple: "${jsonpath($.address.city)}"
                      - log:
                          message: "Agent replied: ${a2a:text}"
                """, catalog);
        assertThat(msgs).isEmpty();
    }

    @Test
    void thePredicateEipsComeFromTheCatalog() {
        // the same text is a predicate (error: no ${}) where the catalog marks the option asPredicate, and a
        // literal expression elsewhere
        List<String> msgs = SourceValidator.validateYamlSimple("""
                - from:
                    uri: timer:tick
                    steps:
                      - filter:
                          simple: "body contains 'critical'"
                      - setBody:
                          simple: "body contains 'critical'"
                """, catalog);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).startsWith("Line 5:").contains("Unexpected token body");
    }

    @Test
    void anExpressionOptionOfTheEipAboveIsAPredicateWhenTheCatalogSaysSo() {
        // handled, continued, retryWhile of onException and completionPredicate of aggregate are not EIPs but
        // expression options the catalog marks asPredicate; onWhen is its own model
        List<String> msgs = SourceValidator.validateYamlSimple("""
                - onException:
                    exception: java.lang.Exception
                    handled:
                      simple: "body contains 'a'"
                    retry-while:
                      simple: "body contains 'b'"
                    onWhen:
                      simple: "body contains 'c'"
                    steps:
                      - log: "x"
                - from:
                    uri: timer:tick
                    steps:
                      - aggregate:
                          aggregationStrategy: myStrategy
                          correlationExpression:
                            simple: "body contains 'd'"
                          completionPredicate:
                            simple: "body contains 'e'"
                          steps:
                            - log: "y"
                """, catalog);
        assertThat(msgs).hasSize(4);
        assertThat(msgs.get(0)).startsWith("Line 4:").contains("Unexpected token body");
        assertThat(msgs.get(1)).startsWith("Line 6:").contains("Unexpected token body");
        assertThat(msgs.get(2)).startsWith("Line 8:").contains("Unexpected token body");
        assertThat(msgs.get(3)).startsWith("Line 19:").contains("Unexpected token body");
    }

    @Test
    void theLoopExpressionIsAPredicateOnlyWithDoWhile() {
        List<String> msgs = SourceValidator.validateYamlSimple("""
                - from:
                    uri: timer:tick
                    steps:
                      - loop:
                          simple: "body contains 'a'"
                          steps:
                            - log: "x"
                      - loop:
                          doWhile: true
                          simple: "body contains 'b'"
                          steps:
                            - log: "y"
                      - loop:
                          simple: "body contains 'c'"
                          do-while: "true"
                          steps:
                            - log: "z"
                """, catalog);
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0)).startsWith("Line 10:").contains("Unexpected token body");
        assertThat(msgs.get(1)).startsWith("Line 14:").contains("Unexpected token body");
    }

    /** CAMEL-24883: the same expression as a block scalar gets the same hint; a > scalar is joined by spaces. */
    @Test
    void aBlockScalarExpressionIsCheckedToo() {
        List<String> msgs = SourceValidator.validateYamlSimple(
                """
                        - from:
                            uri: timer:tick
                            steps:
                              - setBody:
                                  expression:
                                    simple: |
                                      ${exchangeProperty.CamelTimerCounter} == 0 ? 'resource:file:order.json' : 'resource:file:other.json'
                              - setBody:
                                  simple:
                                    expression: >-
                                      ${body.size()} == 0
                                      ? ${null} : ${body[0]}
                              - log:
                                  message: |
                                    all fine: ${body}
                        """,
                catalog);
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0)).startsWith("Line 6:").contains("Simple has no top-level ternary");
        assertThat(msgs.get(1)).startsWith("Line 10:").contains("Simple has no top-level ternary");
    }

    @Test
    void aTopLevelTernaryInAnExpressionIsReported() {
        // the ? and : are outside ${...} so they are literal text: the route silently sets the body to
        // "1 == 0 ?  : ..." instead of a value, which is why the parser cannot report it (CAMEL-24826)
        List<String> msgs = SourceValidator.validateYamlSimple("""
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          simple: "${body.size()} == 0 ? ${null} : ${body[0]}"
                """, catalog);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).startsWith("Line 5:")
                .contains("Simple has no top-level ternary")
                .contains("the ? at index 20")
                .contains("inside one function");
    }

    @Test
    void aTernaryInsideOneFunctionIsAccepted() {
        // the form that does evaluate the operator, so it must not be reported
        List<String> msgs = SourceValidator.validateYamlSimple("""
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          simple: "${body.size() == 0 ? ${null} : ${body[0]}}"
                      - setBody:
                          simple: "${header.foo > 0 ? 1 : 0}"
                """, catalog);
        assertThat(msgs).isEmpty();
    }

    @Test
    void aTopLevelTernaryInAPredicateIsAccepted() {
        // a predicate has no literal text, so the predicate parser does evaluate a top-level ternary
        List<String> msgs = SourceValidator.validateYamlSimple("""
                - from:
                    uri: timer:tick
                    steps:
                      - filter:
                          simple: "${header.foo} > 0 ? 'yes' : 'no'"
                          steps:
                            - log: "x"
                """, catalog);
        assertThat(msgs).isEmpty();
    }

    @Test
    void proseWithAQuestionMarkAndAColonIsNotReported() {
        // literal text is the whole point of the top level; reporting these regressed twice already
        // (CAMEL-22904, CAMEL-23035), so the check needs a function present and skips a log message
        List<String> msgs = SourceValidator.validateYamlSimple("""
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          simple: "Is it ok ? yes : no"
                      - log: ">>> Message received from WebSocket Client : ${body}"
                      - log: "Shipped ${header.id} ? yes : no"
                """, catalog);
        assertThat(msgs).isEmpty();
    }

    @Test
    void aTernaryInsideAQuotedLiteralIsNotReported() {
        List<String> msgs = SourceValidator.validateYamlSimple("""
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          simple: "${body} and 'a ? b : c'"
                """, catalog);
        assertThat(msgs).isEmpty();
    }

    @Test
    void aSyntaxErrorIsReportedInsteadOfTheTernaryHint() {
        // one message per expression: the syntax error is the more actionable one
        List<String> msgs = SourceValidator.validateYamlSimple("""
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          simple: "${body ? ${null} : ${body}"
                """, catalog);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("Simple syntax error");
    }
}
