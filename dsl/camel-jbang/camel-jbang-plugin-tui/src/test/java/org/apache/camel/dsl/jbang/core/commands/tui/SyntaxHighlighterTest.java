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

import dev.tamboui.style.Color;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyntaxHighlighterTest {

    @Test
    void detectsPropertiesLanguage() {
        assertEquals(SyntaxHighlighter.Language.PROPERTIES,
                SyntaxHighlighter.detectLanguage("application.properties"));
        // extension matching is case-insensitive
        assertEquals(SyntaxHighlighter.Language.PROPERTIES,
                SyntaxHighlighter.detectLanguage("Application.PROPERTIES"));
        // trailing line-number suffix is stripped before detection
        assertEquals(SyntaxHighlighter.Language.PROPERTIES,
                SyntaxHighlighter.detectLanguage("application.properties:12"));
    }

    @Test
    void colorsKeySeparatorAndValue() {
        Line line = SyntaxHighlighter.highlightLine("camel.main.name=demo", SyntaxHighlighter.Language.PROPERTIES);

        assertEquals(Color.YELLOW, fg(line, "camel.main.name"));
        assertEquals(Color.WHITE, fg(line, "="));
        assertEquals(Color.BLUE, fg(line, "demo"));
        assertRoundTrip(line, "camel.main.name=demo");
    }

    @Test
    void colorsColonSeparatorWithSpaces() {
        Line line = SyntaxHighlighter.highlightLine("server.port : 8080", SyntaxHighlighter.Language.PROPERTIES);

        assertEquals(Color.YELLOW, fg(line, "server.port"));
        assertEquals(Color.WHITE, fg(line, ":"));
        assertEquals(Color.BLUE, fg(line, "8080"));
        assertRoundTrip(line, "server.port : 8080");
    }

    @Test
    void colorsComments() {
        for (String comment : new String[] { "# a hash comment", "! a bang comment" }) {
            Line line = SyntaxHighlighter.highlightLine(comment, SyntaxHighlighter.Language.PROPERTIES);
            assertEquals(Color.LIGHT_BLUE, fg(line, comment));
            assertRoundTrip(line, comment);
        }
    }

    @Test
    void colorsKeyWithoutValue() {
        Line line = SyntaxHighlighter.highlightLine("enabled", SyntaxHighlighter.Language.PROPERTIES);
        assertEquals(Color.YELLOW, fg(line, "enabled"));
        assertRoundTrip(line, "enabled");
    }

    @Test
    void preservesLeadingIndentationUnstyled() {
        Line line = SyntaxHighlighter.highlightLine("  camel.x=1", SyntaxHighlighter.Language.PROPERTIES);
        // the indentation is emitted as a raw (unstyled) span
        assertEquals(null, fg(line, "  "));
        assertEquals(Color.YELLOW, fg(line, "camel.x"));
        assertEquals(Color.BLUE, fg(line, "1"));
        assertRoundTrip(line, "  camel.x=1");
    }

    /** Returns the foreground color of the first span whose content equals {@code content}, or null. */
    private static Color fg(Line line, String content) {
        for (Span span : line.spans()) {
            if (span.content().equals(content)) {
                return span.style().fg().orElse(null);
            }
        }
        throw new AssertionError("No span with content '" + content + "' in " + line.rawContent());
    }

    private static void assertRoundTrip(Line line, String expected) {
        StringBuilder sb = new StringBuilder();
        for (Span span : line.spans()) {
            sb.append(span.content());
        }
        assertEquals(expected, sb.toString());
    }
}
