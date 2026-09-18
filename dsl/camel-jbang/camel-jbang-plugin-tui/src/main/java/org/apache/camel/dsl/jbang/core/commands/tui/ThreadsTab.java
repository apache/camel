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
import java.util.concurrent.atomic.AtomicBoolean;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class ThreadsTab extends AbstractTableTab {

    private static final String[] FILTER_LABELS = { "camel", "all" };

    private static final long AUTO_REFRESH_INTERVAL_MS = 5000;

    private final AtomicBoolean loading = new AtomicBoolean(false);

    private int filter; // 0=camel, 1=all
    private List<ThreadData> allThreads = Collections.emptyList();
    private int threadCount;
    private int peakThreadCount;
    private int traceScroll;
    private boolean detailFocused;
    private String lastPid;
    private long lastRefreshTime;

    ThreadsTab(MonitorContext ctx) {
        super(ctx, "id", "name", "state");
    }

    @Override
    protected int getRowCount() {
        return sortedThreads().size();
    }

    @Override
    public void onTabSelected() {
        String pid = ctx.selectedPid;
        if (pid != null && !pid.equals(lastPid)) {
            lastPid = pid;
            allThreads = Collections.emptyList();
        }
        if (allThreads.isEmpty()) {
            loadThreads();
        }
    }

    @Override
    public void onIntegrationChanged() {
        allThreads = Collections.emptyList();
        traceScroll = 0;
        lastPid = null;
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        return super.handleKeyEvent(ke);
    }

    @Override
    protected boolean handleTabKeyEvent(KeyEvent ke) {
        if (ke.isKey(KeyCode.TAB)) {
            detailFocused = !detailFocused;
            return true;
        }
        if (detailFocused) {
            if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                traceScroll = Math.max(0, traceScroll - 10);
                return true;
            }
            if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                traceScroll += 10;
                return true;
            }
        }
        if (ke.isCharIgnoreCase('f')) {
            filter = (filter + 1) % FILTER_LABELS.length;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        boolean handled = super.handleMouseEvent(me, area);
        if (handled) {
            traceScroll = 0;
        }
        return handled;
    }

    @Override
    public void navigateUp() {
        if (detailFocused) {
            traceScroll = Math.max(0, traceScroll - 1);
        } else {
            super.navigateUp();
            traceScroll = 0;
        }
    }

    @Override
    public void navigateDown() {
        if (detailFocused) {
            traceScroll++;
        } else {
            super.navigateDown();
            traceScroll = 0;
        }
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        long now = System.currentTimeMillis();
        if (!allThreads.isEmpty() && now - lastRefreshTime >= AUTO_REFRESH_INTERVAL_MS) {
            loadThreads();
        }

        if (loading.get() && allThreads.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(" Loading threads...", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Threads ")
                                    .build())
                            .build(),
                    area);
            return;
        }

        List<ThreadData> visible = sortedThreads();

        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.percentage(40))
                .split(area);
        renderTable(frame, chunks.get(0), visible);
        renderTrace(frame, chunks.get(1), visible);
    }

    private void renderTable(Frame frame, Rect area, List<ThreadData> visible) {
        List<Row> rows = new ArrayList<>();
        for (ThreadData t : visible) {
            String state = t.state != null ? t.state : "";
            String blocked = t.blockedTime > 0
                    ? t.blockedCount + "(" + t.blockedTime + "ms)"
                    : String.valueOf(t.blockedCount);
            String waited = t.waitedTime > 0
                    ? t.waitedCount + "(" + t.waitedTime + "ms)"
                    : String.valueOf(t.waitedCount);

            rows.add(Row.from(
                    rightCell(String.valueOf(t.id), 8),
                    Cell.from(Span.styled(t.name != null ? t.name : "", Style.EMPTY.fg(Theme.accent()))),
                    Cell.from(Span.styled(state, stateStyle(state))),
                    rightCell(blocked, 14),
                    rightCell(waited, 14)));
        }

        if (rows.isEmpty()) {
            rows.add(emptyRow("No threads", 5));
        }

        String title = String.format(" Threads [%d/%d] peak:%d filter:%s ",
                visible.size(), threadCount, peakThreadCount, FILTER_LABELS[filter]);

        Style tableBorderStyle = detailFocused ? Theme.muted() : Style.EMPTY.fg(Theme.accent());
        Style tableTitleStyle = detailFocused ? Style.EMPTY.fg(Theme.accent()) : Theme.title();

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        rightCell(sortLabel("ID", "id"), 8, sortStyle("id")),
                        Cell.from(Span.styled(sortLabel("NAME", "name"), sortStyle("name"))),
                        Cell.from(Span.styled(sortLabel("STATE", "state"), sortStyle("state"))),
                        rightCell("BLOCKED", 14, Style.EMPTY.bold()),
                        rightCell("WAITED", 14, Style.EMPTY.bold())))
                .widths(
                        Constraint.length(8),
                        Constraint.fill(),
                        Constraint.length(16),
                        Constraint.length(14),
                        Constraint.length(15))
                .highlightStyle(detailFocused ? Theme.selectionBg().dim() : Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .borderStyle(tableBorderStyle)
                        .title(Title.from(Line.from(Span.styled(title, tableTitleStyle)))).build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, visible.size());
    }

    private void renderTrace(Frame frame, Rect area, List<ThreadData> visible) {
        Style detailBorderStyle = detailFocused ? Style.EMPTY.fg(Theme.accent()) : Theme.muted();
        Style detailTitleStyle = detailFocused ? Theme.title() : Style.EMPTY.fg(Theme.accent());

        Integer sel = tableState.selected();
        if (sel == null || sel < 0 || sel >= visible.size()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(" Select a thread", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .borderStyle(detailBorderStyle)
                                    .title(Title.from(Line.from(Span.styled(" Stack Trace ", detailTitleStyle))))
                                    .build())
                            .build(),
                    area);
            return;
        }

        ThreadData thread = visible.get(sel);
        String title = " Thread " + thread.id + " " + (thread.name != null ? thread.name : "") + " ["
                       + (thread.state != null ? thread.state : "") + "] ";

        if (thread.stackTrace == null || thread.stackTrace.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(" No stack trace available", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .borderStyle(detailBorderStyle)
                                    .title(Title.from(Line.from(Span.styled(title, detailTitleStyle)))).build())
                            .build(),
                    area);
            return;
        }

        int visibleLines = area.height() - 2;
        if (visibleLines < 1) {
            visibleLines = 1;
        }
        int maxScroll = Math.max(0, thread.stackTrace.size() - visibleLines);
        traceScroll = Math.min(traceScroll, maxScroll);

        int end = Math.min(traceScroll + visibleLines, thread.stackTrace.size());
        List<Line> lines = new ArrayList<>();
        for (int i = traceScroll; i < end; i++) {
            String frame2 = thread.stackTrace.get(i);
            Style style = Style.EMPTY;
            if (frame2 != null && frame2.contains("org.apache.camel")) {
                style = Theme.label();
            }
            lines.add(Line.from(Span.styled("  " + (frame2 != null ? frame2 : ""), style)));
        }

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(lines))
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                .borderStyle(detailBorderStyle)
                                .title(Title.from(Line.from(Span.styled(title, detailTitleStyle)))).build())
                        .build(),
                area);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, "s", "sort");
        hint(spans, "f", "filter [" + FILTER_LABELS[filter] + "]");
        hint(spans, "Tab", detailFocused ? "table" : "trace");
        hintLast(spans, "PgUp/Dn", "scroll");
    }

    @Override
    public SelectionContext getSelectionContext() {
        List<ThreadData> visible = sortedThreads();
        if (visible.isEmpty()) {
            return null;
        }
        List<String> items = visible.stream().map(t -> t.name != null ? t.name : "").toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Threads");
    }

    @Override
    public Boolean isDetailFocused() {
        return detailFocused;
    }

    private List<ThreadData> sortedThreads() {
        List<ThreadData> result = new ArrayList<>();
        for (ThreadData t : allThreads) {
            if (filter == 0 && !isCamelThread(t)) {
                continue;
            }
            result.add(t);
        }
        result.sort((a, b) -> {
            int cmp = switch (sort) {
                case "name" -> compareStr(a.name, b.name);
                case "state" -> compareStr(a.state, b.state);
                default -> Long.compare(a.id, b.id);
            };
            return sortReversed ? -cmp : cmp;
        });
        return result;
    }

    private static boolean isCamelThread(ThreadData t) {
        if (t.name == null) {
            return false;
        }
        String lower = t.name.toLowerCase();
        return lower.contains("camel") || lower.contains("vertx") || lower.contains("netty");
    }

    private static Style stateStyle(String state) {
        if (state == null) {
            return Style.EMPTY;
        }
        return switch (state) {
            case "RUNNABLE" -> Theme.success();
            case "BLOCKED" -> Theme.error();
            case "WAITING" -> Theme.warning();
            case "TIMED_WAITING" -> Style.EMPTY.fg(Theme.accent());
            default -> Style.EMPTY;
        };
    }

    private void loadThreads() {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        String pid = ctx.selectedPid;
        ctx.backgroundExecutor.execute(() -> {
            try {
                loadThreadsInBackground(pid);
            } finally {
                loading.set(false);
            }
        });
    }

    private void loadThreadsInBackground(String pid) {
        JsonObject root = new JsonObject();
        root.put("action", "thread-dump");

        JsonObject jo = ctx.executeAction(pid, root, 5000);

        if (jo == null) {
            return;
        }

        int tc = jo.getIntegerOrDefault("threadCount", 0);
        int peak = jo.getIntegerOrDefault("peakThreadCount", 0);

        JsonArray arr = (JsonArray) jo.get("threads");
        if (arr == null) {
            return;
        }

        List<ThreadData> result = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JsonObject tj = (JsonObject) arr.get(i);
            ThreadData td = new ThreadData();
            Long idVal = tj.getLong("id");
            td.id = idVal != null ? idVal : 0;
            td.name = tj.getString("name");
            td.state = tj.getString("state");
            Long bc = tj.getLong("blockedCount");
            td.blockedCount = bc != null ? bc : 0;
            Long bt = tj.getLong("blockedTime");
            td.blockedTime = bt != null ? bt : 0;
            Long wc = tj.getLong("waitedCount");
            td.waitedCount = wc != null ? wc : 0;
            Long wt = tj.getLong("waitedTime");
            td.waitedTime = wt != null ? wt : 0;
            td.lockName = tj.getString("lockName");

            JsonArray st = tj.getCollection("stackTrace");
            if (st != null && !st.isEmpty()) {
                td.stackTrace = new ArrayList<>();
                for (int j = 0; j < st.size(); j++) {
                    Object frame = st.get(j);
                    td.stackTrace.add(frame != null ? frame.toString() : "");
                }
            }
            result.add(td);
        }

        if (ctx.runner != null) {
            ctx.runner.runOnRenderThread(() -> {
                allThreads = result;
                threadCount = tc;
                peakThreadCount = peak;
                lastPid = pid;
                lastRefreshTime = System.currentTimeMillis();
            });
        }
    }

    static class ThreadData {
        long id;
        String name;
        String state;
        long blockedCount;
        long blockedTime;
        long waitedCount;
        long waitedTime;
        String lockName;
        List<String> stackTrace;
    }

    @Override
    public String description() {
        return "JVM thread dump with thread names, states, and stack traces";
    }

    @Override
    public String getHelpText() {
        return DocHelper.loadHelpText("threads");
    }

    @Override
    public JsonObject getTableDataAsJson() {
        List<ThreadData> threads = sortedThreads();
        if (threads.isEmpty()) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Threads");
        JsonArray rows = new JsonArray();
        for (ThreadData td : threads) {
            JsonObject row = new JsonObject();
            row.put("id", td.id);
            row.put("name", td.name);
            row.put("state", td.state);
            row.put("blockedCount", td.blockedCount);
            row.put("blockedTime", td.blockedTime);
            row.put("waitedCount", td.waitedCount);
            row.put("waitedTime", td.waitedTime);
            if (td.lockName != null) {
                row.put("lockName", td.lockName);
            }
            if (td.stackTrace != null && !td.stackTrace.isEmpty()) {
                JsonArray st = new JsonArray();
                st.addAll(td.stackTrace);
                row.put("stackTrace", st);
            }
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", allThreads.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
