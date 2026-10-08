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
package org.apache.camel.spi;

import java.util.List;

import org.apache.camel.CamelContext;

/**
 * SPI that analyses the active Camel routes and computes a graph describing how they interconnect through shared
 * endpoints.
 * <p/>
 * The implementation is discovered via the service key {@link #FACTORY}. The single
 * {@link #dumpTopology(org.apache.camel.CamelContext)} method returns a {@link TopologyResult} record containing:
 * {@link TopologyNode} entries (one per route, classified as {@code route} or {@code trigger}), {@link TopologyEdge}
 * entries (connections between two routes via a shared endpoint URI, classified as {@code internal} for direct/seda or
 * {@code external} for remote transports), and {@link TopologyExternalEndpoint} entries (remote systems a route talks
 * to). The developer console uses this output to render an interactive route graph.
 * <p/>
 * See <a href="https://camel.apache.org/manual/camel-jbang.html">Camel CLI (camel-jbang)</a> for the developer console
 * that renders this graph.
 *
 * @see   ModelToXMLDumper
 * @see   ModelToYAMLDumper
 * @since 4.21
 */
public interface RouteTopologyDumper {

    /**
     * Service factory key.
     */
    String FACTORY = "route-topology-dumper";

    /**
     * A node in the topology representing a route.
     *
     * @param routeId     the route id
     * @param description the route description (may be null)
     * @param from        the input endpoint URI (scheme:context-path, query parameters stripped)
     * @param fromScheme  the component scheme of the input endpoint
     * @param nodeType    the type of node: "route" for regular routes, "trigger" for timer/quartz/cron/scheduler
     * @since             4.21
     */
    record TopologyNode(String routeId, String description, String from, String fromScheme, String nodeType) {
    }

    /**
     * The kind of an edge where the route sends to the endpoint as it routes a message (the happy path).
     */
    String EDGE_CALL = "call";

    /**
     * The kind of an edge where the route sends to the endpoint only when it handles a failure (an error path).
     */
    String EDGE_ERROR = "error";

    /**
     * An error path of the error handler of the route, such as the dead letter uri of a dead letter channel.
     */
    String VIA_ERROR_HANDLER = "errorHandler";

    /**
     * An error path of an onException clause.
     */
    String VIA_ON_EXCEPTION = "onException";

    /**
     * The failure is handled: the message ends at the error path (as with a dead letter channel).
     */
    String HANDLING_HANDLED = "handled";

    /**
     * The failure is handled and the route continues after the error path.
     */
    String HANDLING_CONTINUED = "continued";

    /**
     * The failure is not handled: the error path is a side trip, and the failure goes on to the caller.
     */
    String HANDLING_NOT_HANDLED = "notHandled";

    /**
     * An edge in the topology representing a connection between two routes via a shared endpoint.
     *
     * @param fromRouteId    the source route id (the route that sends to the endpoint)
     * @param toRouteId      the target route id (the route that consumes from the endpoint)
     * @param endpoint       the shared endpoint URI (scheme:context-path, query parameters stripped)
     * @param connectionType the type of connection: "internal" for direct/seda, "external" for remote components
     * @param kind           {@link #EDGE_CALL} when the source route sends as it routes a message, or
     *                       {@link #EDGE_ERROR} when it sends only when it handles a failure (since 4.23)
     * @param via            for an error path, what sends: {@link #VIA_ERROR_HANDLER} or {@link #VIA_ON_EXCEPTION};
     *                       null for a call (since 4.23)
     * @param handling       for an error path, what happens to the failure: {@link #HANDLING_HANDLED},
     *                       {@link #HANDLING_CONTINUED} or {@link #HANDLING_NOT_HANDLED}; null for a call, and when a
     *                       predicate decides it at runtime (since 4.23)
     * @since                4.21
     */
    record TopologyEdge(String fromRouteId, String toRouteId, String endpoint, String connectionType, String kind,
            String via, String handling) {

        /**
         * An edge of kind {@link #EDGE_CALL}.
         */
        public TopologyEdge(String fromRouteId, String toRouteId, String endpoint, String connectionType) {
            this(fromRouteId, toRouteId, endpoint, connectionType, EDGE_CALL, null, null);
        }

        /**
         * Whether the edge is an error path: the route sends to the target only when it handles a failure.
         */
        public boolean isErrorPath() {
            return EDGE_ERROR.equals(kind);
        }
    }

    /**
     * An external endpoint representing a remote system that a route communicates with.
     *
     * @param id        a synthetic unique identifier
     * @param uri       the endpoint URI (scheme:context-path, query parameters stripped)
     * @param scheme    the component scheme
     * @param direction "in" for consumers (remote systems sending messages into Camel) or "out" for producers (Camel
     *                  sending messages to remote systems)
     * @param routeId   the route id that uses this external endpoint
     * @since           4.21
     */
    record TopologyExternalEndpoint(String id, String uri, String scheme, String direction, String routeId) {
    }

    /**
     * The result of computing route topology.
     *
     * @param nodes             the route nodes
     * @param edges             the connections between routes
     * @param externalEndpoints the external endpoints (remote systems) that routes communicate with (may be empty)
     * @since                   4.21
     */
    record TopologyResult(List<TopologyNode> nodes, List<TopologyEdge> edges,
            List<TopologyExternalEndpoint> externalEndpoints) {
    }

    /**
     * Computes the inter-route topology by analyzing route definitions and matching endpoints across routes.
     *
     * @param  context the CamelContext
     * @return         the topology result with nodes and edges
     */
    TopologyResult dumpTopology(CamelContext context);

}
