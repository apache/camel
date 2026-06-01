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

import java.util.Map;

import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.spi.RouteTopologyDumper;
import org.apache.camel.spi.RouteTopologyDumper.TopologyEdge;
import org.apache.camel.spi.RouteTopologyDumper.TopologyNode;
import org.apache.camel.spi.RouteTopologyDumper.TopologyResult;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "route-topology", description = "Route topology showing inter-route connections")
public class RouteTopologyDevConsole extends AbstractDevConsole {

    private static final String METRIC = "metric";

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

        return root;
    }

}
