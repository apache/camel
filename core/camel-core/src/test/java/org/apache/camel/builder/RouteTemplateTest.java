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
import org.apache.camel.model.Model;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteTemplateDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RouteTemplateTest extends ContextTestSupport {

    @Test
    public void testDefineRouteTemplate() throws Exception {
        assertEquals(1, context.getRouteTemplateDefinitions().size());

        RouteTemplateDefinition template = context.getRouteTemplateDefinition("myTemplate");
        assertEquals("foo,bar", template.getProperties());
    }

    @Test
    public void testCreateRouteFromRouteTemplate() throws Exception {
        assertEquals(1, context.getRouteTemplateDefinitions().size());

        RouteTemplateDefinition routeTemplate = context.getRouteTemplateDefinition("myTemplate");
        assertEquals("foo,bar", routeTemplate.getProperties());

        getMockEndpoint("mock:cheese").expectedBodiesReceived("Hello Cheese");
        getMockEndpoint("mock:cake").expectedBodiesReceived("Hello Cake");

        Map<String, Object> map = new HashMap<>();
        map.put("foo", "one");
        map.put("bar", "cheese");
        context.getExtension(Model.class).addRouteFromTemplate("myTemplate", "first", map);

        map.put("foo", "two");
        map.put("bar", "cake");
        context.getExtension(Model.class).addRouteFromTemplate("myTemplate", "second", map);

        assertEquals(2, context.getRouteDefinitions().size());
        assertEquals(2, context.getRoutes().size());
        assertEquals("Started", context.getRouteController().getRouteStatus("first").name());
        assertEquals("Started", context.getRouteController().getRouteStatus("second").name());

        template.sendBody("direct:one", "Hello Cheese");
        template.sendBody("direct:two", "Hello Cake");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate", "foo", "bar")
                    .from("direct:{{foo}}")
                    .to("mock:{{bar}}");
            }
        };
    }
}
