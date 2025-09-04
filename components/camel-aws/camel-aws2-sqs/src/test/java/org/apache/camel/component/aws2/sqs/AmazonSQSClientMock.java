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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsServiceClientConfiguration;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchResponse;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchResultEntry;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteQueueResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.ListQueuesRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.PurgeQueueResponse;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResultEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

import static java.util.Collections.unmodifiableMap;

public class AmazonSQSClientMock implements SqsClient {
    private static final String DEFAULT_QUEUE_URL = "https://queue.amazonaws.com/queue/camel-836";

    private final Queue<Message> messages = new ConcurrentLinkedQueue<>();
    private final Map<String, Map<String, String>> queueAttributes = new HashMap<>();
    private final Map<String, CreateQueueRequest> queues = new LinkedHashMap<>();
    private final Map<String, ScheduledFuture<?>> inFlight = new LinkedHashMap<>();

    // received requests
    private final Queue<ListQueuesRequest> listQueuesRequests = new ConcurrentLinkedQueue<>();
    private final Queue<SendMessageRequest> sendMessageRequests = new ConcurrentLinkedQueue<>();
    private final Queue<ChangeMessageVisibilityBatchRequest> changeMessageVisibilityBatchRequests
            = new ConcurrentLinkedQueue<>();
    private final Queue<ReceiveMessageRequest> receiveRequests = new ConcurrentLinkedQueue<>();
    private final Queue<CreateQueueRequest> createQueueRequets = new ConcurrentLinkedQueue<>();
    private final Queue<GetQueueUrlRequest> queueUrlRequests = new ConcurrentLinkedQueue<>();
    private final Queue<DeleteMessageRequest> deleteMessageRequests = new ConcurrentLinkedQueue<>();
    private final Queue<DeleteQueueRequest> deleteQueueRequests = new ConcurrentLinkedQueue<>();
    private final Queue<PurgeQueueRequest> purgeQueueRequests = new ConcurrentLinkedQueue<>();
    private final Queue<SetQueueAttributesRequest> setQueueAttributesRequets = new ConcurrentLinkedQueue<>();
    private final Queue<SendMessageBatchRequest> sendMessageBatchRequests = new ConcurrentLinkedQueue<>();

    private ScheduledExecutorService scheduler;
    private String queueName;
    private String queueUrl = DEFAULT_QUEUE_URL;
    private boolean verifyQueueUrl;
    private Consumer<ReceiveMessageRequest> receiveRequestHandler;
    private Consumer<CreateQueueRequest> createQueueHandler;

    public AmazonSQSClientMock() {
        this(null);
    }

    public AmazonSQSClientMock(String queueName) {
        this.queueName = queueName;
    }

    @Override
    public ListQueuesResponse listQueues() {
        return ListQueuesResponse.builder().build();
    }

    @Override
    public ListQueuesResponse listQueues(ListQueuesRequest request) {
        listQueuesRequests.offer(request);

        ListQueuesResponse.Builder result = ListQueuesResponse.builder();
        result.queueUrls(
                Optional.ofNullable(queueName).map(it -> List.of("/" + it)).orElseGet(() -> List.of("/queue1", "/queue2")));
        return result.build();
    }

    @Override
    public CreateQueueResponse createQueue(CreateQueueRequest createQueueRequest) {
        createQueueRequets.offer(createQueueRequest);

        Optional.ofNullable(createQueueHandler).ifPresent(it -> it.accept(createQueueRequest));
        String fqnQueueName = "https://queue.amazonaws.com/541925086079/" + createQueueRequest.queueName();
        queues.put(fqnQueueName, createQueueRequest);
        CreateQueueResponse.Builder result = CreateQueueResponse.builder();
        result.queueUrl(fqnQueueName);
        return result.build();
    }

    @Override
    public SendMessageResponse sendMessage(SendMessageRequest sendMessageRequest) {
        sendMessageRequests.offer(sendMessageRequest);

        if (verifyQueueUrl && sendMessageRequest.queueUrl() == null) {
            throw new RuntimeException("QueueUrl can not be null.");
        }
        Message.Builder message = Message.builder();
        message.body(sendMessageRequest.messageBody());
        message.md5OfBody("6a1559560f67c5e7a7d5d838bf0272ee");
        message.messageId("f6fb6f99-5eb2-4be4-9b15-144774141458");
        message.receiptHandle(
                "0NNAq8PwvXsyZkR6yu4nQ07FGxNmOBWi5zC9+4QMqJZ0DJ3gVOmjI2Gh/oFnb0IeJqy5Zc8kH4JX7GVpfjcEDjaAPSeOkXQZRcaBqt"
                              + "4lOtyfj0kcclVV/zS7aenhfhX5Ixfgz/rHhsJwtCPPvTAdgQFGYrqaHly+etJiawiNPVc=");
        addMessage(message.build());

        return SendMessageResponse.builder().messageId("f6fb6f99-5eb2-4be4-9b15-144774141458")
                .md5OfMessageBody("6a1559560f67c5e7a7d5d838bf0272ee").build();
    }

    @Override
    public ReceiveMessageResponse receiveMessage(ReceiveMessageRequest receiveMessageRequest) {
        receiveRequests.offer(receiveMessageRequest);

        Optional.ofNullable(receiveRequestHandler).ifPresent(it -> it.accept(receiveMessageRequest));
        int maxNumberOfMessages = Optional.ofNullable(receiveMessageRequest.maxNumberOfMessages()).orElse(1);
        ReceiveMessageResponse.Builder result = ReceiveMessageResponse.builder();
        Collection<Message> resultMessages = new ArrayList<>();
        while (resultMessages.size() < maxNumberOfMessages && !messages.isEmpty()) {
            var message = messages.poll();
            resultMessages.add(message);
            scheduleCancelInflight(receiveMessageRequest.queueUrl(), message);
        }
        result.messages(resultMessages);
        return result.build();
    }

    /*
     * Cancel (put back onto queue) in flight messages if the visibility time
     * has expired and has not been manually deleted (ack'd)
     */
    private void scheduleCancelInflight(final String queueUrl, final Message message) {
        if (scheduler != null) {
            int visibility = getVisibilityForQueue(queueUrl);
            if (visibility > 0) {
                ScheduledFuture<?> task = scheduler.schedule(() -> addMessage(message), visibility, TimeUnit.SECONDS);
                inFlight.put(message.receiptHandle(), task);
            }
        }
    }

    private int getVisibilityForQueue(String queueUrl) {
        Map<String, String> queueAttr = queues.get(queueUrl).attributesAsStrings();
        if (queueAttr.containsKey("VisibilityTimeout")) {
            return Integer.parseInt(queueAttr.get("VisibilityTimeout"));
        }
        return 0;
    }

    @Override
    public DeleteMessageResponse deleteMessage(DeleteMessageRequest deleteMessageRequest) {
        deleteMessageRequests.offer(deleteMessageRequest);

        String receiptHandle = deleteMessageRequest.receiptHandle();
        if (inFlight.containsKey(receiptHandle)) {
            ScheduledFuture<?> inFlightTask = inFlight.get(receiptHandle);
            inFlightTask.cancel(true);
        }
        return DeleteMessageResponse.builder().build();
    }

    @Override
    public PurgeQueueResponse purgeQueue(PurgeQueueRequest purgeQueueRequest) {
        purgeQueueRequests.offer(purgeQueueRequest);

        if (purgeQueueRequest.queueUrl() == null) {
            throw SqsException.builder().message("Queue name must be specified.").build();
        }
        return PurgeQueueResponse.builder().build();
    }

    @Override
    public DeleteQueueResponse deleteQueue(DeleteQueueRequest deleteQueueRequest)
            throws AwsServiceException, SdkClientException {
        deleteQueueRequests.offer(deleteQueueRequest);

        if (deleteQueueRequest.queueUrl() == null) {
            throw SqsException.builder().message("Queue name must be specified.").build();
        }
        return DeleteQueueResponse.builder().build();
    }

    @Override
    public SetQueueAttributesResponse setQueueAttributes(SetQueueAttributesRequest setQueueAttributesRequest) {
        setQueueAttributesRequets.offer(setQueueAttributesRequest);

        synchronized (queueAttributes) {
            if (!queueAttributes.containsKey(setQueueAttributesRequest.queueUrl())) {
                queueAttributes.put(setQueueAttributesRequest.queueUrl(), new HashMap<>());
            }
            for (final Map.Entry<String, String> entry : setQueueAttributesRequest.attributesAsStrings().entrySet()) {
                queueAttributes.get(setQueueAttributesRequest.queueUrl()).put(entry.getKey(), entry.getValue());
            }
        }
        return SetQueueAttributesResponse.builder().build();
    }

    @Override
    public SqsServiceClientConfiguration serviceClientConfiguration() {
        return null;
    }

    @Override
    public ChangeMessageVisibilityBatchResponse changeMessageVisibilityBatch(
            ChangeMessageVisibilityBatchRequest changeMessageVisibilityBatchRequest) {
        this.changeMessageVisibilityBatchRequests.offer(changeMessageVisibilityBatchRequest);

        // mark all as success
        List<ChangeMessageVisibilityBatchResultEntry> successful
                = changeMessageVisibilityBatchRequest.entries().stream().map(this::successVisibilityExtension).toList();

        // setting empty collections to null to support hasSuccessful which
        // perform null check rather than isEmpty checks
        if (successful.isEmpty()) {
            successful = null;
        }

        return ChangeMessageVisibilityBatchResponse.builder().successful(successful).build();
    }

    private ChangeMessageVisibilityBatchResultEntry successVisibilityExtension(ChangeMessageVisibilityBatchRequestEntry r) {
        return ChangeMessageVisibilityBatchResultEntry.builder().id(r.id()).build();
    }

    @Override
    public SendMessageBatchResponse sendMessageBatch(SendMessageBatchRequest request) {
        sendMessageBatchRequests.offer(request);

        SendMessageBatchResponse.Builder result = SendMessageBatchResponse.builder();
        Collection<SendMessageBatchResultEntry> entriesSuccess = new ArrayList<>();
        SendMessageBatchResultEntry.Builder entry1 = SendMessageBatchResultEntry.builder();
        SendMessageBatchResultEntry.Builder entry2 = SendMessageBatchResultEntry.builder();
        entry1.id("team1");
        entry2.id("team2");
        entriesSuccess.add(entry1.build());
        entriesSuccess.add(entry2.build());
        Collection<BatchResultErrorEntry> entriesFail = new ArrayList<>();
        BatchResultErrorEntry.Builder entry3 = BatchResultErrorEntry.builder();
        BatchResultErrorEntry.Builder entry4 = BatchResultErrorEntry.builder();
        entry3.id("team3");
        entry4.id("team4");
        entriesFail.add(entry3.build());
        entriesFail.add(entry4.build());
        result.successful(entriesSuccess);
        result.failed(entriesFail);
        return result.build();
    }

    @Override
    public String serviceName() {
        return getClass().getSimpleName();
    }

    @Override
    public void close() {
        messages.clear();
        queues.clear();
        queueAttributes.clear();
        verifyQueueUrl = false;
        receiveRequestHandler = null;
        createQueueHandler = null;
        queueUrl = DEFAULT_QUEUE_URL;
        clearRecordedRequests();
    }

    private void clearRecordedRequests() {
        listQueuesRequests.clear();
        sendMessageRequests.clear();
        changeMessageVisibilityBatchRequests.clear();
        receiveRequests.clear();
        createQueueRequets.clear();
        queueUrlRequests.clear();
        deleteMessageRequests.clear();
        deleteQueueRequests.clear();
        purgeQueueRequests.clear();
        setQueueAttributesRequets.clear();
        sendMessageBatchRequests.clear();
    }

    @Override
    public GetQueueUrlResponse getQueueUrl(GetQueueUrlRequest getQueueUrlRequest)
            throws AwsServiceException, SdkClientException {
        queueUrlRequests.offer(getQueueUrlRequest);

        if (queueUrl == null) {
            throw QueueDoesNotExistException.builder().build();
        }
        return GetQueueUrlResponse.builder().queueUrl(queueUrl).build();
    }

    ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    void setVerifyQueueUrl(boolean verifyQueueUrl) {
        this.verifyQueueUrl = verifyQueueUrl;
    }

    void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    void setQueueUrl(String queueUrl) {
        this.queueUrl = queueUrl;
    }

    void addMessage(Message message) {
        messages.offer(message);
    }

    void setReceiveRequestHandler(Consumer<ReceiveMessageRequest> receiveRequestHandler) {
        this.receiveRequestHandler = receiveRequestHandler;
    }

    void setCreateQueueHandler(Consumer<CreateQueueRequest> createQueueHandler) {
        this.createQueueHandler = createQueueHandler;
    }

    List<Message> getMessages() {
        return List.copyOf(messages);
    }

    Map<String, Map<String, String>> getQueueAttributes() {
        return unmodifiableMap(queueAttributes);
    }

    List<ListQueuesRequest> getListQueuesRequests() {
        return List.copyOf(listQueuesRequests);
    }

    List<SendMessageRequest> getSendMessageRequests() {
        return List.copyOf(sendMessageRequests);
    }

    List<ChangeMessageVisibilityBatchRequest> getChangeMessageVisibilityBatchRequests() {
        return List.copyOf(changeMessageVisibilityBatchRequests);
    }

    Map<String, CreateQueueRequest> getQueues() {
        return unmodifiableMap(queues);
    }

    List<ReceiveMessageRequest> getReceiveRequests() {
        return List.copyOf(receiveRequests);
    }

    List<CreateQueueRequest> getCreateQueueRequets() {
        return List.copyOf(createQueueRequets);
    }

    List<GetQueueUrlRequest> getQueueUrlRequests() {
        return List.copyOf(queueUrlRequests);
    }

    List<DeleteMessageRequest> getDeleteMessageRequests() {
        return List.copyOf(deleteMessageRequests);
    }

    List<DeleteQueueRequest> getDeleteQueueRequests() {
        return List.copyOf(deleteQueueRequests);
    }

    List<PurgeQueueRequest> getPurgeQueueRequests() {
        return List.copyOf(purgeQueueRequests);
    }

    List<SetQueueAttributesRequest> getSetQueueAttributesRequets() {
        return List.copyOf(setQueueAttributesRequets);
    }

    List<SendMessageBatchRequest> getSendMessageBatchRequests() {
        return List.copyOf(sendMessageBatchRequests);
    }
}
