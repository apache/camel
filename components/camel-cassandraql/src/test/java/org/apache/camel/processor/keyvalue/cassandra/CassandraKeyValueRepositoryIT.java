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

import java.util.Set;
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

    private CassandraKeyValueRepository repository;

    @BeforeEach
    protected void doPreSetup() {
        repository = new CassandraKeyValueRepository(getSession());
        repository.setTable("camel_ks.CAMEL_KEYVALUE");
        repository.start();
    }

    @AfterEach
    public void tearDown() {
        if (repository != null) {
            try {
                repository.clear();
            } catch (Exception e) {
                // ignored during cleanup
            }
            repository.stop();
        }
    }

    @Test
    public void testPutAndGet() {
        repository.put("key1", "value1", 0);
        assertEquals("value1", repository.get("key1"));
    }

    @Test
    public void testGetNonExistentKeyReturnsNull() {
        assertNull(repository.get("nonexistent"));
    }

    @Test
    public void testPutOverwritesExistingValue() {
        repository.put("key1", "value1", 0);
        repository.put("key1", "value2", 0);
        assertEquals("value2", repository.get("key1"));
    }

    @Test
    public void testPutReturnsPreviousValue() {
        assertNull(repository.put("key1", "value1", 0));
        assertEquals("value1", repository.put("key1", "value2", 0));
    }

    @Test
    public void testDelete() {
        repository.put("key1", "value1", 0);
        Object deleted = repository.delete("key1");
        assertEquals("value1", deleted);
        assertNull(repository.get("key1"));
    }

    @Test
    public void testDeleteNonExistentKeyReturnsNull() {
        assertNull(repository.delete("nonexistent"));
    }

    @Test
    public void testContains() {
        repository.put("key1", "value1", 0);
        assertTrue(repository.contains("key1"));
        assertFalse(repository.contains("nonexistent"));
    }

    @Test
    public void testKeys() {
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
    public void testClear() {
        repository.put("key1", "value1", 0);
        repository.put("key2", "value2", 0);
        repository.clear();
        assertEquals(0, repository.size());
    }

    @Test
    public void testSize() {
        assertEquals(0, repository.size());
        repository.put("key1", "value1", 0);
        assertEquals(1, repository.size());
        repository.put("key2", "value2", 0);
        assertEquals(2, repository.size());
    }

    @Test
    public void testPutIfAbsentWhenKeyDoesNotExist() {
        Object result = repository.putIfAbsent("key1", "value1", 0);
        assertNull(result);
        assertEquals("value1", repository.get("key1"));
    }

    @Test
    public void testPutIfAbsentWhenKeyExists() {
        repository.put("key1", "value1", 0);
        Object result = repository.putIfAbsent("key1", "value2", 0);
        assertEquals("value1", result);
        // original value should be unchanged
        assertEquals("value1", repository.get("key1"));
    }

    @Test
    public void testTtlExpiration() {
        // Cassandra TTL has 1-second resolution
        repository.put("key1", "value1", 2000);
        assertEquals("value1", repository.get("key1"));

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertNull(repository.get("key1")));
    }

    @Test
    public void testTtlDoesNotAffectNonExpiringEntries() {
        repository.put("key1", "value1", 2000);
        repository.put("key2", "value2", 0);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertNull(repository.get("key1")));

        assertEquals("value2", repository.get("key2"));
    }

    @Test
    public void testDifferentValueTypes() {
        repository.put("string", "hello", 0);
        repository.put("integer", 42, 0);
        repository.put("boolean", Boolean.TRUE, 0);

        assertEquals("hello", repository.get("string"));
        assertEquals(42, repository.get("integer"));
        assertEquals(Boolean.TRUE, repository.get("boolean"));
    }

    @Test
    public void testConveniencePut() {
        repository.put("key1", "value1");
        assertEquals("value1", repository.get("key1"));
    }

    @Test
    public void testConveniencePutIfAbsent() {
        assertNull(repository.putIfAbsent("key1", "value1"));
        assertEquals("value1", repository.putIfAbsent("key1", "value2"));
        assertEquals("value1", repository.get("key1"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return null;
    }
}
