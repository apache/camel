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

import java.util.List;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class BrowsableQueueTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(BrowsableQueueTest.class);

    protected String componentName = "activemq";
    protected Object[] expectedBodies = {"body1", "body2", "body3", "body4", "body5", "body6", "body7", "body8"};

    @Test
    public void testSendMessagesThenBrowseQueue() throws Exception {
        // send some messages
        for (int i = 0; i < expectedBodies.length; i++) {
            Object expectedBody = expectedBodies[i];
            template.sendBodyAndHeader("activemq:test.b", expectedBody, "counter", i);
        }

        // now lets browse the queue
        JmsQueueEndpoint endpoint = getMandatoryEndpoint("activemq:test.b?maximumBrowseSize=6", JmsQueueEndpoint.class);
        assertEquals(6, endpoint.getMaximumBrowseSize());
        List<Exchange> list = endpoint.getExchanges();
        LOG.debug("Received: " + list);
        assertEquals("Size of list", 6, endpoint.getExchanges().size());

        int index = -1;
        for (Exchange exchange : list) {
            String actual = exchange.getIn().getBody(String.class);
            LOG.debug("Received body: " + actual);

            Object expected = expectedBodies[++index];
            assertEquals("Body: " + index, expected, actual);
        }
    }

    @Test
    public void testSendMessagesThenBrowseQueueLimitNotHit() throws Exception {
        // send some messages
        for (int i = 0; i < expectedBodies.length; i++) {
            Object expectedBody = expectedBodies[i];
            template.sendBodyAndHeader("activemq:test.b", expectedBody, "counter", i);
        }

        // now lets browse the queue
        JmsQueueEndpoint endpoint = getMandatoryEndpoint("activemq:test.b?maximumBrowseSize=10", JmsQueueEndpoint.class);
        assertEquals(10, endpoint.getMaximumBrowseSize());
        List<Exchange> list = endpoint.getExchanges();
        LOG.debug("Received: " + list);
        assertEquals("Size of list", 8, endpoint.getExchanges().size());

        int index = -1;
        for (Exchange exchange : list) {
            String actual = exchange.getIn().getBody(String.class);
            LOG.debug("Received body: " + actual);

            Object expected = expectedBodies[++index];
            assertEquals("Body: " + index, expected, actual);
        }
    }

    @Test
    public void testSendMessagesThenBrowseQueueNoMax() throws Exception {
        // send some messages
        for (int i = 0; i < expectedBodies.length; i++) {
            Object expectedBody = expectedBodies[i];
            template.sendBodyAndHeader("activemq:test.b", expectedBody, "counter", i);
        }

        // now lets browse the queue
        JmsQueueEndpoint endpoint = getMandatoryEndpoint("activemq:test.b", JmsQueueEndpoint.class);
        assertEquals(-1, endpoint.getMaximumBrowseSize());
        List<Exchange> list = endpoint.getExchanges();
        LOG.debug("Received: " + list);
        assertEquals("Size of list", 8, endpoint.getExchanges().size());

        int index = -1;
        for (Exchange exchange : list) {
            String actual = exchange.getIn().getBody(String.class);
            LOG.debug("Received body: " + actual);

            Object expected = expectedBodies[++index];
            assertEquals("Body: " + index, expected, actual);
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent(componentName, jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("activemq:test.a").to("activemq:test.b");
            }
        };
    }
}
