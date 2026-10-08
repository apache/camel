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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.KameletDefinitions;
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.tooling.model.ArtifactModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

/**
 * TUI tab showing Camel catalog artifacts (components, data formats, languages, others) that the running integration
 * uses, based on its declared Maven dependencies.
 */
class CatalogTab extends AbstractTableTab {

    private static final String[] SCOPES = { "all", "component", "dataformat", "language", "other", "eip", "kamelet" };

    private final AtomicBoolean loading = new AtomicBoolean(false);

    private boolean filterInputActive;
    private TextInputState filterInputState = new TextInputState("");
    private String filterTerm;
    private int scopeIndex;
    private boolean fullCatalog;
    private CamelCatalog catalog;
    private volatile List<CatalogEntry> allEntries = Collections.emptyList();
    private volatile List<CatalogEntry> filteredEntries = Collections.emptyList();
    private String lastPid;
    private volatile String errorMessage;
    private volatile boolean dataLoaded;

    CatalogTab(MonitorContext ctx) {
        super(ctx, "name", "kind", "description");
    }

    @Override
    public void onTabSelected() {
        String pid = ctx.selectedPid;
        if (pid != null && !pid.equals(lastPid)) {
            lastPid = pid;
            allEntries = Collections.emptyList();
            dataLoaded = false;
        }
        if (!dataLoaded) {
            loadCatalogData();
        }
    }

    @Override
    public boolean ensureDataLoaded() {
        onTabSelected();
        return true;
    }

    @Override
    public String dataLoadError() {
        if (!dataLoaded) {
            return null;
        }
        if (errorMessage != null) {
            return errorMessage;
        }
        return allEntries.isEmpty() ? "No catalog entries for the selected integration" : null;
    }

    @Override
    public void onIntegrationChanged() {
        allEntries = Collections.emptyList();
        filteredEntries = Collections.emptyList();
        filterTerm = null;
        filterInputActive = false;
        scopeIndex = 0;
        catalog = null;
        lastPid = null;
        errorMessage = null;
        dataLoaded = false;
        loading.set(false);
        // loaded when the tab is shown (onTabSelected): a hidden tab must not hold up the one the user looks at
    }

    boolean isFilterInputActive() {
        return filterInputActive;
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (filterInputActive) {
            return handleFilterInput(ke);
        }
        return super.handleKeyEvent(ke);
    }

    @Override
    protected boolean handleTabKeyEvent(KeyEvent ke) {
        if (ke.isChar('/')) {
            filterInputActive = true;
            filterInputState = new TextInputState(filterTerm != null ? filterTerm : "");
            return true;
        }
        if (ke.isCharIgnoreCase('f')) {
            scopeIndex = (scopeIndex + 1) % SCOPES.length;
            refilter();
            return true;
        }
        if (ke.isCharIgnoreCase('a')) {
            fullCatalog = !fullCatalog;
            dataLoaded = false;
            loadCatalogData();
            return true;
        }
        if (ke.isCharIgnoreCase('d')) {
            openDocViewer();
            return true;
        }
        if (ke.isCharIgnoreCase('o')) {
            openOptionsViewer();
            return true;
        }
        return false;
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
            refilter();
            return true;
        }
        FormHelper.handleTextInput(ke, filterInputState);
        return true;
    }

    @Override
    public boolean handleEscape() {
        if (filterTerm != null) {
            filterTerm = null;
            refilter();
            return true;
        }
        return false;
    }

    @Override
    protected int getRowCount() {
        return filteredEntries.size();
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        if (loading.get() && allEntries.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled("  Loading catalog...", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(fullCatalog ? " Catalog [All] " : " Catalog [App] ").build())
                            .build(),
                    area);
            return;
        }

        if (errorMessage != null && allEntries.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled("  " + errorMessage, Theme.error()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(fullCatalog ? " Catalog [All] " : " Catalog [App] ").build())
                            .build(),
                    area);
            return;
        }

        renderContent(frame, area, info);
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        List<CatalogEntry> sorted = new ArrayList<>(filteredEntries);
        sorted.sort(this::sortEntry);

        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.percentage(30))
                .split(area);
        renderTable(frame, chunks.get(0), sorted);
        renderDetail(frame, chunks.get(1), sorted);
    }

    private void renderTable(Frame frame, Rect area, List<CatalogEntry> sorted) {
        List<Row> rows = new ArrayList<>();
        for (CatalogEntry entry : sorted) {
            Style nameStyle = entry.deprecated
                    ? Theme.error().dim()
                    : Style.EMPTY.fg(Theme.accent());
            String name = entry.deprecated ? entry.name + " (deprecated)" : entry.name;
            Style kindStyle = kindStyle(entry.kind);
            rows.add(Row.from(
                    Cell.from(Span.styled(" " + name, nameStyle)),
                    Cell.from(Span.styled(entry.kind, kindStyle)),
                    Cell.from(Span.styled(entry.description, Style.EMPTY.dim())),
                    Cell.from(entry.project
                            ? Span.styled(entry.label, Theme.label().bold())
                            : Span.styled(entry.label != null ? entry.label : "", Style.EMPTY.dim()))));
        }

        if (rows.isEmpty() && dataLoaded) {
            rows.add(emptyRow("No catalog entries found", 4));
        }

        String scope = SCOPES[scopeIndex];
        boolean scoped = !"all".equals(scope);
        boolean filtered = filterTerm != null || scoped;
        long scopeTotal = scoped
                ? allEntries.stream().filter(e -> scope.equals(e.kind)).count()
                : allEntries.size();
        StringBuilder title = new StringBuilder(fullCatalog ? " Catalog [All] " : " Catalog [App] ");
        title.append('[');
        if (filtered) {
            title.append(filteredEntries.size()).append('/').append(scopeTotal);
        } else {
            title.append(filteredEntries.size());
        }
        title.append(']');
        if (!"all".equals(scope)) {
            title.append(" scope:").append(scope);
        }
        if (filterTerm != null) {
            title.append(" filter:\"").append(filterTerm).append('"');
        }
        title.append(' ');

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(" " + sortLabel("NAME", "name"), sortStyle("name"))),
                        Cell.from(Span.styled(sortLabel("KIND", "kind"), sortStyle("kind"))),
                        Cell.from(Span.styled(sortLabel("DESCRIPTION", "description"), sortStyle("description"))),
                        Cell.from(Span.styled("LABEL", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(28),
                        Constraint.length(12),
                        Constraint.fill(),
                        Constraint.length(20))
                .highlightStyle(Theme.selectionBg())
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(title.toString()).build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, table, sorted.size());
    }

    private void renderDetail(Frame frame, Rect area, List<CatalogEntry> sorted) {
        Integer sel = tableState.selected();
        if (sel == null || sel < 0 || sel >= sorted.size()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(" Select an artifact", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Detail ").build())
                            .build(),
                    area);
            return;
        }

        CatalogEntry entry = sorted.get(sel);
        String title = " " + entry.name + " ";

        List<Line> lines = new ArrayList<>();
        if (entry.kamelet != null) {
            kameletDetail(lines, entry, area.width());
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(lines))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(title).build())
                            .build(),
                    area);
            return;
        }
        addDetailField(lines, "Title", entry.title, area.width());
        addDetailField(lines, "Description", entry.description, area.width());
        addDetailField(lines, "Kind", entry.kind, area.width());
        if (entry.groupId != null) {
            String maven = entry.groupId + ":" + entry.artifactId;
            if (entry.version != null) {
                maven += ":" + entry.version;
            }
            addDetailField(lines, "Maven", maven, area.width());
        }
        if (entry.firstVersion != null) {
            addDetailField(lines, "Since", entry.firstVersion, area.width());
        }
        if (entry.supportLevel != null) {
            addDetailField(lines, "Support Level", entry.supportLevel, area.width());
        }
        if (entry.nativeSupported) {
            addDetailField(lines, "Native", "supported", area.width());
        }
        if (entry.label != null) {
            addDetailField(lines, "Labels", entry.label, area.width());
        }
        if (entry.deprecated) {
            String depText = "true";
            if (entry.deprecatedSince != null) {
                depText += " (since " + entry.deprecatedSince + ")";
            }
            if (entry.deprecationNote != null) {
                depText += " — " + entry.deprecationNote;
            }
            addDetailField(lines, "Deprecated", depText, area.width());
        }

        frame.renderWidget(
                Paragraph.builder()
                        .text(Text.from(lines))
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                .title(title).build())
                        .build(),
                area);
    }

    private CatalogEntry selectedEntry() {
        List<CatalogEntry> sorted = new ArrayList<>(filteredEntries);
        sorted.sort(this::sortEntry);
        Integer sel = tableState.selected();
        return sel == null || sel < 0 || sel >= sorted.size() ? null : sorted.get(sel);
    }

    private void openDocViewer() {
        CatalogEntry entry = selectedEntry();
        if (entry != null && entry.kamelet != null) {
            openKameletDoc(entry);
            return;
        }
        if (ctx.openCatalogDocCallback == null || catalog == null || entry == null) {
            return;
        }
        ctx.openCatalogDocCallback.accept(entry.name, entry.kind, catalog);
    }

    private void openOptionsViewer() {
        CatalogEntry entry = selectedEntry();
        if (entry != null && entry.kamelet != null) {
            // the options of a Kamelet are its properties, which its doc lists
            openKameletDoc(entry);
            return;
        }
        if (ctx.openOptionsCallback == null || catalog == null || entry == null) {
            return;
        }
        ctx.openOptionsCallback.accept(entry.name, entry.kind, catalog);
    }

    private void openKameletDoc(CatalogEntry entry) {
        if (ctx.openMarkdownCallback != null) {
            ctx.openMarkdownCallback.accept(entry.name, kameletMarkdown(entry.kamelet));
        }
    }

    // ---- Kamelets (CAMEL-25416) ----

    private static void kameletDetail(List<Line> lines, CatalogEntry entry, int width) {
        KameletDefinitions.Definition def = entry.kamelet;
        lines.add(Line.from(Span.styled("  Kind: ", Style.EMPTY.dim()),
                Span.styled("kamelet" + (def.type() != null ? " (" + def.type() + ")" : ""), kindStyle("kamelet")),
                entry.project ? Span.styled("  the project's own", Theme.label().bold()) : Span.raw("")));
        if (def.title() != null && !def.title().equals(def.name())) {
            lines.add(Line.from(Span.styled("  Title: ", Style.EMPTY.dim()), Span.raw(def.title())));
        }
        if (def.description() != null) {
            lines.add(Line.from(Span.styled("  Description: ", Style.EMPTY.dim()),
                    Span.raw(TuiHelper.truncate(def.description(), Math.max(10, width - 18)))));
        }
        lines.add(Line.from(Span.styled("  From: ", Style.EMPTY.dim()), Span.raw(def.source())));
        String props = KameletDefinitions.propertyList(def);
        lines.add(Line.from(Span.styled("  Properties: ", Style.EMPTY.dim()),
                Span.raw(props.isEmpty() ? "none" : TuiHelper.truncate(props, Math.max(10, width - 17)))));
        lines.add(Line.from(Span.styled("  Use: ", Style.EMPTY.dim()),
                Span.raw(("source".equals(def.type()) ? "from: " : "to: ") + "kamelet:" + def.name()
                         + ", its properties under parameters:")));
    }

    /** The doc of a Kamelet: its type, where it comes from, how a route uses it, and its properties. */
    static String kameletMarkdown(KameletDefinitions.Definition def) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(def.title() != null ? def.title() : def.name()).append("\n\n");
        if (def.description() != null) {
            md.append(def.description()).append("\n\n");
        }
        md.append("**Kamelet:** `").append(def.name()).append('`');
        if (def.type() != null) {
            md.append(" (").append(def.type()).append(')');
        }
        md.append("  \n**From:** ").append(def.source()).append("\n\n");
        md.append("## Usage\n\n```yaml\n");
        boolean source = "source".equals(def.type());
        md.append(source ? "from:\n  uri: kamelet:" : "- to:\n    uri: kamelet:").append(def.name()).append('\n');
        List<KameletDefinitions.Property> required = new ArrayList<>();
        for (KameletDefinitions.Property p : def.properties()) {
            if (p.required() && p.defaultValue() == null) {
                required.add(p);
            }
        }
        if (!required.isEmpty()) {
            md.append(source ? "  parameters:\n" : "    parameters:\n");
            for (KameletDefinitions.Property p : required) {
                md.append(source ? "    " : "      ").append(p.name()).append(": ...\n");
            }
        }
        md.append("```\n\nThe properties go under `parameters:`: they are the Kamelet's, not the options of the"
                  + " component it uses.\n\n## Properties\n\n");
        if (def.properties().isEmpty()) {
            md.append("None.\n");
        } else {
            md.append("| Name | Required | Type | Default | Description |\n|---|---|---|---|---|\n");
            for (KameletDefinitions.Property p : def.properties()) {
                md.append("| ").append(p.name())
                        .append(" | ").append(p.required() && p.defaultValue() == null ? "yes" : "")
                        .append(" | ").append(p.type() != null ? p.type() : "")
                        .append(" | ").append(p.defaultValue() != null ? p.defaultValue() : "")
                        .append(" | ")
                        .append(p.description() != null ? p.description().replace("|", "\\|").replace("\n", " ") : "")
                        .append(" |\n");
            }
        }
        return md.toString();
    }

    private static final java.util.regex.Pattern KAMELET_URI
            = java.util.regex.Pattern.compile("kamelet:([a-z0-9][a-z0-9-]*)");

    /**
     * The Kamelets of the catalog view: the project's own Kamelet files always, the Kamelets of the catalog its routes
     * use, and with the full catalog all the Kamelets of the catalog.
     */
    static List<CatalogEntry> kameletEntries(
            Path dir, boolean full, java.util.Map<String, KameletDefinitions.Definition> catalogKamelets) {
        List<CatalogEntry> entries = new ArrayList<>();
        java.util.Map<String, KameletDefinitions.Definition> project = KameletDefinitions.projectKamelets(dir);
        for (KameletDefinitions.Definition def : project.values()) {
            entries.add(kameletEntry(def, true));
        }
        Set<String> used = full ? Set.of() : usedKamelets(dir);
        for (KameletDefinitions.Definition def : catalogKamelets.values()) {
            if (!project.containsKey(def.name()) && (full || used.contains(def.name()))) {
                entries.add(kameletEntry(def, false));
            }
        }
        return entries;
    }

    private static CatalogEntry kameletEntry(KameletDefinitions.Definition def, boolean project) {
        CatalogEntry entry = new CatalogEntry();
        entry.name = def.name();
        entry.kind = "kamelet";
        entry.title = def.title() != null ? def.title() : def.name();
        entry.description = def.description() != null ? def.description() : "";
        entry.label = project ? "project" : def.type();
        entry.kamelet = def;
        entry.project = project;
        return entry;
    }

    /** The Kamelets the route files of the directory send to or consume from, kamelet:source and :sink left out. */
    static Set<String> usedKamelets(Path dir) {
        Set<String> used = new HashSet<>();
        if (dir == null || !java.nio.file.Files.isDirectory(dir)) {
            return used;
        }
        try (java.util.stream.Stream<Path> files = java.nio.file.Files.walk(dir, 6)) {
            files.filter(f -> {
                String n = f.getFileName().toString();
                return (n.endsWith(".yaml") || n.endsWith(".yml") || n.endsWith(".java") || n.endsWith(".xml"))
                        && !f.toString().contains("/target/") && !f.toString().contains("/.");
            }).forEach(f -> {
                try {
                    java.util.regex.Matcher m = KAMELET_URI.matcher(java.nio.file.Files.readString(f));
                    while (m.find()) {
                        String name = m.group(1);
                        if (!"source".equals(name) && !"sink".equals(name)) {
                            used.add(name);
                        }
                    }
                } catch (Exception e) {
                    // an unreadable file uses none
                }
            });
        } catch (Exception e) {
            // no directory listing: none used
        }
        return used;
    }

    private void addDetailField(List<Line> lines, String label, String value, int width) {
        if (value == null || value.isEmpty()) {
            return;
        }
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
            lines.add(Line.from(Span.styled(prefix, Style.EMPTY.dim())));
            int indent = 6;
            String indentStr = " ".repeat(indent);
            int wrapWidth = width - indent - 2;
            if (wrapWidth <= 0) {
                wrapWidth = 40;
            }
            int pos = 0;
            while (pos < value.length()) {
                int lineEnd = Math.min(pos + wrapWidth, value.length());
                lines.add(Line.from(Span.styled(indentStr + value.substring(pos, lineEnd), Style.EMPTY.fg(Theme.baseFg()))));
                pos = lineEnd;
            }
        }
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
        hint(spans, "s", "sort");
        hint(spans, "a", fullCatalog ? "app only" : "all");
        hint(spans, "f", "scope [" + SCOPES[scopeIndex] + "]");
        hint(spans, "d", "doc");
        hint(spans, "o", "options");
        if (filterTerm != null) {
            spans.add(Span.styled("  /", Theme.label().bold()));
            spans.add(Span.raw("\"" + filterTerm + "\"  "));
        } else {
            hint(spans, "/", "filter");
        }
    }

    private int sortEntry(CatalogEntry a, CatalogEntry b) {
        if (a.project != b.project) {
            // the project's own Kamelets first, whatever the sort
            return a.project ? -1 : 1;
        }
        int result = switch (sort) {
            case "kind" -> a.kind.compareToIgnoreCase(b.kind);
            case "description" -> a.description.compareToIgnoreCase(b.description);
            default -> a.name.compareToIgnoreCase(b.name); // "name"
        };
        return sortReversed ? -result : result;
    }

    private static Style kindStyle(String kind) {
        return switch (kind) {
            case "component" -> Style.EMPTY.fg(Theme.accent());
            case "dataformat" -> Theme.success();
            case "language" -> Theme.warning();
            case "eip" -> Theme.info();
            case "kamelet" -> Theme.label();
            default -> Style.EMPTY.dim();
        };
    }

    // ---- Data Loading ----

    private void loadCatalogData() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || ctx.runner == null) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }

        boolean full = fullCatalog;
        ctx.backgroundExecutor.execute(() -> {
            try {
                Set<String> appArtifacts = null;
                if (!full) {
                    DependencyLoader.LoadResult result = DependencyLoader.loadDependencies(info);
                    if (result.error() != null && result.entries().isEmpty()) {
                        applyResult(Collections.emptyList(), null, result.error());
                        return;
                    }
                    appArtifacts = new HashSet<>();
                    for (DependencyLoader.DepEntry dep : result.entries()) {
                        if (dep.isCamel()) {
                            appArtifacts.add(dep.groupId() + ":" + dep.artifactId());
                        }
                    }
                }

                CamelCatalog cat = CatalogLoader.loadCatalog(null, info.camelVersion, true);
                if (cat == null) {
                    applyResult(Collections.emptyList(), null, "Could not load catalog for version " + info.camelVersion);
                    return;
                }

                List<CatalogEntry> entries = new ArrayList<>();
                collectArtifacts(cat, "component", cat.findComponentNames(), appArtifacts, entries);
                collectArtifacts(cat, "dataformat", cat.findDataFormatNames(), appArtifacts, entries);
                collectArtifacts(cat, "language", cat.findLanguageNames(), appArtifacts, entries);
                collectArtifacts(cat, "other", cat.findOtherNames(), appArtifacts, entries);
                collectEips(cat, info.pid, full, entries);
                // the Kamelets: the project's own first, then those of the catalog (CAMEL-25416)
                Path dir = info.phantom && info.sourceDir != null
                        ? Path.of(info.sourceDir) : FilesBrowser.resolveSourceDirectory(info);
                entries.addAll(kameletEntries(dir, full, KameletDefinitions.catalog()));

                entries.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
                applyResult(entries, cat, null);
            } catch (Exception e) {
                applyResult(Collections.emptyList(), null, "Error: " + e.getMessage());
            } finally {
                loading.set(false);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void collectArtifacts(
            CamelCatalog catalog, String kind, List<String> names,
            Set<String> appArtifacts, List<CatalogEntry> entries) {
        for (String name : names) {
            try {
                ArtifactModel<?> model = (ArtifactModel<?>) switch (kind) {
                    case "component" -> catalog.componentModel(name);
                    case "dataformat" -> catalog.dataFormatModel(name);
                    case "language" -> catalog.languageModel(name);
                    case "other" -> catalog.otherModel(name);
                    default -> null;
                };
                if (model == null) {
                    continue;
                }
                if (appArtifacts != null) {
                    String ga = model.getGroupId() + ":" + model.getArtifactId();
                    if (!appArtifacts.contains(ga)) {
                        continue;
                    }
                }
                CatalogEntry entry = new CatalogEntry();
                entry.name = model.getName();
                entry.kind = kind;
                entry.title = model.getTitle() != null ? model.getTitle() : name;
                entry.description = model.getDescription() != null ? model.getDescription() : "";
                entry.label = model.getLabel();
                entry.groupId = model.getGroupId();
                entry.artifactId = model.getArtifactId();
                entry.version = model.getVersion();
                entry.firstVersion = model.getFirstVersion();
                entry.supportLevel = model.getSupportLevel() != null ? model.getSupportLevel().name() : null;
                entry.nativeSupported = model.isNativeSupported();
                entry.deprecated = model.isDeprecated();
                entry.deprecatedSince = model.getDeprecatedSince();
                entry.deprecationNote = model.getDeprecationNote();
                entries.add(entry);
            } catch (Exception e) {
                // skip unparseable entries
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void collectEips(CamelCatalog catalog, String pid, boolean full, List<CatalogEntry> entries) {
        try {
            Set<String> eipNames;
            if (full) {
                eipNames = new HashSet<>(catalog.findModelNames());
            } else {
                eipNames = new HashSet<>();

                JsonObject root = new JsonObject();
                root.put("action", "route-structure");
                root.put("filter", "*");
                root.put("brief", true);
                root.put("metric", false);

                JsonObject jo = ctx.executeAction(pid, root, 5000);
                if (jo == null) {
                    return;
                }

                List<JsonObject> routes = (List<JsonObject>) jo.getCollection("routes");
                if (routes != null) {
                    for (JsonObject route : routes) {
                        List<JsonObject> code = (List<JsonObject>) route.getCollection("code");
                        if (code != null) {
                            for (JsonObject node : code) {
                                String type = node.getString("type");
                                if (type != null) {
                                    eipNames.add(type);
                                }
                            }
                        }
                    }
                }
            }

            Set<String> skipEips = Set.of("when", "otherwise",
                    "langChain4jCharacterTokenizer", "langChain4jLineTokenizer",
                    "langChain4jParagraphTokenizer", "langChain4jSentenceTokenizer",
                    "langChain4jWordTokenizer");
            for (String name : eipNames) {
                if (skipEips.contains(name)) {
                    continue;
                }
                EipModel model = catalog.eipModel(name);
                if (model == null) {
                    continue;
                }
                if (full && (model.getLabel() == null || !model.getLabel().contains("eip"))) {
                    continue;
                }
                CatalogEntry entry = new CatalogEntry();
                entry.name = model.getName();
                entry.kind = "eip";
                entry.title = model.getTitle() != null ? model.getTitle() : name;
                entry.description = model.getDescription() != null ? model.getDescription() : "";
                entry.label = model.getLabel();
                entry.firstVersion = model.getFirstVersion();
                entry.deprecated = model.isDeprecated();
                entry.deprecatedSince = model.getDeprecatedSince();
                entry.deprecationNote = model.getDeprecationNote();
                entries.add(entry);
            }
        } catch (Exception e) {
            // ignore - EIP detection is best-effort
        }
    }

    private void applyResult(List<CatalogEntry> entries, CamelCatalog cat, String error) {
        if (ctx.runner == null) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> {
            allEntries = entries;
            catalog = cat;
            errorMessage = error;
            dataLoaded = true;
            refilter();
        });
    }

    private void refilter() {
        List<CatalogEntry> result = new ArrayList<>();
        String ft = filterTerm != null ? filterTerm.toLowerCase() : null;
        String scope = SCOPES[scopeIndex];
        for (CatalogEntry entry : allEntries) {
            if ("all".equals(scope) && "eip".equals(entry.kind)) {
                continue;
            }
            if (!"all".equals(scope) && !scope.equals(entry.kind)) {
                continue;
            }
            if (ft != null
                    && !entry.name.toLowerCase().contains(ft)
                    && !entry.title.toLowerCase().contains(ft)
                    && !entry.description.toLowerCase().contains(ft)
                    && !(entry.label != null && entry.label.toLowerCase().contains(ft))) {
                continue;
            }
            result.add(entry);
        }
        filteredEntries = result;
        if (!filteredEntries.isEmpty()) {
            tableState.select(0);
        }
    }

    @Override
    public boolean setFilter(String filter) {
        filterTerm = filter != null && !filter.isEmpty() ? filter : null;
        refilter();
        return true;
    }

    @Override
    public boolean setInputValue(String field, String value) {
        if ("filter".equals(field)) {
            return setFilter(value);
        }
        return false;
    }

    @Override
    public SelectionContext getSelectionContext() {
        if (filteredEntries.isEmpty()) {
            return null;
        }
        List<CatalogEntry> sorted = new ArrayList<>(filteredEntries);
        sorted.sort(this::sortEntry);
        List<String> items = sorted.stream().map(e -> e.name).toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Catalog");
    }

    @Override
    public String description() {
        return "Camel catalog artifacts used by the integration";
    }

    @Override
    public String getHelpText() {
        return DocHelper.loadHelpText("catalog");
    }

    @Override
    public JsonObject getTableDataAsJson() {
        if (filteredEntries.isEmpty()) {
            return null;
        }
        List<CatalogEntry> sorted = new ArrayList<>(filteredEntries);
        sorted.sort(this::sortEntry);
        JsonObject result = new JsonObject();
        result.put("tab", "Catalog");
        JsonArray rows = new JsonArray();
        for (CatalogEntry e : sorted) {
            JsonObject row = new JsonObject();
            row.put("name", e.name);
            row.put("kind", e.kind);
            row.put("title", e.title);
            row.put("description", e.description);
            if (e.label != null) {
                row.put("label", e.label);
            }
            if (e.artifactId != null) {
                row.put("artifactId", e.artifactId);
            }
            if (e.firstVersion != null) {
                row.put("since", e.firstVersion);
            }
            if (e.supportLevel != null) {
                row.put("supportLevel", e.supportLevel);
            }
            if (e.nativeSupported) {
                row.put("nativeSupported", true);
            }
            if (e.deprecated) {
                row.put("deprecated", true);
                if (e.deprecatedSince != null) {
                    row.put("deprecatedSince", e.deprecatedSince);
                }
                if (e.deprecationNote != null) {
                    row.put("deprecationNote", e.deprecationNote);
                }
            }
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", sorted.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }

    // ---- Data Class ----

    static class CatalogEntry {
        String name;
        String kind;
        String title;
        String description;
        String label;
        String groupId;
        String artifactId;
        String version;
        String firstVersion;
        String supportLevel;
        boolean nativeSupported;
        boolean deprecated;
        String deprecatedSince;
        String deprecationNote;
        // a Kamelet: its definition, and whether it is a file of the project
        KameletDefinitions.Definition kamelet;
        boolean project;
    }
}
