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
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.sjms.SjmsEndpoint;
import org.apache.camel.component.sjms.SjmsExchangeMessageHelper;
import org.apache.camel.component.sjms.jms.JmsMessageHelper;
import org.apache.camel.component.sjms.jms.JmsObjectFactory;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * TODO Add Class documentation for AbstractMessageHandler 
 * TODO Create a producer
 * cache manager to store and purge unused cashed producers or we will have a
 * memory leak
 */
public class InOutMessageHandler extends AbstractMessageHandler {

    private Map<String, MessageProducer> producerCache = new TreeMap<String, MessageProducer>();
    private ReadWriteLock lock = new ReentrantReadWriteLock();

    
    /**
     * 
     * @param endpoint
     * @param executor
     */
    public InOutMessageHandler(Endpoint endpoint, ExecutorService executor) {
        super(endpoint, executor);
    }
    
    /**
     *
     * @param endpoint
     * @param executor
     * @param synchronization
     */
    public InOutMessageHandler(Endpoint endpoint, ExecutorService executor, Synchronization synchronization) {
        super(endpoint, executor, synchronization);
    }

    /**
     * @param message
     */
    @Override
    public void handleMessage(final Exchange exchange) {
        try {
            MessageProducer messageProducer = null;
            Object obj = exchange.getIn().getHeader(JmsMessageHelper.JMS_REPLY_TO);
            if (obj != null) {
                Destination replyTo = null;
                if (isDestination(obj)) {
                    replyTo = (Destination)obj;
                } else if (obj instanceof String) {
                    replyTo = JmsObjectFactory.createDestination(getSession(), (String)obj, isTopic());
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

            MessageHanderAsyncCallback callback = new MessageHanderAsyncCallback(exchange, messageProducer);
            if (exchange.isFailed()) {
                return;
            } else {
                if (isTransacted() || isSynchronous()) {
                    // must process synchronous if transacted or configured to
                    // do so
                    log.debug("Synchronous processing: Message[{}], Destination[{}] ", exchange.getIn().getBody(), this.getEndpoint().getEndpointUri());
                    try {
                        AsyncProcessorHelper.process(getProcessor(), exchange);
                    } catch (Exception e) {
                        exchange.setException(e);
                    } finally {
                        callback.done(true);
                    }
                } else {
                    // process asynchronous using the async routing engine
                    log.debug("Aynchronous processing: Message[{}], Destination[{}] ", exchange.getIn().getBody(), this.getEndpoint().getEndpointUri());
                    boolean sync = AsyncProcessorHelper.process(getProcessor(), exchange, callback);
                    if (!sync) {
                        // will be done async so return now
                        return;
                    }
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
        for (String key : producerCache.keySet()) {
            MessageProducer mp = producerCache.get(key);
            try {
                mp.close();
            } catch (JMSException e) {
                ObjectHelper.wrapRuntimeCamelException(e);
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
            answer = ((Queue)destination).getQueueName();
        } else if (destination instanceof Topic) {
            answer = ((Topic)destination).getTopicName();
        }

        return answer;
    }

    protected class MessageHanderAsyncCallback implements AsyncCallback {

        private Exchange exchange;
        private MessageProducer localProducer;

        public MessageHanderAsyncCallback(Exchange exchange, MessageProducer localProducer) {
            super();
            this.exchange = exchange;
            this.localProducer = localProducer;
        }

        @Override
        public void done(boolean sync) {

            try {
                Message response = SjmsExchangeMessageHelper.createMessage(exchange, getSession(), ((SjmsEndpoint)getEndpoint()).getJmsKeyFormatStrategy());
                response.setJMSCorrelationID(exchange.getIn().getHeader("JMSCorrelationID", String.class));
                localProducer.send(response);
            } catch (Exception e) {
                exchange.setException(e);
            }
        }
    }
}
