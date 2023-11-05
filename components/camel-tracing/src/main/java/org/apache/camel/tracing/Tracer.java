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
package org.apache.camel.tracing;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
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
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Tracer extends ServiceSupport implements CamelTracingService, RoutePolicyFactory, StaticService {
    protected static final Map<String, SpanDecorator> DECORATORS = new HashMap<>();
    static final AutoCloseable NOOP_CLOSEABLE = () -> {
    };
    private static final Logger LOG = LoggerFactory.getLogger(Tracer.class);

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

    protected boolean encoding;
    private final TracingLogListener logListener = new TracingLogListener();
    private final TracingEventNotifier eventNotifier = new TracingEventNotifier();
    private String excludePatterns;
    private InterceptStrategy tracingStrategy;
    private CamelContext camelContext;

    protected abstract void initTracer();

    protected abstract SpanAdapter startSendingEventSpan(
            String operationName, SpanKind kind, SpanAdapter parent, Exchange exchange, InjectAdapter injectAdapter);

    protected abstract void initContextPropagators();

    protected abstract SpanAdapter startExchangeBeginSpan(
            Exchange exchange, SpanDecorator sd, String operationName, SpanKind kind, SpanAdapter parent);

    protected abstract void finishSpan(SpanAdapter span);

    protected abstract void inject(SpanAdapter span, InjectAdapter adapter);

    /**
     * Returns the currently used tracing strategy which is responsible for tracking invoked EIP or beans.
     *
     * @return The currently used tracing strategy
     */
    public InterceptStrategy getTracingStrategy() {
        return tracingStrategy;
    }

    /**
     * Specifies the instance responsible for tracking invoked EIP and beans with Tracing.
     *
     * @param tracingStrategy The instance which tracks invoked EIP and beans
     */
    public void setTracingStrategy(InterceptStrategy tracingStrategy) {
        this.tracingStrategy = tracingStrategy;
    }

    public void addDecorator(SpanDecorator decorator) {
        DECORATORS.put(decorator.getComponent(), decorator);
    }

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
    public boolean isEncoding() {
        return encoding;
    }

    public void setEncoding(boolean encoding) {
        this.encoding = encoding;
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
        if (!camelContext.hasService(this)) {
            try {
                // start this service eager so we init before Camel is starting up
                camelContext.addService(this, true, true);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
    }

    @Override
    protected void doInit() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext", this);

        camelContext.getManagementStrategy().addEventNotifier(eventNotifier);
        if (!camelContext.getRoutePolicyFactories().contains(this)) {
            camelContext.addRoutePolicyFactory(this);
        }
        camelContext.getCamelContextExtension().addLogListener(logListener);

        if (tracingStrategy != null) {
            camelContext.getCamelContextExtension().addInterceptStrategy(tracingStrategy);
        }
        initTracer();
        initContextPropagators();
        ServiceHelper.startService(eventNotifier);
    }

    @Override
    protected void doShutdown() throws Exception {
        // stop event notifier
        camelContext.getManagementStrategy().removeEventNotifier(eventNotifier);
        ServiceHelper.stopService(eventNotifier);

        // remove route policy
        camelContext.getRoutePolicyFactories().remove(this);
    }

    protected SpanDecorator getSpanDecorator(Endpoint endpoint) {
        SpanDecorator sd = null;

        String uri = endpoint.getEndpointUri();
        String[] splitURI = StringHelper.splitOnCharacter(uri, ":", 2);
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

    private boolean isExcluded(Exchange exchange, Endpoint endpoint) {
        String url = endpoint.getEndpointUri();
        if (url != null && excludePatterns != null) {
            for (String pattern : excludePatterns.split(",")) {
                pattern = pattern.trim();
                if (EndpointHelper.matchEndpoint(exchange.getContext(), url, pattern)) {
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
            // we need also async processing started events
            setIgnoreExchangeAsyncProcessingStartedEvents(false);
        }

        @Override
        public void notify(CamelEvent event) throws Exception {
            try {
                if (event instanceof CamelEvent.ExchangeSendingEvent) {
                    CamelEvent.ExchangeSendingEvent ese = (CamelEvent.ExchangeSendingEvent) event;
                    SpanDecorator sd = getSpanDecorator(ese.getEndpoint());
                    if (shouldExclude(sd, ese.getExchange(), ese.getEndpoint())) {
                        return;
                    }

                    SpanAdapter parent = ActiveSpanManager.getSpan(ese.getExchange());
                    InjectAdapter injectAdapter = sd.getInjectAdapter(ese.getExchange().getIn().getHeaders(), encoding);
                    SpanAdapter span = startSendingEventSpan(sd.getOperationName(ese.getExchange(), ese.getEndpoint()),
                            sd.getInitiatorSpanKind(), parent, ese.getExchange(), injectAdapter);
                    sd.pre(span, ese.getExchange(), ese.getEndpoint());
                    inject(span, injectAdapter);
                    ActiveSpanManager.activate(ese.getExchange(), span);
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Tracing: start client span={}", span);
                    }
                } else if (event instanceof CamelEvent.ExchangeSentEvent) {
                    CamelEvent.ExchangeSentEvent ese = (CamelEvent.ExchangeSentEvent) event;
                    SpanDecorator sd = getSpanDecorator(ese.getEndpoint());
                    if (shouldExclude(sd, ese.getExchange(), ese.getEndpoint())) {
                        return;
                    }

                    SpanAdapter span = ActiveSpanManager.getSpan(ese.getExchange());
                    if (span != null) {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("Tracing: stop client span={}", span);
                        }
                        sd.post(span, ese.getExchange(), ese.getEndpoint());
                        ActiveSpanManager.deactivate(ese.getExchange());
                        finishSpan(span);
                    } else {
                        LOG.warn("Tracing: could not find managed span for exchange={}", ese.getExchange());
                    }
                } else if (event instanceof CamelEvent.ExchangeAsyncProcessingStartedEvent) {
                    CamelEvent.ExchangeAsyncProcessingStartedEvent eap = (CamelEvent.ExchangeAsyncProcessingStartedEvent) event;

                    // no need to filter scopes here. It's ok to close a scope multiple times and
                    // implementations check if scope being disposed is current
                    // and should not do anything if scopes don't match.
                    ActiveSpanManager.endScope(eap.getExchange());
                }
            } catch (Exception t) {
                // This exception is ignored
                LOG.warn("Tracing: Failed to capture tracing data", t);
            }
        }

        private boolean shouldExclude(SpanDecorator sd, Exchange exchange, Endpoint endpoint) {
            return !sd.newSpan()
                    || isExcluded(exchange, endpoint);
        }
    }

    private final class TracingRoutePolicy extends RoutePolicySupport {

        @Override
        public void onExchangeBegin(Route route, Exchange exchange) {
            try {
                if (isExcluded(exchange, route.getEndpoint())) {
                    return;
                }
                SpanDecorator sd = getSpanDecorator(route.getEndpoint());
                SpanAdapter parent = ActiveSpanManager.getSpan(exchange);
                SpanAdapter span;
                span = startExchangeBeginSpan(exchange, sd, sd.getOperationName(exchange, route.getEndpoint()),
                        sd.getReceiverSpanKind(), parent);
                sd.pre(span, exchange, route.getEndpoint());
                ActiveSpanManager.activate(exchange, span);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Tracing: start server span={}", span);
                }
            } catch (Exception t) {
                // This exception is ignored
                LOG.warn("Tracing: Failed to capture tracing data", t);
            }
        }

        @Override
        public void onExchangeDone(Route route, Exchange exchange) {
            try {
                if (isExcluded(exchange, route.getEndpoint())) {
                    return;
                }
                SpanAdapter span = ActiveSpanManager.getSpan(exchange);
                if (span != null) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Tracing: finish server span={}", span);
                    }
                    SpanDecorator sd = getSpanDecorator(route.getEndpoint());
                    sd.post(span, exchange, route.getEndpoint());
                    finishSpan(span);
                    ActiveSpanManager.deactivate(exchange);
                } else {
                    LOG.warn("Tracing: could not find managed span for exchange={}", exchange);
                }
            } catch (Exception t) {
                // This exception is ignored
                LOG.warn("Tracing: Failed to capture tracing data", t);
            }
        }
    }

    private static final class TracingLogListener implements LogListener {

        @Override
        public String onLog(Exchange exchange, CamelLogger camelLogger, String message) {
            try {
                SpanAdapter span = ActiveSpanManager.getSpan(exchange);
                if (span != null) {
                    Map<String, String> fields = new HashMap<>();
                    fields.put("message", message);
                    span.log(fields);
                }
            } catch (Exception t) {
                // This exception is ignored
                LOG.warn("Tracing: Failed to capture tracing data", t);
            }
            return message;
        }
    }
}
