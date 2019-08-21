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

/**
 * Unit tests on the conditional skip support on InterceptSendToEndpoint.
 */
public class InterceptSendToEndpointConditionalSkip2Test extends ContextTestSupport {

    @Test
    public void testInterceptSendToEndpointNone() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:detour1").expectedMessageCount(0);
        getMockEndpoint("mock:detour2").expectedMessageCount(0);
        getMockEndpoint("mock:c").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInterceptSendToEndpoint1() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:detour1").expectedMessageCount(1);
        getMockEndpoint("mock:detour2").expectedMessageCount(0);
        getMockEndpoint("mock:c").expectedMessageCount(1);

        template.sendBody("direct:start", "skip1");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInterceptSendToEndpoint2() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:detour1").expectedMessageCount(0);
        getMockEndpoint("mock:detour2").expectedMessageCount(1);
        getMockEndpoint("mock:c").expectedMessageCount(1);

        template.sendBody("direct:start", "skip2");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testInterceptSendToEndpointBoth() throws Exception {
        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:detour1").expectedMessageCount(1);
        getMockEndpoint("mock:detour2").expectedMessageCount(1);
        getMockEndpoint("mock:c").expectedMessageCount(1);

        template.sendBody("direct:start", "skip1,skip2");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // we have 2 interceptors, which may both trigger, or either or,
                // or none
                interceptSendToEndpoint("mock:skip1").skipSendToOriginalEndpoint().when(body().contains("skip1")).to("mock:detour1");

                interceptSendToEndpoint("mock:skip2").skipSendToOriginalEndpoint().when(body().contains("skip2")).to("mock:detour2");

                from("direct:start").to("mock:a").to("mock:skip1").to("mock:skip2").to("mock:c");
            }
        };
    }

}
