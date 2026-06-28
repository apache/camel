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

import java.util.Arrays;
import java.util.List;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchHighlighterTest {

    // ---- applyHighlights tests ----

    @Test
    void applyHighlightsNoMatchReturnsOriginal() {
        SearchHighlighter sh = new SearchHighlighter();
        Line original = Line.from(Span.styled("no match here", Style.EMPTY));

        // No find or highlight patterns set, so should return original unchanged
        Line result = sh.applyHighlights(original, 0, -1);
        assertEquals(original.rawContent(), result.rawContent());
        assertEquals(original.spans().size(), result.spans().size());
    }

    @Test
    void applyHighlightsEmptyLineReturnsOriginal() {
        SearchHighlighter sh = new SearchHighlighter();
        Line empty = Line.from(Span.raw(""));

        Line result = sh.applyHighlights(empty, 0, -1);
        assertEquals("", result.rawContent());
    }

    @Test
    void applyHighlightsWithHighlightTerm() {
        SearchHighlighter sh = new SearchHighlighter();
        // Activate highlight mode and set a term
        sh.handleKeyEvent(KeyEvent.ofChar('h', KeyModifiers.NONE)); // activate highlight input
        // Type "err"
        sh.handleKeyEvent(KeyEvent.ofChar('e', KeyModifiers.NONE));
        sh.handleKeyEvent(KeyEvent.ofChar('r', KeyModifiers.NONE));
        sh.handleKeyEvent(KeyEvent.ofChar('r', KeyModifiers.NONE));
        // Confirm with Enter
        sh.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertTrue(sh.hasHighlightTerm());

        Line line = Line.from(Span.styled("An error occurred", Style.EMPTY));
        Line result = sh.applyHighlights(line, 0, -1);

        // The word "err" should be highlighted
        boolean foundHighlight = false;
        for (Span span : result.spans()) {
            if (span.content().equals("err")) {
                assertEquals(SearchHighlighter.HIGHLIGHT_STYLE, span.style());
                foundHighlight = true;
            }
        }
        assertTrue(foundHighlight, "Expected 'err' to be highlighted");
    }

    @Test
    void applyHighlightsWithFindCurrentLine() {
        SearchHighlighter sh = new SearchHighlighter();
        // Activate find mode
        sh.handleKeyEvent(KeyEvent.ofChar('/', KeyModifiers.NONE));
        sh.handleKeyEvent(KeyEvent.ofChar('f', KeyModifiers.NONE));
        sh.handleKeyEvent(KeyEvent.ofChar('o', KeyModifiers.NONE));
        sh.handleKeyEvent(KeyEvent.ofChar('o', KeyModifiers.NONE));
        sh.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        assertTrue(sh.hasFindTerm());

        Line line = Line.from(Span.styled("foo bar foo", Style.EMPTY));
        // lineIndex = 0, currentMatchLine = 0 means this IS the current match line
        Line result = sh.applyHighlights(line, 0, 0);

        // "foo" occurrences should use FIND_CURRENT_STYLE
        boolean foundCurrent = false;
        for (Span span : result.spans()) {
            if (span.content().equals("foo")) {
                assertEquals(SearchHighlighter.FIND_CURRENT_STYLE, span.style());
                foundCurrent = true;
            }
        }
        assertTrue(foundCurrent, "Expected 'foo' to use FIND_CURRENT_STYLE on current match line");
    }

    @Test
    void applyHighlightsWithFindNonCurrentLine() {
        SearchHighlighter sh = new SearchHighlighter();
        // Activate find mode
        sh.handleKeyEvent(KeyEvent.ofChar('/', KeyModifiers.NONE));
        sh.handleKeyEvent(KeyEvent.ofChar('b', KeyModifiers.NONE));
        sh.handleKeyEvent(KeyEvent.ofChar('a', KeyModifiers.NONE));
        sh.handleKeyEvent(KeyEvent.ofChar('r', KeyModifiers.NONE));
        sh.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        Line line = Line.from(Span.styled("foo bar baz", Style.EMPTY));
        // lineIndex = 5, currentMatchLine = 0 → NOT the current line
        Line result = sh.applyHighlights(line, 5, 0);

        boolean foundMatch = false;
        for (Span span : result.spans()) {
            if (span.content().equals("bar")) {
                assertEquals(SearchHighlighter.FIND_MATCH_STYLE, span.style());
                foundMatch = true;
            }
        }
        assertTrue(foundMatch, "Expected 'bar' to use FIND_MATCH_STYLE on non-current line");
    }

    // ---- buildFindMatches + navigation tests ----

    @Test
    void buildFindMatchesFindsMatchingLines() {
        SearchHighlighter sh = new SearchHighlighter();
        sh.handleKeyEvent(KeyEvent.ofChar('/', KeyModifiers.NONE));
        sh.handleKeyEvent(KeyEvent.ofChar('e', KeyModifiers.NONE));
        sh.handleKeyEvent(KeyEvent.ofChar('r', KeyModifiers.NONE));
        sh.handleKeyEvent(KeyEvent.ofChar('r', KeyModifiers.NONE));
        sh.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        List<String> lines = Arrays.asList(
                "line one",
                "error here",
                "line three",
                "another error");

        sh.buildFindMatches(lines);
        int first = sh.jumpToNearestMatch(0);
        assertEquals(1, first); // line index 1 has "error"

        sh.navigateToNextMatch();
        assertEquals(3, sh.currentMatchLine()); // line index 3 has "error"

        // Wrap around
        sh.navigateToNextMatch();
        assertEquals(1, sh.currentMatchLine());
    }

    @Test
    void navigatePrevMatchWrapsBackward() {
        SearchHighlighter sh = new SearchHighlighter();
        sh.handleKeyEvent(KeyEvent.ofChar('/', KeyModifiers.NONE));
        sh.handleKeyEvent(KeyEvent.ofChar('x', KeyModifiers.NONE));
        sh.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        List<String> lines = Arrays.asList("x1", "y2", "x3");
        sh.buildFindMatches(lines);
        sh.jumpToNearestMatch(0);

        // At match 0 (line 0), going prev should wrap to last match
        sh.navigateToPrevMatch();
        assertEquals(2, sh.currentMatchLine()); // last match
    }

    @Test
    void jumpToNearestMatchJumpsForward() {
        SearchHighlighter sh = new SearchHighlighter();
        sh.handleKeyEvent(KeyEvent.ofChar('/', KeyModifiers.NONE));
        sh.handleKeyEvent(KeyEvent.ofChar('z', KeyModifiers.NONE));
        sh.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));

        List<String> lines = Arrays.asList("aaa", "bbb", "zzz", "ddd");
        sh.buildFindMatches(lines);
        int nearest = sh.jumpToNearestMatch(1);
        assertEquals(2, nearest); // Jump to the nearest match at or after position 1
    }

    @Test
    void noFindPatternReturnsEmptyMatches() {
        SearchHighlighter sh = new SearchHighlighter();
        sh.buildFindMatches(Arrays.asList("a", "b", "c"));
        assertEquals(-1, sh.currentMatchLine());
    }

    // ---- handleEscape tests ----

    @Test
    void handleEscapeClearsFindTerm() {
        SearchHighlighter sh = new SearchHighlighter();
        sh.handleKeyEvent(KeyEvent.ofChar('/', KeyModifiers.NONE));
        sh.handleKeyEvent(KeyEvent.ofChar('x', KeyModifiers.NONE));
        sh.handleKeyEvent(KeyEvent.ofKey(KeyCode.ENTER, KeyModifiers.NONE));
        assertTrue(sh.hasFindTerm());

        boolean handled = sh.handleEscape();
        assertTrue(handled);
        assertFalse(sh.hasFindTerm());
    }

    @Test
    void handleEscapeDuringInputCancelsInput() {
        SearchHighlighter sh = new SearchHighlighter();
        sh.handleKeyEvent(KeyEvent.ofChar('/', KeyModifiers.NONE));
        assertTrue(sh.isSearchInputActive());

        boolean handled = sh.handleEscape();
        assertTrue(handled);
        assertFalse(sh.isSearchInputActive());
    }

    @Test
    void handleEscapeNoActiveSearchReturnsFalse() {
        SearchHighlighter sh = new SearchHighlighter();
        assertFalse(sh.handleEscape());
    }
}
