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
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class InflightTab extends AbstractTableTab {

    // Duration thresholds for color coding
    private static final long THRESHOLD_YELLOW = 1000;  // 1 second
    private static final long THRESHOLD_RED = 10000;    // 10 seconds

    // Unicode block characters for duration bar (1/8 increments)
    private static final char[] BAR_CHARS = { ' ', '▏', '▎', '▍', '▌', '▋', '▊', '▉', '█' };

    InflightTab(MonitorContext ctx) {
        super(ctx, "status", "exchange", "route", "duration");
        sortIndex = 3;
        sort = "duration";
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        return false;
    }

    @Override
    protected int getRowCount() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        return info != null ? info.inflightExchanges.size() : 0;
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
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
                    ? Theme.error().bold()
                    : Theme.success();

            String duration = TimeUtils.printDuration(ii.duration, false);
            Style durationStyle = durationColor(ii.duration);

            String route = ii.atRouteId != null ? ii.atRouteId : "";
            String node = ii.nodeId != null ? ii.nodeId : "";
            String routeNode = node.isEmpty() ? route : route + "/" + node;

            Span barSpan = buildDurationBar(ii.duration, maxDuration, 20);

            rows.add(Row.from(
                    Cell.from(Span.styled(" " + status, statusStyle)),
                    Cell.from(Span.styled(ii.exchangeId != null ? ii.exchangeId : "", Style.EMPTY.fg(Theme.accent()))),
                    Cell.from(routeNode),
                    rightCell(duration, 14, durationStyle),
                    Cell.from(barSpan)));
        }

        if (rows.isEmpty()) {
            rows.add(emptyRow("No inflight or blocked exchanges", 5));
        }

        String title = " Inflight (" + sorted.size() + ") ";

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(" " + sortLabel("STATUS", "status"), sortStyle("status"))),
                        Cell.from(Span.styled(sortLabel("EXCHANGE ID", "exchange"), sortStyle("exchange"))),
                        Cell.from(Span.styled(sortLabel("ROUTE/NODE", "route"), sortStyle("route"))),
                        rightCell(sortLabel("DURATION", "duration"), 14, sortStyle("duration")),
                        Cell.from(Span.styled("ELAPSED", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(10),
                        Constraint.length(40),
                        Constraint.fill(),
                        Constraint.length(14),
                        Constraint.length(23))
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, sorted.size());
    }

    private void renderBrowseDisabled(Frame frame, Rect area) {
        Text text = Text.from(
                Line.from(Span.styled(
                        "Inflight browse is not enabled. Start with: camel run --inflight-browse=true",
                        Style.EMPTY.dim())));
        frame.renderWidget(
                Paragraph.builder()
                        .text(text)
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Inflight ").build())
                        .build(),
                area);
    }

    private Style durationColor(long durationMs) {
        if (durationMs >= THRESHOLD_RED) {
            return Theme.error().bold();
        } else if (durationMs >= THRESHOLD_YELLOW) {
            return Theme.warning();
        }
        return Theme.success();
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

        Style barStyle;
        if (duration >= THRESHOLD_RED) {
            barStyle = Theme.error();
        } else if (duration >= THRESHOLD_YELLOW) {
            barStyle = Theme.warning();
        } else {
            barStyle = Theme.success();
        }

        return Span.styled(sb.toString(), barStyle);
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

    @Override
    public String description() {
        return "Currently in-flight exchanges being processed";
    }

    @Override
    public String getHelpText() {
        return DocHelper.loadHelpText("inflight");
    }

    @Override
    public JsonObject getTableDataAsJson() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Inflight");
        JsonArray rows = new JsonArray();
        List<InflightInfo> sorted = new ArrayList<>(info.inflightExchanges);
        sorted.sort(this::sortExchange);
        for (InflightInfo ii : sorted) {
            JsonObject row = new JsonObject();
            row.put("exchangeId", ii.exchangeId);
            row.put("fromRouteId", ii.fromRouteId);
            row.put("fromRemoteEndpoint", ii.fromRemoteEndpoint);
            row.put("atRouteId", ii.atRouteId);
            row.put("nodeId", ii.nodeId);
            row.put("elapsed", ii.elapsed);
            row.put("duration", ii.duration);
            row.put("blocked", ii.blocked);
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", info.inflightExchanges.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
