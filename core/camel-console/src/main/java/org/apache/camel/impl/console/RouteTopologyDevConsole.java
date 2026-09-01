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
package org.apache.camel.impl.console;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.api.management.mbean.ManagedSendProcessorMBean;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteTopologyDumper;
import org.apache.camel.spi.RouteTopologyDumper.TopologyEdge;
import org.apache.camel.spi.RouteTopologyDumper.TopologyExternalEndpoint;
import org.apache.camel.spi.RouteTopologyDumper.TopologyNode;
import org.apache.camel.spi.RouteTopologyDumper.TopologyResult;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "route-topology", description = "Route topology showing inter-route connections")
public class RouteTopologyDevConsole extends AbstractDevConsole {

    public record NodeEntry(
            @Metadata(description = "The route ID") String routeId,
            @Metadata(description = "The route description (only present when configured)") String description,
            @Metadata(description = "The route's endpoint URI") String from,
            @Metadata(description = "The route's endpoint scheme") String fromScheme,
            @Metadata(description = "The node type") String nodeType,
            @Metadata(description = "Total number of exchanges (only present when metrics were requested and available)") Long exchangesTotal,
            @Metadata(description = "Number of failed exchanges (only present when metrics were requested and available)") Long exchangesFailed) {
    }

    public record EdgeEntry(
            @Metadata(description = "The source route ID") String fromRouteId,
            @Metadata(description = "The target route ID") String toRouteId,
            @Metadata(description = "The endpoint URI connecting the two routes") String endpoint,
            @Metadata(description = "The connection type") String connectionType) {
    }

    public record ExternalEndpointEntry(
            @Metadata(description = "The endpoint ID") String id,
            @Metadata(description = "The endpoint URI") String uri,
            @Metadata(description = "The endpoint scheme") String scheme,
            @Metadata(description = "The direction (in or out)") String direction,
            @Metadata(description = "The route ID") String routeId,
            @Metadata(description = "Total number of exchanges (only present when metrics were requested and available)") Long exchangesTotal,
            @Metadata(description = "Number of failed exchanges (only present when metrics were requested and available)") Long exchangesFailed) {
    }

    public record Response(
            @Metadata(description = "The topology nodes (only present when a route topology dumper is available)") List<NodeEntry> nodes,
            @Metadata(description = "The connections between nodes (only present when a route topology dumper is available)") List<EdgeEntry> edges,
            @Metadata(description = "External systems the routes connect to (only present when requested and there are any)") List<ExternalEndpointEntry> externalEndpoints,
            @Metadata(description = "Route structure data, as opaque JSON objects (only present when requested)") List<Map<String, Object>> routes) {
    }

    @Metadata(label = "query", description = "Whether to include live metrics (message counts) on nodes and edges",
              defaultValue = "false", javaType = "java.lang.Boolean")
    public static final String METRIC = "metric";

    @Metadata(label = "query",
              description = "Whether to include external systems (databases, messaging brokers, etc.) as nodes",
              defaultValue = "false", javaType = "java.lang.Boolean")
    public static final String EXTERNAL = "external";

    @Metadata(label = "query", description = "Whether to include route structure data in the response",
              defaultValue = "false", javaType = "java.lang.Boolean")
    public static final String ROUTES = "routes";

    public RouteTopologyDevConsole() {
        super("camel", "route-topology", "Route Topology", "Route topology showing inter-route connections");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        RouteTopologyDumper dumper = PluginHelper.getRouteTopologyDumper(getCamelContext());
        if (dumper == null) {
            return "";
        }
        TopologyResult result = dumper.dumpTopology(getCamelContext());
        boolean external = optionBoolean(options, EXTERNAL, false);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Route Topology (%d routes, %d connections)%n%n",
                result.nodes().size(), result.edges().size()));

        for (TopologyNode node : result.nodes()) {
            sb.append(String.format("  %s (%s) type=%s%n", node.routeId(), node.from(), node.nodeType()));

            for (TopologyEdge edge : result.edges()) {
                if (edge.fromRouteId().equals(node.routeId())) {
                    sb.append(String.format("    --> %s via %s [%s]%n",
                            edge.toRouteId(), edge.endpoint(), edge.connectionType()));
                }
            }
        }

        if (external && !result.externalEndpoints().isEmpty()) {
            sb.append(String.format("%nExternal Endpoints:%n"));
            for (TopologyExternalEndpoint ep : result.externalEndpoints()) {
                sb.append(String.format("  [%s] %s (%s) route=%s%n",
                        ep.direction(), ep.uri(), ep.scheme(), ep.routeId()));
            }
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        RouteTopologyDumper dumper = PluginHelper.getRouteTopologyDumper(getCamelContext());
        if (dumper == null) {
            return JsonRecordSupport.toJsonObject(new Response(null, null, null, null));
        }
        TopologyResult result = dumper.dumpTopology(getCamelContext());

        boolean metric = optionBoolean(options, METRIC, false);
        boolean external = optionBoolean(options, EXTERNAL, false);
        ManagedCamelContext mcc = metric
                ? getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class)
                : null;

        List<NodeEntry> nodes = new ArrayList<>();
        for (TopologyNode node : result.nodes()) {
            Long exchangesTotal = null;
            Long exchangesFailed = null;
            if (mcc != null) {
                ManagedRouteMBean mrb = mcc.getManagedRoute(node.routeId());
                if (mrb != null) {
                    exchangesTotal = mrb.getExchangesTotal();
                    exchangesFailed = mrb.getExchangesFailed();
                }
            }
            nodes.add(new NodeEntry(
                    node.routeId(), node.description(), node.from(), node.fromScheme(), node.nodeType(),
                    exchangesTotal, exchangesFailed));
        }

        List<EdgeEntry> edges = new ArrayList<>();
        for (TopologyEdge edge : result.edges()) {
            edges.add(new EdgeEntry(edge.fromRouteId(), edge.toRouteId(), edge.endpoint(), edge.connectionType()));
        }

        List<ExternalEndpointEntry> externalEndpoints = null;
        if (external && !result.externalEndpoints().isEmpty()) {
            // Collect per-endpoint metrics for producers (direction=out)
            Map<String, long[]> endpointMetrics = collectEndpointMetrics(mcc, result);

            externalEndpoints = new ArrayList<>();
            for (TopologyExternalEndpoint ep : result.externalEndpoints()) {
                Long exchangesTotal = null;
                Long exchangesFailed = null;

                if (mcc != null) {
                    if ("in".equals(ep.direction())) {
                        // Consumer: use route-level metrics (route has exactly 1 consumer)
                        ManagedRouteMBean mrb = mcc.getManagedRoute(ep.routeId());
                        if (mrb != null) {
                            exchangesTotal = mrb.getExchangesTotal();
                            exchangesFailed = mrb.getExchangesFailed();
                        }
                    } else {
                        // Producer: use processor-level metrics
                        String key = ep.routeId() + "|" + ep.uri();
                        long[] stats = endpointMetrics.get(key);
                        if (stats != null) {
                            exchangesTotal = stats[0];
                            exchangesFailed = stats[1];
                        }
                    }
                }

                externalEndpoints.add(new ExternalEndpointEntry(
                        ep.id(), ep.uri(), ep.scheme(), ep.direction(), ep.routeId(), exchangesTotal, exchangesFailed));
            }
        }

        // Optionally include route structure data in the same response
        List<Map<String, Object>> routes = null;
        if (optionBoolean(options, ROUTES, false)) {
            DevConsoleRegistry registry = getCamelContext().getCamelContextExtension()
                    .getContextPlugin(DevConsoleRegistry.class);
            if (registry != null) {
                var structureConsole = registry.resolveById("route-structure");
                if (structureConsole != null) {
                    String metricStr = metric ? "true" : "false";
                    JsonObject structureResult = (JsonObject) structureConsole.call(
                            org.apache.camel.console.DevConsole.MediaType.JSON,
                            Map.of("filter", "*", "brief", "false", "metric", metricStr));
                    if (structureResult != null) {
                        JsonArray routesArr = structureResult.getCollection("routes");
                        if (routesArr != null) {
                            routes = new ArrayList<>();
                            for (Object o : routesArr) {
                                routes.add((JsonObject) o);
                            }
                        }
                    }
                }
            }
        }

        Response response = new Response(nodes, edges, externalEndpoints, routes);
        return JsonRecordSupport.toJsonObject(response);
    }

    /**
     * Collects per-endpoint metrics for producer endpoints by iterating managed send processors. Returns a map keyed by
     * "routeId|normalizedUri" with value [exchangesTotal, exchangesFailed].
     */
    private Map<String, long[]> collectEndpointMetrics(ManagedCamelContext mcc, TopologyResult result) {
        Map<String, long[]> metrics = new HashMap<>();
        if (mcc == null) {
            return metrics;
        }
        for (TopologyExternalEndpoint ep : result.externalEndpoints()) {
            if (!"out".equals(ep.direction())) {
                continue;
            }
            String epUri = stripDoubleSlash(URISupport.stripQuery(ep.uri()));
            ManagedRouteMBean mrb = mcc.getManagedRoute(ep.routeId());
            if (mrb == null) {
                continue;
            }
            Collection<String> ids;
            try {
                ids = mrb.processorIds();
            } catch (Exception e) {
                continue;
            }
            for (String pid : ids) {
                try {
                    ManagedSendProcessorMBean sp = mcc.getManagedProcessor(pid, ManagedSendProcessorMBean.class);
                    if (sp == null) {
                        continue;
                    }
                    String dest = sp.getDestination();
                    if (dest == null) {
                        continue;
                    }
                    dest = stripDoubleSlash(URISupport.stripQuery(dest));
                    if (epUri.equals(dest)) {
                        String key = ep.routeId() + "|" + ep.uri();
                        long[] existing = metrics.get(key);
                        if (existing != null) {
                            existing[0] += sp.getExchangesTotal();
                            existing[1] += sp.getExchangesFailed();
                        } else {
                            metrics.put(key, new long[] { sp.getExchangesTotal(), sp.getExchangesFailed() });
                        }
                    }
                } catch (Exception e) {
                    // skip this processor
                }
            }
        }
        return metrics;
    }

    private static String stripDoubleSlash(String uri) {
        int idx = uri.indexOf("://");
        if (idx > 0) {
            return uri.substring(0, idx + 1) + uri.substring(idx + 3);
        }
        return uri;
    }

}
