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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The checks of the source editor by file type: errors and notes, and which errors keep a file from being saved.
 */
class SourceChecksTest {

    private static final String YAML = """
            - route:
                from:
                  uri: timer:tick
                  steps:
                    - to: direct:cheese
            """;

    @Test
    void anErrorAndANoteOnTheirLines() {
        SourceChecks checks = new SourceChecks();
        checks.setEndpointValidator(content -> List.of("Line 3: timer: unknown option"));
        checks.setNotes(content -> List.of("Line 5: no route consumes direct:cheese", "Line 3: also a note"));
        SourceChecks.Found found = checks.check(SourceChecks.Kind.YAML, YAML);
        assertThat(found.errors()).containsExactly("Line 3: timer: unknown option");
        assertThat(found.notes()).hasSize(2);
        // a line with an error and a note shows the error, and is not a note line
        assertThat(found.byLine(YAML)).containsEntry(2, "timer: unknown option")
                .containsEntry(4, "no route consumes direct:cheese");
        assertThat(found.noteLines(YAML)).containsOnly(4);
    }

    @Test
    void aSaveChecksTheSchemaAndLeavesOutTheNotesOfAFileItDoesNotSave() {
        SourceChecks checks = new SourceChecks();
        checks.setNotes(content -> List.of("Line 5: no route consumes direct:cheese"));
        String misspelled = YAML.replace("steps:", "stepz:");
        // while typing: no schema check, and the note
        assertThat(checks.check(SourceChecks.Kind.YAML, misspelled).errors()).isEmpty();
        assertThat(checks.check(SourceChecks.Kind.YAML, misspelled).notes()).hasSize(1);
        // on save: the schema error, which keeps the file from being saved, and no note
        SourceChecks.Found found = checks.checkOnSave(SourceChecks.Kind.YAML, misspelled);
        assertThat(found.errors()).isNotEmpty();
        assertThat(found.notes()).isEmpty();
        assertThat(found.blocksSave(SourceChecks.Kind.YAML)).isTrue();
        // a valid file is saved with its note
        SourceChecks.Found valid = checks.checkOnSave(SourceChecks.Kind.YAML, YAML);
        assertThat(valid.errors()).isEmpty();
        assertThat(valid.notes()).hasSize(1);
        assertThat(valid.blocksSave(SourceChecks.Kind.YAML)).isFalse();
    }

    @Test
    void theErrorsOfAJavaFileDoNotBlockItsSave() {
        SourceChecks checks = new SourceChecks();
        checks.setRouteValidator(content -> List.of("Line 1: direct: unknown option"));
        SourceChecks.Found java = checks.checkOnSave(SourceChecks.Kind.OTHER, "from(\"direct:a?foo=1\")");
        assertThat(java.errors()).hasSize(1);
        assertThat(java.blocksSave(SourceChecks.Kind.OTHER)).isFalse();
        assertThat(checks.checkOnSave(SourceChecks.Kind.XML, "<from uri=\"direct:a?foo=1\"/>")
                .blocksSave(SourceChecks.Kind.XML)).isTrue();
        // a Java file is saved with its errors, so its notes are said on save too; an XML file's errors keep them out
        checks.setNotes(content -> List.of("Line 1: no route consumes direct:a"));
        assertThat(checks.checkOnSave(SourceChecks.Kind.OTHER, "x").notes()).hasSize(1);
        assertThat(checks.checkOnSave(SourceChecks.Kind.XML, "x").notes()).isEmpty();
        assertThat(checks.check(SourceChecks.Kind.XML, "x").notes()).hasSize(1);
        // the checks of the file forgotten: nothing found
        checks.clear();
        assertThat(checks.check(SourceChecks.Kind.OTHER, "x")).isSameAs(SourceChecks.Found.NONE);
    }

    @Test
    void aPropertiesFileIsCheckedLineByLine() {
        SourceChecks checks = new SourceChecks();
        checks.setPropertiesValidator(line -> line.startsWith("camel.bad") ? "unknown option" : null);
        SourceChecks.Found found = checks.check(SourceChecks.Kind.PROPERTIES, "camel.main.name=x\ncamel.bad=1\n");
        assertThat(found.byLine("")).containsOnlyKeys(1);
        assertThat(found.blocksSave(SourceChecks.Kind.PROPERTIES)).isTrue();
    }
}
