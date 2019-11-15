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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.ExceptionHandler;
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
    private SmppEndpoint endpoint;
    private Processor processor;
    private ExceptionHandler exceptionHandler;
    
    public MessageReceiverListenerImpl(SmppEndpoint endpoint, Processor processor, ExceptionHandler exceptionHandler) {
        this.endpoint = endpoint;
        this.processor = processor;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void onAcceptAlertNotification(AlertNotification alertNotification) {
        LOG.debug("Received an alertNotification {}", alertNotification);

        Exchange exchange = endpoint.createOnAcceptAlertNotificationExchange(alertNotification);
        try {
            processor.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }

        if (exchange.getException() != null) {
            exceptionHandler.handleException("Cannot process exchange. This exception will be ignored.", exchange, exchange.getException());
        }
    }

    @Override
    public void onAcceptDeliverSm(DeliverSm deliverSm) throws ProcessRequestException {
        LOG.debug("Received a deliverSm {}", deliverSm);

        Exchange exchange;
        try {
            exchange = endpoint.createOnAcceptDeliverSmExchange(deliverSm);
        } catch (Exception e) {
            exceptionHandler.handleException("Cannot create exchange. This exception will be ignored.", e);
            return;
        }

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
    }

    @Override
    public DataSmResult onAcceptDataSm(DataSm dataSm, Session session) throws ProcessRequestException {
        LOG.debug("Received a dataSm {}", dataSm);

        MessageId newMessageId = messageIDGenerator.newMessageId();
        Exchange exchange = endpoint.createOnAcceptDataSm(dataSm, newMessageId.getValue());
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
    }

    public void setMessageIDGenerator(MessageIDGenerator messageIDGenerator) {
        this.messageIDGenerator = messageIDGenerator;
    }
}
