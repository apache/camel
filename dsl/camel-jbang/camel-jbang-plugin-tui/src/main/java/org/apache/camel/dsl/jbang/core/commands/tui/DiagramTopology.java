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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * The route topology the diagrams draw: the routes, their steps, the routes they call and the systems they reach, with
 * the messages of each.
 * <p>
 * Its structure changes only when routes are added, removed or reloaded, so it is asked of the integration without
 * numbers, and asked again only then. The numbers come from the status file the TUI reads every second anyway: the
 * totals of each route, and the statistics of each step by its id. Asking for the topology with its numbers every
 * second took a request the connector answers at its next poll, and an answer of hundreds of KB from a big integration.
 */
final class DiagramTopology {

    /** What the route-structure console leaves out of the statistics of the from line, which are the route's. */
    private static final List<String> ROUTE_ONLY_STATISTICS = List.of("coverage", "load01", "load05", "load15");

    /**
     * The structure of the routes of an integration, and which routes they were, with its steps by route and step id.
     */
    private record Structure(
            String pid, int reloaded, List<String> routeIds, JsonObject topology, Map<String, Step> steps) {

        boolean isOf(String pid, int reloaded, List<String> routeIds) {
            return this.pid.equals(pid) && this.reloaded == reloaded && this.routeIds.equals(routeIds);
        }
    }

    /** A step as the route structure has it: its code (to[log:outbox]) and the line of the source it is on. */
    record Step(String code, int line) {
    }

    private volatile Structure structure;

    /**
     * The step of a route by its id, from the structure asked for last (the Routes tab shows what each step does); null
     * until the structure of the integration has been asked for, or when it has no such step.
     */
    Step step(String pid, String routeId, String id) {
        Structure s = structure;
        if (s == null || !s.pid().equals(pid) || routeId == null || id == null) {
            return null;
        }
        return s.steps().get(stepKey(routeId, id));
    }

    /**
     * Asks for the structure when the routes of the integration have changed since it was asked for, as {@link #load}
     * does without making the copy with the numbers. On the background thread only.
     */
    void refreshStructure(MonitorContext ctx, String pid) {
        IntegrationInfo info = integration(ctx, pid);
        if (info == null) {
            return;
        }
        List<String> routeIds = routeIds(info);
        Structure s = structure;
        if (s == null || !s.isOf(pid, info.reloaded, routeIds)) {
            fetch(ctx, pid, info.reloaded, routeIds);
        }
    }

    /**
     * The topology of the integration, with its numbers as of its status file; null when the integration does not
     * answer.
     */
    JsonObject load(MonitorContext ctx, String pid) {
        IntegrationInfo info = integration(ctx, pid);
        if (info == null) {
            // no status to take the numbers from: they come with the structure
            return request(ctx, pid, true);
        }
        List<String> routeIds = routeIds(info);
        Structure s = structure;
        if (s == null || !s.isOf(pid, info.reloaded, routeIds)) {
            s = fetch(ctx, pid, info.reloaded, routeIds);
            if (s == null) {
                return null;
            }
        }
        return withNumbers(s.topology(), info);
    }

    private static List<String> routeIds(IntegrationInfo info) {
        return info.routes.stream().map(r -> r.routeId).filter(Objects::nonNull).sorted().toList();
    }

    /**
     * Asks for the structure, one tab at a time: the Routes and Diagram tabs load together, and the second takes what
     * the first was answered.
     */
    private synchronized Structure fetch(MonitorContext ctx, String pid, int reloaded, List<String> routeIds) {
        Structure s = structure;
        if (s != null && s.isOf(pid, reloaded, routeIds)) {
            return s;
        }
        JsonObject topology = request(ctx, pid, false);
        if (topology == null) {
            return null;
        }
        s = new Structure(pid, reloaded, routeIds, topology, steps(topology));
        structure = s;
        return s;
    }

    /** The steps of the routes of the topology by route and step id, with their code unescaped as the diagrams do. */
    private static Map<String, Step> steps(JsonObject topology) {
        Map<String, Step> answer = new HashMap<>();
        Collection<?> routes = topology.getCollection("routes");
        if (routes == null) {
            return answer;
        }
        for (Object r : routes) {
            if (r instanceof JsonObject route && route.getCollection("code") != null) {
                String routeId = route.getString("routeId");
                for (Object o : route.getCollection("code")) {
                    if (o instanceof JsonObject line && routeId != null && line.getString("id") != null) {
                        String code = line.getString("code");
                        answer.put(stepKey(routeId, line.getString("id")),
                                new Step(code != null ? Jsoner.unescape(code) : null, line.getIntegerOrDefault("line", 0)));
                    }
                }
            }
        }
        return answer;
    }

    private static String stepKey(String routeId, String id) {
        return routeId + '/' + id;
    }

    /** Asks the integration for its topology, with the structure of its routes and the systems they reach. */
    private static JsonObject request(MonitorContext ctx, String pid, boolean metric) {
        JsonObject root = new JsonObject();
        root.put("action", "route-topology");
        root.put("metric", Boolean.toString(metric));
        root.put("external", "true");
        root.put("routes", "true");
        return ctx.executeAction(pid, root, 5000);
    }

    /**
     * A copy of the topology with the numbers of the status of the integration where the route-topology console puts
     * them; the topology is left as it is, for the next time.
     */
    static JsonObject withNumbers(JsonObject topology, IntegrationInfo info) {
        Map<String, RouteInfo> routes = new HashMap<>();
        for (RouteInfo r : info.routes) {
            routes.put(r.routeId, r);
        }
        JsonObject answer = new JsonObject(topology);
        copyEach(answer, "nodes", node -> {
            RouteInfo r = routes.get(node.getString("routeId"));
            if (r != null) {
                node.put("exchangesTotal", r.total);
                node.put("exchangesFailed", r.failed);
            }
        });
        copyEach(answer, "externalEndpoints", endpoint -> {
            RouteInfo r = routes.get(endpoint.getString("routeId"));
            if (r != null) {
                addEndpointNumbers(endpoint, r);
            }
        });
        copyEach(answer, "routes", route -> {
            RouteInfo r = routes.get(route.getString("routeId"));
            if (r != null) {
                addStepStatistics(route, r);
            }
        });
        return answer;
    }

    /**
     * A system the route reads from has the messages of the route; one it sends to has those of the steps of the route
     * that send to it.
     */
    private static void addEndpointNumbers(JsonObject endpoint, RouteInfo route) {
        if ("in".equals(endpoint.getString("direction"))) {
            endpoint.put("exchangesTotal", route.total);
            endpoint.put("exchangesFailed", route.failed);
            return;
        }
        String uri = endpoint.getString("uri");
        if (uri == null) {
            return;
        }
        String key = endpointKey(uri);
        long total = 0;
        long failed = 0;
        boolean sends = false;
        for (ProcessorInfo p : route.processors) {
            if (p.uri != null && key.equals(endpointKey(p.uri))) {
                total += p.total;
                failed += p.failed;
                sends = true;
            }
        }
        if (sends) {
            endpoint.put("exchangesTotal", total);
            endpoint.put("exchangesFailed", failed);
        }
    }

    /**
     * The statistics of each line of the route's code: the first two lines (the route and its from) have the route's,
     * the others those of their step, with the body types the step has been reached with so far (CAMEL-25750): the
     * structure has those it was asked with.
     */
    private static void addStepStatistics(JsonObject route, RouteInfo info) {
        Collection<?> code = route.getCollection("code");
        if (code == null) {
            return;
        }
        Map<String, Map<String, ?>> steps = new HashMap<>();
        Map<String, ProcessorInfo> bodies = new HashMap<>();
        for (ProcessorInfo p : info.processors) {
            if (p.id != null && p.statistics != null) {
                steps.put(p.id, p.statistics);
            }
            if (p.id != null && !p.bodyTypes.isEmpty()) {
                bodies.put(p.id, p);
            }
        }
        JsonArray lines = new JsonArray();
        int index = 0;
        for (Object o : code) {
            if (o instanceof Map<?, ?> m) {
                JsonObject line = copy(m);
                Map<String, ?> statistics = switch (index) {
                    case 0 -> info.statistics;
                    case 1 -> fromStatistics(info.statistics);
                    default -> steps.get(line.getString("id"));
                };
                if (statistics != null) {
                    line.put("statistics", statistics instanceof JsonObject jo ? jo : new JsonObject(statistics));
                }
                ProcessorInfo body = index > 1 ? bodies.get(line.getString("id")) : null;
                if (body != null) {
                    line.put("bodyTypes", new JsonArray(body.bodyTypes));
                    line.put("bodyKinds", new JsonArray(body.bodyKinds));
                }
                lines.add(line);
            } else {
                lines.add(o);
            }
            index++;
        }
        route.put("code", lines);
    }

    private static Map<String, ?> fromStatistics(Map<String, ?> route) {
        if (route == null) {
            return null;
        }
        JsonObject answer = new JsonObject(route);
        ROUTE_ONLY_STATISTICS.forEach(answer::remove);
        return answer;
    }

    /** The endpoint without its options, and without the // after the scheme, as the console matches them. */
    static String endpointKey(String uri) {
        String answer = DiagramSupport.stripQueryParams(uri);
        int idx = answer.indexOf("://");
        return idx > 0 ? answer.substring(0, idx + 1) + answer.substring(idx + 3) : answer;
    }

    /** Puts copies of the objects of the array under the key in their place, changed. */
    private static void copyEach(JsonObject topology, String key, Consumer<JsonObject> change) {
        Collection<?> array = topology.getCollection(key);
        if (array == null) {
            return;
        }
        JsonArray copies = new JsonArray();
        for (Object o : array) {
            if (o instanceof Map<?, ?> m) {
                JsonObject copy = copy(m);
                change.accept(copy);
                copies.add(copy);
            } else {
                copies.add(o);
            }
        }
        topology.put(key, copies);
    }

    private static JsonObject copy(Map<?, ?> map) {
        JsonObject answer = new JsonObject();
        map.forEach((k, v) -> answer.put(String.valueOf(k), v));
        return answer;
    }

    private static IntegrationInfo integration(MonitorContext ctx, String pid) {
        for (IntegrationInfo info : ctx.data.get()) {
            if (pid.equals(info.pid) && !info.vanishing) {
                return info;
            }
        }
        return null;
    }
}
