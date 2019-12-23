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
package org.apache.camel.component.aws.sqs;

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

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AbstractAmazonSQS;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityRequest;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityResult;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.DeleteMessageResult;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.amazonaws.services.sqs.model.SetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.SetQueueAttributesResult;

public class AmazonSQSClientMock extends AbstractAmazonSQS {

    List<Message> messages = new ArrayList<>();
    Map<String, Map<String, String>> queueAttributes = new HashMap<>();
    List<ChangeMessageVisibilityRequest> changeMessageVisibilityRequests = new CopyOnWriteArrayList<>();
    private Map<String, CreateQueueRequest> queues = new LinkedHashMap<>();
    private Map<String, ScheduledFuture<?>> inFlight = new LinkedHashMap<>();
    private ScheduledExecutorService scheduler;

    public AmazonSQSClientMock() {
    }

    @Override
    public ListQueuesResult listQueues() throws AmazonServiceException, AmazonClientException {
        ListQueuesResult result = new ListQueuesResult();
        return result;
    }

    @Override
    public CreateQueueResult createQueue(CreateQueueRequest createQueueRequest) throws AmazonServiceException, AmazonClientException {
        String queueName = "https://queue.amazonaws.com/541925086079/" + createQueueRequest.getQueueName();
        queues.put(queueName, createQueueRequest);
        CreateQueueResult result = new CreateQueueResult();
        result.setQueueUrl(queueName);
        return result;
    }

    @Override
    public SendMessageResult sendMessage(SendMessageRequest sendMessageRequest) throws AmazonServiceException, AmazonClientException {
        Message message = new Message();
        message.setBody(sendMessageRequest.getMessageBody());
        message.setMD5OfBody("6a1559560f67c5e7a7d5d838bf0272ee");
        message.setMessageId("f6fb6f99-5eb2-4be4-9b15-144774141458");
        message.setReceiptHandle("0NNAq8PwvXsyZkR6yu4nQ07FGxNmOBWi5zC9+4QMqJZ0DJ3gVOmjI2Gh/oFnb0IeJqy5Zc8kH4JX7GVpfjcEDjaAPSeOkXQZRcaBqt"
                + "4lOtyfj0kcclVV/zS7aenhfhX5Ixfgz/rHhsJwtCPPvTAdgQFGYrqaHly+etJiawiNPVc=");
 
        synchronized (messages) {
            messages.add(message);
        }
        
        SendMessageResult result = new SendMessageResult();
        result.setMessageId("f6fb6f99-5eb2-4be4-9b15-144774141458");
        result.setMD5OfMessageBody("6a1559560f67c5e7a7d5d838bf0272ee");
        return result;
    }

    @Override
    public ReceiveMessageResult receiveMessage(ReceiveMessageRequest receiveMessageRequest) throws AmazonServiceException, AmazonClientException {
        Integer maxNumberOfMessages = receiveMessageRequest.getMaxNumberOfMessages() != null ? receiveMessageRequest.getMaxNumberOfMessages() : Integer.MAX_VALUE;
        ReceiveMessageResult result = new ReceiveMessageResult();
        Collection<Message> resultMessages = new ArrayList<>();
        
        synchronized (messages) {
            int fetchSize = 0;
            for (Iterator<Message> iterator = messages.iterator(); iterator.hasNext() && fetchSize < maxNumberOfMessages; fetchSize++) {
                Message rc = iterator.next();
                resultMessages.add(rc);
                iterator.remove();
                scheduleCancelInflight(receiveMessageRequest.getQueueUrl(), rc);
            }
        }
        
        result.setMessages(resultMessages);
        return result;
    }

    /*
     * Cancel (put back onto queue) in flight messages if the visibility time has expired
     * and has not been manually deleted (ack'd)
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

                inFlight.put(message.getReceiptHandle(), task);
            }
        }
    }

    private int getVisibilityForQueue(String queueUrl) {
        Map<String, String> queueAttr = queues.get(queueUrl).getAttributes();
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
    public DeleteMessageResult deleteMessage(DeleteMessageRequest deleteMessageRequest) throws AmazonClientException {
        String receiptHandle = deleteMessageRequest.getReceiptHandle();
        if (inFlight.containsKey(receiptHandle)) {
            ScheduledFuture<?> inFlightTask = inFlight.get(receiptHandle);
            inFlightTask.cancel(true);
        }
        return new DeleteMessageResult();
    }

    @Override
    public SetQueueAttributesResult setQueueAttributes(SetQueueAttributesRequest setQueueAttributesRequest) throws AmazonServiceException, AmazonClientException {
        synchronized (queueAttributes) {
            if (!queueAttributes.containsKey(setQueueAttributesRequest.getQueueUrl())) {
                queueAttributes.put(setQueueAttributesRequest.getQueueUrl(), new HashMap<String, String>());
            }
            for (final Map.Entry<String, String> entry : setQueueAttributesRequest.getAttributes().entrySet()) {
                queueAttributes.get(setQueueAttributesRequest.getQueueUrl()).put(entry.getKey(), entry.getValue());
            }
        }
        return new SetQueueAttributesResult();
    }

    @Override
    public ChangeMessageVisibilityResult changeMessageVisibility(ChangeMessageVisibilityRequest changeMessageVisibilityRequest) throws AmazonServiceException, AmazonClientException {
        this.changeMessageVisibilityRequests.add(changeMessageVisibilityRequest);
        return new ChangeMessageVisibilityResult();
    }
}