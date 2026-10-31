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
import org.apache.camel.component.kafka.consumer.KafkaExactlyOnceMetadata;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class KafkaTransactionSynchronization extends SynchronizationAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaTransactionSynchronization.class);
    private final String transactionId;
    private Producer kafkaProducer;
    private final KafkaProducer owner;
    private final Map<TopicPartition, OffsetAndMetadata> offsetsToCommit;
    private final ConsumerGroupMetadata groupMetadata;
    private final String ownerExchangeId;

    public KafkaTransactionSynchronization(String transactionId, Producer kafkaProducer, KafkaProducer owner) {
        this(transactionId, kafkaProducer, owner, null, null, null);
    }

    public KafkaTransactionSynchronization(
                                           String transactionId, Producer kafkaProducer, KafkaProducer owner,
                                           Map<TopicPartition, OffsetAndMetadata> offsetsToCommit,
                                           ConsumerGroupMetadata groupMetadata) {
        this(transactionId, kafkaProducer, owner, offsetsToCommit, groupMetadata, null);
    }

    public KafkaTransactionSynchronization(
                                           String transactionId, Producer kafkaProducer, KafkaProducer owner,
                                           Map<TopicPartition, OffsetAndMetadata> offsetsToCommit,
                                           ConsumerGroupMetadata groupMetadata,
                                           String ownerExchangeId) {
        this.transactionId = transactionId;
        this.kafkaProducer = kafkaProducer;
        this.owner = owner;
        this.offsetsToCommit = offsetsToCommit;
        this.groupMetadata = groupMetadata;
        this.ownerExchangeId = ownerExchangeId;
    }

    public KafkaTransactionSynchronization(
                                           String transactionId, Producer kafkaProducer, KafkaProducer owner,
                                           KafkaExactlyOnceMetadata metadata, String ownerExchangeId) {
        this(transactionId, kafkaProducer, owner,
             metadata == null ? null : metadata.getOffsetsToCommit(),
             metadata == null ? null : metadata.getConsumerGroupMetadata(), ownerExchangeId);
    }

    /** Keep exactly-once synchronization bound to the source exchange; retain legacy handover for ordinary sends. */
    @Override
    public boolean allowHandover() {
        return offsetsToCommit == null;
    }

    /** Remember send failures even if a Camel error handler clears the exchange exception later. */
    public static void recordSendFailure(Exchange exchange, Throwable failure) {
        KafkaExactlyOnceMetadata.recordSendFailure(exchange, failure);
    }

    @Override
    public void onDone(Exchange exchange) {
        try {
            Throwable sendFailure = exchange.getProperty(KafkaExactlyOnceMetadata.SEND_FAILURE_PROPERTY, Throwable.class);
            if (exchange.getException() != null || exchange.isRollbackOnly()) {
                rollback(exchange);
                markAborted(exchange);
            } else if (offsetsToCommit != null && sendFailure != null) {
                // The route error handler handled a failed Kafka send. Abort every output record from the failed
                // transaction, then commit only the source offsets in a fresh transaction so the poison record is
                // deliberately skipped instead of being replayed forever.
                commitOffsetsAfterHandledSendFailure(exchange, sendFailure);
            } else {
                commit(exchange);
                if (exchange.getException() != null) {
                    markAborted(exchange);
                }
            }
        } finally {
            try {
                exchange.getUnitOfWork().endTransactedBy(transactionId);
            } finally {
                if (offsetsToCommit != null) {
                    // allowHandover() is false, but keep the acquired ID as the source of truth even if a caller
                    // completes a copied exchange.
                    owner.releaseExactlyOnceTransaction(
                            ownerExchangeId != null ? ownerExchangeId : exchange.getExchangeId());
                }
            }
        }
    }

    private void markAborted(Exchange exchange) {
        if (offsetsToCommit != null) {
            exchange.setProperty(KafkaExactlyOnceMetadata.TRANSACTION_ABORTED_PROPERTY, Boolean.TRUE);
        }
    }

    private void commit(Exchange exchange) {
        try {
            LOG.debug("Commit kafka transaction {} with exchange {}", transactionId, exchange.getExchangeId());
            if (offsetsToCommit != null) {
                LOG.debug("Sending {} consumer offset(s) to kafka transaction {}",
                        offsetsToCommit.size(), transactionId);
                kafkaProducer.sendOffsetsToTransaction(offsetsToCommit, groupMetadata);
            }
            kafkaProducer.commitTransaction();
        } catch (Exception e) {
            if (offsetsToCommit != null) {
                LOG.warn("Kafka exactly-once offset commit failed. Verify that the source consumer and destination "
                         + "producer use the same Kafka cluster and that the captured consumer group generation "
                         + "is current.",
                        e);
            }
            exchange.setException(e);
            recover(e, "commit");
        }
    }

    private void commitOffsetsAfterHandledSendFailure(Exchange exchange, Throwable sendFailure) {
        try {
            kafkaProducer.abortTransaction();
        } catch (Exception abortFailure) {
            // The old producer may be fenced or left in an unusable transaction state. Close it and
            // initialize a fresh producer with the same transactional.id before trying the offset-only
            // transaction. If recreation fails, leave the source record uncommitted so the consumer replays it.
            try {
                closeForRecreation();
                owner.recreateProducerIfClosed();
                kafkaProducer = owner.getKafkaProducer();
                exchange.setException(null);
            } catch (Exception recreationFailure) {
                abortFailure.addSuppressed(recreationFailure);
                exchange.setException(abortFailure);
                markAborted(exchange);
                return;
            }
        }

        try {
            kafkaProducer.beginTransaction();
            kafkaProducer.sendOffsetsToTransaction(offsetsToCommit, groupMetadata);
            kafkaProducer.commitTransaction();
            LOG.warn("Skipped source offset(s) in transaction {} after a handled Kafka send failure: {}",
                    transactionId, sendFailure.toString());
        } catch (Exception offsetCommitFailure) {
            exchange.setException(offsetCommitFailure);
            recover(offsetCommitFailure, "offset-only commit after handled send failure");
            markAborted(exchange);
        }
    }

    private void rollback(Exchange exchange) {
        recover(exchange.getException(), "rollback");
    }

    /**
     * Returns the shared producer to a usable state after a failed or rolled-back transaction. A fatal error leaves the
     * producer unusable and asks the owner to rebuild it; an abortable error aborts the current transaction.
     */
    private void recover(Throwable cause, String phase) {
        if (isFatal(cause)) {
            LOG.warn("Closing kafka producer for transaction {} after a fatal error during {}: {}",
                    transactionId, phase, cause.toString());
            closeForRecreation();
            return;
        }
        try {
            LOG.warn("Abort kafka transaction {} during {}", transactionId, phase);
            kafkaProducer.abortTransaction();
        } catch (Exception abortFailure) {
            LOG.warn("Closing kafka producer for transaction {} after a failed abort during {}: {}",
                    transactionId, phase, abortFailure.toString(), abortFailure);
            closeForRecreation();
        }
    }

    private void closeForRecreation() {
        try {
            kafkaProducer.close();
        } finally {
            owner.markProducerClosedForRecreation();
        }
    }

    /** Kafka documents these transactional errors as fatal: the producer must be closed, not merely aborted. */
    static boolean isFatal(Throwable cause) {
        return cause instanceof ProducerFencedException
                || cause instanceof OutOfOrderSequenceException
                || cause instanceof AuthorizationException;
    }
}
