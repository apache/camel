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
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class CBRWithLoadBalancerTest extends ContextTestSupport {

    @Test
    public void testFoo() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(0);

        template.sendBody("direct:start", "Camel rules");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFooBar() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);

        template.sendBody("direct:start", "Camel rules");
        template.sendBody("direct:start", "Camel rocks");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testResult() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:foo").expectedMessageCount(0);
        getMockEndpoint("mock:bar").expectedMessageCount(0);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start").choice().when(body().contains("Camel"))
                    // we need to use endChoice to tell Java DSL to return scope
                    // back to the choice DSL
                    .loadBalance().roundRobin().to("mock:foo").to("mock:bar").endChoice().otherwise().to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
}
