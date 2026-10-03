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
package org.apache.camel.component.ehcache.processor;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.camel.support.KeyValueTtlValue;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.impl.copy.SerializingCopier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * A cache that stores copies of its values (here a serializing value copier) returns and compares copies of the
 * entries, and a byte array value has no value equality: the compare-and-swap operations must still match the stored
 * entry.
 */
class EhcacheKeyValueRepositoryByValueTest {

    private static final String CACHE_NAME = "test-kvrepo-by-value";

    private CacheManager cacheManager;
    private Cache<String, KeyValueTtlValue> cache;
    private EhcacheKeyValueRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
                .withCache(CACHE_NAME,
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                String.class,
                                KeyValueTtlValue.class,
                                ResourcePoolsBuilder.heap(100))
                                .withValueCopier(SerializingCopier.<KeyValueTtlValue> asCopierClass()))
                .build(true);
        cache = cacheManager.getCache(CACHE_NAME, String.class, KeyValueTtlValue.class);

        repository = new EhcacheKeyValueRepository(cacheManager, CACHE_NAME);
        repository.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        repository.stop();
        cacheManager.close();
    }

    @Test
    void testPutIfAbsentReplacesExpiredByteArrayValue() {
        repository.put("key1", new byte[] { 1 }, Duration.ofMillis(100));
        // wait on the cache itself, as reading through the repository would remove the expired entry
        await().atMost(2, TimeUnit.SECONDS).until(() -> cache.get("key1").isExpired());

        Object previous = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> repository.putIfAbsent("key1", new byte[] { 2 }, null));

        assertThat(previous).isNull();
        assertThat((byte[]) repository.get("key1")).containsExactly(2);
    }

    @Test
    void testGetRemovesExpiredByteArrayValue() {
        repository.put("key1", new byte[] { 1 }, Duration.ofMillis(100));
        await().atMost(2, TimeUnit.SECONDS).until(() -> cache.get("key1").isExpired());

        assertThat(repository.get("key1")).isNull();
        assertThat(cache.get("key1")).isNull();
    }

    @Test
    void testReplaceAndDeleteMatchTheStoredCopy() {
        repository.put("key1", "value1", null);

        assertThat(repository.replace("key1", "value1", "value2", null)).isTrue();
        assertThat(repository.get("key1")).isEqualTo("value2");
        assertThat(repository.delete("key1", "value2")).isTrue();
        assertThat(cache.get("key1")).isNull();
    }
}
