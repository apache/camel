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
package org.apache.camel.component.disruptor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that reconfiguring a Disruptor (adding a consumer) works correctly while a producer is actively sending
 * messages, and that the new consumer receives messages sent after reconfiguration.
 */
public class DisruptorReconfigureWithBlockingProducerTest extends CamelTestSupport {

    private static final String DISRUPTOR_URI = "disruptor:foo?blockWhenFull=true";

    @Test
    void testDisruptorReconfigureWithBlockingProducer() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(20);
        getMockEndpoint("mock:b").expectedMinimumMessageCount(12);

        CountDownLatch reconfiguredLatch = new CountDownLatch(1);
        ProducerThread producerThread = new ProducerThread(reconfiguredLatch);
        producerThread.start();

        assertTrue(producerThread.awaitFirstBatchSent());

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("disruptor:foo?multipleConsumers=true&size=8").id("testRoute").to("mock:b");
            }
        });

        reconfiguredLatch.countDown();

        assertTrue(producerThread.checkResult());
        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("disruptor:foo?multipleConsumers=true&size=8").delay(200).to("mock:a");
            }
        };
    }

    private class ProducerThread extends Thread {
        private final CountDownLatch firstBatchSentLatch = new CountDownLatch(1);
        private final CountDownLatch reconfiguredLatch;
        private final CountDownLatch resultLatch = new CountDownLatch(1);
        private volatile Exception exception;

        ProducerThread(CountDownLatch reconfiguredLatch) {
            this.reconfiguredLatch = reconfiguredLatch;
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < 8; i++) {
                    template.sendBody(DISRUPTOR_URI, "Message");
                }

                firstBatchSentLatch.countDown();

                reconfiguredLatch.await(10, TimeUnit.SECONDS);

                for (int i = 0; i < 12; i++) {
                    template.sendBody(DISRUPTOR_URI, "Message");
                }
            } catch (Exception e) {
                exception = e;
                firstBatchSentLatch.countDown();
            } finally {
                resultLatch.countDown();
            }
        }

        public boolean awaitFirstBatchSent() throws InterruptedException {
            return firstBatchSentLatch.await(10, TimeUnit.SECONDS);
        }

        public boolean checkResult() throws Exception {
            boolean result = resultLatch.await(30, TimeUnit.SECONDS);
            if (exception != null) {
                throw exception;
            }
            return result;
        }
    }
}
