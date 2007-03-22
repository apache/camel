/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.jms;

import junit.framework.TestCase;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import static org.apache.camel.component.jms.JmsComponent.jmsComponentClientAcknowledge;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.TextMessage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @version $Revision$
 */
public class JmsRouteTest extends TestCase {
    private static final transient Log log = LogFactory.getLog(JmsRouteTest.class);
    protected JmsExchange receivedExchange;
    protected CamelContext container = new DefaultCamelContext();
    protected CountDownLatch latch = new CountDownLatch(1);
    protected Endpoint<JmsExchange> endpoint;

    public void testJmsRouteWithTextMessage() throws Exception {
        String expectedBody = "Hello there!";

        // now lets fire in a message
        JmsExchange exchange = endpoint.createExchange();
        JmsMessage in = exchange.getIn();
        in.setBody(expectedBody);
        in.setHeader("cheese", 123);
        endpoint.onExchange(exchange);

        // lets wait on the message being received
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Did not recieve the message!", received);

        assertNotNull(receivedExchange);

        Object body = receivedExchange.getIn().getBody();
        log.debug("Received body: " + body);
        assertEquals("body", expectedBody, body);

        Message jmsMessage = receivedExchange.getIn().getJmsMessage();
        assertTrue("Received a JMS TextMessage: " + jmsMessage, jmsMessage instanceof TextMessage);

        log.debug("Received JMS message: " + jmsMessage);
    }

    public void testJmsRouteWithObjectMessage() throws Exception {
        PurchaseOrder expectedBody = new PurchaseOrder("Beer", 10);

        // now lets fire in a message
        JmsExchange exchange = endpoint.createExchange();
        JmsMessage in = exchange.getIn();
        in.setBody(expectedBody);
        in.setHeader("cheese", 123);
        endpoint.onExchange(exchange);

        // lets wait on the message being received
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Did not recieve the message!", received);

        assertNotNull(receivedExchange);

        Object body = receivedExchange.getIn().getBody();
        log.debug("Received body: " + body);

        assertEquals("body", expectedBody, body);

        Message jmsMessage = receivedExchange.getIn().getJmsMessage();
        assertTrue("Received a JMS TextMessage: " + jmsMessage, jmsMessage instanceof ObjectMessage);

        log.debug("Received JMS message: " + jmsMessage);
    }

    @Override
    protected void setUp() throws Exception {
        // lets configure some componnets
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        container.addComponent("activemq", jmsComponentClientAcknowledge(connectionFactory));

        // lets add some routes
        container.setRoutes(new RouteBuilder() {
            public void configure() {
                from("jms:activemq:test.a").to("jms:activemq:test.b");
                from("jms:activemq:test.b").process(new Processor<JmsExchange>() {
                    public void onExchange(JmsExchange e) {
                        System.out.println("Received exchange: " + e.getIn());
                        receivedExchange = e;
                        latch.countDown();
                    }
                });
            }
        });
        endpoint = container.resolveEndpoint("jms:activemq:test.a");
        assertNotNull("No endpoint found!", endpoint);

        container.activateEndpoints();
    }

    @Override
    protected void tearDown() throws Exception {
        container.deactivateEndpoints();
    }
}
