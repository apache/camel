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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

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
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class MetricsTab extends AbstractTableTab {

    private static final String[] FILTER_TYPES = { "all", "counter", "gauge", "timer", "longTaskTimer", "distribution" };

    private static final int MOUSE_SCROLL_LINES = 3;

    private static final Style LABEL = Style.EMPTY.dim();
    private static final Style VALUE = Style.EMPTY.fg(Theme.baseFg()).bold();
    private static final Style GOOD = Style.EMPTY.fg(Color.GREEN);
    private static final Style BAD = Style.EMPTY.fg(Color.LIGHT_RED);

    private final ScrollbarState scrollbarState = new ScrollbarState();
    private final ScrollbarState rawScrollbarState = new ScrollbarState();
    private boolean tableMode;
    private int lastRowCount;
    private String filterType = "all";
    private int filterIndex;

    private int camelPanelWidth = -1;
    private final DragSplit hSplit = new DragSplit();

    // raw metrics view
    private boolean showRaw;
    private List<String> rawLines = Collections.emptyList();
    private int rawScroll;
    private String rawTitle;
    private String rawContentType;
    private final AtomicBoolean rawLoading = new AtomicBoolean(false);

    MetricsTab(MonitorContext ctx) {
        super(ctx, "type", "name", "value");
        sortIndex = 1;
        sort = "name";
    }

    @Override
    protected int getRowCount() {
        return lastRowCount;
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (showRaw) {
            if (ke.isUp()) {
                rawScroll = Math.max(0, rawScroll - 1);
                return true;
            }
            if (ke.isDown()) {
                rawScroll++;
                return true;
            }
            if (ke.isPageUp()) {
                rawScroll = Math.max(0, rawScroll - 20);
                return true;
            }
            if (ke.isPageDown()) {
                rawScroll += 20;
                return true;
            }
            if (ke.isHome()) {
                rawScroll = 0;
                return true;
            }
            if (ke.isEnd()) {
                rawScroll = Integer.MAX_VALUE;
                return true;
            }
            if (ke.isKey(KeyCode.F5)) {
                loadRawMetrics();
                return true;
            }
            return false;
        }

        if (ke.isChar('r')) {
            IntegrationInfo info = ctx.findSelectedIntegration();
            if (info != null && findMetricsUrl(info) != null) {
                showRaw = true;
                rawScroll = 0;
                loadRawMetrics();
                return true;
            }
        }
        if (ke.isChar('d')) {
            tableMode = !tableMode;
            return true;
        }
        if (tableMode) {
            if (ke.isPageUp()) {
                for (int i = 0; i < 10; i++) {
                    tableState.selectPrevious();
                }
                return true;
            }
            if (ke.isPageDown()) {
                for (int i = 0; i < 10; i++) {
                    tableState.selectNext(lastRowCount);
                }
                return true;
            }
            if (ke.isHome()) {
                tableState.selectFirst();
                return true;
            }
            if (ke.isEnd()) {
                tableState.selectLast(lastRowCount);
                return true;
            }
            if (ke.isChar('f')) {
                filterIndex = (filterIndex + 1) % FILTER_TYPES.length;
                filterType = FILTER_TYPES[filterIndex];
                return true;
            }
            return super.handleKeyEvent(ke);
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (showRaw) {
            showRaw = false;
            return true;
        }
        if (tableMode) {
            tableMode = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        if (hSplit.handleMouse(me, me.x())) {
            if (hSplit.isDragging()) {
                camelPanelWidth = Math.max(20, Math.min(me.x() - area.x(), area.width() - 20));
            }
            return true;
        }
        if (!showRaw && tableMode) {
            if (handleTableClick(me, lastTableArea, tableState, lastRowCount)) {
                return true;
            }
        }
        if (showRaw) {
            if (me.kind() == MouseEventKind.SCROLL_UP) {
                rawScroll = Math.max(0, rawScroll - MOUSE_SCROLL_LINES);
                return true;
            }
            if (me.kind() == MouseEventKind.SCROLL_DOWN) {
                rawScroll += MOUSE_SCROLL_LINES;
                return true;
            }
        }
        return false;
    }

    @Override
    public void navigateUp() {
        if (tableMode) {
            tableState.selectPrevious();
        }
    }

    @Override
    public void navigateDown() {
        if (tableMode) {
            tableState.selectNext(lastRowCount);
        }
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        if (info.meters.isEmpty()) {
            Paragraph p = Paragraph.from(Line.from(
                    Span.styled("No metrics available. Run with --observe to enable micrometer.", LABEL)));
            frame.renderWidget(p, area);
            return;
        }

        if (showRaw) {
            hSplit.clearBorderPos();
            renderRaw(frame, area);
        } else if (tableMode) {
            hSplit.clearBorderPos();
            renderTable(frame, area, info);
        } else {
            renderDashboard(frame, area, info);
        }
    }

    // ---- Dashboard mode ----

    private void renderDashboard(Frame frame, Rect area, IntegrationInfo info) {
        String metricsUrl = findMetricsUrl(info);
        Rect panelArea = area;
        if (metricsUrl != null) {
            List<Rect> vParts = Layout.vertical()
                    .constraints(Constraint.length(1), Constraint.fill())
                    .split(area);
            frame.renderWidget(Paragraph.from(Line.from(
                    Span.styled("  Endpoint: ", LABEL),
                    Span.styled(metricsUrl, Style.EMPTY.fg(Color.CYAN)))), vParts.get(0));
            panelArea = vParts.get(1);
        }

        int cpw = camelPanelWidth >= 0 ? camelPanelWidth : panelArea.width() / 2;
        cpw = Math.max(20, Math.min(cpw, panelArea.width() - 20));
        List<Rect> panels = Layout.horizontal()
                .constraints(Constraint.length(cpw), Constraint.fill())
                .split(panelArea);
        hSplit.setBorderPos(panels.get(1).x());

        renderCamelPanel(frame, panels.get(0), info.meters);
        renderJvmPanel(frame, panels.get(1), info.meters);
    }

    private void renderCamelPanel(Frame frame, Rect area, List<MicrometerMeterInfo> meters) {
        List<Line> lines = new ArrayList<>();

        // Exchanges section
        lines.add(Line.from(Span.styled("  Exchanges", Theme.label().bold())));
        lines.add(Line.empty());

        long total = counterValue(meters, "camel.exchanges.total");
        long failed = counterValue(meters, "camel.exchanges.failed");
        long succeeded = counterValue(meters, "camel.exchanges.succeeded");
        long inflight = gaugeValueLong(meters, "camel.exchanges.inflight");
        long extReloaded = counterValue(meters, "camel.exchanges.external.reloaded");

        lines.add(Line.from(
                Span.styled("    Total:     ", LABEL),
                Span.styled(formatNumber(total), VALUE),
                Span.styled("    Inflight:  ", LABEL),
                Span.styled(String.valueOf(inflight), inflight > 0 ? GOOD : VALUE)));
        lines.add(Line.from(
                Span.styled("    Succeeded: ", LABEL),
                Span.styled(formatNumber(succeeded), GOOD),
                Span.styled("    Failed:    ", LABEL),
                Span.styled(formatNumber(failed), failed > 0 ? BAD : VALUE)));
        if (extReloaded > 0) {
            lines.add(Line.from(
                    Span.styled("    Ext Reloaded: ", LABEL),
                    Span.styled(formatNumber(extReloaded), VALUE)));
        }
        lines.add(Line.empty());

        // Route timers
        List<MicrometerMeterInfo> routeTimers = findMeters(meters, "camel.route.policy");
        if (!routeTimers.isEmpty()) {
            lines.add(Line.from(Span.styled("  Route Timers", Theme.label().bold()),
                    Span.styled("                     mean / max", LABEL)));
            lines.add(Line.empty());
            for (MicrometerMeterInfo rt : routeTimers) {
                String routeId = tagValue(rt, "routeId");
                if (routeId == null) {
                    routeId = tagValue(rt, "routeid");
                }
                if (routeId == null) {
                    routeId = "?";
                }
                String timing = String.format("%dms / %dms",
                        rt.mean != null ? rt.mean : 0,
                        rt.max != null ? rt.max : 0);
                int pad = Math.max(1, 30 - routeId.length());
                lines.add(Line.from(
                        Span.styled("    " + routeId, Style.EMPTY.fg(Color.CYAN)),
                        Span.styled(" ".repeat(pad), Style.EMPTY),
                        Span.styled(timing, VALUE)));
            }
        }

        // Exchange event notifier timers
        List<MicrometerMeterInfo> eventTimers = findMeters(meters, "camel.exchange.event.notifier");
        if (!eventTimers.isEmpty()) {
            lines.add(Line.empty());
            lines.add(Line.from(Span.styled("  Event Notifiers", Theme.label().bold()),
                    Span.styled("                  mean / max", LABEL)));
            lines.add(Line.empty());
            for (MicrometerMeterInfo et : eventTimers) {
                String name = et.name != null ? et.name : "?";
                String shortName = name.replace("camel.exchange.event.notifier.", "");
                String timing = String.format("%dms / %dms",
                        et.mean != null ? et.mean : 0,
                        et.max != null ? et.max : 0);
                int pad = Math.max(1, 30 - shortName.length());
                lines.add(Line.from(
                        Span.styled("    " + shortName, Style.EMPTY.fg(Color.CYAN)),
                        Span.styled(" ".repeat(pad), Style.EMPTY),
                        Span.styled(timing, VALUE)));
            }
        }

        Paragraph paragraph = Paragraph.builder()
                .text(Text.from(lines))
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Camel ").build())
                .build();
        frame.renderWidget(paragraph, area);
    }

    private void renderJvmPanel(Frame frame, Rect area, List<MicrometerMeterInfo> meters) {
        List<Line> lines = new ArrayList<>();

        // Memory section
        lines.add(Line.from(Span.styled("  Memory", Theme.label().bold())));
        lines.add(Line.empty());

        double heapUsed = gaugeValue(meters, "jvm.memory.used", "area", "heap");
        double heapMax = gaugeValue(meters, "jvm.memory.max", "area", "heap");
        double heapCommitted = gaugeValue(meters, "jvm.memory.committed", "area", "heap");
        double nonHeapUsed = gaugeValue(meters, "jvm.memory.used", "area", "nonheap");

        String heapStr = formatBytes(heapUsed);
        String heapMaxStr = heapMax > 0 ? formatBytes(heapMax) : "-";
        String memBar = heapMax > 0 ? memoryBar(heapUsed, heapMax, 10) : "";

        lines.add(Line.from(
                Span.styled("    Heap:       ", LABEL),
                Span.styled(heapStr, VALUE),
                Span.styled(" / ", LABEL),
                Span.styled(heapMaxStr, VALUE),
                Span.styled("  ", Style.EMPTY),
                Span.styled(memBar, heapMax > 0 && heapUsed / heapMax > 0.85 ? BAD : GOOD)));

        if (heapCommitted > 0) {
            lines.add(Line.from(
                    Span.styled("    Committed:  ", LABEL),
                    Span.styled(formatBytes(heapCommitted), VALUE)));
        }
        lines.add(Line.from(
                Span.styled("    Non-heap:   ", LABEL),
                Span.styled(formatBytes(nonHeapUsed), VALUE)));
        lines.add(Line.empty());

        // Runtime section
        lines.add(Line.from(Span.styled("  Runtime", Theme.label().bold())));
        lines.add(Line.empty());

        double cpuProcess = gaugeValue(meters, "process.cpu.usage");
        double cpuSystem = gaugeValue(meters, "system.cpu.usage");
        double threads = gaugeValue(meters, "jvm.threads.live");
        if (threads == 0) {
            threads = gaugeValue(meters, "jvm.threads.current");
        }
        double peakThreads = gaugeValue(meters, "jvm.threads.peak");
        double daemonThreads = gaugeValue(meters, "jvm.threads.daemon");

        if (cpuProcess >= 0) {
            lines.add(Line.from(
                    Span.styled("    CPU (proc): ", LABEL),
                    Span.styled(String.format("%.1f%%", cpuProcess * 100), VALUE)));
        }
        if (cpuSystem >= 0) {
            lines.add(Line.from(
                    Span.styled("    CPU (sys):  ", LABEL),
                    Span.styled(String.format("%.1f%%", cpuSystem * 100), VALUE)));
        }
        if (threads > 0) {
            lines.add(Line.from(
                    Span.styled("    Threads:    ", LABEL),
                    Span.styled(String.valueOf((long) threads), VALUE),
                    peakThreads > 0
                            ? Span.styled("  (peak: " + (long) peakThreads + ")", LABEL)
                            : Span.raw("")));
        }
        if (daemonThreads > 0) {
            lines.add(Line.from(
                    Span.styled("    Daemon:     ", LABEL),
                    Span.styled(String.valueOf((long) daemonThreads), VALUE)));
        }

        // GC
        long gcCount = counterValue(meters, "jvm.gc.pause");
        List<MicrometerMeterInfo> gcTimers = findMeters(meters, "jvm.gc.pause");
        if (!gcTimers.isEmpty()) {
            lines.add(Line.empty());
            lines.add(Line.from(Span.styled("  Garbage Collection", Theme.label().bold())));
            lines.add(Line.empty());
            for (MicrometerMeterInfo gc : gcTimers) {
                String cause = tagValue(gc, "cause");
                String action = tagValue(gc, "action");
                String label = cause != null ? cause : (action != null ? action : "GC");
                lines.add(Line.from(
                        Span.styled("    " + label + ": ", LABEL),
                        Span.styled("count=", LABEL),
                        Span.styled(String.valueOf(gc.count != null ? gc.count : 0), VALUE),
                        Span.styled(", total=", LABEL),
                        Span.styled((gc.total != null ? gc.total : 0) + "ms", VALUE)));
            }
        }

        Paragraph paragraph = Paragraph.builder()
                .text(Text.from(lines))
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" JVM ").build())
                .build();
        frame.renderWidget(paragraph, area);
    }

    // ---- Table mode ----

    private void renderTable(Frame frame, Rect area, IntegrationInfo info) {
        List<MicrometerMeterInfo> filtered = info.meters;
        if (!"all".equals(filterType)) {
            filtered = filtered.stream()
                    .filter(m -> filterType.equals(m.type))
                    .collect(Collectors.toList());
        }

        List<MicrometerMeterInfo> sorted = new ArrayList<>(filtered);
        sorted.sort(this::sortMeter);

        List<Row> rows = new ArrayList<>();
        for (MicrometerMeterInfo m : sorted) {
            String typeLabel = typeLabel(m.type);
            Style typeStyle = typeStyle(m.type);
            String value = formatValue(m);
            String tags = formatTags(m);

            rows.add(Row.from(
                    Cell.from(Span.styled(typeLabel, typeStyle)),
                    Cell.from(Span.styled(m.name != null ? m.name : "", Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(value),
                    Cell.from(Span.styled(tags, Style.EMPTY.dim()))));
        }

        lastRowCount = rows.size();
        if (!rows.isEmpty() && tableState.selected() == null) {
            tableState.select(0);
        }

        if (rows.isEmpty()) {
            rows.add(emptyRow("No " + filterType + " metrics", 4));
        }

        String title = " Metrics";
        if (!"all".equals(filterType)) {
            title += " filter:" + filterType;
        }
        title += " (" + sorted.size() + ") ";

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("TYPE", "type"), sortStyle("type"))),
                        Cell.from(Span.styled(sortLabel("NAME", "name"), sortStyle("name"))),
                        Cell.from(Span.styled(sortLabel("VALUE", "value"), sortStyle("value"))),
                        Cell.from(Span.styled("TAGS", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(12),
                        Constraint.length(40),
                        Constraint.percentage(30),
                        Constraint.fill())
                .highlightStyle(Theme.selectionBg())
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(title).build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, lastRowCount);

        int visibleRows = Math.max(1, area.height() - 4);
        if (lastRowCount > visibleRows) {
            Integer sel = tableState.selected();
            scrollbarState
                    .contentLength(lastRowCount)
                    .viewportContentLength(visibleRows)
                    .position(sel != null ? sel : 0);
            frame.renderStatefulWidget(Scrollbar.builder().build(), area, scrollbarState);
        }
    }

    // ---- Raw metrics view ----

    private void renderRaw(Frame frame, Rect area) {
        String ct = rawContentType != null ? " " + rawContentType : "";
        String title = " Raw Metrics (" + rawLines.size() + " lines)" + ct + " [" + (rawTitle != null ? rawTitle : "") + "] ";

        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(title)
                .build();
        Rect inner = block.inner(area);
        frame.renderWidget(block, area);

        if (rawLines.isEmpty()) {
            return;
        }

        int visibleLines = inner.height();
        int maxScroll = Math.max(0, rawLines.size() - visibleLines);
        rawScroll = Math.min(rawScroll, maxScroll);

        int end = Math.min(rawScroll + visibleLines, rawLines.size());
        List<Line> visible = new ArrayList<>();
        for (int i = rawScroll; i < end; i++) {
            visible.add(colorPrometheusLine(rawLines.get(i)));
        }

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        frame.renderWidget(Paragraph.builder().text(Text.from(visible)).build(), hChunks.get(0));

        if (rawLines.size() > visibleLines) {
            rawScrollbarState
                    .contentLength(rawLines.size())
                    .viewportContentLength(visibleLines)
                    .position(rawScroll);
            frame.renderStatefulWidget(Scrollbar.builder().build(), hChunks.get(1), rawScrollbarState);
        }
    }

    private void loadRawMetrics() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return;
        }
        String url = findMetricsUrl(info);
        if (url == null) {
            return;
        }
        if (!rawLoading.compareAndSet(false, true)) {
            return;
        }
        rawTitle = url;

        ctx.runner.scheduler().execute(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                String ct = response.headers().firstValue("Content-Type").orElse(null);
                List<String> lines = List.of(response.body().split("\n", -1));
                applyRawResult(url, lines, ct);
            } catch (Exception e) {
                applyRawResult(url, List.of("(Error fetching metrics: " + e.getMessage() + ")"), null);
            } finally {
                rawLoading.set(false);
            }
        });
    }

    private void applyRawResult(String url, List<String> lines, String contentType) {
        if (ctx.runner == null) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> {
            if (!showRaw) {
                return;
            }
            rawTitle = url;
            rawLines = lines;
            rawContentType = contentType;
        });
    }

    @Override
    public void renderFooter(List<Span> spans) {
        if (showRaw) {
            hint(spans, TuiIcons.HINT_SCROLL, "scroll");
            hint(spans, "PgUp/Dn", "page");
            hint(spans, "F5", "refresh");
            hintLast(spans, "Esc", "close");
            return;
        }
        hint(spans, "Esc", "back");
        hint(spans, "d", tableMode ? "dashboard" : "table");
        if (tableMode) {
            hint(spans, "s", "sort");
            hint(spans, "f", "filter:" + filterType);
        }
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info != null && findMetricsUrl(info) != null) {
            hint(spans, "r", "raw");
        }
    }

    // ---- Prometheus syntax coloring ----

    private static final Style PROM_COMMENT = Style.EMPTY.dim();
    private static final Style PROM_NAME = Style.EMPTY.fg(Color.CYAN);
    private static final Style PROM_VALUE = Style.EMPTY.fg(Theme.baseFg()).bold();

    private static Line colorPrometheusLine(String line) {
        if (line.startsWith("#")) {
            return Line.from(Span.styled(line, PROM_COMMENT));
        }
        // metric line: name{tags} value  or  name value
        int braceStart = line.indexOf('{');
        int braceEnd = line.indexOf('}');
        if (braceStart > 0 && braceEnd > braceStart) {
            String name = line.substring(0, braceStart);
            String tags = line.substring(braceStart, braceEnd + 1);
            String rest = line.substring(braceEnd + 1).trim();
            return Line.from(
                    Span.styled(name, PROM_NAME),
                    Span.styled(tags, Theme.label()),
                    Span.styled(" " + rest, PROM_VALUE));
        }
        // name value (no tags)
        int space = line.indexOf(' ');
        if (space > 0) {
            String name = line.substring(0, space);
            String value = line.substring(space);
            return Line.from(
                    Span.styled(name, PROM_NAME),
                    Span.styled(value, PROM_VALUE));
        }
        return Line.from(Span.raw(line));
    }

    // ---- Endpoint helpers ----

    private static String findMetricsUrl(IntegrationInfo info) {
        for (HttpEndpointInfo ep : info.httpEndpoints) {
            if (ep.management && ep.url != null && ep.url.contains("/metrics")) {
                return ep.url;
            }
        }
        return null;
    }

    // ---- Meter lookup helpers ----

    private static MicrometerMeterInfo findMeter(List<MicrometerMeterInfo> meters, String name) {
        for (MicrometerMeterInfo m : meters) {
            if (name.equals(m.name)) {
                return m;
            }
        }
        return null;
    }

    private static MicrometerMeterInfo findMeter(
            List<MicrometerMeterInfo> meters, String name, String tagKey,
            String tagVal) {
        for (MicrometerMeterInfo m : meters) {
            if (name.equals(m.name) && hasTag(m, tagKey, tagVal)) {
                return m;
            }
        }
        return null;
    }

    private static List<MicrometerMeterInfo> findMeters(List<MicrometerMeterInfo> meters, String namePrefix) {
        return meters.stream()
                .filter(m -> m.name != null && m.name.startsWith(namePrefix))
                .collect(Collectors.toList());
    }

    private static boolean hasTag(MicrometerMeterInfo m, String key, String value) {
        for (String[] tag : m.tags) {
            if (key.equals(tag[0]) && value.equals(tag[1])) {
                return true;
            }
        }
        return false;
    }

    private static String tagValue(MicrometerMeterInfo m, String key) {
        for (String[] tag : m.tags) {
            if (key.equals(tag[0])) {
                return tag[1];
            }
        }
        return null;
    }

    private static long counterValue(List<MicrometerMeterInfo> meters, String name) {
        MicrometerMeterInfo m = findMeter(meters, name);
        return m != null && m.count != null ? m.count : 0;
    }

    private static double gaugeValue(List<MicrometerMeterInfo> meters, String name) {
        MicrometerMeterInfo m = findMeter(meters, name);
        return m != null && m.value != null ? m.value : -1;
    }

    private static double gaugeValue(List<MicrometerMeterInfo> meters, String name, String tagKey, String tagVal) {
        MicrometerMeterInfo m = findMeter(meters, name, tagKey, tagVal);
        return m != null && m.value != null ? m.value : 0;
    }

    private static long gaugeValueLong(List<MicrometerMeterInfo> meters, String name) {
        MicrometerMeterInfo m = findMeter(meters, name);
        return m != null && m.value != null ? m.value.longValue() : 0;
    }

    // ---- Formatting helpers ----

    private static String formatNumber(long n) {
        if (n >= 1_000_000) {
            return String.format("%.1fM", n / 1_000_000.0);
        }
        if (n >= 10_000) {
            return String.format("%.1fK", n / 1_000.0);
        }
        return String.valueOf(n);
    }

    private static String formatBytes(double bytes) {
        if (bytes <= 0) {
            return "0";
        }
        if (bytes >= 1_073_741_824) {
            return String.format("%.1fGB", bytes / 1_073_741_824.0);
        }
        if (bytes >= 1_048_576) {
            return String.format("%.0fMB", bytes / 1_048_576.0);
        }
        if (bytes >= 1024) {
            return String.format("%.0fKB", bytes / 1024.0);
        }
        return String.format("%.0fB", bytes);
    }

    private static String memoryBar(double used, double max, int width) {
        if (max <= 0) {
            return "";
        }
        double ratio = Math.min(1.0, used / max);
        int filled = (int) Math.round(ratio * width);
        return "[" + "▓".repeat(filled) + "░".repeat(width - filled) + "]";
    }

    private int sortMeter(MicrometerMeterInfo a, MicrometerMeterInfo b) {
        int result = switch (sort) {
            case "type" -> {
                String ta = a.type != null ? a.type : "";
                String tb = b.type != null ? b.type : "";
                yield ta.compareToIgnoreCase(tb);
            }
            case "value" -> Double.compare(numericValue(b), numericValue(a));
            default -> { // "name"
                String na = a.name != null ? a.name : "";
                String nb = b.name != null ? b.name : "";
                yield na.compareToIgnoreCase(nb);
            }
        };
        return sortReversed ? -result : result;
    }

    private static double numericValue(MicrometerMeterInfo m) {
        if (m.count != null) {
            return m.count;
        }
        if (m.value != null) {
            return m.value;
        }
        return 0;
    }

    private static String typeLabel(String type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case "counter" -> "counter";
            case "gauge" -> "gauge";
            case "timer" -> "timer";
            case "longTaskTimer" -> "long-task";
            case "distribution" -> "dist";
            default -> type;
        };
    }

    private static Style typeStyle(String type) {
        if (type == null) {
            return Style.EMPTY;
        }
        return switch (type) {
            case "counter" -> Style.EMPTY.fg(Color.LIGHT_BLUE);
            case "gauge" -> Style.EMPTY.fg(Color.LIGHT_GREEN);
            case "timer" -> Style.EMPTY.fg(Color.LIGHT_YELLOW);
            case "longTaskTimer" -> Style.EMPTY.fg(Color.LIGHT_MAGENTA);
            case "distribution" -> Style.EMPTY.fg(Color.LIGHT_CYAN);
            default -> Style.EMPTY;
        };
    }

    private static String formatValue(MicrometerMeterInfo m) {
        if (m.type == null) {
            return "";
        }
        return switch (m.type) {
            case "counter" -> m.count != null ? String.valueOf(m.count) : "0";
            case "gauge" -> m.value != null ? String.format("%.1f", m.value) : "0.0";
            case "timer" -> String.format("count=%d, mean=%dms, max=%dms",
                    m.count != null ? m.count : 0,
                    m.mean != null ? m.mean : 0,
                    m.max != null ? m.max : 0);
            case "longTaskTimer" -> String.format("tasks=%d, mean=%dms, max=%dms",
                    m.activeTasks != null ? m.activeTasks : 0,
                    m.mean != null ? m.mean : 0,
                    m.max != null ? m.max : 0);
            case "distribution" -> String.format("count=%d, mean=%.1f, max=%.1f",
                    m.count != null ? m.count : 0,
                    m.meanDouble != null ? m.meanDouble : 0.0,
                    m.maxDouble != null ? m.maxDouble : 0.0);
            default -> "";
        };
    }

    private static String formatTags(MicrometerMeterInfo m) {
        if (m.tags.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < m.tags.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            String[] tag = m.tags.get(i);
            sb.append(tag[0]).append("=").append(tag[1]);
        }
        return sb.toString();
    }

    @Override
    public SelectionContext getSelectionContext() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.meters.isEmpty()) {
            return null;
        }
        List<MicrometerMeterInfo> filtered = info.meters;
        if (!"all".equals(filterType)) {
            filtered = filtered.stream()
                    .filter(m -> filterType.equals(m.type))
                    .collect(Collectors.toList());
        }
        List<MicrometerMeterInfo> sorted = new ArrayList<>(filtered);
        sorted.sort(this::sortMeter);
        List<String> items = sorted.stream().map(m -> m.name != null ? m.name : "").toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Metrics");
    }

    @Override
    public String description() {
        return "Micrometer metrics (counters, gauges, timers, distribution summaries)";
    }

    @Override
    public String getHelpText() {
        return """
                # Metrics

                The Metrics tab shows Micrometer metrics collected by the integration.
                Micrometer is the standard metrics library for Java — Camel automatically
                instruments exchanges, routes, and JVM internals when the `camel-micrometer`
                component is present.

                These metrics can be exported to monitoring systems like Prometheus,
                Grafana, Datadog, or InfluxDB.

                ## Dashboard Mode (default)

                A high-level overview organized into panels showing the most important
                metrics at a glance:

                **Camel Exchanges:**
                - **Total** — Total exchanges processed across all routes
                - **Succeeded** — Exchanges that completed without error
                - **Failed** — Exchanges that ended with an exception
                - **Inflight** — Exchanges currently being processed
                - **Ext Reloaded** — External reloads counter (how many times routes were reloaded from file changes or API calls)

                **Route Timers:**
                Per-route processing time statistics. Shows the mean and max processing
                time in milliseconds for each route. A rising mean indicates degrading
                performance; a high max with low mean suggests occasional slow outliers.

                **Event Notifiers:**
                Timing of exchange lifecycle events. Camel fires events at each stage
                of an exchange's life (created, sending, sent, completed, failed).
                The timers here show how long these lifecycle hooks take — useful for
                detecting slow event listeners or interceptors.

                **JVM Metrics:**
                - **Memory**: Heap used/max/committed, non-heap, buffer pools
                - **CPU**: Process CPU usage and system CPU usage as percentages
                - **Threads**: Current thread count, peak threads, daemon threads
                - **GC**: Per-cause collection count and total pause time

                ## Example Dashboard

                ```
                 ── Camel Exchanges ─────────────────────
                 Total: 1,250    Succeeded: 1,247
                 Failed: 3       Inflight: 1
                 Ext Reloaded: 2

                 ── Route Timers ────────────────────────
                 timer-to-log    mean: 0.5ms   max: 12ms
                 seda-consumer   mean: 0.1ms   max: 2ms

                 ── JVM ─────────────────────────────────
                 Heap: 55/80 MB  CPU: 2.1%  Threads: 12
                ```

                ## Table Mode

                Press `t` to switch to a table showing every individual meter:

                - **TYPE** — Meter type:
                  - `counter` — Cumulative count that only goes up (e.g., total exchanges)
                  - `gauge` — Current value that goes up and down (e.g., memory used)
                  - `timer` — Tracks both count and duration (e.g., processing time)
                  - `dist` — Distribution summary with percentiles
                - **NAME** — Metric name following Micrometer conventions (e.g., `camel.exchanges.total`, `jvm.memory.used`)
                - **VALUE** — Current metric value with appropriate formatting
                - **TAGS** — Key-value dimensions that identify the metric (e.g., `routeId=myRoute`, `area=heap`). Tags let you filter and group metrics in monitoring dashboards

                ## Raw Mode

                Press `r` to see the Prometheus exposition format — the same text output
                you would get from the `/observe/metrics` HTTP endpoint. This is the
                exact format Prometheus scrapes, useful for:

                - Debugging why a metric does not appear in Grafana
                - Verifying metric names and labels match your PromQL queries
                - Copying metric definitions for alerting rules

                ## Common Camel Metrics

                - `camel.exchanges.total` — Total exchanges (tagged by routeId)
                - `camel.exchanges.failed` — Failed exchanges
                - `camel.exchanges.inflight` — Currently processing
                - `camel.exchange.processing.time` — Processing duration timer
                - `jvm.memory.used` — JVM memory (tagged by area: heap/nonheap)
                - `jvm.gc.pause` — GC pause durations

                ## Keys

                - `t` — toggle table mode
                - `r` — toggle raw Prometheus mode
                - `Up/Down` — navigate (in table or raw mode)
                - `s` — cycle sort column (in table mode)
                - `S` — reverse sort order
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.meters.isEmpty()) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Metrics");
        JsonArray rows = new JsonArray();
        for (MicrometerMeterInfo m : info.meters) {
            JsonObject row = new JsonObject();
            row.put("type", m.type);
            row.put("name", m.name);
            if (m.description != null) {
                row.put("description", m.description);
            }
            if (m.count != null) {
                row.put("count", m.count);
            }
            if (m.value != null) {
                row.put("value", m.value);
            }
            if (m.mean != null) {
                row.put("mean", m.mean);
            }
            if (m.max != null) {
                row.put("max", m.max);
            }
            if (m.total != null) {
                row.put("total", m.total);
            }
            if (m.activeTasks != null) {
                row.put("activeTasks", m.activeTasks);
            }
            if (m.meanDouble != null) {
                row.put("meanDouble", m.meanDouble);
            }
            if (m.maxDouble != null) {
                row.put("maxDouble", m.maxDouble);
            }
            if (m.totalDouble != null) {
                row.put("totalDouble", m.totalDouble);
            }
            if (!m.tags.isEmpty()) {
                JsonArray tags = new JsonArray();
                for (String[] tag : m.tags) {
                    JsonObject t = new JsonObject();
                    t.put("key", tag[0]);
                    t.put("value", tag.length > 1 ? tag[1] : "");
                    tags.add(t);
                }
                row.put("tags", tags);
            }
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", info.meters.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
