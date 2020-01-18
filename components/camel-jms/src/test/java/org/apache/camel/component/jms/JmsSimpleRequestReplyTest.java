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
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * A simple request / reply test
 */
public class JmsSimpleRequestReplyTest extends CamelTestSupport {

    protected String componentName = "activemq";

    @Test
    public void testRequestReply() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        Exchange out = template.send("activemq:queue:hello", ExchangePattern.InOut, exchange -> {
            exchange.getIn().setBody("Hello World");
            exchange.getIn().setHeader("foo", 123);
        });

        result.assertIsSatisfied();

        assertNotNull(out);

        assertEquals("Bye World", out.getMessage().getBody(String.class));
        assertEquals(123, out.getMessage().getHeader("foo"));
    }

    @Test
    public void testRequestReply2Messages() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(2);

        template.requestBody("activemq:queue:hello", "Hello World");
        template.requestBody("activemq:queue:hello", "Gooday World");

        result.assertIsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent(componentName, jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("activemq:queue:hello").process(exchange -> {
                    exchange.getIn().setBody("Bye World");
                    assertNotNull(exchange.getIn().getHeader("JMSReplyTo"));
                }).to("mock:result");
            }
        };
    }
}
