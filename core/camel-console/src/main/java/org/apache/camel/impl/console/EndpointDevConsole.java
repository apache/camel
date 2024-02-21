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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

@DevConsole("endpoint")
public class EndpointDevConsole extends AbstractDevConsole {

    public EndpointDevConsole() {
        super("camel", "endpoint", "Endpoints", "Endpoint Registry information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        // runtime registry is optional but if enabled we have additional statistics to use in output
        List<RuntimeEndpointRegistry.Statistic> stats = null;
        RuntimeEndpointRegistry runtimeReg = getCamelContext().getRuntimeEndpointRegistry();
        if (runtimeReg != null) {
            stats = runtimeReg.getEndpointStatistics();
        }
        EndpointRegistry<?> reg = getCamelContext().getEndpointRegistry();
        sb.append(
                String.format("    Endpoints: %s (static: %s dynamic: %s)\n", reg.size(), reg.staticSize(), reg.dynamicSize()));
        sb.append(String.format("    Maximum Cache Size: %s\n", reg.getMaximumCacheSize()));
        Collection<Endpoint> col = reg.getReadOnlyValues();
        if (!col.isEmpty()) {
            for (Endpoint e : col) {
                boolean stub = e.getComponent().getClass().getSimpleName().equals("StubComponent");
                String uri = e.toString();
                if (!uri.startsWith("stub:") && stub) {
                    // shadow-stub
                    uri = uri + " (stub)";
                }
                var stat = findStats(stats, e.getEndpointUri());
                if (stat.isPresent()) {
                    var st = stat.get();
                    sb.append(String.format("\n    %s (direction: %s, usage: %s)", uri, st.getDirection(), st.getHits()));
                } else {
                    sb.append(String.format("\n    %s", uri));
                }
            }
        }
        sb.append("\n");

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        // runtime registry is optional but if enabled we have additional statistics to use in output
        List<RuntimeEndpointRegistry.Statistic> stats = null;
        RuntimeEndpointRegistry runtimeReg = getCamelContext().getRuntimeEndpointRegistry();
        if (runtimeReg != null) {
            stats = runtimeReg.getEndpointStatistics();
        }
        EndpointRegistry<?> reg = getCamelContext().getEndpointRegistry();
        root.put("size", reg.size());
        root.put("staticSize", reg.staticSize());
        root.put("dynamicSize", reg.dynamicSize());
        root.put("maximumCacheSize", reg.getMaximumCacheSize());

        final List<JsonObject> list = new ArrayList<>();
        root.put("endpoints", list);
        Collection<Endpoint> col = reg.getReadOnlyValues();
        for (Endpoint e : col) {
            JsonObject jo = new JsonObject();
            boolean stub = e.getComponent().getClass().getSimpleName().equals("StubComponent");
            jo.put("uri", e.getEndpointUri());
            jo.put("stub", stub);
            var stat = findStats(stats, e.getEndpointUri());
            if (stat.isPresent()) {
                var st = stat.get();
                jo.put("direction", st.getDirection());
                jo.put("hits", st.getHits());
                jo.put("routeId", st.getRouteId());
            }
            list.add(jo);
        }

        return root;
    }

    private static Optional<RuntimeEndpointRegistry.Statistic> findStats(
            List<RuntimeEndpointRegistry.Statistic> stats, String uri) {
        if (stats == null) {
            return Optional.empty();
        }
        return stats.stream()
                .filter(s -> uri.equals(s.getUri()))
                .findFirst();
    }
}
