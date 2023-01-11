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

import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.MessageProducer;
import jakarta.jms.ObjectMessage;
import jakarta.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.component.activemq.support.ActiveMQSpringTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class PoisonJMSPayloadTest extends ActiveMQSpringTestSupport {

    private ActiveMQConnectionFactory factory;
    private Session sess;

    @BeforeEach
    public void setupTest() throws JMSException {
        getMockEndpoint("mock:result-activemq").expectedMessageCount(0);
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        getMockEndpoint("mock:dead").message(0).body(String.class)
                .startsWith(
                        "Poison JMS message payload: Failed to extract body due to: jakarta.jms.JMSException: Failed to build body from content. Serializable class not available to broker.");

        factory = new ActiveMQConnectionFactory(vmUri());
        Connection conn = factory.createConnection();
        conn.start();
        sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

        MessageProducer producer = sess.createProducer(sess.createTopic("foo"));
        ObjectMessage msg = sess.createObjectMessage();

        ObjectPayload payload = new ObjectPayload();
        payload.payload = "test";
        msg.setObject(payload);
        producer.send(msg);
    }

    @Test
    public void testCreateBodyThrowException() throws Exception {
        // bean should not be invoked
        boolean invoked = context.getRegistry().lookupByNameAndType("myBean", MyBean.class).isInvoked();
        assertFalse(invoked, "Bean should not be invoked");

        MockEndpoint.assertIsSatisfied(context);
    }

}
