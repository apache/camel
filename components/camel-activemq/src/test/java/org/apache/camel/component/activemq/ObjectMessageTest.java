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
package org.apache.camel.component.activemq;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.Exchange;
import org.apache.camel.component.jms.JmsBinding;
import org.apache.camel.component.jms.JmsMessage;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.apache.xbean.spring.context.ClassPathXmlApplicationContext;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;

public class ObjectMessageTest extends CamelSpringTestSupport {

    @Test
    public void testUntrusted() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("vm://localhost");
        Connection conn = factory.createConnection();
        conn.start();
        Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer producer = sess.createProducer(sess.createTopic("foo"));
        ObjectMessage msg = sess.createObjectMessage();
        ObjectPayload payload = new ObjectPayload();
        payload.payload = "test";
        msg.setObject(payload);
        producer.send(msg);

        Thread.sleep(1000);

        MockEndpoint resultActiveMQ = resolveMandatoryEndpoint("mock:result-activemq", MockEndpoint.class);
        resultActiveMQ.expectedMessageCount(1);
        resultActiveMQ.assertIsNotSatisfied();

        MockEndpoint resultTrusted = resolveMandatoryEndpoint("mock:result-trusted", MockEndpoint.class);
        resultTrusted.expectedMessageCount(1);
        resultTrusted.assertIsSatisfied();
        assertCorrectObjectReceived(resultTrusted);

        MockEndpoint resultCamel = resolveMandatoryEndpoint("mock:result-camel", MockEndpoint.class);
        resultCamel.expectedMessageCount(1);
        resultCamel.assertIsSatisfied();
        assertCorrectObjectReceived(resultCamel);

        MockEndpoint resultEmpty = resolveMandatoryEndpoint("mock:result-empty", MockEndpoint.class);
        resultEmpty.expectedMessageCount(1);
        resultEmpty.assertIsNotSatisfied();

    }

    protected void assertCorrectObjectReceived(MockEndpoint result) {
        Exchange exchange = result.getReceivedExchanges().get(0);
        // This should be a JMS Exchange
        assertNotNull(ExchangeHelper.getBinding(exchange, JmsBinding.class));
        JmsMessage in = (JmsMessage)exchange.getIn();
        assertNotNull(in);
        assertIsInstanceOf(ObjectMessage.class, in.getJmsMessage());

        ObjectPayload received = exchange.getIn().getBody(ObjectPayload.class);
        assertEquals("test", received.payload);
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        AbstractApplicationContext context = new ClassPathXmlApplicationContext("org/apache/camel/component/activemq/jms-object-message.xml");
        return context;
    }
}
