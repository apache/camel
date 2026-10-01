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

import org.apache.camel.Exchange;
import org.apache.camel.spi.UnitOfWork;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
    void failedAbortDuringRollbackClosesAndMarks() {
        when(exchange.getException()).thenReturn(new RuntimeException("downstream failed"));
        Mockito.doThrow(new KafkaException("abort failed")).when(kafkaProducer).abortTransaction();

        sync.onDone(exchange);

        // abort failed, so the producer is unusable and must be rebuilt rather than left wedged
        verify(kafkaProducer).close();
        verify(owner).markProducerClosedForRecreation();
    }
}
