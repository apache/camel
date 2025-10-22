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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.keycloak.security.KeycloakTokenIntrospector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentMapTokenCacheTest {

    private ConcurrentMapTokenCache cache;
    private KeycloakTokenIntrospector.IntrospectionResult testResult;

    @BeforeEach
    void setUp() {
        cache = new ConcurrentMapTokenCache(60); // 60 seconds TTL

        Map<String, Object> claims = new HashMap<>();
        claims.put("active", true);
        claims.put("sub", "test-user");
        claims.put("scope", "openid profile");
        testResult = new KeycloakTokenIntrospector.IntrospectionResult(claims);
    }

    @Test
    void testPutAndGet() {
        String token = "test-token-123";

        cache.put(token, testResult);

        KeycloakTokenIntrospector.IntrospectionResult retrieved = cache.get(token);
        assertNotNull(retrieved);
        assertTrue(retrieved.isActive());
        assertEquals("test-user", retrieved.getSubject());
    }

    @Test
    void testGetNonExistent() {
        KeycloakTokenIntrospector.IntrospectionResult retrieved = cache.get("non-existent");
        assertNull(retrieved);
    }

    @Test
    void testRemove() {
        String token = "test-token-456";

        cache.put(token, testResult);
        assertNotNull(cache.get(token));

        cache.remove(token);
        assertNull(cache.get(token));
    }

    @Test
    void testClear() {
        cache.put("token1", testResult);
        cache.put("token2", testResult);

        assertEquals(2, cache.size());

        cache.clear();

        assertEquals(0, cache.size());
        assertNull(cache.get("token1"));
        assertNull(cache.get("token2"));
    }

    @Test
    void testExpiration() throws InterruptedException {
        ConcurrentMapTokenCache shortCache = new ConcurrentMapTokenCache(1); // 1 second TTL
        String token = "expiring-token";

        shortCache.put(token, testResult);
        assertNotNull(shortCache.get(token));

        // Wait for expiration
        Thread.sleep(1100);

        assertNull(shortCache.get(token));
    }

    @Test
    void testSize() {
        assertEquals(0, cache.size());

        cache.put("token1", testResult);
        assertEquals(1, cache.size());

        cache.put("token2", testResult);
        assertEquals(2, cache.size());

        cache.remove("token1");
        assertEquals(1, cache.size());
    }

    @Test
    void testStats() {
        TokenCache.CacheStats stats = cache.getStats();
        assertNotNull(stats);
        assertEquals(0, stats.getHitCount());
        assertEquals(0, stats.getMissCount());

        // Put and get (hit)
        cache.put("token1", testResult);
        cache.get("token1");

        stats = cache.getStats();
        assertEquals(1, stats.getHitCount());
        assertEquals(0, stats.getMissCount());

        // Get non-existent (miss)
        cache.get("non-existent");

        stats = cache.getStats();
        assertEquals(1, stats.getHitCount());
        assertEquals(1, stats.getMissCount());
        assertEquals(0.5, stats.getHitRate(), 0.01);
    }

    @Test
    void testStatsAfterClear() {
        cache.put("token1", testResult);
        cache.get("token1");

        TokenCache.CacheStats stats = cache.getStats();
        assertTrue(stats.getHitCount() > 0);

        cache.clear();

        stats = cache.getStats();
        assertEquals(0, stats.getHitCount());
        assertEquals(0, stats.getMissCount());
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                String token = "token-" + index;
                cache.put(token, testResult);
                assertNotNull(cache.get(token));
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(threadCount, cache.size());
    }
}
