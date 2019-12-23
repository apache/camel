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
package org.apache.camel.itest.async;

import javax.jms.ConnectionFactory;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.itest.CamelJmsTestHelper;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class HttpAsyncDslTest extends CamelTestSupport {

    private static volatile String order = "";

    @Test
    public void testRequestOnly() throws Exception {
        getMockEndpoint("mock:validate").expectedMessageCount(1);
        // even though its request only the message is still continued being processed
        getMockEndpoint("mock:order").expectedMessageCount(1);

        template.sendBody("jms:queue:order", "Order: Camel in Action");
        order += "C";

        assertMockEndpointsSatisfied();

        // B should be last (either ABC or BAC depending on threading)
        assertEquals(3, order.length());
        assertTrue(order.endsWith("B"));
    }

    @Test
    public void testRequestReply() throws Exception {
        getMockEndpoint("mock:validate").expectedMessageCount(1);
        // even though its request only the message is still continued being processed
        getMockEndpoint("mock:order").expectedMessageCount(1);

        String response = template.requestBody("jms:queue:order", "Order: Camel in Action", String.class);
        order += "C";

        assertMockEndpointsSatisfied();

        // should be in strict ABC order as we do request/reply
        assertEquals("ABC", order);
        assertEquals("Order OK", response);
    }

    @Override
    @Before
    public void setUp() throws Exception {
        order = "";
        super.setUp();
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry =  new SimpleRegistry();
        registry.bind("validateOrder", new MyValidateOrderBean());
        registry.bind("handleOrder", new MyHandleOrderBean());
        return registry;
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        // add ActiveMQ with embedded broker
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        JmsComponent amq = jmsComponentAutoAcknowledge(connectionFactory);
        amq.setCamelContext(context);
        registry.bind("jms", amq);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1

                // list on the JMS queue for new orders
                from("jms:queue:order")
                    // do some sanity check validation
                    .to("bean:validateOrder")
                    .to("mock:validate")
                    // use multi threading with a pool size of 20
                    // turn the route async as some others do not expect a reply
                    // and a few does then we can use the threads DSL as a turning point
                    // if the JMS ReplyTo was set then we expect a reply, otherwise not
                    // use a pool of 20 threads for the point forward
                    .threads(20)
                    // do some CPU heavy processing of the message (we simulate and delay just 500 ms)
                    .delay(500).to("bean:handleOrder").to("mock:order");
                // END SNIPPET: e1
            }
        };
    }

    public static class MyValidateOrderBean {

        public void validateOrder(byte[] payload) {
            order += "A";
            // noop
        }
    }

    public static class MyHandleOrderBean {

        public String handleOrder(String message) {
            order += "B";
            return "Order OK";
            // noop
        }
    }
}
