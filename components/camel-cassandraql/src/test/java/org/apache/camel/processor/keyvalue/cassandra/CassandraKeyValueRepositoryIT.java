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
package org.apache.camel.processor.keyvalue.cassandra;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cassandra.integration.BaseCassandra;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link CassandraKeyValueRepository} using a real Cassandra instance via Testcontainers.
 */
public class CassandraKeyValueRepositoryIT extends BaseCassandra {

    private final List<CassandraKeyValueRepository> isolatedRepositories = new ArrayList<>();

    private CassandraKeyValueRepository repository;
    private String keyPrefix;

    @BeforeEach
    protected void doPreSetup() {
        // the shared table is never cleared: each test uses its own unique keys to be isolated from the others
        keyPrefix = UUID.randomUUID() + "-";
        repository = startRepository("camel_keyvalue");
    }

    @AfterEach
    public void tearDown() {
        if (repository != null) {
            repository.stop();
        }
        isolatedRepositories.forEach(CassandraKeyValueRepository::stop);
        isolatedRepositories.clear();
    }

    private String key(String name) {
        return keyPrefix + name;
    }

    private CassandraKeyValueRepository startRepository(String table) {
        CassandraKeyValueRepository repo = new CassandraKeyValueRepository(getSession());
        repo.setTable(table);
        repo.start();
        return repo;
    }

    /**
     * Creates a repository backed by a brand new table, for tests relying on the whole content of the repository (keys,
     * size, clear) which cannot be isolated by using unique keys.
     */
    private CassandraKeyValueRepository createIsolatedRepository() {
        String table = "camel_kv_" + UUID.randomUUID().toString().replace("-", "");
        executeCql("CREATE TABLE " + table + " (key varchar, value blob, PRIMARY KEY (key))");
        CassandraKeyValueRepository repo = startRepository(table);
        isolatedRepositories.add(repo);
        return repo;
    }

    @Test
    public void testPutAndGet() {
        repository.put(key("key1"), "value1", null);
        assertEquals("value1", repository.get(key("key1")));
    }

    @Test
    public void testGetNonExistentKeyReturnsNull() {
        assertNull(repository.get(key("nonexistent")));
    }

    @Test
    public void testPutOverwritesExistingValue() {
        repository.put(key("key1"), "value1", null);
        repository.put(key("key1"), "value2", null);
        assertEquals("value2", repository.get(key("key1")));
    }

    @Test
    public void testPutReturnsPreviousValue() {
        assertNull(repository.put(key("key1"), "value1", null));
        assertEquals("value1", repository.put(key("key1"), "value2", null));
    }

    @Test
    public void testDelete() {
        repository.put(key("key1"), "value1", null);
        Object deleted = repository.delete(key("key1"));
        assertEquals("value1", deleted);
        assertNull(repository.get(key("key1")));
    }

    @Test
    public void testDeleteNonExistentKeyReturnsNull() {
        assertNull(repository.delete(key("nonexistent")));
    }

    @Test
    public void testContains() {
        repository.put(key("key1"), "value1", null);
        assertTrue(repository.contains(key("key1")));
        assertFalse(repository.contains(key("nonexistent")));
    }

    @Test
    public void testKeys() {
        CassandraKeyValueRepository isolated = createIsolatedRepository();
        isolated.put(key("key1"), "value1", null);
        isolated.put(key("key2"), "value2", null);
        isolated.put(key("key3"), "value3", null);

        Set<String> keys = isolated.keys();
        assertEquals(3, keys.size());
        assertTrue(keys.contains(key("key1")));
        assertTrue(keys.contains(key("key2")));
        assertTrue(keys.contains(key("key3")));
    }

    @Test
    public void testClear() {
        CassandraKeyValueRepository isolated = createIsolatedRepository();
        isolated.put(key("key1"), "value1", null);
        isolated.put(key("key2"), "value2", null);
        isolated.clear();
        assertEquals(0, isolated.size());
    }

    @Test
    public void testSize() {
        CassandraKeyValueRepository isolated = createIsolatedRepository();
        assertEquals(0, isolated.size());
        isolated.put(key("key1"), "value1", null);
        assertEquals(1, isolated.size());
        isolated.put(key("key2"), "value2", null);
        assertEquals(2, isolated.size());
    }

    @Test
    public void testPutIfAbsentWhenKeyDoesNotExist() {
        Object result = repository.putIfAbsent(key("key1"), "value1", null);
        assertNull(result);
        assertEquals("value1", repository.get(key("key1")));
    }

    @Test
    public void testPutIfAbsentWhenKeyExists() {
        repository.put(key("key1"), "value1", null);
        Object result = repository.putIfAbsent(key("key1"), "value2", null);
        assertEquals("value1", result);
        // original value should be unchanged
        assertEquals("value1", repository.get(key("key1")));
    }

    @Test
    public void testReplaceMatchingOldValue() {
        repository.put(key("key1"), "value1", null);
        boolean replaced = repository.replace(key("key1"), "value1", "value2", null);
        assertTrue(replaced);
        assertEquals("value2", repository.get(key("key1")));
    }

    @Test
    public void testReplaceNonMatchingOldValue() {
        repository.put(key("key1"), "value1", null);
        boolean replaced = repository.replace(key("key1"), "wrong", "value2", null);
        assertFalse(replaced);
        assertEquals("value1", repository.get(key("key1")));
    }

    @Test
    public void testReplaceMissingKey() {
        boolean replaced = repository.replace(key("nonexistent"), "value1", "value2", null);
        assertFalse(replaced);
    }

    @Test
    public void testReplaceWithTtl() {
        repository.put(key("key1"), "value1", null);
        boolean replaced = repository.replace(key("key1"), "value1", "value2", Duration.ofSeconds(2));
        assertTrue(replaced);
        assertEquals("value2", repository.get(key("key1")));

        // The replaced value should expire after the TTL
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertNull(repository.get(key("key1"))));
    }

    @Test
    public void testReplaceWithTtlNonMatchingOldValue() {
        repository.put(key("key1"), "value1", null);
        boolean replaced = repository.replace(key("key1"), "wrong", "value2", Duration.ofSeconds(2));
        assertFalse(replaced);
        assertEquals("value1", repository.get(key("key1")));
    }

    @Test
    public void testDeleteWithExpectedValueMatching() {
        repository.put(key("key1"), "value1", null);
        boolean deleted = repository.delete(key("key1"), "value1");
        assertTrue(deleted);
        assertNull(repository.get(key("key1")));
    }

    @Test
    public void testDeleteWithExpectedValueNotMatching() {
        repository.put(key("key1"), "value1", null);
        boolean deleted = repository.delete(key("key1"), "wrong");
        assertFalse(deleted);
        assertEquals("value1", repository.get(key("key1")));
    }

    @Test
    public void testDeleteWithExpectedValueMissingKey() {
        boolean deleted = repository.delete(key("nonexistent"), "value1");
        assertFalse(deleted);
    }

    @Test
    public void testTtlExpiration() {
        // Cassandra TTL has 1-second resolution
        repository.put(key("key1"), "value1", Duration.ofSeconds(2));
        assertEquals("value1", repository.get(key("key1")));

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertNull(repository.get(key("key1"))));
    }

    @Test
    public void testTtlDoesNotAffectNonExpiringEntries() {
        repository.put(key("key1"), "value1", Duration.ofSeconds(2));
        repository.put(key("key2"), "value2", null);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertNull(repository.get(key("key1"))));

        assertEquals("value2", repository.get(key("key2")));
    }

    @Test
    public void testDifferentValueTypes() {
        repository.put(key("string"), "hello", null);
        repository.put(key("integer"), 42, null);
        repository.put(key("boolean"), Boolean.TRUE, null);

        assertEquals("hello", repository.get(key("string")));
        assertEquals(42, repository.get(key("integer")));
        assertEquals(Boolean.TRUE, repository.get(key("boolean")));
    }

    @Test
    public void testConveniencePut() {
        repository.put(key("key1"), "value1");
        assertEquals("value1", repository.get(key("key1")));
    }

    @Test
    public void testConveniencePutIfAbsent() {
        assertNull(repository.putIfAbsent(key("key1"), "value1"));
        assertEquals("value1", repository.putIfAbsent(key("key1"), "value2"));
        assertEquals("value1", repository.get(key("key1")));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return null;
    }
}
