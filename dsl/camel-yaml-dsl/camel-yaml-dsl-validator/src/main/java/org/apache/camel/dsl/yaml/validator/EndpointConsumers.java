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

import static org.apache.camel.dsl.yaml.validator.RouteGraph.Route;
import static org.apache.camel.dsl.yaml.validator.RouteGraph.normalize;
import static org.apache.camel.dsl.yaml.validator.RouteGraph.routes;
import static org.apache.camel.dsl.yaml.validator.RouteGraph.scheme;
import static org.apache.camel.dsl.yaml.validator.RouteGraph.sendsTo;

/**
 * A {@code direct:} or {@code seda:} endpoint a route sends to, and no route of the application consumes (CAMEL-24955).
 * With {@code direct:} the route fails to start - <i>No consumers available on endpoint</i>; with {@code seda:} nothing
 * fails, and the message is queued and never read.
 * <p/>
 * The routes of an application are spread over files, so a file on its own cannot answer: the caller scans the other
 * route files of the directory and passes what they consume. Without them the check says nothing.
 */
public final class EndpointConsumers {

    /** The components whose consumer is a route of the same application. */
    private static final Set<String> CHECKED = Set.of("direct", "seda");

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

    private EndpointConsumers() {
    }

    /**
     * The {@code direct:} and {@code seda:} endpoints the routes of a YAML file consume, without their options. Empty
     * when the file is not YAML the routes can be read from; null when a route consumes an endpoint only known at
     * runtime ({@code from: direct:{{name}}}), which could be any of them.
     */
    public static Set<String> consumed(String yaml) {
        JsonNode target = read(yaml);
        return target != null ? consumed(routes(target)) : Set.of();
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
        if (target == null) {
            return List.of();
        }
        List<Route> routes = routes(target);
        Set<String> own = consumed(routes);
        if (own == null) {
            return List.of();
        }
        Set<String> consumed = new HashSet<>(consumedElsewhere);
        consumed.addAll(own);
        Set<String> messages = new LinkedHashSet<>();
        for (Route r : routes) {
            for (String uri : sendsTo(r.steps())) {
                String endpoint = endpoint(uri);
                if (endpoint == null || consumed.contains(endpoint)) {
                    continue;
                }
                messages.add((r.id() != null ? "route " + r.id() + ": " : "")
                             + "sends to " + endpoint + ", and no route consumes it - not in this file, nor in the"
                             + " other route files of the directory: "
                             + ("direct".equals(scheme(endpoint))
                                     ? "the route fails to start with No consumers available on endpoint"
                                     : "nothing fails, the messages are queued and never read")
                             + "; add a route with from: " + endpoint + ", or correct the name");
            }
        }
        return new ArrayList<>(messages);
    }

    private static Set<String> consumed(List<Route> routes) {
        Set<String> answer = new HashSet<>();
        for (Route r : routes) {
            if (isDynamic(r.fromUri())) {
                return null;
            }
            String endpoint = endpoint(r.fromUri());
            if (endpoint != null) {
                answer.add(endpoint);
            }
        }
        return answer;
    }

    /**
     * The endpoint in the form the check compares, {@code direct:lookup} for {@code direct://lookup?timeout=1000}; null
     * for an endpoint of another component, or one only known at runtime (a placeholder, an expression).
     */
    public static String endpoint(String uri) {
        String s = normalize(uri);
        String scheme = scheme(s);
        if (scheme == null || !CHECKED.contains(scheme) || s.length() == scheme.length() || isDynamic(s)) {
            return null;
        }
        String name = s.substring(scheme.length() + 1);
        if (name.startsWith("//")) {
            name = name.substring(2);
        }
        return name.isEmpty() ? null : scheme + ":" + name;
    }

    /** Whether the endpoint is only known at runtime: a property placeholder or an expression. */
    public static boolean isDynamic(String uri) {
        return uri != null && (uri.contains("{{") || uri.contains("${"));
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
