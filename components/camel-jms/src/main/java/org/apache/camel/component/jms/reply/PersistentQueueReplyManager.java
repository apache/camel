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

import java.math.BigInteger;
import java.util.Random;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.util.IntrospectionSupport;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.support.destination.DestinationResolver;

/**
 * A {@link ReplyManager} when using persistent queues.
 *
 * @version $Revision$
 */
public class PersistentQueueReplyManager extends ReplyManagerSupport {

    private String replyToSelectorValue;
    private MessageSelectorCreator dynamicMessageSelector;

    public String registerReply(ReplyManager replyManager, Exchange exchange, AsyncCallback callback,
                                String originalCorrelationId, String correlationId, long requestTimeout) {
        // add to correlation map
        PersistentQueueReplyHandler handler = new PersistentQueueReplyHandler(replyManager, exchange, callback,
                originalCorrelationId, requestTimeout, dynamicMessageSelector);
        correlation.put(correlationId, handler, requestTimeout);
        if (dynamicMessageSelector != null) {
            // also remember to keep the dynamic selector updated with the new correlation id
            dynamicMessageSelector.addCorrelationID(correlationId);
        }
        return correlationId;
    }

    public void updateCorrelationId(String correlationId, String newCorrelationId, long requestTimeout) {
        if (log.isTraceEnabled()) {
            log.trace("Updated provisional correlationId [" + correlationId + "] to expected correlationId [" + newCorrelationId + "]");
        }

        ReplyHandler handler = correlation.remove(correlationId);
        if (handler == null) {
            // should not happen that we can't find the handler
            return;
        }

        correlation.put(newCorrelationId, handler, requestTimeout);

        // no not arrived early
        if (dynamicMessageSelector != null) {
            // also remember to keep the dynamic selector updated with the new correlation id
            dynamicMessageSelector.addCorrelationID(newCorrelationId);
        }
    }

    protected void handleReplyMessage(String correlationID, Message message) {
        ReplyHandler handler = correlation.get(correlationID);
        if (handler == null && endpoint.isUseMessageIDAsCorrelationID()) {
            handler = waitForProvisionCorrelationToBeUpdated(correlationID, message);
        }

        if (handler != null) {
            try {
                handler.onReply(correlationID, message);
            } finally {
                if (dynamicMessageSelector != null) {
                    // also remember to keep the dynamic selector updated with the new correlation id
                    dynamicMessageSelector.removeCorrelationID(correlationID);
                }
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
        String replyToSelectorName = endpoint.getReplyToDestinationSelectorName();
        if (replyToSelectorName != null && replyToSelectorValue != null) {
            camelMessage.setHeader(replyToSelectorName, replyToSelectorValue);
            jmsMessage.setStringProperty(replyToSelectorName, replyToSelectorValue);
        }
    }

    private final class DestinationResolverDelegate implements DestinationResolver {
        private DestinationResolver delegate;
        private Destination destination;

        public DestinationResolverDelegate(DestinationResolver delegate) {
            this.delegate = delegate;
        }

        public Destination resolveDestinationName(Session session, String destinationName,
                                                  boolean pubSubDomain) throws JMSException {
            synchronized (PersistentQueueReplyManager.this) {
                // resolve the reply to destination
                if (destination == null) {
                    destination = delegate.resolveDestinationName(session, destinationName, pubSubDomain);
                    setReplyTo(destination);
                }
            }
            return destination;
        }
    };

    private final class PersistentQueueMessageListenerContainer extends DefaultMessageListenerContainer {

        private String fixedMessageSelector;
        private MessageSelectorCreator creator;

        private PersistentQueueMessageListenerContainer(String fixedMessageSelector) {
            this.fixedMessageSelector = fixedMessageSelector;
        }

        private PersistentQueueMessageListenerContainer(MessageSelectorCreator creator) {
            this.creator = creator;
        }

        @Override
        public String getMessageSelector() {
            String id = null;
            if (fixedMessageSelector != null) {
                id = fixedMessageSelector;
            } else if (creator != null) {
                id = creator.get();
            }
            if (log.isTraceEnabled()) {
                log.trace("Using MessageSelector[" + id + "]");
            }
            return id;
        }
    }

    protected AbstractMessageListenerContainer createListenerContainer() throws Exception {
        DefaultMessageListenerContainer answer;

        String replyToSelectorName = endpoint.getReplyToDestinationSelectorName();
        if (replyToSelectorName != null) {
            // create a random selector value we will use for the persistent reply queue
            replyToSelectorValue = "ID:" + new BigInteger(24 * 8, new Random()).toString(16);
            String fixedMessageSelector = replyToSelectorName + "='" + replyToSelectorValue + "'";
            answer = new PersistentQueueMessageListenerContainer(fixedMessageSelector);
        } else {
            // use a dynamic message selector which will select the message we want to receive as reply
            dynamicMessageSelector = new MessageSelectorCreator();
            answer = new PersistentQueueMessageListenerContainer(dynamicMessageSelector);
        }

        answer.setConnectionFactory(endpoint.getListenerConnectionFactory());
        DestinationResolver resolver = endpoint.getDestinationResolver();
        if (resolver == null) {
            resolver = answer.getDestinationResolver();
        }
        answer.setDestinationResolver(new DestinationResolverDelegate(resolver));
        answer.setDestinationName(endpoint.getReplyTo());

        answer.setAutoStartup(true);
        answer.setMessageListener(this);
        answer.setPubSubDomain(false);
        answer.setSubscriptionDurable(false);
        answer.setConcurrentConsumers(1);
        // must use cache level session
        answer.setCacheLevel(DefaultMessageListenerContainer.CACHE_SESSION);

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
        if (endpoint.getTaskExecutor() != null) {
            answer.setTaskExecutor(endpoint.getTaskExecutor());
        }
        if (endpoint.getTaskExecutorSpring2() != null) {
            // use reflection to invoke to support spring 2 when JAR is compiled with Spring 3.0
            IntrospectionSupport.setProperty(answer, "taskExecutor", endpoint.getTaskExecutorSpring2());
        }

        return answer;
    }

}
