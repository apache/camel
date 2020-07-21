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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.pulsar.utils.message.PulsarMessageHeaders;
import org.apache.camel.component.pulsar.utils.message.PulsarMessageUtils;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageListener;
import org.apache.pulsar.client.api.PulsarClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PulsarMessageListener implements MessageListener<byte[]> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PulsarMessageListener.class);

    private final PulsarEndpoint endpoint;
    private final PulsarConsumer pulsarConsumer;

    public PulsarMessageListener(PulsarEndpoint endpoint, PulsarConsumer pulsarConsumer) {
        this.endpoint = endpoint;
        this.pulsarConsumer = pulsarConsumer;
    }

    @Override
    public void received(final Consumer<byte[]> consumer, final Message<byte[]> message) {
        final Exchange exchange = PulsarMessageUtils.updateExchange(message, endpoint.createExchange());

        try {
            if (endpoint.getPulsarConfiguration().isAllowManualAcknowledgement()) {
                exchange.getIn().setHeader(PulsarMessageHeaders.MESSAGE_RECEIPT,
                        endpoint.getComponent().getPulsarMessageReceiptFactory()
                                .newInstance(exchange, message, consumer));
            }
            if (endpoint.isSynchronous()) {
                process(exchange, consumer, message);
            } else {
                processAsync(exchange, consumer, message);
            }
        } catch (Exception exception) {
            handleProcessorException(exchange, exception);
        }
    }

    private void process(final Exchange exchange, final Consumer<byte[]> consumer, final Message<byte[]> message) throws Exception {
        pulsarConsumer.getProcessor().process(exchange);
        acknowledge(consumer, message);
    }

    private void processAsync(final Exchange exchange, final Consumer<byte[]> consumer, final Message<byte[]> message) {
        pulsarConsumer.getAsyncProcessor().process(exchange, new AsyncCallback() {
            @Override
            public void done(boolean doneSync) {
                if (exchange.getException() != null) {
                    handleProcessorException(exchange, exchange.getException());
                } else {
                    try {
                        acknowledge(consumer, message);
                    } catch (Exception e) {
                        handleProcessorException(exchange, e);
                    }
                }
            }
        });
    }

    private void acknowledge(final Consumer<byte[]> consumer, final Message<byte[]> message)
            throws PulsarClientException {
        if (!endpoint.getPulsarConfiguration().isAllowManualAcknowledgement()) {
            consumer.acknowledge(message.getMessageId());
        }
    }

    private void handleProcessorException(final Exchange exchange, final Exception exception) {
        final Exchange exchangeWithException = PulsarMessageUtils
                .updateExchangeWithException(exception, exchange);

        pulsarConsumer.getExceptionHandler()
                .handleException("An error occurred", exchangeWithException, exception);
    }

}
