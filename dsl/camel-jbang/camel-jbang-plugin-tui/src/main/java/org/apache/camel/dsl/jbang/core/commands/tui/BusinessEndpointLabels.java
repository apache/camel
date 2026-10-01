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

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.tooling.model.ComponentModel;

/**
 * The endpoints of a route diagram in plain words for the business view (CAMEL-25161): {@code File: src/main/data}
 * rather than {@code from[file:src/main/data?noop=true]}. The component's title comes from the catalog, the options are
 * left out. A step with a description of its own keeps it.
 */
public final class BusinessEndpointLabels {

    /** The steps whose uri is an endpoint. */
    static final Set<String> ENDPOINT_TYPES = Set.of("from", "to", "toD", "wireTap", "enrich", "pollEnrich", "poll");

    private BusinessEndpointLabels() {
    }

    static void apply(List<RouteInfo> routes) {
        CamelCatalog catalog = ProjectOverviewAssist.catalog();
        for (RouteInfo r : routes) {
            for (NodeInfo n : r.nodes) {
                if (n.type != null && ENDPOINT_TYPES.contains(n.type)
                        && (n.description == null || n.description.isBlank())) {
                    String label = label(DiagramSupport.getBaseUri(n), catalog);
                    if (label != null) {
                        n.description = label;
                    }
                }
            }
        }
    }

    /**
     * The system of an endpoint by its component's title: {@code AMQP} for {@code amqp:queue:x}; null without a uri.
     */
    public static String systemName(NodeInfo node) {
        String label = node != null ? label(DiagramSupport.getBaseUri(node), ProjectOverviewAssist.catalog()) : null;
        if (label == null) {
            return null;
        }
        int colon = label.indexOf(':');
        return colon > 0 ? label.substring(0, colon) : label;
    }

    /** {@code amqp:queue:order.queue} as {@code AMQP: queue:order.queue}; null when there is no uri. */
    static String label(String uri, CamelCatalog catalog) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        int colon = uri.indexOf(':');
        if (colon <= 0) {
            return uri;
        }
        String scheme = uri.substring(0, colon);
        String rest = uri.substring(colon + 1);
        if (rest.startsWith("//")) {
            rest = rest.substring(2);
        }
        String title = null;
        ComponentModel model = catalog != null ? catalog.componentModel(scheme) : null;
        if (model != null && model.getTitle() != null && !model.getTitle().isBlank()) {
            title = model.getTitle();
        }
        if (title == null) {
            title = scheme.substring(0, 1).toUpperCase(Locale.ROOT) + scheme.substring(1);
        }
        return rest.isBlank() ? title : title + ": " + rest;
    }
}
