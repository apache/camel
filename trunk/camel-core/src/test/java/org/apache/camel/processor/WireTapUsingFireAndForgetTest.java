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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class WireTapUsingFireAndForgetTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testFireAndForgetUsingProcessor() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @SuppressWarnings("deprecation")
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    .wireTap("direct:foo", false, new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            exchange.getIn().setBody("Bye World");
                            exchange.getIn().setHeader("foo", "bar");
                        }
                    }).to("mock:result");


                from("direct:foo").to("mock:foo");
                // END SNIPPET: e1
            }
        });
        context.start();

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello World");

        MockEndpoint foo = getMockEndpoint("mock:foo");
        foo.expectedBodiesReceived("Bye World");
        foo.expectedHeaderReceived("foo", "bar");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // should be different exchange instances
        Exchange e1 = result.getReceivedExchanges().get(0);
        Exchange e2 = foo.getReceivedExchanges().get(0);
        assertNotSame("Should not be same Exchange", e1, e2);

        // should have same from endpoint
        assertEquals("direct://start", e1.getFromEndpoint().getEndpointUri());
        assertEquals("direct://start", e2.getFromEndpoint().getEndpointUri());
    }

    public void testFireAndForgetUsingExpression() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @SuppressWarnings("deprecation")
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e2
                from("direct:start")
                    .wireTap("direct:foo", false, constant("Bye World"))
                    .to("mock:result");

                from("direct:foo").to("mock:foo");
                // END SNIPPET: e2
            }
        });
        context.start();

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello World");

        MockEndpoint foo = getMockEndpoint("mock:foo");
        foo.expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // should be different exchange instances
        Exchange e1 = result.getReceivedExchanges().get(0);
        Exchange e2 = foo.getReceivedExchanges().get(0);
        assertNotSame("Should not be same Exchange", e1, e2);

        // should have same from endpoint
        assertEquals("direct://start", e1.getFromEndpoint().getEndpointUri());
        assertEquals("direct://start", e2.getFromEndpoint().getEndpointUri());
    }

}
