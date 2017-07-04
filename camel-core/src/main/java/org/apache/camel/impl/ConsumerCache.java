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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.IsSingleton;
import org.apache.camel.PollingConsumer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ServicePoolAware;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.spi.ServicePool;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.LRUCache;
import org.apache.camel.util.LRUCacheFactory;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cache containing created {@link org.apache.camel.Consumer}.
 *
 * @version 
 */
public class ConsumerCache extends ServiceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ConsumerCache.class);

    private final CamelContext camelContext;
    private final ServicePool<Endpoint, PollingConsumer> pool;
    private final Map<String, PollingConsumer> consumers;
    private final Object source;

    private EndpointUtilizationStatistics statistics;
    private boolean extendedStatistics;
    private int maxCacheSize;

    public ConsumerCache(Object source, CamelContext camelContext) {
        this(source, camelContext, CamelContextHelper.getMaximumCachePoolSize(camelContext));
    }

    public ConsumerCache(Object source, CamelContext camelContext, int cacheSize) {
        this(source, camelContext, createLRUCache(cacheSize));
    }
    
    public ConsumerCache(Object source, CamelContext camelContext, Map<String, PollingConsumer> cache) {
        this(source, camelContext, cache, camelContext.getPollingConsumerServicePool());
    }

    public ConsumerCache(Object source, CamelContext camelContext, Map<String, PollingConsumer> cache, ServicePool<Endpoint, PollingConsumer> pool) {
        this.camelContext = camelContext;
        this.consumers = cache;
        this.source = source;
        this.pool = pool;
        if (consumers instanceof LRUCache) {
            maxCacheSize = ((LRUCache) consumers).getMaxCacheSize();
        }

        // only if JMX is enabled
        if (camelContext.getManagementStrategy().getManagementAgent() != null) {
            this.extendedStatistics = camelContext.getManagementStrategy().getManagementAgent().getStatisticsLevel().isExtended();
        } else {
            this.extendedStatistics = false;
        }
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
    protected static LRUCache<String, PollingConsumer> createLRUCache(int cacheSize) {
        // Use a regular cache as we want to ensure that the lifecycle of the consumers
        // being cache is properly handled, such as they are stopped when being evicted
        // or when this cache is stopped. This is needed as some consumers requires to
        // be stopped so they can shutdown internal resources that otherwise may cause leaks
        return LRUCacheFactory.newLRUCache(cacheSize);
    }
    
    /**
     * Acquires a pooled PollingConsumer which you <b>must</b> release back again after usage using the
     * {@link #releasePollingConsumer(org.apache.camel.Endpoint, org.apache.camel.PollingConsumer)} method.
     *
     * @param endpoint the endpoint
     * @return the PollingConsumer
     */
    public PollingConsumer acquirePollingConsumer(Endpoint endpoint) {
        return doGetPollingConsumer(endpoint, true);
    }

    /**
     * Releases an acquired producer back after usage.
     *
     * @param endpoint the endpoint
     * @param pollingConsumer the pollingConsumer to release
     */
    public void releasePollingConsumer(Endpoint endpoint, PollingConsumer pollingConsumer) {
        if (pollingConsumer instanceof ServicePoolAware) {
            // release back to the pool
            pool.release(endpoint, pollingConsumer);
        } else {
            boolean singleton = false;
            if (pollingConsumer instanceof IsSingleton) {
                singleton = ((IsSingleton) pollingConsumer).isSingleton();
            }
            String key = endpoint.getEndpointUri();
            boolean cached = consumers.containsKey(key);
            if (!singleton || !cached) {
                try {
                    // stop and shutdown non-singleton/non-cached consumers as we should not leak resources
                    if (!singleton) {
                        LOG.debug("Released PollingConsumer: {} is stopped as consumer is not singleton", endpoint);
                    } else {
                        LOG.debug("Released PollingConsumer: {} is stopped as consumer cache is full", endpoint);
                    }
                    ServiceHelper.stopAndShutdownService(pollingConsumer);
                } catch (Throwable ex) {
                    if (ex instanceof RuntimeCamelException) {
                        throw (RuntimeCamelException)ex;
                    } else {
                        throw new RuntimeCamelException(ex);
                    }
                }
            }
        }
    }

    public PollingConsumer getConsumer(Endpoint endpoint) {
        return doGetPollingConsumer(endpoint, true);
    }
    
    protected synchronized PollingConsumer doGetPollingConsumer(Endpoint endpoint, boolean pooled) {
        String key = endpoint.getEndpointUri();
        PollingConsumer answer = consumers.get(key);
        if (pooled && answer == null) {
            pool.acquire(endpoint);
        }  
        
        if (answer == null) {
            try {
                answer = endpoint.createPollingConsumer();
                answer.start();
            } catch (Throwable e) {
                throw new FailedToCreateConsumerException(endpoint, e);
            }
            if (pooled && answer instanceof ServicePoolAware) {
                LOG.debug("Adding to producer service pool with key: {} for producer: {}", endpoint, answer);
                answer = pool.addAndAcquire(endpoint, answer);
            } else {
                boolean singleton = false;
                if (answer instanceof IsSingleton) {
                    singleton = ((IsSingleton) answer).isSingleton();
                }
                if (singleton) {
                    LOG.debug("Adding to consumer cache with key: {} for consumer: {}", endpoint, answer);
                    consumers.put(key, answer);
                } else {
                    LOG.debug("Consumer for endpoint: {} is not singleton and thus not added to consumer cache", key);
                }
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
 
    public Exchange receive(Endpoint endpoint) {
        LOG.debug("<<<< {}", endpoint);
        PollingConsumer consumer = null;
        try {
            consumer = acquirePollingConsumer(endpoint);
            return consumer.receive();
        } finally {
            if (consumer != null) {
                releasePollingConsumer(endpoint, consumer);
            }
        }
    }

    public Exchange receive(Endpoint endpoint, long timeout) {
        LOG.debug("<<<< {}", endpoint);
        PollingConsumer consumer = null;
        try {
            consumer = acquirePollingConsumer(endpoint);
            return consumer.receive(timeout);
        } finally {
            if (consumer != null) {
                releasePollingConsumer(endpoint, consumer);
            }
        }
    }

    public Exchange receiveNoWait(Endpoint endpoint) {
        LOG.debug("<<<< {}", endpoint);
        PollingConsumer consumer = null;
        try {
            consumer = doGetPollingConsumer(endpoint, true);
            return consumer.receiveNoWait();
        } finally {
            if (consumer != null) {
                releasePollingConsumer(endpoint, consumer);
            }
        }
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
     * Returns the current size of the cache
     *
     * @return the current size
     */
    public int size() {
        int size = consumers.size();
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
        if (consumers instanceof LRUCache) {
            LRUCache<String, PollingConsumer> cache = (LRUCache<String, PollingConsumer>)consumers;
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
        if (consumers instanceof LRUCache) {
            LRUCache<String, PollingConsumer> cache = (LRUCache<String, PollingConsumer>)consumers;
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
        if (consumers instanceof LRUCache) {
            LRUCache<String, PollingConsumer> cache = (LRUCache<String, PollingConsumer>)consumers;
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
        if (consumers instanceof LRUCache) {
            LRUCache<String, PollingConsumer> cache = (LRUCache<String, PollingConsumer>)consumers;
            evicted = cache.getEvicted();
        }
        return evicted;
    }

    /**
     * Resets the cache statistics
     */
    public void resetCacheStatistics() {
        if (consumers instanceof LRUCache) {
            LRUCache<String, PollingConsumer> cache = (LRUCache<String, PollingConsumer>)consumers;
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
        consumers.clear();
        if (statistics != null) {
            statistics.clear();
        }
    }

    /**
     * Cleanup the cache (purging stale entries)
     */
    public void cleanUp() {
        if (consumers instanceof LRUCache) {
            LRUCache<String, PollingConsumer> cache = (LRUCache<String, PollingConsumer>)consumers;
            cache.cleanUp();
        }
    }

    public EndpointUtilizationStatistics getEndpointUtilizationStatistics() {
        return statistics;
    }

    @Override
    public String toString() {
        return "ConsumerCache for source: " + source + ", capacity: " + getCapacity();
    }

    protected void doStart() throws Exception {
        if (extendedStatistics) {
            int max = maxCacheSize == 0 ? CamelContextHelper.getMaximumCachePoolSize(camelContext) : maxCacheSize;
            statistics = new DefaultEndpointUtilizationStatistics(max);
        }

        ServiceHelper.startServices(consumers.values());
    }

    protected void doStop() throws Exception {
        // when stopping we intend to shutdown
        ServiceHelper.stopAndShutdownServices(statistics, pool);
        try {
            ServiceHelper.stopAndShutdownServices(consumers.values());
        } finally {
            // ensure consumers are removed, and also from JMX
            for (PollingConsumer consumer : consumers.values()) {
                getCamelContext().removeService(consumer);
            }
        }
        consumers.clear();
        if (statistics != null) {
            statistics.clear();
        }
    }

}
