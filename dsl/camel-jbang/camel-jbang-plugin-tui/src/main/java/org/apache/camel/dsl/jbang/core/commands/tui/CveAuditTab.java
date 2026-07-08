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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.markdown.MarkdownView;
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
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class CveAuditTab extends AbstractTableTab {

    private static final List<String> SEVERITY_ORDER = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN");

    private final OsvClient osvClient = new OsvClient();
    private final AtomicBoolean loading = new AtomicBoolean(false);

    private int detailScroll;

    private List<DependencyLoader.DepEntry> depEntries = Collections.emptyList();
    private List<VulnGroup> allGroups = Collections.emptyList();
    private String lastPid;
    private String errorMessage;
    private boolean dataLoaded;
    private int scannedCount;

    CveAuditTab(MonitorContext ctx) {
        super(ctx, "severity", "id", "artifact");
    }

    @Override
    protected int getRowCount() {
        return allGroups.size();
    }

    @Override
    public void onTabSelected() {
        String pid = ctx.selectedPid;
        if (pid != null && !pid.equals(lastPid)) {
            lastPid = pid;
            allGroups = Collections.emptyList();
            dataLoaded = false;
        }
        if (!dataLoaded) {
            loadAndScan();
        }
    }

    @Override
    public void onIntegrationChanged() {
        allGroups = Collections.emptyList();
        depEntries = Collections.emptyList();
        lastPid = null;
        errorMessage = null;
        dataLoaded = false;
        detailScroll = 0;
        loading.set(false);
        if (ctx.selectedPid != null) {
            lastPid = ctx.selectedPid;
            loadAndScan();
        }
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (ke.isChar('r')) {
            rescan();
            return true;
        }
        return super.handleKeyEvent(ke);
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
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        boolean handled = super.handleMouseEvent(me, area);
        if (handled) {
            detailScroll = 0;
        }
        return handled;
    }

    @Override
    public boolean handleEscape() {
        return false;
    }

    @Override
    public void navigateUp() {
        tableState.selectPrevious();
        detailScroll = 0;
    }

    @Override
    public void navigateDown() {
        tableState.selectNext(allGroups.size());
        detailScroll = 0;
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        if (loading.get() && allGroups.isEmpty()) {
            String msg = "  Scanning dependencies...";
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(msg, Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" CVE Audit ").build())
                            .build(),
                    area);
            return;
        }

        if (errorMessage != null && allGroups.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled("  " + errorMessage, Theme.error()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" CVE Audit ").build())
                            .build(),
                    area);
            return;
        }

        if (dataLoaded && allGroups.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(
                                    Line.from(Span.raw("")),
                                    Line.from(Span.styled("  No vulnerabilities found",
                                            Theme.success())),
                                    Line.from(Span.raw("")),
                                    Line.from(Span.styled(
                                            String.format("  Scanned %d dependencies", scannedCount),
                                            Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" CVE Audit ").build())
                            .build(),
                    area);
            return;
        }

        List<Rect> chunks = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.percentage(60))
                .split(area);
        renderTable(frame, chunks.get(0));
        renderDetail(frame, chunks.get(1));
    }

    private void renderTable(Frame frame, Rect area) {
        List<VulnGroup> visible = sortedGroups();
        List<Row> rows = new ArrayList<>();
        for (VulnGroup group : visible) {
            String firstArtifact = group.affectedGavs.isEmpty() ? "" : group.affectedGavs.get(0);
            String firstParent = group.affectedGavs.isEmpty() ? null : group.gavParents.get(firstArtifact);
            int artCount = group.affectedGavs.size();
            String artDisplay = firstArtifact;
            if (firstParent != null) {
                artDisplay += " (via " + firstParent + ")";
            }
            if (artCount > 1) {
                artDisplay += " (+" + (artCount - 1) + ")";
            }

            rows.add(Row.from(
                    Cell.from(Span.styled(group.severity != null ? group.severity : "", severityStyle(group.severity))),
                    Cell.from(Span.styled(group.canonicalId != null ? group.canonicalId : "", Style.EMPTY.bold())),
                    Cell.from(Span.styled(artDisplay, Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(Span.styled(group.summary != null ? group.summary : "", Style.EMPTY.dim()))));
        }

        if (rows.isEmpty() && dataLoaded) {
            rows.add(emptyRow("No matching vulnerabilities", 4));
        }

        String title = buildTitle();

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(sortLabel("SEVERITY", "severity"), sortStyle("severity"))),
                        Cell.from(Span.styled(sortLabel("CVE ID", "id"), sortStyle("id"))),
                        Cell.from(Span.styled(sortLabel("ARTIFACT", "artifact"), sortStyle("artifact"))),
                        Cell.from(Span.styled("SUMMARY", Style.EMPTY.dim()))))
                .widths(
                        Constraint.length(10),
                        Constraint.length(20),
                        Constraint.percentage(30),
                        Constraint.fill())
                .highlightStyle(Theme.selectionBg())
                .highlightSpacing(Table.HighlightSpacing.ALWAYS)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, visible.size());
    }

    private void renderDetail(Frame frame, Rect area) {
        Integer sel = tableState.selected();
        List<VulnGroup> visible = sortedGroups();
        if (sel == null || sel < 0 || sel >= visible.size()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(Span.styled(" Select a vulnerability", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Details ").build())
                            .build(),
                    area);
            return;
        }

        VulnGroup group = visible.get(sel);
        String title = " " + group.canonicalId + " ";

        StringBuilder md = new StringBuilder();
        md.append("**Severity:** ").append(group.severity != null ? group.severity : "UNKNOWN");
        if (group.cvssVector != null) {
            md.append(" (`").append(group.cvssVector).append("`)");
        }
        md.append("  \n");
        if (group.published != null) {
            String date = group.published.length() > 10 ? group.published.substring(0, 10) : group.published;
            md.append("**Published:** ").append(date).append("  \n");
        }
        md.append("\n");
        if (group.summary != null) {
            md.append(group.summary).append("\n\n");
        }
        md.append("**Affected artifacts:**\n");
        for (String gav : group.affectedGavs) {
            String parent = group.gavParents.get(gav);
            md.append("- `").append(gav).append("`");
            if (parent != null) {
                md.append(" *(via ").append(parent).append(")*");
            }
            md.append("\n");
        }
        if (!group.fixedVersions.isEmpty()) {
            md.append("\n**Fixed in:** ").append(String.join(", ", group.fixedVersions)).append("\n");
        }
        if (!group.aliases.isEmpty()) {
            md.append("\n**Aliases:** ").append(String.join(", ", group.aliases)).append("\n");
        }
        md.append("\nhttps://osv.dev/vulnerability/").append(group.canonicalId).append("\n");
        if (group.details != null && !group.details.isEmpty()) {
            md.append("\n---\n\n").append(group.details).append("\n");
        }

        frame.renderWidget(
                MarkdownView.builder()
                        .source(md.toString())
                        .scroll(detailScroll)
                        .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(title).build())
                        .build(),
                area);
    }

    private String buildTitle() {
        StringBuilder sb = new StringBuilder(" CVE Audit (scanned: ");
        sb.append(scannedCount).append(", vulnerable: ").append(allGroups.size()).append(")");
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String sev : SEVERITY_ORDER) {
            int count = 0;
            for (VulnGroup g : allGroups) {
                if (sev.equals(g.severity)) {
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
            case "LOW" -> Style.EMPTY.dim();
            default -> Style.EMPTY.dim();
        };
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, "r", "rescan");
        super.renderFooter(spans);
        hintLast(spans, "↑↓", "navigate");
    }

    private void rescan() {
        if (loading.get()) {
            return;
        }
        osvClient.clearCache(depEntries);
        allGroups = Collections.emptyList();
        dataLoaded = false;
        errorMessage = null;
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

        ctx.runner.scheduler().execute(() -> {
            try {
                DependencyLoader.LoadResult loadResult = DependencyLoader.loadDependencies(info);
                if (loadResult.error() != null && loadResult.entries().isEmpty()) {
                    applyResult(Collections.emptyList(), Collections.emptyList(), loadResult.error());
                    return;
                }

                List<DependencyLoader.DepEntry> directDeps = loadResult.entries();
                List<DependencyLoader.DepEntry> transitiveDeps = DependencyLoader.resolveTransitives(directDeps);

                List<DependencyLoader.DepEntry> allDeps = new ArrayList<>(directDeps);
                for (DependencyLoader.DepEntry t : transitiveDeps) {
                    allDeps.add(t);
                }

                Map<String, List<OsvClient.Vulnerability>> vulnMap = osvClient.queryBatch(allDeps);

                Map<String, String> gavToParent = new HashMap<>();
                for (DependencyLoader.DepEntry dep : allDeps) {
                    if (dep.transitive() && dep.parent() != null) {
                        gavToParent.put(dep.display(), DependencyLoader.shortArtifact(dep.parent()));
                    }
                }

                List<VulnGroup> groups = buildVulnGroups(vulnMap, gavToParent);
                groups.sort(Comparator
                        .comparingInt((VulnGroup g) -> severityIndex(g.severity))
                        .thenComparing(g -> g.canonicalId));

                applyResult(allDeps, groups, null);
            } catch (Exception e) {
                applyResult(Collections.emptyList(), Collections.emptyList(), "Error: " + e.getMessage());
            } finally {
                loading.set(false);
            }
        });
    }

    private void applyResult(List<DependencyLoader.DepEntry> deps, List<VulnGroup> groups, String error) {
        if (ctx.runner == null) {
            return;
        }
        ctx.runner.runOnRenderThread(() -> {
            depEntries = deps;
            allGroups = groups;
            errorMessage = error;
            dataLoaded = true;
            scannedCount = deps.size();
            if (!allGroups.isEmpty()) {
                tableState.select(0);
            }
            detailScroll = 0;
        });
    }

    private List<VulnGroup> sortedGroups() {
        List<VulnGroup> result = new ArrayList<>(allGroups);
        result.sort((a, b) -> {
            int cmp = switch (sort) {
                case "id" -> compareStr(a.canonicalId, b.canonicalId);
                case "artifact" -> compareStr(
                        a.affectedGavs.isEmpty() ? "" : a.affectedGavs.get(0),
                        b.affectedGavs.isEmpty() ? "" : b.affectedGavs.get(0));
                default -> Integer.compare(severityIndex(a.severity), severityIndex(b.severity));
            };
            return sortReversed ? -cmp : cmp;
        });
        return result;
    }

    static List<VulnGroup> buildVulnGroups(
            Map<String, List<OsvClient.Vulnerability>> vulnMap, Map<String, String> gavToParent) {
        Map<String, String> aliasToCanonical = new HashMap<>();
        Map<String, VulnGroup> groups = new LinkedHashMap<>();

        for (Map.Entry<String, List<OsvClient.Vulnerability>> entry : vulnMap.entrySet()) {
            String gav = entry.getKey();
            for (OsvClient.Vulnerability vuln : entry.getValue()) {
                String canonicalId = resolveCanonicalId(vuln, aliasToCanonical);

                VulnGroup group = groups.get(canonicalId);
                if (group == null) {
                    group = new VulnGroup(
                            canonicalId, normalizeSeverity(vuln.severity()), vuln.cvssVector(), vuln.summary(),
                            vuln.details(), vuln.published(), new ArrayList<>(), new HashMap<>(),
                            new ArrayList<>(), new ArrayList<>());
                    groups.put(canonicalId, group);
                    aliasToCanonical.put(vuln.id(), canonicalId);
                    for (String alias : vuln.aliases()) {
                        aliasToCanonical.put(alias, canonicalId);
                    }
                }

                if (!group.affectedGavs.contains(gav)) {
                    group.affectedGavs.add(gav);
                    String parent = gavToParent.get(gav);
                    if (parent != null) {
                        group.gavParents.put(gav, parent);
                    }
                }
                for (String alias : vuln.aliases()) {
                    if (!alias.equals(canonicalId) && !group.aliases.contains(alias)) {
                        group.aliases.add(alias);
                    }
                }
                if (!vuln.id().equals(canonicalId) && !group.aliases.contains(vuln.id())) {
                    group.aliases.add(vuln.id());
                }
                if (vuln.fixedVersions() != null) {
                    for (String fv : vuln.fixedVersions()) {
                        if (!group.fixedVersions.contains(fv)) {
                            group.fixedVersions.add(fv);
                        }
                    }
                }
            }
        }
        return new ArrayList<>(groups.values());
    }

    private static String resolveCanonicalId(OsvClient.Vulnerability vuln, Map<String, String> aliasToCanonical) {
        String existing = aliasToCanonical.get(vuln.id());
        if (existing != null) {
            return existing;
        }
        for (String alias : vuln.aliases()) {
            existing = aliasToCanonical.get(alias);
            if (existing != null) {
                return existing;
            }
        }
        for (String alias : vuln.aliases()) {
            if (alias.startsWith("CVE-")) {
                return alias;
            }
        }
        return vuln.id();
    }

    static String normalizeSeverity(String severity) {
        if (severity == null) {
            return "UNKNOWN";
        }
        return switch (severity.toUpperCase()) {
            case "MODERATE" -> "MEDIUM";
            default -> severity.toUpperCase();
        };
    }

    private static int severityIndex(String severity) {
        int idx = SEVERITY_ORDER.indexOf(severity);
        return idx >= 0 ? idx : SEVERITY_ORDER.size();
    }

    @Override
    public SelectionContext getSelectionContext() {
        List<VulnGroup> visible = sortedGroups();
        if (visible.isEmpty()) {
            return null;
        }
        List<String> items = visible.stream().map(g -> g.canonicalId != null ? g.canonicalId : "").toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "CVE Audit");
    }

    @Override
    public String description() {
        return "Vulnerability scanning via OSV.dev";
    }

    @Override
    public String getHelpText() {
        return """
                # CVE Audit

                The CVE Audit tab scans the integration's classpath dependencies
                against the **OSV.dev** vulnerability database (https://osv.dev).
                OSV.dev aggregates vulnerabilities from multiple sources including
                the GitHub Advisory Database (GHSA), the National Vulnerability
                Database (NVD/CVE), and other ecosystem-specific databases.

                It queries all Maven JARs using the OSV batch API and displays
                known CVEs grouped by severity.

                ## Table Columns

                - **SEVERITY** — CRITICAL (red), HIGH (red), MEDIUM (yellow), LOW (dim)
                - **CVE ID** — the canonical vulnerability identifier (prefers CVE- over GHSA-)
                - **ARTIFACT** — the affected Maven artifact (groupId:artifactId:version)
                - **SUMMARY** — brief description of the vulnerability

                ## Detail View

                The detail panel at the bottom shows the full summary, affected
                artifacts, aliases (e.g., both CVE and GHSA IDs for the same issue),
                published date, and a link to the OSV.dev page.

                ## Caching

                Results are cached globally so re-visiting the tab is instant.
                Switching to another integration that shares the same JARs will
                also benefit from the cache. Press `r` to force a rescan.

                ## Keys

                - `Up/Down` — navigate vulnerabilities
                - `PgUp/PgDn` — scroll detail panel
                - `s` — cycle sort column
                - `S` — reverse sort order
                - `r` — rescan (clear cache and re-query)
                - `Esc` — back
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        List<VulnGroup> visible = sortedGroups();
        if (visible.isEmpty()) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "CVE Audit");
        JsonArray rows = new JsonArray();
        for (VulnGroup g : visible) {
            JsonObject row = new JsonObject();
            row.put("cveId", g.canonicalId);
            row.put("severity", g.severity);
            row.put("summary", g.summary);
            row.put("published", g.published);
            JsonArray arts = new JsonArray();
            for (String gav : g.affectedGavs) {
                arts.add(gav);
            }
            row.put("affectedArtifacts", arts);
            if (!g.fixedVersions.isEmpty()) {
                JsonArray fixed = new JsonArray();
                for (String fv : g.fixedVersions) {
                    fixed.add(fv);
                }
                row.put("fixedVersions", fixed);
            }
            JsonArray aliases = new JsonArray();
            for (String alias : g.aliases) {
                aliases.add(alias);
            }
            row.put("aliases", aliases);
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", visible.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }

    static class VulnGroup {
        final String canonicalId;
        final String severity;
        final String cvssVector;
        final String summary;
        final String details;
        final String published;
        final List<String> affectedGavs;
        final Map<String, String> gavParents;
        final List<String> aliases;
        final List<String> fixedVersions;

        VulnGroup(String canonicalId, String severity, String cvssVector, String summary, String details,
                  String published, List<String> affectedGavs, Map<String, String> gavParents,
                  List<String> aliases, List<String> fixedVersions) {
            this.canonicalId = canonicalId;
            this.severity = severity;
            this.cvssVector = cvssVector;
            this.summary = summary;
            this.details = details;
            this.published = published;
            this.affectedGavs = affectedGavs;
            this.gavParents = gavParents;
            this.aliases = aliases;
            this.fixedVersions = fixedVersions;
        }
    }
}
