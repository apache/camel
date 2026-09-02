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
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class EhcacheKeyValueRepositoryTest {

    private static final String CACHE_NAME = "test-kvrepo";

    private CacheManager cacheManager;
    private EhcacheKeyValueRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
                .withCache(CACHE_NAME,
                        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                String.class,
                                EhcacheKeyValueRepository.TtlValue.class,
                                ResourcePoolsBuilder.heap(100)))
                .build(true);

        repository = new EhcacheKeyValueRepository(cacheManager, CACHE_NAME);
        repository.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        repository.stop();
        if (cacheManager != null) {
            cacheManager.close();
        }
    }

    @Test
    void testPutAndGet() {
        repository.put("key1", "value1", null);

        assertThat(repository.get("key1")).isEqualTo("value1");
    }

    @Test
    void testGetMissingKeyReturnsNull() {
        assertThat(repository.get("nonexistent")).isNull();
    }

    @Test
    void testPutOverwritesExistingValue() {
        repository.put("key1", "value1", null);
        repository.put("key1", "value2", null);

        assertThat(repository.get("key1")).isEqualTo("value2");
    }

    @Test
    void testPutReturnsPreviousValue() {
        repository.put("key1", "value1", null);
        Object previous = repository.put("key1", "value2", null);

        assertThat(previous).isEqualTo("value1");
    }

    @Test
    void testPutReturnsNullForNewKey() {
        Object previous = repository.put("key1", "value1", null);

        assertThat(previous).isNull();
    }

    @Test
    void testDelete() {
        repository.put("key1", "value1", null);

        Object deleted = repository.delete("key1");

        assertThat(deleted).isEqualTo("value1");
        assertThat(repository.get("key1")).isNull();
    }

    @Test
    void testDeleteMissingKeyReturnsNull() {
        assertThat(repository.delete("nonexistent")).isNull();
    }

    @Test
    void testContains() {
        repository.put("key1", "value1", null);

        assertThat(repository.contains("key1")).isTrue();
        assertThat(repository.contains("nonexistent")).isFalse();
    }

    @Test
    void testKeys() {
        repository.put("key1", "value1", null);
        repository.put("key2", "value2", null);
        repository.put("key3", "value3", null);

        Set<String> keys = repository.keys();

        assertThat(keys).containsExactlyInAnyOrder("key1", "key2", "key3");
    }

    @Test
    void testKeysEmpty() {
        assertThat(repository.keys()).isEmpty();
    }

    @Test
    void testClear() {
        repository.put("key1", "value1", null);
        repository.put("key2", "value2", null);

        repository.clear();

        assertThat(repository.size()).isZero();
        assertThat(repository.get("key1")).isNull();
        assertThat(repository.get("key2")).isNull();
    }

    @Test
    void testSize() {
        assertThat(repository.size()).isZero();

        repository.put("key1", "value1", null);
        assertThat(repository.size()).isEqualTo(1);

        repository.put("key2", "value2", null);
        assertThat(repository.size()).isEqualTo(2);

        repository.delete("key1");
        assertThat(repository.size()).isEqualTo(1);
    }

    @Test
    void testTtlExpiration() {
        repository.put("key1", "value1", Duration.ofMillis(100));

        assertThat(repository.get("key1")).isEqualTo("value1");
        assertThat(repository.contains("key1")).isTrue();

        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(repository.get("key1")).isNull();
                    assertThat(repository.contains("key1")).isFalse();
                });
    }

    @Test
    void testTtlExpirationOnKeys() {
        repository.put("key1", "value1", Duration.ofMillis(100));
        repository.put("key2", "value2", null); // no expiration

        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(repository.keys()).containsExactly("key2");
                    assertThat(repository.size()).isEqualTo(1);
                });
    }

    @Test
    void testNoTtlWithZero() {
        repository.put("key1", "value1", null);

        assertThat(repository.get("key1")).isEqualTo("value1");
        assertThat(repository.contains("key1")).isTrue();
    }

    @Test
    void testNoTtlWithNegative() {
        repository.put("key1", "value1", Duration.ofMillis(-1));

        assertThat(repository.get("key1")).isEqualTo("value1");
        assertThat(repository.contains("key1")).isTrue();
    }

    @Test
    void testStoresDifferentValueTypes() {
        repository.put("string", "hello", null);
        repository.put("integer", 42, null);
        repository.put("boolean", Boolean.TRUE, null);

        assertThat(repository.get("string")).isEqualTo("hello");
        assertThat(repository.get("integer")).isEqualTo(42);
        assertThat(repository.get("boolean")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void testReplaceMatchingValue() {
        repository.put("key1", "value1", null);

        boolean replaced = repository.replace("key1", "value1", "value2", null);

        assertThat(replaced).isTrue();
        assertThat(repository.get("key1")).isEqualTo("value2");
    }

    @Test
    void testReplaceNonMatchingValue() {
        repository.put("key1", "value1", null);

        boolean replaced = repository.replace("key1", "wrong", "value2", null);

        assertThat(replaced).isFalse();
        assertThat(repository.get("key1")).isEqualTo("value1");
    }

    @Test
    void testReplaceMissingKey() {
        boolean replaced = repository.replace("nonexistent", "value1", "value2", null);

        assertThat(replaced).isFalse();
    }

    @Test
    void testDeleteWithMatchingValue() {
        repository.put("key1", "value1", null);

        boolean deleted = repository.delete("key1", "value1");

        assertThat(deleted).isTrue();
        assertThat(repository.get("key1")).isNull();
    }

    @Test
    void testDeleteWithNonMatchingValue() {
        repository.put("key1", "value1", null);

        boolean deleted = repository.delete("key1", "wrong");

        assertThat(deleted).isFalse();
        assertThat(repository.get("key1")).isEqualTo("value1");
    }

    @Test
    void testDeleteWithMissingKey() {
        boolean deleted = repository.delete("nonexistent", "value1");

        assertThat(deleted).isFalse();
    }
}
