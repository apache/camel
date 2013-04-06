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
import org.apache.camel.builder.RouteBuilder;

/**
 * Unit test for stop() DSL
 *
 * @version 
 */
public class RouteStopTest extends ContextTestSupport {

    public void testOtherwise() throws Exception {
        getMockEndpoint("mock:hello").expectedMessageCount(0);
        getMockEndpoint("mock:bye").expectedMessageCount(0);
        getMockEndpoint("mock:other").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Other");

        assertMockEndpointsSatisfied();
    }

    public void testHello() throws Exception {
        getMockEndpoint("mock:hello").expectedMessageCount(1);
        getMockEndpoint("mock:bye").expectedMessageCount(0);
        getMockEndpoint("mock:other").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testByeWithStop() throws Exception {
        getMockEndpoint("mock:hello").expectedMessageCount(0);
        getMockEndpoint("mock:bye").expectedMessageCount(1);
        getMockEndpoint("mock:other").expectedMessageCount(0);
        // we should stop so no message arrives at result
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();
    }

    public void testSetPropertyToStop() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:foo", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    .choice()
                        .when(body().contains("Hello")).to("mock:hello")
                        .when(body().contains("Bye")).to("mock:bye").stop()
                        .otherwise().to("mock:other")
                    .end()
                    .to("mock:result");
                // END SNIPPET: e1

                from("direct:foo")
                    .to("mock:foo")
                    .setProperty(Exchange.ROUTE_STOP, constant("true"))
                    .to("mock:result");
            }
        };
    }
}
