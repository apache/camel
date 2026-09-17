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
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24805: the simple expressions an AI model writes in an answer are checked the way a file is, since the answer
 * is what the user copies and where a small model puts the operator inside the placeholder.
 */
class AnswerChecksTest {

    private static final CamelCatalog CATALOG = new DefaultCamelCatalog();

    @Test
    void theOperatorInsideThePlaceholderIsFoundInProseAndInlineCode() {
        String answer = "Use `${header.user ?: 'Guest'}` to fall back to a guest name, and log it with "
                        + "`${header.user} ?: 'Guest'` between placeholders is what Camel expects.";
        List<AnswerChecks.Problem> problems = AnswerChecks.checkSimple(answer, CATALOG);
        assertThat(problems).hasSize(1);
        assertThat(problems.get(0).expression()).isEqualTo("${header.user ?: 'Guest'}");
        assertThat(problems.get(0).error()).isNotBlank();
        assertThat(problems.get(0).message()).startsWith("${header.user ?: 'Guest'}: ");
        // the same mistake twice is one problem
        assertThat(AnswerChecks.checkSimple(answer + "\nAgain: ${header.user ?: 'Guest'}", CATALOG)).hasSize(1);
    }

    @Test
    void correctSimpleAndForeignPlaceholdersPass() {
        String answer = """
                The body is ${body}, the header ${header.user}, a random number ${random(1,10)} and today
                ${date:now:yyyy-MM-dd}; nested works too: ${header.${header.key}}.
                The Maven property ${camel-version} and the shell variable ${HOME} are not simple.
                A function of another language, ${jsonpath($.name)}, needs its dependency at runtime.
                A property placeholder used as a predicate, {{enabled}} && ${body} != null, is judged at runtime.
                """;
        assertThat(AnswerChecks.checkSimple(answer, CATALOG)).isEmpty();
        assertThat(AnswerChecks.checkSimple(null, CATALOG)).isEmpty();
        assertThat(AnswerChecks.checkSimple("no expressions here", CATALOG)).isEmpty();
    }

    @Test
    void yamlBlocksAreCheckedAsRoutesAndOtherBlocksAsText() {
        String answer = """
                Here is the route:

                ```yaml
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - setBody:
                            expression:
                              simple:
                                expression: "${header.user ?: 'Guest'}"
                        - log:
                            message: "Hello ${body}"
                ```

                And in Java:

                ```java
                from("timer:tick").setBody(simple("${header.user ?: 'Guest'}"));
                ```
                """;
        List<AnswerChecks.Problem> problems = AnswerChecks.checkSimple(answer, CATALOG);
        assertThat(problems).hasSize(2);
        assertThat(problems.get(0).expression()).isEqualTo("the YAML block");
        assertThat(problems.get(0).error()).startsWith("Line 8: Simple syntax error: ");
        assertThat(problems.get(1).expression()).isEqualTo("${header.user ?: 'Guest'}");

        String valid = """
                ```yaml
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - setBody:
                            expression:
                              simple:
                                expression: "${header.user} ?: 'Guest'"
                ```
                """;
        assertThat(AnswerChecks.checkSimple(valid, CATALOG)).isEmpty();
    }

    @Test
    void placeholdersAreBalancedAndStayOnOneLine() {
        assertThat(AnswerChecks.placeholders("a ${body} b ${header.${header.key}} c ${x"))
                .containsExactly("${body}", "${header.${header.key}}");
        assertThat(AnswerChecks.placeholders("${header.a\n}")).isEmpty();
        Set<String> roots = AnswerChecks.roots(CATALOG);
        assertThat(roots).contains("body", "header", "date", "random");
        assertThat(AnswerChecks.isSimple("${header.user}", roots)).isTrue();
        assertThat(AnswerChecks.isSimple("${date-with-timezone:now:UTC:yyyy}", roots)).isTrue();
        assertThat(AnswerChecks.isSimple("${camel-version}", roots)).isFalse();
        assertThat(AnswerChecks.isSimple("${}", roots)).isFalse();
    }

    @Test
    void theDefaultCatalogIsUsedWhenNoneIsGiven() {
        assertThat(AnswerChecks.checkSimple("${header.user ?: 'Guest'}")).hasSize(1);
        assertThat(AnswerChecks.checkSimple("${header.user} ?: 'Guest'")).isEmpty();
    }
}
