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
import java.util.Optional;
import java.util.function.Function;

import dev.tamboui.layout.Alignment;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.paragraph.Paragraph;

/**
 * The input line of the AI panel: the question being typed (it may have several lines; the cursor moves and deletes a
 * character at a time, all the code points of an emoji at once), the earlier questions (Up and Down, and Ctrl+R to
 * search them backward), and TAB completion of slash commands and their arguments. The panel says what completes
 * ({@link Completion}); this class does the cycling.
 */
final class AiInputLine {

    /** What TAB completes: the token at {@code start} of the text, and the candidates for it. */
    record Completion(int start, String token, List<String> candidates) {
    }

    private final StringBuilder inputBuffer = new StringBuilder();

    private int cursorPos;

    private TuiPromptHistory promptHistory;

    // Reverse-i-search state (Ctrl+R)
    private boolean historySearchActive;

    private final StringBuilder searchTerm = new StringBuilder();

    private int searchIndex = -1;

    private String savedInput;

    // TAB completion cycle state. completionMatches holds the candidate command names for the active cycle;
    // completionSnapshot is the buffer text a TAB press last produced. The cycle continues only while the buffer still
    // equals that snapshot, so any other edit (typing, backspace, cursor move) transparently starts a fresh completion.
    private List<String> completionMatches;

    private int completionCycleIndex = -1;

    private String completionSnapshot;

    // Buffer offset of the token being completed: 1 for a command name (after the slash), or the start of the
    // first argument for commands that complete arguments (/model, /tools).
    private int completionStart = 1;

    /** A multi-line question shows up to this many rows; longer ones scroll to keep the cursor visible. */
    static final int MAX_INPUT_ROWS = 6;

    String text() {
        return inputBuffer.toString();
    }

    boolean isEmpty() {
        return inputBuffer.isEmpty();
    }

    void clear() {
        inputBuffer.setLength(0);
        cursorPos = 0;
    }

    boolean isSearching() {
        return historySearchActive;
    }

    String searchTerm() {
        return searchTerm.toString();
    }

    /** Whether Ctrl+R has earlier questions to search. */
    boolean canSearchHistory() {
        return promptHistory != null && promptHistory.isEnabled() && promptHistory.size() > 0;
    }

    /** Up: the previous question in place of the text; false when there is no question history. */
    boolean historyPrevious() {
        if (promptHistory == null || !promptHistory.isEnabled()) {
            return false;
        }
        promptHistory.previous(inputBuffer.toString()).ifPresent(this::replace);
        return true;
    }

    /** Down: the next question, back to what was typed; false when there is no question history. */
    boolean historyNext() {
        if (promptHistory == null || !promptHistory.isEnabled()) {
            return false;
        }
        promptHistory.next(inputBuffer.toString()).ifPresent(this::replace);
        return true;
    }

    /** Remembers a question asked, for Up and Ctrl+R. */
    void remember(String question) {
        if (promptHistory != null) {
            promptHistory.remember(question);
        }
    }

    void clearHistory() {
        if (promptHistory != null) {
            promptHistory.clear();
        }
    }

    TuiPromptHistory promptHistory() {
        return promptHistory;
    }

    void setPromptHistory(TuiPromptHistory history) {
        this.promptHistory = history;
    }

    /** Pasted text: into the search while it is open, else at the cursor (line breaks stay). */
    void paste(String normalized) {
        if (historySearchActive) {
            searchTerm.append(normalized.replace('\n', ' '));
            performSearch(searchIndex >= 0 ? searchIndex : promptHistory.size() - 1);
            return;
        }
        insert(normalized);
    }

    /** Backspace, Delete, Left, Right, Home, End, and typed text; returns whether the key was one of them. */
    boolean handleEditKey(KeyEvent ke) {
        // the cursor moves and deletes a character at a time: all the code points of an emoji, such as a family or a flag
        if (ke.isKey(KeyCode.BACKSPACE)) {
            if (cursorPos > 0) {
                if (promptHistory != null) {
                    promptHistory.resetNavigation();
                }
                int previous = TuiHelper.characterStart(inputBuffer, cursorPos);
                inputBuffer.delete(previous, cursorPos);
                cursorPos = previous;
            }
            return true;
        }
        if (ke.isKey(KeyCode.DELETE)) {
            if (cursorPos < inputBuffer.length()) {
                if (promptHistory != null) {
                    promptHistory.resetNavigation();
                }
                inputBuffer.delete(cursorPos, TuiHelper.characterEnd(inputBuffer, cursorPos));
            }
            return true;
        }
        if (ke.isKey(KeyCode.LEFT)) {
            if (cursorPos > 0) {
                cursorPos = TuiHelper.characterStart(inputBuffer, cursorPos);
            }
            return true;
        }
        if (ke.isKey(KeyCode.RIGHT)) {
            if (cursorPos < inputBuffer.length()) {
                cursorPos = TuiHelper.characterEnd(inputBuffer, cursorPos);
            }
            return true;
        }
        if (ke.isKey(KeyCode.HOME)) {
            cursorPos = lineStart(cursorPos);
            return true;
        }
        if (ke.isKey(KeyCode.END)) {
            cursorPos = lineEnd(cursorPos);
            return true;
        }
        String typed = FormHelper.typedText(ke);
        if (typed != null) {
            if (promptHistory != null) {
                promptHistory.resetNavigation();
            }
            inputBuffer.insert(cursorPos, typed);
            cursorPos += typed.length();
            return true;
        }
        return false;
    }

    // ---- Reverse-i-search (Ctrl+R) ----

    /** Ctrl+R: searches the earlier questions backward as the user types. */
    void enterSearch() {
        savedInput = inputBuffer.toString();
        historySearchActive = true;
        searchTerm.setLength(0);
        searchIndex = -1;
    }

    private void exitHistorySearch(boolean accept) {
        historySearchActive = false;
        if (accept && searchIndex >= 0) {
            replace(promptHistory.get(searchIndex));
        } else {
            replace(savedInput);
        }
        searchTerm.setLength(0);
        searchIndex = -1;
        savedInput = null;
    }

    private void performSearch(int fromIndex) {
        if (searchTerm.isEmpty()) {
            searchIndex = -1;
            return;
        }
        searchIndex = promptHistory.searchBackward(searchTerm.toString(), fromIndex);
    }

    /**
     * A key while the search is open; returns false when the key ends the search (accepting the match) and should be
     * handled as usual.
     */
    boolean handleSearchKey(KeyEvent ke) {
        if (ke.hasCtrl() && ke.isCharIgnoreCase('r')) {
            if (searchIndex > 0) {
                performSearch(searchIndex - 1);
            }
            return true;
        }
        if (ke.isKey(KeyCode.ENTER)) {
            exitHistorySearch(true);
            return true;
        }
        if (ke.isKey(KeyCode.ESCAPE) || ke.isCtrlC()) {
            exitHistorySearch(false);
            return true;
        }
        if (ke.isKey(KeyCode.BACKSPACE)) {
            if (searchTerm.isEmpty()) {
                exitHistorySearch(false);
            } else {
                searchTerm.setLength(TuiHelper.characterStart(searchTerm, searchTerm.length()));
                performSearch(promptHistory.size() - 1);
            }
            return true;
        }
        String typed = FormHelper.typedText(ke);
        if (typed != null) {
            searchTerm.append(typed);
            int from = searchIndex >= 0 ? searchIndex : promptHistory.size() - 1;
            performSearch(from);
            return true;
        }
        // Any other key: accept match and let the key be processed normally
        exitHistorySearch(true);
        return false;
    }

    /** Inserts text at the cursor. */
    void insert(String text) {
        if (promptHistory != null) {
            promptHistory.resetNavigation();
        }
        completionMatches = null;
        inputBuffer.insert(cursorPos, text);
        cursorPos += text.length();
    }

    private int lineStart(int pos) {
        int nl = inputBuffer.lastIndexOf("\n", Math.max(0, pos - 1));
        return pos > 0 && nl >= 0 && nl < pos ? nl + 1 : 0;
    }

    private int lineEnd(int pos) {
        int nl = inputBuffer.indexOf("\n", pos);
        return nl < 0 ? inputBuffer.length() : nl;
    }

    /** Moves the cursor to the previous (-1) or next (1) line of the input; false when there is no such line. */
    boolean moveCursorLine(int direction) {
        int start = lineStart(cursorPos);
        // the column on the screen, as in the source editor: the cursor goes straight up or down, never into an emoji
        int column = CharWidth.of(inputBuffer.substring(start, cursorPos));
        int targetStart;
        if (direction < 0) {
            if (start == 0) {
                return false;
            }
            targetStart = lineStart(start - 1);
        } else {
            int end = lineEnd(cursorPos);
            if (end >= inputBuffer.length()) {
                return false;
            }
            targetStart = end + 1;
        }
        int targetEnd = lineEnd(targetStart);
        cursorPos = targetStart + CharWidth.substringByWidth(inputBuffer.substring(targetStart, targetEnd), column).length();
        return true;
    }

    private List<String> inputLines() {
        return List.of(inputBuffer.toString().split("\n", -1));
    }

    /** Rows the input needs: one per line, capped so the conversation keeps most of the panel; one while searching. */
    int rows() {
        return historySearchActive ? 1 : Math.min(MAX_INPUT_ROWS, inputLines().size());
    }

    /**
     * Completes the slash command name at the cursor, or the first argument of {@code /model} (against the models the
     * provider reports) and {@code /tools} (auto, core, full). With multiple matches, TAB first fills in the longest
     * common prefix; once no further prefix can be added it cycles forward through the matches (wrapping so every match
     * is reachable with TAB alone). A single match is completed fully and a trailing space is appended. Shift+TAB
     * cycles backward.
     */
    void complete(boolean backward, Function<String, Completion> lookup) {
        String text = inputBuffer.toString();
        boolean continuing = text.equals(completionSnapshot) && completionMatches != null && completionMatches.size() > 1;
        if (continuing) {
            int size = completionMatches.size();
            if (completionCycleIndex < 0) {
                completionCycleIndex = backward ? size - 1 : 0;
            } else {
                completionCycleIndex = backward
                        ? (completionCycleIndex - 1 + size) % size
                        : (completionCycleIndex + 1) % size;
            }
            applyCompletionToken(completionMatches.get(completionCycleIndex), false);
            return;
        }

        Completion completion = lookup.apply(text);
        List<String> names = completion.candidates();
        String currentToken = completion.token();
        completionStart = completion.start();
        if (names.isEmpty()) {
            completionMatches = null;
            completionCycleIndex = -1;
            completionSnapshot = null;
            return;
        }
        if (names.size() == 1) {
            applyCompletionToken(names.get(0), true);
            // A single completion ends with a trailing space, so there is nothing left to cycle.
            completionMatches = null;
            completionCycleIndex = -1;
            completionSnapshot = null;
            return;
        }
        String prefix = longestCommonPrefix(names);
        completionMatches = names;
        if (prefix.length() > currentToken.length()) {
            applyCompletionToken(prefix, false);
            completionCycleIndex = -1;
        } else {
            completionCycleIndex = backward ? names.size() - 1 : 0;
            applyCompletionToken(names.get(completionCycleIndex), false);
        }
    }

    private void applyCompletionToken(String token, boolean trailingSpace) {
        inputBuffer.setLength(completionStart);
        inputBuffer.append(token);
        if (trailingSpace) {
            inputBuffer.append(' ');
        }
        cursorPos = inputBuffer.length();
        completionSnapshot = inputBuffer.toString();
    }

    private static String longestCommonPrefix(List<String> values) {
        String prefix = values.get(0);
        for (int i = 1; i < values.size() && !prefix.isEmpty(); i++) {
            String value = values.get(i);
            int max = Math.min(prefix.length(), value.length());
            int j = 0;
            while (j < max && prefix.charAt(j) == value.charAt(j)) {
                j++;
            }
            prefix = prefix.substring(0, j);
        }
        return prefix;
    }

    void reloadPromptHistory() {
        TuiSettings settings = TuiSettings.load();
        promptHistory = TuiPromptHistory.load(settings.getAiPromptHistoryLimit(), TuiHistoryFiles.aiPromptHistoryFile());
    }

    /** Replaces the text, the cursor at its end. */
    void replace(String text) {
        inputBuffer.setLength(0);
        if (text != null) {
            inputBuffer.append(text);
        }
        cursorPos = inputBuffer.length();
        completionMatches = null;
        completionCycleIndex = -1;
        completionSnapshot = null;
    }

    /**
     * Draws the input: the prompt, the lines around the cursor, and the argument placeholder of a slash command; or the
     * search while it is open. A locked input (the model is answering) is dimmed.
     */
    void render(Frame frame, Rect area, String prompt, boolean locked, Function<String, Optional<String>> placeholderFor) {
        if (historySearchActive) {
            renderSearchInput(frame, area);
            return;
        }
        if (area.height() < 1) {
            return;
        }
        List<String> lines = inputLines();
        // the line the cursor is on, and which lines are shown when there are more than rows
        int cursorLine = 0;
        int cursorColumn = cursorPos;
        for (int i = 0, offset = 0; i < lines.size(); i++) {
            int end = offset + lines.get(i).length();
            if (cursorPos <= end || i == lines.size() - 1) {
                cursorLine = i;
                cursorColumn = cursorPos - offset;
                break;
            }
            offset = end + 1;
        }
        int rows = Math.max(1, area.height());
        int firstLine = Math.max(0, Math.min(cursorLine - rows + 1, lines.size() - rows));
        int maxWidth = area.width() - prompt.length();
        if (maxWidth <= 0) {
            return;
        }
        List<Line> rendered = new ArrayList<>();
        for (int i = firstLine; i < Math.min(lines.size(), firstLine + rows); i++) {
            String text = lines.get(i);
            List<Span> spans = new ArrayList<>();
            String gutter = i == 0 ? prompt : " ".repeat(prompt.length());
            spans.add(Span.styled(gutter, Style.EMPTY.fg(Theme.accent()).bold()));
            if (locked) {
                spans.add(Span.styled(text, Style.EMPTY.dim()));
            } else if (i != cursorLine) {
                spans.add(Span.raw(CharWidth.substringByWidth(text, maxWidth)));
            } else {
                // the window of the line keeps the cursor and the character under it in view, measured in columns, and
                // cuts no character in half
                int cursorEnd = cursorColumn < text.length() ? TuiHelper.characterEnd(text, cursorColumn) : cursorColumn;
                int cursorWidth = Math.max(1, CharWidth.of(text.substring(cursorColumn, cursorEnd)));
                String lead = CharWidth.substringByWidthFromEnd(text.substring(0, cursorColumn), maxWidth - cursorWidth);
                int windowStart = cursorColumn - lead.length();
                String visible = CharWidth.substringByWidth(text.substring(windowStart), maxWidth);
                int cursorInWindow = cursorColumn - windowStart;
                if (cursorEnd > cursorColumn && cursorEnd - windowStart <= visible.length()) {
                    // the whole character under the cursor, all the code points of an emoji
                    spans.add(Span.raw(visible.substring(0, cursorInWindow)));
                    spans.add(Span.styled(visible.substring(cursorInWindow, cursorEnd - windowStart),
                            Style.EMPTY.reversed()));
                    spans.add(Span.raw(visible.substring(cursorEnd - windowStart)));
                } else {
                    spans.add(Span.raw(visible));
                    if (cursorInWindow == visible.length()) {
                        spans.add(Span.styled(" ", Style.EMPTY.reversed()));
                    }
                }
                if (lines.size() == 1 && cursorPos == inputBuffer.length()) {
                    Optional<String> placeholder = placeholderFor.apply(text);
                    if (placeholder.isPresent()) {
                        spans.add(Span.styled(" " + placeholder.get(), Style.EMPTY.dim()));
                    }
                }
            }
            rendered.add(Line.from(spans));
        }
        frame.renderWidget(Paragraph.from(new Text(rendered, Alignment.LEFT)), area);
    }

    private void renderSearchInput(Frame frame, Rect area) {
        boolean failing = !searchTerm.isEmpty() && searchIndex < 0;
        String label = failing ? "failing bck-i-search: " : "bck-i-search: ";
        String query = searchTerm.toString();

        List<Span> spans = new ArrayList<>();
        Style labelStyle = failing
                ? Theme.error()
                : Style.EMPTY.fg(Theme.accent());
        spans.add(Span.styled(label, labelStyle));
        spans.add(Span.raw(query));
        spans.add(Span.styled("_", Style.EMPTY.reversed()));

        if (searchIndex >= 0) {
            String matched = promptHistory.get(searchIndex);
            // the room after the label, the query, its caret and the two spaces before the preview
            int maxWidth = area.width() - label.length() - CharWidth.of(query) - 3;
            if (maxWidth > 4) {
                String preview = CharWidth.truncateWithEllipsis(matched, maxWidth, "…", CharWidth.TruncatePosition.END);
                spans.add(Span.styled("  " + preview, Style.EMPTY.dim()));
            }
        }

        frame.renderWidget(Paragraph.from(Line.from(spans)), area);
    }
}
