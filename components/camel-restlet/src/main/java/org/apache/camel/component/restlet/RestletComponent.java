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
package org.apache.camel.component.restlet;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.component.restlet.converter.RestletConverter;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RestApiConsumerFactory;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.spi.RestProducerFactory;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.HostUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.URISupport;
import org.restlet.Component;
import org.restlet.Restlet;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.Method;
import org.restlet.engine.Engine;
import org.restlet.security.ChallengeAuthenticator;
import org.restlet.security.MapVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Camel component embedded Restlet that produces and consumes exchanges.
 *
 * @version
 */
public class RestletComponent extends DefaultComponent implements RestConsumerFactory, RestApiConsumerFactory, RestProducerFactory, SSLContextParametersAware, HeaderFilterStrategyAware {
    private static final Logger LOG = LoggerFactory.getLogger(RestletComponent.class);
    private static final Object LOCK = new Object();

    private final Map<String, RestletHost> restletHostRegistry = new HashMap<>();
    private final Map<String, MethodBasedRouter> routers = new HashMap<>();
    private final Component component;

    // options that can be set on the restlet server
    @Metadata(label = "consumer,advanced")
    private Boolean controllerDaemon;
    @Metadata(label = "consumer,advanced")
    private Integer controllerSleepTimeMs;
    @Metadata(label = "consumer")
    private Integer inboundBufferSize;
    @Metadata(label = "consumer,advanced")
    private Integer minThreads;
    @Metadata(label = "consumer,advanced")
    private Integer maxThreads;
    @Metadata(label = "consumer,advanced")
    private Integer lowThreads;
    @Metadata(label = "common")
    private Integer maxConnectionsPerHost;
    @Metadata(label = "common")
    private Integer maxTotalConnections;
    @Metadata(label = "consumer")
    private Integer outboundBufferSize;
    @Metadata(label = "consumer,advanced")
    private Integer maxQueued;
    @Metadata(label = "consumer,advanced")
    private Boolean persistingConnections;
    @Metadata(label = "consumer,advanced")
    private Boolean pipeliningConnections;
    @Metadata(label = "consumer,advanced")
    private Integer threadMaxIdleTimeMs;
    @Metadata(label = "consumer")
    private Boolean useForwardedForHeader;
    @Metadata(label = "consumer")
    private Boolean reuseAddress;
    @Metadata(label = "consumer,advanced")
    private boolean disableStreamCache;
    @Metadata(label = "consumer")
    private int port;
    @Metadata(label = "producer")
    private Boolean synchronous;
    @Metadata(label = "advanced")
    private List<String> enabledConverters;
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;
    @Metadata(label = "filter", description = "To use a custom org.apache.camel.spi.HeaderFilterStrategy to filter header to and from Camel message.")
    private HeaderFilterStrategy headerFilterStrategy;

    public RestletComponent() {
        this(new Component());
    }

    public RestletComponent(Component component) {
        // Allow the Component to be injected, so that the RestletServlet may be
        // configured within a webapp
        super();
        this.component = component;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        // grab uri and remove all query parameters as we need to rebuild it a bit special
        String endpointUri = uri;
        if (endpointUri.indexOf('?') > 0) {
            endpointUri = endpointUri.substring(0, endpointUri.indexOf('?'));
        }
        // normalize so the uri is as expected
        endpointUri = URISupport.normalizeUri(endpointUri);

        // decode %7B -> {
        // decode %7D -> }
        endpointUri = endpointUri.replaceAll("%7B", "{").replaceAll("%7D", "}");

        // include restlet methods in the uri (use GET as default)
        String restletMethods = getAndRemoveParameter(parameters, "restletMethods", String.class);
        if (restletMethods != null) {
            endpointUri = endpointUri + "?restletMethods=" + restletMethods.toUpperCase();
        }
        String restletMethod = null;
        if (restletMethods == null) {
            restletMethod = getAndRemoveParameter(parameters, "restletMethod", String.class, "GET");
            endpointUri = endpointUri + "?restletMethod=" + restletMethod.toUpperCase();
        }

        RestletEndpoint result = new RestletEndpoint(this, endpointUri);
        if (synchronous != null) {
            result.setSynchronous(synchronous);
        }
        result.setDisableStreamCache(isDisableStreamCache());
        setEndpointHeaderFilterStrategy(result);
        setProperties(result, parameters);
        if (restletMethods != null) {
            result.setRestletMethods(RestletConverter.toMethods(restletMethods));
        } else {
            result.setRestletMethod(RestletConverter.toMethod(restletMethod));
        }

        // construct URI so we can use it to get the splitted information
        // use raw values to support paths that has spaces
        String remainingRaw = URISupport.extractRemainderPath(new URI(uri), true);
        URI u = new URI(remainingRaw);
        String protocol = u.getScheme();

        String uriPattern = URISupport.createRemainingURI(u, parameters).getRawPath();
        // must decode back to use {} style as that is what the restlet router expect to match in its uri pattern
        // decode %7B -> {
        // decode %7D -> }
        uriPattern = uriPattern.replaceAll("%7B", "{").replaceAll("%7D", "}");

        int port;
        String host = u.getHost();
        if (u.getPort() > 0) {
            port = u.getPort();
        } else {
            port = this.port;
        }

        result.setProtocol(protocol);
        result.setUriPattern(uriPattern);
        result.setHost(host);
        if (port > 0) {
            result.setPort(port);
        }

        if (result.getSslContextParameters() == null) {
            result.setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        // any additional query parameters from parameters then we need to include them as well
        if (!parameters.isEmpty()) {
            result.setQueryParameters(parameters);
            endpointUri = URISupport.appendParametersToURI(endpointUri, parameters);
            result.setCompleteEndpointUri(endpointUri);
        }

        return result;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // configure component options
        RestConfiguration config = getCamelContext().getRestConfiguration("restlet", true);
        // configure additional options on spark configuration
        if (config.getComponentProperties() != null && !config.getComponentProperties().isEmpty()) {
            setProperties(this, config.getComponentProperties());
        }

        cleanupConverters(enabledConverters);

        component.start();
    }

    @Override
    protected void doStop() throws Exception {
        component.stop();
        // component stop will stop servers so we should clear our list as well
        restletHostRegistry.clear();
        // routers map entries are removed as consumer stops and servers map
        // is not touch so to keep in sync with component's servers
        super.doStop();
    }

    @Override
    protected boolean useIntrospectionOnEndpoint() {
        // we invoke setProperties ourselves so we can construct "user" uri on
        // on the remaining parameters
        return false;
    }

    public void connect(RestletConsumer consumer) throws Exception {
        RestletEndpoint endpoint = consumer.getEndpoint();
        addServerIfNecessary(endpoint);

        // if restlet servlet server is created, the offsetPath is set in component context
        // see http://restlet.tigris.org/issues/show_bug.cgi?id=988
        String offsetPath = (String) this.component.getContext()
                .getAttributes().get("org.restlet.ext.servlet.offsetPath");

        if (endpoint.getUriPattern() != null && endpoint.getUriPattern().length() > 0) {
            attachUriPatternToRestlet(offsetPath, endpoint.getUriPattern(), endpoint, consumer.getRestlet());
        }

        if (endpoint.getRestletUriPatterns() != null) {
            for (String uriPattern : endpoint.getRestletUriPatterns()) {
                attachUriPatternToRestlet(offsetPath, uriPattern, endpoint, consumer.getRestlet());
            }
        }
    }

    public void disconnect(RestletConsumer consumer) throws Exception {
        RestletEndpoint endpoint = consumer.getEndpoint();

        List<MethodBasedRouter> routesToRemove = new ArrayList<MethodBasedRouter>();

        String pattern = endpoint.getUriPattern();
        if (pattern != null && !pattern.isEmpty()) {
            MethodBasedRouter methodRouter = getMethodRouter(pattern, false);
            if (methodRouter != null) {
                routesToRemove.add(methodRouter);
            }
        }

        if (endpoint.getRestletUriPatterns() != null) {
            for (String uriPattern : endpoint.getRestletUriPatterns()) {
                MethodBasedRouter methodRouter = getMethodRouter(uriPattern, false);
                if (methodRouter != null) {
                    routesToRemove.add(methodRouter);
                }
            }
        }

        for (MethodBasedRouter router : routesToRemove) {

            List<Method> methods = new ArrayList<>();
            Collections.addAll(methods, Method.OPTIONS);
            if (endpoint.getRestletMethods() != null) {
                Collections.addAll(methods, endpoint.getRestletMethods());
            } else {
                Collections.addAll(methods, endpoint.getRestletMethod());
            }
            for (Method method : methods) {
                router.removeRoute(method);
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Detached restlet uriPattern: {} method: {}", router.getUriPattern(),
                          endpoint.getRestletMethod());
            }

            // remove router if its no longer in use
            if (!router.hasRoutes()) {
                deAttachUriPatternFromRestlet(router.getUriPattern(), endpoint, router);
                if (!router.isStopped()) {
                    router.stop();
                }
                routers.remove(router.getUriPattern());
            }
        }
    }

    private MethodBasedRouter getMethodRouter(String uriPattern, boolean addIfEmpty) {
        synchronized (routers) {
            MethodBasedRouter result = routers.get(uriPattern);
            if (result == null && addIfEmpty) {
                result = new MethodBasedRouter(uriPattern);
                LOG.debug("Added method based router: {}", result);
                routers.put(uriPattern, result);
            }
            return result;
        }
    }

    protected void addServerIfNecessary(RestletEndpoint endpoint) throws Exception {
        String key = buildKey(endpoint);
        RestletHost host;
        synchronized (restletHostRegistry) {
            host = restletHostRegistry.get(key);
            if (host == null) {
                host = createRestletHost();
                host.configure(endpoint, component);

                restletHostRegistry.put(key, host);
                LOG.debug("Added host: {}", key);
                host.start();
            }
        }
    }

    protected RestletHost createRestletHost() {
        RestletHostOptions options = new RestletHostOptions();

        if (getControllerDaemon() != null) {
            options.setControllerDaemon(getControllerDaemon());
        }
        if (getControllerSleepTimeMs() != null) {
            options.setControllerSleepTimeMs(getControllerSleepTimeMs());
        }
        if (getInboundBufferSize() != null) {
            options.setInboundBufferSize(getInboundBufferSize());
        }
        if (getMinThreads() != null) {
            options.setMinThreads(getMinThreads());
        }
        if (getMaxThreads() != null) {
            options.setMaxThreads(getMaxThreads());
        }
        if (getLowThreads() != null) {
            options.setLowThreads(getLowThreads());
        }
        if (getMaxQueued() != null) {
            options.setMaxQueued(getMaxQueued());
        }
        if (getMaxConnectionsPerHost() != null) {
            options.setMaxConnectionsPerHost(getMaxConnectionsPerHost());
        }
        if (getMaxTotalConnections() != null) {
            options.setMaxTotalConnections(getMaxTotalConnections());
        }
        if (getOutboundBufferSize() != null) {
            options.setOutboundBufferSize(getOutboundBufferSize());
        }
        if (getPersistingConnections() != null) {
            options.setPersistingConnections(getPersistingConnections());
        }
        if (getPipeliningConnections() != null) {
            options.setPipeliningConnections(getPipeliningConnections());
        }
        if (getThreadMaxIdleTimeMs() != null) {
            options.setThreadMaxIdleTimeMs(getThreadMaxIdleTimeMs());
        }
        if (getUseForwardedForHeader() != null) {
            options.setUseForwardedForHeader(getUseForwardedForHeader());
        }
        if (getReuseAddress() != null) {
            options.setReuseAddress(getReuseAddress());
        }

        return new DefaultRestletHost(options);
    }

    private static String buildKey(RestletEndpoint endpoint) {
        return endpoint.getHost() + ":" + endpoint.getPort();
    }

    private void attachUriPatternToRestlet(String offsetPath, String uriPattern, RestletEndpoint endpoint, Restlet target) throws Exception {
        MethodBasedRouter router = getMethodRouter(uriPattern, true);

        Map<String, String> realm = endpoint.getRestletRealm();
        if (realm != null && realm.size() > 0) {
            ChallengeAuthenticator guard = new ChallengeAuthenticator(component.getContext()
                .createChildContext(), ChallengeScheme.HTTP_BASIC, "Camel-Restlet Endpoint Realm");
            MapVerifier verifier = new MapVerifier();
            for (Map.Entry<String, String> entry : realm.entrySet()) {
                verifier.getLocalSecrets().put(entry.getKey(), entry.getValue().toCharArray());
            }
            guard.setVerifier(verifier);
            guard.setNext(target);
            target = guard;
            LOG.debug("Target has been set to guard: {}", guard);
        }

        List<Method> methods = new ArrayList<>();
        Collections.addAll(methods, Method.OPTIONS);
        if (endpoint.getRestletMethods() != null) {
            Collections.addAll(methods, endpoint.getRestletMethods());
        } else {
            Collections.addAll(methods, endpoint.getRestletMethod());
        }
        for (Method method : methods) {
            router.addRoute(method, target);
            LOG.debug("Attached restlet uriPattern: {} method: {}", uriPattern, method);
        }

        if (!router.hasBeenAttached()) {
            component.getDefaultHost().attach(
                    offsetPath == null ? uriPattern : offsetPath + uriPattern, router);
            LOG.debug("Attached methodRouter uriPattern: {}", uriPattern);
        }

        if (!router.isStarted()) {
            router.start();
            LOG.debug("Started methodRouter uriPattern: {}", uriPattern);
        }
    }

    private void deAttachUriPatternFromRestlet(String uriPattern, RestletEndpoint endpoint, Restlet target) throws Exception {
        component.getDefaultHost().detach(target);
        LOG.debug("De-attached methodRouter uriPattern: {}", uriPattern);
    }

    public Boolean getControllerDaemon() {
        return controllerDaemon;
    }

    /**
     * Indicates if the controller thread should be a daemon (not blocking JVM exit).
     */
    public void setControllerDaemon(Boolean controllerDaemon) {
        this.controllerDaemon = controllerDaemon;
    }

    public Integer getControllerSleepTimeMs() {
        return controllerSleepTimeMs;
    }
    
    /**
     * Time for the controller thread to sleep between each control.
     */
    public void setControllerSleepTimeMs(Integer controllerSleepTimeMs) {
        this.controllerSleepTimeMs = controllerSleepTimeMs;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return this.headerFilterStrategy;
    }

    /**
     * To use a custom {@link org.apache.camel.spi.HeaderFilterStrategy} to filter header to and from Camel message.
     */
    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        this.headerFilterStrategy = strategy;
    }

    public Integer getInboundBufferSize() {
        return inboundBufferSize;
    }

    /**
     * The size of the buffer when reading messages.
     */
    public void setInboundBufferSize(Integer inboundBufferSize) {
        this.inboundBufferSize = inboundBufferSize;
    }

    public Integer getMaxConnectionsPerHost() {
        return maxConnectionsPerHost;
    }

    /**
     * Maximum number of concurrent connections per host (IP address).
     */
    public void setMaxConnectionsPerHost(Integer maxConnectionsPerHost) {
        this.maxConnectionsPerHost = maxConnectionsPerHost;
    }

    public Integer getMaxThreads() {
        return maxThreads;
    }

    /**
     * Maximum threads that will service requests.
     */
    public void setMaxThreads(Integer maxThreads) {
        this.maxThreads = maxThreads;
    }

    public Integer getLowThreads() {
        return lowThreads;
    }

    /**
     * Number of worker threads determining when the connector is considered overloaded.
     */
    public void setLowThreads(Integer lowThreads) {
        this.lowThreads = lowThreads;
    }

    public Integer getMaxTotalConnections() {
        return maxTotalConnections;
    }

    /**
     * Maximum number of concurrent connections in total.
     */
    public void setMaxTotalConnections(Integer maxTotalConnections) {
        this.maxTotalConnections = maxTotalConnections;
    }

    public Integer getMinThreads() {
        return minThreads;
    }

    /**
     * Minimum threads waiting to service requests.
     */
    public void setMinThreads(Integer minThreads) {
        this.minThreads = minThreads;
    }

    public Integer getOutboundBufferSize() {
        return outboundBufferSize;
    }

    /**
     * The size of the buffer when writing messages.
     */
    public void setOutboundBufferSize(Integer outboundBufferSize) {
        this.outboundBufferSize = outboundBufferSize;
    }

    public Boolean getPersistingConnections() {
        return persistingConnections;
    }

    /**
     * Indicates if connections should be kept alive after a call.
     */
    public void setPersistingConnections(Boolean persistingConnections) {
        this.persistingConnections = persistingConnections;
    }

    public Boolean getPipeliningConnections() {
        return pipeliningConnections;
    }

    /**
     * Indicates if pipelining connections are supported.
     */
    public void setPipeliningConnections(Boolean pipeliningConnections) {
        this.pipeliningConnections = pipeliningConnections;
    }

    public Integer getThreadMaxIdleTimeMs() {
        return threadMaxIdleTimeMs;
    }

    /**
     * Time for an idle thread to wait for an operation before being collected.
     */
    public void setThreadMaxIdleTimeMs(Integer threadMaxIdleTimeMs) {
        this.threadMaxIdleTimeMs = threadMaxIdleTimeMs;
    }

    public Boolean getUseForwardedForHeader() {
        return useForwardedForHeader;
    }

    /**
     * Lookup the "X-Forwarded-For" header supported by popular proxies and caches and uses it to populate the Request.getClientAddresses()
     * method result. This information is only safe for intermediary components within your local network.
     * Other addresses could easily be changed by setting a fake header and should not be trusted for serious security checks.
     */
    public void setUseForwardedForHeader(Boolean useForwardedForHeader) {
        this.useForwardedForHeader = useForwardedForHeader;
    }

    public Boolean getReuseAddress() {
        return reuseAddress;
    }

    /**
     * Enable/disable the SO_REUSEADDR socket option.
     * See java.io.ServerSocket#reuseAddress property for additional details.
     */
    public void setReuseAddress(Boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
    }

    public Integer getMaxQueued() {
        return maxQueued;
    }

    /**
     * Maximum number of calls that can be queued if there aren't any worker thread available to service them.
     * If the value is '0', then no queue is used and calls are rejected if no worker thread is immediately available.
     * If the value is '-1', then an unbounded queue is used and calls are never rejected.
     */
    public void setMaxQueued(Integer maxQueued) {
        this.maxQueued = maxQueued;
    }

    public boolean isDisableStreamCache() {
        return disableStreamCache;
    }

    /**
     * Determines whether or not the raw input stream from Restlet is cached or not
     * (Camel will read the stream into a in memory/overflow to file, Stream caching) cache.
     * By default Camel will cache the Restlet input stream to support reading it multiple times to ensure Camel
     * can retrieve all data from the stream. However you can set this option to true when you for example need
     * to access the raw stream, such as streaming it directly to a file or other persistent store.
     * DefaultRestletBinding will copy the request input stream into a stream cache and put it into message body
     * if this option is false to support reading the stream multiple times.
     */
    public void setDisableStreamCache(boolean disableStreamCache) {
        this.disableStreamCache = disableStreamCache;
    }

    public int getPort() {
        return port;
    }

    /**
     * To configure the port number for the restlet consumer routes.
     * This allows to configure this once to reuse the same port for these consumers.
     */
    public void setPort(int port) {
        this.port = port;
    }

    public Boolean getSynchronous() {
        return synchronous;
    }

    /**
     * Whether to use synchronous Restlet Client for the producer. Setting this option to true can yield faster performance
     * as it seems the Restlet synchronous Client works better.
     */
    public void setSynchronous(Boolean synchronous) {
        this.synchronous = synchronous;
    }

    public List<String> getEnabledConverters() {
        return enabledConverters;
    }

    /**
     * A list of converters to enable as full class name or simple class name.
     * All the converters automatically registered are enabled if empty or null
     */
    public void setEnabledConverters(List<String> enabledConverters) {
        if (enabledConverters != null && !enabledConverters.isEmpty()) {
            this.enabledConverters = new ArrayList(enabledConverters);
        }
    }

    /**
     * A comma separated list of converters to enable as full class name or simple
     * class name. All the converters automatically registered are enabled if
     * empty or null
     */
    public void setEnabledConverters(String enabledConverters) {
        if (ObjectHelper.isNotEmpty(enabledConverters)) {
            this.enabledConverters = Arrays.asList(enabledConverters.split(","));
        }
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return this.useGlobalSslContextParameters;
    }

    /**
     * Enable usage of global SSL context parameters.
     */
    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

    @Override
    public Consumer createConsumer(CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
                                   String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters) throws Exception {

        String path = basePath;
        if (uriTemplate != null) {
            // make sure to avoid double slashes
            if (uriTemplate.startsWith("/")) {
                path = path + uriTemplate;
            } else {
                path = path + "/" + uriTemplate;
            }
        }
        path = FileUtil.stripLeadingSeparator(path);

        String scheme = "http";
        String host = "";
        // use the component's port as the default value
        int port = this.getPort();

        // if no explicit port/host configured, then use port from rest configuration
        RestConfiguration config = configuration;
        if (config == null) {
            config = camelContext.getRestConfiguration("restlet", true);
        }
        if (config.getScheme() != null) {
            scheme = config.getScheme();
        }
        if (config.getHost() != null) {
            host = config.getHost();
        }
        int num = config.getPort();
        if (num > 0) {
            port = num;
        }

        // prefix path with context-path if configured in rest-dsl configuration
        String contextPath = config.getContextPath();
        if (ObjectHelper.isNotEmpty(contextPath)) {
            contextPath = FileUtil.stripTrailingSeparator(contextPath);
            contextPath = FileUtil.stripLeadingSeparator(contextPath);
            if (ObjectHelper.isNotEmpty(contextPath)) {
                path = contextPath + "/" + path;
            }
        }

        // if no explicit hostname set then resolve the hostname
        if (ObjectHelper.isEmpty(host)) {
            if (config.getRestHostNameResolver() == RestConfiguration.RestHostNameResolver.allLocalIp) {
                host = "0.0.0.0";
            } else if (config.getRestHostNameResolver() == RestConfiguration.RestHostNameResolver.localHostName) {
                host = HostUtils.getLocalHostName();
            } else if (config.getRestHostNameResolver() == RestConfiguration.RestHostNameResolver.localIp) {
                host = HostUtils.getLocalIp();
            }
        }

        Map<String, Object> map = new HashMap<String, Object>();
        // build query string, and append any endpoint configuration properties
        if (config.getComponent() == null || config.getComponent().equals("restlet")) {
            // setup endpoint options
            if (config.getEndpointProperties() != null && !config.getEndpointProperties().isEmpty()) {
                map.putAll(config.getEndpointProperties());
            }
        }

        // allow HTTP Options as we want to handle CORS in rest-dsl
        boolean cors = config.isEnableCORS();

        String query = URISupport.createQueryString(map);

        String url;
        // must use upper case for restrict
        String restrict = verb.toUpperCase(Locale.US);
        if (cors) {
            restrict += ",OPTIONS";
        }

        if (port > 0) {
            url = "restlet:%s://%s:%s/%s?restletMethods=%s";
            url = String.format(url, scheme, host, port, path, restrict);
        } else {
            // It could use the restlet servlet transport
            url = "restlet:/%s?restletMethods=%s";
            url = String.format(url, path, restrict);
        }
        if (!query.isEmpty()) {
            url = url + "&" + query;
        }
        // get the endpoint
        RestletEndpoint endpoint = camelContext.getEndpoint(url, RestletEndpoint.class);
        setProperties(camelContext, endpoint, parameters);

        // the endpoint must be started before creating the consumer
        ServiceHelper.startService(endpoint);

        // configure consumer properties
        Consumer consumer = endpoint.createConsumer(processor);
        if (config.getConsumerProperties() != null && !config.getConsumerProperties().isEmpty()) {
            setProperties(camelContext, consumer, config.getConsumerProperties());
        }

        return consumer;
    }

    @Override
    public Consumer createApiConsumer(CamelContext camelContext, Processor processor, String contextPath,
                                      RestConfiguration configuration, Map<String, Object> parameters) throws Exception {
        // reuse the createConsumer method we already have. The api need to use GET and match on uri prefix
        return createConsumer(camelContext, processor, "GET", contextPath, null, null, null, configuration, parameters);
    }

    @Override
    public Producer createProducer(CamelContext camelContext, String host,
                                   String verb, String basePath, String uriTemplate, String queryParameters,
                                   String consumes, String produces, Map<String, Object> parameters) throws Exception {

        // avoid leading slash
        basePath = FileUtil.stripLeadingSeparator(basePath);
        uriTemplate = FileUtil.stripLeadingSeparator(uriTemplate);

        // restlet method must be in upper-case
        String restletMethod = verb.toUpperCase(Locale.US);

        // get the endpoint
        String url = "restlet:" + host;
        if (!ObjectHelper.isEmpty(basePath)) {
            url += "/" + basePath;
        }
        if (!ObjectHelper.isEmpty(uriTemplate)) {
            url += "/" + uriTemplate;
        }
        url += "?restletMethod=" + restletMethod;

        RestletEndpoint endpoint = camelContext.getEndpoint(url, RestletEndpoint.class);
        if (parameters != null && !parameters.isEmpty()) {
            setProperties(camelContext, endpoint, parameters);
        }

        // the endpoint must be started before creating the producer
        ServiceHelper.startService(endpoint);

        return endpoint.createProducer();
    }

    protected static void cleanupConverters(List<String> converters) {
        if (converters != null && !converters.isEmpty()) {
            // To avoid race conditions this operation relies on a global lock, we
            // could have used Engine's instance as lock so we'd lock only operations
            // on the same instance but we do not know how Engine is used by the
            // restlet framework
            synchronized (LOCK) {
                Engine.getInstance().getRegisteredConverters().removeIf(
                    converter ->
                        !converters.contains(converter.getClass().getName())
                        && !converters.contains(converter.getClass().getSimpleName())
                );
            }
        }
    }

    public void setEndpointHeaderFilterStrategy(Endpoint endpoint) {
        if (this.headerFilterStrategy != null && endpoint instanceof HeaderFilterStrategyAware) {
            ((HeaderFilterStrategyAware)endpoint).setHeaderFilterStrategy(this.headerFilterStrategy);
        }
    }
}
