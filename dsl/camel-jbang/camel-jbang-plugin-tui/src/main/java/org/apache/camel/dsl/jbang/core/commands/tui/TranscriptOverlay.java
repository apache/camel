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

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.markdown.MarkdownView;
import dev.tamboui.style.Style;
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
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hintLast;

/**
 * The conversation of a GenAI span over the Spans tab: the system instructions, the user and assistant messages, and
 * each tool call with its arguments and result, as Markdown that scrolls.
 */
final class TranscriptOverlay {

    static final String NO_CONTENT
            = "The messages are recorded at GenAI observability level full: set camel.aiObservability.level=full.";

    private final ScrollbarState scrollbarState = new ScrollbarState();
    private boolean visible;
    private String title;
    private String markdown;
    private int scroll;
    private int maxScroll;
    private int pageHeight = 10;

    boolean isVisible() {
        return visible;
    }

    /** Opens the conversation; an empty one says at which level the messages are recorded. */
    void open(String title, GenAiTranscript transcript) {
        this.title = title;
        this.markdown = transcript.isEmpty() ? "*" + NO_CONTENT + "*" : transcript.toMarkdown();
        this.scroll = 0;
        this.maxScroll = 0;
        this.visible = true;
    }

    void close() {
        visible = false;
        markdown = null;
    }

    /** The keys of the overlay: it takes all of them while it is open. */
    boolean handleKey(KeyEvent ke) {
        if (ke.isKey(KeyCode.ESCAPE) || ke.isChar('q') || ke.isChar('t')) {
            close();
        } else if (ke.isUp()) {
            scrollBy(-1);
        } else if (ke.isDown()) {
            scrollBy(1);
        } else if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            scrollBy(-pageHeight);
        } else if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN) || ke.isChar(' ')) {
            scrollBy(pageHeight);
        } else if (ke.isKey(KeyCode.HOME) || ke.isChar('g')) {
            scroll = 0;
        } else if (ke.isKey(KeyCode.END) || ke.isChar('G')) {
            scroll = maxScroll;
        }
        return true;
    }

    void scrollBy(int lines) {
        scroll = Math.max(0, Math.min(maxScroll, scroll + lines));
    }

    void render(Frame frame, Rect area) {
        if (!visible || area.width() < 10 || area.height() < 4) {
            return;
        }
        frame.renderWidget(Clear.INSTANCE, area);
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .borderStyle(Style.EMPTY.fg(Theme.accent()))
                .title(Title.from(Line.from(Span.styled(" " + title + " ", Style.EMPTY.fg(Theme.accent()).bold()))))
                .titleBottom(Title.from(Line.from(Span.styled(position(), Theme.muted()))).right())
                .build();
        frame.renderWidget(block, area);
        Rect inner = block.inner(area);
        List<Rect> columns = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);
        Rect content = columns.get(0);
        if (content.width() < 4 || content.height() < 1) {
            return;
        }
        MarkdownView.Builder view = MarkdownView.builder()
                .source(markdown)
                .styles(Theme.chatMarkdownStyles())
                .syntaxTheme(Theme.syntaxTheme());
        int totalHeight = view.build().computeHeight(content.width());
        pageHeight = Math.max(1, content.height() - 1);
        maxScroll = Math.max(0, totalHeight - content.height());
        scroll = Math.min(scroll, maxScroll);
        frame.renderWidget(view.scroll(scroll).build(), content);
        if (totalHeight > content.height()) {
            scrollbarState.contentLength(totalHeight).viewportContentLength(content.height()).position(scroll);
            frame.renderStatefulWidget(Scrollbar.builder().build(), columns.get(1), scrollbarState);
        }
    }

    private String position() {
        if (maxScroll == 0) {
            return " ";
        }
        return " " + (scroll * 100 / maxScroll) + "% ";
    }

    void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "close");
        hint(spans, "↑↓", "scroll");
        hint(spans, "PgUp/Dn", "page");
        hintLast(spans, "Home/End", "top/end");
    }

    /** The Markdown shown (tests). */
    String markdownForTesting() {
        return markdown;
    }
}
