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

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityRequest;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageNotInflightException;
import com.amazonaws.services.sqs.model.QueueDeletedRecentlyException;
import com.amazonaws.services.sqs.model.QueueDoesNotExistException;
import com.amazonaws.services.sqs.model.ReceiptHandleIsInvalidException;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledBatchPollingConsumer;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Consumer of messages from the Amazon Web Service Simple Queue Service
 * <a href="http://aws.amazon.com/sqs/">AWS SQS</a>
 */
public class SqsConsumer extends ScheduledBatchPollingConsumer {
    
    private static final Logger LOG = LoggerFactory.getLogger(SqsConsumer.class);
    private ScheduledExecutorService scheduledExecutor;
    private transient String sqsConsumerToString;
    private Collection<String> attributeNames;
    private Collection<String> messageAttributeNames;

    public SqsConsumer(SqsEndpoint endpoint, Processor processor) throws NoFactoryAvailableException {
        super(endpoint, processor);

        if (getConfiguration().getAttributeNames() != null) {
            String[] names = getConfiguration().getAttributeNames().split(",");
            attributeNames = Arrays.asList(names);
        }
        if (getConfiguration().getMessageAttributeNames() != null) {
            String[] names = getConfiguration().getMessageAttributeNames().split(",");
            messageAttributeNames = Arrays.asList(names);
        }
    }

    @Override
    protected int poll() throws Exception {
        // must reset for each poll
        shutdownRunningTask = null;
        pendingExchanges = 0;
        
        ReceiveMessageRequest request = new ReceiveMessageRequest(getQueueUrl());
        request.setMaxNumberOfMessages(getMaxMessagesPerPoll() > 0 ? getMaxMessagesPerPoll() : null);
        request.setVisibilityTimeout(getConfiguration().getVisibilityTimeout() != null ? getConfiguration().getVisibilityTimeout() : null);
        request.setWaitTimeSeconds(getConfiguration().getWaitTimeSeconds() != null ? getConfiguration().getWaitTimeSeconds() : null);

        if (attributeNames != null) {
            request.setAttributeNames(attributeNames);
        }
        if (messageAttributeNames != null) {
            request.setMessageAttributeNames(messageAttributeNames);
        }

        LOG.trace("Receiving messages with request [{}]...", request);
        
        ReceiveMessageResult messageResult = null;
        try {
            messageResult = getClient().receiveMessage(request);
        } catch (QueueDoesNotExistException e) {
            LOG.info("Queue does not exist....recreating now...");
            reConnectToQueue();
            messageResult = getClient().receiveMessage(request);
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Received {} messages", messageResult.getMessages().size());
        }
        
        Queue<Exchange> exchanges = createExchanges(messageResult.getMessages());
        return processBatch(CastUtils.cast(exchanges));
    }

    public void reConnectToQueue() {
        try {
            getEndpoint().createQueue(getClient());
        } catch (QueueDeletedRecentlyException qdr) {
            LOG.debug("Queue recently deleted, will retry in 30 seconds.");
            try {
                Thread.sleep(30000);
                getEndpoint().createQueue(getClient());
            } catch (Exception e) {
                LOG.warn("failed to retry queue connection.", e);
            }
        } catch (Exception e) {
            LOG.warn("Could not connect to queue in amazon.", e);
        }
    }
    
    protected Queue<Exchange> createExchanges(List<Message> messages) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Received {} messages in this poll", messages.size());
        }
        
        Queue<Exchange> answer = new LinkedList<>();
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
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
            // add current index and total as properties
            exchange.setProperty(Exchange.BATCH_INDEX, index);
            exchange.setProperty(Exchange.BATCH_SIZE, total);
            exchange.setProperty(Exchange.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            // schedule task to extend visibility if enabled
            Integer visibilityTimeout = getConfiguration().getVisibilityTimeout();
            if (this.scheduledExecutor != null && visibilityTimeout != null && (visibilityTimeout.intValue() / 2) > 0) {
                int delay = visibilityTimeout.intValue() / 2;
                int period = visibilityTimeout.intValue();
                int repeatSeconds = Double.valueOf(visibilityTimeout.doubleValue() * 1.5).intValue();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduled TimeoutExtender task to start after {} delay, and run with {}/{} period/repeat (seconds), to extend exchangeId: {}",
                            new Object[]{delay, period, repeatSeconds, exchange.getExchangeId()});
                }
                final ScheduledFuture<?> scheduledFuture = this.scheduledExecutor.scheduleAtFixedRate(
                        new TimeoutExtender(exchange, repeatSeconds), delay, period, TimeUnit.SECONDS);
                exchange.addOnCompletion(new Synchronization() {
                    @Override
                    public void onComplete(Exchange exchange) {
                        cancelExtender(exchange);
                    }

                    @Override
                    public void onFailure(Exchange exchange) {
                        cancelExtender(exchange);
                    }

                    private void cancelExtender(Exchange exchange) {
                        // cancel task as we are done
                        LOG.trace("Processing done so cancelling TimeoutExtender task for exchangeId: {}", exchange.getExchangeId());
                        scheduledFuture.cancel(true);
                    }
                });
            }

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

            LOG.trace("Processing exchange [{}]...", exchange);
            getAsyncProcessor().process(exchange, new AsyncCallback() {
                @Override
                public void done(boolean doneSync) {
                    LOG.trace("Processing exchange [{}] done.", exchange);
                }
            });
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

            if (shouldDelete(exchange)) {
                String receiptHandle = exchange.getIn().getHeader(SqsConstants.RECEIPT_HANDLE, String.class);
                DeleteMessageRequest deleteRequest = new DeleteMessageRequest(getQueueUrl(), receiptHandle);

                LOG.trace("Deleting message with receipt handle {}...", receiptHandle);

                getClient().deleteMessage(deleteRequest);

                LOG.trace("Deleted message with receipt handle {}...", receiptHandle);
            }
        } catch (AmazonClientException e) {
            getExceptionHandler().handleException("Error occurred during deleting message. This exception is ignored.", exchange, e);
        }
    }

    private boolean shouldDelete(Exchange exchange) {
        boolean shouldDeleteByFilter = exchange.getProperty(Exchange.FILTER_MATCHED) != null
                && getConfiguration().isDeleteIfFiltered()
                && passedThroughFilter(exchange);

        return getConfiguration().isDeleteAfterRead()
                || shouldDeleteByFilter;
    }

    private boolean passedThroughFilter(Exchange exchange) {
        return exchange.getProperty(Exchange.FILTER_MATCHED, false, Boolean.class);
    }

    /**
     * Strategy when processing the exchange failed.
     *
     * @param exchange the exchange
     */
    protected void processRollback(Exchange exchange) {
        Exception cause = exchange.getException();
        if (cause != null) {
            getExceptionHandler().handleException("Error during processing exchange. Will attempt to process the message on next poll.", exchange, cause);
        }
    }

    protected SqsConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }
    
    protected AmazonSQS getClient() {
        return getEndpoint().getClient();
    }
    
    protected String getQueueUrl() {
        return getEndpoint().getQueueUrl();
    }
    
    @Override
    public SqsEndpoint getEndpoint() {
        return (SqsEndpoint) super.getEndpoint();
    }

    @Override
    public String toString() {
        if (sqsConsumerToString == null) {
            sqsConsumerToString = "SqsConsumer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return sqsConsumerToString;
    }

    @Override
    protected void doStart() throws Exception {
        // start scheduler first
        if (getConfiguration().isExtendMessageVisibility() && scheduledExecutor == null) {
            this.scheduledExecutor = getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "SqsTimeoutExtender");
        }

        super.doStart();
    }

    @Override
    protected void doShutdown() throws Exception {
        if (scheduledExecutor != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(scheduledExecutor);
            scheduledExecutor = null;
        }

        super.doShutdown();
    }

    private class TimeoutExtender implements Runnable {

        private final Exchange exchange;
        private final int repeatSeconds;

        TimeoutExtender(Exchange exchange, int repeatSeconds) {
            this.exchange = exchange;
            this.repeatSeconds = repeatSeconds;
        }

        @Override
        public void run() {
            ChangeMessageVisibilityRequest request = new ChangeMessageVisibilityRequest(getQueueUrl(),
                    exchange.getIn().getHeader(SqsConstants.RECEIPT_HANDLE, String.class), repeatSeconds);

            try {
                LOG.trace("Extending visibility window by {} seconds for exchange {}", this.repeatSeconds, this.exchange);
                getEndpoint().getClient().changeMessageVisibility(request);
                LOG.debug("Extended visibility window by {} seconds for exchange {}", this.repeatSeconds, this.exchange);
            } catch (ReceiptHandleIsInvalidException e) {
                // Ignore.
            } catch (MessageNotInflightException e) {
                // Ignore.
            } catch (Exception e) {
                LOG.warn("Extending visibility window failed for exchange " + exchange
                        + ". Will not attempt to extend visibility further. This exception will be ignored.", e);
            }
        }
    }

}
