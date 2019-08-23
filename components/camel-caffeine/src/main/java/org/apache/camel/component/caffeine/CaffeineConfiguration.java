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
package org.apache.camel.component.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.github.benmanes.caffeine.cache.stats.StatsCounter;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class CaffeineConfiguration implements Cloneable {
    @UriParam(defaultValue = "true")
    private boolean createCacheIfNotExist = true;
    @UriParam(label = "producer")
    private String action;
    @UriParam(label = "producer")
    private Object key;
    @UriParam(label = "advanced")
    private String keyType;
    @UriParam(label = "advanced")
    private String valueType;
    @UriParam(label = "producer")
    private Cache cache;
    @UriParam(label = "producer")
    private CacheLoader cacheLoader;
    @UriParam(label = "producer")
    private boolean statsEnabled;
    @UriParam(label = "producer", defaultValue = "10000")
    private int initialCapacity = 10000;
    @UriParam(label = "producer", defaultValue = "10000")
    private int maximumSize = 10000;
    @UriParam(label = "producer", defaultValue = "SIZE_BASED")
    private EvictionType evictionType = EvictionType.SIZE_BASED;
    @UriParam(label = "producer", defaultValue = "300")
    private int expireAfterAccessTime = 300;
    @UriParam(label = "producer", defaultValue = "300")
    private int expireAfterWriteTime = 300;
    @UriParam(label = "producer")
    private RemovalListener removalListener;
    @UriParam(label = "producer")
    private StatsCounter statsCounter;

    public CaffeineConfiguration() {
    }

    public boolean isCreateCacheIfNotExist() {
        return createCacheIfNotExist;
    }

    /**
     * Configure if a cache need to be created if it does exist or can't be
     * pre-configured.
     */
    public void setCreateCacheIfNotExist(boolean createCacheIfNotExist) {
        this.createCacheIfNotExist = createCacheIfNotExist;
    }

    public String getAction() {
        return action;
    }

    /**
     * To configure the default cache action. If an action is set in the message
     * header, then the operation from the header takes precedence.
     */
    public void setAction(String action) {
        this.action = action;
    }

    public Object getKey() {
        return key;
    }

    /**
     * To configure the default action key. If a key is set in the message
     * header, then the key from the header takes precedence.
     */
    public void setKey(Object key) {
        this.key = key;
    }

    public String getKeyType() {
        return keyType;
    }

    /**
     * The cache key type, default "java.lang.Object"
     */
    public void setKeyType(String keyType) {
        this.keyType = keyType;
    }

    public String getValueType() {
        return valueType;
    }

    /**
     * The cache value type, default "java.lang.Object"
     */
    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public Cache getCache() {
        return cache;
    }

    /**
     * To configure an already instantiated cache to be used
     */
    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public CacheLoader getCacheLoader() {
        return cacheLoader;
    }

    /**
     * To configure a CacheLoader in case of a LoadCache use
     */
    public void setCacheLoader(CacheLoader cacheLoader) {
        this.cacheLoader = cacheLoader;
    }

    public boolean isStatsEnabled() {
        return statsEnabled;
    }

    /**
     * To enable stats on the cache
     */
    public void setStatsEnabled(boolean statsEnabled) {
        this.statsEnabled = statsEnabled;
    }

    public int getInitialCapacity() {
        return initialCapacity;
    }

    /**
     * Set the initial Capacity for the cache
     */
    public void setInitialCapacity(int initialCapacity) {
        this.initialCapacity = initialCapacity;
    }

    public int getMaximumSize() {
        return maximumSize;
    }

    /**
     * Set the maximum size for the cache
     */
    public void setMaximumSize(int maximumSize) {
        this.maximumSize = maximumSize;
    }

    public EvictionType getEvictionType() {
        return evictionType;
    }

    /**
     * Set the eviction Type for this cache
     */
    public void setEvictionType(EvictionType evictionType) {
        this.evictionType = evictionType;
    }

    public int getExpireAfterAccessTime() {
        return expireAfterAccessTime;
    }

    /**
     * Set the expire After Access Time in case of time based Eviction (in
     * seconds)
     */
    public void setExpireAfterAccessTime(int expireAfterAccessTime) {
        this.expireAfterAccessTime = expireAfterAccessTime;
    }

    public int getExpireAfterWriteTime() {
        return expireAfterWriteTime;
    }

    /**
     * Set the expire After Access Write in case of time based Eviction (in
     * seconds)
     */
    public void setExpireAfterWriteTime(int expireAfterWriteTime) {
        this.expireAfterWriteTime = expireAfterWriteTime;
    }

    public RemovalListener getRemovalListener() {
        return removalListener;
    }

    /**
     * Set a specific removal Listener for the cache
     */
    public void setRemovalListener(RemovalListener removalListener) {
        this.removalListener = removalListener;
    }

    public StatsCounter getStatsCounter() {
        return statsCounter;
    }

    /**
     * Set a specific Stats Counter for the cache stats
     */
    public void setStatsCounter(StatsCounter statsCounter) {
        this.statsCounter = statsCounter;
    }

    // ****************************
    // Cloneable
    // ****************************
    public CaffeineConfiguration copy() {
        try {
            return (CaffeineConfiguration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
