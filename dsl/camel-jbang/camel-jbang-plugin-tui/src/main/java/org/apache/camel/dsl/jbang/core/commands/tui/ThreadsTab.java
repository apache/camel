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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class ThreadsTab extends AbstractTableTab {

    private static final String[] FILTER_LABELS = { "camel", "all" };

    private final AtomicBoolean loading = new AtomicBoolean(false);

    private int filter; // 0=camel, 1=all
    private List<ThreadData> allThreads = Collections.emptyList();
    private int threadCount;
    private int peakThreadCount;
    private boolean showTrace;
    private int traceScroll;
    private String lastPid;

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
        showTrace = false;
        traceScroll = 0;
        lastPid = null;
        loadThreads();
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (showTrace) {
            if (ke.isUp()) {
                traceScroll = Math.max(0, traceScroll - 1);
                return true;
            }
            if (ke.isDown()) {
                traceScroll++;
                return true;
            }
            if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                traceScroll = Math.max(0, traceScroll - 20);
                return true;
            }
            if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                traceScroll += 20;
                return true;
            }
            return false;
        }
        return super.handleKeyEvent(ke);
    }

    @Override
    protected boolean handleTabKeyEvent(KeyEvent ke) {
        if (ke.isConfirm()) {
            showTrace = !showTrace;
            traceScroll = 0;
            return true;
        }
        if (ke.isCharIgnoreCase('f')) {
            filter = (filter + 1) % FILTER_LABELS.length;
            return true;
        }
        if (ke.isCharIgnoreCase('r')) {
            loadThreads();
            return true;
        }
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            List<ThreadData> visible = sortedThreads();
            for (int i = 0; i < 20 && tableState.selected() != null && tableState.selected() > 0; i++) {
                tableState.selectPrevious();
            }
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            List<ThreadData> visible = sortedThreads();
            for (int i = 0; i < 20; i++) {
                tableState.selectNext(visible.size());
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        if (!showTrace) {
            return super.handleMouseEvent(me, area);
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (showTrace) {
            showTrace = false;
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
        if (!showTrace) {
            super.navigateUp();
        }
    }

    @Override
    public void navigateDown() {
        if (!showTrace) {
            super.navigateDown();
        }
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
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

        if (showTrace) {
            List<Rect> chunks = Layout.vertical()
                    .constraints(Constraint.length(1), Constraint.percentage(40), Constraint.fill())
                    .split(area);
            renderSummary(frame, chunks.get(0), visible);
            renderTable(frame, chunks.get(1), visible);
            renderTrace(frame, chunks.get(2), visible);
        } else {
            List<Rect> chunks = Layout.vertical()
                    .constraints(Constraint.length(1), Constraint.fill())
                    .split(area);
            renderSummary(frame, chunks.get(0), visible);
            renderTable(frame, chunks.get(1), visible);
        }
    }

    private void renderSummary(Frame frame, Rect area, List<ThreadData> visible) {
        List<Span> spans = new ArrayList<>();
        spans.add(Span.styled(" Threads: ", Style.EMPTY.fg(Color.YELLOW).bold()));
        spans.add(Span.styled(String.valueOf(threadCount), Style.EMPTY.fg(Color.WHITE)));
        spans.add(Span.raw("    "));
        spans.add(Span.styled("Peak: ", Style.EMPTY.fg(Color.YELLOW).bold()));
        spans.add(Span.styled(String.valueOf(peakThreadCount), Style.EMPTY.fg(Color.WHITE)));
        spans.add(Span.raw("    "));
        spans.add(Span.styled("Showing: ", Style.EMPTY.fg(Color.YELLOW).bold()));
        spans.add(Span.styled(visible.size() + "/" + allThreads.size(), Style.EMPTY.fg(Color.WHITE)));
        frame.renderWidget(Paragraph.from(Line.from(spans)), area);
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
                    Cell.from(Span.styled(t.name != null ? t.name : "", Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(Span.styled(state, stateStyle(state))),
                    rightCell(blocked, 14),
                    rightCell(waited, 14)));
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(Span.styled("No threads", Style.EMPTY.dim())),
                    Cell.from(""), Cell.from(""), Cell.from(""), Cell.from("")));
        }

        String title = String.format(" Threads [%d] sort:%s filter:%s ", visible.size(), sort, FILTER_LABELS[filter]);

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
                        Constraint.length(14))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        MonitorTab.renderTableScrollbar(frame, lastTableArea, tableState, tableScrollState, visible.size());
    }

    private void renderTrace(Frame frame, Rect area, List<ThreadData> visible) {
        Integer sel = tableState.selected();
        if (sel == null || sel < 0 || sel >= visible.size()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(" Select a thread", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Stack Trace ")
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
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
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
                style = Style.EMPTY.fg(Color.YELLOW);
            }
            lines.add(Line.from(Span.styled("  " + (frame2 != null ? frame2 : ""), style)));
        }

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(lines))
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                        .build(),
                area);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, "s", "sort");
        hint(spans, "f", "filter [" + FILTER_LABELS[filter] + "]");
        hint(spans, "r", "refresh");
        if (showTrace) {
            hintLast(spans, "↑↓", "scroll");
        } else {
            hintLast(spans, "Enter", "trace");
        }
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

    private static int compareStr(String a, String b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareToIgnoreCase(b);
    }

    private static Style stateStyle(String state) {
        if (state == null) {
            return Style.EMPTY;
        }
        return switch (state) {
            case "RUNNABLE" -> Style.EMPTY.fg(Color.GREEN);
            case "BLOCKED" -> Style.EMPTY.fg(Color.LIGHT_RED);
            case "WAITING" -> Style.EMPTY.fg(Color.YELLOW);
            case "TIMED_WAITING" -> Style.EMPTY.fg(Color.CYAN);
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
        ctx.runner.scheduler().execute(() -> {
            try {
                loadThreadsInBackground(pid);
            } finally {
                loading.set(false);
            }
        });
    }

    private void loadThreadsInBackground(String pid) {
        Path outputFile = ctx.getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "thread-dump");

        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 5000);
        PathUtils.deleteFile(outputFile);

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
    public String getHelpText() {
        return """
                # Threads

                The Threads tab shows all JVM threads with their state, blocked/waited
                counts, and stack traces. This helps diagnose deadlocks, thread
                contention, and excessive thread creation.

                By default, the view is filtered to show only Camel-related threads
                (threads whose name contains `camel`, `vertx`, or `netty`). Press `f`
                to toggle between Camel threads and all JVM threads.

                ## Table Columns

                - **ID** — Thread ID assigned by the JVM. Lower IDs are typically system threads created at startup
                - **NAME** — Thread name. Camel names its threads descriptively (e.g., `Camel (camel-demo) thread #1 - timer://hello`). The thread name often tells you which route or component is using it
                - **STATE** — Thread state (see Thread States below)
                - **BLOCKED** — Number of times this thread was blocked waiting for a monitor lock, with total blocked time in parentheses if available (e.g., `3(45ms)`). High blocked counts indicate lock contention
                - **WAITED** — Number of times this thread entered a waiting state, with total wait time if available (e.g., `150(2340ms)`). High wait counts are normal for thread pool threads waiting for work

                ## Example Screen

                ```
                 ID  NAME                                             STATE           BLOCKED     WAITED
                 1   main                                             WAITING         0           5(120ms)
                 22  Camel (camel-demo) thread #1 - timer://hello     TIMED_WAITING   0           17(34000ms)
                 23  Camel (camel-demo) thread #2 - timer://pump      TIMED_WAITING   0           12(36000ms)
                 24  Camel (camel-demo) thread #3 - seda://queue      WAITING         0           12(1200ms)
                 25  vert.x-eventloop-thread-0                        RUNNABLE        0           0
                ```

                ## Thread States

                - **RUNNABLE** (green) — Actively executing code or ready to run. The thread has work to do and the CPU is available
                - **BLOCKED** (red) — Waiting to acquire a monitor lock held by another thread. This is lock contention — another thread holds the `synchronized` block. Multiple BLOCKED threads on the same lock may indicate a bottleneck
                - **WAITING** (yellow) — Waiting indefinitely for another thread to signal. Common for thread pool threads sitting idle in a queue, waiting for work to arrive
                - **TIMED_WAITING** (cyan) — Waiting with a timeout. Typical for: `Thread.sleep()`, scheduled poll consumers waiting for the next poll interval, `Object.wait(timeout)`. Camel timer and polling consumers spend most of their time in this state between polls

                ## What To Look For

                - **Many BLOCKED threads**: Lock contention — multiple threads competing for the same resource. Check the stack traces to find the contested lock
                - **Many WAITING threads**: Usually normal — thread pools waiting for work. But if the application is supposed to be busy, idle threads may indicate a configuration issue
                - **Thread count growing over time**: Possible thread leak — threads being created but not properly shut down. Compare current count with peak count in the summary bar
                - **Deadlock**: Two or more threads each waiting for a lock held by the other. Look for BLOCKED threads with cross-referencing lock names

                ## Stack Trace View

                Press `Enter` to see the full stack trace of the selected thread.
                The stack trace shows exactly what code the thread is executing (or
                waiting in). Lines containing `org.apache.camel` are highlighted in
                yellow to help you find Camel-related frames.

                Use `Up/Down` or `PgUp/PgDn` to scroll through long stack traces.

                ## Filter Modes

                - **camel** (default) — Show only Camel-related threads (names containing `camel`, `vertx`, or `netty`)
                - **all** — Show all JVM threads including system threads, GC threads, JIT compiler threads

                ## Keys

                - `Up/Down` — select thread
                - `Enter` — view/hide stack trace
                - `s` — cycle sort column
                - `S` — reverse sort order
                - `f` — toggle filter (camel / all)
                - `r` — refresh thread data
                - `Esc` — back
                """;
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
