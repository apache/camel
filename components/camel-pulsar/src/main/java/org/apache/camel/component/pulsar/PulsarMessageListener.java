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
package org.apache.camel.component.pulsar;

import org.apache.camel.Exchange;
import org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders;
import org.apache.camel.component.pulsar.utils.message.PulsarMessageUtils;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageListener;
import org.apache.pulsar.client.api.PulsarClientException;

public class PulsarMessageListener implements MessageListener<byte[]> {

    private final PulsarEndpoint endpoint;
    private final PulsarConsumer pulsarConsumer;

    public PulsarMessageListener(PulsarEndpoint endpoint, PulsarConsumer pulsarConsumer) {
        this.endpoint = endpoint;
        this.pulsarConsumer = pulsarConsumer;
    }

    @Override
    public void received(final Consumer<byte[]> consumer, final Message<byte[]> message) {
        final Exchange exchange = PulsarMessageUtils.updateExchange(message, pulsarConsumer.createExchange(false));

        if (endpoint.getPulsarConfiguration().isAllowManualAcknowledgement()) {
            exchange.getIn().setHeader(PulsarMessageHeaders.MESSAGE_RECEIPT,
                    endpoint.getComponent().getPulsarMessageReceiptFactory()
                            .newInstance(exchange, message, consumer));
        }
        processAsync(exchange, consumer, message);
    }

    private void processAsync(final Exchange exchange, final Consumer<byte[]> consumer, final Message<byte[]> message) {
        pulsarConsumer.getAsyncProcessor().process(exchange, doneSync -> {
            try {
                if (exchange.getException() != null) {
                    pulsarConsumer.getExceptionHandler().handleException("Error processing exchange", exchange,
                            exchange.getException());
                } else {
                    try {
                        acknowledge(consumer, message);
                    } catch (Exception e) {
                        pulsarConsumer.getExceptionHandler().handleException("Error processing exchange", exchange,
                                exchange.getException());
                    }
                }
            } finally {
                pulsarConsumer.releaseExchange(exchange, false);
            }
        });
    }

    private void acknowledge(final Consumer<byte[]> consumer, final Message<byte[]> message)
            throws PulsarClientException {
        if (!endpoint.getPulsarConfiguration().isAllowManualAcknowledgement()) {
            consumer.acknowledge(message.getMessageId());
        }
    }

}
