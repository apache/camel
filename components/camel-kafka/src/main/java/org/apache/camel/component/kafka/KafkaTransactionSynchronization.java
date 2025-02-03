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
import org.apache.kafka.common.KafkaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class KafkaTransactionSynchronization extends SynchronizationAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaTransactionSynchronization.class);
    private final String transactionId;
    private final Producer kafkaProducer;

    public KafkaTransactionSynchronization(String transactionId, Producer kafkaProducer) {
        this.transactionId = transactionId;
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    public void onDone(Exchange exchange) {
        try {
            if (exchange.getException() != null || exchange.isRollbackOnly()) {
                if (exchange.getException() instanceof KafkaException) {
                    LOG.warn("Catch {} and will close kafka producer with transaction {} ", exchange.getException(),
                            transactionId);
                    kafkaProducer.close();
                } else {
                    LOG.warn("Abort kafka transaction {} with exchange {}", transactionId, exchange.getExchangeId());
                    kafkaProducer.abortTransaction();
                }
            } else {
                LOG.debug("Commit kafka transaction {} with exchange {}", transactionId, exchange.getExchangeId());
                kafkaProducer.commitTransaction();
            }
        } catch (KafkaException e) {
            exchange.setException(e);
        } catch (Exception e) {
            exchange.setException(e);
            LOG.warn("Abort kafka transaction {} with exchange {} due to {} ", transactionId, exchange.getExchangeId(),
                    e.getMessage(), e);
            kafkaProducer.abortTransaction();
        } finally {
            exchange.getUnitOfWork().endTransactedBy(transactionId);
        }
    }
}
