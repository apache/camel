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
package org.apache.camel.processor.keyvalue.kafka;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.kafka.integration.common.KafkaTestUtil;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.kafka.services.KafkaService;
import org.apache.camel.test.infra.kafka.services.KafkaServiceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link KafkaKeyValueRepository} using a real Kafka broker via Testcontainers.
 */
public class KafkaKeyValueRepositoryIT {

    private static final String TOPIC = "TEST_KV_REPO_" + UUID.randomUUID();

    @Order(1)
    @RegisterExtension
    static KafkaService service = KafkaServiceFactory.createSingletonService();

    private DefaultCamelContext camelContext;
    private KafkaKeyValueRepository repository;

    @BeforeAll
    static void createTopic() {
        KafkaTestUtil.createTopic(service, TOPIC, 1);
    }

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.start();

        repository = new KafkaKeyValueRepository(TOPIC, service.getBootstrapServers());
        repository.setCamelContext(camelContext);
        repository.setStartupOnly(true);
        repository.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (repository != null) {
            try {
                repository.clear();
            } catch (Exception e) {
                // ignored during cleanup
            }
            repository.stop();
        }
        if (camelContext != null) {
            camelContext.stop();
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
        assertEquals("value1", repository.get("key1"));
    }

    @Test
    public void testTtlExpiration() {
        repository.put("key1", "value1", 500);
        assertEquals("value1", repository.get("key1"));

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertNull(repository.get("key1")));
    }

    @Test
    public void testTtlDoesNotAffectNonExpiringEntries() {
        repository.put("key1", "value1", 500);
        repository.put("key2", "value2", 0);

        await().atMost(5, TimeUnit.SECONDS)
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
}
