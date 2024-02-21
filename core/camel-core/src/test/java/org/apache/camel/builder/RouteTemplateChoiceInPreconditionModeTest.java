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

class RouteTemplateChoiceInPreconditionModeTest extends ContextTestSupport {

    @Test
    void testRed() throws Exception {
        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("red", "true")
                .parameter("blue", "false")
                .routeId("myRoute")
                .add();

        getMockEndpoint("mock:end").expectedMessageCount(1);
        getMockEndpoint("mock:red").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello Red");

        assertMockEndpointsSatisfied();
    }

    @Test
    void testBlue() throws Exception {
        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("red", "false")
                .parameter("blue", "true")
                .routeId("myRoute")
                .add();

        getMockEndpoint("mock:end").expectedMessageCount(1);
        getMockEndpoint("mock:blue").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello Red");

        assertMockEndpointsSatisfied();
    }

    @Test
    void testNotProvidedBlue() throws Exception {
        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("blue", "true")
                .routeId("myRoute")
                .add();

        getMockEndpoint("mock:end").expectedMessageCount(1);
        getMockEndpoint("mock:blue").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello Red");

        assertMockEndpointsSatisfied();
    }

    @Test
    void testNotProvided() throws Exception {
        TemplatedRouteBuilder.builder(context, "myTemplate")
                .routeId("myRoute")
                .add();

        getMockEndpoint("mock:end").expectedMessageCount(1);
        getMockEndpoint("mock:red").expectedMessageCount(0);
        getMockEndpoint("mock:blue").expectedMessageCount(0);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate")
                        .templateOptionalParameter("red")
                        .templateOptionalParameter("blue")
                        .from("direct:start")
                        .choice().precondition()
                            .when(simple("{{?red}}")).to("mock:red")
                            .when(simple("{{?blue}}")).to("mock:blue")
                        .end()
                        .to("mock:end");
            }
        };
    }
}
