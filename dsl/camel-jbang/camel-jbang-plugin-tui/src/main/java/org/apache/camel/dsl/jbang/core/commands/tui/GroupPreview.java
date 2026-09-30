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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.tamboui.style.Style;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectCapabilities.Capabilities;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectCapabilities.Group;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview;

/**
 * The mini panel of the architecture view (CAMEL-25147): the inside of the selected group, as a tree from where
 * messages enter its routes along the links between them, so the user sees what Enter goes down to. A route of another
 * group is a leaf with the name of its group, in the colour it has in the topology: why an arrow leaves the box.
 */
final class GroupPreview {

    private GroupPreview() {
    }

    /** The lines of the panel, at most {@code maxLines}, each at most {@code width} columns. */
    static List<Line> lines(Group group, Capabilities caps, ProjectOverview.Overview overview, int width, int maxLines) {
        List<Line> lines = new ArrayList<>();
        Set<String> members = new LinkedHashSet<>(group.routes());
        // roots: where messages enter from outside, or that nothing in the group calls
        Set<String> called = new HashSet<>();
        for (ProjectOverview.Link l : overview.links()) {
            if (members.contains(l.from()) && members.contains(l.to()) && !l.from().equals(l.to())) {
                called.add(l.to());
            }
        }
        Set<String> visited = new HashSet<>();
        for (String route : group.routes()) {
            boolean entry = overview.entryPoints().stream().anyMatch(e -> route.equals(e.route()));
            if (entry || !called.contains(route)) {
                addRoot(lines, route, group, caps, overview, visited, width);
            }
        }
        // what a cycle left out
        for (String route : group.routes()) {
            if (!visited.contains(route)) {
                addRoot(lines, route, group, caps, overview, visited, width);
            }
        }
        if (lines.size() > maxLines) {
            List<Line> cut = new ArrayList<>(lines.subList(0, Math.max(0, maxLines - 1)));
            cut.add(Line.from(Span.styled(" … " + (lines.size() - cut.size()) + " more", Theme.muted())));
            return cut;
        }
        return lines;
    }

    private static void addRoot(
            List<Line> lines, String route, Group group, Capabilities caps, ProjectOverview.Overview overview,
            Set<String> visited, int width) {
        for (ProjectOverview.EntryPoint e : overview.entryPoints()) {
            if (route.equals(e.route())) {
                lines.add(line(width, Span.styled(" ⇢ " + e.label(), Style.EMPTY.fg(Theme.accent()))));
            }
        }
        visited.add(route);
        lines.add(line(width, Span.styled(" " + route, Style.EMPTY.fg(Theme.baseFg()).bold())));
        addChildren(lines, route, " ", group, caps, overview, visited, width);
    }

    private static void addChildren(
            List<Line> lines, String route, String indent, Group group, Capabilities caps,
            ProjectOverview.Overview overview, Set<String> visited, int width) {
        List<ProjectOverview.Link> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ProjectOverview.Link l : overview.links()) {
            if (route.equals(l.from()) && !route.equals(l.to()) && seen.add(l.to())) {
                out.add(l);
            }
        }
        for (int i = 0; i < out.size(); i++) {
            ProjectOverview.Link l = out.get(i);
            boolean last = i == out.size() - 1;
            String kind = l.onError() ? "on error" : l.kind();
            List<Span> spans = new ArrayList<>();
            spans.add(Span.styled(indent + (last ? "└─ " : "├─ ") + kind + " → ", Theme.muted()));
            spans.add(Span.styled(l.to(), Style.EMPTY.fg(Theme.baseFg())));
            String owner = caps.groupOf().get(l.to());
            boolean inside = group.id().equals(owner);
            if (!inside && owner != null) {
                Group g = caps.group(owner);
                if (g != null) {
                    spans.add(Span.styled("  " + (g.ai() ? IntegrationSummaryHints.MARK : "") + g.name(),
                            Style.EMPTY.fg(RouteGroups.colorOf(caps.groups().indexOf(g)))));
                }
            }
            lines.add(line(width, spans.toArray(new Span[0])));
            // follow the links inside the group only, and each route once
            if (inside && visited.add(l.to())) {
                addChildren(lines, l.to(), indent + (last ? "   " : "│  "), group, caps, overview, visited, width);
            }
        }
    }

    /** A line cut to the width, with an ellipsis. */
    private static Line line(int width, Span... spans) {
        List<Span> out = new ArrayList<>();
        int used = 0;
        for (Span s : spans) {
            int w = CharWidth.of(s.content());
            if (used + w <= width) {
                out.add(s);
                used += w;
            } else {
                int room = width - used - 1;
                if (room > 0) {
                    out.add(Span.styled(s.content().substring(0, Math.min(room, s.content().length())) + "…",
                            s.style()));
                }
                break;
            }
        }
        return Line.from(out);
    }
}
