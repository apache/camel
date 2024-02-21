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
package org.apache.camel.component.jms.issues;

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractPersistentJMSTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.RouteStartupOrder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
public class JmsDirectStartupOrderIssueTest extends AbstractPersistentJMSTest {

    @Test
    public void testJmsDirectStartupOrderIssue() throws Exception {
        // send messages to queue so there is messages on the queue before we start the route
        template.sendBody("activemq:queue:JmsDirectStartupOrderIssueTest", "Hello World");
        template.sendBody("activemq:queue:JmsDirectStartupOrderIssueTest", "Hello Camel");
        template.sendBody("activemq:queue:JmsDirectStartupOrderIssueTest", "Bye World");
        template.sendBody("activemq:queue:JmsDirectStartupOrderIssueTest", "Bye Camel");

        context.getRouteController().startRoute("amq");

        getMockEndpoint("mock:result").expectedMessageCount(4);

        MockEndpoint.assertIsSatisfied(context);

        DefaultCamelContext dcc = (DefaultCamelContext) context;
        List<RouteStartupOrder> order = dcc.getCamelContextExtension().getRouteStartupOrder();
        assertEquals(2, order.size());
        assertEquals(1, order.get(0).getStartupOrder());
        assertEquals("direct", order.get(0).getRoute().getId());
        assertEquals(100, order.get(1).getStartupOrder());
        assertEquals("amq", order.get(1).getRoute().getId());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("activemq:queue:JmsDirectStartupOrderIssueTest").routeId("amq").startupOrder(100).autoStartup(false)
                        .to("direct:JmsDirectStartupOrderIssueTest");

                from("direct:JmsDirectStartupOrderIssueTest").routeId("direct").startupOrder(1)
                        .to("mock:result");
            }
        };
    }

}
