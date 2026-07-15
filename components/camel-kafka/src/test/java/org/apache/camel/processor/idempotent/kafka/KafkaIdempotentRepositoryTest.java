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
package org.apache.camel.processor.idempotent.kafka;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.camel.RuntimeCamelException;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link KafkaIdempotentRepository} cache logic, without requiring a Kafka broker.
 */
public class KafkaIdempotentRepositoryTest {

    private Map<String, Object> cache;
    private MockProducer<String, String> mockProducer;
    private KafkaIdempotentRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        cache = new ConcurrentHashMap<>();
        mockProducer = new MockProducer<>(true, null, new StringSerializer(), new StringSerializer());

        repository = new KafkaIdempotentRepository();
        repository.setTopic("test-topic");

        setField(repository, "cache", cache);
        setField(repository, "producer", mockProducer);
    }

    @Test
    void testAddReturnsTrueForNewKey() {
        assertTrue(repository.add("key1"));
        assertTrue(cache.containsKey("key1"));
    }

    @Test
    void testAddReturnsFalseForDuplicateKey() {
        assertTrue(repository.add("key1"));
        assertFalse(repository.add("key1"));
    }

    @Test
    void testAddBroadcastsToKafka() {
        repository.add("key1");

        assertEquals(1, mockProducer.history().size());
        ProducerRecord<String, String> record = mockProducer.history().get(0);
        assertEquals("test-topic", record.topic());
        assertEquals("key1", record.key());
        assertEquals("add", record.value());
    }

    @Test
    void testAddRollsBackCacheOnBroadcastFailure() throws Exception {
        MockProducer<String, String> failingProducer
                = new MockProducer<>(false, null, new StringSerializer(), new StringSerializer());
        setField(repository, "producer", failingProducer);

        Thread sender = new Thread(() -> {
            try {
                repository.add("key1");
            } catch (RuntimeCamelException e) {
                // expected
            }
        });
        sender.start();

        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> sender.getState() == Thread.State.WAITING
                        || sender.getState() == Thread.State.TIMED_WAITING);
        failingProducer.errorNext(new RuntimeException("Simulated send failure"));
        sender.join(5000);

        assertFalse(cache.containsKey("key1"));
    }

    @Test
    void testContainsReflectsCacheState() {
        assertFalse(repository.contains("key1"));
        cache.put("key1", "key1");
        assertTrue(repository.contains("key1"));
    }

    @Test
    void testClearEmptiesLocalCache() {
        repository.add("key1");
        repository.add("key2");
        assertEquals(2, cache.size());

        repository.clear();
        assertTrue(cache.isEmpty());
    }

    @Test
    void testClearBroadcastsToKafka() {
        repository.clear();

        assertEquals(1, mockProducer.history().size());
        ProducerRecord<String, String> record = mockProducer.history().get(0);
        assertNull(record.key());
        assertEquals("clear", record.value());
    }

    @Test
    void testRemoveDeletesFromCache() {
        repository.add("key1");
        assertTrue(repository.contains("key1"));

        repository.remove("key1");
        assertFalse(repository.contains("key1"));
    }

    @Test
    void testRemoveThenAddSucceeds() {
        repository.add("key1");
        repository.remove("key1");
        assertTrue(repository.add("key1"));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
