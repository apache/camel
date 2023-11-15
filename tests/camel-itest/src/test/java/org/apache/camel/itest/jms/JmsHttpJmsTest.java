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
package org.apache.camel.itest.jms;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.utils.extensions.JmsServiceExtension;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Based on user forum.
 */
public class JmsHttpJmsTest extends CamelTestSupport {

    @RegisterExtension
    public static JmsServiceExtension jmsServiceExtension = JmsServiceExtension.createExtension();

    private int port;

    @Test
    void testJmsHttpJms() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Bye World");

        template.sendBody("jms:in", "Hello World");

        Endpoint endpoint = context.getEndpoint("jms:out");
        endpoint.createConsumer(exchange -> assertEquals("Bye World", exchange.getIn().getBody(String.class)));

        mock.assertIsSatisfied();
    }

    @Test
    void testResultReplyJms() throws Exception {
        Exchange exchange = template.request("jms:reply?replyTo=bar", exchange1 -> exchange1.getIn().setBody("Hello World"));
        assertEquals("Bye World", exchange.getMessage().getBody(String.class));
        assertTrue(exchange.getMessage().hasHeaders(), "Should have headers");
        assertEquals("ActiveMQQueue[bar]", exchange.getMessage().getHeader("JMSDestination", String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        port = AvailablePortFinder.getNextAvailable();

        return new RouteBuilder() {
            public void configure() {
                from("jms:in").to("http://localhost:" + port + "/myservice").convertBodyTo(String.class).to("jms:out",
                        "mock:result");

                from("jms:reply").to("http://localhost:" + port + "/myservice");

                from("jetty:http://0.0.0.0:" + port + "/myservice").transform().constant("Bye World");
            }
        };
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        // add ActiveMQ with embedded broker
        JmsComponent amq = jmsServiceExtension.getComponent();

        amq.setCamelContext(context);
        registry.bind("jms", amq);
    }

}
