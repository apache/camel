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

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.Message;

public class SqsBatchConsumerConcurrentConsumersIT extends CamelTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void receiveBatch() throws Exception {
        mock.expectedMinimumMessageCount(5);
        MockEndpoint.assertIsSatisfied(context);
    }

    @BindToRegistry("amazonSQSClient")
    public AmazonSQSClientMock addClient() {

        AmazonSQSClientMock clientMock = new AmazonSQSClientMock();
        // add 6 messages, one more we will poll
        for (int counter = 0; counter < 6; counter++) {
            Message.Builder message = Message.builder();
            message.body("Message " + counter);
            message.md5OfBody("6a1559560f67c5e7a7d5d838bf0272ee");
            message.messageId("f6fb6f99-5eb2-4be4-9b15-144774141458");
            message.receiptHandle("0NNAq8PwvXsyZkR6yu4nQ07FGxNmOBWi5");

            clientMock.messages.add(message.build());
        }

        return clientMock;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("aws2-sqs://MyQueue?amazonSQSClient=#amazonSQSClient&delay=5000&maxMessagesPerPoll=5&concurrentConsumers=2")
                        .to("mock:result");
            }
        };
    }
}
