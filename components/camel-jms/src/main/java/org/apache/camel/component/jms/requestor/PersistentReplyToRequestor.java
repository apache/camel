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
package org.apache.camel.component.jms.requestor;

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;

import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.camel.component.jms.JmsConfiguration;
import org.apache.camel.component.jms.requestor.DeferredRequestReplyMap.DeferredMessageSentCallback;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer102;
import org.springframework.jms.support.destination.DestinationResolver;
import org.springframework.transaction.PlatformTransactionManager;

public class PersistentReplyToRequestor extends Requestor {
    private String replyToSelectorValue;

    public class DestinationResolverDelegate implements DestinationResolver {
        private DestinationResolver delegate;
        private Destination destination;

        public DestinationResolverDelegate(DestinationResolver delegate) {
            this.delegate = delegate;
        }

        public Destination resolveDestinationName(Session session, String destinationName,
                                                  boolean pubSubDomain) throws JMSException {
            synchronized (getOutterInstance()) {
                try {
                    if (destination == null) {
                        destination = delegate.resolveDestinationName(session, destinationName, pubSubDomain);
                        setReplyTo(destination);
                    }
                } finally {
                    getOutterInstance().notifyAll();
                }
            }
            return destination;
        }
    };

    public static interface MessageSelectorComposer {
        void addCorrelationID(String id);
        void removeCorrelationID(String id);
    }

    public static class CamelDefaultMessageListenerContainer102 extends DefaultMessageListenerContainer102
                                                                implements MessageSelectorComposer {
        MessageSelectorProvider provider = new MessageSelectorProvider();

        public void addCorrelationID(String id) {
            provider.addCorrelationID(id);
        }

        public void removeCorrelationID(String id) {
            provider.removeCorrelationID(id);
        }

        @Override
        public void setMessageSelector(String messageSelector) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getMessageSelector() {
            return provider.get();
        }
    }

    public static class CamelDefaultMessageListenerContainer extends DefaultMessageListenerContainer
                                                             implements MessageSelectorComposer {

        MessageSelectorProvider provider = new MessageSelectorProvider();

        public void addCorrelationID(String id) {
            provider.addCorrelationID(id);
        }

        public void removeCorrelationID(String id) {
            provider.removeCorrelationID(id);
        }

        @Override
        public void setMessageSelector(String messageSelector) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getMessageSelector() {
            return provider.get();
        }
    }

    public PersistentReplyToRequestor(JmsConfiguration configuration,
                                      ScheduledExecutorService executorService) {
        super(configuration, executorService);
    }


    @Override
    protected FutureHandler createFutureHandler(String correlationID) {
        boolean dynamicSelector = getConfiguration().getReplyToDestinationSelectorName() == null;
        if (dynamicSelector) {
            return new PersistentReplyToFutureHandler(this, correlationID);
        }
        return new FutureHandler();
    }

    @Override
    protected FutureHandler createFutureHandler(DeferredMessageSentCallback callback) {
        boolean dynamicSelector = getConfiguration().getReplyToDestinationSelectorName() == null;
        if (dynamicSelector) {
            return new PersistentReplyToFutureHandler(this, callback);
        }
        return new FutureHandler();
    }

    @Override
    public AbstractMessageListenerContainer createListenerContainer() {
        JmsConfiguration config = getConfiguration();
        String replyToSelectorName = getConfiguration().getReplyToDestinationSelectorName();

        AbstractMessageListenerContainer container =
            config.isUseVersion102()
                    ? (replyToSelectorName != null) ? new DefaultMessageListenerContainer102()
                           : new CamelDefaultMessageListenerContainer102()
                    : (replyToSelectorName != null) ? new DefaultMessageListenerContainer()
                           : new CamelDefaultMessageListenerContainer();

        container.setConnectionFactory(config.getListenerConnectionFactory());

        DestinationResolver resolver = config.getDestinationResolver();
        if (resolver == null) {
            resolver = container.getDestinationResolver();
        }

        container.setDestinationResolver(new DestinationResolverDelegate(resolver));
        container.setDestinationName(getConfiguration().getReplyTo());

        if (replyToSelectorName != null) {
            replyToSelectorValue = "ID:" + new BigInteger(24 * 8, new Random()).toString(16);
            container.setMessageSelector(replyToSelectorName + "='" + replyToSelectorValue + "'");
        } else {
            ((MessageSelectorComposer)container).addCorrelationID("ID:" + new BigInteger(24 * 8, new Random()).toString(16));
        }

        container.setAutoStartup(true);
        container.setMessageListener(this);
        container.setPubSubDomain(false);
        container.setSubscriptionDurable(false);
        ExceptionListener exceptionListener = config.getExceptionListener();
        if (exceptionListener != null) {
            container.setExceptionListener(exceptionListener);
        }
        container.setSessionTransacted(config.isTransacted());
        if (config.isTransacted()) {
            container.setSessionAcknowledgeMode(Session.SESSION_TRANSACTED);
        } else {
            if (config.getAcknowledgementMode() >= 0) {
                container.setSessionAcknowledgeMode(config.getAcknowledgementMode());
            } else if (config.getAcknowledgementModeName() != null) {
                container.setSessionAcknowledgeModeName(config.getAcknowledgementModeName());
            }
        }
        if (container instanceof DefaultMessageListenerContainer) {
            DefaultMessageListenerContainer defContainer = (DefaultMessageListenerContainer)container;
            defContainer.setConcurrentConsumers(1);
            defContainer.setCacheLevel(DefaultMessageListenerContainer.CACHE_SESSION);

            if (config.getReceiveTimeout() >= 0) {
                defContainer.setReceiveTimeout(config.getReceiveTimeout());
            }
            if (config.getRecoveryInterval() >= 0) {
                defContainer.setRecoveryInterval(config.getRecoveryInterval());
            }
            TaskExecutor taskExecutor = config.getTaskExecutor();
            if (taskExecutor != null) {
                defContainer.setTaskExecutor(taskExecutor);
            }
            PlatformTransactionManager tm = config.getTransactionManager();
            if (tm != null) {
                defContainer.setTransactionManager(tm);
            } else if (config.isTransacted()) {
                throw new IllegalArgumentException("Property transacted is enabled but a transactionManager was not injected!");
            }
            if (config.getTransactionName() != null) {
                defContainer.setTransactionName(config.getTransactionName());
            }
            if (config.getTransactionTimeout() >= 0) {
                defContainer.setTransactionTimeout(config.getTransactionTimeout());
            }
        }
        return container;
    }

    @Override
    public void setReplyToSelectorHeader(org.apache.camel.Message in, Message jmsIn) throws JMSException {
        String replyToSelectorName = getConfiguration().getReplyToDestinationSelectorName();
        if (replyToSelectorValue != null) {
            in.setHeader(replyToSelectorName, replyToSelectorValue);
            jmsIn.setStringProperty(replyToSelectorName, replyToSelectorValue);
        }
    }
}
