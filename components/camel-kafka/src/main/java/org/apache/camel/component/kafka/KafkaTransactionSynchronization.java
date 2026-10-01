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
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class KafkaTransactionSynchronization extends SynchronizationAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaTransactionSynchronization.class);
    private final String transactionId;
    private final Producer kafkaProducer;
    private final KafkaProducer owner;

    public KafkaTransactionSynchronization(String transactionId, Producer kafkaProducer, KafkaProducer owner) {
        this.transactionId = transactionId;
        this.kafkaProducer = kafkaProducer;
        this.owner = owner;
    }

    @Override
    public void onDone(Exchange exchange) {
        try {
            if (exchange.getException() != null || exchange.isRollbackOnly()) {
                rollback(exchange);
            } else {
                commit(exchange);
            }
        } finally {
            exchange.getUnitOfWork().endTransactedBy(transactionId);
        }
    }

    private void commit(Exchange exchange) {
        try {
            LOG.debug("Commit kafka transaction {} with exchange {}", transactionId, exchange.getExchangeId());
            kafkaProducer.commitTransaction();
        } catch (Exception e) {
            // The commit failed. Record it and return the producer to a usable state - abort the still-open
            // transaction, or close and rebuild it when the error is fatal. The previous code only recorded the
            // exception, leaving the transaction open so the next beginTransaction() failed and wedged the route
            // (CAMEL-24782).
            exchange.setException(e);
            recover(e, "commit");
        }
    }

    private void rollback(Exchange exchange) {
        // The routing already failed; abort the transaction so the shared producer stays usable, or - when the
        // failure is a fatal transactional error that the producer cannot recover from - close and rebuild it.
        recover(exchange.getException(), "rollback");
    }

    /**
     * Returns the shared producer to a usable state after a failed or rolled-back transaction. A fatal error (the
     * producer can no longer be used - fenced, out-of-order sequence, authorization) closes it and asks the owner to
     * rebuild it before the next transaction; anything else aborts the current transaction. If the abort itself fails
     * the producer is also closed and rebuilt, so the route is never left wedged on a dead producer (CAMEL-24782).
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
            // even if close() throws, the producer is unusable - make sure the next transaction rebuilds it
            owner.markProducerClosedForRecreation();
        }
    }

    /**
     * A fatal transactional error leaves the producer permanently unusable, so it must be closed rather than aborted
     * (an abort would fail too). These are the errors the Kafka transactional producer documents as fatal.
     */
    private static boolean isFatal(Throwable cause) {
        return cause instanceof ProducerFencedException
                || cause instanceof OutOfOrderSequenceException
                || cause instanceof AuthorizationException;
    }
}
