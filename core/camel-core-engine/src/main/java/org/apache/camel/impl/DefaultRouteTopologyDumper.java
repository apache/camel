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
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Route;
import org.apache.camel.model.EndpointRequiredDefinition;
import org.apache.camel.model.ExpressionSubElementDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SendDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.model.WireTapDefinition;
import org.apache.camel.model.errorhandler.DeadLetterChannelDefinition;
import org.apache.camel.model.errorhandler.NoErrorHandlerDefinition;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.spi.EndpointUriFactory;
import org.apache.camel.spi.RouteTopologyDumper;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.util.URISupport;

@JdkService(RouteTopologyDumper.FACTORY)
public class DefaultRouteTopologyDumper implements RouteTopologyDumper {

    private static final Set<String> TRIGGER_SCHEMES = Set.of("timer", "quartz", "cron", "scheduler");
    private static final Set<String> INTERNAL_SCHEMES = Set.of("direct", "seda", "vertx", "disruptor", "disruptor-vm");
    // the schemes whose target runs before the route goes on, whatever the exchange pattern
    private static final Set<String> WAITING_SCHEMES = Set.of("direct", "kamelet");

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
            ErrorHandlerFactory eh = rd.isErrorHandlerFactorySet()
                    ? rd.getErrorHandlerFactory() : context.getCamelContextExtension().getErrorHandlerFactory();
            // with no error handler a failure goes back to the caller, whose error handler acts: no error paths here
            boolean handlesFailures = !(eh instanceof NoErrorHandlerDefinition);
            for (ProcessorDefinition<?> output : rd.getOutputs()) {
                // an onException clause (a global one is copied into every route) sends only when handling a failure
                boolean onException = output instanceof OnExceptionDefinition;
                if (onException && !handlesFailures) {
                    continue;
                }
                String kind = onException ? EDGE_ERROR : EDGE_CALL;
                String via = onException ? VIA_ON_EXCEPTION : null;
                String handling = onException ? handling((OnExceptionDefinition) output) : null;
                Collection<EndpointRequiredDefinition> sends
                        = ProcessorDefinitionHelper.filterTypeInOutputs(List.of(output), EndpointRequiredDefinition.class);
                for (EndpointRequiredDefinition erd : sends) {
                    // an error path ends there: what it sends to is no answer the route waits for
                    boolean reply = !onException && waitsForAnswer(context, rd, (ProcessorDefinition<?>) erd,
                            erd.getEndpointUri());
                    addEdges(context, edges, inputUriToRouteIds, rd.getRouteId(), erd.getEndpointUri(), kind, via,
                            handling, reply);
                }
                // a toD or a wire tap to an endpoint the route names, not one it computes
                for (ToDynamicDefinition td : staticDynamicSends(output)) {
                    boolean reply = !onException && waitsForAnswer(context, rd, td, td.getUri());
                    addEdges(context, edges, inputUriToRouteIds, rd.getRouteId(), td.getUri(), kind, via, handling,
                            reply);
                }
            }
            if (handlesFailures && eh instanceof DeadLetterChannelDefinition dlc && dlc.getDeadLetterUri() != null) {
                // a dead letter channel handles the failure: the message ends at the dead letter uri
                addEdges(context, edges, inputUriToRouteIds, rd.getRouteId(), dlc.getDeadLetterUri(), EDGE_ERROR,
                        VIA_ERROR_HANDLER, HANDLING_HANDLED, false);
            }
        }

        // Compute external endpoints (remote systems that routes communicate with)
        List<TopologyExternalEndpoint> externalEndpoints = computeExternalEndpoints(context, routeDefs, inputUriToRouteIds);

        return new TopologyResult(nodes, edges, externalEndpoints);
    }

    private static void addEdges(
            CamelContext context, List<TopologyEdge> edges, Map<String, List<String>> inputUriToRouteIds,
            String fromRouteId, String uri, String kind, String via, String handling, boolean reply) {
        String outputUri = normalizeUri(context, uri);
        List<String> targetRouteIds = inputUriToRouteIds.get(outputUri);
        if (targetRouteIds != null) {
            String scheme = extractScheme(outputUri);
            String connType = INTERNAL_SCHEMES.contains(scheme) ? "internal" : "external";
            for (String targetId : targetRouteIds) {
                edges.add(new TopologyEdge(fromRouteId, targetId, outputUri, connType, kind, via, handling, reply));
            }
        }
    }

    /**
     * Whether the route waits for the answer of what it sends to, as the send processor decides the exchange pattern:
     * the exchangePattern of the endpoint uri, else the pattern of the send (to with pattern InOut, a wire tap is
     * InOnly), else the pattern of the message the route consumed (InOut from platform-http, InOnly from a timer).
     * Direct always runs the target before the route goes on.
     */
    static boolean waitsForAnswer(CamelContext context, RouteDefinition route, ProcessorDefinition<?> send, String uri) {
        if (uri == null || send instanceof WireTapDefinition) {
            // a wire tap sends a copy on another thread: the route goes on without its answer
            return false;
        }
        if (WAITING_SCHEMES.contains(extractScheme(uri))) {
            return true;
        }
        ExchangePattern pattern = null;
        try {
            pattern = EndpointHelper.resolveExchangePatternFromUrl(context.resolvePropertyPlaceholders(uri));
        } catch (Exception e) {
            // a uri that cannot be resolved says nothing
        }
        if (pattern == null) {
            String sendPattern = send instanceof SendDefinition<?> sd ? sd.getPattern()
                    : send instanceof ToDynamicDefinition td ? td.getPattern() : null;
            if (sendPattern != null) {
                try {
                    pattern = context.getTypeConverter().tryConvertTo(ExchangePattern.class,
                            context.resolvePropertyPlaceholders(sendPattern));
                } catch (Exception e) {
                    // a pattern that cannot be resolved says nothing
                }
            }
        }
        if (pattern == null) {
            Route running = route.getRouteId() != null ? context.getRoute(route.getRouteId()) : null;
            pattern = running != null && running.getEndpoint() != null ? running.getEndpoint().getExchangePattern() : null;
        }
        return pattern == ExchangePattern.InOut;
    }

    /**
     * The toD and wire tap sends below a processor whose uri has no expression: the route names the endpoint (a wire
     * tap to seda:audit), as a to does.
     */
    private static List<ToDynamicDefinition> staticDynamicSends(ProcessorDefinition<?> output) {
        List<ToDynamicDefinition> answer = new ArrayList<>();
        for (ToDynamicDefinition td : ProcessorDefinitionHelper.filterTypeInOutputs(List.of(output),
                ToDynamicDefinition.class)) {
            String uri = td.getUri();
            if (uri != null && !uri.contains("${") && !uri.contains("$simple{")) {
                answer.add(td);
            }
        }
        return answer;
    }

    /**
     * What an onException clause does with the failure: continued, handled or not handled (a side trip, the failure
     * goes on to the caller); null when a predicate decides it at runtime.
     */
    private static String handling(OnExceptionDefinition oe) {
        Boolean continued = constant(oe.getContinued());
        Boolean handled = constant(oe.getHandled());
        if (Boolean.TRUE.equals(continued)) {
            return HANDLING_CONTINUED;
        }
        if (Boolean.TRUE.equals(handled)) {
            return HANDLING_HANDLED;
        }
        boolean decidedAtRuntime = (oe.getContinued() != null && continued == null)
                || (oe.getHandled() != null && handled == null);
        return decidedAtRuntime ? null : HANDLING_NOT_HANDLED;
    }

    /**
     * The value of a handled or continued option set as a constant (true or false), or null when it is not set or is a
     * predicate.
     */
    private static Boolean constant(ExpressionSubElementDefinition option) {
        if (option != null && option.getExpressionType() instanceof ConstantExpression ce) {
            return Boolean.valueOf(ce.getExpression().trim());
        }
        return null;
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
            for (ProcessorDefinition<?> output : rd.getOutputs()) {
                staticDynamicSends(output).forEach(td -> allOutputUris.add(URISupport.stripQuery(td.getUri())));
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

            List<String> outputUris = new ArrayList<>();
            outputs.forEach(erd -> outputUris.add(erd.getEndpointUri()));
            for (ProcessorDefinition<?> output : rd.getOutputs()) {
                staticDynamicSends(output).forEach(td -> outputUris.add(td.getUri()));
            }
            int outIdx = 0;
            for (String uri : outputUris) {
                String outputUri = URISupport.stripQuery(uri);
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
