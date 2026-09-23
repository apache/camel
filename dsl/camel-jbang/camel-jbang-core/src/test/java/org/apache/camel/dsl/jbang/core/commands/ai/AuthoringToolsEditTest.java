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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CAMEL-24909: camel_edit_file replaces one snippet, so a change to a file does not rewrite every line of it.
 */
class AuthoringToolsEditTest {

    private static final String ROUTE = """
            - route:
                id: stock-service
                from:
                  uri: direct:one-sku
                  steps:
                    - setBody:
                        expression:
                          jsonpath:
                            expression: "$[?(@.sku == '${header.sku}')]"
                    - log:
                        message: "one"

            - route:
                id: check-stock
                from:
                  uri: direct:check
                  steps:
                    - log:
                        message: "two"
            """;

    private static JsonObject edit(Path dir, String find, String replace) {
        return AuthoringTools.editFile(new ToolContext(), dir, "demo.camel.yaml", find, replace);
    }

    @Test
    void aSnippetIsReplacedAndTheRestOfTheFileIsUntouched(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("demo.camel.yaml"), ROUTE);

        JsonObject result = edit(dir, "message: \"two\"", "message: \"two and a half\"");

        assertThat(result.getString("status")).isEqualTo("edited");
        assertThat(result.getInteger("editedAtLine")).isEqualTo(19);
        String after = Files.readString(dir.resolve("demo.camel.yaml"));
        // the line the model would have corrupted by rewriting the whole file is exactly as it was
        assertThat(after).contains("expression: \"$[?(@.sku == '${header.sku}')]\"")
                .contains("message: \"two and a half\"")
                .doesNotContain("message: \"two\"\n");
        assertThat(after.lines().count()).isEqualTo(ROUTE.lines().count());
    }

    @Test
    void escapesLeftInTheSnippetAreReadAsTheNewlinesTheyStandFor(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("demo.camel.yaml"), ROUTE);

        // the model built the snippet as a JSON string and left its escapes in it
        JsonObject result = edit(dir,
                "- log:\\n                        message: \"two\"",
                "- log:\\n                        message: \"two and a half\"");

        assertThat(result.getString("status")).isEqualTo("edited");
        String after = Files.readString(dir.resolve("demo.camel.yaml"));
        assertThat(after).contains("message: \"two and a half\"").doesNotContain("\\n");
        assertThat(after.lines().count()).isEqualTo(ROUTE.lines().count());
    }

    @Test
    void aSnippetWrittenAtAnotherIndentationIsPutInAtTheFileOwn(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("demo.camel.yaml"), ROUTE);

        // the model wrote both the snippet and its replacement flat against the left margin
        JsonObject result = edit(dir, "- log:\n    message: \"two\"", "- log:\n    message: \"two\"\n- to:\n    uri: mock:end");

        assertThat(result.getString("status")).isEqualTo("edited");
        String after = Files.readString(dir.resolve("demo.camel.yaml"));
        assertThat(after).contains("        - log:\n            message: \"two\"\n"
                                   + "        - to:\n            uri: mock:end");
    }

    @Test
    void aTrimmedMatchInTheMiddleOfTheFileKeepsTheLineAfterItOnItsOwnLine(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("demo.camel.yaml"), ROUTE);

        // the first route, so there are lines after the window; the snippet is written flat (a trimmed match)
        JsonObject result = edit(dir, "- log:\n    message: \"one\"", "- log:\n    message: \"ONE\"");

        assertThat(result.getString("status")).isEqualTo("edited");
        String after = Files.readString(dir.resolve("demo.camel.yaml"));
        assertThat(after).contains("            message: \"ONE\"\n\n- route:");
        assertThat(after.lines().count()).isEqualTo(ROUTE.lines().count());
    }

    @Test
    void removingABlockWithATrimmedMatchLeavesNoEmptyLine(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("demo.camel.yaml"), ROUTE);

        JsonObject result = edit(dir, "- log:\n    message: \"one\"", "");

        assertThat(result.getString("status")).isEqualTo("edited");
        String after = Files.readString(dir.resolve("demo.camel.yaml"));
        assertThat(after.lines().count()).isEqualTo(ROUTE.lines().count() - 2);
    }

    @Test
    void aMissHandsBackThePartOfTheFileItWasAimingAt(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("demo.camel.yaml"), ROUTE);

        JsonObject missing = edit(dir, "uri: direct:nowhere", "x");

        assertThat(missing.getString("status")).isEqualTo("not-found");
        // this file is shorter than the window, so the window is the whole of it
        assertThat(missing.getString("fileWindow")).isEqualTo(ROUTE);
        assertThat(missing.getInteger("windowFromLine")).isEqualTo(1);
        assertThat(missing.getString("message"))
                .contains("fileWindow")
                .contains("rather than writing the whole file");
    }

    @Test
    void aMissInALongFileHandsBackOnlyTheLinesAroundThePlace(@TempDir Path dir) throws IOException {
        StringBuilder sb = new StringBuilder("- route:\n    from:\n      uri: direct:long\n      steps:\n");
        for (int i = 0; i < 200; i++) {
            sb.append("        - log:\n            message: \"step ").append(i).append("\"\n");
        }
        Files.writeString(dir.resolve("demo.camel.yaml"), sb.toString());

        // aimed at a line that is there, with a neighbour that is not
        JsonObject missing = edit(dir, "message: \"step 150\"\n            message: \"nowhere\"", "x");

        assertThat(missing.getString("status")).isEqualTo("not-found");
        String window = missing.getString("fileWindow");
        assertThat(window).contains("step 150").doesNotContain("step 100").doesNotContain("step 199");
        assertThat(window.lines().count()).isLessThanOrEqualTo(2L * 20 + 4);
        assertThat(missing.getInteger("windowFromLine")).isGreaterThan(1);
    }

    @Test
    void textThatIsNotThereOrOccursTwiceIsRefusedWithWhatToDo(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("demo.camel.yaml"), ROUTE);

        JsonObject missing = edit(dir, "uri: direct:nowhere", "x");
        assertThat(missing.getString("status")).isEqualTo("not-found");
        assertThat(missing.getString("message")).contains("copy the lines from the file");

        JsonObject twice = edit(dir, "- log:", "- log2:");
        assertThat(twice.getString("status")).isEqualTo("ambiguous");
        assertThat(twice.getInteger("occurrences")).isEqualTo(2);
        assertThat(twice.getString("message")).contains("occurs more than once");
        assertThat(Files.readString(dir.resolve("demo.camel.yaml"))).isEqualTo(ROUTE);
    }

    /** CAMEL-24909: the same lines with other indentation still name the place, when they name only one. */
    @Test
    void theIndentationOfTheSnippetMayDiffer(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("demo.camel.yaml"), ROUTE);

        // the snippet as a model composes it: the right lines, its own indentation
        JsonObject result = edit(dir, "- log:\n    message: \"two\"", "                    - log:\n"
                                                                      + "                        message: \"two and a half\"");

        assertThat(result.getString("status")).isEqualTo("edited");
        assertThat(Files.readString(dir.resolve("demo.camel.yaml"))).contains("message: \"two and a half\"")
                .contains("expression: \"$[?(@.sku == '${header.sku}')]\"");
    }

    /** CAMEL-24909: replacedLines counts the lines of the file that went, not the lines of a find with blanks. */
    @Test
    void replacedLinesCountsTheLinesOfTheFile(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("demo.camel.yaml"), ROUTE);

        JsonObject result = edit(dir, "- log:\n    message: \"two\"\n\n", "                    - log:\n"
                                                                          + "                        message: \"two!\"\n");

        assertThat(result.getString("status")).isEqualTo("edited");
        assertThat(result.getInteger("replacedLines")).isEqualTo(2);
    }

    /** CAMEL-24909: a miss shows the lines the file has there, so the next attempt copies them. */
    @Test
    void aMissShowsTheLinesTheFileHasThere(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("demo.camel.yaml"), ROUTE);

        JsonObject result = edit(dir, "- log:\n    message: \"one\"\n    id: nope", "x");

        assertThat(result.getString("status")).isEqualTo("not-found");
        assertThat(result.getString("nearest")).contains("message: \"one\"");
        assertThat(result.getString("message")).contains("which has there");
    }

    @Test
    void aResultThatDoesNotValidateIsNotWritten(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("demo.camel.yaml"), ROUTE);

        JsonObject result = edit(dir, "message: \"two\"", "mesage: \"two\"");

        assertThat(result.getString("status")).isEqualTo("invalid");
        assertThat(result.getString("message")).contains("The file was not changed");
        assertThat(Files.readString(dir.resolve("demo.camel.yaml"))).isEqualTo(ROUTE);
    }

    @Test
    void aFileThatDoesNotExistSaysToWriteIt(@TempDir Path dir) {
        assertThatThrownBy(() -> edit(dir, "x", "y"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("camel_write_file");
    }
}
