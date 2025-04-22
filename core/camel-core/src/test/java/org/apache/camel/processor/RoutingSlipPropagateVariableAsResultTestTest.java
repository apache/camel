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
import org.junit.jupiter.api.Test;

public class RoutingSlipPropagateVariableAsResultTestTest extends ContextTestSupport {

    @Test
    public void testRoutingSlip() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("ABCD");
        getMockEndpoint("mock:result").expectedVariableReceived("foo1", "AB");
        getMockEndpoint("mock:result").expectedVariableReceived("foo2", "ABC");
        getMockEndpoint("mock:result").expectedVariableReceived("foo3", "ABCD");

        template.sendBodyAndHeader("direct:start", "A", "slip", "direct:slip1,direct:slip2,direct:slip3");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .routingSlip(header("slip"))
                        .to("mock:result");

                from("direct:slip1")
                        .transform(body().append("B"))
                        .setVariable("foo1", simple("${body}"));

                from("direct:slip2")
                        .transform(body().append("C"))
                        .setVariable("foo2", simple("${body}"));

                from("direct:slip3")
                        .transform(body().append("D"))
                        .setVariable("foo3", simple("${body}"));
            }
        };
    }
}
