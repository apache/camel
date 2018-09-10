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

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.processor.PipelineHelper.continueProcessing;

/**
 * {@link org.apache.camel.Processor} used to interceptor and detour the routing
 * when using the {@link InterceptSendToEndpoint} functionality.
 */
public class InterceptSendToEndpointProcessor extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(InterceptSendToEndpointProcessor.class);
    private final InterceptSendToEndpoint endpoint;
    private final Endpoint delegate;
    private final Producer producer;
    private final boolean skip;

    public InterceptSendToEndpointProcessor(InterceptSendToEndpoint endpoint, Endpoint delegate, Producer producer, boolean skip) throws Exception {
        super(delegate);
        this.endpoint = endpoint;
        this.delegate = delegate;
        this.producer = producer;
        this.skip = skip;
    }

    public Endpoint getEndpoint() {
        return producer.getEndpoint();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // process the detour so we do the detour routing
        if (LOG.isDebugEnabled()) {
            LOG.debug("Sending to endpoint: {} is intercepted and detoured to: {} for exchange: {}", getEndpoint(), endpoint.getDetour(), exchange);
        }
        // add header with the real endpoint uri
        exchange.getIn().setHeader(Exchange.INTERCEPTED_ENDPOINT, delegate.getEndpointUri());

        if (endpoint.getDetour() != null) {
            // detour the exchange using synchronous processing
            try {
                endpoint.getDetour().process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }
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
        ServiceHelper.startService(endpoint.getDetour());
        // here we also need to start the producer
        ServiceHelper.startService(producer);
    }

    public void stop() throws Exception {
        // do not stop detour as it should only be stopped when the interceptor stops
        // we should stop the producer here
        ServiceHelper.stopService(producer);
    }

}
