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

class HealthTab implements MonitorTab {

    private final MonitorContext ctx;
    private final TableState tableState = new TableState();
    private boolean showOnlyDown;

    HealthTab(MonitorContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
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

        List<HealthCheckInfo> healthChecks = getFilteredHealthChecks(info);

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
                        Cell.from(Span.styled("GROUP", Style.EMPTY.bold())),
                        Cell.from(Span.styled("NAME", Style.EMPTY.bold())),
                        Cell.from(Span.styled("STATUS", Style.EMPTY.bold())),
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
        MonitorContext.hint(spans, "Esc", "back");
        MonitorContext.hint(spans, "d", "toggle DOWN");
        MonitorContext.hint(spans, "1-9", "tabs");
    }

    boolean isShowOnlyDown() {
        return showOnlyDown;
    }

    List<HealthCheckInfo> getFilteredHealthChecks(IntegrationInfo info) {
        if (showOnlyDown) {
            return info.healthChecks.stream().filter(hc -> "DOWN".equals(hc.state)).toList();
        }
        return info.healthChecks;
    }
}
