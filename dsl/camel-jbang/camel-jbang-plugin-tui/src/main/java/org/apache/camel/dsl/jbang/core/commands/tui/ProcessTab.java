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
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class ProcessTab extends AbstractTab {

    private static final int MOUSE_SCROLL_LINES = 3;

    private boolean wrap;
    private int scroll;
    private final ScrollbarState scrollState = new ScrollbarState();

    ProcessTab(MonitorContext ctx) {
        super(ctx);
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (ke.isChar('w')) {
            wrap = !wrap;
            scroll = 0;
            return true;
        }
        if (ke.isPageUp()) {
            scroll = Math.max(0, scroll - 5);
            return true;
        }
        if (ke.isPageDown()) {
            scroll += 5;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            scroll = Math.max(0, scroll - MOUSE_SCROLL_LINES);
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            scroll += MOUSE_SCROLL_LINES;
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
        scroll = Math.max(0, scroll - 1);
    }

    @Override
    public void navigateDown() {
        scroll += 1;
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        List<Line> lines = new ArrayList<>();

        addField(lines, "PID", info.pid);
        addField(lines, "Name", info.name);
        addField(lines, "Camel", info.camelVersion);

        String platform = info.platform;
        if (platform != null && info.platformVersion != null) {
            platform = platform + " " + info.platformVersion;
        }
        addField(lines, "Platform", platform);
        addField(lines, "Profile", info.profile);

        String java = info.javaVersion;
        if (java != null) {
            StringBuilder sb = new StringBuilder(java);
            if (info.javaVendor != null) {
                sb.append(" (").append(info.javaVendor);
                if (info.javaVmName != null) {
                    sb.append(", ").append(info.javaVmName);
                }
                sb.append(")");
            }
            java = sb.toString();
        }
        addField(lines, "Java", java);
        addField(lines, "Directory", info.directory);
        addField(lines, "Uptime", info.ago);

        lines.add(Line.from(Span.raw("")));

        String cmdLine = getCommandLine(info.pid);
        if (cmdLine != null) {
            lines.add(Line.from(
                    Span.styled("  Command Line", Style.EMPTY.fg(Color.CYAN).bold())));
            lines.add(Line.from(Span.raw("")));
            if (wrap) {
                lines.add(Line.from(Span.raw("  " + cmdLine)));
            } else {
                for (String part : splitCommandLine(cmdLine)) {
                    lines.add(Line.from(Span.raw("  " + part)));
                }
            }
        }

        Block block = Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Process ").build();
        frame.renderWidget(block, area);

        Rect inner = block.inner(area);
        int visibleHeight = Math.max(1, inner.height());
        int visibleWidth = Math.max(1, inner.width() - 1);
        int contentHeight;
        if (wrap) {
            contentHeight = 0;
            for (Line l : lines) {
                int w = l.width();
                contentHeight += Math.max(1, (w + visibleWidth - 1) / visibleWidth);
            }
            contentHeight += visibleHeight;
        } else {
            contentHeight = lines.size();
        }
        int maxScroll = Math.max(0, contentHeight - visibleHeight);
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        Paragraph paragraph = Paragraph.builder()
                .text(Text.from(lines))
                .overflow(wrap ? Overflow.WRAP_WORD : Overflow.CLIP)
                .scroll(scroll)
                .build();
        frame.renderWidget(paragraph, hChunks.get(0));

        if (contentHeight > visibleHeight) {
            scrollState.contentLength(contentHeight);
            scrollState.viewportContentLength(visibleHeight);
            scrollState.position(scroll);
            frame.renderStatefulWidget(
                    Scrollbar.builder().build(),
                    hChunks.get(1), scrollState);
        }
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "↑↓", "scroll");
        hint(spans, "w", "wrap [" + (wrap ? "on" : "off") + "]");
        hintLast(spans, "Esc", "back");
    }

    @Override
    public JsonObject getTableDataAsJson() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Process");
        JsonObject data = new JsonObject();
        data.put("pid", info.pid);
        data.put("name", info.name);
        data.put("camelVersion", info.camelVersion);
        data.put("platform", info.platform);
        data.put("platformVersion", info.platformVersion);
        data.put("profile", info.profile);
        data.put("javaVersion", info.javaVersion);
        data.put("javaVendor", info.javaVendor);
        data.put("javaVmName", info.javaVmName);
        data.put("directory", info.directory);
        data.put("uptime", info.ago);
        String cmdLine = getCommandLine(info.pid);
        if (cmdLine != null) {
            data.put("commandLine", cmdLine);
        }
        result.put("process", data);
        return result;
    }

    private void addField(List<Line> lines, String label, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        String padded = String.format("  %-12s", label + ":");
        lines.add(Line.from(
                Span.styled(padded, Style.EMPTY.dim()),
                Span.styled(value, Style.EMPTY.fg(Color.WHITE).bold())));
    }

    private static String getCommandLine(String pid) {
        try {
            long p = Long.parseLong(pid);
            return ProcessHandle.of(p)
                    .flatMap(ph -> ph.info().commandLine())
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> splitCommandLine(String cmdLine) {
        List<String> parts = new ArrayList<>();
        for (String token : cmdLine.split("\\s+")) {
            if (token.startsWith("-") && !parts.isEmpty()) {
                parts.add(token);
            } else if (parts.isEmpty()) {
                parts.add(token);
            } else {
                String last = parts.get(parts.size() - 1);
                parts.set(parts.size() - 1, last + " " + token);
            }
        }
        return parts;
    }
}
