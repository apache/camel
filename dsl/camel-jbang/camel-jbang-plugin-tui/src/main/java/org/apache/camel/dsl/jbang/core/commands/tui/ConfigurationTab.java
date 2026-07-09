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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
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
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.MainModel;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class ConfigurationTab extends AbstractTableTab {

    private static final Style SECRET_STYLE = Theme.muted();

    private int detailScroll;

    private CamelCatalog catalog;
    private Map<String, BaseOptionModel> mainOptionsMap;
    private final Map<String, Map<String, BaseOptionModel>> componentOptionsCache = new HashMap<>();

    ConfigurationTab(MonitorContext ctx) {
        super(ctx, "key", "value", "source");
    }

    @Override
    protected int getRowCount() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        return info != null ? info.configProperties.size() : 0;
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
        return false;
    }

    @Override
    public void navigateUp() {
        tableState.selectPrevious();
        detailScroll = 0;
    }

    @Override
    public void navigateDown() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        int count = info != null ? info.configProperties.size() : 0;
        tableState.selectNext(count);
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
        if (info.configProperties.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled("  No configuration properties available.", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Configuration ").build())
                            .build(),
                    area);
            return;
        }

        List<ConfigProperty> props = sortedProperties(info);

        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.percentage(30))
                .split(area);
        renderTable(frame, chunks.get(0), props);
        renderDetail(frame, chunks.get(1), props);
    }

    private List<ConfigProperty> sortedProperties(IntegrationInfo info) {
        List<ConfigProperty> result = new ArrayList<>(info.configProperties);
        result.sort((a, b) -> {
            int cmp = switch (sort) {
                case "value" -> compareStr(a.value, b.value);
                case "source" -> compareStr(a.source, b.source);
                default -> compareCamelFirst(a, b);
            };
            return sortReversed ? -cmp : cmp;
        });
        return result;
    }

    private void renderTable(Frame frame, Rect area, List<ConfigProperty> props) {
        List<Row> rows = new ArrayList<>();
        for (ConfigProperty p : props) {
            boolean secret = "xxxxxx".equals(p.value);
            String value = p.value != null ? p.value : "";
            Style valStyle = secret ? SECRET_STYLE : Style.EMPTY;

            String source = "";
            if (p.source != null && !p.source.isEmpty()) {
                source = FileUtil.stripPath(p.source);
            }

            rows.add(Row.from(
                    Cell.from(Span.styled(p.key, Style.EMPTY.fg(Theme.accent()))),
                    Cell.from(Span.styled(value, valStyle)),
                    Cell.from(Span.styled(source, Style.EMPTY.dim()))));
        }

        if (rows.isEmpty()) {
            rows.add(emptyRow("No properties", 3));
        }

        String title = String.format(" Configuration [%d] ", props.size());

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("KEY", "key"), sortStyle("key"))),
                        Cell.from(Span.styled(sortLabel("VALUE", "value"), sortStyle("value"))),
                        Cell.from(Span.styled(sortLabel("SOURCE", "source"), sortStyle("source")))))
                .widths(
                        Constraint.percentage(35),
                        Constraint.fill(),
                        Constraint.length(20))
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, props.size());
    }

    private void renderDetail(Frame frame, Rect area, List<ConfigProperty> props) {
        Integer sel = tableState.selected();
        if (sel == null || sel < 0 || sel >= props.size()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(" Select a property", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Property Detail ").build())
                            .build(),
                    area);
            return;
        }

        ConfigProperty prop = props.get(sel);
        BaseOptionModel option = lookupPropertyDoc(prop.key);
        String title = " " + prop.key + " ";

        List<Line> lines = new ArrayList<>();

        if (option != null) {
            if (option.getDescription() != null && !option.getDescription().isEmpty()) {
                addDetailField(lines, "Description", option.getDescription(), area.width());
            }
            if (option.getType() != null) {
                addDetailField(lines, "Type", option.getType(), area.width());
            }
            if (option.getDefaultValue() != null) {
                addDetailField(lines, "Default", option.getDefaultValue().toString(), area.width());
            }
            if (option.getEnums() != null && !option.getEnums().isEmpty()) {
                addDetailField(lines, "Enum values", String.join(", ", option.getEnums()), area.width());
            }
            if (option.isRequired()) {
                addDetailField(lines, "Required", "true", area.width());
            }
            if (option.isDeprecated()) {
                String depText = "true";
                if (option.getDeprecationNote() != null && !option.getDeprecationNote().isEmpty()) {
                    depText += " — " + option.getDeprecationNote();
                }
                addDetailField(lines, "Deprecated", depText, area.width());
            }
            if (option.isSecret()) {
                addDetailField(lines, "Secret", "true", area.width());
            }
            if (option.getGroup() != null && !option.getGroup().isEmpty()) {
                addDetailField(lines, "Group", option.getGroup(), area.width());
            }
            lines.add(Line.from(Span.raw("")));
        }

        // current value and source
        String value = prop.value != null ? prop.value : "";
        boolean secret = "xxxxxx".equals(value);
        addDetailField(lines, "Current value", secret ? "xxxxxx (masked)" : value, area.width());
        if (prop.source != null && !prop.source.isEmpty()) {
            addDetailField(lines, "Source", FileUtil.stripPath(prop.source), area.width());
        }

        if (option == null) {
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(Span.styled("  No documentation available for this property.", Style.EMPTY.dim())));
        }

        int visibleLines = area.height() - 2;
        if (visibleLines < 1) {
            visibleLines = 1;
        }
        int maxScroll = Math.max(0, lines.size() - visibleLines);
        detailScroll = Math.min(detailScroll, maxScroll);
        int end = Math.min(detailScroll + visibleLines, lines.size());
        List<Line> visible = lines.subList(detailScroll, end);

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(visible))
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                .title(title).build())
                        .build(),
                area);
    }

    private void addDetailField(List<Line> lines, String label, String value, int width) {
        String prefix = "  " + label + ": ";
        int maxValueLen = width - prefix.length() - 2;
        if (maxValueLen <= 0) {
            maxValueLen = 40;
        }

        if (value.length() <= maxValueLen) {
            lines.add(Line.from(
                    Span.styled(prefix, Style.EMPTY.dim()),
                    Span.styled(value, Style.EMPTY.fg(Theme.baseFg()))));
        } else {
            // wrap long values
            lines.add(Line.from(Span.styled(prefix, Style.EMPTY.dim())));
            int indent = 6;
            String indentStr = " ".repeat(indent);
            int wrapWidth = width - indent - 2;
            if (wrapWidth <= 0) {
                wrapWidth = 40;
            }
            int pos = 0;
            while (pos < value.length()) {
                int end = Math.min(pos + wrapWidth, value.length());
                lines.add(Line.from(Span.styled(indentStr + value.substring(pos, end), Style.EMPTY.fg(Theme.baseFg()))));
                pos = end;
            }
        }
    }

    private BaseOptionModel lookupPropertyDoc(String key) {
        if (key == null) {
            return null;
        }
        initCatalog();
        if (mainOptionsMap != null) {
            BaseOptionModel option = mainOptionsMap.get(key);
            if (option != null) {
                return option;
            }
        }
        // try camel.component.<name>.<option>
        if (key.startsWith("camel.component.")) {
            String rest = key.substring("camel.component.".length());
            int dot = rest.indexOf('.');
            if (dot > 0) {
                String componentName = rest.substring(0, dot);
                String optionName = rest.substring(dot + 1);
                Map<String, BaseOptionModel> compOptions = getComponentOptions(componentName);
                if (compOptions != null) {
                    return compOptions.get(optionName);
                }
            }
        }
        return null;
    }

    private void initCatalog() {
        if (catalog != null) {
            return;
        }
        catalog = new DefaultCamelCatalog();
        mainOptionsMap = new HashMap<>();
        MainModel mainModel = catalog.mainModel();
        if (mainModel != null) {
            for (MainModel.MainOptionModel opt : mainModel.getOptions()) {
                if (opt.getName() != null) {
                    mainOptionsMap.put(opt.getName(), opt);
                }
            }
        }
    }

    private Map<String, BaseOptionModel> getComponentOptions(String componentName) {
        return componentOptionsCache.computeIfAbsent(componentName, name -> {
            initCatalog();
            ComponentModel model = catalog.componentModel(name);
            if (model == null) {
                return null;
            }
            Map<String, BaseOptionModel> map = new HashMap<>();
            for (ComponentModel.ComponentOptionModel opt : model.getComponentOptions()) {
                if (opt.getName() != null) {
                    map.put(opt.getName(), opt);
                }
            }
            return map.isEmpty() ? null : map;
        });
    }

    @Override
    public void renderFooter(List<Span> spans) {
        super.renderFooter(spans);
        hint(spans, TuiIcons.HINT_SCROLL, "navigate");
        hintLast(spans, "PgUp/Dn", "scroll");
    }

    @Override
    public SelectionContext getSelectionContext() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.configProperties.isEmpty()) {
            return null;
        }
        List<String> items = info.configProperties.stream().map(p -> p.key).toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Configuration");
    }

    static int compareCamelFirst(ConfigProperty a, ConfigProperty b) {
        boolean aCamel = a.key.startsWith("camel.");
        boolean bCamel = b.key.startsWith("camel.");
        if (aCamel != bCamel) {
            return aCamel ? -1 : 1;
        }
        return a.key.compareToIgnoreCase(b.key);
    }

    static class ConfigProperty {
        String key;
        String value;
        String defaultValue;
        String source;
        String location;
    }

    @Override
    public String description() {
        return "Application configuration properties";
    }

    @Override
    public String getHelpText() {
        return """
                # Configuration

                The Configuration tab shows all configuration properties of the running
                integration. This provides a complete view of how the integration is
                configured at runtime — including Camel settings, component options,
                and application properties.

                Properties can come from multiple sources and Camel merges them with
                a defined priority order.

                ## Table Columns

                - **KEY** — Property name following Camel's naming convention (e.g., `camel.main.name`, `camel.component.kafka.brokers`, `greeting.message`)
                - **VALUE** — Current resolved property value. Sensitive values (passwords, tokens) are masked as `xxxxxx` for security
                - **SOURCE** — Where the property was set:
                  - `application.properties` — from the main properties file
                  - `ENV` — from an environment variable
                  - `JVM` — from a Java system property (`-D`)
                  - `Spring Boot` — from Spring Boot configuration
                  - `camel-component` — default from a Camel component
                  - `override` — set programmatically in code
                  - `initial` — set during context initialization

                ## Detail View

                Press `Enter` on a property to see its documentation from the Camel
                catalog. For `camel.main.*` and `camel.component.*` properties, the
                detail panel shows:

                - **Description** — what the property does
                - **Type** — expected value type (string, boolean, integer, etc.)
                - **Default** — default value if not explicitly set
                - **Enum values** — allowed values for enumerated properties
                - **Required** / **Deprecated** / **Secret** flags
                - **Group** — the configuration group this property belongs to

                ## Keys

                - `Up/Down` — select property
                - `Enter` — view property detail / documentation
                - `s` — cycle sort column
                - `S` — reverse sort order
                - `Esc` — close detail / back
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Configuration");
        JsonArray rows = new JsonArray();
        for (ConfigProperty cp : info.configProperties) {
            JsonObject row = new JsonObject();
            row.put("key", cp.key);
            row.put("value", cp.value);
            if (cp.defaultValue != null) {
                row.put("defaultValue", cp.defaultValue);
            }
            if (cp.source != null) {
                row.put("source", cp.source);
            }
            if (cp.location != null) {
                row.put("location", cp.location);
            }
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", info.configProperties.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
