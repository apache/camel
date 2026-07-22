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
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
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

    private static final String[] SCOPES = { "all", "component", "dataformat", "language", "other", "eip" };

    private final AtomicBoolean loading = new AtomicBoolean(false);

    private boolean filterInputActive;
    private TextInputState filterInputState = new TextInputState("");
    private String filterTerm;
    private int scopeIndex;
    private boolean fullCatalog;
    private CamelCatalog catalog;
    private List<CatalogEntry> allEntries = Collections.emptyList();
    private List<CatalogEntry> filteredEntries = Collections.emptyList();
    private String lastPid;
    private String errorMessage;
    private boolean dataLoaded;

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
        if (ctx.selectedPid != null) {
            lastPid = ctx.selectedPid;
            loadCatalogData();
        }
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
                    Cell.from(Span.styled(entry.label != null ? entry.label : "", Style.EMPTY.dim()))));
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
        renderScrollbar(frame, sorted.size());
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

    private void openDocViewer() {
        if (ctx.openMarkdownCallback == null || catalog == null) {
            return;
        }
        List<CatalogEntry> sorted = new ArrayList<>(filteredEntries);
        sorted.sort(this::sortEntry);
        Integer sel = tableState.selected();
        if (sel == null || sel < 0 || sel >= sorted.size()) {
            return;
        }
        CatalogEntry entry = sorted.get(sel);
        String docName = switch (entry.kind) {
            case "component" -> entry.name + "-component";
            case "dataformat" -> entry.name + "-dataformat";
            case "language" -> entry.name + "-language";
            case "eip" -> entry.name + "-eip";
            default -> entry.name;
        };
        String adoc = catalog.asciiDoc(docName);
        if (adoc != null) {
            String md = DocHelper.asciidocToMarkdown(adoc);
            ctx.openMarkdownCallback.accept(entry.name, md);
        }
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
        if (filterTerm != null) {
            spans.add(Span.styled("  /", Theme.label().bold()));
            spans.add(Span.raw("\"" + filterTerm + "\"  "));
        } else {
            hint(spans, "/", "filter");
        }
        hintLast(spans, TuiIcons.HINT_SCROLL, "navigate");
    }

    private int sortEntry(CatalogEntry a, CatalogEntry b) {
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
        ctx.runner.scheduler().execute(() -> {
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
                Path outputFile = ctx.getOutputFile(pid);
                PathUtils.deleteFile(outputFile);

                JsonObject root = new JsonObject();
                root.put("action", "route-structure");
                root.put("filter", "*");
                root.put("brief", true);
                root.put("metric", false);
                PathUtils.writeTextSafely(root.toJson(), ctx.getActionFile(pid));

                JsonObject jo = TuiHelper.pollJsonResponse(outputFile, 5000);
                PathUtils.deleteFile(outputFile);
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
        return """
                # Catalog

                The Catalog tab shows Camel catalog artifacts (components, data formats,
                languages, and others). By default it shows only artifacts used by the
                running integration (matched via Maven dependencies). Press `a` to toggle
                to the full catalog showing all available artifacts.

                For each Camel dependency in the integration, the tab cross-references
                the Camel catalog to identify all artifacts provided by that dependency.
                For example, `camel-core` provides the `direct`, `seda`, `timer`, `bean`,
                `log`, and `mock` components, while `camel-kafka` provides the `kafka`
                component.

                ## Table Columns

                - **NAME** — The catalog artifact name (e.g., `kafka`, `timer`, `json-jackson`)
                - **KIND** — The artifact type: `component`, `dataformat`, `language`, or `other`
                - **DESCRIPTION** — Short description of the artifact
                - **LABEL** — Category labels (e.g., "messaging", "scheduling", "transformation")

                Deprecated artifacts are shown dimmed with a "(deprecated)" suffix.

                ## Mode

                Press `a` to toggle between:

                - **app only** — show only artifacts from the integration's dependencies (default)
                - **all** — show the full Camel catalog

                ## Scope

                Press `f` to cycle the scope filter:

                - **all** — show all catalog artifacts (default)
                - **component** — show only components
                - **dataformat** — show only data formats
                - **language** — show only expression languages
                - **other** — show only miscellaneous artifacts
                - **eip** — show EIPs detected from the running app's routes

                ## Filter

                Press `/` to open the filter input. Type a search term and press
                `Enter` to filter by substring match on name, title, or label.

                ## Documentation

                Press `d` to open the full documentation for the selected artifact in
                a scrollable viewer. The documentation is loaded from the Camel catalog
                and converted from AsciiDoc to Markdown. Use `↑`/`↓`/`PgUp`/`PgDn` or
                mouse scroll to navigate, and `Esc` to close.

                ## Keys

                - `s` — cycle sort column (name, kind, description)
                - `S` — reverse sort order
                - `a` — toggle app only / full catalog
                - `f` — cycle scope
                - `d` — open documentation viewer
                - `/` — open filter
                - `Esc` — clear filter or back
                """;
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
            row.put("artifactId", e.artifactId);
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
    }
}
