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
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.http.common.HttpCommonEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For calling out to external HTTP servers using Apache HTTP Client 3.x.
 */
@UriEndpoint(firstVersion = "1.0.0", scheme = "http,https", title = "HTTP,HTTPS", syntax = "http:httpUri", producerOnly = true, label = "http", lenientProperties = true)
public class HttpEndpoint extends HttpCommonEndpoint {

    // Note: all options must be documented with description in annotations so extended components can access the documentation

    private static final Logger LOG = LoggerFactory.getLogger(HttpEndpoint.class);

    private HttpClientParams clientParams;

    @UriParam(label = "advanced")
    private HttpClientConfigurer httpClientConfigurer;
    @UriParam(label = "advanced", prefix = "httpClient.", multiValue = true)
    private Map<String, Object> httpClientOptions;
    @UriParam(label = "advanced")
    private HttpConnectionManager httpConnectionManager;
    @UriParam(label = "advanced", prefix = "httpConnectionManager.", multiValue = true)
    private Map<String, Object> httpConnectionManagerOptions;

    public HttpEndpoint() {
    }

    public HttpEndpoint(String endPointURI, HttpComponent component, URI httpURI) throws URISyntaxException {
        this(endPointURI, component, httpURI, null);
    }

    public HttpEndpoint(String endPointURI, HttpComponent component, URI httpURI, HttpConnectionManager httpConnectionManager) throws URISyntaxException {
        this(endPointURI, component, httpURI, new HttpClientParams(), httpConnectionManager, null);
    }

    public HttpEndpoint(String endPointURI, HttpComponent component, HttpClientParams clientParams,
                        HttpConnectionManager httpConnectionManager, HttpClientConfigurer clientConfigurer) throws URISyntaxException {
        this(endPointURI, component, null, clientParams, httpConnectionManager, clientConfigurer);
    }

    public HttpEndpoint(String endPointURI, HttpComponent component, URI httpURI, HttpClientParams clientParams,
                        HttpConnectionManager httpConnectionManager, HttpClientConfigurer clientConfigurer) throws URISyntaxException {
        super(endPointURI, component, httpURI);
        this.clientParams = clientParams;
        this.httpClientConfigurer = clientConfigurer;
        this.httpConnectionManager = httpConnectionManager;
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
     * Factory method used by producers and consumers to create a new {@link HttpClient} instance
     */
    public HttpClient createHttpClient() {
        ObjectHelper.notNull(clientParams, "clientParams");
        ObjectHelper.notNull(httpConnectionManager, "httpConnectionManager");

        HttpClient answer = new HttpClient(getClientParams());

        // configure http proxy from camelContext
        if (ObjectHelper.isNotEmpty(getCamelContext().getProperty("http.proxyHost")) && ObjectHelper.isNotEmpty(getCamelContext().getProperty("http.proxyPort"))) {
            String host = getCamelContext().getProperty("http.proxyHost");
            int port = Integer.parseInt(getCamelContext().getProperty("http.proxyPort"));
            LOG.debug("CamelContext properties http.proxyHost and http.proxyPort detected. Using http proxy host: {} port: {}", host, port);
            answer.getHostConfiguration().setProxy(host, port);
        }

        if (getProxyHost() != null) {
            LOG.debug("Using proxy: {}:{}", getProxyHost(), getProxyPort());
            answer.getHostConfiguration().setProxy(getProxyHost(), getProxyPort());
        }

        if (getAuthMethodPriority() != null) {
            List<String> authPrefs = new ArrayList<String>();
            Iterator<?> it = getCamelContext().getTypeConverter().convertTo(Iterator.class, getAuthMethodPriority());
            int i = 1;
            while (it.hasNext()) {
                Object value = it.next();
                AuthMethod auth = getCamelContext().getTypeConverter().convertTo(AuthMethod.class, value);
                if (auth == null) {
                    throw new IllegalArgumentException("Unknown authMethod: " + value + " in authMethodPriority: " + getAuthMethodPriority());
                }
                LOG.debug("Using authSchemePriority #{}: {}", i, auth);
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

    public HttpConnectionManager getHttpConnectionManager() {
        return httpConnectionManager;
    }

    /**
     * To use a custom HttpConnectionManager to manage connections
     */
    public void setHttpConnectionManager(HttpConnectionManager httpConnectionManager) {
        this.httpConnectionManager = httpConnectionManager;
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

    public Map<String, Object> getHttpConnectionManagerOptions() {
        return httpConnectionManagerOptions;
    }

    /**
     * To configure the HttpConnectionManager using the key/values from the Map.
     */
    public void setHttpConnectionManagerOptions(Map<String, Object> httpConnectionManagerOptions) {
        this.httpConnectionManagerOptions = httpConnectionManagerOptions;
    }
}
