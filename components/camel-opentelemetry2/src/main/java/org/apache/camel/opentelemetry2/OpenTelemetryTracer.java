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

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.InterceptStrategy;
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
    private OpenTelemetrySdk devSdk;
    private String exportTarget;

    @Override
    protected void initTracer() {
        if (tracer == null) {
            this.tracer = CamelContextHelper.findSingleByType(getCamelContext(), Tracer.class);
        }
        if (tracer == null) {
            String profile = getCamelContext().getCamelContextExtension().getProfile();
            if ("dev".equals(profile)) {
                initDevSpanExporter();
            }
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

        InterceptStrategy interceptStrategy = new TraceProcessorsOtelInterceptStrategy();
        getCamelContext().getCamelContextExtension().addInterceptStrategy(interceptStrategy);
    }

    private void initDevSpanExporter() {
        if (isOpenTelemetryAgentPresent()) {
            if ("jaeger".equals(exportTarget)) {
                LOG.info("OpenTelemetry Java Agent detected, exporting traces to Jaeger");
                return;
            }
            LOG.info("OpenTelemetry Java Agent detected, using embedded OTLP receiver");
            initOtlpReceiver();
            return;
        }
        DevSpanExporter exporter = new DevSpanExporter();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        devSdk = OpenTelemetrySdk.builder()
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .setTracerProvider(tracerProvider)
                .build();
        GlobalOpenTelemetry.set(devSdk);
        this.tracer = devSdk.getTracer("camel");
        this.contextPropagators = devSdk.getPropagators();
        getCamelContext().getRegistry().bind("DevSpanExporter", exporter);
        LOG.info("OpenTelemetry in-memory span exporter enabled (dev profile)");
    }

    private void initOtlpReceiver() {
        DevSpanExporter exporter = new DevSpanExporter();
        getCamelContext().getRegistry().bind("DevSpanExporter", exporter);

        // exclude the receiver route from tracing to avoid self-tracing loop
        String ep = getExcludePatterns();
        setExcludePatterns(ep != null ? ep + ",platform-http:/v1/traces*" : "platform-http:/v1/traces*");

        try {
            getCamelContext().addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/v1/traces?httpMethodRestrict=POST")
                            .routeId("otlp-receiver")
                            .process(new OtlpReceiverProcessor(exporter));
                }
            });
            LOG.info("Embedded OTLP receiver enabled on /v1/traces for Java Agent span collection");
        } catch (Exception e) {
            LOG.warn("Failed to start embedded OTLP receiver: {}", e.getMessage());
        }
    }

    private boolean isOpenTelemetryAgentPresent() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .anyMatch(arg -> arg.startsWith("-javaagent") && arg.contains("opentelemetry"));
    }

    void setTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    void setContextPropagators(ContextPropagators cp) {
        this.contextPropagators = cp;
    }

    public String getExportTarget() {
        return exportTarget;
    }

    public void setExportTarget(String exportTarget) {
        this.exportTarget = exportTarget;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.info("Opentelemetry2 enabled");
    }

    @Override
    protected void doShutdown() {
        super.doShutdown();
        if (devSdk != null) {
            devSdk.close();
            devSdk = null;
        }
    }

    private class OpentelemetrySpanLifecycleManager implements SpanLifecycleManager {

        private final static String BAGGAGE_VAR_PREFIX = "OTEL_BAGGAGE_";
        /**
         * Exchange property name for storing a span context to create span links. This is used by components like
         * vertx-websocket to link WebSocket message spans back to the original HTTP upgrade request span.
         */
        private final static String SPAN_LINK_CONTEXT_PROPERTY = "CamelVertxWebsocketHandshakeSpanContext";

        private final Tracer tracer;
        private final ContextPropagators contextPropagators;

        private OpentelemetrySpanLifecycleManager(Tracer tracer, ContextPropagators contextPropagators) {
            this.tracer = tracer;
            this.contextPropagators = contextPropagators;
        }

        @Override
        public Span create(String spanName, String spanKind, Span parent, SpanContextPropagationExtractor extractor) {
            SpanBuilder builder = tracer.spanBuilder(spanName);
            Baggage baggage = Baggage.current();

            // Extract span link contexts (can be multiple for scenarios like sendToAll)
            List<SpanContext> linkContexts = extractSpanLinkContexts(extractor);
            for (SpanContext linkContext : linkContexts) {
                if (linkContext != null && linkContext.isValid()) {
                    builder = builder.addLink(linkContext);
                }
            }

            if (parent != null) {
                OpenTelemetrySpanAdapter otelParentSpan = (OpenTelemetrySpanAdapter) parent;
                builder = builder.setParent(Context.current().with(otelParentSpan.getSpan()));
                if (baggage.isEmpty()) {
                    baggage = otelParentSpan.getBaggage();
                }
            } else {
                Context current = Context.current();
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
                baggage = Baggage.fromContext(ctx);
            }
            baggage = getBaggageFromHeaders(baggage, extractor);

            if (spanKind != null) {
                builder.setSpanKind(SpanKind.valueOf(spanKind));
            }

            return new OpenTelemetrySpanAdapter(builder.startSpan(), baggage);
        }

        /**
         * Extracts span contexts from the exchange properties to create span links. This allows linking spans across
         * asynchronous boundaries, such as WebSocket messages back to the HTTP upgrade request.
         * <p>
         * Supports multiple span links for scenarios like broadcasting to multiple WebSocket connections from different
         * HTTP requests.
         *
         * @param  extractor the span context propagation extractor (usually the Exchange)
         * @return           list of span contexts to link to (empty if none present)
         */
        private List<SpanContext> extractSpanLinkContexts(SpanContextPropagationExtractor extractor) {
            List<SpanContext> result = new ArrayList<>();
            if (extractor == null) {
                return result;
            }

            Object value = extractor.get(SPAN_LINK_CONTEXT_PROPERTY);

            if (value instanceof SpanContext) {
                result.add((SpanContext) value);
            } else if (value instanceof String) {
                try {
                    String str = (String) value;
                    // Format: "traceId1:spanId1,traceId2:spanId2,..."
                    // Split by comma to support multiple span links
                    for (String part : str.split(",")) {
                        String[] components = part.trim().split(":");
                        if (components.length == 2) {
                            SpanContext ctx = SpanContext.createFromRemoteParent(
                                    components[0], components[1],
                                    io.opentelemetry.api.trace.TraceFlags.getSampled(),
                                    io.opentelemetry.api.trace.TraceState.getDefault());
                            result.add(ctx);
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to parse span link contexts from string: {}", value, e);
                }
            }

            return result;
        }

        // We inspect the exchange in order to find any baggage variable
        private Baggage getBaggageFromHeaders(Baggage baggage, SpanContextPropagationExtractor extractor) {
            Iterator<Map.Entry<String, Object>> it = extractor.iterator();

            while (it.hasNext()) {
                Map.Entry<String, Object> entry = it.next();
                String key = getBaggageVar(entry.getKey());
                if (key != null) {
                    baggage = baggage.toBuilder().put(key, entry.getValue().toString()).build();
                }
            }

            return baggage;
        }

        private String getBaggageVar(String key) {
            if (key == null || !key.startsWith(BAGGAGE_VAR_PREFIX)) {
                return null;
            }

            return key.substring(BAGGAGE_VAR_PREFIX.length());
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
        public void inject(Span span, SpanContextPropagationInjector injector, boolean includeTracing) {
            OpenTelemetrySpanAdapter otelSpan = (OpenTelemetrySpanAdapter) span;
            Context ctx = Context.current().with(otelSpan.getSpan());
            if (otelSpan.getBaggage() != null) {
                ctx = ctx.with(otelSpan.getBaggage());
            }
            contextPropagators.getTextMapPropagator().inject(ctx, injector,
                    (carrier, key, value) -> carrier.put(key, value));
            if (includeTracing) {
                injector.put(org.apache.camel.telemetry.Tracer.TRACE_HEADER, otelSpan.getSpan().getSpanContext().getTraceId());
                injector.put(org.apache.camel.telemetry.Tracer.SPAN_HEADER, otelSpan.getSpan().getSpanContext().getSpanId());
            }
        }

    }

}
