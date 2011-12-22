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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.component.jms.JmsEndpoint;
import org.apache.camel.component.jms.JmsMessage;
import org.apache.camel.component.jms.JmsMessageHelper;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.listener.AbstractMessageListenerContainer;

/**
 * Base class for {@link ReplyManager} implementations.
 *
 * @version 
 */
public abstract class ReplyManagerSupport extends ServiceSupport implements ReplyManager {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected ScheduledExecutorService executorService;
    protected JmsEndpoint endpoint;
    protected Destination replyTo;
    protected AbstractMessageListenerContainer listenerContainer;
    protected final CountDownLatch replyToLatch = new CountDownLatch(1);
    protected final long replyToTimeout = 10000;
    protected CorrelationTimeoutMap correlation;

    public void setScheduledExecutorService(ScheduledExecutorService executorService) {
        this.executorService = executorService;
    }

    public void setEndpoint(JmsEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public void setReplyTo(Destination replyTo) {
        log.trace("ReplyTo destination: {}", replyTo);
        this.replyTo = replyTo;
        // trigger latch as the reply to has been resolved and set
        replyToLatch.countDown();
    }

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

    public void onMessage(Message message) {
        String correlationID = null;
        try {
            correlationID = message.getJMSCorrelationID();
        } catch (JMSException e) {
            // ignore
        }
        if (correlationID == null) {
            log.warn("Ignoring message with no correlationID: " + message);
            return;
        }

        log.debug("Received reply message with correlationID [{}] -> {}", correlationID, message);

        // handle the reply message
        handleReplyMessage(correlationID, message);
    }

    public void processReply(ReplyHolder holder) {
        if (holder != null && isRunAllowed()) {
            try {
                Exchange exchange = holder.getExchange();
                Message message = holder.getMessage();

                boolean timeout = holder.isTimeout();
                if (timeout) {
                    // timeout occurred do a WARN log so its easier to spot in the logs
                    log.warn("Timeout occurred after {} millis waiting for reply message with correlationID [{}]."
                            + " Setting ExchangeTimedOutException on ExchangeId: {} and continue routing.",
                            new Object[]{holder.getRequestTimeout(), holder.getCorrelationId(), exchange.getExchangeId()});

                    // no response, so lets set a timed out exception
                    String msg = "reply message with correlationID: " + holder.getCorrelationId() + " not received";
                    exchange.setException(new ExchangeTimedOutException(exchange, holder.getRequestTimeout(), msg));
                } else {
                    JmsMessage response = new JmsMessage(message, endpoint.getBinding());
                    Object body = response.getBody();

                    if (endpoint.isTransferException() && body instanceof Exception) {
                        log.debug("Reply received. Setting reply as an Exception: {}", body);
                        // we got an exception back and endpoint was configured to transfer exception
                        // therefore set response as exception
                        exchange.setException((Exception) body);
                    } else {
                        log.debug("Reply received. Setting reply as OUT message: {}", body);
                        // regular response
                        exchange.setOut(response);
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

    protected abstract void handleReplyMessage(String correlationID, Message message);

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
            log.warn("Early reply received with correlationID [" + correlationID + "] -> " + message);
        }

        ReplyHandler answer = null;

        // wait up till 5 seconds
        boolean done = false;
        int counter = 0;
        while (!done && counter++ < 50) {
            log.trace("Early reply not found handler at attempt {}. Waiting a bit longer.", counter);
            try {
                Thread.sleep(100);
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
        ObjectHelper.notNull(endpoint, "endpoint", this);

        // purge for timeout every second
        correlation = new CorrelationTimeoutMap(executorService, 1000);
        ServiceHelper.startService(correlation);

        // create JMS listener and start it
        listenerContainer = createListenerContainer();
        listenerContainer.afterPropertiesSet();
        log.info("Starting reply listener container on endpoint: " + endpoint);
        listenerContainer.start();
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(correlation);

        if (listenerContainer != null) {
            log.info("Stopping reply listener container on endpoint: " + endpoint);
            listenerContainer.stop();
            listenerContainer.destroy();
            listenerContainer = null;
        }
    }

}
