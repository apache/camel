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
package org.apache.camel.component.jms;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * @version 
 */
public class JmsMultipleConsumersTest extends CamelTestSupport {

    @Test
    public void testMultipleConsumersTopic() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jms:topic:foo").to("mock:foo");

                from("direct:start").to("mock:result");

                from("jms:topic:foo").to("mock:bar");
            }
        });
        context.start();

        // give it a bit time to setup both topic listeners
        Thread.sleep(2000);

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("jms:topic:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMultipleConsumersQueue() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jms:queue:foo").to("mock:result");

                from("direct:start").to("mock:result");

                from("jms:queue:foo").to("mock:result");
            }
        });

        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(2);

        template.sendBody("jms:queue:foo", "Hello World");
        template.sendBody("jms:queue:foo", "Bye World");

        assertMockEndpointsSatisfied();
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("jms", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}