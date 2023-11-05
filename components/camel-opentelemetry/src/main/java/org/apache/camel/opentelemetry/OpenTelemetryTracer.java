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

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import org.apache.camel.Exchange;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.opentelemetry.propagators.OpenTelemetryGetter;
import org.apache.camel.opentelemetry.propagators.OpenTelemetrySetter;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.tracing.ExtractAdapter;
import org.apache.camel.tracing.InjectAdapter;
import org.apache.camel.tracing.SpanAdapter;
import org.apache.camel.tracing.SpanDecorator;
import org.apache.camel.tracing.decorators.AbstractInternalSpanDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JdkService("opentelemetry-tracer")
@Configurer
@ManagedResource(description = "OpenTelemetryTracer")
public class OpenTelemetryTracer extends org.apache.camel.tracing.Tracer {

    private static final Logger LOG = LoggerFactory.getLogger(OpenTelemetryTracer.class);

    private Tracer tracer;
    private String instrumentationName = "camel";
    private ContextPropagators contextPropagators;

    public Tracer getTracer() {
        return tracer;
    }

    public void setTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    @ManagedAttribute
    public String getInstrumentationName() {
        return instrumentationName;
    }

    /**
     * A name uniquely identifying the instrumentation scope, such as the instrumentation library, package, or fully
     * qualified class name. Must not be null.
     */
    public void setInstrumentationName(String instrumentationName) {
        this.instrumentationName = instrumentationName;
    }

    public ContextPropagators getContextPropagators() {
        return contextPropagators;
    }

    public void setContextPropagators(ContextPropagators contextPropagators) {
        this.contextPropagators = contextPropagators;
    }

    private SpanKind mapToSpanKind(org.apache.camel.tracing.SpanKind kind) {
        switch (kind) {
            case SPAN_KIND_CLIENT:
                return SpanKind.CLIENT;
            case SPAN_KIND_SERVER:
                return SpanKind.SERVER;
            case CONSUMER:
                return SpanKind.CONSUMER;
            case PRODUCER:
                return SpanKind.PRODUCER;
            default:
                return SpanKind.SERVER;
        }
    }

    @Override
    protected void initTracer() {
        if (tracer == null) {
            tracer = CamelContextHelper.findSingleByType(getCamelContext(), Tracer.class);
        }
        if (tracer == null) {
            // GlobalOpenTelemetry.get() is always NotNull, falls back to OpenTelemetry.noop()
            tracer = GlobalOpenTelemetry.get().getTracer(instrumentationName);
        }
    }

    @Override
    protected void initContextPropagators() {
        if (contextPropagators == null) {
            contextPropagators = CamelContextHelper.findSingleByType(getCamelContext(), ContextPropagators.class);
        }
        if (contextPropagators == null) {
            // GlobalOpenTelemetry.get() is always NotNull, falls back to OpenTelemetry.noop()
            contextPropagators = GlobalOpenTelemetry.get().getPropagators();
        }
    }

    @Override
    protected SpanAdapter startSendingEventSpan(
            String operationName, org.apache.camel.tracing.SpanKind kind, SpanAdapter parent, Exchange exchange,
            InjectAdapter injectAdapter) {
        Baggage baggage = null;
        SpanBuilder builder = tracer.spanBuilder(operationName).setSpanKind(mapToSpanKind(kind));
        if (parent != null) {
            OpenTelemetrySpanAdapter oTelSpanWrapper = (OpenTelemetrySpanAdapter) parent;
            Span parentSpan = oTelSpanWrapper.getOpenTelemetrySpan();
            baggage = oTelSpanWrapper.getBaggage();
            builder = builder.setParent(Context.current().with(parentSpan));
        }
        return new OpenTelemetrySpanAdapter(builder.startSpan(), baggage);
    }

    @Override
    protected SpanAdapter startExchangeBeginSpan(
            Exchange exchange, SpanDecorator sd, String operationName, org.apache.camel.tracing.SpanKind kind,
            SpanAdapter parent) {
        SpanBuilder builder = tracer.spanBuilder(operationName);
        Baggage baggage;
        if (parent != null) {
            OpenTelemetrySpanAdapter spanFromExchange = (OpenTelemetrySpanAdapter) parent;
            builder = builder.setParent(Context.current().with(spanFromExchange.getOpenTelemetrySpan()));
            baggage = spanFromExchange.getBaggage();
        } else {
            ExtractAdapter adapter = sd.getExtractAdapter(exchange.getIn().getHeaders(), encoding);
            Context ctx = GlobalOpenTelemetry.get().getPropagators().getTextMapPropagator().extract(Context.current(), adapter,
                    new OpenTelemetryGetter(adapter));
            Span span = Span.fromContext(ctx);
            baggage = Baggage.fromContext(ctx);
            if (span != null && span.getSpanContext().isValid()) {
                builder.setParent(ctx).setSpanKind(mapToSpanKind(sd.getReceiverSpanKind()));
            } else if (!(sd instanceof AbstractInternalSpanDecorator)) {
                builder.setSpanKind(mapToSpanKind(sd.getReceiverSpanKind()));
            }
        }

        return new OpenTelemetrySpanAdapter(builder.startSpan(), baggage);
    }

    @Override
    protected void finishSpan(SpanAdapter span) {
        OpenTelemetrySpanAdapter openTracingSpanWrapper = (OpenTelemetrySpanAdapter) span;
        openTracingSpanWrapper.getOpenTelemetrySpan().end();
    }

    @Override
    protected void inject(SpanAdapter span, InjectAdapter adapter) {
        OpenTelemetrySpanAdapter spanFromExchange = (OpenTelemetrySpanAdapter) span;
        Span otelSpan = spanFromExchange.getOpenTelemetrySpan();
        Context ctx;
        if (spanFromExchange.getBaggage() != null) {
            ctx = Context.current().with(otelSpan).with(spanFromExchange.getBaggage());
        } else {
            ctx = Context.current().with(otelSpan);
        }
        GlobalOpenTelemetry.get().getPropagators().getTextMapPropagator().inject(ctx, adapter, new OpenTelemetrySetter());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.info("OpenTelemetryTracer enabled using instrumentation-name: {}", instrumentationName);
    }
}
