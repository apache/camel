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
package org.apache.camel.component.aws2.sqs;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.ScheduledPollConsumerScheduler;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.DefaultScheduledPollConsumerScheduler;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageNotInflightException;
import software.amazon.awssdk.services.sqs.model.QueueDeletedRecentlyException;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiptHandleIsInvalidException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

/**
 * A Consumer of messages from the Amazon Web Service Simple Queue Service <a href="http://aws.amazon.com/sqs/">AWS
 * SQS</a>
 */
public class Sqs2Consumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(Sqs2Consumer.class);

    private TimeoutExtender timeoutExtender;
    private ScheduledFuture<?> scheduledFuture;
    private ScheduledExecutorService scheduledExecutor;
    private transient String sqsConsumerToString;
    private Collection<String> attributeNames;
    private Collection<String> messageAttributeNames;

    public Sqs2Consumer(Sqs2Endpoint endpoint, Processor processor) {
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

        ReceiveMessageRequest.Builder request = ReceiveMessageRequest.builder().queueUrl(getQueueUrl());
        request.maxNumberOfMessages(getMaxMessagesPerPoll() > 0 ? getMaxMessagesPerPoll() : null);
        request.visibilityTimeout(getConfiguration().getVisibilityTimeout());
        request.waitTimeSeconds(getConfiguration().getWaitTimeSeconds());

        if (attributeNames != null) {
            request.attributeNamesWithStrings(attributeNames);
        }
        if (messageAttributeNames != null) {
            request.messageAttributeNames(messageAttributeNames);
        }

        LOG.trace("Receiving messages with request [{}]...", request);

        ReceiveMessageResponse messageResult;
        ReceiveMessageRequest requestBuild = request.build();
        try {
            messageResult = getClient().receiveMessage(requestBuild);
        } catch (QueueDoesNotExistException e) {
            LOG.info("Queue does not exist....recreating now...");
            reConnectToQueue();
            messageResult = getClient().receiveMessage(requestBuild);
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Received {} messages", messageResult.messages().size());
        }

        // okay we have some response from aws so lets mark the consumer as ready
        forceConsumerAsReady();

        Queue<Exchange> exchanges = createExchanges(messageResult.messages());
        return processBatch(CastUtils.cast(exchanges));
    }

    public void reConnectToQueue() {
        try {
            if (getEndpoint().getConfiguration().isAutoCreateQueue()) {
                getEndpoint().createQueue(getClient());
            }
        } catch (QueueDeletedRecentlyException qdr) {
            LOG.debug("Queue recently deleted, will retry in 30 seconds.");
            try {
                Thread.sleep(30000);
                getEndpoint().createQueue(getClient());
            } catch (InterruptedException e) {
                LOG.warn("Interrupted while retrying queue connection.", e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOG.warn("failed to retry queue connection.", e);
            }
        } catch (Exception e) {
            LOG.warn("Could not connect to queue in amazon.", e);
        }
    }

    protected Queue<Exchange> createExchanges(List<software.amazon.awssdk.services.sqs.model.Message> messages) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Received {} messages in this poll", messages.size());
        }

        Queue<Exchange> answer = new LinkedList<>();
        for (software.amazon.awssdk.services.sqs.model.Message message : messages) {
            Exchange exchange = createExchange(message);
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
            exchange.setProperty(ExchangePropertyKey.BATCH_INDEX, index);
            exchange.setProperty(ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(ExchangePropertyKey.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            if (this.timeoutExtender != null) {
                timeoutExtender.add(exchange);
            }

            // add on completion to handle after work when the exchange is done
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
                    return "SqsConsumerOnCompletion";
                }
            });

            // use default consumer callback
            AsyncCallback cb = defaultConsumerCallback(exchange, true);
            getAsyncProcessor().process(exchange, cb);
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
                String receiptHandle = exchange.getIn().getHeader(Sqs2Constants.RECEIPT_HANDLE, String.class);
                DeleteMessageRequest.Builder deleteRequest
                        = DeleteMessageRequest.builder().queueUrl(getQueueUrl()).receiptHandle(receiptHandle);

                LOG.trace("Deleting message with receipt handle {}...", receiptHandle);

                getClient().deleteMessage(deleteRequest.build());

                LOG.trace("Deleted message with receipt handle {}...", receiptHandle);
            }
        } catch (SdkException e) {
            getExceptionHandler().handleException("Error occurred during deleting message. This exception is ignored.",
                    exchange, e);
        }
    }

    private boolean shouldDelete(Exchange exchange) {
        boolean shouldDeleteByFilter = exchange.getProperty(Sqs2Constants.SQS_DELETE_FILTERED) != null
                && getConfiguration().isDeleteIfFiltered() && passedThroughFilter(exchange);

        return getConfiguration().isDeleteAfterRead() || shouldDeleteByFilter;
    }

    private boolean passedThroughFilter(Exchange exchange) {
        return exchange.getProperty(Sqs2Constants.SQS_DELETE_FILTERED, false, Boolean.class);
    }

    /**
     * Strategy when processing the exchange failed.
     *
     * @param exchange the exchange
     */
    protected void processRollback(Exchange exchange) {
        Exception cause = exchange.getException();
        if (cause != null) {
            getExceptionHandler().handleException(
                    "Error during processing exchange. Will attempt to process the message on next poll.", exchange, cause);
        }
    }

    protected Sqs2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    protected SqsClient getClient() {
        return getEndpoint().getClient();
    }

    protected String getQueueUrl() {
        return getEndpoint().getQueueUrl();
    }

    @Override
    public Sqs2Endpoint getEndpoint() {
        return (Sqs2Endpoint) super.getEndpoint();
    }

    public Exchange createExchange(software.amazon.awssdk.services.sqs.model.Message msg) {
        return createExchange(getEndpoint().getExchangePattern(), msg);
    }

    private Exchange createExchange(ExchangePattern pattern, software.amazon.awssdk.services.sqs.model.Message msg) {
        Exchange exchange = createExchange(true);
        exchange.setPattern(pattern);
        Message message = exchange.getIn();
        message.setBody(msg.body());
        message.setHeaders(new HashMap<>(msg.attributesAsStrings()));
        message.setHeader(Sqs2Constants.MESSAGE_ID, msg.messageId());
        message.setHeader(Sqs2Constants.MD5_OF_BODY, msg.md5OfBody());
        message.setHeader(Sqs2Constants.RECEIPT_HANDLE, msg.receiptHandle());
        message.setHeader(Sqs2Constants.ATTRIBUTES, msg.attributes());
        message.setHeader(Sqs2Constants.MESSAGE_ATTRIBUTES, msg.messageAttributes());

        // Need to apply the SqsHeaderFilterStrategy this time
        HeaderFilterStrategy headerFilterStrategy = getEndpoint().getHeaderFilterStrategy();
        // add all sqs message attributes as camel message headers so that
        // knowledge of the Sqs class MessageAttributeValue will not leak to the
        // client
        for (Map.Entry<String, MessageAttributeValue> entry : msg.messageAttributes().entrySet()) {
            String header = entry.getKey();
            Object value = Sqs2MessageHelper.fromMessageAttributeValue(entry.getValue());
            if (!headerFilterStrategy.applyFilterToExternalHeaders(header, value, exchange)) {
                message.setHeader(header, value);
            }
        }
        return exchange;
    }

    @Override
    public String toString() {
        if (sqsConsumerToString == null) {
            sqsConsumerToString = "SqsConsumer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return sqsConsumerToString;
    }

    @Override
    protected void afterConfigureScheduler(ScheduledPollConsumerScheduler scheduler, boolean newScheduler) {
        if (newScheduler && scheduler instanceof DefaultScheduledPollConsumerScheduler) {
            DefaultScheduledPollConsumerScheduler ds = (DefaultScheduledPollConsumerScheduler) scheduler;
            ds.setConcurrentConsumers(getConfiguration().getConcurrentConsumers());
            // if using concurrent consumers then resize pool to be at least
            // same size
            int ps = Math.max(ds.getPoolSize(), getConfiguration().getConcurrentConsumers());
            ds.setPoolSize(ps);
        }
    }

    @Override
    protected void doStart() throws Exception {
        // start scheduler first
        if (getConfiguration().isExtendMessageVisibility() && scheduledExecutor == null) {
            ThreadPoolProfile profile = new ThreadPoolProfile("SqsTimeoutExtender");
            profile.setPoolSize(1);
            profile.setAllowCoreThreadTimeOut(false);
            // the max queue is set to be unbound as there is no way to register
            // the required size. If using the Thread EIP, then the max queue
            // size is equal to maxQueueSize of the consumer thread EIP+max
            // thread count+consumer-thread.
            // The consumer would block when this limit was reached. It is safe
            // to set this queue to unbound as it will be limited by the
            // consumer.
            profile.setMaxQueueSize(-1);

            this.scheduledExecutor = getEndpoint().getCamelContext().getExecutorServiceManager().newScheduledThreadPool(this,
                    "SqsTimeoutExtender", profile);

            Integer visibilityTimeout = getConfiguration().getVisibilityTimeout();

            if (visibilityTimeout != null && visibilityTimeout > 0) {
                int delay = visibilityTimeout;
                int repeatSeconds = (int) (visibilityTimeout.doubleValue() * 1.5);
                this.timeoutExtender = new TimeoutExtender(repeatSeconds);

                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                            "Scheduled TimeoutExtender task to start after {} delay, and run with {}/{} period/repeat (seconds)",
                            delay, delay, repeatSeconds);
                }
                this.scheduledFuture
                        = scheduledExecutor.scheduleAtFixedRate(this.timeoutExtender, delay, delay, TimeUnit.SECONDS);
            }
        }

        super.doStart();
    }

    @Override
    protected void doShutdown() throws Exception {
        if (timeoutExtender != null) {
            timeoutExtender.cancel();
            timeoutExtender = null;
        }

        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
            scheduledFuture = null;
        }

        if (scheduledExecutor != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(scheduledExecutor);
            scheduledExecutor = null;
        }

        super.doShutdown();
    }

    private class TimeoutExtender implements Runnable {

        private static final int MAX_REQUESTS = 10;
        private final int repeatSeconds;
        private final AtomicBoolean run = new AtomicBoolean(true);
        private final Map<String, ChangeMessageVisibilityBatchRequestEntry> entries = new ConcurrentHashMap<>();

        TimeoutExtender(int repeatSeconds) {
            this.repeatSeconds = repeatSeconds;
        }

        public void add(Exchange exchange) {
            exchange.getExchangeExtension().addOnCompletion(new Synchronization() {
                @Override
                public void onComplete(Exchange exchange) {
                    remove(exchange);
                }

                @Override
                public void onFailure(Exchange exchange) {
                    remove(exchange);
                }

                private void remove(Exchange exchange) {
                    LOG.trace("Removing exchangeId {} from the TimeoutExtender, processing done",
                            exchange.getExchangeId());
                    entries.remove(exchange.getExchangeId());
                }
            });

            ChangeMessageVisibilityBatchRequestEntry entry
                    = ChangeMessageVisibilityBatchRequestEntry.builder()
                            .id(exchange.getExchangeId()).visibilityTimeout(repeatSeconds)
                            .receiptHandle(exchange.getIn().getHeader(Sqs2Constants.RECEIPT_HANDLE, String.class))
                            .build();

            entries.put(exchange.getExchangeId(), entry);
        }

        public void cancel() {
            // cancel by setting to no longer run
            run.set(false);
        }

        @Override
        public void run() {
            if (run.get()) {
                Queue<ChangeMessageVisibilityBatchRequestEntry> entryQueue = new LinkedList<>(entries.values());

                while (!entryQueue.isEmpty()) {
                    List<ChangeMessageVisibilityBatchRequestEntry> batchEntries = new LinkedList<>();
                    // up to 10 requests can be sent with each ChangeMessageVisibilityBatch action
                    while (!entryQueue.isEmpty() && batchEntries.size() < MAX_REQUESTS) {
                        batchEntries.add(entryQueue.poll());
                    }

                    ChangeMessageVisibilityBatchRequest request
                            = ChangeMessageVisibilityBatchRequest.builder().queueUrl(getQueueUrl()).entries(batchEntries)
                                    .build();

                    try {
                        LOG.trace("Extending visibility window by {} seconds for request entries {}", repeatSeconds,
                                batchEntries);
                        getEndpoint().getClient().changeMessageVisibilityBatch(request);
                        LOG.debug("Extended visibility window for request entries {}", batchEntries);
                    } catch (MessageNotInflightException | ReceiptHandleIsInvalidException e) {
                        // Ignore.
                    } catch (SqsException e) {
                        if (e.getMessage()
                                .contains("Message does not exist or is not available for visibility timeout change")) {
                            // Ignore.
                        } else {
                            logException(e, batchEntries);
                        }
                    } catch (Exception e) {
                        logException(e, batchEntries);
                    }
                }
            }
        }

        private void logException(Exception e, List<ChangeMessageVisibilityBatchRequestEntry> entries) {
            LOG.warn("Extending visibility window failed for entries {}"
                     + ". Will not attempt to extend visibility further. This exception will be ignored.",
                    entries, e);
        }
    }

}
