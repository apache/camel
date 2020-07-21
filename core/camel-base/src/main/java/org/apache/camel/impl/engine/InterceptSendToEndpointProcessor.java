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

import java.util.Arrays;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.AsyncProducer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.processor.PipelineHelper.continueProcessing;

/**
 * {@link org.apache.camel.Processor} used to interceptor and detour the routing
 * when using the {@link DefaultInterceptSendToEndpoint} functionality.
 */
public class InterceptSendToEndpointProcessor extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(InterceptSendToEndpointProcessor.class);

    private final DefaultInterceptSendToEndpoint endpoint;
    private final Endpoint delegate;
    private final AsyncProducer producer;
    private final boolean skip;

    public InterceptSendToEndpointProcessor(DefaultInterceptSendToEndpoint endpoint, Endpoint delegate, AsyncProducer producer, boolean skip) throws Exception {
        super(delegate);
        this.endpoint = endpoint;
        this.delegate = delegate;
        this.producer = producer;
        this.skip = skip;
    }

    @Override
    public Endpoint getEndpoint() {
        return producer.getEndpoint();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // process the detour so we do the detour routing
        if (LOG.isDebugEnabled()) {
            LOG.debug("Sending to endpoint: {} is intercepted and detoured to: {} for exchange: {}", getEndpoint(), endpoint.getBefore(), exchange);
        }
        // add header with the real endpoint uri
        exchange.getIn().setHeader(Exchange.INTERCEPTED_ENDPOINT, delegate.getEndpointUri());

        if (endpoint.getBefore() != null || endpoint.getAfter() != null) {
            // detour the exchange using synchronous processing
            AsyncProcessor before = null;
            if (endpoint.getBefore() != null) {
                before = AsyncProcessorConverterHelper.convert(endpoint.getBefore());
            }
            AsyncProcessor ascb = new AsyncProcessorSupport() {
                @Override
                public boolean process(Exchange exchange, AsyncCallback callback) {
                    return callback(exchange, callback, true);
                }
            };
            AsyncProcessor after = null;
            if (endpoint.getAfter() != null) {
                after = AsyncProcessorConverterHelper.convert(endpoint.getAfter());
            }

            return new Pipeline(exchange.getContext(), Arrays.asList(before, ascb, after)).process(exchange, callback);
        }

        return callback(exchange, callback, true);
    }

    private boolean callback(Exchange exchange, AsyncCallback callback, boolean doneSync) {
        // Decide whether to continue or not; similar logic to the Pipeline
        // check for error if so we should break out
        if (!continueProcessing(exchange, "skip sending to original intended destination: " + getEndpoint(), LOG)) {
            callback.done(doneSync);
            return doneSync;
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
            boolean s = producer.process(exchange, ds -> {
                callback.done(doneSync && ds);
            });
            return doneSync && s;
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Stop() means skip sending exchange to original intended destination: {} for exchange: {}", getEndpoint(), exchange);
            }
            callback.done(doneSync);
            return doneSync;
        }
    }

    @Override
    public boolean isSingleton() {
        return producer.isSingleton();
    }

    @Override
    public void start() {
        ServiceHelper.startService(endpoint.getBefore(), endpoint.getAfter());
        // here we also need to start the producer
        ServiceHelper.startService(producer);
    }

    @Override
    public void stop() {
        // do not stop before/after as it should only be stopped when the interceptor stops
        // we should stop the producer here
        ServiceHelper.stopService(producer);
    }

}
