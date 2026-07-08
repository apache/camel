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

import dev.tamboui.layout.Rect;
import dev.tamboui.markdown.MarkdownView;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hintLast;

class HelpOverlay {

    private boolean visible;
    private String markdown;
    private int scroll;

    boolean isVisible() {
        return visible;
    }

    void open(String markdown) {
        this.markdown = markdown;
        this.scroll = 0;
        this.visible = true;
    }

    void close() {
        this.visible = false;
        this.markdown = null;
        this.scroll = 0;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (!visible) {
            return false;
        }
        if (ke.isCancel() || ke.isChar('q') || ke.isChar('?') || ke.isKey(KeyCode.F1)) {
            close();
            return true;
        }
        if (ke.isUp() || ke.isChar('k')) {
            scroll = Math.max(0, scroll - 1);
            return true;
        }
        if (ke.isDown() || ke.isChar('j')) {
            scroll++;
            return true;
        }
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            scroll = Math.max(0, scroll - 10);
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            scroll += 10;
            return true;
        }
        return true;
    }

    void render(Frame frame, Rect area) {
        if (!visible || markdown == null) {
            return;
        }

        frame.renderWidget(Clear.INSTANCE, area);
        Rect popup = new Rect(area.left() + 2, area.top() + 1, area.width() - 4, area.height() - 2);

        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(" Help ")
                .titleBottom(Title.from(Line.from(
                        Span.styled(" F1/? ", Theme.hintKey()), Span.raw(" close "),
                        Span.styled(" " + TuiIcons.HINT_SCROLL + " ", Theme.hintKey()), Span.raw(" scroll "))))
                .build();

        MarkdownView view = MarkdownView.builder()
                .source(markdown)
                .scroll(scroll)
                .block(block)
                .build();
        frame.renderWidget(view, popup);
    }

    void renderFooter(List<Span> spans) {
        hint(spans, TuiIcons.HINT_SCROLL, "scroll");
        hintLast(spans, "Esc", "close");
    }
}
