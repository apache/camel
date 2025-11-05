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
package org.apache.camel.component.google.pubsub.consumer;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.common.base.Strings;
import com.google.pubsub.v1.PubsubMessage;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.camel.component.google.pubsub.GooglePubsubConsumer;
import org.apache.camel.component.google.pubsub.GooglePubsubEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamelMessageReceiver implements MessageReceiver {

    private final Logger localLog;
    private final GooglePubsubConsumer consumer;
    private final GooglePubsubEndpoint endpoint;
    private final Processor processor;
    private final HeaderFilterStrategy headerFilterStrategy;

    public CamelMessageReceiver(GooglePubsubConsumer consumer, GooglePubsubEndpoint endpoint, Processor processor) {
        this.consumer = consumer;
        this.endpoint = endpoint;
        this.processor = processor;
        String loggerId = endpoint.getLoggerId();
        if (Strings.isNullOrEmpty(loggerId)) {
            loggerId = this.getClass().getName();
        }
        localLog = LoggerFactory.getLogger(loggerId);
        headerFilterStrategy = endpoint.getHeaderFilterStrategy();
    }

    @Override
    public void receiveMessage(PubsubMessage pubsubMessage, AckReplyConsumer ackReplyConsumer) {
        if (localLog.isTraceEnabled()) {
            localLog.trace("Received message ID : {}", pubsubMessage.getMessageId());
        }

        Exchange exchange = consumer.createExchange(true);
        exchange.getIn().setBody(pubsubMessage.getData().toByteArray());

        // Standard headers
        exchange.getIn().setHeader(GooglePubsubConstants.MESSAGE_ID, pubsubMessage.getMessageId());
        exchange.getIn().setHeader(GooglePubsubConstants.PUBLISH_TIME, pubsubMessage.getPublishTime());
        // Deprecated: replaced by headerFilterStrategy
        exchange.getIn().setHeader(GooglePubsubConstants.ATTRIBUTES, pubsubMessage.getAttributesMap());

        GooglePubsubAcknowledge acknowledge = new AcknowledgeAsync(ackReplyConsumer);
        if (endpoint.getAckMode() != GooglePubsubConstants.AckMode.NONE) {
            exchange.getExchangeExtension().addOnCompletion(new AcknowledgeCompletion(acknowledge));
        } else {
            exchange.getIn().setHeader(GooglePubsubConstants.GOOGLE_PUBSUB_ACKNOWLEDGE, acknowledge);
        }

        // Inherit the rest of headers
        for (String pubSubHeader : pubsubMessage.getAttributesMap().keySet()) {
            String value = pubsubMessage.getAttributesMap().get(pubSubHeader);
            if (headerFilterStrategy != null
                    && headerFilterStrategy.applyFilterToExternalHeaders(pubSubHeader, value, exchange)) {
                continue;
            }
            exchange.getIn().setHeader(pubSubHeader, value);
        }

        try {
            processor.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
        if (exchange.getException() != null) {
            consumer.getExceptionHandler().handleException(exchange.getException());
        }
    }

}
