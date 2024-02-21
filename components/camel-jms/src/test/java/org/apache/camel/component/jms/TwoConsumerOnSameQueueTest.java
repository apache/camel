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

import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tags({ @Tag("not-parallel") })
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class TwoConsumerOnSameQueueTest extends AbstractPersistentJMSTest {

    @Test
    public void testTwoConsumerOnSameQueue() throws Exception {
        sendTwoMessagesWhichShouldReceivedOnBothEndpointsAndAssert();
    }

    @Test
    @DisabledIfSystemProperty(named = "ci.env.name", matches = "github.com", disabledReason = "Flaky on Github CI")
    public void testStopAndStartOneRoute() throws Exception {
        sendTwoMessagesWhichShouldReceivedOnBothEndpointsAndAssert();

        // now stop route A
        context.getRouteController().stopRoute("a");

        // send new message should go to B only
        MockEndpoint.resetMocks(context);

        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:b").expectedBodiesReceived("Bye World", "Bye World");

        template.sendBody("activemq:queue:TwoConsumerOnSameQueueTest", "Bye World");
        template.sendBody("activemq:queue:TwoConsumerOnSameQueueTest", "Bye World");

        MockEndpoint.assertIsSatisfied(context);

        // now start route A
        context.getRouteController().startRoute("a");

        // send new message should go to both A and B
        MockEndpoint.resetMocks(context);

        sendTwoMessagesWhichShouldReceivedOnBothEndpointsAndAssert();
    }

    @Test
    @DisabledIfSystemProperty(named = "ci.env.name", matches = "github.com", disabledReason = "Flaky on Github CI")
    public void testRemoveOneRoute() throws Exception {
        sendTwoMessagesWhichShouldReceivedOnBothEndpointsAndAssert();

        // now stop and remove route A
        context.getRouteController().stopRoute("a");
        assertTrue(context.removeRoute("a"));

        // send new message should go to B only
        MockEndpoint.resetMocks(context);

        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:b").expectedBodiesReceived("Bye World", "Bye World");

        template.sendBody("activemq:queue:TwoConsumerOnSameQueueTest", "Bye World");
        template.sendBody("activemq:queue:TwoConsumerOnSameQueueTest", "Bye World");

        MockEndpoint.assertIsSatisfied(context);
    }

    private void sendTwoMessagesWhichShouldReceivedOnBothEndpointsAndAssert() throws InterruptedException {
        final MockEndpoint mockB = getMockEndpoint("mock:b");
        final MockEndpoint mockA = getMockEndpoint("mock:a");

        template.sendBody("activemq:queue:TwoConsumerOnSameQueueTest", "Hello World");
        template.sendBody("activemq:queue:TwoConsumerOnSameQueueTest", "Hello World");

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(2, mockA.getReceivedCounter() + mockB.getReceivedCounter()));

        for (Exchange exchange : mockA.getReceivedExchanges()) {
            assertExchange(exchange);
        }

        for (Exchange exchange : mockB.getReceivedExchanges()) {
            assertExchange(exchange);
        }
    }

    private static void assertExchange(Exchange exchange) {
        assertNotNull(exchange.getIn(), "There should be an in message");
        assertNotNull(exchange.getIn().getBody(), "There should be an in body");
        assertNotNull(exchange.getIn().getBody(String.class), "The in message body should be of type String");
        assertEquals("Hello World", exchange.getIn().getBody(), "The in message body should be 'Hello World");
    }

    @AfterEach
    void resetMocks() {
        MockEndpoint.resetMocks(context);
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
