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
package org.apache.camel.component.sjms.consumer;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Topic;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.sjms.SjmsEndpoint;
import org.apache.camel.component.sjms.jms.JmsConstants;
import org.apache.camel.spi.Synchronization;

/**
 * cache manager to store and purge unused cashed producers or we will have a
 * memory leak
 */
public class InOutMessageHandler extends AbstractMessageHandler {

    private Map<String, MessageProducer> producerCache = new TreeMap<String, MessageProducer>();
    private ReadWriteLock lock = new ReentrantReadWriteLock();

    public InOutMessageHandler(SjmsEndpoint endpoint, ExecutorService executor) {
        super(endpoint, executor);
    }

    public InOutMessageHandler(SjmsEndpoint endpoint, ExecutorService executor, Synchronization synchronization) {
        super(endpoint, executor, synchronization);
    }

    @Override
    public void handleMessage(final Exchange exchange) {
        try {
            MessageProducer messageProducer = null;
            Object obj = exchange.getIn().getHeader(JmsConstants.JMS_REPLY_TO);
            if (obj != null) {
                Destination replyTo;
                if (isDestination(obj)) {
                    replyTo = (Destination) obj;
                } else if (obj instanceof String) {
                    replyTo = getEndpoint().getDestinationCreationStrategy().createDestination(getSession(), (String)obj, isTopic());
                } else {
                    throw new Exception("The value of JMSReplyTo must be a valid Destination or String.  Value provided: " + obj);
                }

                String destinationName = getDestinationName(replyTo);
                try {
                    lock.readLock().lock();
                    if (producerCache.containsKey(destinationName)) {
                        messageProducer = producerCache.get(destinationName);
                    }
                } finally {
                    lock.readLock().unlock();
                }
                if (messageProducer == null) {
                    try {
                        lock.writeLock().lock();
                        messageProducer = getSession().createProducer(replyTo);
                        producerCache.put(destinationName, messageProducer);
                    } finally {
                        lock.writeLock().unlock();
                    }
                }
            }

            MessageHandlerAsyncCallback callback = new MessageHandlerAsyncCallback(exchange, messageProducer);
            if (exchange.isFailed()) {
                return;
            } else {
                if (isTransacted() || isSynchronous()) {
                    // must process synchronous if transacted or configured to
                    // do so
                    log.debug("Synchronous processing: Message[{}], Destination[{}] ", exchange.getIn().getBody(), getEndpoint().getEndpointUri());
                    try {
                        getProcessor().process(exchange);
                    } catch (Exception e) {
                        exchange.setException(e);
                    } finally {
                        callback.done(true);
                    }
                } else {
                    // process asynchronous using the async routing engine
                    log.debug("Asynchronous processing: Message[{}], Destination[{}] ", exchange.getIn().getBody(), getEndpoint().getEndpointUri());
                    getProcessor().process(exchange, callback);
                }
            }
        } catch (Exception e) {
            exchange.setException(e);
        }

        if (log.isDebugEnabled()) {
            log.debug("SjmsMessageConsumer invoked for Exchange id:{} ", exchange.getExchangeId());
        }
    }

    @Override
    public void close() {
        for (final Map.Entry<String, MessageProducer> entry : producerCache.entrySet()) {
            try {
                entry.getValue().close();
            } catch (JMSException e) {
                log.debug("Cached MessageProducer with key:{} threw an unexpected exception", entry.getKey(), e);
            }
        }
        producerCache.clear();
    }

    private boolean isDestination(Object object) {
        return object instanceof Destination;
    }

    private String getDestinationName(Destination destination) throws Exception {
        String answer = null;
        if (destination instanceof Queue) {
            answer = ((Queue) destination).getQueueName();
        } else if (destination instanceof Topic) {
            answer = ((Topic) destination).getTopicName();
        }

        return answer;
    }

    protected class MessageHandlerAsyncCallback implements AsyncCallback {

        private final Exchange exchange;
        private final MessageProducer localProducer;

        public MessageHandlerAsyncCallback(Exchange exchange, MessageProducer localProducer) {
            this.exchange = exchange;
            this.localProducer = localProducer;
        }

        @Override
        public void done(boolean sync) {
            try {
                // the response can either be in OUT or IN
                org.apache.camel.Message msg = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
                Message response = getEndpoint().getBinding().makeJmsMessage(exchange, msg.getBody(), msg.getHeaders(), getSession(), null);
                response.setJMSCorrelationID(exchange.getIn().getHeader(JmsConstants.JMS_CORRELATION_ID, String.class));
                localProducer.send(response);
            } catch (Exception e) {
                exchange.setException(e);
            }
        }
    }
}
