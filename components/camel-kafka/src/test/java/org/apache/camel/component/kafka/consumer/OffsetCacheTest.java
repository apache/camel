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

package org.apache.camel.component.kafka.consumer;

import java.util.Collections;
import java.util.Map;

import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OffsetCacheTest {
    private final OffsetCache offsetCache = new OffsetCache();

    @Order(1)
    @Test
    @DisplayName("Tests whether the cache can record offset a single offset")
    void updateOffsetsSinglePartition() {
        final TopicPartition topic1 = new TopicPartition("topic1", 1);

        assertDoesNotThrow(() -> offsetCache.recordOffset(topic1, 1));
        assertDoesNotThrow(() -> offsetCache.recordOffset(topic1, 2));
        assertDoesNotThrow(() -> offsetCache.recordOffset(topic1, 2),
                "The cache should not throw exceptions for duplicate records");
    }

    @Order(2)
    @Test
    @DisplayName("Tests whether the cache can retrieve offset information")
    void getOffset() {
        final TopicPartition topic1 = new TopicPartition("topic1", 1);

        assertTrue(offsetCache.contains(topic1));
        assertEquals(2, offsetCache.getOffset(topic1));
        assertEquals(1, offsetCache.cacheSize());
    }

    @Order(3)
    @Test
    @DisplayName("Tests whether the cache records and updates multiple offsets to be committed")
    void updateOffsetsMultiplePartitionsSameTopic() {
        final TopicPartition topic11 = new TopicPartition("topic1", 1);
        final TopicPartition topic12 = new TopicPartition("topic1", 2);
        final TopicPartition topic13 = new TopicPartition("topic1", 3);

        assertDoesNotThrow(() -> offsetCache.recordOffset(topic11, 1));
        assertDoesNotThrow(() -> offsetCache.recordOffset(topic11, 2));
        assertDoesNotThrow(() -> offsetCache.recordOffset(topic11, 2),
                "The cache should not throw exceptions for duplicate records");

        assertDoesNotThrow(() -> offsetCache.recordOffset(topic12, 1));
        assertDoesNotThrow(() -> offsetCache.recordOffset(topic12, 2));
        assertDoesNotThrow(() -> offsetCache.recordOffset(topic12, 2),
                "The cache should not throw exceptions for duplicate records");

        assertDoesNotThrow(() -> offsetCache.recordOffset(topic13, 3));
        assertDoesNotThrow(() -> offsetCache.recordOffset(topic13, 4));
        assertDoesNotThrow(() -> offsetCache.recordOffset(topic13, 5),
                "The cache should not throw exceptions for duplicate records");

        assertTrue(offsetCache.contains(topic11), "The cache should contain an entry for the topic1 on partition 1");
        assertTrue(offsetCache.contains(topic12), "The cache should contain an entry for the topic1 on partition 2");
        assertTrue(offsetCache.contains(topic13), "The cache should contain an entry for the topic1 on partition 3");

        assertEquals(2, offsetCache.getOffset(topic11));
        assertEquals(2, offsetCache.getOffset(topic12));
        assertEquals(5, offsetCache.getOffset(topic13));

        assertEquals(3, offsetCache.cacheSize());
    }

    @Order(4)
    @Test
    @DisplayName("Tests whether the cache removes committed offsets")
    void removeCommittedEntries() {
        final TopicPartition topic11 = new TopicPartition("topic1", 1);
        final TopicPartition topic12 = new TopicPartition("topic1", 2);
        final TopicPartition topic13 = new TopicPartition("topic1", 3);

        final Map<TopicPartition, OffsetAndMetadata> offsets = Collections.singletonMap(topic12, new OffsetAndMetadata(3));

        offsetCache.removeCommittedEntries(offsets, null);

        assertEquals(2, offsetCache.getOffset(topic11));
        assertNull(offsetCache.getOffset(topic12));
        assertEquals(5, offsetCache.getOffset(topic13));

        assertEquals(2, offsetCache.cacheSize());
    }

    @Order(5)
    @Test
    @DisplayName("Tests whether the cache retains offsets if the consumer fails to commit")
    void removeRetainCommittedEntries() {
        final TopicPartition topic13 = new TopicPartition("topic1", 3);
        final Map<TopicPartition, OffsetAndMetadata> offsets = Collections.singletonMap(topic13, new OffsetAndMetadata(3));

        assertDoesNotThrow(() -> offsetCache.removeCommittedEntries(offsets, new Exception("Fake exception")));
        assertEquals(2, offsetCache.cacheSize());
    }
}
