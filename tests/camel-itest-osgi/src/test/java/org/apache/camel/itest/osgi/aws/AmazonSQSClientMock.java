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
package org.apache.camel.itest.osgi.aws;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;

public class AmazonSQSClientMock extends AmazonSQSClient {
    
    List<Message> messages = new ArrayList<Message>();
    
    public AmazonSQSClientMock() {
        super(new BasicAWSCredentials("myAccessKey", "mySecretKey"));
    }

    @Override
    public ListQueuesResult listQueues() throws AmazonServiceException, AmazonClientException {
        ListQueuesResult result = new ListQueuesResult();
        return result;
    }

    @Override
    public CreateQueueResult createQueue(CreateQueueRequest createQueueRequest) throws AmazonServiceException, AmazonClientException {
        CreateQueueResult result = new CreateQueueResult();
        result.setQueueUrl("https://queue.amazonaws.com/541925086079/MyQueue");
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
        Collection<Message> resultMessages = new ArrayList<Message>();
        
        synchronized (messages) {
            int fetchSize = 0;
            for (Iterator<Message> iterator = messages.iterator(); iterator.hasNext() && fetchSize < maxNumberOfMessages; fetchSize++) {
                resultMessages.add(iterator.next());
                iterator.remove();
            }
        }
        
        result.setMessages(resultMessages);
        return result;
    }

    @Override
    public void deleteMessage(DeleteMessageRequest deleteMessageRequest) throws AmazonServiceException, AmazonClientException {
        // noop
    }
}