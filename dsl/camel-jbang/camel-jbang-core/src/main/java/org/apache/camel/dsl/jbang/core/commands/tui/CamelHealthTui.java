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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.TickEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.gauge.Gauge;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "health-tui",
         description = "TUI health check dashboard",
         sortOptions = false)
public class CamelHealthTui extends CamelCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--refresh" },
                        description = "Refresh interval in milliseconds (default: ${DEFAULT-VALUE})",
                        defaultValue = "1000")
    long refreshInterval = 1000;

    // State
    private final AtomicReference<List<HealthRow>> data = new AtomicReference<>(Collections.emptyList());
    private final TableState tableState = new TableState();
    private boolean showOnlyDown;
    private volatile long lastRefresh;

    // Memory info for the selected row's integration
    private long selectedHeapUsed;
    private long selectedHeapMax;

    public CamelHealthTui(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // Eagerly load classes used by the input reader thread and picocli
        // post-processing to avoid ClassNotFoundException during shutdown
        // when the Spring Boot LaunchedClassLoader may already be closing
        try {
            Class.forName("dev.tamboui.tui.event.KeyModifiers");
            Class.forName("dev.tamboui.tui.event.KeyEvent");
            Class.forName("dev.tamboui.tui.event.KeyCode");
            Class.forName("picocli.CommandLine$IExitCodeGenerator");
        } catch (ClassNotFoundException e) {
            // ignore
        }

        // Initial data load
        refreshData();

        try (var tui = TuiRunner.create()) {
            sun.misc.Signal.handle(new sun.misc.Signal("INT"), sig -> tui.quit());
            tui.run(
                    this::handleEvent,
                    this::render);
        }
        return 0;
    }

    // ---- Event Handling ----

    private boolean handleEvent(Event event, TuiRunner runner) {
        if (event instanceof KeyEvent ke) {
            if (ke.isQuit() || ke.isCharIgnoreCase('q') || ke.isKey(KeyCode.ESCAPE)) {
                runner.quit();
                return true;
            }
            if (ke.isChar('r')) {
                refreshData();
                return true;
            }
            if (ke.isChar('d')) {
                showOnlyDown = !showOnlyDown;
                return true;
            }
            if (ke.isUp()) {
                tableState.selectPrevious();
                return true;
            }
            if (ke.isDown()) {
                List<HealthRow> rows = getFilteredRows();
                tableState.selectNext(rows.size());
                return true;
            }
        }
        if (event instanceof TickEvent) {
            long now = System.currentTimeMillis();
            if (now - lastRefresh >= refreshInterval) {
                refreshData();
            }
            return true;
        }
        return false;
    }

    // ---- Rendering ----

    private void render(Frame frame) {
        Rect area = frame.area();

        // Layout: header (3 rows) + health table (fill) + memory gauge (3 rows) + footer (1 row)
        List<Rect> chunks = Layout.vertical()
                .constraints(
                        Constraint.length(3),
                        Constraint.fill(),
                        Constraint.length(3),
                        Constraint.length(1))
                .split(area);

        renderHeader(frame, chunks.get(0));
        renderHealthTable(frame, chunks.get(1));
        renderMemoryGauge(frame, chunks.get(2));
        renderFooter(frame, chunks.get(3));
    }

    private void renderHeader(Frame frame, Rect area) {
        List<HealthRow> allRows = data.get();
        long upCount = allRows.stream().filter(r -> "UP".equals(r.state)).count();
        long downCount = allRows.stream().filter(r -> "DOWN".equals(r.state)).count();

        Line titleLine = Line.from(
                Span.styled(" Health Dashboard", Style.create().fg(Color.rgb(0xF6, 0x91, 0x23)).bold()),
                Span.raw("  "),
                Span.styled(upCount + " UP", Style.create().fg(Color.GREEN).bold()),
                Span.raw(", "),
                downCount > 0
                        ? Span.styled(downCount + " DOWN", Style.create().fg(Color.RED).bold())
                        : Span.styled("0 DOWN", Style.create().fg(Color.GREEN)),
                showOnlyDown ? Span.styled("  [DOWN only]", Style.create().fg(Color.YELLOW)) : Span.raw(""));

        Block headerBlock = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(" Camel Health Checks ")
                .build();

        frame.renderWidget(
                Paragraph.builder().text(Text.from(titleLine)).block(headerBlock).build(),
                area);
    }

    private void renderHealthTable(Frame frame, Rect area) {
        List<HealthRow> rows = getFilteredRows();

        // Update selected integration's memory info
        Integer sel = tableState.selected();
        if (sel != null && sel >= 0 && sel < rows.size()) {
            HealthRow selected = rows.get(sel);
            selectedHeapUsed = selected.heapMemUsed;
            selectedHeapMax = selected.heapMemMax;
        } else if (!rows.isEmpty()) {
            selectedHeapUsed = rows.get(0).heapMemUsed;
            selectedHeapMax = rows.get(0).heapMemMax;
        } else {
            selectedHeapUsed = 0;
            selectedHeapMax = 0;
        }

        List<Row> tableRows = new ArrayList<>();
        for (HealthRow hr : rows) {
            Style stateStyle;
            String icon;
            if ("UP".equals(hr.state)) {
                stateStyle = Style.create().fg(Color.GREEN);
                icon = "\u2714 ";
            } else if ("DOWN".equals(hr.state)) {
                stateStyle = Style.create().fg(Color.RED);
                icon = "\u2716 ";
            } else {
                stateStyle = Style.create().fg(Color.YELLOW);
                icon = "\u26A0 ";
            }

            String rate = "";
            if (hr.readiness) {
                rate += "R";
            }
            if (hr.liveness) {
                rate += rate.isEmpty() ? "L" : "/L";
            }

            tableRows.add(Row.from(
                    Cell.from(hr.pid),
                    Cell.from(Span.styled(truncate(hr.integrationName, 20), Style.create().fg(Color.CYAN))),
                    Cell.from(hr.group != null ? truncate(hr.group, 12) : ""),
                    Cell.from(Span.styled(truncate(hr.checkId, 25), Style.create().fg(Color.CYAN))),
                    Cell.from(Span.styled(icon + hr.state, stateStyle)),
                    Cell.from(rate),
                    Cell.from(hr.since != null ? hr.since : ""),
                    Cell.from(hr.message != null ? truncate(hr.message, 40) : "")));
        }

        if (tableRows.isEmpty()) {
            tableRows.add(Row.from(
                    Cell.from(""),
                    Cell.from(Span.styled("No health checks found", Style.create().dim())),
                    Cell.from(""),
                    Cell.from(""),
                    Cell.from(""),
                    Cell.from(""),
                    Cell.from(""),
                    Cell.from("")));
        }

        Row header = Row.from(
                Cell.from(Span.styled("PID", Style.create().bold())),
                Cell.from(Span.styled("NAME", Style.create().bold())),
                Cell.from(Span.styled("GROUP", Style.create().bold())),
                Cell.from(Span.styled("CHECK", Style.create().bold())),
                Cell.from(Span.styled("STATUS", Style.create().bold())),
                Cell.from(Span.styled("RATE", Style.create().bold())),
                Cell.from(Span.styled("SINCE", Style.create().bold())),
                Cell.from(Span.styled("MESSAGE", Style.create().bold())));

        Table table = Table.builder()
                .rows(tableRows)
                .header(header)
                .widths(
                        Constraint.length(8),
                        Constraint.length(20),
                        Constraint.length(12),
                        Constraint.length(25),
                        Constraint.length(10),
                        Constraint.length(6),
                        Constraint.length(8),
                        Constraint.fill())
                .highlightStyle(Style.create().fg(Color.WHITE).bold().onBlue())
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(showOnlyDown ? " Health Checks [DOWN only] " : " Health Checks ").build())
                .build();

        frame.renderStatefulWidget(table, area, tableState);
    }

    private void renderMemoryGauge(Frame frame, Rect area) {
        if (selectedHeapMax > 0) {
            int pct = (int) (100.0 * selectedHeapUsed / selectedHeapMax);
            Style gaugeStyle = pct > 80 ? Style.create().fg(Color.RED)
                    : pct > 60 ? Style.create().fg(Color.YELLOW) : Style.create().fg(Color.GREEN);
            Gauge gauge = Gauge.builder()
                    .percent(pct)
                    .label(String.format("Heap: %s / %s (%d%%)",
                            formatBytes(selectedHeapUsed), formatBytes(selectedHeapMax), pct))
                    .gaugeStyle(gaugeStyle)
                    .block(Block.builder().borderType(BorderType.ROUNDED).title(" Memory ").build())
                    .build();
            frame.renderWidget(gauge, area);
        } else {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(" No memory data", Style.create().dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).title(" Memory ").build())
                            .build(),
                    area);
        }
    }

    private void renderFooter(Frame frame, Rect area) {
        String refreshLabel = refreshInterval >= 1000
                ? (refreshInterval / 1000) + "s"
                : refreshInterval + "ms";

        Line footer = Line.from(
                Span.styled(" q", Style.create().fg(Color.YELLOW).bold()),
                Span.raw("/"),
                Span.styled("Esc", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" quit  "),
                Span.styled("\u2191\u2193", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" navigate  "),
                Span.styled("r", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" refresh  "),
                Span.styled("d", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" toggle DOWN  "),
                Span.styled("Refresh: " + refreshLabel, Style.create().dim()));

        frame.renderWidget(Paragraph.from(footer), area);
    }

    // ---- Data Loading ----

    private void refreshData() {
        lastRefresh = System.currentTimeMillis();
        try {
            List<HealthRow> rows = new ArrayList<>();
            List<Long> pids = findPids(name);
            ProcessHandle.allProcesses()
                    .filter(ph -> pids.contains(ph.pid()))
                    .forEach(ph -> {
                        JsonObject root = loadStatus(ph.pid());
                        if (root != null) {
                            parseHealthRows(ph, root, rows);
                        }
                    });
            data.set(rows);
        } catch (Exception e) {
            // ignore refresh errors
        }
    }

    @SuppressWarnings("unchecked")
    private void parseHealthRows(ProcessHandle ph, JsonObject root, List<HealthRow> rows) {
        JsonObject context = (JsonObject) root.get("context");
        if (context == null) {
            return;
        }

        String pid = Long.toString(ph.pid());
        String integrationName = context.getString("name");
        if ("CamelJBang".equals(integrationName)) {
            integrationName = ProcessHelper.extractName(root, ph);
        }

        String since = TimeUtils.printSince(
                ph.info().startInstant().map(Instant::toEpochMilli).orElse(0L));

        // Parse memory
        long heapUsed = 0;
        long heapMax = 0;
        JsonObject mem = (JsonObject) root.get("memory");
        if (mem != null) {
            heapUsed = mem.getLong("heapMemoryUsed");
            heapMax = mem.getLong("heapMemoryMax");
        }

        // Parse health checks
        JsonObject healthChecks = (JsonObject) root.get("healthChecks");
        if (healthChecks != null) {
            JsonArray checks = (JsonArray) healthChecks.get("checks");
            if (checks != null) {
                for (Object c : checks) {
                    JsonObject cj = (JsonObject) c;
                    HealthRow hr = new HealthRow();
                    hr.pid = pid;
                    hr.integrationName = integrationName;
                    hr.checkId = cj.getString("id");
                    hr.group = cj.getString("group");
                    hr.state = cj.getString("state");
                    hr.readiness = cj.getBooleanOrDefault("readiness", false);
                    hr.liveness = cj.getBooleanOrDefault("liveness", false);
                    hr.since = since;
                    hr.heapMemUsed = heapUsed;
                    hr.heapMemMax = heapMax;

                    // Extract failure message from details
                    JsonObject details = (JsonObject) cj.get("details");
                    if (details != null && details.containsKey("failure.error.message")) {
                        hr.message = details.getString("failure.error.message");
                    }

                    rows.add(hr);
                }
            }
        }
    }

    // ---- Helpers ----

    private List<HealthRow> getFilteredRows() {
        List<HealthRow> allRows = data.get();
        if (showOnlyDown) {
            return allRows.stream().filter(r -> "DOWN".equals(r.state)).toList();
        }
        return allRows;
    }

    private List<Long> findPids(String name) {
        List<Long> pids = new ArrayList<>();
        final long cur = ProcessHandle.current().pid();
        String pattern = name;
        if (!pattern.matches("\\d+") && !pattern.endsWith("*")) {
            pattern = pattern + "*";
        }
        final String pat = pattern;
        ProcessHandle.allProcesses()
                .filter(ph -> ph.pid() != cur)
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    if (root != null) {
                        String pName = ProcessHelper.extractName(root, ph);
                        pName = FileUtil.onlyName(pName);
                        if (pName != null && !pName.isEmpty() && PatternHelper.matchPattern(pName, pat)) {
                            pids.add(ph.pid());
                        } else {
                            JsonObject context = (JsonObject) root.get("context");
                            if (context != null) {
                                pName = context.getString("name");
                                if ("CamelJBang".equals(pName)) {
                                    pName = null;
                                }
                                if (pName != null && !pName.isEmpty() && PatternHelper.matchPattern(pName, pat)) {
                                    pids.add(ph.pid());
                                }
                            }
                        }
                    }
                });
        return pids;
    }

    private JsonObject loadStatus(long pid) {
        try {
            Path f = getStatusFile(Long.toString(pid));
            if (f != null && Files.exists(f)) {
                String text = Files.readString(f);
                return (JsonObject) Jsoner.deserialize(text);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max - 1) + "\u2026" : s;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + "K";
        }
        return (bytes / (1024 * 1024)) + "M";
    }

    // ---- Data Class ----

    static class HealthRow {
        String pid;
        String integrationName;
        String group;
        String checkId;
        String state;
        boolean readiness;
        boolean liveness;
        String since;
        String message;
        long heapMemUsed;
        long heapMemMax;
    }
}
