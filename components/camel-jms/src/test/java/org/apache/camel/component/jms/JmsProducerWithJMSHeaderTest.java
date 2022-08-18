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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.jms.Destination;

import org.apache.activemq.command.ActiveMQQueue;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.apache.camel.component.jms.JmsConstants.JMS_X_GROUP_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@Tags({ @Tag("slow") })
@Timeout(60)
public class JmsProducerWithJMSHeaderTest extends AbstractJMSTest {

    @Test
    public void testInOnlyJMSPrioritory() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSPriority").isEqualTo(2);

        template.sendBodyAndHeader("activemq:queue:fooJmsProducerWithJMSHeaderTest?preserveMessageQos=true", "Hello World",
                "JMSPriority", "2");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyJMSPrioritoryZero() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSPriority").isEqualTo(0);

        template.sendBodyAndHeader("activemq:queue:fooJmsProducerWithJMSHeaderTest?preserveMessageQos=true", "Hello World",
                "JMSPriority", "0");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyJMSPrioritoryNine() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSPriority").isEqualTo(9);

        template.sendBodyAndHeader("activemq:queue:fooJmsProducerWithJMSHeaderTest?preserveMessageQos=true", "Hello World",
                "JMSPriority", "9");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyJMSPrioritoryTheDeliveryModeIsDefault() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSPriority").isEqualTo(2);
        // not provided as header but should use endpoint default then
        mock.message(0).header("JMSDeliveryMode").isEqualTo(2);

        template.sendBodyAndHeader("activemq:queue:fooJmsProducerWithJMSHeaderTest?preserveMessageQos=true", "Hello World",
                "JMSPriority", "2");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyJMSDeliveryMode() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSDeliveryMode").isEqualTo(1);

        template.sendBodyAndHeader("activemq:queue:fooJmsProducerWithJMSHeaderTest?preserveMessageQos=true", "Hello World",
                "JMSDeliveryMode", "1");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyJMSDeliveryModeAsString() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSDeliveryMode").isEqualTo(1);

        template.sendBodyAndHeader("activemq:queue:fooJmsProducerWithJMSHeaderTest?preserveMessageQos=true", "Hello World",
                "JMSDeliveryMode",
                "NON_PERSISTENT");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyJMSExpiration() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        long ttl = System.currentTimeMillis() + 5000;
        template.sendBodyAndHeader("activemq:queue:barJmsProducerWithJMSHeaderTest?preserveMessageQos=true", "Hello World",
                "JMSExpiration", ttl);

        // sleep just a little
        Thread.sleep(2000);

        // use timeout in case running on slow box
        Exchange bar = consumer.receive("activemq:queue:barJmsProducerWithJMSHeaderTest", 10000);
        assertNotNull(bar, "Should be a message on queue");

        template.send("activemq:queue:fooJmsProducerWithJMSHeaderTest", bar);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyJMSExpirationNoMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        long ttl = System.currentTimeMillis() + 2000;
        template.sendBodyAndHeader("activemq:queue:barJmsProducerWithJMSHeaderTest?preserveMessageQos=true", "Hello World",
                "JMSExpiration", ttl);

        // sleep more so the message is expired
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> assertThat(consumer.receiveNoWait("activemq:queue:barJmsProducerWithJMSHeaderTest")).isNull());
        template.sendBody("activemq:queue:fooJmsProducerWithJMSHeaderTest", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyMultipleJMSHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSPriority").isEqualTo(3);
        mock.message(0).header("JMSDeliveryMode").isEqualTo(2);

        Map<String, Object> headers = new HashMap<>();
        headers.put("JMSPriority", 3);
        headers.put("JMSDeliveryMode", 2);
        template.sendBodyAndHeaders("activemq:queue:fooJmsProducerWithJMSHeaderTest?preserveMessageQos=true", "Hello World",
                headers);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyMultipleJMSHeadersAndExpiration() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSPriority").isEqualTo(3);
        mock.message(0).header("JMSDeliveryMode").isEqualTo(2);

        long ttl = System.currentTimeMillis() + 2000;
        Map<String, Object> headers = new HashMap<>();
        headers.put("JMSPriority", 3);
        headers.put("JMSDeliveryMode", 2);
        headers.put("JMSExpiration", ttl);
        template.sendBodyAndHeaders("activemq:queue:barJmsProducerWithJMSHeaderTest?preserveMessageQos=true", "Hello World",
                headers);

        // sleep just a little
        Thread.sleep(50);

        Exchange bar = consumer.receive("activemq:queue:barJmsProducerWithJMSHeaderTest", 5000);
        assertNotNull(bar, "Should be a message on queue");
        template.send("activemq:queue:fooJmsProducerWithJMSHeaderTest?preserveMessageQos=true", bar);

        Thread.sleep(1000);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyMultipleJMSHeadersAndExpirationNoMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        long ttl = System.currentTimeMillis() + 2000;
        Map<String, Object> headers = new HashMap<>();
        headers.put("JMSPriority", 3);
        headers.put("JMSDeliveryMode", 2);
        headers.put("JMSExpiration", ttl);
        template.sendBodyAndHeaders("activemq:queue:barJmsProducerWithJMSHeaderTest?preserveMessageQos=true", "Hello World",
                headers);

        // sleep more so the message is expired
        await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> assertThat(consumer.receiveNoWait("activemq:queue:barJmsProducerWithJMSHeaderTest")).isNull());

        template.sendBody("activemq:queue:fooJmsProducerWithJMSHeaderTest", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyJMSXGroupID() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header(JMS_X_GROUP_ID).isEqualTo("atom");

        template.sendBodyAndHeader("activemq:queue:fooJmsProducerWithJMSHeaderTest", "Hello World", JMS_X_GROUP_ID, "atom");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyJMSDestination() throws Exception {
        Destination queue = new ActiveMQQueue("fooJmsProducerWithJMSHeaderTest");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSDestination").isNotNull();

        template.sendBodyAndHeader("activemq:queue:barJmsProducerWithJMSHeaderTest", "Hello World",
                JmsConstants.JMS_DESTINATION, queue);

        assertMockEndpointsSatisfied();

        assertEquals("queue://fooJmsProducerWithJMSHeaderTest",
                mock.getReceivedExchanges().get(0).getIn().getHeader("JMSDestination", Destination.class).toString());
    }

    @Test
    public void testInOnlyJMSDestinationName() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSDestination").isNotNull();

        template.sendBodyAndHeader("activemq:queue:barJmsProducerWithJMSHeaderTest", "Hello World",
                JmsConstants.JMS_DESTINATION_NAME, "fooJmsProducerWithJMSHeaderTest");

        assertMockEndpointsSatisfied();

        assertEquals("queue://fooJmsProducerWithJMSHeaderTest",
                mock.getReceivedExchanges().get(0).getIn().getHeader("JMSDestination", Destination.class).toString());
    }

    @Test
    public void testInOutJMSDestination() {
        Destination queue = new ActiveMQQueue("replyJmsProducerWithJMSHeaderTest");

        String reply = (String) template.requestBodyAndHeader("activemq:queue:barJmsProducerWithJMSHeaderTest", "Hello World",
                JmsConstants.JMS_DESTINATION,
                queue);
        assertEquals("Bye World", reply);
    }

    @Test
    public void testInOutJMSDestinationName() {
        String reply = (String) template.requestBodyAndHeader("activemq:queue:barJmsProducerWithJMSHeaderTest", "Hello World",
                JmsConstants.JMS_DESTINATION_NAME, "replyJmsProducerWithJMSHeaderTest");
        assertEquals("Bye World", reply);
    }

    @Test
    public void testInOnlyRouteJMSDestinationName() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("activemq:queue:aJmsProducerWithJMSHeaderTest").to("activemq:queue:bJmsProducerWithJMSHeaderTest");
                from("activemq:queue:bJmsProducerWithJMSHeaderTest").to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.message(0).header("JMSDestination").isNotNull();

        template.sendBodyAndHeader("activemq:queue:barJmsProducerWithJMSHeaderTest", "Hello World",
                JmsConstants.JMS_DESTINATION_NAME, "aJmsProducerWithJMSHeaderTest");

        assertMockEndpointsSatisfied();

        assertEquals("queue://bJmsProducerWithJMSHeaderTest",
                mock.getReceivedExchanges().get(0).getIn().getHeader("JMSDestination", Destination.class).toString());
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:fooJmsProducerWithJMSHeaderTest").to("mock:result");

                from("activemq:queue:replyJmsProducerWithJMSHeaderTest").transform(constant("Bye World"));

            }
        };
    }
}
