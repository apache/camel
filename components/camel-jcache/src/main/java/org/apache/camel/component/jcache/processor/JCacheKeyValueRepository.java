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
package org.apache.camel.component.jcache.processor;

import java.time.Duration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.cache.Cache;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.jcache.JCacheConfiguration;
import org.apache.camel.component.jcache.JCacheHelper;
import org.apache.camel.component.jcache.JCacheManager;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.KeyValueRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.KeyValueTtlValue;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * A {@link KeyValueRepository} implementation backed by a JCache (JSR-107) {@link Cache}.
 * <p/>
 * JCache does not support per-entry TTL natively (expiry is configured at the cache level via
 * {@link javax.cache.expiry.ExpiryPolicy}). This implementation wraps each value in a {@link KeyValueTtlValue} that
 * records the entry's expiration timestamp. Expired entries are removed lazily on access and during key scans.
 * <p/>
 * This single implementation can serve as idempotent repository, aggregation repository, and state store via the
 * adapters in {@code camel-support} ({@code KeyValueIdempotentRepository} and {@code KeyValueAggregationRepository}).
 *
 * @since 4.23
 */
@Metadata(label = "bean",
          description = "A JCache-based KeyValueRepository with per-entry TTL support.",
          annotations = { "interfaceName=org.apache.camel.spi.KeyValueRepository" })
@Configurer(metadataOnly = true)
@ManagedResource(description = "JCache based key-value repository")
public class JCacheKeyValueRepository extends ServiceSupport implements CamelContextAware, KeyValueRepository {

    private CamelContext camelContext;
    private Cache<String, KeyValueTtlValue> cache;
    private JCacheManager<String, KeyValueTtlValue> cacheManager;

    @Metadata(description = "Configuration for JCache")
    private JCacheConfiguration configuration;

    /**
     * Creates a new JCache-backed key-value repository with a default configuration.
     */
    public JCacheKeyValueRepository() {
        this.configuration = new JCacheConfiguration();
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public JCacheConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(JCacheConfiguration configuration) {
        this.configuration = configuration;
    }

    public Cache<String, KeyValueTtlValue> getCache() {
        return cache;
    }

    public void setCache(Cache<String, KeyValueTtlValue> cache) {
        this.cache = cache;
    }

    public void setCacheName(String cacheName) {
        configuration.setCacheName(cacheName);
    }

    @ManagedAttribute(description = "The cache name")
    public String getCacheName() {
        return configuration.getCacheName();
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
     * previous value may be stale. JCache does not provide a {@code getAndPut} equivalent that also accepts a custom
     * value type with per-entry TTL.
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
        ObjectHelper.notNull(camelContext, "camelContext");
        ObjectHelper.notNull(configuration, "configuration");

        if (cache != null) {
            cacheManager = new JCacheManager<>(cache);
        } else {
            cacheManager = JCacheHelper.createManager(getCamelContext(), configuration);
            cache = cacheManager.getCache();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (cacheManager != null) {
            cacheManager.close();
        }
    }
}
