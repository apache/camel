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
package org.apache.camel.component.sjms.consumer;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.component.sjms.jms.ConnectionFactoryResource;
import org.apache.camel.test.junit4.CamelTestSupport;

import org.junit.Test;

public class InOnlyTopicDurableConsumerTest extends CamelTestSupport {
    
    private static final String CONNECTION_ID = "test-connection-1";
    private static final String BROKER_URI = "vm://durable.broker?broker.persistent=false&broker.useJmx=false";
    
    @Override
    protected boolean useJmx() {
        return false;
    }

    @Test
    public void testDurableTopic() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        MockEndpoint mock2 = getMockEndpoint("mock:result2");
        mock2.expectedBodiesReceived("Hello World");

        // wait a bit and send the message
        Thread.sleep(1000);

        template.sendBody("sjms:topic:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    
    /*
     * @see org.apache.camel.test.junit4.CamelTestSupport#createCamelContext()
     *
     * @return
     * @throws Exception
     */
    @Override
    protected CamelContext createCamelContext() throws Exception {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(BROKER_URI);
        ConnectionFactoryResource connectionResource = new ConnectionFactoryResource();
        connectionResource.setConnectionFactory(connectionFactory);
        connectionResource.setClientId(CONNECTION_ID);
        CamelContext camelContext = super.createCamelContext();
        SjmsComponent component = new SjmsComponent();
        component.setConnectionResource(connectionResource);
        component.setConnectionCount(1);
        camelContext.addComponent("sjms", component);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("sjms:topic:foo?durableSubscriptionId=bar1")
                    .to("mock:result");

                from("sjms:topic:foo?durableSubscriptionId=bar2")
                    .to("mock:result2");
            }
        };
    }
}
