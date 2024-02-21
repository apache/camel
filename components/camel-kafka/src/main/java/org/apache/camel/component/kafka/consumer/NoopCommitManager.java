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

import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoopCommitManager extends AbstractCommitManager {
    private static final Logger LOG = LoggerFactory.getLogger(NoopCommitManager.class);

    public NoopCommitManager(Consumer<?, ?> consumer, KafkaConsumer kafkaConsumer, String threadId, String printableTopic) {
        super(consumer, kafkaConsumer, threadId, printableTopic);
    }

    @Override
    public void commit() {
        LOG.info("Auto commit on {} from {} is enabled via Kafka consumer (NO-OP)", threadId, printableTopic);

    }

    @Override
    public void commit(TopicPartition partition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Auto commit to offset {} from topic {} is disabled (NO-OP)", threadId, partition.topic());
        }
    }

    @Override
    public void recordOffset(TopicPartition partition, long partitionLastOffset) {
        // NO-OP
    }
}
