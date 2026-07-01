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
import java.util.Locale;
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

class JfrOldObjectSampleTab implements MonitorTab {

    private enum State {
        IDLE,
        RECORDING,
        LOADING_RESULTS,
        HAS_RESULTS
    }

    private static final String[] SORT_COLUMNS = { "allocationClass", "totalSize", "count", "objectAge" };
    private static final long[] MIN_SIZE_PRESETS = {
            0, 1024, 10 * 1024, 100 * 1024, 1024 * 1024, 10 * 1024 * 1024, 100 * 1024 * 1024 };
    private static final String[] MIN_SIZE_LABELS = {
            "off", "1 KB", "10 KB", "100 KB", "1 MB", "10 MB", "100 MB" };

    private final MonitorContext ctx;
    private final TableState tableState = new TableState();
    private final AtomicBoolean loading = new AtomicBoolean(false);

    private State state = State.IDLE;
    private int duration = 30;
    private long recordingStartTime;

    private String sort = "totalSize";
    private int sortIndex = 1;
    private boolean sortReversed;

    private List<SampleEntry> samples = Collections.emptyList();
    private int sampleCount;
    private long recordingDurationMs;
    private long recordingEndTime;
    private String lastPid;
    private int detailScroll;
    private int minSizeIndex;

    JfrOldObjectSampleTab(MonitorContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onTabSelected() {
        String pid = ctx.selectedPid;
        if (pid != null && !pid.equals(lastPid)) {
            lastPid = pid;
            state = State.IDLE;
            samples = Collections.emptyList();
        }
        if (state == State.IDLE && samples.isEmpty()) {
            checkStatus();
        }
    }

    @Override
    public void onIntegrationChanged() {
        state = State.IDLE;
        samples = Collections.emptyList();
        lastPid = null;
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (state == State.IDLE || state == State.HAS_RESULTS) {
            if (ke.isCharIgnoreCase('r')) {
                startRecording();
                return true;
            }
        }
        if (state == State.IDLE || state == State.HAS_RESULTS) {
            if (ke.isChar('+') || ke.isChar('=')) {
                duration = Math.min(300, duration + 10);
                return true;
            }
            if (ke.isChar('-')) {
                duration = Math.max(10, duration - 10);
                return true;
            }
        }
        if (state == State.RECORDING) {
            if (ke.isCharIgnoreCase('x')) {
                stopRecording();
                return true;
            }
        }
        if (state == State.HAS_RESULTS) {
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
            if (ke.isChar('m')) {
                minSizeIndex = (minSizeIndex + 1) % MIN_SIZE_PRESETS.length;
                tableState.select(0);
                return true;
            }
            if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                detailScroll = Math.max(0, detailScroll - 10);
                return true;
            }
            if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                detailScroll += 10;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        return false;
    }

    @Override
    public void navigateUp() {
        if (state == State.HAS_RESULTS) {
            tableState.selectPrevious();
            detailScroll = 0;
        }
    }

    @Override
    public void navigateDown() {
        if (state == State.HAS_RESULTS) {
            List<SampleEntry> visible = sortedSamples();
            tableState.selectNext(visible.size());
            detailScroll = 0;
        }
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        switch (state) {
            case IDLE -> renderIdle(frame, area);
            case RECORDING -> renderRecording(frame, area);
            case LOADING_RESULTS -> renderLoading(frame, area);
            case HAS_RESULTS -> renderResults(frame, area);
        }
    }

    private void renderIdle(Frame frame, Rect area) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(Span.raw("")));

        if (loading.get()) {
            lines.add(Line.from(
                    Span.styled("  Checking for existing JFR results...", Style.EMPTY.dim())));
        } else {
            lines.add(Line.from(
                    Span.styled("  No JFR recording active.", Style.EMPTY.dim())));
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(
                    Span.styled("  Press ", Style.EMPTY.dim()),
                    Span.styled("R", Style.EMPTY.fg(Color.YELLOW).bold()),
                    Span.styled(" to start recording OldObjectSample events.", Style.EMPTY.dim())));
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(
                    Span.styled("  Duration: ", Style.EMPTY.fg(Color.YELLOW).bold()),
                    Span.styled(duration + "s", Style.EMPTY.fg(Color.WHITE)),
                    Span.styled("  (use ", Style.EMPTY.dim()),
                    Span.styled("+", Style.EMPTY.fg(Color.YELLOW).bold()),
                    Span.styled("/", Style.EMPTY.dim()),
                    Span.styled("-", Style.EMPTY.fg(Color.YELLOW).bold()),
                    Span.styled(" to adjust)", Style.EMPTY.dim())));
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(
                    Span.styled("  JFR OldObjectSample tracks objects surviving multiple GC cycles",
                            Style.EMPTY.dim())));
            lines.add(Line.from(
                    Span.styled("  and captures reference chains back to GC roots, helping",
                            Style.EMPTY.dim())));
            lines.add(Line.from(
                    Span.styled("  diagnose memory leaks.", Style.EMPTY.dim())));
        }

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(lines))
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                .title(" JFR Old Object Samples ").build())
                        .build(),
                area);
    }

    private void renderRecording(Frame frame, Rect area) {
        long elapsed = System.currentTimeMillis() - recordingStartTime;
        long remaining = Math.max(0, (duration * 1000L) - elapsed);

        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(Span.raw("")));
        lines.add(Line.from(
                Span.styled("  JFR recording in progress...", Style.EMPTY.fg(Color.GREEN).bold())));
        lines.add(Line.from(Span.raw("")));
        lines.add(Line.from(
                Span.styled("  Elapsed:    ", Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.styled(formatDuration(elapsed), Style.EMPTY.fg(Color.WHITE))));
        lines.add(Line.from(
                Span.styled("  Remaining:  ", Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.styled(formatDuration(remaining), Style.EMPTY.fg(Color.WHITE))));
        lines.add(Line.from(
                Span.styled("  Duration:   ", Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.styled(duration + "s", Style.EMPTY.fg(Color.WHITE))));
        lines.add(Line.from(Span.raw("")));
        lines.add(Line.from(
                Span.styled("  Press ", Style.EMPTY.dim()),
                Span.styled("X", Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.styled(" to stop recording early and view results.", Style.EMPTY.dim())));

        // progress bar
        int barWidth = Math.max(10, area.width() - 6);
        double pct = duration > 0 ? Math.min(1.0, elapsed / (duration * 1000.0)) : 0;
        int filled = (int) (pct * barWidth);
        StringBuilder bar = new StringBuilder("  ");
        for (int i = 0; i < barWidth; i++) {
            bar.append(i < filled ? '█' : '░');
        }
        lines.add(Line.from(Span.raw("")));
        lines.add(Line.from(Span.styled(bar.toString(), Style.EMPTY.fg(Color.GREEN))));

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(lines))
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                .title(" JFR Recording ").build())
                        .build(),
                area);
    }

    private void renderLoading(Frame frame, Rect area) {
        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(
                                Line.from(Span.styled(" Stopping recording and analyzing results...",
                                        Style.EMPTY.dim()))))
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                .title(" JFR Old Object Samples ").build())
                        .build(),
                area);
    }

    private void renderResults(Frame frame, Rect area) {
        List<SampleEntry> visible = sortedSamples();

        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.percentage(40), Constraint.fill())
                .split(area);
        renderSampleTable(frame, chunks.get(0), visible);
        renderDetail(frame, chunks.get(1), visible);
    }

    private void renderSampleTable(Frame frame, Rect area, List<SampleEntry> visible) {
        List<Row> rows = new ArrayList<>();
        for (SampleEntry e : visible) {
            String totalStr = e.totalSize > 0 ? formatBytes(e.totalSize) : "-";
            rows.add(Row.from(
                    rightCell(String.valueOf(e.num), 6),
                    Cell.from(Span.styled(e.className != null ? e.className : "", Style.EMPTY.fg(Color.CYAN))),
                    rightCell(String.valueOf(e.count), 8),
                    rightCell(totalStr, 12),
                    rightCell(formatDuration(e.objectAge), 12)));
        }

        if (rows.isEmpty()) {
            String msg = minSizeIndex > 0 ? "No samples above " + MIN_SIZE_LABELS[minSizeIndex] : "No samples captured";
            rows.add(Row.from(
                    Cell.from(""), Cell.from(Span.styled(msg, Style.EMPTY.dim())),
                    Cell.from(""), Cell.from(""), Cell.from("")));
        }

        long minSize = MIN_SIZE_PRESETS[minSizeIndex];
        String minLabel = minSize > 0 ? " min:" + MIN_SIZE_LABELS[minSizeIndex] : "";
        String agoLabel = "";
        if (recordingEndTime > 0) {
            long agoMin = (System.currentTimeMillis() - recordingEndTime) / 60000;
            if (agoMin >= 1) {
                agoLabel = " (" + agoMin + "m ago)";
            }
        }
        String title = String.format(" JFR Old Objects [%d] duration:%s sort:%s%s%s ",
                visible.size(), formatDuration(recordingDurationMs), sort, minLabel, agoLabel);

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        rightCell("#", 6, Style.EMPTY.bold()),
                        Cell.from(Span.styled(sortLabel("CLASS", "allocationClass"),
                                sortStyle("allocationClass"))),
                        rightCell(sortLabel("COUNT", "count"), 8,
                                sortStyle("count")),
                        rightCell(sortLabel("TOTAL", "totalSize"), 12,
                                sortStyle("totalSize")),
                        rightCell(sortLabel("AGE", "objectAge"), 12,
                                sortStyle("objectAge"))))
                .widths(
                        Constraint.length(6),
                        Constraint.fill(),
                        Constraint.length(8),
                        Constraint.length(12),
                        Constraint.length(12))
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                .build();

        frame.renderStatefulWidget(table, area, tableState);
    }

    private void renderDetail(Frame frame, Rect area, List<SampleEntry> visible) {
        Integer sel = tableState.selected();
        if (sel == null || sel < 0 || sel >= visible.size()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled(" Select a sample to see reference chain details",
                                            Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Detail ").build())
                            .build(),
                    area);
            return;
        }

        SampleEntry entry = visible.get(sel);
        List<Line> lines = new ArrayList<>();

        // Sample info
        lines.add(Line.from(
                Span.styled("  Class:  ", Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.styled(entry.className != null ? entry.className : "unknown", Style.EMPTY.fg(Color.CYAN))));

        List<Span> infoSpans = new ArrayList<>();
        if (entry.count > 1) {
            infoSpans.add(Span.styled("  Count:  ", Style.EMPTY.fg(Color.YELLOW).bold()));
            infoSpans.add(Span.styled(String.valueOf(entry.count), Style.EMPTY.fg(Color.WHITE)));
        }
        if (entry.totalSize > 0) {
            infoSpans.add(Span.styled(infoSpans.isEmpty() ? "  Total:  " : "      Total: ",
                    Style.EMPTY.fg(Color.YELLOW).bold()));
            infoSpans.add(Span.styled(formatBytes(entry.totalSize), Style.EMPTY.fg(Color.WHITE)));
        }
        if (!infoSpans.isEmpty()) {
            lines.add(Line.from(infoSpans));
        }
        lines.add(Line.from(
                Span.styled("  Age:    ", Style.EMPTY.fg(Color.YELLOW).bold()),
                Span.styled(formatDuration(entry.objectAge), Style.EMPTY.fg(Color.WHITE))));

        // Reference chain
        if (entry.referenceChain != null && !entry.referenceChain.isEmpty()) {
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(
                    Span.styled("  Reference Chain (Object → GC Root):", Style.EMPTY.fg(Color.YELLOW).bold())));

            for (int i = 0; i < entry.referenceChain.size(); i++) {
                ChainLink link = entry.referenceChain.get(i);
                String prefix = i == entry.referenceChain.size() - 1 ? "  └─ " : "  ├─ ";
                String typeName = link.type != null ? abbreviateType(link.type) : "?";
                String fieldInfo = link.field != null ? " (field: " + link.field + ")" : "";
                String descInfo = link.description != null ? " [" + link.description + "]" : "";

                lines.add(Line.from(
                        Span.styled(prefix, Style.EMPTY.fg(Color.BLUE)),
                        Span.styled(typeName, Style.EMPTY.fg(Color.CYAN)),
                        Span.styled(fieldInfo, Style.EMPTY.fg(Color.GREEN)),
                        Span.styled(descInfo, Style.EMPTY.dim())));
            }
        }

        // Stack trace
        if (entry.stackTrace != null && !entry.stackTrace.isEmpty()) {
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(
                    Span.styled("  Allocation Stack Trace:", Style.EMPTY.fg(Color.YELLOW).bold())));

            for (StackEntry frame2 : entry.stackTrace) {
                Style methodStyle = isJdkFrame(frame2.method) ? Style.EMPTY.dim() : Style.EMPTY.fg(Color.WHITE);
                lines.add(Line.from(
                        Span.styled("    at ", Style.EMPTY.dim()),
                        Span.styled(frame2.method, methodStyle),
                        Span.styled(":" + frame2.line, Style.EMPTY.dim())));
            }
        }

        // apply scroll offset
        if (detailScroll > 0 && detailScroll < lines.size()) {
            lines = new ArrayList<>(lines.subList(detailScroll, lines.size()));
        } else if (detailScroll >= lines.size()) {
            detailScroll = Math.max(0, lines.size() - 1);
            if (!lines.isEmpty()) {
                lines = new ArrayList<>(lines.subList(detailScroll, lines.size()));
            }
        }

        String title = " Detail ";
        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(lines))
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                        .build(),
                area);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        switch (state) {
            case IDLE -> {
                hint(spans, "R", "record");
                hint(spans, "+/-", "duration [" + duration + "s]");
                hintLast(spans, "Esc", "back");
            }
            case RECORDING -> {
                hint(spans, "X", "stop");
                hintLast(spans, "Esc", "back");
            }
            case HAS_RESULTS -> {
                hint(spans, "Esc", "back");
                hint(spans, "s", "sort");
                hint(spans, "m", "min-size [" + MIN_SIZE_LABELS[minSizeIndex] + "]");
                hint(spans, "R", "new recording");
                hint(spans, "+/-", "duration [" + duration + "s]");
                hintLast(spans, "PgUp/Dn", "scroll detail");
            }
            default -> hintLast(spans, "Esc", "back");
        }
    }

    @Override
    public SelectionContext getSelectionContext() {
        if (state != State.HAS_RESULTS) {
            return null;
        }
        List<SampleEntry> visible = sortedSamples();
        if (visible.isEmpty()) {
            return null;
        }
        List<String> items = visible.stream()
                .map(e -> e.className != null ? e.className : "").toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "JFR Old Objects");
    }

    @Override
    public JsonObject getTableDataAsJson() {
        if (state != State.HAS_RESULTS || samples.isEmpty()) {
            return null;
        }
        List<SampleEntry> visible = sortedSamples();
        JsonObject result = new JsonObject();
        result.put("tab", "JFR Old Objects");
        JsonArray rows = new JsonArray();
        for (SampleEntry e : visible) {
            JsonObject row = new JsonObject();
            row.put("num", e.num);
            row.put("className", e.className);
            row.put("count", e.count);
            row.put("totalSize", e.totalSize);
            row.put("allocationSize", e.allocationSize);
            row.put("objectAge", e.objectAge);
            row.put("chainSummary", e.chainSummary);
            if (e.referenceChain != null) {
                JsonArray chain = new JsonArray();
                for (ChainLink link : e.referenceChain) {
                    JsonObject cl = new JsonObject();
                    if (link.type != null) {
                        cl.put("type", link.type);
                    }
                    if (link.field != null) {
                        cl.put("field", link.field);
                    }
                    if (link.description != null) {
                        cl.put("description", link.description);
                    }
                    chain.add(cl);
                }
                row.put("referenceChain", chain);
            }
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", sampleCount);
        result.put("recordingDurationMs", recordingDurationMs);
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }

    @Override
    public String getHelpText() {
        return """
                # JFR Old Object Samples

                The JFR Old Object Samples tab uses Java Flight Recorder to capture objects
                that have survived multiple GC cycles (long-lived objects) and traces their
                reference chains back to GC roots. This helps diagnose memory leaks by
                answering "why is this object still alive?"

                ## How To Use

                1. Press **R** to start a JFR recording (default 30 seconds)
                2. Use **+**/**-** to adjust the duration before starting
                3. Wait for the recording to complete (or press **X** to stop early)
                4. Browse the samples table and select entries to see reference chains

                ## Table Columns

                Samples from the same class and allocation site (stack trace) are
                grouped together automatically.

                - **#** — Group number
                - **CLASS** — The class of the sampled long-lived object
                - **COUNT** — Number of samples from the same allocation site
                - **TOTAL** — Total allocation size across all samples in the group
                - **AGE** — Maximum age across samples in the group

                ## Detail Panel

                The detail panel shows the full reference chain and allocation stack trace
                for the selected sample:

                - **Reference Chain** — Path from the object to its GC root, showing
                  each referencing type and field name
                - **Allocation Stack Trace** — Where the object was originally allocated

                ## What To Look For

                - **Objects with very long ages**: These have survived many GC cycles
                - **Unexpected reference chains**: Objects held by caches, maps, or
                  static fields that prevent garbage collection
                - **Growing collections**: HashMap, ArrayList, ConcurrentHashMap entries
                  that keep accumulating
                - **Thread-local references**: Objects held by thread-local variables

                ## Comparison With Heap Histogram

                The Heap Histogram tab shows WHAT is using memory (class instance counts
                and sizes). This tab shows WHY objects are still alive (reference chains
                to GC roots). Use both together: find suspicious classes in Heap Histogram,
                then use JFR Old Objects to trace why they are not being collected.

                ## Keys

                | Key | Action |
                |-----|--------|
                | R | Start/restart JFR recording |
                | X | Stop recording early |
                | +/- | Adjust recording duration |
                | Up/Down | Select sample |
                | s | Cycle sort column (class, size, age) |
                | S | Reverse sort order |
                | m | Cycle minimum size filter |
                | PgUp/PgDn | Scroll detail panel |
                | Esc | Back |
                """;
    }

    // ---- Action methods ----

    private void startRecording() {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        state = State.RECORDING;
        recordingStartTime = System.currentTimeMillis();

        String pid = ctx.selectedPid;
        int dur = duration;
        startDaemonThread("jfr-start-" + pid, () -> {
            try {
                sendStartCommand(pid, dur);
            } finally {
                loading.set(false);
            }
        });
    }

    private void sendStartCommand(String pid, int dur) {
        Path outputFile = ctx.getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "jfr-old-objects");
        root.put("command", "start");
        root.put("duration", String.valueOf(dur));

        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 15000);
        PathUtils.deleteFile(outputFile);

        if (jo != null && "recording".equals(jo.getString("status"))) {
            if (ctx.runner != null) {
                ctx.runner.runOnRenderThread(() -> {
                    state = State.RECORDING;
                    lastPid = pid;
                });
            }
            scheduleResultsPoll(pid, dur);
        } else {
            if (ctx.runner != null) {
                ctx.runner.runOnRenderThread(() -> state = State.IDLE);
            }
        }
    }

    private void stopRecording() {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        state = State.LOADING_RESULTS;

        String pid = ctx.selectedPid;
        startDaemonThread("jfr-stop-" + pid, () -> {
            try {
                sendStopAndLoadResults(pid);
            } finally {
                loading.set(false);
            }
        });
    }

    private void sendStopAndLoadResults(String pid) {
        Path outputFile = ctx.getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "jfr-old-objects");
        root.put("command", "stop");

        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 30000);
        PathUtils.deleteFile(outputFile);

        if (jo != null && "completed".equals(jo.getString("status"))) {
            List<SampleEntry> result = parseSamples(jo);
            int count = jo.getIntegerOrDefault("sampleCount", 0);
            long durationMs = jo.getLongOrDefault("recordingDurationMs", 0);
            long endTime = jo.getLongOrDefault("recordingEndTime", System.currentTimeMillis());

            if (ctx.runner != null) {
                ctx.runner.runOnRenderThread(() -> {
                    samples = result;
                    sampleCount = count;
                    recordingDurationMs = durationMs;
                    recordingEndTime = endTime;
                    state = State.HAS_RESULTS;
                    tableState.select(0);
                    lastPid = pid;
                });
            }
        } else {
            if (ctx.runner != null) {
                ctx.runner.runOnRenderThread(() -> state = State.IDLE);
            }
        }
    }

    private void scheduleResultsPoll(String pid, int dur) {
        if (ctx.runner == null) {
            return;
        }
        startDaemonThread("jfr-poll-" + pid, () -> {
            try {
                Thread.sleep((dur + 3) * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (state != State.RECORDING) {
                return;
            }
            if (ctx.runner != null) {
                ctx.runner.runOnRenderThread(() -> state = State.LOADING_RESULTS);
            }
            loadQueryResults(pid);
        });
    }

    private void checkStatus() {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        String pid = ctx.selectedPid;
        startDaemonThread("jfr-status-" + pid, () -> {
            try {
                sendStatusCommand(pid);
            } finally {
                loading.set(false);
            }
        });
    }

    private void sendStatusCommand(String pid) {
        Path outputFile = ctx.getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "jfr-old-objects");
        root.put("command", "status");

        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 5000);
        PathUtils.deleteFile(outputFile);

        if (jo == null) {
            return;
        }

        String status = jo.getString("status");
        if ("recording".equals(status)) {
            long startTime = jo.getLongOrDefault("startTime", System.currentTimeMillis());
            if (ctx.runner != null) {
                ctx.runner.runOnRenderThread(() -> {
                    state = State.RECORDING;
                    recordingStartTime = startTime;
                    if (jo.containsKey("durationSeconds")) {
                        duration = jo.getIntegerOrDefault("durationSeconds", duration);
                    }
                });
            }
        } else if ("completed".equals(status) && jo.getBooleanOrDefault("hasCachedResults", false)) {
            // there are cached results, load them
            loadQueryResults(pid);
        }
    }

    private void loadQueryResults(String pid) {
        Path outputFile = ctx.getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "jfr-old-objects");
        root.put("command", "query");

        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 10000);
        PathUtils.deleteFile(outputFile);

        if (jo != null && "completed".equals(jo.getString("status"))) {
            List<SampleEntry> result = parseSamples(jo);
            int count = jo.getIntegerOrDefault("sampleCount", 0);
            long durationMs = jo.getLongOrDefault("recordingDurationMs", 0);
            long endTime = jo.getLongOrDefault("recordingEndTime", System.currentTimeMillis());

            if (ctx.runner != null) {
                ctx.runner.runOnRenderThread(() -> {
                    samples = result;
                    sampleCount = count;
                    recordingDurationMs = durationMs;
                    recordingEndTime = endTime;
                    state = State.HAS_RESULTS;
                    tableState.select(0);
                    lastPid = pid;
                });
            }
        } else {
            if (ctx.runner != null) {
                ctx.runner.runOnRenderThread(() -> {
                    if (state == State.LOADING_RESULTS) {
                        state = State.IDLE;
                    }
                });
            }
        }
    }

    // ---- Sorting ----

    private List<SampleEntry> sortedSamples() {
        long minSize = MIN_SIZE_PRESETS[minSizeIndex];
        List<SampleEntry> result = new ArrayList<>(samples);
        if (minSize > 0) {
            result.removeIf(e -> e.totalSize < minSize);
        }
        result.sort((a, b) -> {
            int cmp = switch (sort) {
                case "allocationClass" -> compareStr(a.className, b.className);
                case "count" -> Integer.compare(b.count, a.count);
                case "objectAge" -> Long.compare(b.objectAge, a.objectAge);
                default -> Long.compare(b.totalSize, a.totalSize);
            };
            return sortReversed ? -cmp : cmp;
        });
        return result;
    }

    // ---- Parsing ----

    private List<SampleEntry> parseSamples(JsonObject jo) {
        JsonArray arr = (JsonArray) jo.get("samples");
        if (arr == null) {
            return Collections.emptyList();
        }

        List<SampleEntry> result = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JsonObject sj = (JsonObject) arr.get(i);
            SampleEntry entry = new SampleEntry();
            entry.num = i + 1;
            entry.className = sj.getStringOrDefault("allocationClass", "unknown");
            entry.count = sj.getIntegerOrDefault("count", 1);
            entry.totalSize = sj.getLongOrDefault("totalSize", 0);
            entry.allocationSize = sj.getLongOrDefault("allocationSize", 0);
            entry.lastKnownHeapUsage = sj.getLongOrDefault("lastKnownHeapUsage", 0);
            entry.objectAge = sj.getLongOrDefault("objectAge", 0);

            // reference chain
            JsonArray chain = (JsonArray) sj.get("referenceChain");
            if (chain != null && !chain.isEmpty()) {
                entry.referenceChain = new ArrayList<>();
                for (int j = 0; j < chain.size(); j++) {
                    JsonObject linkJson = (JsonObject) chain.get(j);
                    ChainLink link = new ChainLink();
                    link.type = linkJson.getString("type");
                    link.field = linkJson.getString("field");
                    link.description = linkJson.getString("description");
                    entry.referenceChain.add(link);
                }
                entry.chainSummary = buildChainSummary(entry.referenceChain);
            }

            // stack trace
            JsonArray st = (JsonArray) sj.get("stackTrace");
            if (st != null && !st.isEmpty()) {
                entry.stackTrace = new ArrayList<>();
                for (int j = 0; j < st.size(); j++) {
                    JsonObject fj = (JsonObject) st.get(j);
                    StackEntry se = new StackEntry();
                    se.method = fj.getStringOrDefault("method", "?");
                    se.line = fj.getIntegerOrDefault("line", 0);
                    entry.stackTrace.add(se);
                }
            }

            result.add(entry);
        }
        return result;
    }

    // ---- Helpers ----

    private static String buildChainSummary(List<ChainLink> chain) {
        if (chain == null || chain.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chain.size() && i < 3; i++) {
            ChainLink link = chain.get(i);
            if (i > 0) {
                sb.append(" → ");
            }
            if (link.type != null) {
                sb.append(abbreviateType(link.type));
            }
            if (link.field != null) {
                sb.append('.').append(link.field);
            }
        }
        if (chain.size() > 3) {
            sb.append(" → ...");
        }
        return sb.toString();
    }

    private static String abbreviateType(String type) {
        if (type == null) {
            return "?";
        }
        int dot = type.lastIndexOf('.');
        return dot >= 0 ? type.substring(dot + 1) : type;
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024L * 1024 * 1024) {
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    static String formatDuration(long ms) {
        if (ms < 1000) {
            return ms + "ms";
        } else if (ms < 60000) {
            return String.format(Locale.US, "%.1fs", ms / 1000.0);
        } else {
            long min = ms / 60000;
            long sec = (ms % 60000) / 1000;
            return min + "m " + sec + "s";
        }
    }

    private String sortLabel(String label, String column) {
        return MonitorContext.sortLabel(label, column, sort, sortReversed);
    }

    private Style sortStyle(String column) {
        return MonitorContext.sortStyle(column, sort);
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

    private static boolean isJdkFrame(String method) {
        return method != null
                && (method.startsWith("java.") || method.startsWith("javax.") || method.startsWith("jakarta.")
                        || method.startsWith("jdk.") || method.startsWith("sun.") || method.startsWith("com.sun."));
    }

    private static void startDaemonThread(String name, Runnable task) {
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.setName(name);
        t.start();
    }

    // ---- Data classes ----

    static class SampleEntry {
        int num;
        String className;
        long allocationSize;
        long lastKnownHeapUsage;
        long objectAge;
        int count = 1;
        long totalSize;
        String chainSummary;
        List<ChainLink> referenceChain;
        List<StackEntry> stackTrace;
    }

    static class ChainLink {
        String type;
        String field;
        String description;
    }

    static class StackEntry {
        String method;
        int line;
    }
}
