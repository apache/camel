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

package org.apache.camel.component.jms.integration.consumers;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractPersistentJMSTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the behavior of 2 consumers consuming multiple messages from the topic
 */
public class MultipleMessagesSameTopicIT extends AbstractPersistentJMSTest {

    @Test
    public void testMultipleMessagesOnSameTopic() throws Exception {
        getMockEndpoint("mock:a")
                .expectedBodiesReceived("Hello Camel 1", "Hello Camel 2", "Hello Camel 3", "Hello Camel 4");
        getMockEndpoint("mock:b")
                .expectedBodiesReceived("Hello Camel 1", "Hello Camel 2", "Hello Camel 3", "Hello Camel 4");

        template.sendBody("activemq:topic:MultipleMessagesSameTopicIT", "Hello Camel 1");
        template.sendBody("activemq:topic:MultipleMessagesSameTopicIT", "Hello Camel 2");
        template.sendBody("activemq:topic:MultipleMessagesSameTopicIT", "Hello Camel 3");
        template.sendBody("activemq:topic:MultipleMessagesSameTopicIT", "Hello Camel 4");

        MockEndpoint.assertIsSatisfied(context);
    }

    @BeforeEach
    void waitForConnections() {
        Awaitility.await().until(() -> context.getRoute("a").getUptimeMillis() > 200);
        Awaitility.await().until(() -> context.getRoute("b").getUptimeMillis() > 200);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:topic:MultipleMessagesSameTopicIT").routeId("a").to("log:a", "mock:a");

                from("activemq:topic:MultipleMessagesSameTopicIT").routeId("b").to("log:b", "mock:b");
            }
        };
    }
}
