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

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class JmsPollingHighTimeOutTest extends JmsPollingConsumerTest {
    private volatile String body;

    @BeforeEach
    void setupConsumer() {
        Executors.newSingleThreadExecutor().execute(() -> {
            body = consumer.receiveBody("activemq:queue.JmsPollingConsumerTest.start", 3000, String.class);
            template.sendBody("activemq:queue.JmsPollingConsumerTest.foo", body + " Claus");
        });
    }

    @Test
    public void testJmsPollingConsumerHighTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello Claus");

        assertNull(body, "No message should have been received because the test has not sent any");
        template.sendBody("direct:start", "Hello");
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() -> body != null);

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }
}
