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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "trace-tui",
         description = "TUI exchange trace viewer",
         sortOptions = false)
public class CamelTraceTui extends CamelCommand {

    private static final int MAX_TRACES = 200;

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--refresh" },
                        description = "Refresh interval in milliseconds (default: ${DEFAULT-VALUE})",
                        defaultValue = "200")
    long refreshInterval = 200;

    @CommandLine.Option(names = { "--grep" },
                        description = "Filter traces by text")
    String grep;

    // State
    private final AtomicReference<List<TraceEntry>> traces = new AtomicReference<>(Collections.emptyList());
    private final TableState traceTableState = new TableState();
    private final Map<String, Long> traceFilePositions = new LinkedHashMap<>();

    private boolean showHeaders = true;
    private boolean showBody = true;
    private boolean followMode = true;
    private volatile long lastRefresh;

    public CamelTraceTui(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        TuiHelper.preloadClasses();

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
            if (ke.isUp()) {
                followMode = false;
                traceTableState.selectPrevious();
                return true;
            }
            if (ke.isDown()) {
                List<TraceEntry> current = traces.get();
                traceTableState.selectNext(current.size());
                return true;
            }
            if (ke.isCharIgnoreCase('h')) {
                showHeaders = !showHeaders;
                return true;
            }
            if (ke.isCharIgnoreCase('b')) {
                showBody = !showBody;
                return true;
            }
            if (ke.isCharIgnoreCase('f')) {
                followMode = !followMode;
                if (followMode) {
                    List<TraceEntry> current = traces.get();
                    if (!current.isEmpty()) {
                        traceTableState.select(current.size() - 1);
                    }
                }
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

        // Layout: header (3 rows) + trace list (50%) + detail panel (50%) + footer (1 row)
        List<Rect> mainChunks = Layout.vertical()
                .constraints(
                        Constraint.length(3),
                        Constraint.percentage(50),
                        Constraint.fill(),
                        Constraint.length(1))
                .split(area);

        renderHeader(frame, mainChunks.get(0));
        renderTraceList(frame, mainChunks.get(1));
        renderDetailPanel(frame, mainChunks.get(2));
        renderFooter(frame, mainChunks.get(3));
    }

    private void renderHeader(Frame frame, Rect area) {
        List<TraceEntry> current = traces.get();
        String filterInfo = grep != null ? "  filter: " + grep : "";

        Line titleLine = Line.from(
                Span.styled(" Camel Trace Viewer", Style.create().fg(Color.rgb(0xF6, 0x91, 0x23)).bold()),
                Span.raw("  "),
                Span.styled(current.size() + " trace(s)", Style.create().fg(Color.CYAN)),
                Span.raw("  "),
                Span.styled(followMode ? "[FOLLOW]" : "[SCROLL]",
                        Style.create().fg(followMode ? Color.GREEN : Color.YELLOW).bold()),
                Span.styled(filterInfo, Style.create().dim()));

        Block headerBlock = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(" Apache Camel Traces ")
                .build();

        frame.renderWidget(
                Paragraph.builder().text(Text.from(titleLine)).block(headerBlock).build(),
                area);
    }

    private void renderTraceList(Frame frame, Rect area) {
        List<TraceEntry> current = traces.get();

        // Auto-follow: select last entry
        if (followMode && !current.isEmpty()) {
            traceTableState.select(current.size() - 1);
        }

        List<Row> rows = new ArrayList<>();
        for (TraceEntry entry : current) {
            Style statusStyle = switch (entry.status) {
                case "Created" -> Style.create().fg(Color.CYAN);
                case "Routing", "Processing" -> Style.create().fg(Color.YELLOW);
                case "Sent" -> Style.create().fg(Color.GREEN);
                default -> Style.create().fg(Color.WHITE);
            };

            String bodyPreview = entry.bodyPreview != null ? truncate(entry.bodyPreview, 40) : "";

            rows.add(Row.from(
                    Cell.from(entry.timestamp != null ? truncate(entry.timestamp, 12) : ""),
                    Cell.from(entry.pid != null ? entry.pid : ""),
                    Cell.from(Span.styled(
                            entry.routeId != null ? truncate(entry.routeId, 15) : "",
                            Style.create().fg(Color.CYAN))),
                    Cell.from(entry.nodeId != null ? truncate(entry.nodeId, 15) : ""),
                    Cell.from(Span.styled(entry.status != null ? entry.status : "", statusStyle)),
                    Cell.from(entry.elapsed + "ms"),
                    Cell.from(bodyPreview)));
        }

        Row header = Row.from(
                Cell.from(Span.styled("TIME", Style.create().bold())),
                Cell.from(Span.styled("PID", Style.create().bold())),
                Cell.from(Span.styled("ROUTE", Style.create().bold())),
                Cell.from(Span.styled("NODE", Style.create().bold())),
                Cell.from(Span.styled("STATUS", Style.create().bold())),
                Cell.from(Span.styled("ELAPSED", Style.create().bold())),
                Cell.from(Span.styled("BODY", Style.create().bold())));

        Table table = Table.builder()
                .rows(rows)
                .header(header)
                .widths(
                        Constraint.length(12),
                        Constraint.length(8),
                        Constraint.length(15),
                        Constraint.length(15),
                        Constraint.length(12),
                        Constraint.length(10),
                        Constraint.fill())
                .highlightStyle(Style.create().fg(Color.WHITE).bold().onBlue())
                .block(Block.builder().borderType(BorderType.ROUNDED).title(" Traces ").build())
                .build();

        frame.renderStatefulWidget(table, area, traceTableState);
    }

    private void renderDetailPanel(Frame frame, Rect area) {
        List<TraceEntry> current = traces.get();
        Integer sel = traceTableState.selected();

        if (sel == null || sel < 0 || sel >= current.size()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled(" Select a trace entry to view details",
                                            Style.create().dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED)
                                    .title(" Detail ").build())
                            .build(),
                    area);
            return;
        }

        TraceEntry entry = current.get(sel);
        List<Line> lines = new ArrayList<>();

        // Exchange info
        lines.add(Line.from(
                Span.styled(" Exchange: ", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(entry.exchangeId != null ? entry.exchangeId : "")));
        lines.add(Line.from(
                Span.styled(" UID:      ", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(entry.uid != null ? entry.uid : "")));
        lines.add(Line.from(
                Span.styled(" Location: ", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(entry.location != null ? entry.location : "")));
        lines.add(Line.from(
                Span.styled(" Route:    ", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(entry.routeId != null ? entry.routeId : ""),
                Span.raw("  Node: "),
                Span.raw(entry.nodeId != null ? entry.nodeId : "")));
        lines.add(Line.from(
                Span.styled(" Status:   ", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(entry.status != null ? entry.status : ""),
                Span.raw("  Elapsed: "),
                Span.raw(entry.elapsed + "ms")));
        lines.add(Line.from(Span.raw("")));

        // Headers
        if (showHeaders && entry.headers != null && !entry.headers.isEmpty()) {
            lines.add(Line.from(Span.styled(" Headers:", Style.create().fg(Color.GREEN).bold())));
            for (Map.Entry<String, Object> h : entry.headers.entrySet()) {
                lines.add(Line.from(
                        Span.styled("   " + h.getKey(), Style.create().fg(Color.CYAN)),
                        Span.raw(" = "),
                        Span.raw(h.getValue() != null ? h.getValue().toString() : "null")));
            }
            lines.add(Line.from(Span.raw("")));
        }

        // Body
        if (showBody && entry.body != null) {
            lines.add(Line.from(Span.styled(" Body:", Style.create().fg(Color.GREEN).bold())));
            // Split body into lines for display
            String[] bodyLines = entry.body.split("\n");
            for (String bl : bodyLines) {
                lines.add(Line.from(Span.raw("   " + bl)));
            }
            lines.add(Line.from(Span.raw("")));
        }

        // Exchange properties
        if (entry.exchangeProperties != null && !entry.exchangeProperties.isEmpty()) {
            lines.add(Line.from(Span.styled(" Exchange Properties:", Style.create().fg(Color.GREEN).bold())));
            for (Map.Entry<String, Object> p : entry.exchangeProperties.entrySet()) {
                lines.add(Line.from(
                        Span.styled("   " + p.getKey(), Style.create().fg(Color.CYAN)),
                        Span.raw(" = "),
                        Span.raw(p.getValue() != null ? p.getValue().toString() : "null")));
            }
            lines.add(Line.from(Span.raw("")));
        }

        // Exchange variables
        if (entry.exchangeVariables != null && !entry.exchangeVariables.isEmpty()) {
            lines.add(Line.from(Span.styled(" Exchange Variables:", Style.create().fg(Color.GREEN).bold())));
            for (Map.Entry<String, Object> v : entry.exchangeVariables.entrySet()) {
                lines.add(Line.from(
                        Span.styled("   " + v.getKey(), Style.create().fg(Color.CYAN)),
                        Span.raw(" = "),
                        Span.raw(v.getValue() != null ? v.getValue().toString() : "null")));
            }
        }

        String title = String.format(" Detail [%s] ", entry.exchangeId != null ? truncate(entry.exchangeId, 30) : "");

        Paragraph detail = Paragraph.builder()
                .text(Text.from(lines))
                .overflow(Overflow.CLIP)
                .block(Block.builder().borderType(BorderType.ROUNDED).title(title).build())
                .build();

        frame.renderWidget(detail, area);
    }

    private void renderFooter(Frame frame, Rect area) {
        Line footer = Line.from(
                Span.styled(" q", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" quit  "),
                Span.styled("\u2191\u2193", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" navigate  "),
                Span.styled("h", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" headers" + (showHeaders ? " [on]" : " [off]") + "  "),
                Span.styled("b", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" body" + (showBody ? " [on]" : " [off]") + "  "),
                Span.styled("f", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" follow" + (followMode ? " [on]" : " [off]") + "  "),
                Span.styled("Refresh: " + refreshInterval + "ms", Style.create().dim()));

        frame.renderWidget(Paragraph.from(footer), area);
    }

    // ---- Data Loading ----

    private void refreshData() {
        lastRefresh = System.currentTimeMillis();
        try {
            List<Long> pids = findPids(name);
            List<TraceEntry> allTraces = new ArrayList<>(traces.get());

            for (Long pid : pids) {
                readTraceFile(Long.toString(pid), allTraces);
            }

            // Sort by timestamp
            allTraces.sort((a, b) -> {
                if (a.timestamp == null && b.timestamp == null) {
                    return 0;
                }
                if (a.timestamp == null) {
                    return -1;
                }
                if (b.timestamp == null) {
                    return 1;
                }
                return a.timestamp.compareTo(b.timestamp);
            });

            // Keep only last MAX_TRACES
            if (allTraces.size() > MAX_TRACES) {
                allTraces = new ArrayList<>(allTraces.subList(allTraces.size() - MAX_TRACES, allTraces.size()));
            }

            // Apply grep filter
            if (grep != null && !grep.isEmpty()) {
                String lowerGrep = grep.toLowerCase();
                allTraces = allTraces.stream()
                        .filter(t -> matchesGrep(t, lowerGrep))
                        .collect(java.util.stream.Collectors.toList());
            }

            traces.set(allTraces);
        } catch (Exception e) {
            // ignore refresh errors
        }
    }

    private boolean matchesGrep(TraceEntry entry, String lowerGrep) {
        if (entry.exchangeId != null && entry.exchangeId.toLowerCase().contains(lowerGrep)) {
            return true;
        }
        if (entry.routeId != null && entry.routeId.toLowerCase().contains(lowerGrep)) {
            return true;
        }
        if (entry.nodeId != null && entry.nodeId.toLowerCase().contains(lowerGrep)) {
            return true;
        }
        if (entry.status != null && entry.status.toLowerCase().contains(lowerGrep)) {
            return true;
        }
        if (entry.body != null && entry.body.toLowerCase().contains(lowerGrep)) {
            return true;
        }
        if (entry.location != null && entry.location.toLowerCase().contains(lowerGrep)) {
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void readTraceFile(String pid, List<TraceEntry> allTraces) {
        Path traceFile = CommandLineHelper.getCamelDir().resolve(pid + "-trace.json");
        if (!Files.exists(traceFile)) {
            return;
        }

        long lastPos = traceFilePositions.getOrDefault(pid, 0L);

        try (RandomAccessFile raf = new RandomAccessFile(traceFile.toFile(), "r")) {
            long length = raf.length();
            if (length <= lastPos) {
                return; // no new data
            }

            raf.seek(lastPos);
            // If we're resuming mid-file, skip any partial line
            if (lastPos > 0) {
                raf.readLine();
            }

            // Read remaining bytes
            long startPos = raf.getFilePointer();
            byte[] remaining = new byte[(int) (length - startPos)];
            raf.readFully(remaining);
            String content = new String(remaining, StandardCharsets.UTF_8);

            traceFilePositions.put(pid, length);

            // Each line is a separate JSON object
            String[] lines = content.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    JsonObject json = (JsonObject) Jsoner.deserialize(line);
                    TraceEntry entry = parseTraceEntry(json, pid);
                    if (entry != null) {
                        allTraces.add(entry);
                    }
                } catch (Exception e) {
                    // skip malformed lines
                }
            }
        } catch (IOException e) {
            // ignore
        }
    }

    @SuppressWarnings("unchecked")
    private TraceEntry parseTraceEntry(JsonObject json, String pid) {
        TraceEntry entry = new TraceEntry();
        entry.pid = pid;
        entry.uid = json.getString("uid");
        entry.exchangeId = json.getString("exchangeId");
        entry.timestamp = json.getString("timestamp");
        entry.routeId = json.getString("routeId");
        entry.nodeId = json.getString("nodeId");
        entry.location = json.getString("location");
        entry.status = json.getString("status");

        Object elapsedObj = json.get("elapsed");
        if (elapsedObj instanceof Number n) {
            entry.elapsed = n.longValue();
        } else if (elapsedObj != null) {
            try {
                entry.elapsed = Long.parseLong(elapsedObj.toString());
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        // Parse message object
        JsonObject message = (JsonObject) json.get("message");
        if (message != null) {
            // Headers
            Object headersObj = message.get("headers");
            if (headersObj instanceof Map) {
                entry.headers = new LinkedHashMap<>((Map<String, Object>) headersObj);
            }

            // Body
            Object bodyObj = message.get("body");
            if (bodyObj != null) {
                entry.body = bodyObj.toString();
                // Create a preview (first line, truncated)
                String preview = entry.body.replace("\n", " ").replace("\r", "");
                entry.bodyPreview = preview;
            }

            // Exchange properties
            Object propsObj = message.get("exchangeProperties");
            if (propsObj instanceof Map) {
                entry.exchangeProperties = new LinkedHashMap<>((Map<String, Object>) propsObj);
            }

            // Exchange variables
            Object varsObj = message.get("exchangeVariables");
            if (varsObj instanceof Map) {
                entry.exchangeVariables = new LinkedHashMap<>((Map<String, Object>) varsObj);
            }
        }

        return entry;
    }

    // ---- Helpers ----

    private List<Long> findPids(String name) {
        return TuiHelper.findPids(name, this::getStatusFile);
    }

    private JsonObject loadStatus(long pid) {
        return TuiHelper.loadStatus(pid, this::getStatusFile);
    }

    private static String truncate(String s, int max) {
        return TuiHelper.truncate(s, max);
    }

    // ---- Data Classes ----

    static class TraceEntry {
        String pid;
        String uid;
        String exchangeId;
        String timestamp;
        String routeId;
        String nodeId;
        String location;
        String status;
        long elapsed;
        String body;
        String bodyPreview;
        Map<String, Object> headers;
        Map<String, Object> exchangeProperties;
        Map<String, Object> exchangeVariables;
    }
}
