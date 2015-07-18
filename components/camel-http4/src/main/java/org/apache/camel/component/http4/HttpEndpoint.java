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

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.Consumer;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.http4.helper.HttpHelper;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
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
 * Represents a <a href="http://camel.apache.org/http.html">HTTP endpoint</a>
 *
 * @version 
 */
@UriEndpoint(scheme = "http4,http4s", title = "HTTP4,HTTP4S", syntax = "http4:httpUri", producerOnly = true, label = "http")
public class HttpEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {

    private static final Logger LOG = LoggerFactory.getLogger(HttpEndpoint.class);
    private HttpContext httpContext;
    private HttpComponent component;
    private HttpClientConfigurer httpClientConfigurer;
    private HttpClientConnectionManager clientConnectionManager;
    private HttpClientBuilder clientBuilder;
    private HttpClient httpClient;
    private UrlRewrite urlRewrite;
    private CookieStore cookieStore = new BasicCookieStore();

    @UriPath @Metadata(required = "true", label = "producer")
    private URI httpUri;
    @UriParam
    private HeaderFilterStrategy headerFilterStrategy = new HttpHeaderFilterStrategy();
    @UriParam
    private HttpBinding httpBinding;
    @UriParam(label = "producer", defaultValue = "true")
    private boolean throwExceptionOnFailure = true;
    @UriParam(label = "producer")
    private boolean bridgeEndpoint;
    @UriParam(label = "consumer")
    private boolean matchOnUriPrefix;
    @UriParam(defaultValue = "true")
    private boolean chunked = true;
    @UriParam(label = "consumer")
    private boolean disableStreamCache;
    @UriParam
    private boolean transferException;
    @UriParam(label = "consumer")
    private boolean traceEnabled;
    @UriParam(label = "producer")
    private boolean authenticationPreemptive;
    @UriParam(label = "consumer")
    private String httpMethodRestrict;
    @UriParam(label = "producer", defaultValue = "true")
    private boolean clearExpiredCookies = true;
    @UriParam(label = "producer")
    private boolean ignoreResponseBody;
    @UriParam(label = "producer", defaultValue = "true")
    private boolean copyHeaders = true;
    @UriParam(label = "consumer")
    private boolean eagerCheckContentAvailable;

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
        super(endPointURI, component);
        this.component = component;
        this.httpUri = httpURI;
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

    public void connect(HttpConsumer consumer) throws Exception {
        component.connect(consumer);
    }

    public void disconnect(HttpConsumer consumer) throws Exception {
        component.disconnect(consumer);
    }

    public boolean isLenientProperties() {
        // true to allow dynamic URI options to be configured and passed to external system for eg. the HttpProducer
        return true;
    }

    public boolean isSingleton() {
        return true;
    }
    
    @Override
    protected void doStop() throws Exception {
        if (component != null && component.getClientConnectionManager() != clientConnectionManager) {
            // need to shutdown the ConnectionManager
            clientConnectionManager.shutdown();
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

    public HttpBinding getHttpBinding() {
        if (httpBinding == null) {
            // create a new binding and use the options from this endpoint
            httpBinding = new DefaultHttpBinding();
            httpBinding.setHeaderFilterStrategy(getHeaderFilterStrategy());
            httpBinding.setTransferException(isTransferException());
            httpBinding.setEagerCheckContentAvailable(isEagerCheckContentAvailable());
        }
        return httpBinding;
    }

    /**
     * To use a custom HttpBinding to control the mapping between Camel message and HttpClient.
     */
    public void setHttpBinding(HttpBinding httpBinding) {
        this.httpBinding = httpBinding;
    }

    public void setHttpContext(HttpContext httpContext) {
        this.httpContext = httpContext;
    }

    public String getPath() {
        //if the path is empty, we just return the default path here
        return httpUri.getPath().length() == 0 ? "/" : httpUri.getPath();
    }

    public int getPort() {
        if (httpUri.getPort() == -1) {
            if ("https".equals(getProtocol()) || "https4".equals(getProtocol())) {
                return 443;
            } else {
                return 80;
            }
        }
        return httpUri.getPort();
    }

    public String getProtocol() {
        return httpUri.getScheme();
    }

    public URI getHttpUri() {
        return httpUri;
    }

    /**
     * The url of the HTTP endpoint to call.
     */
    public void setHttpUri(URI httpUri) {
        this.httpUri = httpUri;
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

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
     */
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    /**
     * Option to disable throwing the HttpOperationFailedException in case of failed responses from the remote server.
     * This allows you to get all responses regardless of the HTTP status code.
     */
    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    public boolean isBridgeEndpoint() {
        return bridgeEndpoint;
    }

    /**
     * If the option is true, HttpProducer will ignore the Exchange.HTTP_URI header, and use the endpoint's URI for request.
     * You may also set the option throwExceptionOnFailure to be false to let the HttpProducer send all the fault response back.
     */
    public void setBridgeEndpoint(boolean bridge) {
        this.bridgeEndpoint = bridge;
    }

    public boolean isMatchOnUriPrefix() {
        return matchOnUriPrefix;
    }

    /**
     * Whether or not the consumer should try to find a target consumer by matching the URI prefix if no exact match is found.
     * <p/>
     * See more details at: http://camel.apache.org/how-do-i-let-jetty-match-wildcards.html
     */
    public void setMatchOnUriPrefix(boolean match) {
        this.matchOnUriPrefix = match;
    }
    
    public boolean isDisableStreamCache() {
        return this.disableStreamCache;
    }

    /**
     * Determines whether or not the raw input stream from Jetty is cached or not
     * (Camel will read the stream into a in memory/overflow to file, Stream caching) cache.
     * By default Camel will cache the Jetty input stream to support reading it multiple times to ensure it Camel
     * can retrieve all data from the stream. However you can set this option to true when you for example need
     * to access the raw stream, such as streaming it directly to a file or other persistent store.
     * DefaultHttpBinding will copy the request input stream into a stream cache and put it into message bod
     * if this option is false to support reading the stream multiple times.
     * If you use Jetty to bridge/proxy an endpoint then consider enabling this option to improve performance,
     * in case you do not need to read the message payload multiple times.
     */
    public void setDisableStreamCache(boolean disable) {
        this.disableStreamCache = disable;
    }

    public boolean isChunked() {
        return this.chunked;
    }

    /**
     * If this option is false Jetty servlet will disable the HTTP streaming and set the content-length header on the response
     */
    public void setChunked(boolean chunked) {
        this.chunked = chunked;
    }

    public boolean isTransferException() {
        return transferException;
    }

    /**
     * Option to disable throwing the HttpOperationFailedException in case of failed responses from the remote server.
     * This allows you to get all responses regardless of the HTTP status code.
     */
    public void setTransferException(boolean transferException) {
        this.transferException = transferException;
    }
    
    public boolean isTraceEnabled() {
        return this.traceEnabled;
    }

    /**
     * Specifies whether to enable HTTP TRACE for this Jetty consumer. By default TRACE is turned off.
     */
    public void setTraceEnabled(boolean traceEnabled) {
        this.traceEnabled = traceEnabled;
    }

    public String getHttpMethodRestrict() {
        return httpMethodRestrict;
    }

    /**
     * Used to only allow consuming if the HttpMethod matches, such as GET/POST/PUT etc.
     * Multiple methods can be specified separated by comma.
     */
    public void setHttpMethodRestrict(String httpMethodRestrict) {
        this.httpMethodRestrict = httpMethodRestrict;
    }

    public UrlRewrite getUrlRewrite() {
        return urlRewrite;
    }

    /**
     * Refers to a custom org.apache.camel.component.http.UrlRewrite which allows you to rewrite urls when you bridge/proxy endpoints.
     * See more details at http://camel.apache.org/urlrewrite.html
     */
    public void setUrlRewrite(UrlRewrite urlRewrite) {
        this.urlRewrite = urlRewrite;
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

    public boolean isIgnoreResponseBody() {
        return ignoreResponseBody;
    }

    /**
     * If this option is true, The http producer won't read response body and cached the input stream.
     */
    public void setIgnoreResponseBody(boolean ignoreResponseBody) {
        this.ignoreResponseBody = ignoreResponseBody;
    }

    public boolean isEagerCheckContentAvailable() {
        return eagerCheckContentAvailable;
    }

    /**
     * Whether to eager check whether the HTTP requests has content if the content-length header is 0 or not present.
     * This can be turned on in case HTTP clients do not send streamed data.
     */
    public void setEagerCheckContentAvailable(boolean eagerCheckContentAvailable) {
        this.eagerCheckContentAvailable = eagerCheckContentAvailable;
    }

    /**
     * If this option is true then IN exchange headers will be copied to OUT exchange headers according to copy strategy.
     */
    public boolean isCopyHeaders() {
        return copyHeaders;
    }

    public void setCopyHeaders(boolean copyHeaders) {
        this.copyHeaders = copyHeaders;
    }
}
