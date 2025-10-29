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
package org.apache.camel.micrometer.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span.Builder;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.telemetry.Span;
import org.apache.camel.telemetry.SpanContextPropagationExtractor;
import org.apache.camel.telemetry.SpanContextPropagationInjector;
import org.apache.camel.telemetry.SpanLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JdkService("micrometer-observability-tracer")
@Configurer
@ManagedResource(description = "MicrometerObservabilityTracer")
public class MicrometerObservabilityTracer extends org.apache.camel.telemetry.Tracer {

    private static final Logger LOG = LoggerFactory.getLogger(MicrometerObservabilityTracer.class);

    private Tracer tracer;
    private ObservationRegistry observationRegistry;
    private Propagator propagator;

    public Tracer getTracer() {
        return tracer;
    }

    public void setTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    public ObservationRegistry getObservationRegistry() {
        return observationRegistry;
    }

    public void setObservationRegistry(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    public Propagator getPropagator() {
        return propagator;
    }

    public void setPropagator(Propagator propagator) {
        this.propagator = propagator;
    }

    @Override
    protected void initTracer() {
        if (tracer == null) {
            tracer = CamelContextHelper.findSingleByType(getCamelContext(), Tracer.class);
        }
        if (tracer == null) {
            tracer = new SimpleTracer();
            LOG.warn("No tracer was provided. A default inmemory tracer is used. " +
                     "This can be useful for development only, avoid this in a production environment.");
        }
        if (observationRegistry == null) {
            observationRegistry = CamelContextHelper.findSingleByType(getCamelContext(), ObservationRegistry.class);
        }
        if (observationRegistry == null) {
            MeterRegistry meterRegistry = new SimpleMeterRegistry();
            this.observationRegistry = ObservationRegistry.create();
            this.observationRegistry.observationConfig().observationHandler(
                    new DefaultMeterObservationHandler(meterRegistry));
            LOG.warn("No observation registry was provided. A default inmemory observation registry is used. " +
                     "This can be useful for development only, avoid this in a production environment.");
        }

        if (propagator == null) {
            propagator = CamelContextHelper.findSingleByType(getCamelContext(), Propagator.class);
        }
        if (propagator == null) {
            propagator = Propagator.NOOP;
            LOG.warn("No propagator was provided. A NOOP implementation is used, you won't be able to trace " +
                     "upstream activity. " +
                     "This can be useful for development only, avoid this in a production environment.");
        }

        this.setSpanLifecycleManager(new MicrometerObservabilitySpanLifecycleManager());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.info("Micrometer Observability enabled");
    }

    private class MicrometerObservabilitySpanLifecycleManager implements SpanLifecycleManager {

        private MicrometerObservabilitySpanLifecycleManager() {
            observationRegistry.observationConfig().observationHandler(
                    new ObservationHandler.FirstMatchingCompositeObservationHandler(
                            new PropagatingSenderTracingObservationHandler<>(tracer, propagator),
                            new PropagatingReceiverTracingObservationHandler<>(tracer, propagator),
                            new DefaultTracingObservationHandler(tracer)));
        }

        @Override
        public Span create(String spanName, Span parent, SpanContextPropagationExtractor extractor) {
            io.micrometer.tracing.Span span;
            if (parent != null) {
                MicrometerObservabilitySpanAdapter microObsParentSpan = (MicrometerObservabilitySpanAdapter) parent;
                span = tracer.nextSpan(microObsParentSpan.getSpan());
            } else if (extractor.get("traceparent") != null || extractor.get("X-B3-TraceId") != null) {
                /*
                 * This part is a bit tricky. We need to verify if the extractor
                 * (ie, the Camel Exchange) holds a propagated parent.
                 * As the micrometer-observability is technology agnostic, we need to check against
                 * the available implementations (Opentelemetry and Zipkin at the moment of writing this comment).
                 * TODO: we could do this configurable if it is required.
                 */
                Builder builder = propagator.extract(extractor, (carrier, key) -> {
                    return extractor.get(key) == null ? null : (String) extractor.get(key);
                });

                span = builder.start();
            } else {
                span = tracer.nextSpan();
            }

            span.name(spanName);

            return new MicrometerObservabilitySpanAdapter(span);
        }

        @Override
        public void activate(Span span) {
            MicrometerObservabilitySpanAdapter microObsSpan = (MicrometerObservabilitySpanAdapter) span;
            microObsSpan.activate();
        }

        @Override
        public void close(Span span) {
            MicrometerObservabilitySpanAdapter microObsSpan = (MicrometerObservabilitySpanAdapter) span;
            microObsSpan.close();
        }

        @Override
        public void deactivate(Span span) {
            MicrometerObservabilitySpanAdapter microObsSpan = (MicrometerObservabilitySpanAdapter) span;
            microObsSpan.deactivate();
        }

        @Override
        public void inject(Span span, SpanContextPropagationInjector injector, boolean includeTracing) {
            MicrometerObservabilitySpanAdapter microObsSpan = (MicrometerObservabilitySpanAdapter) span;
            propagator.inject(
                    microObsSpan.getSpan().context(),
                    injector,
                    (carrier, key, value) -> carrier.put(key, value));
            if (includeTracing) {
                injector.put(org.apache.camel.telemetry.Tracer.TRACE_HEADER, microObsSpan.getSpan().context().traceId());
                injector.put(org.apache.camel.telemetry.Tracer.SPAN_HEADER, microObsSpan.getSpan().context().spanId());
            }
        }
    }

}
