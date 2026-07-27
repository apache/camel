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
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
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
    public void navigateUp() {
    }

    @Override
    public void navigateDown() {
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)
                || ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)
                || ke.isHome() || ke.isEnd()) {
            return false;
        }
        return super.handleKeyEvent(ke);
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

        List<Row> rows = new ArrayList<>();
        for (ProducerInfo pi : sorted) {
            String type = producerType(pi);
            Style statusStyle = "Started".equals(pi.state) ? Theme.success() : Theme.error();

            rows.add(Row.from(
                    Cell.from(Span.styled(" " + (pi.routeId != null ? pi.routeId : ""), Style.EMPTY.fg(Theme.accent()))),
                    Cell.from(Span.styled(pi.state != null ? pi.state : "", statusStyle)),
                    Cell.from(type),
                    Cell.from(pi.remote ? "x" : ""),
                    Cell.from(pi.uri != null ? pi.uri : "")));
        }

        if (rows.isEmpty()) {
            rows.add(emptyRow("No producers", 5));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(" " + sortLabel("ROUTE", "route"), sortStyle("route"))),
                        Cell.from(Span.styled(sortLabel("STATUS", "status"), sortStyle("status"))),
                        Cell.from(Span.styled(sortLabel("TYPE", "type"), sortStyle("type"))),
                        Cell.from(Span.styled("REMOTE", Style.EMPTY.bold())),
                        Cell.from(Span.styled(sortLabel("URI", "uri"), sortStyle("uri")))))
                .widths(
                        Constraint.length(20),
                        Constraint.length(10),
                        Constraint.length(20),
                        Constraint.length(8),
                        Constraint.fill())
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Producers ").build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, sorted.size());
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
        return """
                # Producers

                Producers are the **output** side of a Camel route. They send data to
                external systems (message brokers, HTTP endpoints, databases, files, etc.).

                Unlike consumers (one per route), a route can have multiple producers
                — each `.to()` or `.toD()` call in the route creates a producer.

                ## Table Columns

                - **ROUTE** — The route this producer belongs to
                - **STATUS** — Producer state: `Started` (running normally) or `Stopped`
                - **TYPE** — The Camel component type (e.g., `Kafka`, `Http`, `Log`, `Seda`)
                - **REMOTE** — Whether this producer sends to a remote system (`Yes`) or is in-process (`No`)
                - **URI** — The full endpoint URI (e.g., `kafka://my-topic`, `log://mylogger`)

                ## Keys

                - `Up/Down` — select producer
                - `s` — cycle sort column
                - `S` — reverse sort order
                """;
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
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", info.producers.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
