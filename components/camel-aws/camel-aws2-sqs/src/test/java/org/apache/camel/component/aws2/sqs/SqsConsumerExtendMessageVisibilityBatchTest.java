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

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchResponse;
import software.amazon.awssdk.services.sqs.model.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * With {@code extendMessageVisibility=true} and more than one message per poll, the messages that wait in the batch for
 * their turn must be kept invisible too: their visibility timeout runs from the receive, not from the start of their
 * own processing.
 */
class SqsConsumerExtendMessageVisibilityBatchTest extends CamelTestSupport {

    private static final int VISIBILITY_TIMEOUT_SECONDS = 2;
    private static final String FIRST_HANDLE = "receipt-handle-1";
    private static final String SECOND_HANDLE = "receipt-handle-2";

    private final CountDownLatch firstStarted = new CountDownLatch(1);
    private final CountDownLatch releaseFirst = new CountDownLatch(1);
    private final CountDownLatch secondExtended = new CountDownLatch(1);
    private final ExecutorService suspender = Executors.newSingleThreadExecutor();

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @BindToRegistry("amazonSQSClient")
    private final AmazonSQSClientMock client = new AmazonSQSClientMock() {
        @Override
        public ChangeMessageVisibilityBatchResponse changeMessageVisibilityBatch(ChangeMessageVisibilityBatchRequest request) {
            if (request.entries().stream().anyMatch(e -> SECOND_HANDLE.equals(e.receiptHandle()))) {
                secondExtended.countDown();
            }
            return super.changeMessageVisibilityBatch(request);
        }
    };

    {
        // both messages are on the queue before the route starts, so that the first poll receives them together
        client.addMessage(Message.builder().body("first").messageId("id-1").receiptHandle(FIRST_HANDLE).build());
        client.addMessage(Message.builder().body("second").messageId("id-2").receiptHandle(SECOND_HANDLE).build());
    }

    @AfterEach
    void releaseRoute() {
        releaseFirst.countDown();
        suspender.shutdownNow();
    }

    @Test
    void waitingMessageOfTheBatchIsExtendedWhileTheFirstIsProcessed() throws Exception {
        mock.expectedBodiesReceived("first", "second");

        assertThat(firstStarted.await(10, TimeUnit.SECONDS)).as("the first message was routed").isTrue();
        // the second message has been received with the first one; it is not routed before the first is done, but its
        // visibility timeout is running, so the extender must cover it now (the first is held for 5 timeouts at most)
        boolean extended = secondExtended.await(VISIBILITY_TIMEOUT_SECONDS * 5L, TimeUnit.SECONDS);
        releaseFirst.countDown();

        assertThat(extended)
                .as("the visibility of the second message of the batch is extended while the first message is processed"
                    + " (otherwise SQS makes it visible again and delivers it a second time)")
                .isTrue();
        mock.assertIsSatisfied();
    }

    @Test
    void messageLeftInTheBatchIsNoLongerExtendedAfterSuspend() throws Exception {
        mock.expectedBodiesReceived("first");

        assertThat(firstStarted.await(10, TimeUnit.SECONDS)).as("the first message was routed").isTrue();
        // suspend the route while the first message is processed: the second message of the batch is then not routed
        Future<?> suspend = suspender.submit(() -> {
            context.getRouteController().suspendRoute("sqs", 30, TimeUnit.SECONDS);
            return null;
        });
        await().atMost(Duration.ofSeconds(10))
                .until(() -> context.getRoute("sqs").getConsumer() instanceof Sqs2Consumer c && c.isSuspended());
        releaseFirst.countDown();
        suspend.get(30, TimeUnit.SECONDS);
        assertThat(context.getRouteController().getRouteStatus("sqs")).isEqualTo(ServiceStatus.Suspended);
        mock.assertIsSatisfied();

        int requestsSoFar = client.getChangeMessageVisibilityBatchRequests().size();
        // the message that was not routed must not be kept invisible while the route is suspended: no extension for
        // it during several runs of the extender (it runs every VISIBILITY_TIMEOUT_SECONDS / 2 seconds)
        await().during(Duration.ofSeconds(VISIBILITY_TIMEOUT_SECONDS * 2L)).atMost(Duration.ofSeconds(10))
                .until(() -> client.getChangeMessageVisibilityBatchRequests().stream()
                        .skip(requestsSoFar)
                        .flatMap(r -> r.entries().stream())
                        .noneMatch(e -> SECOND_HANDLE.equals(e.receiptHandle())));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("aws2-sqs://MyQueue?amazonSQSClient=#amazonSQSClient&maxMessagesPerPoll=2&visibilityTimeout="
                     + VISIBILITY_TIMEOUT_SECONDS + "&extendMessageVisibility=true")
                        .routeId("sqs")
                        .process(exchange -> {
                            if ("first".equals(exchange.getIn().getBody(String.class))) {
                                firstStarted.countDown();
                                if (!releaseFirst.await(30, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("the test did not release the first message");
                                }
                            }
                        })
                        .to("mock:result");
            }
        };
    }
}
