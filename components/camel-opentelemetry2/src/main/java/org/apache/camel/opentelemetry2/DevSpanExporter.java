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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * In-memory {@link SpanExporter} for development use. Stores finished spans in a bounded store so they can be queried
 * by the dev console and other local tooling.
 *
 * Uses trace-aware eviction: when the total span count exceeds capacity, entire traces are evicted (oldest first) to
 * avoid half-complete traces with orphaned spans.
 *
 * Auto-configured by {@link OpenTelemetryTracer} when the Camel profile is "dev".
 */
final class DevSpanExporter implements SpanExporter {

    static final int DEFAULT_CAPACITY = 2000;

    private final int capacity;
    private volatile boolean stopped;

    // LinkedHashMap preserves insertion order of traces (oldest first for eviction)
    private final Map<String, List<SpanData>> traces = new LinkedHashMap<>();
    private int totalSpanCount;

    DevSpanExporter() {
        this(DEFAULT_CAPACITY);
    }

    DevSpanExporter(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spanDataList) {
        if (stopped) {
            return CompletableResultCode.ofSuccess();
        }
        synchronized (traces) {
            for (SpanData span : spanDataList) {
                String traceId = span.getTraceId();
                traces.computeIfAbsent(traceId, k -> new ArrayList<>()).add(span);
                totalSpanCount++;
            }
            // Evict oldest complete traces until we're under capacity
            while (totalSpanCount > capacity && traces.size() > 1) {
                var it = traces.entrySet().iterator();
                if (it.hasNext()) {
                    var oldest = it.next();
                    totalSpanCount -= oldest.getValue().size();
                    it.remove();
                }
            }
        }
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        stopped = true;
        return CompletableResultCode.ofSuccess();
    }

    List<SpanData> getFinishedSpans() {
        synchronized (traces) {
            List<SpanData> result = new ArrayList<>(totalSpanCount);
            for (List<SpanData> traceSpans : traces.values()) {
                result.addAll(traceSpans);
            }
            return result;
        }
    }

    int getSpanCount() {
        synchronized (traces) {
            return totalSpanCount;
        }
    }

    int getCapacity() {
        return capacity;
    }

    void reset() {
        synchronized (traces) {
            traces.clear();
            totalSpanCount = 0;
        }
    }
}
