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
package org.apache.camel.component.sjms.consumer;

import java.util.concurrent.CompletableFuture;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

public class JmsPollingConsumerTest extends JmsTestSupport {

    @Test
    public void testJmsPollingConsumerWait() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello Claus");

        // use another thread for polling consumer to demonstrate that we can wait before
        // the message is sent to the queue

        CompletableFuture.runAsync(() -> {
            String body = consumer.receiveBody("sjms:queue.start.JmsPollingConsumerTest", String.class);
            template.sendBody("sjms:queue.foo.JmsPollingConsumerTest", body + " Claus");
        });

        // wait a little to demonstrate we can start poll before we have a msg on the queue
        Thread.sleep(500);

        template.sendBody("direct:start", "Hello");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testJmsPollingConsumerNoWait() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello Claus");

        // use another thread for polling consumer to demonstrate that we can wait before
        // the message is sent to the queue
        CompletableFuture.runAsync(() -> {
            String body = consumer.receiveBodyNoWait("sjms:queue.start.JmsPollingConsumerTest", String.class);
            assertNull(body, "Should be null");

            template.sendBody("sjms:queue.foo.JmsPollingConsumerTest", "Hello Claus");
        });

        // wait a little to demonstrate we can start poll before we have a msg on the queue
        Thread.sleep(500);

        template.sendBody("direct:start", "Hello");

        MockEndpoint.assertIsSatisfied(context);

        // Consume the message
        consumer.receiveBody("sjms:queue.start.JmsPollingConsumerTest", String.class);
    }

    @Test
    public void testJmsPollingConsumerLowTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello Claus");

        // use another thread for polling consumer to demonstrate that we can wait before
        // the message is sent to the queue
        CompletableFuture.runAsync(() -> {
            String body = consumer.receiveBody("sjms:queue.start.JmsPollingConsumerTest", 100, String.class);
            assertNull(body, "Should be null");

            template.sendBody("sjms:queue.foo.JmsPollingConsumerTest", "Hello Claus");
        });

        // wait a little to demonstrate we can start poll before we have a msg on the queue
        Thread.sleep(500);

        template.sendBody("direct:start", "Hello");

        MockEndpoint.assertIsSatisfied(context);

        // Consume the message
        consumer.receiveBody("sjms:queue.start.JmsPollingConsumerTest", String.class);
    }

    @Test
    public void testJmsPollingConsumerHighTimeout() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello Claus");

        // use another thread for polling consumer to demonstrate that we can wait before
        // the message is sent to the queue
        CompletableFuture.runAsync(() -> {
            String body = consumer.receiveBody("sjms:queue.start.JmsPollingConsumerTest", 3000, String.class);
            template.sendBody("sjms:queue.foo.JmsPollingConsumerTest", body + " Claus");
        });

        // wait a little to demonstrate we can start poll before we have a msg on the queue
        Thread.sleep(500);

        template.sendBody("direct:start", "Hello");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").log("Sending ${body} to queue.start.JmsPollingConsumerTest")
                        .to("sjms:queue.start.JmsPollingConsumerTest");

                from("sjms:queue.foo.JmsPollingConsumerTest").log("Received ${body} from queue.start.JmsPollingConsumerTest")
                        .to("mock:result");
            }
        };
    }
}
