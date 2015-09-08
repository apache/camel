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

import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointConfiguration;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ShutdownableService;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.processor.PipelineHelper.continueProcessing;

/**
 * This is an endpoint when sending to it, is intercepted and is routed in a detour
 *
 * @version 
 */
public class InterceptSendToEndpoint implements Endpoint, ShutdownableService {

    private static final Logger LOG = LoggerFactory.getLogger(InterceptSendToEndpoint.class);

    private final Endpoint delegate;
    private Producer producer;
    private Processor detour;
    private boolean skip;

    /**
     * Intercepts sending to the given endpoint
     *
     * @param destination  the original endpoint
     * @param skip <tt>true</tt> to skip sending after the detour to the original endpoint
     */
    public InterceptSendToEndpoint(final Endpoint destination, boolean skip) {
        this.delegate = destination;
        this.skip = skip;
    }

    public void setDetour(Processor detour) {
        this.detour = detour;
    }

    public Endpoint getDelegate() {
        return delegate;
    }

    public String getEndpointUri() {
        return delegate.getEndpointUri();
    }

    public EndpointConfiguration getEndpointConfiguration() {
        return delegate.getEndpointConfiguration();
    }

    public String getEndpointKey() {
        return delegate.getEndpointKey();
    }

    public Exchange createExchange() {
        return delegate.createExchange();
    }

    public Exchange createExchange(ExchangePattern pattern) {
        return delegate.createExchange(pattern);
    }

    @Deprecated
    public Exchange createExchange(Exchange exchange) {
        return delegate.createExchange(exchange);
    }

    public CamelContext getCamelContext() {
        return delegate.getCamelContext();
    }

    public Producer createProducer() throws Exception {
        producer = delegate.createProducer();
        return new DefaultAsyncProducer(delegate) {

            public Endpoint getEndpoint() {
                return producer.getEndpoint();
            }

            public Exchange createExchange() {
                return producer.createExchange();
            }

            public Exchange createExchange(ExchangePattern pattern) {
                return producer.createExchange(pattern);
            }

            @Deprecated
            public Exchange createExchange(Exchange exchange) {
                return producer.createExchange(exchange);
            }

            @Override
            public boolean process(Exchange exchange, AsyncCallback callback) {
                // process the detour so we do the detour routing
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Sending to endpoint: {} is intercepted and detoured to: {} for exchange: {}", new Object[]{getEndpoint(), detour, exchange});
                }
                // add header with the real endpoint uri
                exchange.getIn().setHeader(Exchange.INTERCEPTED_ENDPOINT, delegate.getEndpointUri());

                // detour the exchange using synchronous processing
                try {
                    detour.process(exchange);
                } catch (Exception e) {
                    exchange.setException(e);
                }

                // Decide whether to continue or not; similar logic to the Pipeline
                // check for error if so we should break out
                if (!continueProcessing(exchange, "skip sending to original intended destination: " + getEndpoint(), LOG)) {
                    callback.done(true);
                    return true;
                }

                // determine if we should skip or not
                boolean shouldSkip = skip;

                // if then interceptor had a when predicate, then we should only skip if it matched
                Boolean whenMatches = (Boolean) exchange.removeProperty(Exchange.INTERCEPT_SEND_TO_ENDPOINT_WHEN_MATCHED);
                if (whenMatches != null) {
                    shouldSkip = skip && whenMatches;
                }

                if (!shouldSkip) {
                    if (exchange.hasOut()) {
                        // replace OUT with IN as detour changed something
                        exchange.setIn(exchange.getOut());
                        exchange.setOut(null);
                    }

                    // route to original destination leveraging the asynchronous routing engine if possible
                    if (producer instanceof AsyncProcessor) {
                        AsyncProcessor async = (AsyncProcessor) producer;
                        return async.process(exchange, callback);
                    } else {
                        try {
                            producer.process(exchange);
                        } catch (Exception e) {
                            exchange.setException(e);
                        }
                        callback.done(true);
                        return true;
                    }
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Stop() means skip sending exchange to original intended destination: {} for exchange: {}", getEndpoint(), exchange);
                    }
                    callback.done(true);
                    return true;
                }
            }

            public boolean isSingleton() {
                return producer.isSingleton();
            }

            public void start() throws Exception {
                ServiceHelper.startService(detour);
                // here we also need to start the producer
                ServiceHelper.startService(producer);
            }

            public void stop() throws Exception {
                // do not stop detour as it should only be stopped when the interceptor stops
                // we should stop the producer here
                ServiceHelper.stopService(producer);
            }
        };
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return delegate.createConsumer(processor);
    }

    public PollingConsumer createPollingConsumer() throws Exception {
        return delegate.createPollingConsumer();
    }

    public void configureProperties(Map<String, Object> options) {
        delegate.configureProperties(options);
    }

    public void setCamelContext(CamelContext context) {
        delegate.setCamelContext(context);
    }

    public boolean isLenientProperties() {
        return delegate.isLenientProperties();
    }

    public boolean isSingleton() {
        return delegate.isSingleton();
    }

    public void start() throws Exception {
        ServiceHelper.startServices(detour, delegate);
    }

    public void stop() throws Exception {
        ServiceHelper.stopServices(delegate, detour);
    }

    @Override
    public void shutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(delegate, detour);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
