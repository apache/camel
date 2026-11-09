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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** CAMEL-26758: the two forms of exact text in a refusal, and when none is made. */
class RefusalFixesTest {

    private static final String CONTENT = """
            - route:
                from:
                  uri: direct:a
                  steps:
                    - filter:
                        simple: "${body} == 'a'
                    - to: log:x""";

    @Test
    void theLineFormKeepsTheIndentation() {
        LenientRepairs.Result r = RefusalFixes.apply(CONTENT, List.of(
                "line 6: the value opens a double quote and never closes it (a single quote inside a double-quoted value"
                                                                      + " needs no escape); write the line as simple: \"${body} == 'a'\""));
        assertThat(r).isNotNull();
        assertThat(r.content()).isEqualTo(CONTENT.replace("== 'a'\n", "== 'a'\"\n"));
        assertThat(r.applied()).containsExactly("line 6: written as the refusals said: simple: \"${body} == 'a'\"");
        assertThat(r.moved()).isFalse();
    }

    @Test
    void theBlockFormWritesTheLines() {
        LenientRepairs.Result r = RefusalFixes.apply(CONTENT, List.of(
                "line 5: something to fix. Write lines 5-6 as:\n        - filter:\n            simple: \"${body} == 'a'\""));
        assertThat(r).isNotNull();
        assertThat(r.content()).isEqualTo(CONTENT.replace("== 'a'\n", "== 'a'\"\n"));
        assertThat(r.applied()).containsExactly("lines 5-6: written as the refusals said");
    }

    @Test
    void oneLineWrittenAsSeveral() {
        LenientRepairs.Result r = RefusalFixes.apply("a:\n  b: 1\\n  c: 2\nd: 3", List.of(
                "line 2: the value of b is followed by \\n. Write line 2 as these 2 lines:\n  b: 1\n  c: 2"));
        assertThat(r).isNotNull();
        assertThat(r.content()).isEqualTo("a:\n  b: 1\n  c: 2\nd: 3");
        assertThat(r.applied()).containsExactly("line 2: written as the refusals said");
        assertThat(r.moved()).isTrue();
    }

    @Test
    void aProblemWithoutTextIsLeftToTheCheckThatFollows() {
        // the fix of line 6 is made; whether the problem of line 7 is gone, the write's check says
        LenientRepairs.Result r = RefusalFixes.apply(CONTENT, List.of(
                "line 6: the value opens a double quote and never closes it; write the line as simple: \"${body} == 'a'\"",
                "Line 7: to: property 'foo' is not defined in the schema"));
        assertThat(r).isNotNull();
        assertThat(r.content()).isEqualTo(CONTENT.replace("== 'a'\n", "== 'a'\"\n"));
        assertThat(r.applied()).hasSize(1);
        // no problem with text: nothing to make
        assertThat(RefusalFixes.apply(CONTENT, List.of("Line 7: to: property 'foo' is not defined in the schema")))
                .isNull();
        assertThat(RefusalFixes.apply(CONTENT, List.of())).isNull();
    }

    @Test
    void theLineToWriteIsOfTheLineItReplaces() {
        // another key, or a list item written as a key: a message that went on after the line to write
        assertThat(RefusalFixes.apply(CONTENT, List.of("line 6: x; write the line as message: \"${body}\""))).isNull();
        assertThat(RefusalFixes.apply(CONTENT, List.of("line 7: x; write the line as to: log:y"))).isNull();
        assertThat(RefusalFixes.apply(CONTENT, List.of("line 6: x; write the line as simple: \"a\" (see notes)\nmore")))
                .isNull();
        assertThat(RefusalFixes.apply(CONTENT, List.of("line 9: x; write the line as simple: \"a\""))).isNull();
    }

    @Test
    void twoFixesOfTheSameLinesAreNotMade() {
        assertThat(RefusalFixes.apply(CONTENT, List.of(
                "line 5: x. Write lines 5-6 as:\n        - filter:\n            simple: \"a\"",
                "line 6: y; write the line as simple: \"b\""))).isNull();
        // the same fix twice is made once
        String error = "line 6: y; write the line as simple: \"b\"";
        LenientRepairs.Result r = RefusalFixes.apply(CONTENT, List.of(error, error));
        assertThat(r).isNotNull();
        assertThat(r.applied()).hasSize(1);
    }

    @Test
    void aBlockShownInPartIsNotWritten() {
        assertThat(RefusalFixes.apply(CONTENT, List.of(
                "line 4: x. Write lines 4-6 as:\n      steps:\n        - filter:\n(and the 1 lines after them, two spaces"
                                                       + " further right too)")))
                .isNull();
        // a count that does not fit the lines
        assertThat(RefusalFixes.apply(CONTENT, List.of("line 5: x. Write lines 5-6 as:\n        - filter:"))).isNull();
    }
}
