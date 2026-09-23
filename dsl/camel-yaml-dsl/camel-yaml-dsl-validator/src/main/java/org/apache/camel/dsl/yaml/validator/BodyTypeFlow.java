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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Error;
import com.networknt.schema.path.NodePath;

import static org.apache.camel.dsl.yaml.validator.RouteGraph.Route;
import static org.apache.camel.dsl.yaml.validator.RouteGraph.endpointOf;
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

    /** Steps that put something in the body, whatever it is. */
    private static final Set<String> SETS_THE_BODY = Set.of("setBody", "transform", "unmarshal", "marshal",
            "convertBodyTo", "convertVariableTo", "poll", "pollEnrich", "enrich", "process", "bean", "to", "toD",
            "recipientList", "serviceCall", "claimCheck", "aggregate", "split", "loadBalance", "removeBody");

    /** The REST verbs that carry no body, so a route they send to starts with none. */
    private static final Set<String> VERBS_WITHOUT_BODY = Set.of("get", "delete", "head");

    /** The consumers that produce no body of their own, so the message reaching the route has none. */
    private static final Set<String> NO_BODY_CONSUMER = Set.of("timer", "quartz", "scheduler", "cron");

    private BodyTypeFlow() {
    }

    static void check(JsonNode target, NodePath path, List<Error> errors) {
        check(target, path, errors, Set.of());
    }

    /**
     * @param known the endpoints the caller knows deliver no body, such as {@code direct:getStock} for the GET
     *              operation of an OpenAPI specification the route binds to (CAMEL-24844 phase B)
     */
    static void check(JsonNode target, NodePath path, List<Error> errors, Set<String> known) {
        List<Route> routes = routes(target);
        if (routes.isEmpty()) {
            return;
        }
        // the graph: which routes send to the endpoint a route starts from
        Map<String, List<Route>> byFrom = new HashMap<>();
        for (Route r : routes) {
            if (r.fromUri() != null) {
                byFrom.computeIfAbsent(normalize(r.fromUri()), k -> new ArrayList<>()).add(r);
            }
        }
        Map<Route, List<Route>> callers = new HashMap<>();
        for (Route caller : routes) {
            for (String uri : sendsTo(caller.steps())) {
                for (Route target2 : byFrom.getOrDefault(normalize(uri), List.of())) {
                    callers.computeIfAbsent(target2, k -> new ArrayList<>()).add(caller);
                }
            }
        }
        Set<String> restless = new HashSet<>(restEndpointsWithoutABody(target));
        for (String uri : known) {
            restless.add(normalize(uri));
        }
        for (Route r : routes) {
            String reader = firstBodyReaderBeforeAnyProducer(r.steps());
            if (reader == null) {
                continue;
            }
            if (!certainlyWithoutABody(r, callers, restless, new HashSet<>())) {
                continue;
            }
            errors.add(Error.builder()
                    .keyword("type")
                    .instanceLocation(path)
                    .messageKey("type")
                    .format(new java.text.MessageFormat("{0}"))
                    .arguments((r.id() != null ? "route " + r.id() + ": " : "") + reader
                               + (READS_THE_BODY.contains(reader) ? " reads the message body" : " works on the message body")
                               + ", and the message reaching this route has none"
                               + (callers.containsKey(r) ? " - the routes that call it do not set one either" : "")
                               + ": read the data first with setBody and constant: resource:file:... for a known"
                               + " file, or poll: for one that is not")
                    .build());
        }
    }

    /**
     * Whether it is certain that no message reaching this route can have a body: every way in starts at a consumer or a
     * REST verb that produces none, and nothing on the way sets one. Anything unknown answers false, which keeps the
     * check quiet.
     */
    private static boolean certainlyWithoutABody(
            Route route, Map<Route, List<Route>> callers, Set<String> restless,
            Set<Route> seen) {
        if (!seen.add(route)) {
            return false; // a cycle: say nothing
        }
        String scheme = scheme(route.fromUri());
        if (scheme == null) {
            return false;
        }
        if (NO_BODY_CONSUMER.contains(scheme)) {
            return true;
        }
        if (!"direct".equals(scheme) && !"seda".equals(scheme) && !"direct-vm".equals(scheme)) {
            return false; // a consumer of its own: it brings whatever it brings
        }
        boolean fromRestWithoutBody = restless.contains(normalize(route.fromUri()));
        List<Route> from = callers.get(route);
        if (from == null || from.isEmpty()) {
            // nothing in the file calls it: only a REST verb that carries no body makes this certain
            return fromRestWithoutBody;
        }
        for (Route caller : from) {
            if (setsTheBodyBeforeSendingTo(caller, route)) {
                return false;
            }
            if (!certainlyWithoutABody(caller, callers, restless, seen)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the caller puts something in the body <em>before</em> it sends to the route. What it does afterwards
     * cannot help: the call has already happened.
     */
    private static boolean setsTheBodyBeforeSendingTo(Route caller, Route target) {
        JsonNode steps = caller.steps();
        if (steps == null || !steps.isArray()) {
            return false;
        }
        String wanted = normalize(target.fromUri());
        for (JsonNode step : steps) {
            for (var it = step.fieldNames(); it.hasNext();) {
                String name = it.next();
                JsonNode value = step.get(name);
                if (sendsToTheRoute(name, value, wanted)) {
                    return false; // reached the call, and nothing before it set the body
                }
                if (producesTheBody(step)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether this step is the call to that route. */
    private static boolean sendsToTheRoute(String name, JsonNode value, String wanted) {
        if (!"to".equals(name) && !"toD".equals(name) && !"enrich".equals(name) && !"wireTap".equals(name)) {
            return false;
        }
        String uri = endpointOf(value);
        return uri != null && normalize(uri).equals(wanted);
    }

    /**
     * What first reads the body when no step before it produced one: the name of an expression language, or of the step
     * itself when the step is the one that works on the body. Null when nothing reads it, or when a step produced a
     * body first.
     */
    private static String firstBodyReaderBeforeAnyProducer(JsonNode steps) {
        if (steps == null || !steps.isArray()) {
            return null;
        }
        for (JsonNode step : steps) {
            String reader = readsTheBody(step);
            boolean produces = producesTheBody(step);
            if (reader != null && !produces) {
                return reader;
            }
            if (reader != null) {
                // the step both reads and produces, as setBody with a jsonpath expression does, or a choice with a
                // branch that sets the body: which comes first cannot be told from the tree, so say nothing
                return "setBody".equals(firstName(step)) ? reader : null;
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
        var it = step.fieldNames();
        return it.hasNext() ? it.next() : "";
    }

    /** What reads the body in this step: an expression language, or the step itself. Null when nothing does. */
    private static String readsTheBody(JsonNode step) {
        for (var it = step.fieldNames(); it.hasNext();) {
            String name = it.next();
            if (STEPS_THAT_NEED_THE_BODY.contains(name)) {
                return name;
            }
            String reader = readerIn(step.get(name));
            if (reader != null) {
                return reader;
            }
        }
        return null;
    }

    /** Whether this step, or anything nested in it, puts something in the body. */
    private static boolean producesTheBody(JsonNode node) {
        if (node == null) {
            return false;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (producesTheBody(child)) {
                    return true;
                }
            }
            return false;
        }
        if (!node.isObject()) {
            return false;
        }
        for (var it = node.fieldNames(); it.hasNext();) {
            String name = it.next();
            if (SETS_THE_BODY.contains(name)) {
                return true;
            }
            if (producesTheBody(node.get(name))) {
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
        for (var it = node.fieldNames(); it.hasNext();) {
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
            for (var it = rest.fieldNames(); it.hasNext();) {
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
