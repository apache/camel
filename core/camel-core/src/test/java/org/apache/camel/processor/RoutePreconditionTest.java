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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The test ensuring that the precondition set on a route determines if the route is included or not
 */
class RoutePreconditionTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testRouteIncluded() throws Exception {
        Properties init = new Properties();
        init.setProperty("protocol", "json");
        context.getPropertiesComponent().setInitialProperties(init);

        context.addRoutes(createRouteBuilder());
        context.start();

        assertCollectionSize(context.getRouteDefinitions(), 2);
        assertCollectionSize(context.getRoutes(), 2);
        assertNotNull(context.getRoute("myRoute"));
        assertNotNull(context.getRoute("myRouteNP"));

        getMockEndpoint("mock:out").expectedMessageCount(1);

        template.sendBody("direct:in", "Hello Included Route");

        assertMockEndpointsSatisfied();
    }

    @Test
    void testRouteExcluded() throws Exception {
        Properties init = new Properties();
        init.setProperty("protocol", "avro");
        context.getPropertiesComponent().setInitialProperties(init);

        context.addRoutes(createRouteBuilder());
        context.start();

        assertCollectionSize(context.getRouteDefinitions(), 1);
        assertCollectionSize(context.getRoutes(), 1);
        assertNull(context.getRoute("myRoute"));
        assertNotNull(context.getRoute("myRouteNP"));
    }

    @Test
    void testRouteIncludedByDefault() throws Exception {
        Properties init = new Properties();
        init.setProperty("protocol", "foo");
        context.getPropertiesComponent().setInitialProperties(init);

        context.addRoutes(createRouteBuilder());
        context.start();

        assertCollectionSize(context.getRouteDefinitions(), 1);
        assertCollectionSize(context.getRoutes(), 1);
        assertNotNull(context.getRoute("myRouteNP"));

        getMockEndpoint("mock:outNP").expectedMessageCount(1);

        template.sendBody("direct:inNP", "Hello Included Route");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in").routeId("myRoute").precondition("'{{protocol}}' == 'json'")
                        .to("mock:out");
                from("direct:inNP").routeId("myRouteNP")
                        .to("mock:outNP");
            }
        };
    }

}
