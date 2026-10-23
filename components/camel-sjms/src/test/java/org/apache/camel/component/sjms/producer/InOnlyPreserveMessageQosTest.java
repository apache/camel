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
package org.apache.camel.component.sjms.producer;

import java.util.Map;

import jakarta.jms.DeliveryMode;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * preserveMessageQos=true applies the JMSPriority, JMSDeliveryMode and JMSExpiration headers also to InOnly sends, not
 * only to InOut sends, and a message without one of these headers gets the priority, delivery mode or time to live of
 * the endpoint.
 */
class InOnlyPreserveMessageQosTest extends JmsTestSupport {

    private static final String QUEUE = "sjms:queue:in.only.preserve.qos.InOnlyPreserveMessageQosTest";
    private static final String EXPIRY_QUEUE = "sjms:queue:in.only.preserve.qos.expiry.InOnlyPreserveMessageQosTest";
    private static final String REQUEST_QUEUE = "sjms:queue:in.only.preserve.qos.request.InOnlyPreserveMessageQosTest";
    private static final String REPLY_QUEUE = "sjms:queue:in.only.preserve.qos.reply.InOnlyPreserveMessageQosTest";

    @Test
    void priorityAndDeliveryModeFromTheHeadersAreUsed() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello World");
        result.message(0).header("JMSPriority").isEqualTo(8);
        result.message(0).header("JMSDeliveryMode").isEqualTo(DeliveryMode.NON_PERSISTENT);

        template.sendBodyAndHeaders(QUEUE + "?preserveMessageQos=true", "Hello World",
                Map.of("JMSPriority", 8, "JMSDeliveryMode", DeliveryMode.NON_PERSISTENT));

        result.assertIsSatisfied();
    }

    @Test
    void priorityAndDeliveryModeOfTheEndpointAreUsedWithoutHeaders() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello World");
        // a new JMS message has a priority (the default of the JMS client) and the delivery mode set by the binding
        // (PERSISTENT), which must not win over the endpoint when the route did not set the headers
        result.message(0).header("JMSPriority").isEqualTo(7);
        result.message(0).header("JMSDeliveryMode").isEqualTo(DeliveryMode.NON_PERSISTENT);

        template.sendBody(QUEUE + "?preserveMessageQos=true&priority=7&deliveryPersistent=false", "Hello World");

        result.assertIsSatisfied();
    }

    @Test
    void headersWinOverTheEndpoint() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello World");
        result.message(0).header("JMSPriority").isEqualTo(2);
        result.message(0).header("JMSDeliveryMode").isEqualTo(DeliveryMode.PERSISTENT);

        template.sendBodyAndHeaders(QUEUE + "?preserveMessageQos=true&priority=7&deliveryPersistent=false", "Hello World",
                Map.of("JMSPriority", 2, "JMSDeliveryMode", "PERSISTENT"));

        result.assertIsSatisfied();
    }

    @Test
    void requestUsesThePriorityAndDeliveryModeOfTheEndpointWithoutHeaders() throws Exception {
        MockEndpoint request = getMockEndpoint("mock:request");
        request.expectedBodiesReceived("Hello World");
        request.message(0).header("JMSPriority").isEqualTo(7);
        request.message(0).header("JMSDeliveryMode").isEqualTo(DeliveryMode.NON_PERSISTENT);

        Object reply = template.requestBody(REQUEST_QUEUE + "?preserveMessageQos=true&priority=7&deliveryPersistent=false",
                "Hello World");

        request.assertIsSatisfied();
        assertEquals("Bye World", reply);
    }

    @Test
    void replyOfAConsumerUsesThePriorityHeader() {
        Exchange out = template.request(REPLY_QUEUE, e -> e.getIn().setBody("Hello World"));

        assertEquals("Bye World", out.getMessage().getBody(String.class));
        // the consumer endpoint has preserveMessageQos=true and its route sets JMSPriority on the reply
        assertEquals(9, out.getMessage().getHeader("JMSPriority", Integer.class));
    }

    @Test
    void expirationFromTheHeaderIsUsed() throws Exception {
        long expiration = System.currentTimeMillis() + 600_000;

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello World");
        // without the header the message would never expire (JMSExpiration 0)
        result.message(0).header("JMSExpiration").isGreaterThan(expiration - 60_000);

        template.sendBodyAndHeader(QUEUE + "?preserveMessageQos=true", "Hello World", "JMSExpiration", expiration);

        result.assertIsSatisfied();
    }

    @Test
    void timeToLiveOfTheEndpointIsUsedForAMessageWithoutExpiration() throws Exception {
        long before = System.currentTimeMillis();

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello World");
        result.message(0).header("JMSExpiration").isGreaterThanOrEqualTo(before + 600_000);

        template.sendBody(QUEUE + "?preserveMessageQos=true&timeToLive=600000", "Hello World");

        result.assertIsSatisfied();
    }

    @Test
    void requestTimeoutIsUsedForARequestWithoutExpiration() throws Exception {
        long before = System.currentTimeMillis();

        MockEndpoint request = getMockEndpoint("mock:request");
        request.expectedBodiesReceived("Hello World");
        request.message(0).header("JMSExpiration").isGreaterThanOrEqualTo(before + 20_000);

        Object reply = template.requestBody(REQUEST_QUEUE + "?preserveMessageQos=true&requestTimeout=20000", "Hello World");

        request.assertIsSatisfied();
        assertEquals("Bye World", reply);
    }

    @Test
    void anExpiredMessageIsNotDelivered() throws Exception {
        MockEndpoint expiry = getMockEndpoint("mock:expiry");
        expiry.expectedBodiesReceived("Fresh");
        expiry.setAssertPeriod(500);

        // expired one minute ago: sent with the shortest time to live, so the broker drops it
        template.sendBodyAndHeader(EXPIRY_QUEUE + "?preserveMessageQos=true", "Stale", "JMSExpiration",
                System.currentTimeMillis() - 60_000);
        template.sendBody(EXPIRY_QUEUE, "Fresh");
        // the consumer starts only after both messages were sent
        context.getRouteController().startRoute("expiry");

        expiry.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(QUEUE).to("mock:result");

                from(REQUEST_QUEUE).to("mock:request").transform().constant("Bye World");

                from(REPLY_QUEUE + "?preserveMessageQos=true").setHeader("JMSPriority", constant(9))
                        .transform().constant("Bye World");

                from(EXPIRY_QUEUE).routeId("expiry").autoStartup(false).to("mock:expiry");
            }
        };
    }
}
