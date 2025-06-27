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
package org.apache.camel.telemetry;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StaticService;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.CamelTracingService;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.LogListener;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Tracer extends ServiceSupport implements CamelTracingService, RoutePolicyFactory, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(Tracer.class);

    private CamelContext camelContext;
    /*
     * Component configuration
     */
    private String excludePatterns;
    private boolean traceProcessors;

    private final TracingEventNotifier eventNotifier = new TracingEventNotifier();
    private final SpanStorageManager spanStorageManager = new SpanStorageManagerExchange();
    private final SpanDecoratorManager spanDecoratorManager = new SpanDecoratorManagerImpl();

    /*
     * It has to be provided by the specific implementation
     */
    private SpanLifecycleManager spanLifecycleManager;

    protected abstract void initTracer();

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @ManagedAttribute
    public String getExcludePatterns() {
        return excludePatterns;
    }

    public void setExcludePatterns(String excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

    @ManagedAttribute
    public boolean isTraceProcessors() {
        return traceProcessors;
    }

    public void setTraceProcessors(boolean traceProcessors) {
        this.traceProcessors = traceProcessors;
    }

    public SpanLifecycleManager getSpanLifecycleManager() {
        return this.spanLifecycleManager;
    }

    public void setSpanLifecycleManager(SpanLifecycleManager spanLifecycleManager) {
        this.spanLifecycleManager = spanLifecycleManager;
    }

    @Override
    public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, NamedNode route) {
        init(camelContext);
        return new TracingRoutePolicy();
    }

    /**
     * Registers this {@link Tracer} on the {@link CamelContext} if not already registered.
     */
    public void init(CamelContext camelContext) {
        if (hasOtherTracerType(camelContext)) {
            LOG.warn("Could not add {} tracer type. Another tracer type, {}, was already registered. " +
                     "Make sure to include only one tracing dependency type.",
                    this.getClass(),
                    camelContext.hasService(Tracer.class).getClass());
            return;
        }
        if (!camelContext.hasService(this)) {
            try {
                // start this service eager so we init before Camel is starting up
                camelContext.addService(this, true, true);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
    }

    // Check if there is any other registered Tracer.
    private boolean hasOtherTracerType(CamelContext camelContext) {
        Tracer t = camelContext.hasService(Tracer.class);
        if (t == null) {
            return false;
        }
        return !this.getClass().equals(t.getClass());
    }

    @Override
    protected void doInit() {
        ObjectHelper.notNull(camelContext, "CamelContext", this);
        camelContext.getManagementStrategy().addEventNotifier(eventNotifier);
        if (!camelContext.getRoutePolicyFactories().contains(this)) {
            camelContext.addRoutePolicyFactory(this);
        }
        camelContext.getCamelContextExtension().addLogListener(new TracingLogListener());
        if (isTraceProcessors()) {
            InterceptStrategy traceProcessorsStrategy = new TraceProcessorsInterceptStrategy(this);
            camelContext.getCamelContextExtension().addInterceptStrategy(traceProcessorsStrategy);
        }
        initTracer();
        ServiceHelper.startService(eventNotifier);
    }

    @Override
    protected void doShutdown() {
        // stop event notifier
        camelContext.getManagementStrategy().removeEventNotifier(eventNotifier);
        ServiceHelper.stopService(eventNotifier);

        // remove route policy
        camelContext.getRoutePolicyFactories().remove(this);
    }

    public boolean exclude(String endpointUri, CamelContext context) {
        if (endpointUri != null && excludePatterns != null) {
            for (String pattern : excludePatterns.split(",")) {
                pattern = pattern.trim();
                if (EndpointHelper.matchEndpoint(context, endpointUri, pattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    private final class TracingEventNotifier extends EventNotifierSupport {

        public TracingEventNotifier() {
            // ignore these
            setIgnoreCamelContextEvents(true);
            setIgnoreCamelContextInitEvents(true);
            setIgnoreRouteEvents(true);
        }

        @Override
        public void notify(CamelEvent event) throws Exception {
            try {
                if (event instanceof CamelEvent.ExchangeSendingEvent ese) {
                    if (exclude(ese.getEndpoint().getEndpointUri(), ese.getExchange().getContext())) {
                        LOG.debug("Tracing: endpoint {} is explicitly excluded, skipping.", ese.getEndpoint());
                    } else {
                        beginEventSpan(ese.getExchange(), ese.getEndpoint(), Op.EVENT_SENT);
                    }
                } else if (event instanceof CamelEvent.ExchangeSentEvent ese) {
                    if (exclude(ese.getEndpoint().getEndpointUri(), ese.getExchange().getContext())) {
                        LOG.debug("Tracing: endpoint {} is explicitly excluded, skipping.", ese.getEndpoint());
                    } else {
                        endEventSpan(ese.getExchange(), ese.getEndpoint());
                    }
                }
            } catch (Exception t) {
                // This exception is ignored
                LOG.warn("Tracing: Failed to capture tracing data. This exception is ignored.", t);
            }
        }
    }

    private final class TracingRoutePolicy extends RoutePolicySupport {
        @Override
        public void onExchangeBegin(Route route, Exchange exchange) {
            try {
                if (exclude(route.getEndpoint().getEndpointUri(), exchange.getContext())) {
                    LOG.debug("Tracing: endpoint {} is explicitly excluded, skipping.", route.getEndpoint());
                } else {
                    beginEventSpan(exchange, route.getEndpoint(), Op.EVENT_RECEIVED);
                }
            } catch (Exception t) {
                LOG.warn("Tracing: Failed to capture tracing data. This exception is ignored.", t);
            }
        }

        @Override
        public void onExchangeDone(Route route, Exchange exchange) {
            try {
                if (exclude(route.getEndpoint().getEndpointUri(), exchange.getContext())) {
                    LOG.debug("Tracing: endpoint {} is explicitly excluded, skipping.", route.getEndpoint());
                } else {
                    endEventSpan(exchange, route.getEndpoint());
                }
            } catch (Exception t) {
                LOG.warn("Tracing: Failed to capture tracing data. This exception is ignored.", t);
            }
        }
    }

    private final class TracingLogListener implements LogListener {

        @Override
        public String onLog(Exchange exchange, CamelLogger camelLogger, String message) {
            try {
                Span span = spanStorageManager.peek(exchange);
                if (span != null) {
                    Map<String, String> fields = new HashMap<>();
                    fields.put("message", message);
                    span.log(fields);
                }
            } catch (Exception t) {
                // This exception is ignored
                LOG.warn("Tracing: Failed to capture tracing data. This exception is ignored.", t);
            }
            return message;
        }
    }

    protected void beginEventSpan(Exchange exchange, Endpoint endpoint, Op op) throws Exception {
        SpanDecorator spanDecorator = spanDecoratorManager.get(endpoint);
        Span parentSpan = spanStorageManager.peek(exchange);
        String spanName = spanDecorator.getOperationName(exchange, endpoint);
        Span span = spanLifecycleManager.create(spanName, parentSpan, spanDecorator.getExtractor(exchange));
        span.setTag(TagConstants.OP, op.toString());
        spanDecorator.beforeTracingEvent(span, exchange, endpoint);
        spanLifecycleManager.activate(span);
        spanStorageManager.push(exchange, span);
        spanLifecycleManager.inject(span, spanDecorator.getInjector(exchange));
        LOG.debug("Started event span: {}", span);
    }

    protected void beginProcessorSpan(Exchange exchange, String processorName) throws Exception {
        SpanDecorator spanDecorator = spanDecoratorManager.get(processorName);
        Span parentSpan = spanStorageManager.peek(exchange);
        if (parentSpan == null) {
            // there is some inconsistency
            LOG.warn("Processor tracing parent should not be null!");
        }
        Span span = spanLifecycleManager.create(processorName, parentSpan, spanDecorator.getExtractor(exchange));
        span.setTag(TagConstants.OP, Op.EVENT_PROCESS.toString());
        spanDecorator.beforeTracingEvent(span, exchange, null);
        spanLifecycleManager.activate(span);
        spanStorageManager.push(exchange, span);
        LOG.debug("Started processor span: {}", span);
    }

    protected void endEventSpan(Exchange exchange, Endpoint endpoint) throws Exception {
        Span span = spanStorageManager.pull(exchange);
        if (span == null) {
            LOG.warn("Could not find managed span for event: {}", endpoint);
            return;
        }
        SpanDecorator spanDecorator = spanDecoratorManager.get(endpoint);
        spanDecorator.afterTracingEvent(span, exchange);
        spanLifecycleManager.deactivate(span);
        spanLifecycleManager.close(span);
        LOG.debug("Stopped event span: {}", span);
    }

    protected void endProcessorSpan(Exchange exchange, String processorName) throws Exception {
        Span span = spanStorageManager.pull(exchange);
        if (span == null) {
            LOG.warn("Could not find managed span for processor: {}", processorName);
            return;
        }
        SpanDecorator spanDecorator = spanDecoratorManager.get(processorName);
        spanDecorator.afterTracingEvent(span, exchange);
        spanLifecycleManager.deactivate(span);
        spanLifecycleManager.close(span);
        LOG.debug("Stopped processor span: {}", span);
    }

}
