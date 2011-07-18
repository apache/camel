/**
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
package org.apache.camel.component.jms.reply;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TemporaryQueue;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.support.destination.DestinationResolver;

/**
 * A {@link ReplyManager} when using temporary queues.
 *
 * @version 
 */
public class TemporaryQueueReplyManager extends ReplyManagerSupport {

    public String registerReply(ReplyManager replyManager, Exchange exchange, AsyncCallback callback,
                                String originalCorrelationId, String correlationId, long requestTimeout) {
        // add to correlation map
        TemporaryQueueReplyHandler handler = new TemporaryQueueReplyHandler(this, exchange, callback, originalCorrelationId, requestTimeout);
        correlation.put(correlationId, handler, requestTimeout);
        return correlationId;
    }

    public void updateCorrelationId(String correlationId, String newCorrelationId, long requestTimeout) {
        log.trace("Updated provisional correlationId [{}] to expected correlationId [{}]", correlationId, newCorrelationId);

        ReplyHandler handler = correlation.remove(correlationId);
        correlation.put(newCorrelationId, handler, requestTimeout);
    }

    @Override
    protected void handleReplyMessage(String correlationID, Message message) {
        ReplyHandler handler = correlation.get(correlationID);
        if (handler == null && endpoint.isUseMessageIDAsCorrelationID()) {
            handler = waitForProvisionCorrelationToBeUpdated(correlationID, message);
        }

        if (handler != null) {
            try {
                handler.onReply(correlationID, message);
            } finally {
                correlation.remove(correlationID);
            }
        } else {
            // we could not correlate the received reply message to a matching request and therefore
            // we cannot continue routing the unknown message
            String text = "Reply received for unknown correlationID [" + correlationID + "] -> " + message;
            log.warn(text);
            throw new UnknownReplyMessageException(text, message, correlationID);
        }
    }

    public void setReplyToSelectorHeader(org.apache.camel.Message camelMessage, Message jmsMessage) throws JMSException {
        // noop
    }

    @Override
    protected AbstractMessageListenerContainer createListenerContainer() throws Exception {
        // Use DefaultMessageListenerContainer as it supports reconnects (see CAMEL-3193)
        DefaultMessageListenerContainer answer = new DefaultMessageListenerContainer();

        answer.setDestinationName("temporary");
        answer.setDestinationResolver(new DestinationResolver() {
            public Destination resolveDestinationName(Session session, String destinationName,
                                                      boolean pubSubDomain) throws JMSException {
                // use a temporary queue to gather the reply message
                TemporaryQueue queue = session.createTemporaryQueue();
                setReplyTo(queue);
                return queue;
            }
        });
        answer.setAutoStartup(true);
        answer.setMessageListener(this);
        answer.setPubSubDomain(false);
        answer.setSubscriptionDurable(false);
        answer.setConcurrentConsumers(1);
        answer.setConnectionFactory(endpoint.getConnectionFactory());
        String clientId = endpoint.getClientId();
        if (clientId != null) {
            clientId += ".CamelReplyManager";
            answer.setClientId(clientId);
        }

        // we cannot do request-reply over JMS with transaction
        answer.setSessionTransacted(false);

        // other optional properties
        if (endpoint.getExceptionListener() != null) {
            answer.setExceptionListener(endpoint.getExceptionListener());
        }
        if (endpoint.getReceiveTimeout() >= 0) {
            answer.setReceiveTimeout(endpoint.getReceiveTimeout());
        }
        if (endpoint.getRecoveryInterval() >= 0) {
            answer.setRecoveryInterval(endpoint.getRecoveryInterval());
        }
        // do not use a task executor for reply as we are are always a single threaded task

        return answer;
    }

}
