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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceViewerPasteIndentTest {

    @Test
    void noIndentPastedAtIndent8() {
        String paste = "- log:\n    message: Hello\n- to:\n    uri: kafka:orders";
        String result = SourceViewer.reindentBlock(paste, 8);
        assertThat(result).isEqualTo(
                "        - log:\n            message: Hello\n        - to:\n            uri: kafka:orders");
    }

    @Test
    void alreadyCorrectIndent() {
        String paste = "    - log:\n        message: Hello";
        String result = SourceViewer.reindentBlock(paste, 4);
        assertThat(result).isEqualTo(paste);
    }

    @Test
    void reduceIndent() {
        String paste = "        - log:\n            message: Hello";
        String result = SourceViewer.reindentBlock(paste, 4);
        assertThat(result).isEqualTo("    - log:\n        message: Hello");
    }

    @Test
    void preservesRelativeIndentation() {
        String paste = "- split:\n    expression:\n      simple: ${body}\n    steps:\n      - log:\n          message: part";
        String result = SourceViewer.reindentBlock(paste, 6);
        assertThat(result).isEqualTo(
                "      - split:\n          expression:\n            simple: ${body}\n          steps:\n            - log:\n                message: part");
    }

    @Test
    void blankLinesPreserved() {
        String paste = "- log:\n    message: Hello\n\n- to:\n    uri: direct:foo";
        String result = SourceViewer.reindentBlock(paste, 4);
        assertThat(result).isEqualTo(
                "    - log:\n        message: Hello\n\n    - to:\n        uri: direct:foo");
    }

    @Test
    void singleLineNoChange() {
        String paste = "message: Hello";
        String result = SourceViewer.reindentBlock(paste, 0);
        assertThat(result).isEqualTo("message: Hello");
    }

    @Test
    void singleLineIndented() {
        String paste = "message: Hello";
        String result = SourceViewer.reindentBlock(paste, 6);
        assertThat(result).isEqualTo("      message: Hello");
    }

    @Test
    void pasteWithExistingIndentShiftedUp() {
        String paste = "            brokers: localhost:9092\n            groupId: my-group";
        String result = SourceViewer.reindentBlock(paste, 8);
        assertThat(result).isEqualTo("        brokers: localhost:9092\n        groupId: my-group");
    }

    @Test
    void zeroTargetStripsIndent() {
        String paste = "    - log:\n        message: Hello";
        String result = SourceViewer.reindentBlock(paste, 0);
        assertThat(result).isEqualTo("- log:\n    message: Hello");
    }

    @Test
    void trailingNewlinePreserved() {
        String paste = "- log:\n    message: Hello\n";
        String result = SourceViewer.reindentBlock(paste, 4);
        assertThat(result).isEqualTo("    - log:\n        message: Hello\n");
    }
}
