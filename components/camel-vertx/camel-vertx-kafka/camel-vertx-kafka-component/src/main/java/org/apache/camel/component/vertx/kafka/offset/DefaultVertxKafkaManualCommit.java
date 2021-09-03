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
package org.apache.camel.component.vertx.kafka.offset;

import java.util.Collections;

import io.vertx.kafka.client.common.TopicPartition;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.OffsetAndMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultVertxKafkaManualCommit implements VertxKafkaManualCommit {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultVertxKafkaManualCommit.class);

    private final KafkaConsumer<Object, Object> kafkaConsumer;
    private final String topicName;
    private final TopicPartition partition;
    private final long recordOffset;

    public DefaultVertxKafkaManualCommit(KafkaConsumer<Object, Object> kafkaConsumer,
                                         String topicName, TopicPartition partition, long recordOffset) {
        this.kafkaConsumer = kafkaConsumer;
        this.topicName = topicName;
        this.partition = partition;
        this.recordOffset = recordOffset;
    }

    @Override
    public void commit() {
        commitOffset(partition, recordOffset);
    }

    private void commitOffset(final TopicPartition partition, final long recordOffset) {
        if (recordOffset != -1) {
            LOG.info("Commit offsets from topic {} with offset: {}", topicName, recordOffset);
            kafkaConsumer.commit(Collections.singletonMap(partition, new OffsetAndMetadata(recordOffset + 1, "")));
        }
    }

    public KafkaConsumer<Object, Object> getKafkaConsumer() {
        return kafkaConsumer;
    }

    public String getTopicName() {
        return topicName;
    }

    public TopicPartition getPartition() {
        return partition;
    }

    public long getRecordOffset() {
        return recordOffset;
    }
}
