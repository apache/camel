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
package org.apache.camel.model;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.apache.camel.builder.TemplatedRouteBuilder;
import org.junit.jupiter.api.Test;

public class RouteConfigurationOnExceptionTest extends ContextTestSupport {

    @Override
    protected RouteBuilder[] createRouteBuilders() {
        return new RouteBuilder[] {
                new RouteBuilder() {
                    @Override
                    public void configure() {
                        routeTemplate("route-template")
                                .from("direct:start-template")
                                .routeConfigurationId("my-error-handler")
                                .throwException(RuntimeException.class, "Expected Error");
                    }
                },
                new RouteBuilder() {
                    @Override
                    public void configure() {
                        routeTemplate("route-template-parameter")
                                .templateParameter("configuration-id")
                                .templateParameter("route-id")
                                .from("direct:start-template-parameter")
                                .routeId("{{route-id}}")
                                .routeConfigurationId("{{configuration-id}}")
                                .throwException(RuntimeException.class, "Expected Error");
                    }
                },
                new RouteBuilder() {
                    @Override
                    public void configure() {
                        TemplatedRouteBuilder.builder(context, "route-template")
                                .routeId("my-test-file-route")
                                .add();
                    }
                },
                new RouteBuilder() {
                    @Override
                    public void configure() {
                        TemplatedRouteBuilder.builder(context, "route-template-parameter")
                                .routeId("my-test-file-route-parameter")
                                .parameter("configuration-id", "my-error-handler")
                                .parameter("route-id", "custom-route-id")
                                .add();
                    }
                },
                new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("direct:start-normal")
                                .routeConfigurationId("my-error-handler")
                                .throwException(RuntimeException.class, "Expected Error");
                    }
                },
                new RouteConfigurationBuilder() {
                    @Override
                    public void configuration() {
                        routeConfiguration("my-error-handler").onException(Exception.class).handled(true)
                                .transform(constant("Error Received"))
                                .to("mock:result");
                    }
                }
        };
    }

    @Test
    void testRouteTemplateCanSupportRouteConfiguration() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedBodiesReceived("Error Received");
        template.sendBody("direct:start-template", "foo");
        assertMockEndpointsSatisfied();
    }

    @Test
    void testRouteTemplateCanSupportRouteConfigurationWithParameter() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedBodiesReceived("Error Received");
        template.sendBody("direct:start-template-parameter", "foo");
        assertMockEndpointsSatisfied();
    }

    @Test
    void testNormalRouteCanSupportRouteConfiguration() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedBodiesReceived("Error Received");
        template.sendBody("direct:start-normal", "foo");
        assertMockEndpointsSatisfied();
    }
}
