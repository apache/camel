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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.apache.camel.clock.Clock;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.commons.io.function.IOConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDeletedRecentlyException;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;

final class Sqs2PollingClient {
    private static final Logger LOG = LoggerFactory.getLogger(Sqs2PollingClient.class);

    /**
     * The maximum number of messages that can be requested in a single request to AWS SQS.
     */
    private static final int MAX_NUMBER_OF_MESSAGES_PER_REQUEST = 10;

    /**
     * The time to wait before re-creating recently deleted queue.
     */
    private static final long RECENTLY_DELETED_QUEUE_BACKOFF_TIME_MS = 30_000L;

    private static final Pattern COMMA_SEPARATED_PATTERN = Pattern.compile(",");

    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong queueAutoCreationScheduleTime = new AtomicLong(0L);
    private final Object mutex = new Object();

    private final SqsClient sqsClient;
    private final IOConsumer<SqsClient> createQueueOperation;
    private final ExecutorService executor;
    private final Clock clock;
    private final ExecutorServiceManager executorServiceManager;

    private final String queueName;
    private final String queueUrl;
    private final int maxNumberOfMessages;
    private final Integer visibilityTimeout;
    private final Integer waitTimeSeconds;
    private final Collection<String> attributeNames;
    private final Collection<String> messageAttributeNames;

    private final int numberOfRequestsPerPoll;
    private final boolean queueAutoCreationEnabled;

    @SuppressWarnings("resource")
    Sqs2PollingClient(Sqs2Endpoint endpoint) {
        this(endpoint.getClient(), endpoint.getMaxMessagesPerPoll(), endpoint.getConfiguration(), endpoint::createQueue,
             endpoint.getCamelContext().getClock(), endpoint.getCamelContext().getExecutorServiceManager());
    }

    Sqs2PollingClient(SqsClient sqsClient, int maxNumberOfMessages, Sqs2Configuration configuration,
                      IOConsumer<SqsClient> createQueueOperation, Clock clock,
                      ExecutorServiceManager executorServiceManager) {
        this.sqsClient = sqsClient;
        this.createQueueOperation = createQueueOperation;
        this.clock = clock;
        this.executorServiceManager = executorServiceManager;

        this.maxNumberOfMessages = Math.max(1, maxNumberOfMessages);
        queueName = configuration.getQueueName();
        queueUrl = configuration.getQueueUrl();
        visibilityTimeout = configuration.getVisibilityTimeout();
        waitTimeSeconds = configuration.getWaitTimeSeconds();
        messageAttributeNames = splitCommaSeparatedValues(configuration.getMessageAttributeNames());
        attributeNames = splitCommaSeparatedValues(configuration.getAttributeNames());

        numberOfRequestsPerPoll = (int) Math.ceil((double) this.maxNumberOfMessages / MAX_NUMBER_OF_MESSAGES_PER_REQUEST);
        queueAutoCreationEnabled = configuration.isAutoCreateQueue();

        executor = executorServiceManager.newFixedThreadPool(this, "Sqs2PollingClient[%s]".formatted(queueName),
                this.maxNumberOfMessages);
    }

    void shutdown() {
        closed.set(true);
        executorServiceManager.shutdownNow(executor);
    }

    List<Message> poll() throws IOException {
        if (isClosed() || processScheduledQueueAutoCreation()) {
            return emptyList();
        }

        final PollContext context = new PollContext();
        final List<Message> messages = poll(context);
        if (context.errorCount() == numberOfRequestsPerPoll) {
            if (context.errorCount() == 1) {
                context.rethrowIfFirstErrorIsRuntimeException();
                throw new IOException("Error while polling", context.firstError());
            }
            throw new IOException(
                    ("Error while polling - all %s requests resulted in an error, "
                     + "please check the logs for more details").formatted(numberOfRequestsPerPoll));
        }
        return messages;
    }

    private List<Message> poll(final PollContext pollContext) throws IOException {
        if (numberOfRequestsPerPoll == 1) {
            return poll(maxNumberOfMessages, pollContext);
        }
        int remaining = maxNumberOfMessages;
        try {
            CompletableFuture<List<Message>> future = CompletableFuture.completedFuture(emptyList());
            while (remaining > 0) {
                int numberOfMessages = Math.min(remaining, MAX_NUMBER_OF_MESSAGES_PER_REQUEST);
                future = mergeResults(future,
                        CompletableFuture.supplyAsync(() -> poll(numberOfMessages, pollContext), executor));
                remaining -= MAX_NUMBER_OF_MESSAGES_PER_REQUEST;
            }
            return future.thenApply(this::sortBySequenceNumber).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.debug("Polling interrupted", e);
            return emptyList();
        } catch (ExecutionException e) {
            throw new IOException("Error while polling", e.getCause());
        }
    }

    private List<Message> poll(int maxNumberOfMessages, PollContext context) {
        if (context.isQueueMissing()) {
            // if one of the request encountered a missing queue error the remaining requests
            // should be ignored, even if the queue is automatically created it will be empty
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

    private List<Message> handleMissingQueueError(PollContext context, QueueDoesNotExistException error) {
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
        ReceiveMessageRequest.Builder requestBuilder = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(maxNumberOfMessages)
                .visibilityTimeout(visibilityTimeout)
                .waitTimeSeconds(waitTimeSeconds);
        if (!attributeNames.isEmpty()) {
            requestBuilder.messageSystemAttributeNamesWithStrings(attributeNames);
        }
        if (!messageAttributeNames.isEmpty()) {
            requestBuilder.messageAttributeNames(messageAttributeNames);
        }
        LOG.trace("Receiving messages with request [{}]...", requestBuilder);
        return requestBuilder.build();
    }

    private void createQueue(UUID requestId, PollContext context) {
        synchronized (mutex) {
            if (isClosed() || context.isMissingQueueHandledInAnotherRequest(requestId)) {
                // the missing queue error can be thrown by multiple threads
                // the first thread that is handling the error should prevent other threads
                // from repeating the logic
                // as the operation is synchronized, the other threads should wait and then
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
        final PollContext context = new PollContext();
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
        return Arrays.asList(COMMA_SEPARATED_PATTERN.split(value));
    }

    private static CompletableFuture<List<Message>> mergeResults(
            CompletableFuture<List<Message>> future1, CompletableFuture<List<Message>> future2) {
        return future1.thenCombine(future2, (messages1, messages2) -> {
            final List<Message> allMessages = new ArrayList<>(messages1);
            allMessages.addAll(messages2);
            return allMessages;
        });
    }

    /**
     * Sorts the list of messages by the sequence number attribute. The sorting is applied when multiple receive
     * requests are sent asynchronously and merged together. This in consequence should allow messages to be processed
     * in the correct order.
     */
    private List<Message> sortBySequenceNumber(List<Message> messages) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Received {} messages in {} requests", messages.size(), numberOfRequestsPerPoll);
        }
        return messages.stream()
                .sorted(comparing(message -> message.attributes().getOrDefault(MessageSystemAttributeName.SEQUENCE_NUMBER, "")))
                .toList();
    }

    private record PollContext(AtomicReference<UUID> missingQueueHandlerRequestId, Queue<Exception> errors) {
        private PollContext() {
            this(new AtomicReference<>(), new ConcurrentLinkedQueue<>());
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
