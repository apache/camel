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
package org.apache.camel.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.model.EndpointRequiredDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.RouteTopologyDumper;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.util.URISupport;

@JdkService(RouteTopologyDumper.FACTORY)
public class DefaultRouteTopologyDumper implements RouteTopologyDumper {

    private static final Set<String> TRIGGER_SCHEMES = Set.of("timer", "quartz", "cron", "scheduler");
    private static final Set<String> INTERNAL_SCHEMES = Set.of("direct", "seda", "vertx", "disruptor", "disruptor-vm");

    @Override
    public TopologyResult dumpTopology(CamelContext context) {
        Model model = context.getCamelContextExtension().getContextPlugin(Model.class);
        List<RouteDefinition> routeDefs = model.getRouteDefinitions();

        List<TopologyNode> nodes = new ArrayList<>();
        Map<String, List<String>> inputUriToRouteIds = new HashMap<>();

        for (RouteDefinition rd : routeDefs) {
            String inputUri = rd.getInput().getEndpointUri();
            String normalizedInput = URISupport.stripQuery(inputUri);
            String scheme = extractScheme(normalizedInput);
            String nodeType = TRIGGER_SCHEMES.contains(scheme) ? "trigger" : "route";
            String description = rd.getDescriptionText();

            nodes.add(new TopologyNode(rd.getRouteId(), description, normalizedInput, scheme, nodeType));
            inputUriToRouteIds
                    .computeIfAbsent(normalizedInput, k -> new ArrayList<>())
                    .add(rd.getRouteId());
        }

        List<TopologyEdge> edges = new ArrayList<>();
        for (RouteDefinition rd : routeDefs) {
            Collection<EndpointRequiredDefinition> outputs
                    = ProcessorDefinitionHelper.filterTypeInOutputs(
                            rd.getOutputs(), EndpointRequiredDefinition.class);

            for (EndpointRequiredDefinition erd : outputs) {
                String outputUri = URISupport.stripQuery(erd.getEndpointUri());
                String scheme = extractScheme(outputUri);

                List<String> targetRouteIds = inputUriToRouteIds.get(outputUri);
                if (targetRouteIds != null) {
                    String connType = INTERNAL_SCHEMES.contains(scheme) ? "internal" : "external";
                    for (String targetId : targetRouteIds) {
                        edges.add(new TopologyEdge(rd.getRouteId(), targetId, outputUri, connType));
                    }
                }
            }
        }

        // Compute external endpoints (remote systems that routes communicate with)
        List<TopologyExternalEndpoint> externalEndpoints = computeExternalEndpoints(context, routeDefs, inputUriToRouteIds);

        return new TopologyResult(nodes, edges, externalEndpoints);
    }

    private List<TopologyExternalEndpoint> computeExternalEndpoints(
            CamelContext context, List<RouteDefinition> routeDefs,
            Map<String, List<String>> inputUriToRouteIds) {

        // Build scheme -> isRemote map from endpoint registry
        // Skip stub endpoints — they mask the real component's remote status
        Map<String, Boolean> schemeRemoteMap = new HashMap<>();
        for (Endpoint ep : context.getEndpoints()) {
            if (isStubEndpoint(ep)) {
                continue;
            }
            String scheme = extractScheme(ep.getEndpointUri());
            schemeRemoteMap.putIfAbsent(scheme, ep.isRemote());
        }

        List<TopologyExternalEndpoint> externalEndpoints = new ArrayList<>();
        Set<String> seenOutgoing = new HashSet<>();

        for (RouteDefinition rd : routeDefs) {
            String routeId = rd.getRouteId();

            // Consumer (direction=in): each route has exactly 1 "from" endpoint
            String inputUri = URISupport.stripQuery(rd.getInput().getEndpointUri());
            String inputScheme = extractScheme(inputUri);
            if (isRemoteScheme(inputScheme, schemeRemoteMap)) {
                externalEndpoints.add(
                        new TopologyExternalEndpoint("in-" + routeId, inputUri, inputScheme, "in", routeId));
            }

            // Producers (direction=out): 0..N output endpoints per route
            Collection<EndpointRequiredDefinition> outputs
                    = ProcessorDefinitionHelper.filterTypeInOutputs(
                            rd.getOutputs(), EndpointRequiredDefinition.class);

            int outIdx = 0;
            for (EndpointRequiredDefinition erd : outputs) {
                String outputUri = URISupport.stripQuery(erd.getEndpointUri());
                String outputScheme = extractScheme(outputUri);
                if (isRemoteScheme(outputScheme, schemeRemoteMap)) {
                    // Deduplicate per route: same route sending to same URI only listed once
                    String dedupeKey = routeId + "|" + outputUri;
                    if (seenOutgoing.add(dedupeKey)) {
                        externalEndpoints.add(
                                new TopologyExternalEndpoint(
                                        "out-" + routeId + "-" + outIdx, outputUri, outputScheme, "out", routeId));
                        outIdx++;
                    }
                }
            }
        }

        return externalEndpoints;
    }

    private boolean isRemoteScheme(String scheme, Map<String, Boolean> schemeRemoteMap) {
        Boolean remote = schemeRemoteMap.get(scheme);
        if (remote != null) {
            return remote;
        }
        // Fallback: internal and trigger schemes are not remote
        return !INTERNAL_SCHEMES.contains(scheme) && !TRIGGER_SCHEMES.contains(scheme);
    }

    private static boolean isStubEndpoint(Endpoint ep) {
        return "StubEndpoint".equals(ep.getClass().getSimpleName());
    }

    private static String extractScheme(String uri) {
        int idx = uri.indexOf(':');
        return idx > 0 ? uri.substring(0, idx) : uri;
    }

}
