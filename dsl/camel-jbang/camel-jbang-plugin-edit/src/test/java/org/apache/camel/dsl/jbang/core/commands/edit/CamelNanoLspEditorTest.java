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
package org.apache.camel.dsl.jbang.core.commands.edit;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A completion of camel edit changes only the text it replaces (CAMEL-26933).
 */
class CamelNanoLspEditorTest {

    @Test
    void anInsertKeepsTheRestOfTheLine() {
        // without a text edit the rest of the line was dropped
        assertThat(CamelNanoLspEditor.insertAt("uri: \"timer:tick\"", 5, "x")).isEqualTo("uri: x\"timer:tick\"");
        assertThat(CamelNanoLspEditor.insertAt("uri", 10, "x")).isEqualTo("urix");
    }

    @Test
    void anEditOnOneLine() {
        List<String> lines = new ArrayList<>(List.of("- from:", "    uri: tim?period=1", "    steps:"));

        CamelNanoLspEditor.applyEdit(lines, 1, 9, 1, 12, "timer:tick");

        assertThat(lines).containsExactly("- from:", "    uri: timer:tick?period=1", "    steps:");
    }

    @Test
    void anEditOverSeveralLines() {
        // the new text was indexed with the positions of the old lines, which lost text or threw
        List<String> lines = new ArrayList<>(List.of("a: 1", "b: old", "c: old", "d: 4"));

        CamelNanoLspEditor.applyEdit(lines, 1, 3, 2, 6, "new\nc: new");

        assertThat(lines).containsExactly("a: 1", "b: new", "c: new", "d: 4");
    }

    @Test
    void anEditThatAddsLines() {
        List<String> lines = new ArrayList<>(List.of("steps:", "  - lo", "end"));

        CamelNanoLspEditor.applyEdit(lines, 1, 4, 1, 6, "log:\n      message: hello");

        assertThat(lines).containsExactly("steps:", "  - log:", "      message: hello", "end");
    }
}
