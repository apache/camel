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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary.AiContent;
import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary.Capability;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview.Link;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview.Overview;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes.Route;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * The routes of a project grouped by what they do for the business, a level above the route topology so a project with
 * many routes stays readable (CAMEL-25147). Every route has one role:
 * <ul>
 * <li>{@code group} - the route sets a {@code group} in the source; that grouping is a fact and always wins;</li>
 * <li>{@code capability} - an AI placed the route in a business capability;</li>
 * <li>{@code shared} - routes in two or more groups or capabilities call or hand off to it (audit, notification):
 * derived from the links, shown once instead of in each;</li>
 * <li>{@code utility} - plumbing with little business meaning: only error handling (doCatch, onException, a dead letter
 * channel) reaches it, or it only logs, or an AI said so;</li>
 * <li>{@code other} - nothing above placed it.</li>
 * </ul>
 * Groups carry the facts of their routes: entry points, external systems, warnings. Which parts an AI decided is kept,
 * so a view can mark them as AI-assisted.
 */
public final class ProjectCapabilities {

    public static final String SHARED = "shared";
    public static final String UTILITY = "utility";
    public static final String OTHER = "other";

    private static final List<String> LINK_RANK = List.of("call", "async", "event", "shared");

    private ProjectCapabilities() {
    }

    /**
     * A box of the architecture view.
     *
     * @param id          unique id: {@code group:<name>}, {@code capability:<name>}, or shared, utility, other
     * @param kind        group, capability, shared, utility or other
     * @param ai          whether an AI decided the grouping (a capability, or a utility route only an AI named)
     * @param text        what an AI said the capability achieves, may be null
     * @param aiRoutes    the routes in the box because an AI said so, as opposed to the source or the links
     * @param entryPoints where messages enter the routes of the box
     * @param systems     the external systems its routes use, by name
     */
    public record Group(String id, String name, String kind, boolean ai, String text, List<String> routes,
            Set<String> aiRoutes, List<String> entryPoints, List<String> systems, int warnings) {
    }

    /** Groups connected because a route in one links to a route in the other, with the strongest kind of link. */
    public record GroupLink(String from, String to, String kind) {
    }

    public record Capabilities(List<Group> groups, List<GroupLink> links, Map<String, String> groupOf) {

        public Group group(String id) {
            return groups.stream().filter(g -> g.id().equals(id)).findFirst().orElse(null);
        }

        /** Whether an AI had a part in the grouping; false when the source groups everything. */
        public boolean hasAi() {
            return groups.stream().anyMatch(Group::ai);
        }
    }

    /**
     * Groups the routes of an overview.
     *
     * @param ai what an AI wrote about the project (capabilities, utility routes), or null for the facts alone
     */
    public static Capabilities build(Overview overview, AiContent ai) {
        List<Route> flows = overview.flows();
        Map<String, String> groupOf = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        Set<String> aiPlaced = new LinkedHashSet<>();

        // the source's group wins
        for (Route r : flows) {
            if (r.group() != null) {
                String id = "group:" + r.group();
                groupOf.put(r.key(), id);
                names.putIfAbsent(id, r.group());
            }
        }
        // plumbing: from the sources, or an AI said so
        Set<String> aiUtility = ai != null ? new LinkedHashSet<>(ai.utility()) : Set.of();
        for (Route r : flows) {
            if (!groupOf.containsKey(r.key())) {
                boolean fact = isUtility(r, overview);
                // a route that starts a flow is where work comes in, whatever an AI says
                if (fact || aiUtility.contains(r.key()) && !startsAFlow(r, overview)) {
                    groupOf.put(r.key(), UTILITY);
                    if (!fact) {
                        aiPlaced.add(r.key());
                    }
                }
            }
        }
        // the AI's business capabilities
        Map<String, String> texts = new LinkedHashMap<>();
        if (ai != null) {
            for (Capability c : ai.capabilities()) {
                String id = "capability:" + c.name();
                names.putIfAbsent(id, c.name());
                texts.putIfAbsent(id, c.text());
                for (String key : c.routes()) {
                    if (overview.route(key) != null && !groupOf.containsKey(key)) {
                        groupOf.put(key, id);
                        aiPlaced.add(key);
                    }
                }
            }
        }
        // a route that starts a flow, which an AI wrongly called utility, goes with the one group it feeds
        for (Route r : flows) {
            if (!groupOf.containsKey(r.key()) && aiUtility.contains(r.key()) && startsAFlow(r, overview)) {
                Set<String> fed = new LinkedHashSet<>();
                for (Link l : overview.links()) {
                    if (l.from().equals(r.key()) && !l.onError() && !l.to().equals(r.key())) {
                        fed.add(groupOf.get(l.to()));
                    }
                }
                if (fed.size() == 1 && fed.iterator().next() != null && !UTILITY.equals(fed.iterator().next())) {
                    groupOf.put(r.key(), fed.iterator().next());
                    aiPlaced.add(r.key());
                }
            }
        }
        for (Route r : flows) {
            groupOf.putIfAbsent(r.key(), OTHER);
        }
        // shared: callers from two or more groups; a group from the source is never moved
        Map<String, String> placed = new LinkedHashMap<>(groupOf);
        for (Route r : flows) {
            String own = placed.get(r.key());
            if (r.group() != null || UTILITY.equals(own)) {
                continue;
            }
            Set<String> callerGroups = new LinkedHashSet<>();
            for (Link l : overview.links()) {
                String caller = placed.get(l.from());
                if (l.to().equals(r.key()) && caller != null && !UTILITY.equals(caller)) {
                    callerGroups.add(caller);
                }
            }
            if (callerGroups.size() >= 2) {
                groupOf.put(r.key(), SHARED);
                aiPlaced.remove(r.key());
            }
        }

        // the boxes, in order: source groups, capabilities, shared, other, utility
        Map<String, List<String>> members = new LinkedHashMap<>();
        names.keySet().stream().filter(id -> id.startsWith("group:")).forEach(id -> members.put(id, new ArrayList<>()));
        names.keySet().stream().filter(id -> id.startsWith("capability:"))
                .forEach(id -> members.put(id, new ArrayList<>()));
        members.put(SHARED, new ArrayList<>());
        members.put(OTHER, new ArrayList<>());
        members.put(UTILITY, new ArrayList<>());
        for (Route r : flows) {
            members.get(groupOf.get(r.key())).add(r.key());
        }
        List<Group> groups = new ArrayList<>();
        members.forEach((id, routes) -> {
            if (routes.isEmpty()) {
                return;
            }
            Set<String> byAi = new LinkedHashSet<>(routes);
            byAi.retainAll(aiPlaced);
            String kind = id.startsWith("group:") ? "group" : id.startsWith("capability:") ? "capability" : id;
            String name = switch (kind) {
                case SHARED -> "Shared services";
                case UTILITY -> "Utility";
                case OTHER -> "Other";
                default -> names.get(id);
            };
            boolean isAi = "capability".equals(kind) || UTILITY.equals(kind) && !byAi.isEmpty();
            groups.add(new Group(
                    id, name, kind, isAi, texts.get(id), List.copyOf(routes), byAi, entryPoints(overview, routes),
                    systems(overview, routes), warnings(overview, routes)));
        });
        return new Capabilities(groups, links(overview, groupOf), groupOf);
    }

    /**
     * Whether a route starts a flow: it is an entry point (work comes in from outside) and passes the work on to
     * another route of the project. Such a route is business intake, never plumbing, whatever a model says.
     */
    static boolean startsAFlow(Route r, Overview overview) {
        boolean entry = overview.entryPoints().stream().anyMatch(e -> r.key().equals(e.route()));
        return entry && overview.links().stream()
                .anyMatch(l -> l.from().equals(r.key()) && !l.onError() && !l.to().equals(r.key()));
    }

    /**
     * Plumbing, as the sources show it: only error handling (onException, a dead letter channel) reaches the route, or
     * every step of it only logs and it is not where messages enter.
     */
    static boolean isUtility(Route r, Overview overview) {
        List<Link> incoming = overview.links().stream().filter(l -> l.to().equals(r.key())).toList();
        if (!incoming.isEmpty() && incoming.stream().allMatch(Link::onError)) {
            return true;
        }
        boolean entry = overview.entryPoints().stream().anyMatch(e -> r.key().equals(e.route()));
        return r.logOnly() && !entry;
    }

    private static List<String> entryPoints(Overview overview, List<String> routes) {
        List<String> answer = new ArrayList<>();
        overview.entryPoints().stream().filter(e -> e.route() != null && routes.contains(e.route()))
                .forEach(e -> answer.add(ProjectOverview.isInternal(e) ? e.label() + " (internal)" : e.label()));
        return answer;
    }

    private static List<String> systems(Overview overview, List<String> routes) {
        Set<String> answer = new LinkedHashSet<>();
        overview.systems().stream().filter(s -> routes.contains(s.route())).forEach(s -> answer.add(s.name()));
        return List.copyOf(answer);
    }

    private static int warnings(Overview overview, List<String> routes) {
        return (int) overview.findings().stream()
                .filter(f -> !"info".equals(f.level()) && f.route() != null && routes.contains(f.route())).count();
    }

    private static List<GroupLink> links(Overview overview, Map<String, String> groupOf) {
        Map<String, String> strongest = new LinkedHashMap<>();
        for (Link l : overview.links()) {
            String from = groupOf.get(l.from());
            String to = groupOf.get(l.to());
            if (from == null || to == null || from.equals(to)) {
                continue;
            }
            strongest.merge(from + "\u0000" + to, l.kind(),
                    (a, b) -> LINK_RANK.indexOf(a) <= LINK_RANK.indexOf(b) ? a : b);
        }
        List<GroupLink> answer = new ArrayList<>();
        strongest.forEach((k, kind) -> {
            String[] parts = k.split("\u0000", 2);
            answer.add(new GroupLink(parts[0], parts[1], kind));
        });
        return answer;
    }

    /** The grouping as the tools answer it. */
    public static JsonArray toJson(Capabilities capabilities) {
        JsonArray out = new JsonArray();
        for (Group g : capabilities.groups()) {
            JsonObject jo = new JsonObject();
            jo.put("name", g.name());
            jo.put("kind", g.kind());
            if (g.ai()) {
                jo.put("aiAssisted", true);
            }
            if (g.text() != null && !g.text().isBlank()) {
                jo.put("aiText", g.text());
            }
            jo.put("routes", new JsonArray(g.routes()));
            if (!g.entryPoints().isEmpty()) {
                jo.put("entryPoints", new JsonArray(g.entryPoints()));
            }
            if (!g.systems().isEmpty()) {
                jo.put("systems", new JsonArray(g.systems()));
            }
            if (g.warnings() > 0) {
                jo.put("warnings", g.warnings());
            }
            JsonArray to = new JsonArray();
            for (GroupLink l : capabilities.links()) {
                if (l.from().equals(g.id())) {
                    Group target = capabilities.group(l.to());
                    to.add(l.kind() + " " + (target != null ? target.name() : l.to()));
                }
            }
            if (!to.isEmpty()) {
                jo.put("linksTo", to);
            }
            out.add(jo);
        }
        return out;
    }
}
