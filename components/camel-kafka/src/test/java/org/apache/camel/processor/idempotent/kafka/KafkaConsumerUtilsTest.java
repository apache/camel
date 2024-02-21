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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import static org.apache.camel.processor.idempotent.kafka.KafkaConsumerUtil.isReachedOffsets;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KafkaConsumerUtilsTest {

    @Test
    public void testNotReachedOffsets() {
        Map<TopicPartition, Long> targetOffsets = Map.of(
                new TopicPartition("topic1", 0), 10L,
                new TopicPartition("topic1", 1), 100L);

        Consumer<String, String> consumer = mock(Consumer.class);
        when(consumer.assignment())
                .thenReturn(
                        Set.of(
                                new TopicPartition("topic1", 0),
                                new TopicPartition("topic1", 1)));
        when(consumer.position(new TopicPartition("topic1", 0)))
                .thenReturn(9L);
        when(consumer.position(new TopicPartition("topic1", 1)))
                .thenReturn(99L);

        boolean result = isReachedOffsets(consumer, targetOffsets);
        assertFalse(result);
    }

    @Test
    public void testReachedOffsets() {
        Map<TopicPartition, Long> targetOffsets = Map.of(
                new TopicPartition("topic1", 0), 10L,
                new TopicPartition("topic1", 1), 100L);

        Consumer<String, String> consumer = mock(Consumer.class);
        when(consumer.assignment())
                .thenReturn(
                        Set.of(
                                new TopicPartition("topic1", 0),
                                new TopicPartition("topic1", 1)));
        when(consumer.position(new TopicPartition("topic1", 0)))
                .thenReturn(10L);
        when(consumer.position(new TopicPartition("topic1", 1)))
                .thenReturn(100L);

        boolean result = isReachedOffsets(consumer, targetOffsets);
        assertTrue(result);
    }

    @Test
    public void testOverrunOffsets() {
        Map<TopicPartition, Long> targetOffsets = Map.of(
                new TopicPartition("topic1", 0), 10L,
                new TopicPartition("topic1", 1), 100L);

        Consumer<String, String> consumer = mock(Consumer.class);
        when(consumer.assignment())
                .thenReturn(
                        Set.of(
                                new TopicPartition("topic1", 0),
                                new TopicPartition("topic1", 1)));
        when(consumer.position(new TopicPartition("topic1", 0)))
                .thenReturn(11L);
        when(consumer.position(new TopicPartition("topic1", 1)))
                .thenReturn(101L);

        boolean result = isReachedOffsets(consumer, targetOffsets);
        assertTrue(result);
    }

    @Test
    public void testReachedOffsetsForSomePartitions() {
        Map<TopicPartition, Long> targetOffsets = Map.of(
                new TopicPartition("topic1", 0), 10L,
                new TopicPartition("topic1", 1), 100L);

        Consumer<String, String> consumer = mock(Consumer.class);
        when(consumer.assignment())
                .thenReturn(
                        Set.of(
                                new TopicPartition("topic1", 0),
                                new TopicPartition("topic1", 1)));
        when(consumer.position(new TopicPartition("topic1", 0)))
                .thenReturn(10L);
        when(consumer.position(new TopicPartition("topic1", 1)))
                .thenReturn(99L);

        boolean result = isReachedOffsets(consumer, targetOffsets);
        assertFalse(result);
    }

    @Test
    public void testNotReachedOffsetsSomeTargetOffsetsUnspecified() {
        Map<TopicPartition, Long> targetOffsets = Map.of(
                new TopicPartition("topic1", 0), 10L);

        Consumer<String, String> consumer = mock(Consumer.class);
        when(consumer.assignment())
                .thenReturn(
                        Set.of(
                                new TopicPartition("topic1", 0),
                                new TopicPartition("topic1", 1)));
        when(consumer.position(new TopicPartition("topic1", 0)))
                .thenReturn(9L);
        when(consumer.position(new TopicPartition("topic1", 1)))
                .thenReturn(99L);

        boolean result = isReachedOffsets(consumer, targetOffsets);
        assertFalse(result);
    }

    @Test
    public void testReachedOffsetsSomeTargetOffsetsUnspecified() {
        Map<TopicPartition, Long> targetOffsets = Map.of(
                new TopicPartition("topic1", 0), 10L);

        Consumer<String, String> consumer = mock(Consumer.class);
        when(consumer.assignment())
                .thenReturn(
                        Set.of(
                                new TopicPartition("topic1", 0),
                                new TopicPartition("topic1", 1)));
        when(consumer.position(new TopicPartition("topic1", 0)))
                .thenReturn(10L);
        when(consumer.position(new TopicPartition("topic1", 1)))
                .thenReturn(99L);

        boolean result = isReachedOffsets(consumer, targetOffsets);
        assertTrue(result);
    }

    @Test
    public void testTargetOffsetsEmpty() {
        Map<TopicPartition, Long> targetOffsets = Collections.emptyMap();
        Consumer<String, String> consumer = mock(Consumer.class);
        assertThrows(IllegalArgumentException.class, () -> isReachedOffsets(consumer, targetOffsets));
    }

}
