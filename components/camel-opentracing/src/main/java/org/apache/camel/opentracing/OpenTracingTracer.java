/**
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

import java.net.URI;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.management.event.ExchangeSendingEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.global.GlobalTracer;
import io.opentracing.contrib.spanmanager.DefaultSpanManager;
import io.opentracing.contrib.spanmanager.SpanManager;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;

/**
 * To use OpenTracing with Camel then setup this {@link OpenTracingTracer} in your Camel application.
 * <p/>
 * This class is implemented as both an {@link org.apache.camel.spi.EventNotifier} and {@link RoutePolicy} that allows
 * to trap when Camel starts/ends an {@link Exchange} being routed using the {@link RoutePolicy} and during the routing
 * if the {@link Exchange} sends messages, then we track them using the {@link org.apache.camel.spi.EventNotifier}.
 */
public class OpenTracingTracer implements RoutePolicyFactory, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(OpenTracingTracer.class);

    private final OpenTracingEventNotifier eventNotifier = new OpenTracingEventNotifier();

    private CamelContext camelContext;

    private Tracer tracer = GlobalTracer.get();
    private SpanManager spanManager = DefaultSpanManager.getInstance();

    private static Map<String, SpanDecorator> decorators = new HashMap<>();

    static {
        ServiceLoader.load(SpanDecorator.class).forEach(d -> decorators.put(d.getComponent(), d));
    }

    public OpenTracingTracer() {
    }

    @Override
    public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, RouteDefinition route) {
        return new OpenTracingRoutePolicy(routeId);
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        if (this.camelContext != null) {
            // stop event notifier
            camelContext.getManagementStrategy().removeEventNotifier(eventNotifier);

             // remove route policy
            camelContext.getRoutePolicyFactories().remove(this);
        }

        this.camelContext = camelContext;

        if (this.camelContext != null) {
            camelContext.getManagementStrategy().addEventNotifier(eventNotifier);
            if (!camelContext.getRoutePolicyFactories().contains(this)) {
                camelContext.addRoutePolicyFactory(this);
            }
            
            // TODO: In example client, this was required otherwise outbound invocations
            // were not instrumented - may be better to reinstate StaticService approach, but
            // then need to resolve issue with xml dsl correctly starting service to init
            // event notifier.
            try {
                ServiceHelper.startServices(eventNotifier);
            } catch (Exception e) {
                LOG.error("Failed to start event notifier", e);
            }
        }
    }

    public Tracer getTracer() {
    	return tracer;
    }

    public void setTracer(Tracer tracer) {
    	this.tracer = tracer;
    }

    protected SpanDecorator getSpanDecorator(Endpoint endpoint) {
        SpanDecorator sd = decorators.get(URI.create(endpoint.getEndpointUri()).getScheme());
        if (sd == null) {
            return SpanDecorator.DEFAULT;
        }
        return sd;
    }

    private final class OpenTracingEventNotifier extends EventNotifierSupport {

        @Override
        public void notify(EventObject event) throws Exception {
            if (event instanceof ExchangeSendingEvent) {
                ExchangeSendingEvent ese = (ExchangeSendingEvent) event;
                SpanManager.ManagedSpan parent = spanManager.current();
                SpanDecorator sd = getSpanDecorator(ese.getEndpoint());
                SpanBuilder spanBuilder = tracer.buildSpan(sd.getOperationName(ese.getExchange(), ese.getEndpoint()))
                		.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT);
                // Temporary workaround to avoid adding 'null' span as a parent
                if (parent != null && parent.getSpan() != null) {
                    spanBuilder.asChildOf(parent.getSpan());
                }
                Span span = spanBuilder.start();
                sd.pre(span, ese.getExchange(), ese.getEndpoint());
                tracer.inject(span.context(), Format.Builtin.TEXT_MAP,
                        new CamelHeadersInjectAdapter(ese.getExchange().getIn().getHeaders()));
                spanManager.manage(span);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("OpenTracing: start client span=" + span);
                }
            } else if (event instanceof ExchangeSentEvent) {
                SpanManager.ManagedSpan managedSpan = spanManager.current();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("OpenTracing: start client span=" + managedSpan.getSpan());
                }
                SpanDecorator sd = getSpanDecorator(((ExchangeSentEvent)event).getEndpoint());
                sd.post(managedSpan.getSpan(), ((ExchangeSentEvent)event).getExchange(),
                        ((ExchangeSentEvent)event).getEndpoint());
                managedSpan.getSpan().finish();
                managedSpan.release();
            }
        }

        @Override
        public boolean isEnabled(EventObject event) {
            return event instanceof ExchangeSendingEvent
                    || event instanceof ExchangeSentEvent;
        }

        @Override
        public String toString() {
            return "OpenTracingEventNotifier";
        }
    }

    private final class OpenTracingRoutePolicy extends RoutePolicySupport {

        OpenTracingRoutePolicy(String routeId) {
        }

        @Override
        public void onExchangeBegin(Route route, Exchange exchange) {
            SpanDecorator sd = getSpanDecorator(route.getEndpoint());
            Span span = tracer.buildSpan(sd.getOperationName(exchange, route.getEndpoint()))
                    .asChildOf(tracer.extract(Format.Builtin.TEXT_MAP,
                            new CamelHeadersExtractAdapter(exchange.getIn().getHeaders())))
            		.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
            		.start();
            sd.pre(span, exchange, route.getEndpoint());
            spanManager.manage(span);
            if (LOG.isTraceEnabled()) {
                LOG.trace("OpenTracing: start server span=" + span);
            }
        }

        @Override
        public void onExchangeDone(Route route, Exchange exchange) {
            SpanManager.ManagedSpan managedSpan = spanManager.current();
            if (LOG.isTraceEnabled()) {
                LOG.trace("OpenTracing: finish server span=" + managedSpan.getSpan());
            }
            SpanDecorator sd = getSpanDecorator(route.getEndpoint());
            sd.post(managedSpan.getSpan(), exchange, route.getEndpoint());
            managedSpan.getSpan().finish();
            managedSpan.release();
        }
    }

}
