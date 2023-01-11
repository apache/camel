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
package org.apache.camel.component.activemq.converter;

import java.util.List;

import jakarta.jms.Message;
import jakarta.jms.TextMessage;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.activemq.support.ActiveMQTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.activemq.ActiveMQComponent.activeMQComponent;
import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 
 */
public class InvokeMessageListenerTest extends ActiveMQTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(InvokeMessageListenerTest.class);
    protected String startEndpointUri = "activemq:queue:test.a";
    protected ConsumerBean listener = new ConsumerBean();

    @Test
    public void testSendTextMessage() throws Exception {
        String expectedBody = "Hello there!";

        template.sendBodyAndHeader(startEndpointUri, expectedBody, "cheese", 123);

        listener.assertMessagesArrived(1, 5000);

        List<Message> list = listener.flushMessages();
        assertFalse(list.isEmpty(), "Should have received some messages!");
        Message message = list.get(0);

        LOG.debug("Received: {}", message);

        TextMessage textMessage = assertIsInstanceOf(TextMessage.class, message);
        assertEquals(expectedBody, textMessage.getText(), "Text message body: " + textMessage);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.addComponent("activemq", activeMQComponent(vmUri("?broker.persistent=false")));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(startEndpointUri).bean(listener);
            }
        };
    }
}
