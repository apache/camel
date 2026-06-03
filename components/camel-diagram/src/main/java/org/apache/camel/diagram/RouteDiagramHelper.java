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
package org.apache.camel.diagram;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Utility class for parsing Camel route structure JSON into {@link RouteDiagramLayoutEngine.RouteInfo} objects that can
 * be rendered as diagrams.
 */
public final class RouteDiagramHelper {

    private RouteDiagramHelper() {
    }

    /**
     * Parses a JSON object containing route structure data into a list of {@link RouteInfo} objects.
     *
     * @param  jo the JSON object with a "routes" array produced by the route-structure action
     * @return    a list of parsed routes, empty if the input contains no routes
     */
    public static List<RouteInfo> parseRoutes(JsonObject jo) {
        List<RouteInfo> routes = new ArrayList<>();
        JsonArray arr = (JsonArray) jo.get("routes");
        if (arr == null) {
            return routes;
        }

        for (int i = 0; i < arr.size(); i++) {
            Object item = arr.get(i);
            if (!(item instanceof JsonObject)) {
                continue;
            }
            JsonObject o = (JsonObject) item;
            RouteInfo route = new RouteInfo();
            route.routeId = o.getString("routeId");
            String source = o.getString("source");
            route.source = extractSourceName(source);

            List<JsonObject> lines = o.getCollection("code");
            if (lines != null) {
                for (JsonObject line : lines) {
                    NodeInfo node = new NodeInfo();
                    node.type = line.getString("type");
                    node.id = line.getString("id");
                    node.code = Jsoner.unescape(line.getString("code"));
                    String uri = line.getString("uri");
                    if (uri != null) {
                        node.uri = Jsoner.unescape(uri);
                    }
                    node.description = line.getString("description");
                    Integer level = line.getInteger("level");
                    node.level = level != null ? level : 0;
                    Integer lineNum = line.getInteger("line");
                    node.line = lineNum != null ? lineNum : 0;

                    if (line.containsKey("statistics")) {
                        JsonObject ls = line.getJsonObject("statistics");
                        RouteDiagramLayoutEngine.StatInfo stat;
                        if ("route".equals(node.type)) {
                            // route has some special stats
                            var s = new RouteDiagramLayoutEngine.RouteStatInfo();
                            s.coverage = ls.getString("coverage");
                            s.load01 = ls.getString("load01");
                            s.load05 = ls.getString("load05");
                            s.load15 = ls.getString("load15");
                            s.exchangesThroughput = ls.getString("exchangesThroughput");
                            stat = s;
                        } else {
                            // common stats
                            stat = new RouteDiagramLayoutEngine.StatInfo();
                        }
                        node.stat = stat;
                        stat.idleSince = ls.getLong("idleSince");
                        stat.exchangesTotal = ls.getLong("exchangesTotal");
                        stat.exchangesFailed = ls.getLong("exchangesFailed");
                        stat.exchangesInflight = ls.getLong("exchangesInflight");
                        stat.meanProcessingTime = ls.getLong("meanProcessingTime");
                        stat.maxProcessingTime = ls.getLong("maxProcessingTime");
                        stat.minProcessingTime = ls.getLong("minProcessingTime");
                        stat.lastProcessingTime = ls.getLongOrDefault("lastProcessingTime", -1);
                        stat.deltaProcessingTime = ls.getLongOrDefault("deltaProcessingTime", -1);
                        stat.lastCreatedExchangeTimestamp = ls.getLongOrDefault("lastCreatedExchangeTimestamp", -1);
                        stat.lastCompletedExchangeTimestamp = ls.getLongOrDefault("lastCompletedExchangeTimestamp", -1);
                        stat.lastFailedExchangeTimestamp = ls.getLongOrDefault("lastFailedExchangeTimestamp", -1);
                    }
                    route.nodes.add(node);
                }
            }
            routes.add(route);
        }
        return routes;
    }

    public enum HighlightStyle {
        SUCCESS,
        FAIL
    }

    public static class HighlightInfo {
        private final Set<String> nodeIds;
        private final List<String> routeOrder;
        private final HighlightStyle style;

        public HighlightInfo(Set<String> nodeIds, List<String> routeOrder, HighlightStyle style) {
            this.nodeIds = nodeIds;
            this.routeOrder = routeOrder;
            this.style = style;
        }

        public Set<String> getNodeIds() {
            return nodeIds;
        }

        public List<String> getRouteOrder() {
            return routeOrder;
        }

        public HighlightStyle getStyle() {
            return style;
        }
    }

    /**
     * Parses message history entries into a {@link HighlightInfo} containing the node IDs to highlight and the route
     * ordering (by first visit).
     *
     * @param  messageHistory array of history entries in the format {@code "routeId[nodeId] (elapsed ms)"}
     * @param  style          the highlight style to use
     * @return                highlight info with node IDs and route ordering
     */
    public static HighlightInfo parseMessageHistory(String[] messageHistory, HighlightStyle style) {
        Set<String> nodeIds = new LinkedHashSet<>();
        Set<String> routeOrderSet = new LinkedHashSet<>();
        if (messageHistory != null) {
            for (String h : messageHistory) {
                int bracket = h.indexOf('[');
                int end = h.indexOf(']');
                if (bracket >= 0 && end > bracket) {
                    routeOrderSet.add(h.substring(0, bracket));
                    nodeIds.add(h.substring(bracket + 1, end));
                }
            }
        }
        return new HighlightInfo(nodeIds, new ArrayList<>(routeOrderSet), style);
    }

    /**
     * Filters and orders routes based on the highlight info. Only routes that contain at least one highlighted node are
     * included, and they are ordered by the sequence in which the message first visited each route.
     *
     * @param  routes    the full list of parsed routes
     * @param  highlight the highlight info containing route ordering
     * @return           filtered and ordered list of routes
     */
    public static List<RouteInfo> filterAndOrderRoutes(List<RouteInfo> routes, HighlightInfo highlight) {
        if (highlight == null || highlight.routeOrder.isEmpty()) {
            return routes;
        }
        Set<String> nodeIds = highlight.nodeIds;
        List<String> order = highlight.routeOrder;

        List<RouteInfo> result = new ArrayList<>();
        for (RouteInfo route : routes) {
            boolean hasHighlightedNode = route.nodes.stream()
                    .anyMatch(n -> n.id != null && nodeIds.contains(n.id));
            if (hasHighlightedNode) {
                result.add(route);
            }
        }
        result.sort((a, b) -> {
            int ia = order.indexOf(a.routeId);
            int ib = order.indexOf(b.routeId);
            if (ia < 0) {
                ia = Integer.MAX_VALUE;
            }
            if (ib < 0) {
                ib = Integer.MAX_VALUE;
            }
            return Integer.compare(ia, ib);
        });
        return result;
    }

    /**
     * Extracts a short display name from a source path. For example, {@code "file:/path/to/my-route.yaml"} becomes
     * {@code "my-route.yaml"}.
     *
     * @param  source the full source string, may be null
     * @return        the extracted file name, or null if source is null or blank
     */
    static String extractSourceName(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        source = source.replace(' ', '-');
        if (source.startsWith("source:")) {
            source = source.substring(7);
            // skip middle packages
            if (source.contains(".")) {
                source = StringHelper.afterLast(source, ".");
            }
        }
        source = LoggerHelper.sourceNameOnly(source);
        source = LoggerHelper.stripScheme(source);
        source = FileUtil.stripPath(source);
        return source;
    }
}
