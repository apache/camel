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

import javax.jms.ConnectionFactory;
import javax.jms.Destination;

import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.Consume;
import org.apache.camel.Header;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper.createConnectionFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsRequestReplyManualWithJMSReplyToTest extends AbstractJMSTest {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Consume("activemq:queue:fooJmsRequestReplyManualWithJMSReplyToTest")
    public void doSomething(@Header("JMSReplyTo") Destination jmsReplyTo, @Body String body) {
        assertEquals("Hello World", body);

        String endpointName = "activemq:" + jmsReplyTo.toString();
        template.sendBody(endpointName, "Bye World");
    }

    @Test
    public void testManualRequestReply() {
        context.start();

        // send an InOnly but force Camel to pass JMSReplyTo
        template.send("activemq:queue:fooJmsRequestReplyManualWithJMSReplyToTest?preserveMessageQos=true", exchange -> {
            exchange.getIn().setBody("Hello World");
            exchange.getIn().setHeader("JMSReplyTo", "barJmsRequestReplyManualWithJMSReplyToTest");
        });

        String reply = consumer.receiveBody("activemq:queue:barJmsRequestReplyManualWithJMSReplyToTest", 5000, String.class);
        assertEquals("Bye World", reply);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = createConnectionFactory(service);
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

}
