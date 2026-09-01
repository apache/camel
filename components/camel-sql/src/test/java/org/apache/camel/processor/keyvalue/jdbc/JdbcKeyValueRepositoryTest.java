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
package org.apache.camel.processor.keyvalue.jdbc;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcKeyValueRepositoryTest {

    private JdbcKeyValueRepository repository;
    private EmbeddedDatabase dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
        repository = new JdbcKeyValueRepository(dataSource);
        repository.init();
        repository.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        repository.stop();
        dataSource.shutdown();
    }

    @Test
    void testPutAndGet() {
        repository.put("key1", "value1", 0);
        assertEquals("value1", repository.get("key1"));
    }

    @Test
    void testGetMissingKeyReturnsNull() {
        assertNull(repository.get("nonexistent"));
    }

    @Test
    void testPutOverwritesExistingValue() {
        repository.put("key1", "value1", 0);
        repository.put("key1", "value2", 0);
        assertEquals("value2", repository.get("key1"));
    }

    @Test
    void testPutReturnsOldValue() {
        repository.put("key1", "value1", 0);
        Object old = repository.put("key1", "value2", 0);
        assertEquals("value1", old);
    }

    @Test
    void testPutReturnsNullForNewKey() {
        Object old = repository.put("key1", "value1", 0);
        assertNull(old);
    }

    @Test
    void testDelete() {
        repository.put("key1", "value1", 0);
        Object deleted = repository.delete("key1");
        assertEquals("value1", deleted);
        assertNull(repository.get("key1"));
    }

    @Test
    void testDeleteMissingKeyReturnsNull() {
        assertNull(repository.delete("nonexistent"));
    }

    @Test
    void testContains() {
        repository.put("key1", "value1", 0);
        assertTrue(repository.contains("key1"));
        assertFalse(repository.contains("nonexistent"));
    }

    @Test
    void testKeys() {
        repository.put("key1", "value1", 0);
        repository.put("key2", "value2", 0);
        repository.put("key3", "value3", 0);

        Set<String> keys = repository.keys();
        assertEquals(3, keys.size());
        assertTrue(keys.contains("key1"));
        assertTrue(keys.contains("key2"));
        assertTrue(keys.contains("key3"));
    }

    @Test
    void testKeysEmpty() {
        assertTrue(repository.keys().isEmpty());
    }

    @Test
    void testClear() {
        repository.put("key1", "value1", 0);
        repository.put("key2", "value2", 0);
        repository.clear();
        assertEquals(0, repository.size());
        assertNull(repository.get("key1"));
        assertNull(repository.get("key2"));
    }

    @Test
    void testSize() {
        assertEquals(0, repository.size());
        repository.put("key1", "value1", 0);
        assertEquals(1, repository.size());
        repository.put("key2", "value2", 0);
        assertEquals(2, repository.size());
        repository.delete("key1");
        assertEquals(1, repository.size());
    }

    @Test
    void testPutIfAbsentNewKey() {
        Object result = repository.putIfAbsent("key1", "value1", 0);
        assertNull(result);
        assertEquals("value1", repository.get("key1"));
    }

    @Test
    void testPutIfAbsentExistingKey() {
        repository.put("key1", "value1", 0);
        Object result = repository.putIfAbsent("key1", "value2", 0);
        assertEquals("value1", result);
        assertEquals("value1", repository.get("key1"));
    }

    @Test
    void testTtlExpiration() {
        repository.put("key1", "value1", 50);
        assertEquals("value1", repository.get("key1"));
        assertTrue(repository.contains("key1"));

        await().atMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertNull(repository.get("key1"));
                    assertFalse(repository.contains("key1"));
                });
    }

    @Test
    void testTtlExpirationOnKeys() {
        repository.put("key1", "value1", 50);
        repository.put("key2", "value2", 0); // no expiration

        await().atMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Set<String> keys = repository.keys();
                    assertEquals(1, keys.size());
                    assertTrue(keys.contains("key2"));
                });
    }

    @Test
    void testTtlExpirationOnDelete() {
        repository.put("key1", "value1", 50);

        await().atMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertNull(repository.delete("key1")));
    }

    @Test
    void testPutIfAbsentWithExpiredEntry() {
        repository.put("key1", "value1", 50);

        await().atMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Object result = repository.putIfAbsent("key1", "value2", 0);
                    assertNull(result);
                    assertEquals("value2", repository.get("key1"));
                });
    }

    @Test
    void testNoTtlWithZero() {
        repository.put("key1", "value1", 0);
        assertEquals("value1", repository.get("key1"));
        assertTrue(repository.contains("key1"));
    }

    @Test
    void testNoTtlWithNegative() {
        repository.put("key1", "value1", -1);
        assertEquals("value1", repository.get("key1"));
        assertTrue(repository.contains("key1"));
    }

    @Test
    void testStoresDifferentValueTypes() {
        repository.put("string", "hello", 0);
        repository.put("integer", 42, 0);
        repository.put("boolean", Boolean.TRUE, 0);

        assertEquals("hello", repository.get("string"));
        assertEquals(42, repository.get("integer"));
        assertEquals(Boolean.TRUE, repository.get("boolean"));
    }

    @Test
    void testAutoCreateTable() throws Exception {
        repository.stop();
        repository = new JdbcKeyValueRepository(dataSource);
        repository.setTableName("CUSTOM_KV_TABLE");
        repository.init();
        repository.start();

        repository.put("key1", "value1", 0);
        assertEquals("value1", repository.get("key1"));
    }
}
