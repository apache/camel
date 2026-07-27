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
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class TypeConvertersTab extends AbstractTableTab {

    private static final String[] SCOPES = { "all", "non-jdk", "custom", "core" };

    private final AtomicBoolean loading = new AtomicBoolean(false);

    private int scopeIndex;
    private boolean filterInputActive;
    private TextInputState filterInputState = new TextInputState("");
    private String filterTerm;
    private List<ConverterData> allConverters = Collections.emptyList();
    private String lastPid;

    TypeConvertersTab(MonitorContext ctx) {
        super(ctx, "from", "to");
    }

    @Override
    protected int getRowCount() {
        return sortedConverters().size();
    }

    @Override
    public void onTabSelected() {
        String pid = ctx.selectedPid;
        if (pid != null && !pid.equals(lastPid)) {
            lastPid = pid;
            allConverters = Collections.emptyList();
        }
        if (allConverters.isEmpty()) {
            loadConverters();
        }
    }

    @Override
    public void onIntegrationChanged() {
        allConverters = Collections.emptyList();
        lastPid = null;
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (filterInputActive) {
            return handleFilterInput(ke);
        }
        if (ke.isChar('/')) {
            filterInputActive = true;
            filterInputState = new TextInputState(filterTerm != null ? filterTerm : "");
            return true;
        }
        return super.handleKeyEvent(ke);
    }

    private boolean handleFilterInput(KeyEvent ke) {
        if (ke.isKey(KeyCode.ESCAPE)) {
            filterInputActive = false;
            return true;
        }
        if (ke.isConfirm()) {
            String text = filterInputState.text().trim();
            filterTerm = text.isEmpty() ? null : text;
            filterInputActive = false;
            tableState.select(0);
            return true;
        }
        FormHelper.handleTextInput(ke, filterInputState);
        return true;
    }

    @Override
    public boolean handleEscape() {
        if (filterTerm != null) {
            filterTerm = null;
            tableState.select(0);
            return true;
        }
        return false;
    }

    @Override
    protected boolean handleTabKeyEvent(KeyEvent ke) {
        if (ke.isCharIgnoreCase('f')) {
            scopeIndex = (scopeIndex + 1) % SCOPES.length;
            return true;
        }
        return false;
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        if (loading.get() && allConverters.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(" Loading type converters...", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Type Converters ").build())
                            .build(),
                    area);
            return;
        }

        List<ConverterData> visible = sortedConverters();
        renderTable(frame, area, visible);
    }

    private void renderTable(Frame frame, Rect area, List<ConverterData> visible) {
        List<Row> rows = new ArrayList<>();
        for (ConverterData c : visible) {
            boolean core = isCoreConverter(c);
            String kindLabel = core ? "core" : "custom";
            Style kindStyle = core ? Style.EMPTY.dim() : Style.EMPTY.fg(Theme.baseFg());
            rows.add(Row.from(
                    Cell.from(Span.styled(displayName(c.from), Style.EMPTY.fg(Theme.accent()))),
                    Cell.from(Span.styled(displayName(c.to), Style.EMPTY)),
                    Cell.from(Span.styled(kindLabel, kindStyle))));
        }

        if (rows.isEmpty()) {
            rows.add(emptyRow("No type converters", 3));
        }

        String scope = SCOPES[scopeIndex];
        String title = filterTerm != null
                ? String.format(" Type Converters [%d/%d] scope:%s filter:\"%s\" ", visible.size(), allConverters.size(),
                        scope, filterTerm)
                : String.format(" Type Converters [%d/%d] scope:%s ", visible.size(), allConverters.size(), scope);

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("FROM", "from"), sortStyle("from"))),
                        Cell.from(Span.styled(sortLabel("TO", "to"), sortStyle("to"))),
                        Cell.from(Span.styled("KIND", Style.EMPTY))))
                .widths(
                        Constraint.percentage(40),
                        Constraint.percentage(40),
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
        if (filterInputActive) {
            spans.add(Span.styled(" /", Theme.label().bold()));
            spans.add(Span.raw(filterInputState.text() + "█  "));
            hint(spans, "Enter", "filter");
            hintLast(spans, "Esc", "cancel");
            return;
        }
        hint(spans, "Esc", filterTerm != null ? "clear" : "back");
        hint(spans, "f", "scope [" + SCOPES[scopeIndex] + "]");
        if (filterTerm != null) {
            spans.add(Span.styled("  /", Theme.label().bold()));
            spans.add(Span.raw("\"" + filterTerm + "\"  "));
        } else {
            hint(spans, "/", "filter");
        }
        hint(spans, TuiIcons.HINT_SCROLL, "navigate");
        hintLast(spans, "s", "sort");
    }

    @Override
    public SelectionContext getSelectionContext() {
        List<ConverterData> visible = sortedConverters();
        if (visible.isEmpty()) {
            return null;
        }
        List<String> items = visible.stream().map(c -> c.from + " -> " + c.to).toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Type Converters");
    }

    private List<ConverterData> sortedConverters() {
        String scope = SCOPES[scopeIndex];
        String ft = filterTerm != null ? filterTerm.toLowerCase() : null;
        List<ConverterData> result = new ArrayList<>();
        for (ConverterData c : allConverters) {
            if ("non-jdk".equals(scope) && isJdkOnly(c)) {
                continue;
            }
            if ("custom".equals(scope) && isCoreConverter(c)) {
                continue;
            }
            if ("core".equals(scope) && !isCoreConverter(c)) {
                continue;
            }
            if (ft != null && !matchesFilter(c, ft)) {
                continue;
            }
            result.add(c);
        }
        result.sort((a, b) -> {
            int cmp = switch (sort) {
                case "to" -> compareStr(a.to, b.to);
                default -> compareStr(a.from, b.from);
            };
            return sortReversed ? -cmp : cmp;
        });
        return result;
    }

    private static boolean isJdkType(String name) {
        if (name == null) {
            return false;
        }
        return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("org.w3c.")
                || name.startsWith("org.xml.")
                || !name.contains(".");
    }

    private static boolean isJdkOnly(ConverterData c) {
        return isJdkType(c.from) && isJdkType(c.to);
    }

    private static boolean isCoreConverter(ConverterData c) {
        return c.converterClass != null && c.converterClass.startsWith("org.apache.camel");
    }

    private static boolean matchesFilter(ConverterData c, String filter) {
        if (c.from != null && c.from.toLowerCase().contains(filter)) {
            return true;
        }
        return c.to != null && c.to.toLowerCase().contains(filter);
    }

    private static String displayName(String fqcn) {
        if (fqcn == null) {
            return "";
        }
        if (fqcn.startsWith("java.lang.")) {
            return fqcn.substring("java.lang.".length());
        }
        return fqcn;
    }

    private void loadConverters() {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        String pid = ctx.selectedPid;
        ctx.backgroundExecutor.execute(() -> {
            try {
                loadConvertersInBackground(pid);
            } finally {
                loading.set(false);
            }
        });
    }

    private void loadConvertersInBackground(String pid) {
        JsonObject root = new JsonObject();
        root.put("action", "type-converters");

        JsonObject jo = ctx.executeAction(pid, root, 5000);
        if (jo == null) {
            return;
        }

        JsonArray arr = jo.getCollection("converters");
        if (arr == null) {
            return;
        }

        List<ConverterData> result = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JsonObject cj = (JsonObject) arr.get(i);
            ConverterData cd = new ConverterData();
            cd.from = cj.getString("from");
            cd.to = cj.getString("to");
            cd.converterClass = cj.getString("converterClass");
            result.add(cd);
        }

        if (ctx.runner != null) {
            ctx.runner.runOnRenderThread(() -> {
                allConverters = result;
                lastPid = pid;
            });
        }
    }

    static class ConverterData {
        String from;
        String to;
        String converterClass;
    }

    @Override
    public String description() {
        return "Registered type converters in the Camel context";
    }

    @Override
    public String getHelpText() {
        return """
                # Type Converters

                The Type Converters tab shows all type converters registered in the
                Camel type converter registry. Type converters are used to automatically
                convert message bodies, headers, and other values between Java types
                during routing.

                Camel ships with ~230 built-in (core) type converters that handle
                common conversions like String to Integer, byte[] to InputStream,
                Document to String, etc. Components can also register additional
                converters for their own types.

                ## Table Columns

                - **FROM** — Source Java type
                - **TO** — Target Java type
                - **KIND** — `core` (shipped with Camel) or `custom` (added by components or user code)

                ## Scope Modes

                Press `f` to cycle through scope modes:

                - **all** — show all type converters
                - **non-jdk** — hide converters where both types are JDK classes (java.*, javax.*, org.w3c.*, org.xml.*)
                - **custom** — only converters added by custom components or user code
                - **core** — only Camel core converters (shipped with Apache Camel)

                ## Keys

                - `Up/Down` — select converter
                - `s` — cycle sort column
                - `S` — reverse sort order
                - `f` — cycle scope filter
                - `/` — text filter by class name
                - `Esc` — clear filter / back
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        List<ConverterData> converters = sortedConverters();
        if (converters.isEmpty()) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Type Converters");
        JsonArray rows = new JsonArray();
        for (ConverterData c : converters) {
            JsonObject row = new JsonObject();
            row.put("from", c.from);
            row.put("to", c.to);
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", converters.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
