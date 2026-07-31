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
package org.apache.camel.component.seda;

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for the virtualThreadPerTask mode of SEDA consumer
 */
class ThreadPerTaskSedaConsumerTest extends ContextTestSupport {

    @Test
    void testVirtualThreadPerTask() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(10);

        for (int i = 0; i < 10; i++) {
            template.sendBody("seda:test?virtualThreadPerTask=true", "Message " + i);
        }

        mock.assertIsSatisfied();
    }

    @Test
    void testVirtualThreadPerTaskWithConcurrencyLimit() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:limited");
        mock.expectedMessageCount(5);

        for (int i = 0; i < 5; i++) {
            template.sendBody("seda:limited?virtualThreadPerTask=true&concurrentConsumers=2", "Message " + i);
        }

        mock.assertIsSatisfied();
    }

    @Test
    void testVirtualThreadPerTaskHighThroughput() throws Exception {
        int messageCount = 100;
        MockEndpoint mock = getMockEndpoint("mock:throughput");
        mock.expectedMessageCount(messageCount);

        for (int i = 0; i < messageCount; i++) {
            template.sendBody("seda:throughput?virtualThreadPerTask=true", "Message " + i);
        }

        mock.assertIsSatisfied();
    }

    @Test
    void testShutdownWithConcurrencyLimitCompletesQuickly() throws Exception {
        // Send messages so the route is actively used
        for (int i = 0; i < 5; i++) {
            template.sendBody("seda:limited?virtualThreadPerTask=true&concurrentConsumers=2", "Message " + i);
        }

        MockEndpoint mock = getMockEndpoint("mock:limited");
        mock.expectedMessageCount(5);
        mock.assertIsSatisfied();

        // Stop the context and verify it completes quickly.
        // Before the fix, the CountDownLatch was initialized with concurrentConsumers
        // count (2) but only 1 coordinator thread counts down, so prepareShutdown()
        // would wait the full shutdown timeout before proceeding.
        long start = System.nanoTime();
        context.stop();
        long elapsed = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);

        // Shutdown should complete well within the default timeout (300s).
        // Use a generous 30s bound to avoid flakiness, but this is still much less
        // than the full shutdown strategy timeout that would be hit without the fix.
        assertTrue(elapsed < 30, "Context stop took " + elapsed + "s, expected < 30s. "
                                 + "The CountDownLatch count likely does not match the coordinator thread count.");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:test?virtualThreadPerTask=true")
                        .to("log:result")
                        .to("mock:result");

                from("seda:limited?virtualThreadPerTask=true&concurrentConsumers=2")
                        .to("log:limited")
                        .to("mock:limited");

                from("seda:throughput?virtualThreadPerTask=true")
                        .to("log:throughput")
                        .to("mock:throughput");
            }
        };
    }
}
