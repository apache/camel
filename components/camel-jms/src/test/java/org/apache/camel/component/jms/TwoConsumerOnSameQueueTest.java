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

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class TwoConsumerOnSameQueueTest extends AbstractPersistentJMSTest {

    @Test
    public void testTwoConsumerOnSameQueue() throws Exception {
        sendTwoMessagesWhichShouldReceivedOnBothEndpointsAndAssert();
    }

    @Test
    public void testStopAndStartOneRoute() throws Exception {
        sendTwoMessagesWhichShouldReceivedOnBothEndpointsAndAssert();

        // now stop route A
        context.getRouteController().stopRoute("a");

        // send new message should go to B only
        resetMocks();

        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:b").expectedBodiesReceived("Bye World", "Bye World");

        template.sendBody("activemq:queue:TwoConsumerOnSameQueueTest", "Bye World");
        template.sendBody("activemq:queue:TwoConsumerOnSameQueueTest", "Bye World");

        assertMockEndpointsSatisfied();

        // now start route A
        context.getRouteController().startRoute("a");

        // send new message should go to both A and B
        resetMocks();

        sendTwoMessagesWhichShouldReceivedOnBothEndpointsAndAssert();
    }

    @Test
    public void testRemoveOneRoute() throws Exception {
        sendTwoMessagesWhichShouldReceivedOnBothEndpointsAndAssert();

        // now stop and remove route A
        context.getRouteController().stopRoute("a");
        assertTrue(context.removeRoute("a"));

        // send new message should go to B only
        resetMocks();

        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:b").expectedBodiesReceived("Bye World", "Bye World");

        template.sendBody("activemq:queue:TwoConsumerOnSameQueueTest", "Bye World");
        template.sendBody("activemq:queue:TwoConsumerOnSameQueueTest", "Bye World");

        assertMockEndpointsSatisfied();
    }

    private void sendTwoMessagesWhichShouldReceivedOnBothEndpointsAndAssert() throws InterruptedException {
        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello World");

        template.sendBody("activemq:queue:TwoConsumerOnSameQueueTest", "Hello World");
        template.sendBody("activemq:queue:TwoConsumerOnSameQueueTest", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:TwoConsumerOnSameQueueTest").routeId("a")
                        .to("log:a", "mock:a");

                from("activemq:queue:TwoConsumerOnSameQueueTest").routeId("b")
                        .to("log:b", "mock:b");
            }
        };
    }
}
