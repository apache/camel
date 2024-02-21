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

import java.util.Properties;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.junit.jupiter.api.Test;

/**
 * The test ensuring that the precondition set on a route configuration determines if the route configuration is
 * included or not
 */
class RouteConfigurationPreconditionTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testRouteConfigurationIncluded() throws Exception {
        Properties init = new Properties();
        init.setProperty("activate", "true");
        context.getPropertiesComponent().setInitialProperties(init);

        context.addRoutes(createRouteBuilder());
        context.start();

        assertCollectionSize(context.getRouteConfigurationDefinitions(), 2);
        assertCollectionSize(context.getRouteDefinitions(), 1);
        assertCollectionSize(context.getRoutes(), 1);

        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:error").expectedBodiesReceived("Activated");

        template.sendBody("direct:start", "Hello Activated Route Config");

        assertMockEndpointsSatisfied();
    }

    @Test
    void testRouteConfigurationExcluded() throws Exception {
        Properties init = new Properties();
        init.setProperty("activate", "false");
        context.getPropertiesComponent().setInitialProperties(init);

        context.addRoutes(createRouteBuilder());
        context.start();

        assertCollectionSize(context.getRouteConfigurationDefinitions(), 1);
        assertCollectionSize(context.getRouteDefinitions(), 1);
        assertCollectionSize(context.getRoutes(), 1);

        getMockEndpoint("mock:error").expectedMessageCount(1);
        getMockEndpoint("mock:error").expectedBodiesReceived("Default");

        template.sendBody("direct:start", "Hello Not Activated Route Config");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteConfigurationBuilder() {
            @Override
            public void configuration() {
                from("direct:start")
                        .throwException(new IllegalArgumentException("Foo"));
                routeConfiguration().precondition("{{activate}}").onException(IllegalArgumentException.class).handled(true)
                        .transform(constant("Activated"))
                        .to("mock:error");
                routeConfiguration().onException(Exception.class).handled(true)
                        .transform(constant("Default"))
                        .to("mock:error");
            }
        };
    }
}
