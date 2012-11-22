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
 *
 */
public class TwoConsumerOnSameTopicTest extends CamelTestSupport {

    @Test
    public void testTwoConsumerOnSameTopic() throws Exception {
        // give a bit of time for AMQ to properly setup topic subscribers
        Thread.sleep(500);

        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello World");

        template.sendBody("activemq:topic:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testStopOneRoute() throws Exception {
        // give a bit of time for AMQ to properly setup topic subscribers
        Thread.sleep(500);

        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello World");
        template.sendBody("activemq:topic:foo", "Hello World");
        assertMockEndpointsSatisfied();

        // now stop route A
        context.stopRoute("a");

        // send new message should go to B only
        resetMocks();
        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:b").expectedBodiesReceived("Bye World");
        template.sendBody("activemq:topic:foo", "Bye World");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testStopAndStartOneRoute() throws Exception {
        // give a bit of time for AMQ to properly setup topic subscribers
        Thread.sleep(500);

        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello World");
        template.sendBody("activemq:topic:foo", "Hello World");
        assertMockEndpointsSatisfied();

        // now stop route A
        context.stopRoute("a");

        // send new message should go to B only
        resetMocks();
        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:b").expectedBodiesReceived("Bye World");
        template.sendBody("activemq:topic:foo", "Bye World");
        assertMockEndpointsSatisfied();

        // send new message should go to both A and B
        resetMocks();
        getMockEndpoint("mock:a").expectedBodiesReceived("Hi Camel");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hi Camel");
        // now start route A
        context.startRoute("a");
        template.sendBody("activemq:topic:foo", "Hi Camel");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRemoveOneRoute() throws Exception {
        // give a bit of time for AMQ to properly setup topic subscribers
        Thread.sleep(500);

        getMockEndpoint("mock:a").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:b").expectedBodiesReceived("Hello World");
        template.sendBody("activemq:topic:foo", "Hello World");
        assertMockEndpointsSatisfied();

        // now stop and remove route A
        context.stopRoute("a");
        context.removeRoute("a");

        // send new message should go to B only
        resetMocks();
        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:b").expectedBodiesReceived("Bye World");
        template.sendBody("activemq:topic:foo", "Bye World");
        assertMockEndpointsSatisfied();
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        // must be persistent to remember the messages
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createPersistentConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("activemq:topic:foo").routeId("a")
                    .to("mock:a");

                from("activemq:topic:foo").routeId("b")
                    .to("mock:b");
            }
        };
    }
}
