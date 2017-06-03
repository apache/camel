/**
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

import javax.jms.ConnectionFactory;
import javax.jms.Destination;

import org.apache.activemq.command.ActiveMQQueue;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.apache.camel.component.jms.JmsConstants.JMS_X_GROUP_ID;

/**
 * @version 
 */
public class JmsProducerWithJMSHeaderTest extends CamelTestSupport {

    @Test
    public void testInOnlyJMSPrioritory() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSPriority").isEqualTo(2);

        template.sendBodyAndHeader("activemq:queue:foo?preserveMessageQos=true", "Hello World", "JMSPriority", "2");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyJMSPrioritoryZero() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSPriority").isEqualTo(0);

        template.sendBodyAndHeader("activemq:queue:foo?preserveMessageQos=true", "Hello World", "JMSPriority", "0");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyJMSPrioritoryNine() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSPriority").isEqualTo(9);

        template.sendBodyAndHeader("activemq:queue:foo?preserveMessageQos=true", "Hello World", "JMSPriority", "9");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyJMSPrioritoryTheDeliveryModeIsDefault() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSPriority").isEqualTo(2);
        // not provided as header but should use endpoint default then
        mock.message(0).header("JMSDeliveryMode").isEqualTo(2);

        template.sendBodyAndHeader("activemq:queue:foo?preserveMessageQos=true", "Hello World", "JMSPriority", "2");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyJMSDeliveryMode() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSDeliveryMode").isEqualTo(1);

        template.sendBodyAndHeader("activemq:queue:foo?preserveMessageQos=true", "Hello World", "JMSDeliveryMode", "1");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyJMSDeliveryModeAsString() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSDeliveryMode").isEqualTo(1);

        template.sendBodyAndHeader("activemq:queue:foo?preserveMessageQos=true", "Hello World", "JMSDeliveryMode", "NON_PERSISTENT");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyJMSExpiration() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        long ttl = System.currentTimeMillis() + 5000;
        template.sendBodyAndHeader("activemq:queue:bar?preserveMessageQos=true", "Hello World", "JMSExpiration", ttl);

        // sleep just a little
        Thread.sleep(2000);

        // use timeout in case running on slow box
        Exchange bar = consumer.receive("activemq:queue:bar", 10000);
        assertNotNull("Should be a message on queue", bar);

        template.send("activemq:queue:foo", bar);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyJMSExpirationNoMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        long ttl = System.currentTimeMillis() + 2000;
        template.sendBodyAndHeader("activemq:queue:bar?preserveMessageQos=true", "Hello World", "JMSExpiration", ttl);

        // sleep more so the message is expired
        Thread.sleep(5000);

        Exchange bar = consumer.receiveNoWait("activemq:queue:bar");
        assertNull("Should NOT be a message on queue", bar);

        template.sendBody("activemq:queue:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyMultipleJMSHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSPriority").isEqualTo(3);
        mock.message(0).header("JMSDeliveryMode").isEqualTo(2);

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("JMSPriority", 3);
        headers.put("JMSDeliveryMode", 2);
        template.sendBodyAndHeaders("activemq:queue:foo?preserveMessageQos=true", "Hello World", headers);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyMultipleJMSHeadersAndExpiration() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSPriority").isEqualTo(3);
        mock.message(0).header("JMSDeliveryMode").isEqualTo(2);

        long ttl = System.currentTimeMillis() + 2000;
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("JMSPriority", 3);
        headers.put("JMSDeliveryMode", 2);
        headers.put("JMSExpiration", ttl);
        template.sendBodyAndHeaders("activemq:queue:bar?preserveMessageQos=true", "Hello World", headers);

        // sleep just a little
        Thread.sleep(50);

        Exchange bar = consumer.receive("activemq:queue:bar", 5000);
        assertNotNull("Should be a message on queue", bar);

        template.send("activemq:queue:foo?preserveMessageQos=true", bar);

        Thread.sleep(1000);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyMultipleJMSHeadersAndExpirationNoMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        long ttl = System.currentTimeMillis() + 2000;
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("JMSPriority", 3);
        headers.put("JMSDeliveryMode", 2);
        headers.put("JMSExpiration", ttl);
        template.sendBodyAndHeaders("activemq:queue:bar?preserveMessageQos=true", "Hello World", headers);

        // sleep more so the message is expired
        Thread.sleep(5000);

        Exchange bar = consumer.receiveNoWait("activemq:queue:bar");
        assertNull("Should NOT be a message on queue", bar);

        template.sendBody("activemq:queue:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyJMSXGroupID() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header(JMS_X_GROUP_ID).isEqualTo("atom");

        template.sendBodyAndHeader("activemq:queue:foo", "Hello World", JMS_X_GROUP_ID, "atom");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInOnlyJMSDestination() throws Exception {
        Destination queue = new ActiveMQQueue("foo");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSDestination").isNotNull();

        template.sendBodyAndHeader("activemq:queue:bar", "Hello World", JmsConstants.JMS_DESTINATION, queue);

        assertMockEndpointsSatisfied();

        assertEquals("queue://foo", mock.getReceivedExchanges().get(0).getIn().getHeader("JMSDestination", Destination.class).toString());
    }

    @Test
    public void testInOnlyJMSDestinationName() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSDestination").isNotNull();

        template.sendBodyAndHeader("activemq:queue:bar", "Hello World", JmsConstants.JMS_DESTINATION_NAME, "foo");

        assertMockEndpointsSatisfied();

        assertEquals("queue://foo", mock.getReceivedExchanges().get(0).getIn().getHeader("JMSDestination", Destination.class).toString());
    }

    @Test
    public void testInOutJMSDestination() throws Exception {
        Destination queue = new ActiveMQQueue("reply");

        String reply = (String) template.requestBodyAndHeader("activemq:queue:bar", "Hello World", JmsConstants.JMS_DESTINATION, queue);
        assertEquals("Bye World", reply);
    }

    @Test
    public void testInOutJMSDestinationName() throws Exception {
        String reply = (String) template.requestBodyAndHeader("activemq:queue:bar", "Hello World", JmsConstants.JMS_DESTINATION_NAME, "reply");
        assertEquals("Bye World", reply);
    }

    @Test
    public void testInOnlyRouteJMSDestinationName() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("activemq:queue:a").to("activemq:queue:b");
                from("activemq:queue:b").to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.message(0).header("JMSDestination").isNotNull();

        template.sendBodyAndHeader("activemq:queue:bar", "Hello World", JmsConstants.JMS_DESTINATION_NAME, "a");

        assertMockEndpointsSatisfied();

        assertEquals("queue://b", mock.getReceivedExchanges().get(0).getIn().getHeader("JMSDestination", Destination.class).toString());
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("activemq:queue:foo").to("mock:result");

                from("activemq:queue:reply").transform(constant("Bye World"));

            }
        };
    }
}
