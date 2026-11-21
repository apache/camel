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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.markdown.MarkdownView;
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
import org.apache.camel.catalog.cve.CveArtifact;
import org.apache.camel.catalog.cve.CveAudit;
import org.apache.camel.catalog.cve.CveFinding;
import org.apache.camel.catalog.cve.CveScan;
import org.apache.camel.catalog.cve.CvssScore;
import org.apache.camel.catalog.cve.Dependency;
import org.apache.camel.catalog.cve.OnlineAdvisories;
import org.apache.camel.catalog.cve.OsvClient;
import org.apache.camel.catalog.cve.OsvDatabase;
import org.apache.camel.catalog.cve.VulnerabilitySource;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

/**
 * The CVE Audit tab: the JARs on the classpath of the integration checked against OSV.dev and the Apache Camel security
 * advisories of the catalog. One row per CVE, or with {@code g} one row per JAR (what to upgrade), each with the
 * version that fixes it, the dependency chain that brings it in and whether the routes use it (CAMEL-27328).
 */
class CveAuditTab extends AbstractTableTab {

    private final AtomicBoolean loading = new AtomicBoolean(false);
    private final FilterField filter = new FilterField(this::filterChanged);

    private int detailScroll;
    private boolean byArtifact;

    private List<Dependency> depEntries = Collections.emptyList();
    private volatile List<CveFinding> allGroups = Collections.emptyList();
    private volatile List<CveArtifact> allRows = Collections.emptyList();
    private String lastPid;
    private volatile String errorMessage;
    // the JARs were not checked against OSV.dev (it could not be reached): only the Camel advisories were
    private volatile String osvError;
    // which offline OSV database the last scan used, such as "offline OSV database, updated 2026-11-20"; null for the API
    private volatile String sourceNote;
    private volatile boolean dataLoaded;
    private int scannedCount;

    CveAuditTab(MonitorContext ctx) {
        super(ctx, "severity", "id", "artifact", "via");
    }

    @Override
    protected int getRowCount() {
        return byArtifact ? visibleRows().size() : visibleGroups().size();
    }

    @Override
    public void onTabSelected() {
        String pid = ctx.selectedPid;
        if (pid != null && !pid.equals(lastPid)) {
            lastPid = pid;
            allGroups = Collections.emptyList();
            allRows = Collections.emptyList();
            dataLoaded = false;
        }
        if (!dataLoaded) {
            loadAndScan();
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
        if (allGroups.isEmpty()) {
            return osvError != null
                    ? osvError + " (only the Apache Camel advisories were checked)"
                    : "No known vulnerabilities found for the selected integration";
        }
        return null;
    }

    @Override
    public void onIntegrationChanged() {
        allGroups = Collections.emptyList();
        allRows = Collections.emptyList();
        depEntries = Collections.emptyList();
        lastPid = null;
        errorMessage = null;
        osvError = null;
        dataLoaded = false;
        detailScroll = 0;
        filter.reset();
        loading.set(false);
        // loaded when the tab is shown (onTabSelected): a hidden tab must not hold up the one the user looks at
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (filter.handleKey(ke)) {
            return true;
        }
        if (ke.isChar('r')) {
            rescan();
            return true;
        }
        if (ke.isChar('g')) {
            byArtifact = !byArtifact;
            tableState.select(0);
            detailScroll = 0;
            return true;
        }
        return super.handleKeyEvent(ke);
    }

    @Override
    protected boolean handleTabKeyEvent(KeyEvent ke) {
        if (ke.isKey(KeyCode.F8) && ke.hasShift()) {
            // explain with AI (CAMEL-25362): whether the selection affects the integration, and the change that fixes it
            askAi();
            return true;
        }
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

    private void askAi() {
        if (ctx.askAiCallback == null) {
            return;
        }
        if (byArtifact) {
            CveArtifact row = selectedRow();
            if (row != null) {
                ctx.askAiCallback.ask(CveAiPrompt.ofArtifact(ctx.findSelectedIntegration(), row));
            }
        } else {
            CveFinding group = selectedGroup();
            if (group != null) {
                ctx.askAiCallback.ask(CveAiPrompt.of(ctx.findSelectedIntegration(), group));
            }
        }
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
    public boolean setFilter(String term) {
        filter.setTerm(term);
        return true;
    }

    private void filterChanged() {
        tableState.select(0);
        detailScroll = 0;
    }

    @Override
    protected void rowClicked() {
        detailScroll = 0;
    }

    @Override
    protected void scrollDetail(int lines) {
        detailScroll = Math.max(0, detailScroll + lines);
    }

    @Override
    public boolean handleEscape() {
        return filter.handleEscape();
    }

    @Override
    public void navigateUp() {
        tableState.selectPrevious();
        detailScroll = 0;
    }

    @Override
    public void navigateDown() {
        tableState.selectNext(getRowCount());
        detailScroll = 0;
    }

    @Override
    protected boolean selectsFirstRow() {
        return true;
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        if (loading.get() && allGroups.isEmpty()) {
            renderMessage(frame, area, Text.from(Line.from(Span.styled("  Scanning dependencies...", Style.EMPTY.dim()))));
            return;
        }
        if (errorMessage != null && allGroups.isEmpty()) {
            renderMessage(frame, area, Text.from(Line.from(Span.styled("  " + errorMessage, Theme.error()))));
            return;
        }
        if (dataLoaded && allGroups.isEmpty()) {
            Line result = osvError != null
                    ? Line.from(Span.styled("  " + osvError, Theme.error()))
                    : Line.from(Span.styled("  No vulnerabilities found", Theme.success()));
            String scanned = osvError != null
                    ? String.format("  Only the Apache Camel advisories were checked, for %d dependencies (r to retry)",
                            scannedCount)
                    : String.format("  Scanned %d dependencies", scannedCount);
            renderMessage(frame, area, Text.from(Line.from(Span.raw("")), result, Line.from(Span.raw("")),
                    Line.from(Span.styled(scanned, Style.EMPTY.dim()))));
            return;
        }

        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.percentage(60))
                .split(area);
        if (byArtifact) {
            renderArtifactTable(frame, chunks.get(0));
        } else {
            renderCveTable(frame, chunks.get(0));
        }
        renderDetail(frame, chunks.get(1));
    }

    private static void renderMessage(Frame frame, Rect area, Text text) {
        frame.renderWidget(
                Paragraph.builder()
                        .text(text)
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                .title(" CVE Audit ").build())
                        .build(),
                area);
    }

    private void renderCveTable(Frame frame, Rect area) {
        List<CveFinding> visible = visibleGroups();
        List<Row> rows = new ArrayList<>();
        for (CveFinding group : visible) {
            String firstArtifact = group.firstGav();
            String artDisplay = firstArtifact != null ? firstArtifact : "";
            if (group.affectedGavs().size() > 1) {
                artDisplay += " (+" + (group.affectedGavs().size() - 1) + ")";
            }
            String fix = firstArtifact != null ? group.fixFor().get(firstArtifact) : null;
            rows.add(Row.from(
                    Cell.from(Span.styled(group.severity() != null ? group.severity() : "", severityStyle(group.severity()))),
                    Cell.from(Span.styled(CvssScore.format(group.score()), severityStyle(group.severity()))),
                    Cell.from(Span.styled(group.canonicalId() != null ? group.canonicalId() : "", Style.EMPTY.bold())),
                    Cell.from(Span.styled(artDisplay, Style.EMPTY.fg(Theme.accent()))),
                    fixCell(fix),
                    Cell.from(Span.styled(group.firstVia(), Style.EMPTY.dim())),
                    routesCell(group.usedByRoutes()),
                    Cell.from(Span.styled(group.summary() != null ? group.summary() : "", Style.EMPTY.dim()))));
        }
        if (rows.isEmpty() && dataLoaded) {
            rows.add(emptyRow("No matching vulnerabilities", 8, 7));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("SEVERITY", "severity"), sortStyle("severity"))),
                        Cell.from(Span.styled("SCORE", Style.EMPTY.dim())),
                        Cell.from(Span.styled(sortLabel("CVE ID", "id"), sortStyle("id"))),
                        Cell.from(Span.styled(sortLabel("ARTIFACT", "artifact"), sortStyle("artifact"))),
                        Cell.from(Span.styled("FIX", Style.EMPTY.dim())),
                        Cell.from(Span.styled(sortLabel("VIA", "via"), sortStyle("via"))),
                        Cell.from(Span.styled("USED", Style.EMPTY.dim())),
                        Cell.from(Span.styled("SUMMARY", Style.EMPTY.dim()))))
                .widths(
                        Constraint.length(9),
                        Constraint.length(5),
                        Constraint.length(16),
                        Constraint.percentage(24),
                        Constraint.length(12),
                        Constraint.percentage(16),
                        Constraint.length(6),
                        Constraint.fill())
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(buildTitle()).build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, table, visible.size());
    }

    private void renderArtifactTable(Frame frame, Rect area) {
        List<CveArtifact> visible = visibleRows();
        List<Row> rows = new ArrayList<>();
        for (CveArtifact row : visible) {
            rows.add(Row.from(
                    Cell.from(Span.styled(row.severity(), severityStyle(row.severity()))),
                    Cell.from(Span.styled(CvssScore.format(row.score()), severityStyle(row.severity()))),
                    Cell.from(Span.styled(row.gav(), Style.EMPTY.fg(Theme.accent()))),
                    Cell.from(Span.styled(String.valueOf(row.cves().size()), Style.EMPTY.bold())),
                    fixCell(row.camelRelease() && row.fix() != null ? "Camel " + row.fix() : row.fix()),
                    Cell.from(Span.styled(row.via() != null ? row.via() : "", Style.EMPTY.dim())),
                    routesCell(row.usage() != null ? row.usage().used() : null)));
        }
        if (rows.isEmpty() && dataLoaded) {
            rows.add(emptyRow("No matching artifacts", 7, 2));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("SEVERITY", "severity"), sortStyle("severity"))),
                        Cell.from(Span.styled("SCORE", Style.EMPTY.dim())),
                        Cell.from(Span.styled(sortLabel("ARTIFACT", "artifact"), sortStyle("artifact"))),
                        Cell.from(Span.styled(sortLabel("CVES", "id"), sortStyle("id"))),
                        Cell.from(Span.styled("FIXES ALL", Style.EMPTY.dim())),
                        Cell.from(Span.styled(sortLabel("VIA", "via"), sortStyle("via"))),
                        Cell.from(Span.styled("USED", Style.EMPTY.dim()))))
                .widths(
                        Constraint.length(9),
                        Constraint.length(5),
                        Constraint.percentage(35),
                        Constraint.length(6),
                        Constraint.length(16),
                        Constraint.fill(),
                        Constraint.length(6))
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(buildTitle()).build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, table, visible.size());
    }

    private static Cell fixCell(String fix) {
        return fix != null
                ? Cell.from(Span.styled(fix, Theme.success()))
                : Cell.from(Span.styled("none", Style.EMPTY.dim()));
    }

    private static Cell routesCell(Boolean used) {
        if (used == null) {
            return Cell.from(Span.raw(""));
        }
        return used
                ? Cell.from(Span.styled("yes", Theme.warning().bold()))
                : Cell.from(Span.styled("no", Style.EMPTY.dim()));
    }

    private void renderDetail(Frame frame, Rect area) {
        lastDetailArea = area;
        String title = null;
        String md = null;
        if (byArtifact) {
            CveArtifact row = selectedRow();
            if (row != null) {
                title = " " + row.gav() + " ";
                md = CveDetail.artifact(row);
            }
        } else {
            CveFinding group = selectedGroup();
            if (group != null) {
                title = " " + group.canonicalId() + " ";
                md = CveDetail.cve(group);
            }
        }
        if (md == null) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(" Select a row", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Details ").build())
                            .build(),
                    area);
            return;
        }
        frame.renderWidget(
                MarkdownView.builder()
                        .source(md)
                        .scroll(detailScroll)
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                        .styles(Theme.markdownStyles())
                        .syntaxTheme(Theme.syntaxTheme())
                        .build(),
                area);
    }

    private String buildTitle() {
        StringBuilder sb = new StringBuilder(" CVE Audit (scanned: ");
        sb.append(scannedCount).append(", vulnerable: ").append(allGroups.size());
        if (byArtifact) {
            sb.append(", artifacts: ").append(allRows.size());
        }
        sb.append(")");
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String sev : CveScan.SEVERITY_ORDER) {
            int count = 0;
            for (CveFinding g : allGroups) {
                if (sev.equals(g.severity())) {
                    count++;
                }
            }
            if (count > 0) {
                counts.put(sev, count);
            }
        }
        if (!counts.isEmpty()) {
            sb.append(" [");
            boolean first = true;
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(e.getValue()).append(" ").append(e.getKey());
                first = false;
            }
            sb.append("]");
        }
        if (filter.term() != null) {
            sb.append(" filter: ").append(filter.term());
        }
        if (osvError != null) {
            sb.append(" - OSV.dev not checked");
        } else if (sourceNote != null) {
            sb.append(" - ").append(sourceNote);
        }
        sb.append(" ");
        return sb.toString();
    }

    static Style severityStyle(String severity) {
        if (severity == null) {
            return Style.EMPTY.dim();
        }
        return switch (severity) {
            case "CRITICAL" -> Theme.error().bold();
            case "HIGH" -> Theme.error();
            case "MEDIUM" -> Theme.warning();
            default -> Style.EMPTY.dim();
        };
    }

    @Override
    public void renderFooter(List<Span> spans) {
        if (filter.renderEditingFooter(spans)) {
            return;
        }
        hint(spans, "Esc", filter.escapeLabel());
        hint(spans, "g", byArtifact ? "by CVE" : "by artifact");
        filter.renderHint(spans);
        hint(spans, "r", "rescan");
        hintLast(spans, "s", "sort");
    }

    @Override
    public void renderFKeyHints(List<Span> spans) {
        // with the global F-keys, as the AI key of a screen is an F-key of every screen that has one
        if (ctx.askAiCallback != null && (byArtifact ? selectedRow() != null : selectedGroup() != null)) {
            hint(spans, "Shift+F8", "explain with AI");
        }
    }

    /** The vulnerability selected in the table, in the order it is shown; null when none is, or it is by artifact. */
    CveFinding selectedGroup() {
        if (byArtifact) {
            return null;
        }
        List<CveFinding> visible = visibleGroups();
        Integer sel = tableState.selected();
        return sel != null && sel >= 0 && sel < visible.size() ? visible.get(sel) : null;
    }

    /** The artifact selected in the table grouped by artifact; null when none is, or it is by CVE. */
    CveArtifact selectedRow() {
        if (!byArtifact) {
            return null;
        }
        List<CveArtifact> visible = visibleRows();
        Integer sel = tableState.selected();
        return sel != null && sel >= 0 && sel < visible.size() ? visible.get(sel) : null;
    }

    private void rescan() {
        if (loading.get()) {
            return;
        }
        OsvClient.clearCache(depEntries);
        allGroups = Collections.emptyList();
        allRows = Collections.emptyList();
        dataLoaded = false;
        errorMessage = null;
        osvError = null;
        loadAndScan();
    }

    private void loadAndScan() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || ctx.runner == null) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }

        ctx.backgroundExecutor.execute(() -> {
            try {
                scan(info);
            } catch (Exception e) {
                applyResult(Collections.emptyList(), Collections.emptyList(), "Error: " + e.getMessage(), null);
            } finally {
                loading.set(false);
            }
        });
    }

    /** The scan, on the background thread: the classpath, OSV.dev, the Camel advisories, the chains, the routes. */
    private void scan(IntegrationInfo info) {
        // use actual JVM classpath as primary source (correct versions)
        Map<String, Path> jars = new HashMap<>();
        List<DependencyLoader.DepEntry> allDeps = fetchClasspathDeps(jars);
        Map<String, String> fallbackVia = new HashMap<>();
        if (allDeps.isEmpty()) {
            // fallback to pom.xml/run.properties + Maven transitive resolution
            DependencyLoader.LoadResult loadResult = DependencyLoader.loadDependencies(info);
            if (loadResult.error() != null && loadResult.entries().isEmpty()) {
                applyResult(Collections.emptyList(), Collections.emptyList(), loadResult.error(), null);
                return;
            }
            allDeps = new ArrayList<>(loadResult.entries());
            for (DependencyLoader.DepEntry t : DependencyLoader.resolveTransitives(loadResult.entries())) {
                allDeps.add(t);
                if (t.parent() != null) {
                    fallbackVia.put(t.groupId() + ":" + t.artifactId(), DependencyLoader.shortArtifact(t.parent()));
                }
            }
        }

        IntegrationInfo current = ctx.findSelectedIntegration();
        List<Dependency> deps = allDeps.stream()
                .map(d -> new Dependency(d.groupId(), d.artifactId(), d.version())).toList();
        // the source and the proxy are looked up for each scan, so a change in the settings applies without a restart
        VulnerabilitySource source = TuiSettings.vulnerabilitySource();
        OnlineAdvisories.Result advisories = TuiSettings.camelAdvisories(ProjectOverviewAssist.catalog());
        sourceNote = sourceNote(source instanceof OsvDatabase db ? databaseNote(db) : null, advisories);
        CveAudit.Result result = CveAudit.audit(deps, jars, declaredDependencies(info), fallbackVia,
                endpointComponents(current != null ? current : info), ProjectOverviewAssist.catalog(),
                advisories.advisories(), source);
        applyResult(deps, result.findings(), null, result.osvError());
    }

    // the note of the title: the offline OSV database, and the Camel advisories when they could not be checked online
    static String sourceNote(String databaseNote, OnlineAdvisories.Result advisories) {
        String camel = advisories.warning() != null
                ? (advisories.online() > 0
                        ? "Camel advisories of " + advisories.updated().toString().substring(0, 10)
                        : "Camel advisories of the catalog")
                : null;
        if (databaseNote == null) {
            return camel;
        }
        return camel == null ? databaseNote : databaseNote + ", " + camel;
    }

    static String databaseNote(OsvDatabase db) {
        if (db.updated() == null) {
            return null;
        }
        return "offline OSV database, updated " + db.updated().toString().substring(0, 10)
               + (db.warning() != null ? " (the download failed)" : "");
    }

    /** The {@code groupId:artifactId} of the dependencies the project declares. */
    private static Set<String> declaredDependencies(IntegrationInfo info) {
        Set<String> declared = new HashSet<>();
        try {
            for (DependencyLoader.DepEntry d : DependencyLoader.loadDependencies(info).entries()) {
                declared.add(d.groupId() + ":" + d.artifactId());
            }
        } catch (Exception e) {
            // best effort: the chains then start at the JARs no other declares
        }
        return declared;
    }

    private static Set<String> endpointComponents(IntegrationInfo info) {
        Set<String> result = new HashSet<>();
        try {
            for (EndpointInfo ep : new ArrayList<>(info.endpoints)) {
                if (ep.component != null) {
                    result.add(ep.component.toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception e) {
            // the endpoints changed meanwhile: best effort
        }
        return result;
    }

    private List<DependencyLoader.DepEntry> fetchClasspathDeps(Map<String, Path> jars) {
        try {
            String pid = ctx.selectedPid;
            if (pid == null) {
                return Collections.emptyList();
            }
            JsonObject response = ctx.jvmInfo(pid, 5000);
            if (response == null) {
                return Collections.emptyList();
            }
            List<String> classpath = ClasspathTab.classpathOf(response);
            if (classpath.isEmpty()) {
                return Collections.emptyList();
            }
            List<DependencyLoader.DepEntry> deps = new ArrayList<>();
            for (String item : classpath) {
                ClasspathTab.JarEntry entry = ClasspathTab.parseJarEntry(String.valueOf(item));
                if (entry.groupId() == null) {
                    continue;
                }
                jars.putIfAbsent(entry.groupId() + ":" + entry.artifactId(), Path.of(entry.fullPath()));
                if (entry.version() != null && !DependencyLoader.skipArtifact(entry.groupId(), entry.artifactId())) {
                    deps.add(new DependencyLoader.DepEntry(entry.groupId(), entry.artifactId(), entry.version()));
                }
            }
            deps.sort(Comparator.comparing(DependencyLoader.DepEntry::display, String.CASE_INSENSITIVE_ORDER));
            return deps;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private void applyResult(
            List<Dependency> deps, List<CveFinding> groups, String error, String osvFailure) {
        if (ctx.runner == null) {
            return;
        }
        List<CveArtifact> rows = CveScan.artifactRows(groups);
        ctx.runner.runOnRenderThread(() -> {
            depEntries = deps;
            allGroups = groups;
            allRows = rows;
            errorMessage = error;
            osvError = osvFailure;
            dataLoaded = true;
            scannedCount = deps.size();
            if (!allGroups.isEmpty()) {
                tableState.select(0);
            }
            detailScroll = 0;
        });
    }

    /** Shows the vulnerabilities as a scan sets them, without the render thread; for tests. */
    void showForTesting(List<CveFinding> groups) {
        allGroups = groups;
        allRows = CveScan.artifactRows(groups);
        dataLoaded = true;
        if (!groups.isEmpty()) {
            tableState.select(0);
        }
    }

    /** The vulnerabilities the filter lets through, in the order of the sort. */
    List<CveFinding> visibleGroups() {
        String term = filter.term() != null ? filter.term().toLowerCase(Locale.ROOT) : null;
        List<CveFinding> result = new ArrayList<>();
        for (CveFinding g : allGroups) {
            if (term == null || g.matches(term)) {
                result.add(g);
            }
        }
        result.sort((a, b) -> {
            int cmp = switch (sort) {
                case "id" -> compareStr(a.canonicalId(), b.canonicalId());
                case "artifact" -> compareStr(a.firstGav(), b.firstGav());
                case "via" -> compareStr(a.firstVia(), b.firstVia());
                default -> compareSeverity(a.severity(), a.score(), b.severity(), b.score());
            };
            return sortReversed ? -cmp : cmp;
        });
        return result;
    }

    /** The artifacts the filter lets through, in the order of the sort (by CVE count for the id column). */
    List<CveArtifact> visibleRows() {
        String term = filter.term() != null ? filter.term().toLowerCase(Locale.ROOT) : null;
        List<CveArtifact> result = new ArrayList<>();
        for (CveArtifact r : allRows) {
            if (term == null || r.matches(term)) {
                result.add(r);
            }
        }
        result.sort((a, b) -> {
            int cmp = switch (sort) {
                case "id" -> Integer.compare(b.cves().size(), a.cves().size());
                case "artifact" -> compareStr(a.gav(), b.gav());
                case "via" -> compareStr(a.via() != null ? a.via() : "", b.via() != null ? b.via() : "");
                default -> compareSeverity(a.severity(), a.score(), b.severity(), b.score());
            };
            return sortReversed ? -cmp : cmp;
        });
        return result;
    }

    /** The worst first: by severity, then by score. */
    private static int compareSeverity(String sevA, Double scoreA, String sevB, Double scoreB) {
        int cmp = Integer.compare(CveScan.severityIndex(sevA), CveScan.severityIndex(sevB));
        if (cmp != 0) {
            return cmp;
        }
        return Double.compare(scoreB != null ? scoreB : -1, scoreA != null ? scoreA : -1);
    }

    @Override
    public SelectionContext getSelectionContext() {
        List<String> items = byArtifact
                ? visibleRows().stream().map(r -> r.gav()).toList()
                : visibleGroups().stream().map(g -> g.canonicalId() != null ? g.canonicalId() : "").toList();
        if (items.isEmpty()) {
            return null;
        }
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "CVE Audit");
    }

    @Override
    public String description() {
        return "Vulnerability scanning via OSV.dev and the Apache Camel advisories";
    }

    @Override
    public String getHelpText() {
        return DocHelper.loadHelpText("cve-audit");
    }

    @Override
    public JsonObject getTableDataAsJson() {
        if (allGroups.isEmpty()) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "CVE Audit");
        result.put("view", byArtifact ? "artifact" : "cve");
        if (osvError != null) {
            result.put("osvError", osvError);
        }
        if (filter.term() != null) {
            result.put("filter", filter.term());
        }
        JsonArray rows = new JsonArray();
        int total;
        if (byArtifact) {
            List<CveArtifact> visible = visibleRows();
            visible.forEach(r -> rows.add(r.toJson()));
            total = visible.size();
        } else {
            List<CveFinding> visible = visibleGroups();
            visible.forEach(g -> rows.add(g.toJson()));
            total = visible.size();
        }
        result.put("rows", rows);
        result.put("totalRows", total);
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
