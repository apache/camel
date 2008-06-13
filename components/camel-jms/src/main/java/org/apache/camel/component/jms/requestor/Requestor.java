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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;

import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TemporaryQueue;

import org.apache.camel.component.jms.JmsConfiguration;
import org.apache.camel.component.jms.JmsProducer;
import org.apache.camel.component.jms.requestor.DeferredRequestReplyMap.DeferredMessageSentCallback;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.util.DefaultTimeoutMap;
import org.apache.camel.util.TimeoutMap;
import org.apache.camel.util.UuidGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.SimpleMessageListenerContainer;
import org.springframework.jms.listener.SimpleMessageListenerContainer102;
import org.springframework.jms.support.destination.DestinationResolver;

/**
 * @version $Revision$
 */
public class Requestor extends ServiceSupport implements MessageListener {
    private static final transient Log LOG = LogFactory.getLog(Requestor.class);
    private static UuidGenerator uuidGenerator;
    private final JmsConfiguration configuration;
    private ScheduledExecutorService executorService;
    private AbstractMessageListenerContainer listenerContainer;
    private TimeoutMap requestMap;
    private Map<JmsProducer, DeferredRequestReplyMap> producerDeferredRequestReplyMap;
    private TimeoutMap deferredRequestMap;
    private TimeoutMap deferredReplyMap;
    private Destination replyTo;
    private long maxRequestTimeout = -1;
    private long replyToResolverTimeout = 5000;


    public Requestor(JmsConfiguration configuration, ScheduledExecutorService executorService) {
        this.configuration = configuration;
        this.executorService = executorService;
        requestMap = new DefaultTimeoutMap(executorService, configuration.getRequestMapPurgePollTimeMillis());
        producerDeferredRequestReplyMap = new HashMap<JmsProducer, DeferredRequestReplyMap>();
        deferredRequestMap = new DefaultTimeoutMap(executorService, configuration.getRequestMapPurgePollTimeMillis());
        deferredReplyMap = new DefaultTimeoutMap(executorService, configuration.getRequestMapPurgePollTimeMillis());
    }

    public synchronized DeferredRequestReplyMap getDeferredRequestReplyMap(JmsProducer producer) {
        DeferredRequestReplyMap map = producerDeferredRequestReplyMap.get(producer);
        if (map == null) {
            map = new DeferredRequestReplyMap(this, producer, deferredRequestMap, deferredReplyMap);
            producerDeferredRequestReplyMap.put(producer, map);
            if (maxRequestTimeout == -1) {
                maxRequestTimeout = producer.getRequestTimeout();
            } else if (maxRequestTimeout < producer.getRequestTimeout()) {
                maxRequestTimeout = producer.getRequestTimeout();
            }
        }
        return map;
    }

    public synchronized void removeDeferredRequestReplyMap(JmsProducer producer) {
        DeferredRequestReplyMap map = producerDeferredRequestReplyMap.remove(producer);
        if (map == null) {
            // already removed;
            return;
        }
        if (maxRequestTimeout == producer.getRequestTimeout()) {
            long max = -1;
            for (Map.Entry<JmsProducer, DeferredRequestReplyMap> entry : producerDeferredRequestReplyMap.entrySet()) {
                if (max < entry.getKey().getRequestTimeout()) {
                    max = entry.getKey().getRequestTimeout();
                }
            }
            maxRequestTimeout = max;
        }
    }

    public synchronized long getMaxRequestTimeout() {
        return maxRequestTimeout;
    }

    public TimeoutMap getRequestMap() {
        return requestMap;
    }

    public TimeoutMap getDeferredRequestMap() {
        return deferredRequestMap;
    }

    public TimeoutMap getDeferredReplyMap() {
        return deferredReplyMap;
    }

    public FutureTask getReceiveFuture(String correlationID, long requestTimeout) {
        FutureHandler future = createFutureHandler(correlationID);
        requestMap.put(correlationID, future, requestTimeout);
        return future;
    }

    public FutureTask getReceiveFuture(DeferredMessageSentCallback callback) {
        FutureHandler future = createFutureHandler(callback);
        DeferredRequestReplyMap map = callback.getDeferredRequestReplyMap();
        map.put(callback, future);
        return future;
    }

    protected FutureHandler createFutureHandler(String correlationID) {
        return new FutureHandler();
    }

    protected FutureHandler createFutureHandler(DeferredMessageSentCallback callback) {
        return new FutureHandler();
    }

    public void onMessage(Message message) {
        try {
            String correlationID = message.getJMSCorrelationID();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Message correlationID: " + correlationID);
            }
            if (correlationID == null) {
                LOG.warn("Ignoring message with no correlationID! " + message);
                return;
            }
            // lets notify the monitor for this response
            Object handler = requestMap.get(correlationID);
            if (handler != null && handler instanceof ReplyHandler) {
                ReplyHandler replyHandler = (ReplyHandler) handler;
                boolean complete = replyHandler.handle(message);
                if (complete) {
                    requestMap.remove(correlationID);
                }
            } else {
                DeferredRequestReplyMap.processDeferredRequests(
                        this, deferredRequestMap, deferredReplyMap,
                        correlationID, getMaxRequestTimeout(), message);
            }
        } catch (JMSException e) {
            throw new FailedToProcessResponse(message, e);
        }
    }


    public AbstractMessageListenerContainer getListenerContainer() {
        if (listenerContainer == null) {
            listenerContainer = createListenerContainer();
        }
        return listenerContainer;
    }

    public void setListenerContainer(AbstractMessageListenerContainer listenerContainer) {
        this.listenerContainer = listenerContainer;
    }

    public Destination getReplyTo() {
        synchronized (this) {
            try {
                if (replyTo == null) {
                    wait(replyToResolverTimeout);
                }
            } catch (Throwable e) {
                // eat it
            }
        }
        return replyTo;
    }

    public void setReplyTo(Destination replyTo) {
        this.replyTo = replyTo;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    @Override
    protected void doStart() throws Exception {
        AbstractMessageListenerContainer container = getListenerContainer();
        container.afterPropertiesSet();
    }

    @Override
    protected void doStop() throws Exception {
        if (listenerContainer != null) {
            listenerContainer.stop();
            listenerContainer.destroy();
        }
    }

    protected Requestor getOutterInstance() {
        return this;
    }

    protected AbstractMessageListenerContainer createListenerContainer() {
        SimpleMessageListenerContainer answer = configuration.isUseVersion102()
            ? new SimpleMessageListenerContainer102() : new SimpleMessageListenerContainer();
        answer.setDestinationName("temporary");
        answer.setDestinationResolver(new DestinationResolver() {

            public Destination resolveDestinationName(Session session, String destinationName,
                                                      boolean pubSubDomain) throws JMSException {
                TemporaryQueue queue = null;
                synchronized (getOutterInstance()) {
                    try {
                        queue = session.createTemporaryQueue();
                        setReplyTo(queue);
                    } finally {
                        getOutterInstance().notifyAll();
                    }
                }
                return queue;
            }
        });
        answer.setAutoStartup(true);
        answer.setMessageListener(this);
        answer.setPubSubDomain(false);
        answer.setSubscriptionDurable(false);
        answer.setConcurrentConsumers(1);
        answer.setConnectionFactory(configuration.getConnectionFactory());
        String clientId = configuration.getClientId();
        if (clientId != null) {
            clientId += ".Requestor";
            answer.setClientId(clientId);
        }
        TaskExecutor taskExecutor = configuration.getTaskExecutor();
        if (taskExecutor != null) {
            answer.setTaskExecutor(taskExecutor);
        }
        ExceptionListener exceptionListener = configuration.getExceptionListener();
        if (exceptionListener != null) {
            answer.setExceptionListener(exceptionListener);
        }
        return answer;
    }

    public static synchronized UuidGenerator getUuidGenerator() {
        if (uuidGenerator == null) {
            uuidGenerator = new UuidGenerator();
        }
        return uuidGenerator;
    }

    protected JmsConfiguration getConfiguration() {
        return configuration;
    }

    public void setReplyToSelectorHeader(org.apache.camel.Message in, Message jmsIn) throws JMSException {
        // complete
    }
}
