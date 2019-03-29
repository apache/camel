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
package org.apache.camel.component.activemq;

import javax.jms.Message;
import javax.jms.Session;

import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.camel.Exchange;
import org.apache.camel.component.jms.JmsMessage;
import org.apache.camel.component.jms.MessageCreatedStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A strategy to enrich JMS message with their original destination if the Camel
 * route originates from a JMS destination.
 */
public class OriginalDestinationPropagateStrategy implements MessageCreatedStrategy {

    private static final transient Logger LOG = LoggerFactory.getLogger(OriginalDestinationPropagateStrategy.class);

    @Override
    public void onMessageCreated(Message message, Session session, Exchange exchange, Throwable cause) {
        if (exchange.getIn() instanceof JmsMessage) {
            JmsMessage msg = exchange.getIn(JmsMessage.class);
            Message jms = msg.getJmsMessage();
            if (jms != null && jms instanceof ActiveMQMessage && message instanceof ActiveMQMessage) {
                ActiveMQMessage amq = (ActiveMQMessage)jms;
                if (amq.getOriginalDestination() == null) {
                    ActiveMQDestination from = amq.getDestination();
                    if (from != null) {
                        LOG.trace("Setting OriginalDestination: {} on {}", from, message);
                        ((ActiveMQMessage)message).setOriginalDestination(from);
                    }
                }
            }
        }
    }

}
