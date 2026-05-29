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

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class ConfigurationTab implements MonitorTab {

    private static final Style KEY_STYLE = Style.EMPTY.fg(Color.CYAN);
    private static final Style VALUE_STYLE = Style.EMPTY.fg(Color.WHITE);
    private static final Style SECRET_STYLE = Style.EMPTY.fg(Color.DARK_GRAY);
    private static final Style SOURCE_STYLE = Style.EMPTY.dim();

    private final MonitorContext ctx;
    private final ScrollbarState scrollbarState = new ScrollbarState();
    private int scrollOffset;

    ConfigurationTab(MonitorContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (ke.isUp()) {
            scrollOffset = Math.max(0, scrollOffset - 1);
            return true;
        }
        if (ke.isDown()) {
            scrollOffset++;
            return true;
        }
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            scrollOffset = Math.max(0, scrollOffset - 20);
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            scrollOffset += 20;
            return true;
        }
        if (ke.isHome()) {
            scrollOffset = 0;
            return true;
        }
        if (ke.isEnd()) {
            scrollOffset = Integer.MAX_VALUE;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        return false;
    }

    @Override
    public void navigateUp() {
        scrollOffset = Math.max(0, scrollOffset - 1);
    }

    @Override
    public void navigateDown() {
        scrollOffset++;
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        List<ConfigProperty> props = info.configProperties;
        if (props.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled("  No configuration properties available.", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED)
                                    .title(" Configuration ").build())
                            .build(),
                    area);
            return;
        }

        // Find divider position: index of first non-camel property
        int dividerIndex = -1;
        for (int i = 0; i < props.size(); i++) {
            if (!props.get(i).key.startsWith("camel.")) {
                dividerIndex = i;
                break;
            }
        }
        boolean hasDivider = dividerIndex > 0 && dividerIndex < props.size();
        int totalLines = props.size() + (hasDivider ? 1 : 0);

        String title = String.format(" Configuration — %d properties ", props.size());
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(title)
                .build();
        Rect inner = block.inner(area);
        frame.renderWidget(block, area);

        if (inner.height() < 1 || inner.width() < 10) {
            return;
        }

        int visibleLines = inner.height();
        int maxScroll = Math.max(0, totalLines - visibleLines);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        // Compute max key length across visible properties for alignment
        int maxKeyLen = 0;
        for (ConfigProperty p : props) {
            maxKeyLen = Math.max(maxKeyLen, p.key.length());
        }
        int keyWidth = Math.min(maxKeyLen, inner.width() / 2);

        // Build visible lines, inserting divider at the right display position
        List<Line> lines = new ArrayList<>();
        int displayRow = 0;
        for (int i = 0; i < props.size() && lines.size() < visibleLines; i++) {
            if (hasDivider && i == dividerIndex) {
                if (displayRow >= scrollOffset) {
                    String divText = "─".repeat(Math.max(1, inner.width() - 2));
                    lines.add(Line.from(Span.styled(" " + divText, Style.EMPTY.dim())));
                }
                displayRow++;
                if (lines.size() >= visibleLines) {
                    break;
                }
            }
            if (displayRow >= scrollOffset) {
                lines.add(renderProperty(props.get(i), keyWidth));
            }
            displayRow++;
        }

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).build(), hChunks.get(0));

        if (totalLines > visibleLines) {
            scrollbarState
                    .contentLength(totalLines)
                    .viewportContentLength(visibleLines)
                    .position(scrollOffset);
            frame.renderStatefulWidget(Scrollbar.builder().build(), hChunks.get(1), scrollbarState);
        }
    }

    private Line renderProperty(ConfigProperty prop, int keyWidth) {
        String key = prop.key;
        if (key.length() > keyWidth) {
            key = key.substring(0, keyWidth - 1) + "…";
        } else {
            key = String.format("%-" + keyWidth + "s", key);
        }

        boolean secret = "xxxxxx".equals(prop.value);
        Style valStyle = secret ? SECRET_STYLE : VALUE_STYLE;
        String value = prop.value != null ? prop.value : "";

        List<Span> spans = new ArrayList<>();
        spans.add(Span.styled("  " + key + "  ", KEY_STYLE));
        spans.add(Span.styled(value, valStyle));
        if (prop.source != null && !prop.source.isEmpty()) {
            spans.add(Span.styled("  [" + prop.source + "]", SOURCE_STYLE));
        }

        return Line.from(spans);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, "↑↓", "scroll");
        hintLast(spans, "PgUp/Dn", "page");
    }

    @Override
    public SelectionContext getSelectionContext() {
        return null;
    }

    static int compareCamelFirst(ConfigProperty a, ConfigProperty b) {
        boolean aCamel = a.key.startsWith("camel.");
        boolean bCamel = b.key.startsWith("camel.");
        if (aCamel != bCamel) {
            return aCamel ? -1 : 1;
        }
        return a.key.compareToIgnoreCase(b.key);
    }

    static class ConfigProperty {
        String key;
        String value;
        String defaultValue;
        String source;
        String location;
    }
}
