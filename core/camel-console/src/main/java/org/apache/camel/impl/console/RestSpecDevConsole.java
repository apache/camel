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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RestRegistry;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "rest-spec", displayName = "Rest Spec",
            description = "OpenAPI specification content for contract-first REST services")
public class RestSpecDevConsole extends AbstractDevConsole {

    public record SpecificationEntry(
            @Metadata(description = "The OpenAPI specification URI") String specificationUri,
            @Metadata(description = "The route ID (only present when known)") String routeId,
            @Metadata(description = "The specification content (only present when it could be loaded)") String content) {
    }

    public record Response(@Metadata(description = "The OpenAPI specifications") List<SpecificationEntry> specs) {
    }

    @Metadata(label = "query", description = "Filters specifications matching the given URI pattern",
              javaType = "java.lang.String")
    public static final String FILTER = "filter";

    public RestSpecDevConsole() {
        super("camel", "rest-spec", "Rest Spec", "OpenAPI specification content for contract-first REST services");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        String filter = optionString(options, FILTER);
        StringBuilder sb = new StringBuilder();

        for (SpecEntry entry : collectSpecs(filter)) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(String.format("Specification: %s", entry.uri()));
            if (entry.routeId() != null) {
                sb.append(String.format("%n    Route Id: %s", entry.routeId()));
            }
            if (entry.content() != null) {
                sb.append("\n");
                sb.append(entry.content());
            } else {
                sb.append(String.format("%n    (content not available)"));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String filter = optionString(options, FILTER);
        List<SpecificationEntry> list = new ArrayList<>();

        for (SpecEntry entry : collectSpecs(filter)) {
            list.add(new SpecificationEntry(entry.uri(), entry.routeId(), entry.content()));
        }

        Response response = new Response(list);
        return JsonRecordSupport.toJsonObject(response);
    }

    private Set<SpecEntry> collectSpecs(String filter) {
        Set<SpecEntry> result = new LinkedHashSet<>();
        RestRegistry rr = PluginHelper.getRestRegistry(getCamelContext());
        if (rr == null) {
            return result;
        }

        // track which URIs we've already loaded to avoid duplicates (many operations share one spec)
        Set<String> seen = new LinkedHashSet<>();

        for (RestRegistry.RestService rs : rr.listAllRestServices()) {
            String specUri = rs.getSpecificationUri();
            if (specUri == null || seen.contains(specUri)) {
                continue;
            }
            if (filter != null && !PatternHelper.matchPattern(specUri, filter)) {
                continue;
            }
            seen.add(specUri);

            String content = loadContent(specUri);
            result.add(new SpecEntry(specUri, rs.getRouteId(), content));
        }

        return result;
    }

    private String loadContent(String specUri) {
        try {
            Resource resource = PluginHelper.getResourceLoader(getCamelContext()).resolveResource(specUri);
            if (resource != null && resource.exists()) {
                InputStream is = resource.getInputStream();
                String data = IOHelper.loadText(is);
                IOHelper.close(is);
                return data;
            }
        } catch (Exception e) {
            // ignore — content unavailable
        }
        return null;
    }

    private record SpecEntry(String uri, String routeId, String content) {
    }
}
