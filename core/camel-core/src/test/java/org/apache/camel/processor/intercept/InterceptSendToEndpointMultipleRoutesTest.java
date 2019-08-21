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
 * Unit test for intercepting sending to endpoint with multiple routes
 */
public class InterceptSendToEndpointMultipleRoutesTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testInterceptEndpoint() throws Exception {
        // NOTE: each of these routes must extend our base route class

        context.addRoutes(new MyBaseRoute() {
            @Override
            public void configure() throws Exception {
                super.configure();

                from("direct:a").to("seda:a").to("mock:result");
            }
        });
        context.addRoutes(new MyBaseRoute() {
            @Override
            public void configure() throws Exception {
                super.configure();

                from("direct:b").to("seda:b").to("mock:result");
            }
        });
        context.addRoutes(new MyBaseRoute() {
            @Override
            public void configure() throws Exception {
                super.configure();

                from("direct:c").to("seda:c").to("mock:c").to("mock:result");
            }
        });

        context.start();

        getMockEndpoint("mock:result").expectedBodiesReceived("A", "B", "C");
        getMockEndpoint("mock:detour").expectedBodiesReceived("A", "B", "C");
        getMockEndpoint("mock:c").expectedBodiesReceived("C");

        template.sendBody("direct:a", "A");
        template.sendBody("direct:b", "B");
        template.sendBody("direct:c", "C");

        assertMockEndpointsSatisfied();
    }

    private abstract class MyBaseRoute extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            // base route with common interceptors

            interceptSendToEndpoint("seda:*").skipSendToOriginalEndpoint().to("mock:detour");
        }
    }

}
