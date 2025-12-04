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

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.keycloak.security.KeycloakTokenIntrospector;
import org.junit.jupiter.api.Test;

class TokenCacheFactoryTest {

    @Test
    void testCreateConcurrentMapCache() {
        TokenCache cache = TokenCacheFactory.createCache(TokenCacheType.CONCURRENT_MAP, 60, 0, false);

        assertNotNull(cache);
        assertInstanceOf(ConcurrentMapTokenCache.class, cache);
    }

    @Test
    void testCreateCaffeineCache() {
        TokenCache cache = TokenCacheFactory.createCache(TokenCacheType.CAFFEINE, 60, 100, true);

        assertNotNull(cache);
        assertInstanceOf(CaffeineTokenCache.class, cache);
    }

    @Test
    void testCreateNoOpCache() {
        TokenCache cache = TokenCacheFactory.createCache(TokenCacheType.NONE, 60, 0, false);

        assertNotNull(cache);

        Map<String, Object> claims = new HashMap<>();
        claims.put("active", true);
        KeycloakTokenIntrospector.IntrospectionResult result =
                new KeycloakTokenIntrospector.IntrospectionResult(claims);

        // NoOp cache should not store anything
        cache.put("token", result);
        assertNull(cache.get("token"));
        assertEquals(0, cache.size());
    }

    @Test
    void testCreateCacheWithDefaultType() {
        TokenCache cache = TokenCacheFactory.createCache(60);

        assertNotNull(cache);
        assertInstanceOf(ConcurrentMapTokenCache.class, cache);
    }

    @Test
    void testCreateCacheWithNullType() {
        TokenCache cache = TokenCacheFactory.createCache(null, 60, 0, false);

        assertNotNull(cache);
        assertInstanceOf(ConcurrentMapTokenCache.class, cache);
    }

    @Test
    void testNoOpCacheStats() {
        TokenCache cache = TokenCacheFactory.createCache(TokenCacheType.NONE, 60, 0, false);

        TokenCache.CacheStats stats = cache.getStats();
        assertNotNull(stats);
        assertEquals(0, stats.getHitCount());
        assertEquals(0, stats.getMissCount());
        assertEquals(0, stats.getEvictionCount());
        assertEquals(0.0, stats.getHitRate());
    }

    @Test
    void testNoOpCacheClear() {
        TokenCache cache = TokenCacheFactory.createCache(TokenCacheType.NONE, 60, 0, false);

        // Should not throw exception
        cache.clear();
        cache.remove("any-token");
        cache.close();
    }
}
