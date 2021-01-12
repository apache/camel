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
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsToDSendDynamicTwoTest extends CamelTestSupport {

    @Test
    public void testToD() throws Exception {
        template.sendBodyAndHeader("direct:start", "Hello bar", "where", "bar");
        template.sendBodyAndHeader("direct:start", "Hello beer", "where", "beer");
        template.sendBodyAndHeader("direct:start", "Hello gin", "where", "gin");

        template.sendBodyAndHeader("direct:start2", "Hello beer", "where2", "beer");
        template.sendBodyAndHeader("direct:start2", "Hello whiskey", "where2", "whiskey");

        // there should be 2 activemq endpoint
        long count = context.getEndpoints().stream().filter(e -> e.getEndpointUri().startsWith("activemq:")).count();
        assertEquals(2, count, "There should only be 2 activemq endpoint");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createPersistentConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // route message dynamic using toD
                from("direct:start").toD("activemq:queue:${header.where}");

                from("direct:start2").toD("activemq:queue:${header.where2}");
            }
        };
    }
}
