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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.apache.camel.dsl.jbang.core.commands.ai.ProjectCapabilities;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectCapabilities.Capabilities;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectCapabilities.Group;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectCapabilities.GroupLink;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectOverview;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.FlowAnimation.Reading;
import org.apache.camel.dsl.jbang.core.commands.tui.diagram.FlowFeed;

/**
 * The messages moving between the groups of the architecture, from the counts of the running routes: along a link, the
 * messages into the routes of one group from the routes of the other; into the error handling, in red, the failures of
 * the routes of a group; from an external system into a group and out to one, the messages of the endpoint. The
 * architecture is read from the source, so a source route counts only when it is matched to a running route (by its id,
 * or as {@link RouteKeys} matches a route without one).
 */
final class ArchitectureFlow {

    private ArchitectureFlow() {
    }

    /**
     * The feed of the dots of the architecture, or null when the integration does not run the routes of the project.
     *
     * @param endpoints the endpoints of the running routes, with their messages
     * @param runningId the running id of a source route key
     */
    static FlowFeed feed(
            ProjectOverview.Overview overview, Capabilities caps, List<RouteInfo> routes, List<EndpointInfo> endpoints,
            UnaryOperator<String> runningId) {
        if (overview == null || caps == null || routes == null || routes.isEmpty()) {
            return null;
        }
        Map<String, RouteInfo> byId = new HashMap<>();
        routes.forEach(r -> byId.put(r.routeId, r));
        // the running route of each source route key
        Map<String, RouteInfo> running = new HashMap<>();
        for (String key : caps.groupOf().keySet()) {
            RouteInfo r = byId.get(runningId.apply(key));
            if (r != null) {
                running.put(key, r);
            }
        }
        if (running.isEmpty()) {
            return null;
        }
        Map<String, Reading> readings = new HashMap<>();
        running.forEach((key, r) -> readings.put("route/" + key, new Reading(r, r.total, r.failed, 0)));
        Map<String, FlowFeed.Edge> edges = new HashMap<>();
        links(overview, caps, running, readings, edges);
        failures(caps, running, readings, edges);
        systems(overview, caps, running, endpoints != null ? endpoints : List.of(), readings, edges);
        return new FlowFeed(readings, edges);
    }

    /** Along a link of two groups: the messages into the routes of the one from the routes of the other. */
    private static void links(
            ProjectOverview.Overview overview, Capabilities caps, Map<String, RouteInfo> running,
            Map<String, Reading> readings, Map<String, FlowFeed.Edge> edges) {
        Map<String, Set<String>> targets = new LinkedHashMap<>();
        Map<String, Set<String>> senders = new LinkedHashMap<>();
        for (ProjectOverview.Link l : overview.links()) {
            String from = caps.groupOf().get(l.from());
            String to = caps.groupOf().get(l.to());
            if (l.onError() || from == null || to == null || from.equals(to)
                    || ProjectCapabilities.ERRORS.equals(to) || !running.containsKey(l.to())) {
                continue;
            }
            String id = FlowFeed.edgeId(from, to);
            targets.computeIfAbsent(id, k -> new LinkedHashSet<>()).add(l.to());
            if (running.containsKey(l.from())) {
                senders.computeIfAbsent(id, k -> new LinkedHashSet<>()).add("route/" + l.from());
            }
        }
        targets.forEach((id, keys) -> {
            long total = 0;
            long failed = 0;
            for (String key : keys) {
                total += running.get(key).total;
                failed += running.get(key).failed;
            }
            // a reload brings new routes: the first of them tells a new reading from the frames in between
            readings.put("link/" + id, new Reading(running.get(keys.iterator().next()), total, failed, 0));
            edges.put(id, new FlowFeed.Edge("link/" + id, List.copyOf(senders.getOrDefault(id, Set.of()))));
        });
    }

    /**
     * From an external system into a group, and from a group out to one, as the boxes of the systems link them. In: the
     * messages of the route that consumes from it, as a route gets its messages from its one consumer (a polling
     * consumer, such as a file one, counts no hits of its endpoint). Out: the messages sent to the endpoint as the
     * integration counts them, else of the route that sends to it.
     */
    private static void systems(
            ProjectOverview.Overview overview, Capabilities caps, Map<String, RouteInfo> running,
            List<EndpointInfo> endpoints, Map<String, Reading> readings, Map<String, FlowFeed.Edge> edges) {
        Map<String, Long> totals = new LinkedHashMap<>();
        Map<String, Object> sources = new HashMap<>();
        for (ProjectOverview.SystemUse use : overview.systems()) {
            String group = caps.groupOf().get(use.route());
            RouteInfo route = running.get(use.route());
            if (group == null || route == null) {
                continue;
            }
            boolean in = "in".equals(use.direction());
            String system = ArchitectureView.systemId(use.name(), in);
            String id = in ? FlowFeed.edgeId(system, group) : FlowFeed.edgeId(group, system);
            EndpointInfo endpoint = in ? null : endpoint(endpoints, use, route.routeId);
            totals.merge(id, endpoint != null ? endpoint.hits : route.total, Long::sum);
            // a reload brings new endpoints and routes: a new reading
            sources.putIfAbsent(id, endpoint != null ? endpoint : route);
        }
        totals.forEach((id, total) -> {
            String key = "system/" + id;
            readings.put(key, new Reading(sources.get(id), total, 0, 0));
            edges.put(id, new FlowFeed.Edge(key, List.of()));
        });
    }

    /** The running endpoint of a use of a system: the same scheme and path, direction and route. */
    private static EndpointInfo endpoint(List<EndpointInfo> endpoints, ProjectOverview.SystemUse use, String routeId) {
        String key = endpointKey(use.uri());
        for (EndpointInfo e : endpoints) {
            if (routeId.equals(e.routeId) && use.direction().equals(e.direction) && e.uri != null
                    && key.equals(endpointKey(e.uri))) {
                return e;
            }
        }
        return null;
    }

    /** The scheme and path of an endpoint uri, as the source and the running integration write it alike. */
    static String endpointKey(String uri) {
        int q = uri.indexOf('?');
        String u = q >= 0 ? uri.substring(0, q) : uri;
        int c = u.indexOf(':');
        if (c < 0) {
            return u;
        }
        String path = u.substring(c + 1);
        return u.substring(0, c).toLowerCase(Locale.ROOT) + ":" + (path.startsWith("//") ? path.substring(2) : path);
    }

    /** Into the error handling: the failures of the routes of a group, handled or not, in red. */
    private static void failures(
            Capabilities caps, Map<String, RouteInfo> running, Map<String, Reading> readings,
            Map<String, FlowFeed.Edge> edges) {
        Map<String, Group> groups = new HashMap<>();
        caps.groups().forEach(g -> groups.put(g.id(), g));
        for (GroupLink link : caps.links()) {
            Group g = groups.get(link.from());
            if (!ProjectCapabilities.ERRORS.equals(link.to()) || g == null) {
                continue;
            }
            long failures = 0;
            RouteInfo first = null;
            for (String key : g.routes()) {
                RouteInfo r = running.get(key);
                if (r != null) {
                    failures += r.failed + r.failuresHandled;
                    first = first == null ? r : first;
                }
            }
            if (first != null) {
                String key = "errors/" + g.id();
                readings.put(key, new Reading(first, failures, failures, 0));
                edges.put(FlowFeed.edgeId(g.id(), ProjectCapabilities.ERRORS), new FlowFeed.Edge(key, List.of()));
            }
        }
    }
}
