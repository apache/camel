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
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.LRUCache;
import org.apache.camel.util.LRUSoftCache;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cache containing created {@link org.apache.camel.Consumer}.
 *
 * @version 
 */
public class ConsumerCache extends ServiceSupport {
    private static final transient Logger LOG = LoggerFactory.getLogger(ConsumerCache.class);
    private final CamelContext camelContext;
    private final Map<String, PollingConsumer> consumers;
    private final Object source;

    public ConsumerCache(Object source, CamelContext camelContext) {
        this(source, camelContext, CamelContextHelper.getMaximumCachePoolSize(camelContext));
    }

    public ConsumerCache(Object source, CamelContext camelContext, int cacheSize) {
        this(source, camelContext, createLRUCache(cacheSize));
    }

    public ConsumerCache(Object source, CamelContext camelContext, Map<String, PollingConsumer> cache) {
        this.camelContext = camelContext;
        this.consumers = cache;
        this.source = source;
    }

    /**
     * Creates the {@link LRUCache} to be used.
     * <p/>
     * This implementation returns a {@link org.apache.camel.util.LRUSoftCache} instance.

     * @param cacheSize the cache size
     * @return the cache
     */
    protected static LRUCache<String, PollingConsumer> createLRUCache(int cacheSize) {
        // We use a soft reference cache to allow the JVM to re-claim memory if it runs low on memory.
        return new LRUSoftCache<String, PollingConsumer>(cacheSize);
    }

    public synchronized PollingConsumer getConsumer(Endpoint endpoint) {
        String key = endpoint.getEndpointUri();
        PollingConsumer answer = consumers.get(key);
        if (answer == null) {
            try {
                answer = endpoint.createPollingConsumer();
                answer.start();
            } catch (Exception e) {
                throw new FailedToCreateConsumerException(endpoint, e);
            }

            boolean singleton = true;
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
        return answer;
    }

    public Exchange receive(Endpoint endpoint) {
        LOG.debug("<<<< {}", endpoint);

        PollingConsumer consumer = getConsumer(endpoint);
        return consumer.receive();
    }

    public Exchange receive(Endpoint endpoint, long timeout) {
        LOG.debug("<<<< {}", endpoint);

        PollingConsumer consumer = getConsumer(endpoint);
        return consumer.receive(timeout);
    }

    public Exchange receiveNoWait(Endpoint endpoint) {
        LOG.debug("<<<< {}", endpoint);

        PollingConsumer consumer = getConsumer(endpoint);
        return consumer.receiveNoWait();
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
    }

    /**
     * Purges this cache
     */
    public synchronized void purge() {
        consumers.clear();
    }

    @Override
    public String toString() {
        return "ConsumerCache for source: " + source + ", capacity: " + getCapacity();
    }

    protected void doStart() throws Exception {
        ServiceHelper.startServices(consumers.values());
    }

    protected void doStop() throws Exception {
        // when stopping we intend to shutdown
        ServiceHelper.stopAndShutdownServices(consumers.values());
        consumers.clear();
    }

}
