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

import jakarta.jms.Message;

import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.activemq.support.ActiveMQTestSupport;
import org.apache.camel.component.jms.JmsMessage;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.activemq.ActiveMQComponent.activeMQComponent;
import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ActiveMQOriginalDestinationTest extends ActiveMQTestSupport {

    @Test
    public void testActiveMQOriginalDestination() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("activemq:queue:foo", "Hello World");

        MockEndpoint.assertIsSatisfied(context);

        // consume from bar
        Exchange out = consumer.receive("activemq:queue:bar", 5000);
        assertNotNull(out);

        // and we should have foo as the original destination
        JmsMessage msg = out.getIn(JmsMessage.class);
        Message jms = msg.getJmsMessage();
        ActiveMQMessage amq = assertIsInstanceOf(ActiveMQMessage.class, jms);
        ActiveMQDestination original = amq.getOriginalDestination();
        assertNotNull(original);
        assertEquals("foo", original.getPhysicalName());
        assertEquals("Queue", original.getDestinationTypeAsString());
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
            @Override
            public void configure() {
                from("activemq:queue:foo").to("activemq:queue:bar").to("mock:result");
            }
        };
    }

}
