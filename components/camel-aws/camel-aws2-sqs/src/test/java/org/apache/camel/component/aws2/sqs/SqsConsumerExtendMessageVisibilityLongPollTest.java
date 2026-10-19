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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With {@code maxMessagesPerPoll} greater than 10 a poll sends several receive requests and routes the messages only
 * when the last of them returned, which with long polling can be up to {@code waitTimeSeconds} after the first one. The
 * visibility timeout of a message runs from its own receive, so with {@code extendMessageVisibility=true} its first
 * extension must come before that receive plus {@code visibilityTimeout}.
 */
class SqsConsumerExtendMessageVisibilityLongPollTest extends CamelTestSupport {

    private static final int VISIBILITY_TIMEOUT_SECONDS = 4;
    // how long the second receive request of the poll waits for messages (long polling on a queue with no more messages)
    private static final long LONG_POLL_MILLIS = 3000;
    private static final String HANDLE = "receipt-handle-1";

    private final AtomicInteger receiveCalls = new AtomicInteger();
    private final AtomicLong receivedAtNanos = new AtomicLong();
    private final AtomicLong firstExtensionAtNanos = new AtomicLong();
    private final CountDownLatch extended = new CountDownLatch(1);
    private final CountDownLatch releaseRoute = new CountDownLatch(1);
    private final CountDownLatch endOfTest = new CountDownLatch(1);

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @BindToRegistry("amazonSQSClient")
    private final AmazonSQSClientMock client = new AmazonSQSClientMock() {
        @Override
        public ReceiveMessageResponse receiveMessage(ReceiveMessageRequest request) {
            if (receiveCalls.incrementAndGet() == 2) {
                // the other request of the first poll: it waits for messages, like a long poll on a queue where no
                // more messages arrive (it returns early only when the test ends)
                try {
                    endOfTest.await(LONG_POLL_MILLIS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            ReceiveMessageResponse response = super.receiveMessage(request);
            if (response.messages().stream().anyMatch(m -> HANDLE.equals(m.receiptHandle()))) {
                // the visibility timeout of the message starts now
                receivedAtNanos.set(System.nanoTime());
            }
            return response;
        }

        @Override
        public ChangeMessageVisibilityBatchResponse changeMessageVisibilityBatch(ChangeMessageVisibilityBatchRequest request) {
            if (request.entries().stream().anyMatch(e -> HANDLE.equals(e.receiptHandle()))) {
                firstExtensionAtNanos.compareAndSet(0, System.nanoTime());
                extended.countDown();
            }
            return super.changeMessageVisibilityBatch(request);
        }
    };

    {
        client.addMessage(Message.builder().body("slow").messageId("id-1").receiptHandle(HANDLE).build());
    }

    @AfterEach
    void release() {
        releaseRoute.countDown();
        endOfTest.countDown();
    }

    @Test
    void firstExtensionComesBeforeTheVisibilityTimeoutOfTheReceiveRunsOut() throws Exception {
        mock.expectedBodiesReceived("slow");

        // the route holds the message (a slow processing) until it has been extended once, at most 3 timeouts
        boolean wasExtended = extended.await(VISIBILITY_TIMEOUT_SECONDS * 3L, TimeUnit.SECONDS);
        releaseRoute.countDown();
        assertThat(wasExtended).as("the visibility of the message is extended while it is processed").isTrue();

        long millisFromReceive = TimeUnit.NANOSECONDS.toMillis(firstExtensionAtNanos.get() - receivedAtNanos.get());
        assertThat(millisFromReceive)
                .as("first ChangeMessageVisibility for the message, in ms after its receive (it becomes visible again,"
                    + " and is delivered to another consumer, " + VISIBILITY_TIMEOUT_SECONDS + " s after the receive)")
                .isLessThan(VISIBILITY_TIMEOUT_SECONDS * 1000L);
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // maxMessagesPerPoll=20: two receive requests of 10 messages each per poll
                from("aws2-sqs://MyQueue?amazonSQSClient=#amazonSQSClient&maxMessagesPerPoll=20&visibilityTimeout="
                     + VISIBILITY_TIMEOUT_SECONDS + "&extendMessageVisibility=true")
                        .process(exchange -> {
                            if (!releaseRoute.await(30, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("the test did not release the message");
                            }
                        })
                        .to("mock:result");
            }
        };
    }
}
