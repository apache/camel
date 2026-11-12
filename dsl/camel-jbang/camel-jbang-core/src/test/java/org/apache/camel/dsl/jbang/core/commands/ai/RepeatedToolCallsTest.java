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
import java.util.Map;

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RepeatedToolCallsTest {

    /** What the note says after the next step, on the third identical call. */
    private static final String ASKED_3_TIMES = "You asked this 3 times now with the same arguments, and the answer is"
                                                + " the same each time: it is in your earlier tool result.";

    private final RepeatedToolCalls calls = new RepeatedToolCalls();
    private final ToolDescriptor catalogDoc = ToolRegistry.findTool("camel_catalog_doc");

    /** Simulates a successful call: repeatOf returns null, then we record the successful answer. */
    private void successfulCall(ToolDescriptor tool, Map<String, ?> args) {
        JsonObject note = calls.repeatOf(tool, args);
        assertThat(note).as("expected tool to run (repeatOf returned null)").isNull();
        calls.recordSuccessfulAnswer(tool, args);
    }

    @Test
    void theFirstTwoIdenticalCallsAreAnsweredInFullTheThirdGetsANote() {
        Map<String, String> sql = Map.of("name", "sql");
        successfulCall(catalogDoc, sql);
        successfulCall(catalogDoc, sql);

        JsonObject note = calls.repeatOf(catalogDoc, sql);
        assertThat(note).isNotNull();
        assertThat(note.getBoolean("repeated")).isTrue();
        assertThat(note.getString("tool")).isEqualTo("camel_catalog_doc");
        assertThat(note.getInteger("timesAsked")).isEqualTo(3);
        // the next step first, then the fact, with the count of timesAsked (CAMEL-26875)
        assertThat(note.getString("note"))
                .startsWith("Go on with the options the earlier answer gave: write the file with camel_write_file")
                .endsWith(ASKED_3_TIMES);
        // a page that was cut was not answered in full: the note says how to get the sections it left out
        assertThat(note.getString("note")).doesNotContain("in full").contains("docPage=<section title>");

        JsonObject fourth = calls.repeatOf(catalogDoc, sql);
        assertThat(fourth.getInteger("timesAsked")).isEqualTo(4);
        assertThat(fourth.getString("note")).contains("You asked this 4 times now");
    }

    /**
     * r32, a Citrus test step: camel_catalog_sample had no sample of 'send message' as an EIP, and the model asked the
     * same 11 or 12 more times, until its tool calls were spent, without writing the test. The note starts with the
     * next step: write the file, whose checks say what to change (CAMEL-26875).
     */
    @Test
    void aRepeatedSampleCallIsToldToWriteTheFile() {
        ToolDescriptor sample = ToolRegistry.findTool("camel_catalog_sample");
        Map<String, String> sendMessage = Map.of("name", "send message", "kind", "eip");
        successfulCall(sample, sendMessage);
        successfulCall(sample, sendMessage);

        JsonObject note = calls.repeatOf(sample, sendMessage);
        assertThat(note.getString("tool")).isEqualTo("camel_catalog_sample");
        assertThat(note.getInteger("timesAsked")).isEqualTo(3);
        assertThat(note.getString("note")).isEqualTo("Write the file now with camel_write_file: when the earlier answer"
                                                     + " had no sample, asking again gives none either. The write checks"
                                                     + " the file, and its answer says what to change (for a Citrus test:"
                                                     + " which actions it takes). " + ASKED_3_TIMES);
    }

    @Test
    void aRepeatedFindIsToldToUseAMatchOrAnotherTerm() {
        ToolDescriptor find = ToolRegistry.findTool("camel_catalog_find");
        Map<String, String> term = Map.of("term", "send message");
        successfulCall(find, term);
        successfulCall(find, term);

        assertThat(calls.repeatOf(find, term).getString("note"))
                .isEqualTo("Use a match from the earlier answer, or ask with another term; then write the file with"
                           + " camel_write_file. " + ASKED_3_TIMES);
    }

    @Test
    void aToolWithoutAHintIsToldToGoOnWithTheTask() {
        ToolDescriptor diagnose = ToolRegistry.findTool("camel_error_diagnose");
        assertThat(diagnose.repeatHint()).isNull();
        Map<String, String> error = Map.of("error", "No endpoint could be found for: xyz:test");
        successfulCall(diagnose, error);
        successfulCall(diagnose, error);

        assertThat(calls.repeatOf(diagnose, error).getString("note"))
                .isEqualTo("Go on with the next step of your task. " + ASKED_3_TIMES);
    }

    /** The note starts with the hint of the tool, and a sentence of its own follows: a hint is whole sentences. */
    @Test
    void everyHintIsWholeSentences() {
        for (ToolDescriptor tool : ToolRegistry.allTools()) {
            String hint = tool.repeatHint();
            if (hint != null) {
                assertThat(Character.isUpperCase(hint.charAt(0))).as(tool.name() + ": " + hint).isTrue();
                assertThat(hint).as(tool.name()).endsWith(".");
            }
        }
    }

    /**
     * r28 connect-stock-api step 2: body.getClass().getName() evaluated 9 times with the same arguments, up to the call
     * limit. The same expression on the same message gets the note from the third call, unless the expression reads
     * what changes from one call to the next.
     */
    @Test
    void anIdenticalEvaluationGetsTheNoteUnlessItsAnswerCanChange() {
        ToolDescriptor eval = ToolRegistry.findTool("camel_eval_expression");
        Map<String, String> args = Map.of("expression", "body.getClass().getName()", "language", "groovy", "body",
                "[{\"sku\": \"ABC\", \"qty\": 10}]");
        successfulCall(eval, args);
        successfulCall(eval, args);
        JsonObject note = calls.repeatOf(eval, args);
        assertThat(note).isNotNull();
        assertThat(note.getString("note")).isEqualTo("Change the expression or the message (body, headers, variables) to"
                                                     + " learn something new. " + ASKED_3_TIMES);
        // another message is another question
        assertThat(calls.repeatOf(eval, Map.of("expression", "body.getClass().getName()", "language", "groovy", "body",
                "[]"))).isNull();
        // a random value, the clock, an id, a property: asked again, the answer can change
        for (String expression : List.of("${random(1,10)}", "${date:now:yyyy-MM-dd}", "${exchangeId}",
                "${properties:stock.url}", "{{stock.url}}")) {
            Map<String, String> changing = Map.of("expression", expression);
            successfulCall(eval, changing);
            successfulCall(eval, changing);
            assertThat(calls.repeatOf(eval, changing)).as(expression).isNull();
        }
    }

    /** CAMEL-25640: a call that throws must not consume the retry budget. */
    @Test
    void aFailedCallDoesNotCountAgainstTheRetryBudget() {
        Map<String, String> sql = Map.of("name", "sql");
        // first call succeeds
        successfulCall(catalogDoc, sql);
        // second call fails (no recordSuccessfulAnswer)
        assertThat(calls.repeatOf(catalogDoc, sql)).isNull();
        // third call: should still answer in full because only one successful call was counted
        assertThat(calls.repeatOf(catalogDoc, sql)).isNull();
        calls.recordSuccessfulAnswer(catalogDoc, sql);
        // now the second successful answer has been recorded — next identical call gets the note
        assertThat(calls.repeatOf(catalogDoc, sql)).isNotNull();
    }

    @Test
    void differentArgumentsAreDifferentQuestions() {
        for (int i = 0; i < 3; i++) {
            successfulCall(catalogDoc, Map.of("name", "sql", "optionsFilter", "param" + i));
        }
    }

    @Test
    void anEmptyOptionalArgumentAsksTheSameAsLeavingItOut() {
        successfulCall(catalogDoc, Map.of("name", "sql"));
        successfulCall(catalogDoc, Map.of("name", "sql", "kind", ""));
        assertThat(calls.repeatOf(catalogDoc, Map.of("kind", " ", "name", "sql"))).isNotNull();
    }

    @Test
    void toolsThatReadFilesLogsOrTheRunningIntegrationAreNeverCut() {
        // a re-read after an edit and polling the log must keep working
        for (String name : List.of("camel_get_files", "camel_get_log", "camel_get_errors", "camel_validate_source")) {
            ToolDescriptor tool = ToolRegistry.findTool(name);
            assertThat(tool.isDeterministic()).as(name).isFalse();
            for (int i = 0; i < 5; i++) {
                assertThat(calls.repeatOf(tool, Map.of("file", "a.camel.yaml"))).as(name).isNull();
            }
        }
    }

    @Test
    void theCatalogLookupsAreDeterministic() {
        for (String name : List.of("camel_catalog_doc", "camel_catalog_find", "camel_catalog_sample",
                "camel_error_diagnose")) {
            assertThat(ToolRegistry.findTool(name).isDeterministic()).as(name).isTrue();
        }
    }

    @Test
    void aNewSessionStartsCountingAgain() {
        Map<String, String> sql = Map.of("name", "sql");
        successfulCall(catalogDoc, sql);
        successfulCall(catalogDoc, sql);
        calls.reset();
        assertThat(calls.repeatOf(catalogDoc, sql)).isNull();
    }

    /** CAMEL-25371: validating the same content is the same question; validating the file on disk is not. */
    @Test
    void validatingTheSameContentIsARepeat() {
        ToolDescriptor validate = ToolRegistry.findTool("camel_validate_source");
        Map<String, String> args = Map.of("file", "route.camel.yaml", "content", "- from:\n    uri: timer:x\n");
        successfulCall(validate, args);
        successfulCall(validate, args);
        JsonObject note = calls.repeatOf(validate, args);
        assertThat(note).isNotNull();
        assertThat(note.getString("note")).startsWith("Change the line the error names");

        Map<String, String> fromDisk = Map.of("file", "route.camel.yaml");
        for (int i = 0; i < 4; i++) {
            assertThat(calls.repeatOf(validate, fromDisk)).isNull();
        }
    }

    /**
     * CAMEL-25371: with a directory the checks read the other files of the project, so after a fix in another file the
     * same content gets another answer: always answered in full.
     */
    @Test
    void validatingContentAgainstADirectoryIsNotARepeat() {
        ToolDescriptor validate = ToolRegistry.findTool("camel_validate_source");
        Map<String, String> args = Map.of("directory", "/work/project", "file", "orders.camel.yaml",
                "content", "- from:\n    uri: timer:x\n    steps:\n      - to: direct:audit\n");
        for (int i = 0; i < 4; i++) {
            assertThat(calls.repeatOf(validate, args)).isNull();
        }
    }

    /**
     * CAMEL-25371: the source is compared exactly, as whitespace can decide the answer (a newline before
     * {@code <?xml}).
     */
    @Test
    void contentThatOnlyDiffersInWhitespaceIsAnotherQuestion() {
        ToolDescriptor validate = ToolRegistry.findTool("camel_validate_source");
        String xml = "<?xml version=\"1.0\"?>\n<routes/>\n";
        Map<String, String> withNewline = Map.of("file", "routes.camel.xml", "content", "\n" + xml);
        successfulCall(validate, withNewline);
        successfulCall(validate, withNewline);
        // the fix (the newline removed) is validated, not answered with the note
        assertThat(calls.repeatOf(validate, Map.of("file", "routes.camel.xml", "content", xml))).isNull();
        // and the key does not hold the source itself
        assertThat(RepeatedToolCalls.key("camel_validate_source", withNewline, "content")).doesNotContain("<routes/>");
    }
}
