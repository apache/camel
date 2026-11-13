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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

/**
 * The routes of a file and the endpoints between them: what the route topology is at runtime, read from the source.
 * <p/>
 * {@code DefaultRouteTopologyDumper} builds the same graph from the route definitions of a running context, by indexing
 * each route's input and matching the outputs against it. Here the routes are still text, so an endpoint has to be
 * recognised in both the forms the YAML DSL allows (CAMEL-24844).
 */
final class RouteGraph {

    /** The steps that send a message to an endpoint. */
    static final Set<String> SENDS = Set.of("to", "toD", "enrich", "pollEnrich", "wireTap");

    private RouteGraph() {
    }

    /** One route of the file: where it starts, what it does, and the node it was read from. */
    record Route(String id, String fromUri, JsonNode steps, JsonNode node) {
    }

    /** The from of a route entry, in both the canonical and the short form; null for another entry. */
    static JsonNode from(JsonNode entry) {
        if (entry == null || !entry.isObject()) {
            return null;
        }
        JsonNode route = entry.get("route");
        return route != null ? route.get("from") : entry.get("from");
    }

    /** The routes of the file, in both the canonical and the short form. */
    static List<Route> routes(JsonNode target) {
        List<Route> answer = new ArrayList<>();
        if (target == null || !target.isArray()) {
            return answer;
        }
        for (JsonNode entry : target) {
            if (!entry.isObject()) {
                continue;
            }
            JsonNode route = entry.get("route");
            JsonNode from = route != null ? route.get("from") : entry.get("from");
            if (from == null) {
                continue;
            }
            String id = route != null && route.has("id") ? route.get("id").asString("") : null;
            JsonNode steps = from.get("steps");
            if (steps == null && route != null) {
                steps = route.get("steps");
            }
            answer.add(new Route(id, endpointOf(from), steps, entry));
        }
        return answer;
    }

    /**
     * The endpoint a node means, whether its path is in the uri or in the parameters: {@code uri: direct} with
     * {@code parameters: {name: lookup}} is the endpoint {@code direct:lookup}, which is how the YAML DSL lets an
     * endpoint be written and how it is often written.
     */
    static String endpointOf(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isString()) {
            return node.asString("");
        }
        if (!node.isObject() || !node.has("uri")) {
            return null;
        }
        String uri = node.get("uri").asString("");
        if (uri.indexOf(':') > 0) {
            return uri;
        }
        JsonNode parameters = node.get("parameters");
        if (parameters == null || !parameters.isObject()) {
            return uri;
        }
        for (String key : new String[] { "name", "destinationName", "topic", "queue", "path", "address" }) {
            if (parameters.has(key) && parameters.get(key).isValueNode()) {
                return uri + ":" + parameters.get(key).asString("");
            }
        }
        return uri;
    }

    /** The endpoints a route sends to, at any depth: to, toD and the enrich family. */
    static List<String> sendsTo(JsonNode steps) {
        List<String> answer = new ArrayList<>();
        collect(steps, answer);
        return answer;
    }

    private static void collect(JsonNode node, List<String> answer) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collect(child, answer);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        for (var it = node.propertyNames().iterator(); it.hasNext();) {
            String name = it.next();
            JsonNode value = node.get(name);
            if (SENDS.contains(name)) {
                String uri = endpointOf(value);
                if (uri != null) {
                    answer.add(uri);
                }
            }
            collect(value, answer);
        }
    }

    /** The verbs of the REST DSL, each a list of operations under rest:. */
    private static final List<String> REST_VERBS = List.of("get", "post", "put", "delete", "patch", "head");

    /** A path parameter of a REST path: id in /orders/{id}. */
    private static final Pattern PATH_PARAMETER = Pattern.compile("\\{([A-Za-z_][\\w.-]*)}");

    /** The components of the routes a route calls with its own message, headers and all. */
    private static final Set<String> CALLS = Set.of("direct", "seda", "direct-vm");

    /**
     * The path parameters of the REST operations that reach each route of the file, by the endpoint the route starts
     * from: a get with path /{id} and to: direct:get-order gives the route of direct:get-order the header id, and so
     * the direct: and seda: routes it calls with that message. The operations of the other files of the project, and of
     * an OpenAPI specification the file binds to, come in from the caller by the endpoint they call.
     */
    static Map<String, Set<String>> pathParameters(JsonNode target, Map<String, Set<String>> project) {
        Map<String, Set<String>> answer = new HashMap<>();
        if (project != null) {
            project.forEach((uri, names) -> answer.computeIfAbsent(normalize(uri), k -> new TreeSet<>()).addAll(names));
        }
        if (target == null || !target.isArray()) {
            return answer;
        }
        for (JsonNode entry : target) {
            JsonNode rest = entry.isObject() ? entry.get("rest") : null;
            if (rest == null || !rest.isObject()) {
                continue;
            }
            for (String verb : REST_VERBS) {
                JsonNode operations = rest.get(verb);
                if (operations == null) {
                    continue;
                }
                for (JsonNode operation : operations.isArray() ? operations : List.of(operations)) {
                    String uri = operation.isObject() ? endpointOf(operation.get("to")) : null;
                    Set<String> names = pathParameterNames(rest.get("path"));
                    names.addAll(pathParameterNames(operation.get("path")));
                    if (uri != null && !names.isEmpty()) {
                        answer.computeIfAbsent(normalize(uri), k -> new TreeSet<>()).addAll(names);
                    }
                }
            }
        }
        // a route those routes call gets the same message: each pass only adds names, so it ends
        List<Route> routes = routes(target);
        boolean changed = !answer.isEmpty();
        while (changed) {
            changed = false;
            for (Route route : routes) {
                Set<String> names = answer.get(normalize(route.fromUri()));
                if (names == null) {
                    continue;
                }
                for (String uri : sendsTo(route.steps())) {
                    if (CALLS.contains(scheme(uri))
                            && answer.computeIfAbsent(normalize(uri), k -> new TreeSet<>()).addAll(names)) {
                        changed = true;
                    }
                }
            }
        }
        return answer;
    }

    /** The path parameters of a REST path: id for /orders/{id}. */
    static Set<String> pathParameterNames(JsonNode path) {
        Set<String> answer = new TreeSet<>();
        if (path != null && path.isValueNode()) {
            Matcher m = PATH_PARAMETER.matcher(path.asString(""));
            while (m.find()) {
                answer.add(m.group(1));
            }
        }
        return answer;
    }

    /** The endpoint without its options: direct:lookup?timeout=1000 is the endpoint direct:lookup. */
    static String normalize(String uri) {
        if (uri == null) {
            return "";
        }
        String s = uri.trim();
        int q = s.indexOf('?');
        return q > 0 ? s.substring(0, q) : s;
    }

    /** The component of an endpoint, or null. */
    static String scheme(String uri) {
        if (uri == null) {
            return null;
        }
        int colon = uri.indexOf(':');
        return colon > 0 ? uri.substring(0, colon) : uri;
    }
}
