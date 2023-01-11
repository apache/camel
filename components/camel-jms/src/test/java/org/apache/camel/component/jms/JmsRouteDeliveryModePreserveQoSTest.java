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

import java.util.Map;

import jakarta.jms.DeliveryMode;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit test for preserveMessageQos with delivery mode
 */
public class JmsRouteDeliveryModePreserveQoSTest extends AbstractPersistentJMSTest {

    @Test
    public void testSendDefault() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:JmsRouteDeliveryModePreserveQoSTest.bar");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("activemq:queue:JmsRouteDeliveryModePreserveQoSTest.foo?preserveMessageQos=true", "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        // should be persistent by default
        Map<String, Object> map = mock.getReceivedExchanges().get(0).getIn().getHeaders();
        assertNotNull(map);
        assertEquals(DeliveryMode.PERSISTENT, map.get("JMSDeliveryMode"));
    }

    @Test
    public void testSendNonPersistent() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:JmsRouteDeliveryModePreserveQoSTest.bar");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("activemq:queue:JmsRouteDeliveryModePreserveQoSTest.foo?preserveMessageQos=true",
                "Hello World", "JMSDeliveryMode",
                DeliveryMode.NON_PERSISTENT);

        MockEndpoint.assertIsSatisfied(context);

        // should preserve non persistent
        Map<String, Object> map = mock.getReceivedExchanges().get(0).getIn().getHeaders();
        assertNotNull(map);
        assertEquals(DeliveryMode.NON_PERSISTENT, map.get("JMSDeliveryMode"));
    }

    @Test
    public void testSendNonPersistentAsString() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:JmsRouteDeliveryModePreserveQoSTest.bar");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("activemq:queue:JmsRouteDeliveryModePreserveQoSTest.foo?preserveMessageQos=true",
                "Hello World", "JMSDeliveryMode",
                "NON_PERSISTENT");

        MockEndpoint.assertIsSatisfied(context);

        // should preserve non persistent
        Map<String, Object> map = mock.getReceivedExchanges().get(0).getIn().getHeaders();
        assertNotNull(map);
        assertEquals(DeliveryMode.NON_PERSISTENT, map.get("JMSDeliveryMode"));
    }

    @Test
    public void testSendPersistent() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:JmsRouteDeliveryModePreserveQoSTest.bar");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("activemq:queue:JmsRouteDeliveryModePreserveQoSTest.foo?preserveMessageQos=true",
                "Hello World", "JMSDeliveryMode",
                DeliveryMode.PERSISTENT);

        MockEndpoint.assertIsSatisfied(context);

        // should preserve persistent
        Map<String, Object> map = mock.getReceivedExchanges().get(0).getIn().getHeaders();
        assertNotNull(map);
        assertEquals(DeliveryMode.PERSISTENT, map.get("JMSDeliveryMode"));
    }

    @Test
    public void testSendPersistentAsString() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:JmsRouteDeliveryModePreserveQoSTest.bar");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("activemq:queue:JmsRouteDeliveryModePreserveQoSTest.foo?preserveMessageQos=true",
                "Hello World", "JMSDeliveryMode",
                "PERSISTENT");

        MockEndpoint.assertIsSatisfied(context);

        // should preserve persistent
        Map<String, Object> map = mock.getReceivedExchanges().get(0).getIn().getHeaders();
        assertNotNull(map);
        assertEquals(DeliveryMode.PERSISTENT, map.get("JMSDeliveryMode"));
    }

    @Test
    @Disabled("Artemis does not fallback to deliveryMode 1 automatically, therefore disabling the test")
    public void testNonJmsDeliveryMode() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:JmsRouteDeliveryModePreserveQoSTest.bar");
        mock.expectedBodiesReceived("Beer is good...");

        // since we're using activemq, we really cannot set a delivery mode to something other
        // than 1 or 2 (NON-PERSISTENT or PERSISTENT, respectively). but this test does try to
        // set the delivery mode to '3'... so ActiveMQ will just test to see whether it's persistent
        // by testing deliverMode == 2 ... but it won't be... so it will evaluate to NON-PERSISTENT..
        // so our test asserts the deliveryMode was changed to 1 (NON-PERSISTENT), as 2 (PERSISTENT) is the default.
        // we would need an in memory broker that does allow non-jms delivery modes to really
        // test this right....
        mock.message(0).header("JMSDeliveryMode").isEqualTo(1);

        template.sendBody("direct:nonJmsDeliveryMode", "Beer is good...");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testNonJmsDeliveryModeDisableExplicityQos() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:JmsRouteDeliveryModePreserveQoSTest.bar");
        mock.expectedBodiesReceived("Beer is good...");

        // in this test, we're using explicitQosEnabled=false so we will not rely on our
        // settings, we will rely on whatever is created in the Message creator, which
        // should default to default message QoS, namely, PERSISTENT deliveryMode
        mock.message(0).header("JMSDeliveryMode").isEqualTo(2);

        template.sendBody("direct:noExplicitNonJmsDeliveryMode", "Beer is good...");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    @Disabled("Artemis does not fallback to deliveryMode 1 automatically, therefore disabling the test")
    public void testNonJmsDeliveryModePreserveQos() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:JmsRouteDeliveryModePreserveQoSTest.bar");
        mock.expectedBodiesReceived("Beer is good...");

        // in this test, we can only pass if we are "preserving" existing deliveryMode.
        // this means camel expects to have an existing QoS set as a header, or it will pick
        // from the JMS message created by the message creator
        // otherwise, "preserveMessageQos==true" does not allow us to explicity set the deliveryMode
        // on the message
        mock.message(0).header("JMSDeliveryMode").isEqualTo(1);

        template.sendBodyAndHeader("direct:preserveQosNonJmsDeliveryMode", "Beer is good...", JmsConstants.JMS_DELIVERY_MODE,
                3);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:JmsRouteDeliveryModePreserveQoSTest.foo")
                        .to("activemq:queue:JmsRouteDeliveryModePreserveQoSTest.bar?preserveMessageQos=true");

                from("activemq:queue:JmsRouteDeliveryModePreserveQoSTest.bar")
                        .to("mock:JmsRouteDeliveryModePreserveQoSTest.bar");

                from("direct:nonJmsDeliveryMode").to("activemq:queue:JmsRouteDeliveryModePreserveQoSTest.bar?deliveryMode=3");
                from("direct:noExplicitNonJmsDeliveryMode")
                        .to("activemq:queue:JmsRouteDeliveryModePreserveQoSTest.bar?deliveryMode=3&explicitQosEnabled=false");
                from("direct:preserveQosNonJmsDeliveryMode")
                        .to("activemq:queue:JmsRouteDeliveryModePreserveQoSTest.bar?preserveMessageQos=true");
            }
        };
    }
}
