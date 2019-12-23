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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class DynamicRouterOnExceptionTest extends ContextTestSupport {

    @Test
    public void testOk() throws Exception {
        getMockEndpoint("mock:end").expectedMessageCount(1);

        MockEndpoint route = getMockEndpoint("mock:route");
        route.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testException() throws Exception {
        getMockEndpoint("mock:end").expectedMessageCount(1);

        MockEndpoint route = getMockEndpoint("mock:route");
        route.whenExchangeReceived(1, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.setException(new IllegalArgumentException("Forced"));
            }
        });
        route.whenExchangeReceived(2, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Bye World");
            }
        });
        route.expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testExceptionTwo() throws Exception {
        getMockEndpoint("mock:end").expectedMessageCount(2);

        MockEndpoint route = getMockEndpoint("mock:route");
        route.whenExchangeReceived(1, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.setException(new IllegalArgumentException("Forced"));
            }
        });
        route.whenExchangeReceived(2, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Bye World");
            }
        });
        route.whenExchangeReceived(3, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.setException(new IllegalArgumentException("Forced"));
            }
        });
        route.whenExchangeReceived(4, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Bye World");
            }
        });
        route.expectedMessageCount(4);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(IllegalArgumentException.class)
                    // setting delay to zero is just to make unit testing faster
                    .redeliveryDelay(0).maximumRedeliveries(5);

                from("direct:start").dynamicRouter(method(DynamicRouterOnExceptionTest.class, "whereTo")).to("mock:end");
            }
        };
    }

    public static String whereTo(Exchange exchange) {
        Boolean invoked = exchange.getProperty("invoked", Boolean.class);
        if (invoked == null) {
            exchange.setProperty("invoked", true);
            return "mock:route";
        } else {
            return null;
        }
    }
}
