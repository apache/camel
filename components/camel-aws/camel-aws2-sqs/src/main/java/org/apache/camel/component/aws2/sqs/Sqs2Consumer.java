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

import java.io.Closeable;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.clock.Clock;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.ScheduledPollConsumerScheduler;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.DefaultScheduledPollConsumerScheduler;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.commons.io.function.IOConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDeletedRecentlyException;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Comparator.comparing;

/**
 * A Consumer of messages from the Amazon Web Service Simple Queue Service <a href="http://aws.amazon.com/sqs/">AWS
 * SQS</a>
 */
public class Sqs2Consumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(Sqs2Consumer.class);

    private TimeoutExtender timeoutExtender;
    private ScheduledFuture<?> scheduledFuture;
    private ScheduledExecutorService scheduledExecutor;
    private PollingTask pollingTask;
    private final String sqsConsumerToString;

    public Sqs2Consumer(Sqs2Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
        sqsConsumerToString = "SqsConsumer[%s]".formatted(URISupport.sanitizeUri(endpoint.getEndpointUri()));
    }

    @Override
    protected int poll() throws Exception {
        // must reset for each poll
        shutdownRunningTask = null;
        pendingExchanges = 0;

        List<software.amazon.awssdk.services.sqs.model.Message> messages = pollingTask.call();
        // okay we have some response from aws so lets mark the consumer as
        // ready
        forceConsumerAsReady();

        Queue<Exchange> exchanges = createExchanges(messages);
        return processBatch(CastUtils.cast(exchanges));
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
            try {
                Boolean a = getAsyncProcessor().process(exchange, cb);
            } catch (Error e) {
                LOG.debug("Error processing exchange, stopping exchange from extending its visibility", e);
                if (timeoutExtender != null && timeoutExtender.entries != null) {
                    timeoutExtender.entries.remove(exchange.getExchangeId());
                }
                throw e;
            }
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

    private static boolean passedThroughFilter(Exchange exchange) {
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
        return sqsConsumerToString;
    }

    @Override
    protected void afterConfigureScheduler(ScheduledPollConsumerScheduler scheduler, boolean newScheduler) {
        if (newScheduler && scheduler instanceof DefaultScheduledPollConsumerScheduler defaultScheduledPollConsumerScheduler) {
            defaultScheduledPollConsumerScheduler.setConcurrentConsumers(getConfiguration().getConcurrentConsumers());
            // if using concurrent consumers then resize pool to be at least
            // same size
            int poolSize = Math.max(defaultScheduledPollConsumerScheduler.getPoolSize(),
                    getConfiguration().getConcurrentConsumers());
            defaultScheduledPollConsumerScheduler.setPoolSize(poolSize);
        }
    }

    @Override
    protected void doStart() throws Exception {
        pollingTask = new PollingTask(getEndpoint());
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
                int delay = Math.max(1, visibilityTimeout / 2);
                this.timeoutExtender = new TimeoutExtender(visibilityTimeout, delay);

                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                            "Scheduled TimeoutExtender task to start after {} delay, and run with {}/{} delay/repeat (seconds)",
                            delay, delay, visibilityTimeout);
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
        if (pollingTask != null) {
            pollingTask.close();
            pollingTask = null;
        }

        super.doShutdown();
    }

    private class TimeoutExtender implements Runnable {

        private static final String RECEIPT_HANDLE_IS_INVALID = "ReceiptHandleIsInvalid";
        private static final int MAX_REQUESTS = 10;
        private final int visibilityTimeout;
        private final int delayBetweenExecutions;
        private final AtomicBoolean run = new AtomicBoolean(true);
        private final Map<String, TimeoutExtenderEntry> entries = new ConcurrentHashMap<>();

        TimeoutExtender(int visibilityTimeout, int delayBetweenExecutions) {
            this.visibilityTimeout = visibilityTimeout;
            this.delayBetweenExecutions = delayBetweenExecutions;
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
                    LOG.trace("Removing exchangeId {} from the TimeoutExtender, processing done", exchange.getExchangeId());
                    entries.remove(exchange.getExchangeId());
                }
            });

            ChangeMessageVisibilityBatchRequestEntry entry = ChangeMessageVisibilityBatchRequestEntry.builder()
                    .id(exchange.getExchangeId()).visibilityTimeout(visibilityTimeout)
                    .receiptHandle(exchange.getIn().getHeader(Sqs2Constants.RECEIPT_HANDLE, String.class)).build();

            entries.put(exchange.getExchangeId(), new TimeoutExtenderEntry(entry));
        }

        public void cancel() {
            // cancel by setting to no longer run
            run.set(false);
        }

        @Override
        public void run() {
            if (run.get()) {
                final Instant nextExpectedExecution = Instant.now().plusSeconds(Math.max(1, delayBetweenExecutions));

                final Queue<TimeoutExtenderEntry> entryQueue = new LinkedList<>(entries.values());

                while (!entryQueue.isEmpty()) {
                    List<ChangeMessageVisibilityBatchRequestEntry> batchEntries = new ArrayList<>();
                    // up to 10 requests can be sent with each
                    // ChangeMessageVisibilityBatch action
                    while (!entryQueue.isEmpty() && batchEntries.size() < MAX_REQUESTS) {
                        TimeoutExtenderEntry nextEntry = entryQueue.poll();
                        if (nextEntry.isDeadlineReachedAt(nextExpectedExecution)) {
                            batchEntries.add(nextEntry.extendRequest);
                        }
                    }
                    if (!batchEntries.isEmpty()) {
                        ChangeMessageVisibilityBatchRequest request = ChangeMessageVisibilityBatchRequest.builder()
                                .queueUrl(getQueueUrl()).entries(batchEntries).build();

                        try {
                            LOG.trace("Extending visibility window by {} seconds for request entries: {}", visibilityTimeout,
                                    batchEntries);
                            ChangeMessageVisibilityBatchResponse br
                                    = getEndpoint().getClient().changeMessageVisibilityBatch(request);
                            if (br.hasFailed()) {
                                br.failed().forEach(failedEntry -> {
                                    if (failedEntry.code().equals(RECEIPT_HANDLE_IS_INVALID)) {
                                        LOG.debug("Extended visibility window for request entry failed with invalid handle.",
                                                br.failed());
                                    } else {
                                        LOG.warn("Extended visibility window for request entry failed: {}", br.failed());
                                    }
                                });
                            }
                            if (br.hasSuccessful()) {
                                br.successful().forEach(successEntry -> {
                                    LOG.debug("Extended visibility window for request entry: {}", successEntry.id());
                                    entries.computeIfPresent(successEntry.id(), (t, u) -> u.extendDeadline());
                                });
                            }
                        } catch (SdkException e) {
                            logException(e, batchEntries);
                        }
                    }
                }
            }
        }

        private void logException(Exception e, List<ChangeMessageVisibilityBatchRequestEntry> entries) {
            LOG.warn(
                    "Extending visibility window failed for entries {}. Will not attempt to extend visibility further. This exception will be ignored.",
                    entries, e);
        }

        private final class TimeoutExtenderEntry {

            /**
             * Should be extended before this deadline is reached
             */
            private final Instant deadline;

            /**
             * The entry send to AWS for extending the message visibility
             */
            private final ChangeMessageVisibilityBatchRequestEntry extendRequest;

            TimeoutExtenderEntry(ChangeMessageVisibilityBatchRequestEntry extendRequest) {

                // setting deadline to 80% of now until expected visibility
                // timeout, this is for taking into account processing time
                this.deadline = Instant.now().plusMillis(extendRequest.visibilityTimeout() * 800);
                this.extendRequest = extendRequest;
            }

            TimeoutExtenderEntry extendDeadline() {
                return new TimeoutExtenderEntry(extendRequest);
            }

            boolean isDeadlineReachedAt(Instant time) {
                return deadline.isBefore(time);
            }
        }
    }

    /**
     * Task responsible for polling the messages from Amazon SQS server.
     * <p />
     * Depending on the configuration, the polling may involve sending one or more receive requests in a single task
     * call. The number of send requests depends on the {@link Sqs2Endpoint#getMaxMessagesPerPoll()} configuration. The
     * Amazon SQS receive API has upper limit of maximum 10 messages that can be fetched with a single request. To
     * enable handling greater number of messages fetched per poll, multiple requests are being send asynchronously and
     * then joined together.
     * <p />
     * To preserver the ordering, an optional {@link Sqs2Configuration#getSortAttributeName()} can be configured. When
     * specified, all messages collected from the concurrent requests are being sorted using this attribute.
     * <p />
     * In addition to that, the task is also responsible for handling auto-creation of the SQS queue, when its missing.
     * The queue is created when receive request returns an error about the missing queue and the
     * {@link Sqs2Configuration#isAutoCreateQueue()} is enabled. In such case, the queue will be created and the task
     * will return empty list of messages.
     * <p />
     * If the queue creation fails with an error related to recently deleted queue, the queue creation will be postponed
     * for at least 30 seconds. To prevent task from blocking the consumer thread, the 30 second timeout is being
     * checked in each task call. If the scheduled time for queue auto-creation was not reached yet, the task will
     * simply return empty list of messages. Once the scheduled time is reached, another queue creation attempt will be
     * made.
     */
    private static class PollingTask implements Callable<List<software.amazon.awssdk.services.sqs.model.Message>>, Closeable {
        /**
         * The maximum number of messages that can be requested in a single request to AWS SQS.
         */
        private static final int MAX_NUMBER_OF_MESSAGES_PER_REQUEST = 10;

        /**
         * The time to wait before re-creating recently deleted queue.
         */
        private static final long RECENTLY_DELETED_QUEUE_BACKOFF_TIME_MS = 30_000L;

        private static final Pattern COMMA_SEPARATED_PATTERN = Pattern.compile(",");

        /**
         * A scheduled time for queue auto-creation, measured with {@link Clock#elapsed()} value. The value of
         *
         * <pre>
         * 0
         * </pre>
         *
         * means there is no schedule.
         */
        private final AtomicLong queueAutoCreationScheduleTime = new AtomicLong(0L);
        private final Lock lock = new ReentrantLock();
        private final AtomicBoolean closed = new AtomicBoolean();

        private final Clock clock;
        private final SqsClient sqsClient;
        private final ExecutorService requestExecutor;
        private final ExecutorServiceManager executorServiceManager;
        private final IOConsumer<SqsClient> createQueueOperation;

        private final String queueName;
        private final String queueUrl;
        private final int maxMessagesPerPoll;
        private final Integer visibilityTimeout;
        private final Integer waitTimeSeconds;
        private final Collection<MessageSystemAttributeName> attributeNames;
        private final Collection<String> messageAttributeNames;
        private final int numberOfRequestsPerPoll;
        private final boolean queueAutoCreationEnabled;
        private final MessageSystemAttributeName sortAttributeName;

        @SuppressWarnings("resource")
        private PollingTask(Sqs2Endpoint endpoint) {
            clock = endpoint.getClock();
            sqsClient = endpoint.getClient();
            executorServiceManager = endpoint.getCamelContext().getExecutorServiceManager();
            createQueueOperation = endpoint::createQueue;

            queueName = endpoint.getConfiguration().getQueueName();
            queueUrl = endpoint.getQueueUrl();
            visibilityTimeout = endpoint.getConfiguration().getVisibilityTimeout();
            waitTimeSeconds = endpoint.getConfiguration().getWaitTimeSeconds();
            messageAttributeNames = splitCommaSeparatedValues(endpoint.getConfiguration().getMessageAttributeNames());
            sortAttributeName = getSortAttributeName(endpoint.getConfiguration());
            attributeNames = getAttributeNames(endpoint.getConfiguration(), sortAttributeName);
            queueAutoCreationEnabled = endpoint.getConfiguration().isAutoCreateQueue();
            maxMessagesPerPoll = Math.max(1, endpoint.getMaxMessagesPerPoll());
            numberOfRequestsPerPoll = computeNumberOfRequestPerPoll(maxMessagesPerPoll);
            requestExecutor = executorServiceManager.newFixedThreadPool(this,
                    "%s[%s]".formatted(getClass().getSimpleName(), queueName),
                    Math.min(numberOfRequestsPerPoll, Math.max(1, endpoint.getConfiguration().getConcurrentRequestLimit())));
        }

        @Override
        public void close() {
            closed.set(true);
            executorServiceManager.shutdownNow(requestExecutor);
        }

        @Override
        public List<software.amazon.awssdk.services.sqs.model.Message> call() throws IOException {
            if (isClosed() || processScheduledQueueAutoCreation()) {
                return emptyList();
            }

            final PollingContext context = new PollingContext();
            final List<software.amazon.awssdk.services.sqs.model.Message> messages = poll(context);
            if (context.errorCount() == numberOfRequestsPerPoll) {
                if (context.errorCount() == 1) {
                    context.rethrowIfFirstErrorIsRuntimeException();
                    throw new IOException("Error while polling", context.firstError());
                }
                throw new IOException(
                        ("Error while polling - all %s requests resulted in an error, "
                         + "please check the logs for more details")
                                .formatted(numberOfRequestsPerPoll));
            }
            return messages;
        }

        private List<software.amazon.awssdk.services.sqs.model.Message> poll(final PollingContext pollContext)
                throws IOException {
            if (numberOfRequestsPerPoll == 1) {
                return poll(maxMessagesPerPoll, pollContext);
            }
            int remaining = maxMessagesPerPoll;
            try {
                CompletableFuture<List<software.amazon.awssdk.services.sqs.model.Message>> future
                        = CompletableFuture.completedFuture(emptyList());
                while (remaining > 0) {
                    int numberOfMessages = Math.min(remaining, MAX_NUMBER_OF_MESSAGES_PER_REQUEST);
                    future = mergeResults(future,
                            CompletableFuture.supplyAsync(() -> poll(numberOfMessages, pollContext), requestExecutor));
                    remaining -= MAX_NUMBER_OF_MESSAGES_PER_REQUEST;
                }
                return future.thenApply(this::sortIfNeeded).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.debug("Polling interrupted", e);
                return emptyList();
            } catch (ExecutionException e) {
                throw new IOException("Error while polling", e.getCause());
            }
        }

        private List<software.amazon.awssdk.services.sqs.model.Message> poll(int maxNumberOfMessages, PollingContext context) {
            if (context.isQueueMissing()) {
                // if one of the request encountered a missing queue error the
                // remaining requests
                // should be ignored, even if the queue is automatically created
                // it will be empty
                // so there is no reason for immediate polling after creation
                return emptyList();
            }
            try {
                return sqsClient.receiveMessage(createReceiveRequest(maxNumberOfMessages)).messages();
            } catch (QueueDoesNotExistException e) {
                return handleMissingQueueError(context, e);
            } catch (Exception e) {
                LOG.error("Error while polling", e);
                context.firePollingError(e);
                return emptyList();
            }
        }

        private List<software.amazon.awssdk.services.sqs.model.Message> handleMissingQueueError(
                PollingContext context, QueueDoesNotExistException error) {
            if (context.isQueueMissing()) {
                // if the context is flagged with missing queue
                // it means another thread is handling the error
                return emptyList();
            }
            final UUID requestId = UUID.randomUUID();
            context.fireQueueMissing(requestId);
            if (queueAutoCreationEnabled) {
                createQueue(requestId, context);
                return emptyList();
            }
            LOG.error("Error while polling {} queue does not exists", queueName, error);
            context.firePollingError(error);
            return emptyList();
        }

        private ReceiveMessageRequest createReceiveRequest(int maxNumberOfMessages) {
            ReceiveMessageRequest.Builder requestBuilder
                    = ReceiveMessageRequest.builder().queueUrl(queueUrl).maxNumberOfMessages(maxNumberOfMessages)
                            .visibilityTimeout(visibilityTimeout).waitTimeSeconds(waitTimeSeconds);
            if (!attributeNames.isEmpty()) {
                requestBuilder.messageSystemAttributeNames(attributeNames);
            }
            if (!messageAttributeNames.isEmpty()) {
                requestBuilder.messageAttributeNames(messageAttributeNames);
            }
            LOG.trace("Receiving messages with request [{}]...", requestBuilder);
            return requestBuilder.build();
        }

        private void createQueue(UUID requestId, PollingContext context) {
            lock.lock();
            try {
                if (isClosed() || context.isMissingQueueHandledInAnotherRequest(requestId)) {
                    // the missing queue error can be thrown by multiple threads
                    // the first thread that is handling the error should
                    // prevent other threads
                    // from repeating the logic
                    // as the operation is synchronized, the other threads
                    // should wait and then
                    // check if it wasn't handled already
                    return;
                }
                try {
                    createQueueOperation.accept(sqsClient);
                } catch (QueueDeletedRecentlyException e) {
                    LOG.debug("Queue recently deleted, will retry after at least 30 seconds on next polling request.", e);
                    scheduleQueueAutoCreation();
                } catch (Exception e) {
                    LOG.error("Error while creating queue.", e);
                    context.firePollingError(e);
                }
            } finally {
                lock.unlock();
            }
        }

        private boolean processScheduledQueueAutoCreation() throws IOException {
            long scheduleTimeMs = queueAutoCreationScheduleTime.get();
            if (scheduleTimeMs == 0) {
                // queue creation is not scheduled - ignoring
                return false;
            }
            long elapsedTimeMillis = clock.elapsed();
            if (scheduleTimeMs > elapsedTimeMillis) {
                LOG.debug("{}ms remaining until queue auto-creation is triggered", scheduleTimeMs - elapsedTimeMillis);
                return true;
            }
            final PollingContext context = new PollingContext();
            createQueue(UUID.randomUUID(), context);
            if (context.hasErrors()) {
                context.rethrowIfFirstErrorIsRuntimeException();
                throw new IOException("Error while creating %s queue".formatted(queueName), context.firstError());
            }
            cancelScheduledQueueAutoCreation();
            return true;
        }

        private void scheduleQueueAutoCreation() {
            queueAutoCreationScheduleTime.set(clock.elapsed() + RECENTLY_DELETED_QUEUE_BACKOFF_TIME_MS);
        }

        private void cancelScheduledQueueAutoCreation() {
            queueAutoCreationScheduleTime.set(0);
        }

        private boolean isClosed() {
            return closed.get();
        }

        private static List<String> splitCommaSeparatedValues(String value) {
            if (value == null || value.isEmpty()) {
                return emptyList();
            }
            return COMMA_SEPARATED_PATTERN.splitAsStream(value).map(String::trim).filter(it -> !it.isEmpty()).toList();
        }

        private static Optional<MessageSystemAttributeName> parseMessageSystemAttributeName(String attribute) {
            if (attribute == null || attribute.isEmpty()) {
                return Optional.empty();
            }
            MessageSystemAttributeName result = MessageSystemAttributeName.fromValue(attribute);
            if (result == MessageSystemAttributeName.UNKNOWN_TO_SDK_VERSION) {
                LOG.warn("Unsupported attribute name '{}' use one of {}", attribute, MessageSystemAttributeName.knownValues());
                return Optional.empty();
            }
            return Optional.of(result);
        }

        private static MessageSystemAttributeName getSortAttributeName(Sqs2Configuration configuration) {
            return parseMessageSystemAttributeName(configuration.getSortAttributeName()).filter(attribute -> {
                if (attribute == MessageSystemAttributeName.ALL) {
                    LOG.warn("The {} attribute cannot be used for sorting the received messages",
                            MessageSystemAttributeName.ALL);
                    return false;
                }
                return true;
            }).orElse(null);
        }

        private static List<MessageSystemAttributeName> getAttributeNames(
                Sqs2Configuration configuration, MessageSystemAttributeName sortAttributeName) {
            List<MessageSystemAttributeName> result = new ArrayList<>();
            for (String attributeName : splitCommaSeparatedValues(configuration.getAttributeNames())) {
                parseMessageSystemAttributeName(attributeName).filter(it -> !result.contains(it)).ifPresent(result::add);
            }
            if (sortAttributeName != null && !result.contains(MessageSystemAttributeName.ALL)
                    && !result.contains(sortAttributeName)) {
                result.add(sortAttributeName);
            }
            return unmodifiableList(result);
        }

        private static int computeNumberOfRequestPerPoll(int maxMessagesPerPoll) {
            return (int) Math.ceil((double) Math.max(1, maxMessagesPerPoll) / MAX_NUMBER_OF_MESSAGES_PER_REQUEST);
        }

        private static <T> CompletableFuture<List<T>> mergeResults(
                CompletableFuture<List<T>> future1, CompletableFuture<List<T>> future2) {
            return future1.thenCombine(future2, (messages1, messages2) -> {
                final List<T> allMessages = new ArrayList<>(messages1);
                allMessages.addAll(messages2);
                return allMessages;
            });
        }

        private List<software.amazon.awssdk.services.sqs.model.Message> sortIfNeeded(
                List<software.amazon.awssdk.services.sqs.model.Message> messages) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Received {} messages in {} requests", messages.size(), numberOfRequestsPerPoll);
            }
            if (sortAttributeName != null) {
                return messages.stream().sorted(comparing(message -> message.attributes().getOrDefault(sortAttributeName, "")))
                        .toList();
            }
            return messages;
        }

    }

    private record PollingContext(AtomicReference<UUID> missingQueueHandlerRequestId, Queue<Exception> errors) {
        private PollingContext() {
            this(new AtomicReference<>(), new ConcurrentLinkedQueue<>());
        }

        PollingContext {
            Objects.requireNonNull(missingQueueHandlerRequestId);
            Objects.requireNonNull(errors);
        }

        private void fireQueueMissing(UUID requestId) {
            missingQueueHandlerRequestId.compareAndSet(null, requestId);
        }

        private void firePollingError(Exception error) {
            errors.offer(error);
        }

        private boolean isQueueMissing() {
            return missingQueueHandlerRequestId.get() != null;
        }

        private boolean isMissingQueueHandledInAnotherRequest(UUID requestId) {
            UUID handlingRequestId = missingQueueHandlerRequestId.get();
            return handlingRequestId != null && !requestId.equals(handlingRequestId);
        }

        private boolean hasErrors() {
            return !errors.isEmpty();
        }

        private int errorCount() {
            return errors.size();
        }

        private Exception firstError() {
            return errors.peek();
        }

        private void rethrowIfFirstErrorIsRuntimeException() {
            if (firstError() instanceof RuntimeException runtimeError) {
                throw runtimeError;
            }
        }
    }
}
