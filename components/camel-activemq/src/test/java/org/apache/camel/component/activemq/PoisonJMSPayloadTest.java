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
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.apache.xbean.spring.context.ClassPathXmlApplicationContext;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;

public class PoisonJMSPayloadTest extends CamelSpringTestSupport {

    @Test
    public void testCreateBodyThrowException() throws Exception {
        getMockEndpoint("mock:result-activemq").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        getMockEndpoint("mock:dead").message(0).body(String.class)
                .startsWith("Poison JMS message payload: Failed to extract body due to: javax.jms.JMSException: Failed to build body from content. Serializable class not available to broker.");

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

        // bean should not be invoked
        boolean invoked = context.getRegistry().lookupByNameAndType("myBean", MyBean.class).isInvoked();
        assertFalse("Bean should not be invoked", invoked);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        AbstractApplicationContext context = new ClassPathXmlApplicationContext("org/apache/camel/component/activemq/jms-createbody.xml");
        return context;
    }
}
