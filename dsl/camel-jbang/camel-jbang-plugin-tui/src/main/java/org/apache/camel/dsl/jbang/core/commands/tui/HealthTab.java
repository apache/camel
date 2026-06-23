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
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class HealthTab implements MonitorTab {

    private static final String[] SORT_COLUMNS = { "group", "name", "status" };

    private final MonitorContext ctx;
    private final TableState tableState = new TableState();
    private boolean showOnlyDown;
    private String sort = "name";
    private int sortIndex = 1;
    private boolean sortReversed;

    HealthTab(MonitorContext ctx) {
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
        if (ke.isCharIgnoreCase('d')) {
            showOnlyDown = !showOnlyDown;
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
            MonitorContext.renderNoSelection(frame, area);
            return;
        }

        List<HealthCheckInfo> healthChecks = new ArrayList<>(getFilteredHealthChecks(info));
        healthChecks.sort(this::sortHealth);

        List<Row> rows = new ArrayList<>();
        for (HealthCheckInfo hc : healthChecks) {
            Style stateStyle;
            String icon;
            if ("UP".equals(hc.state)) {
                stateStyle = Style.EMPTY.fg(Color.GREEN);
                icon = "✔ ";
            } else if ("DOWN".equals(hc.state)) {
                stateStyle = Style.EMPTY.fg(Color.LIGHT_RED);
                icon = "✖ ";
            } else {
                stateStyle = Style.EMPTY.fg(Color.YELLOW);
                icon = "⚠ ";
            }

            String kind = "";
            if (hc.readiness) {
                kind += "R";
            }
            if (hc.liveness) {
                kind += kind.isEmpty() ? "L" : "/L";
            }

            rows.add(Row.from(
                    Cell.from(Span.styled(hc.group != null ? hc.group : "", Style.EMPTY.dim())),
                    Cell.from(Span.styled(hc.name != null ? hc.name : "", Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(Span.styled(icon + hc.state, stateStyle)),
                    Cell.from(kind),
                    Cell.from(hc.message != null ? hc.message : "")));
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(""),
                    Cell.from(Span.styled(showOnlyDown ? "No DOWN checks" : "No health checks registered",
                            Style.EMPTY.dim())),
                    Cell.from(""),
                    Cell.from(""),
                    Cell.from("")));
        }

        String title = showOnlyDown
                ? " Health [DOWN only] "
                : " Health ";

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("GROUP", "group", sort, sortReversed), sortStyle("group", sort))),
                        Cell.from(Span.styled(sortLabel("NAME", "name", sort, sortReversed), sortStyle("name", sort))),
                        Cell.from(Span.styled(sortLabel("STATUS", "status", sort, sortReversed), sortStyle("status", sort))),
                        Cell.from(Span.styled("KIND", Style.EMPTY.bold())),
                        Cell.from(Span.styled("MESSAGE", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(12),
                        Constraint.length(25),
                        Constraint.length(12),
                        Constraint.length(6),
                        Constraint.fill())
                .block(Block.builder().borderType(BorderType.ROUNDED).title(title).build())
                .build();

        frame.renderStatefulWidget(table, area, tableState);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, "s", "sort");
        hint(spans, "d", "toggle DOWN");
    }

    boolean isShowOnlyDown() {
        return showOnlyDown;
    }

    private int sortHealth(HealthCheckInfo a, HealthCheckInfo b) {
        int result = switch (sort) {
            case "name" -> compareStr(a.name, b.name);
            case "status" -> compareStr(a.state, b.state);
            default -> compareStr(a.group, b.group);
        };
        return sortReversed ? -result : result;
    }

    List<HealthCheckInfo> getFilteredHealthChecks(IntegrationInfo info) {
        if (showOnlyDown) {
            return info.healthChecks.stream().filter(hc -> "DOWN".equals(hc.state)).toList();
        }
        return info.healthChecks;
    }

    @Override
    public SelectionContext getSelectionContext() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return null;
        }
        List<HealthCheckInfo> checks = new ArrayList<>(getFilteredHealthChecks(info));
        if (checks.isEmpty()) {
            return null;
        }
        checks.sort(this::sortHealth);
        List<String> items = checks.stream().map(hc -> hc.name != null ? hc.name : "").toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Health");
    }

    @Override
    public String getHelpText() {
        return """
                # Health

                Health checks verify that the integration and its dependencies are
                functioning correctly. They are essential for container orchestration
                platforms like Kubernetes, but also useful for local monitoring.

                ## Table Columns

                - **GROUP** — Category of the health check: `camel` (core checks), `routes` (per-route health), `consumers` (consumer health)
                - **NAME** — Specific check name. For route checks this is the route ID; for consumer checks it is the consumer endpoint
                - **STATUS** — Health state: `UP` (green, healthy), `DOWN` (red, unhealthy), `UNKNOWN` (yellow, cannot determine)
                - **KIND** — Check type: `R` = Readiness only, `L` = Liveness only, `R/L` = serves both purposes
                - **MESSAGE** — Status details. Most useful when a check is DOWN — shows what went wrong (e.g., `Connection refused`, `Route stopped`)

                ## Example Screen

                ```
                 GROUP    NAME              STATUS  KIND  MESSAGE
                 camel    context           UP      R/L
                 camel    route-controller  UP      R
                 camel    security-policy   UP      R     security.status: clean
                 routes   timer-to-log      UP      R
                 routes   seda-consumer     UP      R
                ```

                All checks UP means the integration is healthy and ready to receive
                traffic. If any check shows DOWN, the MESSAGE column explains why.

                ## Readiness vs Liveness

                These concepts come from Kubernetes but apply to any deployment:

                - **Liveness** (`L`): Is the process alive and not stuck in a deadlock
                  or infinite loop? If a liveness check fails, the platform should
                  **restart** the process. Only fundamental checks use liveness —
                  the `context` check verifies the CamelContext is still running.

                - **Readiness** (`R`): Is the process ready to handle traffic? During
                  startup, a process may be alive but not yet ready (e.g., still
                  connecting to a database or loading routes). If readiness fails,
                  the platform should **stop sending traffic** but not restart.

                A check marked `R/L` serves both purposes. The `context` check is
                typically both — if the CamelContext is down, the process should be
                restarted and traffic should be rerouted.

                ## Built-in Health Checks

                Camel includes several built-in checks:

                - **context** — Is the CamelContext started and running?
                - **route-controller** — Are all routes started successfully?
                - **security-policy** — Are there any security policy violations?
                - **consumers** — Are consumers connected and healthy?
                - **routes** — Per-route health status

                Components can also contribute their own health checks. For example,
                a Kafka consumer checks its broker connectivity, and a database
                component checks its connection pool.

                ## Kubernetes Integration

                When deployed to Kubernetes, these health checks are exposed as
                HTTP endpoints:

                - `/observe/health/ready` — returns 200 if all readiness checks pass
                - `/observe/health/live` — returns 200 if all liveness checks pass

                Kubernetes uses these to decide when to send traffic to a pod and
                when to restart it.

                ## Keys

                - `Up/Down` — select health check
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
        result.put("tab", "Health");
        JsonArray rows = new JsonArray();
        for (HealthCheckInfo hi : info.healthChecks) {
            JsonObject row = new JsonObject();
            row.put("group", hi.group);
            row.put("name", hi.name);
            row.put("state", hi.state);
            row.put("readiness", hi.readiness);
            row.put("liveness", hi.liveness);
            if (hi.message != null) {
                row.put("message", hi.message);
            }
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", info.healthChecks.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
