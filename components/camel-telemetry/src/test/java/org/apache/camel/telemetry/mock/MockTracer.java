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

package org.apache.camel.telemetry.mock;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.telemetry.Span;
import org.apache.camel.telemetry.SpanContextPropagationExtractor;
import org.apache.camel.telemetry.SpanContextPropagationInjector;
import org.apache.camel.telemetry.SpanLifecycleManager;
import org.apache.camel.telemetry.Tracer;

@JdkService("mock-tracer")
@Configurer
@ManagedResource(description = "MockTracer")
public class MockTracer extends Tracer {

    MockSpanLifecycleManager slcm;

    @Override
    protected void initTracer() {
        this.slcm = new MockSpanLifecycleManager();
        setSpanLifecycleManager(this.slcm);
    }

    public Map<String, MockTrace> traces() {
        return slcm.traces();
    }

    private class MockSpanLifecycleManager implements SpanLifecycleManager {

        // Used to collect the traces for later evaluation as traces
        Map<String, Span> inMemoryStorageMap = new HashMap<>();

        @Override
        public Span create(String spanName, Span parentSpan, SpanContextPropagationExtractor extractor) {
            Span span = MockSpanAdapter.buildSpan(spanName);
            String traceId = UUID.randomUUID().toString().replaceAll("-", "");
            if (parentSpan != null) {
                traceId = spanTraceId(parentSpan);
                span.setTag("parentSpan", spanSpanId(parentSpan));
            } else {
                String upstreamTraceParent = (String) extractor.get("traceparent");
                if (upstreamTraceParent != null) {
                    traceId = upstreamTraceParent.toString().split("-")[0];
                    String parentSpanId = upstreamTraceParent.toString().split("-")[1];
                    span.setTag("parentSpan", parentSpanId);
                }
            }
            span.setTag("traceid", traceId);
            span.setTag("spanid", UUID.randomUUID().toString().replaceAll("-", ""));
            return span;
        }

        @Override
        public void activate(Span span) {
            span.setTag("initTimestamp", "" + System.nanoTime());
            inMemoryStorageMap.put(span.toString(), span);
        }

        @Override
        public void deactivate(Span span) {
            span.setTag("endTimestamp", "" + System.nanoTime());
        }

        @Override
        public void close(Span span) {
            span.setTag("isDone", "true");
        }

        @Override
        public void inject(Span span, SpanContextPropagationInjector injector) {
            injector.put("traceparent", span.toString());
        }

        public Map<String, MockTrace> traces() {
            Map<String, MockTrace> traces = new HashMap<>();
            for (Span span : inMemoryStorageMap.values()) {
                String traceId = spanTraceId(span);
                MockTrace trace = traces.get(traceId);
                if (trace == null) {
                    trace = new MockTrace();
                    traces.put(traceId, trace);
                }
                trace.addSpan(span);
            }
            return traces;
        }

    }

    private static String spanTraceId(Span span) {
        if (span == null) {
            return "";
        }
        return span.toString().split("-")[0];
    }

    private static String spanSpanId(Span span) {
        if (span == null) {
            return "";
        }
        return span.toString().split("-")[1];
    }

}
