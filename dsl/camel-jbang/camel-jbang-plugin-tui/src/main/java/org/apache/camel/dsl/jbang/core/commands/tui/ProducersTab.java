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
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class ProducersTab extends AbstractTableTab {

    ProducersTab(MonitorContext ctx) {
        super(ctx, "route", "status", "type", "uri");
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        return false;
    }

    @Override
    protected int getRowCount() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        return info != null ? info.producers.size() : 0;
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        List<ProducerInfo> sorted = new ArrayList<>(info.producers);
        sorted.sort(this::sortProducer);

        boolean percentiles = sorted.stream().map(pi -> stats(info, pi))
                .anyMatch(st -> st != null && st.busiest().p50Time >= 0);

        List<Row> rows = new ArrayList<>();
        for (ProducerInfo pi : sorted) {
            String type = producerType(pi);
            Style statusStyle = "Started".equals(pi.state) ? Theme.success() : Theme.error();
            Stats st = stats(info, pi);

            rows.add(Row.from(
                    Cell.from(Span.styled(" " + (pi.routeId != null ? pi.routeId : ""), Style.EMPTY.fg(Theme.accent()))),
                    Cell.from(Span.styled(pi.state != null ? pi.state : "", statusStyle)),
                    Cell.from(type),
                    Cell.from(pi.remote ? "x" : ""),
                    rightCell(st != null ? String.valueOf(st.total()) : "", 10),
                    st != null && st.failed() > 0
                            ? rightCell(String.valueOf(st.failed()), 8, Theme.error()) : rightCell("", 8),
                    rightCell(st != null ? timing(st.busiest(), percentiles) : "", 16),
                    Cell.from(pi.uri != null ? pi.uri : "")));
        }

        if (rows.isEmpty()) {
            rows.add(emptyRow("No producers", 8));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(" " + sortLabel("ROUTE", "route"), sortStyle("route"))),
                        Cell.from(Span.styled(sortLabel("STATUS", "status"), sortStyle("status"))),
                        Cell.from(Span.styled(sortLabel("TYPE", "type"), sortStyle("type"))),
                        Cell.from(Span.styled("REMOTE", Style.EMPTY.bold())),
                        rightCell("TOTAL", 10, Style.EMPTY.bold()),
                        rightCell("FAIL", 8, Style.EMPTY.bold()),
                        rightCell(percentiles ? "P50/P95/P99" : "MIN/MAX/MEAN", 16, Style.EMPTY.bold()),
                        Cell.from(Span.styled(sortLabel("URI", "uri"), sortStyle("uri")))))
                .widths(
                        Constraint.length(20),
                        Constraint.length(10),
                        Constraint.length(20),
                        Constraint.length(8),
                        Constraint.length(10),
                        Constraint.length(8),
                        Constraint.length(16),
                        Constraint.fill())
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Producers ").build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, table, sorted.size());
    }

    private int sortProducer(ProducerInfo a, ProducerInfo b) {
        int result = switch (sort) {
            case "status" -> {
                String sa = a.state != null ? a.state : "";
                String sb = b.state != null ? b.state : "";
                yield sa.compareToIgnoreCase(sb);
            }
            case "type" -> {
                String ta = producerType(a);
                String tb = producerType(b);
                yield ta.compareToIgnoreCase(tb);
            }
            case "uri" -> {
                String ua = a.uri != null ? a.uri : "";
                String ub = b.uri != null ? b.uri : "";
                yield ua.compareToIgnoreCase(ub);
            }
            default -> { // "route"
                String ra = a.routeId != null ? a.routeId : "";
                String rb = b.routeId != null ? b.routeId : "";
                yield ra.compareToIgnoreCase(rb);
            }
        };
        return sortReversed ? -result : result;
    }

    /**
     * What a producer sent: the statistics of the steps of its route that send to its endpoint (to, wireTap, enrich),
     * and the busiest of them for the timings. Null when no step does, such as a toD whose endpoint is computed.
     */
    record Stats(long total, long failed, ProcessorInfo busiest) {
    }

    static Stats stats(IntegrationInfo info, ProducerInfo pi) {
        if (pi.routeId == null || pi.uri == null) {
            return null;
        }
        String uri = normalize(pi.uri);
        long total = 0;
        long failed = 0;
        ProcessorInfo busiest = null;
        for (RouteInfo route : info.routes) {
            if (!pi.routeId.equals(route.routeId)) {
                continue;
            }
            for (ProcessorInfo step : route.processors) {
                if (step.uri != null && uri.equals(normalize(step.uri))) {
                    total += step.total;
                    failed += step.failed;
                    if (busiest == null || step.total > busiest.total) {
                        busiest = step;
                    }
                }
            }
        }
        return busiest != null ? new Stats(total, failed, busiest) : null;
    }

    /** The same endpoint with or without the // after the scheme (direct:a and direct://a). */
    private static String normalize(String uri) {
        int colon = uri.indexOf(':');
        if (colon > 0 && !uri.startsWith("//", colon + 1)) {
            return uri.substring(0, colon) + "://" + uri.substring(colon + 1);
        }
        return uri;
    }

    private static String timing(ProcessorInfo step, boolean percentiles) {
        if (percentiles && step.p50Time >= 0) {
            return formatDurationMs(step.p50Time) + "/" + formatDurationMs(step.p95Time) + "/"
                   + formatDurationMs(step.p99Time);
        }
        if (step.total > 0) {
            return formatDurationMs(step.minTime) + "/" + formatDurationMs(step.maxTime) + "/"
                   + formatDurationMs(step.meanTime);
        }
        return "";
    }

    private static String producerType(ProducerInfo pi) {
        if (pi.className == null) {
            return "";
        }
        String s = pi.className;
        if (s.endsWith("Producer")) {
            s = s.substring(0, s.length() - 8);
        }
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : s;
    }

    @Override
    public SelectionContext getSelectionContext() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.producers.isEmpty()) {
            return null;
        }
        List<ProducerInfo> sorted = new ArrayList<>(info.producers);
        sorted.sort(this::sortProducer);
        List<String> items = sorted.stream().map(p -> p.routeId != null ? p.routeId : "").toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Producers");
    }

    @Override
    public String description() {
        return "Producer statistics (output endpoints sending data to external systems)";
    }

    @Override
    public String getHelpText() {
        return DocHelper.loadHelpText("producers");
    }

    @Override
    public JsonObject getTableDataAsJson() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Producers");
        JsonArray rows = new JsonArray();
        List<ProducerInfo> sorted = new ArrayList<>(info.producers);
        sorted.sort(this::sortProducer);
        for (ProducerInfo pi : sorted) {
            JsonObject row = new JsonObject();
            row.put("routeId", pi.routeId);
            row.put("uri", pi.uri);
            row.put("state", pi.state);
            row.put("className", pi.className);
            row.put("remote", pi.remote);
            row.put("singleton", pi.singleton);
            if (pi.stepId != null) {
                row.put("stepId", pi.stepId);
            }
            Stats st = stats(info, pi);
            if (st != null) {
                row.put("total", st.total());
                row.put("failed", st.failed());
                ProcessorInfo b = st.busiest();
                if (b.p50Time >= 0) {
                    row.put("p50Time", b.p50Time);
                    row.put("p95Time", b.p95Time);
                    row.put("p99Time", b.p99Time);
                } else {
                    row.put("meanTime", b.meanTime);
                }
            }
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", info.producers.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
