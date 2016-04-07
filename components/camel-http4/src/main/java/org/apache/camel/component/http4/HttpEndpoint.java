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

import org.apache.camel.Consumer;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.http.common.HttpCommonEndpoint;
import org.apache.camel.http.common.HttpHelper;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.http.HttpHost;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For calling out to external HTTP servers using Apache HTTP Client 4.x.
 */
@UriEndpoint(scheme = "http4,http4s", title = "HTTP4,HTTP4S", syntax = "http4:httpUri", producerOnly = true, label = "http", lenientProperties = true)
public class HttpEndpoint extends HttpCommonEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(HttpEndpoint.class);

    @UriParam(label = "advanced")
    private HttpContext httpContext;
    @UriParam(label = "advanced")
    private HttpClientConfigurer httpClientConfigurer;
    @UriParam(label = "advanced", prefix = "httpClient.", multiValue = true)
    private Map<String, Object> httpClientOptions;
    @UriParam(label = "advanced")
    private HttpClientConnectionManager clientConnectionManager;
    @UriParam(label = "advanced")
    private HttpClientBuilder clientBuilder;
    @UriParam(label = "advanced")
    private HttpClient httpClient;
    @UriParam(label = "advanced", defaultValue = "false")
    private boolean useSystemProperties;

    @UriParam(label = "producer")
    private CookieStore cookieStore = new BasicCookieStore();
    @UriParam(label = "producer")
    private boolean authenticationPreemptive;
    @UriParam(label = "producer", defaultValue = "true")
    private boolean clearExpiredCookies = true;

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

    /**
     * Gets the HttpClient to be used by {@link org.apache.camel.component.http4.HttpProducer}
     */
    public synchronized HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = createHttpClient();
        }
        return httpClient;
    }

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
        if (httpClient != null && httpClient instanceof Closeable) {
            IOHelper.close((Closeable)httpClient);
        }
    }

    // Properties
    //-------------------------------------------------------------------------

    /**
     * Provide access to the http client request parameters used on new {@link RequestConfig} instances
     * used by producers or consumers of this endpoint.
     */
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
    
    public HttpContext getHttpContext() {
        return httpContext;
    }

    /**
     * Register a custom configuration strategy for new {@link HttpClient} instances
     * created by producers or consumers such as to configure authentication mechanisms etc
     */
    public void setHttpClientConfigurer(HttpClientConfigurer httpClientConfigurer) {
        this.httpClientConfigurer = httpClientConfigurer;
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

    public CookieStore getCookieStore() {
        return cookieStore;
    }

    /**
     * To use a custom org.apache.http.client.CookieStore.
     * By default the org.apache.http.impl.client.BasicCookieStore is used which is an in-memory only cookie store.
     * Notice if bridgeEndpoint=true then the cookie store is forced to be a noop cookie store as cookie
     * shouldn't be stored as we are just bridging (eg acting as a proxy).
     */
    public void setCookieStore(CookieStore cookieStore) {
        this.cookieStore = cookieStore;
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
}
