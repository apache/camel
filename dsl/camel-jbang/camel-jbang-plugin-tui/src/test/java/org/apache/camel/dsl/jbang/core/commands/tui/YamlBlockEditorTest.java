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
 * Tests for YAML block operations (CAMEL-24372).
 */
class YamlBlockEditorTest {

    private static final String SAMPLE = """
            - route:
                from:
                  uri: timer:tick
                  steps:
                    - to: log:info
                    - to: log:warn
            """;

    @Test
    void findBlockSelectsYamlListItemWithChildren() {
        List<String> lines = YamlBlockEditor.toLines(SAMPLE);
        int stepsRow = findLineContaining(lines, "- to: log:info");

        YamlBlockEditor.BlockRange block = YamlBlockEditor.findBlock(lines, stepsRow, true);

        assertThat(block.startRow()).isEqualTo(stepsRow);
        assertThat(lines.get(block.startRow())).contains("log:info");
        assertThat(block.endRow()).isGreaterThanOrEqualTo(block.startRow());
    }

    @Test
    void duplicateBlockInsertsCopyBelow() {
        List<String> lines = YamlBlockEditor.toLines(SAMPLE);
        int row = findLineContaining(lines, "- to: log:info");

        YamlBlockEditor.EditResult result = YamlBlockEditor.duplicateBlock(lines, row, true);

        assertThat(result.lines()).hasSize(lines.size() + (YamlBlockEditor.findBlock(lines, row, true).endRow()
                                                           - YamlBlockEditor.findBlock(lines, row, true).startRow() + 1));
        String text = YamlBlockEditor.fromLines(result.lines());
        assertThat(text.split("- to: log:info", -1)).hasSize(3);
    }

    @Test
    void deleteLineRemovesSingleLine() {
        List<String> lines = YamlBlockEditor.toLines(SAMPLE);
        int row = findLineContaining(lines, "- to: log:warn");
        int before = lines.size();

        YamlBlockEditor.EditResult result = YamlBlockEditor.deleteLine(lines, row);

        assertThat(result.lines()).hasSize(before - 1);
        assertThat(YamlBlockEditor.fromLines(result.lines())).doesNotContain("log:warn");
        assertThat(YamlBlockEditor.fromLines(result.lines())).contains("log:info");
        assertThat(YamlBlockEditor.fromLines(result.lines())).contains("timer:tick");
    }

    @Test
    void deleteBlockRemovesSelectedYamlBlock() {
        List<String> lines = YamlBlockEditor.toLines(SAMPLE);
        int row = findLineContaining(lines, "- to: log:warn");
        int before = lines.size();

        YamlBlockEditor.EditResult result = YamlBlockEditor.deleteBlock(lines, row, true);

        assertThat(result.lines()).hasSizeLessThan(before);
        assertThat(YamlBlockEditor.fromLines(result.lines())).doesNotContain("log:warn");
        assertThat(YamlBlockEditor.fromLines(result.lines())).contains("log:info");
    }

    @Test
    void moveBlockDownCursorFollowsMovedBlock() {
        List<String> lines = YamlBlockEditor.toLines(SAMPLE);
        int firstStep = findLineContaining(lines, "- to: log:info");

        YamlBlockEditor.EditResult result = YamlBlockEditor.moveBlockDown(lines, firstStep, true);

        assertThat(result).isNotNull();
        assertThat(result.lines().get(result.cursorRow())).contains("log:info");
        assertThat(YamlBlockEditor.fromLines(result.lines()).indexOf("log:warn"))
                .isLessThan(YamlBlockEditor.fromLines(result.lines()).indexOf("log:info"));
    }

    @Test
    void moveBlockDownSwapsWithNextSibling() {
        List<String> lines = YamlBlockEditor.toLines(SAMPLE);
        int firstStep = findLineContaining(lines, "- to: log:info");

        YamlBlockEditor.EditResult result = YamlBlockEditor.moveBlockDown(lines, firstStep, true);

        assertThat(result).isNotNull();
        String text = YamlBlockEditor.fromLines(result.lines());
        assertThat(text.indexOf("log:warn")).isLessThan(text.indexOf("log:info"));
    }

    @Test
    void moveBlockUpSwapsWithPreviousSibling() {
        List<String> lines = YamlBlockEditor.toLines(SAMPLE);
        int secondStep = findLineContaining(lines, "- to: log:warn");

        YamlBlockEditor.EditResult result = YamlBlockEditor.moveBlockUp(lines, secondStep, true);

        assertThat(result).isNotNull();
        String text = YamlBlockEditor.fromLines(result.lines());
        assertThat(text.indexOf("log:warn")).isLessThan(text.indexOf("log:info"));
    }

    @Test
    void moveBlockUpAtTopReturnsNull() {
        List<String> lines = YamlBlockEditor.toLines(SAMPLE);
        int row = findLineContaining(lines, "- route:");

        assertThat(YamlBlockEditor.moveBlockUp(lines, row, true)).isNull();
    }

    @Test
    void toggleCommentCommentsUncommentedLines() {
        List<String> lines = YamlBlockEditor.toLines("key: value\nother: x\n");
        YamlBlockEditor.BlockRange block = new YamlBlockEditor.BlockRange(0, 1);

        List<String> commented = YamlBlockEditor.toggleComment(lines, block);

        assertThat(commented.get(0)).startsWith("# ");
        assertThat(commented.get(1)).startsWith("# ");
    }

    @Test
    void toggleCommentUncommentsWhenAllLinesCommented() {
        List<String> lines = YamlBlockEditor.toLines("# key: value\n# other: x\n");
        YamlBlockEditor.BlockRange block = new YamlBlockEditor.BlockRange(0, 1);

        List<String> uncommented = YamlBlockEditor.toggleComment(lines, block);

        assertThat(uncommented.get(0)).doesNotStartWith("#");
        assertThat(uncommented.get(0)).contains("key: value");
        assertThat(uncommented.get(1)).contains("other: x");
    }

    @Test
    void nonYamlListModeUsesSingleLineBlocks() {
        List<String> lines = YamlBlockEditor.toLines("alpha\nbeta\ngamma\n");

        YamlBlockEditor.BlockRange block = YamlBlockEditor.findBlock(lines, 1, false);
        assertThat(block.startRow()).isEqualTo(1);
        assertThat(block.endRow()).isEqualTo(1);

        YamlBlockEditor.EditResult moved = YamlBlockEditor.moveBlockDown(lines, 0, false);
        assertThat(moved).isNotNull();
        assertThat(YamlBlockEditor.fromLines(moved.lines())).isEqualTo("beta\nalpha\ngamma\n");
    }

    @Test
    void toLinesAndFromLinesPreserveTrailingNewlineContent() {
        List<String> lines = YamlBlockEditor.toLines("a\nb");
        assertThat(lines).containsExactly("a", "b");
        assertThat(YamlBlockEditor.fromLines(lines)).isEqualTo("a\nb");
    }

    private static int findLineContaining(List<String> lines, String needle) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) {
                return i;
            }
        }
        throw new AssertionError("Line not found: " + needle);
    }
}
