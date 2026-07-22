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
package org.apache.camel.component.kafka.consumer.support.batching;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies which record metadata headers are propagated from the records in a batch onto the batch exchange.
 */
class KafkaRecordBatchingProcessorCommonHeadersTest {

    private CamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private Exchange record(String topic, Integer partition, Long offset) {
        Exchange exchange = new DefaultExchange(context);
        Message message = exchange.getMessage();
        message.setHeader(KafkaConstants.TOPIC, topic);
        message.setHeader(KafkaConstants.PARTITION, partition);
        message.setHeader(KafkaConstants.OFFSET, offset);
        return exchange;
    }

    private Message batchMessageFor(List<Exchange> records) {
        Exchange batch = new DefaultExchange(context);
        KafkaRecordBatchingProcessor.propagateCommonHeaders(records, batch.getMessage());
        return batch.getMessage();
    }

    @Test
    void uniformTopicAndPartitionArePropagated() {
        Message batch = batchMessageFor(List.of(
                record("orders", 0, 1L),
                record("orders", 0, 2L)));

        assertEquals("orders", batch.getHeader(KafkaConstants.TOPIC));
        assertEquals(0, batch.getHeader(KafkaConstants.PARTITION));
    }

    @Test
    void offsetIsNeverPropagatedBecauseItVariesPerRecord() {
        Message batch = batchMessageFor(List.of(
                record("orders", 0, 1L),
                record("orders", 0, 2L)));

        assertNull(batch.getHeader(KafkaConstants.OFFSET));
    }

    @Test
    void mixedTopicIsNotPropagated() {
        Message batch = batchMessageFor(List.of(
                record("orders", 0, 1L),
                record("shipments", 0, 2L)));

        assertNull(batch.getHeader(KafkaConstants.TOPIC));
        // the partition is still uniform across the batch, so it is still propagated
        assertEquals(0, batch.getHeader(KafkaConstants.PARTITION));
    }

    @Test
    void mixedPartitionIsNotPropagated() {
        Message batch = batchMessageFor(List.of(
                record("orders", 0, 1L),
                record("orders", 1, 2L)));

        assertEquals("orders", batch.getHeader(KafkaConstants.TOPIC));
        assertNull(batch.getHeader(KafkaConstants.PARTITION));
    }

    @Test
    void missingHeaderOnAnyRecordIsNotPropagated() {
        Message batch = batchMessageFor(List.of(
                record("orders", 0, 1L),
                record("orders", null, 2L)));

        assertEquals("orders", batch.getHeader(KafkaConstants.TOPIC));
        assertNull(batch.getHeader(KafkaConstants.PARTITION));
    }

    @Test
    void emptyBatchSetsNoHeaders() {
        Message batch = batchMessageFor(List.of());

        assertNull(batch.getHeader(KafkaConstants.TOPIC));
        assertNull(batch.getHeader(KafkaConstants.PARTITION));
    }
}
