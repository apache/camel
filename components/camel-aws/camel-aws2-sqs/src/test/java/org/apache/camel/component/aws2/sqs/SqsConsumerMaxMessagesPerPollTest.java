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

import java.util.List;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The number of messages a poll asks SQS for: 10 by default, which is the most a receive request returns, and one with
 * {@code maxMessagesPerPoll} set to 1, 0 or a negative value.
 */
class SqsConsumerMaxMessagesPerPollTest extends CamelTestSupport {

    private static final int QUEUED = 25;

    @BindToRegistry("defaultClient")
    private final AmazonSQSClientMock defaultClient = clientWithQueuedMessages();
    @BindToRegistry("oneClient")
    private final AmazonSQSClientMock oneClient = clientWithQueuedMessages();
    @BindToRegistry("zeroClient")
    private final AmazonSQSClientMock zeroClient = clientWithQueuedMessages();
    @BindToRegistry("negativeClient")
    private final AmazonSQSClientMock negativeClient = clientWithQueuedMessages();

    private final Map<String, AmazonSQSClientMock> clients = Map.of(
            "default", defaultClient, "one", oneClient, "zero", zeroClient, "negative", negativeClient);

    @Test
    void defaultPollReceivesTenMessagesWithOneRequest() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:default");
        mock.expectedMessageCount(10);
        mock.expectedPropertyReceived(Exchange.BATCH_SIZE, 10);

        context.getRouteController().startRoute("default");

        MockEndpoint.assertIsSatisfied(context);
        List<ReceiveMessageRequest> receives = defaultClient.getReceiveRequests();
        assertEquals(1, receives.size(), "one receive request in the poll");
        assertEquals(10, receives.get(0).maxNumberOfMessages(), "the receive request asks for 10 messages");
    }

    @ParameterizedTest
    @ValueSource(strings = { "one", "zero", "negative" })
    void pollReceivesOneMessage(String routeId) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:" + routeId);
        mock.expectedMessageCount(1);
        mock.expectedPropertyReceived(Exchange.BATCH_SIZE, 1);

        context.getRouteController().startRoute(routeId);

        MockEndpoint.assertIsSatisfied(context);
        List<ReceiveMessageRequest> receives = clients.get(routeId).getReceiveRequests();
        assertEquals(1, receives.size(), "one receive request in the poll");
        assertEquals(1, receives.get(0).maxNumberOfMessages(), "the receive request asks for 1 message");
    }

    private static AmazonSQSClientMock clientWithQueuedMessages() {
        AmazonSQSClientMock client = new AmazonSQSClientMock();
        for (int i = 0; i < QUEUED; i++) {
            client.addMessage(Message.builder().body("message " + i).messageId("id-" + i).receiptHandle("handle-" + i)
                    .build());
        }
        return client;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // one poll per test: the next poll is an hour later
                String options = "&initialDelay=0&delay=3600000";
                from("aws2-sqs://MyQueue?amazonSQSClient=#defaultClient" + options)
                        .routeId("default").autoStartup(false).to("mock:default");
                from("aws2-sqs://MyQueue?amazonSQSClient=#oneClient&maxMessagesPerPoll=1" + options)
                        .routeId("one").autoStartup(false).to("mock:one");
                from("aws2-sqs://MyQueue?amazonSQSClient=#zeroClient&maxMessagesPerPoll=0" + options)
                        .routeId("zero").autoStartup(false).to("mock:zero");
                from("aws2-sqs://MyQueue?amazonSQSClient=#negativeClient&maxMessagesPerPoll=-1" + options)
                        .routeId("negative").autoStartup(false).to("mock:negative");
            }
        };
    }
}
