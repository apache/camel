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
import java.util.Map;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine.Command;

@Command(name = "catalog-tui",
         description = "Interactive TUI catalog browser",
         sortOptions = false)
public class CamelCatalogTui extends CamelCommand {

    private static final int FOCUS_LIST = 0;
    private static final int FOCUS_DETAILS = 1;

    // Catalog data
    private List<ComponentInfo> allComponents = Collections.emptyList();
    private List<ComponentInfo> filteredComponents = Collections.emptyList();

    // UI state
    private final TableState listTableState = new TableState();
    private final TableState optionsTableState = new TableState();
    private int focus = FOCUS_LIST;
    private boolean searching;
    private final StringBuilder searchText = new StringBuilder();
    private int detailScroll;

    // Selected component details
    private ComponentDetail selectedDetail;

    public CamelCatalogTui(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        try {
            Class.forName("dev.tamboui.tui.event.KeyModifiers");
            Class.forName("dev.tamboui.tui.event.KeyEvent");
            Class.forName("dev.tamboui.tui.event.KeyCode");
            Class.forName("picocli.CommandLine$IExitCodeGenerator");
        } catch (ClassNotFoundException e) {
            // ignore
        }

        loadCatalog();

        try (var tui = TuiRunner.create()) {
            sun.misc.Signal.handle(new sun.misc.Signal("INT"), sig -> tui.quit());
            tui.run(this::handleEvent, this::render);
        }
        return 0;
    }

    // ---- Catalog Loading ----

    @SuppressWarnings("unchecked")
    private void loadCatalog() {
        CamelCatalog catalog = new DefaultCamelCatalog();
        List<String> names = catalog.findComponentNames();
        Collections.sort(names);

        allComponents = new ArrayList<>();
        for (String name : names) {
            try {
                String json = catalog.componentJSonSchema(name);
                if (json == null) {
                    continue;
                }
                JsonObject root = (JsonObject) Jsoner.deserialize(json);
                JsonObject component = (JsonObject) root.get("component");
                if (component == null) {
                    continue;
                }

                ComponentInfo info = new ComponentInfo();
                info.name = name;
                info.title = component.getStringOrDefault("title", name);
                info.description = component.getStringOrDefault("description", "");
                info.label = component.getStringOrDefault("label", "");
                info.groupId = component.getStringOrDefault("groupId", "");
                info.artifactId = component.getStringOrDefault("artifactId", "");
                info.version = component.getStringOrDefault("version", "");
                info.scheme = component.getStringOrDefault("scheme", name);
                info.syntax = component.getStringOrDefault("syntax", "");
                info.deprecated = component.getBooleanOrDefault("deprecated", false);

                // Parse component properties
                JsonObject properties = (JsonObject) root.get("properties");
                if (properties != null) {
                    for (Map.Entry<String, Object> entry : properties.entrySet()) {
                        JsonObject prop = (JsonObject) entry.getValue();
                        OptionInfo opt = parseOption(entry.getKey(), prop);
                        opt.kind = "property";
                        info.componentOptions.add(opt);
                    }
                }

                // Parse endpoint properties (headers in newer catalogs, but "properties" is the standard)
                // The endpoint options are typically under "properties" for the endpoint
                // In the catalog JSON, component-level options have "kind":"property"
                // and endpoint options have "kind":"path" or "kind":"parameter"
                // So we re-scan and separate them
                info.componentOptions.clear();
                info.endpointOptions.clear();
                if (properties != null) {
                    for (Map.Entry<String, Object> entry : properties.entrySet()) {
                        JsonObject prop = (JsonObject) entry.getValue();
                        OptionInfo opt = parseOption(entry.getKey(), prop);
                        String kind = prop.getStringOrDefault("kind", "parameter");
                        opt.kind = kind;
                        if ("property".equals(kind)) {
                            info.componentOptions.add(opt);
                        } else {
                            info.endpointOptions.add(opt);
                        }
                    }
                }

                allComponents.add(info);
            } catch (Exception e) {
                // skip unparseable components
            }
        }

        applyFilter();
        if (!filteredComponents.isEmpty()) {
            listTableState.select(0);
            updateSelectedDetail();
        }
    }

    private OptionInfo parseOption(String name, JsonObject prop) {
        OptionInfo opt = new OptionInfo();
        opt.name = name;
        opt.type = prop.getStringOrDefault("type", "");
        opt.required = prop.getBooleanOrDefault("required", false);
        opt.defaultValue = prop.getStringOrDefault("defaultValue", "");
        opt.description = prop.getStringOrDefault("description", "");
        opt.group = prop.getStringOrDefault("group", "");
        opt.enumValues = "";
        Object enums = prop.get("enum");
        if (enums instanceof JsonArray arr) {
            List<String> vals = new ArrayList<>();
            for (Object v : arr) {
                vals.add(String.valueOf(v));
            }
            opt.enumValues = String.join(", ", vals);
        }
        return opt;
    }

    private void applyFilter() {
        String filter = searchText.toString().toLowerCase();
        if (filter.isEmpty()) {
            filteredComponents = new ArrayList<>(allComponents);
        } else {
            filteredComponents = new ArrayList<>();
            for (ComponentInfo c : allComponents) {
                if (c.name.toLowerCase().contains(filter)
                        || c.title.toLowerCase().contains(filter)
                        || c.description.toLowerCase().contains(filter)
                        || c.label.toLowerCase().contains(filter)) {
                    filteredComponents.add(c);
                }
            }
        }
        // Reset selection
        if (!filteredComponents.isEmpty()) {
            listTableState.select(0);
        } else {
            listTableState.clearSelection();
        }
        updateSelectedDetail();
    }

    private void updateSelectedDetail() {
        Integer sel = listTableState.selected();
        if (sel != null && sel >= 0 && sel < filteredComponents.size()) {
            ComponentInfo info = filteredComponents.get(sel);
            selectedDetail = new ComponentDetail(info);
            detailScroll = 0;
        } else {
            selectedDetail = null;
        }
    }

    // ---- Event Handling ----

    private boolean handleEvent(Event event, TuiRunner runner) {
        if (event instanceof KeyEvent ke) {
            // Search mode handling
            if (searching) {
                if (ke.isKey(KeyCode.ESCAPE)) {
                    if (searchText.isEmpty()) {
                        searching = false;
                    } else {
                        searchText.setLength(0);
                        applyFilter();
                    }
                    return true;
                }
                if (ke.isKey(KeyCode.ENTER)) {
                    searching = false;
                    return true;
                }
                if (ke.isKey(KeyCode.BACKSPACE)) {
                    if (!searchText.isEmpty()) {
                        searchText.deleteCharAt(searchText.length() - 1);
                        applyFilter();
                    }
                    return true;
                }
                if (ke.isUp()) {
                    listTableState.selectPrevious();
                    updateSelectedDetail();
                    return true;
                }
                if (ke.isDown()) {
                    listTableState.selectNext(filteredComponents.size());
                    updateSelectedDetail();
                    return true;
                }
                // Typed character
                if (ke.code() == KeyCode.CHAR) {
                    searchText.append(ke.character());
                    applyFilter();
                    return true;
                }
                return true;
            }

            // Normal mode
            if (ke.isQuit() || ke.isCharIgnoreCase('q') || ke.isKey(KeyCode.ESCAPE)) {
                runner.quit();
                return true;
            }

            if (ke.isChar('/')) {
                searching = true;
                return true;
            }

            if (ke.isKey(KeyCode.TAB)) {
                focus = (focus == FOCUS_LIST) ? FOCUS_DETAILS : FOCUS_LIST;
                return true;
            }

            if (ke.isUp()) {
                if (focus == FOCUS_LIST) {
                    listTableState.selectPrevious();
                    updateSelectedDetail();
                } else {
                    detailScroll = Math.max(0, detailScroll - 1);
                }
                return true;
            }
            if (ke.isDown()) {
                if (focus == FOCUS_LIST) {
                    listTableState.selectNext(filteredComponents.size());
                    updateSelectedDetail();
                } else {
                    detailScroll++;
                }
                return true;
            }
            if (ke.isKey(KeyCode.PAGE_UP)) {
                if (focus == FOCUS_DETAILS) {
                    detailScroll = Math.max(0, detailScroll - 20);
                }
                return true;
            }
            if (ke.isKey(KeyCode.PAGE_DOWN)) {
                if (focus == FOCUS_DETAILS) {
                    detailScroll += 20;
                }
                return true;
            }
            if (ke.isKey(KeyCode.ENTER)) {
                if (focus == FOCUS_LIST) {
                    updateSelectedDetail();
                    focus = FOCUS_DETAILS;
                }
                return true;
            }
        }
        return false;
    }

    // ---- Rendering ----

    private void render(Frame frame) {
        Rect area = frame.area();

        // Layout: header (3 rows) + content (fill) + footer (1 row)
        List<Rect> mainChunks = Layout.vertical()
                .constraints(
                        Constraint.length(3),
                        Constraint.fill(),
                        Constraint.length(1))
                .split(area);

        renderHeader(frame, mainChunks.get(0));
        renderContent(frame, mainChunks.get(1));
        renderFooter(frame, mainChunks.get(2));
    }

    private void renderHeader(Frame frame, Rect area) {
        String searchStatus;
        if (searching) {
            searchStatus = "  Search: " + searchText + "_";
        } else if (!searchText.isEmpty()) {
            searchStatus = "  Filter: \"" + searchText + "\"";
        } else {
            searchStatus = "";
        }

        Line titleLine = Line.from(
                Span.styled(" Camel Catalog", Style.create().fg(Color.rgb(0xF6, 0x91, 0x23)).bold()),
                Span.raw("  "),
                Span.styled(filteredComponents.size() + "/" + allComponents.size() + " components",
                        Style.create().fg(Color.CYAN)),
                Span.styled(searchStatus, Style.create().fg(Color.YELLOW)));

        Block headerBlock = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(" Apache Camel ")
                .build();

        frame.renderWidget(
                Paragraph.builder().text(Text.from(titleLine)).block(headerBlock).build(),
                area);
    }

    private void renderContent(Frame frame, Rect area) {
        // Split horizontally: left panel (30%) + right panel (70%)
        List<Rect> chunks = Layout.horizontal()
                .constraints(
                        Constraint.percentage(30),
                        Constraint.percentage(70))
                .split(area);

        renderComponentList(frame, chunks.get(0));
        renderComponentDetails(frame, chunks.get(1));
    }

    private void renderComponentList(Frame frame, Rect area) {
        List<Row> rows = new ArrayList<>();
        for (ComponentInfo comp : filteredComponents) {
            Style nameStyle = comp.deprecated
                    ? Style.create().fg(Color.RED).dim()
                    : Style.create().fg(Color.CYAN);
            String label = comp.name;
            if (comp.deprecated) {
                label = label + " (deprecated)";
            }
            rows.add(Row.from(Cell.from(Span.styled(label, nameStyle))));
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(Cell.from(Span.styled("No matching components", Style.create().dim()))));
        }

        Style borderStyle = focus == FOCUS_LIST
                ? Style.create().fg(Color.rgb(0xF6, 0x91, 0x23))
                : Style.create();

        Table table = Table.builder()
                .rows(rows)
                .widths(Constraint.fill())
                .highlightStyle(Style.create().fg(Color.WHITE).bold().onBlue())
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED)
                        .borderStyle(borderStyle)
                        .title(" Components ")
                        .build())
                .build();

        frame.renderStatefulWidget(table, area, listTableState);
    }

    private void renderComponentDetails(Frame frame, Rect area) {
        Style borderStyle = focus == FOCUS_DETAILS
                ? Style.create().fg(Color.rgb(0xF6, 0x91, 0x23))
                : Style.create();

        if (selectedDetail == null) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled(" Select a component from the list",
                                            Style.create().dim()))))
                            .block(Block.builder()
                                    .borderType(BorderType.ROUNDED)
                                    .borderStyle(borderStyle)
                                    .title(" Details ")
                                    .build())
                            .build(),
                    area);
            return;
        }

        ComponentInfo comp = selectedDetail.info;

        // Split: info area (top) + options table (bottom)
        List<Rect> chunks = Layout.vertical()
                .constraints(
                        Constraint.length(9),
                        Constraint.fill())
                .split(area);

        // Component info panel
        List<Line> infoLines = new ArrayList<>();
        infoLines.add(Line.from(
                Span.styled(" Title: ", Style.create().bold()),
                Span.styled(comp.title, Style.create().fg(Color.CYAN))));
        infoLines.add(Line.from(
                Span.styled(" Scheme: ", Style.create().bold()),
                Span.raw(comp.scheme)));
        infoLines.add(Line.from(
                Span.styled(" Syntax: ", Style.create().bold()),
                Span.raw(comp.syntax)));
        infoLines.add(Line.from(
                Span.styled(" Label: ", Style.create().bold()),
                Span.styled(comp.label, Style.create().fg(Color.GREEN))));
        infoLines.add(Line.from(
                Span.styled(" Maven: ", Style.create().bold()),
                Span.raw(comp.groupId + ":" + comp.artifactId + ":" + comp.version)));
        if (comp.deprecated) {
            infoLines.add(Line.from(
                    Span.styled(" Status: ", Style.create().bold()),
                    Span.styled("DEPRECATED", Style.create().fg(Color.RED).bold())));
        }
        infoLines.add(Line.from(Span.raw("")));
        // Word-wrap description into the available width
        int descWidth = Math.max(10, chunks.get(0).width() - 4);
        String desc = comp.description;
        if (desc.length() > descWidth) {
            infoLines.add(Line.from(
                    Span.styled(" " + desc.substring(0, descWidth), Style.create().dim())));
            if (desc.length() > descWidth * 2) {
                infoLines.add(Line.from(
                        Span.styled(" " + desc.substring(descWidth, descWidth * 2) + "...", Style.create().dim())));
            } else {
                infoLines.add(Line.from(
                        Span.styled(" " + desc.substring(descWidth), Style.create().dim())));
            }
        } else {
            infoLines.add(Line.from(Span.styled(" " + desc, Style.create().dim())));
        }

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(infoLines))
                        .overflow(Overflow.CLIP)
                        .block(Block.builder()
                                .borderType(BorderType.ROUNDED)
                                .borderStyle(borderStyle)
                                .title(" " + comp.title + " ")
                                .build())
                        .build(),
                chunks.get(0));

        // Options table
        renderOptionsTable(frame, chunks.get(1), borderStyle);
    }

    private void renderOptionsTable(Frame frame, Rect area, Style borderStyle) {
        if (selectedDetail == null) {
            return;
        }

        ComponentInfo comp = selectedDetail.info;

        // Combine all options with section headers
        List<Row> rows = new ArrayList<>();

        if (!comp.endpointOptions.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(Span.styled("--- Endpoint Options ---", Style.create().fg(Color.YELLOW).bold())),
                    Cell.from(""),
                    Cell.from(""),
                    Cell.from(""),
                    Cell.from("")));
            for (OptionInfo opt : comp.endpointOptions) {
                rows.add(optionToRow(opt));
            }
        }

        if (!comp.componentOptions.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(Span.styled("--- Component Options ---", Style.create().fg(Color.YELLOW).bold())),
                    Cell.from(""),
                    Cell.from(""),
                    Cell.from(""),
                    Cell.from("")));
            for (OptionInfo opt : comp.componentOptions) {
                rows.add(optionToRow(opt));
            }
        }

        if (rows.isEmpty()) {
            rows.add(Row.from(
                    Cell.from(Span.styled("No options", Style.create().dim())),
                    Cell.from(""),
                    Cell.from(""),
                    Cell.from(""),
                    Cell.from("")));
        }

        // Apply scrolling
        int innerHeight = Math.max(1, area.height() - 3); // border + header
        int maxScroll = Math.max(0, rows.size() - innerHeight);
        if (detailScroll > maxScroll) {
            detailScroll = maxScroll;
        }
        List<Row> visibleRows;
        if (detailScroll > 0 && detailScroll < rows.size()) {
            int end = Math.min(detailScroll + innerHeight, rows.size());
            visibleRows = rows.subList(detailScroll, end);
        } else {
            visibleRows = rows;
        }

        Row header = Row.from(
                Cell.from(Span.styled("NAME", Style.create().bold())),
                Cell.from(Span.styled("TYPE", Style.create().bold())),
                Cell.from(Span.styled("REQ", Style.create().bold())),
                Cell.from(Span.styled("DEFAULT", Style.create().bold())),
                Cell.from(Span.styled("DESCRIPTION", Style.create().bold())));

        String scrollInfo = rows.size() > innerHeight
                ? " [" + (detailScroll + 1) + "-" + Math.min(detailScroll + innerHeight, rows.size())
                  + "/" + rows.size() + "] "
                : " ";

        Table table = Table.builder()
                .rows(visibleRows)
                .header(header)
                .widths(
                        Constraint.length(25),
                        Constraint.length(12),
                        Constraint.length(4),
                        Constraint.length(12),
                        Constraint.fill())
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED)
                        .borderStyle(borderStyle)
                        .title(" Options" + scrollInfo)
                        .build())
                .build();

        frame.renderStatefulWidget(table, area, optionsTableState);
    }

    private Row optionToRow(OptionInfo opt) {
        Style nameStyle = opt.required
                ? Style.create().fg(Color.CYAN).bold()
                : Style.create().fg(Color.CYAN);

        String desc = opt.description;
        if (!opt.enumValues.isEmpty()) {
            desc = desc + " [" + opt.enumValues + "]";
        }

        return Row.from(
                Cell.from(Span.styled(opt.name, nameStyle)),
                Cell.from(Span.styled(opt.type, Style.create().dim())),
                Cell.from(opt.required
                        ? Span.styled("*", Style.create().fg(Color.RED).bold())
                        : Span.raw("")),
                Cell.from(Span.styled(opt.defaultValue, Style.create().dim())),
                Cell.from(truncate(desc, 80)));
    }

    private void renderFooter(Frame frame, Rect area) {
        Line footer;
        if (searching) {
            footer = Line.from(
                    Span.styled(" Type", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" to filter  "),
                    Span.styled("Enter", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" confirm  "),
                    Span.styled("Esc", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" clear/cancel  "),
                    Span.styled("\u2191\u2193", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" navigate"));
        } else {
            footer = Line.from(
                    Span.styled(" q", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" quit  "),
                    Span.styled("/", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" search  "),
                    Span.styled("\u2191\u2193", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" navigate  "),
                    Span.styled("Tab", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" switch panel  "),
                    Span.styled("Enter", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" select  "),
                    Span.styled("PgUp/PgDn", Style.create().fg(Color.YELLOW).bold()),
                    Span.raw(" scroll details"));
        }

        frame.renderWidget(Paragraph.from(footer), area);
    }

    // ---- Helpers ----

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max - 1) + "\u2026" : s;
    }

    // ---- Data Classes ----

    static class ComponentInfo {
        String name;
        String title;
        String description;
        String label;
        String groupId;
        String artifactId;
        String version;
        String scheme;
        String syntax;
        boolean deprecated;
        final List<OptionInfo> componentOptions = new ArrayList<>();
        final List<OptionInfo> endpointOptions = new ArrayList<>();
    }

    static class OptionInfo {
        String name;
        String kind;
        String type;
        boolean required;
        String defaultValue;
        String description;
        String group;
        String enumValues;
    }

    static class ComponentDetail {
        final ComponentInfo info;

        ComponentDetail(ComponentInfo info) {
            this.info = info;
        }
    }
}
