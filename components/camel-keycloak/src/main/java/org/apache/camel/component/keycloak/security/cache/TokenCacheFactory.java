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

import org.apache.camel.component.keycloak.security.KeycloakTokenIntrospector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating token cache instances based on the specified cache type and configuration.
 */
public final class TokenCacheFactory {
    private static final Logger LOG = LoggerFactory.getLogger(TokenCacheFactory.class);

    private TokenCacheFactory() {
        // Utility class
    }

    /**
     * Creates a token cache instance based on the specified type and configuration.
     *
     * @param  cacheType                the type of cache to create
     * @param  ttlSeconds               time-to-live for cache entries in seconds
     * @param  maxSize                  maximum number of entries (only for CAFFEINE type, 0 for unlimited)
     * @param  recordStats              whether to record cache statistics (only for CAFFEINE type)
     * @return                          a TokenCache instance, or null if caching is disabled
     * @throws IllegalArgumentException if the cache type is not supported or dependencies are missing
     */
    public static TokenCache createCache(TokenCacheType cacheType, long ttlSeconds, long maxSize, boolean recordStats) {

        if (cacheType == null) {
            cacheType = TokenCacheType.CONCURRENT_MAP;
        }

        switch (cacheType) {
            case CONCURRENT_MAP:
                LOG.debug("Creating ConcurrentMap token cache");
                return new ConcurrentMapTokenCache(ttlSeconds);

            case CAFFEINE:
                LOG.debug("Creating Caffeine token cache");
                return createCaffeineCache(ttlSeconds, maxSize, recordStats);

            case NONE:
                LOG.debug("Token caching is disabled");
                return new NoOpTokenCache();

            default:
                throw new IllegalArgumentException("Unsupported cache type: " + cacheType);
        }
    }

    /**
     * Creates a token cache instance with default settings (ConcurrentMap type).
     *
     * @param  ttlSeconds time-to-live for cache entries in seconds
     * @return            a TokenCache instance
     */
    public static TokenCache createCache(long ttlSeconds) {
        return createCache(TokenCacheType.CONCURRENT_MAP, ttlSeconds, 0, false);
    }

    private static TokenCache createCaffeineCache(long ttlSeconds, long maxSize, boolean recordStats) {
        try {
            // Check if Caffeine is available on the classpath
            Class.forName("com.github.benmanes.caffeine.cache.Caffeine");
            return new CaffeineTokenCache(ttlSeconds, maxSize, recordStats);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(
                    "Caffeine cache type selected but caffeine dependency is not available. "
                            + "Add caffeine to your dependencies or use CONCURRENT_MAP cache type.",
                    e);
        }
    }

    /**
     * No-op cache implementation that doesn't cache anything.
     */
    private static class NoOpTokenCache implements TokenCache {

        @Override
        public KeycloakTokenIntrospector.IntrospectionResult get(String token) {
            return null;
        }

        @Override
        public void put(String token, KeycloakTokenIntrospector.IntrospectionResult result) {
            // No-op
        }

        @Override
        public void remove(String token) {
            // No-op
        }

        @Override
        public void clear() {
            // No-op
        }

        @Override
        public long size() {
            return 0;
        }

        @Override
        public CacheStats getStats() {
            return new CacheStats(0, 0, 0);
        }
    }
}
