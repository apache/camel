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
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class HeapHistogramTab implements MonitorTab {

    private static final String[] SORT_COLUMNS = { "className", "instances", "bytes" };
    private static final String[] FILTER_LABELS = { "all", "non-jdk", "camel" };

    private final MonitorContext ctx;
    private final TableState tableState = new TableState();
    private final AtomicBoolean loading = new AtomicBoolean(false);

    private String sort = "bytes";
    private int sortIndex = 2;
    private boolean sortReversed;
    private int filter; // 0=all, 1=camel
    private List<HeapEntry> allEntries = Collections.emptyList();
    private long totalInstances;
    private long totalBytes;
    private String lastPid;

    HeapHistogramTab(MonitorContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onTabSelected() {
        String pid = ctx.selectedPid;
        if (pid != null && !pid.equals(lastPid)) {
            lastPid = pid;
            allEntries = Collections.emptyList();
        }
        if (allEntries.isEmpty()) {
            loadHeapHistogram();
        }
    }

    @Override
    public void onIntegrationChanged() {
        allEntries = Collections.emptyList();
        lastPid = null;
        loadHeapHistogram();
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (ke.isChar('s')) {
            sortIndex = (sortIndex + 1) % SORT_COLUMNS.length;
            sort = SORT_COLUMNS[sortIndex];
            sortReversed = false;
            return true;
        }
        if (ke.isChar('S')) {
            sortReversed = !sortReversed;
            return true;
        }
        if (ke.isCharIgnoreCase('f')) {
            filter = (filter + 1) % FILTER_LABELS.length;
            return true;
        }
        if (ke.isKey(KeyCode.F5)) {
            loadHeapHistogram();
            return true;
        }
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            for (int i = 0; i < 20 && tableState.selected() != null && tableState.selected() > 0; i++) {
                tableState.selectPrevious();
            }
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            List<HeapEntry> visible = sortedEntries();
            for (int i = 0; i < 20; i++) {
                tableState.selectNext(visible.size());
            }
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
        tableState.selectPrevious();
    }

    @Override
    public void navigateDown() {
        List<HeapEntry> visible = sortedEntries();
        tableState.selectNext(visible.size());
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        if (loading.get() && allEntries.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(dev.tamboui.text.Text.from(
                                    Line.from(Span.styled(" Loading heap histogram...", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Heap Histogram ").build())
                            .build(),
                    area);
            return;
        }

        List<HeapEntry> visible = sortedEntries();

        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.length(1), Constraint.fill())
                .split(area);
        renderSummary(frame, chunks.get(0), visible);
        renderTable(frame, chunks.get(1), visible);
    }

    private void renderSummary(Frame frame, Rect area, List<HeapEntry> visible) {
        List<Span> spans = new ArrayList<>();
        spans.add(Span.styled(" Classes: ", Style.EMPTY.fg(Color.YELLOW).bold()));
        spans.add(Span.styled(String.valueOf(allEntries.size()), Style.EMPTY.fg(Color.WHITE)));
        spans.add(Span.raw("    "));
        spans.add(Span.styled("Showing: ", Style.EMPTY.fg(Color.YELLOW).bold()));
        spans.add(Span.styled(String.valueOf(visible.size()), Style.EMPTY.fg(Color.WHITE)));
        spans.add(Span.raw("    "));
        spans.add(Span.styled("Total Instances: ", Style.EMPTY.fg(Color.YELLOW).bold()));
        spans.add(Span.styled(formatNumber(totalInstances), Style.EMPTY.fg(Color.WHITE)));
        spans.add(Span.raw("    "));
        spans.add(Span.styled("Total Bytes: ", Style.EMPTY.fg(Color.YELLOW).bold()));
        spans.add(Span.styled(formatBytes(totalBytes), Style.EMPTY.fg(Color.WHITE)));
        frame.renderWidget(Paragraph.from(Line.from(spans)), area);
    }

    private void renderTable(Frame frame, Rect area, List<HeapEntry> visible) {
        List<Row> rows = new ArrayList<>();
        for (HeapEntry e : visible) {
            rows.add(Row.from(
                    rightCell(String.valueOf(e.num), 6),
                    Cell.from(Span.styled(e.className != null ? e.className : "", Style.EMPTY.fg(Color.CYAN))),
                    rightCell(formatNumber(e.instances), 14),
                    rightCell(formatBytes(e.bytes), 14)));
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(Span.styled("No data", Style.EMPTY.dim())),
                    Cell.from(""), Cell.from(""), Cell.from("")));
        }

        String title = String.format(" Heap Histogram [%d] sort:%s filter:%s ",
                visible.size(), sort, FILTER_LABELS[filter]);

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        rightCell("#", 6, Style.EMPTY.bold()),
                        Cell.from(Span.styled(sortLabel("CLASS NAME", "className"), sortStyle("className"))),
                        rightCell(sortLabel("INSTANCES", "instances"), 14, sortStyle("instances")),
                        rightCell(sortLabel("BYTES", "bytes"), 14, sortStyle("bytes"))))
                .widths(
                        Constraint.length(6),
                        Constraint.fill(),
                        Constraint.length(14),
                        Constraint.length(14))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                .build();

        frame.renderStatefulWidget(table, area, tableState);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, "s", "sort");
        hint(spans, "f", "filter [" + FILTER_LABELS[filter] + "]");
        hintLast(spans, "F5", "refresh");
    }

    @Override
    public SelectionContext getSelectionContext() {
        List<HeapEntry> visible = sortedEntries();
        if (visible.isEmpty()) {
            return null;
        }
        List<String> items = visible.stream().map(e -> e.className != null ? e.className : "").toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Heap Histogram");
    }

    @Override
    public JsonObject getTableDataAsJson() {
        List<HeapEntry> entries = sortedEntries();
        if (entries.isEmpty()) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Heap Histogram");
        JsonArray rows = new JsonArray();
        for (HeapEntry e : entries) {
            JsonObject row = new JsonObject();
            row.put("num", e.num);
            row.put("className", e.className);
            row.put("instances", e.instances);
            row.put("bytes", e.bytes);
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", allEntries.size());
        result.put("totalInstances", totalInstances);
        result.put("totalBytes", totalBytes);
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }

    @Override
    public String getHelpText() {
        return """
                # Heap Histogram

                The Heap Histogram tab shows class-level memory usage in the JVM heap,
                similar to `jcmd <pid> GC.class_histogram`. Each row represents a class
                with the number of live instances and total bytes consumed.

                This is useful for diagnosing memory leaks, finding unexpected object
                retention, and understanding which classes dominate heap usage.

                ## Table Columns

                - **#** — Rank by bytes (from the raw JVM histogram)
                - **CLASS NAME** — Fully qualified class name. Array types use JVM notation (e.g., `[B` = byte array, `[Ljava.lang.Object;` = Object array)
                - **INSTANCES** — Number of live instances of this class on the heap
                - **BYTES** — Total bytes consumed by all instances of this class

                ## Example Screen

                ```
                 #     CLASS NAME                                        INSTANCES         BYTES
                 1     [B                                                   45,230      12.5 MB
                 2     [C                                                   38,100       8.2 MB
                 3     java.lang.String                                     38,050       1.8 MB
                 4     java.util.HashMap$Node                               12,400     595.0 KB
                 5     java.lang.Object[]                                    8,200     450.2 KB
                ```

                ## Filter Modes

                - **all** (default) — Show all classes
                - **camel** — Show only classes from `org.apache.camel` packages

                ## What To Look For

                - **Large byte counts at the top**: Normal for byte arrays (`[B`) and char arrays (`[C`) — these back Strings and buffers
                - **Unexpected classes with high counts**: May indicate a memory leak — objects being created but not released
                - **Growing instance counts on refresh**: Press F5 repeatedly to spot classes whose instance counts keep growing, which suggests a leak
                - **Camel-specific classes**: Use the `camel` filter to focus on Camel's own objects. High counts of Exchange, Message, or Endpoint objects may indicate route issues

                ## Keys

                | Key | Action |
                |-----|--------|
                | Up/Down | Select class |
                | s | Cycle sort column (bytes, instances, className) |
                | S | Reverse sort order |
                | f | Toggle filter (all / camel) |
                | F5 | Refresh heap histogram |
                | PgUp/PgDn | Scroll by page |
                | Esc | Back |
                """;
    }

    private List<HeapEntry> sortedEntries() {
        List<HeapEntry> result = new ArrayList<>();
        for (HeapEntry e : allEntries) {
            if (filter == 1 && isJavaClass(e)) {
                continue;
            }
            if (filter == 2 && !isCamelClass(e)) {
                continue;
            }
            result.add(e);
        }
        result.sort((a, b) -> {
            int cmp = switch (sort) {
                case "instances" -> Long.compare(b.instances, a.instances);
                case "className" -> compareStr(a.className, b.className);
                default -> Long.compare(b.bytes, a.bytes);
            };
            return sortReversed ? -cmp : cmp;
        });
        return result;
    }

    private static boolean isJavaClass(HeapEntry e) {
        if (e.className == null) {
            return false;
        }
        return e.className.startsWith("java.")
                || e.className.startsWith("javax.")
                || e.className.startsWith("jdk.")
                || e.className.startsWith("sun.")
                || e.className.startsWith("com.sun.")
                || e.className.startsWith("[");
    }

    private static boolean isCamelClass(HeapEntry e) {
        return e.className != null && e.className.contains("org.apache.camel");
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

    private String sortLabel(String label, String column) {
        return MonitorContext.sortLabel(label, column, sort, sortReversed);
    }

    private Style sortStyle(String column) {
        return MonitorContext.sortStyle(column, sort);
    }

    private void loadHeapHistogram() {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        String pid = ctx.selectedPid;
        ctx.runner.scheduler().execute(() -> {
            try {
                loadHeapHistogramInBackground(pid);
            } finally {
                loading.set(false);
            }
        });
    }

    private void loadHeapHistogramInBackground(String pid) {
        Path outputFile = ctx.getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "heap-histogram");

        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 5000);
        PathUtils.deleteFile(outputFile);

        if (jo == null) {
            return;
        }

        long ti = jo.getLongOrDefault("totalInstances", 0);
        long tb = jo.getLongOrDefault("totalBytes", 0);

        JsonArray arr = (JsonArray) jo.get("classes");
        if (arr == null) {
            return;
        }

        List<HeapEntry> result = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JsonObject ej = (JsonObject) arr.get(i);
            HeapEntry entry = new HeapEntry();
            entry.num = ej.getIntegerOrDefault("num", 0);
            entry.className = ej.getString("className");
            entry.instances = ej.getLongOrDefault("instances", 0);
            entry.bytes = ej.getLongOrDefault("bytes", 0);
            result.add(entry);
        }

        if (ctx.runner != null) {
            ctx.runner.runOnRenderThread(() -> {
                allEntries = result;
                totalInstances = ti;
                totalBytes = tb;
                lastPid = pid;
            });
        }
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    static String formatNumber(long num) {
        return String.format("%,d", num);
    }

    static class HeapEntry {
        int num;
        String className;
        long instances;
        long bytes;
    }
}
