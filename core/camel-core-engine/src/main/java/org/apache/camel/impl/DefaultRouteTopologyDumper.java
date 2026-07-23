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
import java.util.TreeMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.model.EndpointRequiredDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.EndpointUriFactory;
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
            String normalizedInput = normalizeUri(context, inputUri);
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
                String outputUri = normalizeUri(context, erd.getEndpointUri());
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

        // Collect all output URIs to determine which "from" endpoints are truly external
        Set<String> allOutputUris = new HashSet<>();
        for (RouteDefinition rd : routeDefs) {
            Collection<EndpointRequiredDefinition> outputs
                    = ProcessorDefinitionHelper.filterTypeInOutputs(
                            rd.getOutputs(), EndpointRequiredDefinition.class);
            for (EndpointRequiredDefinition erd : outputs) {
                allOutputUris.add(URISupport.stripQuery(erd.getEndpointUri()));
            }
        }

        List<TopologyExternalEndpoint> externalEndpoints = new ArrayList<>();
        Set<String> seenOutgoing = new HashSet<>();

        for (RouteDefinition rd : routeDefs) {
            String routeId = rd.getRouteId();

            // Consumer (direction=in): only if no route sends to this URI (truly from outside Camel)
            String inputUri = URISupport.stripQuery(rd.getInput().getEndpointUri());
            String inputScheme = extractScheme(inputUri);
            if (isRemoteScheme(inputScheme, schemeRemoteMap) && !allOutputUris.contains(inputUri)) {
                externalEndpoints.add(
                        new TopologyExternalEndpoint("in-" + routeId, inputUri, inputScheme, "in", routeId));
            }

            // Producers (direction=out): only if no route consumes from this URI (truly leaving Camel)
            Collection<EndpointRequiredDefinition> outputs
                    = ProcessorDefinitionHelper.filterTypeInOutputs(
                            rd.getOutputs(), EndpointRequiredDefinition.class);

            int outIdx = 0;
            for (EndpointRequiredDefinition erd : outputs) {
                String outputUri = URISupport.stripQuery(erd.getEndpointUri());
                String outputScheme = extractScheme(outputUri);
                if (isRemoteScheme(outputScheme, schemeRemoteMap) && !inputUriToRouteIds.containsKey(outputUri)) {
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

    /**
     * Normalizes an endpoint URI for topology matching. Strips all query parameters except those marked as endpoint
     * identity parameters in the component metadata.
     */
    private static String normalizeUri(CamelContext context, String uri) {
        String base = URISupport.stripQuery(uri);
        String scheme = extractScheme(uri);

        EndpointUriFactory factory = context.getCamelContextExtension().getEndpointUriFactory(scheme);
        if (factory == null) {
            return base;
        }

        Set<String> identityNames = factory.endpointIdentityPropertyNames();
        if (identityNames == null || identityNames.isEmpty()) {
            return base;
        }

        String query = URISupport.extractQuery(uri);
        if (query == null || query.isEmpty()) {
            return base;
        }

        try {
            Map<String, Object> params = URISupport.parseQuery(query);
            // extract only identity params (sorted for deterministic matching)
            Map<String, String> identityParams = new TreeMap<>();
            for (String name : identityNames) {
                Object value = params.get(name);
                if (value != null) {
                    identityParams.put(name, value.toString());
                }
            }
            if (!identityParams.isEmpty()) {
                StringBuilder sb = new StringBuilder(base);
                sb.append('?');
                boolean first = true;
                for (Map.Entry<String, String> entry : identityParams.entrySet()) {
                    if (!first) {
                        sb.append('&');
                    }
                    sb.append(entry.getKey()).append('=').append(entry.getValue());
                    first = false;
                }
                return sb.toString();
            }
        } catch (Exception e) {
            // ignore parse errors, fall back to base
        }

        return base;
    }

    private static String extractScheme(String uri) {
        int idx = uri.indexOf(':');
        return idx > 0 ? uri.substring(0, idx) : uri;
    }

}
