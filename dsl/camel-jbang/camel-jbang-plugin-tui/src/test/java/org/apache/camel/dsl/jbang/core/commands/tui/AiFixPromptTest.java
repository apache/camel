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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The source the fix with AI question (Shift+F8) quotes: the whole step of the line, with context, as in a diff.
 */
class AiFixPromptTest {

    // the canonical YAML form puts the endpoint of a step on the lines below "- to:"
    private static final String ROUTE = """
            - route:
                id: orders
                from:
                  uri: timer
                  parameters:
                    timerName: orders
                  steps:
                    - choice:
                        when:
                          - expression:
                              simple:
                                expression: "${body} > 100"
                            steps:
                              - to:
                                  uri: direct
                                  parameters:
                                    name: aproval
                        otherwise:
                          steps:
                            - log:
                                message: Order accepted
            """;

    private static final int TO_ROW = 13; // 0-based row of "- to:"

    @TempDir
    Path tempDir;

    @Test
    void quotesTheWholeStepWithContext() {
        String excerpt = AiFixPrompt.excerpt(ROUTE.lines().toList(), TO_ROW);

        assertThat(excerpt).isEqualTo("""
                  12 |                     expression: "${body} > 100"
                  13 |                 steps:
                > 14 |                   - to:
                  15 |                       uri: direct
                  16 |                       parameters:
                  17 |                         name: aproval
                  18 |             otherwise:
                  19 |               steps:
                """);
    }

    @Test
    void aBigStepIsCutAfterAFewLines() {
        List<String> lines = new ArrayList<>(List.of("steps:", "  - choice:"));
        for (int i = 0; i < 20; i++) {
            lines.add("      branch" + i + ": x");
        }

        String excerpt = AiFixPrompt.excerpt(lines, 1);

        // the line, MAX_STEP_LINES of its step and CONTEXT_LINES after it
        assertThat(excerpt.lines()).hasSize(1 + 1 + AiFixPrompt.MAX_STEP_LINES + AiFixPrompt.CONTEXT_LINES)
                .first().isEqualTo("   1 | steps:");
        assertThat(excerpt).contains(">  2 |   - choice:").contains("branch9").doesNotContain("branch10");
    }

    @Test
    void quotesTheCallsChainedOnTheLinesBelow() {
        List<String> lines = List.of(
                "    public void configure() {",
                "        from(\"timer:tick?peroid=1000\")",
                "            .log(\"tick\")",
                "            .to(\"seda:out\");",
                "    }",
                "}");

        String excerpt = AiFixPrompt.excerpt(lines, 1);

        assertThat(excerpt).isEqualTo("""
                  1 |     public void configure() {
                > 2 |         from("timer:tick?peroid=1000")
                  3 |             .log("tick")
                  4 |             .to("seda:out");
                  5 |     }
                  6 | }
                """);
    }

    @Test
    void noExcerptForABlankOrUnknownRow() {
        List<String> lines = List.of("a", "", "b");

        assertThat(AiFixPrompt.excerpt(lines, 1)).isNull();
        assertThat(AiFixPrompt.excerpt(lines, 3)).isNull();
        assertThat(AiFixPrompt.excerpt(lines, -1)).isNull();
    }

    @Test
    void theQuestionQuotesTheSourceOfTheFile() throws Exception {
        Path file = tempDir.resolve("orders.camel.yaml");
        Files.writeString(file, ROUTE, StandardCharsets.UTF_8);

        String q = AiFixPrompt.ofFailure(tempDir, file, TO_ROW + 1, "2 exchanges failed on this line", "- to:");

        assertThat(q).startsWith("Exchanges fail at runtime on line 14 of orders.camel.yaml: 2 exchanges failed")
                .contains("The source around it (> marks line 14):\n```\n")
                .contains("> 14 |                   - to:\n")
                .contains("  17 |                         name: aproval\n")
                .contains("```\nFind the cause")
                .doesNotContain("The line is:");
    }

    @Test
    void theChatShowsTheQuotedSourceAsACodeBlock() {
        String md = AiPanel.quoteQuestion("""
                Exchanges fail at runtime on line 14 of orders.camel.yaml: 2 exchanges failed
                The source around it (> marks line 14):
                ```
                > 14 |   - to:
                  15 |       uri: direct
                ```
                Find the cause""");

        // the question is quoted line by line, the code block is outside the quote (not rendered inside one)
        assertThat(md).isEqualTo("""
                > Exchanges fail at runtime on line 14 of orders.camel.yaml: 2 exchanges failed \s
                > The source around it (> marks line 14): \s

                ```
                > 14 |   - to:
                  15 |       uri: direct
                ```

                > Find the cause""");
    }

    @Test
    void theLineAloneWhenTheFileCannotBeRead() {
        String q = AiFixPrompt.of(tempDir, tempDir.resolve("gone.camel.yaml"), 3, "Unknown option", "    uri: timr:x");

        assertThat(q).contains("The line is: uri: timr:x\n");
    }
}
