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
import java.util.List;

import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
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
            JsonObject o = (JsonObject) arr.get(i);
            RouteInfo route = new RouteInfo();
            route.routeId = o.getString("routeId");
            String source = o.getString("source");
            route.source = extractSourceName(source);

            List<JsonObject> lines = o.getCollection("code");
            if (lines != null) {
                for (JsonObject line : lines) {
                    NodeInfo node = new NodeInfo();
                    node.type = line.getString("type");
                    node.code = Jsoner.unescape(line.getString("code"));
                    Integer level = line.getInteger("level");
                    node.level = level != null ? level : 0;
                    route.nodes.add(node);
                }
            }
            routes.add(route);
        }
        return routes;
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
        // strip scheme prefix (e.g. "file:")
        int colon = source.lastIndexOf(':');
        if (colon >= 0 && colon < source.length() - 1) {
            source = source.substring(colon + 1);
        }
        // return just the filename part
        int slash = Math.max(source.lastIndexOf('/'), source.lastIndexOf('\\'));
        if (slash >= 0 && slash < source.length() - 1) {
            return source.substring(slash + 1);
        }
        return source;
    }
}
