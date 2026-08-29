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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.camel.Route;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "opentelemetry", displayName = "OpenTelemetry Spans",
            description = "OpenTelemetry span data captured in dev mode")
public class OpenTelemetryDevConsole extends AbstractDevConsole {

    @Metadata(label = "query", description = "Whether to dump span data",
              javaType = "java.lang.String", enums = "true,false")
    public static final String DUMP = "dump";

    @Metadata(label = "query", description = "Limits the number of spans dumped", defaultValue = "100",
              javaType = "java.lang.Integer")
    public static final String LIMIT = "limit";

    public record SpanEntry(
            @Metadata(description = "The trace id") String traceId,
            @Metadata(description = "The span id") String spanId,
            @Metadata(description = "The parent span id (only present when known)") String parentSpanId,
            @Metadata(description = "The span name") String name,
            @Metadata(description = "The span kind") String kind,
            @Metadata(description = "The span status") String status,
            @Metadata(description = "Epoch time in nanoseconds when the span started") long startEpochNanos,
            @Metadata(description = "Epoch time in nanoseconds when the span ended") long endEpochNanos,
            @Metadata(description = "The span duration in milliseconds") long durationMs,
            @Metadata(description = "The instrumentation scope name (only present when known)") String scopeName,
            @Metadata(description = "The span attributes (only present when there are any)") Map<String, Object> attributes,
            @Metadata(description = "The route id this span belongs to (only present when known)") String routeId,
            @Metadata(description = "The processor id this span belongs to (only present for processor spans)") String processorId) {
    }

    public record Response(
            @Metadata(description = "Whether the OpenTelemetry in-memory exporter is enabled") boolean enabled,
            @Metadata(description = "The number of finished spans held (only present when not dumping spans)") Integer spanCount,
            @Metadata(description = "The maximum number of spans held (only present when not dumping spans)") Integer capacity,
            @Metadata(description = "The dumped spans (only present when dumping spans)") List<SpanEntry> spans) {
    }

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

        String dump = optionString(options, DUMP);
        if (dump != null) {
            int limit = optionInt(options, LIMIT, 100);
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
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        DevSpanExporter exporter = findExporter();
        boolean enabled = exporter != null;
        Integer spanCount = null;
        Integer capacity = null;
        List<SpanEntry> spanEntries = null;

        if (exporter != null) {
            String dump = optionString(options, DUMP);
            if (dump != null) {
                int limit = optionInt(options, LIMIT, 100);
                List<SpanData> spans = exporter.getFinishedSpans();
                int start = Math.max(0, spans.size() - limit);
                List<SpanData> selected = spans.subList(start, spans.size());

                // Build lookup map for enriching spans with route context
                Map<String, String> endpointToRouteId = new HashMap<>();
                buildEnrichmentMaps(endpointToRouteId);

                // First pass: resolve direct routeIds for endpoint spans, and track parent relationships
                Map<String, String> directRouteIdBySpanId = new HashMap<>();
                Map<String, String> spanIdToParent = new HashMap<>();
                for (SpanData sd : selected) {
                    String uri = sd.getAttributes().get(AttributeKey.stringKey("camel.uri"));
                    String directRouteId = uri != null ? endpointToRouteId.get(uri) : null;
                    if (directRouteId != null) {
                        directRouteIdBySpanId.put(sd.getSpanId(), directRouteId);
                    }
                    String pid = sd.getParentSpanId();
                    if (pid != null && !pid.isEmpty() && !"0000000000000000".equals(pid)) {
                        spanIdToParent.put(sd.getSpanId(), pid);
                    }
                }

                // Second pass: propagate routeId to processor spans by walking parent chain
                spanEntries = new ArrayList<>();
                for (SpanData sd : selected) {
                    String routeId = directRouteIdBySpanId.get(sd.getSpanId());
                    if (routeId == null) {
                        routeId = findAncestorRouteId(sd.getSpanId(), directRouteIdBySpanId, spanIdToParent);
                    }
                    spanEntries.add(buildSpanEntry(sd, routeId));
                }
            } else {
                spanCount = exporter.getSpanCount();
                capacity = exporter.getCapacity();
            }
        }

        Response response = new Response(enabled, spanCount, capacity, spanEntries);
        return JsonRecordSupport.toJsonObject(response);
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
    private static SpanEntry buildSpanEntry(SpanData span, String routeId) {
        String parentSpanId = span.getParentSpanId();
        if (parentSpanId != null && (parentSpanId.isEmpty() || "0000000000000000".equals(parentSpanId))) {
            parentSpanId = null;
        }

        String scopeName = span.getInstrumentationScopeInfo().getName();
        if (scopeName != null && scopeName.isEmpty()) {
            scopeName = null;
        }

        Map<String, Object> attrs = new LinkedHashMap<>();
        span.getAttributes().forEach((key, value) -> attrs.put(key.getKey(), value));
        Map<String, Object> attributes = attrs.isEmpty() ? null : attrs;

        // Enrich processor spans with processorId extracted from span name (format: id-shortName)
        String processorId = null;
        String op = span.getAttributes().get(AttributeKey.stringKey("op"));
        if ("EVENT_PROCESS".equals(op)) {
            String name = span.getName();
            int dash = name.lastIndexOf('-');
            if (dash > 0) {
                processorId = name.substring(0, dash);
            }
        }

        return new SpanEntry(
                span.getTraceId(), span.getSpanId(), parentSpanId, span.getName(), span.getKind().name(),
                span.getStatus().getStatusCode().name(), span.getStartEpochNanos(), span.getEndEpochNanos(),
                (span.getEndEpochNanos() - span.getStartEpochNanos()) / 1_000_000, scopeName, attributes, routeId,
                processorId);
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
