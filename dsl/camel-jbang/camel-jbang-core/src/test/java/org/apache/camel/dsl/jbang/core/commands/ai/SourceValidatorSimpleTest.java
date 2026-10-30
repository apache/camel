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
import org.apache.camel.catalog.LanguageValidationResult;
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
    void semanticDependencyDetectionToleratesSurroundingDiagnosticChanges() {
        CamelCatalog changedDiagnostic = new DefaultCamelCatalog() {
            @Override
            public LanguageValidationResult validateLanguageExpression(ClassLoader classLoader, String language, String text) {
                LanguageValidationResult result = new LanguageValidationResult(text);
                result.setError("Semantic evaluation requires camel-semantic; add the dependency to use this function");
                return result;
            }
        };
        List<String> msgs = SourceValidator.validateYamlSimple("""
                - from:
                    uri: direct:start
                    steps:
                      - setBody:
                          simple: "${semantic('department')}"
                """, changedDiagnostic);
        assertThat(msgs).isEmpty();
    }

    @Test
    void semanticExpressionsAndPredicatesDoNotRequireTheRuntimeDependency() {
        assertThat(catalog.validateLanguageExpression(null, "simple", "${semantic('department')}").getShortError())
                .contains("requires camel-semantic");
        List<String> msgs = SourceValidator.validateYamlSimple("""
                - from:
                    uri: direct:start
                    steps:
                      - setBody:
                          simple: "${semantic('department')}"
                      - filter:
                          simple: "${semantic('allowed')}"
                          steps:
                            - log: accepted
                """, catalog);
        assertThat(msgs).isEmpty();
    }

    @Test
    void invalidSemanticArgumentsAreStillReportedWithoutTheRuntimeDependency() {
        List<String> msgs = SourceValidator.validateYamlSimple("""
                - from:
                    uri: direct:start
                    steps:
                      - setBody:
                          simple: "${semantic(department)}"
                      - filter:
                          simple: "${semantic('allowed', 'other')}"
                          steps:
                            - log: accepted
                """, catalog);
        assertThat(msgs).hasSize(2);
        assertThat(msgs).allSatisfy(msg -> assertThat(msg)
                .contains("Simple syntax error", "Semantic requires one quoted evaluation name"));
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
                .endsWith("Write the whole ternary inside one function: ${body.size() == 0 ? ${null} : ${body[0]}}");
    }

    /**
     * The message shows the expression itself rewritten: with only an example, models wrote the same top-level ternary
     * up to 20 times in a row (a step of the local-model benchmark).
     */
    @Test
    void aTopLevelTernaryIsShownInsideOneFunction() {
        assertThat(rewritten("${header.CamelTimerCounter} == 1 ? 'order.json' : 'order-bad-email.json'"))
                .isEqualTo("${header.CamelTimerCounter == 1 ? 'order.json' : 'order-bad-email.json'}");
        // the literal text before the condition stays, and a bare value is quoted
        assertThat(rewritten("resource:file:${exchangeProperty.CamelTimerCounter} == 1 ? order.json : order-bad-email.json"))
                .isEqualTo("resource:file:${exchangeProperty.CamelTimerCounter == 1 ? 'order.json' : 'order-bad-email.json'}");
        assertThat(rewritten("Order ${header.id}: ${header.qty} > 5 ? big : small"))
                .isEqualTo("Order ${header.id}: ${header.qty > 5 ? 'big' : 'small'}");
        // a brace of the function's own text does not end it
        assertThat(rewritten("${body.find { it.x == 1 }} != null ? found : none"))
                .isEqualTo("${body.find { it.x == 1 } != null ? 'found' : 'none'}");
        // two functions compared: the condition starts with the first of them
        assertThat(rewritten("${header.a} == ${header.b} ? same : 2"))
                .isEqualTo("${header.a == ${header.b} ? 'same' : 2}");
        // a value with a single quote of its own is quoted with double quotes
        assertThat(rewritten("${header.vip} == true ? guest : O'Brien"))
                .isEqualTo("${header.vip == true ? 'guest' : \"O'Brien\"}");
        // no function before the ?: no rewrite, the message keeps its example
        assertThat(SimpleChecks.topLevelTernary("Total 5 > 3 ? ${header.a} : no"))
                .endsWith("as in ${body.size() == 0 ? ${null} : ${body[0]}}");
        // the rewritten expression is accepted
        assertThat(SourceValidator.validateYamlSimple("""
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          simple: "%s"
                """.formatted(rewritten("${header.CamelTimerCounter} == 1 ? 'order.json' : 'order-bad-email.json'")),
                catalog)).isEmpty();
        assertThat(SourceValidator.validateYamlSimple("""
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          simple: |-
                            %s
                """.formatted(rewritten("${header.vip} == true ? guest : O'Brien")),
                catalog)).isEmpty();
    }

    private static String rewritten(String text) {
        String message = SimpleChecks.topLevelTernary(text);
        assertThat(message).contains("Write the whole ternary inside one function: ");
        return message.substring(message.indexOf("inside one function: ") + "inside one function: ".length());
    }

    @Test
    void aTopLevelTernaryInAResourceLocationIsReported() {
        // the location of resource: is a template, so the ? and : outside ${...} are literal text and the route
        // fails with "Cannot load resource file:2 == 1 ? order.json : order-bad-email.json" (CAMEL-25485)
        List<String> msgs = SourceValidator.validateYamlSimple("""
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          simple: "resource:file:${exchangeProperty.CamelTimerCounter} == 1 ? order.json : order-bad-email.json"
                """, catalog);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).startsWith("Line 5:").contains("Simple has no top-level ternary")
                .endsWith("resource:file:${exchangeProperty.CamelTimerCounter == 1 ? 'order.json' : 'order-bad-email.json'}");
    }

    @Test
    void aResourceInBothValuesOfATopLevelTernaryGoesBeforeTheFunction() {
        // inside the function, 'resource:file:order.json' is only text: the body would be that text, not the file
        assertThat(rewritten(
                "${exchangeProperty.CamelTimerCounter} == 1 ? resource:file:order.json : resource:file:order-bad-email.json"))
                .isEqualTo("resource:file:${exchangeProperty.CamelTimerCounter == 1 ? 'order.json' : 'order-bad-email.json'}");
        // a value that is not a resource of the same scheme stays as it is
        assertThat(rewritten("${header.a} == 1 ? resource:file:order.json : resource:classpath:order.json"))
                .isEqualTo("${header.a == 1 ? 'resource:file:order.json' : 'resource:classpath:order.json'}");
        assertThat(rewritten("${header.a} == 1 ? resource:file:order.json : none"))
                .isEqualTo("${header.a == 1 ? 'resource:file:order.json' : 'none'}");
    }

    @Test
    void aResourceLocationWithoutATopLevelTernaryIsAccepted() {
        List<String> msgs = SourceValidator.validateYamlSimple(
                """
                        - from:
                            uri: timer:tick
                            steps:
                              - setBody:
                                  simple: "resource:file:${exchangeProperty.CamelTimerCounter == 1 ? 'order.json' : 'order-bad-email.json'}"
                              - setBody:
                                  simple: "resource:classpath:script.txt"
                        """,
                catalog);
        assertThat(msgs).isEmpty();
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

    /**
     * CAMEL-26401: without a space on both sides the elvis operator inside ${...} is part of the function, so the
     * expression is valid and evaluates to null; a model wrote this into its SQL and got a BadSqlGrammarException.
     */
    @Test
    void anElvisWithoutItsSpacesInsideAFunctionIsReported() {
        List<String> msgs = SourceValidator.validateYamlSimple("""
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          simple: "LIMIT :#${header.offset ?:0}, :#${header.limit ?:10}"
                """, catalog);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).startsWith("Line 5: `${header.offset ?:0}` reads a header named 'offset ?:0': the elvis"
                                           + " operator needs a space on both sides and goes after the function:"
                                           + " `${header.offset} ?: 0` (from Camel 4.24 also `${header.offset ?: 0}`")
                .endsWith("; the same for ${header.limit ?:10}");
    }

    @Test
    void everyFormOfAnElvisWithoutItsSpacesIsReported() {
        assertThat(SimpleChecks.elvisInsideFunction("${header.limit?: 10}", null))
                .startsWith("`${header.limit?: 10}` reads a header named 'limit?: 10'")
                .contains("`${header.limit} ?: 10`");
        // bare text is quoted, as the operator takes a quoted literal
        assertThat(SimpleChecks.elvisInsideFunction("${h?:x}", null))
                .startsWith("`${h?:x}` is one function, 'h?:x', not a value with a fallback")
                .contains("`${h} ?: 'x'`");
        assertThat(SimpleChecks.elvisInsideFunction("${exchangeProperty.retries ?:3}", null))
                .contains("reads an exchange property named 'retries ?:3'").contains("`${exchangeProperty.retries} ?: 3`");
        assertThat(SimpleChecks.elvisInsideFunction("${variable.name?:none}", null))
                .contains("reads a variable named 'name?:none'").contains("`${variable.name} ?: 'none'`");
        // a nested function is checked with its own text
        assertThat(SimpleChecks.elvisInsideFunction("${header.a ?: ${header.b?:1}}", null))
                .startsWith("`${header.b?:1}` reads a header named 'b?:1'");
    }

    @Test
    void anElvisInsideAFunctionWithItsSpacesIsAccepted() {
        List<String> msgs = SourceValidator.validateYamlSimple("""
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          simple: "LIMIT :#${header.offset ?: 0}, :#${header.limit ?: 10}"
                      - setBody:
                          simple: "${header.user} ?: 'Guest'"
                """, catalog);
        assertThat(msgs).isEmpty();
    }

    @Test
    void aQuestionMarkAndAColonThatAreNotAnElvisAreNotReported() {
        // in quotes, in arguments and brackets, the null-safe operator, a ternary, outside the braces
        for (String text : List.of("${body.replace('?:', '')}", "${header.a} == 'x?:y'", "${body.replaceAll('(?:a|b)', '')}",
                "${header[a?:b]}", "${body?.name}", "${header.n > 0 ? 'a' : 'b'}", "${header.a} ?:b", "What?: ${body}",
                "${header.a ?:}")) {
            assertThat(SimpleChecks.elvisInsideFunction(text, null)).as(text).isNull();
        }
    }

    @Test
    void anElvisWithoutItsSpacesIsReportedInAPredicateAndALogMessage() {
        List<String> msgs = SourceValidator.validateYamlSimple("""
                - from:
                    uri: timer:tick
                    steps:
                      - filter:
                          simple: "${header.n ?:0} > 5"
                          steps:
                            - log:
                                message: "Limit ${header.limit ?:10}"
                """, catalog);
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0)).startsWith("Line 5: `${header.n ?:0}` reads a header named 'n ?:0'");
        assertThat(msgs.get(1)).startsWith("Line 8: `${header.limit ?:10}` reads a header named 'limit ?:10'");
    }

    @Test
    void anElvisInsideAFunctionIsRewrittenForACamelBefore424() {
        // the catalog parses with the CLI's own Camel, which reads it; Camel 4.22 refuses it with "${ cannot accept ?:"
        CamelCatalog older = new DefaultCamelCatalog() {
            @Override
            public String getCatalogVersion() {
                return "4.22.1";
            }
        };
        List<String> msgs = SourceValidator.validateYamlSimple("""
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          simple: "LIMIT ${header.limit ?: 10}"
                """, older);
        assertThat(msgs).containsExactly("Line 5: `${header.limit ?: 10}` needs Camel 4.24 or later, where the elvis"
                                         + " operator may be written inside ${...} (here Camel 4.22.1): write it after the"
                                         + " function: `${header.limit} ?: 10`");
        // without the space the form inside the braces is not offered
        assertThat(SimpleChecks.elvisInsideFunction("${header.limit ?:10}", "4.22.1"))
                .endsWith("goes after the function: `${header.limit} ?: 10`");
        assertThat(SimpleChecks.elvisInsideFunction("${header.limit ?: 10}", "4.24.0-SNAPSHOT")).isNull();
        assertThat(SimpleChecks.elvisInsideFunction("${header.limit ?: 10}", "4.24.0")).isNull();
        assertThat(SimpleChecks.elvisInsideFunction("${header.limit ?: 10}", "5.0.0")).isNull();
    }
}
