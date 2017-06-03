/**
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
package org.apache.camel.impl;

import java.net.URL;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;

/**
 * @version 
 */
public class CamelContextAddRouteDefinitionsFromXmlTest extends ContextTestSupport {

    protected JAXBContext jaxbContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        jaxbContext = context.getModelJAXBContextFactory().newJAXBContext();
    }

    protected Object parseUri(String uri) throws JAXBException {
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        URL resource = getClass().getResource(uri);
        assertNotNull("Cannot find resource on the classpath: " + uri, resource);
        Object value = unmarshaller.unmarshal(resource);
        return value;
    }

    protected RouteDefinition loadRoute(String uri) throws Exception {
        Object route = parseUri(uri);
        return assertIsInstanceOf(RouteDefinition.class, route);
    }

    public void testAddRouteDefinitionsFromXml() throws Exception {
        RouteDefinition route = loadRoute("route1.xml");
        assertNotNull(route);

        assertEquals("foo", route.getId());
        assertEquals(0, context.getRoutes().size());

        context.addRouteDefinition(route);
        assertEquals(1, context.getRoutes().size());
        assertTrue("Route should be started", context.getRouteStatus("foo").isStarted());

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();
    }

    public void testRemoveRouteDefinitionsFromXml() throws Exception {
        RouteDefinition route = loadRoute("route1.xml");
        assertNotNull(route);

        assertEquals("foo", route.getId());
        assertEquals(0, context.getRoutes().size());

        context.addRouteDefinition(route);
        assertEquals(1, context.getRouteDefinitions().size());
        assertEquals(1, context.getRoutes().size());
        assertTrue("Route should be started", context.getRouteStatus("foo").isStarted());

        context.removeRouteDefinition(route);
        assertEquals(0, context.getRoutes().size());
        assertNull(context.getRouteStatus("foo"));

        assertEquals(0, context.getRouteDefinitions().size());
    }

    public void testAddRouteDefinitionsFromXml2() throws Exception {
        RouteDefinition route = loadRoute("route2.xml");
        assertNotNull(route);

        assertEquals("foo", route.getId());
        assertEquals(0, context.getRoutes().size());

        context.addRouteDefinition(route);
        assertEquals(1, context.getRoutes().size());
        assertTrue("Route should be stopped", context.getRouteStatus("foo").isStopped());

        context.startRoute("foo");
        assertTrue("Route should be started", context.getRouteStatus("foo").isStarted());

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();
    }

    public void testAddRouteDefinitionsFromXmlIsPrepared() throws Exception {
        RouteDefinition route = loadRoute("route1.xml");
        assertNotNull(route);

        assertEquals("foo", route.getId());
        assertEquals(0, context.getRoutes().size());

        context.addRouteDefinition(route);
        assertEquals(1, context.getRoutes().size());
        assertTrue("Route should be started", context.getRouteStatus("foo").isStarted());

        // should be prepared, check parents has been set
        assertNotNull("Parent should be set on outputs");
        route = context.getRouteDefinition("foo");
        for (ProcessorDefinition<?> output : route.getOutputs()) {
            assertNotNull("Parent should be set on output", output.getParent());
            assertEquals(route, output.getParent());
        }
    }

    public void testAddRouteDefinitionsFromXml3() throws Exception {
        RouteDefinition route = loadRoute("route3.xml");
        assertNotNull(route);

        assertEquals("foo", route.getId());
        assertEquals(0, context.getRoutes().size());

        context.addRouteDefinition(route);
        assertEquals(1, context.getRoutes().size());
        assertTrue("Route should be started", context.getRouteStatus("foo").isStarted());

        getMockEndpoint("mock:foo").whenExchangeReceived(2, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.setException(new IllegalArgumentException("Damn"));
            }
        });

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:handled").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start", "Bye World");

        assertMockEndpointsSatisfied();
    }

    public void testAddRouteDefinitionsAfterExceptionFromXml() throws Exception {
        RouteDefinition route = loadRoute("route4_error.xml");
        assertNotNull(route);

        assertEquals("foo", route.getId());
        assertEquals(0, context.getRoutes().size());

        try {
            context.addRouteDefinition(route);
        } catch (Exception e) {
            // catch this is error to simulate test case!!!!
        }
        // load route with same id
        route = loadRoute("route4_ok.xml");
        assertNotNull(route);
        assertEquals("foo", route.getId());
        assertEquals(0, context.getRoutes().size());

        context.addRouteDefinition(route);
        assertEquals(1, context.getRoutes().size());
    }
}
