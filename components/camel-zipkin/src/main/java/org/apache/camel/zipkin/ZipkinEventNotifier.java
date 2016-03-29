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

import java.io.Closeable;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientSpanThreadBinder;
import com.github.kristofa.brave.Sampler;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import com.github.kristofa.brave.SpanCollector;
import com.twitter.zipkin.gen.Span;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.StatefulService;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.management.event.ExchangeCreatedEvent;
import org.apache.camel.management.event.ExchangeFailedEvent;
import org.apache.camel.management.event.ExchangeSendingEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ServiceHelper;

import static org.apache.camel.builder.ExpressionBuilder.routeIdExpression;

/**
 * To use zipkin with Camel then setup this {@link org.apache.camel.spi.EventNotifier} in your Camel application.
 * <p/>
 * Events (span) are captured for incoming and outgoing messages being sent to/from Camel.
 * This means you need to configure which which Camel endpoints that maps to zipkin service names.
 * The mapping can be configured using
 * <ul>
 * <li>route id - A Camel route id</li>
 * <li>endpoint url - A Camel endpoint url</li>
 * </ul>
 * For both kinds you can use wildcards and regular expressions to match, which is using the rules from
 * {@link EndpointHelper#matchPattern(String, String)} and {@link EndpointHelper#matchEndpoint(CamelContext, String, String)}
 * <p/>
 * At least one mapping must be configured, you can use <tt>*</tt> to match all incoming and outgoing messages.
 */
@ManagedResource(description = "Managing ZipkinEventNotifier")
public class ZipkinEventNotifier extends EventNotifierSupport implements StatefulService {

    private float rate = 1.0f;
    private SpanCollector spanCollector;
    private Map<String, String> serviceMappings = new HashMap<>();
    private Map<String, Brave> braves = new HashMap<>();
    private boolean includeMessageBody;

    public ZipkinEventNotifier() {
    }

    public float getRate() {
        return rate;
    }

    /**
     * Configures a rate that decides how many events should be traced by zpkin.
     * The rate is expressed as a percentage (1.0f = 100%, 0.5f is 50%, 0.1f is 10%).
     *
     * @param rate minimum sample rate is 0.0001, or 0.01% of traces
     */
    public void setRate(float rate) {
        this.rate = rate;
    }

    public SpanCollector getSpanCollector() {
        return spanCollector;
    }

    /**
     * The collector to use for sending zipkin span events to the zipkin server.
     */
    public void setSpanCollector(SpanCollector spanCollector) {
        this.spanCollector = spanCollector;
    }

    public String getServiceName() {
        return serviceMappings.get("*");
    }

    /**
     * To use a global service name that matches all Camel events
     */
    public void setServiceName(String serviceName) {
        serviceMappings.put("*", serviceName);
    }

    public Map<String, String> getServiceMappings() {
        return serviceMappings;
    }

    public void setServiceMappings(Map<String, String> serviceMappings) {
        this.serviceMappings = serviceMappings;
    }

    /**
     * Adds a service mapping that matches Camel events to the given zipkin serivce name.
     * See more details at the class javadoc.
     *
     * @param pattern  the pattern such as route id, endpoint url
     * @param serviceName the zpkin service name
     */
    public void addServiceMapping(String pattern, String serviceName) {
        serviceMappings.put(pattern, serviceName);
    }

    @ManagedAttribute(description = "Whether to include the Camel message body in the zipkin traces")
    public boolean isIncludeMessageBody() {
        return includeMessageBody;
    }

    /**
     * Whether to include the Camel message body in the zipkin traces.
     * <p/>
     * This is not recommended for production usage, or when having big payloads. You can limit the size by
     * configuring the <a href="http://camel.apache.org/how-do-i-set-the-max-chars-when-debug-logging-messages-in-camel.html">max debug log size</a>.
     */
    @ManagedAttribute(description = "Whether to include the Camel message body in the zipkin traces")
    public void setIncludeMessageBody(boolean includeMessageBody) {
        this.includeMessageBody = includeMessageBody;
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

        ServiceHelper.startService(spanCollector);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        // stop and close collector
        ServiceHelper.stopAndShutdownService(spanCollector);
        if (spanCollector instanceof Closeable) {
            IOHelper.close((Closeable) spanCollector);
        }
    }

    @Override
    public boolean isEnabled(EventObject event) {
        return event instanceof ExchangeSendingEvent
                || event instanceof ExchangeSentEvent
                || event instanceof ExchangeCreatedEvent
                || event instanceof ExchangeCompletedEvent
                || event instanceof ExchangeFailedEvent;
    }

    private String getServiceName(Exchange exchange, Endpoint endpoint) {
        String answer = null;

        String id = routeIdExpression().evaluate(exchange, String.class);
        if (id != null) {
            for (Map.Entry<String, String> entry : serviceMappings.entrySet()) {
                String pattern = entry.getKey();
                if (EndpointHelper.matchPattern(pattern, id)) {
                    answer = entry.getValue();
                    break;
                }
            }
        }

        if (answer == null) {
            id = exchange.getFromRouteId();
            if (id != null) {
                for (Map.Entry<String, String> entry : serviceMappings.entrySet()) {
                    String pattern = entry.getKey();
                    if (EndpointHelper.matchPattern(pattern, id)) {
                        answer = entry.getValue();
                        break;
                    }
                }
            }
        }

        if (answer == null && endpoint != null) {
            String url = endpoint.getEndpointUri();
            if (url != null) {
                for (Map.Entry<String, String> entry : serviceMappings.entrySet()) {
                    String pattern = entry.getKey();
                    if (EndpointHelper.matchEndpoint(exchange.getContext(), url, pattern)) {
                        answer = entry.getValue();
                        break;
                    }
                }
            }
        }

        if (answer == null && exchange.getFromEndpoint() != null) {
            String url = exchange.getFromEndpoint().getEndpointUri();
            if (url != null) {
                for (Map.Entry<String, String> entry : serviceMappings.entrySet()) {
                    String pattern = entry.getKey();
                    if (EndpointHelper.matchEndpoint(exchange.getContext(), url, pattern)) {
                        answer = entry.getValue();
                        break;
                    }
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
            ExchangeSendingEvent ese = (ExchangeSendingEvent) event;
            String serviceName = getServiceName(ese.getExchange(), ese.getEndpoint());
            Brave brave = getBrave(serviceName);
            if (brave != null) {
                clientRequest(brave, serviceName, ese);
            }
        } else if (event instanceof ExchangeSentEvent) {
            ExchangeSentEvent ese = (ExchangeSentEvent) event;
            String serviceName = getServiceName(ese.getExchange(), ese.getEndpoint());
            Brave brave = getBrave(serviceName);
            if (brave != null) {
                clientResponse(brave, serviceName, ese);
            }
        } else if (event instanceof ExchangeCreatedEvent) {
            ExchangeCreatedEvent ece = (ExchangeCreatedEvent) event;
            String serviceName = getServiceName(ece.getExchange(), null);
            Brave brave = getBrave(serviceName);
            if (brave != null) {
                serverRequest(brave, serviceName, ece);
            }
        } else if (event instanceof ExchangeCompletedEvent) {
            ExchangeCompletedEvent ece = (ExchangeCompletedEvent) event;
            String serviceName = getServiceName(ece.getExchange(), null);
            Brave brave = getBrave(serviceName);
            if (brave != null) {
                serverResponse(brave, serviceName, ece);
            }
        } else if (event instanceof ExchangeFailedEvent) {
            ExchangeFailedEvent efe = (ExchangeFailedEvent) event;
            String serviceName = getServiceName(efe.getExchange(), null);
            Brave brave = getBrave(serviceName);
            if (brave != null) {
                serverResponse(brave, serviceName, efe);
            }
        }
    }

    private void clientRequest(Brave brave, String serviceName, ExchangeSendingEvent event) {
        ClientSpanThreadBinder binder = brave.clientSpanThreadBinder();
        brave.clientRequestInterceptor().handle(new ZipkinClientRequestAdapter(this, serviceName, event.getExchange(), event.getEndpoint()));
        Span span = binder.getCurrentClientSpan();

        String key = "CamelZipkinClientSpan-" + serviceName;
        event.getExchange().setProperty(key, span);

        if (log.isDebugEnabled()) {
            log.debug("clientRequest[service={}, spanId={}]", serviceName, span != null ? span.getId() : "<null>");
        }
    }

    private void clientResponse(Brave brave, String serviceName, ExchangeSentEvent event) {
        ClientSpanThreadBinder binder = brave.clientSpanThreadBinder();
        String key = "CamelZipkinClientSpan-" + serviceName;
        Span span = event.getExchange().getProperty(key, Span.class);
        binder.setCurrentSpan(span);
        brave.clientResponseInterceptor().handle(new ZipkinClientResponseAdaptor(this, event.getExchange(), event.getEndpoint()));
        binder.setCurrentSpan(null);

        if (log.isDebugEnabled()) {
            log.debug("clientResponse[service={}, spanId={}]", serviceName, span != null ? span.getId() : "<null>");
        }
    }

    private void serverRequest(Brave brave, String serviceName, ExchangeCreatedEvent event) {
        ServerSpanThreadBinder binder = brave.serverSpanThreadBinder();
        brave.serverRequestInterceptor().handle(new ZipkinServerRequestAdapter(this, event.getExchange()));
        ServerSpan span = binder.getCurrentServerSpan();
        String key = "CamelZipkinServerSpan-" + serviceName;
        event.getExchange().setProperty(key, span);

        if (log.isDebugEnabled()) {
            log.debug("serverRequest[service={}, spanId={}]", serviceName, span != null ? span.getSpan().getId() : "<null>");
        }
    }

    private void serverResponse(Brave brave, String serviceName, ExchangeCompletedEvent event) {
        ServerSpanThreadBinder binder = brave.serverSpanThreadBinder();
        String key = "CamelZipkinServerSpan-" + serviceName;
        ServerSpan span = event.getExchange().getProperty(key, ServerSpan.class);
        binder.setCurrentSpan(span);
        brave.serverResponseInterceptor().handle(new ZipkinServerResponseAdapter(this, event.getExchange()));
        binder.setCurrentSpan(null);

        if (log.isDebugEnabled()) {
            log.debug("serverResponse[service={}, spanId={}, status=exchangeCompleted]", serviceName, span != null ? span.getSpan().getId() : "<null>");
        }
    }

    private void serverResponse(Brave brave, String serviceName, ExchangeFailedEvent event) {
        ServerSpanThreadBinder binder = brave.serverSpanThreadBinder();
        String key = "CamelZipkinServerSpan-" + serviceName;
        ServerSpan span = event.getExchange().getProperty(key, ServerSpan.class);
        binder.setCurrentSpan(span);
        brave.serverResponseInterceptor().handle(new ZipkinServerResponseAdapter(this, event.getExchange()));
        binder.setCurrentSpan(null);

        if (log.isDebugEnabled()) {
            log.debug("serverResponse[service={}, spanId={}, status=exchangeFailed]", serviceName, span != null ? span.getSpan().getId() : "<null>");
        }
    }

}
