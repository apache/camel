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

@Command(name = "catalog",
         description = "Interactive TUI catalog browser",
         sortOptions = false)
public class CamelCatalogTui extends CamelCommand {

    private static final int FOCUS_LIST = 0;
    private static final int FOCUS_OPTIONS = 1;

    // Catalog data
    private List<ComponentInfo> allComponents = Collections.emptyList();
    private List<ComponentInfo> filteredComponents = Collections.emptyList();

    // UI state
    private final TableState listTableState = new TableState();
    private final TableState optionsTableState = new TableState();
    private int focus = FOCUS_LIST;
    private final StringBuilder componentFilter = new StringBuilder();
    private final StringBuilder optionFilter = new StringBuilder();
    private boolean componentFullText;
    private boolean optionFullText;
    private int descriptionScroll;

    // All options for selected component (unfiltered)
    private List<OptionInfo> allOptionsUnfiltered = Collections.emptyList();
    // Filtered options displayed in table
    private List<OptionInfo> filteredOptions = Collections.emptyList();

    private ClassLoader classLoader;

    public CamelCatalogTui(CamelJBangMain main, ClassLoader classLoader) {
        super(main);
        this.classLoader = classLoader;
    }

    @Override
    public Integer doCall() throws Exception {
        // to make ServiceLoader work with tamboui for downloaded JARs
        Thread.currentThread().setContextClassLoader(classLoader);
        TuiHelper.preloadClasses(classLoader);

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
        List<String> names = new ArrayList<>(catalog.findComponentNames());
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

                // Parse properties — separate by kind
                JsonObject properties = (JsonObject) root.get("properties");
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

        applyComponentFilter();
        if (!filteredComponents.isEmpty()) {
            listTableState.select(0);
            updateSelectedComponent();
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

    private void applyComponentFilter() {
        String filter = componentFilter.toString().toLowerCase();
        if (filter.isEmpty()) {
            filteredComponents = new ArrayList<>(allComponents);
        } else {
            filteredComponents = new ArrayList<>();
            for (ComponentInfo c : allComponents) {
                if (componentFullText) {
                    if (c.name.toLowerCase().contains(filter)
                            || c.title.toLowerCase().contains(filter)
                            || c.description.toLowerCase().contains(filter)
                            || c.label.toLowerCase().contains(filter)) {
                        filteredComponents.add(c);
                    }
                } else {
                    if (c.name.toLowerCase().contains(filter)) {
                        filteredComponents.add(c);
                    }
                }
            }
        }
        if (!filteredComponents.isEmpty()) {
            listTableState.select(0);
        } else {
            listTableState.clearSelection();
        }
        updateSelectedComponent();
    }

    private void applyOptionFilter() {
        String filter = optionFilter.toString().toLowerCase();
        if (filter.isEmpty()) {
            filteredOptions = new ArrayList<>(allOptionsUnfiltered);
        } else {
            filteredOptions = new ArrayList<>();
            for (OptionInfo o : allOptionsUnfiltered) {
                if (optionFullText) {
                    if (o.name.toLowerCase().contains(filter)
                            || o.description.toLowerCase().contains(filter)
                            || o.group.toLowerCase().contains(filter)) {
                        filteredOptions.add(o);
                    }
                } else {
                    if (o.name.toLowerCase().contains(filter)) {
                        filteredOptions.add(o);
                    }
                }
            }
        }
        if (!filteredOptions.isEmpty()) {
            optionsTableState.select(0);
        } else {
            optionsTableState.clearSelection();
        }
        descriptionScroll = 0;
    }

    private void updateSelectedComponent() {
        Integer sel = listTableState.selected();
        if (sel != null && sel >= 0 && sel < filteredComponents.size()) {
            ComponentInfo info = filteredComponents.get(sel);
            allOptionsUnfiltered = new ArrayList<>();
            allOptionsUnfiltered.addAll(info.endpointOptions);
            allOptionsUnfiltered.addAll(info.componentOptions);
        } else {
            allOptionsUnfiltered = Collections.emptyList();
        }
        optionFilter.setLength(0);
        optionFullText = false;
        applyOptionFilter();
        descriptionScroll = 0;
    }

    // ---- Event Handling ----

    private boolean handleEvent(Event event, TuiRunner runner) {
        if (event instanceof KeyEvent ke) {
            // Quit
            if (ke.isQuit()) {
                runner.quit();
                return true;
            }

            // Escape: clear filter first, then go back, then quit
            if (ke.isKey(KeyCode.ESCAPE)) {
                if (focus == FOCUS_OPTIONS && (!optionFilter.isEmpty() || optionFullText)) {
                    optionFilter.setLength(0);
                    optionFullText = false;
                    applyOptionFilter();
                    return true;
                }
                if (focus == FOCUS_OPTIONS) {
                    focus = FOCUS_LIST;
                    descriptionScroll = 0;
                    return true;
                }
                if (!componentFilter.isEmpty() || componentFullText) {
                    componentFilter.setLength(0);
                    componentFullText = false;
                    applyComponentFilter();
                    return true;
                }
                runner.quit();
                return true;
            }

            // Backspace: delete from active filter
            if (ke.isKey(KeyCode.BACKSPACE)) {
                if (focus == FOCUS_LIST && !componentFilter.isEmpty()) {
                    componentFilter.deleteCharAt(componentFilter.length() - 1);
                    applyComponentFilter();
                } else if (focus == FOCUS_OPTIONS && !optionFilter.isEmpty()) {
                    optionFilter.deleteCharAt(optionFilter.length() - 1);
                    applyOptionFilter();
                }
                return true;
            }

            // Panel switching — only when no active filter on current panel
            if (ke.isKey(KeyCode.TAB)) {
                if (focus == FOCUS_LIST) {
                    focus = FOCUS_OPTIONS;
                } else {
                    focus = FOCUS_LIST;
                }
                descriptionScroll = 0;
                return true;
            }
            if (ke.isKey(KeyCode.RIGHT) && focus == FOCUS_LIST) {
                focus = FOCUS_OPTIONS;
                descriptionScroll = 0;
                return true;
            }
            if (ke.isKey(KeyCode.LEFT) && focus == FOCUS_OPTIONS) {
                focus = FOCUS_LIST;
                descriptionScroll = 0;
                return true;
            }

            // Enter drills into options
            if (ke.isKey(KeyCode.ENTER) && focus == FOCUS_LIST) {
                focus = FOCUS_OPTIONS;
                descriptionScroll = 0;
                return true;
            }

            // Navigation
            if (ke.isUp()) {
                if (focus == FOCUS_LIST) {
                    listTableState.selectPrevious();
                    updateSelectedComponent();
                } else {
                    optionsTableState.selectPrevious();
                    descriptionScroll = 0;
                }
                return true;
            }
            if (ke.isDown()) {
                if (focus == FOCUS_LIST) {
                    listTableState.selectNext(filteredComponents.size());
                    updateSelectedComponent();
                } else {
                    optionsTableState.selectNext(filteredOptions.size());
                    descriptionScroll = 0;
                }
                return true;
            }
            if (ke.isKey(KeyCode.PAGE_UP)) {
                descriptionScroll = Math.max(0, descriptionScroll - 5);
                return true;
            }
            if (ke.isKey(KeyCode.PAGE_DOWN)) {
                descriptionScroll += 5;
                return true;
            }

            // '/' toggles full-text search mode
            if (ke.isChar('/')) {
                if (focus == FOCUS_LIST) {
                    componentFullText = !componentFullText;
                    applyComponentFilter();
                } else {
                    optionFullText = !optionFullText;
                    applyOptionFilter();
                }
                return true;
            }

            // Typing filters the active panel
            if (ke.code() == KeyCode.CHAR) {
                if (focus == FOCUS_LIST) {
                    componentFilter.append(ke.character());
                    applyComponentFilter();
                } else {
                    optionFilter.append(ke.character());
                    applyOptionFilter();
                }
                return true;
            }
        }
        return false;
    }

    // ---- Rendering ----

    private void render(Frame frame) {
        Rect area = frame.area();

        // Layout: header (3) + top panels (fill) + separator (1) + description (40%) + footer (1)
        List<Rect> mainChunks = Layout.vertical()
                .constraints(
                        Constraint.length(3),
                        Constraint.percentage(50),
                        Constraint.length(1),
                        Constraint.fill(),
                        Constraint.length(1))
                .split(area);

        renderHeader(frame, mainChunks.get(0));
        renderTopPanels(frame, mainChunks.get(1));
        renderSeparator(frame, mainChunks.get(2));
        renderDescription(frame, mainChunks.get(3));
        renderFooter(frame, mainChunks.get(4));
    }

    private void renderHeader(Frame frame, Rect area) {
        Line titleLine = Line.from(
                Span.styled(" Camel Catalog", Style.create().fg(Color.rgb(0xF6, 0x91, 0x23)).bold()),
                Span.raw("  "),
                Span.styled(filteredComponents.size() + "/" + allComponents.size() + " components",
                        Style.create().fg(Color.CYAN)));

        Block headerBlock = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(" Apache Camel ")
                .build();

        frame.renderWidget(
                Paragraph.builder().text(Text.from(titleLine)).block(headerBlock).build(),
                area);
    }

    private void renderTopPanels(Frame frame, Rect area) {
        // Split horizontally: component list (30%) + options table (70%)
        List<Rect> chunks = Layout.horizontal()
                .constraints(Constraint.percentage(30), Constraint.percentage(70))
                .split(area);

        renderComponentList(frame, chunks.get(0));
        renderOptionsTable(frame, chunks.get(1));
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

        String modePrefix = componentFullText ? "/" : "";
        String listTitle = componentFilter.isEmpty() && !componentFullText
                ? " Components "
                : " Components [" + modePrefix + componentFilter + "] ";

        Table table = Table.builder()
                .rows(rows)
                .widths(Constraint.fill())
                .highlightStyle(Style.create().fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED)
                        .borderStyle(borderStyle)
                        .title(listTitle)
                        .build())
                .build();

        frame.renderStatefulWidget(table, area, listTableState);
    }

    private void renderOptionsTable(Frame frame, Rect area) {
        Style borderStyle = focus == FOCUS_OPTIONS
                ? Style.create().fg(Color.rgb(0xF6, 0x91, 0x23))
                : Style.create();

        String optModePrefix = optionFullText ? "/" : "";
        String optTitle = optionFilter.isEmpty() && !optionFullText
                ? " Options "
                : " Options [" + optModePrefix + optionFilter + "] ";

        if (filteredOptions.isEmpty()) {
            String emptyMsg = allOptionsUnfiltered.isEmpty() ? " Select a component" : " No matching options";
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled(emptyMsg, Style.create().dim()))))
                            .block(Block.builder()
                                    .borderType(BorderType.ROUNDED)
                                    .borderStyle(borderStyle)
                                    .title(optTitle)
                                    .build())
                            .build(),
                    area);
            return;
        }

        List<Row> rows = new ArrayList<>();
        for (OptionInfo opt : filteredOptions) {
            rows.add(optionToRow(opt));
        }

        Row header = Row.from(
                Cell.from(Span.styled("NAME", Style.create().bold())),
                Cell.from(Span.styled("TYPE", Style.create().bold())),
                Cell.from(Span.styled("REQ", Style.create().bold())),
                Cell.from(Span.styled("DEFAULT", Style.create().bold())),
                Cell.from(Span.styled("KIND", Style.create().bold())));

        Table table = Table.builder()
                .rows(rows)
                .header(header)
                .widths(
                        Constraint.length(25),
                        Constraint.length(12),
                        Constraint.length(4),
                        Constraint.length(12),
                        Constraint.fill())
                .highlightStyle(Style.create().fg(Color.WHITE).bold().onBlue())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED)
                        .borderStyle(borderStyle)
                        .title(optTitle)
                        .build())
                .build();

        frame.renderStatefulWidget(table, area, optionsTableState);
    }

    private void renderSeparator(Frame frame, Rect area) {
        String line = "\u2500".repeat(Math.max(0, area.width()));
        frame.renderWidget(
                Paragraph.from(Line.from(Span.styled(line, Style.create().fg(Color.DARK_GRAY)))),
                area);
    }

    private void renderDescription(Frame frame, Rect area) {
        List<Line> lines = new ArrayList<>();
        String title;
        int wrapWidth = Math.max(20, area.width() - 4);

        if (focus == FOCUS_OPTIONS) {
            // Show selected option detail
            Integer sel = optionsTableState.selected();
            if (sel != null && sel >= 0 && sel < filteredOptions.size()) {
                OptionInfo opt = filteredOptions.get(sel);
                title = " " + opt.name + " ";

                List<String[]> fields = new ArrayList<>();
                fields.add(new String[] { "Name", opt.name });
                if (opt.kind != null && !opt.kind.isEmpty()) {
                    fields.add(new String[] { "Kind", opt.kind });
                }
                fields.add(new String[] { "Type", opt.type });
                fields.add(new String[] { "Required", opt.required ? "Yes" : "No" });
                if (!opt.defaultValue.isEmpty()) {
                    fields.add(new String[] { "Default", opt.defaultValue });
                }
                if (opt.group != null && !opt.group.isEmpty()) {
                    fields.add(new String[] { "Group", opt.group });
                }
                if (opt.enumValues != null && !opt.enumValues.isEmpty()) {
                    fields.add(new String[] { "Values", opt.enumValues });
                }
                flowFields(lines, fields, wrapWidth, opt);

                lines.add(Line.from(Span.raw("")));
                wrapText(lines, opt.description, wrapWidth, Style.create());
            } else {
                title = " Description ";
            }
        } else {
            // Show selected component detail
            Integer sel = listTableState.selected();
            if (sel != null && sel >= 0 && sel < filteredComponents.size()) {
                ComponentInfo comp = filteredComponents.get(sel);
                title = " " + comp.title + " ";

                List<String[]> fields = new ArrayList<>();
                fields.add(new String[] { "Scheme", comp.scheme });
                fields.add(new String[] { "Syntax", comp.syntax });
                fields.add(new String[] { "Label", comp.label });
                fields.add(new String[] { "Maven", comp.groupId + ":" + comp.artifactId + ":" + comp.version });
                fields.add(new String[] {
                        "Options",
                        comp.endpointOptions.size() + " endpoint, " + comp.componentOptions.size() + " component" });
                if (comp.deprecated) {
                    fields.add(new String[] { "Status", "DEPRECATED" });
                }
                flowFields(lines, fields, wrapWidth, null);

                lines.add(Line.from(Span.raw("")));
                wrapText(lines, comp.description, wrapWidth, Style.create());
            } else {
                title = " Description ";
            }
        }

        // Apply scroll
        int innerHeight = Math.max(1, area.height() - 2);
        int maxScroll = Math.max(0, lines.size() - innerHeight);
        if (descriptionScroll > maxScroll) {
            descriptionScroll = maxScroll;
        }
        List<Line> visible;
        if (descriptionScroll > 0) {
            int end = Math.min(descriptionScroll + innerHeight, lines.size());
            visible = lines.subList(descriptionScroll, end);
        } else if (lines.size() > innerHeight) {
            visible = lines.subList(0, innerHeight);
        } else {
            visible = lines;
        }

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(visible))
                        .overflow(Overflow.CLIP)
                        .block(Block.builder()
                                .borderType(BorderType.ROUNDED)
                                .title(title)
                                .build())
                        .build(),
                area);
    }

    private Row optionToRow(OptionInfo opt) {
        Style nameStyle = opt.required
                ? Style.create().fg(Color.CYAN).bold()
                : Style.create().fg(Color.CYAN);

        return Row.from(
                Cell.from(Span.styled(opt.name, nameStyle)),
                Cell.from(Span.styled(opt.type, Style.create().dim())),
                Cell.from(opt.required
                        ? Span.styled("*", Style.create().fg(Color.RED).bold())
                        : Span.raw("")),
                Cell.from(Span.styled(opt.defaultValue, Style.create().dim())),
                Cell.from(Span.styled(opt.kind != null ? opt.kind : "", Style.create().dim())));
    }

    private void renderFooter(Frame frame, Rect area) {
        Line footer = Line.from(
                Span.styled(" Type", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" name filter  "),
                Span.styled("/", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" full-text  "),
                Span.styled("Esc", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" clear/back/quit  "),
                Span.styled("\u2191\u2193", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" navigate  "),
                Span.styled("\u2190\u2192", Style.create().fg(Color.YELLOW).bold()),
                Span.raw("/"),
                Span.styled("Tab", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" panels  "),
                Span.styled("PgUp/Dn", Style.create().fg(Color.YELLOW).bold()),
                Span.raw(" scroll"));

        frame.renderWidget(Paragraph.from(footer), area);
    }

    // ---- Helpers ----

    private static void flowFields(List<Line> lines, List<String[]> fields, int maxWidth, OptionInfo opt) {
        List<Span> currentSpans = new ArrayList<>();
        int currentLen = 1; // leading space
        String gap = "    ";

        for (String[] field : fields) {
            String label = field[0] + ": ";
            String value = field[1];
            int fieldLen = label.length() + value.length();

            // If adding this field would exceed width, flush current line
            if (!currentSpans.isEmpty() && currentLen + gap.length() + fieldLen > maxWidth) {
                lines.add(Line.from(currentSpans));
                currentSpans = new ArrayList<>();
                currentLen = 1;
            }

            if (!currentSpans.isEmpty()) {
                currentSpans.add(Span.raw(gap));
                currentLen += gap.length();
            } else {
                currentSpans.add(Span.raw(" "));
            }

            currentSpans.add(Span.styled(label, Style.create().fg(Color.YELLOW).bold()));

            // Apply special styling for certain values
            Style valueStyle;
            if ("DEPRECATED".equals(value)) {
                valueStyle = Style.create().fg(Color.RED).bold();
            } else if (opt != null && "Required".equals(field[0]) && opt.required) {
                valueStyle = Style.create().fg(Color.RED).bold();
            } else if (opt != null && "Name".equals(field[0])) {
                valueStyle = Style.create().fg(Color.CYAN).bold();
            } else if ("Label".equals(field[0]) || "Values".equals(field[0])) {
                valueStyle = Style.create().fg(Color.CYAN);
            } else {
                valueStyle = Style.create();
            }
            currentSpans.add(Span.styled(value, valueStyle));
            currentLen += fieldLen;
        }

        if (!currentSpans.isEmpty()) {
            lines.add(Line.from(currentSpans));
        }
    }

    private static void wrapText(List<Line> lines, String text, int width, Style style) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int pos = 0;
        while (pos < text.length()) {
            int end = Math.min(pos + width, text.length());
            if (end < text.length() && end > pos) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > pos) {
                    end = lastSpace + 1;
                }
            }
            lines.add(Line.from(Span.styled(" " + text.substring(pos, end).trim(), style)));
            pos = end;
        }
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
}
