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
import com.github.benmanes.caffeine.cache.Expiry;
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
        Caffeine<String, KeycloakTokenIntrospector.IntrospectionResult> builder = Caffeine.newBuilder()
                .expireAfter(new IntrospectionExpiry(TimeUnit.SECONDS.toNanos(ttlSeconds)));

        if (maxSize > 0) {
            builder.maximumSize(maxSize);
        }

        if (recordStats) {
            builder.recordStats();
        }

        this.cache = builder.build();
        LOG.debug("Initialized Caffeine token cache with TTL={}s, maxSize={}, stats={}",
                ttlSeconds, maxSize > 0 ? maxSize : "unlimited", recordStats);
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

        return new CacheStats(
                caffeineStats.hitCount(),
                caffeineStats.missCount(),
                caffeineStats.evictionCount());
    }

    /**
     * Returns the underlying Caffeine cache for advanced usage.
     *
     * @return the Caffeine cache instance
     */
    public Cache<String, KeycloakTokenIntrospector.IntrospectionResult> getCaffeineCache() {
        return cache;
    }

    /**
     * Caffeine expiry policy that bounds each entry's lifetime by the smaller of the configured TTL and the token's own
     * remaining validity ({@code exp}), so a cached introspection result is never returned after the token has expired.
     * Reads do not extend an entry's lifetime.
     */
    private static final class IntrospectionExpiry
            implements Expiry<String, KeycloakTokenIntrospector.IntrospectionResult> {

        private final long maxTtlNanos;

        IntrospectionExpiry(long maxTtlNanos) {
            this.maxTtlNanos = maxTtlNanos;
        }

        @Override
        public long expireAfterCreate(
                String key, KeycloakTokenIntrospector.IntrospectionResult value, long currentTime) {
            return expiryNanos(value);
        }

        @Override
        public long expireAfterUpdate(
                String key, KeycloakTokenIntrospector.IntrospectionResult value, long currentTime, long currentDuration) {
            return expiryNanos(value);
        }

        @Override
        public long expireAfterRead(
                String key, KeycloakTokenIntrospector.IntrospectionResult value, long currentTime, long currentDuration) {
            // Reads must not extend the cached lifetime beyond the token's expiry.
            return currentDuration;
        }

        private long expiryNanos(KeycloakTokenIntrospector.IntrospectionResult value) {
            Long expSeconds = value.getExpiration();
            if (expSeconds == null) {
                return maxTtlNanos;
            }
            long remainingMillis = expSeconds * 1000L - System.currentTimeMillis();
            if (remainingMillis <= 0) {
                // Already expired: expire immediately so the entry is not served.
                return 0L;
            }
            long remainingNanos = TimeUnit.MILLISECONDS.toNanos(remainingMillis);
            if (remainingNanos < 0) {
                // Overflow guard for a far-future exp: fall back to the configured TTL.
                return maxTtlNanos;
            }
            return Math.min(maxTtlNanos, remainingNanos);
        }
    }
}
