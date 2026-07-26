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

class RouteControllerTab extends AbstractTableTab {

    RouteControllerTab(MonitorContext ctx) {
        super(ctx, "route", "status", "supervising", "attempts", "uri");
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
        return info != null ? info.routeControllerRoutes.size() : 0;
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        // Handle non-supervised controller
        if ("DefaultRouteController".equals(info.routeControllerType)) {
            renderEmptyState(frame, area, "Route controller: Default (not supervised)", " Route Controller ");
            return;
        }

        List<RouteControllerInfo> sorted = new ArrayList<>(info.routeControllerRoutes);
        sorted.sort(this::sortRouteController);

        // Handle supervised controller with all routes started successfully
        if ("SupervisingRouteController".equals(info.routeControllerType)
                && !info.routeControllerUnhealthy
                && info.routeControllerRestartingRoutes == 0
                && info.routeControllerExhaustedRoutes == 0
                && sorted.stream().allMatch(r -> "Started".equals(r.status))) {
            renderEmptyState(frame, area, "All routes started successfully", " Route Controller (Supervised) ");
            return;
        }

        List<Row> rows = new ArrayList<>();
        for (RouteControllerInfo rc : sorted) {
            Style statusStyle = "Started".equals(rc.status) ? Theme.success() : Theme.error();
            String supervising = rc.supervising != null ? rc.supervising : "";
            String attempts = rc.attempts > 0 ? String.valueOf(rc.attempts) : "";
            String lastAttempt = rc.lastAttempt > 0 ? TimeUtils.printSince(rc.lastAttempt) : "";
            String nextAttempt = rc.nextAttempt > 0 ? TimeUtils.printSince(rc.nextAttempt) : "";
            String uriOrError = rc.error != null ? rc.error : (rc.uri != null ? rc.uri : "");
            Style uriStyle = rc.error != null ? Theme.error() : Style.EMPTY;

            rows.add(Row.from(
                    Cell.from(Span.styled(" " + (rc.routeId != null ? rc.routeId : ""), Style.EMPTY.fg(Theme.accent()))),
                    Cell.from(Span.styled(rc.status != null ? rc.status : "", statusStyle)),
                    Cell.from(supervising),
                    rightCell(attempts, 10),
                    rightCell(lastAttempt, 12),
                    rightCell(nextAttempt, 12),
                    Cell.from(Span.styled(uriOrError, uriStyle))));
        }

        if (rows.isEmpty()) {
            rows.add(emptyRow("No routes under supervision", 7));
        }

        String title = "SupervisingRouteController".equals(info.routeControllerType)
                ? " Route Controller (Supervised) "
                : " Route Controller ";

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(" " + sortLabel("ROUTE", "route"), sortStyle("route"))),
                        Cell.from(Span.styled(sortLabel("STATUS", "status"), sortStyle("status"))),
                        Cell.from(Span.styled(sortLabel("SUPERVISING", "supervising"), sortStyle("supervising"))),
                        rightCell(sortLabel("ATTEMPTS", "attempts"), 10, sortStyle("attempts")),
                        rightCell("LAST", 12, Style.EMPTY.bold()),
                        rightCell("NEXT", 12, Style.EMPTY.bold()),
                        Cell.from(Span.styled(sortLabel("URI", "uri"), sortStyle("uri")))))
                .widths(
                        Constraint.length(20),
                        Constraint.length(10),
                        Constraint.length(14),
                        Constraint.length(10),
                        Constraint.length(12),
                        Constraint.length(12),
                        Constraint.fill())
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(title).build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, sorted.size());
    }

    private void renderEmptyState(Frame frame, Rect area, String message, String title) {
        List<Row> rows = new ArrayList<>();
        rows.add(Row.from(Cell.from(Span.styled(" " + message, Theme.muted()))));

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(Cell.from(Span.styled(" INFO", Style.EMPTY.bold()))))
                .widths(Constraint.fill())
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(title).build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
    }

    private int sortRouteController(RouteControllerInfo a, RouteControllerInfo b) {
        int result = switch (sort) {
            case "status" -> {
                String sa = a.status != null ? a.status : "";
                String sb = b.status != null ? b.status : "";
                yield sa.compareToIgnoreCase(sb);
            }
            case "supervising" -> {
                String sa = a.supervising != null ? a.supervising : "";
                String sb = b.supervising != null ? b.supervising : "";
                yield sa.compareToIgnoreCase(sb);
            }
            case "attempts" -> Long.compare(b.attempts, a.attempts);
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

    @Override
    public SelectionContext getSelectionContext() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.routeControllerRoutes.isEmpty()) {
            return null;
        }
        List<RouteControllerInfo> sorted = new ArrayList<>(info.routeControllerRoutes);
        sorted.sort(this::sortRouteController);
        List<String> items = sorted.stream().map(r -> r.routeId != null ? r.routeId : "").toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Route Controller");
    }

    @Override
    public String description() {
        return "Route controller status (supervised route startup and restart attempts)";
    }

    @Override
    public String getHelpText() {
        return """
                # Route Controller

                Shows the status of the Camel route controller. The route controller manages
                route startup and can automatically restart routes that fail to start.

                There are two types of route controllers:

                - **Default** — Routes are started in order and a failure stops the context.
                  This tab shows "Route controller: Default (not supervised)" in this case.
                - **Supervising** — Routes that fail to start are retried with exponential
                  backoff. The controller tracks attempts and manages restart scheduling.

                When using the supervised controller and all routes start successfully,
                the tab shows "All routes started successfully".

                ## Table Columns

                - **ROUTE** — The route ID
                - **STATUS** — Route state: `Started`, `Stopped`, or other lifecycle states
                - **SUPERVISING** — Supervision status (e.g., `Active` when the route is being
                  restarted by the controller)
                - **ATTEMPTS** — Number of restart attempts so far
                - **LAST** — Time since the last restart attempt
                - **NEXT** — Time until the next scheduled restart attempt
                - **URI** — The route's consumer endpoint URI. If the route has a startup error,
                  the error message is shown here instead (in red)

                ## Keys

                - `Up/Down` — select route
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
        result.put("tab", "Route Controller");
        result.put("controllerType", info.routeControllerType);
        result.put("unhealthy", info.routeControllerUnhealthy);
        result.put("restartingRoutes", info.routeControllerRestartingRoutes);
        result.put("exhaustedRoutes", info.routeControllerExhaustedRoutes);
        JsonArray rows = new JsonArray();
        List<RouteControllerInfo> sorted = new ArrayList<>(info.routeControllerRoutes);
        sorted.sort(this::sortRouteController);
        for (RouteControllerInfo rc : sorted) {
            JsonObject row = new JsonObject();
            row.put("routeId", rc.routeId);
            row.put("status", rc.status);
            row.put("uri", rc.uri);
            if (rc.supervising != null) {
                row.put("supervising", rc.supervising);
            }
            row.put("attempts", rc.attempts);
            if (rc.lastAttempt > 0) {
                row.put("lastAttempt", rc.lastAttempt);
            }
            if (rc.nextAttempt > 0) {
                row.put("nextAttempt", rc.nextAttempt);
            }
            if (rc.error != null) {
                row.put("error", rc.error);
            }
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", info.routeControllerRoutes.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
