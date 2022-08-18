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
package org.apache.camel.component.jms;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JmsPollingConsumerTest extends AbstractJMSTest {

    @Nested
    class ConsumerWaitTest {

        private CountDownLatch latch = new CountDownLatch(1);

        @BeforeEach
        void setupConsumer() {
            // use another thread for polling consumer to demonstrate that we can wait before
            // the message is sent to the queue
            Executors.newSingleThreadExecutor().execute(() -> {
                String body = consumer.receiveBody("activemq:queue.JmsPollingConsumerTest.start.wait", String.class);
                template.sendBody("activemq:queue.JmsPollingConsumerTest.foo", body + " Claus");
                latch.countDown();
            });
        }

        @Test
        void testJmsPollingConsumerWait() throws Exception {
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedBodiesReceived("Hello Claus");

            /* Wait a little to demonstrate we can start poll before we have a msg on the queue. Also,
            because expect no message to be received, the latch should timeout. That is why we test for
            false.
             */
            assertFalse(latch.await(1, TimeUnit.SECONDS));

            template.sendBody("direct:start.wait", "Hello");

            assertMockEndpointsSatisfied();
        }
    }

    @Nested
    class ConsumerNoWaitTest {
        private CountDownLatch latch = new CountDownLatch(1);
        private volatile String body;

        @BeforeEach
        void setupConsumer() {
            // use another thread for polling consumer to demonstrate that we can wait before
            // the message is sent to the queue
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    body = consumer.receiveBodyNoWait("activemq:queue.JmsPollingConsumerTest.start", String.class);
                    template.sendBody("activemq:queue.JmsPollingConsumerTest.foo", "Hello Claus");
                } finally {
                    latch.countDown();
                }
            });
        }

        @Test
        public void testJmsPollingConsumerNoWait() throws Exception {
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedBodiesReceived("Hello Claus");

            // wait a little to demonstrate we can start poll before we have a msg on the queue
            assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
            assertNull(body, "Message body should be null because there was no message and the polling consumer is 'no wait'");

            template.sendBody("direct:start", "Hello");

            assertMockEndpointsSatisfied();
        }
    }

    @Nested
    class LowTimeoutTest {
        private CountDownLatch latch = new CountDownLatch(1);
        private volatile String body;

        @BeforeEach
        void setupConsumer() {
            // use another thread for polling consumer to demonstrate that we can wait before
            // the message is sent to the queue
            Executors.newSingleThreadExecutor().execute(() -> {
                body = consumer.receiveBody("activemq:queue.JmsPollingConsumerTest.start", 100, String.class);
                template.sendBody("activemq:queue.JmsPollingConsumerTest.foo", "Hello Claus");
                latch.countDown();
            });
        }

        @Test
        public void testJmsPollingConsumerLowTimeout() throws Exception {
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedBodiesReceived("Hello Claus");

            // wait a little to demonstrate we can start poll before we have a msg on the queue
            assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
            assertNull(body, "Message body should be null because the receive timed out");

            template.sendBody("direct:start", "Hello");

            assertMockEndpointsSatisfied();
        }
    }

    @Nested
    class HighTimeOutTest {
        private CountDownLatch latch = new CountDownLatch(1);

        @BeforeEach
        void setupConsumer() {
            // use another thread for polling consumer to demonstrate that we can wait before
            // the message is sent to the queue
            Executors.newSingleThreadExecutor().execute(() -> {
                String body = consumer.receiveBody("activemq:queue.JmsPollingConsumerTest.start", 3000, String.class);
                template.sendBody("activemq:queue.JmsPollingConsumerTest.foo", body + " Claus");
                latch.countDown();
            });
        }

        @Test
        public void testJmsPollingConsumerHighTimeout() throws Exception {
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedBodiesReceived("Hello Claus");

            // wait a little to demonstrate we can start poll before we have a msg on the queue
            assertFalse(latch.await(500, TimeUnit.MILLISECONDS),
                    "No message should have been received within 500 milliseconds");
            template.sendBody("direct:start", "Hello");

            assertMockEndpointsSatisfied();
        }
    }

    @AfterEach
    void cleanupConsumer() {
        // We must ensure there's nothing on the queue between test executions
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                consumer.receiveBody("activemq:queue.JmsPollingConsumerTest.start", 200, String.class);
            } catch (Exception e) {
                // This is usually safe to ignore (ie.: if not connected, not started, etc after the test was run)
            }
        });
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("activemq:queue.JmsPollingConsumerTest.start");

                from("direct:start.wait").to("activemq:queue.JmsPollingConsumerTest.start.wait");

                from("activemq:queue.JmsPollingConsumerTest.foo").to("mock:result");
            }
        };
    }
}
