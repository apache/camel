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
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class ConsumersTab implements MonitorTab {

    private static final String[] SORT_COLUMNS = { "id", "status", "type", "inflight", "polls", "uri" };

    private final MonitorContext ctx;
    private final TableState tableState = new TableState();
    private String sort = "id";
    private int sortIndex;
    private boolean sortReversed;

    ConsumersTab(MonitorContext ctx) {
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
    }

    @Override
    public void navigateDown() {
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        List<ConsumerInfo> sorted = new ArrayList<>(info.consumers);
        sorted.sort(this::sortConsumer);

        List<Row> rows = new ArrayList<>();
        for (ConsumerInfo ci : sorted) {
            String status = consumerStatus(ci);
            HealthCheckInfo hc = consumerHealthCheck(info, ci);
            boolean healthDown = hc != null && "DOWN".equals(hc.state);
            Style statusStyle = healthDown
                    ? Style.EMPTY.fg(Color.LIGHT_RED)
                    : ("Started".equals(ci.state) || "Polling".equals(status)
                            ? Style.EMPTY.fg(Color.GREEN)
                            : Style.EMPTY.fg(Color.LIGHT_RED));
            String statusText = healthDown ? "⚠ " + status : status;
            String type = consumerType(ci);
            String period = consumerPeriod(ci);
            String sinceLast = consumerSinceLast(ci);
            String uri = healthDown && hc.message != null
                    ? hc.message
                    : (ci.uri != null ? ci.uri : "");

            rows.add(Row.from(
                    Cell.from(Span.styled(ci.id != null ? ci.id : "", Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(Span.styled(statusText, statusStyle)),
                    Cell.from(type),
                    rightCell(String.valueOf(ci.inflight), 8),
                    rightCell(ci.totalCounter != null ? String.valueOf(ci.totalCounter) : "", 8),
                    rightCell(period, 10),
                    Cell.from(sinceLast),
                    Cell.from(Span.styled(uri, healthDown ? Style.EMPTY.fg(Color.LIGHT_RED) : Style.EMPTY))));
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(Span.styled("No consumers", Style.EMPTY.dim())),
                    Cell.from(""), Cell.from(""), Cell.from(""),
                    Cell.from(""), Cell.from(""), Cell.from(""), Cell.from("")));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("ROUTE", "id"), sortStyle("id"))),
                        Cell.from(Span.styled(sortLabel("STATUS", "status"), sortStyle("status"))),
                        Cell.from(Span.styled(sortLabel("TYPE", "type"), sortStyle("type"))),
                        rightCell(sortLabel("INFLIGHT", "inflight"), 8, sortStyle("inflight")),
                        rightCell(sortLabel("POLLS", "polls"), 8, sortStyle("polls")),
                        rightCell("PERIOD", 10, Style.EMPTY.bold()),
                        Cell.from(Span.styled("SINCE-LAST", Style.EMPTY.bold())),
                        Cell.from(Span.styled(sortLabel("URI", "uri"), sortStyle("uri")))))
                .widths(
                        Constraint.length(20),
                        Constraint.length(10),
                        Constraint.length(20),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(10),
                        Constraint.length(22),
                        Constraint.fill())
                .block(Block.builder().borderType(BorderType.ROUNDED)
                        .title(" Consumers sort:" + sort + " ").build())
                .build();

        frame.renderStatefulWidget(table, area, tableState);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, "s", "sort");
        hint(spans, "1-9", "tabs");
    }

    private String sortLabel(String label, String column) {
        return MonitorContext.sortLabel(label, column, sort, sortReversed);
    }

    private Style sortStyle(String column) {
        return MonitorContext.sortStyle(column, sort);
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

    private static String consumerPeriod(ConsumerInfo ci) {
        if (ci.period != null) {
            return ci.period + "ms";
        } else if (ci.delay != null) {
            return ci.delay + "ms";
        }
        return "";
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
}
