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
package org.apache.camel.component.alibaba.mns;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.aliyun.mns.client.CloudQueue;
import com.aliyun.mns.model.Message;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.component.alibaba.mns.constants.MNSHeaders;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MNSConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(MNSConsumer.class);

    public MNSConsumer(MNSEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {
        shutdownRunningTask = null;
        pendingExchanges = 0;

        MNSEndpoint endpoint = getEndpoint();
        endpoint.initClient();

        CloudQueue queue = endpoint.getMnsClient().getQueueRef(endpoint.getQueueName());
        List<Message> messages = receiveMessages(queue, endpoint.getMaxMessagesPerPoll(), endpoint.getWaitSeconds());

        forceConsumerAsReady();

        if (messages.isEmpty()) {
            return 0;
        }

        Queue<Exchange> exchanges = createExchanges(messages);
        return processBatch(CastUtils.cast(exchanges));
    }

    private List<Message> receiveMessages(CloudQueue queue, int maxMessagesPerPoll, int waitSeconds) throws Exception {
        int maxMessages = Math.max(1, maxMessagesPerPoll);
        List<Message> messages = new ArrayList<>();

        if (maxMessages == 1) {
            Message message = waitSeconds > 0 ? queue.popMessage(waitSeconds) : queue.popMessage();
            if (message != null) {
                messages.add(message);
            }
            return messages;
        }

        List<Message> batch = waitSeconds > 0
                ? queue.batchPopMessage(maxMessages, waitSeconds)
                : queue.batchPopMessage(maxMessages);
        if (batch != null) {
            messages.addAll(batch);
        }
        return messages;
    }

    protected Queue<Exchange> createExchanges(List<Message> messages) {
        Queue<Exchange> answer = new LinkedList<>();
        for (Message message : messages) {
            if (ObjectHelper.isNotEmpty(message)) {
                answer.add(createExchange(message));
            }
        }
        return answer;
    }

    private Exchange createExchange(Message message) {
        Exchange exchange = createExchange(true);
        org.apache.camel.Message camelMessage = exchange.getIn();
        camelMessage.setBody(message.getMessageBody());
        camelMessage.setHeader(MNSHeaders.MESSAGE_ID, message.getMessageId());
        camelMessage.setHeader(MNSHeaders.RECEIPT_HANDLE, message.getReceiptHandle());
        camelMessage.setHeader(MNSHeaders.MESSAGE_BODY_MD5, message.getMessageBodyMD5());
        camelMessage.setHeader(MNSHeaders.DEQUEUE_COUNT, message.getDequeueCount());
        camelMessage.setHeader(MNSHeaders.ENQUEUE_TIME, message.getEnqueueTime());
        camelMessage.setHeader(MNSHeaders.NEXT_VISIBLE_TIME, message.getNextVisibleTime());
        camelMessage.setHeader(MNSHeaders.FIRST_DEQUEUE_TIME, message.getFirstDequeueTime());
        camelMessage.setHeader(MNSHeaders.PRIORITY, message.getPriority());
        return exchange;
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
            exchange.setProperty(ExchangePropertyKey.BATCH_INDEX, index);
            exchange.setProperty(ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(ExchangePropertyKey.BATCH_COMPLETE, index == total - 1);

            pendingExchanges = total - index - 1;

            exchange.getExchangeExtension().addOnCompletion(new Synchronization() {
                @Override
                public void onComplete(Exchange exchange) {
                    processCommit(exchange);
                }

                @Override
                public void onFailure(Exchange exchange) {
                    processRollback(exchange);
                }

                @Override
                public String toString() {
                    return "MNSConsumerOnCompletion";
                }
            });

            AsyncCallback callback = defaultConsumerCallback(exchange, true);
            getAsyncProcessor().process(exchange, callback);
        }

        return total;
    }

    protected void processCommit(Exchange exchange) {
        if (!getEndpoint().isDeleteAfterRead()) {
            return;
        }
        try {
            String receiptHandle = exchange.getIn().getHeader(MNSHeaders.RECEIPT_HANDLE, String.class);
            if (ObjectHelper.isEmpty(receiptHandle)) {
                return;
            }
            CloudQueue queue = getEndpoint().getMnsClient().getQueueRef(getEndpoint().getQueueName());
            queue.deleteMessage(receiptHandle);
        } catch (Exception e) {
            getExceptionHandler().handleException("Error occurred during deleting MNS message. This exception is ignored.",
                    exchange, e);
        }
    }

    protected void processRollback(Exchange exchange) {
        Exception cause = exchange.getException();
        if (ObjectHelper.isNotEmpty(cause)) {
            getExceptionHandler().handleException(
                    "Error during processing MNS exchange. Will attempt to process the message on next poll.", exchange,
                    cause);
        }
    }

    @Override
    public MNSEndpoint getEndpoint() {
        return (MNSEndpoint) super.getEndpoint();
    }
}
