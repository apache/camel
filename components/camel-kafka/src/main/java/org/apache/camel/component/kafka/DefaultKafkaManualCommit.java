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

import org.apache.camel.spi.StateRepository;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

public abstract class DefaultKafkaManualCommit implements KafkaManualCommit {

    private final KafkaConsumer consumer;
    private final String topicName;
    private final String threadId;
    private final StateRepository<String, String> offsetRepository;
    private final TopicPartition partition;
    private final long recordOffset;
    private final long commitTimeout;

    public DefaultKafkaManualCommit(KafkaConsumer consumer, String topicName, String threadId,
                                    StateRepository<String, String> offsetRepository, TopicPartition partition,
                                    long recordOffset, long commitTimeout) {
        this.consumer = consumer;
        this.topicName = topicName;
        this.threadId = threadId;
        this.offsetRepository = offsetRepository;
        this.partition = partition;
        this.recordOffset = recordOffset;
        this.commitTimeout = commitTimeout;
    }

    @Override
    public void commitSync() {
        throw new IllegalStateException("This method is deprecated and should not be used anymore.");
    }

    protected String serializeOffsetKey(TopicPartition topicPartition) {
        return topicPartition.topic() + '/' + topicPartition.partition();
    }

    protected String serializeOffsetValue(long offset) {
        return String.valueOf(offset);
    }

    public KafkaConsumer getConsumer() {
        return consumer;
    }

    public String getTopicName() {
        return topicName;
    }

    public String getThreadId() {
        return threadId;
    }

    public StateRepository<String, String> getOffsetRepository() {
        return offsetRepository;
    }

    public TopicPartition getPartition() {
        return partition;
    }

    public long getRecordOffset() {
        return recordOffset;
    }

    public long getCommitTimeout() {
        return commitTimeout;
    }
}
