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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.dsl.jbang.core.commands.ai.About;
import org.apache.camel.util.json.JsonObject;

/**
 * How the routes of a running integration reach each other, from its route topology (CAMEL-26823), whatever the
 * component: a route the sender waits for (direct, a request-reply over a JMS queue) is a call, one it hands the
 * message to (seda, a topic, a wire tap) a hand-off, and one it sends to only when it handles a failure (a dead letter
 * queue) an error path. The sources of the integration name the links by their scheme only, and took a hop over a
 * broker for a message from outside: jms-request-reply had its queues as In and Out, and 14 exchanges for 3 orders.
 */
final class RuntimeLinks {

    /** How a route reaches another: call, async (a hand-off) or error (only when it handles a failure). */
    record Link(String from, String to, String endpoint, String how) {
    }

    private static final int MAX_DEPTH = 6;

    private final List<Link> links;

    private RuntimeLinks(List<Link> links) {
        this.links = links;
    }

    List<Link> links() {
        return links;
    }

    /** The links between different routes in the topology of the route-topology console. */
    static RuntimeLinks of(JsonObject topology) {
        List<Link> answer = new ArrayList<>();
        Collection<?> edges = topology != null ? topology.getCollection("edges") : null;
        if (edges != null) {
            for (Object o : edges) {
                if (!(o instanceof JsonObject e)) {
                    continue;
                }
                String from = e.getString("fromRouteId");
                String to = e.getString("toRouteId");
                String endpoint = e.getString("endpoint");
                if (from == null || to == null || endpoint == null || from.equals(to)) {
                    continue;
                }
                // a hand-off unless the route waits for the answer; an edge the TUI adds for the sends the runtime saw
                // (a recipient list) waits, as a call
                String how = "error".equals(e.getString("kind")) ? "error"
                        : Boolean.TRUE.equals(e.get("reply")) || Boolean.TRUE.equals(e.get("observed")) ? "call" : "async";
                answer.add(new Link(from, to, endpoint, how));
            }
        }
        return new RuntimeLinks(answer);
    }

    /**
     * The model of the sources with the links of the runtime: the routes under the route that reaches them, In without
     * an endpoint only the integration sends to (the rest of its messages, from outside, as +N from outside), and Out
     * without one only the integration consumes.
     */
    About.Model apply(About.Model m, IntegrationInfo info) {
        if (links.isEmpty()) {
            return m;
        }
        return new About.Model(m.purpose(), m.purposeFrom(), in(m.in(), info), routes(m.routes()), out(m.out()));
    }

    private List<About.In> in(List<About.In> in, IntegrationInfo info) {
        List<About.In> answer = new ArrayList<>();
        for (About.In e : in) {
            String key = DiagramTopology.endpointKey(e.label());
            Link fed = links.stream()
                    .filter(l -> l.to().equals(e.route()) && key.equals(DiagramTopology.endpointKey(l.endpoint())))
                    .findFirst().orElse(null);
            if (fed == null) {
                answer.add(e);
                continue;
            }
            // what came in beyond what the routes of the integration sent there came from outside
            long outside = hits(info, key, "in", e.route()) - hits(info, key, "out", null);
            if (outside > 0) {
                answer.add(new About.In(e.kind(), e.label() + "  +" + outside + " from outside", e.route()));
            }
        }
        return answer;
    }

    private static long hits(IntegrationInfo info, String key, String direction, String route) {
        long answer = 0;
        for (EndpointInfo ep : info.endpoints) {
            if (direction.equals(ep.direction) && ep.uri != null && (route == null || route.equals(ep.routeId))
                    && key.equals(DiagramTopology.endpointKey(ep.uri))) {
                answer += ep.hits;
            }
        }
        return answer;
    }

    private List<About.Out> out(List<About.Out> out) {
        return out.stream().filter(o -> !consumedHere(o)).toList();
    }

    /** Whether the destination of an out is one a route of the integration consumes from: a hop, no system outside. */
    boolean consumedHere(About.Out o) {
        String key = DiagramTopology.endpointKey(o.uri());
        return links.stream()
                .anyMatch(l -> l.from().equals(o.route()) && key.equals(DiagramTopology.endpointKey(l.endpoint())));
    }

    private List<About.RouteLine> routes(List<About.RouteLine> lines) {
        Map<String, About.RouteLine> byId = new LinkedHashMap<>();
        lines.forEach(r -> byId.putIfAbsent(r.routeId(), r));
        Map<String, List<Link>> from = new HashMap<>();
        Set<String> reached = new LinkedHashSet<>();
        for (Link l : links) {
            if (byId.containsKey(l.from()) && byId.containsKey(l.to())) {
                from.computeIfAbsent(l.from(), k -> new ArrayList<>()).add(l);
                reached.add(l.to());
            }
        }
        List<About.RouteLine> answer = new ArrayList<>();
        Set<String> shown = new LinkedHashSet<>();
        for (String id : byId.keySet()) {
            if (!reached.contains(id)) {
                add(byId.get(id), 0, null, byId, from, shown, answer);
            }
        }
        // what is left: routes reached only from a cycle
        for (String id : byId.keySet()) {
            add(byId.get(id), 0, null, byId, from, shown, answer);
        }
        return answer;
    }

    private static void add(
            About.RouteLine r, int depth, String how, Map<String, About.RouteLine> byId, Map<String, List<Link>> from,
            Set<String> shown, List<About.RouteLine> answer) {
        if (!shown.add(r.routeId()) || depth > MAX_DEPTH) {
            return;
        }
        answer.add(new About.RouteLine(r.routeId(), depth, how, r.from(), r.steps()));
        for (Link l : from.getOrDefault(r.routeId(), List.of())) {
            add(byId.get(l.to()), depth + 1, l.how(), byId, from, shown, answer);
        }
    }

    /** How each route that another one reaches is reached: call, async or error; none for the others. */
    Map<String, String> howReached() {
        Map<String, String> answer = new HashMap<>();
        for (Link l : links) {
            // a route called by one and handed to by another: the call keeps it inside the exchange of its caller
            answer.merge(l.to(), l.how(), (a, b) -> "call".equals(a) || "call".equals(b) ? "call" : a);
        }
        return answer;
    }
}
