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
package org.apache.camel.oauth;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.util.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe JWKS cache with TTL and key-rotation-aware re-fetch.
 */
final class JwksCache {

    private static final Logger LOG = LoggerFactory.getLogger(JwksCache.class);
    private static final JwksCache INSTANCE = new JwksCache();
    private static final int MAX_JWKS_SIZE_BYTES = 512 * 1024;
    private static final long MIN_FORCED_REFRESH_INTERVAL_MILLIS = 30_000L;
    private static final long MIN_NORMAL_REFRESH_RETRY_INTERVAL_MILLIS = 30_000L;

    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> forcedRefreshAttempts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> normalRefreshFailures = new ConcurrentHashMap<>();
    private volatile LongSupplier currentTimeMillis = System::currentTimeMillis;

    private JwksCache() {
    }

    static JwksCache instance() {
        return INSTANCE;
    }

    JWKSet getJwkSet(String jwksEndpoint, long ttlSeconds, int connectTimeoutMs, int readTimeoutMs) {
        CacheEntry entry = cache.get(jwksEndpoint);
        if (entry != null && !entry.isExpired(ttlSeconds, now())) {
            return entry.jwkSet;
        }
        return fetchAndCache(jwksEndpoint, ttlSeconds, connectTimeoutMs, readTimeoutMs);
    }

    JWKSet refreshJwkSet(String jwksEndpoint, int connectTimeoutMs, int readTimeoutMs) {
        return cache.compute(jwksEndpoint, (key, existing) -> {
            long now = now();
            if (existing != null && !canAttemptRefresh(key, now)) {
                LOG.debug("Skipping forced JWKS refresh from {} because a refresh was attempted recently", key);
                return existing;
            }
            LOG.debug("Forcing JWKS refresh from {}", key);
            recordRefreshAttempt(key, now);
            CacheEntry refreshed = fetch(key, connectTimeoutMs, readTimeoutMs, now);
            normalRefreshFailures.remove(key);
            return refreshed;
        }).jwkSet;
    }

    private JWKSet fetchAndCache(String jwksEndpoint, long ttlSeconds, int connectTimeoutMs, int readTimeoutMs) {
        return cache.compute(jwksEndpoint, (key, existing) -> {
            long now = now();
            if (existing != null && !existing.isExpired(ttlSeconds, now)) {
                return existing;
            }
            assertCanAttemptNormalRefresh(key, now);
            try {
                CacheEntry refreshed = fetch(key, connectTimeoutMs, readTimeoutMs, now);
                normalRefreshFailures.remove(key);
                return refreshed;
            } catch (RuntimeException e) {
                normalRefreshFailures.put(key, now);
                throw e;
            }
        }).jwkSet;
    }

    void put(String endpoint, JWKSet jwkSet) {
        cache.put(endpoint, new CacheEntry(jwkSet, now()));
    }

    void put(String endpoint, JWKSet jwkSet, long fetchedAtMillis) {
        cache.put(endpoint, new CacheEntry(jwkSet, fetchedAtMillis));
    }

    void clear() {
        cache.clear();
        forcedRefreshAttempts.clear();
        normalRefreshFailures.clear();
        currentTimeMillis = System::currentTimeMillis;
    }

    void setCurrentTimeMillisSupplier(LongSupplier currentTimeMillis) {
        this.currentTimeMillis = Objects.requireNonNull(currentTimeMillis, "currentTimeMillis");
    }

    private CacheEntry fetch(String jwksEndpoint, int connectTimeoutMs, int readTimeoutMs, long fetchedAtMillis) {
        try {
            DefaultResourceRetriever retriever = new DefaultResourceRetriever(
                    connectTimeoutMs, readTimeoutMs, MAX_JWKS_SIZE_BYTES);
            Resource resource = retriever.retrieveResource(URI.create(jwksEndpoint).toURL());
            JWKSet jwkSet = JWKSet.parse(resource.getContent());
            LOG.debug("Fetched JWKS from {} ({} keys)", jwksEndpoint, jwkSet.getKeys().size());
            return new CacheEntry(jwkSet, fetchedAtMillis);
        } catch (Exception e) {
            throw new OAuthException("Failed to fetch JWKS from " + jwksEndpoint, e);
        }
    }

    private long now() {
        return currentTimeMillis.getAsLong();
    }

    private boolean canAttemptRefresh(String jwksEndpoint, long now) {
        Long refreshAttemptedAtMillis = forcedRefreshAttempts.get(jwksEndpoint);
        return refreshAttemptedAtMillis == null
                || now - refreshAttemptedAtMillis >= MIN_FORCED_REFRESH_INTERVAL_MILLIS;
    }

    private void recordRefreshAttempt(String jwksEndpoint, long now) {
        forcedRefreshAttempts.put(jwksEndpoint, now);
    }

    private void assertCanAttemptNormalRefresh(String jwksEndpoint, long now) {
        Long failedAt = normalRefreshFailures.get(jwksEndpoint);
        if (failedAt != null && now - failedAt < MIN_NORMAL_REFRESH_RETRY_INTERVAL_MILLIS) {
            throw new OAuthException("JWKS fetch from " + jwksEndpoint + " was attempted recently");
        }
    }

    private static final class CacheEntry {
        final JWKSet jwkSet;
        final long fetchedAtMillis;

        CacheEntry(JWKSet jwkSet, long fetchedAtMillis) {
            this.jwkSet = jwkSet;
            this.fetchedAtMillis = fetchedAtMillis;
        }

        boolean isExpired(long ttlSeconds, long now) {
            return now - fetchedAtMillis > ttlSeconds * 1000L;
        }

    }
}
