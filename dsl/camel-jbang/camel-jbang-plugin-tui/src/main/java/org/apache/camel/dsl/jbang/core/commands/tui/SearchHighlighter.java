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
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextInputState;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hintLast;

/**
 * Shared find/highlight search logic used by LogTab and SourceViewer.
 */
class SearchHighlighter {

    static final Style FIND_CURRENT_STYLE = Theme.searchMatch();

    private boolean findInputActive;
    private boolean highlightInputActive;
    private TextInputState searchInputState = new TextInputState("");
    private String findTerm;
    private Pattern findPattern;
    private int findMatchIndex = -1;
    private List<Integer> findMatches = Collections.emptyList();
    private String highlightTerm;
    private Pattern highlightPattern;

    boolean handleKeyEvent(KeyEvent ke) {
        if (findInputActive || highlightInputActive) {
            return handleSearchInput(ke);
        }
        if (ke.isChar('/')) {
            findInputActive = true;
            searchInputState = new TextInputState("");
            return true;
        }
        if (ke.isChar('h')) {
            highlightInputActive = true;
            searchInputState = new TextInputState("");
            return true;
        }
        if (ke.isChar('n') && findTerm != null) {
            navigateToNextMatch();
            return true;
        }
        if (ke.isChar('N') && findTerm != null) {
            navigateToPrevMatch();
            return true;
        }
        return false;
    }

    private boolean handleSearchInput(KeyEvent ke) {
        if (ke.isKey(KeyCode.ESCAPE)) {
            findInputActive = false;
            highlightInputActive = false;
            return true;
        }
        if (ke.isConfirm()) {
            String text = searchInputState.text().trim();
            if (findInputActive) {
                if (text.isEmpty()) {
                    findTerm = null;
                    findPattern = null;
                    findMatches = Collections.emptyList();
                    findMatchIndex = -1;
                } else {
                    findTerm = text;
                    findPattern = Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE);
                }
                findInputActive = false;
            } else if (highlightInputActive) {
                if (text.isEmpty()) {
                    highlightTerm = null;
                    highlightPattern = null;
                } else {
                    highlightTerm = text;
                    highlightPattern = Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE);
                }
                highlightInputActive = false;
            }
            return true;
        }
        FormHelper.handleTextInput(ke, searchInputState);
        return true;
    }

    boolean handleEscape() {
        if (findInputActive || highlightInputActive) {
            findInputActive = false;
            highlightInputActive = false;
            return true;
        }
        if (findTerm != null) {
            findTerm = null;
            findPattern = null;
            findMatches = Collections.emptyList();
            findMatchIndex = -1;
            return true;
        }
        return false;
    }

    boolean isSearchInputActive() {
        return findInputActive || highlightInputActive;
    }

    void handlePaste(String text) {
        if (findInputActive || highlightInputActive) {
            FormHelper.handlePaste(text, searchInputState);
        }
    }

    void buildFindMatches(List<String> plainTextLines) {
        if (findPattern == null) {
            findMatches = Collections.emptyList();
            findMatchIndex = -1;
            return;
        }
        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i < plainTextLines.size(); i++) {
            if (findPattern.matcher(plainTextLines.get(i)).find()) {
                matches.add(i);
            }
        }
        findMatches = matches;
    }

    int jumpToNearestMatch(int currentPosition) {
        if (findMatches.isEmpty()) {
            findMatchIndex = -1;
            return currentPosition;
        }
        for (int i = 0; i < findMatches.size(); i++) {
            if (findMatches.get(i) >= currentPosition) {
                findMatchIndex = i;
                return findMatches.get(findMatchIndex);
            }
        }
        findMatchIndex = 0;
        return findMatches.get(0);
    }

    void navigateToNextMatch() {
        if (findMatches.isEmpty()) {
            return;
        }
        findMatchIndex = (findMatchIndex + 1) % findMatches.size();
    }

    void navigateToPrevMatch() {
        if (findMatches.isEmpty()) {
            return;
        }
        findMatchIndex = findMatchIndex <= 0 ? findMatches.size() - 1 : findMatchIndex - 1;
    }

    int currentMatchLine() {
        if (findMatchIndex >= 0 && findMatchIndex < findMatches.size()) {
            return findMatches.get(findMatchIndex);
        }
        return -1;
    }

    Line applyHighlights(Line line, int lineIndex, int currentMatchLine) {
        String fullText = line.rawContent();
        if (fullText.isEmpty()) {
            return line;
        }

        List<int[]> ranges = new ArrayList<>();
        List<Style> rangeStyles = new ArrayList<>();
        if (highlightPattern != null) {
            Matcher m = highlightPattern.matcher(fullText);
            while (m.find()) {
                ranges.add(new int[] { m.start(), m.end() });
                rangeStyles.add(Theme.searchMatch());
            }
        }
        if (findPattern != null) {
            boolean isCurrentLine = lineIndex == currentMatchLine;
            Matcher m = findPattern.matcher(fullText);
            while (m.find()) {
                ranges.add(new int[] { m.start(), m.end() });
                rangeStyles.add(isCurrentLine ? FIND_CURRENT_STYLE : Theme.searchMatch());
            }
        }
        if (ranges.isEmpty()) {
            return line;
        }

        // sort ranges by start position so the emit loop can assume ascending order
        Integer[] order = new Integer[ranges.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Integer.compare(ranges.get(a)[0], ranges.get(b)[0]));

        List<Span> original = line.spans();
        List<Span> result = new ArrayList<>();
        int charPos = 0;
        for (Span span : original) {
            String content = span.content();
            Style baseStyle = span.style();
            int spanStart = charPos;
            int spanEnd = charPos + content.length();
            int cursor = 0;
            for (int idx : order) {
                int matchStart = ranges.get(idx)[0];
                int matchEnd = ranges.get(idx)[1];
                if (matchEnd <= spanStart || matchStart >= spanEnd) {
                    continue;
                }
                int localStart = Math.max(0, matchStart - spanStart);
                int localEnd = Math.min(content.length(), matchEnd - spanStart);
                if (localStart < cursor) {
                    localStart = cursor;
                }
                if (localStart >= localEnd) {
                    continue;
                }
                if (localStart > cursor) {
                    result.add(Span.styled(content.substring(cursor, localStart), baseStyle));
                }
                result.add(Span.styled(content.substring(localStart, localEnd), rangeStyles.get(idx)));
                cursor = localEnd;
            }
            if (cursor < content.length()) {
                result.add(Span.styled(content.substring(cursor), baseStyle));
            }
            charPos = spanEnd;
        }
        return Line.from(result);
    }

    void renderFooterHints(List<Span> spans) {
        if (findInputActive) {
            spans.add(Span.styled(" /", Theme.hintKey()));
            spans.add(Span.raw(searchInputState.text() + "█  "));
            hint(spans, "Enter", "search");
            hintLast(spans, "Esc", "cancel");
            return;
        }
        if (highlightInputActive) {
            spans.add(Span.styled(" h:", Theme.hintKey()));
            spans.add(Span.raw(searchInputState.text() + "█  "));
            hint(spans, "Enter", "set");
            hintLast(spans, "Esc", "cancel");
            return;
        }
    }

    void renderFindStatus(List<Span> spans) {
        if (findTerm != null) {
            hint(spans, "Esc", "clear find");
            hint(spans, "n", "next");
            hint(spans, "N", "prev");
            String pos = findMatches.isEmpty()
                    ? "0/0"
                    : (findMatchIndex + 1) + "/" + findMatches.size();
            spans.add(Span.styled("  /", Theme.hintKey()));
            spans.add(Span.raw("\"" + findTerm + "\" [" + pos + "]  "));
        }
    }

    void renderSearchHints(List<Span> spans) {
        hint(spans, "/", "find");
        hint(spans, "h", "highlight" + (highlightTerm != null ? " [" + highlightTerm + "]" : ""));
    }

    boolean hasFindTerm() {
        return findTerm != null;
    }

    boolean hasHighlightTerm() {
        return highlightTerm != null;
    }

    void reset() {
        findInputActive = false;
        highlightInputActive = false;
        searchInputState = new TextInputState("");
        findTerm = null;
        findPattern = null;
        findMatchIndex = -1;
        findMatches = Collections.emptyList();
        highlightTerm = null;
        highlightPattern = null;
    }
}
