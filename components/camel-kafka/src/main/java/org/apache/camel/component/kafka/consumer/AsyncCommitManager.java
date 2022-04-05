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
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.camel.Exchange;
import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncCommitManager extends AbstractCommitManager {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncCommitManager.class);
    private final Consumer<?, ?> consumer;

    private final ConcurrentLinkedQueue<KafkaAsyncManualCommit> asyncCommits = new ConcurrentLinkedQueue<>();

    public AsyncCommitManager(Consumer<?, ?> consumer, KafkaConsumer kafkaConsumer, String threadId, String printableTopic) {
        super(consumer, kafkaConsumer, threadId, printableTopic);

        this.consumer = consumer;
    }

    @Override
    @Deprecated
    public void processAsyncCommits() {
        while (!asyncCommits.isEmpty()) {
            asyncCommits.poll().processAsyncCommit();
        }
    }

    @Override
    public void commit() {
        processAsyncCommits();

        if (kafkaConsumer.getEndpoint().getConfiguration().isAutoCommitEnable()) {
            LOG.info("Auto commitAsync {} from {}", threadId, printableTopic);
            consumer.commitAsync();
        }
    }

    @Override
    public void commitOffsetOnStop(TopicPartition partition, long partitionLastOffset) {
        commitAsync(consumer, partition, partitionLastOffset);
    }

    @Override
    public void commitOffset(TopicPartition partition, long partitionLastOffset) {
        // NO-OP runs async
    }

    private void commitAsync(Consumer<?, ?> consumer, TopicPartition partition, long partitionLastOffset) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Auto commitAsync on stop {} from topic {}", threadId, partition.topic());
        }

        consumer.commitAsync(
                Collections.singletonMap(partition, new OffsetAndMetadata(partitionLastOffset + 1)), null);
    }

    @Override
    public KafkaManualCommit getManualCommit(
            Exchange exchange, TopicPartition partition, ConsumerRecord<Object, Object> record) {

        KafkaManualCommitFactory manualCommitFactory = kafkaConsumer.getEndpoint().getKafkaManualCommitFactory();
        if (manualCommitFactory == null) {
            manualCommitFactory = new DefaultKafkaManualAsyncCommitFactory();
        }

        return getManualCommit(exchange, partition, record, asyncCommits, manualCommitFactory);
    }
}
