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

import org.apache.camel.Exchange;
import org.apache.camel.spi.StateRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;

/**
 * Factory to create a new {@link KafkaManualCommit} to store on the {@link Exchange}.
 */
public interface KafkaManualCommitFactory {
    /**
     * A holder class for the Camel exchange related payload, such as the exchange itself, the consumer, thread ID, etc
     */
    class CamelExchangePayload {
        public final Exchange exchange;
        public final Consumer<?, ?> consumer;
        public final String threadId;
        public final StateRepository<String, String> offsetRepository;

        public CamelExchangePayload(Exchange exchange, Consumer<?, ?> consumer, String threadId,
                                    StateRepository<String, String> offsetRepository) {
            this.exchange = exchange;
            this.consumer = consumer;
            this.threadId = threadId;
            this.offsetRepository = offsetRepository;
        }
    }

    /**
     * A holder class for the payload related to the Kafka record, such as partition and topic information
     */
    class KafkaRecordPayload {
        public final TopicPartition partition;
        public final long recordOffset;
        public final long commitTimeout;

        public KafkaRecordPayload(TopicPartition partition, long recordOffset, long commitTimeout) {
            this.partition = partition;
            this.recordOffset = recordOffset;
            this.commitTimeout = commitTimeout;
        }
    }

    /**
     * Creates a new instance
     *
     * @param camelExchangePayload the exchange-related payload from Camel
     * @param kafkaRecordPayload   the record-related payload from Kafka
     */
    KafkaManualCommit newInstance(
            CamelExchangePayload camelExchangePayload, KafkaRecordPayload kafkaRecordPayload, CommitManager commitManager);
}
