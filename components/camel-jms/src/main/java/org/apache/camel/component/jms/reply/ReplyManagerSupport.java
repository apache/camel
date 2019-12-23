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
package org.apache.camel.component.jms.reply;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.component.jms.JmsEndpoint;
import org.apache.camel.component.jms.JmsMessage;
import org.apache.camel.component.jms.JmsMessageHelper;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.listener.AbstractMessageListenerContainer;

/**
 * Base class for {@link ReplyManager} implementations.
 */
public abstract class ReplyManagerSupport extends ServiceSupport implements ReplyManager {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final CamelContext camelContext;
    protected ScheduledExecutorService scheduledExecutorService;
    protected ExecutorService executorService;
    protected JmsEndpoint endpoint;
    protected Destination replyTo;
    protected AbstractMessageListenerContainer listenerContainer;
    protected final CountDownLatch replyToLatch = new CountDownLatch(1);
    protected final long replyToTimeout = 10000;
    protected CorrelationTimeoutMap correlation;
    protected String correlationProperty;

    public ReplyManagerSupport(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void setScheduledExecutorService(ScheduledExecutorService executorService) {
        this.scheduledExecutorService = executorService;
    }

    @Override
    public void setOnTimeoutExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public void setEndpoint(JmsEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public void setReplyTo(Destination replyTo) {
        log.trace("ReplyTo destination: {}", replyTo);
        this.replyTo = replyTo;
        // trigger latch as the reply to has been resolved and set
        replyToLatch.countDown();
    }

    @Override
    public void setCorrelationProperty(final String correlationProperty) {
        this.correlationProperty = correlationProperty;
    }

    @Override
    public Destination getReplyTo() {
        if (replyTo != null) {
            return replyTo;
        }
        try {
            // the reply to destination has to be resolved using a DestinationResolver using
            // the MessageListenerContainer which occurs asynchronously so we have to wait
            // for that to happen before we can retrieve the reply to destination to be used
            log.trace("Waiting for replyTo to be set");
            boolean done = replyToLatch.await(replyToTimeout, TimeUnit.MILLISECONDS);
            if (!done) {
                log.warn("ReplyTo destination was not set and timeout occurred");
            } else {
                log.trace("Waiting for replyTo to be set done");
            }
        } catch (InterruptedException e) {
            // ignore
        }
        return replyTo;
    }
    
    @Override
    public String registerReply(ReplyManager replyManager, Exchange exchange, AsyncCallback callback,
                                String originalCorrelationId, String correlationId, long requestTimeout) {
        // add to correlation map
        QueueReplyHandler handler = new QueueReplyHandler(replyManager, exchange, callback,
                originalCorrelationId, correlationId, requestTimeout);
        // Just make sure we don't override the old value of the correlationId
        ReplyHandler result = correlation.putIfAbsent(correlationId, handler, requestTimeout);
        if (result != null) {
            String logMessage = String.format("The correlationId [%s] is not unique.", correlationId);
            throw new IllegalArgumentException(logMessage);
        }
        return correlationId;
    }
    
    
    protected abstract ReplyHandler createReplyHandler(ReplyManager replyManager, Exchange exchange, AsyncCallback callback,
                                String originalCorrelationId, String correlationId, long requestTimeout);

    @Override
    public void onMessage(Message message, Session session) throws JMSException {
        String correlationID = null;

        try {
            if (correlationProperty == null) {
                correlationID = message.getJMSCorrelationID();
            } else {
                correlationID = message.getStringProperty(correlationProperty);
            }
        } catch (JMSException e) {
            // ignore
        }

        if (correlationID == null) {
            log.warn("Ignoring message with no correlationID: {}", message);
            return;
        }

        log.debug("Received reply message with correlationID [{}] -> {}", correlationID, message);

        // handle the reply message
        handleReplyMessage(correlationID, message, session);
    }

    @Override
    public void processReply(ReplyHolder holder) {
        if (holder != null && isRunAllowed()) {
            try {
                Exchange exchange = holder.getExchange();

                boolean timeout = holder.isTimeout();
                if (timeout) {
                    // timeout occurred do a WARN log so its easier to spot in the logs
                    if (log.isWarnEnabled()) {
                        log.warn("Timeout occurred after {} millis waiting for reply message with correlationID [{}] on destination {}."
                                + " Setting ExchangeTimedOutException on {} and continue routing.",
                                new Object[]{holder.getRequestTimeout(), holder.getCorrelationId(), replyTo, ExchangeHelper.logIds(exchange)});
                    }

                    // no response, so lets set a timed out exception
                    String msg = "reply message with correlationID: " + holder.getCorrelationId() + " not received on destination: " + replyTo;
                    exchange.setException(new ExchangeTimedOutException(exchange, holder.getRequestTimeout(), msg));
                } else {
                    Message message = holder.getMessage();
                    Session session = holder.getSession();
                    JmsMessage response = new JmsMessage(exchange, message, session, endpoint.getBinding());
                    // the JmsBinding is designed to be "pull-based": it will populate the Camel message on demand
                    // therefore, we link Exchange and OUT message before continuing, so that the JmsBinding has full access 
                    // to everything it may need, and can populate headers, properties, etc. accordingly (solves CAMEL-6218).
                    exchange.setOut(response);
                    Object body = response.getBody();

                    if (endpoint.isTransferException() && body instanceof Exception) {
                        log.debug("Reply was an Exception. Setting the Exception on the Exchange: {}", body);
                        // we got an exception back and endpoint was configured to transfer exception
                        // therefore set response as exception
                        exchange.setException((Exception) body);
                    } else {
                        log.debug("Reply received. OUT message body set to reply payload: {}", body);
                    }

                    // restore correlation id in case the remote server messed with it
                    if (holder.getOriginalCorrelationId() != null) {
                        JmsMessageHelper.setCorrelationId(message, holder.getOriginalCorrelationId());
                        exchange.getOut().setHeader("JMSCorrelationID", holder.getOriginalCorrelationId());
                    }
                }
            } finally {
                // notify callback
                AsyncCallback callback = holder.getCallback();
                callback.done(false);
            }
        }
    }

    protected abstract void handleReplyMessage(String correlationID, Message message, Session session);

    protected abstract AbstractMessageListenerContainer createListenerContainer() throws Exception;

    /**
     * <b>IMPORTANT:</b> This logic is only being used due to high performance in-memory only
     * testing using InOut over JMS. Its unlikely to happen in a real life situation with communication
     * to a remote broker, which always will be slower to send back reply, before Camel had a chance
     * to update it's internal correlation map.
     */
    protected ReplyHandler waitForProvisionCorrelationToBeUpdated(String correlationID, Message message) {
        // race condition, when using messageID as correlationID then we store a provisional correlation id
        // at first, which gets updated with the JMSMessageID after the message has been sent. And in the unlikely
        // event that the reply comes back really really fast, and the correlation map hasn't yet been updated
        // from the provisional id to the JMSMessageID. If so we have to wait a bit and lookup again.
        if (log.isWarnEnabled()) {
            log.warn("Early reply received with correlationID [{}] -> {}", correlationID, message);
        }

        ReplyHandler answer = null;

        // wait up until configured values
        boolean done = false;
        int counter = 0;
        while (!done && counter++ < endpoint.getConfiguration().getWaitForProvisionCorrelationToBeUpdatedCounter()) {
            log.trace("Early reply not found handler at attempt {}. Waiting a bit longer.", counter);
            try {
                Thread.sleep(endpoint.getConfiguration().getWaitForProvisionCorrelationToBeUpdatedThreadSleepingTime());
            } catch (InterruptedException e) {
                // ignore
            }

            // try again
            answer = correlation.get(correlationID);
            done = answer != null;

            if (answer != null) {
                if (log.isTraceEnabled()) {
                    log.trace("Early reply with correlationID [{}] has been matched after {} attempts and can be processed using handler: {}",
                            new Object[]{correlationID, counter, answer});
                }
            }
        }

        return answer;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(executorService, "executorService", this);
        ObjectHelper.notNull(scheduledExecutorService, "scheduledExecutorService", this);
        ObjectHelper.notNull(endpoint, "endpoint", this);

        // timeout map to use for purging messages which have timed out, while waiting for an expected reply
        // when doing request/reply over JMS
        log.trace("Using timeout checker interval with {} millis", endpoint.getRequestTimeoutCheckerInterval());
        correlation = new CorrelationTimeoutMap(scheduledExecutorService, endpoint.getRequestTimeoutCheckerInterval(), executorService);
        ServiceHelper.startService(correlation);

        // create JMS listener and start it
        listenerContainer = createListenerContainer();
        listenerContainer.afterPropertiesSet();
        log.debug("Starting reply listener container on endpoint: {}", endpoint);

        endpoint.onListenerContainerStarting(listenerContainer);
        listenerContainer.start();
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(correlation);

        if (listenerContainer != null) {
            log.debug("Stopping reply listener container on endpoint: {}", endpoint);
            try {
                listenerContainer.stop();
                listenerContainer.destroy();
            } finally {
                endpoint.onListenerContainerStopped(listenerContainer);
                listenerContainer = null;
            }
        }

        // must also stop executor service
        if (scheduledExecutorService != null) {
            camelContext.getExecutorServiceManager().shutdownGraceful(scheduledExecutorService);
            scheduledExecutorService = null;
        }
        if (executorService != null) {
            camelContext.getExecutorServiceManager().shutdownGraceful(executorService);
            executorService = null;
        }
    }

}
