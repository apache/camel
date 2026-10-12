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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import static org.apache.camel.dsl.yaml.validator.RouteGraph.endpointOf;

/**
 * A {@code direct:} or {@code seda:} endpoint a route sends to, and no route of the application consumes (CAMEL-24955).
 * With {@code direct:} the exchange fails - <i>No consumers available on endpoint</i>; with {@code seda:} nothing
 * fails, and the message is queued and never read. The endpoints and EIPs that count are the {@link EndpointRules} that
 * {@code camel overview} uses too.
 * <p/>
 * The routes of an application are spread over files, so a file on its own cannot answer: the caller scans the other
 * route files of the directory and passes what they consume. Without them the check says nothing.
 */
public final class EndpointConsumers {

    /** The route templates and the routes made from them, whose endpoints are only known once instantiated. */
    private static final Set<String> TEMPLATES = Set.of(
            "routeTemplate", "route-template", "templatedRoute", "templated-route");
    /** The resources whose routes are not in the file: what they consume is not known here. */
    private static final Set<String> UNKNOWN_KINDS = Set.of("Kamelet", "Pipe", "KameletBinding");
    /** The top-level entries that are not routes, nor have steps that send: the beans of the file. */
    private static final Set<String> NOT_STEPS = Set.of("beans", "dataFormats");

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

    private EndpointConsumers() {
    }

    /**
     * The {@code direct:} and {@code seda:} endpoints the routes of a YAML file consume (their input, and a poll or
     * pollEnrich in a route), without their options. Empty when the file is not YAML the routes can be read from; null
     * when a route consumes an endpoint only known at runtime ({@code from: direct:{{name}}}), or the file has route
     * templates, is a Kamelet or a Pipe, which could consume any of them.
     */
    public static Set<String> consumed(String yaml) {
        JsonNode target = read(yaml);
        if (target == null) {
            return Set.of();
        }
        JsonNode entries = entries(target);
        if (entries == null || hasTemplates(entries)) {
            return null;
        }
        return consumed(entries, new HashSet<>()) ? answer(entries) : null;
    }

    /**
     * @param  yaml              the YAML DSL source
     * @param  consumedElsewhere the endpoints the other route files of the application consume, as returned by
     *                           {@link #consumed(String)}; null when they are not known, which keeps the check quiet
     * @return                   a message for each endpoint a route sends to and no route consumes
     */
    public static List<String> check(String yaml, Set<String> consumedElsewhere) {
        if (consumedElsewhere == null) {
            return List.of();
        }
        JsonNode target = read(yaml);
        JsonNode entries = target != null ? entries(target) : null;
        if (entries == null || hasTemplates(entries)) {
            return List.of();
        }
        Set<String> consumed = new HashSet<>(consumedElsewhere);
        if (!consumed(entries, consumed)) {
            return List.of();
        }
        Set<String> messages = new LinkedHashSet<>();
        for (JsonNode entry : entries) {
            String routeId = routeId(entry);
            for (String uri : sends(entry)) {
                String endpoint = endpoint(uri);
                if (endpoint == null || consumed.contains(endpoint)) {
                    continue;
                }
                messages.add((routeId != null ? "route " + routeId + ": " : "")
                             + "sends to " + endpoint + ", and no route consumes it - not in this file, nor in the"
                             + " other route files of the directory: " + EndpointRules.withoutConsumer(endpoint)
                             + "; add a route with from: " + endpoint + ", or correct the name");
            }
        }
        return new ArrayList<>(messages);
    }

    /**
     * The endpoint in the form the check compares, {@code direct:lookup} for {@code direct://lookup?timeout=1000}; null
     * for an endpoint of another component, or one only known at runtime (a placeholder, an expression).
     */
    public static String endpoint(String uri) {
        return EndpointRules.endpoint(uri);
    }

    /** Whether the endpoint is only known at runtime: a property placeholder or an expression. */
    public static boolean isDynamic(String uri) {
        return EndpointRules.isDynamic(uri);
    }

    private static Set<String> answer(JsonNode entries) {
        Set<String> answer = new HashSet<>();
        consumed(entries, answer);
        return answer;
    }

    /**
     * Adds the checked endpoints the entries consume: the route inputs, and the poll and pollEnrich steps at any depth.
     *
     * @return false when one is only known at runtime, which could be any endpoint
     */
    private static boolean consumed(JsonNode entries, Set<String> answer) {
        for (JsonNode entry : entries) {
            if (!entry.isObject()) {
                continue;
            }
            JsonNode route = entry.get("route");
            JsonNode from = route != null ? route.get("from") : entry.get("from");
            if (from != null && !add(endpointOf(from), answer)) {
                return false;
            }
            List<String> polled = new ArrayList<>();
            collect(entry, Set.of("poll", "pollEnrich"), polled);
            for (String uri : polled) {
                if (!add(uri, answer)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Adds the endpoint when it is a checked one; false when it is one only known at runtime. */
    private static boolean add(String uri, Set<String> answer) {
        if (uri == null) {
            return true;
        }
        if (EndpointRules.isDynamic(uri) && EndpointRules.mayBeChecked(uri)) {
            return false;
        }
        String endpoint = endpoint(uri);
        if (endpoint != null) {
            answer.add(endpoint);
        }
        return true;
    }

    /**
     * The endpoints an entry sends to at any depth: the steps of a route, of a route configuration (onException...), of
     * a rest verb, and the dead letter endpoint of an error handler.
     */
    private static List<String> sends(JsonNode entry) {
        List<String> answer = new ArrayList<>();
        if (entry.isObject()) {
            for (var it = entry.fieldNames(); it.hasNext();) {
                String name = it.next();
                if (!NOT_STEPS.contains(name)) {
                    collect(entry.get(name), EndpointRules.SENDERS, answer);
                }
            }
        }
        return answer;
    }

    private static void collect(JsonNode node, Set<String> keys, List<String> answer) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collect(child, keys, answer);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        for (var it = node.fieldNames(); it.hasNext();) {
            String name = it.next();
            JsonNode value = node.get(name);
            if (keys.contains(name)) {
                String uri = uriOf(name, value);
                if (uri != null) {
                    answer.add(uri);
                }
            }
            collect(value, keys, answer);
        }
    }

    /**
     * The endpoint of a step: its uri, or for an enrich and a pollEnrich the constant their expression is (the endpoint
     * of {@code pollEnrich: {constant: seda:orders}}), as the Java and XML checks read them; an expression of another
     * language is only known at runtime, and not an endpoint here.
     */
    private static String uriOf(String key, JsonNode value) {
        if (("enrich".equals(key) || "pollEnrich".equals(key)) && value != null && value.isObject()
                && !value.has("uri")) {
            JsonNode constant = value.has("expression") ? value.get("expression").get("constant") : value.get("constant");
            if (constant != null && constant.isObject()) {
                constant = constant.get("expression");
            }
            return constant != null && constant.isTextual() ? constant.asText() : null;
        }
        return endpointOf(value);
    }

    private static String routeId(JsonNode entry) {
        JsonNode route = entry.get("route");
        return route != null && route.has("id") ? route.get("id").asText() : null;
    }

    /**
     * The entries of the file: a list of routes and the like, or the flows of an Integration; null for a Kamelet or a
     * Pipe, whose routes are not in the file, and an empty list for a YAML file that has no routes.
     */
    private static JsonNode entries(JsonNode target) {
        if (target.isArray()) {
            return target;
        }
        if (target.isObject()) {
            String kind = target.path("kind").asText();
            if (UNKNOWN_KINDS.contains(kind)) {
                return null;
            }
            if ("Integration".equals(kind)) {
                JsonNode flows = target.path("spec").path("flows");
                return flows.isArray() ? flows : MAPPER.createArrayNode();
            }
        }
        return MAPPER.createArrayNode();
    }

    /** Whether the entries have a route template or a route made from one: the routes they create are not read here. */
    private static boolean hasTemplates(JsonNode entries) {
        for (JsonNode entry : entries) {
            if (entry.isObject()) {
                for (String key : TEMPLATES) {
                    if (entry.has(key)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static JsonNode read(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(yaml);
        } catch (Exception e) {
            // not YAML the routes can be read from: nothing to learn from it
            return null;
        }
    }
}
