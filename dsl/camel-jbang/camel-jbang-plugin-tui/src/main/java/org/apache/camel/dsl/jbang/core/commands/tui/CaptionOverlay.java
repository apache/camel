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

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.paragraph.Paragraph;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hintLast;

class CaptionOverlay {

    private static final long CHAR_DELAY_MS = 50;
    private static final long HOLD_DURATION_MS = 3000;
    private static final long FADE_DURATION_MS = 1000;
    private static final long INLINE_IDLE_TIMEOUT_MS = 3000;

    private boolean inlineMode;
    private StringBuilder inlineBuffer;
    private long inlineLastKeystroke;

    private String captionText;
    private long captionStartTime;
    private long captionFullyTypedTime;
    private long captionAutoDismissTime;

    boolean isInlineMode() {
        return inlineMode;
    }

    boolean isCaptionVisible() {
        return captionText != null;
    }

    boolean isVisible() {
        return inlineMode || captionText != null;
    }

    void openInline() {
        inlineMode = true;
        inlineBuffer = new StringBuilder();
        inlineLastKeystroke = System.currentTimeMillis();
        captionText = "";
        captionStartTime = System.currentTimeMillis();
        captionFullyTypedTime = 0;
    }

    void showCaption(String text) {
        captionText = text.replace("\\n", "\n");
        captionStartTime = System.currentTimeMillis();
        captionFullyTypedTime = 0;
        captionAutoDismissTime = 0;
    }

    void showCaption(String text, int durationSeconds) {
        captionText = text.replace("\\n", "\n");
        captionStartTime = System.currentTimeMillis();
        captionFullyTypedTime = 0;
        if (durationSeconds > 0) {
            captionAutoDismissTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        } else {
            captionAutoDismissTime = 0;
        }
    }

    void close() {
        inlineMode = false;
        inlineBuffer = null;
        captionText = null;
        captionFullyTypedTime = 0;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (inlineMode) {
            if (ke.isCancel() || ke.isConfirm()) {
                finishInline();
            } else if (ke.isDeleteBackward()) {
                if (!inlineBuffer.isEmpty()) {
                    inlineBuffer.deleteCharAt(inlineBuffer.length() - 1);
                    captionText = inlineBuffer.toString();
                    inlineLastKeystroke = System.currentTimeMillis();
                }
            } else if (ke.code() == KeyCode.CHAR) {
                inlineBuffer.append(ke.string());
                captionText = inlineBuffer.toString();
                inlineLastKeystroke = System.currentTimeMillis();
            }
            return true;
        }
        if (captionText != null) {
            if (captionAutoDismissTime > 0) {
                return false;
            }
            captionText = null;
            captionFullyTypedTime = 0;
            return true;
        }
        return false;
    }

    void tick(long now) {
        if (inlineMode && !inlineBuffer.isEmpty()
                && now - inlineLastKeystroke > INLINE_IDLE_TIMEOUT_MS) {
            finishInline();
            return;
        }
        if (captionText == null || inlineMode) {
            return;
        }

        if (captionAutoDismissTime > 0 && now > captionAutoDismissTime) {
            captionText = null;
            captionFullyTypedTime = 0;
            captionAutoDismissTime = 0;
            return;
        }

        int totalChars = captionText.length();
        long elapsed = now - captionStartTime;
        int charsToShow = (int) (elapsed / CHAR_DELAY_MS);

        if (charsToShow >= totalChars && captionFullyTypedTime == 0) {
            captionFullyTypedTime = now;
        }
        if (captionAutoDismissTime == 0
                && captionFullyTypedTime > 0
                && now - captionFullyTypedTime > HOLD_DURATION_MS + FADE_DURATION_MS) {
            captionText = null;
            captionFullyTypedTime = 0;
        }
    }

    private void finishInline() {
        inlineMode = false;
        if (inlineBuffer.isEmpty()) {
            captionText = null;
            inlineBuffer = null;
            return;
        }
        captionText = inlineBuffer.toString().replace("\\n", "\n");
        inlineBuffer = null;
        captionFullyTypedTime = System.currentTimeMillis();
    }

    void render(Frame frame, Rect area) {
        if (inlineMode) {
            renderInline(frame, area);
            return;
        }
        if (captionText != null) {
            renderCaption(frame, area);
        }
    }

    void renderFooter(List<Span> spans) {
        if (inlineMode) {
            hint(spans, "Enter", "finish");
            hintLast(spans, "Esc", "cancel");
        } else if (captionText != null) {
            hintLast(spans, "any key", "dismiss");
        }
    }

    private void renderInline(Frame frame, Rect area) {
        Style style = Style.EMPTY.fg(Color.WHITE).bold();
        String text = inlineBuffer != null ? inlineBuffer.toString() : "";
        String display = text + "█";

        String[] parts = display.split("\\\\n", -1);
        List<Line> lines = new ArrayList<>();
        int maxWidth = 0;
        for (String part : parts) {
            lines.add(Line.from(Span.styled("  " + part + "  ", style)));
            maxWidth = Math.max(maxWidth, part.length() + 4);
        }

        int captionW = Math.min(Math.max(maxWidth, 6), area.width() - 2);
        int captionH = lines.size();
        int captionX = area.left() + Math.max(0, (area.width() - captionW) / 2);
        int captionY = area.top() + Math.max(0, (area.height() - captionH) / 2);
        Rect captionArea = new Rect(
                captionX, captionY, Math.min(captionW, area.width()),
                Math.min(captionH, area.height()));

        frame.renderWidget(Clear.INSTANCE, captionArea);
        frame.renderWidget(Paragraph.builder()
                .text(Text.from(lines.toArray(Line[]::new)))
                .build(), captionArea);
    }

    private void renderCaption(Frame frame, Rect area) {
        long now = System.currentTimeMillis();
        long elapsed = now - captionStartTime;
        int charsToShow = Math.min((int) (elapsed / CHAR_DELAY_MS), captionText.length());
        String visible = captionText.substring(0, charsToShow);

        Style style;
        if (captionFullyTypedTime == 0 || now - captionFullyTypedTime < HOLD_DURATION_MS) {
            style = Style.EMPTY.fg(Color.WHITE).bold();
        } else {
            style = Style.EMPTY.dim();
        }

        String[] parts = visible.split("\n", -1);
        List<Line> lines = new ArrayList<>();
        int maxWidth = 0;
        for (String part : parts) {
            lines.add(Line.from(Span.styled("  " + part + "  ", style)));
            maxWidth = Math.max(maxWidth, part.length() + 4);
        }

        int captionW = Math.min(maxWidth, area.width() - 2);
        int captionH = lines.size();
        int captionX = area.left() + Math.max(0, (area.width() - captionW) / 2);
        int captionY = area.top() + Math.max(0, (area.height() - captionH) / 2);
        Rect captionArea = new Rect(
                captionX, captionY, Math.min(captionW, area.width()),
                Math.min(captionH, area.height()));

        frame.renderWidget(Clear.INSTANCE, captionArea);
        frame.renderWidget(Paragraph.builder()
                .text(Text.from(lines.toArray(Line[]::new)))
                .build(), captionArea);
    }
}
