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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * About an integration (CAMEL-26700): what it does at a glance and without an AI, from the analysis of its sources
 * ({@link ProjectOverview}). Its purpose in the words of its author, what comes in, its routes (a route called with
 * direct or seda under the route that calls it) and what goes out. The About screen of the TUI and {@code camel get
 * about} are built from it, so they say the same.
 */
public final class About {

    /**
     * What starts the integration.
     *
     * @param kind  the kind of entry: http, timer, rest, or for a consumer the scheme of what it consumes from
     * @param label the entry as the sources have it, such as timer:orders (period=500)
     * @param route the route it starts
     */
    public record In(String kind, String label, String route) {
    }

    /**
     * A route, in the order to show it: a route that something else in the integration calls comes after its caller,
     * one deeper.
     *
     * @param routeId the route ID (else its key in the analysis)
     * @param depth   how deep under the route that calls it (0 for a route that nothing in the integration calls)
     * @param how     call (direct: and the like, the caller waits), async (seda: and the like), or null at depth 0
     * @param from    the scheme the route consumes from
     * @param steps   the number of steps of the route
     */
    public record RouteLine(String routeId, int depth, String how, String from, int steps) {
    }

    /**
     * A system the integration talks to.
     *
     * @param category  the kind of system, as the catalog groups the component
     * @param uri       the endpoint as the sources have it
     * @param direction out, or in/out for a request-reply
     * @param route     the route that talks to it
     */
    public record Out(String category, String uri, String direction, String route) {
    }

    /**
     * About an integration.
     *
     * @param purpose     what it does, in the words of its author, or null
     * @param purposeFrom where the purpose comes from, or null
     */
    public record Model(String purpose, String purposeFrom, List<In> in, List<RouteLine> routes, List<Out> out) {
    }

    private About() {
    }

    /** About the integration in the directory, from the analysis of its sources. */
    public static Model of(Path dir, ProjectOverview.Overview o) {
        String[] p = purpose(dir, o);
        List<In> in = new ArrayList<>();
        for (ProjectOverview.EntryPoint e : o.entryPoints()) {
            // a consumer by the kind of system it consumes from (its scheme)
            String kind = "consumer".equals(e.kind()) && e.label().indexOf(':') > 0
                    ? e.label().substring(0, e.label().indexOf(':')) : e.kind();
            in.add(new In(kind, e.label(), e.route()));
        }

        // the routes that something else in the integration calls go under their caller
        Map<String, List<ProjectOverview.Link>> calledBy = new LinkedHashMap<>();
        Set<String> called = new LinkedHashSet<>();
        for (ProjectOverview.Link l : o.links()) {
            if (("call".equals(l.kind()) || "async".equals(l.kind())) && !l.from().equals(l.to())) {
                calledBy.computeIfAbsent(l.from(), k -> new ArrayList<>()).add(l);
                called.add(l.to());
            }
        }
        List<RouteLine> routes = new ArrayList<>();
        Set<String> shown = new LinkedHashSet<>();
        for (ProjectRoutes.Route r : o.flows()) {
            if (!called.contains(r.key())) {
                routeLines(o, r, 0, null, calledBy, shown, routes);
            }
        }
        // what is left: routes called only by routes in a cycle
        for (ProjectRoutes.Route r : o.flows()) {
            if (!shown.contains(r.key())) {
                routeLines(o, r, 0, null, calledBy, shown, routes);
            }
        }

        List<Out> out = new ArrayList<>();
        Set<String> systems = new LinkedHashSet<>();
        for (ProjectOverview.SystemUse s : o.systems()) {
            if (!"in".equals(s.direction()) && systems.add(s.uri() + "|" + s.direction())) {
                out.add(new Out(s.category(), s.uri(), s.direction(), s.route()));
            }
        }
        return new Model(p != null ? p[0] : null, p != null ? p[1] : null, in, routes, out);
    }

    private static void routeLines(
            ProjectOverview.Overview o, ProjectRoutes.Route r, int depth, String how,
            Map<String, List<ProjectOverview.Link>> calledBy, Set<String> shown, List<RouteLine> lines) {
        if (!shown.add(r.key()) || depth > 6) {
            return;
        }
        String from = r.from() != null ? r.from().scheme() : r.kind();
        lines.add(new RouteLine(r.id() != null ? r.id() : r.key(), depth, how, from, r.steps()));
        for (ProjectOverview.Link l : calledBy.getOrDefault(r.key(), List.of())) {
            ProjectRoutes.Route target = o.route(l.to());
            if (target != null) {
                routeLines(o, target, depth + 1, l.kind(), calledBy, shown, lines);
            }
        }
    }

    /**
     * What the integration does, in the words of its author: the comment at the top of a route file (the files in the
     * order of the analysis), else the descriptions of the routes that something outside starts. Returns the text and
     * where it comes from, or null.
     */
    public static String[] purpose(Path dir, ProjectOverview.Overview o) {
        for (String file : o.files()) {
            try {
                String text = leadingComment(file, Files.readString(dir.resolve(file), StandardCharsets.UTF_8));
                if (text != null) {
                    return new String[] { text, "from the comment at the top of " + file };
                }
            } catch (IOException e) {
                // an unreadable file has no comment
            }
        }
        List<String> descriptions = new ArrayList<>();
        Set<String> entryRoutes = new LinkedHashSet<>();
        o.entryPoints().forEach(e -> entryRoutes.add(e.route()));
        for (ProjectRoutes.Route r : o.flows()) {
            if (r.hasDescription() && entryRoutes.contains(r.key())) {
                descriptions.add(r.description());
            }
        }
        for (ProjectRoutes.Route r : o.flows()) {
            if (r.hasDescription() && !entryRoutes.contains(r.key()) && descriptions.size() < 2) {
                descriptions.add(r.description());
            }
        }
        if (!descriptions.isEmpty()) {
            return new String[] {
                    String.join(" ", descriptions.subList(0, Math.min(2, descriptions.size()))),
                    "from the descriptions of the routes" };
        }
        return null;
    }

    /**
     * The comment a file starts with: the {@code #} lines of YAML, the Javadoc or {@code //} lines before the class of
     * Java, the first {@code <!-- -->} of XML. A license header is no description.
     */
    public static String leadingComment(String file, String content) {
        String text = null;
        if (file.endsWith(".yaml") || file.endsWith(".yml")) {
            StringBuilder sb = new StringBuilder();
            for (String line : content.lines().toList()) {
                String l = line.strip();
                if (l.startsWith("#")) {
                    sb.append(l.replaceFirst("^#+\\s?", "")).append(' ');
                } else if (!l.isEmpty() || !sb.isEmpty()) {
                    break;
                }
            }
            text = sb.toString();
        } else if (file.endsWith(".java")) {
            int cls = content.indexOf(" class ");
            String before = cls > 0 ? content.substring(0, cls) : content;
            int doc = before.lastIndexOf("/**");
            if (doc >= 0) {
                int end = before.indexOf("*/", doc);
                text = end > 0 ? before.substring(doc + 3, end).replaceAll("(?m)^\\s*\\*\\s?", " ") : null;
            } else {
                StringBuilder sb = new StringBuilder();
                for (String line : before.lines().toList()) {
                    String l = line.strip();
                    if (l.startsWith("//") && !l.startsWith("///") && !l.startsWith("//DEPS")
                            && !l.startsWith("//JAVA")) {
                        sb.append(l.substring(2).strip()).append(' ');
                    }
                }
                text = sb.toString();
            }
        } else if (file.endsWith(".xml")) {
            int start = content.indexOf("<!--");
            int end = start >= 0 ? content.indexOf("-->", start) : -1;
            text = end > 0 ? content.substring(start + 4, end) : null;
        }
        if (text == null) {
            return null;
        }
        // a Javadoc tag ends the description
        int tag = text.indexOf(" @");
        if (tag > 0) {
            text = text.substring(0, tag);
        }
        text = text.replaceAll("\\s+", " ").strip();
        if (text.isEmpty() || text.contains("Licensed to the Apache") || text.contains("SPDX-License")) {
            return null;
        }
        return text.length() > 300 ? text.substring(0, 297) + "…" : text;
    }
}
