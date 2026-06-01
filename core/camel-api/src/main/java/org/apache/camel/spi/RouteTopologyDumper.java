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
 * SPI for computing inter-route topology, showing how routes connect to each other through shared endpoints.
 *
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
     * An edge in the topology representing a connection between two routes via a shared endpoint.
     *
     * @param fromRouteId    the source route id (the route that sends to the endpoint)
     * @param toRouteId      the target route id (the route that consumes from the endpoint)
     * @param endpoint       the shared endpoint URI (scheme:context-path, query parameters stripped)
     * @param connectionType the type of connection: "internal" for direct/seda, "external" for remote components
     * @since                4.21
     */
    record TopologyEdge(String fromRouteId, String toRouteId, String endpoint, String connectionType) {
    }

    /**
     * The result of computing route topology.
     *
     * @param nodes the route nodes
     * @param edges the connections between routes
     * @since       4.21
     */
    record TopologyResult(List<TopologyNode> nodes, List<TopologyEdge> edges) {
    }

    /**
     * Computes the inter-route topology by analyzing route definitions and matching endpoints across routes.
     *
     * @param  context the CamelContext
     * @return         the topology result with nodes and edges
     */
    TopologyResult dumpTopology(CamelContext context);

}
