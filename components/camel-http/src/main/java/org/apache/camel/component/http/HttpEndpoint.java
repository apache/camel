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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
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

    public HttpEndpoint() {
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

    public Exchange createExchange(ExchangePattern pattern) {
        return new DefaultExchange(this, pattern);
    }

    public Exchange createExchange(HttpServletRequest request, HttpServletResponse response) {
        DefaultExchange exchange = new DefaultExchange(this, ExchangePattern.InOut);
        exchange.setProperty(HttpConstants.SERVLET_REQUEST, request);
        exchange.setProperty(HttpConstants.SERVLET_RESPONSE, response);
        exchange.setIn(new HttpMessage(exchange, request));
        return exchange;
    }

    /**
     * Factory method used by producers and consumers to create a new {@link HttpClient} instance
     */
    public HttpClient createHttpClient() {
        ObjectHelper.notNull(clientParams, "clientParams");
        ObjectHelper.notNull(httpConnectionManager, "httpConnectionManager");

        HttpClient answer = new HttpClient(getClientParams());

        // configure http proxy if defined as system property
        // http://java.sun.com/javase/6/docs/technotes/guides/net/proxies.html
        if (System.getProperty("http.proxyHost") != null && System.getProperty("http.proxyPort") != null) {
            String host = System.getProperty("http.proxyHost");
            int port = Integer.parseInt(System.getProperty("http.proxyPort"));
            if (LOG.isDebugEnabled()) {
                LOG.debug("Java System Property http.proxyHost and http.proxyPort detected. Using http proxy host: "
                        + host + " port: " + port);
            }
            answer.getHostConfiguration().setProxy(host, port);
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
            binding = new DefaultHttpBinding(getHeaderFilterStrategy());
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
}
