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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.model.RouteTemplateDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RouteTemplateAndExistingRouteTest extends ContextTestSupport {

    @Test
    public void testDefineRouteTemplate() throws Exception {
        assertEquals(1, context.getRouteTemplateDefinitions().size());

        RouteTemplateDefinition routeTemplate = context.getRouteTemplateDefinition("myTemplate");
        assertEquals("foo", routeTemplate.getTemplateParameters().get(0).getName());
        assertEquals("greeting", routeTemplate.getTemplateParameters().get(1).getName());
    }

    @Test
    public void testCreateRouteFromRouteTemplate() throws Exception {
        assertEquals(1, context.getRouteTemplateDefinitions().size());

        RouteTemplateDefinition routeTemplate = context.getRouteTemplateDefinition("myTemplate");
        assertEquals("foo", routeTemplate.getTemplateParameters().get(0).getName());
        assertEquals("greeting", routeTemplate.getTemplateParameters().get(1).getName());

        getMockEndpoint("mock:common").expectedBodiesReceived("Hello Camel", "Hello World");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("foo", "one");
        parameters.put("greeting", "Camel");
        context.addRouteFromTemplate("first", "myTemplate", parameters);

        parameters.put("foo", "two");
        parameters.put("greeting", "World");
        context.addRouteFromTemplate("second", "myTemplate", parameters);

        assertEquals(3, context.getRouteDefinitions().size());
        assertEquals(3, context.getRoutes().size());
        assertEquals("Started", context.getRouteController().getRouteStatus("first").name());
        assertEquals("Started", context.getRouteController().getRouteStatus("second").name());
        assertEquals("Started", context.getRouteController().getRouteStatus("common").name());

        template.sendBody("direct:one", "Camel");
        template.sendBody("direct:two", "World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("greeting")
                        .description("Route saying {{greeting}}")
                        .from("direct:{{foo}}")
                        .transform(simple("Hello {{greeting}}"))
                        .to("direct:common");

                from("direct:common").routeId("common")
                        .to("mock:common");
            }
        };
    }
}
