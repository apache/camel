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
package org.apache.camel.component.kafka.consumer.support.classic;

import java.util.Set;

import org.apache.camel.spi.StateRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A resume strategy that uses Kafka's offset for resuming
 */
public class OffsetPartitionAssignmentAdapter implements PartitionAssignmentAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(OffsetPartitionAssignmentAdapter.class);

    private final StateRepository<String, String> offsetRepository;
    private Consumer<?, ?> consumer;

    public OffsetPartitionAssignmentAdapter(StateRepository<String, String> offsetRepository) {
        this.offsetRepository = offsetRepository;
    }

    @Override
    public void setConsumer(Consumer<?, ?> consumer) {
        this.consumer = consumer;
    }

    private void resumeFromOffset(final Consumer<?, ?> consumer, TopicPartition topicPartition, String offsetState) {
        // The state contains the last read offset, so you need to seek from the next one
        long offset = deserializeOffsetValue(offsetState) + 1;
        LOG.debug("Resuming partition {} from offset {} from state", topicPartition.partition(), offset);
        consumer.seek(topicPartition, offset);
    }

    public void handlePartitionAssignment() {
        Set<TopicPartition> assignments = consumer.assignment();
        for (TopicPartition topicPartition : assignments) {
            String offsetState = offsetRepository.getState(serializeOffsetKey(topicPartition));
            if (offsetState != null && !offsetState.isEmpty()) {
                resumeFromOffset(consumer, topicPartition, offsetState);
            }
        }
    }

    public static String serializeOffsetKey(TopicPartition topicPartition) {
        return topicPartition.topic() + '/' + topicPartition.partition();
    }

    public static long deserializeOffsetValue(String offset) {
        return Long.parseLong(offset);
    }
}
