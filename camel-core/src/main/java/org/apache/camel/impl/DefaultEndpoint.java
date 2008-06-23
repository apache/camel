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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.PollingConsumer;
import org.apache.camel.util.ObjectHelper;

/**
 * A default endpoint useful for implementation inheritance
 *
 * @version $Revision$
 */
public abstract class DefaultEndpoint<E extends Exchange> implements Endpoint<E>, CamelContextAware {
    private String endpointUri;
    private CamelContext camelContext;
    private Component component;
    private ScheduledExecutorService executorService;
    private ExchangePattern exchangePattern = ExchangePattern.InOnly;

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

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public Component getComponent() {
        return component;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public synchronized ScheduledExecutorService getExecutorService() {
        if (executorService == null) {
            Component c = getComponent();
            if (c != null && c instanceof DefaultComponent) {
                DefaultComponent dc = (DefaultComponent) c;
                executorService = dc.getExecutorService();
            }
            if (executorService == null) {
                executorService = createExecutorService();
            }
        }
        return executorService;
    }

    public synchronized void setExecutorService(ScheduledExecutorService executorService) {
        this.executorService = executorService;
    }

    public PollingConsumer<E> createPollingConsumer() throws Exception {
        return new EventDrivenPollingConsumer<E>(this);
    }

    /**
     * Converts the given exchange to the specified exchange type
     */
    public E convertTo(Class<E> type, Exchange exchange) {
        // TODO we could infer type parameter
        if (type.isInstance(exchange)) {
            return type.cast(exchange);
        }
        return getCamelContext().getExchangeConverter().convertTo(type, exchange);
    }

    public E createExchange(Exchange exchange) {
        Class<E> exchangeType = getExchangeType();
        if (exchangeType != null) {
            if (exchangeType.isInstance(exchange)) {
                return exchangeType.cast(exchange);
            }
        }
        E answer = createExchange();
        answer.copyFrom(exchange);
        return answer;
    }

    /**
     * Returns the type of the exchange which is generated by this component
     */
    public Class<E> getExchangeType() {
        Type type = getClass().getGenericSuperclass();
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type[] arguments = parameterizedType.getActualTypeArguments();
            if (arguments.length > 0) {
                Type argumentType = arguments[0];
                if (argumentType instanceof Class) {
                    return (Class<E>) argumentType;
                }
            }
        }
        return null;
    }

    public E createExchange() {
        return createExchange(getExchangePattern());
    }

    public E createExchange(ExchangePattern pattern) {
        return (E) new DefaultExchange(getCamelContext(), pattern);
    }

    public ExchangePattern getExchangePattern() {
        return exchangePattern;
    }

    public void setExchangePattern(ExchangePattern exchangePattern) {
        this.exchangePattern = exchangePattern;
    }

    protected ScheduledThreadPoolExecutor createExecutorService() {
        return new ScheduledThreadPoolExecutor(10);
    }

    public void configureProperties(Map options) {
    }

    /**
     * A factory method to lazily create the endpointUri if none is specified 
     */
    protected String createEndpointUri() {
        return null;
    }

    protected void setEndpointUri(String endpointUri) {
        this.endpointUri = endpointUri;
    }
    
    @Deprecated
    public CamelContext getContext() {
        return getCamelContext();
    }
    
    @Deprecated
    public void setContext(CamelContext context) {
        setCamelContext(context);
    }

}
