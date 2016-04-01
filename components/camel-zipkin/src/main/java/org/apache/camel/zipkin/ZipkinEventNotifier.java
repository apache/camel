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
import org.apache.camel.component.properties.ServiceHostPropertiesFunction;
import org.apache.camel.component.properties.ServicePortPropertiesFunction;
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
 * Camel will auto-configure a {@link ScribeSpanCollector} if no SpanCollector explicit has been configured, and
 * if the hostname and port to the span collector has been configured as environment variables
 * <ul>
 *     <li>ZIPKIN_COLLECTOR_SERVICE_HOST - The hostname</li>
 *     <li>ZIPKIN_COLLECTOR_SERVICE_PORT - The port number</li>
 * </ul>
 */
@ManagedResource(description = "Managing ZipkinEventNotifier")
public class ZipkinEventNotifier extends EventNotifierSupport implements StatefulService, CamelContextAware {

    private final Map<String, Brave> braves = new HashMap<>();
    private transient boolean useFallbackServiceNames;

    private CamelContext camelContext;
    private String hostName;
    private int port;
    private float rate = 1.0f;
    private SpanCollector spanCollector;
    private Map<String, String> clientServiceMappings = new HashMap<>();
    private Map<String, String> serverServiceMappings = new HashMap<>();
    private Set<String> excludePatterns = new HashSet<>();
    private boolean includeMessageBody;

    public ZipkinEventNotifier() {
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getHostName() {
        return hostName;
    }

    /**
     * Sets a hostname for the remote zipkin server to use.
     */
    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public int getPort() {
        return port;
    }

    /**
     * Sets the port number for the remote zipkin server to use.
     */
    public void setPort(int port) {
        this.port = port;
    }

    public float getRate() {
        return rate;
    }

    /**
     * Configures a rate that decides how many events should be traced by zipkin.
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
        return clientServiceMappings.get("*");
    }

    /**
     * To use a global service name that matches all Camel events
     */
    public void setServiceName(String serviceName) {
        clientServiceMappings.put("*", serviceName);
        serverServiceMappings.put("*", serviceName);
    }

    public Map<String, String> getClientServiceMappings() {
        return clientServiceMappings;
    }

    public void setClientServiceMappings(Map<String, String> clientServiceMappings) {
        this.clientServiceMappings = clientServiceMappings;
    }

    /**
     * Adds a client service mapping that matches Camel events to the given zipkin service name.
     * See more details at the class javadoc.
     *
     * @param pattern  the pattern such as route id, endpoint url
     * @param serviceName the zipkin service name
     */
    public void addClientServiceMapping(String pattern, String serviceName) {
        clientServiceMappings.put(pattern, serviceName);
    }

    public Map<String, String> getServerServiceMappings() {
        return serverServiceMappings;
    }

    public void setServerServiceMappings(Map<String, String> serverServiceMappings) {
        this.serverServiceMappings = serverServiceMappings;
    }

    /**
     * Adds a server service mapping that matches Camel events to the given zipkin service name.
     * See more details at the class javadoc.
     *
     * @param pattern  the pattern such as route id, endpoint url
     * @param serviceName the zipkin service name
     */
    public void addServerServiceMapping(String pattern, String serviceName) {
        serverServiceMappings.put(pattern, serviceName);
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
            if (hostName != null && port > 0) {
                log.info("Configuring Zipkin ScribeSpanCollector using host: {} and port: {}", hostName, port);
                spanCollector = new ScribeSpanCollector(hostName, port);
            } else {
                // is there a zipkin service setup as ENV variable to auto register a scribe span collector
                String host = new ServiceHostPropertiesFunction().apply("zipkin-collector");
                String port = new ServicePortPropertiesFunction().apply("zipkin-collector");
                if (ObjectHelper.isNotEmpty(host) && ObjectHelper.isNotEmpty(port)) {
                    log.info("Auto-configuring Zipkin ScribeSpanCollector using host: {} and port: {}", host, port);
                    int num = camelContext.getTypeConverter().mandatoryConvertTo(Integer.class, port);
                    spanCollector = new ScribeSpanCollector(host, num);
                }
            }
        }

        ObjectHelper.notNull(spanCollector, "SpanCollector", this);

        if (clientServiceMappings.isEmpty()) {
            log.warn("No service name(s) has been configured. Camel will fallback and use endpoint uris as service names.");
            useFallbackServiceNames = true;
        }

        // create braves mapped per service name
        for (Map.Entry<String, String> entry : clientServiceMappings.entrySet()) {
            String pattern = entry.getKey();
            String serviceName = entry.getValue();
            createBraveForService(pattern, serviceName);
        }
        for (Map.Entry<String, String> entry : serverServiceMappings.entrySet()) {
            String pattern = entry.getKey();
            String serviceName = entry.getValue();
            createBraveForService(pattern, serviceName);
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

        braves.clear();
    }

    @Override
    public boolean isEnabled(EventObject event) {
        return event instanceof ExchangeSendingEvent
                || event instanceof ExchangeSentEvent
                || event instanceof ExchangeCreatedEvent
                || event instanceof ExchangeCompletedEvent
                || event instanceof ExchangeFailedEvent;
    }

    private String getServiceName(EventObject event, Exchange exchange, Endpoint endpoint, boolean server, boolean client) {
        if (client) {
            return getServiceName(event, exchange, endpoint, clientServiceMappings, server, client);
        } else if (server) {
            return getServiceName(event, exchange, endpoint, serverServiceMappings, server, client);
        } else {
            return null;
        }
    }

    private String getServiceName(EventObject event, Exchange exchange, Endpoint endpoint, Map<String, String> serviceMappings, boolean server, boolean client) {
        String answer = null;

        // endpoint takes precedence over route
        if (endpoint != null) {
            String url = endpoint.getEndpointUri();
            if (url != null) {
                // exclude patterns take precedence
                for (String pattern : excludePatterns) {
                    if (EndpointHelper.matchEndpoint(exchange.getContext(), url, pattern)) {
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

        // special for created event as its a server side to know where it was from
        if (server && (event instanceof ExchangeCreatedEvent || event instanceof ExchangeCompletedEvent || event instanceof ExchangeFailedEvent)) {
            if (answer == null && exchange.getFromEndpoint() != null) {
                String url = exchange.getFromEndpoint().getEndpointUri();
                if (url != null) {
                    // exclude patterns take precedence
                    for (String pattern : excludePatterns) {
                        if (EndpointHelper.matchEndpoint(exchange.getContext(), url, pattern)) {
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
        }

        // route
        if (answer == null) {
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
        }

        if (answer == null) {
            String id = exchange.getFromRouteId();
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

    private void createBraveForService(String pattern, String serviceName) {
        Brave brave = braves.get(pattern);
        if (brave == null && !braves.containsKey(serviceName)) {
            Brave.Builder builder = new Brave.Builder(serviceName);
            builder = builder.traceSampler(Sampler.create(rate));
            if (spanCollector != null) {
                builder = builder.spanCollector(spanCollector);
            }
            brave = builder.build();
            braves.put(serviceName, brave);
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
        // client events
        if (event instanceof ExchangeSendingEvent) {
            ExchangeSendingEvent ese = (ExchangeSendingEvent) event;
            String serviceName = getServiceName(ese, ese.getExchange(), ese.getEndpoint(), false, true);
            Brave brave = getBrave(serviceName);
            if (brave != null) {
                clientRequest(brave, serviceName, ese);
            }
        } else if (event instanceof ExchangeSentEvent) {
            ExchangeSentEvent ese = (ExchangeSentEvent) event;
            String serviceName = getServiceName(ese, ese.getExchange(), ese.getEndpoint(), false, true);
            Brave brave = getBrave(serviceName);
            if (brave != null) {
                clientResponse(brave, serviceName, ese);
            }
        }

        // server received client request
        if (event instanceof ExchangeCreatedEvent) {
            ExchangeCreatedEvent ece = (ExchangeCreatedEvent) event;
            // we should only emit a server request if we have an existing trace id (eg a client has called us)
            if (hasZipkinTraceId(ece.getExchange())) {
                String serviceName = getServiceName(ece, ece.getExchange(), null, true, false);
                Brave brave = getBrave(serviceName);
                if (brave != null) {
                    serverRequest(brave, serviceName, ece);
                }
            }
        }
        // server completed events
        if (event instanceof ExchangeCompletedEvent) {
            ExchangeCompletedEvent ece = (ExchangeCompletedEvent) event;
            String serviceName = getServiceName(ece, ece.getExchange(), null, true, false);
            Brave brave = getBrave(serviceName);
            if (brave != null) {
                serverResponse(brave, serviceName, ece);
            }
        } else if (event instanceof ExchangeFailedEvent) {
            ExchangeFailedEvent efe = (ExchangeFailedEvent) event;
            String serviceName = getServiceName(efe, efe.getExchange(), null, true, false);
            Brave brave = getBrave(serviceName);
            if (brave != null) {
                serverResponse(brave, serviceName, efe);
            }
        }
    }

    private void clientRequest(Brave brave, String serviceName, ExchangeSendingEvent event) {
        ClientSpanThreadBinder clientBinder = brave.clientSpanThreadBinder();
        ServerSpanThreadBinder serverBinder = brave.serverSpanThreadBinder();

        // reuse existing span if we do multiple requests from the same
        ZipkinState state = event.getExchange().getProperty(ZipkinState.KEY, ZipkinState.class);
        if (state == null) {
            state = new ZipkinState();
            event.getExchange().setProperty(ZipkinState.KEY, state);
        }
        // need to store the last span in use whether it was a server or client based span
        Object last = state.getLast();
        if (last != null && last instanceof Span) {
            clientBinder.setCurrentSpan((Span) last);
        } else if (last != null && last instanceof ServerSpan) {
            serverBinder.setCurrentSpan((ServerSpan) last);
        }

        brave.clientRequestInterceptor().handle(new ZipkinClientRequestAdapter(this, serviceName, event.getExchange(), event.getEndpoint()));

        // store span after request
        Span span = clientBinder.getCurrentClientSpan();
        state.pushClientSpan(span);
        // and reset binder
        clientBinder.setCurrentSpan(null);
        serverBinder.setCurrentSpan(null);

        if (log.isDebugEnabled()) {
            String traceId = "<null>";
            if (span != null) {
                traceId = "" + span.getTrace_id();
            }
            String spanId = "<null>";
            if (span != null) {
                spanId = "" + span.getId();
            }
            String parentId = "<null>";
            if (span != null) {
                parentId = "" + span.getParent_id();
            }
            log.debug("clientRequest [service={}, traceId={}, spanId={}, parentId={}]", serviceName, traceId, spanId, parentId);
        }
    }

    private void clientResponse(Brave brave, String serviceName, ExchangeSentEvent event) {
        Span span = null;
        ZipkinState state = event.getExchange().getProperty(ZipkinState.KEY, ZipkinState.class);
        if (state != null) {
            // only process if it was a zipkin client event
            span = state.popClientSpan();
        }

        if (span != null) {
            ClientSpanThreadBinder clientBinder = brave.clientSpanThreadBinder();
            clientBinder.setCurrentSpan(span);
            brave.clientResponseInterceptor().handle(new ZipkinClientResponseAdaptor(this, event.getExchange(), event.getEndpoint()));
            // and reset binder
            clientBinder.setCurrentSpan(null);

            if (log.isDebugEnabled()) {
                String traceId = "" + span.getTrace_id();
                String spanId = "" + span.getId();
                String parentId = "" + span.getParent_id();
                log.debug("clientResponse[service={}, traceId={}, spanId={}, parentId={}]", serviceName, traceId, spanId, parentId);
            }
        }
    }

    private void serverRequest(Brave brave, String serviceName, ExchangeCreatedEvent event) {
        ServerSpanThreadBinder serverBinder = brave.serverSpanThreadBinder();

        // reuse existing span if we do multiple requests from the same
        ZipkinState state = event.getExchange().getProperty(ZipkinState.KEY, ZipkinState.class);
        if (state == null) {
            state = new ZipkinState();
            event.getExchange().setProperty(ZipkinState.KEY, state);
        }
        Object last = state.getLast();
        if (last != null && last instanceof ServerSpan) {
            serverBinder.setCurrentSpan((ServerSpan) last);
        }

        brave.serverRequestInterceptor().handle(new ZipkinServerRequestAdapter(this, event.getExchange()));

        // store span after request
        ServerSpan span = serverBinder.getCurrentServerSpan();
        state.pushServerSpan(span);
        // and reset binder
        serverBinder.setCurrentSpan(null);

        if (log.isDebugEnabled()) {
            String traceId = "<null>";
            if (span.getSpan() != null) {
                traceId = "" + span.getSpan().getTrace_id();
            }
            String spanId = "<null>";
            if (span.getSpan() != null) {
                spanId = "" + span.getSpan().getId();
            }
            String parentId = "<null>";
            if (span.getSpan() != null) {
                parentId = "" + span.getSpan().getParent_id();
            }
            log.debug("serverRequest [service={}, traceId={}, spanId={}, parentId={}]", serviceName, traceId, spanId, parentId);
        }
    }

    private void serverResponse(Brave brave, String serviceName, ExchangeCompletedEvent event) {
        ServerSpan span = null;
        ZipkinState state = event.getExchange().getProperty(ZipkinState.KEY, ZipkinState.class);
        if (state != null) {
            // only process if it was a zipkin server event
            span = state.popServerSpan();
        }

        if (span != null) {
            ServerSpanThreadBinder serverBinder = brave.serverSpanThreadBinder();
            serverBinder.setCurrentSpan(span);
            brave.serverResponseInterceptor().handle(new ZipkinServerResponseAdapter(this, event.getExchange()));
            // and reset binder
            serverBinder.setCurrentSpan(null);

            if (log.isDebugEnabled()) {
                String traceId = "<null>";
                if (span.getSpan() != null) {
                    traceId = "" + span.getSpan().getTrace_id();
                }
                String spanId = "<null>";
                if (span.getSpan() != null) {
                    spanId = "" + span.getSpan().getId();
                }
                String parentId = "<null>";
                if (span.getSpan() != null) {
                    parentId = "" + span.getSpan().getParent_id();
                }
                log.debug("serverResponse[service={}, traceId={}, spanId={}, parentId={}] [status=exchangeCompleted]", serviceName, traceId, spanId, parentId);
            }
        }
    }

    private void serverResponse(Brave brave, String serviceName, ExchangeFailedEvent event) {
        ServerSpan span = null;
        ZipkinState state = event.getExchange().getProperty(ZipkinState.KEY, ZipkinState.class);
        if (state != null) {
            // only process if it was a zipkin server event
            span = state.popServerSpan();
        }

        if (span != null) {
            ServerSpanThreadBinder serverBinder = brave.serverSpanThreadBinder();
            serverBinder.setCurrentSpan(span);
            brave.serverResponseInterceptor().handle(new ZipkinServerResponseAdapter(this, event.getExchange()));
            // and reset binder
            serverBinder.setCurrentSpan(null);

            if (log.isDebugEnabled()) {
                String traceId = "<null>";
                if (span.getSpan() != null) {
                    traceId = "" + span.getSpan().getTrace_id();
                }
                String spanId = "<null>";
                if (span.getSpan() != null) {
                    spanId = "" + span.getSpan().getId();
                }
                String parentId = "<null>";
                if (span.getSpan() != null) {
                    parentId = "" + span.getSpan().getParent_id();
                }
                log.debug("serverResponse[service={}, traceId={}, spanId={}, parentId={}] [status=exchangeFailed]", serviceName, traceId, spanId, parentId);
            }
        }
    }

    private boolean hasZipkinTraceId(Exchange exchange) {
        // must have zipkin headers to start a server event
        return exchange.getIn().getHeader(ZipkinConstants.TRACE_ID) != null;
    }

}
