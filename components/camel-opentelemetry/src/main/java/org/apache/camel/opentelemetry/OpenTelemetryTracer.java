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

import java.util.*;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.ContextUtils;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Route;
import org.apache.camel.StaticService;
import org.apache.camel.opentelemetry.propagators.OpenTelemetryGetter;
import org.apache.camel.opentelemetry.propagators.OpenTelemetrySetter;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.LogListener;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.tracing.SpanDecorator;
import org.apache.camel.tracing.SpanKind;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author rvargasp
 */

public class OpenTelemetryTracer extends ServiceSupport
implements RoutePolicyFactory, StaticService, CamelContextAware {

    private static final Map<String, SpanDecorator> DECORATORS = new HashMap<>();
    private static final Logger LOG = LoggerFactory.getLogger(OpenTelemetryTracer.class);

    static {
        ServiceLoader.load(SpanDecorator.class).forEach(d -> {
            SpanDecorator existing = DECORATORS.get(d.getComponent());
            // Add span decorator if no existing decorator for the component,
            // or if derived from the existing decorator's class, allowing
            // custom decorators to be added if they extend the standard
            // decorators
            if (existing == null || existing.getClass().isInstance(d)) {
                DECORATORS.put(d.getComponent(), d);
            }
        });
    }

    private final OpenTelemetryEventNotifier eventNotifier = new OpenTelemetryEventNotifier();
    private Tracer tracer;
    private CamelContext camelContext;
    private boolean encoding;
    private Set<String> excludePatterns = new HashSet<>();

    protected SpanDecorator getSpanDecorator(Endpoint endpoint) {
        SpanDecorator sd = null;

        String uri = endpoint.getEndpointUri();
        String splitURI[] = StringHelper.splitOnCharacter(uri, ":", 2);
        if (splitURI[1] != null) {
            String scheme = splitURI[0];
            sd = DECORATORS.get(scheme);
        }
        if (sd == null) {
            // okay there was no decorator found via component name (scheme), then try FQN
            if (endpoint instanceof DefaultEndpoint) {
                Component comp = ((DefaultEndpoint) endpoint).getComponent();
                String fqn = comp.getClass().getName();
                // lookup via FQN
                sd = DECORATORS.values().stream().filter(d -> fqn.equals(d.getComponentClassName())).findFirst()
                .orElse(null);
            }
        }
        if (sd == null) {
            sd = SpanDecorator.DEFAULT;
        }

        return sd;
    }

    public Set<String> getExcludePatterns() {
        return excludePatterns;
    }

    public void setExcludePatterns(Set<String> excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

    public void addExcludePattern(String pattern) {
        excludePatterns.add(pattern);
    }

    public Tracer getTracer() {
        return tracer;
    }

    public void setTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, NamedNode route) {
        return new OpenTelemetryRoutePolicy();

    }

    @Override protected void doInit() throws Exception {
        camelContext.getManagementStrategy().addEventNotifier(eventNotifier);
        if (!camelContext.getRoutePolicyFactories().contains(this)) {
            camelContext.addRoutePolicyFactory(this);
        }
        if (tracer == null) {
            Set<Tracer> tracers = camelContext.getRegistry().findByType(Tracer.class);
            if (tracers.size() == 1) {
                tracer = tracers.iterator().next();
            }
        }
    }

    private Span.Kind mapToSpanKind(SpanKind kind) {
        if (kind == SpanKind.SPAN_KIND_CLIENT) {
            return Span.Kind.CLIENT;
        }
        return Span.Kind.SERVER;
    }

    private boolean isExcluded(Exchange exchange, Endpoint endpoint) {
        String url = endpoint.getEndpointUri();
        if (url != null && !excludePatterns.isEmpty()) {
            for (String pattern : excludePatterns) {
                if (EndpointHelper.matchEndpoint(exchange.getContext(), url, pattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    private final class OpenTelemetryEventNotifier extends EventNotifierSupport {

        private void handleExchangeSendingEvent(CamelEvent.ExchangeSendingEvent e) {
            Span parent = tracer.getCurrentSpan();
            if (parent != null) {
                tracer.withSpan(parent);
            }
            Span span = tracer.spanBuilder("operation").startSpan();
            SpanDecorator sd = getSpanDecorator(e.getEndpoint());
            if (!sd.newSpan() || isExcluded(e.getExchange(), e.getEndpoint())) {
                return;
            }
            sd.pre(new OpenTelemetrySpanWrapper(span), e.getExchange(), e.getEndpoint());
            OpenTelemetry.getPropagators().getHttpTextFormat().inject(Context.current(),
            sd.getInjectAdapter(e.getExchange().getIn().getHeaders(), encoding), new OpenTelemetrySetter());
            if (LOG.isTraceEnabled()) {
                LOG.trace("OpenTracing: start client span={}", span);
            }
        }


        private void handleExchangeSentEvent(CamelEvent.ExchangeSentEvent e) {
            Span span = tracer.getCurrentSpan();
            SpanDecorator sd = getSpanDecorator(e.getEndpoint());
            if (!sd.newSpan() || isExcluded(e.getExchange(), e.getEndpoint())) {
                return;
            }
            if (span != null) {
                sd.post(new OpenTelemetrySpanWrapper(span), e.getExchange(), e.getEndpoint());
                span.end();
            } else {
                LOG.warn("OpenTelemetry: could not find managed span for exchange={}", e.getExchange());
            }
        }

        @Override
        public void notify(CamelEvent event) throws Exception {
            try {
                if (event instanceof CamelEvent.ExchangeSendingEvent) {
                    CamelEvent.ExchangeSendingEvent ese = (CamelEvent.ExchangeSendingEvent) event;
                    handleExchangeSendingEvent(ese);

                } else if (event instanceof CamelEvent.ExchangeSentEvent) {
                    CamelEvent.ExchangeSentEvent ese = (CamelEvent.ExchangeSentEvent) event;
                    handleExchangeSentEvent(ese);
                }
            } catch (Throwable t) {
                LOG.warn("OpenTracing: Failed to capture tracing data", t);
            }
        }

        @Override
        public boolean isEnabled(CamelEvent event) {
            return event instanceof CamelEvent.ExchangeSendingEvent || event instanceof CamelEvent.ExchangeSentEvent;
        }

        @Override
        public String toString() {
            return "OpenTelemetryEventNotifier";
        }
    }

    private final class OpenTelemetryRoutePolicy extends RoutePolicySupport {
        @Override
        public void onExchangeBegin(Route route, Exchange exchange) {
            SpanDecorator sd = getSpanDecorator(route.getEndpoint());

            Context extractedContext = OpenTelemetry.getPropagators().getHttpTextFormat().extract(Context.current(),
            sd.getExtractAdapter(exchange.getIn().getHeaders(), encoding), new OpenTelemetryGetter());
            try (Scope scope = ContextUtils.withScopedContext(extractedContext)) {
                Span span = tracer.spanBuilder(sd.getOperationName(exchange, route.getEndpoint()))
                .setSpanKind(mapToSpanKind(sd.getReceiverSpanKind())).startSpan();
                sd.pre(new OpenTelemetrySpanWrapper(span), exchange, route.getEndpoint());
                if (LOG.isTraceEnabled()) {
                    LOG.trace("OpenTelemetry: start server span={}", span);
                }
            }
        }

        @Override
        public void onExchangeDone(Route route, Exchange exchange) {
            try {
                Span span = tracer.getCurrentSpan();
                if (span != null) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("OpenTracing: finish server span={}", span);
                    }
                    SpanDecorator sd = getSpanDecorator(route.getEndpoint());
                    sd.post(new OpenTelemetrySpanWrapper(span), exchange, route.getEndpoint());
                    span.end();
                } else {
                    LOG.warn("OpenTracing: could not find managed span for exchange={}", exchange);
                }
            } catch (Throwable t) {
                // This exception is ignored
                LOG.warn("OpenTracing: Failed to capture tracing data", t);
            }
        }

        private final class OpenTracingLogListener implements LogListener {

            @Override public String onLog(Exchange exchange, CamelLogger camelLogger, String message) {
                return null;
            }
        }
    }
}