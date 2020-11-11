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
package org.apache.camel.support.cache;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.StatefulService;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.spi.ProducerCache;
import org.apache.camel.spi.SharedInternalProcessor;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultEndpointUtilizationStatistics;
import org.apache.camel.support.EventHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link ProducerCache}.
 */
public class DefaultProducerCache extends ServiceSupport implements ProducerCache {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultProducerCache.class);
    private static final long ACQUIRE_WAIT_TIME = 30000;

    private final ExtendedCamelContext camelContext;
    private final ProducerServicePool producers;
    private final Object source;
    private final SharedInternalProcessor sharedInternalProcessor;

    private EndpointUtilizationStatistics statistics;
    private boolean eventNotifierEnabled = true;
    private boolean extendedStatistics;
    private int maxCacheSize;

    public DefaultProducerCache(Object source, CamelContext camelContext, int cacheSize) {
        this.source = source;
        this.camelContext = (ExtendedCamelContext) camelContext;
        this.maxCacheSize = cacheSize <= 0 ? CamelContextHelper.getMaximumCachePoolSize(camelContext) : cacheSize;
        if (cacheSize >= 0) {
            this.producers = createServicePool(camelContext, maxCacheSize);
        } else {
            // no cache then empty
            this.producers = null;
        }

        // only if JMX is enabled
        if (camelContext.getManagementStrategy() != null && camelContext.getManagementStrategy().getManagementAgent() != null) {
            this.extendedStatistics
                    = camelContext.getManagementStrategy().getManagementAgent().getStatisticsLevel().isExtended();
        } else {
            this.extendedStatistics = false;
        }

        // internal processor used for sending
        sharedInternalProcessor
                = this.camelContext.getInternalProcessorFactory().createSharedCamelInternalProcessor(this.camelContext);
    }

    protected ProducerServicePool createServicePool(CamelContext camelContext, int cacheSize) {
        return new ProducerServicePool(Endpoint::createAsyncProducer, Producer::getEndpoint, cacheSize);
    }

    @Override
    public boolean isEventNotifierEnabled() {
        return eventNotifierEnabled;
    }

    @Override
    public void setEventNotifierEnabled(boolean eventNotifierEnabled) {
        this.eventNotifierEnabled = eventNotifierEnabled;
    }

    public boolean isExtendedStatistics() {
        return extendedStatistics;
    }

    /**
     * Whether extended JMX statistics is enabled for {@link org.apache.camel.spi.EndpointUtilizationStatistics}
     */
    public void setExtendedStatistics(boolean extendedStatistics) {
        this.extendedStatistics = extendedStatistics;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public Object getSource() {
        return source;
    }

    @Override
    public AsyncProducer acquireProducer(Endpoint endpoint) {
        try {
            AsyncProducer producer = producers.acquire(endpoint);
            if (statistics != null) {
                statistics.onHit(endpoint.getEndpointUri());
            }
            // if producer is starting then wait for it to be ready
            if (producer instanceof StatefulService) {
                StatefulService ss = (StatefulService) producer;
                if (ss.isStarting()) {
                    LOG.trace("Waiting for producer to finish starting: {}", producer);
                    StopWatch watch = new StopWatch();
                    boolean done = false;
                    while (!done) {
                        done = !ss.isStarting() || watch.taken() > ACQUIRE_WAIT_TIME;
                        if (!done) {
                            Thread.sleep(5);
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("Waiting {} ms for producer to finish starting: {} state: {}", watch.taken(),
                                        producer, ss.getStatus());
                            }
                        }
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Waited {} ms for producer to finish starting: {} state: {}", watch.taken(), producer,
                                ss.getStatus());
                    }
                }
            }
            return producer;
        } catch (Throwable e) {
            throw new FailedToCreateProducerException(endpoint, e);
        }
    }

    @Override
    public void releaseProducer(Endpoint endpoint, AsyncProducer producer) {
        producers.release(endpoint, producer);
    }

    @Override
    public Exchange send(Endpoint endpoint, Exchange exchange, Processor resultProcessor) {
        if (camelContext.isStopped()) {
            exchange.setException(new RejectedExecutionException("CamelContext is stopped"));
            return exchange;
        }

        AsyncProducer producer = acquireProducer(endpoint);
        try {
            // now lets dispatch
            LOG.debug(">>>> {} {}", endpoint, exchange);

            // set property which endpoint we send to
            exchange.setProperty(Exchange.TO_ENDPOINT, endpoint.getEndpointUri());

            // send the exchange using the processor
            StopWatch watch = null;
            try {
                if (eventNotifierEnabled && camelContext.isEventNotificationApplicable()) {
                    boolean sending = EventHelper.notifyExchangeSending(exchange.getContext(), exchange, endpoint);
                    if (sending) {
                        watch = new StopWatch();
                    }
                }

                // invoke the synchronous method
                sharedInternalProcessor.process(exchange, producer, resultProcessor);

            } catch (Throwable e) {
                // ensure exceptions is caught and set on the exchange
                exchange.setException(e);
            } finally {
                // emit event that the exchange was sent to the endpoint
                if (watch != null) {
                    long timeTaken = watch.taken();
                    EventHelper.notifyExchangeSent(exchange.getContext(), exchange, endpoint, timeTaken);
                }
            }
            return exchange;
        } finally {
            releaseProducer(endpoint, producer);
        }
    }

    /**
     * Asynchronously sends an exchange to an endpoint using a supplied {@link Processor} to populate the exchange
     * <p>
     * This method will <b>neither</b> throw an exception <b>nor</b> complete future exceptionally. If processing of the
     * given Exchange failed then the exception is stored on the return Exchange
     *
     * @param  endpoint        the endpoint to send the exchange to
     * @param  pattern         the message {@link ExchangePattern} such as {@link ExchangePattern#InOnly} or
     *                         {@link ExchangePattern#InOut}
     * @param  processor       the transformer used to populate the new exchange
     * @param  resultProcessor a processor to process the exchange when the send is complete.
     * @param  future          the preexisting future to complete when processing is done or null if to create new one
     * @return                 future that completes with exchange when processing is done. Either passed into future
     *                         parameter or new one if parameter was null
     */
    @Deprecated
    public CompletableFuture<Exchange> asyncSend(
            Endpoint endpoint,
            ExchangePattern pattern,
            Processor processor,
            Processor resultProcessor,
            CompletableFuture<Exchange> future) {
        return asyncSendExchange(endpoint, pattern, processor, resultProcessor, null, future);
    }

    @Override
    public CompletableFuture<Exchange> asyncSendExchange(
            Endpoint endpoint,
            ExchangePattern pattern,
            Processor processor,
            Processor resultProcessor,
            Exchange exchange,
            CompletableFuture<Exchange> future) {
        if (exchange == null) {
            exchange = pattern != null ? endpoint.createExchange(pattern) : endpoint.createExchange();
        }
        return doAsyncSendExchange(endpoint, processor, resultProcessor, exchange, future);
    }

    protected CompletableFuture<Exchange> doAsyncSendExchange(
            Endpoint endpoint,
            Processor processor,
            Processor resultProcessor,
            Exchange exchange,
            CompletableFuture<Exchange> f) {
        CompletableFuture<Exchange> future = f != null ? f : new CompletableFuture<>();
        AsyncProducerCallback cb = (p, e, c) -> asyncDispatchExchange(endpoint, p, resultProcessor, e, c);
        try {
            if (processor instanceof AsyncProcessor) {
                ((AsyncProcessor) processor).process(exchange,
                        doneSync -> doInAsyncProducer(endpoint, exchange, ds -> future.complete(exchange), cb));
            } else {
                if (processor != null) {
                    processor.process(exchange);
                }
                doInAsyncProducer(endpoint, exchange, ds -> future.complete(exchange), cb);
            }
        } catch (Throwable e) {
            // populate failed so return
            exchange.setException(e);
            future.complete(exchange);
        }
        return future;
    }

    @Override
    public boolean doInAsyncProducer(
            Endpoint endpoint,
            Exchange exchange,
            AsyncCallback callback,
            AsyncProducerCallback producerCallback) {

        AsyncProducer producer;
        try {
            // get the producer and we do not mind if its pooled as we can handle returning it back to the pool
            producer = acquireProducer(endpoint);

            if (producer == null) {
                if (isStopped()) {
                    LOG.warn("Ignoring exchange sent after processor is stopped: {}", exchange);
                    callback.done(true);
                    return true;
                } else {
                    exchange.setException(
                            new IllegalStateException("No producer, this processor has not been started: " + this));
                    callback.done(true);
                    return true;
                }
            }
        } catch (Throwable e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        try {
            // record timing for sending the exchange using the producer
            StopWatch watch;
            if (eventNotifierEnabled && camelContext.isEventNotificationApplicable()) {
                boolean sending = EventHelper.notifyExchangeSending(exchange.getContext(), exchange, endpoint);
                if (sending) {
                    watch = new StopWatch();
                } else {
                    watch = null;
                }
            } else {
                watch = null;
            }

            // invoke the callback
            return producerCallback.doInAsyncProducer(producer, exchange, doneSync -> {
                try {
                    if (watch != null) {
                        long timeTaken = watch.taken();
                        // emit event that the exchange was sent to the endpoint
                        EventHelper.notifyExchangeSent(exchange.getContext(), exchange, endpoint, timeTaken);
                    }

                    // release back to the pool
                    releaseProducer(endpoint, producer);
                } finally {
                    callback.done(doneSync);
                }
            });
        } catch (Throwable e) {
            // ensure exceptions is caught and set on the exchange
            if (exchange != null) {
                exchange.setException(e);
            }
            callback.done(true);
            return true;
        }
    }

    protected boolean asyncDispatchExchange(
            Endpoint endpoint, AsyncProducer producer,
            Processor resultProcessor, Exchange exchange, AsyncCallback callback) {
        // now lets dispatch
        LOG.debug(">>>> {} {}", endpoint, exchange);

        // set property which endpoint we send to
        exchange.setProperty(Exchange.TO_ENDPOINT, endpoint.getEndpointUri());

        // send the exchange using the processor
        try {
            if (eventNotifierEnabled && camelContext.isEventNotificationApplicable()) {
                callback = new EventNotifierCallback(callback, exchange, endpoint);
            }
            // invoke the asynchronous method
            return sharedInternalProcessor.process(exchange, callback, producer, resultProcessor);
        } catch (Throwable e) {
            // ensure exceptions is caught and set on the exchange
            exchange.setException(e);
            callback.done(true);
            return true;
        }

    }

    @Override
    protected void doInit() throws Exception {
        if (extendedStatistics) {
            int max = maxCacheSize == 0 ? CamelContextHelper.getMaximumCachePoolSize(camelContext) : maxCacheSize;
            statistics = new DefaultEndpointUtilizationStatistics(max);
        }
        ServiceHelper.initService(producers);
    }

    @Override
    protected void doStart() throws Exception {
        if (statistics != null) {
            statistics.clear();
        }
        ServiceHelper.startService(producers);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(producers);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(producers);
    }

    @Override
    public int size() {
        int size = producers != null ? producers.size() : 0;

        LOG.trace("size = {}", size);
        return size;
    }

    @Override
    public int getCapacity() {
        return maxCacheSize;
    }

    @Override
    public synchronized void purge() {
        try {
            if (producers != null) {
                producers.stop();
                producers.start();
            }
        } catch (Exception e) {
            LOG.debug("Error restarting producers", e);
        }
        if (statistics != null) {
            statistics.clear();
        }
    }

    @Override
    public void cleanUp() {
        if (producers != null) {
            producers.cleanUp();
        }
    }

    @Override
    public EndpointUtilizationStatistics getEndpointUtilizationStatistics() {
        return statistics;
    }

    @Override
    public String toString() {
        return "ProducerCache for source: " + source + ", capacity: " + getCapacity();
    }

}
