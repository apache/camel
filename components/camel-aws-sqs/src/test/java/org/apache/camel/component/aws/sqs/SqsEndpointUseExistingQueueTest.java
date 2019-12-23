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

import java.util.List;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.SetQueueAttributesResult;
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SqsEndpointUseExistingQueueTest extends CamelTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @BindToRegistry("amazonSQSClient")
    private AmazonSQSClientMock client = new SqsEndpointUseExistingQueueTest.AmazonSQSClientMock();

    @Test
    public void defaultsToDisabled() throws Exception {
        this.mock.expectedMessageCount(1);

        assertMockEndpointsSatisfied(); // Wait for message to arrive.
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("aws-sqs://MyQueue?amazonSQSClient=#amazonSQSClient").to("mock:result");
            }
        };
    }

    static class AmazonSQSClientMock extends AmazonSQSClient {

        AmazonSQSClientMock() {
            super(new BasicAWSCredentials("myAccessKey", "mySecretKey"));
        }

        @Override
        public ListQueuesResult listQueues() throws AmazonServiceException, AmazonClientException {
            ListQueuesResult result = new ListQueuesResult();
            result.getQueueUrls().add("http://queue.amazonaws.com/0815/Foo");
            result.getQueueUrls().add("http://queue.amazonaws.com/0815/MyQueue");
            result.getQueueUrls().add("http://queue.amazonaws.com/0815/Bar");
            return result;
        }

        @Override
        public CreateQueueResult createQueue(CreateQueueRequest createQueueRequest) throws AmazonServiceException, AmazonClientException {
            throw new AmazonServiceException("forced exception for test if this method is called");
        }

        @Override
        public SetQueueAttributesResult setQueueAttributes(SetQueueAttributesRequest setQueueAttributesRequest) throws AmazonServiceException, AmazonClientException {
            return new SetQueueAttributesResult();
        }

        @Override
        public ReceiveMessageResult receiveMessage(ReceiveMessageRequest receiveMessageRequest) throws AmazonServiceException, AmazonClientException {
            ReceiveMessageResult result = new ReceiveMessageResult();
            List<Message> resultMessages = result.getMessages();
            Message message = new Message();
            resultMessages.add(message);

            return result;
        }
    }
}
