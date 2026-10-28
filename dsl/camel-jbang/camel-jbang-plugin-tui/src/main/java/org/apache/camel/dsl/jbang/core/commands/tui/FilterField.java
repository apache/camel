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

import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextInputState;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hintLast;

/**
 * The {@code /} filter of a table or list tab: {@code /} opens a text field in the footer, Enter filters the rows with
 * what was typed, and Esc closes the field, or clears the filter once the field is closed. The tab keeps its rows and
 * filters them with {@link #term()}; it is called back when the term changes, to refilter and select again.
 * <p>
 * A tab with a filter field answers {@link MonitorTab#isTextInputActive()} with {@link #isEditing()}, and passes Esc
 * and pastes on with {@link #handleEscape()} and {@link #handlePaste(String)}: CamelMonitor handles Esc itself before
 * the tab gets keys, and without the first the global keys ({@code q}, the digits) act while the user types.
 */
final class FilterField {

    private final Runnable onTermChanged;
    private boolean editing;
    private TextInputState input = new TextInputState("");
    private String term;

    /**
     * @param onTermChanged called when the term is set or cleared, to refilter the rows and select the first
     */
    FilterField(Runnable onTermChanged) {
        this.onTermChanged = onTermChanged;
    }

    /** Whether the filter is being typed. */
    boolean isEditing() {
        return editing;
    }

    /** The term the rows are filtered with, or null when they are not filtered. */
    String term() {
        return term;
    }

    /**
     * Keys of the filter: {@code /} opens the field with the current term, and the open field takes every key (Enter
     * filters, Esc closes it, the others edit the text). Returns whether the key was taken.
     */
    boolean handleKey(KeyEvent ke) {
        if (editing) {
            if (ke.isKey(KeyCode.ESCAPE)) {
                editing = false;
                return true;
            }
            if (ke.isConfirm()) {
                String text = input.text().trim();
                term = text.isEmpty() ? null : text;
                editing = false;
                onTermChanged.run();
                return true;
            }
            FormHelper.handleTextInput(ke, input);
            return true;
        }
        if (ke.isChar('/')) {
            editing = true;
            input = new TextInputState(term != null ? term : "");
            return true;
        }
        return false;
    }

    /**
     * Esc: closes the field while the filter is typed (the term stays), else clears the term. Returns {@code false}
     * when there was neither, so Esc goes on to do what it does on the tab.
     */
    boolean handleEscape() {
        if (editing) {
            editing = false;
            return true;
        }
        if (term != null) {
            term = null;
            onTermChanged.run();
            return true;
        }
        return false;
    }

    /** Pastes into the field while the filter is typed; {@code false} when it is not. */
    boolean handlePaste(String text) {
        if (!editing) {
            return false;
        }
        FormHelper.handlePaste(text, input);
        return true;
    }

    /** Filters with the given term, as the tui_filter tool asks; a null or empty term shows every row. */
    void setTerm(String filter) {
        term = filter != null && !filter.isEmpty() ? filter : null;
        editing = false;
        onTermChanged.run();
    }

    /** Forgets the term and closes the field, as for another integration; does not call back. */
    void reset() {
        term = null;
        editing = false;
    }

    /**
     * The whole footer while the filter is typed: the field with its caret, Enter and Esc. Returns {@code false}, and
     * adds nothing, when it is not typed.
     */
    boolean renderEditingFooter(List<Span> spans) {
        if (!editing) {
            return false;
        }
        spans.add(Span.styled(" /", Theme.label().bold()));
        FormHelper.addCaretField(spans, input);
        spans.add(Span.raw("  "));
        hint(spans, "Enter", "filter");
        hintLast(spans, "Esc", "cancel");
        return true;
    }

    /** The {@code /} hint of the footer, or the term the rows are filtered with. */
    void renderHint(List<Span> spans) {
        if (term != null) {
            spans.add(Span.styled("  /", Theme.label().bold()));
            spans.add(Span.raw("\"" + term + "\"  "));
        } else {
            hint(spans, "/", "filter");
        }
    }

    /** The label of the Esc hint while the field is closed: Esc clears the term, or goes back. */
    String escapeLabel() {
        return term != null ? "clear" : "back";
    }
}
