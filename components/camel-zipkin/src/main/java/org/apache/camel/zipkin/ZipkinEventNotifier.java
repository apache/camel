/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.zipkin;

import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientSpanThreadBinder;
import com.github.kristofa.brave.Sampler;
import com.github.kristofa.brave.SpanCollector;
import com.twitter.zipkin.gen.Span;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.management.event.ExchangeCreatedEvent;
import org.apache.camel.management.event.ExchangeFailedEvent;
import org.apache.camel.management.event.ExchangeSendingEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.util.EndpointHelper;

import static org.apache.camel.builder.ExpressionBuilder.routeIdExpression;

/**
 * To use zipkin with Camel then setup this {@link org.apache.camel.spi.EventNotifier} in your Camel application.
 * <p/>
 * Events (span) are captured for incoming and outgoing messages being sent to/from Camel.
 * This means you need to configure which which Camel endpoints that maps to zipkin service names.
 * The mapping can be configured using
 * <ul>
 *     <li>route id - A Camel route id</li>
 *     <li>endpoint url - A Camel endpoint url</li>
 * </ul>
 * For both kinds you can use wildcards and regular expressions to match, which is using the rules from
 * {@link EndpointHelper#matchPattern(String, String)} and {@link EndpointHelper#matchEndpoint(CamelContext, String, String)}
 * <p/>
 * At least one mapping must be configured, you can use <tt>*</tt> to match all incoming and outgoing messages.
 */
public class ZipkinEventNotifier extends EventNotifierSupport {

    private float rate = 1.0f;
    private SpanCollector spanCollector;
    private Map<String, String> serviceMappings = new HashMap<>();
    private Map<String, Brave> braves = new HashMap<>();

    public ZipkinEventNotifier() {
    }

    public float getRate() {
        return rate;
    }

    public void setRate(float rate) {
        this.rate = rate;
    }

    public SpanCollector getSpanCollector() {
        return spanCollector;
    }

    public void setSpanCollector(SpanCollector spanCollector) {
        this.spanCollector = spanCollector;
    }

    public String getServiceName() {
        return serviceMappings.get("*");
    }

    public void setServiceName(String serviceName) {
        serviceMappings.put("*", serviceName);
    }

    public Map<String, String> getServiceMappings() {
        return serviceMappings;
    }

    public void setServiceMappings(Map<String, String> serviceMappings) {
        this.serviceMappings = serviceMappings;
    }

    public void addServiceMapping(String pattern, String serviceName) {
        serviceMappings.put(pattern, serviceName);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (serviceMappings.isEmpty()) {
            throw new IllegalStateException("At least one service name must be configured");
        }

        // create braves mapped per service name
        for (Map.Entry<String, String> entry : serviceMappings.entrySet()) {
            String pattern = entry.getKey();
            String serviceName = entry.getValue();
            Brave brave = braves.get(pattern);
            if (brave == null) {
                Brave.Builder builder = new Brave.Builder(serviceName);
                builder = builder.traceSampler(Sampler.create(rate));
                if (spanCollector != null) {
                    builder = builder.spanCollector(spanCollector);
                }
                brave = builder.build();
                braves.put(serviceName, brave);
            }
        }
    }

    @Override
    public boolean isEnabled(EventObject event) {
        return event instanceof ExchangeSendingEvent || event instanceof ExchangeSentEvent
                || event instanceof ExchangeCreatedEvent || event instanceof ExchangeCompletedEvent || event instanceof ExchangeFailedEvent;
    }

    private String getServiceName(Exchange exchange, Endpoint endpoint) {
        String answer = null;

        String id = routeIdExpression().evaluate(exchange, String.class);
        for (Map.Entry<String, String> entry : serviceMappings.entrySet()) {
            String pattern = entry.getKey();
            if (EndpointHelper.matchPattern(pattern, id)) {
                answer = entry.getValue();
                break;
            }
        }

        if (answer == null) {
            id = exchange.getFromRouteId();
            for (Map.Entry<String, String> entry : serviceMappings.entrySet()) {
                String pattern = entry.getKey();
                if (EndpointHelper.matchPattern(pattern, id)) {
                    answer = entry.getValue();
                    break;
                }
            }
        }

        if (answer == null && endpoint != null) {
            String url = endpoint.getEndpointUri();
            for (Map.Entry<String, String> entry : serviceMappings.entrySet()) {
                String pattern = entry.getKey();
                if (EndpointHelper.matchEndpoint(exchange.getContext(), url, pattern)) {
                    answer = entry.getValue();
                    break;
                }
            }
        }

        if (answer == null && exchange.getFromEndpoint() != null) {
            String url = exchange.getFromEndpoint().getEndpointUri();
            for (Map.Entry<String, String> entry : serviceMappings.entrySet()) {
                String pattern = entry.getKey();
                if (EndpointHelper.matchEndpoint(exchange.getContext(), url, pattern)) {
                    answer = entry.getValue();
                    break;
                }
            }
        }

        return answer;
    }

    private Brave getBrave(String serviceName) {
        if (serviceName != null) {
            return braves.get(serviceName);
        } else {
            return null;
        }
    }

    @Override
    public void notify(EventObject event) throws Exception {
        if (event instanceof ExchangeSendingEvent) {
            clientRequest((ExchangeSendingEvent) event);
        } else if (event instanceof ExchangeSentEvent) {
            clientResponse((ExchangeSentEvent) event);
        } else if (event instanceof ExchangeCreatedEvent) {
            serverRequest((ExchangeCreatedEvent) event);
        } else if (event instanceof ExchangeCompletedEvent) {
            serverResponse((ExchangeCompletedEvent) event);
        } else if (event instanceof ExchangeFailedEvent) {
            serverResponse((ExchangeFailedEvent) event);
        }
    }

    private void clientRequest(ExchangeSendingEvent event) {
        String serviceName = getServiceName(event.getExchange(), event.getEndpoint());
        Brave brave = getBrave(serviceName);
        if (brave != null) {
            ClientSpanThreadBinder binder = brave.clientSpanThreadBinder();
            brave.clientRequestInterceptor().handle(new ZipkinClientRequestAdapter(serviceName, event.getExchange(), event.getEndpoint()));
            Span span = binder.getCurrentClientSpan();
            event.getExchange().setProperty("CamelZipkinSpan", span);
        }
    }

    private void clientResponse(ExchangeSentEvent event) {
        String serviceName = getServiceName(event.getExchange(), event.getEndpoint());
        Brave brave = getBrave(serviceName);
        if (brave != null) {
            ClientSpanThreadBinder binder = brave.clientSpanThreadBinder();
            Span span = event.getExchange().getProperty("CamelZipkinSpan", Span.class);
            binder.setCurrentSpan(span);
            brave.clientResponseInterceptor().handle(new ZipkinClientResponseAdaptor(event.getExchange(), event.getEndpoint()));
            binder.setCurrentSpan(null);
        }
    }

    private void serverRequest(ExchangeCreatedEvent event) {
        String serviceName = getServiceName(event.getExchange(), null);
        Brave brave = getBrave(serviceName);
        if (brave != null) {
            ClientSpanThreadBinder binder = brave.clientSpanThreadBinder();
            brave.serverRequestInterceptor().handle(new ZipkinServerRequestAdapter(event.getExchange()));
            Span span = binder.getCurrentClientSpan();
            event.getExchange().setProperty("CamelZipkinSpan", span);
        }
    }

    private void serverResponse(ExchangeCompletedEvent event) {
        String serviceName = getServiceName(event.getExchange(), null);
        Brave brave = getBrave(serviceName);
        if (brave != null) {
            ClientSpanThreadBinder binder = brave.clientSpanThreadBinder();
            Span span = event.getExchange().getProperty("CamelZipkinSpan", Span.class);
            binder.setCurrentSpan(span);
            brave.serverResponseInterceptor().handle(new ZipkinServerResponseAdapter(event.getExchange()));
            binder.setCurrentSpan(null);
        }
    }

    private void serverResponse(ExchangeFailedEvent event) {
        String serviceName = getServiceName(event.getExchange(), null);
        Brave brave = getBrave(serviceName);
        if (brave != null) {
            ClientSpanThreadBinder binder = brave.clientSpanThreadBinder();
            Span span = event.getExchange().getProperty("CamelZipkinSpan", Span.class);
            binder.setCurrentSpan(span);
            brave.serverResponseInterceptor().handle(new ZipkinServerResponseAdapter(event.getExchange()));
            binder.setCurrentSpan(null);
        }
    }

}
