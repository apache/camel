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

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.http.common.cookie.CookieHandler;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.restlet.data.Method;

/**
 * Component for consuming and producing Restful resources using Restlet.
 */
@UriEndpoint(firstVersion = "2.0.0", scheme = "restlet", title = "Restlet", syntax = "restlet:protocol:host:port/uriPattern",
        consumerClass = RestletConsumer.class, label = "rest", lenientProperties = true)
public class RestletEndpoint extends DefaultEndpoint implements AsyncEndpoint, HeaderFilterStrategyAware {
    private static final int DEFAULT_PORT = 80;
    private static final String DEFAULT_PROTOCOL = "http";
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_SOCKET_TIMEOUT = 30000;
    private static final int DEFAULT_CONNECT_TIMEOUT = 30000;

    @UriPath(enums = "http,https") @Metadata(required = "true")
    private String protocol = DEFAULT_PROTOCOL;
    @UriPath @Metadata(required = "true")
    private String host = DEFAULT_HOST;
    @UriPath(defaultValue = "80") @Metadata(required = "true")
    private int port = DEFAULT_PORT;
    @UriPath
    private String uriPattern;
    @UriParam(label = "producer", defaultValue = "" + DEFAULT_SOCKET_TIMEOUT)
    private int socketTimeout = DEFAULT_SOCKET_TIMEOUT;
    @UriParam(label = "producer", defaultValue = "" + DEFAULT_CONNECT_TIMEOUT)
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    @UriParam(defaultValue = "GET", enums = "ALL,CONNECT,DELETE,GET,HEAD,OPTIONS,PATCH,POST,PUT,TRACE")
    private Method restletMethod = Method.GET;
    @UriParam(label = "consumer", javaType = "java.lang.String")
    private Method[] restletMethods;
    @UriParam(label = "consumer,advanced")
    @Deprecated
    private List<String> restletUriPatterns;
    @UriParam(label = "security")
    private Map<String, String> restletRealm;
    @UriParam(label = "advanced")
    private HeaderFilterStrategy headerFilterStrategy;
    @UriParam(label = "advanced")
    private RestletBinding restletBinding;
    @UriParam(label = "producer", defaultValue = "true")
    private boolean throwExceptionOnFailure = true;
    @UriParam(label = "consumer,advanced")
    private boolean disableStreamCache;
    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;
    @UriParam(label = "producer,advanced")
    private boolean streamRepresentation;
    @UriParam(label = "producer,advanced")
    private boolean autoCloseStream;
    @UriParam(label = "producer")
    private CookieHandler cookieHandler;
    // should NOT be exposes as @UriParam
    private transient Map<String, Object> queryParameters;

    public RestletEndpoint(RestletComponent component, String remaining) throws Exception {
        super(remaining, component);
    }

    public void setCompleteEndpointUri(String uri) {
        setEndpointUri(uri);
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    public boolean isLenientProperties() {
        // true to allow dynamic URI options to be configured and passed to external system.
        return true;
    }

    @Override
    public Exchange createExchange() {
        Exchange exchange = super.createExchange();
        if (isDisableStreamCache()) {
            exchange.setProperty(Exchange.DISABLE_HTTP_STREAM_CACHE, Boolean.TRUE);
        }
        return exchange;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        RestletConsumer answer = new RestletConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    public Producer createProducer() throws Exception {
        return new RestletProducer(this);
    }

    public void connect(RestletConsumer restletConsumer) throws Exception {
        ((RestletComponent) getComponent()).connect(restletConsumer);
    }

    public void disconnect(RestletConsumer restletConsumer) throws Exception {
        ((RestletComponent) getComponent()).disconnect(restletConsumer);
    }

    public Method getRestletMethod() {
        return restletMethod;
    }

    /**
     * On a producer endpoint, specifies the request method to use.
     * On a consumer endpoint, specifies that the endpoint consumes only restletMethod requests.
     */
    public void setRestletMethod(Method restletMethod) {
        this.restletMethod = restletMethod;
    }

    public String getProtocol() {
        return protocol;
    }

    /**
     * The protocol to use which is http or https
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getHost() {
        return host;
    }

    /**
     * The hostname of the restlet service
     */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * The port number of the restlet service
     */
    public void setPort(int port) {
        this.port = port;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    /**
     * The Client socket receive timeout, 0 for unlimited wait.
     */
    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }
    
    public int getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * The Client will give up connection if the connection is timeout, 0 for unlimited wait.
     */
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public String getUriPattern() {
        return uriPattern;
    }

    /**
     * The resource pattern such as /customer/{id}
     */
    public void setUriPattern(String uriPattern) {
        this.uriPattern = uriPattern;
    }

    public RestletBinding getRestletBinding() {
        return restletBinding;
    }

    /**
     * To use a custom RestletBinding to bind between Restlet and Camel message.
     */
    public void setRestletBinding(RestletBinding restletBinding) {
        this.restletBinding = restletBinding;
    }

    /**
     * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
     */
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
        if (restletBinding instanceof HeaderFilterStrategyAware) {
            ((HeaderFilterStrategyAware) restletBinding).setHeaderFilterStrategy(headerFilterStrategy);
        }
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To configure the security realms of restlet as a map.
     */
    public void setRestletRealm(Map<String, String> restletRealm) {
        this.restletRealm = restletRealm;
    }

    public Map<String, String> getRestletRealm() {
        return restletRealm;
    }

    @Override
    public ExchangePattern getExchangePattern() {
        // should always use in out for restlet
        return ExchangePattern.InOut;
    }

    /**
     * Specify one or more methods separated by commas (e.g. restletMethods=post,put) to be serviced by a restlet consumer endpoint.
     * If both restletMethod and restletMethods options are specified, the restletMethod setting is ignored.
     * The possible methods are: ALL,CONNECT,DELETE,GET,HEAD,OPTIONS,PATCH,POST,PUT,TRACE
     */
    public void setRestletMethods(Method[] restletMethods) {
        this.restletMethods = restletMethods;
    }

    public Method[] getRestletMethods() {
        return restletMethods;
    }

    /**
     * Specify one ore more URI templates to be serviced by a restlet consumer endpoint, using the # notation to
     * reference a List<String> in the Camel Registry.
     * If a URI pattern has been defined in the endpoint URI, both the URI pattern defined in the endpoint and the restletUriPatterns option will be honored.
     */
    @Deprecated
    public void setRestletUriPatterns(List<String> restletUriPatterns) {
        this.restletUriPatterns = restletUriPatterns;
    }

    @Deprecated
    public List<String> getRestletUriPatterns() {
        return restletUriPatterns;
    }

    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    /**
     * Whether to throw exception on a producer failure. If this option is false then the http status code is set as a message header which
     * can be checked if it has an error value.
     */
    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
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
    
    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * To configure security using SSLContextParameters.
     */
    public void setSslContextParameters(SSLContextParameters scp) {
        this.sslContextParameters = scp;
    }

    public boolean isStreamRepresentation() {
        return streamRepresentation;
    }

    /**
     * Whether to support stream representation as response from calling a REST service using the restlet producer.
     * If the response is streaming then this option can be enabled to use an {@link java.io.InputStream} as the
     * message body on the Camel {@link Message} body. If using this option you may want to enable the
     * autoCloseStream option as well to ensure the input stream is closed when the Camel {@link Exchange}
     * is done being routed. However if you need to read the stream outside a Camel route, you may need
     * to not auto close the stream.
     */
    public void setStreamRepresentation(boolean streamRepresentation) {
        this.streamRepresentation = streamRepresentation;
    }

    public boolean isAutoCloseStream() {
        return autoCloseStream;
    }

    /**
     * Whether to auto close the stream representation as response from calling a REST service using the restlet producer.
     * If the response is streaming and the option streamRepresentation is enabled then you may want to auto close
     * the {@link InputStream} from the streaming response to ensure the input stream is closed when the Camel {@link Exchange}
     * is done being routed. However if you need to read the stream outside a Camel route, you may need
     * to not auto close the stream.
     */
    public void setAutoCloseStream(boolean autoCloseStream) {
        this.autoCloseStream = autoCloseStream;
    }

    public CookieHandler getCookieHandler() {
        return cookieHandler;
    }

    /**
     * Configure a cookie handler to maintain a HTTP session
     */
    public void setCookieHandler(CookieHandler cookieHandler) {
        this.cookieHandler = cookieHandler;
    }

    public Map<String, Object> getQueryParameters() {
        return queryParameters;
    }

    /**
     * Additional query parameters for producer
     */
    public void setQueryParameters(Map<String, Object> queryParameters) {
        this.queryParameters = queryParameters;
    }

    @Override
    protected void doStart() throws Exception {
        if (headerFilterStrategy == null) {
            headerFilterStrategy = new RestletHeaderFilterStrategy();
        }
        if (restletBinding == null) {
            restletBinding = new DefaultRestletBinding();
        }
        if (restletBinding instanceof HeaderFilterStrategyAware) {
            ((HeaderFilterStrategyAware) restletBinding).setHeaderFilterStrategy(getHeaderFilterStrategy());
        }
        if (restletBinding instanceof DefaultRestletBinding) {
            ((DefaultRestletBinding) restletBinding).setStreamRepresentation(isStreamRepresentation());
            ((DefaultRestletBinding) restletBinding).setAutoCloseStream(isAutoCloseStream());
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
