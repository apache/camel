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

import java.util.Collection;
import java.util.Collections;

import org.apache.camel.component.kafka.consumer.support.KafkaRecordProcessor;
import org.apache.camel.spi.StateRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultKafkaManualAsyncCommit extends DefaultKafkaManualCommit implements KafkaAsyncManualCommit {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultKafkaManualAsyncCommit.class);

    private final Collection<KafkaAsyncManualCommit> asyncCommits;

    public DefaultKafkaManualAsyncCommit(Consumer consumer, String topicName, String threadId,
                                         StateRepository<String, String> offsetRepository, TopicPartition partition,
                                         long recordOffset, long commitTimeout,
                                         Collection<KafkaAsyncManualCommit> asyncCommits) {
        super(consumer, topicName, threadId, offsetRepository, partition, recordOffset, commitTimeout);
        this.asyncCommits = asyncCommits;

        LOG.debug("Using commit timeout of {}", commitTimeout);
    }

    @Override
    public void commit() {
        asyncCommits.add(this);
    }

    @Override
    public void processAsyncCommit() {
        commitAsyncOffset(getOffsetRepository(), getPartition(), getRecordOffset());
    }

    protected void commitAsyncOffset(
            StateRepository<String, String> offsetRepository, TopicPartition partition, long recordOffset) {
        if (recordOffset != KafkaRecordProcessor.START_OFFSET) {
            if (offsetRepository != null) {
                offsetRepository.setState(serializeOffsetKey(partition), serializeOffsetValue(recordOffset));
            } else {
                LOG.debug("CommitAsync {} from topic {} with offset: {}", getThreadId(), getTopicName(), recordOffset);
                getConsumer().commitAsync(Collections.singletonMap(partition, new OffsetAndMetadata(recordOffset + 1)),
                        (offsets, exception) -> {
                            if (exception != null) {
                                LOG.error("Error during async commit for {} from topic {} with offset {}: ",
                                        getThreadId(), getTopicName(), recordOffset, exception);
                            } else {
                                LOG.debug("CommitAsync done for {} from topic {} with offset: {}", getThreadId(),
                                        getTopicName(), recordOffset);
                            }
                        });
            }
        }
    }
}
