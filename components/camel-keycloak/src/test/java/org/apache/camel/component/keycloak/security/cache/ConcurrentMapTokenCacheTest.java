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
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.keycloak.security.KeycloakTokenIntrospector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
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

    @Test
    void testExpiredResultNotServed() {
        // A result whose token has already expired must not be served, even while the configured TTL has not elapsed.
        Map<String, Object> claims = new HashMap<>();
        claims.put("active", true);
        claims.put("sub", "test-user");
        claims.put("exp", System.currentTimeMillis() / 1000 - 60); // expired 60 seconds ago
        KeycloakTokenIntrospector.IntrospectionResult expired
                = new KeycloakTokenIntrospector.IntrospectionResult(claims);

        cache.put("expired-token", expired);

        assertNull(cache.get("expired-token"));
    }

    @Test
    void testResultWithFutureExpirationServed() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("active", true);
        claims.put("sub", "test-user");
        claims.put("exp", System.currentTimeMillis() / 1000 + 300); // valid for 5 more minutes
        KeycloakTokenIntrospector.IntrospectionResult valid
                = new KeycloakTokenIntrospector.IntrospectionResult(claims);

        cache.put("valid-token", valid);

        KeycloakTokenIntrospector.IntrospectionResult retrieved = cache.get("valid-token");
        assertNotNull(retrieved);
        assertTrue(retrieved.isActive());
    }

    @Test
    void testResultExpiringBeforeTtlNotServedAfterExp() {
        // The token's exp lands inside the TTL window (~2s vs a 300s TTL), so the entry must expire at exp,
        // not at the configured TTL. This exercises effectiveTtlMillis()'s min(ttl, remaining): replacing that
        // with a plain ttlMillis would keep the entry served for 300s and fail this test.
        ConcurrentMapTokenCache longTtlCache = new ConcurrentMapTokenCache(300);
        Map<String, Object> claims = new HashMap<>();
        claims.put("active", true);
        claims.put("sub", "test-user");
        claims.put("exp", System.currentTimeMillis() / 1000 + 2); // expires in ~2 seconds
        KeycloakTokenIntrospector.IntrospectionResult shortLived
                = new KeycloakTokenIntrospector.IntrospectionResult(claims);

        longTtlCache.put("short-lived-token", shortLived);
        assertNotNull(longTtlCache.get("short-lived-token"));

        await().atMost(10, TimeUnit.SECONDS).until(() -> longTtlCache.get("short-lived-token") == null);
    }
}
