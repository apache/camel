/**
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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version
 */
public class DisruptorReconfigureWithBlockingProducer extends CamelTestSupport {

    @Test
    public void testDisruptorReconfigureWithBlockingProducer() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(20);
        getMockEndpoint("mock:b").expectedMinimumMessageCount(10);

        long beforeStart = System.currentTimeMillis();
        ProducerThread producerThread = new ProducerThread();
        producerThread.start();

        //synchronize with the producer to the point that the buffer is full
        assertTrue(producerThread.awaitFullBufferProduced());

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("disruptor:foo?multipleConsumers=true&size=8").id("testRoute").to("mock:b");
            }
        });

        // adding the consumer may take place after the current buffer is flushed
        // which will take approximately 8*200=1600 ms because of delay on route.
        // If the reconfigure does not correctly hold back the producer thread on this request,
        // it will take approximately 20*200=4000 ms.
        // be on the safe side and check that it was at least faster than 2 seconds.
        assertTrue("Reconfigure of Disruptor blocked", (System.currentTimeMillis() - beforeStart) < 2000);

        //Wait and check that the producer has produced all messages without throwing an exception
        assertTrue(producerThread.checkResult());
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("disruptor:foo?multipleConsumers=true&size=8").delay(200).to("mock:a");
            }
        };
    }

    private class ProducerThread extends Thread {
        private final CountDownLatch startedLatch = new CountDownLatch(1);
        private final CountDownLatch resultLatch = new CountDownLatch(1);
        private Exception exception;

        @Override
        public void run() {
            for (int i = 0; i < 8; i++) {
                template.sendBody("disruptor:foo", "Message");
            }

            startedLatch.countDown();

            try {
                for (int i = 0; i < 12; i++) {
                    template.sendBody("disruptor:foo", "Message");
                }
            } catch (Exception e) {
                exception = e;
            }

            resultLatch.countDown();
        }

        public boolean awaitFullBufferProduced() throws InterruptedException {
            return startedLatch.await(5, TimeUnit.SECONDS);
        }

        public boolean checkResult() throws Exception {
            if (exception != null) {
                throw exception;
            }
            boolean result = resultLatch.await(5, TimeUnit.SECONDS);
            if (exception != null) {
                throw exception;
            }

            return result;
        }
    }
}
