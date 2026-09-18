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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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

class ConsumersTab extends AbstractTableTab {

    ConsumersTab(MonitorContext ctx) {
        super(ctx, "id", "status", "type", "remote", "inflight", "polls", "uri");
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        return false;
    }

    @Override
    protected int getRowCount() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        return info != null ? info.consumers.size() : 0;
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        List<ConsumerInfo> sorted = new ArrayList<>(info.consumers);
        sorted.sort(this::sortConsumer);

        List<Row> rows = new ArrayList<>();
        for (ConsumerInfo ci : sorted) {
            String status = consumerStatus(ci);
            HealthCheckInfo hc = consumerHealthCheck(info, ci);
            boolean healthDown = hc != null && "DOWN".equals(hc.state);
            Style statusStyle = healthDown
                    ? Theme.error()
                    : ("Started".equals(ci.state) || "Polling".equals(status)
                            ? Theme.success()
                            : Theme.error());
            String statusText = healthDown ? TuiIcons.HEALTH_WARN + " " + status : status;
            String type = consumerType(ci);
            String schedule = consumerSchedule(ci);
            String sinceLast = consumerSinceLast(ci);
            String uri = healthDown && hc.message != null
                    ? hc.message
                    : (ci.uri != null ? ci.uri : "");

            rows.add(Row.from(
                    Cell.from(Span.styled(" " + (ci.id != null ? ci.id : ""), Style.EMPTY.fg(Theme.accent()))),
                    Cell.from(Span.styled(statusText, statusStyle)),
                    Cell.from(type),
                    Cell.from(ci.remote ? "x" : ""),
                    rightCell(String.valueOf(ci.inflight), 8),
                    Cell.from(schedule),
                    rightCell(ci.totalCounter != null ? String.valueOf(ci.totalCounter) : "", 8),
                    Cell.from(sinceLast),
                    Cell.from(Span.styled(uri, healthDown ? Theme.error() : Style.EMPTY))));
        }

        if (rows.isEmpty()) {
            rows.add(emptyRow("No consumers", 9));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(" " + sortLabel("ROUTE", "id"), sortStyle("id"))),
                        Cell.from(Span.styled(sortLabel("STATUS", "status"), sortStyle("status"))),
                        Cell.from(Span.styled(sortLabel("TYPE", "type"), sortStyle("type"))),
                        Cell.from(Span.styled(sortLabel("REMOTE", "remote"), sortStyle("remote"))),
                        rightCell(sortLabel("INFLIGHT", "inflight"), 8, sortStyle("inflight")),
                        Cell.from(Span.styled("SCHEDULE", Style.EMPTY.bold())),
                        rightCell(sortLabel("POLLS", "polls"), 8, sortStyle("polls")),
                        Cell.from(Span.styled("SINCE-LAST", Style.EMPTY.bold())),
                        Cell.from(Span.styled(sortLabel("URI", "uri"), sortStyle("uri")))))
                .widths(
                        Constraint.length(20),
                        Constraint.length(10),
                        Constraint.length(16),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(22),
                        Constraint.length(8),
                        Constraint.length(22),
                        Constraint.fill())
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Consumers ").build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, sorted.size());
    }

    private int sortConsumer(ConsumerInfo a, ConsumerInfo b) {
        int result = switch (sort) {
            case "status" -> {
                String sa = consumerStatus(a);
                String sb = consumerStatus(b);
                yield sa.compareToIgnoreCase(sb);
            }
            case "type" -> {
                String ta = consumerType(a);
                String tb = consumerType(b);
                yield ta.compareToIgnoreCase(tb);
            }
            case "remote" -> Boolean.compare(b.remote, a.remote);
            case "inflight" -> Integer.compare(b.inflight, a.inflight);
            case "polls" -> {
                long la = a.totalCounter != null ? a.totalCounter : 0;
                long lb = b.totalCounter != null ? b.totalCounter : 0;
                yield Long.compare(lb, la);
            }
            case "uri" -> {
                String ua = a.uri != null ? a.uri : "";
                String ub = b.uri != null ? b.uri : "";
                yield ua.compareToIgnoreCase(ub);
            }
            default -> { // "id"
                String ia = a.id != null ? a.id : "";
                String ib = b.id != null ? b.id : "";
                yield ia.compareToIgnoreCase(ib);
            }
        };
        return sortReversed ? -result : result;
    }

    private static String consumerStatus(ConsumerInfo ci) {
        if (ci.polling != null && ci.polling) {
            return "Polling";
        }
        return ci.state != null ? ci.state : "";
    }

    private static String consumerType(ConsumerInfo ci) {
        if (ci.className == null) {
            return "";
        }
        String s = ci.className;
        if (s.endsWith("Consumer")) {
            s = s.substring(0, s.length() - 8);
        }
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : s;
    }

    private static HealthCheckInfo consumerHealthCheck(IntegrationInfo info, ConsumerInfo ci) {
        if (ci.id == null) {
            return null;
        }
        String hcId = "consumer:" + ci.id;
        for (HealthCheckInfo hc : info.healthChecks) {
            if (hcId.equals(hc.name)) {
                return hc;
            }
        }
        return null;
    }

    static String consumerSchedule(ConsumerInfo ci) {
        // Prefer server-provided schedule (e.g., from quartz dev console)
        if (ci.schedule != null) {
            return ci.schedule;
        }
        // Try to extract cron expression from URI for cron/quartz components
        String cron = extractCronFromUri(ci.uri);
        if (cron != null) {
            return cron;
        }
        // Fall back to period/delay for timer and poll consumers
        if (ci.period != null && ci.period > 0) {
            return "every " + humanDuration(ci.period);
        } else if (ci.delay != null && ci.delay > 0) {
            return "every " + humanDuration(ci.delay);
        }
        return "";
    }

    private static String extractCronFromUri(String uri) {
        if (uri == null) {
            return null;
        }
        // cron:name?schedule=0/5+*+*+*+*+?
        if (uri.startsWith("cron:")) {
            String expr = extractParam(uri, "schedule");
            if (expr != null) {
                return decodeCron(expr);
            }
        }
        // quartz:group/name?cron=0/5+*+*+*+*+?
        if (uri.startsWith("quartz:")) {
            String expr = extractParam(uri, "cron");
            if (expr != null) {
                return decodeCron(expr);
            }
            String trigger = extractParam(uri, "trigger.repeatInterval");
            if (trigger != null) {
                try {
                    return "every " + humanDuration(Long.parseLong(trigger));
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        return null;
    }

    private static String extractParam(String uri, String param) {
        int q = uri.indexOf('?');
        if (q < 0) {
            return null;
        }
        String query = uri.substring(q + 1);
        for (String part : query.split("&")) {
            if (part.startsWith(param + "=")) {
                return part.substring(param.length() + 1);
            }
        }
        return null;
    }

    private static String decodeCron(String encoded) {
        try {
            return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return encoded.replace('+', ' ');
        }
    }

    private static String humanDuration(long ms) {
        if (ms >= 60000 && ms % 60000 == 0) {
            long min = ms / 60000;
            return min + (min == 1 ? "min" : "min");
        }
        if (ms >= 1000 && ms % 1000 == 0) {
            long sec = ms / 1000;
            return sec + "s";
        }
        return ms + "ms";
    }

    private static String consumerSinceLast(ConsumerInfo ci) {
        String s1 = ci.sinceLastStarted != null ? ci.sinceLastStarted : "-";
        String s2 = ci.sinceLastCompleted != null ? ci.sinceLastCompleted : "-";
        String s3 = ci.sinceLastFailed != null ? ci.sinceLastFailed : "-";
        return s1 + "/" + s2 + "/" + s3;
    }

    @Override
    public SelectionContext getSelectionContext() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.consumers.isEmpty()) {
            return null;
        }
        List<ConsumerInfo> sorted = new ArrayList<>(info.consumers);
        sorted.sort(this::sortConsumer);
        List<String> items = sorted.stream().map(c -> c.id != null ? c.id : "").toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Consumers");
    }

    @Override
    public String description() {
        return "Consumer statistics (polling and event-driven consumers)";
    }

    @Override
    public String getHelpText() {
        return DocHelper.loadHelpText("consumers");
    }

    @Override
    public JsonObject getTableDataAsJson() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Consumers");
        JsonArray rows = new JsonArray();
        List<ConsumerInfo> sorted = new ArrayList<>(info.consumers);
        sorted.sort(this::sortConsumer);
        for (ConsumerInfo ci : sorted) {
            JsonObject row = new JsonObject();
            row.put("id", ci.id);
            row.put("uri", ci.uri);
            row.put("state", ci.state);
            row.put("className", ci.className);
            row.put("scheduled", ci.scheduled);
            row.put("remote", ci.remote);
            row.put("inflight", ci.inflight);
            if (ci.totalCounter != null) {
                row.put("totalCounter", ci.totalCounter);
            }
            if (ci.polling != null) {
                row.put("polling", ci.polling);
            }
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", info.consumers.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
