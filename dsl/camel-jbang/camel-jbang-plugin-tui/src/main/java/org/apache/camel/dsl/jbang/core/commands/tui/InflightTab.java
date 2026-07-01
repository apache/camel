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

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

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
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        MonitorTab.renderTableScrollbar(frame, lastTableArea, tableState, tableScrollState, sorted.size());
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
    public String getHelpText() {
        return """
                # Inflight

                The Inflight tab shows exchanges that are currently being processed or
                are blocked waiting for a response. This is your real-time view into
                what the integration is doing right now — which messages are in flight
                and how long they have been processing.

                This tab requires `--inflight-browse=true` when starting the integration.
                Without it, Camel does not track individual inflight exchanges.

                ## Table Columns

                - **STATUS** — Exchange state: `inflight` (green, actively processing) or `blocked` (red, waiting for a resource like a lock or a slow downstream service)
                - **EXCHANGE ID** — Unique identifier for this exchange (e.g., `ID-myhost-1234-5`)
                - **ROUTE/NODE** — Which route and specific processor node is currently handling the exchange. Format: `routeId/nodeId` (e.g., `my-route/to1`). This tells you exactly where in the route the message is right now
                - **DURATION** — Time elapsed since processing started (e.g., `1s`, `5s`, `1m30s`). Long durations may indicate a slow downstream service or a deadlock
                - **ELAPSED** — Visual duration bar showing relative processing time. Longer bars mean longer processing. Color indicates severity

                ## Example Screen

                ```
                 STATUS    EXCHANGE ID          ROUTE/NODE          DURATION    ELAPSED
                 inflight  ID-myhost-1234-10    my-route/to1        234ms       ██
                 inflight  ID-myhost-1234-9     my-route/process2   1s          ████████
                 blocked   ID-myhost-1234-7     api-route/to3       12s         ████████████████████
                ```

                The third exchange has been blocked for 12 seconds — it is likely
                waiting for a response from a slow external service.

                ## Duration Colors

                - **Green**: less than 1 second — normal processing time
                - **Yellow**: 1-10 seconds — getting slow, worth monitoring
                - **Red**: over 10 seconds — potentially stuck or waiting on an unresponsive service

                ## When To Use

                - **Diagnosing slow processing**: If messages are taking longer than expected, this tab shows exactly which route and node they are stuck at
                - **Detecting blocked exchanges**: Blocked exchanges are waiting for a resource (e.g., a database connection, a file lock, a thread pool). Multiple blocked exchanges at the same node may indicate contention
                - **Monitoring concurrent load**: The number of inflight exchanges shows how much concurrent work the integration is handling
                - **Identifying stuck messages**: Exchanges that stay inflight for minutes are likely stuck — the ROUTE/NODE column tells you where to investigate

                ## Keys

                - `Up/Down` — select exchange
                - `s` — cycle sort column
                - `S` — reverse sort order
                - `Esc` — back
                """;
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
        for (InflightInfo ii : info.inflightExchanges) {
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
