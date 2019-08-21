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
package org.apache.camel.processor.interceptor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.reifier.RouteReifier;
import org.junit.Test;

public class AdviceWithTwoRoutesTest extends ContextTestSupport {

    @Test
    public void testAdviceWithA() throws Exception {
        RouteDefinition route = context.getRouteDefinition("a");
        RouteReifier.adviceWith(route, context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("mock://a").skipSendToOriginalEndpoint().to("mock:detour");
            }
        });

        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:detour").expectedMessageCount(1);

        template.sendBody("direct:a", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAdviceWithB() throws Exception {
        RouteDefinition route = context.getRouteDefinition("b");
        RouteReifier.adviceWith(route, context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("mock://b").skipSendToOriginalEndpoint().to("mock:detour");
            }
        });

        getMockEndpoint("mock:b").expectedMessageCount(0);
        getMockEndpoint("mock:detour").expectedMessageCount(1);

        template.sendBody("direct:b", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAdviceWithAB() throws Exception {
        RouteDefinition route = context.getRouteDefinition("a");
        RouteReifier.adviceWith(route, context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("mock://a").skipSendToOriginalEndpoint().to("mock:detour");
            }
        });

        route = context.getRouteDefinition("b");
        RouteReifier.adviceWith(route, context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("mock://b").skipSendToOriginalEndpoint().to("mock:detour");
            }
        });

        getMockEndpoint("mock:a").expectedMessageCount(0);
        getMockEndpoint("mock:b").expectedMessageCount(0);
        getMockEndpoint("mock:detour").expectedMessageCount(2);

        template.sendBody("direct:a", "Hello World");
        template.sendBody("direct:b", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:a").routeId("a").to("log:a").setBody(body().regexReplaceAll("\n", "")).to("mock:a");

                from("direct:b").routeId("b").to("log:b").to("mock:b");
            }
        };
    }

}
