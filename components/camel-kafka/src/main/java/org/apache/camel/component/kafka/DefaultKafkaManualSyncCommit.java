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
package org.apache.camel.component.kafka;

import java.time.Duration;
import java.util.Collections;

import org.apache.camel.component.kafka.consumer.support.KafkaRecordProcessor;
import org.apache.camel.spi.StateRepository;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultKafkaManualSyncCommit extends DefaultKafkaManualCommit implements KafkaManualCommit {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultKafkaManualSyncCommit.class);

    public DefaultKafkaManualSyncCommit(KafkaConsumer consumer, String topicName, String threadId,
                                        StateRepository<String, String> offsetRepository, TopicPartition partition,
                                        long recordOffset, long commitTimeout) {
        super(consumer, topicName, threadId, offsetRepository, partition, recordOffset, commitTimeout);

        LOG.debug("Using commit timeout of {}", commitTimeout);
    }

    @Override
    public void commitSync() {
        commit();
    }

    @Override
    public void commit() {
        commitOffset(getOffsetRepository(), getPartition(), getRecordOffset());
    }

    protected void commitOffset(StateRepository<String, String> offsetRepository, TopicPartition partition, long recordOffset) {
        if (recordOffset != KafkaRecordProcessor.START_OFFSET) {
            if (offsetRepository != null) {
                offsetRepository.setState(serializeOffsetKey(partition), serializeOffsetValue(recordOffset));
            } else {
                LOG.debug("CommitSync {} from topic {} with offset: {}", getThreadId(), getTopicName(), recordOffset);
                getConsumer().commitSync(Collections.singletonMap(partition, new OffsetAndMetadata(recordOffset + 1)),
                        Duration.ofMillis(getCommitTimeout()));
                LOG.debug("CommitSync done for {} from topic {} with offset: {}", getThreadId(), getTopicName(), recordOffset);
            }
        }
    }
}
