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

package org.apache.camel.component.jms.polling;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JmsPollingConsumerNoWaitTest extends JmsPollingConsumerTest {
    private final CountDownLatch latch = new CountDownLatch(1);
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

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertNull(body, "Message body should be null because there was no message and the polling consumer is 'no wait'");

        template.sendBody("direct:start", "Hello");

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }
}
