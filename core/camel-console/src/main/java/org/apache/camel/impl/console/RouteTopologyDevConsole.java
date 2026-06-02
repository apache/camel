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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.api.management.mbean.ManagedSendProcessorMBean;
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

@DevConsole(name = "route-topology", description = "Route topology showing inter-route connections")
public class RouteTopologyDevConsole extends AbstractDevConsole {

    private static final String METRIC = "metric";
    private static final String EXTERNAL = "external";

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
        boolean external = "true".equals(options.get(EXTERNAL));

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
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();
        RouteTopologyDumper dumper = PluginHelper.getRouteTopologyDumper(getCamelContext());
        if (dumper == null) {
            return root;
        }
        TopologyResult result = dumper.dumpTopology(getCamelContext());

        boolean metric = "true".equals(options.get(METRIC));
        boolean external = "true".equals(options.get(EXTERNAL));
        ManagedCamelContext mcc = metric
                ? getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class)
                : null;

        JsonArray nodesArr = new JsonArray();
        for (TopologyNode node : result.nodes()) {
            JsonObject jo = new JsonObject();
            jo.put("routeId", node.routeId());
            if (node.description() != null) {
                jo.put("description", node.description());
            }
            jo.put("from", node.from());
            jo.put("fromScheme", node.fromScheme());
            jo.put("nodeType", node.nodeType());

            if (mcc != null) {
                ManagedRouteMBean mrb = mcc.getManagedRoute(node.routeId());
                if (mrb != null) {
                    jo.put("exchangesTotal", mrb.getExchangesTotal());
                    jo.put("exchangesFailed", mrb.getExchangesFailed());
                }
            }

            nodesArr.add(jo);
        }
        root.put("nodes", nodesArr);

        JsonArray edgesArr = new JsonArray();
        for (TopologyEdge edge : result.edges()) {
            JsonObject jo = new JsonObject();
            jo.put("fromRouteId", edge.fromRouteId());
            jo.put("toRouteId", edge.toRouteId());
            jo.put("endpoint", edge.endpoint());
            jo.put("connectionType", edge.connectionType());
            edgesArr.add(jo);
        }
        root.put("edges", edgesArr);

        if (external && !result.externalEndpoints().isEmpty()) {
            // Collect per-endpoint metrics for producers (direction=out)
            Map<String, long[]> endpointMetrics = collectEndpointMetrics(mcc, result);

            JsonArray extArr = new JsonArray();
            for (TopologyExternalEndpoint ep : result.externalEndpoints()) {
                JsonObject jo = new JsonObject();
                jo.put("id", ep.id());
                jo.put("uri", ep.uri());
                jo.put("scheme", ep.scheme());
                jo.put("direction", ep.direction());
                jo.put("routeId", ep.routeId());

                if (mcc != null) {
                    if ("in".equals(ep.direction())) {
                        // Consumer: use route-level metrics (route has exactly 1 consumer)
                        ManagedRouteMBean mrb = mcc.getManagedRoute(ep.routeId());
                        if (mrb != null) {
                            jo.put("exchangesTotal", mrb.getExchangesTotal());
                            jo.put("exchangesFailed", mrb.getExchangesFailed());
                        }
                    } else {
                        // Producer: use processor-level metrics
                        String key = ep.routeId() + "|" + ep.uri();
                        long[] stats = endpointMetrics.get(key);
                        if (stats != null) {
                            jo.put("exchangesTotal", stats[0]);
                            jo.put("exchangesFailed", stats[1]);
                        }
                    }
                }

                extArr.add(jo);
            }
            root.put("externalEndpoints", extArr);
        }

        return root;
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
