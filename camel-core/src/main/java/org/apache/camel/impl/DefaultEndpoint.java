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
package org.apache.camel.impl;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.PollingConsumer;
import org.apache.camel.spi.HasId;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * A default endpoint useful for implementation inheritance.
 * <p/>
 * Components which leverages <a href="http://camel.apache.org/asynchronous-routing-engine.html">asynchronous processing model</a>
 * should check the {@link #isSynchronous()} to determine if asynchronous processing is allowed.
 * The <tt>synchronous</tt> option on the endpoint allows Camel end users to dictate whether they want the asynchronous model or not.
 * The option is default <tt>false</tt> which means asynchronous processing is allowed.
 *
 * @version $Revision$
 */
public abstract class DefaultEndpoint implements Endpoint, HasId, CamelContextAware {

    private String endpointUri;
    private CamelContext camelContext;
    private Component component;
    private ExchangePattern exchangePattern = ExchangePattern.InOnly;
    // option to allow end user to dictate whether async processing should be used or not (if possible)
    private boolean synchronous;
    private final String id = EndpointHelper.createEndpointId();

    protected DefaultEndpoint(String endpointUri, Component component) {
        this(endpointUri, component.getCamelContext());
        this.component = component;
    }

    protected DefaultEndpoint(String endpointUri, CamelContext camelContext) {
        this(endpointUri);
        this.camelContext = camelContext;
    }

    protected DefaultEndpoint(String endpointUri) {
        this.setEndpointUri(endpointUri);
    }

    protected DefaultEndpoint() {
        super();
    }

    public int hashCode() {
        return getEndpointUri().hashCode() * 37 + 1;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof DefaultEndpoint) {
            DefaultEndpoint that = (DefaultEndpoint) object;
            return ObjectHelper.equal(this.getEndpointUri(), that.getEndpointUri());
        }
        return false;
    }

    @Override
    public String toString() {
        return "Endpoint[" + getEndpointUri() + "]";
    }

    /**
     * Returns a unique String ID which can be used for aliasing without having to use the whole URI which
     * is not unique 
     */
    public String getId() {
        return id;
    }

    public String getEndpointUri() {
        if (endpointUri == null) {
            endpointUri = createEndpointUri();
            if (endpointUri == null) {
                throw new IllegalArgumentException("endpointUri is not specified and " + getClass().getName()
                        + " does not implement createEndpointUri() to create a default value");
            }
        }
        return endpointUri;
    }

    public String getEndpointKey() {
        if (isLenientProperties()) {
            // only use the endpoint uri without parameters as the properties is lenient
            String uri = getEndpointUri();
            if (uri.indexOf('?') != -1) {
                return ObjectHelper.before(uri, "?");
            } else {
                return uri;
            }
        } else {
            // use the full endpoint uri
            return getEndpointUri();
        }
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public Component getComponent() {
        return component;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public PollingConsumer createPollingConsumer() throws Exception {
        return new EventDrivenPollingConsumer(this);
    }

    public Exchange createExchange(Exchange exchange) {
        Class<Exchange> exchangeType = getExchangeType();
        if (exchangeType != null) {
            if (exchangeType.isInstance(exchange)) {
                return exchangeType.cast(exchange);
            }
        }
        return exchange.copy();
    }

    /**
     * Returns the type of the exchange which is generated by this component
     */
    @SuppressWarnings("unchecked")
    public Class<Exchange> getExchangeType() {
        Type type = getClass().getGenericSuperclass();
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type[] arguments = parameterizedType.getActualTypeArguments();
            if (arguments.length > 0) {
                Type argumentType = arguments[0];
                if (argumentType instanceof Class) {
                    return (Class<Exchange>) argumentType;
                }
            }
        }
        return null;
    }

    public Exchange createExchange() {
        return createExchange(getExchangePattern());
    }

    public Exchange createExchange(ExchangePattern pattern) {
        return new DefaultExchange(this, pattern);
    }

    public ExchangePattern getExchangePattern() {
        return exchangePattern;
    }

    public void setExchangePattern(ExchangePattern exchangePattern) {
        this.exchangePattern = exchangePattern;
    }

    public boolean isSynchronous() {
        return synchronous;
    }

    /**
     * Sets whether synchronous processing should be strictly used, or Camel is allowed to use
     * asynchronous processing (if supported).
     *
     * @param synchronous <tt>true</tt> to enforce synchronous processing
     */
    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }

    public void configureProperties(Map<String, Object> options) {
        // do nothing by default
    }

    /**
     * A factory method to lazily create the endpointUri if none is specified 
     */
    protected String createEndpointUri() {
        return null;
    }

    /**
     * Sets the endpointUri if it has not been specified yet via some kind of dependency injection mechanism.
     * This allows dependency injection frameworks such as Spring or Guice to set the default endpoint URI in cases
     * where it has not been explicitly configured using the name/context in which an Endpoint is created.
     */
    public void setEndpointUriIfNotSpecified(String value) {
        if (endpointUri == null) {
            setEndpointUri(value);
        }
    }
    protected void setEndpointUri(String endpointUri) {
        this.endpointUri = endpointUri;
    }

    public boolean isLenientProperties() {
        // default should be false for most components
        return false;
    }

    public void start() throws Exception {
        // noop
    }

    public void stop() throws Exception {
        // noop
    }
}
