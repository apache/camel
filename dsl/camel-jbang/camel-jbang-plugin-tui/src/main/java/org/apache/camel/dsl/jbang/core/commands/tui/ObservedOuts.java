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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.dsl.jbang.core.commands.ai.About;
import org.apache.camel.dsl.jbang.core.commands.ai.AboutReport;

/**
 * The systems an integration sent to that its routes do not name: a toD or a recipient list computes the endpoint as it
 * routes (http://localhost:8080/stock/${sku}), so the About view, which reads the routes, does not know them. The
 * runtime endpoint registry counted each one; the URIs a route built for one service are one line
 * (http://localhost:8080/stock/…).
 */
final class ObservedOuts {

    /** Marks a line that stands for the URIs under it. */
    static final String MORE = "…";

    private ObservedOuts() {
    }

    /** The outs the integration sent to that are not among those of its routes, for the Out column of About. */
    static List<About.Out> of(IntegrationInfo info, List<About.Out> known) {
        Set<String> knownUris = new HashSet<>();
        for (About.Out o : known) {
            knownUris.add(AboutReport.baseUri(o.uri()));
        }
        // the endpoints each route consumes: what a consumer sends back on them is its reply (rest, platform-http)
        Set<String> consumed = new HashSet<>();
        for (EndpointInfo ep : info.endpoints) {
            if ("in".equals(ep.direction) && ep.routeId != null && ep.uri != null) {
                consumed.add(ep.routeId + "|" + AboutReport.baseUri(ep.uri));
            }
        }
        Map<String, List<String>> groups = new LinkedHashMap<>();
        Map<String, String> routes = new LinkedHashMap<>();
        for (EndpointInfo ep : info.endpoints) {
            if (!"out".equals(ep.direction) || !"remote".equals(ep.scope) || ep.routeId == null || ep.uri == null
                    || ep.hits <= 0) {
                continue;
            }
            String base = AboutReport.baseUri(ep.uri);
            if (knownUris.contains(base) || consumed.contains(ep.routeId + "|" + base)) {
                continue;
            }
            String key = ep.routeId + "|" + group(base);
            // as the endpoint is written, without its options
            int q = ep.uri.indexOf('?');
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(q >= 0 ? ep.uri.substring(0, q) : ep.uri);
            routes.put(key, ep.routeId);
        }
        List<About.Out> answer = new ArrayList<>();
        for (var e : groups.entrySet()) {
            List<String> uris = e.getValue().stream().distinct().toList();
            String uri = uris.size() == 1 ? uris.get(0) : group(uris.get(0)) + MORE;
            answer.add(new About.Out(scheme(uri), uri, "out", routes.get(e.getKey())));
        }
        return answer;
    }

    /**
     * The service of a URI with a path: all but its last segment (http:localhost:8080/stock/ of
     * http:localhost:8080/stock/CAMEL-MUG), as a computed URI puts the id or the name there; a URI without a path is
     * its own service.
     */
    static String group(String base) {
        int scheme = base.indexOf(':');
        int slash = base.lastIndexOf('/');
        // after the host: the // after the scheme is not a path
        int start = base.startsWith("//", scheme + 1) ? scheme + 3 : scheme + 1;
        int host = base.indexOf('/', start);
        return slash > scheme && host >= 0 && slash > host ? base.substring(0, slash + 1) : base;
    }

    private static String scheme(String uri) {
        int colon = uri.indexOf(':');
        String scheme = colon > 0 ? uri.substring(0, colon) : uri;
        return scheme.startsWith("http") || scheme.endsWith("http") ? "http" : scheme;
    }

    /** Whether the URI of a line stands for the URIs under it, and so matches the base URI of an endpoint. */
    static boolean matches(String lineUri, String endpointBaseUri) {
        if (lineUri.endsWith(MORE)) {
            return endpointBaseUri.startsWith(lineUri.substring(0, lineUri.length() - MORE.length()));
        }
        return lineUri.equals(endpointBaseUri);
    }
}
