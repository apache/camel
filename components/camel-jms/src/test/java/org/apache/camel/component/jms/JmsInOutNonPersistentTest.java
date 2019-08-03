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
import javax.jms.DeliveryMode;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class JmsInOutNonPersistentTest extends CamelTestSupport {

    @Test
    public void testInOutNonPersistent() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("World");
        getMockEndpoint("mock:foo").expectedHeaderReceived("JMSDeliveryMode", DeliveryMode.NON_PERSISTENT);
        getMockEndpoint("mock:done").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:done").expectedHeaderReceived("JMSDeliveryMode", DeliveryMode.NON_PERSISTENT);

        String reply = template.requestBody("direct:start", "World", String.class);
        assertEquals("Bye World", reply);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("activemq:queue:foo?replyTo=queue:bar&deliveryPersistent=false")
                    .to("log:done?showAll=true", "mock:done");

                from("activemq:queue:foo?replyToDeliveryPersistent=false&preserveMessageQos=true")
                    .to("log:foo?showAll=true", "mock:foo")
                    .transform(body().prepend("Bye "));
            }
        };
    }
}
