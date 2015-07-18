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

import org.apache.camel.Consumer;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a <a href="http://camel.apache.org/http.html">HTTP endpoint</a>
 *
 * @version 
 */
@UriEndpoint(scheme = "http,https", title = "HTTP,HTTPS", syntax = "http:httpUri", producerOnly = true, label = "http")
public class HttpEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {

    // Note: all options must be documented with description in annotations so extended components can access the documentation

    private static final Logger LOG = LoggerFactory.getLogger(HttpEndpoint.class);

    private HttpComponent component;
    private HttpClientParams clientParams;
    private HttpClientConfigurer httpClientConfigurer;
    private HttpConnectionManager httpConnectionManager;
    private UrlRewrite urlRewrite;

    @UriPath(label = "producer", description = "The url of the HTTP endpoint to call.") @Metadata(required = "true")
    private URI httpUri;
    @UriParam(description = "To use a custom HeaderFilterStrategy to filter header to and from Camel message.")
    private HeaderFilterStrategy headerFilterStrategy = new HttpHeaderFilterStrategy();
    @UriParam(description = "To use a custom HttpBinding to control the mapping between Camel message and HttpClient.")
    private HttpBinding binding;
    @UriParam(label = "producer", defaultValue = "true",
            description = "Option to disable throwing the HttpOperationFailedException in case of failed responses from the remote server."
                    + " This allows you to get all responses regardless of the HTTP status code.")
    private boolean throwExceptionOnFailure = true;
    @UriParam(label = "producer",
            description = "If the option is true, HttpProducer will ignore the Exchange.HTTP_URI header, and use the endpoint's URI for request."
                    + " You may also set the option throwExceptionOnFailure to be false to let the HttpProducer send all the fault response back.")
    private boolean bridgeEndpoint;
    @UriParam(label = "consumer",
            description = "Whether or not the consumer should try to find a target consumer by matching the URI prefix if no exact match is found.")
    private boolean matchOnUriPrefix;
    @UriParam(defaultValue = "true", description = "If this option is false Jetty servlet will disable the HTTP streaming and set the content-length header on the response")
    private boolean chunked = true;
    @UriParam(label = "consumer",
            description = "Determines whether or not the raw input stream from Jetty is cached or not"
                    + " (Camel will read the stream into a in memory/overflow to file, Stream caching) cache."
                    + " By default Camel will cache the Jetty input stream to support reading it multiple times to ensure it Camel"
                    + " can retrieve all data from the stream. However you can set this option to true when you for example need"
                    + " to access the raw stream, such as streaming it directly to a file or other persistent store."
                    + " DefaultHttpBinding will copy the request input stream into a stream cache and put it into message body"
                    + " if this option is false to support reading the stream multiple times."
                    + " If you use Jetty to bridge/proxy an endpoint then consider enabling this option to improve performance,"
                    + " in case you do not need to read the message payload multiple times.")
    private boolean disableStreamCache;
    @UriParam(label = "producer", description = "The proxy host name")
    private String proxyHost;
    @UriParam(label = "producer", description = "The proxy port number")
    private int proxyPort;
    @UriParam(label = "producer", enums = "Basic,Digest,NTLM", description = "Authentication method for proxy, either as Basic, Digest or NTLM.")
    private String authMethodPriority;
    @UriParam(description = "Option to disable throwing the HttpOperationFailedException in case of failed responses from the remote server."
                    + " This allows you to get all responses regardless of the HTTP status code.")
    private boolean transferException;
    @UriParam(label = "consumer",
            description = "Specifies whether to enable HTTP TRACE for this Jetty consumer. By default TRACE is turned off.")
    private boolean traceEnabled;
    @UriParam(label = "consumer",
            description = "Used to only allow consuming if the HttpMethod matches, such as GET/POST/PUT etc. Multiple methods can be specified separated by comma.")
    private String httpMethodRestrict;
    @UriParam(label = "consumer",
            description = "To use a custom buffer size on the javax.servlet.ServletResponse.")
    private Integer responseBufferSize;
    @UriParam(label = "producer",
            description = "If this option is true, The http producer won't read response body and cache the input stream")
    private boolean ignoreResponseBody;
    @UriParam(defaultValue = "true",
            description = "If this option is true then IN exchange headers will be copied to OUT exchange headers"
                    + " according to copy strategy.")
    private boolean copyHeaders = true;
    @UriParam(label = "consumer",
            description = "Whether to eager check whether the HTTP requests has content if the content-length header is 0 or not present."
                    + " This can be turned on in case HTTP clients do not send streamed data.")
    private boolean eagerCheckContentAvailable;

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

        if (proxyHost != null) {
            LOG.debug("Using proxy: {}:{}", proxyHost, proxyPort);
            answer.getHostConfiguration().setProxy(proxyHost, proxyPort);
        }

        if (authMethodPriority != null) {
            List<String> authPrefs = new ArrayList<String>();
            Iterator<?> it = getCamelContext().getTypeConverter().convertTo(Iterator.class, authMethodPriority);
            int i = 1;
            while (it.hasNext()) {
                Object value = it.next();
                AuthMethod auth = getCamelContext().getTypeConverter().convertTo(AuthMethod.class, value);
                if (auth == null) {
                    throw new IllegalArgumentException("Unknown authMethod: " + value + " in authMethodPriority: " + authMethodPriority);
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
            // create a new binding and use the options from this endpoint
            binding = new DefaultHttpBinding();
            binding.setHeaderFilterStrategy(getHeaderFilterStrategy());
            binding.setTransferException(isTransferException());
            binding.setEagerCheckContentAvailable(isEagerCheckContentAvailable());
        }
        return binding;
    }

    /**
     * To use a custom HttpBinding to control the mapping between Camel message and HttpClient.
     */
    public void setBinding(HttpBinding binding) {
        this.binding = binding;
    }

    public String getPath() {
        //if the path is empty, we just return the default path here
        return httpUri.getPath().length() == 0 ? "/" : httpUri.getPath();
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

    /**
     * The url of the HTTP endpoint to call.
     */
    public void setHttpUri(URI httpUri) {
        this.httpUri = httpUri;
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
     * DefaultHttpBinding will copy the request input stream into a stream cache and put it into message body
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

    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * The proxy host name
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    /**
     * The proxy port number
     */
    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getAuthMethodPriority() {
        return authMethodPriority;
    }

    /**
     * Authentication method for proxy, either as Basic, Digest or NTLM.
     */
    public void setAuthMethodPriority(String authMethodPriority) {
        this.authMethodPriority = authMethodPriority;
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

    public Integer getResponseBufferSize() {
        return responseBufferSize;
    }

    /**
     * To use a custom buffer size on the javax.servlet.ServletResponse.
     */
    public void setResponseBufferSize(Integer responseBufferSize) {
        this.responseBufferSize = responseBufferSize;
    }

    public boolean isIgnoreResponseBody() {
        return ignoreResponseBody;
    }

    /**
     * If this option is true, The http producer won't read response body and cache the input stream.
     */
    public void setIgnoreResponseBody(boolean ignoreResponseBody) {
        this.ignoreResponseBody = ignoreResponseBody;
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
}
