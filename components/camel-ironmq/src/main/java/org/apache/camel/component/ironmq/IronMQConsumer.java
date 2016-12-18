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
package org.apache.camel.component.ironmq;

import java.util.LinkedList;
import java.util.Queue;

import io.iron.ironmq.EmptyQueueException;
import io.iron.ironmq.Message;
import io.iron.ironmq.Messages;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledBatchPollingConsumer;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The IronMQ consumer.
 */
public class IronMQConsumer extends ScheduledBatchPollingConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(IronMQConsumer.class);
    
    private final io.iron.ironmq.Queue ironQueue;
    
    public IronMQConsumer(Endpoint endpoint, Processor processor, io.iron.ironmq.Queue ironQueue) {
        super(endpoint, processor);
        this.ironQueue = ironQueue;
    }

    @Override
    protected int poll() throws Exception {
        // must reset for each poll
        shutdownRunningTask = null;
        pendingExchanges = 0;
        try {
            Messages messages = null;
            LOG.trace("Receiving messages with request [messagePerPoll {}, timeout {}]...", getMaxMessagesPerPoll(), getEndpoint().getConfiguration().getTimeout());
            messages = this.ironQueue.reserve(getMaxMessagesPerPoll(), getEndpoint().getConfiguration().getTimeout(), getEndpoint().getConfiguration().getWait());
            LOG.trace("Received {} messages", messages.getSize());

            Queue<Exchange> exchanges = createExchanges(messages.getMessages());
            int noProcessed = processBatch(CastUtils.cast(exchanges));
            // delete all processed messages in one batch;
            if (getEndpoint().getConfiguration().isBatchDelete()) {
                LOG.trace("Batch deleting {} messages", messages.getSize());
                this.ironQueue.deleteMessages(messages);
            }
            return noProcessed;
        } catch (EmptyQueueException e) {
            return 0;
        }
    }

    protected Queue<Exchange> createExchanges(Message[] messages) {
        LOG.trace("Received {} messages in this poll", messages.length);

        Queue<Exchange> answer = new LinkedList<Exchange>();
        for (Message message : messages) {
            Exchange exchange = getEndpoint().createExchange(message);
            answer.add(exchange);
        }
        return answer;
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
            // add current index and total as properties
            exchange.setProperty(Exchange.BATCH_INDEX, index);
            exchange.setProperty(Exchange.BATCH_SIZE, total);
            exchange.setProperty(Exchange.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            // add on completion to handle after work when the exchange is done
            // if batchDelete is not enabled
            if (!getEndpoint().getConfiguration().isBatchDelete()) {
                exchange.addOnCompletion(new Synchronization() {
                    final String reservationId = ExchangeHelper.getMandatoryHeader(exchange, IronMQConstants.MESSAGE_RESERVATION_ID, String.class);
                    final String messageid = ExchangeHelper.getMandatoryHeader(exchange, IronMQConstants.MESSAGE_ID, String.class);

                    public void onComplete(Exchange exchange) {
                        processCommit(exchange, messageid, reservationId);
                    }

                    public void onFailure(Exchange exchange) {
                        processRollback(exchange);
                    }

                    @Override
                    public String toString() {
                        return "IronMQConsumerOnCompletion";
                    }
                });
            }

            LOG.trace("Processing exchange [{}]...", exchange);

            getProcessor().process(exchange);
        }

        return total;
    }

    /**
     * Strategy to delete the message after being processed.
     * 
     * @param exchange the exchange
     */
    protected void processCommit(Exchange exchange, String messageid, String reservationId) {
        try {
            LOG.trace("Deleting message with messageId {} and reservationId {}...", messageid, reservationId);
            this.ironQueue.deleteMessage(messageid, reservationId);
            LOG.trace("Message deleted");
        } catch (Exception e) {
            getExceptionHandler().handleException("Error occurred during delete of message. This exception is ignored.", exchange, e);
        }
    }

    /**
     * Strategy when processing the exchange failed.
     * 
     * @param exchange the exchange
     */
    protected void processRollback(Exchange exchange) {
        Exception cause = exchange.getException();
        if (cause != null) {
            LOG.warn("Exchange failed, so rolling back message status: " + exchange, cause);
        } else {
            LOG.warn("Exchange failed, so rolling back message status: {}", exchange);
        }
    }

    @Override
    public IronMQEndpoint getEndpoint() {
        return (IronMQEndpoint)super.getEndpoint();
    }

}
