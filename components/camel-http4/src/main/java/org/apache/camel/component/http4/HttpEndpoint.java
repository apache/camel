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

import org.apache.camel.PollingConsumer;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.http4.helper.HttpHelper;
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.util.ObjectHelper;
import org.apache.http.HttpHost;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a <a href="http://camel.apache.org/http.html">HTTP endpoint</a>
 *
 * @version 
 */
public class HttpEndpoint extends DefaultPollingEndpoint implements HeaderFilterStrategyAware {

    private static final Logger LOG = LoggerFactory.getLogger(HttpEndpoint.class);
    private HeaderFilterStrategy headerFilterStrategy = new HttpHeaderFilterStrategy();
    private HttpBinding binding;
    private HttpContext httpContext;
    private HttpComponent component;
    private URI httpUri;
    private HttpParams clientParams;
    private HttpClientConfigurer httpClientConfigurer;
    private ClientConnectionManager clientConnectionManager;
    private HttpClient httpClient;
    private boolean throwExceptionOnFailure = true;
    private boolean bridgeEndpoint;
    private boolean matchOnUriPrefix;
    private boolean chunked = true;
    private boolean disableStreamCache;
    private boolean transferException;
    private boolean traceEnabled;
    private boolean authenticationPreemptive;
    private String httpMethodRestrict;
    private UrlRewrite urlRewrite;
    private boolean clearExpiredCookies = true;
    private CookieStore cookieStore;

    public HttpEndpoint() {
    }

    public HttpEndpoint(String endPointURI, HttpComponent component, URI httpURI) throws URISyntaxException {
        this(endPointURI, component, httpURI, null);
    }

    public HttpEndpoint(String endPointURI, HttpComponent component, URI httpURI, ClientConnectionManager clientConnectionManager) throws URISyntaxException {
        this(endPointURI, component, httpURI, new BasicHttpParams(), clientConnectionManager, null);
    }
    
    public HttpEndpoint(String endPointURI, HttpComponent component, HttpParams clientParams,
                        ClientConnectionManager clientConnectionManager, HttpClientConfigurer clientConfigurer) throws URISyntaxException {
        this(endPointURI, component, null, clientParams, clientConnectionManager, clientConfigurer);
    }

    public HttpEndpoint(String endPointURI, HttpComponent component, URI httpURI, HttpParams clientParams,
                        ClientConnectionManager clientConnectionManager, HttpClientConfigurer clientConfigurer) throws URISyntaxException {
        super(endPointURI, component);
        this.component = component;
        this.httpUri = httpURI;
        this.clientParams = clientParams;
        this.httpClientConfigurer = clientConfigurer;
        this.clientConnectionManager = clientConnectionManager;
    }

    public Producer createProducer() throws Exception {
        return new HttpProducer(this);
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
        ObjectHelper.notNull(clientParams, "clientParams");
        ObjectHelper.notNull(clientConnectionManager, "httpConnectionManager");

        DefaultHttpClient answer = new DefaultHttpClient(clientConnectionManager, getClientParams());
        if (cookieStore != null) {
            answer.setCookieStore(cookieStore);
        }

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
            try {
                component.registerPort(HttpHelper.isSecureConnection(scheme), component.getX509HostnameVerifier(), port, component.getSslContextParameters());
            } catch (Exception ex) {
                throw new RuntimeCamelException(ex);
            }
            HttpHost proxy = new HttpHost(host, port, scheme);
            answer.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
        }

        HttpClientConfigurer configurer = getHttpClientConfigurer();
        if (configurer != null) {
            configurer.configureHttpClient(answer);
        }

        if (isBridgeEndpoint()) {
            // need to use noop cookiestore as we do not want to keep cookies in memory
            answer.setCookieStore(new NoopCookieStore());
        }

        LOG.debug("Created HttpClient {}", answer);
        return answer;
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


    // Properties
    //-------------------------------------------------------------------------

    /**
     * Provide access to the client parameters used on new {@link HttpClient} instances
     * used by producers or consumers of this endpoint.
     */
    public HttpParams getClientParams() {
        return clientParams;
    }

    /**
     * Provide access to the client parameters used on new {@link HttpClient} instances
     * used by producers or consumers of this endpoint.
     */
    public void setClientParams(HttpParams clientParams) {
        this.clientParams = clientParams;
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
     *
     * @param httpClientConfigurer the strategy for configuring new {@link HttpClient} instances
     */
    public void setHttpClientConfigurer(HttpClientConfigurer httpClientConfigurer) {
        this.httpClientConfigurer = httpClientConfigurer;
    }

    public HttpBinding getBinding() {
        if (binding == null) {
            binding = new DefaultHttpBinding(this);
        }
        return binding;
    }

    public void setBinding(HttpBinding binding) {
        this.binding = binding;
    }
    
    public void setHttpBinding(HttpBinding binding) {
        this.binding = binding;
    }

    public void setHttpBindingRef(HttpBinding binding) {
        this.binding = binding;
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

    public void setHttpUri(URI httpUri) {
        this.httpUri = httpUri;
    }

    public ClientConnectionManager getClientConnectionManager() {
        return clientConnectionManager;
    }

    public void setClientConnectionManager(ClientConnectionManager clientConnectionManager) {
        this.clientConnectionManager = clientConnectionManager;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    public boolean isBridgeEndpoint() {
        return bridgeEndpoint;
    }

    public void setBridgeEndpoint(boolean bridge) {
        this.bridgeEndpoint = bridge;
    }

    public boolean isMatchOnUriPrefix() {
        return matchOnUriPrefix;
    }

    public void setMatchOnUriPrefix(boolean match) {
        this.matchOnUriPrefix = match;
    }
    
    public boolean isDisableStreamCache() {
        return this.disableStreamCache;
    }
       
    public void setDisableStreamCache(boolean disable) {
        this.disableStreamCache = disable;
    }

    public boolean isChunked() {
        return this.chunked;
    }

    public void setChunked(boolean chunked) {
        this.chunked = chunked;
    }

    public boolean isTransferException() {
        return transferException;
    }

    public void setTransferException(boolean transferException) {
        this.transferException = transferException;
    }
    
    public boolean isTraceEnabled() {
        return this.traceEnabled;
    }

    public void setTraceEnabled(boolean traceEnabled) {
        this.traceEnabled = traceEnabled;
    }

    public String getHttpMethodRestrict() {
        return httpMethodRestrict;
    }

    public void setHttpMethodRestrict(String httpMethodRestrict) {
        this.httpMethodRestrict = httpMethodRestrict;
    }

    public UrlRewrite getUrlRewrite() {
        return urlRewrite;
    }

    public void setUrlRewrite(UrlRewrite urlRewrite) {
        this.urlRewrite = urlRewrite;
    }

    public boolean isClearExpiredCookies() {
        return clearExpiredCookies;
    }

    public void setClearExpiredCookies(boolean clearExpiredCookies) {
        this.clearExpiredCookies = clearExpiredCookies;
    }

    public CookieStore getCookieStore() {
        return cookieStore;
    }

    public void setCookieStore(CookieStore cookieStore) {
        this.cookieStore = cookieStore;
    }

    public boolean isAuthenticationPreemptive() {
        return authenticationPreemptive;
    }

    public void setAuthenticationPreemptive(boolean authenticationPreemptive) {
        this.authenticationPreemptive = authenticationPreemptive;
    }
}
