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

public class JmsToTest extends AbstractJMSTest {

    @Test
    public void testTo() throws Exception {
        getMockEndpoint("mock:JmsToTest.bar").expectedMessageCount(0);
        getMockEndpoint("mock:JmsToTest.beer").expectedMessageCount(0);
        getMockEndpoint("mock:where").expectedMessageCount(2);

        template.sendBodyAndHeader("direct:start", "Hello bar", "where", "JmsToTest.bar");
        template.sendBodyAndHeader("direct:start", "Hello beer", "where", "JmsToTest.beer");

        assertMockEndpointsSatisfied();
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
                // wrongly using to instead of toD
                from("direct:start").to("activemq:queue:${header.where}");

                from("activemq:queue:JmsToTest.bar").to("mock:JmsToTest.bar");
                from("activemq:queue:JmsToTest.beer").to("mock:JmsToTest.beer");

                // and all the messages goes here
                from("activemq:queue:${header.where}").to("mock:where");
            }
        };
    }
}
