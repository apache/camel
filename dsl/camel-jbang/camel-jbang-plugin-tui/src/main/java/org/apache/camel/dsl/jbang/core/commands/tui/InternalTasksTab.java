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

class InternalTasksTab extends AbstractTableTab {

    InternalTasksTab(MonitorContext ctx) {
        super(ctx, "name", "status", "attempts", "elapsed");
    }

    @Override
    protected int getRowCount() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        return info != null ? info.internalTasks.size() : 0;
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        List<InternalTaskInfo> sorted = new ArrayList<>(info.internalTasks);
        sorted.sort(this::sortTask);

        List<Row> rows = new ArrayList<>();
        for (InternalTaskInfo ti : sorted) {
            String statusText = ti.attempting
                    ? "Attempting" : ("Active".equals(ti.status) ? "Waiting" : (ti.status != null ? ti.status : ""));
            Style statusStyle = ti.attempting ? Theme.warning() : statusStyle(ti.status);
            String elapsed = TimeUtils.printAge(ti.elapsed);
            String first = ti.firstTime > 0 ? TimeUtils.printSince(ti.firstTime) : "";
            String last = ti.lastTime > 0 ? TimeUtils.printSince(ti.lastTime) : "";
            String next = formatNext(ti.nextTime);
            String error = ti.error != null ? ti.error : "";

            rows.add(Row.from(
                    Cell.from(Span.styled(" " + (ti.name != null ? ti.name : ""), Style.EMPTY.fg(Theme.accent()))),
                    Cell.from(Span.styled(statusText, statusStyle)),
                    rightCell(String.valueOf(ti.attempts), 8),
                    rightCell(String.valueOf(ti.delay), 8),
                    Cell.from(elapsed),
                    Cell.from(first),
                    Cell.from(last),
                    Cell.from(next),
                    Cell.from(Span.styled(error, error.isEmpty() ? Style.EMPTY : Theme.error()))));
        }

        if (rows.isEmpty()) {
            rows.add(emptyRow("No recovery tasks", 9));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(" " + sortLabel("NAME", "name"), sortStyle("name"))),
                        Cell.from(Span.styled(sortLabel("STATUS", "status"), sortStyle("status"))),
                        rightCell(sortLabel("ATTEMPTS", "attempts"), 8, sortStyle("attempts")),
                        rightCell("DELAY", 8, Style.EMPTY.bold()),
                        Cell.from(Span.styled(sortLabel("ELAPSED", "elapsed"), sortStyle("elapsed"))),
                        Cell.from(Span.styled("FIRST", Style.EMPTY.bold())),
                        Cell.from(Span.styled("LAST", Style.EMPTY.bold())),
                        Cell.from(Span.styled("NEXT", Style.EMPTY.bold())),
                        Cell.from(Span.styled("ERROR", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(30),
                        Constraint.length(12),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(10),
                        Constraint.length(10),
                        Constraint.length(10),
                        Constraint.length(10),
                        Constraint.fill())
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" Recovery Tasks ").build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, sorted.size());
    }

    private int sortTask(InternalTaskInfo a, InternalTaskInfo b) {
        int result = switch (sort) {
            case "status" -> {
                String sa = a.status != null ? a.status : "";
                String sb = b.status != null ? b.status : "";
                yield sa.compareToIgnoreCase(sb);
            }
            case "attempts" -> Long.compare(b.attempts, a.attempts);
            case "elapsed" -> Long.compare(b.elapsed, a.elapsed);
            default -> { // "name"
                String na = a.name != null ? a.name : "";
                String nb = b.name != null ? b.name : "";
                yield na.compareToIgnoreCase(nb);
            }
        };
        return sortReversed ? -result : result;
    }

    private static Style statusStyle(String status) {
        if (status == null) {
            return Style.EMPTY;
        }
        return switch (status) {
            case "Active" -> Theme.success();
            case "Completed" -> Theme.info();
            case "Failed", "Exhausted" -> Theme.error();
            default -> Style.EMPTY;
        };
    }

    private static String formatNext(long nextTime) {
        if (nextTime <= 0) {
            return "";
        }
        long age = nextTime - System.currentTimeMillis();
        if (age <= 0) {
            return "";
        }
        return TimeUtils.printDuration(age);
    }

    @Override
    public String description() {
        return "Recovery and reconnection tasks (retries, leader election)";
    }

    @Override
    public String getHelpText() {
        return DocHelper.loadHelpText("internal-tasks");
    }

    @Override
    public JsonObject getTableDataAsJson() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Recovery Tasks");
        JsonArray rows = new JsonArray();
        List<InternalTaskInfo> sorted = new ArrayList<>(info.internalTasks);
        sorted.sort(this::sortTask);
        for (InternalTaskInfo ti : sorted) {
            JsonObject row = new JsonObject();
            row.put("name", ti.name);
            row.put("status", ti.status);
            row.put("attempting", ti.attempting);
            row.put("attempts", ti.attempts);
            row.put("delay", ti.delay);
            row.put("elapsed", ti.elapsed);
            row.put("firstTime", ti.firstTime);
            row.put("lastTime", ti.lastTime);
            row.put("nextTime", ti.nextTime);
            row.put("error", ti.error);
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", info.internalTasks.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
