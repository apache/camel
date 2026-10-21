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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQS counts the new visibility timeout of a {@code ChangeMessageVisibility} call from the time of the call, not from
 * its response. When the response is slow, the next extension must still come before the call plus
 * {@code visibilityTimeout}.
 */
class SqsConsumerExtendMessageVisibilitySlowCallTest extends CamelTestSupport {

    private static final int VISIBILITY_TIMEOUT_SECONDS = 4;
    // how long the response to the first ChangeMessageVisibilityBatch call takes (SQS extended the message at the call)
    private static final long SLOW_RESPONSE_MILLIS = 3000;
    private static final String HANDLE = "receipt-handle-1";

    private final List<Long> extensionCallsAtNanos = new CopyOnWriteArrayList<>();
    private final CountDownLatch extendedTwice = new CountDownLatch(2);
    private final CountDownLatch releaseRoute = new CountDownLatch(1);
    private final CountDownLatch endOfTest = new CountDownLatch(1);

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @BindToRegistry("amazonSQSClient")
    private final AmazonSQSClientMock client = new AmazonSQSClientMock() {
        @Override
        public ChangeMessageVisibilityBatchResponse changeMessageVisibilityBatch(ChangeMessageVisibilityBatchRequest request) {
            if (request.entries().stream().anyMatch(e -> HANDLE.equals(e.receiptHandle()))) {
                // the visibility timeout of the message runs from now on
                extensionCallsAtNanos.add(System.nanoTime());
                extendedTwice.countDown();
                if (extensionCallsAtNanos.size() == 1) {
                    // a slow response (it returns early only when the test ends)
                    try {
                        endOfTest.await(SLOW_RESPONSE_MILLIS, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
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
    void nextExtensionComesBeforeTheVisibilityTimeoutOfTheSlowCallRunsOut() throws Exception {
        mock.expectedBodiesReceived("slow");

        // the route holds the message (a slow processing) until it has been extended twice
        boolean wasExtendedTwice = extendedTwice.await(VISIBILITY_TIMEOUT_SECONDS * 5L, TimeUnit.SECONDS);
        releaseRoute.countDown();
        assertThat(wasExtendedTwice).as("the visibility of the message is extended twice while it is processed").isTrue();

        long millisBetweenCalls = TimeUnit.NANOSECONDS.toMillis(extensionCallsAtNanos.get(1) - extensionCallsAtNanos.get(0));
        assertThat(millisBetweenCalls)
                .as("second ChangeMessageVisibility for the message, in ms after the first call (whose response took "
                    + SLOW_RESPONSE_MILLIS + " ms; the message becomes visible again " + VISIBILITY_TIMEOUT_SECONDS
                    + " s after that call)")
                .isLessThan(VISIBILITY_TIMEOUT_SECONDS * 1000L);
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("aws2-sqs://MyQueue?amazonSQSClient=#amazonSQSClient&visibilityTimeout=" + VISIBILITY_TIMEOUT_SECONDS
                     + "&extendMessageVisibility=true")
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
