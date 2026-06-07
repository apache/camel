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
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * In-memory {@link SpanExporter} for development use. Stores finished spans in a bounded queue so they can be queried
 * by the dev console and other local tooling.
 *
 * Auto-configured by {@link OpenTelemetryTracer} when the Camel profile is "dev".
 */
final class DevSpanExporter implements SpanExporter {

    static final int DEFAULT_CAPACITY = 500;

    private final Queue<SpanData> spans;
    private final int capacity;
    private volatile boolean stopped;

    DevSpanExporter() {
        this(DEFAULT_CAPACITY);
    }

    DevSpanExporter(int capacity) {
        this.capacity = capacity;
        this.spans = new LinkedBlockingQueue<>(capacity);
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spanDataList) {
        if (stopped) {
            return CompletableResultCode.ofSuccess();
        }
        for (SpanData span : spanDataList) {
            while (!spans.offer(span)) {
                spans.poll();
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
        return new ArrayList<>(spans);
    }

    int getSpanCount() {
        return spans.size();
    }

    int getCapacity() {
        return capacity;
    }

    void reset() {
        spans.clear();
    }
}
