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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.spi.RouteTopologyDumper.TopologyEdge;
import org.apache.camel.spi.RouteTopologyDumper.TopologyExternalEndpoint;
import org.apache.camel.spi.RouteTopologyDumper.TopologyNode;
import org.apache.camel.spi.RouteTopologyDumper.TopologyResult;
import org.apache.camel.tooling.model.ComponentModel;

import static org.apache.camel.dsl.yaml.validator.RouteGraph.INTERNAL;
import static org.apache.camel.dsl.yaml.validator.RouteGraph.Route;
import static org.apache.camel.dsl.yaml.validator.RouteGraph.endpointOf;
import static org.apache.camel.dsl.yaml.validator.RouteGraph.normalize;
import static org.apache.camel.dsl.yaml.validator.RouteGraph.scheme;
import static org.apache.camel.dsl.yaml.validator.RouteGraph.sendsTo;

/**
 * The route topology of YAML route files, read from the source without running them: the routes and where each starts,
 * the endpoints that connect one route to another, the endpoints that leave and enter the application, where the body
 * of each route comes from, and what looks wrong (CAMEL-24956).
 * <p/>
 * The nodes, edges and external endpoints are the records of {@code RouteTopologyDumper}, which
 * {@code DefaultRouteTopologyDumper} fills from the route definitions of a running context, so what is read here and
 * what is dumped at runtime have the same shape. It builds on the analysis of the validator ({@code RouteGraph},
 * {@code BodyTypeFlow}, {@code EndpointConsumers}), which it does not change.
 * <p/>
 * Only YAML is read. The routes of Java and XML files, route templates and Kamelets are not, and a route that is called
 * from them is not seen to be: what the analysis cannot be certain of, it leaves out.
 */
public final class SourceTopology {

    /** A route no route sends to, in the files read. */
    public static final String UNCALLED_ROUTE = "uncalled-route";
    /** A route that sends to a {@code direct:} or {@code seda:} endpoint that no route consumes (CAMEL-24955). */
    public static final String UNCONSUMED_ENDPOINT = "unconsumed-endpoint";
    /** A route that reads a body that neither it nor any route that calls it sets (CAMEL-24844). */
    public static final String BODY_NEVER_SET = "body-never-set";
    /**
     * The reason a file has a route template: the template is not read, and the routes written beside it in the file
     * are.
     */
    public static final String ROUTE_TEMPLATES_NOT_READ = "route templates not read";

    /**
     * What sends to a route without a {@code to} in the files: the routes of an OpenAPI specification are bound to
     * their operations, and the expressions of these steps choose the endpoint at runtime.
     */
    private static final Set<String> IMPLICIT_CALLERS = Set.of("openApi", "recipientList", "routingSlip", "dynamicRouter");

    private static final Pattern SCHEME = Pattern.compile("[a-zA-Z][a-zA-Z0-9+.-]*");

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

    private SourceTopology() {
    }

    /** The catalog of this version of Camel, loaded when it is first asked for. */
    private static final class DefaultCatalog {
        static final CamelCatalog INSTANCE = new DefaultCamelCatalog();
    }

    /**
     * Where the message a route receives gets its body.
     */
    public enum BodyOrigin {
        /** It is certain that the route starts without one: a timer or another consumer with no body, a GET. */
        NONE,
        /** The route is called by other routes, whose bodies it gets. */
        CALLERS,
        /** The consumer of the route brings its own: a kafka message, an HTTP request. */
        CONSUMER,
        /** A route only another route can call, and no route in the files does. */
        UNKNOWN
    }

    /**
     * What the routes of the files say about the body.
     *
     * @param routeId    the id of the route, or {@code <file>#<n>} for the n-th route of the file when it has none
     * @param file       the file the route is in
     * @param bodyOrigin where the body of the message reaching the route comes from
     * @param callers    the ids of the routes that send to the endpoint the route starts from
     * @param bodySetBy  the name of the first step of the route that may put something in the body, or null
     */
    public record RouteInfo(String routeId, String file, BodyOrigin bodyOrigin, List<String> callers, String bodySetBy) {
    }

    /**
     * Something that looks wrong. It is informational: the routes may run as they are.
     *
     * @param kind     {@link #UNCONSUMED_ENDPOINT}, {@link #BODY_NEVER_SET} or {@link #UNCALLED_ROUTE}
     * @param routeId  the route it was found in
     * @param file     the file of the route
     * @param endpoint the endpoint it is about, or null
     * @param message  what is wrong, and what to do about it
     */
    public record Finding(String kind, String routeId, String file, String endpoint, String message) {
    }

    /**
     * A file that was not read, or not all of it.
     *
     * @param file   the file
     * @param reason {@code unparseable}, {@link #ROUTE_TEMPLATES_NOT_READ} or {@code kamelet}
     */
    public record Skipped(String file, String reason) {
    }

    /**
     * @param topology the routes, the connections between them, and the external endpoints they use
     * @param routes   where the body of each route comes from
     * @param findings what looks wrong
     * @param skipped  the files that were not read
     */
    public record Result(
            TopologyResult topology, List<RouteInfo> routes, List<Finding> findings, List<Skipped> skipped) {
    }

    /**
     * As {@link #analyze(Map, CamelCatalog)}, with the catalog of this version of Camel.
     */
    public static Result analyze(Map<String, String> yamlByFile) {
        return analyze(yamlByFile, DefaultCatalog.INSTANCE);
    }

    /**
     * @param  yamlByFile the YAML source of each file, by the name to report it under, in the order to read them
     * @param  catalog    the catalog that says which components are remote systems
     * @return            the topology of the routes of all the files together, as the routes of an application are
     *                    spread over files
     */
    public static Result analyze(Map<String, String> yamlByFile, CamelCatalog catalog) {
        List<Skipped> skipped = new ArrayList<>();
        boolean templates = false;
        // the entries of all the files in one list, so that the routes of a file can call those of another
        ArrayNode entries = MAPPER.createArrayNode();
        Map<JsonNode, String> fileOf = new IdentityHashMap<>();
        for (Map.Entry<String, String> file : yamlByFile.entrySet()) {
            JsonNode target;
            try {
                target = read(file.getValue());
            } catch (Exception e) {
                skipped.add(new Skipped(file.getKey(), "unparseable"));
                continue;
            }
            if (target == null) {
                continue;
            }
            if (EndpointConsumers.hasTemplates(target)) {
                // the routes they create are not read, and could consume or call any endpoint
                skipped.add(new Skipped(file.getKey(), target.isObject() ? "kamelet" : ROUTE_TEMPLATES_NOT_READ));
                templates = true;
            }
            if (target.isArray()) {
                // a route template has no from of its own, so it is left out of the routes, and the routes written
                // beside it in the file are read
                for (JsonNode entry : target) {
                    entries.add(entry);
                    fileOf.put(entry, file.getKey());
                }
            }
        }

        // NOTE: the analysis of the body keeps its callers by route, and a route is equal to another that is written
        // exactly alike (the same id, endpoint and steps), so two such routes in different files are taken as one
        BodyTypeFlow.Analysis analysis = BodyTypeFlow.analyze(entries, Set.of());
        List<Route> routes = analysis.routes();
        Map<Route, Placed> placed = place(routes, fileOf);

        Set<TopologyEdge> edges = edges(routes, placed);
        Map<String, Set<String>> callers = new HashMap<>();
        for (TopologyEdge edge : edges) {
            callers.computeIfAbsent(edge.toRouteId(), k -> new LinkedHashSet<>()).add(edge.fromRouteId());
        }

        List<TopologyNode> nodes = new ArrayList<>();
        List<RouteInfo> infos = new ArrayList<>();
        for (Route r : routes) {
            Placed p = placed.get(r);
            String from = r.fromUri() != null ? normalize(r.fromUri()) : null;
            String scheme = from != null ? scheme(from) : null;
            nodes.add(new TopologyNode(p.id(), descriptionOf(r), from, scheme, isTrigger(scheme) ? "trigger" : "route"));
            List<String> callerIds = List.copyOf(callers.getOrDefault(p.id(), Set.of()));
            infos.add(new RouteInfo(
                    p.id(), p.file(), bodyOrigin(r, scheme, callerIds, analysis),
                    callerIds, BodyTypeFlow.firstBodyProducer(r.steps())));
        }

        List<Finding> findings = new ArrayList<>();
        if (!templates) {
            unconsumed(routes, placed, findings);
        }
        for (Route r : routes) {
            String reader = analysis.readerWithoutABody(r);
            if (reader != null) {
                Placed p = placed.get(r);
                String message = BodyTypeFlow.message(new Route(p.id(), r.fromUri(), r.steps(), r.node()), reader,
                        analysis.callers().containsKey(r));
                findings.add(new Finding(BODY_NEVER_SET, p.id(), p.file(), null, message));
            }
        }
        if (!templates) {
            uncalled(routes, entries, placed, findings);
        }

        return new Result(
                new TopologyResult(nodes, new ArrayList<>(edges), externalEndpoints(routes, placed, catalog)),
                infos, findings, skipped);
    }

    /** Where a route is: the id it is reported under, and its file. */
    private record Placed(String id, String file) {
    }

    /** The id and file of each route; a route without an id is the n-th of its file. */
    private static Map<Route, Placed> place(List<Route> routes, Map<JsonNode, String> fileOf) {
        Map<Route, Placed> answer = new IdentityHashMap<>();
        Map<String, Integer> count = new HashMap<>();
        for (Route r : routes) {
            String file = fileOf.get(r.node());
            int n = count.merge(file, 1, Integer::sum);
            answer.put(r, new Placed(r.id() != null ? r.id() : file + "#" + n, file));
        }
        return answer;
    }

    /** The connections: a route that sends to the endpoint another route starts from. */
    private static Set<TopologyEdge> edges(List<Route> routes, Map<Route, Placed> placed) {
        Map<String, List<Route>> startsFrom = new HashMap<>();
        for (Route r : routes) {
            if (r.fromUri() != null) {
                startsFrom.computeIfAbsent(endpoint(r.fromUri()), k -> new ArrayList<>()).add(r);
            }
        }
        Set<TopologyEdge> answer = new LinkedHashSet<>();
        for (Route caller : routes) {
            for (String uri : sendsTo(caller.steps())) {
                String endpoint = endpoint(uri);
                for (Route target : startsFrom.getOrDefault(endpoint, List.of())) {
                    answer.add(new TopologyEdge(
                            placed.get(caller).id(), placed.get(target).id(), endpoint,
                            isInternal(scheme(endpoint)) ? "internal" : "external"));
                }
            }
        }
        return answer;
    }

    /**
     * The remote systems the routes read from ({@code in}) and send to ({@code out}), as
     * {@code DefaultRouteTopologyDumper} has them: an endpoint that is between two routes is a connection, and not the
     * border of the application, so a route that reads from an endpoint some route sends to is not {@code in}, and a
     * route that sends to an endpoint some route reads from is not {@code out}.
     */
    private static List<TopologyExternalEndpoint> externalEndpoints(
            List<Route> routes, Map<Route, Placed> placed, CamelCatalog catalog) {
        Set<String> sentTo = new HashSet<>();
        Set<String> startedFrom = new HashSet<>();
        for (Route r : routes) {
            if (r.fromUri() != null) {
                startedFrom.add(endpoint(r.fromUri()));
            }
            for (String send : sendsTo(r.steps())) {
                sentTo.add(endpoint(send));
            }
        }

        List<TopologyExternalEndpoint> answer = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Route r : routes) {
            String id = placed.get(r).id();
            if (r.fromUri() != null) {
                String uri = endpoint(r.fromUri());
                if (isRemote(scheme(uri), catalog) && !sentTo.contains(uri)) {
                    answer.add(new TopologyExternalEndpoint("in-" + id, uri, scheme(uri), "in", id));
                }
            }
            int index = 0;
            for (String send : sendsTo(r.steps())) {
                String uri = endpoint(send);
                // once for each route and endpoint, however many times the route sends to it
                if (isRemote(scheme(uri), catalog) && !startedFrom.contains(uri) && seen.add(id + "|" + uri)) {
                    answer.add(new TopologyExternalEndpoint("out-" + id + "-" + index++, uri, scheme(uri), "out", id));
                }
            }
        }
        return answer;
    }

    private static BodyOrigin bodyOrigin(Route r, String scheme, List<String> callers, BodyTypeFlow.Analysis analysis) {
        if (scheme == null) {
            return BodyOrigin.UNKNOWN;
        }
        if (analysis.certainlyWithoutABody(r)) {
            return BodyOrigin.NONE;
        }
        if (!isInternal(scheme)) {
            return BodyOrigin.CONSUMER;
        }
        return callers.isEmpty() ? BodyOrigin.UNKNOWN : BodyOrigin.CALLERS;
    }

    /** A route that sends to a {@code direct:} or {@code seda:} endpoint no route consumes (CAMEL-24955). */
    private static void unconsumed(List<Route> routes, Map<Route, Placed> placed, List<Finding> findings) {
        Set<String> consumed = EndpointConsumers.consumed(routes);
        if (consumed == null) {
            return; // a route consumes an endpoint only known at runtime, which could be any
        }
        for (Route r : routes) {
            Placed p = placed.get(r);
            for (EndpointConsumers.Unconsumed u : EndpointConsumers.unconsumed(List.of(r), consumed)) {
                findings.add(new Finding(
                        UNCONSUMED_ENDPOINT, p.id(), p.file(), u.endpoint(),
                        unconsumedMessage(p.id(), u.endpoint())));
            }
        }
    }

    /**
     * Not the text of the validator, which has read the other route files of the directory, Java and XML as well: here
     * only the YAML files that were given are known, so it does not say more than that.
     */
    private static String unconsumedMessage(String routeId, String endpoint) {
        return "route " + routeId + ": sends to " + endpoint
               + ", and no route in the YAML files read consumes it - if nothing else does (a Java or XML route, a file"
               + " not read): "
               + ("direct".equals(scheme(endpoint))
                       ? "the route fails to start with No consumers available on endpoint"
                       : "nothing fails, the messages are queued and never read")
               + "; add a route with from: " + endpoint + ", or correct the name";
    }

    /**
     * A route that starts from a {@code direct:} or {@code seda:} endpoint, and that nothing in the files sends to.
     * Quiet when a way in is not in the files: an OpenAPI binding, a recipient list or a routing slip, an endpoint only
     * known at runtime.
     */
    private static void uncalled(List<Route> routes, JsonNode entries, Map<Route, Placed> placed, List<Finding> findings) {
        if (hasKey(entries, IMPLICIT_CALLERS)) {
            return;
        }
        Set<String> called = new HashSet<>();
        // every entry, not only the routes: a rest operation, an onException or a route configuration sends as well
        for (JsonNode entry : entries) {
            List<String> uris = sendsTo(entry);
            urisOfOtherCallers(entry, uris);
            for (String uri : uris) {
                if (EndpointConsumers.isDynamic(uri)) {
                    return;
                }
                String endpoint = EndpointConsumers.endpoint(uri);
                if (endpoint != null) {
                    called.add(endpoint);
                }
            }
        }
        for (Route r : routes) {
            String endpoint = EndpointConsumers.endpoint(r.fromUri());
            if (endpoint != null && !called.contains(endpoint)) {
                Placed p = placed.get(r);
                findings.add(new Finding(
                        UNCALLED_ROUTE, p.id(), p.file(), endpoint,
                        "route " + p.id() + ": nothing in the YAML route files sends to " + endpoint
                                                                    + ", so it is called from elsewhere (Java, a ProducerTemplate, another"
                                                                    + " application), or not at all"));
            }
        }
    }

    /**
     * The endpoints that are called without a {@code to}, added to the list: the {@code deadLetterUri} of a dead letter
     * channel, wherever the error handler is (of the route, of a route configuration, or a global one), and the
     * {@code compensation} and {@code completion} of a saga.
     */
    private static void urisOfOtherCallers(JsonNode node, List<String> answer) {
        if (node.isArray()) {
            for (JsonNode child : node) {
                urisOfOtherCallers(child, answer);
            }
            return;
        }
        for (var it = node.fieldNames(); it.hasNext();) {
            String name = it.next();
            JsonNode value = node.get(name);
            if ("deadLetterUri".equals(name)) {
                addIfNotNull(answer, endpointOf(value));
            } else if ("saga".equals(name) && value.isObject()) {
                addIfNotNull(answer, endpointOf(value.get("compensation")));
                addIfNotNull(answer, endpointOf(value.get("completion")));
            }
            urisOfOtherCallers(value, answer);
        }
    }

    private static void addIfNotNull(List<String> answer, String uri) {
        if (uri != null) {
            answer.add(uri);
        }
    }

    /** Whether the node, or anything nested in it, has a field of one of these names. */
    private static boolean hasKey(JsonNode node, Set<String> names) {
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (hasKey(child, names)) {
                    return true;
                }
            }
            return false;
        }
        for (var it = node.fieldNames(); it.hasNext();) {
            String name = it.next();
            if (names.contains(name) || hasKey(node.get(name), names)) {
                return true;
            }
        }
        return false;
    }

    /** The endpoint as the routes are matched on it: {@code direct:lookup} for {@code direct://lookup?timeout=1000}. */
    private static String endpoint(String uri) {
        String endpoint = EndpointConsumers.endpoint(uri);
        return endpoint != null ? endpoint : normalize(uri);
    }

    private static String descriptionOf(Route r) {
        JsonNode route = r.node().get("route");
        JsonNode description = route != null ? route.get("description") : null;
        return description != null && description.isValueNode() ? description.asText() : null;
    }

    private static boolean isInternal(String scheme) {
        return scheme != null && INTERNAL.contains(scheme);
    }

    private static boolean isTrigger(String scheme) {
        return scheme != null && BodyTypeFlow.NO_BODY_CONSUMER.contains(scheme);
    }

    /**
     * Whether the component is a remote system, as the catalog says. A component the catalog does not know, such as
     * {@code direct-vm} and {@code vm}, is one unless it is a route of the application or a trigger. Never a
     * placeholder.
     */
    private static boolean isRemote(String scheme, CamelCatalog catalog) {
        if (scheme == null || !SCHEME.matcher(scheme).matches()) {
            return false;
        }
        ComponentModel model = catalog.componentModel(scheme);
        if (model != null) {
            return model.isRemote();
        }
        return !isInternal(scheme) && !isTrigger(scheme);
    }

    private static JsonNode read(String yaml) throws IOException {
        return yaml == null || yaml.isBlank() ? null : MAPPER.readTree(yaml);
    }
}
