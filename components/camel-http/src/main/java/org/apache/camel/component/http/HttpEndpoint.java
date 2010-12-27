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
package org.apache.camel.component.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.PollingConsumer;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Represents a <a href="http://camel.apache.org/http.html">HTTP endpoint</a>
 *
 * @version $Revision$
 */
public class HttpEndpoint extends DefaultPollingEndpoint implements HeaderFilterStrategyAware {

    private static final transient Log LOG = LogFactory.getLog(HttpEndpoint.class);
    private HeaderFilterStrategy headerFilterStrategy = new HttpHeaderFilterStrategy();
    private HttpBinding binding;
    private HttpComponent component;
    private URI httpUri;
    private HttpClientParams clientParams;
    private HttpClientConfigurer httpClientConfigurer;
    private HttpConnectionManager httpConnectionManager;
    private boolean throwExceptionOnFailure = true;
    private boolean bridgeEndpoint;
    private boolean matchOnUriPrefix;
    private boolean chunked = true;
    private boolean disableStreamCache;
    private String proxyHost;
    private int proxyPort;
    private String authMethodPriority;
    private boolean transferException;

    public HttpEndpoint() {
    }

    public HttpEndpoint(String endPointURI, HttpComponent component, URI httpURI) throws URISyntaxException {
        this(endPointURI, component, httpURI, null);
    }

    public HttpEndpoint(String endPointURI, HttpComponent component, URI httpURI, HttpConnectionManager httpConnectionManager) throws URISyntaxException {
        this(endPointURI, component, httpURI, new HttpClientParams(), httpConnectionManager, null);
    }

    public HttpEndpoint(String endPointURI, HttpComponent component, URI httpURI, HttpClientParams clientParams,
                        HttpConnectionManager httpConnectionManager, HttpClientConfigurer clientConfigurer) throws URISyntaxException {
        super(endPointURI, component);
        this.component = component;
        this.httpUri = httpURI;
        this.clientParams = clientParams;
        this.httpClientConfigurer = clientConfigurer;
        this.httpConnectionManager = httpConnectionManager;
    }

    public Producer createProducer() throws Exception {
        return new HttpProducer(this);
    }

    public PollingConsumer createPollingConsumer() throws Exception {
        return new HttpPollingConsumer(this);
    }

    /**
     * Factory method used by producers and consumers to create a new {@link HttpClient} instance
     */
    public HttpClient createHttpClient() {
        ObjectHelper.notNull(clientParams, "clientParams");
        ObjectHelper.notNull(httpConnectionManager, "httpConnectionManager");

        HttpClient answer = new HttpClient(getClientParams());

        // configure http proxy from camelContext
        if (ObjectHelper.isNotEmpty(getCamelContext().getProperties().get("http.proxyHost")) && ObjectHelper.isNotEmpty(getCamelContext().getProperties().get("http.proxyPort"))) {
            String host = getCamelContext().getProperties().get("http.proxyHost");
            int port = Integer.parseInt(getCamelContext().getProperties().get("http.proxyPort"));
            if (LOG.isDebugEnabled()) {
                LOG.debug("CamelContext properties http.proxyHost and http.proxyPort detected. Using http proxy host: "
                        + host + " port: " + port);
            }
            answer.getHostConfiguration().setProxy(host, port);
        }

        if (proxyHost != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Using proxy: " + proxyHost + ":" + proxyPort);
            }
            answer.getHostConfiguration().setProxy(proxyHost, proxyPort);
        }

        if (authMethodPriority != null) {
            List<String> authPrefs = new ArrayList<String>();
            Iterator it = getCamelContext().getTypeConverter().convertTo(Iterator.class, authMethodPriority);
            int i = 1;
            while (it.hasNext()) {
                Object value = it.next();
                AuthMethod auth = getCamelContext().getTypeConverter().convertTo(AuthMethod.class, value);
                if (auth == null) {
                    throw new IllegalArgumentException("Unknown authMethod: " + value + " in authMethodPriority: " + authMethodPriority);
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Using authSchemePriority #" + i + ": " + auth);
                }
                authPrefs.add(auth.name());
                i++;
            }
            if (!authPrefs.isEmpty()) {
                answer.getParams().setParameter(AuthPolicy.AUTH_SCHEME_PRIORITY, authPrefs);
            }
        }

        answer.setHttpConnectionManager(httpConnectionManager);
        HttpClientConfigurer configurer = getHttpClientConfigurer();
        if (configurer != null) {
            configurer.configureHttpClient(answer);
        }
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
    public HttpClientParams getClientParams() {
        return clientParams;
    }

    /**
     * Provide access to the client parameters used on new {@link HttpClient} instances
     * used by producers or consumers of this endpoint.
     */
    public void setClientParams(HttpClientParams clientParams) {
        this.clientParams = clientParams;
    }

    public HttpClientConfigurer getHttpClientConfigurer() {
        return httpClientConfigurer;
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

    public String getPath() {
        return httpUri.getPath();
    }

    public int getPort() {
        if (httpUri.getPort() == -1) {
            if ("https".equals(getProtocol())) {
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

    public HttpConnectionManager getHttpConnectionManager() {
        return httpConnectionManager;
    }

    public void setHttpConnectionManager(HttpConnectionManager httpConnectionManager) {
        this.httpConnectionManager = httpConnectionManager;
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

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getAuthMethodPriority() {
        return authMethodPriority;
    }

    public void setAuthMethodPriority(String authMethodPriority) {
        this.authMethodPriority = authMethodPriority;
    }

    public boolean isTransferException() {
        return transferException;
    }

    public void setTransferException(boolean transferException) {
        this.transferException = transferException;
    }
}
