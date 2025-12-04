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

package org.apache.camel.component.keycloak.security.cache;

import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.camel.component.keycloak.security.KeycloakTokenIntrospector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-performance token cache implementation using Caffeine. This cache provides advanced features including automatic
 * expiration, size-based eviction, and detailed statistics. This implementation is recommended for production use with
 * high throughput requirements.
 */
public class CaffeineTokenCache implements TokenCache {
    private static final Logger LOG = LoggerFactory.getLogger(CaffeineTokenCache.class);

    private final Cache<String, KeycloakTokenIntrospector.IntrospectionResult> cache;

    /**
     * Creates a new Caffeine-based token cache with the specified configuration.
     *
     * @param ttlSeconds  time-to-live for cache entries in seconds
     * @param maxSize     maximum number of entries to cache (0 or negative for unlimited)
     * @param recordStats whether to record cache statistics
     */
    public CaffeineTokenCache(long ttlSeconds, long maxSize, boolean recordStats) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder().expireAfterWrite(ttlSeconds, TimeUnit.SECONDS);

        if (maxSize > 0) {
            builder.maximumSize(maxSize);
        }

        if (recordStats) {
            builder.recordStats();
        }

        this.cache = builder.build();
        LOG.debug(
                "Initialized Caffeine token cache with TTL={}s, maxSize={}, stats={}",
                ttlSeconds,
                maxSize > 0 ? maxSize : "unlimited",
                recordStats);
    }

    /**
     * Creates a new Caffeine-based token cache with default settings (no size limit, stats enabled).
     *
     * @param ttlSeconds time-to-live for cache entries in seconds
     */
    public CaffeineTokenCache(long ttlSeconds) {
        this(ttlSeconds, 0, true);
    }

    @Override
    public KeycloakTokenIntrospector.IntrospectionResult get(String token) {
        KeycloakTokenIntrospector.IntrospectionResult result = cache.getIfPresent(token);
        if (result != null) {
            LOG.trace("Cache hit for token");
        } else {
            LOG.trace("Cache miss for token");
        }
        return result;
    }

    @Override
    public void put(String token, KeycloakTokenIntrospector.IntrospectionResult result) {
        cache.put(token, result);
        LOG.trace("Token introspection result cached");
    }

    @Override
    public void remove(String token) {
        cache.invalidate(token);
        LOG.trace("Token removed from cache");
    }

    @Override
    public void clear() {
        cache.invalidateAll();
        LOG.debug("Cache cleared");
    }

    @Override
    public long size() {
        return cache.estimatedSize();
    }

    @Override
    public void close() {
        cache.invalidateAll();
        cache.cleanUp();
        LOG.debug("Cache closed and cleaned up");
    }

    @Override
    public CacheStats getStats() {
        com.github.benmanes.caffeine.cache.stats.CacheStats caffeineStats = cache.stats();

        return new CacheStats(caffeineStats.hitCount(), caffeineStats.missCount(), caffeineStats.evictionCount());
    }

    /**
     * Returns the underlying Caffeine cache for advanced usage.
     *
     * @return the Caffeine cache instance
     */
    public Cache<String, KeycloakTokenIntrospector.IntrospectionResult> getCaffeineCache() {
        return cache;
    }
}
