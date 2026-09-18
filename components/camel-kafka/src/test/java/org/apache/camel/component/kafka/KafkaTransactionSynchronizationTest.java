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

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.spi.UnitOfWork;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaTransactionSynchronizationTest {

    private static Exchange successfulExchange() {
        Exchange exchange = mock(Exchange.class);
        UnitOfWork uow = mock(UnitOfWork.class);
        when(exchange.getUnitOfWork()).thenReturn(uow);
        when(exchange.getException()).thenReturn(null);
        when(exchange.isRollbackOnly()).thenReturn(false);
        return exchange;
    }

    @Test
    void commitsConsumerOffsetsInsideTransactionForExactlyOnce() {
        Producer<?, ?> producer = mock(Producer.class);
        Map<TopicPartition, OffsetAndMetadata> offsets
                = Map.of(new TopicPartition("orders", 0), new OffsetAndMetadata(43));
        ConsumerGroupMetadata groupMetadata = new ConsumerGroupMetadata("orders-group");

        KafkaTransactionSynchronization sync
                = new KafkaTransactionSynchronization("tx-1", producer, offsets, groupMetadata);
        sync.onDone(successfulExchange());

        // The offsets must be sent to the transaction before it is committed, so both happen atomically.
        InOrder inOrder = inOrder(producer);
        inOrder.verify(producer).sendOffsetsToTransaction(offsets, groupMetadata);
        inOrder.verify(producer).commitTransaction();
    }

    @Test
    void commitsWithoutSendingOffsetsWhenNotExactlyOnce() {
        Producer<?, ?> producer = mock(Producer.class);

        KafkaTransactionSynchronization sync = new KafkaTransactionSynchronization("tx-1", producer);
        sync.onDone(successfulExchange());

        verify(producer).commitTransaction();
        verify(producer, never()).sendOffsetsToTransaction(any(), any());
    }

    @Test
    void abortsTransactionWhenSendOffsetsFails() {
        Producer<?, ?> producer = mock(Producer.class);
        Map<TopicPartition, OffsetAndMetadata> offsets
                = Map.of(new TopicPartition("orders", 0), new OffsetAndMetadata(43));
        ConsumerGroupMetadata groupMetadata = new ConsumerGroupMetadata("orders-group");
        doThrow(new KafkaException("boom")).when(producer).sendOffsetsToTransaction(offsets, groupMetadata);

        KafkaTransactionSynchronization sync
                = new KafkaTransactionSynchronization("tx-1", producer, offsets, groupMetadata);
        sync.onDone(successfulExchange());

        // A failed sendOffsetsToTransaction must abort the (now open) transaction, not commit it.
        verify(producer).abortTransaction();
        verify(producer, never()).commitTransaction();
    }
}
