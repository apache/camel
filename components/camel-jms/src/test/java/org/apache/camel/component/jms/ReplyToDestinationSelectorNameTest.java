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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 *
 */
public class ReplyToDestinationSelectorNameTest extends CamelTestSupport {

    @Test
    public void testReplyToDestinationSelectorName() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World", "Bye Camel");

        Object body1 = template.requestBody("direct:start", "World");
        Object body2 = template.requestBody("direct:start", "Camel");

        assertMockEndpointsSatisfied();

        assertEquals("Bye World", body1);
        assertEquals("Bye Camel", body2);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        JmsComponent component = jmsComponentAutoAcknowledge(connectionFactory);
        camelContext.addComponent("activemq", component);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("activemq:queue:foo?replyTo=queue:bar&replyToDestinationSelectorName=replyId")
                    .to("mock:result");

                from("activemq:queue:foo")
                    .log("Using header named replyId with value as correlation - ${header.replyId}")
                    .transform(body().prepend("Bye "));
            }
        };
    }
}
