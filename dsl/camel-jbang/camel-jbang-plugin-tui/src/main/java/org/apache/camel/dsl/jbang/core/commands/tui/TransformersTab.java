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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class TransformersTab extends AbstractTableTab {

    private final AtomicBoolean loading = new AtomicBoolean(false);

    private List<TransformerData> allTransformers = Collections.emptyList();
    private String lastPid;

    TransformersTab(MonitorContext ctx) {
        super(ctx, "name", "from", "to");
    }

    @Override
    protected int getRowCount() {
        return sortedTransformers().size();
    }

    @Override
    public void onTabSelected() {
        String pid = ctx.selectedPid;
        if (pid != null && !pid.equals(lastPid)) {
            lastPid = pid;
            allTransformers = Collections.emptyList();
        }
        if (allTransformers.isEmpty()) {
            loadTransformers();
        }
    }

    @Override
    public void onIntegrationChanged() {
        allTransformers = Collections.emptyList();
        lastPid = null;
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        if (loading.get() && allTransformers.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(" Loading transformers...", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Data Type Transformers ").build())
                            .build(),
                    area);
            return;
        }

        List<TransformerData> visible = sortedTransformers();
        renderTable(frame, area, visible);
    }

    private void renderTable(Frame frame, Rect area, List<TransformerData> visible) {
        List<Row> rows = new ArrayList<>();
        for (TransformerData t : visible) {
            rows.add(Row.from(
                    Cell.from(Span.styled(t.name != null ? t.name : "", Style.EMPTY.fg(Theme.accent()))),
                    Cell.from(Span.styled(t.from != null ? t.from : "*", Style.EMPTY)),
                    Cell.from(Span.styled(t.to != null ? t.to : "*", Style.EMPTY))));
        }

        if (rows.isEmpty()) {
            rows.add(emptyRow("No data type transformers", 3));
        }

        String title = String.format(" Data Type Transformers [%d] ", visible.size());

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("NAME", "name"), sortStyle("name"))),
                        Cell.from(Span.styled(sortLabel("FROM", "from"), sortStyle("from"))),
                        Cell.from(Span.styled(sortLabel("TO", "to"), sortStyle("to")))))
                .widths(
                        Constraint.percentage(40),
                        Constraint.percentage(30),
                        Constraint.fill())
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, visible.size());
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, TuiIcons.HINT_SCROLL, "navigate");
        hintLast(spans, "s", "sort");
    }

    @Override
    public SelectionContext getSelectionContext() {
        List<TransformerData> visible = sortedTransformers();
        if (visible.isEmpty()) {
            return null;
        }
        List<String> items = visible.stream().map(t -> t.name != null ? t.name : "?").toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Data Type Transformers");
    }

    private List<TransformerData> sortedTransformers() {
        List<TransformerData> result = new ArrayList<>(allTransformers);
        result.sort((a, b) -> {
            int cmp = switch (sort) {
                case "from" -> compareStr(a.from, b.from);
                case "to" -> compareStr(a.to, b.to);
                default -> compareStr(a.name, b.name);
            };
            return sortReversed ? -cmp : cmp;
        });
        return result;
    }

    private void loadTransformers() {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        String pid = ctx.selectedPid;
        ctx.backgroundExecutor.execute(() -> {
            try {
                loadTransformersInBackground(pid);
            } finally {
                loading.set(false);
            }
        });
    }

    private void loadTransformersInBackground(String pid) {
        JsonObject root = new JsonObject();
        root.put("action", "transformers");

        JsonObject jo = ctx.executeAction(pid, root, 5000);
        if (jo == null) {
            return;
        }

        JsonArray arr = jo.getCollection("transformers");
        if (arr == null) {
            return;
        }

        List<TransformerData> result = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JsonObject tj = (JsonObject) arr.get(i);
            TransformerData td = new TransformerData();
            td.name = tj.getString("name");
            td.from = tj.getString("from");
            td.to = tj.getString("to");
            result.add(td);
        }

        if (ctx.runner != null) {
            ctx.runner.runOnRenderThread(() -> {
                allTransformers = result;
                lastPid = pid;
            });
        }
    }

    static class TransformerData {
        String name;
        String from;
        String to;
    }

    @Override
    public String description() {
        return "Registered data type transformers in the Camel context";
    }

    @Override
    public String getHelpText() {
        return """
                # Data Type Transformers

                The Data Type Transformers tab shows all transformers registered in the
                Camel transformer registry. Data type transformers are used to convert
                message bodies between declared data types as part of Camel's contract
                mechanism (inputType/outputType on routes).

                When a route declares an inputType or outputType, Camel automatically
                looks up a matching transformer and applies it to convert the message
                body between the source and target data types.

                ## Table Columns

                - **NAME** — Transformer name (e.g. `json:jackson`, `xml:jaxb`)
                - **FROM** — Source data type (`*` means any input type)
                - **TO** — Target data type (`*` means any output type)

                ## Keys

                - `Up/Down` — select transformer
                - `s` — cycle sort column
                - `S` — reverse sort order
                - `Esc` — back
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        List<TransformerData> transformers = sortedTransformers();
        if (transformers.isEmpty()) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Data Type Transformers");
        JsonArray rows = new JsonArray();
        for (TransformerData t : transformers) {
            JsonObject row = new JsonObject();
            row.put("name", t.name);
            if (t.from != null) {
                row.put("from", t.from);
            }
            if (t.to != null) {
                row.put("to", t.to);
            }
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", transformers.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
