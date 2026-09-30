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
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The routes of a file and the endpoints between them: what the route topology is at runtime, read from the source.
 * <p/>
 * {@code DefaultRouteTopologyDumper} builds the same graph from the route definitions of a running context, by indexing
 * each route's input and matching the outputs against it. Here the routes are still text, so an endpoint has to be
 * recognised in both the forms the YAML DSL allows (CAMEL-24844).
 */
final class RouteGraph {

    /** The components whose endpoints link one route of the application to another. */
    static final Set<String> INTERNAL = Set.of("direct", "seda", "direct-vm", "vm", "disruptor", "disruptor-vm");

    /** The steps that send a message to an endpoint. */
    static final Set<String> SENDS = Set.of("to", "toD", "enrich", "pollEnrich", "wireTap");

    private RouteGraph() {
    }

    /** One route of the file: where it starts, what it does, and the node it was read from. */
    record Route(String id, String fromUri, JsonNode steps, JsonNode node) {
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
            String id = route != null && route.has("id") ? route.get("id").asText() : null;
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
        if (node.isTextual()) {
            return node.asText();
        }
        if (!node.isObject() || !node.has("uri")) {
            return null;
        }
        String uri = node.get("uri").asText();
        if (uri.indexOf(':') > 0) {
            return uri;
        }
        JsonNode parameters = node.get("parameters");
        if (parameters == null || !parameters.isObject()) {
            return uri;
        }
        for (String key : new String[] { "name", "destinationName", "topic", "queue", "path", "address" }) {
            if (parameters.has(key) && parameters.get(key).isValueNode()) {
                return uri + ":" + parameters.get(key).asText();
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
        for (var it = node.fieldNames(); it.hasNext();) {
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
