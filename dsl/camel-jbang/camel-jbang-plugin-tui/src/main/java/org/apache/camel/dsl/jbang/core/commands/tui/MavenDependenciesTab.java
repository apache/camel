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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.dsl.jbang.core.common.outdated.CamelRelease;
import org.apache.camel.dsl.jbang.core.common.outdated.DependencyCheck;
import org.apache.camel.dsl.jbang.core.common.outdated.DependencyRow;
import org.apache.camel.dsl.jbang.core.common.outdated.Versions;
import org.apache.camel.tooling.maven.MavenDownloaderImpl;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

/**
 * TUI tab showing declared Maven dependencies for the selected integration. Reads from pom.xml (for exported Spring
 * Boot / Quarkus apps) or from .camel-jbang/camel-jbang-run.properties (for JBang mode). Unlike the Classpath tab, this
 * filters out internal infrastructure JARs and shows only the dependencies the user's application actually declares.
 * <p/>
 * Each dependency is checked (CAMEL-27351): the version the Camel dependencies bring (CAMEL), whether the project
 * overrides it or the JAR is loaded twice (STATUS), and on demand ({@code u}) the newer releases and the CVEs of the
 * CVE Audit tab. The Camel version itself is one line on top: the Camel JARs move together.
 */
class MavenDependenciesTab extends AbstractTableTab {

    private static final String[] SCOPES = { "all", "camel", "other", "outdated" };

    private final AtomicBoolean loading = new AtomicBoolean(false);

    private final FilterField filter = new FilterField(this::refilter);
    private int scopeIndex;
    private volatile List<DependencyLoader.DepEntry> allEntries = Collections.emptyList();
    private List<DependencyRow> directRows = Collections.emptyList();
    private List<DependencyRow> transitiveRows = Collections.emptyList();
    private List<DependencyRow> filteredRows = Collections.emptyList();
    private String lastPid;
    private volatile String errorMessage;
    private volatile boolean dataLoaded;
    private String dataSource;
    private boolean transitiveMode;
    private boolean transitiveLoading;
    private boolean transitiveLoaded;
    private List<DependencyLoader.DepEntry> transitiveEntries = Collections.emptyList();

    // the check: what the Camel dependencies bring, the JARs loaded, the Camel release, the newer versions
    private boolean checkLoading;
    private Map<String, String> camelBrings = Collections.emptyMap();
    private Map<String, Set<String>> loadedVersions;
    private CamelRelease camelRelease;
    private String camelVersion;
    private boolean onlineChecking;
    private Map<String, List<String>> available;
    private String onlineError;

    // the CVE Audit tab, for the CVES column and to open it on a dependency
    private CveAuditTab cveAudit;
    private Consumer<String> openCveAudit;

    MavenDependenciesTab(MonitorContext ctx) {
        super(ctx, "artifact", "version", "status", "via");
    }

    /** The CVE Audit tab gives the CVES column, and {@code v} opens it filtered on the selected dependency. */
    void setCveAudit(CveAuditTab cveAudit, Consumer<String> openCveAudit) {
        this.cveAudit = cveAudit;
        this.openCveAudit = openCveAudit;
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
        return allEntries.isEmpty() ? "No Maven dependencies found for the selected integration" : null;
    }

    @Override
    public void onIntegrationChanged() {
        allEntries = Collections.emptyList();
        directRows = Collections.emptyList();
        transitiveRows = Collections.emptyList();
        filteredRows = Collections.emptyList();
        filter.reset();
        scopeIndex = 0;
        lastPid = null;
        errorMessage = null;
        dataLoaded = false;
        dataSource = null;
        transitiveMode = false;
        transitiveLoading = false;
        transitiveLoaded = false;
        transitiveEntries = Collections.emptyList();
        checkLoading = false;
        camelBrings = Collections.emptyMap();
        loadedVersions = null;
        camelRelease = null;
        camelVersion = null;
        onlineChecking = false;
        available = null;
        onlineError = null;
        loading.set(false);
        // loaded when the tab is shown (onTabSelected): a hidden tab must not hold up the one the user looks at
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (filter.handleKey(ke)) {
            return true;
        }
        return super.handleKeyEvent(ke);
    }

    @Override
    protected boolean handleTabKeyEvent(KeyEvent ke) {
        if (ke.isKey(KeyCode.F8) && ke.hasShift()) {
            askAi();
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
        if (ke.isCharIgnoreCase('u')) {
            checkOnline();
            return true;
        }
        if (ke.isCharIgnoreCase('v')) {
            DependencyRow row = selectedRow();
            if (row != null && openCveAudit != null) {
                openCveAudit.accept(row.artifactId());
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean isTextInputActive() {
        return filter.isEditing();
    }

    @Override
    public boolean handlePaste(String text) {
        return filter.handlePaste(text);
    }

    @Override
    public boolean handleEscape() {
        return filter.handleEscape();
    }

    @Override
    protected int getRowCount() {
        return filteredRows.size();
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
                                    Span.styled("  " + errorMessage, Theme.error()))))
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
        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.length(1), Constraint.fill(), Constraint.length(1))
                .split(area);
        frame.renderWidget(Paragraph.builder().text(Text.from(summaryLine())).build(), chunks.get(0));

        Map<String, CveAuditTab.CveCount> cves = cveCounts();
        List<DependencyRow> sorted = sortedRows();
        List<Row> rows = new ArrayList<>();
        for (DependencyRow row : sorted) {
            Style style = row.transitive() ? Style.EMPTY.dim() : Style.EMPTY;
            CveAuditTab.CveCount cve = cves != null ? cves.get(row.gav()) : null;
            List<Cell> cells = new ArrayList<>(
                    List.of(
                            Cell.from(Span.styled(" " + row.ga(), style)),
                            Cell.from(Span.styled(orEmpty(row.version()), style)),
                            Cell.from(Span.styled(row.camel() ? "" : orEmpty(row.camelVersion()), Style.EMPTY.dim())),
                            Cell.from(Span.styled(row.status().label(), statusStyle(row.status()))),
                            Cell.from(Span.styled(orEmpty(row.newer()), Theme.warning())),
                            Cell.from(Span.styled(latestText(row), row.latestIsMajor() ? Style.EMPTY.dim() : Style.EMPTY)),
                            Cell.from(cve != null
                                    ? Span.styled(String.valueOf(cve.cves()), Theme.error())
                                    : Span.raw(""))));
            if (transitiveMode) {
                cells.add(Cell.from(Span.styled(orEmpty(row.via()), Style.EMPTY.dim())));
            }
            rows.add(Row.from(cells));
        }

        int columns = transitiveMode ? 8 : 7;
        if (rows.isEmpty() && dataLoaded) {
            rows.add(emptyRow("No Maven dependency entries found", columns));
        }

        String scope = SCOPES[scopeIndex];
        int totalCount = directRows.size() + (transitiveMode ? transitiveRows.size() : 0);
        boolean filtered = filter.term() != null || !"all".equals(scope);
        StringBuilder title = new StringBuilder(" Maven Dependencies ");
        if (transitiveMode) {
            title.append("(+transitive) ");
        }
        title.append('[');
        if (filtered) {
            title.append(filteredRows.size()).append('/').append(totalCount);
        } else {
            title.append(filteredRows.size());
        }
        title.append(']');
        if (!"all".equals(scope)) {
            title.append(" scope:").append(scope);
        }
        if (filter.term() != null) {
            title.append(" filter:\"").append(filter.term()).append('"');
        }
        if (dataSource != null) {
            title.append(" (").append(dataSource).append(')');
        }
        title.append(' ');

        List<Cell> header = new ArrayList<>(
                List.of(
                        Cell.from(Span.styled(" " + sortLabel("GROUP:ARTIFACT", "artifact"), sortStyle("artifact"))),
                        Cell.from(Span.styled(sortLabel("VERSION", "version"), sortStyle("version"))),
                        Cell.from(Span.styled("CAMEL", Style.EMPTY.bold())),
                        Cell.from(Span.styled(sortLabel("STATUS", "status"), sortStyle("status"))),
                        Cell.from(Span.styled("NEWER", Style.EMPTY.bold())),
                        Cell.from(Span.styled("LATEST", Style.EMPTY.bold())),
                        Cell.from(Span.styled("CVES", Style.EMPTY.bold()))));
        List<Constraint> widths = new ArrayList<>(
                List.of(
                        Constraint.fill(),
                        Constraint.length(18),
                        Constraint.length(14),
                        Constraint.length(9),
                        Constraint.length(14),
                        Constraint.length(18),
                        Constraint.length(5)));
        if (transitiveMode) {
            header.add(Cell.from(Span.styled(sortLabel("VIA", "via"), sortStyle("via"))));
            widths.add(Constraint.length(24));
        }

        Table table = Table.builder()
                .rows(rows)
                .highlightStyle(Theme.selectionBg())
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(title.toString()).build())
                .header(Row.from(header))
                .widths(widths)
                .build();

        lastTableArea = chunks.get(1);
        frame.renderStatefulWidget(table, chunks.get(1), tableState);
        renderScrollbar(frame, table, sorted.size());

        frame.renderWidget(Paragraph.builder().text(Text.from(detailLine(cves))).build(), chunks.get(2));
    }

    /** The Camel version on top: LTS, end of life and the newer releases, and what the check found. */
    Line summaryLine() {
        List<Span> spans = new ArrayList<>();
        if (camelRelease != null) {
            spans.add(Span.styled(" " + camelRelease.summary(),
                    camelRelease.eolPassed() ? Theme.warning() : Style.EMPTY.bold()));
        } else if (camelVersion != null) {
            spans.add(Span.styled(" Camel " + camelVersion, Style.EMPTY.bold()));
        } else {
            spans.add(Span.raw(" "));
        }
        if (checkLoading) {
            spans.add(Span.styled("  checking the versions Camel brings...", Style.EMPTY.dim()));
        } else if (onlineChecking) {
            spans.add(Span.styled("  looking up newer versions...", Style.EMPTY.dim()));
        } else if (onlineError != null) {
            spans.add(Span.styled("  some newer versions could not be looked up: " + onlineError, Theme.warning()));
        }
        return Line.from(spans);
    }

    /** What the selected dependency is about, in words. */
    private Line detailLine(Map<String, CveAuditTab.CveCount> cves) {
        DependencyRow row = selectedRow();
        if (row == null) {
            return Line.from(Span.raw(""));
        }
        List<Span> spans = new ArrayList<>();
        spans.add(Span.styled(" " + row.gav() + ": ", Style.EMPTY.bold()));
        spans.add(Span.styled(row.explain(), statusStyle(row.status())));
        if (row.newer() != null) {
            spans.add(Span.raw(" · newer on this line: " + row.newer()));
        }
        if (row.latest() != null && !row.latest().equals(row.newer())) {
            spans.add(Span.raw(" · latest: " + row.latest() + (row.latestIsMajor() ? " (another major version)" : "")));
        }
        CveAuditTab.CveCount cve = cves != null ? cves.get(row.gav()) : null;
        if (cve != null) {
            spans.add(Span.styled(" · " + cveText(cve), Theme.error()));
        }
        return Line.from(spans);
    }

    static String cveText(CveAuditTab.CveCount cve) {
        return cve.cves() + (cve.cves() == 1 ? " CVE" : " CVEs") + " (worst " + cve.severity() + ")"
               + (cve.fix() != null ? ", fixed in " + cve.fix() : "");
    }

    private static Style statusStyle(DependencyRow.Status status) {
        return switch (status) {
            case CONFLICT, MIXED -> Theme.error();
            case OVERRIDE -> Theme.warning();
            default -> Style.EMPTY.dim();
        };
    }

    static String latestText(DependencyRow row) {
        if (row.latest() == null || row.latest().equals(row.newer())) {
            return "";
        }
        return row.latest() + (row.latestIsMajor() ? " (major)" : "");
    }

    private static String orEmpty(String s) {
        return s != null ? s : "";
    }

    @Override
    public void renderFooter(List<Span> spans) {
        if (filter.renderEditingFooter(spans)) {
            return;
        }
        hint(spans, "Esc", filter.escapeLabel());
        hint(spans, "s", "sort");
        hint(spans, "f", "scope [" + SCOPES[scopeIndex] + "]");
        if (transitiveLoading) {
            hint(spans, "t", "resolving...");
        } else {
            hint(spans, "t", transitiveMode ? "direct" : "transitive");
        }
        hint(spans, "u", onlineChecking ? "checking..." : available != null ? "check again" : "check newer + CVEs");
        if (openCveAudit != null && selectedRow() != null) {
            hint(spans, "v", "CVE Audit");
        }
        if (ctx.askAiCallback != null && !directRows.isEmpty()) {
            hint(spans, "Shift+F8", "ask AI");
        }
        filter.renderHint(spans);
    }

    private int sortRow(DependencyRow a, DependencyRow b) {
        int result = switch (sort) {
            case "version" -> orEmpty(a.version()).compareToIgnoreCase(orEmpty(b.version()));
            case "status" -> {
                int s = Integer.compare(a.status().ordinal(), b.status().ordinal());
                yield s != 0 ? s : Boolean.compare(b.outdated(true), a.outdated(true));
            }
            case "via" -> orEmpty(a.via()).compareToIgnoreCase(orEmpty(b.via()));
            default -> 0; // "artifact": below
        };
        if (result == 0) {
            result = a.ga().compareToIgnoreCase(b.ga());
        }
        if (result == 0) {
            result = orEmpty(a.version()).compareToIgnoreCase(orEmpty(b.version()));
        }
        return sortReversed ? -result : result;
    }

    private List<DependencyRow> sortedRows() {
        List<DependencyRow> sorted = new ArrayList<>(filteredRows);
        sorted.sort(this::sortRow);
        return sorted;
    }

    /** The selected dependency, in the order shown; null when none. */
    DependencyRow selectedRow() {
        Integer sel = tableState.selected();
        List<DependencyRow> sorted = sortedRows();
        return sel != null && sel >= 0 && sel < sorted.size() ? sorted.get(sel) : null;
    }

    private Map<String, CveAuditTab.CveCount> cveCounts() {
        return cveAudit != null ? cveAudit.cveCounts() : null;
    }

    private void loadDependencies() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || ctx.runner == null) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }

        ctx.backgroundExecutor.execute(() -> {
            try {
                DependencyLoader.LoadResult result = DependencyLoader.loadDependencies(info);
                applyResult(result.entries(), result.source(), result.error());
                check(info, result.entries());
            } catch (Exception e) {
                applyResult(Collections.emptyList(), null, "Error: " + e.getMessage());
            } finally {
                loading.set(false);
            }
        });
    }

    /**
     * The check that needs no network, on the background thread: what the Camel dependencies bring (resolved from the
     * local Maven repository), the JARs the integration loaded, and where its Camel version stands.
     */
    private void check(IntegrationInfo info, List<DependencyLoader.DepEntry> entries) {
        if (ctx.runner == null || entries.isEmpty()) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> checkLoading = true);
        Set<String> camelGavs = new LinkedHashSet<>();
        for (DependencyLoader.DepEntry d : entries) {
            if (d.version() != null && Versions.isCamel(d.groupId())) {
                camelGavs.add(d.display());
            }
        }
        Map<String, String> brings = Collections.emptyMap();
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.build();
            brings = DependencyCheck.camelBrings(downloader, camelGavs);
        } catch (Exception e) {
            // the CAMEL column stays empty
        }
        Map<String, Set<String>> loaded = null;
        try {
            JsonObject response = info.pid != null ? ctx.jvmInfo(info.pid, 5000) : null;
            List<String> classpath = response != null ? ClasspathTab.classpathOf(response) : List.of();
            if (!classpath.isEmpty()) {
                loaded = DependencyCheck.loadedVersions(classpath);
            }
        } catch (Exception e) {
            // not known: no conflicts
        }
        String version = info.camelVersion;
        CamelRelease release = null;
        try {
            release = CamelRelease.of(version, ProjectOverviewAssist.catalog().camelReleases(), LocalDate.now());
        } catch (Exception e) {
            // the catalog has no releases: the version only
        }
        Map<String, String> b = brings;
        Map<String, Set<String>> l = loaded;
        CamelRelease r = release;
        if (ctx.runner == null) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> {
            camelBrings = b;
            loadedVersions = l;
            camelRelease = r;
            camelVersion = version;
            checkLoading = false;
            rebuildRows();
        });
    }

    /** {@code u}: the newer versions of the dependencies from the Maven repositories, and the CVE Audit scan. */
    private void checkOnline() {
        if (onlineChecking || ctx.runner == null || directRows.isEmpty()) {
            return;
        }
        onlineChecking = true;
        onlineError = null;
        Set<String> gas = new LinkedHashSet<>();
        for (List<DependencyRow> list : List.of(directRows, transitiveRows)) {
            for (DependencyRow row : list) {
                if (!row.camel() && row.version() != null) {
                    gas.add(row.ga());
                }
            }
        }
        if (cveAudit != null) {
            cveAudit.scanInBackground();
        }
        ctx.backgroundExecutor.execute(() -> {
            Map<String, List<String>> result = Collections.emptyMap();
            AtomicReference<String> error = new AtomicReference<>();
            try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
                downloader.build();
                result = DependencyCheck.availableVersions(downloader, gas, error);
            } catch (Exception e) {
                error.compareAndSet(null, e.getMessage());
            }
            Map<String, List<String>> found = result;
            if (ctx.runner == null) {
                return;
            }
            ctx.runner.runOnRenderThread(() -> {
                Map<String, List<String>> merged = available != null ? new HashMap<>(available) : new HashMap<>();
                merged.putAll(found);
                available = merged;
                onlineError = error.get();
                onlineChecking = false;
                rebuildRows();
            });
        });
    }

    private void resolveTransitives() {
        if (allEntries.isEmpty() || ctx.runner == null) {
            return;
        }
        transitiveLoading = true;
        ctx.backgroundExecutor.execute(() -> {
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
            rebuildRows();
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
            rebuildRows();
        });
    }

    /** Shows the dependencies as the load and the checks set them, without the render thread; for tests. */
    void showForTesting(
            List<DependencyLoader.DepEntry> entries, String version, Map<String, String> brings,
            Map<String, Set<String>> loaded, Map<String, List<String>> versions, CamelRelease release) {
        allEntries = entries;
        camelVersion = version;
        camelBrings = brings;
        loadedVersions = loaded;
        available = versions;
        camelRelease = release;
        dataLoaded = true;
        rebuildRows();
    }

    /** The rows of the entries, checked with what is known so far. */
    void rebuildRows() {
        Set<String> directKeys = new HashSet<>();
        List<DependencyRow> direct = new ArrayList<>();
        for (DependencyLoader.DepEntry d : allEntries) {
            directKeys.add(d.groupId() + ":" + d.artifactId());
            direct.add(new DependencyRow(d.groupId(), d.artifactId(), d.version(), false, null));
        }
        List<DependencyRow> transitive = new ArrayList<>();
        for (DependencyLoader.DepEntry t : transitiveEntries) {
            if (!directKeys.contains(t.groupId() + ":" + t.artifactId())) {
                transitive.add(new DependencyRow(
                        t.groupId(), t.artifactId(), t.version(), true,
                        t.parent() != null ? DependencyLoader.shortArtifact(t.parent()) : null));
            }
        }
        List<DependencyRow> all = new ArrayList<>(direct);
        all.addAll(transitive);
        DependencyCheck.classify(all, camelVersion, camelBrings, loadedVersions);
        if (available != null) {
            DependencyCheck.applyAvailable(all, available);
        }
        directRows = direct;
        transitiveRows = transitive;
        refilter();
    }

    private void refilter() {
        List<DependencyRow> source = new ArrayList<>(directRows);
        if (transitiveMode) {
            source.addAll(transitiveRows);
        }
        Map<String, CveAuditTab.CveCount> cves = cveCounts();
        List<DependencyRow> result = new ArrayList<>();
        String ft = filter.term() != null ? filter.term().toLowerCase() : null;
        String scope = SCOPES[scopeIndex];
        for (DependencyRow row : source) {
            if ("camel".equals(scope) && !row.camel()) {
                continue;
            }
            if ("other".equals(scope) && row.camel()) {
                continue;
            }
            if ("outdated".equals(scope) && !row.outdated(true) && (cves == null || !cves.containsKey(row.gav()))) {
                continue;
            }
            if (ft != null && !row.matches(ft)) {
                continue;
            }
            result.add(row);
        }
        filteredRows = result;
        if (!filteredRows.isEmpty()) {
            tableState.select(0);
        }
    }

    /** Shift+F8: which dependencies to upgrade, and whether it is safe. */
    private void askAi() {
        if (ctx.askAiCallback == null || directRows.isEmpty()) {
            return;
        }
        List<DependencyRow> rows = new ArrayList<>(directRows);
        rows.addAll(transitiveRows);
        ctx.askAiCallback.ask(DependencyAiPrompt.of(
                ctx.findSelectedIntegration(), camelRelease, camelVersion, rows, cveCounts(), selectedRow(),
                available != null));
    }

    @Override
    public boolean setFilter(String term) {
        filter.setTerm(term);
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
        if (filteredRows.isEmpty()) {
            return null;
        }
        List<String> items = sortedRows().stream().map(DependencyRow::gav).toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "Maven Dependencies");
    }

    @Override
    public String description() {
        return "Maven dependency entries with the versions Camel brings, conflicts, newer versions and CVEs";
    }

    @Override
    public String getHelpText() {
        return DocHelper.loadHelpText("maven-dependencies");
    }

    @Override
    public JsonObject getTableDataAsJson() {
        if (filteredRows.isEmpty()) {
            return null;
        }
        Map<String, CveAuditTab.CveCount> cves = cveCounts();
        JsonObject result = new JsonObject();
        result.put("tab", "Maven Dependencies");
        if (camelRelease != null) {
            result.put("camel", camelRelease.toJson());
        }
        JsonArray rows = new JsonArray();
        for (DependencyRow d : sortedRows()) {
            JsonObject row = d.toJson();
            CveAuditTab.CveCount cve = cves != null ? cves.get(d.gav()) : null;
            if (cve != null) {
                row.put("cves", cve.cves());
                row.put("cveSeverity", cve.severity());
                if (cve.fix() != null) {
                    row.put("cveFix", cve.fix());
                }
            }
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", rows.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        if (dataSource != null) {
            result.put("dataSource", dataSource);
        }
        result.put("newerVersionsChecked", available != null);
        result.put("cvesChecked", cves != null);
        return result;
    }
}
