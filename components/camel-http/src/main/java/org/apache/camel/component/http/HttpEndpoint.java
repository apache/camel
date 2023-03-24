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
package org.apache.camel.component.http;

import java.io.Closeable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.http.base.HttpHelper;
import org.apache.camel.http.base.cookie.CookieHandler;
import org.apache.camel.http.common.HttpCommonEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.apache.hc.core5.pool.PoolStats;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Send requests to external HTTP servers using Apache HTTP Client 5.x.
 */
@UriEndpoint(firstVersion = "2.3.0", scheme = "http,https", title = "HTTP,HTTPS", syntax = "http://httpUri",
             producerOnly = true, category = { Category.HTTP }, lenientProperties = true, headersClass = HttpConstants.class)
@Metadata(excludeProperties = "httpBinding,matchOnUriPrefix,chunked,transferException")
@ManagedResource(description = "Managed HttpEndpoint")
public class HttpEndpoint extends HttpCommonEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(HttpEndpoint.class);

    @UriParam(label = "security", description = "To configure security using SSLContextParameters."
                                                + " Important: Only one instance of org.apache.camel.util.jsse.SSLContextParameters is supported per HttpComponent."
                                                + " If you need to use 2 or more different instances, you need to define a new HttpComponent per instance you need.")
    protected SSLContextParameters sslContextParameters;

    @UriParam(label = "advanced", description = "To use a custom HttpContext instance")
    private HttpContext httpContext;
    @UriParam(label = "advanced", description = "Register a custom configuration strategy for new HttpClient instances"
                                                + " created by producers or consumers such as to configure authentication mechanisms etc.")
    private HttpClientConfigurer httpClientConfigurer;
    @UriParam(label = "advanced", prefix = "httpClient.", multiValue = true,
              description = "To configure the HttpClient using the key/values from the Map.")
    private Map<String, Object> httpClientOptions;
    @UriParam(label = "advanced", prefix = "httpConnection.", multiValue = true,
              description = "To configure the connection and the socket using the key/values from the Map.")
    private Map<String, Object> httpConnectionOptions;
    @UriParam(label = "advanced", description = "To use a custom HttpClientConnectionManager to manage connections")
    private HttpClientConnectionManager clientConnectionManager;
    @UriParam(label = "advanced",
              description = "Provide access to the http client request parameters used on new RequestConfig instances used by producers or consumers of this endpoint.")
    private HttpClientBuilder clientBuilder;
    @UriParam(label = "advanced", description = "Sets a custom HttpClient to be used by the producer")
    private HttpClient httpClient;
    @UriParam(label = "advanced", defaultValue = "false",
              description = "To use System Properties as fallback for configuration")
    private boolean useSystemProperties;

    // timeout
    @Metadata(label = "timeout", defaultValue = "3 minutes",
              description = "Returns the connection lease request timeout used when requesting"
                            + " a connection from the connection manager."
                            + " A timeout value of zero is interpreted as a disabled timeout.",
              javaType = "org.apache.hc.core5.util.Timeout")
    private Timeout connectionRequestTimeout = Timeout.ofMinutes(3);
    @Metadata(label = "timeout", defaultValue = "3 minutes",
              description = "Determines the timeout until a new connection is fully established."
                            + " A timeout value of zero is interpreted as an infinite timeout.",
              javaType = "org.apache.hc.core5.util.Timeout")
    private Timeout connectTimeout = Timeout.ofMinutes(3);
    @Metadata(label = "timeout", defaultValue = "3 minutes",
              description = "Determines the default socket timeout value for blocking I/O operations.",
              javaType = "org.apache.hc.core5.util.Timeout")
    private Timeout soTimeout = Timeout.ofMinutes(3);
    @Metadata(label = "timeout", defaultValue = "0",
              description = "Determines the timeout until arrival of a response from the opposite"
                            + " endpoint. A timeout value of zero is interpreted as an infinite timeout."
                            + " Please note that response timeout may be unsupported by HTTP transports "
                            + "with message multiplexing.",
              javaType = "org.apache.hc.core5.util.Timeout")
    private Timeout responseTimeout = Timeout.ofMilliseconds(0);

    @UriParam(label = "producer,advanced", description = "To use a custom CookieStore."
                                                         + " By default the BasicCookieStore is used which is an in-memory only cookie store."
                                                         + " Notice if bridgeEndpoint=true then the cookie store is forced to be a noop cookie store as cookie shouldn't be stored as we are just bridging (eg acting as a proxy)."
                                                         + " If a cookieHandler is set then the cookie store is also forced to be a noop cookie store as cookie handling is then performed by the cookieHandler.")
    private CookieStore cookieStore = new BasicCookieStore();
    @UriParam(label = "producer,advanced", defaultValue = "true",
              description = "Whether to clear expired cookies before sending the HTTP request."
                            + " This ensures the cookies store does not keep growing by adding new cookies which is newer removed when they are expired."
                            + " If the component has disabled cookie management then this option is disabled too.")
    private boolean clearExpiredCookies = true;
    @UriParam(label = "producer,security",
              description = "If this option is true, camel-http sends preemptive basic authentication to the server.")
    private boolean authenticationPreemptive;
    @UriParam(label = "producer,advanced", description = "Whether the HTTP GET should include the message body or not."
                                                         + " By default HTTP GET do not include any HTTP body. However in some rare cases users may need to be able to include the message body.")
    private boolean getWithBody;
    @UriParam(label = "producer,advanced", description = "Whether the HTTP DELETE should include the message body or not."
                                                         + " By default HTTP DELETE do not include any HTTP body. However in some rare cases users may need to be able to include the message body.")
    private boolean deleteWithBody;
    @UriParam(label = "advanced", defaultValue = "200", description = "The maximum number of connections.")
    private int maxTotalConnections;
    @UriParam(label = "advanced", defaultValue = "20", description = "The maximum number of connections per route.")
    private int connectionsPerRoute;
    @UriParam(label = "security",
              description = "To use a custom X509HostnameVerifier such as DefaultHostnameVerifier or NoopHostnameVerifier")
    private HostnameVerifier x509HostnameVerifier;
    @UriParam(label = "producer,advanced", description = "To use custom host header for producer. When not set in query will "
                                                         + "be ignored. When set will override host header derived from url.")
    private String customHostHeader;
    @UriParam(label = "producer,advanced",
              description = "Whether to skip mapping all the Camel headers as HTTP request headers."
                            + " If there are no data from Camel headers needed to be included in the HTTP request then this can avoid"
                            + " parsing overhead with many object allocations for the JVM garbage collector.")
    private boolean skipRequestHeaders;

    @UriParam(label = "producer", defaultValue = "false",
              description = "Whether to the HTTP request should follow redirects."
                            + " By default the HTTP request does not follow redirects ")
    private boolean followRedirects;

    @UriParam(label = "producer,advanced",
              description = "Whether to skip mapping all the HTTP response headers to Camel headers."
                            + " If there are no data needed from HTTP headers then this can avoid parsing overhead"
                            + " with many object allocations for the JVM garbage collector.")
    private boolean skipResponseHeaders;
    @UriParam(label = "producer,advanced", description = "To set a custom HTTP User-Agent request header")
    private String userAgent;

    public HttpEndpoint() {
    }

    public HttpEndpoint(String endPointURI, HttpComponent component, URI httpURI) throws URISyntaxException {
        this(endPointURI, component, httpURI, null);
    }

    public HttpEndpoint(String endPointURI, HttpComponent component, URI httpURI,
                        HttpClientConnectionManager clientConnectionManager) {
        this(endPointURI, component, httpURI, HttpClientBuilder.create(), clientConnectionManager, null);
    }

    public HttpEndpoint(String endPointURI, HttpComponent component, HttpClientBuilder clientBuilder,
                        HttpClientConnectionManager clientConnectionManager,
                        HttpClientConfigurer clientConfigurer) {
        this(endPointURI, component, null, clientBuilder, clientConnectionManager, clientConfigurer);
    }

    public HttpEndpoint(String endPointURI, HttpComponent component, URI httpURI, HttpClientBuilder clientBuilder,
                        HttpClientConnectionManager clientConnectionManager,
                        HttpClientConfigurer clientConfigurer) {
        super(endPointURI, component, httpURI);
        this.clientBuilder = clientBuilder;
        this.httpClientConfigurer = clientConfigurer;
        this.clientConnectionManager = clientConnectionManager;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new HttpProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from http endpoint");
    }

    @Override
    public PollingConsumer createPollingConsumer() throws Exception {
        HttpPollingConsumer answer = new HttpPollingConsumer(this);
        configurePollingConsumer(answer);
        return answer;
    }

    public synchronized HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = createHttpClient();
        }
        return httpClient;
    }

    /**
     * Sets a custom HttpClient to be used by the producer
     */
    public synchronized void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Factory method to create a new {@link HttpClient} instance
     * <p/>
     * Producers and consumers should use the {@link #getHttpClient()} method instead.
     */
    protected HttpClient createHttpClient() {
        ObjectHelper.notNull(clientBuilder, "httpClientBuilder");
        ObjectHelper.notNull(clientConnectionManager, "httpConnectionManager");

        // setup the cookieStore
        clientBuilder.setDefaultCookieStore(cookieStore);
        // setup the httpConnectionManager
        clientBuilder.setConnectionManager(clientConnectionManager);
        if (getComponent() != null && getComponent().getClientConnectionManager() == getClientConnectionManager()) {
            clientBuilder.setConnectionManagerShared(true);
        }

        if (!useSystemProperties) {
            // configure http proxy from camelContext
            if (ObjectHelper.isNotEmpty(getCamelContext().getGlobalOption("http.proxyHost"))
                    && ObjectHelper.isNotEmpty(getCamelContext().getGlobalOption("http.proxyPort"))) {
                String host = getCamelContext().getGlobalOption("http.proxyHost");
                int port = Integer.parseInt(getCamelContext().getGlobalOption("http.proxyPort"));
                String scheme = getCamelContext().getGlobalOption("http.proxyScheme");
                // fallback and use either http or https depending on secure
                if (scheme == null) {
                    scheme = HttpHelper.isSecureConnection(getEndpointUri()) ? "https" : "http";
                }
                LOG.debug(
                        "CamelContext properties http.proxyHost, http.proxyPort, and http.proxyScheme detected. Using http proxy host: {} port: {} scheme: {}",
                        host, port, scheme);
                HttpHost proxy = new HttpHost(scheme, host, port);
                clientBuilder.setProxy(proxy);
            }
        } else {
            clientBuilder.useSystemProperties();
        }

        if (isAuthenticationPreemptive()) {
            // setup the preemptive authentication here
            clientBuilder.addExecInterceptorFirst("preemptive-auth", new PreemptiveAuthExecChainHandler());
        }
        String userAgent = getUserAgent();
        if (userAgent != null) {
            clientBuilder.setUserAgent(userAgent);
        }

        HttpClientConfigurer configurer = getHttpClientConfigurer();
        if (configurer != null) {
            configurer.configureHttpClient(clientBuilder);
        }

        if (isBridgeEndpoint()) {
            // need to use noop cookiestore as we do not want to keep cookies in memory
            clientBuilder.setDefaultCookieStore(new NoopCookieStore());
        }

        if (isFollowRedirects()) {
            clientBuilder.setRedirectStrategy(DefaultRedirectStrategy.INSTANCE);
        }

        LOG.debug("Setup the HttpClientBuilder {}", clientBuilder);
        return clientBuilder.build();
    }

    @Override
    public HttpComponent getComponent() {
        return (HttpComponent) super.getComponent();
    }

    @Override
    protected void doStop() throws Exception {
        if (getComponent() != null && getComponent().getClientConnectionManager() != clientConnectionManager) {
            // need to shutdown the ConnectionManager
            clientConnectionManager.close();
        }
        if (httpClient instanceof Closeable closeable) {
            IOHelper.close(closeable);
        }
    }

    // Properties
    //-------------------------------------------------------------------------

    public HttpClientBuilder getClientBuilder() {
        return clientBuilder;
    }

    /**
     * Provide access to the http client request parameters used on new {@link RequestConfig} instances used by
     * producers or consumers of this endpoint.
     */
    public void setClientBuilder(HttpClientBuilder clientBuilder) {
        this.clientBuilder = clientBuilder;
    }

    public HttpClientConfigurer getHttpClientConfigurer() {
        return httpClientConfigurer;
    }

    /**
     * Register a custom configuration strategy for new {@link HttpClient} instances created by producers or consumers
     * such as to configure authentication mechanisms etc
     */
    public void setHttpClientConfigurer(HttpClientConfigurer httpClientConfigurer) {
        this.httpClientConfigurer = httpClientConfigurer;
    }

    public HttpContext getHttpContext() {
        return httpContext;
    }

    /**
     * To use a custom HttpContext instance
     */
    public void setHttpContext(HttpContext httpContext) {
        this.httpContext = httpContext;
    }

    public HttpClientConnectionManager getClientConnectionManager() {
        return clientConnectionManager;
    }

    /**
     * To use a custom HttpClientConnectionManager to manage connections
     */
    public void setClientConnectionManager(HttpClientConnectionManager clientConnectionManager) {
        this.clientConnectionManager = clientConnectionManager;
    }

    public boolean isClearExpiredCookies() {
        return clearExpiredCookies;
    }

    /**
     * Whether to clear expired cookies before sending the HTTP request. This ensures the cookies store does not keep
     * growing by adding new cookies which is newer removed when they are expired. If the component has disabled cookie
     * management then this option is disabled too.
     */
    public void setClearExpiredCookies(boolean clearExpiredCookies) {
        this.clearExpiredCookies = clearExpiredCookies;
    }

    public boolean isDeleteWithBody() {
        return deleteWithBody;
    }

    /**
     * Whether the HTTP DELETE should include the message body or not.
     * <p/>
     * By default HTTP DELETE do not include any HTTP body. However in some rare cases users may need to be able to
     * include the message body.
     */
    public void setDeleteWithBody(boolean deleteWithBody) {
        this.deleteWithBody = deleteWithBody;
    }

    public boolean isGetWithBody() {
        return getWithBody;
    }

    /**
     * Whether the HTTP GET should include the message body or not.
     * <p/>
     * By default HTTP GET do not include any HTTP body. However in some rare cases users may need to be able to include
     * the message body.
     */
    public void setGetWithBody(boolean getWithBody) {
        this.getWithBody = getWithBody;
    }

    public CookieStore getCookieStore() {
        return cookieStore;
    }

    /**
     * To use a custom CookieStore. By default the BasicCookieStore is used which is an in-memory only cookie store.
     * Notice if bridgeEndpoint=true then the cookie store is forced to be a noop cookie store as cookie shouldn't be
     * stored as we are just bridging (eg acting as a proxy). If a cookieHandler is set then the cookie store is also
     * forced to be a noop cookie store as cookie handling is then performed by the cookieHandler.
     */
    public void setCookieStore(CookieStore cookieStore) {
        this.cookieStore = cookieStore;
    }

    @Override
    public void setCookieHandler(CookieHandler cookieHandler) {
        super.setCookieHandler(cookieHandler);
        // if we set an explicit cookie handler
        this.cookieStore = new NoopCookieStore();
    }

    public boolean isAuthenticationPreemptive() {
        return authenticationPreemptive;
    }

    /**
     * If this option is true, camel-http sends preemptive basic authentication to the server.
     */
    public void setAuthenticationPreemptive(boolean authenticationPreemptive) {
        this.authenticationPreemptive = authenticationPreemptive;
    }

    public Map<String, Object> getHttpClientOptions() {
        return httpClientOptions;
    }

    /**
     * To configure the HttpClient using the key/values from the Map.
     */
    public void setHttpClientOptions(Map<String, Object> httpClientOptions) {
        this.httpClientOptions = httpClientOptions;
    }

    public Map<String, Object> getHttpConnectionOptions() {
        return httpConnectionOptions;
    }

    /**
     * To configure the connection and the socket using the key/values from the Map.
     */
    public void setHttpConnectionOptions(Map<String, Object> httpConnectionOptions) {
        this.httpConnectionOptions = httpConnectionOptions;
    }

    public boolean isUseSystemProperties() {
        return useSystemProperties;
    }

    /**
     * To use System Properties as fallback for configuration
     */
    public void setUseSystemProperties(boolean useSystemProperties) {
        this.useSystemProperties = useSystemProperties;
    }

    public int getMaxTotalConnections() {
        return maxTotalConnections;
    }

    /**
     * The maximum number of connections.
     */
    public void setMaxTotalConnections(int maxTotalConnections) {
        this.maxTotalConnections = maxTotalConnections;
    }

    public int getConnectionsPerRoute() {
        return connectionsPerRoute;
    }

    /**
     * The maximum number of connections per route.
     */
    public void setConnectionsPerRoute(int connectionsPerRoute) {
        this.connectionsPerRoute = connectionsPerRoute;
    }

    public HostnameVerifier getX509HostnameVerifier() {
        return x509HostnameVerifier;
    }

    /**
     * To use a custom X509HostnameVerifier such as {@link org.apache.hc.client5.http.ssl.DefaultHostnameVerifier} or
     * {@link org.apache.hc.client5.http.ssl.NoopHostnameVerifier}.
     */
    public void setX509HostnameVerifier(HostnameVerifier x509HostnameVerifier) {
        this.x509HostnameVerifier = x509HostnameVerifier;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * To configure security using SSLContextParameters. Important: Only one instance of
     * org.apache.camel.util.jsse.SSLContextParameters is supported per HttpComponent. If you need to use 2 or more
     * different instances, you need to define a new HttpComponent per instance you need.
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public Timeout getConnectionRequestTimeout() {
        return connectionRequestTimeout;
    }

    /**
     * Returns the connection lease request timeout used when requesting a connection from the connection manager.
     * <p>
     * A timeout value of zero is interpreted as a disabled timeout.
     * </p>
     * <p>
     * Default: 3 minutes
     * </p>
     */
    public void setConnectionRequestTimeout(Timeout connectionRequestTimeout) {
        this.connectionRequestTimeout = connectionRequestTimeout;
    }

    public Timeout getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Determines the timeout until a new connection is fully established. This may also include transport security
     * negotiation exchanges such as {@code SSL} or {@code TLS} protocol negotiation).
     * <p>
     * A timeout value of zero is interpreted as an infinite timeout.
     * </p>
     * <p>
     * Default: 3 minutes
     * </p>
     */
    public void setConnectTimeout(Timeout connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Timeout getSoTimeout() {
        return soTimeout;
    }

    /**
     * Determines the default socket timeout value for blocking I/O operations.
     * <p>
     * Default: 3 minutes
     * </p>
     */
    public void setSoTimeout(Timeout soTimeout) {
        this.soTimeout = soTimeout;
    }

    public Timeout getResponseTimeout() {
        return responseTimeout;
    }

    /**
     * Determines the timeout until arrival of a response from the opposite endpoint.
     * <p>
     * A timeout value of zero is interpreted as an infinite timeout.
     * </p>
     * <p>
     * Please note that response timeout may be unsupported by HTTP transports with message multiplexing.
     * </p>
     * <p>
     * Default: {@code 0}
     * </p>
     */
    public void setResponseTimeout(Timeout responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    /**
     * Defines a custom host header which will be sent when producing http request.
     * <p>
     * When not set in query will be ignored. When set will override host header derived from url.
     * </p>
     * <p>
     * Default: {@code null}
     * </p>
     */
    public void setCustomHostHeader(String customHostHeader) {
        this.customHostHeader = customHostHeader;
    }

    public String getCustomHostHeader() {
        return customHostHeader;
    }

    public boolean isSkipRequestHeaders() {
        return skipRequestHeaders;
    }

    /**
     * Whether to skip mapping all the Camel headers as HTTP request headers. If there are no data from Camel headers
     * needed to be included in the HTTP request then this can avoid parsing overhead with many object allocations for
     * the JVM garbage collector.
     */
    public void setSkipRequestHeaders(boolean skipRequestHeaders) {
        this.skipRequestHeaders = skipRequestHeaders;
    }

    public boolean isFollowRedirects() {
        return followRedirects;
    }

    /**
     * Whether to the HTTP request should follow redirects. By default the HTTP request does not follow redirects
     */
    public void setFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
    }

    public boolean isSkipResponseHeaders() {
        return skipResponseHeaders;
    }

    /**
     * Whether to skip mapping all the HTTP response headers to Camel headers. If there are no data needed from HTTP
     * headers then this can avoid parsing overhead with many object allocations for the JVM garbage collector.
     */
    public void setSkipResponseHeaders(boolean skipResponseHeaders) {
        this.skipResponseHeaders = skipResponseHeaders;
    }

    public String getUserAgent() {
        return userAgent;
    }

    /**
     * To set a custom HTTP User-Agent request header
     */
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    @ManagedAttribute(description = "Maximum number of allowed persistent connections")
    public int getClientConnectionsPoolStatsMax() {
        ConnPoolControl<?> pool = null;
        if (clientConnectionManager instanceof ConnPoolControl) {
            pool = (ConnPoolControl<?>) clientConnectionManager;
        }
        if (pool != null) {
            PoolStats stats = pool.getTotalStats();
            if (stats != null) {
                return stats.getMax();
            }
        }
        return -1;
    }

    @ManagedAttribute(description = "Number of available idle persistent connections")
    public int getClientConnectionsPoolStatsAvailable() {
        ConnPoolControl<?> pool = null;
        if (clientConnectionManager instanceof ConnPoolControl) {
            pool = (ConnPoolControl<?>) clientConnectionManager;
        }
        if (pool != null) {
            PoolStats stats = pool.getTotalStats();
            if (stats != null) {
                return stats.getAvailable();
            }
        }
        return -1;
    }

    @ManagedAttribute(description = "Number of persistent connections tracked by the connection manager currently being used to execute requests")
    public int getClientConnectionsPoolStatsLeased() {
        ConnPoolControl<?> pool = null;
        if (clientConnectionManager instanceof ConnPoolControl) {
            pool = (ConnPoolControl<?>) clientConnectionManager;
        }
        if (pool != null) {
            PoolStats stats = pool.getTotalStats();
            if (stats != null) {
                return stats.getLeased();
            }
        }
        return -1;
    }

    @ManagedAttribute(description = "Number of connection requests being blocked awaiting a free connection."
                                    + " This can happen only if there are more worker threads contending for fewer connections.")
    public int getClientConnectionsPoolStatsPending() {
        ConnPoolControl<?> pool = null;
        if (clientConnectionManager instanceof ConnPoolControl) {
            pool = (ConnPoolControl<?>) clientConnectionManager;
        }
        if (pool != null) {
            PoolStats stats = pool.getTotalStats();
            if (stats != null) {
                return stats.getPending();
            }
        }
        return -1;
    }

}
