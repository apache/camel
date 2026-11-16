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
package org.apache.camel.component.alibaba.mns;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.aliyun.mns.client.CloudQueue;
import com.aliyun.mns.client.CloudTopic;
import com.aliyun.mns.client.MNSClient;
import com.aliyun.mns.model.Message;
import com.aliyun.mns.model.TopicMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.alibaba.mns.constants.MNSHeaders;
import org.apache.camel.component.alibaba.mns.constants.MNSOperations;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The producer and the poll of the consumer send their requests with the client that {@link MNSEndpoint#initClient()}
 * returns. A stop of the endpoint closes its client and clears the field, so a later getMnsClient() could give null.
 */
class MNSInitClientTest {

    private final TestConfiguration testConfiguration = new TestConfiguration();
    private final String queueName = testConfiguration.getProperty("queue");
    private final String topicName = testConfiguration.getProperty("topic");
    private final MNSClient client = mock(MNSClient.class);
    private final CloudQueue cloudQueue = mock(CloudQueue.class);
    private final CloudTopic cloudTopic = mock(CloudTopic.class);
    private final List<Exchange> received = new CopyOnWriteArrayList<>();
    private CamelContext context;

    @BeforeEach
    void startContext() {
        context = new DefaultCamelContext();
        context.start();
        when(client.getQueueRef(queueName)).thenReturn(cloudQueue);
        when(client.getTopicRef(topicName)).thenReturn(cloudTopic);
    }

    @AfterEach
    void stopContext() {
        context.stop();
    }

    @Test
    void sendMessageUsesTheClientThatInitClientReturns() throws Exception {
        new MNSProducer(endpointThatLosesItsClient()).process(exchange(MNSOperations.SEND_MESSAGE, "hello"));

        verify(cloudQueue).putMessage(any(Message.class));
    }

    @Test
    void deleteMessageUsesTheClientThatInitClientReturns() throws Exception {
        Exchange exchange = exchange(MNSOperations.DELETE_MESSAGE, null);
        exchange.getIn().setHeader(MNSHeaders.RECEIPT_HANDLE, "receipt-1");

        new MNSProducer(endpointThatLosesItsClient()).process(exchange);

        verify(cloudQueue).deleteMessage("receipt-1");
    }

    @Test
    void publishMessageUsesTheClientThatInitClientReturns() throws Exception {
        new MNSProducer(endpointThatLosesItsClient()).process(exchange(MNSOperations.PUBLISH_MESSAGE, "hello"));

        verify(cloudTopic).publishMessage(any(TopicMessage.class));
    }

    @Test
    void pollUsesTheClientThatInitClientReturns() throws Exception {
        when(cloudQueue.popMessage()).thenReturn(message("receipt-1"));
        MNSConsumer consumer = startedConsumer(endpointThatLosesItsClient());
        try {
            assertThat(consumer.poll()).isEqualTo(1);

            assertThat(received).hasSize(1);
            assertThat(received.get(0).getIn().getBody()).isEqualTo("hello");
        } finally {
            consumer.stop();
        }
    }

    /**
     * An endpoint that creates the mock client, but whose getMnsClient() returns null, as it would after a stop between
     * initClient and a later getMnsClient().
     */
    private MNSEndpoint endpointThatLosesItsClient() {
        MNSEndpoint endpoint
                = new MNSEndpoint("alibaba-mns:" + queueName, context.getComponent("alibaba-mns", MNSComponent.class)) {
                    @Override
                    MNSClient createClient() {
                        return client;
                    }

                    @Override
                    public MNSClient getMnsClient() {
                        return null;
                    }
                };
        configure(endpoint);
        return endpoint;
    }

    private void configure(MNSEndpoint endpoint) {
        endpoint.setQueueName(queueName);
        endpoint.setTopicName(topicName);
        endpoint.setAccessKey(testConfiguration.getProperty("accessKey"));
        endpoint.setSecretKey(testConfiguration.getProperty("secretKey"));
    }

    /**
     * A consumer whose scheduler does not start, so that only the test polls.
     */
    private MNSConsumer startedConsumer(MNSEndpoint endpoint) throws Exception {
        MNSConsumer consumer = (MNSConsumer) endpoint.createConsumer(received::add);
        consumer.setStartScheduler(false);
        consumer.start();
        return consumer;
    }

    private Exchange exchange(String operation, Object body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(MNSHeaders.OPERATION, operation);
        exchange.getIn().setBody(body);
        return exchange;
    }

    private static Message message(String receiptHandle) {
        Message message = new Message("hello");
        message.setReceiptHandle(receiptHandle);
        return message;
    }
}
