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

import org.apache.camel.spi.StateRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;

public abstract class DefaultKafkaManualCommit implements KafkaManualCommit {

    protected final KafkaManualCommitFactory.CamelExchangePayload camelExchangePayload;
    protected final KafkaManualCommitFactory.KafkaRecordPayload kafkaRecordPayload;

    protected DefaultKafkaManualCommit(KafkaManualCommitFactory.CamelExchangePayload camelExchangePayload,
                                       KafkaManualCommitFactory.KafkaRecordPayload kafkaRecordPayload) {
        this.camelExchangePayload = camelExchangePayload;
        this.kafkaRecordPayload = kafkaRecordPayload;
    }

    /**
     * @deprecated Use {@link #getCamelExchangePayload()}
     */
    @Deprecated(since = "3.15.0")
    public Consumer<?, ?> getConsumer() {
        return camelExchangePayload.consumer;
    }

    public String getTopicName() {
        return getPartition().topic();
    }

    public String getThreadId() {
        return camelExchangePayload.threadId;
    }

    @Deprecated
    public StateRepository<String, String> getOffsetRepository() {
        return camelExchangePayload.offsetRepository;
    }

    public TopicPartition getPartition() {
        return kafkaRecordPayload.partition;
    }

    public long getRecordOffset() {
        return kafkaRecordPayload.recordOffset;
    }

    public long getCommitTimeout() {
        return kafkaRecordPayload.commitTimeout;
    }

    /**
     * Gets the Camel Exchange payload
     *
     * @return
     */
    public KafkaManualCommitFactory.CamelExchangePayload getCamelExchangePayload() {
        return camelExchangePayload;
    }

    /**
     * Gets the Kafka record payload
     *
     * @return
     */
    public KafkaManualCommitFactory.KafkaRecordPayload getKafkaRecordPayload() {
        return kafkaRecordPayload;
    }
}
