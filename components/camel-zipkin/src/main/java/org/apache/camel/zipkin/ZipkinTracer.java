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
package org.apache.camel.zipkin;

import java.io.Closeable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import brave.Span;
import brave.Tracing;
import brave.context.slf4j.MDCScopeDecorator;
import brave.propagation.B3Propagation;
import brave.propagation.Propagation.Getter;
import brave.propagation.Propagation.Setter;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.Sampler;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.NamedNode;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StaticService;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSendingEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSentEvent;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.libthrift.LibthriftSender;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * To use Zipkin with Camel then setup this {@link ZipkinTracer} in your Camel
 * application.
 * <p/>
 * Events (span) are captured for incoming and outgoing messages being sent
 * to/from Camel. This means you need to configure which which Camel endpoints
 * that maps to zipkin service names. The mapping can be configured using
 * <ul>
 * <li>route id - A Camel route id</li>
 * <li>endpoint url - A Camel endpoint url</li>
 * </ul>
 * For both kinds you can use wildcards and regular expressions to match, which
 * is using the rules from {@link PatternHelper#matchPattern(String, String)}
 * and {@link EndpointHelper#matchEndpoint(CamelContext, String, String)}
 * <p/>
 * To match all Camel messages you can use <tt>*</tt> in the pattern and
 * configure that to the same service name. <br/>
 * If no mapping has been configured then Camel will fallback and use endpoint
 * uri's as service names. However its recommended to configure service mappings
 * so you can use human logic names instead of Camel endpoint uris in the names.
 * <p/>
 * Camel will auto-configure a {@link Reporter span reporter} one hasn't been
 * explicitly configured, and if the hostname and port to a zipkin collector has
 * been configured as environment variables
 * <ul>
 * <li>ZIPKIN_COLLECTOR_HTTP_SERVICE_HOST - The http hostname</li>
 * <li>ZIPKIN_COLLECTOR_HTTP_SERVICE_PORT - The port number</li>
 * </ul>
 * or
 * <ul>
 * <li>ZIPKIN_COLLECTOR_THRIFT_SERVICE_HOST - The Scribe (Thrift RPC)
 * hostname</li>
 * <li>ZIPKIN_COLLECTOR_THRIFT_SERVICE_PORT - The port number</li>
 * </ul>
 * <p/>
 * This class is implemented as both an
 * {@link org.apache.camel.spi.EventNotifier} and {@link RoutePolicy} that
 * allows to trap when Camel starts/ends an {@link Exchange} being routed using
 * the {@link RoutePolicy} and during the routing if the {@link Exchange} sends
 * messages, then we track them using the
 * {@link org.apache.camel.spi.EventNotifier}.
 */
// NOTE: this implementation currently only does explicit propagation, meaning
// that non-camel
// components will not see the current trace context, and therefore will be
// unassociated. This can
// be fixed by using CurrentTraceContext to scope a span where user code is
// invoked.
// If this is desirable, an instance variable of
// CurrentTraceContext.Default.create() could do the
// trick.
@ManagedResource(description = "ZipkinTracer")
public class ZipkinTracer extends ServiceSupport implements RoutePolicyFactory, StaticService, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(ZipkinTracer.class);
    private static final String ZIPKIN_COLLECTOR_HTTP_SERVICE = "zipkin-collector-http";
    private static final String ZIPKIN_COLLECTOR_THRIFT_SERVICE = "zipkin-collector-thrift";
    private static final Getter<Message, String> GETTER = (message, key) -> message.getHeader(key, String.class);
    private static final Setter<Message, String> SETTER = (message, key, value) -> message.setHeader(key, value);
    private static final Extractor<Message> EXTRACTOR = B3Propagation.B3_STRING.extractor(GETTER);
    private static final Injector<Message> INJECTOR = B3Propagation.B3_STRING.injector(SETTER);

    private final ZipkinEventNotifier eventNotifier = new ZipkinEventNotifier();
    private final Map<String, Tracing> braves = new HashMap<>();
    private transient boolean useFallbackServiceNames;

    private CamelContext camelContext;
    private String endpoint;
    private String hostName;
    private int port;
    private float rate = 1.0f;
    private Reporter<zipkin2.Span> spanReporter;
    private Map<String, String> clientServiceMappings = new HashMap<>();
    private Map<String, String> serverServiceMappings = new HashMap<>();
    private Set<String> excludePatterns = new HashSet<>();
    private boolean includeMessageBody;
    private boolean includeMessageBodyStreams;

    public ZipkinTracer() {
    }

    @Override
    public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, NamedNode route) {
        // ensure this zipkin tracer gets initialized when Camel starts
        init(camelContext);
        return new ZipkinRoutePolicy();
    }

    /**
     * Registers this {@link ZipkinTracer} on the {@link CamelContext} if not
     * already registered.
     */
    public void init(CamelContext camelContext) {
        if (!camelContext.hasService(this)) {
            try {
                // start this service eager so we init before Camel is starting
                // up
                camelContext.addService(this, true, true);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @ManagedAttribute(description = "The POST URL for zipkin's v2 api.")
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * Sets the POST URL for zipkin's
     * <a href="http://zipkin.io/zipkin-api/#/">v2 api</a>, usually
     * "http://zipkinhost:9411/api/v2/spans"
     */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    @ManagedAttribute(description = "The hostname for the remote zipkin scribe collector.")
    public String getHostName() {
        return hostName;
    }

    /**
     * Sets the hostname for the remote zipkin scribe collector.
     */
    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    @ManagedAttribute(description = "The port number for the remote zipkin scribe collector.")
    public int getPort() {
        return port;
    }

    /**
     * Sets the port number for the remote zipkin scribe collector.
     */
    public void setPort(int port) {
        this.port = port;
    }

    @ManagedAttribute(description = "Rates how many events should be traced by zipkin. The rate is expressed as a percentage (1.0f = 100%, 0.5f is 50%, 0.1f is 10%).")
    public float getRate() {
        return rate;
    }

    /**
     * Configures a rate that decides how many events should be traced by
     * zipkin. The rate is expressed as a percentage (1.0f = 100%, 0.5f is 50%,
     * 0.1f is 10%).
     *
     * @param rate minimum sample rate is 0.0001, or 0.01% of traces
     */
    public void setRate(float rate) {
        this.rate = rate;
    }

    /**
     * Sets the reporter used to send timing data (spans) to the zipkin server.
     */
    public void setSpanReporter(Reporter<zipkin2.Span> spanReporter) {
        this.spanReporter = spanReporter;
    }

    /**
     * Returns the reporter used to send timing data (spans) to the zipkin
     * server.
     */
    public Reporter<zipkin2.Span> getSpanReporter() {
        return spanReporter;
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
     * Adds a client service mapping that matches Camel events to the given
     * zipkin service name. See more details at the class javadoc.
     *
     * @param pattern the pattern such as route id, endpoint url
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
     * Adds a server service mapping that matches Camel events to the given
     * zipkin service name. See more details at the class javadoc.
     *
     * @param pattern the pattern such as route id, endpoint url
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
     * Adds an exclude pattern that will disable tracing with zipkin for Camel
     * messages that matches the pattern.
     *
     * @param pattern the pattern such as route id, endpoint url
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
     * This is not recommended for production usage, or when having big
     * payloads. You can limit the size by configuring the <a href=
     * "http://camel.apache.org/how-do-i-set-the-max-chars-when-debug-logging-messages-in-camel.html">max
     * debug log size</a>.
     * <p/>
     * By default message bodies that are stream based are <b>not</b> included.
     * You can use the option {@link #setIncludeMessageBodyStreams(boolean)} to
     * turn that on.
     */
    @ManagedAttribute(description = "Whether to include the Camel message body in the zipkin traces")
    public void setIncludeMessageBody(boolean includeMessageBody) {
        this.includeMessageBody = includeMessageBody;
    }

    @ManagedAttribute(description = "Whether to include stream based Camel message bodies in the zipkin traces")
    public boolean isIncludeMessageBodyStreams() {
        return includeMessageBodyStreams;
    }

    /**
     * Whether to include message bodies that are stream based in the zipkin
     * traces.
     * <p/>
     * This requires enabling
     * <a href="http://camel.apache.org/stream-caching.html">stream caching</a>
     * on the routes or globally on the CamelContext.
     * <p/>
     * This is not recommended for production usage, or when having big
     * payloads. You can limit the size by configuring the <a href=
     * "http://camel.apache.org/how-do-i-set-the-max-chars-when-debug-logging-messages-in-camel.html">max
     * debug log size</a>.
     */
    @ManagedAttribute(description = "Whether to include stream based Camel message bodies in the zipkin traces")
    public void setIncludeMessageBodyStreams(boolean includeMessageBodyStreams) {
        this.includeMessageBodyStreams = includeMessageBodyStreams;
    }

    @Override
    protected void doInit() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext", this);

        camelContext.getManagementStrategy().addEventNotifier(eventNotifier);
        if (!camelContext.getRoutePolicyFactories().contains(this)) {
            camelContext.addRoutePolicyFactory(this);
        }

        if (spanReporter == null) {
            if (endpoint != null) {
                LOG.info("Configuring Zipkin URLConnectionSender using endpoint: {}", endpoint);
                spanReporter = AsyncReporter.create(URLConnectionSender.create(endpoint));
            } else if (hostName != null && port > 0) {
                LOG.info("Configuring Zipkin ScribeSpanCollector using host: {} and port: {}", hostName, port);
                LibthriftSender sender = LibthriftSender.newBuilder().host(hostName).port(port).build();
                spanReporter = AsyncReporter.create(sender);
            } else {
                // is there a zipkin service setup as ENV variable to auto
                // register a span reporter
                String host = ServiceHostFunction.apply(ZIPKIN_COLLECTOR_HTTP_SERVICE);
                String port = ServicePortFunction.apply(ZIPKIN_COLLECTOR_HTTP_SERVICE);
                if (ObjectHelper.isNotEmpty(host) && ObjectHelper.isNotEmpty(port)) {
                    LOG.info("Auto-configuring Zipkin URLConnectionSender using host: {} and port: {}", host, port);
                    int num = camelContext.getTypeConverter().mandatoryConvertTo(Integer.class, port);
                    String implicitEndpoint = "http://" + host + ":" + num + "/api/v2/spans";
                    spanReporter = AsyncReporter.create(URLConnectionSender.create(implicitEndpoint));
                } else {
                    host = ServiceHostFunction.apply(ZIPKIN_COLLECTOR_THRIFT_SERVICE);
                    port = ServicePortFunction.apply(ZIPKIN_COLLECTOR_THRIFT_SERVICE);
                    if (ObjectHelper.isNotEmpty(host) && ObjectHelper.isNotEmpty(port)) {
                        LOG.info("Auto-configuring Zipkin ScribeSpanCollector using host: {} and port: {}", host, port);
                        int num = camelContext.getTypeConverter().mandatoryConvertTo(Integer.class, port);
                        LibthriftSender sender = LibthriftSender.newBuilder().host(host).port(num).build();
                        spanReporter = AsyncReporter.create(sender);
                    }
                }
            }
        }

        if (spanReporter == null) {
            // Try to lookup the span reporter from the registry if only one
            // instance is present
            Set<Reporter> reporters = camelContext.getRegistry().findByType(Reporter.class);
            if (reporters.size() == 1) {
                spanReporter = reporters.iterator().next();
            }
        }

        ObjectHelper.notNull(spanReporter, "Reporter<zipkin2.Span>", this);

        if (clientServiceMappings.isEmpty() && serverServiceMappings.isEmpty()) {
            LOG.warn("No service name(s) has been mapped in clientServiceMappings or serverServiceMappings. Camel will fallback and use endpoint uris as service names.");
            useFallbackServiceNames = true;
        }

        // create braves mapped per service name
        for (Map.Entry<String, String> entry : clientServiceMappings.entrySet()) {
            String pattern = entry.getKey();
            String serviceName = entry.getValue();
            createTracingForService(pattern, serviceName);
        }
        for (Map.Entry<String, String> entry : serverServiceMappings.entrySet()) {
            String pattern = entry.getKey();
            String serviceName = entry.getValue();
            createTracingForService(pattern, serviceName);
        }

        ServiceHelper.startService(spanReporter, eventNotifier);
    }

    @Override
    protected void doShutdown() throws Exception {
        // stop event notifier
        camelContext.getManagementStrategy().removeEventNotifier(eventNotifier);
        ServiceHelper.stopService(eventNotifier);

        // stop and close collector
        ServiceHelper.stopAndShutdownService(spanReporter);
        if (spanReporter instanceof Closeable) {
            IOHelper.close((Closeable)spanReporter);
        }
        // clear braves
        braves.clear();
        // remove route policy
        camelContext.getRoutePolicyFactories().remove(this);
    }

    private String getServiceName(Exchange exchange, Endpoint endpoint, boolean server, boolean client) {
        if (client) {
            return getServiceName(exchange, endpoint, clientServiceMappings);
        } else if (server) {
            return getServiceName(exchange, endpoint, serverServiceMappings);
        } else {
            return null;
        }
    }

    private String getServiceName(Exchange exchange, Endpoint endpoint, Map<String, String> serviceMappings) {
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

        // route
        if (answer == null) {
            String id = routeIdExpression().evaluate(exchange, String.class);
            if (id != null) {
                // exclude patterns take precedence
                for (String pattern : excludePatterns) {
                    if (PatternHelper.matchPattern(id, pattern)) {
                        return null;
                    }
                }
                for (Map.Entry<String, String> entry : serviceMappings.entrySet()) {
                    String pattern = entry.getKey();
                    if (PatternHelper.matchPattern(id, pattern)) {
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
                    if (PatternHelper.matchPattern(id, pattern)) {
                        return null;
                    }
                }
                for (Map.Entry<String, String> entry : serviceMappings.entrySet()) {
                    String pattern = entry.getKey();
                    if (PatternHelper.matchPattern(id, pattern)) {
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
                if (PatternHelper.matchPattern(key, pattern)) {
                    return null;
                }
            }
            String sanitizedKey = URISupport.sanitizeUri(key);
            if (LOG.isTraceEnabled() && sanitizedKey != null) {
                LOG.trace("Using serviceName: {} as fallback", sanitizedKey);
            }
            return sanitizedKey;
        } else {
            if (LOG.isTraceEnabled() && answer != null) {
                LOG.trace("Using serviceName: {}", answer);
            }
            return answer;
        }
    }

    private void createTracingForService(String pattern, String serviceName) {
        Tracing brave = braves.get(pattern);
        if (brave == null && !braves.containsKey(serviceName)) {
            brave = newTracing(serviceName);
            braves.put(serviceName, brave);
        }
    }

    private Tracing newTracing(String serviceName) {
        Tracing brave = null;
        if (camelContext.isUseMDCLogging()) {
            brave = Tracing.newBuilder().currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder().addScopeDecorator(MDCScopeDecorator.create()).build())
                    .localServiceName(serviceName).sampler(Sampler.create(rate)).spanReporter(spanReporter).build();
        } else {
            brave = Tracing.newBuilder().localServiceName(serviceName).sampler(Sampler.create(rate)).spanReporter(spanReporter).build();
        }
        return brave;
    }

    private Tracing getTracing(String serviceName) {
        Tracing brave = null;
        if (serviceName != null) {
            brave = braves.get(serviceName);

            if (brave == null && useFallbackServiceNames) {
                LOG.debug("Creating Tracing assigned to serviceName: {}", serviceName + " as fallback");
                brave = newTracing(serviceName);
                braves.put(serviceName, brave);
            }
        }

        return brave;
    }

    private void clientRequest(Tracing brave, String serviceName, ExchangeSendingEvent event) {
        // reuse existing span if we do multiple requests from the same
        ZipkinState state = event.getExchange().getProperty(ZipkinState.KEY, ZipkinState.class);
        if (state == null) {
            state = new ZipkinState();
            event.getExchange().setProperty(ZipkinState.KEY, state);
        }
        // if we started from a server span then lets reuse that when we call a
        // downstream service
        Span last = state.peekServerSpan();
        Span span;
        if (last != null) {
            span = brave.tracer().newChild(last.context());
        } else {
            span = brave.tracer().nextSpan();
        }
        span.kind(Span.Kind.CLIENT).start();

        ZipkinClientRequestAdapter parser = new ZipkinClientRequestAdapter(this, event.getEndpoint());
        INJECTOR.inject(span.context(), event.getExchange().getIn());
        parser.onRequest(event.getExchange(), span.customizer());

        // store span after request
        state.pushClientSpan(span);
        TraceContext context = span.context();
        String traceId = "" + context.traceIdString();
        String spanId = "" + context.spanId();
        String parentId = context.parentId() != null ? "" + context.parentId() : null;
        if (camelContext.isUseMDCLogging()) {
            MDC.put("traceId", traceId);
            MDC.put("spanId", spanId);
            MDC.put("parentId", parentId);
        }
        if (LOG.isDebugEnabled()) {
            if (parentId != null) {
                LOG.debug(String.format("clientRequest [service=%s, traceId=%20s, spanId=%20s, parentId=%20s]", serviceName, traceId, spanId, parentId));
            } else {
                LOG.debug(String.format("clientRequest [service=%s, traceId=%20s, spanId=%20s]", serviceName, traceId, spanId));
            }
        }
    }

    private void clientResponse(Tracing brave, String serviceName, ExchangeSentEvent event) {
        Span span = null;
        ZipkinState state = event.getExchange().getProperty(ZipkinState.KEY, ZipkinState.class);
        if (state != null) {
            // only process if it was a zipkin client event
            span = state.popClientSpan();
        }

        if (span != null) {
            ZipkinClientResponseAdaptor parser = new ZipkinClientResponseAdaptor(this, event.getEndpoint());
            parser.onResponse(event.getExchange(), span.customizer());
            span.finish();
            TraceContext context = span.context();
            String traceId = "" + context.traceIdString();
            String spanId = "" + context.spanId();
            String parentId = context.parentId() != null ? "" + context.parentId() : null;
            if (camelContext.isUseMDCLogging()) {
                MDC.put("traceId", traceId);
                MDC.put("spanId", spanId);
                MDC.put("parentId", parentId);
            }
            if (LOG.isDebugEnabled()) {
                if (parentId != null) {
                    LOG.debug(String.format("clientResponse[service=%s, traceId=%20s, spanId=%20s, parentId=%20s]", serviceName, traceId, spanId, parentId));
                } else {
                    LOG.debug(String.format("clientResponse[service=%s, traceId=%20s, spanId=%20s]", serviceName, traceId, spanId));
                }
            }
        }
    }

    private Span serverRequest(Tracing brave, String serviceName, Exchange exchange) {
        // reuse existing span if we do multiple requests from the same
        ZipkinState state = exchange.getProperty(ZipkinState.KEY, ZipkinState.class);
        if (state == null) {
            state = new ZipkinState();
            exchange.setProperty(ZipkinState.KEY, state);
        }
        Span span = null;
        TraceContextOrSamplingFlags sampleFlag = EXTRACTOR.extract(exchange.getIn());
        if (ObjectHelper.isEmpty(sampleFlag)) {
            span = brave.tracer().nextSpan();
            INJECTOR.inject(span.context(), exchange.getIn());
        } else {
            span = brave.tracer().nextSpan(sampleFlag);
        }
        span.kind(Span.Kind.SERVER).start();
        ZipkinServerRequestAdapter parser = new ZipkinServerRequestAdapter(this, exchange);
        parser.onRequest(exchange, span.customizer());

        // store span after request
        state.pushServerSpan(span);
        TraceContext context = span.context();
        String traceId = "" + context.traceIdString();
        String spanId = "" + context.spanId();
        String parentId = context.parentId() != null ? "" + context.parentId() : null;
        if (camelContext.isUseMDCLogging()) {
            MDC.put("traceId", traceId);
            MDC.put("spanId", spanId);
            MDC.put("parentId", parentId);
        }
        if (LOG.isDebugEnabled()) {
            if (parentId != null) {
                LOG.debug(String.format("serverRequest [service=%s, traceId=%20s, spanId=%20s, parentId=%20s]", serviceName, traceId, spanId, parentId));
            } else {
                LOG.debug(String.format("serverRequest [service=%s, traceId=%20s, spanId=%20s]", serviceName, traceId, spanId));
            }
        }

        return span;
    }

    private void serverResponse(Tracing brave, String serviceName, Exchange exchange) {
        Span span = null;
        ZipkinState state = exchange.getProperty(ZipkinState.KEY, ZipkinState.class);
        if (state != null) {
            // only process if it was a zipkin server event
            span = state.popServerSpan();
        }

        if (span != null) {
            ZipkinServerResponseAdapter parser = new ZipkinServerResponseAdapter(this, exchange);
            parser.onResponse(exchange, span.customizer());
            span.finish();
            TraceContext context = span.context();
            String traceId = "" + context.traceIdString();
            String spanId = "" + context.spanId();
            String parentId = context.parentId() != null ? "" + context.parentId() : null;
            if (camelContext.isUseMDCLogging()) {
                MDC.put("traceId", traceId);
                MDC.put("spanId", spanId);
                MDC.put("parentId", parentId);
            }
            if (LOG.isDebugEnabled()) {
                if (parentId != null) {
                    LOG.debug(String.format("serverResponse[service=%s, traceId=%20s, spanId=%20s, parentId=%20s]", serviceName, traceId, spanId, parentId));
                } else {
                    LOG.debug(String.format("serverResponse[service=%s, traceId=%20s, spanId=%20s]", serviceName, traceId, spanId));
                }
            }
        }
    }

    private final class ZipkinEventNotifier extends EventNotifierSupport {

        @Override
        public void notify(CamelEvent event) throws Exception {
            // use event notifier to track events when Camel messages to
            // endpoints
            // these events corresponds to Zipkin client events

            // client events
            if (event instanceof ExchangeSendingEvent) {
                ExchangeSendingEvent ese = (ExchangeSendingEvent)event;
                String serviceName = getServiceName(ese.getExchange(), ese.getEndpoint(), false, true);
                Tracing brave = getTracing(serviceName);
                if (brave != null) {
                    clientRequest(brave, serviceName, ese);
                }
            } else if (event instanceof ExchangeSentEvent) {
                ExchangeSentEvent ese = (ExchangeSentEvent)event;
                String serviceName = getServiceName(ese.getExchange(), ese.getEndpoint(), false, true);
                Tracing brave = getTracing(serviceName);
                if (brave != null) {
                    clientResponse(brave, serviceName, ese);
                }
            }
        }

        @Override
        public boolean isEnabled(CamelEvent event) {
            switch (event.getType()) {
                case ExchangeSending:
                case ExchangeSent:
                case ExchangeCreated:
                case ExchangeCompleted:
                case ExchangeFailed:
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public String toString() {
            return "ZipkinEventNotifier";
        }
    }

    private final class ZipkinRoutePolicy extends RoutePolicySupport {

        @Override
        public void onExchangeBegin(Route route, Exchange exchange) {
            // use route policy to track events when Camel a Camel route
            // begins/end the lifecycle of an Exchange
            // these events corresponds to Zipkin server events

            String serviceName = getServiceName(exchange, route.getEndpoint(), true, false);
            Tracing brave = getTracing(serviceName);
            if (brave != null) {
                serverRequest(brave, serviceName, exchange);
            }

        }

        // Report Server send after route has completed processing of the
        // exchange.
        @Override
        public void onExchangeDone(Route route, Exchange exchange) {
            String serviceName = getServiceName(exchange, route.getEndpoint(), true, false);
            Tracing brave = getTracing(serviceName);
            if (brave != null) {
                serverResponse(brave, serviceName, exchange);
            }
        }
    }

    private static Expression routeIdExpression() {
        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                String answer = ExchangeHelper.getRouteId(exchange);
                return type.cast(answer);
            }
        };
    }

}
