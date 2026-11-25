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
package org.apache.camel.dsl.yaml.validator;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.networknt.schema.Error;
import com.networknt.schema.path.NodePath;
import tools.jackson.databind.JsonNode;

import static org.apache.camel.dsl.yaml.validator.RouteGraph.REST_VERBS;
import static org.apache.camel.dsl.yaml.validator.RouteGraph.Route;
import static org.apache.camel.dsl.yaml.validator.RouteGraph.endpointOf;
import static org.apache.camel.dsl.yaml.validator.RouteGraph.from;
import static org.apache.camel.dsl.yaml.validator.RouteGraph.normalize;
import static org.apache.camel.dsl.yaml.validator.RouteGraph.routes;
import static org.apache.camel.dsl.yaml.validator.RouteGraph.scheme;
import static org.apache.camel.dsl.yaml.validator.RouteGraph.sendsTo;

/**
 * Where the body comes from, across the routes of a file.
 * <p/>
 * A step that reads the message body - a jsonpath, jq or xpath expression - fails at runtime when there is no body. A
 * route reached with {@code direct:} has the body of its caller, so the question is not answered inside one route: the
 * routes of the file form a graph through their {@code direct:} and {@code seda:} endpoints, the way
 * {@code DefaultRouteTopologyDumper} builds it from the route definitions at runtime, and the answer follows the edges
 * (CAMEL-24844).
 * <p/>
 * This first pass reports one thing, and only when it is certain: a route that reads the body although nothing in it,
 * or in any route that calls it, ever sets one. It does not claim to know the type - a POST carries a body that no
 * route sets - it reports that the file itself never produces one.
 */
final class BodyTypeFlow {

    /** The expressions that read the message body and fail when there is none. */
    private static final Set<String> READS_THE_BODY = Set.of("jsonpath", "jq", "xpath", "xquery", "xtokenize");

    /** The steps that work on the body itself, and have nothing to work on when there is none. */
    private static final Set<String> STEPS_THAT_NEED_THE_BODY = Set.of("unmarshal", "marshal", "convertBodyTo");

    /** The REST verbs that carry no body, so a route they send to starts with none. */
    private static final Set<String> VERBS_WITHOUT_BODY = Set.of("get", "delete", "head");

    private BodyTypeFlow() {
    }

    /** The components of the routes a route calls with its own message. */
    private static final Set<String> ROUTE_CALLS = Set.of("direct", "seda", "direct-vm");

    /**
     * Where the body of each route of the file comes from, with the message of the check for a route that reads a body
     * it cannot have: what {@link #report} reports, and what {@code camel overview} shows (CAMEL-25200).
     *
     * @param known     the endpoints the caller knows deliver no body, such as {@code direct:getStock} for the GET
     *                  operation of an OpenAPI specification the route binds to (CAMEL-24844 phase B)
     * @param bodyTypes the body types of the catalog, which say the consumers that give no body (timer, cron)
     */
    static List<BodyOrigin> analyze(JsonNode target, Set<String> known, CatalogBodyTypes bodyTypes) {
        List<Route> routes = routes(target);
        List<BodyOrigin> answer = new ArrayList<>();
        if (routes.isEmpty()) {
            return answer;
        }
        Graph graph = new Graph(target, routes, known, bodyTypes);
        for (int i = 0; i < routes.size(); i++) {
            answer.add(graph.origin(i, routes.get(i)));
        }
        return answer;
    }

    /** Adds the message of each route that reads a body it cannot have, as {@link #analyze} found them. */
    static void report(List<BodyOrigin> origins, NodePath path, List<Error> errors) {
        for (BodyOrigin origin : origins) {
            if (origin.problem() != null) {
                errors.add(Error.builder()
                        .keyword("type")
                        .instanceLocation(path)
                        .messageKey("type")
                        .format(new MessageFormat("{0}"))
                        .arguments(origin.problem())
                        .build());
            }
        }
    }

    /** The routes of a file and the ways a message reaches each of them. */
    private static final class Graph {

        private final Map<Route, List<Route>> callers = new HashMap<>();
        private final Map<String, String> rest;
        private final Set<String> restless;
        private final Set<String> known;
        private final Map<String, List<RouteGraph.ErrorPath>> errorPaths = new HashMap<>();
        private final CatalogBodyTypes bodyTypes;

        Graph(JsonNode target, List<Route> routes, Set<String> known, CatalogBodyTypes bodyTypes) {
            this.bodyTypes = bodyTypes;
            // the graph: which routes send to the endpoint a route starts from
            Map<String, List<Route>> byFrom = new HashMap<>();
            for (Route r : routes) {
                if (r.fromUri() != null) {
                    byFrom.computeIfAbsent(normalize(r.fromUri()), k -> new ArrayList<>()).add(r);
                }
            }
            for (Route caller : routes) {
                for (String uri : sendsTo(caller.steps())) {
                    for (Route target2 : byFrom.getOrDefault(normalize(uri), List.of())) {
                        callers.computeIfAbsent(target2, k -> new ArrayList<>()).add(caller);
                    }
                }
            }
            this.rest = restOperations(target);
            this.restless = new HashSet<>(restEndpointsWithoutABody(target));
            this.known = new HashSet<>();
            for (String uri : known) {
                this.restless.add(normalize(uri));
                this.known.add(normalize(uri));
            }
            for (RouteGraph.ErrorPath p : RouteGraph.errorPaths(target)) {
                errorPaths.computeIfAbsent(p.endpoint(), k -> new ArrayList<>()).add(p);
            }
        }

        BodyOrigin origin(int index, Route r) {
            boolean noBody = certainlyWithoutABody(r, new HashSet<>());
            String reader = firstBodyReaderBeforeAnyProducer(r.steps(), bodyTypes);
            String problem = reader != null && noBody ? problem(r, reader) : null;
            String setBy = null;
            int setAt = 0;
            if (noBody && r.steps() != null && r.steps().isArray()) {
                for (int i = 0; i < r.steps().size(); i++) {
                    if (producesTheBody(r.steps().get(i), bodyTypes)) {
                        setBy = firstName(r.steps().get(i));
                        setAt = i + 1;
                        break;
                    }
                }
            }
            String key = normalize(r.fromUri());
            String origin;
            String via = null;
            List<String> who = List.of();
            String scheme = scheme(r.fromUri());
            if (scheme == null) {
                // a from without an endpoint: nothing to say where the body comes from
                origin = BodyOrigin.UNKNOWN;
            } else if (!ROUTE_CALLS.contains(scheme)) {
                origin = BodyOrigin.CONSUMER;
            } else if (callers.containsKey(r)) {
                origin = BodyOrigin.CALLER;
                via = key;
                // a caller without an id by its endpoint, without the options, which may carry credentials
                who = callers.get(r).stream().map(c -> c.id() != null ? c.id() : normalize(c.fromUri())).distinct()
                        .toList();
            } else if (rest.containsKey(key) || known.contains(key)) {
                origin = BodyOrigin.REST;
                via = rest.get(key);
            } else if (errorPaths.containsKey(key)) {
                List<RouteGraph.ErrorPath> paths = errorPaths.get(key);
                via = paths.get(0).kind();
                origin = switch (via) {
                    case "onCompletion" -> BodyOrigin.ON_COMPLETION;
                    case "compensation", "completion" -> BodyOrigin.SAGA;
                    default -> BodyOrigin.ERROR_HANDLER;
                };
                String kind = via;
                who = paths.stream().filter(p -> kind.equals(p.kind()) && p.route() != null)
                        .map(RouteGraph.ErrorPath::route).distinct().toList();
            } else {
                origin = BodyOrigin.UNKNOWN;
            }
            return new BodyOrigin(index, r.id(), r.fromUri(), origin, via, who, noBody, setBy, setAt, problem);
        }

        private String problem(Route r, String reader) {
            return (r.id() != null ? "route " + r.id() + ": " : "") + reader
                   + (READS_THE_BODY.contains(reader) ? " reads the message body" : " works on the message body")
                   + ", and the message reaching this route has none"
                   + (callers.containsKey(r) ? " - the routes that call it do not set one either" : "")
                   + ": read the data first with setBody and constant: resource:file:... for a known"
                   + " file, or poll: for one that is not";
        }

        /**
         * Whether it is certain that no message reaching this route can have a body: every way in starts at a consumer
         * or a REST verb that produces none, and nothing on the way sets one. Anything unknown answers false, which
         * keeps the check quiet.
         */
        private boolean certainlyWithoutABody(Route route, Set<Route> seen) {
            if (!seen.add(route)) {
                return false; // a cycle: say nothing
            }
            String scheme = scheme(route.fromUri());
            if (scheme == null) {
                return false;
            }
            // with variableReceive the message goes into the variable, and the route starts with no body
            if (bodyTypes.startsWithNoBody(from(route.node()))) {
                return true;
            }
            // a consumer that produces no body of its own, as the catalog says: timer, cron, scheduler, quartz
            CatalogBodyTypes.Answer out = bodyTypes.consumerOut(route.fromUri(), from(route.node()));
            if (out != null && out.none()) {
                return true;
            }
            if (!ROUTE_CALLS.contains(scheme)) {
                return false; // a consumer of its own: it brings whatever it brings
            }
            String key = normalize(route.fromUri());
            if (errorPaths.containsKey(key)) {
                // the failed or completed message, which has the body of wherever the route was: not known here
                return false;
            }
            boolean fromRestWithoutBody = restless.contains(key);
            List<Route> from = callers.get(route);
            if (from == null || from.isEmpty()) {
                // nothing in the file calls it: only a REST verb that carries no body makes this certain
                return fromRestWithoutBody;
            }
            for (Route caller : from) {
                if (setsTheBodyBeforeSendingTo(caller, route, bodyTypes)) {
                    return false;
                }
                if (!certainlyWithoutABody(caller, seen)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Whether the caller puts something in the body <em>before</em> it sends to the route. What it does afterwards
     * cannot help: the call has already happened.
     */
    private static boolean setsTheBodyBeforeSendingTo(Route caller, Route target, CatalogBodyTypes bodyTypes) {
        JsonNode steps = caller.steps();
        if (steps == null || !steps.isArray()) {
            return false;
        }
        String wanted = normalize(target.fromUri());
        for (JsonNode step : steps) {
            for (var it = step.propertyNames().iterator(); it.hasNext();) {
                String name = it.next();
                JsonNode value = step.get(name);
                if (sendsToTheRoute(name, value, wanted)) {
                    return false; // reached the call, and nothing before it set the body
                }
                if (producesTheBody(step, bodyTypes)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether this step is the call to that route. */
    private static boolean sendsToTheRoute(String name, JsonNode value, String wanted) {
        if (!RouteGraph.CALLS_A_ROUTE.contains(name)) {
            return false;
        }
        String uri = EndpointConsumers.uriOf(name, value);
        return uri != null && normalize(uri).equals(wanted);
    }

    /**
     * What first reads the body when no step before it produced one: the name of an expression language, or of the step
     * itself when the step is the one that works on the body. Null when nothing reads it, or when a step produced a
     * body first.
     */
    private static String firstBodyReaderBeforeAnyProducer(JsonNode steps, CatalogBodyTypes bodyTypes) {
        if (steps == null || !steps.isArray()) {
            return null;
        }
        for (JsonNode step : steps) {
            String reader = readsTheBody(step, bodyTypes);
            boolean produces = producesTheBody(step, bodyTypes);
            if (reader != null && !produces) {
                return reader;
            }
            if (reader != null) {
                // the step both reads and produces, as setBody with a jsonpath expression does, or a choice with a
                // branch that sets the body: which comes first cannot be told from the tree, so say nothing. Not so
                // for a setBody, which evaluates its expression first, nor for an unmarshal, a marshal or a
                // convertBodyTo, which read the body to make the new one
                String first = firstName(step);
                return "setBody".equals(first) || first.equals(reader) ? reader : null;
            }
            if (produces) {
                // a branch of a choice, a doTry or a split may set the body: from here on nothing is certain
                return null;
            }
        }
        return null;
    }

    /** The first key of a step, which is the EIP it is. */
    private static String firstName(JsonNode step) {
        var it = step.propertyNames().iterator();
        return it.hasNext() ? it.next() : "";
    }

    /**
     * What reads the body in this step: an expression language, or the step itself, unless it works on a variable
     * (variableSend). Null when nothing does.
     */
    private static String readsTheBody(JsonNode step, CatalogBodyTypes bodyTypes) {
        for (var it = step.propertyNames().iterator(); it.hasNext();) {
            String name = it.next();
            if (STEPS_THAT_NEED_THE_BODY.contains(name) && !bodyTypes.needsNoBody(name, step.get(name))) {
                return name;
            }
            String reader = readerIn(step.get(name));
            if (reader != null) {
                return reader;
            }
        }
        return null;
    }

    /**
     * Whether this step, or anything nested in it, may put something in the body, as the body types of the EIPs in the
     * catalog say.
     */
    private static boolean producesTheBody(JsonNode node, CatalogBodyTypes bodyTypes) {
        if (node == null) {
            return false;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (producesTheBody(child, bodyTypes)) {
                    return true;
                }
            }
            return false;
        }
        if (!node.isObject()) {
            return false;
        }
        for (var it = node.propertyNames().iterator(); it.hasNext();) {
            String name = it.next();
            if (bodyTypes.isEip(name) && bodyTypes.maySetTheBody(name)
                    && !bodyTypes.keepsTheBody(name, node.get(name))) {
                return true;
            }
            if (producesTheBody(node.get(name), bodyTypes)) {
                return true;
            }
        }
        return false;
    }

    /** The name of a body-reading language used in this node, at any depth, or null. */
    private static String readerIn(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String found = readerIn(child);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (!node.isObject()) {
            return null;
        }
        for (var it = node.propertyNames().iterator(); it.hasNext();) {
            String name = it.next();
            if (READS_THE_BODY.contains(name)) {
                return name;
            }
            String found = readerIn(node.get(name));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The REST operations of the file by the endpoint they send to: GET /stock/{id} for direct:getStock. */
    private static Map<String, String> restOperations(JsonNode target) {
        Map<String, String> answer = new LinkedHashMap<>();
        if (target == null || !target.isArray()) {
            return answer;
        }
        for (JsonNode entry : target) {
            JsonNode rest = entry.isObject() ? entry.get("rest") : null;
            if (rest == null || !rest.isObject()) {
                continue;
            }
            String base = rest.has("path") ? rest.get("path").asString("") : "";
            for (var it = rest.propertyNames().iterator(); it.hasNext();) {
                String verb = it.next();
                if (!REST_VERBS.contains(verb)) {
                    continue;
                }
                JsonNode value = rest.get(verb);
                for (JsonNode operation : value.isArray() ? value : List.of(value)) {
                    String uri = operation.isObject() ? endpointOf(operation.get("to")) : null;
                    if (uri != null) {
                        String path = operation.has("path") ? operation.get("path").asString("") : "";
                        answer.putIfAbsent(normalize(uri), verb.toUpperCase(Locale.ROOT) + " " + joinPath(base, path));
                    }
                }
            }
        }
        return answer;
    }

    private static String joinPath(String base, String path) {
        if (base.isEmpty()) {
            return path.isEmpty() ? "/" : path;
        }
        if (path.isEmpty()) {
            return base;
        }
        if (base.endsWith("/") && path.startsWith("/")) {
            return base + path.substring(1);
        }
        return base.endsWith("/") || path.startsWith("/") ? base + path : base + "/" + path;
    }

    /** The endpoints a REST verb without a body sends to, such as a get: that routes to direct:getStock. */
    private static Set<String> restEndpointsWithoutABody(JsonNode target) {
        Set<String> answer = new HashSet<>();
        if (target == null || !target.isArray()) {
            return answer;
        }
        for (JsonNode entry : target) {
            JsonNode rest = entry.isObject() ? entry.get("rest") : null;
            if (rest == null || !rest.isObject()) {
                continue;
            }
            for (var it = rest.propertyNames().iterator(); it.hasNext();) {
                String verb = it.next();
                if (!VERBS_WITHOUT_BODY.contains(verb)) {
                    continue;
                }
                JsonNode value = rest.get(verb);
                for (JsonNode operation : value.isArray() ? value : List.of(value)) {
                    String uri = endpointOf(operation.get("to"));
                    if (uri != null) {
                        answer.add(normalize(uri));
                    }
                }
            }
        }
        return answer;
    }

}
