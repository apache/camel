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

/**
 * Interface for pluggable token cache implementations. This abstraction allows for different caching strategies and
 * backends to be used for storing token introspection results.
 */
public interface TokenCache {

    /**
     * Retrieves a cached introspection result for the given token.
     *
     * @param  token the access token to look up
     * @return       the cached introspection result, or null if not found or expired
     */
    KeycloakTokenIntrospector.IntrospectionResult get(String token);

    /**
     * Stores an introspection result in the cache.
     *
     * @param token  the access token used as the cache key
     * @param result the introspection result to cache
     */
    void put(String token, KeycloakTokenIntrospector.IntrospectionResult result);

    /**
     * Removes a token from the cache.
     *
     * @param token the access token to remove
     */
    void remove(String token);

    /**
     * Clears all entries from the cache.
     */
    void clear();

    /**
     * Returns the approximate number of entries in the cache.
     *
     * @return the cache size, or -1 if unknown
     */
    long size();

    /**
     * Closes the cache and releases any resources.
     */
    default void close() {
        // Default implementation does nothing
    }

    /**
     * Returns statistics about the cache performance.
     *
     * @return cache statistics, or null if not supported
     */
    default CacheStats getStats() {
        return null;
    }

    /**
     * Cache statistics holder.
     */
    class CacheStats {
        private final long hitCount;
        private final long missCount;
        private final long evictionCount;
        private final double hitRate;

        public CacheStats(long hitCount, long missCount, long evictionCount) {
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.evictionCount = evictionCount;
            long totalRequests = hitCount + missCount;
            this.hitRate = totalRequests == 0 ? 0.0 : (double) hitCount / totalRequests;
        }

        public long getHitCount() {
            return hitCount;
        }

        public long getMissCount() {
            return missCount;
        }

        public long getEvictionCount() {
            return evictionCount;
        }

        public double getHitRate() {
            return hitRate;
        }

        @Override
        public String toString() {
            return String.format("CacheStats{hits=%d, misses=%d, hitRate=%.2f%%, evictions=%d}",
                    hitCount, missCount, hitRate * 100, evictionCount);
        }
    }
}
