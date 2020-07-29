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
package org.apache.camel.opentelemetry;

import java.util.Set;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.trace.DefaultTracer;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.TracingContextUtils;
import org.apache.camel.Exchange;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.opentelemetry.propagators.OpenTelemetryGetter;
import org.apache.camel.opentelemetry.propagators.OpenTelemetrySetter;
import org.apache.camel.tracing.InjectAdapter;
import org.apache.camel.tracing.SpanAdapter;
import org.apache.camel.tracing.SpanDecorator;
import org.apache.camel.tracing.SpanKind;
import org.apache.camel.tracing.decorators.AbstractInternalSpanDecorator;

@ManagedResource(description = "OpenTelemetryTracer")
public class OpenTelemetryTracer extends org.apache.camel.tracing.Tracer {

    private Tracer tracer;

    public Tracer getTracer() {
        return tracer;
    }

    public void setTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    private Span.Kind mapToSpanKind(SpanKind kind) {
        switch (kind) {
            case SPAN_KIND_CLIENT: return Span.Kind.CLIENT;
            case SPAN_KIND_SERVER: return Span.Kind.SERVER;
            case CONSUMER: return Span.Kind.CONSUMER;
            case PRODUCER: return Span.Kind.PRODUCER;
            default: return Span.Kind.SERVER;
        }
    }

    @Override
    protected void initTracer() {
        if (tracer == null) {
            Set<Tracer> tracers = getCamelContext().getRegistry().findByType(Tracer.class);
            if (tracers.size() == 1) {
                tracer = tracers.iterator().next();
            }
        }

        if (tracer == null) {
            // No tracer is available, so setup NoopTracer
            tracer = DefaultTracer.getInstance();
        }
    }

    @Override
    protected SpanAdapter startSendingEventSpan(String operationName, SpanKind kind, SpanAdapter parent) {
        Span.Builder builder = tracer.spanBuilder(operationName).setSpanKind(mapToSpanKind(kind));
        if (parent != null) {
            OpenTelemetrySpanAdapter oTelSpanWrapper = (OpenTelemetrySpanAdapter) parent;
            Span parentSpan = oTelSpanWrapper.getOpenTelemetrySpan();
            builder = builder.setParent(parentSpan);
        }
        return new OpenTelemetrySpanAdapter(builder.startSpan());
    }

    @Override
    protected SpanAdapter startExchangeBeginSpan(Exchange exchange, SpanDecorator sd, String operationName, SpanKind kind, SpanAdapter parent) {
        Span.Builder builder = tracer.spanBuilder(operationName);
        if (parent != null) {
            OpenTelemetrySpanAdapter spanFromExchange = (OpenTelemetrySpanAdapter) parent;
            builder = builder.setParent(spanFromExchange.getOpenTelemetrySpan());
        } else {
            Context ctx = OpenTelemetry.getPropagators().getHttpTextFormat().extract(Context.current(), sd.getExtractAdapter(exchange.getIn().getHeaders(), encoding), new OpenTelemetryGetter());
            Span span = TracingContextUtils.getSpan(ctx);
            SpanContext parentFromHeaders = span.getContext();

            if (parentFromHeaders != null && parentFromHeaders.isValid()) {
                builder.setParent(parentFromHeaders).setSpanKind(mapToSpanKind(sd.getReceiverSpanKind()));
            } else if (!(sd instanceof AbstractInternalSpanDecorator)) {
                builder.setSpanKind(mapToSpanKind(sd.getReceiverSpanKind()));
            }
        }

        return new OpenTelemetrySpanAdapter(builder.startSpan());
    }

    @Override
    protected void finishSpan(SpanAdapter span) {
        OpenTelemetrySpanAdapter openTracingSpanWrapper = (OpenTelemetrySpanAdapter) span;
        openTracingSpanWrapper.getOpenTelemetrySpan().end();
    }

    @Override
    protected void inject(SpanAdapter span, InjectAdapter adapter) {
        OpenTelemetry.getPropagators().getHttpTextFormat().inject(Context.current(), adapter, new OpenTelemetrySetter());
    }

}
