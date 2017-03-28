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
package org.apache.camel.component.cache;

import net.sf.ehcache.store.MemoryStoreEvictionPolicy;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class CacheConfiguration implements Cloneable {
    @UriPath @Metadata(required = "true")
    private String cacheName;
    @UriParam(defaultValue = "1000")
    private int maxElementsInMemory = 1000;
    @UriParam(defaultValue = "LFU", enums = "LRU,LFU,FIFO")
    private MemoryStoreEvictionPolicy memoryStoreEvictionPolicy = MemoryStoreEvictionPolicy.LFU;
    @UriParam(defaultValue = "true")
    private boolean overflowToDisk = true;
    @UriParam
    @Deprecated
    private String diskStorePath;
    @UriParam
    private boolean eternal;
    @UriParam(defaultValue = "300")
    private long timeToLiveSeconds = 300;
    @UriParam(defaultValue = "300")
    private long timeToIdleSeconds = 300;
    @UriParam
    private boolean diskPersistent;
    @UriParam
    private long diskExpiryThreadIntervalSeconds;
    @UriParam
    private boolean objectCache;
    @UriParam(label = "advanced")
    private CacheEventListenerRegistry eventListenerRegistry = new CacheEventListenerRegistry();
    @UriParam(label = "advanced")
    private CacheLoaderRegistry cacheLoaderRegistry = new CacheLoaderRegistry();

    public CacheConfiguration() {
    }

    public CacheConfiguration copy() {
        try {
            CacheConfiguration copy = (CacheConfiguration) clone();
            // override any properties where a reference copy isn't what we want
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
    
    public String getCacheName() {
        return cacheName;
    }

    /**
     * Name of the cache
     */
    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    public int getMaxElementsInMemory() {
        return maxElementsInMemory;
    }

    /**
     * The number of elements that may be stored in the defined cache in memory.
     */
    public void setMaxElementsInMemory(int maxElementsInMemory) {
        this.maxElementsInMemory = maxElementsInMemory;
    }

    public MemoryStoreEvictionPolicy getMemoryStoreEvictionPolicy() {
        return memoryStoreEvictionPolicy;
    }

    /**
     * Which eviction strategy to use when maximum number of elements in memory is reached. The strategy defines
     * which elements to be removed.
     * <ul>
     *     <li>LRU - Lest Recently Used</li>
     *     <li>LFU - Lest Frequently Used</li>
     *     <li>FIFO - First In First Out</li>
     * </ul>
     */
    public void setMemoryStoreEvictionPolicy(MemoryStoreEvictionPolicy memoryStoreEvictionPolicy) {
        this.memoryStoreEvictionPolicy = memoryStoreEvictionPolicy;
    }

    public boolean isOverflowToDisk() {
        return overflowToDisk;
    }

    /**
     * Specifies whether cache may overflow to disk
     */
    public void setOverflowToDisk(boolean overflowToDisk) {
        this.overflowToDisk = overflowToDisk;
    }

    @Deprecated
    public String getDiskStorePath() {
        return diskStorePath;
    }

    /**
     * This parameter is ignored. CacheManager sets it using setter injection.
     */
    @Deprecated
    public void setDiskStorePath(String diskStorePath) {
        this.diskStorePath = diskStorePath;
    }

    public boolean isEternal() {
        return eternal;
    }

    /**
     * Sets whether elements are eternal. If eternal, timeouts are ignored and the element never expires.
     */
    public void setEternal(boolean eternal) {
        this.eternal = eternal;
    }

    public long getTimeToLiveSeconds() {
        return timeToLiveSeconds;
    }

    /**
     * The maximum time between creation time and when an element expires. Is used only if the element is not eternal
     */
    public void setTimeToLiveSeconds(long timeToLiveSeconds) {
        this.timeToLiveSeconds = timeToLiveSeconds;
    }

    public long getTimeToIdleSeconds() {
        return timeToIdleSeconds;
    }

    /**
     * The maximum amount of time between accesses before an element expires
     */
    public void setTimeToIdleSeconds(long timeToIdleSeconds) {
        this.timeToIdleSeconds = timeToIdleSeconds;
    }

    public boolean isDiskPersistent() {
        return diskPersistent;
    }

    /**
     * Whether the disk store persists between restarts of the application.
     */
    public void setDiskPersistent(boolean diskPersistent) {
        this.diskPersistent = diskPersistent;
    }

    public long getDiskExpiryThreadIntervalSeconds() {
        return diskExpiryThreadIntervalSeconds;
    }

    /**
     * The number of seconds between runs of the disk expiry thread.
     */
    public void setDiskExpiryThreadIntervalSeconds(long diskExpiryThreadIntervalSeconds) {
        this.diskExpiryThreadIntervalSeconds = diskExpiryThreadIntervalSeconds;
    }

    /**
     * To configure event listeners using the CacheEventListenerRegistry
    */
    public void setEventListenerRegistry(CacheEventListenerRegistry eventListenerRegistry) {
        this.eventListenerRegistry = eventListenerRegistry;
    }

    public CacheEventListenerRegistry getEventListenerRegistry() {
        return eventListenerRegistry;
    }

    /**
     * To configure cache loader using the CacheLoaderRegistry
     */
    public void setCacheLoaderRegistry(CacheLoaderRegistry cacheLoaderRegistry) {
        this.cacheLoaderRegistry = cacheLoaderRegistry;
    }

    public CacheLoaderRegistry getCacheLoaderRegistry() {
        return cacheLoaderRegistry;
    }

    public boolean isObjectCache() {
        return objectCache;
    }

    /**
     * Whether to turn on allowing to store non serializable objects in the cache.
     * If this option is enabled then overflow to disk cannot be enabled as well.
     */
    public void setObjectCache(boolean objectCache) {
        this.objectCache = objectCache;
    }
}
