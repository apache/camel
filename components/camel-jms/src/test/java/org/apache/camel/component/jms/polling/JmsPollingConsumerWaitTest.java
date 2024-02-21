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

import static org.junit.jupiter.api.Assertions.assertFalse;

class JmsPollingConsumerWaitTest extends JmsPollingConsumerTest {

    private final CountDownLatch latch = new CountDownLatch(1);

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

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }
}
