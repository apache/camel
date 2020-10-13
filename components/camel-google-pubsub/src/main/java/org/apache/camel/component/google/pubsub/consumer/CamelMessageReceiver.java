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
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Processor;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.camel.component.google.pubsub.GooglePubsubEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamelMessageReceiver implements MessageReceiver {

    private final Logger localLog;
    private final GooglePubsubEndpoint endpoint;
    private final Processor processor;

    public CamelMessageReceiver(GooglePubsubEndpoint endpoint, Processor processor) {
        this.endpoint = endpoint;
        this.processor = processor;
        String loggerId = endpoint.getLoggerId();
        if (Strings.isNullOrEmpty(loggerId)) {
            loggerId = this.getClass().getName();
        }
        localLog = LoggerFactory.getLogger(loggerId);
    }

    @Override
    public void receiveMessage(PubsubMessage pubsubMessage, AckReplyConsumer ackReplyConsumer) {
        if (localLog.isTraceEnabled()) {
            localLog.trace("Received message ID : {}", pubsubMessage.getMessageId());
        }

        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody(pubsubMessage.getData().toByteArray());

        exchange.getIn().setHeader(GooglePubsubConstants.MESSAGE_ID, pubsubMessage.getMessageId());
        exchange.getIn().setHeader(GooglePubsubConstants.PUBLISH_TIME, pubsubMessage.getPublishTime());

        if (null != pubsubMessage.getAttributesMap()) {
            exchange.getIn().setHeader(GooglePubsubConstants.ATTRIBUTES, pubsubMessage.getAttributesMap());
        }

        if (endpoint.getAckMode() != GooglePubsubConstants.AckMode.NONE) {
            exchange.adapt(ExtendedExchange.class).addOnCompletion(new AcknowledgeAsync(ackReplyConsumer));
        }

        try {
            processor.process(exchange);
        } catch (Throwable e) {
            exchange.setException(e);
        }
    }
}
