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
package org.apache.camel.component.smpp;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.support.LoggingExceptionHandler;
import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.session.DataSmResult;
import org.jsmpp.session.MessageReceiverListener;
import org.jsmpp.session.Session;
import org.jsmpp.util.MessageIDGenerator;
import org.jsmpp.util.MessageId;
import org.jsmpp.util.RandomMessageIDGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageReceiverListenerImpl implements MessageReceiverListener {

    private static final Logger LOG = LoggerFactory.getLogger(MessageReceiverListenerImpl.class);

    private MessageIDGenerator messageIDGenerator = new RandomMessageIDGenerator();
    private Consumer consumer;
    private SmppEndpoint endpoint;
    private Processor processor;
    private ExceptionHandler exceptionHandler;
    private final CountDownLatch initialized = new CountDownLatch(1);

    public MessageReceiverListenerImpl(SmppConsumer consumer, SmppEndpoint endpoint, Processor processor,
                                       ExceptionHandler exceptionHandler) {
        this.consumer = consumer;
        this.endpoint = endpoint;
        this.processor = processor;
        this.exceptionHandler = exceptionHandler;
        this.initialized.countDown();
    }

    public MessageReceiverListenerImpl(SmppEndpoint endpoint, String messageReceiverRouteId) throws Exception {
        this.endpoint = endpoint;

        this.endpoint.getCamelContext().addStartupListener((context, alreadyStarted) -> {
            Route route = context.getRoute(messageReceiverRouteId);
            if (route == null) {
                throw new IllegalArgumentException("No route with id '" + messageReceiverRouteId + "' found!");
            }
            this.consumer = route.getConsumer();
            this.processor = this.consumer.getProcessor();
            this.exceptionHandler = new LoggingExceptionHandler(endpoint.getCamelContext(), this.getClass());
            this.initialized.countDown();
        });
    }

    @Override
    public void onAcceptAlertNotification(AlertNotification alertNotification) {
        try {
            // wait for initialization in case consumer is active before startup listener above has been executed
            initialized.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // ignore
        }
        LOG.debug("Received an alertNotification {}", alertNotification);

        Exchange exchange = createOnAcceptAlertNotificationExchange(alertNotification);
        try {
            try {
                processor.process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }

            if (exchange.getException() != null) {
                exceptionHandler.handleException("Cannot process exchange. This exception will be ignored.", exchange,
                        exchange.getException());
            }
        } finally {
            // in a finally so that a custom exception handler that throws cannot cost the release
            consumer.releaseExchange(exchange, false);
        }
    }

    @Override
    public void onAcceptDeliverSm(DeliverSm deliverSm) throws ProcessRequestException {
        try {
            // wait for initialization in case consumer is active before startup listener above has been executed
            initialized.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // ignore
        }
        LOG.debug("Received a deliverSm {}", deliverSm);

        Exchange exchange;
        try {
            exchange = createOnAcceptDeliverSmExchange(deliverSm);
        } catch (Exception e) {
            exceptionHandler.handleException("Cannot create exchange. This exception will be ignored.", e);
            return;
        }

        try {
            try {
                processor.process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }

            if (exchange.getException() != null) {
                ProcessRequestException pre = exchange.getException(ProcessRequestException.class);
                if (pre == null) {
                    pre = new ProcessRequestException(exchange.getException().getMessage(), 255, exchange.getException());
                }
                throw pre;
            }
        } finally {
            consumer.releaseExchange(exchange, false);
        }
    }

    @Override
    public DataSmResult onAcceptDataSm(DataSm dataSm, Session session) throws ProcessRequestException {
        try {
            // wait for initialization in case consumer is active before startup listener above has been executed
            initialized.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // ignore
        }
        LOG.debug("Received a dataSm {}", dataSm);

        MessageId newMessageId = messageIDGenerator.newMessageId();
        Exchange exchange = createOnAcceptDataSmExchange(dataSm, newMessageId.getValue());
        try {
            try {
                processor.process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }

            if (exchange.getException() != null) {
                ProcessRequestException pre = exchange.getException(ProcessRequestException.class);
                if (pre == null) {
                    pre = new ProcessRequestException(exchange.getException().getMessage(), 255, exchange.getException());
                }
                throw pre;
            }

            return new DataSmResult(newMessageId, dataSm.getOptionalParameters());
        } finally {
            consumer.releaseExchange(exchange, false);
        }
    }

    public void setMessageIDGenerator(MessageIDGenerator messageIDGenerator) {
        this.messageIDGenerator = messageIDGenerator;
    }

    /**
     * Creates the exchange for a received {@code deliver_sm}.
     * <p/>
     * Built through {@link Consumer#createExchange(boolean)} rather than on the endpoint, as
     * {@link org.apache.camel.Endpoint#createExchange()} instructs: an exchange a consumer received a message for has
     * to come from the configured {@code ExchangeFactory}, or pooling never applies to it and its {@code fromRouteId}
     * is left unset.
     *
     * @param  deliverSm the received message from the SMSC
     * @return           a new exchange
     */
    private Exchange createOnAcceptDeliverSmExchange(DeliverSm deliverSm) throws Exception {
        Exchange exchange = consumer.createExchange(false);
        try {
            exchange.setPattern(endpoint.getExchangePattern());
            exchange.setProperty(Exchange.BINDING, endpoint.getBinding());
            exchange.setIn(endpoint.getBinding().createSmppMessage(endpoint.getCamelContext(), deliverSm));
            return exchange;
        } catch (Exception e) {
            // the exchange is already out of the ExchangeFactory, and the caller's variable is still unassigned,
            // so its finally cannot release it - a failed decode would otherwise cost a pooled exchange
            consumer.releaseExchange(exchange, false);
            throw e;
        }
    }

    /**
     * Creates the exchange for a received {@code data_sm}, through the consumer for the same reason as
     * {@link #createOnAcceptDeliverSmExchange(DeliverSm)}.
     *
     * @param  dataSm        the received message from the SMSC
     * @param  smppMessageId the smpp message id which will be used in the response
     * @return               a new exchange
     */
    private Exchange createOnAcceptDataSmExchange(DataSm dataSm, String smppMessageId) {
        Exchange exchange = consumer.createExchange(false);
        try {
            exchange.setPattern(endpoint.getExchangePattern());
            exchange.setProperty(Exchange.BINDING, endpoint.getBinding());
            exchange.setIn(endpoint.getBinding().createSmppMessage(endpoint.getCamelContext(), dataSm, smppMessageId));
            return exchange;
        } catch (Exception e) {
            consumer.releaseExchange(exchange, false);
            throw e;
        }
    }

    /**
     * Create a new exchange for communicating with this endpoint from a SMSC with the specified {@link ExchangePattern}
     * such as whether its going to be an {@link ExchangePattern#InOnly} or {@link ExchangePattern#InOut} exchange
     *
     * @param  alertNotification the received message from the SMSC
     * @return                   a new exchange
     */
    public Exchange createOnAcceptAlertNotificationExchange(AlertNotification alertNotification) {
        Exchange exchange = consumer.createExchange(false);
        try {
            exchange.setProperty(Exchange.BINDING, endpoint.getBinding());
            exchange.setIn(endpoint.getBinding().createSmppMessage(endpoint.getCamelContext(), alertNotification));
            return exchange;
        } catch (Exception e) {
            consumer.releaseExchange(exchange, false);
            throw e;
        }
    }

}
