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

import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.jms.Destination;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.TemporaryQueue;

import org.apache.camel.CamelContext;
import org.apache.camel.component.sjms.MessageListenerContainer;
import org.apache.camel.component.sjms.jms.DestinationCreationStrategy;

/**
 * A {@link ReplyManager} when using temporary queues.
 */
public class TemporaryQueueReplyManager extends ReplyManagerSupport {

    final TemporaryReplyQueueDestinationResolver destResolver = new TemporaryReplyQueueDestinationResolver();

    public TemporaryQueueReplyManager(CamelContext camelContext) {
        super(camelContext);
    }

    @Override
    public Destination getReplyTo() {
        try {
            destResolver.destinationReady();
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for JMSReplyTo destination refresh", e);
            Thread.currentThread().interrupt();
        }
        return super.getReplyTo();
    }

    @Override
    public void updateCorrelationId(String correlationId, String newCorrelationId, long requestTimeout) {
        log.trace("Updated provisional correlationId [{}] to expected correlationId [{}]", correlationId, newCorrelationId);

        ReplyHandler handler = correlation.remove(correlationId);
        if (handler != null) {
            correlation.put(newCorrelationId, handler, requestTimeout);
        }
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
            log.warn("Reply received for unknown correlationID [{}]. The message will be ignored: {}", correlationID, message);
        }
    }

    @Override
    protected MessageListenerContainer createListenerContainer() throws Exception {
        TemporaryQueueMessageListenerContainer answer
                = new TemporaryQueueMessageListenerContainer(endpoint);
        answer.setMessageListener(this);

        String clientId = endpoint.getClientId();
        if (clientId != null) {
            clientId += ".CamelReplyManager";
            answer.setClientId(clientId);
        }
        answer.setConcurrentConsumers(endpoint.getReplyToConcurrentConsumers());
        answer.setExceptionListener(new TemporaryReplyQueueExceptionListener(destResolver));
        answer.setDestinationCreationStrategy(destResolver);
        answer.setDestinationName("temporary");
        return answer;
    }

    private final class TemporaryReplyQueueExceptionListener implements ExceptionListener {
        private final TemporaryReplyQueueDestinationResolver destResolver;

        private TemporaryReplyQueueExceptionListener(TemporaryReplyQueueDestinationResolver destResolver) {
            this.destResolver = destResolver;
        }

        @Override
        public void onException(JMSException exception) {
            // capture exceptions, and schedule a refresh of the ReplyTo destination
            log.warn("Exception inside the DMLC for Temporary ReplyTo Queue for destination {}, refreshing ReplyTo destination",
                    endpoint.getDestinationName(), exception);
            destResolver.scheduleRefresh();
        }

    }

    private final class TemporaryReplyQueueDestinationResolver implements DestinationCreationStrategy {
        private TemporaryQueue queue;
        private final AtomicBoolean refreshWanted = new AtomicBoolean();

        @Override
        public Destination createDestination(Session session, String name, boolean topic) throws JMSException {
            return null;
        }

        @Override
        public Destination createTemporaryDestination(Session session, boolean topic) throws JMSException {
            synchronized (refreshWanted) {
                if (queue == null || refreshWanted.get()) {
                    refreshWanted.set(false);
                    queue = session.createTemporaryQueue();
                    setReplyTo(queue);
                    if (log.isDebugEnabled()) {
                        log.debug("Refreshed Temporary ReplyTo Queue. New queue: {}", queue.getQueueName());
                    }
                    refreshWanted.notifyAll();
                }
            }
            return queue;
        }

        public void scheduleRefresh() {
            refreshWanted.set(true);
        }

        public void destinationReady() throws InterruptedException {
            if (refreshWanted.get()) {
                synchronized (refreshWanted) {
                    //check if requestWanted is still true
                    if (refreshWanted.get()) {
                        log.debug("Waiting for new Temporary ReplyTo queue to be assigned before we can continue");
                        refreshWanted.wait();
                    }
                }
            }
        }

    }

}
