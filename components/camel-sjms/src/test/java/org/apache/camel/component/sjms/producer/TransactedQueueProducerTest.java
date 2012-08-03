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
package org.apache.camel.component.sjms.producer;

import java.util.Enumeration;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.component.sjms.jms.JmsObjectFactory;
import org.apache.camel.test.junit4.CamelTestSupport;

import org.junit.Test;

public class TransactedQueueProducerTest extends CamelTestSupport {
    
    private static final String TEST_DESTINATION_NAME = "transacted.test.queue";

    @Produce
    protected ProducerTemplate template;
    protected ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://broker?broker.useJmx=false");
    private Connection connection;
    private Session session;
    
    public TransactedQueueProducerTest() {
    }

    @Override
    protected boolean useJmx() {
        return false;
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        connectionFactory = new ActiveMQConnectionFactory("vm://broker?broker.persistent=false&broker.useJmx=false");
        connection = connectionFactory.createConnection();
        connection.start();
        session = connection.createSession(true, Session.SESSION_TRANSACTED);
    }

    @Override
    public void tearDown() throws Exception {
        if (session != null) {
            session.close();
        }
        if (connection != null) {
            connection.stop();
        }
        super.tearDown();
    }

    @Test
    public void testTransactedQueueProducer() throws Exception {
        MessageConsumer mc = JmsObjectFactory.createQueueConsumer(session, TEST_DESTINATION_NAME);
        assertNotNull(mc);
        final String expectedBody = "Hello World!";
        MockEndpoint mock = getMockEndpoint("mock:result");

        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(expectedBody);

        template.sendBody("direct:start.transacted", expectedBody);
        mc.receive(5000);
        session.rollback();
        Message message = mc.receive(5000);
        session.commit();
        assertNotNull(message);
        assertTrue(message instanceof TextMessage);
        
        TextMessage tm = (TextMessage) message;
        String text = tm.getText();
        assertNotNull(text);
        
        template.sendBody("direct:finish", text);
        
        mock.assertIsSatisfied();
        mc.close();

    }

    @Test
    public void testTransactedQueueProducerAsynchronousOverride() throws Exception {
        MessageConsumer mc = JmsObjectFactory.createQueueConsumer(session, TEST_DESTINATION_NAME);
        assertNotNull(mc);
        final String expectedBody = "Hello World!";
        MockEndpoint mock = getMockEndpoint("mock:result");

        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(expectedBody);

        template.sendBody("direct:start.transacted.async.override", expectedBody);
        mc.receive(5000);
        session.rollback();
        Message message = mc.receive(5000);
        session.commit();
        assertNotNull(message);
        assertTrue(message instanceof TextMessage);
        
        TextMessage tm = (TextMessage) message;
        String text = tm.getText();
        assertNotNull(text);
        
        template.sendBody("direct:finish", text);
        
        mock.assertIsSatisfied();
        mc.close();

    }

    @Test
    public void testTransactedQueueProducerFailed() throws Exception {
        MessageConsumer mc = JmsObjectFactory.createQueueConsumer(session, TEST_DESTINATION_NAME);
        assertNotNull(mc);
        final String expectedBody = "Transaction Failed";
        MockEndpoint mock = getMockEndpoint("mock:result");

        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(expectedBody);

        template.sendBody("direct:start.transacted", expectedBody);
        mc.receive(5000);
        session.rollback();
        Enumeration<?> enumeration = session.createBrowser(session.createQueue(TEST_DESTINATION_NAME)).getEnumeration();
        while (enumeration.hasMoreElements()) {
            TextMessage tm = (TextMessage) enumeration.nextElement();
            String text = tm.getText();
            log.info("Element from Enumeration: {}", text);  
            assertNotNull(text);
            
            template.sendBody("direct:finish", text);
        }
        
        mock.assertIsSatisfied();
        mc.close();
    }
    
    /*
     * @see org.apache.camel.test.junit4.CamelTestSupport#createCamelContext()
     *
     * @return
     * @throws Exception
     */
    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        SjmsComponent component = new SjmsComponent();
        component.setMaxConnections(1);
        component.setConnectionFactory(connectionFactory);
        camelContext.addComponent("sjms", component);
        return camelContext;
    }

    /*
     * @see org.apache.camel.test.junit4.CamelTestSupport#createRouteBuilder()
     *
     * @return
     * @throws Exception
     */
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                
                from("direct:start.transacted")
                    .to("sjms:queue:" + TEST_DESTINATION_NAME + "?transacted=true");
                
                from("direct:start.transacted.async.override")
                    .to("sjms:queue:" + TEST_DESTINATION_NAME + "?transacted=true&synchronous=false");
                
                from("direct:finish")
                    .to("log:test.log.1?showBody=true", "mock:result");
            }
        };
    }
}
