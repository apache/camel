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
package org.apache.camel.component.sjms.batch;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.sjms.batch.BatchTestSupport.assertBatchSizesInOrder;

public class BatchConsumerTimeoutTest extends JmsTestSupport {

    private static final String SJMS_FROMF_URI = "%s?batching=true&batchingSize=100&batchingTimeout=1000";
    private static final String SJMS_QUEUE_NAME
            = "sjms:queue:batch.consumer.queue.BatchConsumerTimeoutTest";
    private static final String MOCK_RESULT = "mock:result";

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    public void after() {
        executor.shutdownNow();
    }

    @Test
    public void testBatchConsumerTimeoutOneBatch() throws Exception {
        MockEndpoint mock = getMockEndpoint(MOCK_RESULT);
        mock.expectedMessageCount(1);

        AtomicInteger counter = new AtomicInteger();
        executor.scheduleAtFixedRate(
                () -> {
                    int count = counter.incrementAndGet();
                    template.sendBody(SJMS_QUEUE_NAME, "Message");
                    if (count >= 5) {
                        executor.shutdown();
                    }
                },
                0,
                750,
                TimeUnit.MILLISECONDS);

        MockEndpoint.assertIsSatisfied(context);
        assertBatchSizesInOrder(mock, 5);
    }

    @Test
    public void testBatchConsumerTimeoutMultipleBatches() throws Exception {
        MockEndpoint mock = getMockEndpoint(MOCK_RESULT);
        mock.expectedMessageCount(5);

        AtomicInteger counter = new AtomicInteger();
        executor.scheduleAtFixedRate(
                () -> {
                    int count = counter.incrementAndGet();
                    template.sendBody(SJMS_QUEUE_NAME, "Message");
                    if (count >= 5) {
                        executor.shutdown();
                    }
                },
                0,
                1200,
                TimeUnit.MILLISECONDS);

        MockEndpoint.assertIsSatisfied(context);
        assertBatchSizesInOrder(mock, 1, 1, 1, 1, 1);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                fromF(SJMS_FROMF_URI, SJMS_QUEUE_NAME)
                        .to(MOCK_RESULT);
            }
        };
    }
}
