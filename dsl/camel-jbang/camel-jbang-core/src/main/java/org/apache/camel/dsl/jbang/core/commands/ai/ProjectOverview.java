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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes.Endpoint;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes.Route;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * A high-level overview of the integrations in a project directory, read from the route sources: which routes there
 * are, how they connect (a call over direct, a hand-off over seda, an event over a broker), which external systems they
 * use, where messages enter, and what looks wrong (a direct endpoint nobody consumes, a cycle, routes without a
 * description).
 * <p/>
 * Everything here is derived from the sources, without an AI. The explanations an AI adds on top (an overview, the
 * capabilities, descriptions of routes that have none) live in the {@link IntegrationSummary} file, marked as
 * AI-assisted.
 */
public final class ProjectOverview {

    /** The most routes an overview lists; a project with more is summarized by its groups and systems. */
    static final int MAX_ROUTES = 300;

    private ProjectOverview() {
    }

    /** Two routes connected over an endpoint one sends to and the other consumes from. */
    public record Link(String from, String to, String endpoint, String kind, boolean onError) {

        public Link(String from, String to, String endpoint, String kind) {
            this(from, to, endpoint, kind, false);
        }
    }

    /** An external system a route talks to. */
    public record SystemUse(String category, String name, String uri, String direction, String route) {
    }

    /**
     * Where messages enter the project: a schedule, an HTTP endpoint or a consumer of an external system.
     *
     * @param remote whether a remote system drives it, as the catalog says of the component (a timer or scheduler is
     *               not remote: the work starts inside the integration)
     */
    public record EntryPoint(String kind, String label, String route, boolean remote) {
    }

    /** Whether work starts inside the integration rather than from a remote system. */
    public static boolean isInternal(EntryPoint e) {
        return !e.remote();
    }

    /** Something in the structure worth a look. */
    public record Finding(String level, String kind, String message, String route) {
    }

    /**
     * The overview of a project.
     *
     * @param files       the route files read, relative to the directory
     * @param fingerprint a hash of the route files, to tell whether a summary written earlier is still up to date
     */
    public record Overview(Path directory, List<String> files, List<Route> routes, List<Link> links,
            List<SystemUse> systems, List<EntryPoint> entryPoints, List<Finding> findings, String fingerprint) {

        /**
         * The routes, templates and templated routes; REST operations are entry points and error handlers are how
         * failures travel, not routes.
         */
        public List<Route> flows() {
            return routes.stream().filter(ProjectOverview::isFlow).toList();
        }

        public Route route(String key) {
            for (Route r : routes) {
                if (r.key().equals(key)) {
                    return r;
                }
            }
            return null;
        }

        /** The routes without a description in the source, which an AI may describe. */
        public List<Route> undescribed() {
            return flows().stream().filter(r -> !r.hasDescription()).toList();
        }
    }

    /** Whether a route is a route of the project, not a REST operation or an error handler. */
    static boolean isFlow(Route r) {
        return !"rest".equals(r.kind()) && !"errorHandler".equals(r.kind());
    }

    /** Reads the route files of a directory and builds the overview. */
    public static Overview analyze(Path dir, CamelCatalog catalog) {
        Map<String, String> sources = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        // every Java file, read only when a route refers to one of its constants
        Map<String, Supplier<String>> java = new LinkedHashMap<>();
        for (Path p : AuthoringTools.projectFiles(dir)) {
            String rel = AuthoringTools.relativePath(dir, p);
            if (IntegrationSummary.FILE_NAME.equals(rel)) {
                continue;
            }
            String type = AuthoringTools.fileType(rel);
            if ("route".equals(AuthoringTools.fileKind(p, rel, type))) {
                try {
                    sources.put(rel, Files.readString(p, StandardCharsets.UTF_8));
                } catch (IOException e) {
                    // an unreadable file is left out
                }
            }
            if (rel.endsWith(".java")) {
                java.put(rel, () -> {
                    try {
                        return Files.readString(p, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        return null;
                    }
                });
            }
        }
        return analyze(dir, sources, new ProjectConstantResolver(java, catalog), catalog);
    }

    /**
     * Builds the overview from route sources already read.
     *
     * @param sources the content of each route file by its path relative to the directory
     */
    public static Overview analyze(Path dir, Map<String, String> sources, CamelCatalog catalog) {
        Map<String, Supplier<String>> java = new LinkedHashMap<>();
        sources.forEach((file, content) -> java.put(file, () -> content));
        return analyze(dir, sources, new ProjectConstantResolver(java, catalog), catalog);
    }

    private static Overview analyze(
            Path dir, Map<String, String> sources, ProjectConstantResolver constants, CamelCatalog catalog) {
        List<Route> routes = new ArrayList<>();
        for (Map.Entry<String, String> e : sources.entrySet()) {
            routes.addAll(ProjectRoutes.parse(e.getKey(), e.getValue(), catalog, constants));
        }
        if (routes.size() > MAX_ROUTES) {
            routes = new ArrayList<>(routes.subList(0, MAX_ROUTES));
        }
        List<Link> links = links(routes, catalog);
        List<SystemUse> systems = systems(routes, catalog);
        List<EntryPoint> entryPoints = entryPoints(routes, links, catalog);
        List<Finding> findings = findings(routes, links, sources.size() > 0 && routes.size() == MAX_ROUTES);
        return new Overview(
                dir, List.copyOf(sources.keySet()), List.copyOf(routes), links, systems, entryPoints,
                findings, fingerprint(sources));
    }

    // ---- categories ----

    /**
     * What kind of system a component talks to, from its catalog labels: ai, database, messaging, file, email, apps
     * (SaaS, chat, documents), http, cloud, schedule, internal (plumbing inside Camel such as direct, seda, log, bean)
     * or other.
     */
    static String category(String scheme, CamelCatalog catalog) {
        String s = scheme.toLowerCase(Locale.ROOT);
        String family = ProjectRoutes.family(s);
        if ("direct".equals(family) || "seda".equals(family) || "kamelet".equals(s) || s.startsWith("dynamic")) {
            return "internal";
        }
        ComponentModel model = componentModel(s, catalog);
        if (model == null || model.getLabel() == null) {
            return "other";
        }
        Set<String> labels = new LinkedHashSet<>();
        for (String label : model.getLabel().split(",")) {
            labels.add(label.strip().toLowerCase(Locale.ROOT));
        }
        if (labels.contains("ai")) {
            return "ai";
        }
        if (labels.contains("scheduling")) {
            return "schedule";
        }
        if (labels.contains("database") || labels.contains("sql") || labels.contains("nosql")
                || labels.contains("bigdata")) {
            return "database";
        }
        if (labels.contains("messaging")) {
            return "messaging";
        }
        if (labels.contains("file")) {
            return "file";
        }
        if (labels.contains("mail")) {
            return "email";
        }
        if (labels.contains("social") || labels.contains("chat") || labels.contains("saas")
                || labels.contains("document") || labels.contains("finance")) {
            return "apps";
        }
        if (labels.contains("http") || labels.contains("rest") || labels.contains("webservice")
                || labels.contains("api")) {
            return "http";
        }
        if (labels.contains("cloud")) {
            return "cloud";
        }
        if (labels.contains("core")) {
            return "internal";
        }
        return "other";
    }

    /**
     * Whether the component talks to remote systems, as the catalog says; a component the catalog does not know is
     * taken as remote.
     */
    public static boolean isRemote(String scheme, CamelCatalog catalog) {
        ComponentModel model = componentModel(scheme.toLowerCase(Locale.ROOT), catalog);
        return model == null || model.isRemote();
    }

    /** The component's title from the catalog, e.g. Kafka or SQL, else its scheme. */
    static String systemName(String scheme, CamelCatalog catalog) {
        ComponentModel model = componentModel(scheme.toLowerCase(Locale.ROOT), catalog);
        return model != null && model.getTitle() != null ? model.getTitle() : scheme;
    }

    private static ComponentModel componentModel(String scheme, CamelCatalog catalog) {
        if (catalog == null) {
            return null;
        }
        try {
            return catalog.componentModel(scheme);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- links ----

    private static List<Link> links(List<Route> routes, CamelCatalog catalog) {
        Map<String, List<Route>> consumers = new HashMap<>();
        for (Route r : routes) {
            for (Endpoint e : r.consumes()) {
                if (!e.dynamic()) {
                    consumers.computeIfAbsent(e.key(), k -> new ArrayList<>()).add(r);
                }
            }
        }
        // one link per route pair and endpoint; it is on error only when every send to it handles a failure
        Map<String, Link> links = new LinkedHashMap<>();
        for (Route r : routes) {
            for (Endpoint e : r.produces()) {
                if (e.dynamic()) {
                    continue;
                }
                for (Route target : consumers.getOrDefault(e.key(), List.of())) {
                    if (target == r) {
                        continue;
                    }
                    String id = r.key() + "->" + target.key() + "@" + e.key();
                    Link known = links.get(id);
                    if (known == null) {
                        links.put(id, new Link(r.key(), target.key(), e.uri(), linkKind(e.scheme(), catalog), e.onError()));
                    } else if (known.onError() && !e.onError()) {
                        links.put(id, new Link(known.from(), known.to(), known.endpoint(), known.kind(), false));
                    }
                }
            }
        }
        return new ArrayList<>(links.values());
    }

    /** call (waits for the answer), async (hands off in memory), event (over a broker) or shared (e.g. a folder). */
    static String linkKind(String scheme, CamelCatalog catalog) {
        String family = ProjectRoutes.family(scheme.toLowerCase(Locale.ROOT));
        if ("direct".equals(family) || "kamelet".equals(family)) {
            return "call";
        }
        if ("seda".equals(family)) {
            return "async";
        }
        return "messaging".equals(category(scheme, catalog)) ? "event" : "shared";
    }

    // ---- systems and entry points ----

    private static List<SystemUse> systems(List<Route> routes, CamelCatalog catalog) {
        List<SystemUse> answer = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Route r : routes) {
            for (Endpoint e : r.consumes()) {
                addSystem(answer, seen, e, "in", r, catalog);
            }
            for (Endpoint e : r.produces()) {
                addSystem(answer, seen, e, "out", r, catalog);
            }
        }
        return answer;
    }

    private static void addSystem(
            List<SystemUse> answer, Set<String> seen, Endpoint e, String direction, Route r, CamelCatalog catalog) {
        if (e.dynamic()) {
            return;
        }
        String category = category(e.scheme(), catalog);
        if ("internal".equals(category) || "schedule".equals(category)) {
            return;
        }
        if (seen.add(e.uri() + "|" + direction + "|" + r.key())) {
            answer.add(new SystemUse(category, systemName(e.scheme(), catalog), e.uri(), direction, r.key()));
        }
    }

    private static List<EntryPoint> entryPoints(List<Route> routes, List<Link> links, CamelCatalog catalog) {
        List<EntryPoint> answer = new ArrayList<>();
        for (Route r : routes) {
            if ("rest".equals(r.kind())) {
                String target = links.stream().filter(l -> l.from().equals(r.key())).map(Link::to).findFirst()
                        .orElse(null);
                answer.add(new EntryPoint("http", r.restVerb() + " " + r.restPath(), target, true));
                continue;
            }
            Endpoint from = r.from();
            if (from == null || !"route".equals(r.kind())) {
                continue;
            }
            String category = category(from.scheme(), catalog);
            if ("internal".equals(category)) {
                continue;
            }
            String kind = switch (category) {
                case "schedule" -> "schedule";
                case "http" -> "http";
                default -> "consumer";
            };
            String label = from.label() != null ? from.uri() + " (" + from.label() + ")" : from.uri();
            answer.add(new EntryPoint(kind, label, r.key(), isRemote(from.scheme(), catalog)));
        }
        return answer;
    }

    // ---- findings ----

    private static List<Finding> findings(List<Route> routes, List<Link> links, boolean truncated) {
        List<Finding> answer = new ArrayList<>();
        Map<String, List<Route>> byId = new LinkedHashMap<>();
        Set<String> consumed = new LinkedHashSet<>();
        Set<String> produced = new LinkedHashSet<>();
        for (Route r : routes) {
            if (r.id() != null) {
                byId.computeIfAbsent(r.id(), k -> new ArrayList<>()).add(r);
            }
            r.consumes().forEach(e -> consumed.add(e.key()));
            r.produces().forEach(e -> produced.add(e.key()));
        }
        byId.forEach((id, list) -> {
            if (list.size() > 1 && list.stream().noneMatch(r -> "rest".equals(r.kind()))) {
                answer.add(new Finding(
                        "error", "duplicate-id", "Route id " + id + " is used " + list.size()
                                                 + " times: " + String.join(", ",
                                                         list.stream().map(r -> r.file() + ":" + r.line())
                                                                 .toList()),
                        id));
            }
        });
        for (Route r : routes) {
            for (Endpoint e : r.produces()) {
                String family = e.key().substring(0, e.key().indexOf(':'));
                if (!e.dynamic() && ("direct".equals(family) || "seda".equals(family)) && !e.uri().contains("{{")
                        && !consumed.contains(e.key())) {
                    answer.add(new Finding(
                            "warning", "missing-route",
                            r.key() + " sends to " + e.uri() + " but no route in the project consumes from it", r.key()));
                }
            }
        }
        for (Route r : routes) {
            Endpoint from = r.from();
            if (from != null && "route".equals(r.kind())) {
                String family = from.key().substring(0, from.key().indexOf(':'));
                if (("direct".equals(family) || "seda".equals(family)) && !produced.contains(from.key())) {
                    answer.add(new Finding(
                            "info", "unreferenced", r.key() + " consumes from " + from.uri()
                                                    + " but no route in the project sends there (it may be called from code)",
                            r.key()));
                }
            }
        }
        for (List<String> cycle : cycles(links)) {
            answer.add(new Finding(
                    "warning", "cycle", "Routes call each other in a cycle: " + String.join(" -> ", cycle),
                    cycle.get(0)));
        }
        List<Route> flows = routes.stream().filter(ProjectOverview::isFlow).toList();
        List<String> undescribed = flows.stream().filter(r -> !r.hasDescription()).map(Route::key).toList();
        if (!undescribed.isEmpty()) {
            answer.add(new Finding(
                    "info", "no-description", undescribed.size() + " of " + flows.size()
                                              + " routes have no description: "
                                              + String.join(", ", undescribed.stream().limit(10).toList())
                                              + (undescribed.size() > 10 ? ", ..." : ""),
                    null));
        }
        for (Route r : flows) {
            if ("route".equals(r.kind()) && r.steps() == 1 && r.produces().size() == 1 && r.from() != null
                    && !r.produces().get(0).dynamic() && isInMemory(r.produces().get(0))) {
                answer.add(new Finding(
                        "info", "pass-through", r.key() + " only forwards from " + r.from().uri() + " to "
                                                + r.produces().get(0).uri(),
                        r.key()));
            }
        }
        long heuristic = flows.stream().filter(Route::heuristic).count();
        if (heuristic > 0) {
            answer.add(new Finding(
                    "info", "java-dsl", heuristic + " Java DSL routes have parts that are only known at runtime"
                                        + " (a lambda, a value from a helper method): their endpoints may be incomplete",
                    null));
        }
        if (truncated) {
            answer.add(new Finding("info", "truncated", "Only the first " + MAX_ROUTES + " routes are included", null));
        }
        return answer;
    }

    /** A direct or seda endpoint: a hop inside Camel that a route could make itself. */
    private static boolean isInMemory(Endpoint e) {
        return e.key().startsWith("direct:") || e.key().startsWith("seda:");
    }

    /** The cycles among call and hand-off links, each once, starting at its smallest route key. */
    static List<List<String>> cycles(List<Link> links) {
        Map<String, List<String>> graph = new TreeMap<>();
        for (Link l : links) {
            if ("call".equals(l.kind()) || "async".equals(l.kind())) {
                graph.computeIfAbsent(l.from(), k -> new ArrayList<>()).add(l.to());
            }
        }
        List<List<String>> answer = new ArrayList<>();
        Set<String> reported = new LinkedHashSet<>();
        for (String start : graph.keySet()) {
            findCycles(graph, start, start, new ArrayList<>(List.of(start)), answer, reported);
            if (answer.size() >= 10) {
                break;
            }
        }
        return answer;
    }

    private static void findCycles(
            Map<String, List<String>> graph, String start, String node, List<String> path, List<List<String>> answer,
            Set<String> reported) {
        if (path.size() > 20 || answer.size() >= 10) {
            return;
        }
        for (String next : graph.getOrDefault(node, List.of())) {
            if (next.equals(start)) {
                List<String> cycle = new ArrayList<>(path);
                cycle.add(start);
                String id = String.join(",", new TreeSet<>(path));
                if (path.stream().allMatch(p -> p.compareTo(start) >= 0) && reported.add(id)) {
                    answer.add(cycle);
                }
            } else if (!path.contains(next) && next.compareTo(start) > 0) {
                path.add(next);
                findCycles(graph, start, next, path, answer, reported);
                path.remove(path.size() - 1);
            }
        }
    }

    // ---- fingerprint ----

    /** A short hash of the route files: their paths and content. */
    static String fingerprint(Map<String, String> sources) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            new TreeMap<>(sources).forEach((file, content) -> {
                md.update(file.getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
                // line endings do not change what the routes do
                md.update(content.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
            });
            return HexFormat.of().formatHex(md.digest()).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- JSON ----

    /** The overview as the tools answer it, with what an existing summary file adds. */
    public static JsonObject toJson(Overview overview, IntegrationSummary.Summary summary) {
        JsonObject out = new JsonObject();
        out.put("directory", overview.directory() != null ? overview.directory().toString() : null);
        out.put("routeFiles", new JsonArray(overview.files()));
        out.put("fingerprint", overview.fingerprint());
        JsonArray routes = new JsonArray();
        for (Route r : overview.flows()) {
            JsonObject jo = new JsonObject();
            jo.put("route", r.key());
            if (!"route".equals(r.kind())) {
                jo.put("kind", r.kind());
            }
            jo.put("file", r.file() + ":" + r.line());
            if (r.group() != null) {
                jo.put("group", r.group());
            }
            if (r.from() != null) {
                jo.put("from", r.from().uri());
            }
            JsonArray to = new JsonArray();
            r.produces().forEach(e -> to.add(e.uri()));
            if (!to.isEmpty()) {
                jo.put("to", to);
            }
            if (r.hasDescription()) {
                jo.put("description", r.description());
            } else if (summary != null && summary.descriptions().containsKey(r.key())) {
                jo.put("aiDescription", summary.descriptions().get(r.key()));
            }
            if (r.hasNote()) {
                jo.put("note", r.note());
            } else if (summary != null && summary.notes().containsKey(r.key())) {
                jo.put("aiNote", summary.notes().get(r.key()));
            }
            if (r.heuristic()) {
                jo.put("heuristic", true);
            }
            if (!r.decisions().isEmpty()) {
                // the decision points, by their path in the route; aiLabel/aiWhy from the summary (CAMEL-25161)
                JsonArray decisions = new JsonArray();
                for (RouteDecisions.DecisionPoint d : r.decisions()) {
                    JsonObject dj = new JsonObject();
                    dj.put("path", d.path());
                    dj.put("type", d.type());
                    if (d.expression() != null) {
                        dj.put("expression", d.expression());
                    }
                    IntegrationSummary.StepLabel st = summary != null ? summary.step(r.key(), d.path()) : null;
                    if (st != null && st.label() != null) {
                        dj.put("aiLabel", st.label());
                    }
                    if (st != null && st.why() != null) {
                        dj.put("aiWhy", st.why());
                    }
                    decisions.add(dj);
                }
                jo.put("decisions", decisions);
            }
            routes.add(jo);
        }
        out.put("routes", routes);
        JsonArray entry = new JsonArray();
        for (EntryPoint e : overview.entryPoints()) {
            JsonObject jo = new JsonObject();
            jo.put("kind", e.kind());
            jo.put("origin", isInternal(e) ? "internal" : "remote");
            jo.put("label", e.label());
            if (e.route() != null) {
                jo.put("route", e.route());
            }
            entry.add(jo);
        }
        out.put("entryPoints", entry);
        JsonArray links = new JsonArray();
        for (Link l : overview.links()) {
            JsonObject jo = new JsonObject();
            jo.put("from", l.from());
            jo.put("to", l.to());
            jo.put("endpoint", l.endpoint());
            jo.put("kind", l.kind());
            links.add(jo);
        }
        out.put("links", links);
        out.put("systems", systemsJson(overview.systems()));
        JsonArray findings = new JsonArray();
        for (Finding f : overview.findings()) {
            JsonObject jo = new JsonObject();
            jo.put("level", f.level());
            jo.put("kind", f.kind());
            jo.put("message", f.message());
            findings.add(jo);
        }
        out.put("findings", findings);
        out.put("architecture", ProjectCapabilities.toJson(
                ProjectCapabilities.build(overview, summary != null ? summary.ai() : null)));
        JsonObject s = new JsonObject();
        s.put("file", IntegrationSummary.FILE_NAME);
        if (summary == null) {
            s.put("exists", false);
        } else {
            s.put("exists", true);
            s.put("upToDate", overview.fingerprint().equals(summary.fingerprint()));
            if (summary.model() != null) {
                s.put("model", summary.model());
            }
            if (summary.date() != null) {
                s.put("date", summary.date());
            }
            if (summary.overview() != null) {
                s.put("aiOverview", summary.overview());
            }
            if (!summary.capabilities().isEmpty()) {
                JsonArray caps = new JsonArray();
                for (IntegrationSummary.Capability c : summary.capabilities()) {
                    JsonObject jo = new JsonObject();
                    jo.put("name", c.name());
                    jo.put("routes", new JsonArray(c.routes()));
                    jo.put("text", c.text());
                    caps.add(jo);
                }
                s.put("aiCapabilities", caps);
            }
        }
        s.put("note", "Fields starting with ai were written by an AI and are not from the sources: say so when you"
                      + " repeat them");
        out.put("summary", s);
        return out;
    }

    /** The systems grouped by endpoint: which routes read from it and which write to it. */
    static JsonArray systemsJson(List<SystemUse> systems) {
        Map<String, JsonObject> byUri = new LinkedHashMap<>();
        for (SystemUse s : systems) {
            JsonObject jo = byUri.computeIfAbsent(s.uri(), k -> {
                JsonObject o = new JsonObject();
                o.put("category", s.category());
                o.put("name", s.name());
                o.put("uri", s.uri());
                return o;
            });
            String key = "in".equals(s.direction()) ? "readBy" : "writtenBy";
            JsonArray list = jo.containsKey(key) ? (JsonArray) jo.get(key) : new JsonArray();
            if (!list.contains(s.route())) {
                list.add(s.route());
            }
            jo.put(key, list);
        }
        return new JsonArray(byUri.values());
    }
}
