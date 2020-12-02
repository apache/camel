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
package org.apache.kafka.clients.consumer;

import java.time.Duration;

import org.hamcrest.core.IsNot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class KafkaConsumerTest {

    @Mock
    private KafkaConsumer<Object, Object> kafkaConsumer;

    @BeforeEach
    public void init() {
        when(kafkaConsumer.poll(Duration.ofSeconds(1))).thenReturn(ConsumerRecords.empty());
    }

    @Test
    public void testPollGivenReturnsEmptyConsumerRecordShouldNotBeNull() {
        ConsumerRecords<Object, Object> consumerRecords = kafkaConsumer.poll(Duration.ofSeconds(1));
        assertThat(consumerRecords, IsNot.not(nullValue()));
    }

    @Test
    public void testPollGivenReturnsEmptyPartitionsShouldNotBeNull() {
        ConsumerRecords<Object, Object> consumerRecords = kafkaConsumer.poll(Duration.ofSeconds(1));
        assertThat(consumerRecords.partitions(), IsNot.not(nullValue()));
    }
}
