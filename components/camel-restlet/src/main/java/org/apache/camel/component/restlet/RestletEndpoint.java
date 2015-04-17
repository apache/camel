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

import java.util.List;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.CollectionStringBuffer;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.restlet.data.Method;

/**
 * Represents a <a href="http://www.restlet.org/"> endpoint</a>
 *
 * @version 
 */
@UriEndpoint(scheme = "restlet", title = "Restlet", syntax = "restlet:protocol:host:port/uriPattern", consumerClass = RestletConsumer.class, label = "http,rest")
public class RestletEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {

    private static final int DEFAULT_PORT = 80;
    private static final String DEFAULT_PROTOCOL = "http";
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_SOCKET_TIMEOUT = 30000;
    private static final int DEFAULT_CONNECT_TIMEOUT = 30000;

    @UriPath(enums = "http") @Metadata(required = "true")
    private String protocol = DEFAULT_PROTOCOL;
    @UriPath @Metadata(required = "true")
    private String host = DEFAULT_HOST;
    @UriPath(defaultValue = "80")
    private int port = DEFAULT_PORT;
    @UriPath @Metadata(required = "true")
    private String uriPattern;
    @UriParam(defaultValue = "" + DEFAULT_SOCKET_TIMEOUT)
    private int socketTimeout = DEFAULT_SOCKET_TIMEOUT;
    @UriParam(defaultValue = "" + DEFAULT_CONNECT_TIMEOUT)
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    @UriParam(defaultValue = "GET")
    private Method restletMethod = Method.GET;
    // Optional and for consumer only. This allows a single route to service multiple methods.
    // If it is non-null then restletMethod is ignored.
    @UriParam
    private Method[] restletMethods;
    // Optional and for consumer only. This allows a single route to service multiple URI patterns.
    // The URI pattern defined in the endpoint will still be honored.
    @UriParam
    private List<String> restletUriPatterns;
    @UriParam
    private Map<String, String> restletRealm;
    @UriParam
    private HeaderFilterStrategy headerFilterStrategy;
    @UriParam
    private RestletBinding restletBinding;
    @UriParam(defaultValue = "true")
    private boolean throwExceptionOnFailure = true;
    @UriParam
    private boolean disableStreamCache;
    @UriParam
    private SSLContextParameters sslContextParameters;

    public RestletEndpoint(RestletComponent component, String remaining) throws Exception {
        super(remaining, component);
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

    public void setRestletMethod(Method restletMethod) {
        this.restletMethod = restletMethod;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }
    
    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public String getUriPattern() {
        return uriPattern;
    }

    public void setUriPattern(String uriPattern) {
        this.uriPattern = uriPattern;
    }

    public RestletBinding getRestletBinding() {
        return restletBinding;
    }

    public void setRestletBinding(RestletBinding restletBinding) {
        this.restletBinding = restletBinding;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
        if (restletBinding instanceof HeaderFilterStrategyAware) {
            ((HeaderFilterStrategyAware) restletBinding).setHeaderFilterStrategy(headerFilterStrategy);
        }
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

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

    public void setRestletMethods(Method[] restletMethods) {
        this.restletMethods = restletMethods;
    }

    public Method[] getRestletMethods() {
        return restletMethods;
    }

    public void setRestletUriPatterns(List<String> restletUriPatterns) {
        this.restletUriPatterns = restletUriPatterns;
    }

    public List<String> getRestletUriPatterns() {
        return restletUriPatterns;
    }

    public boolean isThrowExceptionOnFailure() {
        return throwExceptionOnFailure;
    }

    public void setThrowExceptionOnFailure(boolean throwExceptionOnFailure) {
        this.throwExceptionOnFailure = throwExceptionOnFailure;
    }

    public boolean isDisableStreamCache() {
        return disableStreamCache;
    }

    public void setDisableStreamCache(boolean disableStreamCache) {
        this.disableStreamCache = disableStreamCache;
    }
    
    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }
    
    public void setSslContextParameters(SSLContextParameters scp) {
        this.sslContextParameters = scp;
    }

    // Update the endpointUri with the restlet method information
    protected void updateEndpointUri() {
        String endpointUri = getEndpointUri();
        CollectionStringBuffer methods = new CollectionStringBuffer(",");
        if (getRestletMethods() != null && getRestletMethods().length > 0) {
            // list the method(s) as a comma seperated list
            for (Method method : getRestletMethods()) {
                methods.append(method.getName());
            }
        } else {
            // otherwise consider the single method we own
            methods.append(getRestletMethod());
        }

        // update the uri
        endpointUri = endpointUri + "?restletMethods=" + methods;
        setEndpointUri(endpointUri);
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
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
