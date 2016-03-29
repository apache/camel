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
package org.apache.camel.zipkin;

import java.io.Closeable;
import java.util.EventObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientSpanThreadBinder;
import com.github.kristofa.brave.Sampler;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import com.github.kristofa.brave.SpanCollector;
import com.github.kristofa.brave.scribe.ScribeSpanCollector;
import com.twitter.zipkin.gen.Span;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
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
import org.apache.camel.util.ObjectHelper;
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
 * To match all Camel messages you can use <tt>*</tt> in the pattern and configure that to the same service name.
 * <br/>
 * If no mapping has been configured then Camel will fallback and use endpoint uri's as service names.
 * However its recommended to configure service mappings so you can use human logic names instead of Camel
 * endpoint uris in the names.
 * <p/>
 * Camel will auto-configure a {@link ScribeSpanCollector} if no SpanCollector has explict been configured, and
 * if the hostname and port has been configured as environment variables
 * <ul>
 *     <li>ZIPKIN_SERVICE_HOST - The hostname</li>
 *     <li>ZIPKIN_SERVICE_PORT - The port number</li>
 * </ul>
 */
@ManagedResource(description = "Managing ZipkinEventNotifier")
public class ZipkinEventNotifier extends EventNotifierSupport implements StatefulService, CamelContextAware {

    private CamelContext camelContext;
    private float rate = 1.0f;
    private SpanCollector spanCollector;
    private Map<String, String> serviceMappings = new HashMap<>();
    private Set<String> excludePatterns = new HashSet<>();
    private Map<String, Brave> braves = new HashMap<>();
    private boolean includeMessageBody;
    private boolean useFallbackServiceNames;

    public ZipkinEventNotifier() {
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
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
     * @param serviceName the zipkin service name
     */
    public void addServiceMapping(String pattern, String serviceName) {
        serviceMappings.put(pattern, serviceName);
    }

    public Set<String> getExcludePatterns() {
        return excludePatterns;
    }

    public void setExcludePatterns(Set<String> excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

    /**
     * Adds an exclude pattern that will disable tracing with zipkin for Camel messages that matches the pattern.
     *
     * @param pattern  the pattern such as route id, endpoint url
     */
    public void addExcludePattern(String pattern) {
        excludePatterns.add(pattern);
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

        ObjectHelper.notNull(camelContext, "CamelContext", this);

        if (spanCollector == null) {
            // is there a zipkin service setup as ENV variable to auto register a scribe span collector
            // use the {{service:name}} function that resolves this for us
            String host = camelContext.resolvePropertyPlaceholders("{{service.host:zipkin}}");
            String port = camelContext.resolvePropertyPlaceholders("{{service.port:zipkin}}");
            if (ObjectHelper.isNotEmpty(host) && ObjectHelper.isNotEmpty(port)) {
                log.info("Auto-configuring ZipkinScribeSpanCollector using host: {} and port: {}", host, port);
                int num = camelContext.getTypeConverter().mandatoryConvertTo(Integer.class, port);
                spanCollector = new ScribeSpanCollector(host, num);
            }
        }

        ObjectHelper.notNull(spanCollector, "SpanCollector", this);

        if (serviceMappings.isEmpty()) {
            log.warn("No service name(s) has been configured. Camel will fallback and use endpoint uris as service names.");
            useFallbackServiceNames = true;
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
            // exclude patterns take precedence
            for (String pattern : excludePatterns) {
                if (EndpointHelper.matchPattern(id, pattern)) {
                    return null;
                }
            }
            for (Map.Entry<String, String> entry : serviceMappings.entrySet()) {
                String pattern = entry.getKey();
                if (EndpointHelper.matchPattern(id, pattern)) {
                    answer = entry.getValue();
                    break;
                }
            }
        }

        if (answer == null) {
            id = exchange.getFromRouteId();
            if (id != null) {
                // exclude patterns take precedence
                for (String pattern : excludePatterns) {
                    if (EndpointHelper.matchPattern(id, pattern)) {
                        return null;
                    }
                }
                for (Map.Entry<String, String> entry : serviceMappings.entrySet()) {
                    String pattern = entry.getKey();
                    if (EndpointHelper.matchPattern(id, pattern)) {
                        answer = entry.getValue();
                        break;
                    }
                }
            }
        }

        if (answer == null && endpoint != null) {
            String url = endpoint.getEndpointUri();
            if (url != null) {
                // exclude patterns take precedence
                for (String pattern : excludePatterns) {
                    if (EndpointHelper.matchPattern(url, pattern)) {
                        return null;
                    }
                }
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
                // exclude patterns take precedence
                for (String pattern : excludePatterns) {
                    if (EndpointHelper.matchPattern(url, pattern)) {
                        return null;
                    }
                }
                for (Map.Entry<String, String> entry : serviceMappings.entrySet()) {
                    String pattern = entry.getKey();
                    if (EndpointHelper.matchEndpoint(exchange.getContext(), url, pattern)) {
                        answer = entry.getValue();
                        break;
                    }
                }
            }
        }

        if (answer == null && useFallbackServiceNames) {
            String key = null;
            if (endpoint != null) {
                key = endpoint.getEndpointKey();
            } else if (exchange.getFromEndpoint() != null) {
                key = exchange.getFromEndpoint().getEndpointKey();
            }
            // exclude patterns take precedence
            for (String pattern : excludePatterns) {
                if (EndpointHelper.matchPattern(key, pattern)) {
                    return null;
                }
            }
            if (log.isTraceEnabled() && key != null) {
                log.trace("Using serviceName: {} as fallback", key);
            }
            return key;
        } else {
            if (log.isTraceEnabled() && answer != null) {
                log.trace("Using serviceName: {}", answer);
            }
            return answer;
        }
    }

    private Brave getBrave(String serviceName) {
        Brave brave = null;
        if (serviceName != null) {
            brave = braves.get(serviceName);

            if (brave == null && useFallbackServiceNames) {
                log.debug("Creating Brave assigned to serviceName: {}", serviceName + " as fallback");
                Brave.Builder builder = new Brave.Builder(serviceName);
                builder = builder.traceSampler(Sampler.create(rate));
                if (spanCollector != null) {
                    builder = builder.spanCollector(spanCollector);
                }
                brave = builder.build();
                braves.put(serviceName, brave);
            }
        }

        return brave;
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
            log.debug("clientRequest\t[service={}, spanId={}]", serviceName, span != null ? span.getId() : "<null>");
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
            // one space to align client vs server in the logs
            log.debug("clientResponse\t[service={}, spanId={}]", serviceName, span != null ? span.getId() : "<null>");
        }
    }

    private void serverRequest(Brave brave, String serviceName, ExchangeCreatedEvent event) {
        ServerSpanThreadBinder binder = brave.serverSpanThreadBinder();
        brave.serverRequestInterceptor().handle(new ZipkinServerRequestAdapter(this, event.getExchange()));
        ServerSpan span = binder.getCurrentServerSpan();
        String key = "CamelZipkinServerSpan-" + serviceName;
        event.getExchange().setProperty(key, span);

        if (log.isDebugEnabled()) {
            log.debug("serverRequest\t[service={}, spanId={}]", serviceName, span != null ? span.getSpan().getId() : "<null>");
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
            log.debug("serverResponse\t[service={}, spanId={}]\t[status=exchangeCompleted]", serviceName, span != null ? span.getSpan().getId() : "<null>");
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
            log.debug("serverResponse[service={}, spanId={}]\t[status=exchangeFailed]", serviceName, span != null ? span.getSpan().getId() : "<null>");
        }
    }

}
