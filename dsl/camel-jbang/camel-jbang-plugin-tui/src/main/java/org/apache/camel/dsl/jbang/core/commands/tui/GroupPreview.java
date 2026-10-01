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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import dev.tamboui.style.Style;
import dev.tamboui.text.CharWidth;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectCapabilities.Capabilities;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectCapabilities.Group;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes;

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
        return lines(group, caps, overview, width, maxLines, r -> r,
                scheme -> ProjectOverview.isRemote(scheme, ProjectOverviewAssist.catalog()));
    }

    /**
     * @param name   how a route is named: the running id of a route without one in the source, for instance
     * @param remote whether a component talks to a system outside the integration: sends to others (a log, a mock) are
     *               left out, they say nothing about how the routes and systems connect
     */
    static List<Line> lines(
            Group group, Capabilities caps, ProjectOverview.Overview overview, int width, int maxLines,
            Function<String, String> name, Predicate<String> remote) {
        List<Line> lines = new ArrayList<>();
        for (String route : flowOrder(group, overview)) {
            for (ProjectOverview.EntryPoint e : overview.entryPoints()) {
                if (route.equals(e.route())) {
                    lines.add(line(width, Span.styled(" \u21e2 " + e.label(), Style.EMPTY.fg(Theme.accent()))));
                }
            }
            lines.add(line(width, Span.styled(" " + name.apply(route), Style.EMPTY.fg(Theme.baseFg()).bold())));
            List<Child> out = children(route, overview, remote);
            for (int i = 0; i < out.size(); i++) {
                lines.add(childLine(out.get(i), i == out.size() - 1, group, caps, width, name));
            }
        }
        if (lines.size() > maxLines) {
            List<Line> cut = new ArrayList<>(lines.subList(0, Math.max(0, maxLines - 1)));
            cut.add(Line.from(Span.styled(" \u2026 " + (lines.size() - cut.size()) + " more", Theme.muted())));
            return cut;
        }
        return lines;
    }

    /**
     * The routes of the group in the order messages flow: those nothing in the group calls first, then the routes they
     * hand off to, then what a cycle left.
     */
    private static List<String> flowOrder(Group group, ProjectOverview.Overview overview) {
        Set<String> members = new LinkedHashSet<>(group.routes());
        Set<String> called = new HashSet<>();
        for (ProjectOverview.Link l : overview.links()) {
            if (members.contains(l.from()) && members.contains(l.to()) && !l.from().equals(l.to())) {
                called.add(l.to());
            }
        }
        Set<String> order = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        for (String route : group.routes()) {
            if (!called.contains(route)) {
                queue.add(route);
            }
        }
        while (!queue.isEmpty() || order.size() < members.size()) {
            if (queue.isEmpty()) {
                // a cycle: start it at the first route not shown
                members.stream().filter(r -> !order.contains(r)).findFirst().ifPresent(queue::add);
            }
            String route = queue.poll();
            if (route == null || !order.add(route)) {
                continue;
            }
            for (ProjectOverview.Link l : overview.links()) {
                if (route.equals(l.from()) && members.contains(l.to()) && !order.contains(l.to())) {
                    queue.add(l.to());
                }
            }
        }
        return new ArrayList<>(order);
    }

    /** A child in the tree: a link to another route, or an endpoint outside the project the route sends to. */
    private record Child(ProjectOverview.Link link, ProjectRoutes.Endpoint endpoint) {
    }

    /**
     * What a route sends to, in order: the routes it links to, and the remote endpoints no route of the project
     * consumes.
     */
    private static List<Child> children(String route, ProjectOverview.Overview overview, Predicate<String> remote) {
        List<Child> answer = new ArrayList<>();
        Set<String> targets = new HashSet<>();
        ProjectRoutes.Route r = overview.route(route);
        List<ProjectRoutes.Endpoint> produces = r != null ? r.produces() : List.of();
        for (ProjectRoutes.Endpoint e : produces) {
            boolean linked = false;
            for (ProjectOverview.Link l : overview.links()) {
                if (route.equals(l.from()) && !route.equals(l.to()) && e.uri().equals(l.endpoint())) {
                    linked = true;
                    if (targets.add(l.to())) {
                        answer.add(new Child(l, null));
                    }
                }
            }
            if (!linked && (e.dynamic() || remote.test(e.scheme()))) {
                answer.add(new Child(null, e));
            }
        }
        // links over what the route consumes (poll, enrich), which are not among its sends
        for (ProjectOverview.Link l : overview.links()) {
            if (route.equals(l.from()) && !route.equals(l.to()) && targets.add(l.to())) {
                answer.add(new Child(l, null));
            }
        }
        return answer;
    }

    private static Line childLine(
            Child c, boolean last, Group group, Capabilities caps, int width, Function<String, String> name) {
        String branch = last ? " \u2514\u2500 " : " \u251c\u2500 ";
        if (c.endpoint() != null) {
            ProjectRoutes.Endpoint e = c.endpoint();
            String kind = e.onError() ? "on error" : "to";
            return line(width, Span.styled(branch + kind + " \u2192 ", Theme.muted()),
                    Span.styled(e.uri(), Style.EMPTY.fg(Theme.accent())));
        }
        ProjectOverview.Link l = c.link();
        String kind = l.onError() ? "on error" : l.kind();
        List<Span> spans = new ArrayList<>();
        spans.add(Span.styled(branch + kind + " \u2192 ", Theme.muted()));
        spans.add(Span.styled(name.apply(l.to()), Style.EMPTY.fg(Theme.baseFg())));
        String owner = caps.groupOf().get(l.to());
        if (owner != null && !group.id().equals(owner)) {
            Group g = caps.group(owner);
            if (g != null) {
                spans.add(Span.styled("  " + (g.ai() ? IntegrationSummaryHints.MARK : "") + g.name(),
                        Style.EMPTY.fg(RouteGroups.colorOf(caps.groups().indexOf(g)))));
            }
        }
        return line(width, spans.toArray(new Span[0]));
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
