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

class HealthTab extends AbstractTableTab {

    private boolean showOnlyDown;

    HealthTab(MonitorContext ctx) {
        super(ctx, "group", "name", "status");
        sortIndex = 1;
        sort = "name";
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        return false;
    }

    @Override
    protected int getRowCount() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        return info != null ? getFilteredHealthChecks(info).size() : 0;
    }

    @Override
    protected boolean handleTabKeyEvent(KeyEvent ke) {
        if (ke.isCharIgnoreCase('d')) {
            showOnlyDown = !showOnlyDown;
            return true;
        }
        return false;
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        List<HealthCheckInfo> healthChecks = new ArrayList<>(getFilteredHealthChecks(info));
        healthChecks.sort(this::sortHealth);

        List<Row> rows = new ArrayList<>();
        for (HealthCheckInfo hc : healthChecks) {
            Style stateStyle;
            String icon;
            if ("UP".equals(hc.state)) {
                stateStyle = Theme.success();
                icon = TuiIcons.HEALTH_UP + " ";
            } else if ("DOWN".equals(hc.state)) {
                stateStyle = Theme.error();
                icon = TuiIcons.HEALTH_DOWN + " ";
            } else {
                stateStyle = Theme.warning();
                icon = TuiIcons.HEALTH_WARN + " ";
            }

            String kind = "";
            if (hc.readiness) {
                kind += "R";
            }
            if (hc.liveness) {
                kind += kind.isEmpty() ? "L" : "/L";
            }

            rows.add(Row.from(
                    Cell.from(Span.styled(" " + (hc.group != null ? hc.group : ""), Style.EMPTY.dim())),
                    Cell.from(Span.styled(hc.name != null ? hc.name : "", Style.EMPTY.fg(Theme.accent()))),
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
                        Cell.from(Span.styled(" " + sortLabel("GROUP", "group"), sortStyle("group"))),
                        Cell.from(Span.styled(sortLabel("NAME", "name"), sortStyle("name"))),
                        Cell.from(Span.styled(sortLabel("STATUS", "status"), sortStyle("status"))),
                        Cell.from(Span.styled("KIND", Style.EMPTY.bold())),
                        Cell.from(Span.styled("MESSAGE", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(12),
                        Constraint.length(25),
                        Constraint.length(12),
                        Constraint.length(6),
                        Constraint.fill())
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, healthChecks.size());
    }

    @Override
    public void renderFooter(List<Span> spans) {
        super.renderFooter(spans);
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
    public String description() {
        return "Health check status for readiness and liveness probes";
    }

    @Override
    public String getHelpText() {
        return DocHelper.loadHelpText("health");
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
        List<HealthCheckInfo> checks = new ArrayList<>(getFilteredHealthChecks(info));
        checks.sort(this::sortHealth);
        for (HealthCheckInfo hi : checks) {
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
