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

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentClientAcknowledge;

/**
 * @version $Revision$
 */
public class JmsProducerWithJMSHeaderTest extends ContextTestSupport {

    public void testInOnlyJMSPrioritory() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSPriority").isEqualTo(2);

        template.sendBodyAndHeader("activemq:queue:foo?preserveMessageQos=true", "Hello World", "JMSPriority", "2");

        assertMockEndpointsSatisfied();
    }

    public void testInOnlyJMSPrioritoryZero() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSPriority").isEqualTo(0);

        template.sendBodyAndHeader("activemq:queue:foo?preserveMessageQos=true", "Hello World", "JMSPriority", "0");

        assertMockEndpointsSatisfied();
    }

    public void testInOnlyJMSPrioritoryNine() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSPriority").isEqualTo(9);

        template.sendBodyAndHeader("activemq:queue:foo?preserveMessageQos=true", "Hello World", "JMSPriority", "9");

        assertMockEndpointsSatisfied();
    }

    public void testInOnlyJMSExpiration() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        long ttl = System.currentTimeMillis() + 5000;
        template.sendBodyAndHeader("activemq:queue:bar?preserveMessageQos=true", "Hello World", "JMSExpiration", ttl);

        // sleep just a little
        Thread.sleep(2000);

        PollingConsumer consumer = context.getEndpoint("activemq:queue:bar").createPollingConsumer();
        consumer.start();

        // use timeout in case running on slow box
        Exchange bar = consumer.receive(10000);
        assertNotNull("Should be a message on queue", bar);

        consumer.stop();

        template.send("activemq:queue:foo", bar);

        assertMockEndpointsSatisfied();
    }

    public void testInOnlyJMSExpirationNoMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        long ttl = System.currentTimeMillis() + 2000;
        template.sendBodyAndHeader("activemq:queue:bar?preserveMessageQos=true", "Hello World", "JMSExpiration", ttl);

        // sleep more so the message is expired
        Thread.sleep(5000);

        PollingConsumer consumer = context.getEndpoint("activemq:queue:bar").createPollingConsumer();
        consumer.start();

        Exchange bar = consumer.receiveNoWait();
        assertNull("Should NOT be a message on queue", bar);

        consumer.stop();

        template.sendBody("activemq:queue:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testInOnlyMultipleJMSHeadersAndExpiration() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSPriority").isEqualTo(3);

        long ttl = System.currentTimeMillis() + 2000;
        Map headers = new HashMap();
        headers.put("JMSPriority", 3);
        headers.put("JMSExpiration", ttl);
        template.sendBodyAndHeaders("activemq:queue:bar?preserveMessageQos=true", "Hello World", headers);

        // sleep just a little
        Thread.sleep(50);

        PollingConsumer consumer = context.getEndpoint("activemq:queue:bar").createPollingConsumer();
        consumer.start();

        Exchange bar = consumer.receive(5000);
        assertNotNull("Should be a message on queue", bar);

        consumer.stop();

        template.send("activemq:queue:foo?preserveMessageQos=true", bar);

        Thread.sleep(1000);

        assertMockEndpointsSatisfied();
    }

    public void testInOnlyMultipleJMSHeadersAndExpirationNoMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        long ttl = System.currentTimeMillis() + 2000;
        Map headers = new HashMap();
        headers.put("JMSPriority", 3);
        headers.put("JMSExpiration", ttl);
        template.sendBodyAndHeaders("activemq:queue:bar?preserveMessageQos=true", "Hello World", headers);

        // sleep more so the message is expired
        Thread.sleep(5000);

        PollingConsumer consumer = context.getEndpoint("activemq:queue:bar").createPollingConsumer();
        consumer.start();

        Exchange bar = consumer.receiveNoWait();
        assertNull("Should NOT be a message on queue", bar);

        consumer.stop();

        template.sendBody("activemq:queue:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testInOnlyJMSDestinationName() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("JMSDestination").isNotNull();

        template.sendBodyAndHeader("activemq:queue:bar", "Hello World", "CamelJmsDestinationName", "foo");

        assertMockEndpointsSatisfied();

        assertEquals("queue://foo", mock.getReceivedExchanges().get(0).getIn().getHeader("JMSDestination", Destination.class).toString());
    }

    public void testInOutJMSDestinationName() throws Exception {
        String reply = (String) template.requestBodyAndHeader("activemq:queue:bar", "Hello World", "CamelJmsDestinationName", "reply");
        assertEquals("Bye World", reply);
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        camelContext.addComponent("activemq", jmsComponentClientAcknowledge(connectionFactory));

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
