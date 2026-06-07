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
package org.apache.camel.opentelemetry2;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.Route;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "opentelemetry", displayName = "OpenTelemetry Spans",
            description = "OpenTelemetry span data captured in dev mode")
public class OpenTelemetryDevConsole extends AbstractDevConsole {

    public static final String DUMP = "dump";
    public static final String LIMIT = "limit";

    public OpenTelemetryDevConsole() {
        super("camel", "opentelemetry", "OpenTelemetry Spans",
              "OpenTelemetry span data captured in dev mode");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        DevSpanExporter exporter = findExporter();
        if (exporter == null) {
            return "OpenTelemetry in-memory exporter is not enabled (requires dev profile)\n";
        }

        String dump = (String) options.get(DUMP);
        if (dump != null) {
            int limit = Integer.parseInt((String) options.getOrDefault(LIMIT, "100"));
            List<SpanData> spans = exporter.getFinishedSpans();
            int start = Math.max(0, spans.size() - limit);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < spans.size(); i++) {
                SpanData span = spans.get(i);
                long durationMs = (span.getEndEpochNanos() - span.getStartEpochNanos()) / 1_000_000;
                sb.append(String.format("  TraceId: %s SpanId: %s Name: %s Kind: %s Status: %s Duration: %dms%n",
                        span.getTraceId(), span.getSpanId(), span.getName(),
                        span.getKind(), span.getStatus().getStatusCode(), durationMs));
            }
            return sb.toString();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Enabled: true\n");
        sb.append(String.format("Span Count: %d%n", exporter.getSpanCount()));
        sb.append(String.format("Capacity: %d%n", exporter.getCapacity()));
        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        DevSpanExporter exporter = findExporter();
        if (exporter == null) {
            root.put("enabled", false);
            return root;
        }

        root.put("enabled", true);

        String dump = (String) options.get(DUMP);
        if (dump != null) {
            int limit = Integer.parseInt((String) options.getOrDefault(LIMIT, "100"));
            List<SpanData> spans = exporter.getFinishedSpans();
            int start = Math.max(0, spans.size() - limit);

            // Build lookup map for enriching spans with route context
            Map<String, String> endpointToRouteId = new HashMap<>();
            buildEnrichmentMaps(endpointToRouteId);

            // First pass: convert spans to JSON and resolve routeIds for endpoint spans
            JsonArray arr = new JsonArray();
            Map<String, String> spanIdToRouteId = new HashMap<>();
            Map<String, String> spanIdToParent = new HashMap<>();
            for (int i = start; i < spans.size(); i++) {
                SpanData sd = spans.get(i);
                JsonObject jo = spanToJson(sd, endpointToRouteId);
                arr.add(jo);
                // Track routeId and parent relationships for propagation
                if (jo.containsKey("routeId")) {
                    spanIdToRouteId.put(sd.getSpanId(), jo.getString("routeId"));
                }
                String pid = sd.getParentSpanId();
                if (pid != null && !pid.isEmpty() && !"0000000000000000".equals(pid)) {
                    spanIdToParent.put(sd.getSpanId(), pid);
                }
            }

            // Second pass: propagate routeId to processor spans by walking parent chain
            for (int i = 0; i < arr.size(); i++) {
                JsonObject jo = (JsonObject) arr.get(i);
                if (!jo.containsKey("routeId")) {
                    String spanId = jo.getString("spanId");
                    String inheritedRouteId = findAncestorRouteId(spanId, spanIdToRouteId, spanIdToParent);
                    if (inheritedRouteId != null) {
                        jo.put("routeId", inheritedRouteId);
                    }
                }
            }
            root.put("spans", arr);
        } else {
            root.put("spanCount", exporter.getSpanCount());
            root.put("capacity", exporter.getCapacity());
        }

        return root;
    }

    private DevSpanExporter findExporter() {
        return CamelContextHelper.findSingleByType(getCamelContext(), DevSpanExporter.class);
    }

    private void buildEnrichmentMaps(Map<String, String> endpointToRouteId) {
        try {
            // Map route endpoint URIs (sanitized) to route IDs
            for (Route route : getCamelContext().getRoutes()) {
                if (route.getEndpoint() != null && route.getId() != null) {
                    endpointToRouteId.put(route.getEndpoint().getEndpointUri(), route.getId());
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    @SuppressWarnings("unchecked")
    private static JsonObject spanToJson(SpanData span, Map<String, String> endpointToRouteId) {
        JsonObject jo = new JsonObject();
        jo.put("traceId", span.getTraceId());
        jo.put("spanId", span.getSpanId());
        String parentSpanId = span.getParentSpanId();
        if (parentSpanId != null && !parentSpanId.isEmpty()
                && !"0000000000000000".equals(parentSpanId)) {
            jo.put("parentSpanId", parentSpanId);
        }
        jo.put("name", span.getName());
        jo.put("kind", span.getKind().name());
        jo.put("status", span.getStatus().getStatusCode().name());
        jo.put("startEpochNanos", span.getStartEpochNanos());
        jo.put("endEpochNanos", span.getEndEpochNanos());
        jo.put("durationMs", (span.getEndEpochNanos() - span.getStartEpochNanos()) / 1_000_000);

        JsonObject attrs = new JsonObject();
        span.getAttributes().forEach((key, value) -> attrs.put(key.getKey(), value));
        if (!attrs.isEmpty()) {
            jo.put("attributes", attrs);
        }

        // Enrich with route context from endpoint URI
        String uri = span.getAttributes().get(AttributeKey.stringKey("camel.uri"));
        if (uri != null) {
            String routeId = endpointToRouteId.get(uri);
            if (routeId != null) {
                jo.put("routeId", routeId);
            }
        }

        // Enrich processor spans with processorId extracted from span name (format: id-shortName)
        String op = span.getAttributes().get(AttributeKey.stringKey("op"));
        if ("EVENT_PROCESS".equals(op)) {
            String name = span.getName();
            int dash = name.lastIndexOf('-');
            if (dash > 0) {
                jo.put("processorId", name.substring(0, dash));
            }
        }

        return jo;
    }

    private static String findAncestorRouteId(
            String spanId, Map<String, String> spanIdToRouteId, Map<String, String> spanIdToParent) {
        String current = spanId;
        int maxDepth = 50;
        while (current != null && maxDepth-- > 0) {
            String parent = spanIdToParent.get(current);
            if (parent == null) {
                return null;
            }
            String routeId = spanIdToRouteId.get(parent);
            if (routeId != null) {
                return routeId;
            }
            current = parent;
        }
        return null;
    }
}
