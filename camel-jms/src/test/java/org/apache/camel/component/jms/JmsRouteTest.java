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
import org.apache.camel.util.ProducerCache;
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
    protected ProducerCache<JmsExchange> client = new ProducerCache<JmsExchange>();

    public void testJmsRouteWithTextMessage() throws Exception {
        String expectedBody = "Hello there!";
        sendExchange(expectedBody);

        Object body = assertReceivedValidExchange(TextMessage.class);
        assertEquals("body", expectedBody, body);
    }

    public void testJmsRouteWithObjectMessage() throws Exception {
        PurchaseOrder expectedBody = new PurchaseOrder("Beer", 10);

        sendExchange(expectedBody);

        Object body = assertReceivedValidExchange(ObjectMessage.class);
        assertEquals("body", expectedBody, body);
    }

    protected void sendExchange(final Object expectedBody) {
        client.send(endpoint, new Processor<JmsExchange>() {
            public void onExchange(JmsExchange exchange) {
                // now lets fire in a message
                JmsMessage in = exchange.getIn();
                in.setBody(expectedBody);
                in.setHeader("cheese", 123);
            }
        });
    }

    protected Object assertReceivedValidExchange(Class type) throws Exception {
        // lets wait on the message being received
        boolean received = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Did not receive the message!", received);

        assertNotNull(receivedExchange);
        JmsMessage receivedMessage = receivedExchange.getIn();

        assertEquals("cheese header", 123, receivedMessage.getHeader("cheese"));
        Object body = receivedMessage.getBody();
        log.debug("Received body: " + body);
        Message jmsMessage = receivedMessage.getJmsMessage();
        assertTrue("Expected an instance of " + type.getName() + " but was " + jmsMessage, type.isInstance(jmsMessage));

        log.debug("Received JMS message: " + jmsMessage);
        return body;
    }

    @Override
    protected void setUp() throws Exception {
        // lets configure some componnets
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        container.addComponent("activemq", jmsComponentClientAcknowledge(connectionFactory));

        // lets add some routes
        container.addRoutes(new RouteBuilder() {
            public void configure() {
                from("jms:queue:test.a").to("jms:queue:test.b");
                from("jms:queue:test.b").process(new Processor<JmsExchange>() {
                    public void onExchange(JmsExchange e) {
                        System.out.println("Received exchange: " + e.getIn());
                        receivedExchange = e;
                        latch.countDown();
                    }
                });
            }
        });
        endpoint = container.resolveEndpoint("jms:queue:test.a");
        assertNotNull("No endpoint found!", endpoint);

        container.activateEndpoints();
    }

    @Override
    protected void tearDown() throws Exception {
        client.stop();
        container.deactivateEndpoints();
    }
}
