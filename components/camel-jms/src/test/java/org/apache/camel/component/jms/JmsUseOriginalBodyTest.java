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
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * Unit test for useOriginalBody unit test
 */
public class JmsUseOriginalBodyTest extends CamelTestSupport {

    @Test
    public void testUseOriginalBody() throws Exception {
        MockEndpoint dead = getMockEndpoint("mock:a");
        dead.expectedBodiesReceived("Hello");

        template.sendBody("activemq:queue:a", "Hello");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDoNotUseOriginalBody() throws Exception {
        MockEndpoint dead = getMockEndpoint("mock:b");
        dead.expectedBodiesReceived("Hello World");

        template.sendBody("activemq:queue:b", "Hello");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("activemq:queue:a")
                    .onException(IllegalArgumentException.class)
                        .handled(true)
                        .useOriginalMessage()
                        .maximumRedeliveries(2)
                        .to("mock:a")
                        .end()
                    .setBody(body().append(" World"))
                    .process(new MyThrowProcessor());

                from("activemq:queue:b")
                    .onException(IllegalArgumentException.class)
                        .handled(true)
                        .maximumRedeliveries(2)
                        // this route does not .useOriginalMessage()
                        .to("mock:b")
                        .end()
                    .setBody(body().append(" World"))
                    .process(new MyThrowProcessor());
            }
        };
    }

    public static class MyThrowProcessor implements Processor {

        public MyThrowProcessor() {
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            assertEquals("Hello World", exchange.getIn().getBody(String.class));
            throw new IllegalArgumentException("Forced");
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

}
