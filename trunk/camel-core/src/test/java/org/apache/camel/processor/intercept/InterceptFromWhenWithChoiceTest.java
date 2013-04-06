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
package org.apache.camel.processor.intercept;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class InterceptFromWhenWithChoiceTest extends ContextTestSupport {

    public void testInterceptorHelloWorld() throws Exception {
        getMockEndpoint("mock:goofy").expectedMessageCount(0);
        getMockEndpoint("mock:hello").expectedMessageCount(0);
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:end").expectedMessageCount(1);

        sendBody("direct:start", "Hello World!");

        assertMockEndpointsSatisfied();
    }

    public void testInterceptorHelloGoofy() throws Exception {
        getMockEndpoint("mock:goofy").expectedMessageCount(0);
        getMockEndpoint("mock:hello").expectedMessageCount(1);
        getMockEndpoint("mock:foo").expectedMessageCount(0);
        getMockEndpoint("mock:end").expectedMessageCount(0);

        sendBody("direct:start", "Hello Goofy");

        assertMockEndpointsSatisfied();
    }

    public void testInterceptorByeGoofy() throws Exception {
        getMockEndpoint("mock:goofy").expectedMessageCount(1);
        getMockEndpoint("mock:hello").expectedMessageCount(0);
        getMockEndpoint("mock:foo").expectedMessageCount(0);
        getMockEndpoint("mock:end").expectedMessageCount(0);

        sendBody("direct:start", "Bye Goofy");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptFrom()
                    .when(simple("${body} contains 'Goofy'"))
                        .choice()
                            .when(body().contains("Hello")).to("mock:hello")
                            .otherwise().to("log:foo").to("mock:goofy")
                        .end()
                    .stop();

                from("direct:start").to("mock:foo").to("mock:end");
            }
        };
    }
}