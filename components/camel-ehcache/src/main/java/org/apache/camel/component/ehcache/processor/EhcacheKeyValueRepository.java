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
package org.apache.camel.component.ehcache.processor;

import java.time.Duration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.ehcache.EhcacheManager;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.KeyValueRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.KeyValueTtlValue;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.ehcache.Cache;
import org.ehcache.CacheManager;

/**
 * A {@link KeyValueRepository} implementation backed by an Ehcache {@link Cache}.
 * <p/>
 * Ehcache does not support per-entry TTL natively (TTL is set at the cache configuration level). This implementation
 * wraps each value in a {@link KeyValueTtlValue} that records the entry's expiration timestamp. Expired entries are
 * removed lazily on access and during key scans, similar to how {@code MemoryKeyValueRepository} handles TTL.
 * <p/>
 * This single implementation can serve as idempotent repository, aggregation repository, and state store via the
 * adapters in {@code camel-support} ({@code KeyValueIdempotentRepository} and {@code KeyValueAggregationRepository}).
 *
 * @since 4.23
 */
@Metadata(label = "bean",
          description = "An Ehcache-based KeyValueRepository with per-entry TTL support.",
          annotations = { "interfaceName=org.apache.camel.spi.KeyValueRepository" })
@Configurer(metadataOnly = true)
@ManagedResource(description = "Ehcache based key-value repository")
public class EhcacheKeyValueRepository extends ServiceSupport implements KeyValueRepository {

    private Cache<String, KeyValueTtlValue> cache;
    private EhcacheManager ehcacheManager;

    @Metadata(description = "Name of cache", defaultValue = "EhcacheKeyValueRepository")
    private String cacheName = EhcacheKeyValueRepository.class.getSimpleName();

    @Metadata(description = "The Ehcache CacheManager to use")
    private CacheManager cacheManager;

    /**
     * Creates a new Ehcache-backed key-value repository. A {@link CacheManager} must be provided before starting.
     */
    public EhcacheKeyValueRepository() {
    }

    /**
     * Creates a new Ehcache-backed key-value repository using the given cache manager.
     */
    public EhcacheKeyValueRepository(CacheManager cacheManager) {
        this(cacheManager, EhcacheKeyValueRepository.class.getSimpleName());
    }

    /**
     * Creates a new Ehcache-backed key-value repository using the given cache manager and cache name.
     */
    public EhcacheKeyValueRepository(CacheManager cacheManager, String cacheName) {
        this.cacheManager = cacheManager;
        this.cacheName = cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    @ManagedAttribute(description = "The cache name")
    public String getCacheName() {
        return cacheName;
    }

    public CacheManager getCacheManager() {
        return cacheManager;
    }

    public void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    @ManagedOperation(description = "Get value by key")
    public Object get(String key) {
        KeyValueTtlValue entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            cache.remove(key);
            return null;
        }
        return entry.value();
    }

    /**
     * Stores a value with optional TTL.
     * <p/>
     * <b>Note:</b> The previous value is read in a separate call before the put. This is not atomic — another thread
     * could modify the entry between the read and the write. The stored value is always correct, but the returned
     * previous value may be stale. Ehcache does not provide a native {@code getAndPut} equivalent for its
     * {@code Cache<K,V>} API.
     */
    @Override
    @ManagedOperation(description = "Put a key-value pair with optional TTL")
    public Object put(String key, Object value, Duration ttl) {
        long expiresAt = hasPositiveTtl(ttl) ? System.currentTimeMillis() + ttl.toMillis() : Long.MAX_VALUE;
        KeyValueTtlValue previous = cache.get(key);
        cache.put(key, new KeyValueTtlValue(value, expiresAt));
        if (previous == null || previous.isExpired()) {
            return null;
        }
        return previous.value();
    }

    @Override
    @ManagedOperation(description = "Delete a key")
    public Object delete(String key) {
        KeyValueTtlValue entry = cache.get(key);
        cache.remove(key);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        return entry.value();
    }

    @Override
    @ManagedOperation(description = "Check if key exists")
    public boolean contains(String key) {
        KeyValueTtlValue entry = cache.get(key);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired()) {
            cache.remove(key);
            return false;
        }
        return true;
    }

    @Override
    public Set<String> keys() {
        Set<String> keys = new HashSet<>();
        Iterator<Cache.Entry<String, KeyValueTtlValue>> it = cache.iterator();
        while (it.hasNext()) {
            Cache.Entry<String, KeyValueTtlValue> entry = it.next();
            if (!entry.getValue().isExpired()) {
                keys.add(entry.getKey());
            } else {
                cache.remove(entry.getKey());
            }
        }
        return Set.copyOf(keys);
    }

    @Override
    @ManagedOperation(description = "Clear all entries")
    public void clear() {
        cache.clear();
    }

    @Override
    @ManagedAttribute(description = "The number of entries in the repository")
    public int size() {
        return keys().size();
    }

    private static boolean hasPositiveTtl(Duration ttl) {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(cacheManager, "cacheManager");
        ehcacheManager = new EhcacheManager(cacheManager, false, null);
        ehcacheManager.start();
        cache = ehcacheManager.getCache(cacheName, String.class, KeyValueTtlValue.class);
    }

    @Override
    protected void doStop() throws Exception {
        if (ehcacheManager != null) {
            ehcacheManager.stop();
        }
    }
}
