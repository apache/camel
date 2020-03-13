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
package org.apache.camel.component.rabbitmq.reply;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Connection;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.TimeoutMap;
import org.apache.camel.component.rabbitmq.RabbitMQConstants;
import org.apache.camel.component.rabbitmq.RabbitMQEndpoint;
import org.apache.camel.component.rabbitmq.RabbitMQMessageConverter;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ReplyManagerSupport extends ServiceSupport implements ReplyManager {
    private static final int CLOSE_TIMEOUT = 30 * 1000;

    private static final Logger LOG = LoggerFactory.getLogger(ReplyManagerSupport.class);

    protected final CamelContext camelContext;
    protected final CountDownLatch replyToLatch = new CountDownLatch(1);
    protected final long replyToTimeout = 1000;

    protected ScheduledExecutorService executorService;
    protected RabbitMQEndpoint endpoint;
    protected String replyTo;

    protected Connection listenerContainer;
    protected TimeoutMap<String, ReplyHandler> correlation;

    private final RabbitMQMessageConverter messageConverter = new RabbitMQMessageConverter();

    public ReplyManagerSupport(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void setScheduledExecutorService(ScheduledExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public void setEndpoint(RabbitMQEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public void setReplyTo(String replyTo) {
        LOG.debug("ReplyTo destination: {}", replyTo);
        this.replyTo = replyTo;
        // trigger latch as the reply to has been resolved and set
        replyToLatch.countDown();
    }

    @Override
    public String getReplyTo() {
        if (replyTo != null) {
            return replyTo;
        }
        try {
            // the reply to destination has to be resolved using a
            // DestinationResolver using
            // the MessageListenerContainer which occurs asynchronously so we
            // have to wait
            // for that to happen before we can retrieve the reply to
            // destination to be used
            LOG.trace("Waiting for replyTo to be set");
            boolean done = replyToLatch.await(replyToTimeout, TimeUnit.MILLISECONDS);
            if (!done) {
                LOG.warn("ReplyTo destination was not set and timeout occurred");
            } else {
                LOG.trace("Waiting for replyTo to be set done");
            }
        } catch (InterruptedException e) {
            // ignore
        }
        return replyTo;
    }

    @Override
    public String registerReply(ReplyManager replyManager, Exchange exchange, AsyncCallback callback, String originalCorrelationId, String correlationId, long requestTimeout) {
        // add to correlation map
        QueueReplyHandler handler = new QueueReplyHandler(replyManager, exchange, callback, originalCorrelationId, correlationId, requestTimeout);
        // Just make sure we don't override the old value of the correlationId
        ReplyHandler result = correlation.putIfAbsent(correlationId, handler, requestTimeout);
        if (result != null) {
            String logMessage = String.format("The correlationId [%s] is not unique.", correlationId);
            throw new IllegalArgumentException(logMessage);
        }
        return correlationId;
    }

    protected abstract ReplyHandler createReplyHandler(ReplyManager replyManager, Exchange exchange, AsyncCallback callback, String originalCorrelationId, String correlationId,
                                                       long requestTimeout);

    @Override
    public void cancelCorrelationId(String correlationId) {
        ReplyHandler handler = correlation.get(correlationId);
        if (handler != null) {
            LOG.warn("Cancelling correlationID: {}", correlationId);
            correlation.remove(correlationId);
        }
    }

    public void onMessage(AMQP.BasicProperties properties, byte[] message) {
        String correlationID = properties.getCorrelationId();

        if (correlationID == null) {
            LOG.warn("Ignoring message with no correlationID: {}", message);
            return;
        }

        LOG.debug("Received reply message with correlationID [{}] -> {}", correlationID, message);

        // handle the reply message
        handleReplyMessage(correlationID, properties, message);
    }

    @Override
    public void processReply(ReplyHolder holder) {
        if (holder != null && isRunAllowed()) {
            try {
                Exchange exchange = holder.getExchange();

                boolean timeout = holder.isTimeout();
                if (timeout) {
                    // timeout occurred do a WARN log so its easier to spot in
                    // the logs
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("Timeout occurred after {} millis waiting for reply message with correlationID [{}] on destination {}."
                                 + " Setting ExchangeTimedOutException on {} and continue routing.", holder.getRequestTimeout(), holder.getCorrelationId(), replyTo,
                                 ExchangeHelper.logIds(exchange));
                    }

                    // no response, so lets set a timed out exception
                    String msg = "reply message with correlationID: " + holder.getCorrelationId() + " not received on destination: " + replyTo;
                    exchange.setException(new ExchangeTimedOutException(exchange, holder.getRequestTimeout(), msg));
                } else {

                    messageConverter.populateRabbitExchange(exchange, null, holder.getProperties(), holder.getMessage(), true,
                                                            endpoint.isAllowMessageBodySerialization());

                    // restore correlation id in case the remote server messed
                    // with it
                    if (holder.getOriginalCorrelationId() != null) {
                        if (exchange.hasOut()) {
                            exchange.getOut().setHeader(RabbitMQConstants.CORRELATIONID, holder.getOriginalCorrelationId());
                        } else {
                            exchange.getIn().setHeader(RabbitMQConstants.CORRELATIONID, holder.getOriginalCorrelationId());
                        }
                    }
                }
            } finally {
                // notify callback
                AsyncCallback callback = holder.getCallback();
                callback.done(false);
            }
        }
    }

    protected abstract void handleReplyMessage(String correlationID, AMQP.BasicProperties properties, byte[] message);

    protected abstract Connection createListenerContainer() throws Exception;

    /**
     * <b>IMPORTANT:</b> This logic is only being used due to high performance
     * in-memory only testing using InOut over JMS. Its unlikely to happen in a
     * real life situation with communication to a remote broker, which always
     * will be slower to send back reply, before Camel had a chance to update
     * it's internal correlation map.
     */
    protected ReplyHandler waitForProvisionCorrelationToBeUpdated(String correlationID, byte[] message) {
        // race condition, when using messageID as correlationID then we store a
        // provisional correlation id
        // at first, which gets updated with the JMSMessageID after the message
        // has been sent. And in the unlikely
        // event that the reply comes back really really fast, and the
        // correlation map hasn't yet been updated
        // from the provisional id to the JMSMessageID. If so we have to wait a
        // bit and lookup again.
        if (LOG.isWarnEnabled()) {
            LOG.warn("Early reply received with correlationID [{}] -> {}", correlationID, message);
        }

        ReplyHandler answer = null;

        // wait up till 5 seconds
        boolean done = false;
        int counter = 0;
        while (!done && counter++ < 50) {
            LOG.trace("Early reply not found handler at attempt {}. Waiting a bit longer.", counter);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // ignore
            }

            // try again
            answer = correlation.get(correlationID);
            done = answer != null;

            if (answer != null) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Early reply with correlationID [{}] has been matched after {} attempts and can be processed using handler: {}", correlationID, counter, answer);
                }
            }
        }

        return answer;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(executorService, "executorService", this);
        ObjectHelper.notNull(endpoint, "endpoint", this);

        messageConverter.setAllowNullHeaders(endpoint.isAllowNullHeaders());
        // timeout map to use for purging messages which have timed out, while
        // waiting for an expected reply
        // when doing request/reply over JMS
        LOG.debug("Using timeout checker interval with {} millis", endpoint.getRequestTimeoutCheckerInterval());
        correlation = new CorrelationTimeoutMap(executorService, endpoint.getRequestTimeoutCheckerInterval());
        ServiceHelper.startService(correlation);

        // create listener and start it
        listenerContainer = createListenerContainer();

        LOG.debug("Using executor {}", executorService);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(correlation);

        if (listenerContainer != null) {
            LOG.debug("Closing connection: {} with timeout: {} ms.", listenerContainer, CLOSE_TIMEOUT);
            listenerContainer.close(CLOSE_TIMEOUT);
            listenerContainer = null;
        }

        // must also stop executor service
        if (executorService != null) {
            camelContext.getExecutorServiceManager().shutdownGraceful(executorService);
            executorService = null;
        }
    }
}
