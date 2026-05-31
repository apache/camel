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
}
