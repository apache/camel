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
package org.apache.camel.observation;

import java.util.Set;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.observation.transport.RequestReplyReceiverContext;
import io.micrometer.observation.transport.RequestReplySenderContext;
import io.micrometer.observation.transport.SenderContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.TracingObservationHandler;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.tracing.ExtractAdapter;
import org.apache.camel.tracing.InjectAdapter;
import org.apache.camel.tracing.SpanAdapter;
import org.apache.camel.tracing.SpanDecorator;
import org.apache.camel.tracing.SpanKind;
import org.apache.camel.tracing.decorators.AbstractInternalSpanDecorator;

@ManagedResource(description = "MicrometerObservationTracer")
public class MicrometerObservationTracer extends org.apache.camel.tracing.Tracer {

    static final String SPAN_DECORATOR_INTERNAL = "camel.micrometer.abstract-internal";

    private static final String CAMEL_CONTEXT_NAME = "camel.component";

    private Tracer tracer = Tracer.NOOP;

    private ObservationRegistry observationRegistry;

    public ObservationRegistry getObservationRegistry() {
        return observationRegistry;
    }

    public void setObservationRegistry(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    public Tracer getTracer() {
        return tracer;
    }

    public void setTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    private Observation.Context spanKindToContextOnExtract(
            org.apache.camel.tracing.SpanKind kind, SpanDecorator sd, Exchange exchange) {
        ExtractAdapter adapter = sd.getExtractAdapter(exchange.getIn().getHeaders(), encoding);
        switch (kind) {
            case PRODUCER:
                throw new UnsupportedOperationException("You can't extract when sending a message");
            case SPAN_KIND_SERVER:
                RequestReplyReceiverContext<Object, Message> replyReceiverContext
                        = new RequestReplyReceiverContext<>((carrier, key) -> String.valueOf(adapter.get(key)));
                replyReceiverContext.setResponse(exchange.getMessage());
                replyReceiverContext.setCarrier(exchange.getIn());
                return replyReceiverContext;
            case CONSUMER:
            case SPAN_KIND_CLIENT:
                ReceiverContext<Message> receiverContext
                        = new ReceiverContext<>((carrier, key) -> String.valueOf(adapter.get(key)));
                receiverContext.setCarrier(exchange.getIn());
                return receiverContext;
            default:
                return new Observation.Context();
        }
    }

    private Observation.Context spanKindToContextOnInject(
            org.apache.camel.tracing.SpanKind kind, InjectAdapter adapter, Exchange exchange) {
        switch (kind) {
            case SPAN_KIND_CLIENT:
                RequestReplySenderContext<Object, Message> senderContext
                        = new RequestReplySenderContext<>((carrier, key, value) -> adapter.put(key, value));
                senderContext.setResponse(exchange.getMessage());
                senderContext.setCarrier(exchange.getIn());
                return senderContext;
            case PRODUCER:
                SenderContext<Message> context = new SenderContext<>((carrier, key, value) -> adapter.put(key, value));
                context.setCarrier(exchange.getIn());
                return context;
            case SPAN_KIND_SERVER:
            case CONSUMER:
                throw new UnsupportedOperationException("You can't inject when receiving a message");
            default:
                return new Observation.Context();
        }
    }

    @Override
    protected void initTracer() {
        if (observationRegistry == null) {
            Set<ObservationRegistry> registries = getCamelContext().getRegistry().findByType(ObservationRegistry.class);
            if (registries.size() == 1) {
                observationRegistry = registries.iterator().next();
            }
        }

        if (tracer == null) {
            Set<Tracer> tracers = getCamelContext().getRegistry().findByType(Tracer.class);
            if (tracers.size() == 1) {
                tracer = tracers.iterator().next();
            }
        }

        if (observationRegistry == null) {
            // No Observation Registry is available, so setup Noop
            observationRegistry = ObservationRegistry.NOOP;
        }
    }

    @Override
    protected SpanAdapter startSendingEventSpan(
            String operationName, SpanKind kind, SpanAdapter parent, Exchange exchange,
            InjectAdapter injectAdapter) {
        Observation.Context context = spanKindToContextOnInject(kind, injectAdapter, exchange);
        Observation observation = Observation.createNotStarted(CAMEL_CONTEXT_NAME, () -> context, observationRegistry);
        observation.contextualName(operationName);
        Observation parentObservation = getParentObservation(parent);
        Tracer.SpanInScope scope = null;
        try {
            if (parentObservation != null && parentObservation != observationRegistry.getCurrentObservation()) {
                // Because Camel allows to close scopes multiple times
                TracingObservationHandler.TracingContext tracingContext
                        = parentObservation.getContextView().get(TracingObservationHandler.TracingContext.class);
                Span parentSpan = tracingContext.getSpan();
                scope = tracer.withSpan(parentSpan);
            }
            if (parentObservation != null) {
                observation.parentObservation(parentObservation);
            }
            return new MicrometerObservationSpanAdapter(observation.start(), tracer);
        } finally {
            if (scope != null) {
                scope.close();
            }
        }
    }

    @Override
    protected void initContextPropagators() {

    }

    private static Observation getParentObservation(SpanAdapter parentObservation) {
        if (parentObservation == null) {
            return null;
        }
        MicrometerObservationSpanAdapter observationWrapper = (MicrometerObservationSpanAdapter) parentObservation;
        return observationWrapper.getMicrometerObservation();
    }

    @Override
    protected SpanAdapter startExchangeBeginSpan(
            Exchange exchange, SpanDecorator sd, String operationName, org.apache.camel.tracing.SpanKind kind,
            SpanAdapter parent) {
        boolean parentPresent = parent != null;
        Observation.Context context = spanKindToContextOnExtract(kind, sd, exchange);
        boolean internalSpanDecorator = sd instanceof AbstractInternalSpanDecorator;
        context.put(SPAN_DECORATOR_INTERNAL, internalSpanDecorator);
        Observation observation = Observation.createNotStarted(operationName, () -> context, observationRegistry);
        if (parentPresent) {
            observation.parentObservation(getParentObservation(parent));
        }
        return new MicrometerObservationSpanAdapter(observation.start(), tracer);
    }

    @Override
    protected void finishSpan(SpanAdapter span) {
        MicrometerObservationSpanAdapter observationSpanAdapter = (MicrometerObservationSpanAdapter) span;
        observationSpanAdapter.getMicrometerObservation().stop();
    }

    @Override
    protected void inject(SpanAdapter span, InjectAdapter adapter) {
        // Inject happens on start of an observation
    }

}
