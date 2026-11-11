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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.processor.idempotent.kafka.KafkaChangelog;
import org.apache.camel.support.cache.SimpleLRUCache;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for the expiry handling of {@link KafkaKeyValueRepository}, without a Kafka broker: the changelog sends to
 * a {@link MockProducer}, and the records of the topic are applied by calling the repository's poller callback.
 */
public class KafkaKeyValueRepositoryTest {

    // an expiry time long past: the entry has expired when it is applied, as for a record replayed on startup
    private static final long EXPIRED = 1L;

    private MockProducer<String, byte[]> producer;
    private KafkaKeyValueRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        producer = new MockProducer<>(true, null, new StringSerializer(), new ByteArraySerializer());
        repository = new KafkaKeyValueRepository("test-topic", "localhost:9092");
        // the cache that LRUCacheFactory creates by default
        setField("cache", new SimpleLRUCache<>(16, 16, v -> {
        }));
        setField("changelog", changelog(producer));
    }

    @Test
    void keysSkipsAnEntryThatHasExpired() throws Exception {
        repository.put("live", "a", null);
        applyPut("expired", "b", EXPIRED);

        assertEquals(Set.of("live"), repository.keys());
        assertNull(repository.get("expired"));
    }

    @Test
    void sizeCountsOnlyTheEntriesThatHaveNotExpired() throws Exception {
        repository.put("live", "a", null);
        applyPut("expired", "b", EXPIRED);

        assertEquals(1, repository.size());
        assertFalse(cache().containsKey("expired"), "The expired entry was not evicted");
    }

    @Test
    void putIfAbsentReplacesAnEntryThatHasExpired() throws Exception {
        applyPut("key", "old", EXPIRED);

        assertNull(repository.putIfAbsent("key", "new", null));

        assertEquals("new", repository.get("key"));
        assertEquals(1, producer.history().size());
        assertEquals("key", producer.history().get(0).key());
    }

    @Test
    void putIfAbsentStoresAndSendsTheValueWhenTheEntryFoundMeanwhileHasExpired() throws Exception {
        // the poller applies an expired record of the key after putIfAbsent found the key absent, and before it stores
        // its own entry (the poller can lag behind by more than the time to live of the record)
        setField("cache", new ConcurrentHashMap<String, Object>() {
            private boolean applied;

            @Override
            public Object putIfAbsent(String key, Object value) {
                if (!applied) {
                    applied = true;
                    applyPut(key, "peer", EXPIRED);
                }
                return super.putIfAbsent(key, value);
            }
        });

        // null means that the value was stored
        assertNull(repository.putIfAbsent("key", "mine", null));

        assertEquals("mine", repository.get("key"));
        assertEquals(1, producer.history().size(), "The value was not sent to the topic");
    }

    @Test
    void putIfAbsentReturnsTheValueStoredMeanwhileInsteadOfAnExpiredEntry() throws Exception {
        applyPut("key", "old", EXPIRED);
        // the poller applies a live record of the key after putIfAbsent found the expired entry, and before it replaces
        // that entry
        setField("cache", new ConcurrentHashMap<String, Object>(cache()) {
            private boolean applied;

            @Override
            public boolean replace(String key, Object oldValue, Object newValue) {
                if (!applied) {
                    applied = true;
                    applyPut(key, "peer", 0);
                }
                return super.replace(key, oldValue, newValue);
            }
        });

        assertEquals("peer", repository.putIfAbsent("key", "mine", null));

        assertEquals("peer", repository.get("key"));
        assertEquals(0, producer.history().size());
    }

    @Test
    void putIfAbsentReturnsTheValueOfAnEntryThatHasNotExpired() throws Exception {
        applyPut("key", "peer", 0);

        assertEquals("peer", repository.putIfAbsent("key", "mine", null));

        assertEquals("peer", repository.get("key"));
        assertEquals(0, producer.history().size());
    }

    /**
     * Applies a put record of the topic to the cache, as the poller does.
     */
    private void applyPut(String key, Object value, long expiresAt) {
        try {
            Method serialize = KafkaKeyValueRepository.class.getDeclaredMethod("serializePutAction", Object.class, long.class);
            serialize.setAccessible(true);
            byte[] payload = (byte[]) serialize.invoke(repository, value, expiresAt);
            Method addToCache = KafkaKeyValueRepository.class.getDeclaredMethod("addToCache", ConsumerRecord.class);
            addToCache.setAccessible(true);
            addToCache.invoke(repository, new ConsumerRecord<>("test-topic", 0, 0L, key, payload));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cache() throws Exception {
        Field field = KafkaKeyValueRepository.class.getDeclaredField("cache");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(repository);
    }

    private static KafkaChangelog<byte[]> changelog(Producer<String, byte[]> producer) throws Exception {
        KafkaChangelog<byte[]> changelog = new KafkaChangelog<>(
                "KafkaKeyValueRepository", "test-topic", new Properties(), new Properties(), ByteArrayDeserializer.class,
                ByteArraySerializer.class, 100, true, record -> {
                });
        Method setProducer = KafkaChangelog.class.getDeclaredMethod("setProducer", Producer.class);
        setProducer.setAccessible(true);
        setProducer.invoke(changelog, producer);
        return changelog;
    }

    private void setField(String name, Object value) throws Exception {
        Field field = KafkaKeyValueRepository.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(repository, value);
    }
}
