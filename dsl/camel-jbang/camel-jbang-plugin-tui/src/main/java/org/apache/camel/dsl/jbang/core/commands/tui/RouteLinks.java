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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.diagram.RouteDiagramLayoutEngine;
import org.apache.camel.diagram.RouteErrorFrame;

/**
 * The routes a route diagram links to (the ↵ beside a box): from an endpoint a step sends to, the route that consumes
 * it, and for the from of the route shown, the route that calls it. A send in the error frame of a route (its
 * onException clauses, shown with x; a global one is in every route) is not a call: a route reached on error says where
 * from in its own frame (CAMEL-25466).
 */
final class RouteLinks {

    private RouteLinks() {
    }

    /**
     * @param  currentRouteId the route shown
     * @param  layouts        the layouts of the routes
     * @param  frames         the error frames of the routes, when the error handling is shown
     * @return                the route by the base uri of the endpoint that links to it
     */
    static Map<String, String> linkable(
            String currentRouteId, Map<String, RouteDiagramLayoutEngine.LayoutRoute> layouts,
            Map<String, RouteErrorFrame> frames) {
        Map<String, String> endpoints = new HashMap<>();
        var currentLayout = layouts.get(currentRouteId);
        String currentFromUri = currentLayout != null ? fromUri(currentLayout) : null;
        for (var entry : layouts.entrySet()) {
            if (currentRouteId.equals(entry.getKey())) {
                continue;
            }
            var lr = entry.getValue();
            String fromUri = fromUri(lr);
            if (fromUri != null) {
                endpoints.put(fromUri, entry.getKey());
            }
            if (currentFromUri == null) {
                continue;
            }
            if (sendsTo(lr, currentFromUri, frames.get(entry.getKey()))) {
                endpoints.put(currentFromUri, entry.getKey());
            }
        }
        return endpoints;
    }

    /**
     * Whether a route calls the endpoint: a to, toD, wireTap or enrich of that base uri, not in the error frame of the
     * route.
     *
     * @param frame the error frame of the route, when the error handling is shown, or null
     */
    static boolean sendsTo(RouteDiagramLayoutEngine.LayoutRoute lr, String baseUri, RouteErrorFrame frame) {
        for (var node : lr.nodes) {
            boolean inFrame = frame != null && node.y >= frame.topY();
            if (!inFrame && node.treeNode != null
                    && ("to".equals(node.type) || "toD".equals(node.type) || "case".equals(node.type)
                            || "wireTap".equals(node.type) || "enrich".equals(node.type))
                    && baseUri.equals(DiagramSupport.getBaseUri(node.treeNode.info))) {
                return true;
            }
        }
        return false;
    }

    /** The base uri of the from of a route. */
    static String fromUri(RouteDiagramLayoutEngine.LayoutRoute lr) {
        for (var node : lr.nodes) {
            if ("from".equals(node.type) && node.treeNode != null) {
                return DiagramSupport.getBaseUri(node.treeNode.info);
            }
        }
        return null;
    }
}
