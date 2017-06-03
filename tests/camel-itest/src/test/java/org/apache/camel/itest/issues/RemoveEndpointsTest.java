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
package org.apache.camel.itest.issues;

import java.util.Collection;

import javax.jms.ConnectionFactory;
import javax.naming.Context;

import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.CamelJmsTestHelper;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.jndi.JndiContext;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * @version 
 */
public class RemoveEndpointsTest extends CamelTestSupport {
    @Test
    public void testRemoveAllEndpoints() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:jms-queue");
        mock.expectedMessageCount(1);

        assertEquals(7, context.getEndpoints().size());

        template.sendBody("direct:foo", "Hello World");
        assertMockEndpointsSatisfied();

        Collection<Endpoint> list = context.removeEndpoints("*");
        assertEquals(7, list.size()); // all have been removed
        assertEquals(0, context.getEndpoints().size());
    }

    @Override
    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();

        // add ActiveMQ with embedded broker
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        JmsComponent amq = jmsComponentAutoAcknowledge(connectionFactory);
        amq.setCamelContext(context);

        answer.bind("jms", amq);
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").to("jms:queue:foo");
                from("jms:queue:foo").to("mock:jms-queue");
                from("jms:topic:bar").to("mock:jms-topic");
                from("seda:mem-queue").to("mock:seda-queue");
            }
        };
    }
}
