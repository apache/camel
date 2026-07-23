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
package org.apache.camel.component.kafka.consumer.support.streaming;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaConsumer;
import org.apache.camel.component.kafka.consumer.CommitManager;
import org.apache.camel.component.kafka.consumer.KafkaManualCommit;
import org.apache.camel.component.kafka.serde.KafkaHeaderDeserializer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaRecordStreamingProcessorTest {

    private final KafkaConfiguration configuration = mock(KafkaConfiguration.class);
    private final Processor processor = mock(Processor.class);
    private final CommitManager commitManager = mock(CommitManager.class);
    private final KafkaConsumer kafkaConsumer = mock(KafkaConsumer.class);

    private final TopicPartition topicPartition = new TopicPartition("test-topic", 0);
    private final ConsumerRecord<Object, Object> record = new ConsumerRecord<>("test-topic", 0, 42L, "key", "value");

    @BeforeEach
    void setup() {
        Exchange exchange = mock(Exchange.class);
        Message message = mock(Message.class);
        when(kafkaConsumer.createExchange(false)).thenReturn(exchange);
        when(exchange.getMessage()).thenReturn(message);
        when(exchange.getIn()).thenReturn(message);

        when(configuration.getHeaderFilterStrategy()).thenReturn(mock(HeaderFilterStrategy.class));
        when(configuration.getHeaderDeserializer()).thenReturn(mock(KafkaHeaderDeserializer.class));
    }

    @Test
    void manualCommitStillRecordsOffset() {
        when(configuration.isAllowManualCommit()).thenReturn(true);
        when(commitManager.getManualCommit(any(), any(), any())).thenReturn(mock(KafkaManualCommit.class));

        KafkaRecordStreamingProcessor proc = new KafkaRecordStreamingProcessor(configuration, processor, commitManager);
        proc.processExchange(kafkaConsumer, topicPartition, false, false, record);

        verify(commitManager).recordOffset(topicPartition, 42L);
    }

    @Test
    void autoCommitRecordsOffset() {
        when(configuration.isAllowManualCommit()).thenReturn(false);

        KafkaRecordStreamingProcessor proc = new KafkaRecordStreamingProcessor(configuration, processor, commitManager);
        proc.processExchange(kafkaConsumer, topicPartition, false, false, record);

        verify(commitManager).recordOffset(topicPartition, 42L);
    }
}
