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
package org.apache.camel.component.http4;

import java.io.Closeable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import javax.net.ssl.HostnameVerifier;

import org.apache.camel.Consumer;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.http.common.HttpCommonEndpoint;
import org.apache.camel.http.common.HttpHelper;
import org.apache.camel.http.common.cookie.CookieHandler;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.http.HttpHost;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For calling out to external HTTP servers using Apache HTTP Client 4.x.
 */
@UriEndpoint(firstVersion = "2.3.0", scheme = "http4,https4", title = "HTTP4,HTTPS4", syntax = "http4:httpUri",
    producerOnly = true, label = "http", lenientProperties = true)
public class HttpEndpoint extends HttpCommonEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(HttpEndpoint.class);

    @UriParam(label = "advanced", description = "To use a custom HttpContext instance")
    private HttpContext httpContext;
    @UriParam(label = "advanced", description = "Register a custom configuration strategy for new HttpClient instances"
        + " created by producers or consumers such as to configure authentication mechanisms etc.")
    private HttpClientConfigurer httpClientConfigurer;
    @UriParam(label = "advanced", prefix = "httpClient.", multiValue = true, description = "To configure the HttpClient using the key/values from the Map.")
    private Map<String, Object> httpClientOptions;
    @UriParam(label = "advanced", description = "To use a custom HttpClientConnectionManager to manage connections")
    private HttpClientConnectionManager clientConnectionManager;
    @UriParam(label = "advanced", description = "Provide access to the http client request parameters used on new RequestConfig instances used by producers or consumers of this endpoint.")
    private HttpClientBuilder clientBuilder;
    @UriParam(label = "advanced", description = "Sets a custom HttpClient to be used by the producer")
    private HttpClient httpClient;
    @UriParam(label = "advanced", defaultValue = "false", description = "To use System Properties as fallback for configuration")
    private boolean useSystemProperties;

    @UriParam(label = "producer", description = "To use a custom CookieStore."
        + " By default the BasicCookieStore is used which is an in-memory only cookie store."
        + " Notice if bridgeEndpoint=true then the cookie store is forced to be a noop cookie store as cookie shouldn't be stored as we are just bridging (eg acting as a proxy)."
        + " If a cookieHandler is set then the cookie store is also forced to be a noop cookie store as cookie handling is then performed by the cookieHandler.")
    private CookieStore cookieStore = new BasicCookieStore();
    @UriParam(label = "producer", defaultValue = "true", description = "Whether to clear expired cookies before sending the HTTP request."
        + " This ensures the cookies store does not keep growing by adding new cookies which is newer removed when they are expired.")
    private boolean clearExpiredCookies = true;
    @UriParam(label = "producer", description = "If this option is true, camel-http4 sends preemptive basic authentication to the server.")
    private boolean authenticationPreemptive;
    @UriParam(label = "producer", description = "Whether the HTTP DELETE should include the message body or not."
        + " By default HTTP DELETE do not include any HTTP message. However in some rare cases users may need to be able to include the message body.")
    private boolean deleteWithBody;

    @UriParam(label = "advanced", defaultValue = "200", description = "The maximum number of connections.")
    private int maxTotalConnections;
    @UriParam(label = "advanced", defaultValue = "20", description = "The maximum number of connections per route.")
    private int connectionsPerRoute;
    @UriParam(label = "security", description = "To use a custom X509HostnameVerifier such as DefaultHostnameVerifier or NoopHostnameVerifier")
    private HostnameVerifier x509HostnameVerifier;

    public HttpEndpoint() {
    }

    public HttpEndpoint(String endPointURI, HttpComponent component, URI httpURI) throws URISyntaxException {
        this(endPointURI, component, httpURI, null);
    }

    public HttpEndpoint(String endPointURI, HttpComponent component, URI httpURI, HttpClientConnectionManager clientConnectionManager) throws URISyntaxException {
        this(endPointURI, component, httpURI, HttpClientBuilder.create(), clientConnectionManager, null);
    }

    public HttpEndpoint(String endPointURI, HttpComponent component, HttpClientBuilder clientBuilder,
                        HttpClientConnectionManager clientConnectionManager, HttpClientConfigurer clientConfigurer) throws URISyntaxException {
        this(endPointURI, component, null, clientBuilder, clientConnectionManager, clientConfigurer);
    }

    public HttpEndpoint(String endPointURI, HttpComponent component, URI httpURI, HttpClientBuilder clientBuilder,
                        HttpClientConnectionManager clientConnectionManager, HttpClientConfigurer clientConfigurer) throws URISyntaxException {
        super(endPointURI, component, httpURI);
        this.clientBuilder = clientBuilder;
        this.httpClientConfigurer = clientConfigurer;
        this.clientConnectionManager = clientConnectionManager;
    }

    public Producer createProducer() throws Exception {
        return new HttpProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from http endpoint");
    }

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
    public void setHttpClient(HttpClient httpClient) {
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
            if (ObjectHelper.isNotEmpty(getCamelContext().getProperty("http.proxyHost")) && ObjectHelper.isNotEmpty(getCamelContext().getProperty("http.proxyPort"))) {
                String host = getCamelContext().getProperty("http.proxyHost");
                int port = Integer.parseInt(getCamelContext().getProperty("http.proxyPort"));
                String scheme = getCamelContext().getProperty("http.proxyScheme");
                // fallback and use either http or https depending on secure
                if (scheme == null) {
                    scheme = HttpHelper.isSecureConnection(getEndpointUri()) ? "https" : "http";
                }
                LOG.debug("CamelContext properties http.proxyHost, http.proxyPort, and http.proxyScheme detected. Using http proxy host: {} port: {} scheme: {}", new Object[]{host, port, scheme});
                HttpHost proxy = new HttpHost(host, port, scheme);
                clientBuilder.setProxy(proxy);
            }
        } else {
            clientBuilder.useSystemProperties();
        }

        if (isAuthenticationPreemptive()) {
            // setup the PreemptiveAuthInterceptor here
            clientBuilder.addInterceptorFirst(new PreemptiveAuthInterceptor());
        }

        HttpClientConfigurer configurer = getHttpClientConfigurer();
        if (configurer != null) {
            configurer.configureHttpClient(clientBuilder);
        }

        if (isBridgeEndpoint()) {
            // need to use noop cookiestore as we do not want to keep cookies in memory
            clientBuilder.setDefaultCookieStore(new NoopCookieStore());
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
            clientConnectionManager.shutdown();
        }
        if (httpClient instanceof Closeable) {
            IOHelper.close((Closeable)httpClient);
        }
    }

    // Properties
    //-------------------------------------------------------------------------

    public HttpClientBuilder getClientBuilder() {
        return clientBuilder;
    }

    /**
     * Provide access to the http client request parameters used on new {@link RequestConfig} instances
     * used by producers or consumers of this endpoint.
     */
    public void setClientBuilder(HttpClientBuilder clientBuilder) {
        this.clientBuilder = clientBuilder;
    }

    public HttpClientConfigurer getHttpClientConfigurer() {
        return httpClientConfigurer;
    }

    /**
     * Register a custom configuration strategy for new {@link HttpClient} instances
     * created by producers or consumers such as to configure authentication mechanisms etc
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
     * Whether to clear expired cookies before sending the HTTP request.
     * This ensures the cookies store does not keep growing by adding new cookies which is newer removed when they are expired.
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
     * By default HTTP DELETE do not include any HTTP message. However in some rare cases users may need to be able to include the
     * message body.
     */
    public void setDeleteWithBody(boolean deleteWithBody) {
        this.deleteWithBody = deleteWithBody;
    }

    public CookieStore getCookieStore() {
        return cookieStore;
    }

    /**
     * To use a custom CookieStore.
     * By default the BasicCookieStore is used which is an in-memory only cookie store.
     * Notice if bridgeEndpoint=true then the cookie store is forced to be a noop cookie store as cookie
     * shouldn't be stored as we are just bridging (eg acting as a proxy).
     * If a cookieHandler is set then the cookie store is also forced to be a noop cookie store as cookie handling is
     * then performed by the cookieHandler.
     */
    public void setCookieStore(CookieStore cookieStore) {
        this.cookieStore = cookieStore;
    }

    public void setCookieHandler(CookieHandler cookieHandler) {
        super.setCookieHandler(cookieHandler);
        // if we set an explicit cookie handler
        this.cookieStore = new NoopCookieStore();
    }

    public boolean isAuthenticationPreemptive() {
        return authenticationPreemptive;
    }

    /**
     * If this option is true, camel-http4 sends preemptive basic authentication to the server.
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
     * To use a custom X509HostnameVerifier such as {@link DefaultHostnameVerifier}
     * or {@link org.apache.http.conn.ssl.NoopHostnameVerifier}.
     */
    public void setX509HostnameVerifier(HostnameVerifier x509HostnameVerifier) {
        this.x509HostnameVerifier = x509HostnameVerifier;
    }
}
