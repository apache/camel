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

import java.util.Arrays;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Route;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RouteTemplateInterceptFromAndInterceptSendToEndpointWithPlaceholderTest extends ContextTestSupport {

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

                RouteConfigurationDefinition routeConfigurationDefinition = new RouteConfigurationDefinition();
                routeConfigurationDefinition.interceptFrom("direct:intercepted-from").to("mock:intercepted-from");
                routeConfigurationDefinition.interceptSendToEndpoint("mock:intercepted-send")
                        .to("mock:intercepted-send-to-before")
                        .afterUri("mock:intercepted-send-to-after");

                context.addRouteConfiguration(routeConfigurationDefinition);
            }
        });

        assertEquals(1, context.getRouteTemplateDefinitions().size());

        RouteTemplateDefinition routeTemplate = context.getRouteTemplateDefinition("myTemplate");
        assertEquals("foo", routeTemplate.getTemplateParameters().get(0).getName());
        assertEquals("bar", routeTemplate.getTemplateParameters().get(1).getName());

        for (String uriSuffix : Arrays.asList("from", "send-to-before", "send-to-after")) {
            getMockEndpoint("mock:intercepted-" + uriSuffix).expectedBodiesReceived("Hello Intercepted");
        }

        TemplatedRouteBuilder.builder(context, "myTemplate")
                .routeId("intercepted")
                .parameter("foo", "intercepted-from")
                .parameter("bar", "intercepted-send")
                .add();

        // now start camel
        context.start();

        assertEquals(1, context.getRouteDefinitions().size());
        assertEquals(1, context.getRoutes().size());
        assertEquals("Started", context.getRouteController().getRouteStatus("intercepted").name());
        assertEquals("true", context.getRoute("intercepted").getProperties().get(Route.TEMPLATE_PROPERTY));

        template.sendBody("direct:intercepted-from", "Hello Intercepted");

        assertMockEndpointsSatisfied();

        context.stop();
    }

}
