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
package org.apache.camel.builder;

import org.apache.camel.ContextTestSupport;
import org.junit.jupiter.api.Test;

class RouteTemplateChoicePredicateTest extends ContextTestSupport {

    @Test
    void testRed() throws Exception {
        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("start", "start")
                .parameter("color", "red")
                .routeId("myRoute")
                .add();

        getMockEndpoint("mock:other").expectedMessageCount(0);
        getMockEndpoint("mock:red").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello Red");

        assertMockEndpointsSatisfied();
    }

    @Test
    void testBlue() throws Exception {
        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("start", "start")
                .parameter("color", "blue")
                .routeId("myRoute")
                .add();

        getMockEndpoint("mock:other").expectedMessageCount(1);
        getMockEndpoint("mock:red").expectedMessageCount(0);

        template.sendBody("direct:start", "Hello Blue");

        assertMockEndpointsSatisfied();
    }

    @Test
    void testRedAndBlue() throws Exception {
        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("start", "start1")
                .parameter("color", "blue")
                .routeId("myRoute1")
                .add();

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("start", "start2")
                .parameter("color", "red")
                .routeId("myRoute2")
                .add();

        getMockEndpoint("mock:other").expectedMessageCount(1);
        getMockEndpoint("mock:red").expectedMessageCount(1);

        template.sendBody("direct:start1", "Hello Red");
        template.sendBody("direct:start2", "Hello Bue");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate")
                        .templateParameter("start")
                        .templateParameter("color")
                        .from("direct:{{start}}")
                        .choice()
                            .when(simple("'{{color}}' == 'red'")).to("mock:red")
                            .otherwise().to("mock:other")
                        .end()
                        .to("mock:end");
            }
        };
    }
}
