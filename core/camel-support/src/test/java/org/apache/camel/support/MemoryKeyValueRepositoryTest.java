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
package org.apache.camel.support;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryKeyValueRepositoryTest {

    private MemoryKeyValueRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = new MemoryKeyValueRepository();
        repository.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        repository.stop();
    }

    @Test
    void testPutAndGet() {
        repository.put("key1", "value1", 0);

        assertThat(repository.get("key1")).isEqualTo("value1");
    }

    @Test
    void testGetMissingKeyReturnsNull() {
        assertThat(repository.get("nonexistent")).isNull();
    }

    @Test
    void testPutOverwritesExistingValue() {
        repository.put("key1", "value1", 0);
        repository.put("key1", "value2", 0);

        assertThat(repository.get("key1")).isEqualTo("value2");
    }

    @Test
    void testDelete() {
        repository.put("key1", "value1", 0);

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
        repository.put("key1", "value1", 0);

        assertThat(repository.contains("key1")).isTrue();
        assertThat(repository.contains("nonexistent")).isFalse();
    }

    @Test
    void testKeys() {
        repository.put("key1", "value1", 0);
        repository.put("key2", "value2", 0);
        repository.put("key3", "value3", 0);

        Set<String> keys = repository.keys();

        assertThat(keys).containsExactlyInAnyOrder("key1", "key2", "key3");
    }

    @Test
    void testKeysEmpty() {
        assertThat(repository.keys()).isEmpty();
    }

    @Test
    void testClear() {
        repository.put("key1", "value1", 0);
        repository.put("key2", "value2", 0);

        repository.clear();

        assertThat(repository.size()).isZero();
        assertThat(repository.get("key1")).isNull();
        assertThat(repository.get("key2")).isNull();
    }

    @Test
    void testSize() {
        assertThat(repository.size()).isZero();

        repository.put("key1", "value1", 0);
        assertThat(repository.size()).isEqualTo(1);

        repository.put("key2", "value2", 0);
        assertThat(repository.size()).isEqualTo(2);

        repository.delete("key1");
        assertThat(repository.size()).isEqualTo(1);
    }

    @Test
    void testPutIfAbsentNewKey() {
        Object result = repository.putIfAbsent("key1", "value1", 0);

        assertThat(result).isNull();
        assertThat(repository.get("key1")).isEqualTo("value1");
    }

    @Test
    void testPutIfAbsentExistingKey() {
        repository.put("key1", "value1", 0);

        Object result = repository.putIfAbsent("key1", "value2", 0);

        assertThat(result).isEqualTo("value1");
        assertThat(repository.get("key1")).isEqualTo("value1");
    }

    @Test
    void testTtlExpiration() throws Exception {
        // Use a very short TTL
        repository.put("key1", "value1", 50);

        assertThat(repository.get("key1")).isEqualTo("value1");
        assertThat(repository.contains("key1")).isTrue();

        // Wait for the entry to expire
        Thread.sleep(100);

        assertThat(repository.get("key1")).isNull();
        assertThat(repository.contains("key1")).isFalse();
    }

    @Test
    void testTtlExpirationOnKeys() throws Exception {
        repository.put("key1", "value1", 50);
        repository.put("key2", "value2", 0); // no expiration

        Thread.sleep(100);

        assertThat(repository.keys()).containsExactly("key2");
        assertThat(repository.size()).isEqualTo(1);
    }

    @Test
    void testTtlExpirationOnDelete() throws Exception {
        repository.put("key1", "value1", 50);

        Thread.sleep(100);

        // Deleting an expired entry should return null
        assertThat(repository.delete("key1")).isNull();
    }

    @Test
    void testPutIfAbsentWithExpiredEntry() throws Exception {
        repository.put("key1", "value1", 50);

        Thread.sleep(100);

        // The entry has expired, so putIfAbsent should succeed
        Object result = repository.putIfAbsent("key1", "value2", 0);

        assertThat(result).isNull();
        assertThat(repository.get("key1")).isEqualTo("value2");
    }

    @Test
    void testNoTtlWithZero() {
        repository.put("key1", "value1", 0);

        // Entry with TTL=0 should not expire
        assertThat(repository.get("key1")).isEqualTo("value1");
        assertThat(repository.contains("key1")).isTrue();
    }

    @Test
    void testNoTtlWithNegative() {
        repository.put("key1", "value1", -1);

        // Entry with negative TTL should not expire
        assertThat(repository.get("key1")).isEqualTo("value1");
        assertThat(repository.contains("key1")).isTrue();
    }

    @Test
    void testStoresDifferentValueTypes() {
        repository.put("string", "hello", 0);
        repository.put("integer", 42, 0);
        repository.put("boolean", Boolean.TRUE, 0);

        assertThat(repository.get("string")).isEqualTo("hello");
        assertThat(repository.get("integer")).isEqualTo(42);
        assertThat(repository.get("boolean")).isEqualTo(Boolean.TRUE);
    }
}
