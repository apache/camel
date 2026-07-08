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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.tamboui.layout.Constraint;
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
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

/**
 * TUI tab showing declared Maven dependencies for the selected integration. Reads from pom.xml (for exported Spring
 * Boot / Quarkus apps) or from .camel-jbang/camel-jbang-run.properties (for JBang mode). Unlike the Classpath tab, this
 * filters out internal infrastructure JARs and shows only the dependencies the user's application actually declares.
 */
class MavenDependenciesTab extends AbstractTableTab {

    private static final String[] SCOPES = { "all", "camel", "other" };

    private final AtomicBoolean loading = new AtomicBoolean(false);

    private boolean filterInputActive;
    private TextInputState filterInputState = new TextInputState("");
    private String filterTerm;
    private int scopeIndex;
    private List<DependencyLoader.DepEntry> allEntries = Collections.emptyList();
    private List<DependencyLoader.DepEntry> filteredEntries = Collections.emptyList();
    private String lastPid;
    private String errorMessage;
    private boolean dataLoaded;
    private String dataSource;
    private boolean transitiveMode;
    private boolean transitiveLoading;
    private boolean transitiveLoaded;
    private List<DependencyLoader.DepEntry> transitiveEntries = Collections.emptyList();

    MavenDependenciesTab(MonitorContext ctx) {
        super(ctx, "artifact", "version", "via");
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
            loadDependencies();
        }
    }

    @Override
    public void onIntegrationChanged() {
        allEntries = Collections.emptyList();
        filteredEntries = Collections.emptyList();
        filterTerm = null;
        filterInputActive = false;
        scopeIndex = 0;
        lastPid = null;
        errorMessage = null;
        dataLoaded = false;
        dataSource = null;
        transitiveMode = false;
        transitiveLoading = false;
        transitiveLoaded = false;
        transitiveEntries = Collections.emptyList();
        loading.set(false);
        if (ctx.selectedPid != null) {
            lastPid = ctx.selectedPid;
            loadDependencies();
        }
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
        if (ke.isCharIgnoreCase('t')) {
            if (!transitiveLoaded && !transitiveLoading) {
                resolveTransitives();
            } else {
                transitiveMode = !transitiveMode;
                refilter();
            }
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

    boolean isFilterInputActive() {
        return filterInputActive;
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
                            .text(Text.from(Line.from(Span.styled("  Loading dependencies...", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Maven Dependencies ").build())
                            .build(),
                    area);
            return;
        }

        if (errorMessage != null && allEntries.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled("  " + errorMessage, Style.EMPTY.fg(Color.LIGHT_RED)))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Maven Dependencies ").build())
                            .build(),
                    area);
            return;
        }

        renderContent(frame, area, info);
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        List<DependencyLoader.DepEntry> sorted = new ArrayList<>(filteredEntries);
        sorted.sort(this::sortDep);

        List<Row> rows = new ArrayList<>();
        for (DependencyLoader.DepEntry entry : sorted) {
            String artifact = entry.groupId() + ":" + entry.artifactId();
            String ver = entry.version() != null ? entry.version() : "";
            Style style = entry.transitive() ? Style.EMPTY.dim() : Style.EMPTY;
            if (transitiveMode) {
                String via = entry.parent() != null ? DependencyLoader.shortArtifact(entry.parent()) : "";
                rows.add(Row.from(
                        Cell.from(Span.styled(" " + artifact, style)),
                        Cell.from(Span.styled(ver, style)),
                        Cell.from(Span.styled(via, Style.EMPTY.dim()))));
            } else {
                rows.add(Row.from(
                        Cell.from(Span.styled(" " + artifact, style)),
                        Cell.from(Span.styled(ver, style))));
            }
        }

        if (rows.isEmpty() && dataLoaded) {
            rows.add(emptyRow("No Maven dependency entries found", 2));
        }

        String scope = SCOPES[scopeIndex];
        int totalCount = transitiveMode ? combinedEntries().size() : allEntries.size();
        boolean filtered = filterTerm != null || !"all".equals(scope);
        StringBuilder title = new StringBuilder(" Maven Dependencies ");
        if (transitiveMode) {
            title.append("(+transitive) ");
        }
        title.append('[');
        if (filtered) {
            title.append(filteredEntries.size()).append('/').append(totalCount);
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
        if (dataSource != null) {
            title.append(" (").append(dataSource).append(')');
        }
        title.append(' ');

        Table.Builder tableBuilder = Table.builder()
                .rows(rows)
                .highlightStyle(Theme.selectionBg())
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(title.toString()).build());

        if (transitiveMode) {
            tableBuilder
                    .header(Row.from(
                            Cell.from(Span.styled(" " + sortLabel("GROUP:ARTIFACT", "artifact"), sortStyle("artifact"))),
                            Cell.from(Span.styled(sortLabel("VERSION", "version"), sortStyle("version"))),
                            Cell.from(Span.styled(sortLabel("VIA", "via"), sortStyle("via")))))
                    .widths(
                            Constraint.fill(),
                            Constraint.length(20),
                            Constraint.length(30));
        } else {
            tableBuilder
                    .header(Row.from(
                            Cell.from(Span.styled(" " + sortLabel("GROUP:ARTIFACT", "artifact"), sortStyle("artifact"))),
                            Cell.from(Span.styled(sortLabel("VERSION", "version"), sortStyle("version")))))
                    .widths(
                            Constraint.fill(),
                            Constraint.length(20));
        }

        Table table = tableBuilder.build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, sorted.size());
    }

    @Override
    public void renderFooter(List<Span> spans) {
        if (filterInputActive) {
            spans.add(Span.styled(" /", Style.EMPTY.fg(Color.YELLOW).bold()));
            spans.add(Span.raw(filterInputState.text() + "█  "));
            hint(spans, "Enter", "filter");
            hintLast(spans, "Esc", "cancel");
            return;
        }
        hint(spans, "Esc", filterTerm != null ? "clear" : "back");
        hint(spans, "s", "sort");
        hint(spans, "f", "scope [" + SCOPES[scopeIndex] + "]");
        if (transitiveLoading) {
            hint(spans, "t", "resolving...");
        } else {
            hint(spans, "t", transitiveMode ? "direct" : "transitive");
        }
        if (filterTerm != null) {
            spans.add(Span.styled("  /", Style.EMPTY.fg(Color.YELLOW).bold()));
            spans.add(Span.raw("\"" + filterTerm + "\"  "));
        } else {
            hint(spans, "/", "filter");
        }
        hintLast(spans, TuiIcons.HINT_SCROLL, "navigate");
    }

    private int sortDep(DependencyLoader.DepEntry a, DependencyLoader.DepEntry b) {
        int result = switch (sort) {
            case "version" -> {
                String va = a.version() != null ? a.version() : "";
                String vb = b.version() != null ? b.version() : "";
                yield va.compareToIgnoreCase(vb);
            }
            case "via" -> {
                String pa = a.parent() != null ? DependencyLoader.shortArtifact(a.parent()) : "";
                String pb = b.parent() != null ? DependencyLoader.shortArtifact(b.parent()) : "";
                yield pa.compareToIgnoreCase(pb);
            }
            default -> { // "artifact"
                yield a.display().compareToIgnoreCase(b.display());
            }
        };
        return sortReversed ? -result : result;
    }

    private void loadDependencies() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || ctx.runner == null) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }

        ctx.runner.scheduler().execute(() -> {
            try {
                DependencyLoader.LoadResult result = DependencyLoader.loadDependencies(info);
                applyResult(result.entries(), result.source(), result.error());
            } catch (Exception e) {
                applyResult(Collections.emptyList(), null, "Error: " + e.getMessage());
            } finally {
                loading.set(false);
            }
        });
    }

    private void resolveTransitives() {
        if (allEntries.isEmpty() || ctx.runner == null) {
            return;
        }
        transitiveLoading = true;
        ctx.runner.scheduler().execute(() -> {
            try {
                List<DependencyLoader.DepEntry> entries = DependencyLoader.resolveTransitives(allEntries);
                applyTransitiveResult(entries);
            } catch (Exception e) {
                applyTransitiveResult(Collections.emptyList());
            }
        });
    }

    private void applyTransitiveResult(List<DependencyLoader.DepEntry> entries) {
        if (ctx.runner == null) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> {
            transitiveEntries = entries;
            transitiveLoading = false;
            transitiveLoaded = true;
            transitiveMode = true;
            refilter();
        });
    }

    private void applyResult(List<DependencyLoader.DepEntry> parsed, String source, String error) {
        if (ctx.runner == null) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> {
            allEntries = parsed;
            errorMessage = error;
            dataSource = source;
            dataLoaded = true;
            refilter();
        });
    }

    private void refilter() {
        List<DependencyLoader.DepEntry> source = transitiveMode
                ? combinedEntries()
                : allEntries;
        List<DependencyLoader.DepEntry> result = new ArrayList<>();
        String ft = filterTerm != null ? filterTerm.toLowerCase() : null;
        String scope = SCOPES[scopeIndex];
        for (DependencyLoader.DepEntry entry : source) {
            if ("camel".equals(scope) && !entry.isCamel()) {
                continue;
            }
            if ("other".equals(scope) && entry.isCamel()) {
                continue;
            }
            if (ft != null && !entry.display().toLowerCase().contains(ft)) {
                continue;
            }
            result.add(entry);
        }
        filteredEntries = result;
        if (!filteredEntries.isEmpty()) {
            tableState.select(0);
        }
    }

    private List<DependencyLoader.DepEntry> combinedEntries() {
        Set<String> directKeys = new HashSet<>();
        for (DependencyLoader.DepEntry d : allEntries) {
            directKeys.add(d.groupId() + ":" + d.artifactId());
        }
        List<DependencyLoader.DepEntry> combined = new ArrayList<>(allEntries);
        for (DependencyLoader.DepEntry t : transitiveEntries) {
            if (!directKeys.contains(t.groupId() + ":" + t.artifactId())) {
                combined.add(t);
            }
        }
        combined.sort(Comparator.comparing(DependencyLoader.DepEntry::display, String.CASE_INSENSITIVE_ORDER));
        return combined;
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
        List<String> items = filteredEntries.stream().map(DependencyLoader.DepEntry::display).toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Maven Dependencies");
    }

    @Override
    public String description() {
        return "Maven dependency entries with filtering";
    }

    @Override
    public String getHelpText() {
        return """
                # Maven Dependencies

                The Maven Dependencies tab shows the declared dependencies for the
                selected integration, as they would appear in a pom.xml file. Unlike
                the Classpath tab (which shows all JARs on the JVM classpath including
                internal infrastructure), this tab filters out internal bootstrap JARs
                and shows only the dependencies the application actually declares.

                ## Data Sources

                The tab discovers dependencies from (in order of priority):

                - **pom.xml** — For exported Maven projects (Spring Boot, Quarkus).
                  Parses compile-scoped dependencies from the project's pom.xml.
                - **.camel-jbang/camel-jbang-run.properties** — For Camel CLI (JBang)
                  mode. Reads the `dependency=mvn:...` lines that list all declared
                  dependencies.

                The data source is shown in the title bar (e.g., "pom.xml" or "jbang").

                ## Table Columns

                - **GROUP:ARTIFACT** — Maven coordinate (e.g., `org.apache.camel:camel-core`)
                - **VERSION** — The dependency version (e.g., `4.22.0`)

                Each row shows the Maven coordinate and its version.

                ## Sort

                Press `s` to cycle sort column (artifact, version).
                Press `S` to reverse sort order.

                ## Scope

                Press `f` to cycle the scope filter:

                - **all** — show all dependencies (default)
                - **camel** — show only Apache Camel dependencies
                - **other** — show only non-Camel (third-party) dependencies

                The active scope is shown in the footer and title bar.

                ## Transitive Dependencies

                Press `t` to resolve transitive dependencies on demand. The first
                press triggers Maven resolution (shown as "resolving..." in the
                footer). Once resolved, transitive dependencies appear dimmed in
                the table with a **VIA** column showing which direct dependency
                pulled them in. Subsequent presses toggle between showing all
                dependencies (direct + transitive) and direct only.

                The title shows "(+transitive)" when transitive mode is active.
                This is useful for CVE auditing — when a transitive JAR has a
                known vulnerability, the VIA column tells you which direct
                dependency to upgrade or exclude.

                ## Filter

                Press `/` to open the filter input. Type a search term and press
                `Enter` to filter by substring match. For example, type `kafka` to
                find Kafka-related dependencies, or `spring` to find Spring
                dependencies. The scope and text filter work together.

                ## When To Use

                - **CVE auditing**: Review only the real application dependencies, not
                  internal infrastructure JARs that leak in via camel-kamelet-main
                - **Dependency review**: Understand what your integration actually
                  depends on
                - **Version checking**: Verify specific dependency versions
                - **Transitive analysis**: See the full dependency tree similar to
                  `mvn dependency:tree` (flat list)

                ## Keys

                - `s` — cycle sort column
                - `S` — reverse sort order
                - `f` — cycle scope (all, camel, other)
                - `t` — resolve and toggle transitive dependencies
                - `/` — open filter
                - `Esc` — clear filter or back
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        List<DependencyLoader.DepEntry> entries = filteredEntries;
        if (entries.isEmpty()) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Maven Dependencies");
        JsonArray rows = new JsonArray();
        for (DependencyLoader.DepEntry d : entries) {
            JsonObject row = new JsonObject();
            row.put("groupId", d.groupId());
            row.put("artifactId", d.artifactId());
            if (d.version() != null) {
                row.put("version", d.version());
            }
            if (d.transitive()) {
                row.put("transitive", true);
                if (d.parent() != null) {
                    row.put("parent", d.parent());
                }
            }
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", entries.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        if (dataSource != null) {
            result.put("dataSource", dataSource);
        }
        return result;
    }
}
