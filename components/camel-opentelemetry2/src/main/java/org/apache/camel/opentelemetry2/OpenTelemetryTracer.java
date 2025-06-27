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

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.apache.camel.RuntimeCamelException;
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

@JdkService("opentelemetry-tracer-2")
@Configurer
@ManagedResource(description = "OpenTelemetry2")
public class OpenTelemetryTracer extends org.apache.camel.telemetry.Tracer {

    private static final Logger LOG = LoggerFactory.getLogger(OpenTelemetryTracer.class);

    private Tracer tracer;
    private ContextPropagators contextPropagators;

    @Override
    protected void initTracer() {
        if (tracer == null) {
            this.tracer = CamelContextHelper.findSingleByType(getCamelContext(), Tracer.class);
        }
        if (tracer == null) {
            this.tracer = GlobalOpenTelemetry.get().getTracer("camel");
        }
        if (tracer == null) {
            throw new RuntimeCamelException("Could not find any Opentelemetry tracer!");
        }

        if (contextPropagators == null) {
            contextPropagators = CamelContextHelper.findSingleByType(
                    getCamelContext(), ContextPropagators.class);
        }
        if (contextPropagators == null) {
            contextPropagators = GlobalOpenTelemetry.get().getPropagators();
        }
        if (contextPropagators == null) {
            throw new RuntimeCamelException("Could not find any Opentelemetry context propagator!");
        }

        this.setSpanLifecycleManager(new OpentelemetrySpanLifecycleManager(tracer, contextPropagators));
    }

    void setTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    void setContextPropagators(ContextPropagators cp) {
        this.contextPropagators = cp;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.info("Opentelemetry2 enabled");
    }

    private class OpentelemetrySpanLifecycleManager implements SpanLifecycleManager {

        private final Tracer tracer;
        private final ContextPropagators contextPropagators;

        private OpentelemetrySpanLifecycleManager(Tracer tracer, ContextPropagators contextPropagators) {
            this.tracer = tracer;
            this.contextPropagators = contextPropagators;
        }

        @Override
        public Span create(String spanName, Span parent, SpanContextPropagationExtractor extractor) {
            SpanBuilder builder = tracer.spanBuilder(spanName);
            Baggage baggage = null;

            if (parent != null) {
                OpenTelemetrySpanAdapter otelParentSpan = (OpenTelemetrySpanAdapter) parent;
                builder = builder.setParent(Context.current().with(otelParentSpan.getSpan()));
                baggage = otelParentSpan.getBaggage();
            } else {
                /*
                 * This part is a bit tricky in Opentelemetry. We need to verify if the extractor
                 * (ie, the Camel Exchange) holds a propagated parent. If it doesn't, then, we must use a null Context.
                 */
                Context current = null;
                if (extractor.get("traceparent") != null) {
                    current = Context.current();
                }
                // Try to get parent from context propagation (upstream traces)
                Context ctx = contextPropagators.getTextMapPropagator().extract(current, extractor,
                        new TextMapGetter<SpanContextPropagationExtractor>() {
                            @Override
                            public Iterable<String> keys(SpanContextPropagationExtractor carrier) {
                                return carrier.keys();
                            }

                            @Override
                            public String get(SpanContextPropagationExtractor carrier, String key) {
                                if (carrier.get(key) == null) {
                                    return null;
                                }
                                return carrier.get(key).toString();
                            }
                        });
                builder = builder.setParent(ctx);
            }

            return new OpenTelemetrySpanAdapter(builder.startSpan(), baggage);
        }

        @Override
        public void activate(Span span) {
            OpenTelemetrySpanAdapter otelSpan = (OpenTelemetrySpanAdapter) span;
            otelSpan.makeCurrent();
        }

        @Override
        public void deactivate(Span span) {
            OpenTelemetrySpanAdapter otelSpan = (OpenTelemetrySpanAdapter) span;
            otelSpan.end();
        }

        @Override
        public void close(Span span) {
            OpenTelemetrySpanAdapter otelSpan = (OpenTelemetrySpanAdapter) span;
            otelSpan.close();
        }

        @Override
        public void inject(Span span, SpanContextPropagationInjector injector) {
            OpenTelemetrySpanAdapter otelSpan = (OpenTelemetrySpanAdapter) span;
            Context ctx = Context.current().with(otelSpan.getSpan());
            if (otelSpan.getBaggage() != null) {
                ctx = ctx.with(otelSpan.getBaggage());
            }
            contextPropagators.getTextMapPropagator().inject(ctx, injector,
                    (carrier, key, value) -> carrier.put(key, value));
        }

    }

}
