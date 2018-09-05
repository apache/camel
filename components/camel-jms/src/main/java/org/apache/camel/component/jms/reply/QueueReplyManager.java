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
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.jms.DefaultSpringErrorHandler;
import org.apache.camel.component.jms.ReplyToType;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.support.destination.DestinationResolver;

/**
 * A {@link ReplyManager} when using regular queues.
 *
 * @version 
 */
public class QueueReplyManager extends ReplyManagerSupport {

    private String replyToSelectorValue;
    private MessageSelectorCreator dynamicMessageSelector;

    public QueueReplyManager(CamelContext camelContext) {
        super(camelContext);
    }

    protected ReplyHandler createReplyHandler(ReplyManager replyManager, Exchange exchange, AsyncCallback callback,
                                              String originalCorrelationId, String correlationId, long requestTimeout) {
        return new QueueReplyHandler(replyManager, exchange, callback,
                originalCorrelationId, correlationId, requestTimeout);
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

    protected void handleReplyMessage(String correlationID, Message message, Session session) {
        ReplyHandler handler = correlation.get(correlationID);
        if (handler == null && endpoint.isUseMessageIDAsCorrelationID()) {
            handler = waitForProvisionCorrelationToBeUpdated(correlationID, message);
        }

        if (handler != null) {
            correlation.remove(correlationID);
            handler.onReply(correlationID, message, session);
        } else {
            // we could not correlate the received reply message to a matching request and therefore
            // we cannot continue routing the unknown message
            // log a warn and then ignore the message
            log.warn("Reply received for unknown correlationID [{}] on reply destination [{}]. Current correlation map size: {}. The message will be ignored: {}",
                    new Object[]{correlationID, replyTo, correlation.size(), message});
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

        DestinationResolverDelegate(DestinationResolver delegate) {
            this.delegate = delegate;
        }

        public Destination resolveDestinationName(Session session, String destinationName,
                                                  boolean pubSubDomain) throws JMSException {
            synchronized (QueueReplyManager.this) {
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
            // use shared by default for reply queues
            type = ReplyToType.Shared;
        }

        if (ReplyToType.Shared == type) {
            // shared reply to queues support either a fixed or dynamic JMS message selector
            String replyToSelectorName = endpoint.getReplyToDestinationSelectorName();
            if (replyToSelectorName != null) {
                // create a random selector value we will use for the reply queue
                replyToSelectorValue = "ID:" + new BigInteger(24 * 8, new Random()).toString(16);
                String fixedMessageSelector = replyToSelectorName + "='" + replyToSelectorValue + "'";
                answer = new SharedQueueMessageListenerContainer(endpoint, fixedMessageSelector);
                // must use cache level consumer for fixed message selector
                answer.setCacheLevel(DefaultMessageListenerContainer.CACHE_CONSUMER);
                log.debug("Using shared queue: {} with fixed message selector [{}] as reply listener: {}", endpoint.getReplyTo(), fixedMessageSelector, answer);
            } else {
                // use a dynamic message selector which will select the message we want to receive as reply
                dynamicMessageSelector = new MessageSelectorCreator(correlation);
                answer = new SharedQueueMessageListenerContainer(endpoint, dynamicMessageSelector);
                // must use cache level session for dynamic message selector,
                // as otherwise the dynamic message selector will not be updated on-the-fly
                answer.setCacheLevel(DefaultMessageListenerContainer.CACHE_SESSION);
                log.debug("Using shared queue: {} with dynamic message selector as reply listener: {}", endpoint.getReplyTo(), answer);
            }
            // shared is not as fast as temporary or exclusive, so log this so the end user may be aware of this
            log.warn("{} is using a shared reply queue, which is not as fast as alternatives."
                    + " See more detail at the section 'Request-reply over JMS' at http://camel.apache.org/jms", endpoint);
        } else if (ReplyToType.Exclusive == type) {
            answer = new ExclusiveQueueMessageListenerContainer(endpoint);
            // must use cache level consumer for exclusive as there is no message selector
            answer.setCacheLevel(DefaultMessageListenerContainer.CACHE_CONSUMER);
            log.debug("Using exclusive queue: {} as reply listener: {}", endpoint.getReplyTo(), answer);
        } else {
            throw new IllegalArgumentException("ReplyToType " + type + " is not supported for reply queues");
        }
        
        String replyToCacheLevelName = endpoint.getConfiguration().getReplyToCacheLevelName();
        if (replyToCacheLevelName != null) {
            answer.setCacheLevelName(replyToCacheLevelName);
            log.debug("Setting the replyCacheLevel to be {}", replyToCacheLevelName);
        }

        DestinationResolver resolver = endpoint.getDestinationResolver();
        if (resolver == null) {
            resolver = answer.getDestinationResolver();
        }
        answer.setDestinationResolver(new DestinationResolverDelegate(resolver));
        answer.setDestinationName(endpoint.getReplyTo());

        answer.setAutoStartup(true);
        answer.setIdleConsumerLimit(endpoint.getIdleConsumerLimit());
        answer.setIdleTaskExecutionLimit(endpoint.getIdleTaskExecutionLimit());
        if (endpoint.getMaxMessagesPerTask() >= 0) {
            answer.setMaxMessagesPerTask(endpoint.getMaxMessagesPerTask());
        }
        answer.setMessageListener(this);
        answer.setPubSubDomain(false);
        answer.setSubscriptionDurable(false);
        answer.setConcurrentConsumers(endpoint.getReplyToConcurrentConsumers());
        if (endpoint.getReplyToMaxConcurrentConsumers() > 0) {
            answer.setMaxConcurrentConsumers(endpoint.getReplyToMaxConcurrentConsumers());
        }
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
        } else {
            answer.setErrorHandler(new DefaultSpringErrorHandler(endpoint.getCamelContext(), QueueReplyManager.class, endpoint.getErrorHandlerLoggingLevel(), endpoint.isErrorHandlerLogStackTrace()));
        }
        if (endpoint.getReceiveTimeout() >= 0) {
            answer.setReceiveTimeout(endpoint.getReceiveTimeout());
        }
        if (endpoint.getRecoveryInterval() >= 0) {
            answer.setRecoveryInterval(endpoint.getRecoveryInterval());
        }
        // set task executor
        if (endpoint.getTaskExecutor() != null) {
            log.debug("Using custom TaskExecutor: {} on listener container: {}", endpoint.getTaskExecutor(), answer);
            answer.setTaskExecutor(endpoint.getTaskExecutor());
        }

        // setup a bean name which is used by Spring JMS as the thread name
        String name = "QueueReplyManager[" + answer.getDestinationName() + "]";
        answer.setBeanName(name);

        if (answer.getConcurrentConsumers() > 1) {
            if (ReplyToType.Shared == type) {
                // warn if using concurrent consumer with shared reply queue as that may not work properly
                log.warn("Using {}-{} concurrent consumer on {} with shared queue {} may not work properly with all message brokers.",
                        new Object[]{answer.getConcurrentConsumers(), answer.getMaxConcurrentConsumers(), name, endpoint.getReplyTo()});
            } else {
                // log that we are using concurrent consumers
                log.info("Using {}-{} concurrent consumers on {}",
                        new Object[]{answer.getConcurrentConsumers(), answer.getMaxConcurrentConsumers(), name});
            }
        }

        return answer;
    }

}
