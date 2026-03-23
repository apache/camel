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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "top-tui",
         description = "Live TUI dashboard for route performance",
         sortOptions = false)
public class CamelTopTui extends CamelCommand {

    private static final String[] SORT_COLUMNS = { "mean", "max", "total", "failed", "name" };

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--refresh" },
                        description = "Refresh interval in milliseconds (default: ${DEFAULT-VALUE})",
                        defaultValue = "500")
    long refreshInterval = 500;

    @CommandLine.Option(names = { "--sort" },
                        description = "Sort column: mean, max, total, failed, name (default: ${DEFAULT-VALUE})",
                        defaultValue = "mean")
    String sort = "mean";

    // State
    private final AtomicReference<List<RouteRow>> data = new AtomicReference<>(Collections.emptyList());
    private final TableState tableState = new TableState();
    private int sortIndex;
    private volatile long lastRefresh;

    public CamelTopTui(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        // Eagerly load classes used by the input reader thread and picocli
        // post-processing to avoid ClassNotFoundException during shutdown
        try {
            Class.forName("dev.tamboui.tui.event.KeyModifiers");
            Class.forName("dev.tamboui.tui.event.KeyEvent");
            Class.forName("dev.tamboui.tui.event.KeyCode");
            Class.forName("picocli.CommandLine$IExitCodeGenerator");
        } catch (ClassNotFoundException e) {
            // ignore
        }

        // Resolve initial sort index
        sortIndex = indexOfSort(sort);

        // Initial data load
        refreshData();

        try (var tui = TuiRunner.create()) {
            sun.misc.Signal.handle(new sun.misc.Signal("INT"), sig -> tui.quit());
            tui.run(this::handleEvent, this::render);
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
            if (ke.isCharIgnoreCase('s')) {
                // Cycle sort column
                sortIndex = (sortIndex + 1) % SORT_COLUMNS.length;
                sort = SORT_COLUMNS[sortIndex];
                sortData();
                return true;
            }
            if (ke.isUp()) {
                tableState.selectPrevious();
                return true;
            }
            if (ke.isDown()) {
                tableState.selectNext(data.get().size());
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

        // Layout: header (3 rows) + table (fill) + footer (1 row)
        List<Rect> chunks = Layout.vertical()
                .constraints(
                        Constraint.length(3),
                        Constraint.fill(),
                        Constraint.length(1))
                .split(area);

        renderHeader(frame, chunks.get(0));
        renderTable(frame, chunks.get(1));
        renderFooter(frame, chunks.get(2));
    }

    private void renderHeader(Frame frame, Rect area) {
        List<RouteRow> rows = data.get();
        long totalExchanges = rows.stream().mapToLong(r -> r.total).sum();
        long totalFailed = rows.stream().mapToLong(r -> r.failed).sum();
        int routeCount = rows.size();
        long pidCount = rows.stream().map(r -> r.pid).distinct().count();

        Line titleLine = Line.from(
                Span.styled(" Camel Top", Style.create().fg(Color.rgb(0xF6, 0x91, 0x23)).bold()),
                Span.raw("  "),
                Span.styled(pidCount + " integration(s)", Style.create().fg(Color.CYAN)),
                Span.raw("  "),
                Span.styled(routeCount + " route(s)", Style.create().fg(Color.GREEN)),
                Span.raw("  "),
                Span.styled("total: " + totalExchanges, Style.create().fg(Color.WHITE)),
                Span.raw("  "),
                Span.styled("failed: " + totalFailed,
                        totalFailed > 0 ? Style.create().fg(Color.RED).bold() : Style.create().dim()),
                Span.raw("  "),
                Span.styled("sort: " + sort, Style.create().fg(Color.YELLOW)));

        Block headerBlock = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(" Apache Camel - Route Performance ")
                .build();

        frame.renderWidget(
                Paragraph.builder().text(Text.from(titleLine)).block(headerBlock).build(),
                area);
    }

    private void renderTable(Frame frame, Rect area) {
        List<RouteRow> rows = data.get();

        List<Row> tableRows = new ArrayList<>();
        for (RouteRow r : rows) {
            Style statusStyle = "Started".equals(r.state)
                    ? Style.create().fg(Color.GREEN)
                    : Style.create().fg(Color.RED);

            Style failStyle = r.failed > 0
                    ? Style.create().fg(Color.RED).bold()
                    : Style.create();

            tableRows.add(Row.from(
                    Cell.from(r.pid),
                    Cell.from(Span.styled(truncate(r.name, 20), Style.create().fg(Color.CYAN))),
                    Cell.from(Span.styled(truncate(r.routeId, 18), Style.create().fg(Color.WHITE))),
                    Cell.from(truncate(r.from, 30)),
                    Cell.from(Span.styled(r.state != null ? r.state : "", statusStyle)),
                    Cell.from(String.valueOf(r.total)),
                    Cell.from(Span.styled(String.valueOf(r.failed), failStyle)),
                    Cell.from(String.valueOf(r.inflight)),
                    Cell.from(r.mean >= 0 ? String.valueOf(r.mean) : ""),
                    Cell.from(r.min >= 0 ? String.valueOf(r.min) : ""),
                    Cell.from(r.max >= 0 ? String.valueOf(r.max) : ""),
                    Cell.from(r.last >= 0 ? String.valueOf(r.last) : ""),
                    Cell.from(r.throughput != null ? r.throughput : "")));
        }

        Row header = Row.from(
                Cell.from(Span.styled("PID", Style.create().bold())),
                Cell.from(Span.styled("NAME", Style.create().bold())),
                Cell.from(Span.styled("ROUTE", Style.create().bold())),
                Cell.from(Span.styled("FROM", Style.create().bold())),
                Cell.from(Span.styled("STATUS", Style.create().bold())),
                Cell.from(Span.styled("TOTAL", Style.create().bold())),
                Cell.from(Span.styled("FAIL", Style.create().bold())),
                Cell.from(Span.styled("INFLIGHT", Style.create().bold())),
                Cell.from(Span.styled(sortLabel("MEAN", "mean"), sortStyle("mean"))),
                Cell.from(Span.styled(sortLabel("MIN", "min"), sortStyle("min"))),
                Cell.from(Span.styled(sortLabel("MAX", "max"), sortStyle("max"))),
                Cell.from(Span.styled("LAST", Style.create().bold())),
                Cell.from(Span.styled("THRUPUT", Style.create().bold())));

        Table table = Table.builder()
                .rows(tableRows)
                .header(header)
                .widths(
                        Constraint.length(8),   // PID
                        Constraint.length(20),  // NAME
                        Constraint.length(18),  // ROUTE
                        Constraint.fill(),      // FROM
                        Constraint.length(9),   // STATUS
                        Constraint.length(8),   // TOTAL
                        Constraint.length(6),   // FAIL
                        Constraint.length(9),   // INFLIGHT
                        Constraint.length(6),   // MEAN
                        Constraint.length(6),   // MIN
                        Constraint.length(6),   // MAX
                        Constraint.length(6),   // LAST
                        Constraint.length(9))   // THRUPUT
                .highlightStyle(Style.create().fg(Color.WHITE).bold().onBlue())
                .block(Block.builder().borderType(BorderType.ROUNDED).title(" Routes ").build())
                .build();

        frame.renderStatefulWidget(table, area, tableState);
    }

    private void renderFooter(Frame frame, Rect area) {
        String refreshLabel = refreshInterval >= 1000
                ? (refreshInterval / 1000) + "s"
                : refreshInterval + "ms";

        Line footer = Line.from(
                Span.styled(" q", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" quit  "),
                Span.styled("s", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" sort  "),
                Span.styled("r", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" refresh  "),
                Span.styled("\u2191\u2193", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" navigate  "),
                Span.styled("Refresh: " + refreshLabel, Style.create().dim()));

        frame.renderWidget(Paragraph.from(footer), area);
    }

    // ---- Data Loading ----

    @SuppressWarnings("unchecked")
    private void refreshData() {
        lastRefresh = System.currentTimeMillis();
        try {
            List<RouteRow> rows = new ArrayList<>();
            List<Long> pids = findPids(name);
            ProcessHandle.allProcesses()
                    .filter(ph -> pids.contains(ph.pid()))
                    .forEach(ph -> {
                        JsonObject root = loadStatus(ph.pid());
                        if (root != null) {
                            JsonObject context = (JsonObject) root.get("context");
                            if (context == null) {
                                return;
                            }
                            String integrationName = context.getString("name");
                            if ("CamelJBang".equals(integrationName)) {
                                integrationName = ProcessHelper.extractName(root, ph);
                            }
                            String pid = Long.toString(ph.pid());

                            JsonArray routesArray = (JsonArray) root.get("routes");
                            if (routesArray != null) {
                                for (Object r : routesArray) {
                                    JsonObject rj = (JsonObject) r;
                                    RouteRow row = new RouteRow();
                                    row.pid = pid;
                                    row.name = integrationName;
                                    row.routeId = rj.getString("routeId");
                                    row.from = rj.getString("from");
                                    row.state = rj.getString("state");

                                    Map<String, ?> stats = rj.getMap("statistics");
                                    if (stats != null) {
                                        row.total = objToLong(stats.get("exchangesTotal"));
                                        row.failed = objToLong(stats.get("exchangesFailed"));
                                        row.inflight = objToLong(stats.get("exchangesInflight"));
                                        row.mean = objToLong(stats.get("meanProcessingTime"));
                                        row.min = objToLong(stats.get("minProcessingTime"));
                                        row.max = objToLong(stats.get("maxProcessingTime"));
                                        row.last = objToLong(stats.get("lastProcessingTime"));
                                        Object thp = stats.get("exchangesThroughput");
                                        if (thp != null) {
                                            row.throughput = thp.toString();
                                        }
                                    }
                                    rows.add(row);
                                }
                            }
                        }
                    });

            // Sort
            rows.sort(this::sortRow);
            data.set(rows);
        } catch (Exception e) {
            // ignore refresh errors
        }
    }

    private void sortData() {
        List<RouteRow> rows = new ArrayList<>(data.get());
        rows.sort(this::sortRow);
        data.set(rows);
    }

    private int sortRow(RouteRow o1, RouteRow o2) {
        switch (sort) {
            case "mean":
                return Long.compare(o2.mean, o1.mean); // highest first
            case "max":
                return Long.compare(o2.max, o1.max);
            case "total":
                return Long.compare(o2.total, o1.total);
            case "failed":
                return Long.compare(o2.failed, o1.failed);
            case "name":
                int c = o1.name != null && o2.name != null
                        ? o1.name.compareToIgnoreCase(o2.name)
                        : 0;
                if (c == 0) {
                    c = o1.routeId != null && o2.routeId != null
                            ? o1.routeId.compareToIgnoreCase(o2.routeId)
                            : 0;
                }
                return c;
            default:
                return 0;
        }
    }

    // ---- Helpers ----

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

    private static int indexOfSort(String s) {
        for (int i = 0; i < SORT_COLUMNS.length; i++) {
            if (SORT_COLUMNS[i].equals(s)) {
                return i;
            }
        }
        return 0;
    }

    private String sortLabel(String label, String column) {
        return sort.equals(column) ? label + "\u25BC" : label;
    }

    private Style sortStyle(String column) {
        return sort.equals(column)
                ? Style.create().fg(Color.YELLOW).bold()
                : Style.create().bold();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max - 1) + "\u2026" : s;
    }

    private static long objToLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o != null) {
            try {
                return Long.parseLong(o.toString());
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return 0;
    }

    // ---- Data Class ----

    static class RouteRow {
        String pid;
        String name;
        String routeId;
        String from;
        String state;
        long total;
        long failed;
        long inflight;
        long mean;
        long min;
        long max;
        long last;
        String throughput;
    }
}
