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
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class EventTab extends AbstractTableTab {

    EventTab(MonitorContext ctx) {
        super(ctx, "timestamp", "category", "type", "message");
        this.sort = "timestamp";
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
        return info != null ? info.events.size() : 0;
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        List<EventInfo> sorted = new ArrayList<>(info.events);
        sorted.sort(this::sortEvent);

        List<Row> rows = new ArrayList<>();
        for (EventInfo ei : sorted) {
            String ts = ei.timestamp > 0 ? TimeUtils.printSince(ei.timestamp) : "";
            Style categoryStyle = categoryStyle(ei.category);

            rows.add(Row.from(
                    rightCell(ts, 12),
                    Cell.from(Span.styled(ei.category != null ? ei.category : "", categoryStyle)),
                    Cell.from(Span.styled(ei.type != null ? ei.type : "", Style.EMPTY.fg(Theme.accent()))),
                    Cell.from(ei.message != null ? ei.message : "")));
        }

        if (rows.isEmpty()) {
            rows.add(emptyRow("No events", 4));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        rightCell(sortLabel("AGO", "timestamp"), 12, sortStyle("timestamp")),
                        Cell.from(Span.styled(sortLabel("CATEGORY", "category"), sortStyle("category"))),
                        Cell.from(Span.styled(sortLabel("TYPE", "type"), sortStyle("type"))),
                        Cell.from(Span.styled(sortLabel("MESSAGE", "message"), sortStyle("message")))))
                .widths(
                        Constraint.length(12),
                        Constraint.length(12),
                        Constraint.length(30),
                        Constraint.fill())
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Events ").build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, sorted.size());
    }

    private int sortEvent(EventInfo a, EventInfo b) {
        int result = switch (sort) {
            case "category" -> {
                String ca = a.category != null ? a.category : "";
                String cb = b.category != null ? b.category : "";
                yield ca.compareToIgnoreCase(cb);
            }
            case "type" -> {
                String ta = a.type != null ? a.type : "";
                String tb = b.type != null ? b.type : "";
                yield ta.compareToIgnoreCase(tb);
            }
            case "message" -> {
                String ma = a.message != null ? a.message : "";
                String mb = b.message != null ? b.message : "";
                yield ma.compareToIgnoreCase(mb);
            }
            default -> // "timestamp" — newest first by default
                Long.compare(b.timestamp, a.timestamp);
        };
        return sortReversed ? -result : result;
    }

    private static Style categoryStyle(String category) {
        if (category == null) {
            return Style.EMPTY;
        }
        return switch (category) {
            case "route" -> Style.EMPTY.fg(Theme.accent());
            case "exchange" -> Theme.success();
            default -> Style.EMPTY;
        };
    }

    @Override
    public SelectionContext getSelectionContext() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.events.isEmpty()) {
            return null;
        }
        List<EventInfo> sorted = new ArrayList<>(info.events);
        sorted.sort(this::sortEvent);
        List<String> items = sorted.stream().map(e -> e.type != null ? e.type : "").toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Events");
    }

    @Override
    public String description() {
        return "Camel lifecycle events (context, route, and exchange events)";
    }

    @Override
    public String getHelpText() {
        return """
                # Events

                Shows Camel lifecycle events captured by the event notifier. Events are
                grouped into three categories:

                - **general** — Context-level events: CamelContext starting/started/stopping,
                  service add/remove, component add/remove
                - **route** — Route lifecycle events: route added/removed/started/stopped/reloaded
                - **exchange** — Exchange events: exchange created/completed/failed/sending/sent

                Events are stored in a circular buffer (default capacity 25 per category).
                Only the most recent events are shown.

                ## Table Columns

                - **AGO** — How long ago the event occurred (e.g., `2s`, `1m30s`)
                - **CATEGORY** — Event category: `general`, `route`, or `exchange`
                - **TYPE** — The specific event type (e.g., `CamelContextStartedEvent`,
                  `RouteStartedEvent`, `ExchangeCompletedEvent`)
                - **MESSAGE** — Human-readable event description

                ## Keys

                - `Up/Down` — select event
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
        result.put("tab", "Events");
        JsonArray rows = new JsonArray();
        List<EventInfo> sorted = new ArrayList<>(info.events);
        sorted.sort(this::sortEvent);
        for (EventInfo ei : sorted) {
            JsonObject row = new JsonObject();
            row.put("type", ei.type);
            row.put("category", ei.category);
            if (ei.timestamp > 0) {
                row.put("timestamp", ei.timestamp);
            }
            if (ei.exchangeId != null) {
                row.put("exchangeId", ei.exchangeId);
            }
            row.put("message", ei.message);
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", info.events.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
