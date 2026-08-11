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

class EditDiffTest {

    @Test
    void unchangedLinesAreUnchanged() {
        List<String> lines = List.of("a", "b", "c");
        EditDiff.LineStatus[] statuses = EditDiff.diff(lines, lines);

        assertThat(statuses).containsExactly(
                EditDiff.LineStatus.UNCHANGED,
                EditDiff.LineStatus.UNCHANGED,
                EditDiff.LineStatus.UNCHANGED);
    }

    @Test
    void addedLineIsGreen() {
        List<String> original = List.of("a", "c");
        List<String> current = List.of("a", "b", "c");

        EditDiff.LineStatus[] statuses = EditDiff.diff(original, current);

        assertThat(statuses[0]).isEqualTo(EditDiff.LineStatus.UNCHANGED);
        assertThat(statuses[1]).isEqualTo(EditDiff.LineStatus.ADDED);
        assertThat(statuses[2]).isEqualTo(EditDiff.LineStatus.UNCHANGED);
    }

    @Test
    void modifiedLineIsYellow() {
        List<String> original = List.of("a", "b", "c");
        List<String> current = List.of("a", "B", "c");

        EditDiff.LineStatus[] statuses = EditDiff.diff(original, current);

        assertThat(statuses[0]).isEqualTo(EditDiff.LineStatus.UNCHANGED);
        assertThat(statuses[1]).isEqualTo(EditDiff.LineStatus.MODIFIED);
        assertThat(statuses[2]).isEqualTo(EditDiff.LineStatus.UNCHANGED);
    }

    @Test
    void addedAtEnd() {
        List<String> original = List.of("a", "b");
        List<String> current = List.of("a", "b", "c", "d");

        EditDiff.LineStatus[] statuses = EditDiff.diff(original, current);

        assertThat(statuses[0]).isEqualTo(EditDiff.LineStatus.UNCHANGED);
        assertThat(statuses[1]).isEqualTo(EditDiff.LineStatus.UNCHANGED);
        assertThat(statuses[2]).isEqualTo(EditDiff.LineStatus.ADDED);
        assertThat(statuses[3]).isEqualTo(EditDiff.LineStatus.ADDED);
    }

    @Test
    void emptyOriginalAllAdded() {
        List<String> original = List.of();
        List<String> current = List.of("a", "b");

        EditDiff.LineStatus[] statuses = EditDiff.diff(original, current);

        assertThat(statuses).containsExactly(
                EditDiff.LineStatus.ADDED,
                EditDiff.LineStatus.ADDED);
    }

    @Test
    void unifiedDiffShowsChanges() {
        List<String> original = List.of("a", "b", "c", "d", "e");
        List<String> current = List.of("a", "B", "c", "d", "e");

        List<EditDiff.DiffEntry> diff = EditDiff.unifiedDiff(original, current, 1);

        assertThat(diff).anyMatch(e -> e.type() == '-' && e.text().equals("b") && e.lineNum() == 2);
        assertThat(diff).anyMatch(e -> e.type() == '+' && e.text().equals("B") && e.lineNum() == 2);
        assertThat(diff).anyMatch(e -> e.type() == ' ' && e.text().equals("a"));
        assertThat(diff).anyMatch(e -> e.type() == ' ' && e.text().equals("c"));
        assertThat(diff).noneMatch(e -> e.text().equals("e"));
    }

    @Test
    void unifiedDiffAddedLines() {
        List<String> original = List.of("a", "c");
        List<String> current = List.of("a", "b", "c");

        List<EditDiff.DiffEntry> diff = EditDiff.unifiedDiff(original, current, 1);

        assertThat(diff).anyMatch(e -> e.type() == '+' && e.text().equals("b") && e.lineNum() == 2);
        assertThat(diff).anyMatch(e -> e.type() == ' ' && e.text().equals("a"));
        assertThat(diff).anyMatch(e -> e.type() == ' ' && e.text().equals("c"));
    }

    @Test
    void unifiedDiffLineNumbersMatchSourceFiles() {
        List<String> original = List.of("line1", "line2", "line3", "line4");
        List<String> current = List.of("line1", "CHANGED", "line3", "line4");

        List<EditDiff.DiffEntry> diff = EditDiff.unifiedDiff(original, current, 1);

        EditDiff.DiffEntry removed = diff.stream().filter(e -> e.type() == '-').findFirst().orElseThrow();
        assertThat(removed.lineNum()).isEqualTo(2);
        assertThat(removed.text()).isEqualTo("line2");

        EditDiff.DiffEntry added = diff.stream().filter(e -> e.type() == '+').findFirst().orElseThrow();
        assertThat(added.lineNum()).isEqualTo(2);
        assertThat(added.text()).isEqualTo("CHANGED");
    }

    @Test
    void identicalFilesEmptyDiff() {
        List<String> lines = List.of("a", "b", "c");
        List<EditDiff.DiffEntry> diff = EditDiff.unifiedDiff(lines, lines, 3);

        assertThat(diff).isEmpty();
    }
}
