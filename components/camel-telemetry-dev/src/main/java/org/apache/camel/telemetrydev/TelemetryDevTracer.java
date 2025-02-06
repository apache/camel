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
package org.apache.camel.telemetrydev;

import java.util.UUID;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.telemetry.Span;
import org.apache.camel.telemetry.SpanContextPropagationExtractor;
import org.apache.camel.telemetry.SpanContextPropagationInjector;
import org.apache.camel.telemetry.SpanLifecycleManager;
import org.apache.camel.telemetry.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JdkService("telemetry-dev-tracer")
@Configurer
@ManagedResource(description = "TelemetryDevTracer")
public class TelemetryDevTracer extends Tracer {

    private static final Logger LOG = LoggerFactory.getLogger(TelemetryDevTracer.class);

    private final Logger LOGTRACE = LoggerFactory.getLogger("LOG_TRACE");
    private String traceFormat;

    @ManagedAttribute(description = "The format of traces (default, tree, json)")
    public String getTraceFormat() {
        return traceFormat;
    }

    public void setTraceFormat(String traceFormat) {
        this.traceFormat = traceFormat;
    }

    @Override
    protected void initTracer() {
        this.setSpanLifecycleManager(new DevSpanLifecycleManager(traceFormat));
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.warn(
                "TelemetryDevTracer enabled. This is a development tracer and you should avoid using it in production workflows.");
    }

    private class DevSpanLifecycleManager implements SpanLifecycleManager {

        private final InMemoryCollector inMemoryCollector = new InMemoryCollector();
        private DevTraceFormat stf;

        private DevSpanLifecycleManager(String traceFormat) {
            if (traceFormat == null) {
                traceFormat = "default";
            }
            switch (traceFormat.toLowerCase()) {
                case "tree":
                    this.stf = new DevTraceFormatTree();
                    break;
                case "json":
                    this.stf = new DevTraceFormatJson();
                    break;
                case "default":
                    this.stf = new DevTraceFormatDefault();
                    break;
                default:
                    LOG.warn("Unknown {} trace format. Fallback to default.", traceFormat);
                    break;
            }
        }

        @Override
        public Span create(String spanName, Span parent, SpanContextPropagationExtractor extractor) {
            Span span = DevSpanAdapter.buildSpan(spanName);
            String traceId = UUID.randomUUID().toString().replaceAll("-", "");
            if (parent != null) {
                traceId = spanTraceId(parent);
                span.setTag("parentSpan", spanSpanId(parent));
            } else {
                String upstreamTraceParent = (String) extractor.get("traceparent");
                if (upstreamTraceParent != null) {
                    String[] split = upstreamTraceParent.toString().split("-");
                    if (split.length != 2) {
                        LOG.error("TRACE ERROR: wrong format, could not split traceparent {}", upstreamTraceParent);
                        span.setTag("traceid", traceId);
                        span.setTag("spanid", UUID.randomUUID().toString().replaceAll("-", ""));
                        return span;
                    }
                    traceId = split[0];
                    String parentSpanId = split[1];
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
            DevSpanAdapter ssa = (DevSpanAdapter) span;
            inMemoryCollector.push(ssa.getTag("traceid"), ssa);
        }

        @Override
        public void close(Span span) {
            span.setTag("isDone", "true");
            DevSpanAdapter ssa = (DevSpanAdapter) span;
            DevTrace trace = inMemoryCollector.get(ssa.getTag("traceid"));
            if (trace != null) {
                LOGTRACE.info("{}", stf.format(trace));
            }
        }

        @Override
        public void deactivate(Span span) {
            span.setTag("endTimestamp", "" + System.nanoTime());
        }

        @Override
        public void inject(Span span, SpanContextPropagationInjector injector) {
            String[] split = span.toString().split("-");
            if (split.length < 2) {
                LOG.error("TRACE ERROR: wrong format, could not split traceparent {}", span);
                return;
            }
            injector.put("traceparent", split[0] + "-" + split[1]);
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
