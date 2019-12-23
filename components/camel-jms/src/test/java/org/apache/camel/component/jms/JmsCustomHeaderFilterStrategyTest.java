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

import java.util.HashMap;
import java.util.Map;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class JmsCustomHeaderFilterStrategyTest extends CamelTestSupport {

    protected String componentName = "activemq";

    @Test
    public void testCustomHeaderFilterStrategy() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header("foo").isEqualTo("bar");
        mock.message(0).header("skipme").isNull();

        Map<String, Object> headers = new HashMap<>();
        headers.put("foo", "bar");
        headers.put("skipme", 123);

        template.sendBodyAndHeaders("activemq:queue:foo", "Hello World", headers);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent(componentName, jmsComponentAutoAcknowledge(connectionFactory));

        JmsComponent jms = camelContext.getComponent(componentName, JmsComponent.class);
        jms.setHeaderFilterStrategy(new MyHeaderFilterStrategy());

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("activemq:queue:foo?eagerLoadingOfProperties=true").to("mock:result");
            }
        };
    }

    private static class MyHeaderFilterStrategy implements HeaderFilterStrategy {

        @Override
        public boolean applyFilterToCamelHeaders(String s, Object o, Exchange exchange) {
            return false;
        }

        @Override
        public boolean applyFilterToExternalHeaders(String s, Object o, Exchange exchange) {
            return s.equals("skipme");
        }
    }

}
