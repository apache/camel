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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With deleteAfterRead the consumer deletes the message it received once the exchange is done, with the receipt handle
 * it received the message with, whatever the route did with the CamelAwsSqsReceiptHandle header in the meantime.
 */
class SqsConsumerDeleteMessageTest extends CamelTestSupport {

    @BindToRegistry("client")
    private final AmazonSQSClientMock client = new AmazonSQSClientMock();

    @Override
    protected void doPreSetup() {
        client.addMessage(Message.builder().messageId("m-1").receiptHandle("handle-1").body("hello").build());
    }

    @Test
    void messageIsDeletedWhenTheRouteRemovesTheHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("hello");
        startRoute(route -> route.removeHeaders("*"));

        mock.assertIsSatisfied();
        assertDeleted("handle-1");
    }

    @Test
    void messageIsDeletedWhenTheRouteReplacesTheMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("reply");
        // the new message has none of the headers of the received one
        startRoute(route -> route.process(exchange -> {
            DefaultMessage message = new DefaultMessage(exchange);
            message.setBody("reply");
            exchange.setMessage(message);
        }));

        mock.assertIsSatisfied();
        assertDeleted("handle-1");
    }

    @Test
    void messageIsDeletedWithTheReceiptHandleItWasReceivedWith() throws Exception {
        // control: the route keeps the headers the consumer set
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("hello");
        mock.expectedHeaderReceived(Sqs2Constants.RECEIPT_HANDLE, "handle-1");
        startRoute(route -> {
        });

        mock.assertIsSatisfied();
        assertDeleted("handle-1");
    }

    private void startRoute(Consumer<RouteDefinition> steps) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                RouteDefinition route = from("aws2-sqs://MyQueue?amazonSQSClient=#client&delay=50");
                steps.accept(route);
                route.to("mock:result");
            }
        });
    }

    private void assertDeleted(String receiptHandle) {
        // the delete is sent once the exchange is done, after the mock endpoint got it
        await().atMost(10, TimeUnit.SECONDS).until(() -> !client.getDeleteMessageRequests().isEmpty());
        assertEquals(List.of(receiptHandle),
                client.getDeleteMessageRequests().stream().map(DeleteMessageRequest::receiptHandle).toList());
    }
}
