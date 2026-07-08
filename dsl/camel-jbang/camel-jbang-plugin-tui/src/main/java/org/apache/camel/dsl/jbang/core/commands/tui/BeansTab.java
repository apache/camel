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
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class BeansTab extends AbstractTableTab {

    private final AtomicBoolean loading = new AtomicBoolean(false);

    private int filterIndex;
    private boolean filterInputActive;
    private TextInputState filterInputState = new TextInputState("");
    private String filterTerm;
    private List<BeanData> allBeans = Collections.emptyList();
    private int detailScroll;
    private String lastPid;

    BeansTab(MonitorContext ctx) {
        super(ctx, "name", "type");
    }

    @Override
    protected int getRowCount() {
        return sortedBeans().size();
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
        detailScroll = 0;
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
            detailScroll = 0;
            return true;
        }
        FormHelper.handleTextInput(ke, filterInputState);
        return true;
    }

    boolean isFilterInputActive() {
        return filterInputActive;
    }

    @Override
    public boolean handleEscape() {
        if (filterTerm != null) {
            filterTerm = null;
            tableState.select(0);
            detailScroll = 0;
            return true;
        }
        return false;
    }

    @Override
    protected boolean handleTabKeyEvent(KeyEvent ke) {
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            detailScroll = Math.max(0, detailScroll - 10);
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            detailScroll += 10;
            return true;
        }
        if (ke.isCharIgnoreCase('f')) {
            filterIndex = (filterIndex + 1) % filterModes().length;
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
        tableState.selectPrevious();
        detailScroll = 0;
    }

    @Override
    public void navigateDown() {
        List<BeanData> visible = sortedBeans();
        tableState.selectNext(visible.size());
        detailScroll = 0;
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        boolean handled = super.handleMouseEvent(me, area);
        if (handled) {
            detailScroll = 0;
        }
        return handled;
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        if (loading.get() && allBeans.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(" Loading beans...", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Beans ").build())
                            .build(),
                    area);
            return;
        }

        List<BeanData> visible = sortedBeans();

        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.percentage(30))
                .split(area);
        renderTable(frame, chunks.get(0), visible);
        renderDetail(frame, chunks.get(1), visible);
    }

    private void renderTable(Frame frame, Rect area, List<BeanData> visible) {
        List<Row> rows = new ArrayList<>();
        for (BeanData b : visible) {
            String type = b.type != null ? b.type : "";
            int dot = type.lastIndexOf('.');
            String shortType = dot >= 0 ? type.substring(dot + 1) : type;

            rows.add(Row.from(
                    Cell.from(Span.styled(b.name != null ? b.name : "", Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(Span.styled(shortType, Style.EMPTY))));
        }

        if (rows.isEmpty()) {
            rows.add(emptyRow("No beans", 2));
        }

        String mode = filterModes()[filterIndex];
        String title = filterTerm != null
                ? String.format(" Beans [%d] scope:%s filter:\"%s\" ", visible.size(), mode, filterTerm)
                : String.format(" Beans [%d] scope:%s ", visible.size(), mode);

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("NAME", "name"), sortStyle("name"))),
                        Cell.from(Span.styled(sortLabel("TYPE", "type"), sortStyle("type")))))
                .widths(
                        Constraint.percentage(55),
                        Constraint.fill())
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, visible.size());
    }

    private void renderDetail(Frame frame, Rect area, List<BeanData> visible) {
        Integer sel = tableState.selected();
        if (sel == null || sel < 0 || sel >= visible.size()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(" Select a bean", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Bean Detail ").build())
                            .build(),
                    area);
            return;
        }

        BeanData bean = visible.get(sel);
        String title = " " + (bean.name != null ? bean.name : "") + " ";

        List<Line> lines = new ArrayList<>();

        // full type
        if (bean.type != null && !bean.type.isEmpty()) {
            lines.add(Line.from(
                    Span.styled("  Type: ", Style.EMPTY.dim()),
                    Span.styled(bean.type, Style.EMPTY.fg(Color.WHITE))));
        }

        // properties
        if (bean.properties != null && !bean.properties.isEmpty()) {
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(Span.styled("  Properties:", Style.EMPTY.dim())));

            int maxNameLen = 0;
            for (BeanData.Property prop : bean.properties) {
                if (prop.name != null) {
                    maxNameLen = Math.max(maxNameLen, prop.name.length());
                }
            }
            int nameWidth = Math.max(15, maxNameLen + 1);

            for (BeanData.Property prop : bean.properties) {
                String propType = prop.type != null ? prop.type : "";
                int dot = propType.lastIndexOf('.');
                String shortPropType = dot >= 0 ? propType.substring(dot + 1) : propType;
                String value = prop.value != null ? prop.value : "null";

                lines.add(Line.from(
                        Span.styled("  " + String.format("%-" + nameWidth + "s", prop.name), Style.EMPTY.fg(Color.CYAN)),
                        Span.styled(String.format("%-15s", shortPropType), Style.EMPTY.dim()),
                        Span.styled(" = ", Style.EMPTY.dim()),
                        Span.styled(value, "null".equals(value) ? Style.EMPTY.dim() : Style.EMPTY.fg(Color.WHITE))));
            }
        } else {
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(Span.styled("  No properties", Style.EMPTY.dim())));
        }

        int visibleLines = area.height() - 2;
        if (visibleLines < 1) {
            visibleLines = 1;
        }
        int maxScroll = Math.max(0, lines.size() - visibleLines);
        detailScroll = Math.min(detailScroll, maxScroll);
        int end = Math.min(detailScroll + visibleLines, lines.size());
        List<Line> visibleContent = lines.subList(detailScroll, end);

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(visibleContent))
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                        .build(),
                area);
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
        hint(spans, "f", "scope [" + filterModes()[filterIndex] + "]");
        if (filterTerm != null) {
            spans.add(Span.styled("  /", Theme.label().bold()));
            spans.add(Span.raw("\"" + filterTerm + "\"  "));
        } else {
            hint(spans, "/", "filter");
        }
        hint(spans, TuiIcons.HINT_SCROLL, "navigate");
        hintLast(spans, "PgUp/Dn", "scroll");
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

    private String[] filterModes() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        String platform = info != null ? info.platform : null;
        if ("Spring Boot".equals(platform)) {
            return new String[] { "all", "user", "camel", "spring" };
        } else if ("Quarkus".equals(platform)) {
            return new String[] { "all", "user", "camel", "quarkus" };
        }
        return new String[] { "all", "user", "camel" };
    }

    private List<BeanData> sortedBeans() {
        String[] modes = filterModes();
        if (filterIndex >= modes.length) {
            filterIndex = 0;
        }
        String mode = modes[filterIndex];
        String ft = filterTerm != null ? filterTerm.toLowerCase() : null;
        List<BeanData> result = new ArrayList<>();
        for (BeanData b : allBeans) {
            if ("user".equals(mode) && isFrameworkBean(b)) {
                continue;
            }
            if ("camel".equals(mode) && !b.internal) {
                continue;
            }
            if ("spring".equals(mode) && !isSpringBean(b)) {
                continue;
            }
            if ("quarkus".equals(mode) && !isQuarkusBean(b)) {
                continue;
            }
            if (ft != null && !matchesFilter(b, ft)) {
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

    private static boolean matchesFilter(BeanData b, String filter) {
        if (b.name != null && b.name.toLowerCase().contains(filter)) {
            return true;
        }
        return b.type != null && b.type.toLowerCase().contains(filter);
    }

    private static boolean isFrameworkBean(BeanData b) {
        if (isInternalBean(b.name, b.type)) {
            return true;
        }
        if (b.name != null && (b.name.startsWith("org.springframework") || b.name.startsWith("io.quarkus"))) {
            return true;
        }
        if (b.type != null
                && (b.type.startsWith("org.springframework") || b.type.startsWith("io.quarkus")
                        || b.type.startsWith("java."))) {
            return true;
        }
        return false;
    }

    private static boolean isSpringBean(BeanData b) {
        if (b.name != null && b.name.startsWith("org.springframework")) {
            return true;
        }
        return b.type != null && b.type.startsWith("org.springframework");
    }

    private static boolean isQuarkusBean(BeanData b) {
        if (b.name != null && b.name.startsWith("io.quarkus")) {
            return true;
        }
        return b.type != null && b.type.startsWith("io.quarkus");
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
    public String description() {
        return "Registered beans in the Camel registry";
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
                - **TYPE** — Short Java class name of the bean (e.g., `Greeter`, `HikariDataSource`)

                ## Detail View

                The detail panel at the bottom shows the full type (including package)
                and the bean's properties with their current values. This is useful for
                verifying configuration — for example, checking that a database connection
                pool has the correct URL, or that a data format is configured with the
                right options.

                ## Filter Modes

                Press `f` to cycle through filter modes:

                - **all** — show all beans (including framework internals)
                - **user** — only your application beans (excludes Camel, Spring, Quarkus, and JDK beans)
                - **camel** — only Camel internal beans
                - **spring** — only Spring Framework beans (Spring Boot apps only)
                - **quarkus** — only Quarkus beans (Quarkus apps only)

                ## Keys

                - `Up/Down` — select bean
                - `PgUp/PgDn` — scroll detail panel
                - `s` — cycle sort column
                - `S` — reverse sort order
                - `f` — cycle filter
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
