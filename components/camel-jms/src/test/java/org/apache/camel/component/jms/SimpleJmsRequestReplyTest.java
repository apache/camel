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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper.createConnectionFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SimpleJmsRequestReplyTest extends AbstractJMSTest {

    @Test
    public void testJmsRequestReply() {
        assertEquals("Hello A",
                template.requestBody("activemq:queue:SimpleJmsRequestReplyTest?replyToConsumerType=Simple", "A"));
        assertEquals("Hello B",
                template.requestBody("activemq:queue:SimpleJmsRequestReplyTest?replyToConsumerType=Simple", "B"));
        assertEquals("Hello C",
                template.requestBody("activemq:queue:SimpleJmsRequestReplyTest?replyToConsumerType=Simple", "C"));
        assertEquals("Hello D",
                template.requestBody("activemq:queue:SimpleJmsRequestReplyTest?replyToConsumerType=Simple", "D"));
        assertEquals("Hello E",
                template.requestBody("activemq:queue:SimpleJmsRequestReplyTest?replyToConsumerType=Simple", "E"));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory
                = createConnectionFactory(service);
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("activemq:queue:SimpleJmsRequestReplyTest")
                        .transform(body().prepend("Hello "));
            }
        };
    }
}
