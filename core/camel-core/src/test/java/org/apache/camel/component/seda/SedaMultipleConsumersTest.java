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
package org.apache.camel.component.seda;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class SedaMultipleConsumersTest extends ContextTestSupport {

    @Test
    public void testSedaMultipleConsumers() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceivedInAnyOrder("Hello World", "Bye World");
        getMockEndpoint("mock:b").expectedBodiesReceivedInAnyOrder("Hello World", "Bye World");

        template.sendBody("seda:foo", "Hello World");
        template.sendBody("seda:bar", "Bye World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSedaMultipleConsumersNewAdded() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceivedInAnyOrder("Hello World", "Bye World");
        getMockEndpoint("mock:b").expectedBodiesReceivedInAnyOrder("Hello World", "Bye World");
        getMockEndpoint("mock:c").expectedMessageCount(0);

        template.sendBody("seda:foo", "Hello World");
        template.sendBody("seda:bar", "Bye World");

        assertMockEndpointsSatisfied();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo?multipleConsumers=true").id("testRoute").to("mock:c");

            }

        });
        resetMocks();

        getMockEndpoint("mock:a").expectedMessageCount(20);
        getMockEndpoint("mock:b").expectedMessageCount(20);
        getMockEndpoint("mock:c").expectedMessageCount(20);

        for (int i = 0; i < 10; i++) {
            template.sendBody("seda:foo", "Hello World");
            template.sendBody("seda:bar", "Bye World");
        }
        assertMockEndpointsSatisfied();
        resetMocks();

        context.getRouteController().suspendRoute("testRoute");
        getMockEndpoint("mock:a").expectedMessageCount(20);
        getMockEndpoint("mock:b").expectedMessageCount(20);
        getMockEndpoint("mock:c").expectedMessageCount(0);

        for (int i = 0; i < 10; i++) {
            template.sendBody("seda:foo", "Hello World");
            template.sendBody("seda:bar", "Bye World");
        }
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo?multipleConsumers=true").to("mock:a");

                from("seda:foo?multipleConsumers=true").to("mock:b");

                from("seda:bar").to("seda:foo?multipleConsumers=true");
            }
        };
    }
}
