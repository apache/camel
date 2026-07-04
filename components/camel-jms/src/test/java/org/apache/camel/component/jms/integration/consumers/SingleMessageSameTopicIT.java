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
import org.apache.camel.component.jms.JmsTestHelper;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SingleMessageSameTopicIT extends AbstractPersistentJMSTest {

    // topic subscriptions take a little longer to register than queue consumers, so keep the longer 200ms threshold
    private static final long TOPIC_ROUTE_UPTIME_MILLIS = 200;

    @BeforeEach
    void waitAndPrepare() {
        // JUnit does not honor @Order on @BeforeEach methods, so the wait and the send must live in a single
        // @BeforeEach to guarantee the consumer routes are ready before the message is published.
        JmsTestHelper.waitForJmsConsumerRoutes(context, TOPIC_ROUTE_UPTIME_MILLIS, "a", "b");

        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello World");

        template.sendBody("activemq:topic:SingleMessageSameTopicIT", "Hello World");
    }

    @Order(1)
    @Test
    void testTwoConsumerOnSameTopic() throws Exception {
        MockEndpoint.assertIsSatisfied(context);
    }

    @Order(2)
    @Test
    void testStopAndStartOneRoute() throws Exception {
        MockEndpoint.assertIsSatisfied(context);

        // now stop route A
        context.getRouteController().stopRoute("a");

        // send a new message should go to B only
        MockEndpoint.resetMocks(context);

        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:b").expectedBodiesReceived("Bye World");

        JmsTestHelper.waitForJmsConsumerRoutes(context, TOPIC_ROUTE_UPTIME_MILLIS, "b");
        template.sendBody("activemq:topic:SingleMessageSameTopicIT", "Bye World");

        MockEndpoint.assertIsSatisfied(context);

        // send new message should go to both A and B
        MockEndpoint.resetMocks(context);

        // now start route A
        context.getRouteController().startRoute("a");

        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello World");

        JmsTestHelper.waitForJmsConsumerRoutes(context, TOPIC_ROUTE_UPTIME_MILLIS, "a", "b");
        template.sendBody("activemq:topic:SingleMessageSameTopicIT", "Hello World");
    }

    @Order(3)
    @Test
    void testRemoveOneRoute() throws Exception {
        MockEndpoint.assertIsSatisfied(context);

        // now stop and remove route A
        context.getRouteController().stopRoute("a");
        assertTrue(context.removeRoute("a"));

        // send new message should go to B only
        MockEndpoint.resetMocks(context);

        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:b").expectedBodiesReceived("Bye World");

        JmsTestHelper.waitForJmsConsumerRoutes(context, TOPIC_ROUTE_UPTIME_MILLIS, "b");
        template.sendBody("activemq:topic:SingleMessageSameTopicIT", "Bye World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:topic:SingleMessageSameTopicIT").routeId("a")
                        .to("log:a", "mock:a");

                from("activemq:topic:SingleMessageSameTopicIT").routeId("b")
                        .to("log:b", "mock:b");
            }
        };
    }
}
