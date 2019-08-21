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

public class RoutingSlipInOutAndInOnlyTest extends ContextTestSupport {

    private String slip = "direct:a,direct:b,direct:c";

    @Test
    public void testRoutingSlipInOut() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("HelloABC");

        String out = template.requestBodyAndHeader("direct:start", "Hello", "slip", slip, String.class);
        assertEquals("HelloABC", out);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRoutingSlipInOnly() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("HelloABC");

        template.sendBodyAndHeader("direct:start", "Hello", "slip", slip);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routingSlip(header("slip")).to("mock:result");

                from("direct:a").transform(body().append("A"));

                from("direct:b").transform(body().append("B"));

                from("direct:c").transform(body().append("C"));
            }
        };
    }
}
