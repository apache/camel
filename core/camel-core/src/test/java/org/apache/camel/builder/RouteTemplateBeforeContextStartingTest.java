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
import org.apache.camel.Route;
import org.apache.camel.model.RouteTemplateDefinition;
import org.junit.jupiter.api.Test;

import static org.apache.camel.util.CollectionHelper.mapOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RouteTemplateBeforeContextStartingTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testCreateRouteFromRouteTemplate() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .from("direct:{{foo}}")
                        .to("mock:{{bar}}");
            }
        });

        assertEquals(1, context.getRouteTemplateDefinitions().size());

        RouteTemplateDefinition routeTemplate = context.getRouteTemplateDefinition("myTemplate");
        assertEquals("foo", routeTemplate.getTemplateParameters().get(0).getName());
        assertEquals("bar", routeTemplate.getTemplateParameters().get(1).getName());

        getMockEndpoint("mock:cheese").expectedBodiesReceived("Hello Cheese");
        getMockEndpoint("mock:cake").expectedBodiesReceived("Hello Cake");

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .routeId("first")
                .parameter("foo", "one")
                .parameter("bar", "cheese")
                .add();

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .routeId("second")
                .parameters(mapOf("foo", "two", "bar", "cake"))
                .add();

        // now start camel
        context.start();

        assertEquals(2, context.getRouteDefinitions().size());
        assertEquals(2, context.getRoutes().size());
        assertEquals("Started", context.getRouteController().getRouteStatus("first").name());
        assertEquals("Started", context.getRouteController().getRouteStatus("second").name());
        assertEquals("true", context.getRoute("first").getProperties().get(Route.TEMPLATE_PROPERTY));
        assertEquals("true", context.getRoute("second").getProperties().get(Route.TEMPLATE_PROPERTY));

        template.sendBody("direct:one", "Hello Cheese");
        template.sendBody("direct:two", "Hello Cake");

        assertMockEndpointsSatisfied();

        context.stop();
    }

}
