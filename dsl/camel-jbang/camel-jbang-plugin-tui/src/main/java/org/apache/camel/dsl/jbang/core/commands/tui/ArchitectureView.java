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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyEdgeInfo;
import org.apache.camel.diagram.TopologyLayoutEngine.TopologyNodeInfo;
import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectCapabilities;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectCapabilities.Capabilities;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectCapabilities.Group;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectCapabilities.GroupLink;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.TopologyDiagramWidget.NodeLine;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;

/**
 * The architecture level of the Diagram tab (CAMEL-25147): the project's routes grouped by what they do for the
 * business, a level above the route topology, so a project with many routes stays readable.
 * <p/>
 * It shows one box per group: the route groups of the source, the capabilities the AI project overview proposed, shared
 * services (routes several groups use), Other, and Utility (plumbing, hidden until {@code u}). Each box shows its
 * facts: routes, entry points, external systems, warnings; {@code e} draws the systems as boxes of their own, linked to
 * the groups that use them. Enter goes down to the topology of all routes with the group's routes highlighted, so the
 * whole picture of how routes connect is never lost. What an AI decided is drawn in the ai-assisted style with the
 * mark, so it is never taken for what the source says.
 * <p/>
 * It reads the source files of the selected integration, not the running routes, so it works for any project the TUI
 * can see the sources of.
 */
final class ArchitectureView {

    private static final int INFO_PANEL_WIDTH = 34;
    private static final long RECHECK_MS = 3000;
    private static volatile CamelCatalog catalog;

    private final MonitorContext ctx;
    private final DiagramSupport support = new DiagramSupport();
    /** Goes down to the topology with a group's routes highlighted. */
    private final Consumer<Group> openGroup;
    private final BooleanSupplier labels;

    private boolean active;
    private boolean suspended;
    private volatile boolean loading;
    private String error;
    private Path dir;
    private ProjectOverview.Overview overview;
    /** The groups shown: with the AI's capabilities, or from the facts alone when its hints are not shown. */
    private Capabilities capabilities;
    private Capabilities aiCapabilities;
    private Capabilities factCapabilities;
    private boolean showUtility;
    /** Whether the external systems are drawn as boxes linked to the groups that use them (e). */
    private boolean showExternal;
    /** The kinds of an external system box when it is selected: messages come in from it, or go out to it. */
    private static final String SYSTEM_IN = "system-in";
    private static final String SYSTEM_OUT = "system-out";
    private volatile boolean checking;
    private long lastCheck;
    private long summaryModified;
    private String lastGroup;

    ArchitectureView(MonitorContext ctx, Consumer<Group> openGroup) {
        this(ctx, openGroup, () -> true);
    }

    /**
     * @param labels whether the boxes read as business (what a group achieves) or technical (entry points and systems):
     *               the Diagram tab's view setting
     */
    ArchitectureView(MonitorContext ctx, Consumer<Group> openGroup, BooleanSupplier labels) {
        this.ctx = ctx;
        this.openGroup = openGroup;
        this.labels = labels;
    }

    /** Draws the groups again, after the view setting changed. */
    void refresh() {
        pick();
        if (capabilities != null && error == null) {
            showMap(support.getSelectedRouteId());
        }
    }

    boolean isActive() {
        return active && !suspended;
    }

    /** Whether the view was left for the topology of a group, and Esc there comes back to it. */
    boolean isSuspended() {
        return active && suspended;
    }

    void resume() {
        suspended = false;
        // settings shared with the topology (utility) may have changed while it was shown
        refresh();
    }

    void close() {
        active = false;
        suspended = false;
        lastGroup = null;
    }

    /** Opens the view over a project directory and reads it in the background. */
    void open(Path directory) {
        active = true;
        suspended = false;
        dir = directory;
        error = null;
        if (directory == null) {
            error = "No source directory for the selected integration.";
            return;
        }
        reload(false);
    }

    /**
     * Reads the project in the background. A quiet read (the regular check for changes) shows no "Reading" message and
     * changes nothing unless the routes or the summary changed since the last read.
     */
    private void reload(boolean quiet) {
        if (!quiet) {
            loading = true;
        }
        Path d = dir;
        Thread t = new Thread(() -> {
            try {
                long modified = summaryModified(d);
                ProjectOverview.Overview o = ProjectOverview.analyze(d, catalog());
                if (quiet && overview != null && o.fingerprint().equals(overview.fingerprint())
                        && modified == summaryModified) {
                    return;
                }
                IntegrationSummary.Summary summary = IntegrationSummary.read(d);
                Capabilities caps = ProjectCapabilities.build(o, summary != null ? summary.ai() : null);
                Capabilities facts = ProjectCapabilities.build(o, null);
                onRenderThread(() -> {
                    String selected = support.getSelectedRouteId();
                    overview = o;
                    aiCapabilities = caps;
                    factCapabilities = facts;
                    pick();
                    summaryModified = modified;
                    loading = false;
                    error = o.flows().isEmpty() ? "No routes found in " + d : null;
                    if (error == null) {
                        showMap(quiet && selected != null ? selected : lastGroup);
                    }
                });
            } catch (Exception e) {
                if (!quiet) {
                    onRenderThread(() -> {
                        loading = false;
                        error = "Cannot read the project: " + e.getMessage();
                    });
                }
            } finally {
                checking = false;
            }
        }, "tui-capabilities");
        t.setDaemon(true);
        t.start();
    }

    /**
     * While the view is shown, reads the project again every few seconds so it follows edited routes (dev mode) and a
     * summary /overview has just written, without a reload key.
     */
    void checkForChanges() {
        long now = System.currentTimeMillis();
        if (!isActive() || loading || checking || dir == null || now - lastCheck < RECHECK_MS) {
            return;
        }
        lastCheck = now;
        checking = true;
        reload(true);
    }

    /** The groups to show: the AI's while its hints are shown (setting on, ai view on), else the facts alone. */
    private void pick() {
        capabilities = IntegrationSummaryHints.enabled() ? aiCapabilities : factCapabilities;
    }

    private static long summaryModified(Path d) {
        try {
            Path f = d.resolve(IntegrationSummary.FILE_NAME);
            return Files.isRegularFile(f) ? Files.getLastModifiedTime(f).toMillis() : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    private void onRenderThread(Runnable r) {
        if (ctx != null && ctx.runner != null) {
            ctx.runner.runOnRenderThread(r);
        } else {
            r.run();
        }
    }

    static CamelCatalog catalog() {
        CamelCatalog c = catalog;
        if (c == null) {
            c = new DefaultCamelCatalog();
            catalog = c;
        }
        return c;
    }

    // ---- the groups ----

    private List<Group> visibleGroups() {
        return capabilities.groups().stream()
                .filter(g -> showUtility || !ProjectCapabilities.UTILITY.equals(g.kind())).toList();
    }

    private void showMap(String select) {
        List<TopologyNodeInfo> nodes = new ArrayList<>();
        Map<String, List<NodeLine>> lines = new LinkedHashMap<>();
        Set<String> shown = new LinkedHashSet<>();
        for (Group g : visibleGroups()) {
            nodes.add(node(g.id()));
            lines.put(g.id(), groupLines(g, labels.getAsBoolean(), !showExternal));
            shown.add(g.id());
        }
        List<TopologyEdgeInfo> edges = new ArrayList<>();
        for (GroupLink l : capabilities.links()) {
            if (shown.contains(l.from()) && shown.contains(l.to())) {
                edges.add(edge(l.from(), l.to(), l.kind()));
            }
        }
        if (showExternal) {
            addSystems(nodes, edges, lines, shown);
        }
        // each group in the colour its routes have in the topology
        Map<String, Color> colors = new LinkedHashMap<>();
        List<Group> all = capabilities.groups();
        for (int i = 0; i < all.size(); i++) {
            if (shown.contains(all.get(i).id())) {
                colors.put(all.get(i).id(), RouteGroups.colorOf(i));
            }
        }
        support.setGroups(Map.of(), colors);
        List<Group> visible = visibleGroups();
        support.showCustomTopology(nodes, edges, lines,
                select != null || visible.isEmpty() ? select : visible.get(0).id());
    }

    /**
     * The external systems as boxes: one where messages come in from (above the groups) and one they go out to (below),
     * linked to each group that uses it, as the topology's external edges show them for routes.
     */
    private void addSystems(
            List<TopologyNodeInfo> nodes, List<TopologyEdgeInfo> edges, Map<String, List<NodeLine>> lines,
            Set<String> shown) {
        Set<String> added = new LinkedHashSet<>();
        Set<String> linked = new LinkedHashSet<>();
        for (ProjectOverview.SystemUse use : overview.systems()) {
            String group = capabilities.groupOf().get(use.route());
            if (group == null || !shown.contains(group)) {
                continue;
            }
            boolean in = "in".equals(use.direction());
            String id = systemId(use.name(), in);
            if (added.add(id)) {
                TopologyNodeInfo n = node(id);
                n.from = use.name();
                n.nodeType = in ? "external-in" : "external-out";
                nodes.add(n);
            }
            if (linked.add(id + ">" + group)) {
                edges.add(in ? edge(id, group, "external") : edge(group, id, "external"));
            }
        }
    }

    static String systemId(String name, boolean in) {
        return (in ? "ext-in:" : "ext-out:") + name;
    }

    private static TopologyNodeInfo node(String id) {
        TopologyNodeInfo n = new TopologyNodeInfo();
        n.routeId = id;
        n.from = "";
        n.nodeType = "route";
        return n;
    }

    private static TopologyEdgeInfo edge(String from, String to, String kind) {
        TopologyEdgeInfo e = new TopologyEdgeInfo();
        e.fromRouteId = from;
        e.toRouteId = to;
        e.endpoint = kind;
        e.connectionType = "internal";
        return e;
    }

    /**
     * A group's box: its name and size, then what it achieves (business) or its entry points and systems (technical;
     * without the systems when they are drawn as boxes of their own).
     */
    private static List<NodeLine> groupLines(Group g, boolean labels, boolean systems) {
        List<NodeLine> lines = new ArrayList<>();
        String name = g.ai() ? IntegrationSummaryHints.MARK + g.name() : g.name();
        lines.add(new NodeLine(name, (g.ai() ? Theme.aiAssisted() : Theme.label()).bold()));
        String count = g.routes().size() + (g.routes().size() == 1 ? " route" : " routes");
        if (g.warnings() > 0) {
            lines.add(new NodeLine(count + " · " + g.warnings() + " warn", Theme.warning()));
        } else {
            lines.add(new NodeLine(count, Theme.muted()));
        }
        if (labels && g.text() != null && !g.text().isBlank()) {
            // business: what the group achieves, in two lines at most
            List<String> text = DiagramDetailSupport.wrapWords(IntegrationSummaryHints.MARK + g.text().strip(), 40);
            lines.add(new NodeLine(text.get(0), Theme.aiAssisted()));
            if (text.size() > 1) {
                lines.add(new NodeLine(text.get(1) + (text.size() > 2 ? "..." : ""), Theme.aiAssisted()));
            }
            return lines;
        }
        if (!g.entryPoints().isEmpty()) {
            lines.add(new NodeLine(String.join(", ", g.entryPoints()), Style.EMPTY.fg(Theme.accent())));
        }
        if (systems && !g.systems().isEmpty()) {
            lines.add(new NodeLine(String.join(" · ", g.systems()), Theme.info()));
        }
        return lines;
    }

    // ---- for the Diagram tab ----

    /** Whether the utility routes are shown; the topology follows the same setting. */
    boolean isShowUtility() {
        return showUtility;
    }

    boolean isShowExternal() {
        return showExternal;
    }

    void setShowUtility(boolean show) {
        showUtility = show;
        refresh();
    }

    /** The selected group, or null. */
    Group selectedGroup() {
        String selected = support.getSelectedRouteId();
        if (selected == null || capabilities == null) {
            return null;
        }
        return selected.startsWith("ext-") ? systemGroup(selected) : capabilities.group(selected);
    }

    /**
     * An external system box as a group of the routes that use it, for the Info panel and to go down to the topology
     * with those routes highlighted.
     */
    private Group systemGroup(String id) {
        boolean in = id.startsWith("ext-in:");
        String name = id.substring(id.indexOf(':') + 1);
        Set<String> shown = new LinkedHashSet<>();
        visibleGroups().forEach(g -> shown.add(g.id()));
        List<String> routes = new ArrayList<>();
        Set<String> uris = new LinkedHashSet<>();
        for (ProjectOverview.SystemUse use : overview.systems()) {
            String group = capabilities.groupOf().get(use.route());
            if (name.equals(use.name()) && in == "in".equals(use.direction()) && shown.contains(group)) {
                if (!routes.contains(use.route())) {
                    routes.add(use.route());
                }
                // each endpoint with the route that uses it
                uris.add(use.uri() + " \u2192 " + use.route());
            }
        }
        return new Group(
                id, name, in ? SYSTEM_IN : SYSTEM_OUT, false, null, routes, Set.of(), List.copyOf(uris),
                List.of(), 0);
    }

    /** Goes down to the topology with the selected group's routes highlighted; false when nothing is selected. */
    boolean openSelectedGroup() {
        Group g = selectedGroup();
        if (g == null) {
            return false;
        }
        lastGroup = g.id();
        suspended = true;
        openGroup.accept(g);
        return true;
    }

    // ---- keys ----

    boolean handleKeyEvent(KeyEvent ke) {
        if (!isActive() || loading || capabilities == null || error != null) {
            return false;
        }
        if (ke.isUp()) {
            support.selectNodeUp();
            support.scrollToSelectedNode();
            return true;
        }
        if (ke.isDown()) {
            support.selectNodeDown();
            support.scrollToSelectedNode();
            return true;
        }
        if (ke.isLeft()) {
            support.selectNodeLeft();
            support.scrollToSelectedNode();
            return true;
        }
        if (ke.isRight()) {
            support.selectNodeRight();
            support.scrollToSelectedNode();
            return true;
        }
        if (ke.isHome()) {
            support.selectFirstNode();
            support.scrollToSelectedNode();
            return true;
        }
        if (ke.isEnd()) {
            support.selectLastNode();
            support.scrollToSelectedNode();
            return true;
        }
        if (support.handleScrollKeys(ke)) {
            return true;
        }
        if (ke.isCharIgnoreCase('u')) {
            showUtility = !showUtility;
            showMap(support.getSelectedRouteId());
            return true;
        }
        if (ke.isCharIgnoreCase('e')) {
            showExternal = !showExternal;
            showMap(support.getSelectedRouteId());
            return true;
        }
        if (ke.isConfirm()) {
            openSelectedGroup();
            return true;
        }
        return false;
    }

    /** The groups are the top level: Esc is the tab's (back to the Overview tab) and the view stays where it is. */
    boolean handleEscape() {
        return false;
    }

    // ---- rendering ----

    void render(Frame frame, Rect area, String integrationName) {
        checkForChanges();
        // the AI's groups come and go with its hints (the ai view setting, the AI overview setting)
        Capabilities want = IntegrationSummaryHints.enabled() ? aiCapabilities : factCapabilities;
        if (want != null && want != capabilities && !loading && error == null) {
            String selected = support.getSelectedRouteId();
            capabilities = want;
            showMap(selected);
        }
        if (loading || error != null || capabilities == null) {
            String text = error != null ? error : "Reading the project...";
            frame.renderWidget(Paragraph.builder()
                    .text(Text.from(Line.from(Span.styled(" " + text, error != null ? Theme.warning() : Theme.muted()))))
                    .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                            .title(" Architecture ").build())
                    .build(), area);
            return;
        }
        Line title = title(integrationName);
        Group selected = selectedGroup();
        if (selected != null && area.width() > 70) {
            List<Rect> chunks = Layout.horizontal()
                    .constraints(Constraint.length(INFO_PANEL_WIDTH), Constraint.fill())
                    .split(area);
            renderInfo(frame, chunks.get(0), selected);
            support.renderNativeDiagram(frame, chunks.get(1), title, false);
            renderPreview(frame, chunks.get(1), selected);
        } else {
            support.renderNativeDiagram(frame, area, title, false);
            renderPreview(frame, area, selected);
        }
    }

    /**
     * The mini panel at the bottom right, as the topology has for a route: the inside of the selected group, what Enter
     * goes down to. None for an external system, whose Info panel already lists its routes.
     */
    private void renderPreview(Frame frame, Rect area, Group g) {
        if (g == null || SYSTEM_IN.equals(g.kind()) || SYSTEM_OUT.equals(g.kind()) || g.routes().isEmpty()) {
            return;
        }
        int w = Math.min(48, area.width() / 3);
        int h = Math.min(14, area.height() / 2);
        if (w < 20 || h < 5 || area.width() < 60 || area.height() < 14) {
            return;
        }
        List<Line> lines = GroupPreview.lines(g, capabilities, overview, w - 2, h - 2, r -> RouteKeys.display(dir, r),
                scheme -> ProjectOverview.isRemote(scheme, catalog()));
        h = Math.min(h, lines.size() + 2);
        Rect rect = new Rect(area.x() + area.width() - w - 2, area.y() + area.height() - h - 1, w, h);
        frame.renderWidget(Clear.INSTANCE, rect);
        // how messages flow through the group, as the summary's flows between routes: the group's name is on its box
        Block block = Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(Title.from(Line.from(Span.styled(" Flow ", Style.EMPTY.dim()))))
                .build();
        frame.renderWidget(block, rect);
        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).build(), block.inner(rect));
    }

    private Line title(String integrationName) {
        List<Span> spans = new ArrayList<>();
        spans.add(Span.raw(" Architecture"));
        if (integrationName != null) {
            spans.add(Span.raw(" ["));
            spans.add(Span.styled(integrationName, Theme.label().bold()));
            spans.add(Span.raw("]"));
        }
        if (capabilities.hasAi()) {
            spans.add(Span.styled("  " + IntegrationSummary.AI_MARK, Theme.aiAssisted()));
        }
        spans.add(Span.raw(" "));
        return Line.from(spans);
    }

    private void renderInfo(Frame frame, Rect area, Group g) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.from(Span.styled(" " + g.name(), (g.ai() ? Theme.aiAssisted() : Theme.label()).bold())));
        lines.add(Line.from(Span.styled(" " + kindLabel(g), g.ai() ? Theme.aiAssisted() : Theme.muted())));
        if (g.text() != null && !g.text().isBlank()) {
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(Span.styled(" " + IntegrationSummaryHints.MARK + g.text(), Theme.aiAssisted())));
        }
        if (SYSTEM_IN.equals(g.kind()) || SYSTEM_OUT.equals(g.kind())) {
            List<String> groups = new ArrayList<>();
            for (String route : g.routes()) {
                Group owner = capabilities.group(capabilities.groupOf().get(route));
                String name = owner == null ? null : owner.ai() ? IntegrationSummaryHints.MARK + owner.name() : owner.name();
                if (name != null && !groups.contains(name)) {
                    groups.add(name);
                }
            }
            section(lines, "Used by", groups, Set.of());
            section(lines, "Routes", names(g.routes()), Set.of());
            section(lines, "Endpoints", g.entryPoints(), Set.of());
            frame.renderWidget(Paragraph.builder()
                    .text(Text.from(lines))
                    .overflow(Overflow.WRAP_WORD)
                    .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Info ").build())
                    .build(), area);
            return;
        }
        section(lines, "Routes", names(g.routes()), names(g.aiRoutes()));
        section(lines, "Entry points", g.entryPoints(), Set.of());
        section(lines, "Systems", g.systems(), Set.of());
        List<String> to = new ArrayList<>();
        for (GroupLink l : capabilities.links()) {
            if (l.from().equals(g.id())) {
                Group target = capabilities.group(l.to());
                to.add(l.kind() + " → " + (target != null ? target.name() : l.to()));
            }
        }
        section(lines, "Talks to", to, Set.of());
        if (g.warnings() > 0) {
            lines.add(Line.from(Span.raw("")));
            lines.add(Line.from(Span.styled(" " + g.warnings() + " warnings: camel overview lists them",
                    Theme.warning())));
        }
        frame.renderWidget(Paragraph.builder()
                .text(Text.from(lines))
                .overflow(Overflow.WRAP_WORD)
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL).title(" Info ").build())
                .build(), area);
    }

    /** Routes as the views name them: the running id, or the file name and line of one without an id. */
    private List<String> names(List<String> routes) {
        return routes.stream().map(r -> RouteKeys.display(dir, r)).toList();
    }

    private Set<String> names(Set<String> routes) {
        Set<String> answer = new LinkedHashSet<>();
        routes.forEach(r -> answer.add(RouteKeys.display(dir, r)));
        return answer;
    }

    private static void section(List<Line> lines, String title, List<String> items, Set<String> aiItems) {
        if (items.isEmpty()) {
            return;
        }
        lines.add(Line.from(Span.raw("")));
        lines.add(Line.from(Span.styled(" " + title, Theme.label())));
        for (String item : items) {
            boolean ai = aiItems.contains(item);
            lines.add(Line.from(Span.styled("  " + (ai ? IntegrationSummaryHints.MARK : "") + item,
                    ai ? Theme.aiAssisted() : Style.EMPTY.fg(Theme.baseFg()))));
        }
    }

    private static String kindLabel(Group g) {
        return switch (g.kind()) {
            case "group" -> "route group (from the source)";
            case "capability" -> "capability " + IntegrationSummary.AI_MARK;
            case ProjectCapabilities.SHARED -> "used by several groups";
            case ProjectCapabilities.UTILITY -> g.ai() ? "plumbing, partly " + IntegrationSummary.AI_MARK : "plumbing";
            case SYSTEM_IN -> "external: messages come in";
            case SYSTEM_OUT -> "external: messages go out";
            default -> "not grouped yet: /overview groups it";
        };
    }

    void renderFooter(List<Span> spans) {
        // actions only: the levels are on the level bar, the settings beside it
        hint(spans, "Enter", "topology");
        hint(spans, "s", "summary");
    }

    // ---- for tests ----

    void checkNowForTesting() {
        lastCheck = 0;
        checkForChanges();
    }

    Capabilities capabilitiesForTesting() {
        return capabilities;
    }

    void selectForTesting(String id) {
        support.setSelectedNodeIndex(support.findNodeIndexByRouteId(id));
    }

    String selectedForTesting() {
        return support.getSelectedRouteId();
    }

    boolean isLoadingForTesting() {
        return loading;
    }
}
