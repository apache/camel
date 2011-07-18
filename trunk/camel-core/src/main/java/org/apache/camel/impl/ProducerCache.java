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
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;
import org.apache.camel.processor.UnitOfWorkProducer;
import org.apache.camel.spi.ServicePool;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.EventHelper;
import org.apache.camel.util.LRUCache;
import org.apache.camel.util.LRUSoftCache;
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
    private static final transient Logger LOG = LoggerFactory.getLogger(ProducerCache.class);

    private final CamelContext camelContext;
    private final ServicePool<Endpoint, Producer> pool;
    private final Map<String, Producer> producers;
    private final Object source;

    public ProducerCache(Object source, CamelContext camelContext) {
        this(source, camelContext, CamelContextHelper.getMaximumCachePoolSize(camelContext));
    }

    public ProducerCache(Object source, CamelContext camelContext, int cacheSize) {
        this(source, camelContext, camelContext.getProducerServicePool(), createLRUCache(cacheSize));
    }

    public ProducerCache(Object source, CamelContext camelContext, ServicePool<Endpoint, Producer> producerServicePool, Map<String, Producer> cache) {
        this.source = source;
        this.camelContext = camelContext;
        this.pool = producerServicePool;
        this.producers = cache;
    }

    /**
     * Creates the {@link LRUCache} to be used.
     * <p/>
     * This implementation returns a {@link LRUSoftCache} instance.

     * @param cacheSize the cache size
     * @return the cache
     */
    protected static LRUCache<String, Producer> createLRUCache(int cacheSize) {
        // We use a soft reference cache to allow the JVM to re-claim memory if it runs low on memory.
        return new LRUSoftCache<String, Producer>(cacheSize);
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
            // stop non singleton producers as we should not leak resources
            producer.stop();
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
        sendExchange(endpoint, null, null, exchange);
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
        return sendExchange(endpoint, null, processor, null);
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
        return sendExchange(endpoint, pattern, processor, null);
    }

    /**
     * Sends an exchange to an endpoint using a supplied callback
     * <p/>
     * If an exception was thrown during processing, it would be set on the given Exchange
     *
     * @param endpoint  the endpoint to send the exchange to
     * @param exchange  the exchange, can be <tt>null</tt> if so then create a new exchange from the producer
     * @param pattern   the exchange pattern, can be <tt>null</tt>
     * @param callback  the callback
     * @return the response from the callback
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

        StopWatch watch = null;
        if (exchange != null) {
            // record timing for sending the exchange using the producer
            watch = new StopWatch();
        }

        try {
            // invoke the callback
            answer = callback.doInProducer(producer, exchange, pattern);
        } catch (Throwable e) {
            if (exchange != null) {
                exchange.setException(e);
            }
        } finally {
            if (exchange != null) {
                long timeTaken = watch.stop();
                // emit event that the exchange was sent to the endpoint
                EventHelper.notifyExchangeSent(exchange.getContext(), exchange, endpoint, timeTaken);
            }
            if (producer instanceof ServicePoolAware) {
                // release back to the pool
                pool.release(endpoint, producer);
            } else if (!producer.isSingleton()) {
                // stop non singleton producers as we should not leak resources
                try {
                    ServiceHelper.stopService(producer);
                } catch (Exception e) {
                    // ignore and continue
                    LOG.warn("Error stopping producer: " + producer, e);
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
    public boolean doInAsyncProducer(Endpoint endpoint, Exchange exchange, ExchangePattern pattern, AsyncCallback callback, AsyncProducerCallback producerCallback) {
        boolean sync = true;

        // get the producer and we do not mind if its pooled as we can handle returning it back to the pool
        Producer producer = doGetProducer(endpoint, true);

        if (producer == null) {
            if (isStopped()) {
                LOG.warn("Ignoring exchange sent after processor is stopped: " + exchange);
                return false;
            } else {
                throw new IllegalStateException("No producer, this processor has not been started: " + this);
            }
        }

        StopWatch watch = null;
        if (exchange != null) {
            // record timing for sending the exchange using the producer
            watch = new StopWatch();
        }

        try {
            // invoke the callback
            AsyncProcessor asyncProcessor = AsyncProcessorTypeConverter.convert(producer);
            sync = producerCallback.doInAsyncProducer(producer, asyncProcessor, exchange, pattern, callback);
        } catch (Throwable e) {
            // ensure exceptions is caught and set on the exchange
            if (exchange != null) {
                exchange.setException(e);
            }
        } finally {
            if (exchange != null && exchange.getException() == null) {
                long timeTaken = watch.stop();
                // emit event that the exchange was sent to the endpoint
                EventHelper.notifyExchangeSent(exchange.getContext(), exchange, endpoint, timeTaken);
            }
            if (producer instanceof ServicePoolAware) {
                // release back to the pool
                pool.release(endpoint, producer);
            } else if (!producer.isSingleton()) {
                // stop non singleton producers as we should not leak resources
                try {
                    ServiceHelper.stopService(producer);
                } catch (Exception e) {
                    // ignore and continue
                    LOG.warn("Error stopping producer: " + producer, e);
                }
            }
        }

        return sync;
    }

    protected Exchange sendExchange(final Endpoint endpoint, ExchangePattern pattern,
                                    final Processor processor, Exchange exchange) {
        return doInProducer(endpoint, exchange, pattern, new ProducerCallback<Exchange>() {
            public Exchange doInProducer(Producer producer, Exchange exchange, ExchangePattern pattern) {
                if (exchange == null) {
                    exchange = pattern != null ? producer.createExchange(pattern) : producer.createExchange();
                }

                if (processor != null) {
                    // lets populate using the processor callback
                    try {
                        processor.process(exchange);
                    } catch (Exception e) {
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
                StopWatch watch = new StopWatch();
                try {
                    // ensure we run in an unit of work
                    Producer target = new UnitOfWorkProducer(producer);
                    target.process(exchange);
                } catch (Throwable e) {
                    // ensure exceptions is caught and set on the exchange
                    exchange.setException(e);
                } finally {
                    // emit event that the exchange was sent to the endpoint
                    long timeTaken = watch.stop();
                    EventHelper.notifyExchangeSent(exchange.getContext(), exchange, endpoint, timeTaken);
                }
                return exchange;
            }
        });
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
                // must then start service so producer is ready to be used
                ServiceHelper.startService(answer);
            } catch (Exception e) {
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

        return answer;
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(producers, pool);
        producers.clear();
    }

    protected void doStart() throws Exception {
        ServiceHelper.startServices(pool, producers);
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
            LRUCache cache = (LRUCache) producers;
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
            LRUCache cache = (LRUCache) producers;
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
            LRUCache cache = (LRUCache) producers;
            misses = cache.getMisses();
        }
        return misses;
    }

    /**
     * Resets the cache statistics
     */
    public void resetCacheStatistics() {
        if (producers instanceof LRUCache) {
            LRUCache cache = (LRUCache) producers;
            cache.resetStatistics();
        }
    }

    /**
     * Purges this cache
     */
    public synchronized void purge() {
        producers.clear();
        pool.purge();
    }

    @Override
    public String toString() {
        return "ProducerCache for source: " + source + ", capacity: " + getCapacity();
    }
}
