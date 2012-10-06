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
package org.apache.camel.processor;

import java.util.HashMap;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.AsyncProducerCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.ProducerCallback;
import org.apache.camel.Traceable;
import org.apache.camel.impl.InterceptSendToEndpoint;
import org.apache.camel.impl.ProducerCache;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processor for forwarding exchanges to an endpoint destination.
 *
 * @version 
 */
public class SendProcessor extends ServiceSupport implements AsyncProcessor, Traceable {
    protected final transient Logger log = LoggerFactory.getLogger(getClass());
    protected final CamelContext camelContext;
    protected ProducerCache producerCache;
    protected Endpoint destination;
    protected ExchangePattern pattern;

    public SendProcessor(Endpoint destination) {
        ObjectHelper.notNull(destination, "destination");
        this.destination = destination;
        this.camelContext = destination.getCamelContext();
        ObjectHelper.notNull(this.camelContext, "camelContext");
    }

    public SendProcessor(Endpoint destination, ExchangePattern pattern) {
        this(destination);
        this.pattern = pattern;
    }

    @Override
    public String toString() {
        return "sendTo(" + destination + (pattern != null ? " " + pattern : "") + ")";
    }

    public void setDestination(Endpoint destination) {
        this.destination = destination;
        // destination changed so purge the cache
        if (producerCache != null) {
            producerCache.purge();
        }
    }

    public String getTraceLabel() {
        return URISupport.sanitizeUri(destination.getEndpointUri());
    }

    public void process(final Exchange exchange) throws Exception {
        if (!isStarted()) {
            throw new IllegalStateException("SendProcessor has not been started: " + this);
        }

        // we should preserve existing MEP so remember old MEP
        // if you want to permanently to change the MEP then use .setExchangePattern in the DSL
        final ExchangePattern existingPattern = exchange.getPattern();

        // send the exchange to the destination using a producer
        producerCache.doInProducer(destination, exchange, pattern, new ProducerCallback<Exchange>() {
            public Exchange doInProducer(Producer producer, Exchange exchange, ExchangePattern pattern) throws Exception {
                exchange = configureExchange(exchange, pattern);
                log.debug(">>>> {} {}", destination, exchange);
                try {
                    producer.process(exchange);
                } finally {
                    // restore previous MEP
                    exchange.setPattern(existingPattern);
                }
                return exchange;
            }
        });
    }

    public boolean process(Exchange exchange, final AsyncCallback callback) {
        if (!isStarted()) {
            throw new IllegalStateException("SendProcessor has not been started: " + this);
        }

        // we should preserve existing MEP so remember old MEP
        // if you want to permanently to change the MEP then use .setExchangePattern in the DSL
        final ExchangePattern existingPattern = exchange.getPattern();

        // send the exchange to the destination using a producer
        return producerCache.doInAsyncProducer(destination, exchange, pattern, callback, new AsyncProducerCallback() {
            public boolean doInAsyncProducer(Producer producer, AsyncProcessor asyncProducer, final Exchange exchange,
                                             ExchangePattern pattern, final AsyncCallback callback) {
                final Exchange target = configureExchange(exchange, pattern);
                log.debug(">>>> {} {}", destination, exchange);
                return AsyncProcessorHelper.process(asyncProducer, target, new AsyncCallback() {
                    public void done(boolean doneSync) {
                        // restore previous MEP
                        target.setPattern(existingPattern);
                        // signal we are done
                        callback.done(doneSync);
                    }
                });
            }
        });
    }

    public Endpoint getDestination() {
        return destination;
    }

    public ExchangePattern getPattern() {
        return pattern;
    }

    protected Exchange configureExchange(Exchange exchange, ExchangePattern pattern) {
        if (pattern != null) {
            exchange.setPattern(pattern);
        }
        // set property which endpoint we send to
        exchange.setProperty(Exchange.TO_ENDPOINT, destination.getEndpointUri());
        return exchange;
    }

    protected void doStart() throws Exception {
        if (producerCache == null) {
            // use a single producer cache as we need to only hold reference for one destination
            // and use a regular HashMap as we do not want a soft reference store that may get re-claimed when low on memory
            // as we want to ensure the producer is kept around, to ensure its lifecycle is fully managed,
            // eg stopping the producer when we stop etc.
            producerCache = new ProducerCache(this, camelContext, new HashMap<String, Producer>(1));
            // do not add as service as we do not want to manage the producer cache
        }
        ServiceHelper.startService(producerCache);

        // the destination could since have been intercepted by a interceptSendToEndpoint so we got to
        // lookup this before we can use the destination
        Endpoint lookup = camelContext.hasEndpoint(destination.getEndpointKey());
        if (lookup instanceof InterceptSendToEndpoint) {
            if (log.isDebugEnabled()) {
                log.debug("Intercepted sending to {} -> {}",
                        URISupport.sanitizeUri(destination.getEndpointUri()), URISupport.sanitizeUri(lookup.getEndpointUri()));
            }
            destination = lookup;
        }
        // warm up the producer by starting it so we can fail fast if there was a problem
        // however must start endpoint first
        ServiceHelper.startService(destination);
        producerCache.startProducer(destination);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopService(producerCache);
    }

    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownService(producerCache);
    }
}
