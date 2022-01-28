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
package org.apache.camel.opentracing;

import java.util.Set;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import org.apache.camel.Exchange;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.tracing.InjectAdapter;
import org.apache.camel.tracing.SpanAdapter;
import org.apache.camel.tracing.SpanDecorator;
import org.apache.camel.tracing.SpanKind;
import org.apache.camel.tracing.decorators.AbstractInternalSpanDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To use OpenTracing with Camel then setup this {@link OpenTracingTracer} in your Camel application.
 * <p/>
 * This class is implemented as both an {@link org.apache.camel.spi.EventNotifier} and {@link RoutePolicy} that allows
 * to trap when Camel starts/ends an {@link Exchange} being routed using the {@link RoutePolicy} and during the routing
 * if the {@link Exchange} sends messages, then we track them using the {@link org.apache.camel.spi.EventNotifier}.
 */
@ManagedResource(description = "OpenTracingTracer")
@Deprecated
public class OpenTracingTracer extends org.apache.camel.tracing.Tracer {

    private static final Logger LOG = LoggerFactory.getLogger(OpenTracingTracer.class);

    Tracer tracer;

    public OpenTracingTracer() {
    }

    private String mapToSpanKind(SpanKind kind) {
        switch (kind) {
            case SPAN_KIND_CLIENT:
                return Tags.SPAN_KIND_CLIENT;
            case SPAN_KIND_SERVER:
                return Tags.SPAN_KIND_SERVER;
            case CONSUMER:
                return Tags.SPAN_KIND_CONSUMER;
            case PRODUCER:
                return Tags.SPAN_KIND_PRODUCER;
            default:
                return null;
        }
    }

    protected void initTracer() {
        if (tracer == null) {
            Set<Tracer> tracers = getCamelContext().getRegistry().findByType(Tracer.class);
            if (tracers.size() == 1) {
                tracer = tracers.iterator().next();
            }
        }

        if (tracer == null) {
            tracer = TracerResolver.resolveTracer();
        }

        if (tracer == null) {
            // No tracer is available, so setup NoopTracer
            tracer = NoopTracerFactory.create();
        }

        if (tracer != null) {
            try { // Take care NOT to import GlobalTracer as it is an optional dependency and may not be on the classpath.
                io.opentracing.util.GlobalTracer.registerIfAbsent(tracer);
            } catch (NoClassDefFoundError globalTracerNotInClasspath) {
                LOG.trace("GlobalTracer is not found on the classpath.");
            }
        }
    }

    @Override
    protected SpanAdapter startSendingEventSpan(String operationName, SpanKind kind, SpanAdapter parent) {
        SpanBuilder spanBuilder = tracer.buildSpan(operationName).withTag(Tags.SPAN_KIND.getKey(), mapToSpanKind(kind));
        if (parent != null) {
            io.opentracing.Span parentSpan = ((OpenTracingSpanAdapter) parent).getOpenTracingSpan();
            spanBuilder.asChildOf(parentSpan);
        }
        return new OpenTracingSpanAdapter(spanBuilder.start());
    }

    @Override
    protected SpanAdapter startExchangeBeginSpan(
            Exchange exchange, SpanDecorator sd, String operationName, SpanKind kind, SpanAdapter parent) {
        SpanBuilder builder = tracer.buildSpan(operationName);
        if (parent != null) {
            // we found a Span already associated with this exchange, use it as parent
            Span parentFromExchange = ((OpenTracingSpanAdapter) parent).getOpenTracingSpan();
            builder.asChildOf(parentFromExchange);
        } else {
            SpanContext parentFromHeaders = tracer.extract(Format.Builtin.TEXT_MAP,
                    new OpenTracingExtractAdapter(sd.getExtractAdapter(exchange.getIn().getHeaders(), encoding)));

            if (parentFromHeaders != null) {
                // this means it's an inter-process request or the context was manually injected into the headers
                // we add the server tag
                builder.asChildOf(parentFromHeaders).withTag(Tags.SPAN_KIND.getKey(), mapToSpanKind(sd.getReceiverSpanKind()));
            } else if (!(sd instanceof AbstractInternalSpanDecorator)) {
                // no parent found anywhere and it's not an internal endpoint
                // we add the server tag
                builder.withTag(Tags.SPAN_KIND.getKey(), mapToSpanKind(sd.getReceiverSpanKind()));
            }
        }

        return new OpenTracingSpanAdapter(builder.start());
    }

    public Tracer getTracer() {
        return tracer;
    }

    public void setTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    protected void finishSpan(SpanAdapter span) {
        OpenTracingSpanAdapter openTracingSpanWrapper = (OpenTracingSpanAdapter) span;
        openTracingSpanWrapper.getOpenTracingSpan().finish();
    }

    protected void inject(SpanAdapter span, InjectAdapter adapter) {
        OpenTracingSpanAdapter openTracingSpanWrapper = (OpenTracingSpanAdapter) span;
        tracer.inject(openTracingSpanWrapper.getOpenTracingSpan().context(), Format.Builtin.TEXT_MAP,
                new OpenTracingInjectAdapter(adapter));
    }
}
