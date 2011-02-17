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
package org.apache.camel.component.aws.sqs;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;

import org.apache.camel.BatchConsumer;
import org.apache.camel.Exchange;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A Consumer of messages from the Amazon Web Service Simple Queue Service
 * <a href="http://aws.amazon.com/aws-sqs/">AWS SQS</a>
 * 
 * @version 
 */
public class SqsConsumer extends ScheduledPollConsumer implements BatchConsumer, ShutdownAware {
    
    private static final transient Logger LOG = LoggerFactory.getLogger(SqsConsumer.class);
    
    private volatile ShutdownRunningTask shutdownRunningTask;
    private volatile int pendingExchanges;

    public SqsConsumer(SqsEndpoint endpoint, Processor processor) throws NoFactoryAvailableException {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {
        // must reset for each poll
        shutdownRunningTask = null;
        pendingExchanges = 0;
        
        ReceiveMessageRequest request = new ReceiveMessageRequest(getQueueUrl());
        request.setMaxNumberOfMessages(getMaxMessagesPerPoll() > 0 ? getMaxMessagesPerPoll() : null);
        request.setVisibilityTimeout(getConfiguration().getVisibilityTimeout() != null ? getConfiguration().getVisibilityTimeout() : null);
        request.setAttributeNames(getConfiguration().getAttributeNames() != null ? getConfiguration().getAttributeNames() : null);
        ReceiveMessageResult messageResult = getClient().receiveMessage(request);
        
        Queue<Exchange> exchanges = createExchanges(messageResult.getMessages());
        return processBatch(CastUtils.cast(exchanges));
    }
    
    protected Queue<Exchange> createExchanges(List<Message> messages) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Received " + messages.size() + " messages in this poll");
        }
        
        Queue<Exchange> answer = new LinkedList<Exchange>();
        for (Message message : messages) {
            Exchange exchange = getEndpoint().createExchange(message);
            answer.add(exchange);
        }

        return answer;
    }
    
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
            // add current index and total as properties
            exchange.setProperty(Exchange.BATCH_INDEX, index);
            exchange.setProperty(Exchange.BATCH_SIZE, total);
            exchange.setProperty(Exchange.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            // add on completion to handle after work when the exchange is done
            exchange.addOnCompletion(new Synchronization() {
                public void onComplete(Exchange exchange) {
                    processCommit(exchange);
                }

                public void onFailure(Exchange exchange) {
                    processRollback(exchange);
                }

                @Override
                public String toString() {
                    return "SqsConsumerOnCompletion";
                }
            });

            if (LOG.isTraceEnabled()) {
                LOG.trace("Processing exchange [" + exchange + "]...");
            }

            getProcessor().process(exchange);
        }

        return total;
    }
    
    /**
     * Strategy to delete the message after being processed.
     *
     * @param exchange the exchange
     */
    protected void processCommit(Exchange exchange) {
        try {
            if (getConfiguration().isDeleteAfterRead()) {
                String receiptHandle = exchange.getIn().getHeader(SqsConstants.RECEIPT_HANDLE, String.class);
                DeleteMessageRequest deleteRequest = new DeleteMessageRequest(getQueueUrl(), receiptHandle);
                
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Deleting message with receipt handle " + receiptHandle + "...");
                }
                
                getClient().deleteMessage(deleteRequest);
            }
        } catch (AmazonClientException e) {
            LOG.warn("Error occurred during deleting message", e);
            exchange.setException(e);
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
            LOG.warn("Exchange failed, so rolling back message status: " + exchange);
        }
    }
    
    public boolean isBatchAllowed() {
        // stop if we are not running
        boolean answer = isRunAllowed();
        if (!answer) {
            return false;
        }

        if (shutdownRunningTask == null) {
            // we are not shutting down so continue to run
            return true;
        }

        // we are shutting down so only continue if we are configured to complete all tasks
        return ShutdownRunningTask.CompleteAllTasks == shutdownRunningTask;
    }

    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        // store a reference what to do in case when shutting down and we have pending messages
        this.shutdownRunningTask = shutdownRunningTask;
        // do not defer shutdown
        return false;
    }

    public int getPendingExchangesSize() {
        // only return the real pending size in case we are configured to complete all tasks
        if (ShutdownRunningTask.CompleteAllTasks == shutdownRunningTask) {
            return pendingExchanges;
        } else {
            return 0;
        }
    }

    public void prepareShutdown() {
     // noop
    }
    
    protected SqsConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }
    
    protected AmazonSQSClient getClient() {
        return getEndpoint().getClient();
    }
    
    protected String getQueueUrl() {
        return getEndpoint().getQueueUrl();
    }
    
    @Override
    public SqsEndpoint getEndpoint() {
        return (SqsEndpoint) super.getEndpoint();
    }
    
    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        getEndpoint().setMaxMessagesPerPoll(maxMessagesPerPoll);
    }
    
    public int getMaxMessagesPerPoll() {
        return getEndpoint().getMaxMessagesPerPoll();
    }
}
