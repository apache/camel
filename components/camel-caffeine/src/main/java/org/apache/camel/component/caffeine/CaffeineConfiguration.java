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
    @UriParam(enums = "GET,GET_ALL,PUT,PUT_ALL,INVALIDATE,INVALIDATE_ALL,CLEANUP,AS_MAP")
    private String action;
    @UriParam
    private String key;
    @UriParam(label = "advanced")
    private String valueType;
    @UriParam
    private Integer initialCapacity;
    @UriParam
    private Integer maximumSize;
    @UriParam(defaultValue = "SIZE_BASED")
    private EvictionType evictionType = EvictionType.SIZE_BASED;
    @UriParam(defaultValue = "300")
    private int expireAfterAccessTime = 300;
    @UriParam(defaultValue = "300")
    private int expireAfterWriteTime = 300;
    @UriParam(label = "advanced")
    private RemovalListener removalListener;
    @UriParam(label = "advanced")
    private boolean statsEnabled;
    @UriParam(label = "advanced")
    private StatsCounter statsCounter;
    @UriParam(label = "advanced")
    private CacheLoader cacheLoader;

    public CaffeineConfiguration() {
    }

    public boolean isCreateCacheIfNotExist() {
        return createCacheIfNotExist;
    }

    /**
     * Automatic create the Caffeine cache if none has been configured or exists in the registry.
     */
    public void setCreateCacheIfNotExist(boolean createCacheIfNotExist) {
        this.createCacheIfNotExist = createCacheIfNotExist;
    }

    public String getAction() {
        return action;
    }

    /**
     * To configure the default cache action. If an action is set in the message header, then the operation from the
     * header takes precedence.
     */
    public void setAction(String action) {
        this.action = action;
    }

    public String getKey() {
        return key;
    }

    /**
     * To configure the default action key. If a key is set in the message header, then the key from the header takes
     * precedence.
     */
    public void setKey(String key) {
        this.key = key;
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

    public Integer getInitialCapacity() {
        return initialCapacity;
    }

    /**
     * Sets the minimum total size for the internal data structures. Providing a large enough estimate at construction
     * time avoids the need for expensive resizing operations later, but setting this value unnecessarily high wastes
     * memory.
     */
    public void setInitialCapacity(int initialCapacity) {
        this.initialCapacity = initialCapacity;
    }

    public Integer getMaximumSize() {
        return maximumSize;
    }

    /**
     * Specifies the maximum number of entries the cache may contain. Note that the cache may evict an entry before this
     * limit is exceeded or temporarily exceed the threshold while evicting. As the cache size grows close to the
     * maximum, the cache evicts entries that are less likely to be used again. For example, the cache may evict an
     * entry because it hasn't been used recently or very often. When size is zero, elements will be evicted immediately
     * after being loaded into the cache. This can be useful in testing, or to disable caching temporarily without a
     * code change. As eviction is scheduled on the configured executor, tests may instead prefer to configure the cache
     * to execute tasks directly on the same thread.
     */
    public void setMaximumSize(Integer maximumSize) {
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
     * Specifies that each entry should be automatically removed from the cache once a fixed duration has elapsed after
     * the entry's creation, the most recent replacement of its value, or its last read. Access time is reset by all
     * cache read and write operations.
     *
     * The unit is in seconds.
     */
    public void setExpireAfterAccessTime(int expireAfterAccessTime) {
        this.expireAfterAccessTime = expireAfterAccessTime;
    }

    public int getExpireAfterWriteTime() {
        return expireAfterWriteTime;
    }

    /**
     * Specifies that each entry should be automatically removed from the cache once a fixed duration has elapsed after
     * the entry's creation, or the most recent replacement of its value.
     *
     * The unit is in seconds.
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
            return (CaffeineConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
