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
import org.apache.camel.ServicePoolAware;
import org.apache.camel.Traceable;
import org.apache.camel.impl.InterceptSendToEndpoint;
import org.apache.camel.impl.ProducerCache;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorConverterHelper;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.EventHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processor for forwarding exchanges to an endpoint destination.
 *
 * @version 
 */
public class SendProcessor extends ServiceSupport implements AsyncProcessor, Traceable {
    protected static final Logger LOG = LoggerFactory.getLogger(SendProcessor.class);
    protected final CamelContext camelContext;
    protected final ExchangePattern pattern;
    protected ProducerCache producerCache;
    protected AsyncProcessor producer;
    protected Endpoint destination;
    protected final boolean unhandleException;

    public SendProcessor(Endpoint destination) {
        this(destination, null);
    }

    public SendProcessor(Endpoint destination, ExchangePattern pattern) {
        this(destination, pattern, false);
    }
    
    public SendProcessor(Endpoint destination, ExchangePattern pattern, boolean unhandleException) {
        ObjectHelper.notNull(destination, "destination");
        this.destination = destination;
        this.camelContext = destination.getCamelContext();
        this.pattern = pattern;
        this.unhandleException = unhandleException;
        ObjectHelper.notNull(this.camelContext, "camelContext");
    }

    @Override
    public String toString() {
        return "sendTo(" + destination + (pattern != null ? " " + pattern : "") + ")";
    }

    /**
     * @deprecated not longer supported.
     */
    @Deprecated
    public void setDestination(Endpoint destination) {
    }

    public String getTraceLabel() {
        return URISupport.sanitizeUri(destination.getEndpointUri());
    }
    
    public void process(final Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, final AsyncCallback callback) {
        if (!isStarted()) {
            exchange.setException(new IllegalStateException("SendProcessor has not been started: " + this));
            callback.done(true);
            return true;
        }


        // we should preserve existing MEP so remember old MEP
        // if you want to permanently to change the MEP then use .setExchangePattern in the DSL
        final ExchangePattern existingPattern = exchange.getPattern();

        // if we have a producer then use that as its optimized
        if (producer != null) {

            // record timing for sending the exchange using the producer
            final StopWatch watch = new StopWatch();

            final Exchange target = configureExchange(exchange, pattern);

            EventHelper.notifyExchangeSending(exchange.getContext(), target, destination);
            LOG.debug(">>>> {} {}", destination, exchange);

            boolean sync = true;
            try {
                sync = producer.process(exchange, new AsyncCallback() {
                    @Override
                    public void done(boolean doneSync) {
                        try {
                            // restore previous MEP
                            target.setPattern(existingPattern);
                            // emit event that the exchange was sent to the endpoint
                            long timeTaken = watch.stop();
                            EventHelper.notifyExchangeSent(target.getContext(), target, destination, timeTaken);
                        } finally {
                            checkException(target);
                            callback.done(doneSync);
                        }
                    }
                });
            } catch (Throwable throwable) {
                if (exchange != null) {
                    exchange.setException(throwable);
                    checkException(exchange);
                }

            }

            return sync;
        }

        // send the exchange to the destination using the producer cache for the non optimized producers
        return producerCache.doInAsyncProducer(destination, exchange, pattern, callback, new AsyncProducerCallback() {
            public boolean doInAsyncProducer(Producer producer, AsyncProcessor asyncProducer, final Exchange exchange,
                                             ExchangePattern pattern, final AsyncCallback callback) {
                final Exchange target = configureExchange(exchange, pattern);
                LOG.debug(">>>> {} {}", destination, exchange);
                return asyncProducer.process(target, new AsyncCallback() {
                    public void done(boolean doneSync) {
                        // restore previous MEP
                        target.setPattern(existingPattern);
                        checkException(target);
                        // signal we are done
                        callback.done(doneSync);
                    }
                });
            }
        });
    }
    
    protected void checkException(Exchange exchange) {
        if (unhandleException && exchange.getException() != null) {
            // Override the default setting of DeadLetterChannel
            exchange.setProperty(Exchange.ERRORHANDLER_HANDLED, "false");
            // just override the exception with the new added
            exchange.setProperty(Exchange.EXCEPTION_CAUGHT, exchange.getException());
        }
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
            if (LOG.isDebugEnabled()) {
                LOG.debug("Intercepted sending to {} -> {}",
                        URISupport.sanitizeUri(destination.getEndpointUri()), URISupport.sanitizeUri(lookup.getEndpointUri()));
            }
            destination = lookup;
        }
        // warm up the producer by starting it so we can fail fast if there was a problem
        // however must start endpoint first
        ServiceHelper.startService(destination);

        // this SendProcessor is used a lot in Camel (eg every .to in the route DSL) and therefore we
        // want to optimize for regular producers, by using the producer directly instead of the ProducerCache
        // Only for pooled and non singleton producers we have to use the ProducerCache as it supports these
        // kind of producer better (though these kind of producer should be rare)

        Producer producer = producerCache.acquireProducer(destination);
        if (producer instanceof ServicePoolAware || !producer.isSingleton()) {
            // no we cannot optimize it - so release the producer back to the producer cache
            // and use the producer cache for sending
            producerCache.releaseProducer(destination, producer);
        } else {
            // yes we can optimize and use the producer directly for sending
            this.producer = AsyncProcessorConverterHelper.convert(producer);
        }
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(producerCache, producer);
    }

    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(producerCache, producer);
    }
}
