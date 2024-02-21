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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsServiceClientConfiguration;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchResponse;
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

public class AmazonSQSClientMock implements SqsClient {

    List<Message> messages = new ArrayList<>();
    Map<String, Map<String, String>> queueAttributes = new HashMap<>();
    List<ChangeMessageVisibilityBatchRequest> changeMessageVisibilityBatchRequests = new CopyOnWriteArrayList<>();
    private Map<String, CreateQueueRequest> queues = new LinkedHashMap<>();
    private Map<String, ScheduledFuture<?>> inFlight = new LinkedHashMap<>();
    private ScheduledExecutorService scheduler;
    private String queueName;
    private boolean verifyQueueUrl;

    public AmazonSQSClientMock() {
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
        ListQueuesResponse.Builder result = ListQueuesResponse.builder();
        List<String> queues = new ArrayList<>();
        if (queueName != null) {
            queues.add("/" + queueName);
        } else {
            queues.add("/queue1");
            queues.add("/queue2");
        }
        result.queueUrls(queues);
        return result.build();
    }

    @Override
    public CreateQueueResponse createQueue(CreateQueueRequest createQueueRequest) {
        String queueName = "https://queue.amazonaws.com/541925086079/" + createQueueRequest.queueName();
        queues.put(queueName, createQueueRequest);
        CreateQueueResponse.Builder result = CreateQueueResponse.builder();
        result.queueUrl(queueName);
        return result.build();
    }

    @Override
    public SendMessageResponse sendMessage(SendMessageRequest sendMessageRequest) {
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

        synchronized (messages) {
            messages.add(message.build());
        }

        return SendMessageResponse.builder().messageId("f6fb6f99-5eb2-4be4-9b15-144774141458")
                .md5OfMessageBody("6a1559560f67c5e7a7d5d838bf0272ee").build();
    }

    @Override
    public ReceiveMessageResponse receiveMessage(ReceiveMessageRequest receiveMessageRequest) {
        int maxNumberOfMessages = receiveMessageRequest.maxNumberOfMessages() != null
                ? receiveMessageRequest.maxNumberOfMessages() : Integer.MAX_VALUE;
        ReceiveMessageResponse.Builder result = ReceiveMessageResponse.builder();
        Collection<Message> resultMessages = new ArrayList<>();

        synchronized (messages) {
            int fetchSize = 0;
            for (Iterator<Message> iterator = messages.iterator();
                 iterator.hasNext() && fetchSize < maxNumberOfMessages;
                 fetchSize++) {
                Message rc = iterator.next();
                resultMessages.add(rc);
                iterator.remove();
                scheduleCancelInflight(receiveMessageRequest.queueUrl(), rc);
            }
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
                ScheduledFuture<?> task = scheduler.schedule(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (messages) {
                            // put it back!
                            messages.add(message);
                        }
                    }
                }, visibility, TimeUnit.SECONDS);

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

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public DeleteMessageResponse deleteMessage(DeleteMessageRequest deleteMessageRequest) {
        String receiptHandle = deleteMessageRequest.receiptHandle();
        if (inFlight.containsKey(receiptHandle)) {
            ScheduledFuture<?> inFlightTask = inFlight.get(receiptHandle);
            inFlightTask.cancel(true);
        }
        return DeleteMessageResponse.builder().build();
    }

    @Override
    public PurgeQueueResponse purgeQueue(PurgeQueueRequest purgeQueueRequest) {
        if (purgeQueueRequest.queueUrl() == null) {
            throw SqsException.builder().message("Queue name must be specified.").build();
        }
        return PurgeQueueResponse.builder().build();
    }

    @Override
    public DeleteQueueResponse deleteQueue(DeleteQueueRequest deleteQueueRequest)
            throws AwsServiceException, SdkClientException {
        if (deleteQueueRequest.queueUrl() == null) {
            throw SqsException.builder().message("Queue name must be specified.").build();
        }
        return DeleteQueueResponse.builder().build();
    }

    @Override
    public SetQueueAttributesResponse setQueueAttributes(SetQueueAttributesRequest setQueueAttributesRequest) {
        synchronized (queueAttributes) {
            if (!queueAttributes.containsKey(setQueueAttributesRequest.queueUrl())) {
                queueAttributes.put(setQueueAttributesRequest.queueUrl(), new HashMap<String, String>());
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
        this.changeMessageVisibilityBatchRequests.add(changeMessageVisibilityBatchRequest);
        return ChangeMessageVisibilityBatchResponse.builder().build();
    }

    @Override
    public SendMessageBatchResponse sendMessageBatch(SendMessageBatchRequest request) {
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
        entry3.id("team1");
        entry4.id("team4");
        entriesFail.add(entry3.build());
        entriesFail.add(entry4.build());
        result.successful(entriesSuccess);
        result.failed(entriesFail);
        return result.build();
    }

    @Override
    public String serviceName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub

    }

    @Override
    public GetQueueUrlResponse getQueueUrl(GetQueueUrlRequest getQueueUrlRequest)
            throws AwsServiceException, SdkClientException {
        return GetQueueUrlResponse.builder()
                .queueUrl("https://queue.amazonaws.com/queue/camel-836")
                .build();
    }

    public void setVerifyQueueUrl(boolean verifyQueueUrl) {
        this.verifyQueueUrl = verifyQueueUrl;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }
}
