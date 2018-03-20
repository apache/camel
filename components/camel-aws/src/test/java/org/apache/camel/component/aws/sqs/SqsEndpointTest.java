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

import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.GetQueueUrlRequest;
import com.amazonaws.services.sqs.model.GetQueueUrlResult;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.QueueAttributeName;

import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;

public class SqsEndpointTest {

    private SqsEndpoint endpoint;
    private AmazonSQSClient amazonSQSClient;
    private SqsConfiguration config;

    @Before
    public void setUp() throws Exception {
        amazonSQSClient = Mockito.mock(AmazonSQSClient.class);

        config = new SqsConfiguration();
        config.setQueueName("test-queue");
        config.setAmazonSQSClient(amazonSQSClient);

        endpoint = new SqsEndpoint("aws-sqs://test-queue", new SqsComponent(new DefaultCamelContext()), config);

    }

    @Test
    public void doStartShouldNotCallUpdateQueueAttributesIfQueueExistAndNoOptionIsSpecified() throws Exception {
        Mockito.when(amazonSQSClient.listQueues())
            .thenReturn(new ListQueuesResult().withQueueUrls("https://sqs.us-east-1.amazonaws.com/ID/dummy-queue", "https://sqs.us-east-1.amazonaws.com/ID/test-queue"));

        endpoint.doStart();

        Mockito.verify(amazonSQSClient).listQueues();
    }

    @Test
    public void doStartWithDifferentQueueOwner() throws Exception {

        GetQueueUrlRequest expectedGetQueueUrlRequest = new GetQueueUrlRequest("test-queue")
                            .withQueueOwnerAWSAccountId("111222333");
        Mockito.when(amazonSQSClient.getQueueUrl(expectedGetQueueUrlRequest))
            .thenReturn(new GetQueueUrlResult()
                           .withQueueUrl("https://sqs.us-east-1.amazonaws.com/111222333/test-queue"));

        endpoint.getConfiguration().setQueueOwnerAWSAccountId("111222333");
        endpoint.doStart();

        Mockito.verify(amazonSQSClient).getQueueUrl(expectedGetQueueUrlRequest);

    }

    @Test
    public void createQueueShouldCreateFifoQueueWithContentBasedDeduplication() {
        config.setQueueName("test-queue.fifo");
        config.setMessageDeduplicationIdStrategy("useContentBasedDeduplication");

        CreateQueueRequest expectedCreateQueueRequest = new CreateQueueRequest("test-queue.fifo")
                .addAttributesEntry(QueueAttributeName.FifoQueue.name(), "true")
                .addAttributesEntry(QueueAttributeName.ContentBasedDeduplication.name(), "true");
        Mockito.when(amazonSQSClient.createQueue(Mockito.any(CreateQueueRequest.class)))
                .thenReturn(new CreateQueueResult()
                                .withQueueUrl("https://sqs.us-east-1.amazonaws.com/111222333/test-queue.fifo"));

        endpoint.createQueue(amazonSQSClient);

        Mockito.verify(amazonSQSClient).createQueue(expectedCreateQueueRequest);
        assertEquals("https://sqs.us-east-1.amazonaws.com/111222333/test-queue.fifo", endpoint.getQueueUrl());
    }

    @Test
    public void createQueueShouldCreateFifoQueueWithoutContentBasedDeduplication() {
        config.setQueueName("test-queue.fifo");
        config.setMessageDeduplicationIdStrategy("useExchangeId");

        CreateQueueRequest expectedCreateQueueRequest = new CreateQueueRequest("test-queue.fifo")
                .addAttributesEntry(QueueAttributeName.FifoQueue.name(), "true")
                .addAttributesEntry(QueueAttributeName.ContentBasedDeduplication.name(), "false");
        Mockito.when(amazonSQSClient.createQueue(Mockito.any(CreateQueueRequest.class)))
                .thenReturn(new CreateQueueResult()
                                .withQueueUrl("https://sqs.us-east-1.amazonaws.com/111222333/test-queue.fifo"));

        endpoint.createQueue(amazonSQSClient);

        Mockito.verify(amazonSQSClient).createQueue(expectedCreateQueueRequest);
        assertEquals("https://sqs.us-east-1.amazonaws.com/111222333/test-queue.fifo", endpoint.getQueueUrl());
    }

    @Test
    public void createQueueShouldCreateStandardQueueWithCorrectAttributes() {
        config.setDefaultVisibilityTimeout(1000);
        config.setMaximumMessageSize(128);
        config.setMessageRetentionPeriod(1000);
        config.setPolicy("{\"Version\": \"2012-10-17\"}");
        config.setReceiveMessageWaitTimeSeconds(5);
        config.setRedrivePolicy("{ \"deadLetterTargetArn\" : String, \"maxReceiveCount\" : Integer }");

        CreateQueueRequest expectedCreateQueueRequest = new CreateQueueRequest("test-queue")
                .addAttributesEntry(QueueAttributeName.VisibilityTimeout.name(), "1000")
                .addAttributesEntry(QueueAttributeName.MaximumMessageSize.name(), "128")
                .addAttributesEntry(QueueAttributeName.MessageRetentionPeriod.name(), "1000")
                .addAttributesEntry(QueueAttributeName.Policy.name(), "{\"Version\": \"2012-10-17\"}")
                .addAttributesEntry(QueueAttributeName.ReceiveMessageWaitTimeSeconds.name(), "5")
                .addAttributesEntry(QueueAttributeName.RedrivePolicy.name(), "{ \"deadLetterTargetArn\" : String, \"maxReceiveCount\" : Integer }");
        Mockito.when(amazonSQSClient.createQueue(Mockito.any(CreateQueueRequest.class)))
                .thenReturn(new CreateQueueResult()
                                .withQueueUrl("https://sqs.us-east-1.amazonaws.com/111222333/test-queue"));

        endpoint.createQueue(amazonSQSClient);

        Mockito.verify(amazonSQSClient).createQueue(expectedCreateQueueRequest);
        assertEquals("https://sqs.us-east-1.amazonaws.com/111222333/test-queue", endpoint.getQueueUrl());
    }
}