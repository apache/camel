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
        assertThat(note.getString("note")).contains("2 times");
        // a page that was cut was not answered in full: the note says how to get the sections it left out
        assertThat(note.getString("note")).doesNotContain("in full").contains("docPage=<section title>");

        assertThat(calls.repeatOf(catalogDoc, sql).getInteger("timesAsked")).isEqualTo(4);
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
        assertThat(note.getString("note")).contains("change the line the error names");

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
