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

import java.util.ArrayList;
import java.util.List;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;

/**
 * Reusable fuzzy filter for TUI list views. Manages filter state and provides utilities for fuzzy matching and
 * highlighted rendering of matched characters.
 *
 * <p>
 * Usage: call {@link #appendChar(char)} / {@link #deleteChar()} as the user types. Use {@link #match(String)} to test
 * each item and get match positions. Use {@link #highlightLine(String, int[], Style, Style)} to render with matched
 * characters highlighted.
 */
class FuzzyFilter {

    private String filter = "";

    boolean hasFilter() {
        return !filter.isEmpty();
    }

    String filter() {
        return filter;
    }

    void appendChar(char c) {
        filter += Character.toLowerCase(c);
    }

    void deleteChar() {
        if (!filter.isEmpty()) {
            filter = filter.substring(0, filter.length() - 1);
        }
    }

    void clearFilter() {
        filter = "";
    }

    /**
     * Fuzzy-match the filter against the given text. Characters in the filter must appear in order (but not
     * consecutively) in the text. Case-insensitive.
     *
     * @return positions of matched characters in the text, or null if no match
     */
    int[] match(String text) {
        return fuzzyMatch(text, filter);
    }

    /**
     * Static fuzzy match: find each character of {@code pattern} in order within {@code text} (case-insensitive).
     *
     * @return array of match positions, or null if pattern doesn't match
     */
    static int[] fuzzyMatch(String text, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return new int[0];
        }
        String lowerText = text.toLowerCase();
        int[] positions = new int[pattern.length()];
        int textIdx = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            int found = lowerText.indexOf(c, textIdx);
            if (found < 0) {
                return null;
            }
            positions[i] = found;
            textIdx = found + 1;
        }
        return positions;
    }

    /**
     * Build a {@link Line} with matched character positions highlighted.
     *
     * @param  text           the full display text
     * @param  matchPositions positions of matched characters (from {@link #match(String)})
     * @param  normalStyle    style for non-matched characters
     * @param  matchStyle     style for matched characters
     * @return                a Line with interleaved normal and highlighted spans
     */
    static Line highlightLine(String text, int[] matchPositions, Style normalStyle, Style matchStyle) {
        if (matchPositions == null || matchPositions.length == 0) {
            return Line.from(Span.styled(text, normalStyle));
        }
        List<Span> spans = new ArrayList<>();
        int pos = 0;
        for (int matchIdx : matchPositions) {
            if (matchIdx < pos || matchIdx >= text.length()) {
                continue;
            }
            if (matchIdx > pos) {
                spans.add(Span.styled(text.substring(pos, matchIdx), normalStyle));
            }
            spans.add(Span.styled(text.substring(matchIdx, matchIdx + 1), matchStyle));
            pos = matchIdx + 1;
        }
        if (pos < text.length()) {
            spans.add(Span.styled(text.substring(pos), normalStyle));
        }
        return Line.from(spans);
    }
}
