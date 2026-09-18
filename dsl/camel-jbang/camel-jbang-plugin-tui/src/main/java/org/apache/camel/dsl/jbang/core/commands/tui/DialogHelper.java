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

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;

/**
 * Shared building blocks for the small modal dialogs of the TUI so they all look and behave the same.
 * <p>
 * Conventions:
 * <ul>
 * <li>Small dialogs (confirm, single text input) are centered horizontally and sit in the upper third of the screen
 * ({@link #centered(Rect, int, int)}). Tall list browsers and menus stay anchored just below the tab bar instead.</li>
 * <li>Confirm dialogs use the warning color, or the error color for irreversible actions such as kill or delete. The
 * title, border and message all share that one accent color.</li>
 * <li>Key hints inside a dialog use the same badge style as the footer bar ({@link TuiHelper#hintLine}).</li>
 * <li>Every dialog clears the area beneath it before drawing.</li>
 * </ul>
 */
final class DialogHelper {

    /** Height of a confirm dialog: border, blank, message, blank, hints, border. */
    static final int CONFIRM_HEIGHT = 6;

    /** Height of a single-field input dialog: border, blank, field, blank, border. */
    static final int INPUT_HEIGHT = 5;

    private static final int CONFIRM_MIN_WIDTH = 34;
    private static final int INPUT_MIN_WIDTH = 50;
    private static final int INPUT_MAX_WIDTH = 64;

    private DialogHelper() {
    }

    /**
     * Computes the rectangle for a dialog of the given preferred size: centered horizontally and placed in the upper
     * third of {@code area} vertically, clamped so it always fits inside the area.
     */
    static Rect centered(Rect area, int popupW, int popupH) {
        int w = Math.max(1, Math.min(popupW, area.width()));
        int h = Math.max(1, Math.min(popupH, area.height()));
        int x = area.left() + Math.max(0, (area.width() - w) / 2);
        int y = area.top() + Math.max(0, (area.height() - h) / 3);
        return new Rect(x, y, w, h);
    }

    /**
     * Clamps a dialog width to {@code [min, max]} while keeping a two-cell margin to each side of {@code area}. When
     * the area is narrower than {@code min} the dialog shrinks to fit rather than overflowing.
     */
    static int clampWidth(Rect area, int min, int max) {
        int w = Math.min(max, area.width() - 4);
        w = Math.max(min, w);
        return Math.max(1, Math.min(w, area.width() - 2));
    }

    /**
     * Renders a confirm dialog that is accepted with Enter and cancelled with Esc.
     *
     * @param  title   dialog title without surrounding spaces, e.g. {@code "Confirm Quit"}
     * @param  message the question, e.g. {@code "Quit the TUI?"}
     * @param  danger  {@code true} for irreversible actions (kill, delete) to use the error color instead of warning
     * @return         the rectangle the dialog was drawn in, for mouse hit-testing
     */
    static Rect renderConfirm(Frame frame, Rect area, String title, String message, boolean danger) {
        return renderConfirm(frame, area, title, message, danger, "Enter", "confirm");
    }

    /**
     * Renders a confirm dialog with a custom accept key, for dialogs that deliberately do not accept Enter.
     *
     * @see #renderConfirm(Frame, Rect, String, String, boolean)
     */
    static Rect renderConfirm(
            Frame frame, Rect area, String title, String message, boolean danger,
            String acceptKey, String acceptLabel) {
        Style accent = danger ? Theme.error() : Theme.warning();
        String titleText = " " + title + " ";
        String msg = message.trim();
        int popupW = clampWidth(area, CONFIRM_MIN_WIDTH, Math.max(msg.length() + 6, titleText.length() + 4));
        Rect popup = centered(area, popupW, CONFIRM_HEIGHT);

        frame.renderWidget(Clear.INSTANCE, popup);
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .borderStyle(accent)
                .title(Title.from(Line.from(Span.styled(titleText, accent.bold()))))
                .build();
        frame.renderWidget(block, popup);
        Rect inner = block.inner(popup);
        frame.renderWidget(
                Paragraph.builder()
                        .centered()
                        .text(Text.from(
                                Line.empty(),
                                Line.from(Span.styled(msg, accent.bold())),
                                Line.empty(),
                                TuiHelper.hintLine(acceptKey, acceptLabel, "Esc", "cancel")))
                        .build(),
                inner);
        return popup;
    }

    /**
     * Renders a dialog with a single text field, e.g. for entering a new file name.
     *
     * @param  title       dialog title without surrounding spaces
     * @param  state       the text input state; the caret is painted so the user sees where they type
     * @param  placeholder dimmed text shown while the field is empty, may be {@code null}
     * @return             the rectangle the dialog was drawn in, for mouse hit-testing
     */
    static Rect renderInputDialog(Frame frame, Rect area, String title, TextInputState state, String placeholder) {
        int popupW = clampWidth(area, INPUT_MIN_WIDTH, INPUT_MAX_WIDTH);
        Rect popup = centered(area, popupW, INPUT_HEIGHT);

        frame.renderWidget(Clear.INSTANCE, popup);
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(" " + title + " ")
                .build();
        frame.renderWidget(block, popup);
        Rect inner = block.inner(popup);

        // Place the field on the middle row with a small horizontal margin, leaving a blank line above and below so
        // the dialog does not feel cramped.
        int pad = 2;
        int fieldW = Math.max(1, inner.width() - 2 * pad);
        int fieldY = inner.top() + Math.max(0, (inner.height() - 1) / 2);
        FormHelper.renderTextField(frame, new Rect(inner.left() + pad, fieldY, fieldW, 1), state, true, placeholder);
        return popup;
    }
}
