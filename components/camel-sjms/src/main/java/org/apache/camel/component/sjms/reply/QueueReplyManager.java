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
package org.apache.camel.component.sjms.reply;

import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;

import org.apache.camel.CamelContext;
import org.apache.camel.component.sjms.MessageListenerContainer;
import org.apache.camel.component.sjms.ReplyToType;
import org.apache.camel.component.sjms.consumer.SimpleMessageListenerContainer;
import org.apache.camel.component.sjms.jms.DestinationCreationStrategy;

/**
 * A {@link ReplyManager} when using regular queues.
 */
public class QueueReplyManager extends ReplyManagerSupport {

    public QueueReplyManager(CamelContext camelContext) {
        super(camelContext);
    }

    @Override
    public void updateCorrelationId(String correlationId, String newCorrelationId, long requestTimeout) {
        log.trace("Updated provisional correlationId [{}] to expected correlationId [{}]", correlationId, newCorrelationId);

        ReplyHandler handler = correlation.remove(correlationId);
        if (handler == null) {
            // should not happen that we can't find the handler
            return;
        }

        correlation.put(newCorrelationId, handler, requestTimeout);
    }

    @Override
    protected void handleReplyMessage(String correlationID, Message message, Session session) {
        ReplyHandler handler = correlation.get(correlationID);

        if (handler != null) {
            correlation.remove(correlationID);
            handler.onReply(correlationID, message, session);
        } else {
            // we could not correlate the received reply message to a matching request and therefore
            // we cannot continue routing the unknown message
            // log a warn and then ignore the message
            log.warn(
                    "Reply received for unknown correlationID [{}] on reply destination [{}]. Current correlation map size: {}. The message will be ignored: {}",
                    correlationID, replyTo, correlation.size(), message);
        }
    }

    private final class DestinationResolverDelegate implements DestinationCreationStrategy {
        private final DestinationCreationStrategy delegate;
        private Destination destination;

        DestinationResolverDelegate(DestinationCreationStrategy delegate) {
            this.delegate = delegate;
        }

        @Override
        public Destination createDestination(Session session, String destinationName, boolean topic) throws JMSException {
            synchronized (QueueReplyManager.this) {
                // resolve the reply to destination
                if (destination == null) {
                    destination = delegate.createDestination(session, destinationName, topic);
                    setReplyTo(destination);
                }
            }
            return destination;
        }

        @Override
        public Destination createTemporaryDestination(Session session, boolean topic) {
            return null;
        }
    }

    @Override
    protected MessageListenerContainer createListenerContainer() throws Exception {
        SimpleMessageListenerContainer answer;

        ReplyToType type = endpoint.getReplyToType();
        if (type == null) {
            // use exclusive by default for reply queues
            type = ReplyToType.Exclusive;
        }

        if (ReplyToType.Exclusive == type) {
            answer = new ExclusiveQueueMessageListenerContainer(endpoint);
            log.debug("Using exclusive queue: {} as reply listener: {}", endpoint.getReplyTo(), answer);
        } else {
            throw new IllegalArgumentException("ReplyToType " + type + " is not supported for reply queues");
        }
        answer.setMessageListener(this);

        answer.setConcurrentConsumers(endpoint.getReplyToConcurrentConsumers());
        answer.setDestinationCreationStrategy(new DestinationResolverDelegate(endpoint.getDestinationCreationStrategy()));
        answer.setDestinationName(endpoint.getReplyTo());

        String clientId = endpoint.getClientId();
        if (clientId != null) {
            clientId += ".CamelReplyManager";
            answer.setClientId(clientId);
        }

        return answer;
    }

}
