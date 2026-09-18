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

import org.apache.camel.Endpoint;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "endpoint", displayName = "Endpoints", description = "Endpoint Registry information")
public class EndpointDevConsole extends AbstractDevConsole {

    public record EndpointEntry(
            @Metadata(description = "The endpoint URI") String uri,
            @Metadata(description = "Whether the endpoint is remote") boolean remote,
            @Metadata(description = "Whether the endpoint is a stub endpoint") boolean stub,
            @Metadata(description = "Whether the endpoint is used as input or output (in or out) (only present when runtime endpoint registry statistics are available)") String direction,
            @Metadata(description = "Usage of the endpoint (only present when runtime endpoint registry statistics are available)") Long hits,
            @Metadata(description = "The route ID (only present when runtime endpoint registry statistics are available and the endpoint is associated with a route)") String routeId,
            @Metadata(description = "Minimum message body size in bytes (only present when available)") Long minBodySize,
            @Metadata(description = "Maximum message body size in bytes (only present when available)") Long maxBodySize,
            @Metadata(description = "Mean message body size in bytes (only present when available)") Long meanBodySize,
            @Metadata(description = "Minimum message headers size in bytes (only present when available)") Long minHeadersSize,
            @Metadata(description = "Maximum message headers size in bytes (only present when available)") Long maxHeadersSize,
            @Metadata(description = "Mean message headers size in bytes (only present when available)") Long meanHeadersSize) {
    }

    public record Response(
            @Metadata(description = "Total number of endpoints") int size,
            @Metadata(description = "Number of endpoints in the static registry") int staticSize,
            @Metadata(description = "Number of endpoints in the dynamic registry") int dynamicSize,
            @Metadata(description = "Maximum number of entries to store in the dynamic registry") int maximumCacheSize,
            @Metadata(description = "The endpoints") List<EndpointEntry> endpoints) {
    }

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
        EndpointRegistry reg = getCamelContext().getEndpointRegistry();
        sb.append(
                String.format("    Endpoints: %s (static: %s dynamic: %s)%n", reg.size(), reg.staticSize(), reg.dynamicSize()));
        sb.append(String.format("    Maximum Cache Size: %s%n", reg.getMaximumCacheSize()));
        Collection<Endpoint> col = reg.getReadOnlyValues();
        if (!col.isEmpty()) {
            for (Endpoint e : col) {
                // NOTE: StubComponent is not available at compilation time.
                boolean stub = e.getComponent().getClass().getSimpleName().equals("StubComponent"); // NOSONAR
                boolean remote = e.isRemote();
                String uri = e.toString();
                if (!uri.startsWith("stub:") && stub) {
                    // shadow-stub
                    uri = uri + " (stub)";
                }
                List<RuntimeEndpointRegistry.Statistic> endpointStats = findStats(stats, e.getEndpointUri());
                if (!endpointStats.isEmpty()) {
                    for (RuntimeEndpointRegistry.Statistic st : endpointStats) {
                        sb.append(String.format("%n    %s (remote: %s direction: %s, usage: %s)", uri, remote,
                                st.getDirection(), st.getHits()));
                        if (st.getMinBodySize() >= 0) {
                            sb.append(String.format(" body: min/max/mean=%s/%s/%s",
                                    st.getMinBodySize(), st.getMaxBodySize(), st.getMeanBodySize()));
                        }
                        if (st.getMinHeadersSize() >= 0) {
                            sb.append(String.format(" headers: min/max/mean=%s/%s/%s",
                                    st.getMinHeadersSize(), st.getMaxHeadersSize(), st.getMeanHeadersSize()));
                        }
                    }
                } else {
                    sb.append(String.format("%n    %s (remote: %s)", uri, remote));
                }
            }
        }
        sb.append("\n");

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        // runtime registry is optional but if enabled we have additional statistics to use in output
        List<RuntimeEndpointRegistry.Statistic> stats = null;
        RuntimeEndpointRegistry runtimeReg = getCamelContext().getRuntimeEndpointRegistry();
        if (runtimeReg != null) {
            stats = runtimeReg.getEndpointStatistics();
        }
        EndpointRegistry reg = getCamelContext().getEndpointRegistry();

        final List<EndpointEntry> list = new ArrayList<>();
        Collection<Endpoint> col = reg.getReadOnlyValues();
        for (Endpoint e : col) {
            // NOTE: StubComponent is not available at compilation time.
            boolean stub = e.getComponent().getClass().getSimpleName().equals("StubComponent"); // NOSONAR
            boolean remote = e.isRemote();
            List<RuntimeEndpointRegistry.Statistic> endpointStats = findStats(stats, e.getEndpointUri());
            if (!endpointStats.isEmpty()) {
                // emit one entry per direction so both in and out hits are captured
                for (RuntimeEndpointRegistry.Statistic st : endpointStats) {
                    Long minBodySize = null;
                    Long maxBodySize = null;
                    Long meanBodySize = null;
                    if (st.getMinBodySize() >= 0) {
                        minBodySize = st.getMinBodySize();
                        maxBodySize = st.getMaxBodySize();
                        meanBodySize = st.getMeanBodySize();
                    }
                    Long minHeadersSize = null;
                    Long maxHeadersSize = null;
                    Long meanHeadersSize = null;
                    if (st.getMinHeadersSize() >= 0) {
                        minHeadersSize = st.getMinHeadersSize();
                        maxHeadersSize = st.getMaxHeadersSize();
                        meanHeadersSize = st.getMeanHeadersSize();
                    }
                    list.add(new EndpointEntry(
                            e.getEndpointUri(), remote, stub, st.getDirection(), st.getHits(), st.getRouteId(),
                            minBodySize, maxBodySize, meanBodySize, minHeadersSize, maxHeadersSize, meanHeadersSize));
                }
            } else {
                list.add(new EndpointEntry(
                        e.getEndpointUri(), remote, stub, null, null, null, null, null, null, null, null, null));
            }
        }

        Response response = new Response(reg.size(), reg.staticSize(), reg.dynamicSize(), reg.getMaximumCacheSize(), list);
        return JsonRecordSupport.toJsonObject(response);
    }

    private static List<RuntimeEndpointRegistry.Statistic> findStats(
            List<RuntimeEndpointRegistry.Statistic> stats, String uri) {
        if (stats == null) {
            return List.of();
        }
        return stats.stream()
                .filter(s -> uri.equals(s.getUri()))
                .toList();
    }
}
