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

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchResponse;
import software.amazon.awssdk.services.sqs.model.Message;

import static org.awaitility.Awaitility.await;

/**
 * Regression test for CAMEL-27473: when {@code changeMessageVisibilityBatch} throws a non-{@code SdkException} (such as
 * an {@code IllegalStateException} from an execution interceptor), the {@code TimeoutExtender} task must keep running
 * and extend the visibility of subsequent batches.
 * <p>
 * Before the fix, the catch block in {@code TimeoutExtender.run()} only caught {@code SdkException}. Per the
 * {@code ScheduledExecutorService} contract, any uncaught exception from {@code scheduleAtFixedRate} permanently
 * cancels the task with no log. After one non-{@code SdkException} failure the extender stopped silently, so SQS would
 * re-deliver in-flight messages.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class SqsConsumerExtendMessageVisibilityNonSdkExceptionTest extends CamelTestSupport {

    private static final int VISIBILITY_TIMEOUT_SECONDS = 2;
    private static final String HANDLE = "receipt-handle-1";

    /** Number of {@code changeMessageVisibilityBatch} calls; the first one throws, the rest must still happen. */
    private final AtomicInteger extensionCallCount = new AtomicInteger();
    private final CountDownLatch releaseRoute = new CountDownLatch(1);

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @BindToRegistry("amazonSQSClient")
    private final AmazonSQSClientMock client = new AmazonSQSClientMock() {
        @Override
        public ChangeMessageVisibilityBatchResponse changeMessageVisibilityBatch(ChangeMessageVisibilityBatchRequest request) {
            if (request.entries().stream().anyMatch(e -> HANDLE.equals(e.receiptHandle()))) {
                int call = extensionCallCount.incrementAndGet();
                if (call == 1) {
                    // simulate a non-SDK exception, e.g. from an execution interceptor
                    throw new IllegalStateException("simulated non-SdkException from changeMessageVisibilityBatch");
                }
            }
            return super.changeMessageVisibilityBatch(request);
        }
    };

    {
        client.addMessage(Message.builder().body("body").messageId("id-1").receiptHandle(HANDLE).build());
    }

    @AfterEach
    void release() {
        releaseRoute.countDown();
    }

    @Test
    void extenderKeepsRunningAfterNonSdkException() throws Exception {
        mock.expectedBodiesReceived("body");

        // the route holds the message long enough for the extender to run at least twice
        await().atMost(10, TimeUnit.SECONDS).until(() -> extensionCallCount.get() >= 2);
        releaseRoute.countDown();

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
