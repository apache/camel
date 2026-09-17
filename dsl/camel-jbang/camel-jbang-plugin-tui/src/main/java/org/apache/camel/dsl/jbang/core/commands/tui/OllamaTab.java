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

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.HostStats;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.LoadedModel;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.ModelShape;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.RequestEntry;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.RequestSource;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.SessionTotals;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.SlotState;
import org.apache.camel.dsl.jbang.core.commands.tui.OllamaMonitor.Snapshot;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hintLast;

/**
 * Shows how the model served by Ollama is performing: the loaded model and its shape, decode and prefill tokens per
 * second, context and cache usage, the load on this host, and a log of the requests made by the TUI's AI panel and by
 * Camel routes. The data comes from {@link OllamaMonitor}.
 */
class OllamaTab extends AbstractTab {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final String[] SPARK = { " ", "▁", "▂", "▃", "▄", "▅", "▆", "▇", "█" };
    private static final String GAUGE_FILLED = "▓";
    private static final String GAUGE_EMPTY = "░";
    private static final int PANELS_HEIGHT = 8;
    private static final int MAX_HEADER_MODELS = 3;

    private final OllamaMonitor monitor;
    private final TableState tableState = new TableState();
    private final ScrollbarState scrollState = new ScrollbarState();
    private Rect lastTableArea;

    OllamaTab(MonitorContext ctx, OllamaMonitor monitor) {
        super(ctx);
        this.monitor = monitor;
    }

    @Override
    public String description() {
        return "Ollama server and model performance: loaded models, tokens per second, context and cache usage, "
               + "host GPU and process load, and the request log";
    }

    @Override
    public void onTabSelected() {
        if (monitor != null) {
            ctx.backgroundExecutor.execute(monitor::poll);
        }
    }

    // ---- input ----

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (ke.isUp()) {
            navigateUp();
            return true;
        }
        if (ke.isDown()) {
            navigateDown();
            return true;
        }
        if (ke.isChar('r') && monitor != null) {
            monitor.reset();
            tableState.select(0);
            return true;
        }
        if (ke.isKey(KeyCode.F5) && monitor != null) {
            ctx.backgroundExecutor.execute(monitor::poll);
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
        tableState.selectPrevious();
    }

    @Override
    public void navigateDown() {
        int rows = monitor != null ? monitor.snapshot().requests().size() : 0;
        if (rows > 0) {
            tableState.selectNext(rows);
        }
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        int rows = monitor != null ? monitor.snapshot().requests().size() : 0;
        return handleTableClick(me, lastTableArea, tableState, rows);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, TuiIcons.ARROW_UP + TuiIcons.ARROW_DOWN, "select");
        hint(spans, "r", "reset");
        hintLast(spans, "F5", "refresh");
    }

    // ---- rendering ----

    @Override
    public void render(Frame frame, Rect area) {
        Snapshot s = monitor != null ? monitor.snapshot() : null;
        if (s == null || !s.connected()) {
            renderNotConnected(frame, area, s);
            return;
        }
        int modelLines = Math.max(1, Math.min(MAX_HEADER_MODELS, s.models().size())) * 2;
        List<Rect> rows = Layout.vertical()
                .constraints(Constraint.length(modelLines + 2), Constraint.length(PANELS_HEIGHT), Constraint.fill())
                .split(area);
        renderHeader(frame, rows.get(0), s);
        renderPanels(frame, rows.get(1), s);
        renderRequests(frame, rows.get(2), s);
    }

    private void renderNotConnected(Frame frame, Rect area, Snapshot s) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(Span.raw("")));
        String where = s != null && s.probedUrl() != null ? OllamaParsers.displayHost(s.probedUrl()) : "localhost:11434";
        lines.add(Line.from(Span.styled("  Ollama not detected at " + where, Theme.warning().bold())));
        if (s != null && s.lastError() != null) {
            lines.add(Line.from(Span.styled("  " + s.lastError(), Theme.muted())));
        }
        lines.add(Line.from(Span.raw("")));
        lines.add(Line.from(Span.styled("  Start it with ", Theme.muted()), Span.styled("ollama serve", Theme.label()),
                Span.styled(" or ", Theme.muted()), Span.styled("camel infra run ollama", Theme.label()),
                Span.styled(", or point the AI panel at a remote Ollama.", Theme.muted())));
        lines.add(Line.from(Span.styled("  The tab checks again every few seconds.", Theme.muted())));
        frame.renderWidget(Paragraph.builder()
                .text(Text.from(lines))
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Ollama ").build())
                .build(), area);
    }

    private void renderHeader(Frame frame, Rect area, Snapshot s) {
        StringBuilder title = new StringBuilder(" Ollama");
        if (s.server().version() != null) {
            title.append(' ').append(s.server().version());
        }
        title.append(" · ").append(OllamaParsers.displayHost(s.server().baseUrl()));
        if (s.runner() != null) {
            title.append(" · runner ").append(s.runner().executable()).append(" :").append(s.runner().port());
        } else if (!s.local()) {
            title.append(" · remote");
        }
        title.append(' ');

        List<Line> lines = new ArrayList<>();
        if (s.models().isEmpty()) {
            lines.add(Line.from(Span.styled("  No model loaded", Theme.warning().bold())));
            String installed = s.installed().isEmpty()
                    ? "no models pulled"
                    : s.installed().size() + " installed: " + String.join(", ", s.installed().subList(0,
                            Math.min(3, s.installed().size())))
                      + (s.installed().size() > 3 ? ", ..." : "");
            lines.add(Line.from(Span.styled("  " + installed
                                            + " · a model loads on the first request and unloads after its keep-alive",
                    Theme.muted())));
        } else {
            for (int i = 0; i < Math.min(MAX_HEADER_MODELS, s.models().size()); i++) {
                LoadedModel m = s.models().get(i);
                lines.add(Line.from(Span.styled("  " + m.name(), Style.EMPTY.fg(Theme.accent()).bold()),
                        Span.styled("   " + describeShape(m), Theme.muted())));
                lines.add(Line.from(Span.styled("  " + describeResidency(m, Instant.now()), Theme.label())));
            }
        }
        frame.renderWidget(Paragraph.builder()
                .text(Text.from(lines))
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(title.toString()).build())
                .build(), area);
    }

    private void renderPanels(Frame frame, Rect area, Snapshot s) {
        List<Rect> cols;
        if (s.local()) {
            cols = Layout.horizontal()
                    .constraints(Constraint.percentage(40), Constraint.percentage(30), Constraint.fill())
                    .split(area);
        } else {
            cols = Layout.horizontal()
                    .constraints(Constraint.percentage(55), Constraint.fill())
                    .split(area);
        }
        renderThroughput(frame, cols.get(0), s);
        renderContext(frame, cols.get(1), s);
        if (s.local()) {
            renderHost(frame, cols.get(2), s);
        }
    }

    private void renderThroughput(Frame frame, Rect area, Snapshot s) {
        RequestEntry last = s.lastRequest();
        boolean live = s.slot() != null && (s.slot().processing() || s.liveDecodeRate() > 0);
        double decode = live ? s.liveDecodeRate() : last != null ? last.decodeTokensPerSecond() : 0;
        double prefill = live ? s.livePrefillRate() : last != null ? last.prefillTokensPerSecond() : 0;
        String tag = live ? "live" : last != null && last.hasTimings() ? "last request" : "";
        Style big = Theme.success().bold();

        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(
                Span.styled(" decode  ", Theme.muted()),
                Span.styled(padLeft(decode > 0 ? formatRate(decode) : "-", 6), big),
                Span.styled(" tok/s", Theme.muted()),
                Span.styled(tag.isEmpty() ? "" : "  " + tag, Theme.muted().dim())));
        lines.add(Line.from(
                Span.styled(" prefill ", Theme.muted()),
                Span.styled(padLeft(prefill > 0 ? formatRate(prefill) : "-", 6), Theme.info().bold()),
                Span.styled(" tok/s", Theme.muted())));
        if (last != null && last.hasTimings()) {
            lines.add(Line.from(
                    Span.styled(" TTFT ", Theme.muted()),
                    Span.styled(formatSeconds(last.ttftMs()), Theme.label()),
                    Span.styled(" · load ", Theme.muted()),
                    Span.styled(formatSeconds(last.loadMs()), last.coldStart() ? Theme.warning() : Theme.label()),
                    Span.styled(last.coldStart() ? " cold" : " warm", Theme.muted())));
        } else {
            lines.add(Line.from(Span.styled(" TTFT - · load -", Theme.muted())));
        }
        SessionTotals t = s.totals();
        if (t.requests() > 0) {
            lines.add(Line.from(
                    Span.styled(" session ", Theme.muted()),
                    Span.styled(t.avgDecodeTokensPerSecond() > 0
                            ? formatRate(t.avgDecodeTokensPerSecond()) + " tok/s"
                            : "-", Theme.label()),
                    Span.styled(" · in " + formatTokens(t.inputTokens()) + " · out " + formatTokens(t.outputTokens())
                                + " · " + t.requests() + (t.requests() == 1 ? " request" : " requests"),
                            Theme.muted())));
        } else {
            lines.add(Line.from(Span.styled(" session  no requests yet", Theme.muted())));
        }

        Block block = Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Throughput ").build();
        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).block(block).build(), area);

        // sparkline on the remaining rows: live decode history with the runner, otherwise one bar per request
        Rect inner = block.inner(area);
        int sparkY = inner.y() + lines.size();
        if (sparkY < inner.y() + inner.height() && inner.width() > 2) {
            long[] data = s.runner() != null ? s.decodeHistory() : requestHistory(s.requests(), inner.width() - 2);
            String spark = sparkline(data, inner.width() - 2);
            if (!spark.isBlank()) {
                frame.buffer().setString(inner.x() + 1, sparkY, spark, Theme.success());
            }
            int labelY = sparkY + 1;
            if (labelY < inner.y() + inner.height()) {
                String label = s.runner() != null
                        ? "decode tok/s, last " + (OllamaMonitor.HISTORY_POINTS / 2) + "s"
                        : "decode tok/s per request";
                frame.buffer().setString(inner.x() + 1, labelY, label, Theme.muted().dim());
            }
        }
    }

    private void renderContext(Frame frame, Rect area, Snapshot s) {
        List<Line> lines = new ArrayList<>();
        SlotState slot = s.slot();
        LoadedModel model = s.models().isEmpty() ? null : s.models().get(0);
        Block block = Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Context ").build();
        int gaugeWidth = Math.max(8, Math.min(24, block.inner(area).width() - 22));
        if (slot != null) {
            long used = slot.contextUsed();
            long size = slot.contextSize();
            int pct = size > 0 ? (int) Math.min(100, used * 100 / size) : 0;
            lines.add(Line.from(
                    Span.styled(" " + gaugeBar(pct, gaugeWidth) + " ", pct >= 90 ? Theme.error() : Theme.info()),
                    Span.styled(formatTokens(used) + " / " + formatTokens(size), Theme.label()),
                    Span.styled(" (" + pct + "%)", Theme.muted())));
            lines.add(Line.from(Span.styled(" prompt " + formatTokens(slot.promptTokens()) + " · cache hit "
                                            + slot.cacheHitPercent() + "%"
                                            + (slot.slots() > 1 ? " · " + slot.slots() + " slots" : ""),
                    Theme.muted())));
            List<Span> state = new ArrayList<>();
            if (slot.processing()) {
                state.add(Span.styled(" " + TuiIcons.SELECTED + " working", Theme.success().bold()));
                // still in prefill: nothing decoded yet and the prompt only partly processed
                if (slot.decoded() == 0 && slot.promptProcessed() > 0 && slot.promptProcessed() < slot.promptTokens()) {
                    state.add(Span.styled("  prefill " + formatTokens(slot.promptProcessed()) + "/"
                                          + formatTokens(slot.promptTokens()),
                            Theme.muted()));
                }
            } else {
                state.add(Span.styled(" " + TuiIcons.IDLE + " idle", Theme.muted()));
            }
            if (slot.speculative() != null) {
                state.add(Span.styled(" · speculative " + slot.speculative(), Theme.muted()));
            }
            lines.add(Line.from(state));
        } else {
            RequestEntry last = s.lastRequest();
            long ctx = model != null ? model.contextLength() : 0;
            if (last != null && ctx > 0) {
                long used = (long) last.inputTokens() + last.outputTokens() + last.cachedTokens();
                int pct = (int) Math.min(100, used * 100 / ctx);
                lines.add(Line.from(
                        Span.styled(" " + gaugeBar(pct, gaugeWidth) + " ", pct >= 90 ? Theme.error() : Theme.info()),
                        Span.styled(formatTokens(used) + " / " + formatTokens(ctx), Theme.label()),
                        Span.styled(" (" + pct + "%) last request", Theme.muted())));
                lines.add(Line.from(Span.styled(" cached prompt " + formatTokens(last.cachedTokens()), Theme.muted())));
            } else if (ctx > 0) {
                lines.add(Line.from(Span.styled(" " + gaugeBar(0, gaugeWidth) + " ", Theme.info()),
                        Span.styled("0 / " + formatTokens(ctx), Theme.label())));
            } else {
                lines.add(Line.from(Span.styled(" no context data yet", Theme.muted())));
            }
            lines.add(Line.from(Span.styled(s.local()
                    ? " live state appears once the runner is found"
                    : " live state needs the runner on this machine", Theme.muted().dim())));
        }
        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).block(block).build(), area);
    }

    private void renderHost(Frame frame, Rect area, Snapshot s) {
        List<Line> lines = new ArrayList<>();
        HostStats host = s.host();
        Block block = Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Host ").build();
        int gaugeWidth = Math.max(6, Math.min(16, block.inner(area).width() - 14));
        if (host == null) {
            lines.add(Line.from(Span.styled(" collecting...", Theme.muted())));
        } else {
            if (host.gpu() != null) {
                int util = host.gpu().utilizationPercent();
                Style gs = util >= 90 ? Theme.error() : util >= 60 ? Theme.warning() : Theme.success();
                lines.add(Line.from(
                        Span.styled(" GPU  ", Theme.muted()),
                        Span.styled(gaugeBar(util, gaugeWidth), gs),
                        Span.styled(" " + util + "%", gs.bold()),
                        Span.styled(host.gpu().count() > 1 ? " ×" + host.gpu().count() : "", Theme.muted())));
                String mem = " GPU mem " + TuiHelper.formatBytes(host.gpu().memoryUsedBytes());
                if (host.gpu().memoryTotalBytes() > host.gpu().memoryUsedBytes()) {
                    mem += " / " + TuiHelper.formatBytes(host.gpu().memoryTotalBytes());
                }
                lines.add(Line.from(Span.styled(mem, Theme.label())));
            } else {
                lines.add(Line.from(Span.styled(" GPU  no reading on this host", Theme.muted())));
            }
            if (host.runner() != null) {
                lines.add(processLine(host.runner().label(), host.runner().cpuPercent(), host.runner().rssBytes()));
            }
            if (host.server() != null) {
                lines.add(processLine(host.server().label(), host.server().cpuPercent(), host.server().rssBytes()));
            }
        }
        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).block(block).build(), area);
    }

    private static Line processLine(String label, double cpu, long rss) {
        String name = label != null ? label : "process";
        if (name.length() > 13) {
            name = name.substring(0, 13);
        }
        return Line.from(
                Span.styled(" " + String.format(Locale.ROOT, "%-13s", name), Theme.muted()),
                Span.styled(String.format(Locale.ROOT, "%5.1f%% cpu", cpu), Theme.label()),
                Span.styled(rss > 0 ? " · " + TuiHelper.formatBytes(rss) : "", Theme.muted()));
    }

    private void renderRequests(Frame frame, Rect area, Snapshot s) {
        List<RequestEntry> requests = s.requests();
        List<Row> rows = new ArrayList<>();
        for (RequestEntry e : requests) {
            rows.add(Row.from(
                    Cell.from(Span.styled(" " + TIME.format(e.timestamp()), Theme.muted())),
                    Cell.from(Span.styled(sourceLabel(e), e.source() == RequestSource.ROUTE
                            ? Theme.notice()
                            : Theme.info())),
                    Cell.from(Span.raw(e.model())),
                    rightCell(formatTokens(e.inputTokens()), 6),
                    rightCell(formatTokens(e.outputTokens()), 6),
                    rightCell(e.cachedTokens() > 0 ? formatTokens(e.cachedTokens()) : "-", 6, Theme.muted()),
                    rightCell(e.prefillMs() > 0 ? formatRate(e.prefillTokensPerSecond()) : "-", 8, Theme.info()),
                    rightCell(e.decodeMs() > 0 ? formatRate(e.decodeTokensPerSecond()) : "-", 8, Theme.success()),
                    rightCell(e.hasTimings() ? formatSeconds(e.ttftMs()) : "-", 7,
                            e.coldStart() ? Theme.warning() : Style.EMPTY),
                    rightCell(e.totalMs() > 0 ? formatSeconds(e.totalMs()) : "-", 7),
                    Cell.from(Span.styled(" " + (e.doneReason() != null ? e.doneReason() : ""), Theme.muted()))));
        }
        if (rows.isEmpty()) {
            lastTableArea = area;
            frame.renderWidget(Paragraph.builder()
                    .text(Text.from(List.of(
                            Line.from(Span.styled(" No requests yet.", Theme.muted())),
                            Line.from(Span.styled(" Ask something in the AI panel (F8) with Ollama as the provider, "
                                                  + "or let a route call Ollama with GenAI observability on.",
                                    Theme.muted())))))
                    .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                            .title(" Requests (0) ").build())
                    .build(), area);
            return;
        }
        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(" TIME", Style.EMPTY.bold())),
                        Cell.from(Span.styled("SOURCE", Style.EMPTY.bold())),
                        Cell.from(Span.styled("MODEL", Style.EMPTY.bold())),
                        rightCell("IN", 6, Style.EMPTY.bold()),
                        rightCell("OUT", 6, Style.EMPTY.bold()),
                        rightCell("CACHED", 6, Style.EMPTY.bold()),
                        rightCell("PREFILL", 8, Style.EMPTY.bold()),
                        rightCell("DECODE", 8, Style.EMPTY.bold()),
                        rightCell("TTFT", 7, Style.EMPTY.bold()),
                        rightCell("TOTAL", 7, Style.EMPTY.bold()),
                        Cell.from(Span.styled(" REASON", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(9),
                        Constraint.length(18),
                        Constraint.fill(),
                        Constraint.length(6),
                        Constraint.length(6),
                        Constraint.length(6),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(7),
                        Constraint.length(7),
                        Constraint.length(12))
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Requests (" + requests.size() + ")  tok/s for prefill and decode ").build())
                .build();
        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderTableScrollbar(frame, area, tableState, scrollState, rows.size());
    }

    // ---- MCP / table data ----

    @Override
    public JsonObject getTableDataAsJson() {
        JsonObject result = new JsonObject();
        result.put("tab", "Ollama");
        JsonArray rows = new JsonArray();
        int total = 0;
        if (monitor != null) {
            JsonObject full = monitor.toJson(MAX_ROWS_JSON);
            Object reqs = full.remove("requests");
            if (reqs instanceof JsonArray ja) {
                rows = ja;
            }
            total = monitor.snapshot().requests().size();
            result.put("summary", full);
        }
        result.put("rows", rows);
        result.put("totalRows", total);
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }

    private static final int MAX_ROWS_JSON = 100;

    @Override
    public String getHelpText() {
        return DocHelper.loadHelpText("ollama");
    }

    // ---- formatting helpers (package-private for tests) ----

    static String describeShape(LoadedModel m) {
        List<String> parts = new ArrayList<>();
        ModelShape shape = m.shape();
        String arch = shape != null && shape.architecture() != null ? shape.architecture() : m.family();
        if (arch != null) {
            parts.add(arch);
        }
        if (m.parameterSize() != null) {
            parts.add(m.parameterSize());
        }
        if (m.quantization() != null) {
            parts.add(m.quantization());
        }
        if (shape != null) {
            if (shape.layers() > 0) {
                parts.add(shape.layers() + " layers");
            }
            if (shape.experts() > 0) {
                parts.add(shape.experts() + " experts"
                          + (shape.expertsUsed() > 0 ? " (" + shape.expertsUsed() + " active)" : ""));
            }
            if (shape.capabilities() != null && !shape.capabilities().isEmpty()) {
                List<String> caps = new ArrayList<>();
                for (String c : shape.capabilities()) {
                    if (!"completion".equals(c)) {
                        caps.add(c);
                    }
                }
                if (!caps.isEmpty()) {
                    parts.add(String.join(", ", caps));
                }
            }
        }
        return String.join(" · ", parts);
    }

    static String describeResidency(LoadedModel m, Instant now) {
        StringBuilder sb = new StringBuilder();
        sb.append(TuiHelper.formatBytes(m.sizeBytes())).append(" loaded");
        if (m.sizeBytes() > 0) {
            int gpu = m.gpuPercent();
            sb.append(", ").append(gpu >= 100 ? "100% GPU" : gpu > 0 ? gpu + "% GPU" : "CPU only");
        }
        if (m.contextLength() > 0) {
            sb.append(" · ctx ").append(String.format(Locale.ROOT, "%,d", m.contextLength()));
            if (m.shape() != null && m.shape().maxContext() > m.contextLength()) {
                sb.append(" of ").append(String.format(Locale.ROOT, "%,d", m.shape().maxContext()));
            }
        }
        if (m.expiresAt() != null) {
            sb.append(" · ").append(formatCountdown(m.expiresAt(), now));
        }
        return sb.toString();
    }

    static String formatCountdown(Instant expiresAt, Instant now) {
        Duration left = Duration.between(now, expiresAt);
        if (left.isNegative() || left.isZero()) {
            return "unloading";
        }
        if (left.toDays() > 365) {
            return "stays loaded";
        }
        long secs = left.toSeconds();
        if (secs >= 3600) {
            return "unloads in " + (secs / 3600) + "h" + ((secs % 3600) / 60) + "m";
        }
        if (secs >= 60) {
            return "unloads in " + (secs / 60) + "m" + (secs % 60) + "s";
        }
        return "unloads in " + secs + "s";
    }

    static String sourceLabel(RequestEntry e) {
        if (e.source() == RequestSource.ROUTE) {
            return e.routeId() != null ? "route:" + e.routeId() : "route";
        }
        return "tui";
    }

    static String formatRate(double tokensPerSecond) {
        if (tokensPerSecond <= 0) {
            return "0";
        }
        if (tokensPerSecond < 10) {
            return String.format(Locale.ROOT, "%.1f", tokensPerSecond);
        }
        return String.format(Locale.ROOT, "%.0f", tokensPerSecond);
    }

    static String formatTokens(long tokens) {
        if (tokens < 1000) {
            return Long.toString(tokens);
        }
        if (tokens < 10_000) {
            return String.format(Locale.ROOT, "%.1fk", tokens / 1000.0);
        }
        if (tokens < 1_000_000) {
            return (tokens / 1000) + "k";
        }
        return String.format(Locale.ROOT, "%.1fM", tokens / 1_000_000.0);
    }

    static String formatSeconds(long ms) {
        if (ms <= 0) {
            return "0s";
        }
        if (ms < 1000) {
            return String.format(Locale.ROOT, "%.2fs", ms / 1000.0);
        }
        if (ms < 10_000) {
            return String.format(Locale.ROOT, "%.1fs", ms / 1000.0);
        }
        if (ms < 60_000) {
            return (ms / 1000) + "s";
        }
        return (ms / 60_000) + "m" + ((ms % 60_000) / 1000) + "s";
    }

    static String gaugeBar(int pct, int width) {
        int p = Math.max(0, Math.min(100, pct));
        int filled = p * width / 100;
        return GAUGE_FILLED.repeat(filled) + GAUGE_EMPTY.repeat(Math.max(0, width - filled));
    }

    /** Right-aligned one-row sparkline scaled to the largest value; an all-zero series renders as spaces. */
    static String sparkline(long[] data, int width) {
        if (data == null || width <= 0) {
            return "";
        }
        long max = 0;
        for (long v : data) {
            max = Math.max(max, v);
        }
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, data.length - width);
        for (int i = 0; i < width - Math.min(width, data.length); i++) {
            sb.append(' ');
        }
        for (int i = start; i < data.length; i++) {
            if (max <= 0 || data[i] <= 0) {
                sb.append(' ');
            } else {
                int level = (int) Math.max(1, Math.round(data[i] * 8.0 / max));
                sb.append(SPARK[Math.min(8, level)]);
            }
        }
        return sb.toString();
    }

    /** Decode rate of the most recent requests, oldest first, for hosts without a runner to sample live. */
    static long[] requestHistory(List<RequestEntry> newestFirst, int width) {
        int n = Math.min(Math.max(0, width), newestFirst.size());
        long[] data = new long[n];
        for (int i = 0; i < n; i++) {
            data[n - 1 - i] = Math.round(newestFirst.get(i).decodeTokensPerSecond());
        }
        return data;
    }

    private static String padLeft(String s, int width) {
        return s.length() >= width ? s : " ".repeat(width - s.length()) + s;
    }
}
