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

import java.util.List;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * @version $Revision$
 */
public class BrowsableQueueTest extends CamelTestSupport {
    private static final transient Log LOG = LogFactory.getLog(BrowsableQueueTest.class);

    protected String componentName = "activemq";
    protected String startEndpointUri;
    protected int counter;
    protected Object[] expectedBodies = {"body1", "body2"};

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
        assertEquals("Size of list", 2, endpoint.qeueSize());

        // for JMX stuff
        for (int i = 0; i < 2; i++) {
            String data = endpoint.browseExchange(i);
            assertNotNull(data);
        }

        int index = -1;
        for (Exchange exchange : list) {
            String actual = exchange.getIn().getBody(String.class);
            LOG.debug("Received body: " + actual);

            Object expected = expectedBodies[++index];
            assertEquals("Body: " + index, expected, actual);
        }
    }

    protected void sendExchange(final Object expectedBody) {
        template.sendBodyAndHeader(startEndpointUri, expectedBody, "counter", ++counter);
    }


    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false&broker.useJmx=false");
        camelContext.addComponent(componentName, jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("activemq:test.a").to("activemq:test.b");
            }
        };
    }
}