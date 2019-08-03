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
package org.apache.camel.component.jms.issues;

import java.util.HashMap;
import java.util.Map;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class JmsHeaderAsObjectTest extends CamelTestSupport {

    @Test
    public void testSendHeaderAsPrimitiveOnly() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.message(0).header("foo").isEqualTo("bar");
        mock.message(0).header("number").isEqualTo(23);

        Map<String, Object> headers = new HashMap<>();
        headers.put("foo", "bar");
        headers.put("number", 23);
        template.sendBodyAndHeaders("activemq:in", "Hello World", headers);

        mock.assertIsSatisfied();
    }

    @Test
    public void testSendHeaderAsObject() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.message(0).header("foo").isEqualTo("bar");
        mock.message(0).header("order").isNull();

        DummyOrder order = new DummyOrder();
        order.setItemId(4444);
        order.setOrderId(333);
        order.setQuantity(2);

        Map<String, Object> headers = new HashMap<>();
        headers.put("foo", "bar");
        headers.put("order", order);
        template.sendBodyAndHeaders("activemq:in", "Hello World", headers);

        mock.assertIsSatisfied();
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
            public void configure() throws Exception {
                from("activemq:in").to("mock:result");
            }
        };
    }
}
