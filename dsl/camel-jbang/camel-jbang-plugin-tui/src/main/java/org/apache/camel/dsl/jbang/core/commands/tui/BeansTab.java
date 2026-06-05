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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class BeansTab implements MonitorTab {

    private static final String[] SORT_COLUMNS = { "name", "type" };

    private final MonitorContext ctx;
    private final TableState tableState = new TableState();
    private final AtomicBoolean loading = new AtomicBoolean(false);

    private String sort = "name";
    private int sortIndex;
    private boolean sortReversed;
    private boolean showInternal;
    private List<BeanData> allBeans = Collections.emptyList();
    private boolean showDetail;
    private int detailScroll;
    private String lastPid;

    BeansTab(MonitorContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onTabSelected() {
        String pid = ctx.selectedPid;
        if (pid != null && !pid.equals(lastPid)) {
            lastPid = pid;
            allBeans = Collections.emptyList();
        }
        if (allBeans.isEmpty()) {
            loadBeans();
        }
    }

    @Override
    public void onIntegrationChanged() {
        allBeans = Collections.emptyList();
        showDetail = false;
        detailScroll = 0;
        lastPid = null;
        loadBeans();
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (showDetail) {
            if (ke.isUp()) {
                detailScroll = Math.max(0, detailScroll - 1);
                return true;
            }
            if (ke.isDown()) {
                detailScroll++;
                return true;
            }
            if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
                detailScroll = Math.max(0, detailScroll - 20);
                return true;
            }
            if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
                detailScroll += 20;
                return true;
            }
            return false;
        }

        if (ke.isConfirm()) {
            showDetail = !showDetail;
            detailScroll = 0;
            return true;
        }
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
        if (ke.isCharIgnoreCase('i')) {
            showInternal = !showInternal;
            return true;
        }
        if (ke.isCharIgnoreCase('r')) {
            loadBeans();
            return true;
        }
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            for (int i = 0; i < 20 && tableState.selected() != null && tableState.selected() > 0; i++) {
                tableState.selectPrevious();
            }
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            List<BeanData> visible = sortedBeans();
            for (int i = 0; i < 20; i++) {
                tableState.selectNext(visible.size());
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (showDetail) {
            showDetail = false;
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
        if (!showDetail) {
            tableState.selectPrevious();
        }
    }

    @Override
    public void navigateDown() {
        if (!showDetail) {
            List<BeanData> visible = sortedBeans();
            tableState.selectNext(visible.size());
        }
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        if (loading.get() && allBeans.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(" Loading beans...", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).title(" Beans ").build())
                            .build(),
                    area);
            return;
        }

        List<BeanData> visible = sortedBeans();

        if (showDetail) {
            List<Rect> chunks = Layout.vertical()
                    .constraints(Constraint.percentage(40), Constraint.fill())
                    .split(area);
            renderTable(frame, chunks.get(0), visible);
            renderDetail(frame, chunks.get(1), visible);
        } else {
            renderTable(frame, area, visible);
        }
    }

    private void renderTable(Frame frame, Rect area, List<BeanData> visible) {
        List<Row> rows = new ArrayList<>();
        for (BeanData b : visible) {
            String type = b.type != null ? b.type : "";
            int dot = type.lastIndexOf('.');
            String shortType = dot >= 0 ? type.substring(dot + 1) : type;

            rows.add(Row.from(
                    Cell.from(Span.styled(b.name != null ? b.name : "", Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(Span.styled(shortType, Style.EMPTY)),
                    Cell.from(Span.styled(type, Style.EMPTY.dim()))));
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(Span.styled("No beans", Style.EMPTY.dim())),
                    Cell.from(""), Cell.from("")));
        }

        String title = String.format(" Beans [%d] sort:%s ", visible.size(), sort);
        if (showInternal) {
            title = String.format(" Beans [%d] sort:%s internal:on ", visible.size(), sort);
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("NAME", "name"), sortStyle("name"))),
                        Cell.from(Span.styled(sortLabel("TYPE", "type"), sortStyle("type"))),
                        Cell.from(Span.styled("PACKAGE", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(30),
                        Constraint.length(30),
                        Constraint.fill())
                .highlightStyle(Style.EMPTY.fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).title(title).build())
                .build();

        frame.renderStatefulWidget(table, area, tableState);
    }

    private void renderDetail(Frame frame, Rect area, List<BeanData> visible) {
        Integer sel = tableState.selected();
        if (sel == null || sel < 0 || sel >= visible.size()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(" Select a bean", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).title(" Properties ").build())
                            .build(),
                    area);
            return;
        }

        BeanData bean = visible.get(sel);
        String title = " " + bean.name + " (" + (bean.type != null ? bean.type : "") + ") ";

        if (bean.properties == null || bean.properties.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(" No properties", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).title(title).build())
                            .build(),
                    area);
            return;
        }

        int visibleLines = area.height() - 2;
        if (visibleLines < 1) {
            visibleLines = 1;
        }
        int maxScroll = Math.max(0, bean.properties.size() - visibleLines);
        detailScroll = Math.min(detailScroll, maxScroll);

        int end = Math.min(detailScroll + visibleLines, bean.properties.size());
        List<Line> lines = new ArrayList<>();
        for (int i = detailScroll; i < end; i++) {
            BeanData.Property prop = bean.properties.get(i);
            String propType = prop.type != null ? prop.type : "";
            int dot = propType.lastIndexOf('.');
            String shortPropType = dot >= 0 ? propType.substring(dot + 1) : propType;
            String value = prop.value != null ? prop.value : "null";

            lines.add(Line.from(
                    Span.styled("  " + String.format("%-25s", prop.name), Style.EMPTY.fg(Color.CYAN)),
                    Span.styled(String.format("%-15s", shortPropType), Style.EMPTY.dim()),
                    Span.styled(" = ", Style.EMPTY.dim()),
                    Span.styled(value, "null".equals(value) ? Style.EMPTY.dim() : Style.EMPTY.fg(Color.WHITE))));
        }

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(lines))
                        .block(Block.builder().borderType(BorderType.ROUNDED).title(title).build())
                        .build(),
                area);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, "s", "sort");
        hint(spans, "i", "internal" + (showInternal ? " [on]" : ""));
        hint(spans, "r", "refresh");
        if (showDetail) {
            hintLast(spans, "↑↓", "scroll");
        } else {
            hintLast(spans, "Enter", "detail");
        }
    }

    @Override
    public SelectionContext getSelectionContext() {
        List<BeanData> visible = sortedBeans();
        if (visible.isEmpty()) {
            return null;
        }
        List<String> items = visible.stream().map(b -> b.name != null ? b.name : "").toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Beans");
    }

    private List<BeanData> sortedBeans() {
        List<BeanData> result = new ArrayList<>();
        for (BeanData b : allBeans) {
            if (!showInternal && b.internal) {
                continue;
            }
            result.add(b);
        }
        result.sort((a, b) -> {
            int cmp = switch (sort) {
                case "type" -> compareStr(a.type, b.type);
                default -> compareStr(a.name, b.name);
            };
            return sortReversed ? -cmp : cmp;
        });
        return result;
    }

    private static int compareStr(String a, String b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareToIgnoreCase(b);
    }

    private String sortLabel(String label, String column) {
        return MonitorContext.sortLabel(label, column, sort, sortReversed);
    }

    private Style sortStyle(String column) {
        return MonitorContext.sortStyle(column, sort);
    }

    private void loadBeans() {
        if (ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        String pid = ctx.selectedPid;
        ctx.runner.scheduler().execute(() -> {
            try {
                loadBeansInBackground(pid);
            } finally {
                loading.set(false);
            }
        });
    }

    private void loadBeansInBackground(String pid) {
        Path outputFile = ctx.getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "bean");
        root.put("properties", true);
        root.put("nulls", true);
        root.put("internal", true);

        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 5000);
        PathUtils.deleteFile(outputFile);

        if (jo == null) {
            return;
        }

        JsonObject beans = (JsonObject) jo.get("beans");
        if (beans == null) {
            return;
        }

        List<BeanData> result = new ArrayList<>();
        for (String name : beans.keySet()) {
            JsonObject bj = (JsonObject) beans.get(name);
            BeanData bd = new BeanData();
            bd.name = bj.getString("name");
            bd.type = bj.getString("type");
            bd.internal = isInternalBean(bd.name, bd.type);

            JsonArray props = bj.getCollection("properties");
            if (props != null && !props.isEmpty()) {
                bd.properties = new ArrayList<>();
                for (int i = 0; i < props.size(); i++) {
                    JsonObject pj = (JsonObject) props.get(i);
                    BeanData.Property prop = new BeanData.Property();
                    prop.name = pj.getString("name");
                    prop.type = pj.getString("type");
                    Object val = pj.get("value");
                    prop.value = val != null ? val.toString() : null;
                    bd.properties.add(prop);
                }
            }
            result.add(bd);
        }

        if (ctx.runner != null) {
            ctx.runner.runOnRenderThread(() -> {
                allBeans = result;
                lastPid = pid;
            });
        }
    }

    private static boolean isInternalBean(String name, String type) {
        if (name == null) {
            return false;
        }
        if (name.startsWith("camel-") || name.startsWith("org.apache.camel")) {
            return true;
        }
        if (type != null && type.startsWith("org.apache.camel")) {
            return true;
        }
        return false;
    }

    static class BeanData {
        String name;
        String type;
        boolean internal;
        List<Property> properties;

        static class Property {
            String name;
            String type;
            String value;
        }
    }

    @Override
    public String getHelpText() {
        return """
                # Beans

                The Beans tab shows all beans registered in the Camel registry. Beans
                are reusable Java objects that routes can reference by name — for
                example, a database connection pool, a custom processor, a REST
                configuration, or a type converter.

                In Camel, beans can be registered in several ways:

                - **YAML DSL** — defined in a `beans:` section of your YAML route file
                - **Java** — bound to the registry via `bindToRegistry()` or annotations
                - **Spring/Quarkus** — injected as managed beans (`@Component`, `@Named`)
                - **Camel auto-discovery** — components and languages auto-register their beans

                ## Table Columns

                - **NAME** — Bean name used to look it up from routes. In a route you reference this with `.bean("myService")` or `${bean:myService}`
                - **TYPE** — Java class of the bean (e.g., `com.example.MyService`, `java.util.HashMap`)

                ## Example Screen

                ```
                 NAME              TYPE
                 greeter           com.example.Greeter
                 myDataSource      com.zaxxer.hikari.HikariDataSource
                 restConfiguration org.apache.camel.spi.RestConfiguration
                 json-jackson      org.apache.camel.component.jackson.JacksonDataFormat
                ```

                ## Detail View

                Press `Enter` on a bean to inspect its properties and current values.
                Camel uses Java bean introspection to read property values, showing
                you the runtime state of each bean. This is useful for verifying
                configuration — for example, checking that a database connection pool
                has the correct URL, or that a data format is configured with the
                right options.

                ## Keys

                - `Up/Down` — select bean
                - `Enter` — view bean properties
                - `s` — cycle sort column
                - `S` — reverse sort order
                - `Esc` — back
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        List<BeanData> beans = sortedBeans();
        if (beans.isEmpty()) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Beans");
        JsonArray rows = new JsonArray();
        for (BeanData b : beans) {
            JsonObject row = new JsonObject();
            row.put("name", b.name);
            row.put("type", b.type);
            row.put("internal", b.internal);
            if (b.properties != null && !b.properties.isEmpty()) {
                JsonArray props = new JsonArray();
                for (BeanData.Property p : b.properties) {
                    JsonObject prop = new JsonObject();
                    prop.put("name", p.name);
                    prop.put("type", p.type);
                    if (p.value != null) {
                        prop.put("value", p.value);
                    }
                    props.add(prop);
                }
                row.put("properties", props);
            }
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", beans.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
