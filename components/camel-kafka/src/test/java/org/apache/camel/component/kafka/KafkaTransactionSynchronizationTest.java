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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.Exchange;
import org.apache.camel.component.kafka.consumer.DefaultKafkaManualCommit;
import org.apache.camel.component.kafka.consumer.KafkaExactlyOnceMetadata;
import org.apache.camel.spi.UnitOfWork;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KafkaTransactionSynchronization#onDone}: a fatal transactional error must close the shared
 * producer and have the owner rebuild it (otherwise the route wedges on a dead producer), an abortable error must abort
 * the transaction (so the producer stays usable), and a failed commit must not be left silently open (CAMEL-24782).
 */
class KafkaTransactionSynchronizationTest {

    private static final String TX_ID = "tx-1";

    @SuppressWarnings("rawtypes")
    private Producer kafkaProducer;
    private KafkaProducer owner;
    private Exchange exchange;
    private KafkaTransactionSynchronization sync;

    @BeforeEach
    void setUp() {
        kafkaProducer = Mockito.mock(Producer.class);
        owner = Mockito.mock(KafkaProducer.class);
        exchange = Mockito.mock(Exchange.class);
        when(exchange.getUnitOfWork()).thenReturn(Mockito.mock(UnitOfWork.class));
        sync = new KafkaTransactionSynchronization(TX_ID, kafkaProducer, owner);
    }

    @Test
    void fatalExceptionClosesAndMarksForRecreation() {
        when(exchange.getException()).thenReturn(new ProducerFencedException("fenced"));

        sync.onDone(exchange);

        verify(kafkaProducer).close();
        verify(owner).markProducerClosedForRecreation();
        verify(kafkaProducer, never()).abortTransaction();
    }

    @Test
    void nonFatalExceptionAbortsTransaction() {
        when(exchange.getException()).thenReturn(new RuntimeException("downstream failed"));

        sync.onDone(exchange);

        verify(kafkaProducer).abortTransaction();
        verify(kafkaProducer, never()).close();
        verify(owner, never()).markProducerClosedForRecreation();
    }

    @Test
    void rollbackOnlyAbortsTransaction() {
        when(exchange.getException()).thenReturn(null);
        when(exchange.isRollbackOnly()).thenReturn(true);

        sync.onDone(exchange);

        verify(kafkaProducer).abortTransaction();
        verify(kafkaProducer, never()).commitTransaction();
        verify(kafkaProducer, never()).close();
    }

    @Test
    void commitSuccessCommitsTransaction() {
        when(exchange.getException()).thenReturn(null);
        when(exchange.isRollbackOnly()).thenReturn(false);

        sync.onDone(exchange);

        verify(kafkaProducer).commitTransaction();
        verify(kafkaProducer, never()).abortTransaction();
        verify(kafkaProducer, never()).close();
        verify(owner, never()).releaseExactlyOnceTransaction(Mockito.any());
    }

    @Test
    void commitFatalErrorClosesAndMarks() {
        when(exchange.getException()).thenReturn(null);
        when(exchange.isRollbackOnly()).thenReturn(false);
        Mockito.doThrow(new OutOfOrderSequenceException("gap")).when(kafkaProducer).commitTransaction();

        sync.onDone(exchange);

        verify(kafkaProducer).close();
        verify(owner).markProducerClosedForRecreation();
        verify(kafkaProducer, never()).abortTransaction();
        verify(exchange).setException(Mockito.isA(OutOfOrderSequenceException.class));
    }

    @Test
    void commitAbortableErrorAbortsTransaction() {
        when(exchange.getException()).thenReturn(null);
        when(exchange.isRollbackOnly()).thenReturn(false);
        Mockito.doThrow(new KafkaException("commit failed, abortable")).when(kafkaProducer).commitTransaction();

        sync.onDone(exchange);

        // the still-open transaction is aborted so the next beginTransaction() can succeed, and the producer is kept
        verify(kafkaProducer).abortTransaction();
        verify(kafkaProducer, never()).close();
        verify(owner, never()).markProducerClosedForRecreation();
        verify(exchange).setException(Mockito.isA(KafkaException.class));
    }

    @Test
    void sendsConsumerOffsetsBeforeCommittingTransaction() {
        when(exchange.getException()).thenReturn(null);
        when(exchange.isRollbackOnly()).thenReturn(false);
        Map<TopicPartition, OffsetAndMetadata> offsets = Map.of(
                new TopicPartition("orders", 0), new OffsetAndMetadata(43));
        ConsumerGroupMetadata group = Mockito.mock(ConsumerGroupMetadata.class);
        sync = new KafkaTransactionSynchronization(TX_ID, kafkaProducer, owner, offsets, group);

        when(exchange.getExchangeId()).thenReturn("exchange-1");
        sync.onDone(exchange);

        var order = inOrder(kafkaProducer);
        order.verify(kafkaProducer).sendOffsetsToTransaction(offsets, group);
        order.verify(kafkaProducer).commitTransaction();
        verify(owner).releaseExactlyOnceTransaction("exchange-1");
    }

    @Test
    void failedSendingConsumerOffsetsUsesProducerRecovery() {
        when(exchange.getException()).thenReturn(null);
        when(exchange.isRollbackOnly()).thenReturn(false);
        Map<TopicPartition, OffsetAndMetadata> offsets = Map.of(
                new TopicPartition("orders", 0), new OffsetAndMetadata(43));
        ConsumerGroupMetadata group = Mockito.mock(ConsumerGroupMetadata.class);
        Mockito.doThrow(new KafkaException("offset commit failed"))
                .when(kafkaProducer).sendOffsetsToTransaction(offsets, group);
        sync = new KafkaTransactionSynchronization(TX_ID, kafkaProducer, owner, offsets, group);

        when(exchange.getExchangeId()).thenReturn("exchange-2");
        sync.onDone(exchange);

        verify(kafkaProducer).abortTransaction();
        verify(owner).releaseExactlyOnceTransaction("exchange-2");
        verify(kafkaProducer, never()).commitTransaction();
        verify(exchange).setException(Mockito.isA(KafkaException.class));
        verify(kafkaProducer, never()).close();
    }

    @Test
    void handledSendFailureAbortsOutputAndCommitsOnlySourceOffsets() {
        when(exchange.getException()).thenReturn(null);
        when(exchange.isRollbackOnly()).thenReturn(false);
        when(exchange.getProperty(KafkaExactlyOnceMetadata.SEND_FAILURE_PROPERTY, Throwable.class))
                .thenReturn(new KafkaException("record send failed but the route handled it"));
        Map<TopicPartition, OffsetAndMetadata> offsets = Map.of(
                new TopicPartition("orders", 0), new OffsetAndMetadata(43));
        ConsumerGroupMetadata group = Mockito.mock(ConsumerGroupMetadata.class);
        sync = new KafkaTransactionSynchronization(TX_ID, kafkaProducer, owner, offsets, group, "source-exchange");

        sync.onDone(exchange);

        var order = inOrder(kafkaProducer);
        order.verify(kafkaProducer).abortTransaction();
        order.verify(kafkaProducer).beginTransaction();
        order.verify(kafkaProducer).sendOffsetsToTransaction(offsets, group);
        order.verify(kafkaProducer).commitTransaction();
        verify(owner).releaseExactlyOnceTransaction("source-exchange");
        verify(exchange, never()).setException(Mockito.any());
    }

    @Test
    void failedKafkaDeadLetterSendAbortsWithoutCommittingSourceOffsets() {
        when(exchange.getException()).thenReturn(null);
        when(exchange.isRollbackOnly()).thenReturn(false);
        when(exchange.getProperty(KafkaExactlyOnceMetadata.SEND_FAILURE_PROPERTY, Throwable.class))
                .thenReturn(new KafkaException("original Kafka send failed"));
        when(exchange.getProperty(KafkaExactlyOnceMetadata.SECONDARY_SEND_FAILURE_PROPERTY, Throwable.class))
                .thenReturn(new IllegalStateException("Kafka DLQ destination was rejected"));
        Map<TopicPartition, OffsetAndMetadata> offsets = Map.of(
                new TopicPartition("orders", 0), new OffsetAndMetadata(43));
        ConsumerGroupMetadata group = Mockito.mock(ConsumerGroupMetadata.class);
        sync = new KafkaTransactionSynchronization(TX_ID, kafkaProducer, owner, offsets, group);

        sync.onDone(exchange);

        verify(kafkaProducer).abortTransaction();
        verify(kafkaProducer, never()).beginTransaction();
        verify(kafkaProducer, never()).sendOffsetsToTransaction(
                Mockito.anyMap(), Mockito.any(ConsumerGroupMetadata.class));
        verify(kafkaProducer, never()).commitTransaction();
        verify(exchange).setException(Mockito.isA(IllegalStateException.class));
        verify(exchange).setProperty(KafkaExactlyOnceMetadata.TRANSACTION_ABORTED_PROPERTY, Boolean.TRUE);
    }

    @Test
    void secondaryFailureOnDeadLetterExchangeCopyAbortsTheSourceTransaction() {
        when(exchange.getException()).thenReturn(null);
        when(exchange.isRollbackOnly()).thenReturn(false);
        KafkaExactlyOnceMetadata metadata = sourceMetadata();
        Exchange deadLetterExchange = Mockito.mock(Exchange.class);
        when(exchange.getProperty(KafkaExactlyOnceMetadata.EXCHANGE_PROPERTY, KafkaExactlyOnceMetadata.class))
                .thenReturn(metadata);
        when(deadLetterExchange.getProperty(
                KafkaExactlyOnceMetadata.EXCHANGE_PROPERTY, KafkaExactlyOnceMetadata.class)).thenReturn(metadata);

        KafkaException originalFailure = new KafkaException("original Kafka send failed");
        IllegalStateException deadLetterFailure = new IllegalStateException("Kafka DLQ destination was rejected");
        KafkaExactlyOnceMetadata.recordSendFailure(exchange, originalFailure);
        KafkaExactlyOnceMetadata.recordSendFailure(deadLetterExchange, deadLetterFailure);
        sync = new KafkaTransactionSynchronization(TX_ID, kafkaProducer, owner, metadata, "source-exchange");

        sync.onDone(exchange);

        verify(kafkaProducer).abortTransaction();
        verify(kafkaProducer, never()).beginTransaction();
        verify(kafkaProducer, never()).sendOffsetsToTransaction(
                Mockito.anyMap(), Mockito.any(ConsumerGroupMetadata.class));
        verify(kafkaProducer, never()).commitTransaction();
        verify(exchange).setException(Mockito.isA(IllegalStateException.class));
        verify(exchange).setProperty(KafkaExactlyOnceMetadata.TRANSACTION_ABORTED_PROPERTY, Boolean.TRUE);
    }

    @Test
    void rejectedMetadataClaimAbortsTheSourceTransaction() {
        when(exchange.getException()).thenReturn(null);
        when(exchange.isRollbackOnly()).thenReturn(false);
        ConsumerGroupMetadata group = Mockito.mock(ConsumerGroupMetadata.class);
        when(group.groupId()).thenReturn("orders-group");
        when(group.generationId()).thenReturn(1);
        when(group.memberId()).thenReturn("member-1");
        when(group.groupInstanceId()).thenReturn(Optional.empty());
        DefaultKafkaManualCommit manual = Mockito.mock(DefaultKafkaManualCommit.class);
        when(manual.getConsumerGroupMetadata()).thenReturn(group);
        when(manual.getPartition()).thenReturn(new TopicPartition("orders", 0));
        when(manual.getRecordOffset()).thenReturn(42L);
        when(manual.getLeaderEpoch()).thenReturn(Optional.empty());
        KafkaExactlyOnceMetadata metadata
                = KafkaExactlyOnceMetadata.fromManualCommits(List.of(manual), "source-exchange");
        metadata.rejectClaim("exactlyOnce source metadata cannot be claimed from a copied exchange");
        sync = new KafkaTransactionSynchronization(TX_ID, kafkaProducer, owner, metadata, "source-exchange");

        sync.onDone(exchange);

        verify(kafkaProducer).abortTransaction();
        verify(kafkaProducer, never()).sendOffsetsToTransaction(
                Mockito.anyMap(), Mockito.any(ConsumerGroupMetadata.class));
        verify(kafkaProducer, never()).commitTransaction();
        verify(exchange).setException(Mockito.isA(IllegalStateException.class));
        verify(exchange).setProperty(KafkaExactlyOnceMetadata.TRANSACTION_ABORTED_PROPERTY, Boolean.TRUE);
    }

    @Test
    void handledSendFailureRecreatesProducerWhenAbortFails() {
        when(exchange.getException()).thenReturn(null);
        when(exchange.isRollbackOnly()).thenReturn(false);
        when(exchange.getProperty(KafkaExactlyOnceMetadata.SEND_FAILURE_PROPERTY, Throwable.class))
                .thenReturn(new KafkaException("send failed and was handled"));
        Producer replacement = Mockito.mock(Producer.class);
        Mockito.doThrow(new KafkaException("abort failed")).when(kafkaProducer).abortTransaction();
        when(owner.getKafkaProducer()).thenReturn(replacement);
        Map<TopicPartition, OffsetAndMetadata> offsets = Map.of(
                new TopicPartition("orders", 0), new OffsetAndMetadata(43));
        ConsumerGroupMetadata group = Mockito.mock(ConsumerGroupMetadata.class);
        sync = new KafkaTransactionSynchronization(TX_ID, kafkaProducer, owner, offsets, group, "source-exchange");

        sync.onDone(exchange);

        verify(owner).markProducerClosedForRecreation();
        verify(owner).recreateProducerIfClosed();
        var order = inOrder(replacement);
        order.verify(replacement).beginTransaction();
        order.verify(replacement).sendOffsetsToTransaction(offsets, group);
        order.verify(replacement).commitTransaction();
        verify(owner).releaseExactlyOnceTransaction("source-exchange");
    }

    @Test
    void synchronizationDoesNotAllowExchangeHandover() {
        sync = new KafkaTransactionSynchronization(
                TX_ID, kafkaProducer, owner, Map.of(
                        new TopicPartition("orders", 0), new OffsetAndMetadata(43)),
                Mockito.mock(ConsumerGroupMetadata.class),
                "source-exchange");
        org.junit.jupiter.api.Assertions.assertFalse(sync.allowHandover());
    }

    @Test
    void failedAbortDuringRollbackClosesAndMarks() {
        when(exchange.getException()).thenReturn(new RuntimeException("downstream failed"));
        Mockito.doThrow(new KafkaException("abort failed")).when(kafkaProducer).abortTransaction();

        sync.onDone(exchange);

        // abort failed, so the producer is unusable and must be rebuilt rather than left wedged
        verify(kafkaProducer).close();
        verify(owner).markProducerClosedForRecreation();
    }

    private static KafkaExactlyOnceMetadata sourceMetadata() {
        DefaultKafkaManualCommit manual = Mockito.mock(DefaultKafkaManualCommit.class);
        ConsumerGroupMetadata group = Mockito.mock(ConsumerGroupMetadata.class);
        when(group.groupId()).thenReturn("orders-group");
        when(group.generationId()).thenReturn(1);
        when(group.memberId()).thenReturn("member-1");
        when(group.groupInstanceId()).thenReturn(Optional.empty());
        when(manual.getPartition()).thenReturn(new TopicPartition("orders", 0));
        when(manual.getRecordOffset()).thenReturn(42L);
        when(manual.getLeaderEpoch()).thenReturn(Optional.empty());
        when(manual.getConsumerGroupMetadata()).thenReturn(group);
        return KafkaExactlyOnceMetadata.fromManualCommits(List.of(manual), "source-exchange");
    }
}
