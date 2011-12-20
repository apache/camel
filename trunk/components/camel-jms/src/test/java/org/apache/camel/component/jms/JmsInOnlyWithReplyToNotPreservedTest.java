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
public class JmsInOnlyWithReplyToNotPreservedTest extends CamelTestSupport {

    @Test
    public void testSendInOnlyWithReplyTo() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("World");
        getMockEndpoint("mock:done").expectedBodiesReceived("World");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();

        // there should be no messages on the bar queue
        Object msg = consumer.receiveBody("activemq:queue:bar", 1000);
        assertNull("Should be no message on bar queue", msg);
    }

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
                    .to("activemq:queue:foo?replyTo=queue:bar")
                    .to("mock:done");

                from("activemq:queue:foo")
                    .to("log:foo?showAll=true", "mock:foo")
                    .transform(body().prepend("Bye "));
            }
        };
    }
}
