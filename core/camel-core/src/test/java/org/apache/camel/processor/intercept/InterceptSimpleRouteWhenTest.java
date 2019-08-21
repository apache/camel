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
package org.apache.camel.processor.intercept;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class InterceptSimpleRouteWhenTest extends ContextTestSupport {

    @Test
    public void testInterceptWhen() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World", "Hi");
        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello World", "Hi");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World", "Hi");

        getMockEndpoint("mock:intercepted").expectedBodiesReceived("Hello World", "Hello World", "Hello World");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Hi");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                intercept().when(body().contains("Hello")).to("mock:intercepted");

                from("direct:start").to("mock:foo", "mock:bar", "mock:result");
                // END SNIPPET: e1
            }
        };
    }
}
