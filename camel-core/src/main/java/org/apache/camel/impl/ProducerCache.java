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
import java.util.concurrent.CompletableFuture;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.AsyncProducerCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ProducerCallback;
import org.apache.camel.ServicePoolAware;
import org.apache.camel.processor.CamelInternalProcessor;
import org.apache.camel.processor.SharedCamelInternalProcessor;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.spi.ServicePool;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorConverterHelper;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.EventHelper;
import org.apache.camel.util.LRUCache;
import org.apache.camel.util.LRUCacheFactory;
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
    private final ServicePool<Endpoint, Producer> pool;
    private final Map<String, Producer> producers;
    private final Object source;
    private final SharedCamelInternalProcessor internalProcessor;

    private EndpointUtilizationStatistics statistics;
    private boolean eventNotifierEnabled = true;
    private boolean extendedStatistics;
    private int maxCacheSize;
    private boolean stopServicePool;

    public ProducerCache(Object source, CamelContext camelContext) {
        this(source, camelContext, CamelContextHelper.getMaximumCachePoolSize(camelContext));
    }

    public ProducerCache(Object source, CamelContext camelContext, int cacheSize) {
        this(source, camelContext, null, createLRUCache(cacheSize));
    }

    public ProducerCache(Object source, CamelContext camelContext, Map<String, Producer> cache) {
        this(source, camelContext, null, cache);
    }

    public ProducerCache(Object source, CamelContext camelContext, ServicePool<Endpoint, Producer> producerServicePool, Map<String, Producer> cache) {
        this.source = source;
        this.camelContext = camelContext;
        if (producerServicePool == null) {
            // use shared producer pool which lifecycle is managed by CamelContext
            this.pool = camelContext.getProducerServicePool();
            this.stopServicePool = false;
        } else {
            this.pool = producerServicePool;
            this.stopServicePool = true;
        }
        this.producers = cache;
        if (producers instanceof LRUCache) {
            maxCacheSize = ((LRUCache) producers).getMaxCacheSize();
        }

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

    /**
     * Creates the {@link LRUCache} to be used.
     * <p/>
     * This implementation returns a {@link LRUCache} instance.

     * @param cacheSize the cache size
     * @return the cache
     */
    @SuppressWarnings("unchecked")
    protected static LRUCache<String, Producer> createLRUCache(int cacheSize) {
        // Use a regular cache as we want to ensure that the lifecycle of the producers
        // being cache is properly handled, such as they are stopped when being evicted
        // or when this cache is stopped. This is needed as some producers requires to
        // be stopped so they can shutdown internal resources that otherwise may cause leaks
        return LRUCacheFactory.newLRUCache(cacheSize);
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
     * {@link #releaseProducer(org.apache.camel.Endpoint, org.apache.camel.Producer)} method.
     *
     * @param endpoint the endpoint
     * @return the producer
     */
    public Producer acquireProducer(Endpoint endpoint) {
        return doGetProducer(endpoint, true);
    }

    /**
     * Releases an acquired producer back after usage.
     *
     * @param endpoint the endpoint
     * @param producer the producer to release
     * @throws Exception can be thrown if error stopping producer if that was needed.
     */
    public void releaseProducer(Endpoint endpoint, Producer producer) throws Exception {
        if (producer instanceof ServicePoolAware) {
            // release back to the pool
            pool.release(endpoint, producer);
        } else if (!producer.isSingleton()) {
            // stop and shutdown non-singleton producers as we should not leak resources
            ServiceHelper.stopAndShutdownService(producer);
        }
    }

    /**
     * Starts the {@link Producer} to be used for sending to the given endpoint
     * <p/>
     * This can be used to early start the {@link Producer} to ensure it can be created,
     * such as when Camel is started. This allows to fail fast in case the {@link Producer}
     * could not be started.
     *
     * @param endpoint the endpoint to send the exchange to
     * @throws Exception is thrown if failed to create or start the {@link Producer}
     */
    public void startProducer(Endpoint endpoint) throws Exception {
        Producer producer = acquireProducer(endpoint);
        releaseProducer(endpoint, producer);
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
    public void send(Endpoint endpoint, Exchange exchange) {
        sendExchange(endpoint, null, null, null, exchange);
    }

    /**
     * Sends an exchange to an endpoint using a supplied
     * {@link Processor} to populate the exchange
     * <p>
     * This method will <b>not</b> throw an exception. If processing of the given
     * Exchange failed then the exception is stored on the return Exchange
     *
     * @param endpoint the endpoint to send the exchange to
     * @param processor the transformer used to populate the new exchange
     * @throws org.apache.camel.CamelExecutionException is thrown if sending failed
     * @return the exchange
     */
    public Exchange send(Endpoint endpoint, Processor processor) {
        return sendExchange(endpoint, null, processor, null, null);
    }

    /**
     * Sends an exchange to an endpoint using a supplied
     * {@link Processor} to populate the exchange
     * <p>
     * This method will <b>not</b> throw an exception. If processing of the given
     * Exchange failed then the exception is stored on the return Exchange
     *
     * @param endpoint the endpoint to send the exchange to
     * @param pattern the message {@link ExchangePattern} such as
     *   {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @param processor the transformer used to populate the new exchange
     * @return the exchange
     */
    public Exchange send(Endpoint endpoint, ExchangePattern pattern, Processor processor) {
        return sendExchange(endpoint, pattern, processor, null, null);
    }

    /**
     * Sends an exchange to an endpoint using a supplied
     * {@link Processor} to populate the exchange
     * <p>
     * This method will <b>not</b> throw an exception. If processing of the given
     * Exchange failed then the exception is stored on the return Exchange
     *
     * @param endpoint the endpoint to send the exchange to
     * @param pattern the message {@link ExchangePattern} such as
     *   {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut}
     * @param processor the transformer used to populate the new exchange
     * @param resultProcessor a processor to process the exchange when the send is complete.
     * @return the exchange
     */
    public Exchange send(Endpoint endpoint, ExchangePattern pattern, Processor processor, Processor resultProcessor) {
        return sendExchange(endpoint, pattern, processor, resultProcessor, null);
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
    public CompletableFuture<Exchange> asyncSend(Endpoint endpoint, ExchangePattern pattern, Processor processor, Processor resultProcessor,
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
    public CompletableFuture<Exchange> asyncSendExchange(final Endpoint endpoint, ExchangePattern pattern,
                                                         final Processor processor, final Processor resultProcessor, Exchange exchange,
                                                         CompletableFuture<Exchange> future) {
        AsyncCallbackToCompletableFutureAdapter<Exchange> futureAdapter = new AsyncCallbackToCompletableFutureAdapter<>(future, exchange);
        doInAsyncProducer(endpoint, exchange, pattern, futureAdapter,
            (producer, asyncProducer, innerExchange, exchangePattern, producerCallback) -> {
                if (innerExchange == null) {
                    innerExchange = pattern != null
                            ? producer.getEndpoint().createExchange(pattern)
                            : producer.getEndpoint().createExchange();
                    futureAdapter.setResult(innerExchange);
                }

                if (processor != null) {
                    // lets populate using the processor callback
                    AsyncProcessor asyncProcessor = AsyncProcessorConverterHelper.convert(processor);
                    try {
                        final Exchange finalExchange = innerExchange;
                        asyncProcessor.process(innerExchange,
                            doneSync -> asyncDispatchExchange(endpoint, producer, resultProcessor,
                                    finalExchange, producerCallback));
                        return false;
                    } catch (Throwable e) {
                        // populate failed so return
                        innerExchange.setException(e);
                        producerCallback.done(true);
                        return true;
                    }
                }

                return asyncDispatchExchange(endpoint, producer, resultProcessor, innerExchange, producerCallback);
            });
        return futureAdapter.getFuture();
    }

    /**
     * Sends an exchange to an endpoint using a supplied callback, using the synchronous processing.
     * <p/>
     * If an exception was thrown during processing, it would be set on the given Exchange
     *
     * @param endpoint  the endpoint to send the exchange to
     * @param exchange  the exchange, can be <tt>null</tt> if so then create a new exchange from the producer
     * @param pattern   the exchange pattern, can be <tt>null</tt>
     * @param callback  the callback
     * @return the response from the callback
     * @see #doInAsyncProducer(org.apache.camel.Endpoint, org.apache.camel.Exchange, org.apache.camel.ExchangePattern, org.apache.camel.AsyncCallback, org.apache.camel.AsyncProducerCallback)
     */
    public <T> T doInProducer(Endpoint endpoint, Exchange exchange, ExchangePattern pattern, ProducerCallback<T> callback) {
        T answer = null;

        // get the producer and we do not mind if its pooled as we can handle returning it back to the pool
        Producer producer = doGetProducer(endpoint, true);

        if (producer == null) {
            if (isStopped()) {
                LOG.warn("Ignoring exchange sent after processor is stopped: " + exchange);
                return null;
            } else {
                throw new IllegalStateException("No producer, this processor has not been started: " + this);
            }
        }

        try {
            // invoke the callback
            answer = callback.doInProducer(producer, exchange, pattern);
        } catch (Throwable e) {
            if (exchange != null) {
                exchange.setException(e);
            }
        } finally {
            if (producer instanceof ServicePoolAware) {
                // release back to the pool
                pool.release(endpoint, producer);
            } else if (!producer.isSingleton()) {
                // stop and shutdown non-singleton producers as we should not leak resources
                try {
                    ServiceHelper.stopAndShutdownService(producer);
                } catch (Exception e) {
                    // ignore and continue
                    LOG.warn("Error stopping/shutting down producer: " + producer, e);
                }
            }
        }

        return answer;
    }

    /**
     * Sends an exchange to an endpoint using a supplied callback supporting the asynchronous routing engine.
     * <p/>
     * If an exception was thrown during processing, it would be set on the given Exchange
     *
     * @param endpoint         the endpoint to send the exchange to
     * @param exchange         the exchange, can be <tt>null</tt> if so then create a new exchange from the producer
     * @param pattern          the exchange pattern, can be <tt>null</tt>
     * @param callback         the asynchronous callback
     * @param producerCallback the producer template callback to be executed
     * @return (doneSync) <tt>true</tt> to continue execute synchronously, <tt>false</tt> to continue being executed asynchronously
     */
    public boolean doInAsyncProducer(final Endpoint endpoint, final Exchange exchange, final ExchangePattern pattern,
                                     final AsyncCallback callback, final AsyncProducerCallback producerCallback) {

        Producer target;
        try {
            // get the producer and we do not mind if its pooled as we can handle returning it back to the pool
            target = doGetProducer(endpoint, true);

            if (target == null) {
                if (isStopped()) {
                    LOG.warn("Ignoring exchange sent after processor is stopped: " + exchange);
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

        final Producer producer = target;

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
            AsyncProcessor asyncProcessor = AsyncProcessorConverterHelper.convert(producer);
            return producerCallback.doInAsyncProducer(producer, asyncProcessor, exchange, pattern, doneSync -> {
                try {
                    if (eventNotifierEnabled && watch != null) {
                        long timeTaken = watch.taken();
                        // emit event that the exchange was sent to the endpoint
                        EventHelper.notifyExchangeSent(exchange.getContext(), exchange, endpoint, timeTaken);
                    }

                    if (producer instanceof ServicePoolAware) {
                        // release back to the pool
                        pool.release(endpoint, producer);
                    } else if (!producer.isSingleton()) {
                        // stop and shutdown non-singleton producers as we should not leak resources
                        try {
                            ServiceHelper.stopAndShutdownService(producer);
                        } catch (Exception e) {
                            // ignore and continue
                            LOG.warn("Error stopping/shutting down producer: " + producer, e);
                        }
                    }
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

    protected boolean asyncDispatchExchange(final Endpoint endpoint, Producer producer,
                                            final Processor resultProcessor, Exchange exchange, AsyncCallback callback) {
        // now lets dispatch
        LOG.debug(">>>> {} {}", endpoint, exchange);

        // set property which endpoint we send to
        exchange.setProperty(Exchange.TO_ENDPOINT, endpoint.getEndpointUri());

        // send the exchange using the processor
        try {
            if (eventNotifierEnabled) {
                callback = new EventNotifierCallback(callback, exchange, endpoint);
            }
            AsyncProcessor target = prepareProducer(producer);
            // invoke the asynchronous method
            return internalProcessor.process(exchange, callback, target, resultProcessor);
        } catch (Throwable e) {
            // ensure exceptions is caught and set on the exchange
            exchange.setException(e);
            callback.done(true);
            return true;
        }

    }

    protected Exchange sendExchange(final Endpoint endpoint, ExchangePattern pattern,
                                    final Processor processor, final Processor resultProcessor, Exchange exchange) {
        return doInProducer(endpoint, exchange, pattern, new ProducerCallback<Exchange>() {
            public Exchange doInProducer(Producer producer, Exchange exchange, ExchangePattern pattern) {
                if (exchange == null) {
                    exchange = pattern != null ? producer.getEndpoint().createExchange(pattern) : producer.getEndpoint().createExchange();
                }

                if (processor != null) {
                    // lets populate using the processor callback
                    try {
                        processor.process(exchange);
                    } catch (Throwable e) {
                        // populate failed so return
                        exchange.setException(e);
                        return exchange;
                    }
                }

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

                    AsyncProcessor target = prepareProducer(producer);
                    // invoke the synchronous method
                    internalProcessor.process(exchange, target, resultProcessor);

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
            }
        });
    }

    protected AsyncProcessor prepareProducer(Producer producer) {
        return AsyncProcessorConverterHelper.convert(producer);
    }

    protected synchronized Producer doGetProducer(Endpoint endpoint, boolean pooled) {
        String key = endpoint.getEndpointUri();
        Producer answer = producers.get(key);
        if (pooled && answer == null) {
            // try acquire from connection pool
            answer = pool.acquire(endpoint);
        }

        if (answer == null) {
            // create a new producer
            try {
                answer = endpoint.createProducer();
                // add as service to CamelContext so its managed via JMX
                boolean add = answer.isSingleton() || answer instanceof ServicePoolAware;
                if (add) {
                    // (false => we and handling the lifecycle of the producer in this cache)
                    getCamelContext().addService(answer, false);
                } else {
                    // fallback and start producer manually
                    ServiceHelper.startService(answer);
                }
            } catch (Throwable e) {
                throw new FailedToCreateProducerException(endpoint, e);
            }

            // add producer to cache or pool if applicable
            if (pooled && answer instanceof ServicePoolAware) {
                LOG.debug("Adding to producer service pool with key: {} for producer: {}", endpoint, answer);
                answer = pool.addAndAcquire(endpoint, answer);
            } else if (answer.isSingleton()) {
                LOG.debug("Adding to producer cache with key: {} for producer: {}", endpoint, answer);
                producers.put(key, answer);
            }
        }

        if (answer != null) {
            // record statistics
            if (extendedStatistics) {
                statistics.onHit(key);
            }
        }

        return answer;
    }

    protected void doStart() throws Exception {
        if (extendedStatistics) {
            int max = maxCacheSize == 0 ? CamelContextHelper.getMaximumCachePoolSize(camelContext) : maxCacheSize;
            statistics = new DefaultEndpointUtilizationStatistics(max);
        }

        ServiceHelper.startServices(producers.values());
        ServiceHelper.startServices(statistics, pool);
    }

    protected void doStop() throws Exception {
        // when stopping we intend to shutdown
        ServiceHelper.stopAndShutdownService(statistics);
        if (stopServicePool) {
            ServiceHelper.stopAndShutdownService(pool);
        }
        try {
            ServiceHelper.stopAndShutdownServices(producers.values());
        } finally {
            // ensure producers are removed, and also from JMX
            for (Producer producer : producers.values()) {
                getCamelContext().removeService(producer);
            }
        }
        producers.clear();
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
        size += pool.size();

        LOG.trace("size = {}", size);
        return size;
    }

    /**
     * Gets the maximum cache size (capacity).
     * <p/>
     * Will return <tt>-1</tt> if it cannot determine this if a custom cache was used.
     *
     * @return the capacity
     */
    public int getCapacity() {
        int capacity = -1;
        if (producers instanceof LRUCache) {
            LRUCache<String, Producer> cache = (LRUCache<String, Producer>) producers;
            capacity = cache.getMaxCacheSize();
        }
        return capacity;
    }

    /**
     * Gets the cache hits statistic
     * <p/>
     * Will return <tt>-1</tt> if it cannot determine this if a custom cache was used.
     *
     * @return the hits
     */
    public long getHits() {
        long hits = -1;
        if (producers instanceof LRUCache) {
            LRUCache<String, Producer> cache = (LRUCache<String, Producer>) producers;
            hits = cache.getHits();
        }
        return hits;
    }

    /**
     * Gets the cache misses statistic
     * <p/>
     * Will return <tt>-1</tt> if it cannot determine this if a custom cache was used.
     *
     * @return the misses
     */
    public long getMisses() {
        long misses = -1;
        if (producers instanceof LRUCache) {
            LRUCache<String, Producer> cache = (LRUCache<String, Producer>) producers;
            misses = cache.getMisses();
        }
        return misses;
    }

    /**
     * Gets the cache evicted statistic
     * <p/>
     * Will return <tt>-1</tt> if it cannot determine this if a custom cache was used.
     *
     * @return the evicted
     */
    public long getEvicted() {
        long evicted = -1;
        if (producers instanceof LRUCache) {
            LRUCache<String, Producer> cache = (LRUCache<String, Producer>) producers;
            evicted = cache.getEvicted();
        }
        return evicted;
    }

    /**
     * Resets the cache statistics
     */
    public void resetCacheStatistics() {
        if (producers instanceof LRUCache) {
            LRUCache<String, Producer> cache = (LRUCache<String, Producer>) producers;
            cache.resetStatistics();
        }
        if (statistics != null) {
            statistics.clear();
        }
    }

    /**
     * Purges this cache
     */
    public synchronized void purge() {
        producers.clear();
        pool.purge();
        if (statistics != null) {
            statistics.clear();
        }
    }

    /**
     * Cleanup the cache (purging stale entries)
     */
    public void cleanUp() {
        if (producers instanceof LRUCache) {
            LRUCache<String, Producer> cache = (LRUCache<String, Producer>) producers;
            cache.cleanUp();
        }
    }

    public EndpointUtilizationStatistics getEndpointUtilizationStatistics() {
        return statistics;
    }

    @Override
    public String toString() {
        return "ProducerCache for source: " + source + ", capacity: " + getCapacity();
    }

}
