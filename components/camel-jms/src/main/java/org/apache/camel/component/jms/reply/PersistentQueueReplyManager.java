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
import org.apache.camel.component.jms.ReplyToType;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.support.destination.DestinationResolver;

/**
 * A {@link ReplyManager} when using persistent queues.
 *
 * @version 
 */
public class PersistentQueueReplyManager extends ReplyManagerSupport {

    private String replyToSelectorValue;
    private MessageSelectorCreator dynamicMessageSelector;

    public String registerReply(ReplyManager replyManager, Exchange exchange, AsyncCallback callback,
                                String originalCorrelationId, String correlationId, long requestTimeout) {
        // add to correlation map
        PersistentQueueReplyHandler handler = new PersistentQueueReplyHandler(replyManager, exchange, callback,
                originalCorrelationId, correlationId, requestTimeout);
        correlation.put(correlationId, handler, requestTimeout);
        return correlationId;
    }

    public void updateCorrelationId(String correlationId, String newCorrelationId, long requestTimeout) {
        log.trace("Updated provisional correlationId [{}] to expected correlationId [{}]", correlationId, newCorrelationId);

        ReplyHandler handler = correlation.remove(correlationId);
        if (handler == null) {
            // should not happen that we can't find the handler
            return;
        }

        correlation.put(newCorrelationId, handler, requestTimeout);
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
                correlation.remove(correlationID);
            }
        } else {
            // we could not correlate the received reply message to a matching request and therefore
            // we cannot continue routing the unknown message
            String text = "Reply received for unknown correlationID [" + correlationID + "]. The message will be ignored: " + message;
            // log a warn and then ignore the message
            log.warn(text);
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

    protected AbstractMessageListenerContainer createListenerContainer() throws Exception {
        DefaultMessageListenerContainer answer;

        ReplyToType type = endpoint.getConfiguration().getReplyToType();
        if (type == null) {
            // use shared by default for persistent reply queues
            type = ReplyToType.Shared;
        }

        if (ReplyToType.Shared == type) {
            // shared reply to queues support either a fixed or dynamic JMS message selector
            String replyToSelectorName = endpoint.getReplyToDestinationSelectorName();
            if (replyToSelectorName != null) {
                // create a random selector value we will use for the persistent reply queue
                replyToSelectorValue = "ID:" + new BigInteger(24 * 8, new Random()).toString(16);
                String fixedMessageSelector = replyToSelectorName + "='" + replyToSelectorValue + "'";
                answer = new SharedPersistentQueueMessageListenerContainer(fixedMessageSelector);
                // must use cache level consumer for fixed message selector
                answer.setCacheLevel(DefaultMessageListenerContainer.CACHE_CONSUMER);
                log.debug("Using shared queue: " + endpoint.getReplyTo() + " with fixed message selector [" + fixedMessageSelector + "] as reply listener: " + answer);
            } else {
                // use a dynamic message selector which will select the message we want to receive as reply
                dynamicMessageSelector = new MessageSelectorCreator(correlation);
                answer = new SharedPersistentQueueMessageListenerContainer(dynamicMessageSelector);
                // must use cache level session for dynamic message selector,
                // as otherwise the dynamic message selector will not be updated on-the-fly
                answer.setCacheLevel(DefaultMessageListenerContainer.CACHE_SESSION);
                log.debug("Using shared queue: " + endpoint.getReplyTo() + " with dynamic message selector as reply listener: " + answer);
            }
        } else if (ReplyToType.Exclusive == type) {
            answer = new ExclusivePersistentQueueMessageListenerContainer();
            // must use cache level consumer for exclusive as there is no message selector
            answer.setCacheLevel(DefaultMessageListenerContainer.CACHE_CONSUMER);
            log.debug("Using exclusive queue:" + endpoint.getReplyTo() + " as reply listener: " + answer);
        } else {
            throw new IllegalArgumentException("ReplyToType " + type + " is not supported for persistent reply queues");
        }

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
        answer.setMaxConcurrentConsumers(1);
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
        if (endpoint.getErrorHandler() != null) {
            answer.setErrorHandler(endpoint.getErrorHandler());
        }
        if (endpoint.getReceiveTimeout() >= 0) {
            answer.setReceiveTimeout(endpoint.getReceiveTimeout());
        }
        if (endpoint.getRecoveryInterval() >= 0) {
            answer.setRecoveryInterval(endpoint.getRecoveryInterval());
        }
        // do not use a task executor for reply as we are are always a single threaded task

        // setup a bean name which is used ny Spring JMS as the thread name
        String name = "PersistentQueueReplyManager[" + answer.getDestinationName() + "]";
        name = endpoint.getCamelContext().getExecutorServiceManager().resolveThreadName(name);
        answer.setBeanName(name);

        return answer;
    }

}
