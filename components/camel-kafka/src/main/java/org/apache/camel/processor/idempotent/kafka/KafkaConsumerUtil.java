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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.util.ObjectHelper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;

public class KafkaConsumerUtil {

    /**
     * Tests whether the Kafka consumer reached the target offsets for all specified topic partitions.
     *
     * @param  consumer      Kafka consumer. It is expected to have some assignment to topic partitions.
     * @param  targetOffsets Target offsets for topic partitions.
     * @param  <K>           Key type.
     * @param  <V>           Value type.
     * @return               {@code true} if the consumer has reached the target offsets for all specified topic
     *                       partitions.
     */
    public static <K, V> boolean isReachedOffsets(Consumer<K, V> consumer, Map<TopicPartition, Long> targetOffsets) {
        if (ObjectHelper.isEmpty(targetOffsets)) {
            throw new IllegalArgumentException("Target offsets must be non-empty");
        }

        Set<TopicPartition> partitions = consumer.assignment();

        /* If some partition is missing in the targetOffsets map, then we do not check the offset for this partition. */
        Map<TopicPartition, Long> extendedTargetOffsets = new HashMap<>(targetOffsets);
        partitions.forEach(partition -> extendedTargetOffsets.putIfAbsent(partition, Long.MIN_VALUE));

        return partitions.stream()
                .allMatch(partition -> consumer.position(partition) >= extendedTargetOffsets.get(partition));
    }

}
