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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.jms.client.ActiveMQQueue;
import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.Consume;
import org.apache.camel.Header;
import org.junit.jupiter.api.Test;
import org.springframework.jms.core.JmsTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JmsRequestReplyManualReplyTest extends AbstractJMSTest {

    private static volatile String tempName;
    private final CountDownLatch latch = new CountDownLatch(1);
    private JmsTemplate jms;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Consume("activemq:queue:JmsRequestReplyManualReplyTest")
    public void doSomething(@Header("JMSReplyTo") Destination jmsReplyTo, @Body String body) {
        assertEquals("Hello World", body);

        String endpointName = "activemq:" + jmsReplyTo.toString();
        endpointName = endpointName.replaceAll("//", ":");

        tempName = endpointName;
        latch.countDown();

        template.sendBody(tempName, "Bye World");
    }

    @Test
    public void testManualRequestReply() throws Exception {
        context.start();

        // send using pure JMS API to set a custom JMSReplyTo
        jms.send(new ActiveMQQueue("JmsRequestReplyManualReplyTest"), session -> {
            TextMessage msg = session.createTextMessage("Hello World");
            msg.setJMSReplyTo(new ActiveMQQueue("bar"));
            return msg;
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        String reply = consumer.receiveBody(tempName, 5000, String.class);
        assertEquals("Bye World", reply);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected JmsComponent setupComponent(
            CamelContext camelContext, ConnectionFactory connectionFactory, String componentName) {
        final JmsComponent component = super.setupComponent(camelContext, connectionFactory, componentName);

        jms = new JmsTemplate(connectionFactory);
        return component;
    }
}
