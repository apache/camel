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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectCapabilities;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.TopologyDiagramWidget.NodeLine;

/**
 * The group of each route of a project, for the Diagram tab's group setting (CAMEL-25147): the architecture one level
 * down, as a tag in each route box of the topology and a colour per group. Groups come from the route groups of the
 * source, the capabilities the AI project overview proposed (only while its hints are shown), shared services and
 * utility; routes nothing placed get no tag.
 * <p/>
 * Read in the background and at most every few seconds per project, since the topology asks on every render.
 */
final class RouteGroups {

    /** A route's group: its id, name, whether an AI decided it, and its place among the groups (for the colour). */
    record Tag(String group, String name, boolean ai, int index) {
    }

    /** The groups with the AI's capabilities, and from the facts alone (the ai view setting off). */
    private record Entry(long checkedAt, Map<String, Tag> withAi, Map<String, Tag> facts) {

        Map<String, Tag> tags() {
            return IntegrationSummaryHints.enabled() ? withAi : facts;
        }
    }

    private static final long RECHECK_MS = 3000;
    private static final Map<Path, Entry> CACHE = new ConcurrentHashMap<>();
    private static final Set<Path> LOADING = ConcurrentHashMap.newKeySet();

    private RouteGroups() {
    }

    /** The group of each route by route id, as last read; reads again in the background when it is old. */
    static Map<String, Tag> of(Path dir) {
        if (dir == null) {
            return Map.of();
        }
        Path key = dir.toAbsolutePath().normalize();
        Entry e = CACHE.get(key);
        if ((e == null || System.currentTimeMillis() - e.checkedAt() > RECHECK_MS) && LOADING.add(key)) {
            Thread t = new Thread(() -> {
                try {
                    ProjectOverview.Overview overview = ProjectOverview.analyze(key, ProjectOverviewAssist.catalog());
                    CACHE.put(key, new Entry(
                            System.currentTimeMillis(), tags(overview, IntegrationSummary.read(key)),
                            tags(overview, null)));
                } catch (RuntimeException ex) {
                    CACHE.put(key, new Entry(System.currentTimeMillis(), Map.of(), Map.of()));
                } finally {
                    LOADING.remove(key);
                }
            }, "tui-route-groups");
            t.setDaemon(true);
            t.start();
        }
        return e != null ? e.tags() : Map.of();
    }

    /** Reads the groups now; for tests and the first read. */
    static Map<String, Tag> load(Path dir) {
        ProjectOverview.Overview overview = ProjectOverview.analyze(dir, ProjectOverviewAssist.catalog());
        // the AI's groups only while its hints are shown
        return tags(overview, IntegrationSummaryHints.enabled() ? IntegrationSummary.read(dir) : null);
    }

    private static Map<String, Tag> tags(ProjectOverview.Overview overview, IntegrationSummary.Summary summary) {
        ProjectCapabilities.Capabilities caps
                = ProjectCapabilities.build(overview, summary != null ? summary.ai() : null);
        Map<String, Tag> tags = new LinkedHashMap<>();
        List<ProjectCapabilities.Group> groups = caps.groups();
        for (int i = 0; i < groups.size(); i++) {
            ProjectCapabilities.Group g = groups.get(i);
            if (ProjectCapabilities.OTHER.equals(g.kind())) {
                continue;
            }
            for (String route : g.routes()) {
                tags.put(route, new Tag(g.id(), g.name(), g.ai(), i));
            }
        }
        return tags;
    }

    /** Whether the project has groups worth showing: some route is in a group. */
    static boolean any(Path dir) {
        return !of(dir).isEmpty();
    }

    /** The utility routes: plumbing such as error handling, tracing and housekeeping. */
    static Set<String> utility(Map<String, Tag> tags) {
        Set<String> ids = new LinkedHashSet<>();
        tags.forEach((route, t) -> {
            if (ProjectCapabilities.UTILITY.equals(t.group())) {
                ids.add(route);
            }
        });
        return ids;
    }

    /** The tag line of each route: the group name, marked and styled when an AI decided it. */
    static Map<String, NodeLine> tagLines(Map<String, Tag> tags) {
        Map<String, NodeLine> lines = new LinkedHashMap<>();
        tags.forEach((route, t) -> lines.put(route, new NodeLine(
                "▸ " + (t.ai() ? IntegrationSummaryHints.MARK : "") + t.name(),
                t.ai() ? Theme.aiAssisted() : Style.EMPTY.fg(colorOf(t.index())))));
        return lines;
    }

    /**
     * The groups the topology shows, each in its colour, as the Architecture shows them: the legend that links the two
     * levels. Null when there are none.
     */
    static Line legend(Map<String, Tag> tags, Set<String> hidden) {
        Map<String, Tag> groups = new LinkedHashMap<>();
        tags.forEach((route, t) -> {
            if (!hidden.contains(route)) {
                groups.putIfAbsent(t.group(), t);
            }
        });
        if (groups.isEmpty()) {
            return null;
        }
        List<Span> spans = new ArrayList<>();
        groups.values().stream().sorted(Comparator.comparingInt(Tag::index)).forEach(t -> {
            spans.add(Span.styled(" \u25a0 ", Style.EMPTY.fg(colorOf(t.index()))));
            spans.add(t.ai()
                    ? Span.styled(IntegrationSummaryHints.MARK + t.name(), Theme.aiAssisted())
                    : Span.raw(t.name()));
        });
        spans.add(Span.raw(" "));
        return Line.from(spans);
    }

    /** The border colour of each route, one per group. */
    static Map<String, Color> colors(Map<String, Tag> tags) {
        Map<String, Color> colors = new LinkedHashMap<>();
        tags.forEach((route, t) -> colors.put(route, colorOf(t.index())));
        return colors;
    }

    /** Distinct colours from the theme, one per group, repeating when there are more groups. */
    static Color colorOf(int index) {
        List<Color> palette = List.of(Theme.diagramFrom(), Theme.diagramTo(), Theme.diagramChoice(),
                Theme.diagramAction(), Theme.diagramEip());
        return palette.get(Math.floorMod(index, palette.size()));
    }

    /** For tests: forget what was read. */
    static void resetForTesting() {
        CACHE.clear();
    }
}
