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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.component.keycloak.security.KeycloakTokenIntrospector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple in-memory token cache implementation using ConcurrentHashMap with time-based expiration. This is the default
 * cache implementation that provides basic caching without external dependencies.
 */
public class ConcurrentMapTokenCache implements TokenCache {
    private static final Logger LOG = LoggerFactory.getLogger(ConcurrentMapTokenCache.class);

    private final Map<String, CachedEntry> cache;
    private final long ttlMillis;
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong evictionCount = new AtomicLong(0);

    public ConcurrentMapTokenCache(long ttlSeconds) {
        this.cache = new ConcurrentHashMap<>();
        this.ttlMillis = ttlSeconds * 1000;
    }

    @Override
    public KeycloakTokenIntrospector.IntrospectionResult get(String token) {
        CachedEntry entry = cache.get(token);
        if (entry != null) {
            if (!entry.isExpired()) {
                hitCount.incrementAndGet();
                LOG.trace("Cache hit for token");
                return entry.result;
            } else {
                // Remove expired entry
                cache.remove(token);
                evictionCount.incrementAndGet();
                LOG.trace("Cache entry expired and removed");
            }
        }
        missCount.incrementAndGet();
        LOG.trace("Cache miss for token");
        return null;
    }

    @Override
    public void put(String token, KeycloakTokenIntrospector.IntrospectionResult result) {
        cache.put(token, new CachedEntry(result, ttlMillis));
        LOG.trace("Token introspection result cached");
        cleanupExpiredEntries();
    }

    @Override
    public void remove(String token) {
        cache.remove(token);
        LOG.trace("Token removed from cache");
    }

    @Override
    public void clear() {
        cache.clear();
        hitCount.set(0);
        missCount.set(0);
        evictionCount.set(0);
        LOG.debug("Cache cleared");
    }

    @Override
    public long size() {
        return cache.size();
    }

    @Override
    public CacheStats getStats() {
        return new CacheStats(hitCount.get(), missCount.get(), evictionCount.get());
    }

    /**
     * Cleanup expired cache entries to prevent memory leaks. This method is called periodically and removes entries in
     * batches to avoid performance issues.
     */
    private void cleanupExpiredEntries() {
        if (cache.isEmpty()) {
            return;
        }

        // Remove expired entries
        cache.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                evictionCount.incrementAndGet();
                LOG.trace("Removing expired cache entry during cleanup");
                return true;
            }
            return false;
        });
    }

    /**
     * Internal class to hold cached introspection results with expiration time.
     */
    private static class CachedEntry {
        private final KeycloakTokenIntrospector.IntrospectionResult result;
        private final long expirationTime;

        CachedEntry(KeycloakTokenIntrospector.IntrospectionResult result, long ttlMillis) {
            this.result = result;
            this.expirationTime = System.currentTimeMillis() + ttlMillis;
        }

        boolean isExpired() {
            return System.currentTimeMillis() >= expirationTime;
        }
    }
}
