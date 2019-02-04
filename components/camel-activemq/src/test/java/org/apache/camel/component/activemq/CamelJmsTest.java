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
package org.apache.camel.component.activemq;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * 
 */
public class CamelJmsTest extends CamelSpringTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(CamelJmsTest.class);

    protected String expectedBody = "<hello>world!</hello>";

    @Test
    public void testSendingViaJmsIsReceivedByCamel() throws Exception {
        MockEndpoint result = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        result.expectedBodiesReceived(expectedBody);
        result.message(0).header("foo").isEqualTo("bar");

        // lets create a message
        Destination destination = getMandatoryBean(Destination.class, "sendTo");
        ConnectionFactory factory = getMandatoryBean(ConnectionFactory.class, "connectionFactory");

        Connection connection = factory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer producer = session.createProducer(destination);

        // now lets send a message
        ObjectMessage message = session.createObjectMessage(expectedBody);
        message.setStringProperty("foo", "bar");
        producer.send(message);

        result.assertIsSatisfied();

        LOG.info("Received message: " + result.getReceivedExchanges());
    }

    @Test
    public void testConsumingViaJMSReceivesMessageFromCamel() throws Exception {
        // lets create a message
        Destination destination = getMandatoryBean(Destination.class, "consumeFrom");
        ConnectionFactory factory = getMandatoryBean(ConnectionFactory.class, "connectionFactory");
        ProducerTemplate template = getMandatoryBean(ProducerTemplate.class, "camelTemplate");
        assertNotNull("template is valid", template);

        Connection connection = factory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        LOG.info("Consuming from: " + destination);
        MessageConsumer consumer = session.createConsumer(destination);

        // now lets send a message
        template.sendBody("seda:consumer", expectedBody);

        Message message = consumer.receive(5000);
        assertNotNull("Should have received a message from destination: " + destination, message);

        TextMessage textMessage = assertIsInstanceOf(TextMessage.class, message);
        assertEquals("Message body", expectedBody, textMessage.getText());

        LOG.info("Received message: " + message);
    }

    protected int getExpectedRouteCount() {
        return 0;
    }

    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/activemq/spring.xml");
    }
}
