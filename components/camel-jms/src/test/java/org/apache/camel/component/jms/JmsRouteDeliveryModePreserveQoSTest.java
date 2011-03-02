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

import java.util.Map;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.CamelTestSupport;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * Unit test for preserveMessageQos with delivery mode
 */
public class JmsRouteDeliveryModePreserveQoSTest extends CamelTestSupport {

    public void testSendDefault() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:bar");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("activemq:queue:foo?preserveMessageQos=true", "Hello World");

        assertMockEndpointsSatisfied();

        // should be persistent by default
        Map map = mock.getReceivedExchanges().get(0).getIn().getHeaders();
        assertNotNull(map);
        assertEquals(DeliveryMode.PERSISTENT, map.get("JMSDeliveryMode"));
    }

    public void testSendNonPersistent() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:bar");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("activemq:queue:foo?preserveMessageQos=true", "Hello World", "JMSDeliveryMode", DeliveryMode.NON_PERSISTENT);

        assertMockEndpointsSatisfied();

        // should preserve non persistent
        Map map = mock.getReceivedExchanges().get(0).getIn().getHeaders();
        assertNotNull(map);
        assertEquals(DeliveryMode.NON_PERSISTENT, map.get("JMSDeliveryMode"));
    }

    public void testSendPersistent() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:bar");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("activemq:queue:foo?preserveMessageQos=true", "Hello World", "JMSDeliveryMode", DeliveryMode.PERSISTENT);

        assertMockEndpointsSatisfied();

        // should preserve persistent
        Map map = mock.getReceivedExchanges().get(0).getIn().getHeaders();
        assertNotNull(map);
        assertEquals(DeliveryMode.PERSISTENT, map.get("JMSDeliveryMode"));
    }

    protected CamelContext createCamelContext() throws Exception {
        deleteDirectory("activemq-data");
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
                from("activemq:queue:foo")
                    .to("log:foo")
                    .to("activemq:queue:bar?preserveMessageQos=true");

                from("activemq:queue:bar").to("mock:bar");
            }
        };
    }
}
