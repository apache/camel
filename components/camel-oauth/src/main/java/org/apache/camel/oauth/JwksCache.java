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
 * <p/>
 * The cache is a JVM-wide singleton shared by all Camel contexts in the same classloader. Entries are keyed by JWKS
 * endpoint URL and contain public key material only.
 */
final class JwksCache {

    private static final Logger LOG = LoggerFactory.getLogger(JwksCache.class);
    private static final JwksCache INSTANCE = new JwksCache();
    private static final int MAX_JWKS_SIZE_BYTES = 512 * 1024;
    private static final long MIN_FORCED_REFRESH_INTERVAL_MILLIS = 30_000L;
    private static final long MIN_NORMAL_REFRESH_RETRY_INTERVAL_MILLIS = 30_000L;

    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> forcedRefreshAttempts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, FailureRecord> normalRefreshFailures = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> refreshLocks = new ConcurrentHashMap<>();
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
        Object lock = refreshLocks.computeIfAbsent(jwksEndpoint, key -> new Object());
        synchronized (lock) {
            CacheEntry existing = cache.get(jwksEndpoint);
            long now = now();
            if (existing != null && !canAttemptRefresh(jwksEndpoint, now)) {
                LOG.debug("Skipping forced JWKS refresh from {} because a refresh was attempted recently", jwksEndpoint);
                return existing.jwkSet;
            }
            LOG.debug("Forcing JWKS refresh from {}", jwksEndpoint);
            recordRefreshAttempt(jwksEndpoint, now);
            CacheEntry refreshed = fetch(jwksEndpoint, connectTimeoutMs, readTimeoutMs, now);
            normalRefreshFailures.remove(jwksEndpoint);
            cache.put(jwksEndpoint, refreshed);
            return refreshed.jwkSet;
        }
    }

    private JWKSet fetchAndCache(String jwksEndpoint, long ttlSeconds, int connectTimeoutMs, int readTimeoutMs) {
        Object lock = refreshLocks.computeIfAbsent(jwksEndpoint, key -> new Object());
        synchronized (lock) {
            CacheEntry existing = cache.get(jwksEndpoint);
            long now = now();
            if (existing != null && !existing.isExpired(ttlSeconds, now)) {
                return existing.jwkSet;
            }
            FailureRecord recentFailure = recentNormalRefreshFailure(jwksEndpoint, now);
            if (recentFailure != null) {
                if (existing != null) {
                    // serve-stale-on-error: the cache holds public key material only, so an expired entry is
                    // preferable to failing all requests while the identity provider is unreachable
                    LOG.debug("Serving stale JWKS from {} while refresh is rate-limited", jwksEndpoint);
                    return existing.jwkSet;
                }
                throw new OAuthException(
                        "JWKS fetch from " + jwksEndpoint + " was attempted recently", recentFailure.cause);
            }
            try {
                CacheEntry refreshed = fetch(jwksEndpoint, connectTimeoutMs, readTimeoutMs, now);
                normalRefreshFailures.remove(jwksEndpoint);
                cache.put(jwksEndpoint, refreshed);
                return refreshed.jwkSet;
            } catch (RuntimeException e) {
                normalRefreshFailures.put(jwksEndpoint, new FailureRecord(now, e));
                if (existing != null) {
                    LOG.warn("JWKS refresh from {} failed, using cached keys: {}", jwksEndpoint, e.getMessage());
                    return existing.jwkSet;
                }
                throw e;
            }
        }
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
        refreshLocks.clear();
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

    private FailureRecord recentNormalRefreshFailure(String jwksEndpoint, long now) {
        FailureRecord failure = normalRefreshFailures.get(jwksEndpoint);
        if (failure != null && now - failure.failedAtMillis < MIN_NORMAL_REFRESH_RETRY_INTERVAL_MILLIS) {
            return failure;
        }
        return null;
    }

    private static final class FailureRecord {
        final long failedAtMillis;
        final Throwable cause;

        FailureRecord(long failedAtMillis, Throwable cause) {
            this.failedAtMillis = failedAtMillis;
            this.cause = cause;
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
