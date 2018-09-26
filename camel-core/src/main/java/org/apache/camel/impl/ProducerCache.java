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

import java.util.concurrent.CompletableFuture;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.processor.CamelInternalProcessor;
import org.apache.camel.processor.SharedCamelInternalProcessor;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.EventHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cache containing created {@link Producer}.
 *
 * @version
 */
public class ProducerCache extends ServiceSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ProducerCache.class);

    private final CamelContext camelContext;
    private final ServicePool<AsyncProducer> producers;
    private final Object source;
    private final SharedCamelInternalProcessor internalProcessor;

    private EndpointUtilizationStatistics statistics;
    private boolean eventNotifierEnabled = true;
    private boolean extendedStatistics;
    private int maxCacheSize;

    public ProducerCache(Object source, CamelContext camelContext, int cacheSize) {
        this.source = source;
        this.camelContext = camelContext;
        this.maxCacheSize = cacheSize == 0 ? CamelContextHelper.getMaximumCachePoolSize(camelContext) : cacheSize;
        this.producers = new ServicePool<>(Endpoint::createAsyncProducer, AsyncProducer::getEndpoint, maxCacheSize);

        // only if JMX is enabled
        if (camelContext.getManagementStrategy().getManagementAgent() != null) {
            this.extendedStatistics = camelContext.getManagementStrategy().getManagementAgent().getStatisticsLevel().isExtended();
        } else {
            this.extendedStatistics = false;
        }

        // internal processor used for sending
        internalProcessor = new SharedCamelInternalProcessor(new CamelInternalProcessor.UnitOfWorkProcessorAdvice(null));
    }

    public boolean isEventNotifierEnabled() {
        return eventNotifierEnabled;
    }

    /**
     * Whether {@link org.apache.camel.spi.EventNotifier} is enabled
     */
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

    /**
     * Gets the source which uses this cache
     *
     * @return the source
     */
    public Object getSource() {
        return source;
    }

    /**
     * Acquires a pooled producer which you <b>must</b> release back again after usage using the
     * {@link #releaseProducer(org.apache.camel.Endpoint, org.apache.camel.AsyncProducer)} method.
     *
     * @param endpoint the endpoint
     * @return the producer
     */
    public AsyncProducer acquireProducer(Endpoint endpoint) {
        try {
            AsyncProducer producer = producers.acquire(endpoint);
            if (statistics != null) {
                statistics.onHit(endpoint.getEndpointUri());
            }
            return producer;
        } catch (Throwable e) {
            throw new FailedToCreateProducerException(endpoint, e);
        }
    }

    /**
     * Releases an acquired producer back after usage.
     *
     * @param endpoint the endpoint
     * @param producer the producer to release
     */
    public void releaseProducer(Endpoint endpoint, AsyncProducer producer) {
        producers.release(endpoint, producer);
    }

    /**
     * Sends the exchange to the given endpoint.
     * <p>
     * This method will <b>not</b> throw an exception. If processing of the given
     * Exchange failed then the exception is stored on the provided Exchange
     *
     * @param endpoint the endpoint to send the exchange to
     * @param exchange the exchange to send
     */
    public Exchange send(Endpoint endpoint, Exchange exchange, Processor resultProcessor) {
        AsyncProducer producer = acquireProducer(endpoint);
        try {
            // now lets dispatch
            LOG.debug(">>>> {} {}", endpoint, exchange);

            // set property which endpoint we send to
            exchange.setProperty(Exchange.TO_ENDPOINT, endpoint.getEndpointUri());

            // send the exchange using the processor
            StopWatch watch = null;
            try {
                if (eventNotifierEnabled) {
                    boolean sending = EventHelper.notifyExchangeSending(exchange.getContext(), exchange, endpoint);
                    if (sending) {
                        watch = new StopWatch();
                    }
                }

                // invoke the synchronous method
                internalProcessor.process(exchange, producer, resultProcessor);

            } catch (Throwable e) {
                // ensure exceptions is caught and set on the exchange
                exchange.setException(e);
            } finally {
                // emit event that the exchange was sent to the endpoint
                if (eventNotifierEnabled && watch != null) {
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
     * Asynchronously sends an exchange to an endpoint using a supplied
     * {@link Processor} to populate the exchange
     * <p>
     * This method will <b>neither</b> throw an exception <b>nor</b> complete future exceptionally.
     * If processing of the given Exchange failed then the exception is stored on the return Exchange
     *
     * @param endpoint        the endpoint to send the exchange to
     * @param pattern         the message {@link ExchangePattern} such as
     *                        {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @param processor       the transformer used to populate the new exchange
     * @param resultProcessor a processor to process the exchange when the send is complete.
     * @param future          the preexisting future to complete when processing is done or null if to create new one
     * @return future that completes with exchange when processing is done. Either passed into future parameter
     *              or new one if parameter was null
     */
    public CompletableFuture<Exchange> asyncSend(Endpoint endpoint,
                                                 ExchangePattern pattern,
                                                 Processor processor,
                                                 Processor resultProcessor,
                                                 CompletableFuture<Exchange> future) {
        return asyncSendExchange(endpoint, pattern, processor, resultProcessor, null, future);
    }

    /**
     * Asynchronously sends an exchange to an endpoint using a supplied
     * {@link Processor} to populate the exchange
     * <p>
     * This method will <b>neither</b> throw an exception <b>nor</b> complete future exceptionally.
     * If processing of the given Exchange failed then the exception is stored on the return Exchange
     *
     * @param endpoint        the endpoint to send the exchange to
     * @param pattern         the message {@link ExchangePattern} such as
     *                        {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @param processor       the transformer used to populate the new exchange
     * @param resultProcessor a processor to process the exchange when the send is complete.
     * @param exchange        an exchange to use in processing. Exchange will be created if parameter is null.
     * @param future          the preexisting future to complete when processing is done or null if to create new one
     * @return future that completes with exchange when processing is done. Either passed into future parameter
     *              or new one if parameter was null
     */
    public CompletableFuture<Exchange> asyncSendExchange(Endpoint endpoint,
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

    protected CompletableFuture<Exchange> doAsyncSendExchange(Endpoint endpoint,
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

    /**
     * Sends an exchange to an endpoint using a supplied callback supporting the asynchronous routing engine.
     * <p/>
     * If an exception was thrown during processing, it would be set on the given Exchange
     *
     * @param endpoint         the endpoint to send the exchange to
     * @param exchange         the exchange, can be <tt>null</tt> if so then create a new exchange from the producer
     * @param callback         the asynchronous callback
     * @param producerCallback the producer template callback to be executed
     * @return (doneSync) <tt>true</tt> to continue execute synchronously, <tt>false</tt> to continue being executed asynchronously
     */
    public boolean doInAsyncProducer(Endpoint endpoint,
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
                    exchange.setException(new IllegalStateException("No producer, this processor has not been started: " + this));
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
            StopWatch sw = null;
            if (eventNotifierEnabled && exchange != null) {
                boolean sending = EventHelper.notifyExchangeSending(exchange.getContext(), exchange, endpoint);
                if (sending) {
                    sw = new StopWatch();
                }
            }

            // record timing for sending the exchange using the producer
            final StopWatch watch = sw;

            // invoke the callback
            return producerCallback.doInAsyncProducer(producer, exchange, doneSync -> {
                try {
                    if (eventNotifierEnabled && watch != null) {
                        long timeTaken = watch.taken();
                        // emit event that the exchange was sent to the endpoint
                        EventHelper.notifyExchangeSent(exchange.getContext(), exchange, endpoint, timeTaken);
                    }

                    // release back to the pool
                    producers.release(endpoint, producer);
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

    protected boolean asyncDispatchExchange(Endpoint endpoint, AsyncProducer producer,
                                            Processor resultProcessor, Exchange exchange, AsyncCallback callback) {
        // now lets dispatch
        LOG.debug(">>>> {} {}", endpoint, exchange);

        // set property which endpoint we send to
        exchange.setProperty(Exchange.TO_ENDPOINT, endpoint.getEndpointUri());

        // send the exchange using the processor
        try {
            if (eventNotifierEnabled) {
                callback = new EventNotifierCallback(callback, exchange, endpoint);
            }
            // invoke the asynchronous method
            return internalProcessor.process(exchange, callback, producer, resultProcessor);
        } catch (Throwable e) {
            // ensure exceptions is caught and set on the exchange
            exchange.setException(e);
            callback.done(true);
            return true;
        }

    }

    protected AsyncProducer doGetProducer(Endpoint endpoint) throws Exception {
        return producers.acquire(endpoint);
    }

    protected void doStart() throws Exception {
        if (extendedStatistics) {
            int max = maxCacheSize == 0 ? CamelContextHelper.getMaximumCachePoolSize(camelContext) : maxCacheSize;
            statistics = new DefaultEndpointUtilizationStatistics(max);
        }

        ServiceHelper.startServices(producers, statistics);
    }

    protected void doStop() throws Exception {
        // when stopping we intend to shutdown
        ServiceHelper.stopAndShutdownServices(statistics, producers);
        if (statistics != null) {
            statistics.clear();
        }
    }

    /**
     * Returns the current size of the cache
     *
     * @return the current size
     */
    public int size() {
        int size = producers.size();

        LOG.trace("size = {}", size);
        return size;
    }

    /**
     * Gets the maximum cache size (capacity).
     *
     * @return the capacity
     */
    public int getCapacity() {
        return maxCacheSize;
    }

    /**
     * Gets the cache hits statistic
     * <p/>
     * Will return <tt>-1</tt> if it cannot determine this if a custom cache was used.
     *
     * @return the hits
     */
    public long getHits() {
        return producers.getHits();
    }

    /**
     * Gets the cache misses statistic
     * <p/>
     * Will return <tt>-1</tt> if it cannot determine this if a custom cache was used.
     *
     * @return the misses
     */
    public long getMisses() {
        return producers.getMisses();
    }

    /**
     * Gets the cache evicted statistic
     * <p/>
     * Will return <tt>-1</tt> if it cannot determine this if a custom cache was used.
     *
     * @return the evicted
     */
    public long getEvicted() {
        return producers.getEvicted();
    }

    /**
     * Resets the cache statistics
     */
    public void resetCacheStatistics() {
        producers.resetStatistics();
        if (statistics != null) {
            statistics.clear();
        }
    }

    /**
     * Purges this cache
     */
    public synchronized void purge() {
        try {
            producers.stop();
            producers.start();
        } catch (Exception e) {
            LOG.debug("Error restarting producers", e);
        }
        if (statistics != null) {
            statistics.clear();
        }
    }

    /**
     * Cleanup the cache (purging stale entries)
     */
    public void cleanUp() {
        producers.cleanUp();
    }

    public EndpointUtilizationStatistics getEndpointUtilizationStatistics() {
        return statistics;
    }

    @Override
    public String toString() {
        return "ProducerCache for source: " + source + ", capacity: " + getCapacity();
    }

    /**
     * Callback for sending a exchange message to a endpoint using an {@link AsyncProcessor} capable producer.
     * <p/>
     * Using this callback as a template pattern ensures that Camel handles the resource handling and will
     * start and stop the given producer, to avoid resource leaks.
     *
     * @version
     */
    public interface AsyncProducerCallback {

        /**
         * Performs operation on the given producer to send the given exchange.
         *
         * @param asyncProducer   the async producer, is never <tt>null</tt>
         * @param exchange        the exchange to process
         * @param callback        the async callback
         * @return (doneSync) <tt>true</tt> to continue execute synchronously, <tt>false</tt> to continue being executed asynchronously
         */
        boolean doInAsyncProducer(AsyncProducer asyncProducer, Exchange exchange, AsyncCallback callback);
    }
}
