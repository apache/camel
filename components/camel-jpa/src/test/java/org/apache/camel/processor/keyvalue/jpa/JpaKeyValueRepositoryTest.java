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
package org.apache.camel.processor.keyvalue.jpa;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.apache.camel.component.jpa.DefaultTransactionStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link JpaKeyValueRepository} using an embedded H2 database with OpenJPA.
 */
class JpaKeyValueRepositoryTest {

    private EntityManagerFactory entityManagerFactory;
    private JpaKeyValueRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        entityManagerFactory = Persistence.createEntityManagerFactory("keyvalueDb");

        // Create the repository with a Spring-managed transaction strategy
        repository = new JpaKeyValueRepository(entityManagerFactory);
        repository.setTransactionStrategy(new DefaultTransactionStrategy(null, entityManagerFactory));
        repository.setJoinTransaction(false);
        repository.setSharedEntityManager(true);
        repository.init();
        repository.start();

        // start clean
        repository.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (repository != null) {
            repository.stop();
        }
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
    }

    @Test
    void testPutAndGet() {
        repository.put("key1", "value1", null);
        assertEquals("value1", repository.get("key1"));
    }

    @Test
    void testGetNonExistentKeyReturnsNull() {
        assertNull(repository.get("nonexistent"));
    }

    @Test
    void testPutOverwritesExistingValue() {
        repository.put("key1", "value1", null);
        repository.put("key1", "value2", null);
        assertEquals("value2", repository.get("key1"));
    }

    @Test
    void testPutReturnsPreviousValue() {
        assertNull(repository.put("key1", "value1", null));
        assertEquals("value1", repository.put("key1", "value2", null));
    }

    @Test
    void testDelete() {
        repository.put("key1", "value1", null);
        Object deleted = repository.delete("key1");
        assertEquals("value1", deleted);
        assertNull(repository.get("key1"));
    }

    @Test
    void testDeleteNonExistentKeyReturnsNull() {
        assertNull(repository.delete("nonexistent"));
    }

    @Test
    void testContains() {
        repository.put("key1", "value1", null);
        assertTrue(repository.contains("key1"));
        assertFalse(repository.contains("nonexistent"));
    }

    @Test
    void testKeys() {
        repository.put("key1", "value1", null);
        repository.put("key2", "value2", null);
        repository.put("key3", "value3", null);

        Set<String> keys = repository.keys();
        assertEquals(3, keys.size());
        assertTrue(keys.contains("key1"));
        assertTrue(keys.contains("key2"));
        assertTrue(keys.contains("key3"));
    }

    @Test
    void testClear() {
        repository.put("key1", "value1", null);
        repository.put("key2", "value2", null);
        repository.clear();
        assertEquals(0, repository.size());
        assertNull(repository.get("key1"));
    }

    @Test
    void testSize() {
        assertEquals(0, repository.size());
        repository.put("key1", "value1", null);
        assertEquals(1, repository.size());
        repository.put("key2", "value2", null);
        assertEquals(2, repository.size());
    }

    @Test
    void testPutIfAbsentWhenKeyDoesNotExist() {
        Object result = repository.putIfAbsent("key1", "value1", null);
        assertNull(result);
        assertEquals("value1", repository.get("key1"));
    }

    @Test
    void testPutIfAbsentWhenKeyExists() {
        repository.put("key1", "value1", null);
        Object result = repository.putIfAbsent("key1", "value2", null);
        assertEquals("value1", result);
        assertEquals("value1", repository.get("key1"));
    }

    @Test
    void testReplaceMatchingOldValue() {
        repository.put("key1", "value1", null);
        boolean replaced = repository.replace("key1", "value1", "value2", null);
        assertTrue(replaced);
        assertEquals("value2", repository.get("key1"));
    }

    @Test
    void testReplaceNonMatchingOldValue() {
        repository.put("key1", "value1", null);
        boolean replaced = repository.replace("key1", "wrong", "value2", null);
        assertFalse(replaced);
        assertEquals("value1", repository.get("key1"));
    }

    @Test
    void testReplaceMissingKey() {
        boolean replaced = repository.replace("nonexistent", "value1", "value2", null);
        assertFalse(replaced);
    }

    @Test
    void testDeleteWithExpectedValueMatching() {
        repository.put("key1", "value1", null);
        boolean deleted = repository.delete("key1", "value1");
        assertTrue(deleted);
        assertNull(repository.get("key1"));
    }

    @Test
    void testDeleteWithExpectedValueNotMatching() {
        repository.put("key1", "value1", null);
        boolean deleted = repository.delete("key1", "wrong");
        assertFalse(deleted);
        assertEquals("value1", repository.get("key1"));
    }

    @Test
    void testDeleteWithExpectedValueMissingKey() {
        boolean deleted = repository.delete("nonexistent", "value1");
        assertFalse(deleted);
    }

    @Test
    void testTtlExpiration() {
        repository.put("key1", "value1", Duration.ofMillis(500));
        assertEquals("value1", repository.get("key1"));

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertNull(repository.get("key1")));
    }

    @Test
    void testTtlExpirationDoesNotAffectNonExpiringEntries() {
        repository.put("key1", "value1", Duration.ofMillis(500));
        repository.put("key2", "value2", null);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertNull(repository.get("key1")));

        assertEquals("value2", repository.get("key2"));
    }

    @Test
    void testDifferentValueTypes() {
        repository.put("string", "hello", null);
        repository.put("integer", 42, null);
        repository.put("boolean", Boolean.TRUE, null);

        assertEquals("hello", repository.get("string"));
        assertEquals(42, repository.get("integer"));
        assertEquals(Boolean.TRUE, repository.get("boolean"));
    }

    @Test
    void testConveniencePut() {
        repository.put("key1", "value1");
        assertEquals("value1", repository.get("key1"));
    }

    @Test
    void testConveniencePutIfAbsent() {
        assertNull(repository.putIfAbsent("key1", "value1"));
        assertEquals("value1", repository.putIfAbsent("key1", "value2"));
        assertEquals("value1", repository.get("key1"));
    }
}
