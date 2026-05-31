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
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.util.TimeUtils;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class InflightTab implements MonitorTab {

    private static final String[] SORT_COLUMNS = { "status", "exchange", "route", "duration" };

    // Duration thresholds for color coding
    private static final long THRESHOLD_YELLOW = 1000;  // 1 second
    private static final long THRESHOLD_RED = 10000;    // 10 seconds

    // Unicode block characters for duration bar (1/8 increments)
    private static final char[] BAR_CHARS = { ' ', '▏', '▎', '▍', '▌', '▋', '▊', '▉', '█' };

    private final MonitorContext ctx;
    private final TableState tableState = new TableState();
    private String sort = "duration";
    private int sortIndex = 3;
    private boolean sortReversed;

    InflightTab(MonitorContext ctx) {
        this.ctx = ctx;
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
        List<InflightInfo> list = getInflightExchanges();
        if (list != null) {
            tableState.selectNext(list.size());
        }
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        if (!info.inflightBrowseEnabled && info.inflightExchanges.isEmpty()) {
            renderBrowseDisabled(frame, area);
            return;
        }

        List<InflightInfo> sorted = new ArrayList<>(info.inflightExchanges);
        sorted.sort(this::sortExchange);

        long maxDuration = sorted.stream().mapToLong(i -> i.duration).max().orElse(1);

        List<Row> rows = new ArrayList<>();
        for (InflightInfo ii : sorted) {
            String status = ii.blocked ? "blocked" : "inflight";
            Style statusStyle = ii.blocked
                    ? Style.EMPTY.fg(Color.LIGHT_RED).bold()
                    : Style.EMPTY.fg(Color.GREEN);

            String duration = TimeUtils.printDuration(ii.duration, true);
            Style durationStyle = durationColor(ii.duration);

            String route = ii.atRouteId != null ? ii.atRouteId : "";
            String node = ii.nodeId != null ? ii.nodeId : "";
            String routeNode = node.isEmpty() ? route : route + "/" + node;

            Span barSpan = buildDurationBar(ii.duration, maxDuration, 20);

            rows.add(Row.from(
                    Cell.from(Span.styled(status, statusStyle)),
                    Cell.from(Span.styled(ii.exchangeId != null ? ii.exchangeId : "", Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(routeNode),
                    rightCell(duration, 14, durationStyle),
                    Cell.from(barSpan)));
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(Span.styled("No inflight or blocked exchanges", Style.EMPTY.dim())),
                    Cell.from(""), Cell.from(""), Cell.from(""), Cell.from("")));
        }

        String title = " Inflight (" + sorted.size() + ") sort:" + sort + " ";

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("STATUS", "status"), sortStyle("status"))),
                        Cell.from(Span.styled(sortLabel("EXCHANGE ID", "exchange"), sortStyle("exchange"))),
                        Cell.from(Span.styled(sortLabel("ROUTE/NODE", "route"), sortStyle("route"))),
                        rightCell(sortLabel("DURATION", "duration"), 14, sortStyle("duration")),
                        Cell.from(Span.styled("ELAPSED", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(10),
                        Constraint.length(40),
                        Constraint.fill(),
                        Constraint.length(14),
                        Constraint.length(22))
                .block(Block.builder().borderType(BorderType.ROUNDED).title(title).build())
                .build();

        frame.renderStatefulWidget(table, area, tableState);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, "s", "sort");
    }

    private void renderBrowseDisabled(Frame frame, Rect area) {
        Text text = Text.from(
                Line.from(Span.styled(
                        "Inflight browse is not enabled. Start with: camel run --inflight-browse=true",
                        Style.EMPTY.dim())));
        frame.renderWidget(
                Paragraph.builder()
                        .text(text)
                        .block(Block.builder().borderType(BorderType.ROUNDED).title(" Inflight ").build())
                        .build(),
                area);
    }

    private List<InflightInfo> getInflightExchanges() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        return info != null ? info.inflightExchanges : null;
    }

    private Style durationColor(long durationMs) {
        if (durationMs >= THRESHOLD_RED) {
            return Style.EMPTY.fg(Color.LIGHT_RED).bold();
        } else if (durationMs >= THRESHOLD_YELLOW) {
            return Style.EMPTY.fg(Color.YELLOW);
        }
        return Style.EMPTY.fg(Color.GREEN);
    }

    private Span buildDurationBar(long duration, long maxDuration, int barWidth) {
        if (maxDuration <= 0) {
            return Span.raw("");
        }

        double ratio = (double) duration / maxDuration;
        double filled = ratio * barWidth;
        int fullBlocks = (int) filled;
        int partial = (int) ((filled - fullBlocks) * 8);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fullBlocks && i < barWidth; i++) {
            sb.append(BAR_CHARS[8]); // full block
        }
        if (fullBlocks < barWidth && partial > 0) {
            sb.append(BAR_CHARS[partial]);
        }

        Color color;
        if (duration >= THRESHOLD_RED) {
            color = Color.LIGHT_RED;
        } else if (duration >= THRESHOLD_YELLOW) {
            color = Color.YELLOW;
        } else {
            color = Color.GREEN;
        }

        return Span.styled(sb.toString(), Style.EMPTY.fg(color));
    }

    private String sortLabel(String label, String column) {
        return MonitorContext.sortLabel(label, column, sort, sortReversed);
    }

    private Style sortStyle(String column) {
        return MonitorContext.sortStyle(column, sort);
    }

    private int sortExchange(InflightInfo a, InflightInfo b) {
        int result = switch (sort) {
            case "route" -> {
                String ra = a.atRouteId != null ? a.atRouteId : "";
                String rb = b.atRouteId != null ? b.atRouteId : "";
                yield ra.compareToIgnoreCase(rb);
            }
            case "exchange" -> {
                String ea = a.exchangeId != null ? a.exchangeId : "";
                String eb = b.exchangeId != null ? b.exchangeId : "";
                yield ea.compareToIgnoreCase(eb);
            }
            case "status" -> {
                int sa = a.blocked ? 1 : 0;
                int sb = b.blocked ? 1 : 0;
                yield Integer.compare(sb, sa); // blocked first
            }
            default -> Long.compare(b.duration, a.duration); // longest first
        };
        return sortReversed ? -result : result;
    }
}
