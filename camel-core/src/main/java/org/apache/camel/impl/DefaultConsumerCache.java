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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.PollingConsumer;
import org.apache.camel.spi.ConsumerCache;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ServiceHelper;
import org.apache.camel.support.ServiceSupport;

/**
 * Cache containing created {@link org.apache.camel.Consumer}.
 */
public class DefaultConsumerCache extends ServiceSupport implements ConsumerCache {

    private final CamelContext camelContext;
    private final ServicePool<PollingConsumer> consumers;
    private final Object source;

    private EndpointUtilizationStatistics statistics;
    private boolean extendedStatistics;
    private int maxCacheSize;

    public DefaultConsumerCache(Object source, CamelContext camelContext, int cacheSize) {
        this.source = source;
        this.camelContext = camelContext;
        this.maxCacheSize = cacheSize == 0 ? CamelContextHelper.getMaximumCachePoolSize(camelContext) : cacheSize;
        this.consumers = new ServicePool<>(Endpoint::createPollingConsumer, PollingConsumer::getEndpoint, maxCacheSize);
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
     * Releases an acquired producer back after usage.
     *
     * @param endpoint the endpoint
     * @param pollingConsumer the pollingConsumer to release
     */
    public void releasePollingConsumer(Endpoint endpoint, PollingConsumer pollingConsumer) {
        consumers.release(endpoint, pollingConsumer);
    }

    /**
     * Acquires a pooled PollingConsumer which you <b>must</b> release back again after usage using the
     * {@link #releasePollingConsumer(org.apache.camel.Endpoint, org.apache.camel.PollingConsumer)} method.
     *
     * @param endpoint the endpoint
     * @return the PollingConsumer
     */
    public PollingConsumer acquirePollingConsumer(Endpoint endpoint) {
        try {
            PollingConsumer consumer = consumers.acquire(endpoint);
            if (statistics != null) {
                statistics.onHit(endpoint.getEndpointUri());
            }
            return consumer;
        } catch (Throwable e) {
            throw new FailedToCreateConsumerException(endpoint, e);
        }
    }
 
    public Exchange receive(Endpoint endpoint) {
        log.debug("<<<< {}", endpoint);
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
        log.debug("<<<< {}", endpoint);
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
        log.debug("<<<< {}", endpoint);
        PollingConsumer consumer = null;
        try {
            consumer = acquirePollingConsumer(endpoint);
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
        log.trace("size = {}", size);
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
        return consumers.getMaxCacheSize();
    }

    /**
     * Gets the cache hits statistic
     * <p/>
     * Will return <tt>-1</tt> if it cannot determine this if a custom cache was used.
     *
     * @return the hits
     */
    public long getHits() {
        return consumers.getHits();
    }

    /**
     * Gets the cache misses statistic
     * <p/>
     * Will return <tt>-1</tt> if it cannot determine this if a custom cache was used.
     *
     * @return the misses
     */
    public long getMisses() {
        return consumers.getMisses();
    }

    /**
     * Gets the cache evicted statistic
     * <p/>
     * Will return <tt>-1</tt> if it cannot determine this if a custom cache was used.
     *
     * @return the evicted
     */
    public long getEvicted() {
        return consumers.getEvicted();
    }

    /**
     * Resets the cache statistics
     */
    public void resetCacheStatistics() {
        consumers.resetStatistics();
        if (statistics != null) {
            statistics.clear();
        }
    }

    /**
     * Purges this cache
     */
    public synchronized void purge() {
        try {
            consumers.stop();
            consumers.start();
        } catch (Exception e) {
            log.debug("Error restarting consumer pool", e);
        }
        if (statistics != null) {
            statistics.clear();
        }
    }

    /**
     * Cleanup the cache (purging stale entries)
     */
    public void cleanUp() {
        consumers.cleanUp();
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
        ServiceHelper.startService(consumers);
    }

    protected void doStop() throws Exception {
        // when stopping we intend to shutdown
        ServiceHelper.stopAndShutdownServices(statistics, consumers);
        if (statistics != null) {
            statistics.clear();
        }
    }

}
