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

class RouteTemplateOptionalEndpointUriTest extends ContextTestSupport {

    @Test
    void testToWithOptionalUriNotProvided() throws Exception {
        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("name", "test1")
                .routeId("myRoute1")
                .add();

        getMockEndpoint("mock:end").expectedMessageCount(1);
        getMockEndpoint("mock:end").expectedBodiesReceived("Hello World");

        template.sendBody("direct:test1", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    void testToWithOptionalUriProvided() throws Exception {
        TemplatedRouteBuilder.builder(context, "myTemplate")
                .parameter("name", "test2")
                .parameter("optionalUri", "mock:middle")
                .routeId("myRoute2")
                .add();

        getMockEndpoint("mock:middle").expectedMessageCount(1);
        getMockEndpoint("mock:end").expectedMessageCount(1);

        template.sendBody("direct:test2", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    void testMultipleOptionalUris() throws Exception {
        TemplatedRouteBuilder.builder(context, "myMultiTemplate")
                .parameter("name", "test3")
                .parameter("optionalUri2", "mock:second")
                .routeId("myRoute3")
                .add();

        // optionalUri1 is not provided, so that step is skipped
        // optionalUri2 is provided, so it should receive the message
        getMockEndpoint("mock:second").expectedMessageCount(1);
        getMockEndpoint("mock:end").expectedMessageCount(1);

        template.sendBody("direct:test3", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    void testAllOptionalUrisNotProvided() throws Exception {
        TemplatedRouteBuilder.builder(context, "myMultiTemplate")
                .parameter("name", "test4")
                .routeId("myRoute4")
                .add();

        // both optional URIs not provided, message goes straight to mock:end
        getMockEndpoint("mock:end").expectedMessageCount(1);
        getMockEndpoint("mock:end").expectedBodiesReceived("Hello World");

        template.sendBody("direct:test4", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("myTemplate")
                        .templateParameter("name")
                        .templateOptionalParameter("optionalUri")
                        .from("direct:{{name}}")
                        .to("{{?optionalUri}}")
                        .to("mock:end");

                routeTemplate("myMultiTemplate")
                        .templateParameter("name")
                        .templateOptionalParameter("optionalUri1")
                        .templateOptionalParameter("optionalUri2")
                        .from("direct:{{name}}")
                        .to("{{?optionalUri1}}")
                        .to("{{?optionalUri2}}")
                        .to("mock:end");
            }
        };
    }
}
