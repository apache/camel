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
package org.apache.camel.component.kafka.consumer.support.resume;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.camel.resume.Serializable;
import org.apache.camel.resume.cache.ResumeCache;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaResumeAdapterTest {

    private KafkaResumeAdapter adapter;
    private TestResumeCache cache;

    @BeforeEach
    void setUp() {
        adapter = new KafkaResumeAdapter();
        cache = new TestResumeCache();
        adapter.setCache(cache);
    }

    @Test
    void testDeserializeValidKey() {
        ByteBuffer keyBuffer = serializeString("myTopic/3");
        ByteBuffer valueBuffer = serializeLong(42L);

        adapter.deserialize(keyBuffer, valueBuffer);

        TopicPartition expected = new TopicPartition("myTopic", 3);
        assertEquals(42L, cache.get(expected));
    }

    @Test
    void testDeserializeMultipleKeys() {
        adapter.deserialize(serializeString("topic1/0"), serializeLong(10L));
        adapter.deserialize(serializeString("topic1/1"), serializeLong(20L));
        adapter.deserialize(serializeString("topic2/0"), serializeLong(30L));

        assertEquals(10L, cache.get(new TopicPartition("topic1", 0)));
        assertEquals(20L, cache.get(new TopicPartition("topic1", 1)));
        assertEquals(30L, cache.get(new TopicPartition("topic2", 0)));
    }

    @Test
    void testDeserializeInvalidKeyNoSlash() {
        ByteBuffer keyBuffer = serializeString("invalidKey");
        ByteBuffer valueBuffer = serializeLong(42L);

        boolean result = adapter.deserialize(keyBuffer, valueBuffer);

        assertFalse(result);
        assertTrue(cache.isEmpty());
    }

    @Test
    void testDeserializeInvalidKeyTooManyParts() {
        ByteBuffer keyBuffer = serializeString("a/b/c");
        ByteBuffer valueBuffer = serializeLong(42L);

        boolean result = adapter.deserialize(keyBuffer, valueBuffer);

        assertFalse(result);
        assertTrue(cache.isEmpty());
    }

    @Test
    void testDeserializeInvalidValueType() {
        ByteBuffer keyBuffer = serializeString("myTopic/0");
        ByteBuffer valueBuffer = serializeString("notALong");

        adapter.deserialize(keyBuffer, valueBuffer);

        assertNull(cache.get(new TopicPartition("myTopic", 0)));
    }

    private ByteBuffer serializeString(String value) {
        byte[] data = value.getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + data.length);
        buffer.putInt(Serializable.TYPE_STRING);
        buffer.put(data);
        return buffer;
    }

    private ByteBuffer serializeLong(long value) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Long.BYTES);
        buffer.putInt(Serializable.TYPE_LONG);
        buffer.putLong(value);
        return buffer;
    }

    private static class TestResumeCache implements ResumeCache<TopicPartition> {
        private final Map<TopicPartition, Object> store = new HashMap<>();

        @Override
        public Object computeIfAbsent(TopicPartition key, Function<? super TopicPartition, ? super Object> mapping) {
            return store.computeIfAbsent(key, k -> mapping.apply(k));
        }

        @Override
        public Object computeIfPresent(
                TopicPartition key,
                BiFunction<? super TopicPartition, ? super Object, ? super Object> remapping) {
            return store.computeIfPresent(key, (k, v) -> remapping.apply(k, v));
        }

        @Override
        public boolean contains(TopicPartition key, Object entry) {
            Object val = store.get(key);
            return val != null && val.equals(entry);
        }

        @Override
        public void add(TopicPartition key, Object offsetValue) {
            store.put(key, offsetValue);
        }

        @Override
        public boolean isFull() {
            return false;
        }

        @Override
        public long capacity() {
            return Long.MAX_VALUE;
        }

        @Override
        public <T> T get(TopicPartition key, Class<T> clazz) {
            return clazz.cast(store.get(key));
        }

        @Override
        public Object get(TopicPartition key) {
            return store.get(key);
        }

        @Override
        public void forEach(BiFunction<? super TopicPartition, ? super Object, Boolean> action) {
            for (Map.Entry<TopicPartition, Object> entry : store.entrySet()) {
                if (!action.apply(entry.getKey(), entry.getValue())) {
                    break;
                }
            }
        }

        boolean isEmpty() {
            return store.isEmpty();
        }
    }
}
