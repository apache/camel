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
package org.apache.camel.spring.routebuilder;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.spring.SpringTestSupport;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SpringRouteTemplateTest extends SpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/routebuilder/SpringRouteTemplateTest.xml");
    }

    @Test
    public void testDefineRouteTemplate() throws Exception {
        assertEquals(1, context.getRouteTemplateDefinitions().size());

        RouteTemplateDefinition routeTemplate = context.getRouteTemplateDefinition("myTemplate");
        assertEquals("foo", routeTemplate.getTemplateParameters().get(0).getName());
        assertEquals("bar", routeTemplate.getTemplateParameters().get(1).getName());
    }

    @Test
    public void testCreateRouteFromRouteTemplate() throws Exception {
        assertEquals(1, context.getRouteTemplateDefinitions().size());

        RouteTemplateDefinition routeTemplate = context.getRouteTemplateDefinition("myTemplate");
        assertEquals("foo", routeTemplate.getTemplateParameters().get(0).getName());
        assertEquals("bar", routeTemplate.getTemplateParameters().get(1).getName());

        getMockEndpoint("mock:cheese").expectedBodiesReceived("Hello Cheese");
        getMockEndpoint("mock:cake").expectedBodiesReceived("Hello Cake");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("foo", "one");
        parameters.put("bar", "cheese");
        context.addRouteFromTemplate("first", "myTemplate", parameters);

        parameters.put("foo", "two");
        parameters.put("bar", "cake");
        context.addRouteFromTemplate("second", "myTemplate", parameters);

        assertEquals(2, context.getRouteDefinitions().size());
        assertEquals(2, context.getRoutes().size());
        assertEquals("Started", context.getRouteController().getRouteStatus("first").name());
        assertEquals("Started", context.getRouteController().getRouteStatus("second").name());
        assertEquals("true", context.getRoute("first").getProperties().get(Route.TEMPLATE_PROPERTY));
        assertEquals("true", context.getRoute("second").getProperties().get(Route.TEMPLATE_PROPERTY));

        template.sendBody("direct:one", "Hello Cheese");
        template.sendBody("direct:two", "Hello Cake");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCreateRouteFromRouteTemplateAutoAssignedRouteId() throws Exception {
        assertEquals(1, context.getRouteTemplateDefinitions().size());

        RouteTemplateDefinition routeTemplate = context.getRouteTemplateDefinition("myTemplate");
        assertEquals("foo", routeTemplate.getTemplateParameters().get(0).getName());
        assertEquals("bar", routeTemplate.getTemplateParameters().get(1).getName());

        getMockEndpoint("mock:cheese").expectedBodiesReceived("Hello Cheese");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("foo", "one");
        parameters.put("bar", "cheese");
        String routeId = context.addRouteFromTemplate(null, "myTemplate", parameters);

        assertNotNull(routeId);
        assertEquals(1, context.getRouteDefinitions().size());
        assertEquals(1, context.getRoutes().size());
        assertEquals("Started", context.getRouteController().getRouteStatus(routeId).name());
        assertEquals("true", context.getRoute(routeId).getProperties().get(Route.TEMPLATE_PROPERTY));

        template.sendBody("direct:one", "Hello Cheese");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testCreateRouteFromRouteTemplateAutoAssignedRouteIdClash() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // use a route id that can clash with auto assigned
                from("direct:hello").to("mock:hello").routeId("route1");
            }
        });

        assertEquals(1, context.getRouteDefinitions().size());
        assertEquals(1, context.getRouteTemplateDefinitions().size());

        RouteTemplateDefinition routeTemplate = context.getRouteTemplateDefinition("myTemplate");
        assertEquals("foo", routeTemplate.getTemplateParameters().get(0).getName());
        assertEquals("bar", routeTemplate.getTemplateParameters().get(1).getName());

        getMockEndpoint("mock:cheese").expectedBodiesReceived("Hello Cheese");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("foo", "one");
        parameters.put("bar", "cheese");
        String routeId = context.addRouteFromTemplate(null, "myTemplate", parameters);

        assertNotNull(routeId);
        assertNotEquals("route1", routeId, "Should not be named route1");
        assertEquals(2, context.getRouteDefinitions().size());
        assertEquals(2, context.getRoutes().size());
        assertEquals("Started", context.getRouteController().getRouteStatus(routeId).name());
        assertEquals("true", context.getRoute(routeId).getProperties().get(Route.TEMPLATE_PROPERTY));

        template.sendBody("direct:one", "Hello Cheese");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDumpModelAsXml() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("foo", "one");
        parameters.put("bar", "cheese");
        context.addRouteFromTemplate("first", "myTemplate", parameters);

        RouteDefinition def = context.getRouteDefinition("first");

        String xml = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, def, true, false);

        assertNotNull(xml);
        Assertions.assertTrue(xml.contains("<from uri=\"direct:one\"/>"));
    }

    @Test
    public void testDumpModelsAsXml() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("foo", "one");
        parameters.put("bar", "cheese");
        context.addRouteFromTemplate("first", "myTemplate", parameters);

        RoutesDefinition def = new RoutesDefinition();
        def.setRoutes(context.getRouteDefinitions());

        String xml = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, def, true, false);

        assertNotNull(xml);
        Assertions.assertTrue(xml.contains("<from uri=\"direct:one\"/>"));
    }

}
