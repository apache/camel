/*
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
package org.apache.camel.impl.engine;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.spi.ConsumerCache;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.UnitOfWorkHelper;
import org.apache.camel.support.cache.DefaultConsumerCache;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.RuntimeCamelException.wrapRuntimeCamelException;

/**
 * Default implementation of {@link ConsumerTemplate}.
 */
public class DefaultConsumerTemplate extends ServiceSupport implements ConsumerTemplate {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultConsumerTemplate.class);

    private final CamelContext camelContext;
    private ConsumerCache consumerCache;
    private int maximumCacheSize;

    public DefaultConsumerTemplate(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public int getMaximumCacheSize() {
        return maximumCacheSize;
    }

    @Override
    public void setMaximumCacheSize(int maximumCacheSize) {
        this.maximumCacheSize = maximumCacheSize;
    }

    @Override
    public int getCurrentCacheSize() {
        if (consumerCache == null) {
            return 0;
        }
        return consumerCache.size();
    }

    @Override
    public void cleanUp() {
        if (consumerCache != null) {
            consumerCache.cleanUp();
        }
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public Exchange receive(String endpointUri) {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        return getConsumerCache().receive(endpoint);
    }

    @Override
    public Exchange receive(Endpoint endpoint) {
        return receive(endpoint.getEndpointUri());
    }

    @Override
    public Exchange receive(String endpointUri, long timeout) {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        return getConsumerCache().receive(endpoint, timeout);
    }

    @Override
    public Exchange receive(Endpoint endpoint, long timeout) {
        return receive(endpoint.getEndpointUri(), timeout);
    }

    @Override
    public Exchange receiveNoWait(String endpointUri) {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        return getConsumerCache().receiveNoWait(endpoint);
    }

    @Override
    public Exchange receiveNoWait(Endpoint endpoint) {
        return receiveNoWait(endpoint.getEndpointUri());
    }

    @Override
    public Object receiveBody(String endpointUri) {
        return receiveBody(receive(endpointUri));
    }

    @Override
    public Object receiveBody(Endpoint endpoint) {
        return receiveBody(endpoint.getEndpointUri());
    }

    @Override
    public Object receiveBody(String endpointUri, long timeout) {
        return receiveBody(receive(endpointUri, timeout));
    }

    @Override
    public Object receiveBody(Endpoint endpoint, long timeout) {
        return receiveBody(endpoint.getEndpointUri(), timeout);
    }

    @Override
    public Object receiveBodyNoWait(String endpointUri) {
        return receiveBody(receiveNoWait(endpointUri));
    }

    private Object receiveBody(Exchange exchange) {
        Object answer;
        try {
            answer = extractResultBody(exchange);
        } finally {
            doneUoW(exchange);
        }
        return answer;
    }

    @Override
    public Object receiveBodyNoWait(Endpoint endpoint) {
        return receiveBodyNoWait(endpoint.getEndpointUri());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T receiveBody(String endpointUri, Class<T> type) {
        Object answer;
        Exchange exchange = receive(endpointUri);
        try {
            answer = extractResultBody(exchange);
            answer = camelContext.getTypeConverter().convertTo(type, exchange, answer);
        } finally {
            doneUoW(exchange);
        }
        return (T) answer;
    }

    @Override
    public <T> T receiveBody(Endpoint endpoint, Class<T> type) {
        return receiveBody(endpoint.getEndpointUri(), type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T receiveBody(String endpointUri, long timeout, Class<T> type) {
        Object answer;
        Exchange exchange = receive(endpointUri, timeout);
        try {
            answer = extractResultBody(exchange);
            answer = camelContext.getTypeConverter().convertTo(type, exchange, answer);
        } finally {
            doneUoW(exchange);
        }
        return (T) answer;
    }

    @Override
    public <T> T receiveBody(Endpoint endpoint, long timeout, Class<T> type) {
        return receiveBody(endpoint.getEndpointUri(), timeout, type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T receiveBodyNoWait(String endpointUri, Class<T> type) {
        Object answer;
        Exchange exchange = receiveNoWait(endpointUri);
        try {
            answer = extractResultBody(exchange);
            answer = camelContext.getTypeConverter().convertTo(type, exchange, answer);
        } finally {
            doneUoW(exchange);
        }
        return (T) answer;
    }

    @Override
    public <T> T receiveBodyNoWait(Endpoint endpoint, Class<T> type) {
        return receiveBodyNoWait(endpoint.getEndpointUri(), type);
    }

    @Override
    public void doneUoW(Exchange exchange) {
        try {
            // The receiveBody method will get a null exchange
            if (exchange == null) {
                return;
            }
            if (exchange.getUnitOfWork() == null) {
                // handover completions and done them manually to ensure they are being executed
                List<Synchronization> synchronizations = exchange.getExchangeExtension().handoverCompletions();
                UnitOfWorkHelper.doneSynchronizations(exchange, synchronizations);
            } else {
                // done the unit of work
                exchange.getUnitOfWork().done(exchange);
            }
        } catch (Exception e) {
            LOG.warn("Exception occurred during done UnitOfWork for Exchange: {}. This exception will be ignored.",
                    exchange, e);
        }
    }

    protected Endpoint resolveMandatoryEndpoint(String endpointUri) {
        return CamelContextHelper.getMandatoryEndpoint(camelContext, endpointUri);
    }

    /**
     * Extracts the body from the given result.
     * <p/>
     * If the exchange pattern is provided it will try to honor it and retrieve the body from either IN or OUT according
     * to the pattern.
     *
     * @param  result the result
     * @return        the result, can be <tt>null</tt>.
     */
    protected Object extractResultBody(Exchange result) {
        Object answer = null;
        if (result != null) {
            // rethrow if there was an exception
            if (result.getException() != null) {
                throw wrapRuntimeCamelException(result.getException());
            }

            // okay no fault then return the response
            answer = result.getMessage().getBody();

            // in a very seldom situation then getBody can cause an exception to be set on the exchange
            // rethrow if there was an exception during execution
            if (result.getException() != null) {
                throw wrapRuntimeCamelException(result.getException());
            }
        }
        return answer;
    }

    private org.apache.camel.spi.ConsumerCache getConsumerCache() {
        if (!isStarted()) {
            throw new IllegalStateException("ConsumerTemplate has not been started");
        }
        return consumerCache;
    }

    @Override
    protected void doBuild() throws Exception {
        if (consumerCache == null) {
            consumerCache = new DefaultConsumerCache(this, camelContext, maximumCacheSize);
        }
        ServiceHelper.buildService(consumerCache);
    }

    @Override
    protected void doInit() throws Exception {
        ServiceHelper.initService(consumerCache);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(consumerCache);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(consumerCache);
    }

    @Override
    protected void doShutdown() throws Exception {
        // we should shutdown the services as this is our intention, to not re-use the services anymore
        ServiceHelper.stopAndShutdownService(consumerCache);
        consumerCache = null;
    }

}
